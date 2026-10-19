package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

/**
 * Calculates power connectivity across the ship's interior grid, and the energy each
 * part of the grid can supply.
 *
 * Power floods outward from every footprint tile of each REACTOR that is switched on and
 * not disabled by damage (see {@link SubsystemComponent#DISABLE_THRESHOLD}), across
 * connected POWER_CONDUIT and FLOOR tiles. A subsystem is powered when any tile of its
 * footprint is reachable.
 *
 * Power does not pass through intact full-tile walls, through closed edge walls (except
 * where a conduit runs on both sides of the wall), or through tiles at integrity 0:
 * a destroyed conduit stops conducting.
 *
 * An {@link EdgeConduit} on a tile edge carries power along the edge, through any wall it runs
 * in: to the edge conduits that meet it at either end, and to the conducting tiles on both sides.
 * It stops conducting when the wall it runs in is destroyed (the thin wall on its edge, or a
 * full-tile wall beside it).
 *
 * Each connected part of the grid with a working reactor in it is a {@link PowerCircuit}
 * (listed on {@link ShipGridComponent#circuits}, and on each powered system's
 * {@link SubsystemComponent#circuit}). A reactor adds {@link #reactorOutput} watts to its
 * circuit, less as it is damaged, and the circuit stores {@link #STORE_SECONDS} of that.
 * Weapons and other burst loads draw from the store; see {@link SubsystemComponent#tryDrawPower}.
 *
 * Each reroute also reroutes the ship's pipes ({@link PipeRouting}).
 *
 * Stores recharge every {@link #CHARGE_TICK}. Routing runs once per second to reflect
 * structural changes, or on the next frame when {@link ShipGridComponent#gridDirty} is set.
 */
public class PowerRoutingSystem extends FixedLogicSystem {

    /** Seconds between reroutes when nothing has changed. */
    public static final float ROUTING_TICK = 1.0f;
    /** Seconds between store recharges. */
    public static final float CHARGE_TICK = 0.25f;
    /** Seconds of its reactors' output a circuit's store holds. */
    public static final float STORE_SECONDS = 2f;
    /** Output of an undamaged reactor in each mount size, watts. */
    public static final double REACTOR_OUTPUT_S = 1e6, REACTOR_OUTPUT_M = 3e6, REACTOR_OUTPUT_L = 1e7, REACTOR_OUTPUT_XL = 3e7;
    /** Interior systems run after exterior physics; power first, so others see this tick's state. */
    public static final int DEFAULT_PRIORITY = 30;
    private static final Family FAMILY = Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class).get();

    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<ShipSubsystemRegistryComponent> registryMapper =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper =
            ComponentMapper.getFor(SubsystemComponent.class);

    private ImmutableArray<Entity> entities;
    private float sinceRoute = ROUTING_TICK;

    public PowerRoutingSystem() {
        super(DEFAULT_PRIORITY, CHARGE_TICK);
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
        sinceRoute += deltaTime;
        boolean reroute = sinceRoute >= ROUTING_TICK - 1e-4f;
        if (reroute) sinceRoute = 0f;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            ShipGridComponent grid = gridMapper.get(e);
            if (reroute || grid.gridDirty) route(e);
            for (PowerCircuit c : grid.circuits) c.recharge(deltaTime);
        }
    }

    /** Runs every frame: grids that damage code marked dirty reroute at once. */
    @Override
    protected void updateInterpolated(float alpha) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (gridMapper.get(e).gridDirty) route(e);
        }
    }

    /** Rails warp: reroute once, and every store ends the jump full. */
    @Override
    protected void catchUp(long ticks, double seconds) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            route(e);
            for (PowerCircuit c : gridMapper.get(e).circuits) c.recharge(seconds);
        }
    }

    /** Watts a reactor produces: its rated output (by component, else by mount size), scaled down by its damage. */
    public static double reactorOutput(SubsystemComponent reactor) {
        double rated = reactor.ratedOutput > 0 ? reactor.ratedOutput : standardOutput(reactor.footprint.size);
        return rated * Math.max(0f, Math.min(1f, reactor.health));
    }

    /** Watts an undamaged standard reactor in a mount of this size produces. */
    public static double standardOutput(MountFootprint.MountSize size) {
        switch (size) {
            case S: return REACTOR_OUTPUT_S;
            case M: return REACTOR_OUTPUT_M;
            case L: return REACTOR_OUTPUT_L;
            default: return REACTOR_OUTPUT_XL;
        }
    }

    /** Recalculates which tiles and systems of one ship are powered, and its circuits. */
    public void route(Entity entity) {
        ShipGridComponent grid = gridMapper.get(entity);
        grid.gridDirty = false;
        ShipSubsystemRegistryComponent registry = registryMapper.get(entity);

        // Energy in the old stores goes back to the reactors that fed them, by output
        IdentityMap<SubsystemComponent, Double> carried = new IdentityMap<>();
        IdentityMap<SubsystemComponent, PowerCircuit> previous = new IdentityMap<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            if (sub == null || sub.type != SubsystemComponent.SubsystemType.REACTOR || sub.circuit == null) continue;
            PowerCircuit old = sub.circuit;
            double share = old.output > 0 ? reactorOutput(sub) / old.output : 0;
            carried.put(sub, old.stored * Math.min(1.0, share));
            previous.put(sub, old);
        }

        // Step 1: split every conducting tile and edge conduit into connected groups
        int[] nodeGroup = new int[nodeCount(grid)];
        int groups = labelGroups(grid, nodeGroup);
        int[][] group = new int[grid.height][grid.width];
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) group[y][x] = nodeGroup[y * grid.width + x];

        // Step 2: each working reactor joins the groups its footprint touches into one circuit
        int[] parent = new int[groups];
        for (int i = 0; i < groups; i++) parent[i] = i;
        Array<SubsystemComponent> reactors = new Array<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            // Seed from the reactor's own state, never from last tick's isOperational, so a
            // reactor that was off can come back on
            if (sub == null || sub.type != SubsystemComponent.SubsystemType.REACTOR
                    || !sub.switchedOn || sub.isDisabled()) continue;
            reactors.add(sub);
            int first = -1;
            for (int[] t : footprintTiles(grid, sub.footprint)) {
                int g = group[t[1]][t[0]];
                if (g < 0) continue;
                if (first < 0) first = find(parent, g);
                else parent[find(parent, g)] = first;
            }
        }

        // Step 3: one circuit per joined set of groups with a reactor in it
        IdentityMap<SubsystemComponent, PowerCircuit> reactorCircuit = new IdentityMap<>();
        PowerCircuit[] byRoot = new PowerCircuit[groups];
        grid.circuits.clear();
        reused.clear();
        for (SubsystemComponent r : reactors) {
            PowerCircuit c = null;
            for (int[] t : footprintTiles(grid, r.footprint)) {
                int g = group[t[1]][t[0]];
                if (g >= 0) {
                    int root = find(parent, g);
                    if (byRoot[root] == null) byRoot[root] = newCircuit(grid, previous.get(r));
                    c = byRoot[root];
                    break;
                }
            }
            // A reactor whose own tiles are all wrecked still powers just itself
            if (c == null) c = newCircuit(grid, previous.get(r));
            c.output += reactorOutput(r);
            Double kept = carried.get(r);
            c.stored += kept != null ? kept : reactorOutput(r) * STORE_SECONDS;
            reactorCircuit.put(r, c);
        }
        for (PowerCircuit c : grid.circuits) {
            c.capacity = c.output * STORE_SECONDS;
            c.stored = Math.min(c.stored, c.capacity);
        }

        // Step 4: tiles are powered when their group belongs to a circuit
        PowerCircuit[][] tileCircuit = new PowerCircuit[grid.height][grid.width];
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                int g = group[y][x];
                tileCircuit[y][x] = g < 0 ? null : byRoot[find(parent, g)];
                t.isPowered = tileCircuit[y][x] != null;
            }
        }
        for (int n = grid.width * grid.height; n < nodeGroup.length; n++) {
            EdgeConduit c = conduitOf(grid, n);
            if (c != null) c.isPowered = nodeGroup[n] >= 0 && byRoot[find(parent, nodeGroup[n])] != null;
        }
        // A reactor always powers its own footprint
        for (SubsystemComponent r : reactors) {
            for (int[] t : footprintTiles(grid, r.footprint)) {
                grid.getTile(t[0], t[1]).isPowered = true;
                if (tileCircuit[t[1]][t[0]] == null) tileCircuit[t[1]][t[0]] = reactorCircuit.get(r);
            }
        }

        // Step 5: a subsystem is on the circuit of its first powered footprint tile
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            if (sub == null) continue;
            sub.circuit = null;
            for (int[] t : footprintTiles(grid, sub.footprint)) {
                if (tileCircuit[t[1]][t[0]] != null) {
                    sub.circuit = tileCircuit[t[1]][t[0]];
                    break;
                }
            }
            sub.powered = sub.circuit != null;
            sub.isOperational = sub.switchedOn && sub.powered && !sub.isDisabled();
        }

        // Pipes follow the same structural changes
        PipeRouting.route(grid, registry);
    }

    /** Old circuits already taken by a new one during this reroute. */
    private final Array<PowerCircuit> reused = new Array<>();

    /**
     * A circuit for this reroute: the reactor's old one when no other new circuit has taken
     * it yet, so code holding a circuit keeps a live one while the layout stays the same.
     */
    private PowerCircuit newCircuit(ShipGridComponent grid, PowerCircuit old) {
        PowerCircuit c;
        if (old != null && !reused.contains(old, true)) {
            reused.add(old);
            c = old;
        } else {
            c = new PowerCircuit();
        }
        c.output = 0;
        c.stored = 0;
        c.drawnThisTick = 0;
        grid.circuits.add(c);
        return c;
    }

    // Nodes of the conducting graph are numbered by GridNodes: tiles, then edges

    private static int nodeCount(ShipGridComponent grid) {
        return GridNodes.count(grid);
    }

    /** The edge conduit node {@code n} stands for, or null for a tile or an edge without one. */
    private static EdgeConduit conduitOf(ShipGridComponent grid, int n) {
        if (n < GridNodes.tiles(grid)) return null;
        int x = GridNodes.edgeX(grid, n), y = GridNodes.edgeY(grid, n);
        return GridNodes.isHorizontal(grid, n) ? grid.conduitH[y][x] : grid.conduitV[y][x];
    }

    /**
     * Labels connected conducting tiles and edge conduits with a group number, -1 elsewhere.
     * Returns the number of groups.
     */
    private static int labelGroups(ShipGridComponent grid, int[] group) {
        java.util.Arrays.fill(group, -1);
        int groups = 0;
        int tiles = GridNodes.tiles(grid);
        IntArray queue = new IntArray();
        IntArray next = new IntArray();
        for (int start = 0; start < group.length; start++) {
            if (group[start] >= 0 || !conducts(grid, start)) continue;
            int id = groups++;
            group[start] = id;
            queue.clear();
            queue.add(start);
            for (int head = 0; head < queue.size; head++) {
                int n = queue.get(head);
                next.clear();
                if (n < tiles) tileNeighbors(grid, n % grid.width, n / grid.width, next);
                else GridNodes.edgeNeighbors(grid, n, next);
                for (int i = 0; i < next.size; i++) {
                    int m = next.get(i);
                    if (group[m] >= 0 || !conducts(grid, m)) continue;
                    group[m] = id;
                    queue.add(m);
                }
            }
        }
        return groups;
    }

    /** What tile (x, y) passes power to, before checking that they conduct. */
    private static void tileNeighbors(ShipGridComponent grid, int x, int y, IntArray out) {
        Tile current = grid.getTile(x, y);
        for (Side side : Side.values()) {
            out.add(GridNodes.edgeNode(grid, x, y, side));
            int nx = x + side.dx, ny = y + side.dy;
            if (!grid.inBounds(nx, ny)) continue;
            // A conduit passes through a thin wall; nothing else does
            if (grid.isEdgeBlocked(x, y, nx, ny)
                    && !(current.isPowerConduit() && grid.getTile(nx, ny).isPowerConduit())) continue;
            out.add(ny * grid.width + nx);
        }
    }

    private static boolean conducts(ShipGridComponent grid, int n) {
        if (n < GridNodes.tiles(grid)) return conducts(grid.getTile(n % grid.width, n / grid.width));
        return conduitOf(grid, n) != null && !GridNodes.edgeCut(grid, n);
    }

    private static Array<int[]> footprintTiles(ShipGridComponent grid, MountFootprint f) {
        Array<int[]> out = new Array<>();
        for (int y = f.originY; y < f.originY + f.height(); y++)
            for (int x = f.originX; x < f.originX + f.width(); x++)
                if (grid.inBounds(x, y)) out.add(new int[]{x, y});
        return out;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i] = parent[parent[i]];
        return i;
    }

    /** Floors and conduits carry power until they are destroyed; walls never do. */
    private static boolean conducts(Tile t) {
        return t.isFloor() && !t.isWall() && t.getIntegrity() > 0f;
    }
}
