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
 * Only power conduits carry power: {@link TileConduit}s across tiles and {@link EdgeConduit}s
 * along tile edges. Bare floor carries none. A tile conduit joins the tile conduits on the four
 * tiles beside it, through any thin wall or door between them, and the edge conduits on its own
 * tile's edges. An edge conduit joins the edge conduits that meet it at either end and the tile
 * conduits on both sides, through any wall it runs in.
 *
 * A system is on the network of every conduit that lies on one of its footprint tiles or along
 * one of their edges; an engine outside the hull, also those of its feed tiles inside
 * ({@link EngineLayout#feedTiles}). Power flows out from every REACTOR that is switched on and not
 * disabled by damage (see {@link SubsystemComponent#DISABLE_THRESHOLD}) into the networks it
 * touches, and a system is powered when it touches one of them. Cargo bays, tanks and escape
 * pods need no power ({@link SubsystemComponent.SubsystemType#needsPower}): they are operational
 * without it.
 *
 * A tile conduit stops conducting when it is cut (its own condition, apart from the tile's, reaches
 * 0) or shorted. An edge conduit stops when the wall it runs in is destroyed (the thin wall on its
 * edge, or a full-tile wall beside it).
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
 *
 * A system or tile conduit a short circuit faulted ({@link SubsystemComponent#shorted},
 * {@link TileConduit#shorted}) is left out the same way until the crew reset it, or its breaker recloses
 * after {@link DamageStates#AUTO_RESET}; this system counts that wait (see {@link DamageStates}).
 *
 * A system an ion hit stunned ({@link SubsystemComponent#stunned}) is not operational until the
 * stun runs out, and a stunned reactor feeds nothing; this system runs the stuns down and
 * reroutes as each one ends.
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
            if (runDownStuns(registryMapper.get(e), deltaTime)) grid.gridDirty = true;
            if (DamageStates.runDownShorts(grid, registryMapper.get(e), deltaTime)) grid.gridDirty = true;
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
            runDownStuns(registryMapper.get(e), (float) seconds);
            DamageStates.runDownShorts(gridMapper.get(e), registryMapper.get(e), (float) seconds);
            route(e);
            for (PowerCircuit c : gridMapper.get(e).circuits) c.recharge(seconds);
        }
    }

    /** Counts down every stunned system's stun; true if one ran out, and the ship must reroute. */
    private boolean runDownStuns(ShipSubsystemRegistryComponent registry, float seconds) {
        boolean ended = false;
        for (Entity s : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(s);
            if (sub == null || sub.stunned <= 0f) continue;
            sub.stunned = Math.max(0f, sub.stunned - seconds);
            if (sub.stunned == 0f) ended = true;
        }
        return ended;
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

        // Step 1: split every live conduit, across tiles and along edges, into connected groups
        int[] nodeGroup = new int[nodeCount(grid)];
        int groups = labelGroups(grid, nodeGroup);

        // Step 2: each working reactor joins the groups it touches into one circuit
        int[] parent = new int[groups];
        for (int i = 0; i < groups; i++) parent[i] = i;
        Array<SubsystemComponent> reactors = new Array<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            // Seed from the reactor's own state, never from last tick's isOperational, so a
            // reactor that was off can come back on
            if (sub == null || sub.type != SubsystemComponent.SubsystemType.REACTOR
                    || !sub.switchedOn || sub.isDisabled() || sub.isStunned() || sub.shorted) continue;
            reactors.add(sub);
            int first = -1;
            touching(grid, sub, touched);
            for (int i = 0; i < touched.size; i++) {
                int g = nodeGroup[touched.get(i)];
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
            touching(grid, r, touched);
            for (int i = 0; i < touched.size && c == null; i++) {
                int g = nodeGroup[touched.get(i)];
                if (g < 0) continue;
                int root = find(parent, g);
                if (byRoot[root] == null) byRoot[root] = newCircuit(grid, previous.get(r));
                c = byRoot[root];
            }
            // A reactor no live conduit reaches still powers just itself
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

        // Step 4: a conduit is powered when its group belongs to a circuit
        PowerCircuit[] nodeCircuit = new PowerCircuit[nodeGroup.length];
        for (int n = 0; n < nodeGroup.length; n++) {
            nodeCircuit[n] = nodeGroup[n] < 0 ? null : byRoot[find(parent, nodeGroup[n])];
        }
        int tiles = GridNodes.tiles(grid);
        for (int n = 0; n < tiles; n++) {
            TileConduit c = grid.tileConduits[n / grid.width][n % grid.width];
            if (c != null) c.isPowered = nodeCircuit[n] != null;
        }
        for (int n = tiles; n < nodeGroup.length; n++) {
            EdgeConduit c = conduitOf(grid, n);
            if (c != null) c.isPowered = nodeCircuit[n] != null;
        }

        // Step 5: a subsystem is on the circuit of the first powered conduit it touches; a reactor on its own
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = subsystemMapper.get(e);
            if (sub == null) continue;
            sub.circuit = reactorCircuit.get(sub);
            if (sub.circuit == null) {
                touching(grid, sub, touched);
                for (int i = 0; i < touched.size && sub.circuit == null; i++) sub.circuit = nodeCircuit[touched.get(i)];
            }
            sub.powered = sub.circuit != null;
            sub.isOperational = sub.switchedOn && (sub.powered || !sub.type.needsPower()) && !sub.isDisabled()
                    && !sub.isStunned() && !sub.shorted;
        }

        // Pipes follow the same structural changes
        PipeRouting.route(grid, registry);
    }

    /** Feed tiles of the engine being wired, reused. */
    private final IntArray feed = new IntArray();
    /** Nodes the system being wired touches, reused. */
    private final IntArray touched = new IntArray();

    /**
     * The nodes a system draws power through: its footprint tiles and their edges, and for an
     * engine its feed tiles and their edges too.
     */
    private void touching(ShipGridComponent grid, SubsystemComponent sub, IntArray out) {
        out.clear();
        MountFootprint f = sub.footprint;
        for (int y = f.originY; y < f.originY + f.height(); y++)
            for (int x = f.originX; x < f.originX + f.width(); x++) addTile(grid, x, y, out);
        if (sub.type == SubsystemComponent.SubsystemType.ENGINE) {
            feed.clear();
            EngineLayout.feedTiles(grid, f, feed);
            for (int i = 0; i < feed.size; i++) addTile(grid, feed.get(i) % grid.width, feed.get(i) / grid.width, out);
        }
    }

    private static void addTile(ShipGridComponent grid, int x, int y, IntArray out) {
        if (!grid.inBounds(x, y)) return;
        out.add(GridNodes.tileNode(grid, x, y));
        for (Side side : Side.values()) out.add(GridNodes.edgeNode(grid, x, y, side));
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

    /** What tile (x, y)'s conduit passes power to, before checking that they conduct: through any thin wall. */
    private static void tileNeighbors(ShipGridComponent grid, int x, int y, IntArray out) {
        for (Side side : Side.values()) {
            out.add(GridNodes.edgeNode(grid, x, y, side));
            int nx = x + side.dx, ny = y + side.dy;
            if (grid.inBounds(nx, ny)) out.add(GridNodes.tileNode(grid, nx, ny));
        }
    }

    private static boolean conducts(ShipGridComponent grid, int n) {
        if (n < GridNodes.tiles(grid)) {
            TileConduit c = grid.tileConduits[n / grid.width][n % grid.width];
            return c != null && c.isLive();
        }
        return conduitOf(grid, n) != null && !GridNodes.edgeCut(grid, n);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i] = parent[parent[i]];
        return i;
    }
}
