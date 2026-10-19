package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Works out which tanks each ship system can draw from, through the ship's {@link Pipe}s.
 *
 * <ul>
 *   <li>A pipe across a tile's middle joins the pipes across the four tiles beside it (passing
 *       through any thin wall between them) and the pipes on its own tile's four edges.</li>
 *   <li>A pipe along an edge joins the edge pipes that meet it at either end, and the pipes across
 *       the tiles on both sides, whatever wall it runs in.</li>
 *   <li>A pipe across a tile is cut when the tile is destroyed (integrity 0); one along an edge
 *       when the wall it runs in is destroyed, as with edge conduits.</li>
 *   <li>A system is on every network that runs across one of its footprint tiles or along one of
 *       their edges. It can draw from every tank on those networks
 *       ({@link SubsystemComponent#pipedTanks}).</li>
 * </ul>
 * {@link PowerRoutingSystem} runs this whenever it reroutes power, so pipes follow damage just as
 * quickly.
 */
public final class PipeRouting {
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    private PipeRouting() {
    }

    /** Rebuilds the grid's pipe networks and every system's piped tanks. */
    public static void route(ShipGridComponent grid, ShipSubsystemRegistryComponent registry) {
        grid.pipeNetworks.clear();
        int count = GridNodes.count(grid);
        PipeNetwork[] network = new PipeNetwork[count];
        IntArray queue = new IntArray();
        IntArray next = new IntArray();
        for (int start = 0; start < count; start++) {
            Pipe first = pipeAt(grid, start);
            if (first != null) first.network = null;
        }
        for (int start = 0; start < count; start++) {
            if (network[start] != null || !carries(grid, start)) continue;
            PipeNetwork net = new PipeNetwork();
            grid.pipeNetworks.add(net);
            network[start] = net;
            queue.clear();
            queue.add(start);
            for (int head = 0; head < queue.size; head++) {
                int n = queue.get(head);
                pipeAt(grid, n).network = net;
                next.clear();
                neighbors(grid, n, next);
                for (int i = 0; i < next.size; i++) {
                    int m = next.get(i);
                    if (network[m] != null || !carries(grid, m)) continue;
                    network[m] = net;
                    queue.add(m);
                }
            }
        }

        // Each system joins the networks its footprint touches, then reaches every tank on them
        Array<SubsystemComponent> systems = new Array<>();
        Array<Array<PipeNetwork>> touched = new Array<>();
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = SUBSYSTEMS.get(e);
            if (sub == null) continue;
            Array<PipeNetwork> nets = new Array<>();
            touching(grid, sub.footprint, network, nets);
            for (PipeNetwork net : nets) {
                if (sub.type == SubsystemComponent.SubsystemType.TANK) net.tanks.add(sub);
                else net.systems.add(sub);
            }
            systems.add(sub);
            touched.add(nets);
        }
        for (int i = 0; i < systems.size; i++) {
            SubsystemComponent sub = systems.get(i);
            sub.pipedTanks.clear();
            for (PipeNetwork net : touched.get(i)) {
                for (SubsystemComponent t : net.tanks) {
                    if (t != sub && !sub.pipedTanks.contains(t, true)) sub.pipedTanks.add(t);
                }
            }
        }
    }

    /** The distinct networks that run across or along the footprint's tiles. */
    private static void touching(ShipGridComponent grid, MountFootprint f, PipeNetwork[] network, Array<PipeNetwork> out) {
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (!grid.inBounds(x, y)) continue;
                add(out, network[GridNodes.tileNode(grid, x, y)]);
                for (Side side : Side.values()) add(out, network[GridNodes.edgeNode(grid, x, y, side)]);
            }
        }
    }

    private static void add(Array<PipeNetwork> out, PipeNetwork net) {
        if (net != null && !out.contains(net, true)) out.add(net);
    }

    /** What node {@code n} could join, before checking that they carry. */
    private static void neighbors(ShipGridComponent grid, int n, IntArray out) {
        if (n < GridNodes.tiles(grid)) {
            int x = n % grid.width, y = n / grid.width;
            for (Side side : Side.values()) {
                out.add(GridNodes.edgeNode(grid, x, y, side));
                int nx = x + side.dx, ny = y + side.dy;
                if (grid.inBounds(nx, ny)) out.add(GridNodes.tileNode(grid, nx, ny));
            }
        } else {
            GridNodes.edgeNeighbors(grid, n, out);
        }
    }

    /** The pipe at node {@code n}, or null. */
    static Pipe pipeAt(ShipGridComponent grid, int n) {
        if (n < GridNodes.tiles(grid)) return grid.pipes[n / grid.width][n % grid.width];
        int x = GridNodes.edgeX(grid, n), y = GridNodes.edgeY(grid, n);
        return GridNodes.isHorizontal(grid, n) ? grid.pipeH[y][x] : grid.pipeV[y][x];
    }

    /** True if node {@code n} has a pipe that is not cut. */
    private static boolean carries(ShipGridComponent grid, int n) {
        if (pipeAt(grid, n) == null) return false;
        if (n < GridNodes.tiles(grid)) return grid.getTile(n % grid.width, n / grid.width).getIntegrity() > 0f;
        return !GridNodes.edgeCut(grid, n);
    }
}
