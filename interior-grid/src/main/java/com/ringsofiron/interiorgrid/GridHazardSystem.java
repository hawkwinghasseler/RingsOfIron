package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;

/**
 * Simulates atmosphere, heat and fire inside a ship's interior grid. Runs on a fixed
 * tick of {@link #HAZARD_TICK} seconds; every rate below is per second.
 *
 * <h2>Atmosphere</h2>
 * Air lives in {@link Compartment}s, one well-mixed volume per room (see
 * {@link CompartmentBuilder} for what counts as a room). Air is conserved. It moves
 * between neighbouring compartments through the openings between them, driven by the
 * pressure difference, and leaves the ship only through openings to space: breached
 * hull, holes in the deck, and damaged walls, hull and floors that leak (see
 * {@link Tile#leakFactor(float)}). Vacuum is simply a compartment with no air left; it
 * does not spread. A sealed bulkhead or closed door is airtight, so sealing between a
 * breach and a room stops the room's loss.
 *
 * <h2>Heat</h2>
 * Every tile has a temperature. Heat moves between neighbouring tiles by conduction
 * through decks and walls and, faster, by convection where air connects them (scaled by
 * the air pressure, so a vented room barely convects). Every tile also sheds heat into
 * the ship's structure, so it cools back to ambient once a fire is out.
 *
 * <h2>Fire</h2>
 * A fire burns the combustible covering on a tile (carpet; the steel floor itself does
 * not burn). It starts small and grows toward fully involved while its compartment has
 * oxygen, and shrinks when the oxygen runs low or the fuel runs out. While it burns it
 * uses up fuel and oxygen and gives off heat, smoke and CO2 into its compartment, so a
 * fire in a sealed room starves itself. Fire jumps to a neighbouring flammable tile it
 * is connected to by air with a chance that grows with the fire's size and the target's
 * temperature, and any flammable tile that reaches {@link #IGNITION_TEMPERATURE} with
 * oxygen present catches on its own, even through a wall.
 *
 * Random draws come from a seeded generator so runs are reproducible.
 */
public class GridHazardSystem extends IteratingSystem {

    /** Seconds between hazard steps. */
    public static final float HAZARD_TICK = 0.25f;

    // --- Air flow ----------------------------------------------------------
    /** Molar flow through an opening one tile wide, mol/s per kPa of pressure difference. */
    public static final double FLOW_COEFFICIENT = 2.0;

    // --- Heat --------------------------------------------------------------
    /** Heat capacity of one tile (its surfaces and the air over it), J/K. */
    public static final float TILE_HEAT_CAPACITY = 20e3f;
    /** Heat each tile sheds into the ship's structure, W/K above ambient. */
    public static final float AMBIENT_LOSS = 150f;
    /** Conduction between neighbouring tiles through decks and walls, W/K. */
    public static final float CONDUCTION = 60f;
    /** Extra transfer between tiles connected by air at standard pressure, W/K. */
    public static final float CONVECTION = 300f;

    // --- Fire --------------------------------------------------------------
    /** Oxygen ratio (see {@link Compartment#oxygenRatio()}) below which fires shrink. */
    public static final float FIRE_MIN_OXYGEN = 0.6f;
    /** Intensity gained per second at full oxygen: about 30 s from a small fire to fully involved. */
    public static final float FIRE_GROWTH_RATE = 1f / 30f;
    /** Intensity lost per second in vacuum; less as the oxygen approaches the minimum. */
    public static final float FIRE_SMOTHER_RATE = 0.25f;
    /** Below this much fuel per m², a fire's intensity is capped in proportion, so it dies down as the fuel runs out. */
    public static final float FUEL_TAPER = 0.3f;
    /** Share of {@link #FUEL_TAPER} below which the last embers count as burnt out. */
    public static final float BURNT_OUT = 0.05f;
    /** Fuel burned per second by a fully involved fire, kg per m². */
    public static final float BURN_RATE = 0.01f;
    /**
     * Heat a fully involved fire puts into its tile, W per m². Tuned for gameplay rather
     * than derived from {@link #BURN_RATE}: fires burn slowly but hot.
     */
    public static final float FIRE_HEAT_OUTPUT = 1.2e6f;
    /** Oxygen used per kg of fuel, mol. */
    public static final double OXYGEN_PER_FUEL = 70.0;
    /** Carbon dioxide produced per kg of fuel, mol. */
    public static final double CO2_PER_FUEL = 55.0;
    /** Smoke produced per kg of fuel, kg. */
    public static final double SMOKE_PER_FUEL = 0.08;
    /** Temperature at which a flammable tile catches fire on its own, K. */
    public static final float IGNITION_TEMPERATURE = 600f;
    /** Chance per second that a fully involved fire jumps to a neighbour at ignition temperature. */
    public static final float SPREAD_RATE = 0.15f;
    /** Share of {@link #SPREAD_RATE} that still applies to a neighbour at ambient temperature. */
    public static final float SPREAD_COLD_FACTOR = 0.15f;
    /** Chance per second that a tile at ignition temperature catches. */
    public static final float AUTO_IGNITION_RATE = 0.5f;

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final RandomXS128 random;

    private float accumulator = 0f;

    public GridHazardSystem() {
        this(0x5EEDL);
    }

    public GridHazardSystem(long seed) {
        super(Family.all(ShipGridComponent.class).get());
        random = new RandomXS128(seed);
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        while (accumulator >= HAZARD_TICK) {
            accumulator -= HAZARD_TICK;
            super.update(HAZARD_TICK); // process each entity
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        step(gridMapper.get(entity), deltaTime);
    }

    /** Advances one grid by dt seconds. Also usable without an engine. */
    public void step(ShipGridComponent grid, float dt) {
        CompartmentBuilder.rebuildIfChanged(grid);
        for (Compartment c : grid.compartments) c.temperature = CompartmentBuilder.meanTemperature(grid, c);
        processAirflow(grid, dt);
        processFire(grid, dt);
        processHeat(grid, dt);
    }

    /**
     * How a fire responds to its compartment's oxygen: positive grows it (1 at standard
     * air), negative smothers it (-1 in vacuum).
     */
    public static float oxygenFactor(Compartment c) {
        if (c == null) return -1f;
        float f = (c.oxygenRatio() - FIRE_MIN_OXYGEN) / (1f - FIRE_MIN_OXYGEN);
        return Math.max(-1f, Math.min(1f, f));
    }

    // -----------------------------------------------------------------------
    // Air flow
    // -----------------------------------------------------------------------

    /**
     * Total opening between every pair of nodes, in tile widths. Node 0 is space and
     * node i + 1 is compartment i. Only the upper triangle is used.
     */
    float[][] openings(ShipGridComponent grid) {
        int n = grid.compartments.size + 1;
        float[][] open = new float[n][n];
        Side[] sides = Side.values();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.holdsAir()) {
                    int a = node(t.compartment);
                    // A damaged deck plate vents straight to space
                    if (t.isFloor()) add(open, a, 0, t.leakFactor());
                    for (Side s : sides) {
                        Tile nb = grid.getTile(x + s.dx, y + s.dy);
                        int b;
                        if (nb == null || nb.isSpaceExposed()) b = 0;
                        else if (nb.holdsAir()) {
                            // Count each pair of air tiles once
                            if (s == Side.LEFT || s == Side.BOTTOM) continue;
                            b = node(nb.compartment);
                        } else continue; // a wall or a seal; wall leaks are counted below
                        EdgeWall e = grid.getEdge(x, y, s);
                        add(open, a, b, e == null || !e.blocks() ? 1f : e.leakFactor());
                    }
                } else if (t.isIntactWall() && t.leakFactor() > 0f) {
                    // A leaking wall joins every distinct node it touches
                    int[] touched = new int[4];
                    int count = 0;
                    for (Side s : sides) {
                        if (grid.isEdgeBlocked(x, y, x + s.dx, y + s.dy)) continue;
                        Tile nb = grid.getTile(x + s.dx, y + s.dy);
                        int b;
                        if (nb == null || nb.isSpaceExposed()) b = 0;
                        else if (nb.holdsAir()) b = node(nb.compartment);
                        else continue;
                        boolean seen = false;
                        for (int i = 0; i < count; i++) seen |= touched[i] == b;
                        if (!seen) touched[count++] = b;
                    }
                    for (int i = 0; i < count; i++)
                        for (int j = i + 1; j < count; j++) add(open, touched[i], touched[j], t.leakFactor());
                }
            }
        }
        return open;
    }

    private void processAirflow(ShipGridComponent grid, float dt) {
        Array<Compartment> comps = grid.compartments;
        int n = comps.size + 1;
        if (n == 1) return;
        float[][] open = openings(grid);

        double[] pressure = new double[n], k = new double[n], moles = new double[n];
        for (int i = 1; i < n; i++) {
            Compartment c = comps.get(i - 1);
            k[i] = c.kPaPerMole();
            moles[i] = c.totalMoles();
            pressure[i] = moles[i] * k[i];
        }

        // Flow from the higher-pressure side, at most half way to equal pressure per step
        double[][] flow = new double[n][n];
        double[] outflow = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (open[i][j] <= 0f) continue;
                int from = pressure[i] >= pressure[j] ? i : j, to = from == i ? j : i;
                double dp = pressure[from] - pressure[to];
                if (dp <= 1e-9 || from == 0) continue;
                double q = FLOW_COEFFICIENT * open[i][j] * dp * dt;
                double equalize = dp / (k[from] + k[to]);
                q = Math.min(q, 0.5 * equalize);
                flow[from][to] = q;
                outflow[from] += q;
            }
        }

        // Never move more than 90% of a compartment's air in one step
        double[] keepShare = new double[n];
        double[] o2 = new double[n], n2 = new double[n], co2 = new double[n], smoke = new double[n];
        for (int i = 1; i < n; i++) {
            Compartment c = comps.get(i - 1);
            double scale = outflow[i] > 0.9 * moles[i] ? 0.9 * moles[i] / outflow[i] : 1.0;
            for (int j = 0; j < n; j++) flow[i][j] *= scale;
            double out = outflow[i] * scale;
            keepShare[i] = moles[i] > 0 ? 1.0 - out / moles[i] : 1.0;
            o2[i] = c.oxygen; n2[i] = c.nitrogen; co2[i] = c.carbonDioxide; smoke[i] = c.smoke;
        }

        for (int i = 1; i < n; i++) comps.get(i - 1).scaleContents(keepShare[i]);
        for (int from = 1; from < n; from++) {
            if (moles[from] <= 0) continue;
            for (int to = 1; to < n; to++) {
                if (flow[from][to] <= 0) continue;
                double f = flow[from][to] / moles[from];
                Compartment c = comps.get(to - 1);
                c.oxygen += o2[from] * f;
                c.nitrogen += n2[from] * f;
                c.carbonDioxide += co2[from] * f;
                c.smoke += smoke[from] * f;
            }
        }
        // Clear out the last traces so a vented room reads as hard vacuum
        for (Compartment c : comps) {
            if (c.totalMoles() < 1e-3 * c.volume) c.scaleContents(0.0);
        }
    }

    private static int node(Compartment c) {
        return c == null ? 0 : c.id + 1;
    }

    private static void add(float[][] open, int a, int b, float width) {
        if (a == b || width <= 0f) return;
        if (a < b) open[a][b] += width;
        else open[b][a] += width;
    }

    // -----------------------------------------------------------------------
    // Fire
    // -----------------------------------------------------------------------

    private void processFire(ShipGridComponent grid, float dt) {
        float area = grid.tileSize * grid.tileSize;
        Array<Tile> ignitions = new Array<>();

        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (!t.isBurning()) {
                    // Hot enough to catch on its own
                    if (t.isFlammable() && t.temperature >= IGNITION_TEMPERATURE
                            && oxygenFactor(t.compartment) > 0f && random.nextFloat() < AUTO_IGNITION_RATE * dt) {
                        ignitions.add(t);
                    }
                    continue;
                }
                if (!t.holdsAir()) {
                    t.extinguish();
                    continue;
                }
                Compartment c = t.compartment;

                // Grow or shrink
                float o = oxygenFactor(c);
                if (o < 0f) t.fireIntensity += FIRE_SMOTHER_RATE * o * dt;
                else t.fireIntensity += FIRE_GROWTH_RATE * o * dt;
                // As the last of the fuel goes, the fire dies down with it
                t.fireIntensity = Math.min(t.fireIntensity, Math.min(1f, t.fuel / (FUEL_TAPER * area)));
                if (t.fireIntensity <= 0f) {
                    t.extinguish();
                    continue;
                }

                // Burn fuel and oxygen into heat, smoke and CO2
                float wanted = BURN_RATE * area * t.fireIntensity * dt;
                float burned = Math.min(t.fuel, wanted);
                burned = (float) Math.min(burned, c.oxygen / OXYGEN_PER_FUEL);
                t.fuel -= burned;
                c.oxygen = Math.max(0.0, c.oxygen - burned * OXYGEN_PER_FUEL);
                c.carbonDioxide += burned * CO2_PER_FUEL;
                c.smoke += burned * SMOKE_PER_FUEL;
                // Heat follows the share of the wanted burn that fuel and oxygen allowed
                t.temperature += FIRE_HEAT_OUTPUT * area * t.fireIntensity * dt * (burned / wanted) / TILE_HEAT_CAPACITY;
                if (t.fuel <= BURNT_OUT * FUEL_TAPER * area) {
                    // The carpet is gone; the steel floor stays
                    t.fuel = 0f;
                    t.covering = FloorCovering.NONE;
                    t.extinguish();
                    continue;
                }

                // Jump to neighbours connected by air; bigger fires and hotter targets catch sooner
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (!grid.canFlow(x, y, nx, ny)) continue;
                    Tile nb = grid.tiles[ny][nx];
                    if (nb.isBurning() || !nb.isFlammable() || oxygenFactor(nb.compartment) <= 0f) continue;
                    float heat = (nb.temperature - Compartment.AMBIENT_TEMPERATURE)
                            / (IGNITION_TEMPERATURE - Compartment.AMBIENT_TEMPERATURE);
                    heat = Math.max(0f, Math.min(1f, heat));
                    float chance = SPREAD_RATE * t.fireIntensity * (SPREAD_COLD_FACTOR + (1f - SPREAD_COLD_FACTOR) * heat);
                    if (random.nextFloat() < chance * dt) ignitions.add(nb);
                }
            }
        }
        for (Tile t : ignitions) t.ignite();
    }

    // -----------------------------------------------------------------------
    // Heat
    // -----------------------------------------------------------------------

    private void processHeat(ShipGridComponent grid, float dt) {
        int w = grid.width, h = grid.height;
        float[] delta = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = grid.tiles[y][x];
                if (t.isVoid()) continue;
                delta[y * w + x] -= AMBIENT_LOSS * (t.temperature - Compartment.AMBIENT_TEMPERATURE);
                exchange(grid, delta, x, y, x + 1, y);
                exchange(grid, delta, x, y, x, y + 1);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.tiles[y][x].temperature += delta[y * w + x] * dt / TILE_HEAT_CAPACITY;
            }
        }
    }

    private static void exchange(ShipGridComponent grid, float[] delta, int x, int y, int nx, int ny) {
        Tile a = grid.getTile(x, y), b = grid.getTile(nx, ny);
        if (b == null || b.isVoid()) return;
        float g = CONDUCTION;
        if (grid.canFlow(x, y, nx, ny)) g += CONVECTION * Math.min(1f, a.compartment.pressureRatio());
        float flux = g * (b.temperature - a.temperature);
        delta[y * grid.width + x] += flux;
        delta[ny * grid.width + nx] -= flux;
    }
}
