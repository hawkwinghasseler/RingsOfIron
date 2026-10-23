package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

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
 * not burn) and the furnishings set down on it, each its share of the fuel; a furnishing burned
 * past {@link Furnishing#BURNT} is gone ({@link ShipGridComponent#burntFurnishings}). It starts small and grows toward fully involved while its compartment has
 * oxygen, and shrinks when the oxygen runs low or the fuel runs out. While it burns it
 * uses up fuel and oxygen and gives off heat, smoke and CO2 into its compartment, so a
 * fire in a sealed room starves itself. Fire jumps to a neighbouring flammable tile it
 * is connected to by air with a chance that grows with the fire's size and the target's
 * temperature, and any flammable tile that reaches {@link #IGNITION_TEMPERATURE} with
 * oxygen present catches on its own, even through a wall.
 *
 * Random draws come from a seeded generator so runs are reproducible.
 */
public class GridHazardSystem extends FixedLogicSystem {

    /** Seconds between hazard steps. */
    public static final float HAZARD_TICK = 0.25f;
    /** Runs after power routing and before life support. */
    public static final int DEFAULT_PRIORITY = PowerRoutingSystem.DEFAULT_PRIORITY + 1;

    // --- Air flow ----------------------------------------------------------
    /** Molar flow through an opening one tile wide, mol/s per kPa of pressure difference. */
    public static final double FLOW_COEFFICIENT = 2.0;
    /**
     * Gas mixing through an opening one tile wide between two rooms, m³/s of air swapped
     * each way. It evens out composition (oxygen, CO2, smoke) without moving net air. Two
     * 54-tile rooms joined by one door mix most of the way in about 2 minutes. Bulk flow
     * moves this much air at a pressure difference of about 12 kPa, and fires and breaches
     * drive far larger differences, so bulk flow dominates whenever pressures differ.
     */
    public static final double DIFFUSION_COEFFICIENT = 0.6;

    // --- Heat --------------------------------------------------------------
    /** Heat capacity of one tile (its surfaces and the air over it), J/K. */
    public static final float TILE_HEAT_CAPACITY = 20e3f;
    /** Heat each tile sheds into the ship's structure, W/K above ambient. */
    public static final float AMBIENT_LOSS = 150f;
    /** Stefan-Boltzmann constant, W/(m² K⁴). */
    public static final double STEFAN_BOLTZMANN = 5.67e-8;
    /**
     * Surface radiating a tile's heat into the surrounding structure, m² per m² of floor
     * (floor, ceiling and a share of the walls). Radiation grows with T⁴, so it is what
     * holds a fully involved fire near 1500 K instead of letting it climb without limit.
     */
    public static final float RADIATING_AREA = 4f;
    /** Conduction between neighbouring tiles through decks and walls, W/K. */
    public static final float CONDUCTION = 60f;
    /** Extra transfer between tiles connected by air at standard pressure, W/K. */
    public static final float CONVECTION = 300f;

    // --- Damage ------------------------------------------------------------
    /** Wall and door temperature above which steel starts to lose integrity, K. */
    public static final float STRUCTURE_DAMAGE_TEMPERATURE = 900f;
    /** Integrity a wall or door loses per second for every 100 K above {@link #STRUCTURE_DAMAGE_TEMPERATURE}. */
    public static final float STRUCTURE_HEAT_DAMAGE = 0.0005f;
    /** Temperature above which an installed system's electronics take damage, K. */
    public static final float SYSTEM_DAMAGE_TEMPERATURE = 450f;
    /** Health a system loses per second for every kelvin above {@link #SYSTEM_DAMAGE_TEMPERATURE}. */
    public static final float SYSTEM_HEAT_DAMAGE = 0.00005f;
    /**
     * Pressure difference that blows out a closed door of each weight class, kPa. A plain
     * edge wall holds twice as much, so a door is what goes first.
     */
    public static final float BURST_PRESSURE_LIGHT = 150f, BURST_PRESSURE_MEDIUM = 300f, BURST_PRESSURE_HEAVY = 600f;
    /** How much more pressure a plain edge wall holds than a door of the same weight class. */
    public static final float WALL_BURST_FACTOR = 2f;

    // --- Fire --------------------------------------------------------------
    /** Oxygen ratio (see {@link Compartment#oxygenRatio()}) below which fires shrink. */
    public static final float FIRE_MIN_OXYGEN = 0.6f;
    /** Intensity gained per second at full oxygen: about a minute from a small fire to fully involved. */
    public static final float FIRE_GROWTH_RATE = 1f / 60f;
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
    public static final float SPREAD_RATE = 0.05f;
    /** Share of {@link #SPREAD_RATE} that still applies to a neighbour at ambient temperature. */
    public static final float SPREAD_COLD_FACTOR = 0.15f;
    /** Chance per second that a tile at ignition temperature catches. */
    public static final float AUTO_IGNITION_RATE = 0.05f;

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final RandomXS128 random;

    private static final Family FAMILY = Family.all(ShipGridComponent.class).get();
    private ImmutableArray<Entity> entities;

    public GridHazardSystem() {
        this(0x5EEDL);
    }

    public GridHazardSystem(long seed) {
        super(DEFAULT_PRIORITY, HAZARD_TICK);
        random = new RandomXS128(seed);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) step(gridMapper.get(entities.get(i)), deltaTime);
        for (int i = 0; i < entities.size(); i++) exchangeAcrossLinks(gridMapper.get(entities.get(i)), deltaTime);
    }

    /**
     * Air, heat and fire through this grid's {@link GridLink}s into docked craft. Air: the same
     * pressure-driven flow and mixing as between two rooms of one ship, for each pair of rooms the
     * links join. Heat: convection between the two tiles of each link, as across an open doorway
     * (the collars carry none by conduction). Fire: a fire on either tile can jump to the other,
     * as to a neighbour, through the share of the opening the doors leave. Runs once per pair of
     * links, from the primary side. Call after both grids have stepped.
     */
    public void exchangeAcrossLinks(ShipGridComponent grid, float dt) {
        for (int i = 0; i < grid.links.size; i++) {
            GridLink l = grid.links.get(i);
            if (!l.primary) continue;
            float w = l.flowWidth();
            if (w <= 0f) continue;
            Compartment a = grid.compartmentAt(l.x, l.y), b = l.other.compartmentAt(l.ox, l.oy);
            if (a == null || b == null) continue;
            exchange(a, b, w, dt);
            Tile ta = grid.getTile(l.x, l.y), tb = l.other.getTile(l.ox, l.oy);
            float pressure = Math.min(1f, Math.min(a.pressureRatio(), b.pressureRatio()));
            float flux = CONVECTION * w * pressure * (tb.temperature - ta.temperature) * dt / TILE_HEAT_CAPACITY;
            // No more than evens them out
            flux = Math.signum(flux) * Math.min(Math.abs(flux), Math.abs(tb.temperature - ta.temperature) * 0.5f);
            ta.temperature += flux;
            tb.temperature -= flux;
            if (catches(ta, tb, w, dt)) tb.ignite();
            else if (catches(tb, ta, w, dt)) ta.ignite();
        }
    }

    /** Pressure-driven flow, then mixing, between two rooms joined by an opening {@code width} tiles wide. */
    static void exchange(Compartment a, Compartment b, float width, float dt) {
        double ka = a.kPaPerMole(), kb = b.kPaPerMole();
        double ma = a.totalMoles(), mb = b.totalMoles();
        double pa = ma * ka, pb = mb * kb;
        Compartment from = pa >= pb ? a : b, to = from == a ? b : a;
        double mFrom = from == a ? ma : mb;
        double dp = Math.abs(pa - pb);
        if (dp > 1e-9 && mFrom > 0) {
            double q = FLOW_COEFFICIENT * width * dp * dt;
            q = Math.min(q, 0.5 * dp / (ka + kb));
            q = Math.min(q, 0.9 * mFrom);
            double f = q / mFrom;
            to.oxygen += from.oxygen * f;
            to.nitrogen += from.nitrogen * f;
            to.carbonDioxide += from.carbonDioxide * f;
            to.smoke += from.smoke * f;
            from.scaleContents(1.0 - f);
        }
        ma = a.totalMoles();
        mb = b.totalMoles();
        if (ma <= 0 || mb <= 0) return;
        double density = 0.5 * (ma / a.volume + mb / b.volume);
        double swap = Math.min(DIFFUSION_COEFFICIENT * width * density * dt, 0.25 * ma * mb / (ma + mb));
        double fa = swap / ma, fb = swap / mb;
        double dO2 = a.oxygen * fa - b.oxygen * fb;
        double dN2 = a.nitrogen * fa - b.nitrogen * fb;
        double dCO2 = a.carbonDioxide * fa - b.carbonDioxide * fb;
        double dSmoke = a.smoke * fa - b.smoke * fb;
        a.oxygen -= dO2; b.oxygen += dO2;
        a.nitrogen -= dN2; b.nitrogen += dN2;
        a.carbonDioxide -= dCO2; b.carbonDioxide += dCO2;
        a.smoke -= dSmoke; b.smoke += dSmoke;
    }

    /**
     * Rails warp runs only while no fire burns and no air vents (see
     * {@link ShipGridComponent#hasActiveHazard()}), so the jump skips straight to where
     * the slow processes settle: heat bleeds back to ambient, and air mixes evenly
     * between rooms joined by any opening. Rooms that still reach space through a slow
     * leak have had all the time they need to empty.
     */
    @Override
    protected void catchUp(long ticks, double seconds) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) settle(gridMapper.get(entities.get(i)), seconds);
        // Rooms joined across a dock have long since mixed too
        for (int i = 0; i < entities.size(); i++) {
            ShipGridComponent grid = gridMapper.get(entities.get(i));
            for (int k = 0; k < grid.links.size; k++) {
                GridLink l = grid.links.get(k);
                if (!l.primary || l.flowWidth() <= 0f) continue;
                Compartment a = grid.compartmentAt(l.x, l.y), b = l.other.compartmentAt(l.ox, l.oy);
                if (a != null && b != null && a != b) pool(a, b);
            }
        }
    }

    /** Spreads the gas of two rooms over both, by volume. */
    private static void pool(Compartment a, Compartment b) {
        double volume = a.volume + b.volume;
        if (volume <= 0) return;
        double fa = a.volume / volume, fb = b.volume / volume;
        double o2 = a.oxygen + b.oxygen, n2 = a.nitrogen + b.nitrogen, co2 = a.carbonDioxide + b.carbonDioxide,
                smoke = a.smoke + b.smoke;
        a.oxygen = o2 * fa; b.oxygen = o2 * fb;
        a.nitrogen = n2 * fa; b.nitrogen = n2 * fb;
        a.carbonDioxide = co2 * fa; b.carbonDioxide = co2 * fb;
        a.smoke = smoke * fa; b.smoke = smoke * fb;
    }

    /** Advances one grid by a long quiet stretch of {@code seconds}. Also usable without an engine. */
    public void settle(ShipGridComponent grid, double seconds) {
        CompartmentBuilder.rebuildIfChanged(grid);

        // Heat: every tile relaxes toward ambient through its loss to the structure
        float keep = (float) Math.exp(-seconds * AMBIENT_LOSS / TILE_HEAT_CAPACITY);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                t.temperature = Compartment.AMBIENT_TEMPERATURE + (t.temperature - Compartment.AMBIENT_TEMPERATURE) * keep;
            }
        }
        for (Compartment c : grid.compartments) c.temperature = CompartmentBuilder.meanTemperature(grid, c);

        // Air: pool every group of connected rooms; a group open to space empties
        Array<Compartment> comps = grid.compartments;
        int n = comps.size + 1;
        Array<AirflowEdge> edges = new Array<>();
        float[][] open = openings(grid, edges);
        int[] group = new int[n];
        for (int i = 0; i < n; i++) group[i] = i;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (open[i][j] > 0f) union(group, i, j);
        for (int root = 0; root < n; root++) {
            double o2 = 0, n2 = 0, co2 = 0, smoke = 0, volume = 0;
            boolean members = false;
            for (int i = 1; i < n; i++) {
                if (find(group, i) != root) continue;
                Compartment c = comps.get(i - 1);
                o2 += c.oxygen; n2 += c.nitrogen; co2 += c.carbonDioxide; smoke += c.smoke;
                volume += c.volume;
                members = true;
            }
            if (!members || volume <= 0) continue;
            boolean vented = find(group, 0) == root;
            for (int i = 1; i < n; i++) {
                if (find(group, i) != root) continue;
                Compartment c = comps.get(i - 1);
                double f = vented ? 0 : c.volume / volume;
                c.oxygen = o2 * f; c.nitrogen = n2 * f; c.carbonDioxide = co2 * f; c.smoke = smoke * f;
            }
        }
        // Everything has settled: the openings are still there, but nothing moves through them
        grid.airflow.clear();
        grid.airflow.addAll(edges);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /** Advances one grid by dt seconds. Also usable without an engine. */
    public void step(ShipGridComponent grid, float dt) {
        CompartmentBuilder.rebuildIfChanged(grid);
        for (Compartment c : grid.compartments) c.temperature = CompartmentBuilder.meanTemperature(grid, c);
        processAirflow(grid, dt);
        processFire(grid, dt);
        processHeat(grid, dt);
        processDamage(grid, dt);
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
     *
     * @param edges if not null, receives one {@link AirflowEdge} per side of each opening
     */
    float[][] openings(ShipGridComponent grid, Array<AirflowEdge> edges) {
        int n = grid.compartments.size + 1;
        float[][] open = new float[n][n];
        Side[] sides = Side.values();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.holdsAir()) {
                    int a = node(t.compartment);
                    // A damaged deck plate vents straight to space
                    if (t.isFloor() && t.leakFactor() > 0f) {
                        add(open, a, 0, t.leakFactor());
                        if (edges != null) edges.add(new AirflowEdge(x, y, null, t.compartment, null, t.leakFactor()));
                    }
                    for (Side s : sides) {
                        Tile nb = grid.getTile(x + s.dx, y + s.dy);
                        // A docked port's opening joins another craft's room, not space
                        if (nb == null && grid.links.size > 0 && grid.linkAt(x, y, s) != null) continue;
                        int b;
                        if (nb == null || nb.isSpaceExposed()) b = 0;
                        else if (nb.holdsAir()) {
                            // Count each pair of air tiles once
                            if (s == Side.LEFT || s == Side.BOTTOM) continue;
                            b = node(nb.compartment);
                        } else continue; // a wall or a seal; wall leaks are counted below
                        EdgeWall e = grid.getEdge(x, y, s);
                        float w = e == null || !e.blocks() ? 1f : e.leakFactor();
                        if (w <= 0f || a == b) continue;
                        add(open, a, b, w);
                        if (edges != null) {
                            edges.add(new AirflowEdge(x, y, s, t.compartment, b == 0 ? null : nb.compartment, w));
                            if (b != 0) edges.add(new AirflowEdge(x + s.dx, y + s.dy, s.opposite(), nb.compartment, t.compartment, w));
                        }
                    }
                } else if (t.isIntactWall() && t.leakFactor() > 0f) {
                    // A leaking wall joins every distinct node it touches
                    int[] touched = new int[4];
                    int count = 0;
                    // Which node lies across each side of the wall (-1 for none)
                    int[] across = {-1, -1, -1, -1};
                    for (Side s : sides) {
                        if (grid.isEdgeBlocked(x, y, x + s.dx, y + s.dy)) continue;
                        Tile nb = grid.getTile(x + s.dx, y + s.dy);
                        int b;
                        if (nb == null || nb.isSpaceExposed()) b = 0;
                        else if (nb.holdsAir()) b = node(nb.compartment);
                        else continue;
                        across[s.ordinal()] = b;
                        boolean seen = false;
                        for (int i = 0; i < count; i++) seen |= touched[i] == b;
                        if (!seen) touched[count++] = b;
                    }
                    float f = t.leakFactor();
                    for (int i = 0; i < count; i++) {
                        for (int j = i + 1; j < count; j++) {
                            add(open, touched[i], touched[j], f);
                            if (edges != null) {
                                addWallLeakEdges(grid, edges, x, y, across, touched[i], touched[j], f);
                                addWallLeakEdges(grid, edges, x, y, across, touched[j], touched[i], f);
                            }
                        }
                    }
                }
            }
        }
        return open;
    }

    /**
     * Records a wall leak between nodes {@code from} and {@code to} on every air tile of
     * {@code from} that touches the wall, sharing the leak's width between them.
     */
    private static void addWallLeakEdges(ShipGridComponent grid, Array<AirflowEdge> edges, int x, int y,
                                         int[] across, int from, int to, float width) {
        if (from == 0) return; // space has no tiles to show it on
        int sidesOfFrom = 0;
        for (int v : across) if (v == from) sidesOfFrom++;
        for (Side s : Side.values()) {
            if (across[s.ordinal()] != from) continue;
            Tile air = grid.getTile(x + s.dx, y + s.dy);
            Compartment other = to == 0 ? null : grid.compartments.get(to - 1);
            edges.add(new AirflowEdge(x + s.dx, y + s.dy, s.opposite(), air.compartment, other, width / sidesOfFrom));
        }
    }

    private void processAirflow(ShipGridComponent grid, float dt) {
        Array<Compartment> comps = grid.compartments;
        int n = comps.size + 1;
        grid.airflow.clear();
        if (n == 1) return;
        Array<AirflowEdge> edges = new Array<>();
        float[][] open = openings(grid, edges);

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
        double[][] smokeDiffusion = diffuse(comps, open, dt);

        // Share each pair's net flow among the openings between them, by width
        for (AirflowEdge e : edges) {
            int a = node(e.room), b = node(e.other);
            float total = a < b ? open[a][b] : open[b][a];
            if (total <= 0f) continue;
            double share = e.width / total / dt;
            double out = flow[a][b] - flow[b][a];
            double smokeOut = (moles[a] > 0 ? flow[a][b] / moles[a] * smoke[a] : 0)
                    - (b > 0 && moles[b] > 0 ? flow[b][a] / moles[b] * smoke[b] : 0);
            if (a > 0 && b > 0) smokeOut += smokeDiffusion[a][b] - smokeDiffusion[b][a];
            e.flow = (float) (out * share);
            e.smokeFlow = (float) (smokeOut * share);
            grid.airflow.add(e);
        }

        // Clear out the last traces so a vented room reads as hard vacuum
        for (Compartment c : comps) {
            if (c.totalMoles() < 1e-3 * c.volume) c.scaleContents(0.0);
        }
    }

    /**
     * Mixes the gas of every pair of rooms joined by an opening. Each swaps the same
     * number of moles with the other, carrying its own mix, so pressures are left alone
     * and only composition evens out. Returns the smoke moved from node a to node b this
     * step (kg), for the particles.
     */
    private double[][] diffuse(Array<Compartment> comps, float[][] open, float dt) {
        int n = comps.size + 1;
        double[][] smokeMoved = new double[n][n];
        double[] o2 = new double[n], n2 = new double[n], co2 = new double[n], smoke = new double[n], moles = new double[n];
        for (int i = 1; i < n; i++) {
            Compartment c = comps.get(i - 1);
            o2[i] = c.oxygen; n2[i] = c.nitrogen; co2[i] = c.carbonDioxide; smoke[i] = c.smoke;
            moles[i] = c.totalMoles();
        }
        for (int i = 1; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (open[i][j] <= 0f || moles[i] <= 0 || moles[j] <= 0) continue;
                Compartment a = comps.get(i - 1), b = comps.get(j - 1);
                // Moles swapped each way: the volume exchanged at the rooms' mean molar density
                double density = 0.5 * (moles[i] / a.volume + moles[j] / b.volume);
                double swap = DIFFUSION_COEFFICIENT * open[i][j] * density * dt;
                // Never more than a quarter of the way to fully mixed in one step
                double limit = 0.25 * moles[i] * moles[j] / (moles[i] + moles[j]);
                swap = Math.min(swap, limit);
                double fa = swap / moles[i], fb = swap / moles[j];
                double dO2 = o2[i] * fa - o2[j] * fb;
                double dN2 = n2[i] * fa - n2[j] * fb;
                double dCO2 = co2[i] * fa - co2[j] * fb;
                double dSmoke = smoke[i] * fa - smoke[j] * fb;
                a.oxygen -= dO2; b.oxygen += dO2;
                a.nitrogen -= dN2; b.nitrogen += dN2;
                a.carbonDioxide -= dCO2; b.carbonDioxide += dCO2;
                a.smoke -= dSmoke; b.smoke += dSmoke;
                if (dSmoke > 0) smokeMoved[i][j] = dSmoke;
                else smokeMoved[j][i] = -dSmoke;
            }
        }
        return smokeMoved;
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
                float furnished = t.furnishingFuel(), fuel = t.fuel + furnished;
                t.fireIntensity = Math.min(t.fireIntensity, Math.min(1f, fuel / (FUEL_TAPER * area)));
                if (t.fireIntensity <= 0f) {
                    t.extinguish();
                    continue;
                }

                // Burn fuel and oxygen into heat, smoke and CO2: the covering and the furnishings
                // on the tile alike, each its share
                float wanted = BURN_RATE * area * t.fireIntensity * dt;
                float burned = Math.min(fuel, wanted);
                burned = (float) Math.min(burned, c.oxygen / OXYGEN_PER_FUEL);
                float share = fuel > 0f ? burned / fuel : 0f;
                t.fuel -= t.fuel * share;
                if (furnished > 0f) burnFurnishings(grid, t, share);
                c.oxygen = Math.max(0.0, c.oxygen - burned * OXYGEN_PER_FUEL);
                c.carbonDioxide += burned * CO2_PER_FUEL;
                c.smoke += burned * SMOKE_PER_FUEL;
                // Heat follows the share of the wanted burn that fuel and oxygen allowed
                t.temperature += FIRE_HEAT_OUTPUT * area * t.fireIntensity * dt * (burned / wanted) / TILE_HEAT_CAPACITY;
                if (t.fuel + t.furnishingFuel() <= BURNT_OUT * FUEL_TAPER * area) {
                    // The carpet is gone, and whatever stood on it; the steel floor stays
                    t.fuel = 0f;
                    if (t.covering.fuelPerArea > 0f) t.covering = FloorCovering.NONE;
                    while (t.furnishings.size > 0) burnAway(grid, t, t.furnishings.peek());
                    t.extinguish();
                    continue;
                }

                // Jump to neighbours connected by air; bigger fires and hotter targets catch sooner
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (!grid.canFlow(x, y, nx, ny)) continue;
                    Tile nb = grid.tiles[ny][nx];
                    if (catches(t, nb, 1f, dt)) ignitions.add(nb);
                }
            }
        }
        for (Tile t : ignitions) t.ignite();
    }

    /** Takes {@code share} of each furnishing's fuel on {@code t}; one burned past {@link Furnishing#BURNT} is gone. */
    private static void burnFurnishings(ShipGridComponent grid, Tile t, float share) {
        for (int i = t.furnishings.size - 1; i >= 0; i--) {
            Furnishing f = t.furnishings.get(i);
            f.fuel -= f.fuel * share;
            if (f.isBurnt()) burnAway(grid, t, f);
        }
    }

    private static void burnAway(ShipGridComponent grid, Tile t, Furnishing f) {
        t.furnishings.removeValue(f, true);
        f.fuel = 0f;
        f.users.clear();
        // Kept for the game to report; a grid nobody reads keeps only the latest few
        if (grid.burntFurnishings.size >= 16) grid.burntFurnishings.removeIndex(0);
        grid.burntFurnishings.add(f);
    }

    /**
     * Rolls whether the fire on {@code t} jumps to {@code nb} this tick, through an opening
     * {@code width} tiles wide: bigger fires and hotter targets catch sooner.
     */
    private boolean catches(Tile t, Tile nb, float width, float dt) {
        if (!t.isBurning() || nb.isBurning() || !nb.isFlammable() || oxygenFactor(nb.compartment) <= 0f) return false;
        float heat = (nb.temperature - Compartment.AMBIENT_TEMPERATURE)
                / (IGNITION_TEMPERATURE - Compartment.AMBIENT_TEMPERATURE);
        heat = Math.max(0f, Math.min(1f, heat));
        float chance = SPREAD_RATE * width * t.fireIntensity * (SPREAD_COLD_FACTOR + (1f - SPREAD_COLD_FACTOR) * heat);
        return random.nextFloat() < chance * dt;
    }

    // -----------------------------------------------------------------------
    // Damage
    // -----------------------------------------------------------------------

    /**
     * Heat weakens walls, doors and installed systems, and a large enough pressure
     * difference blows out a closed edge wall or door. Floors are left alone, so a fire
     * does not burn a hole to space through the deck it sits on.
     */
    private void processDamage(ShipGridComponent grid, float dt) {
        boolean structureChanged = false;
        burstThisTick = false;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (t.isIntactWall() && t.temperature > STRUCTURE_DAMAGE_TEMPERATURE) {
                    float loss = STRUCTURE_HEAT_DAMAGE * (t.temperature - STRUCTURE_DAMAGE_TEMPERATURE) / 100f * dt;
                    t.setIntegrity(t.getIntegrity() - loss);
                    structureChanged |= t.getIntegrity() <= 0f;
                }
            }
        }
        // Edge walls: bottom edges of each row, then left edges of each column
        for (int y = 0; y <= grid.height; y++)
            for (int x = 0; x < grid.width; x++)
                structureChanged |= damageEdge(grid, grid.edgeH[y][x], x, y - 1, x, y, dt);
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x <= grid.width; x++)
                structureChanged |= damageEdge(grid, grid.edgeV[y][x], x - 1, y, x, y, dt);

        for (MountFootprint m : grid.mounts) {
            if (m.installed == null) continue;
            SubsystemComponent sub = m.installed.getComponent(SubsystemComponent.class);
            if (sub == null || sub.isDestroyed()) continue;
            float hottest = 0f;
            for (int y = m.originY; y < m.originY + m.height(); y++)
                for (int x = m.originX; x < m.originX + m.width(); x++) {
                    Tile t = grid.getTile(x, y);
                    if (t != null) hottest = Math.max(hottest, t.temperature);
                }
            if (hottest <= SYSTEM_DAMAGE_TEMPERATURE) continue;
            boolean wasWorking = !sub.isDisabled();
            sub.health = Math.max(0f, sub.health - SYSTEM_HEAT_DAMAGE * (hottest - SYSTEM_DAMAGE_TEMPERATURE) * dt);
            structureChanged |= wasWorking && sub.isDisabled();
        }
        if (structureChanged) grid.gridDirty = true;
    }

    /**
     * Set once an edge has blown this tick. The pressures read below are from before the
     * blast, so only one edge may blow per tick; the next tick sees the air rushing out.
     */
    private boolean burstThisTick;

    /**
     * Heat and overpressure on one edge wall between tiles a and b (either may be out of
     * bounds). Returns true if the wall was destroyed.
     */
    private boolean damageEdge(ShipGridComponent grid, EdgeWall e, int ax, int ay, int bx, int by, float dt) {
        if (e == null || e.isDestroyed()) return false;
        Tile a = grid.getTile(ax, ay), b = grid.getTile(bx, by);
        float temperature = Math.max(a == null ? 0f : a.temperature, b == null ? 0f : b.temperature);
        if (temperature > STRUCTURE_DAMAGE_TEMPERATURE) {
            e.integrity -= STRUCTURE_HEAT_DAMAGE * (temperature - STRUCTURE_DAMAGE_TEMPERATURE) / 100f * dt;
        }
        if (e.blocks() && !burstThisTick) {
            double pa = sidePressure(a), pb = sidePressure(b);
            float limit = burstPressure(e.weightClass) * (e.hasBulkhead ? 1f : WALL_BURST_FACTOR);
            if (!Double.isNaN(pa) && !Double.isNaN(pb) && Math.abs(pa - pb) > limit) {
                e.integrity = 0f;
                burstThisTick = true;
            }
        }
        if (e.integrity <= 0f) {
            e.integrity = 0f;
            return true;
        }
        return false;
    }

    /** Pressure pushing on a wall from this tile, kPa: its room's, 0 for space, NaN for solid wall. */
    private static double sidePressure(Tile t) {
        if (t == null || t.isSpaceExposed()) return 0.0;
        if (t.compartment != null) return t.compartment.pressure();
        return Double.NaN;
    }

    public static float burstPressure(WeightClass w) {
        switch (w) {
            case LIGHT: return BURST_PRESSURE_LIGHT;
            case HEAVY: return BURST_PRESSURE_HEAVY;
            default: return BURST_PRESSURE_MEDIUM;
        }
    }

    // -----------------------------------------------------------------------
    // Heat
    // -----------------------------------------------------------------------

    private void processHeat(ShipGridComponent grid, float dt) {
        int w = grid.width, h = grid.height;
        float[] delta = new float[w * h];
        double ambient4 = Math.pow(Compartment.AMBIENT_TEMPERATURE, 4);
        double radiating = STEFAN_BOLTZMANN * RADIATING_AREA * grid.tileSize * grid.tileSize;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = grid.tiles[y][x];
                if (t.isVoid()) continue;
                delta[y * w + x] -= AMBIENT_LOSS * (t.temperature - Compartment.AMBIENT_TEMPERATURE);
                double t2 = (double) t.temperature * t.temperature;
                delta[y * w + x] -= (float) (radiating * (t2 * t2 - ambient4));
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
