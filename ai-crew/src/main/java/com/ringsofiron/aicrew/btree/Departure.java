package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Setting out to cross to another craft with no dock between, a step at a time: into an EVA suit
 * (aboard their own craft; the way home they go in the one they have on), to the airlock of the
 * craft they are aboard nearest the other, and out ({@link EvaSystem#beginCrossing}), to go in
 * there by its {@link #entryPort}. A boarder ({@code breaking}) forces its door from outside.
 */
public final class Departure {
    private MountFootprint port;
    private float planned, stuck;
    private final int[] tile = new int[2];

    /** Forgets the airlock picked, and lets it go if it was asked to cycle for them. */
    public void reset(CrewAgent a) {
        if (a != null && port != null && port.airlock != null && EvaSystem.of(a.entity) == null) port.airlock.release(a.entity);
        port = null;
        stuck = 0f;
    }

    /**
     * One step toward going out to cross to {@code to}: RUNNING while on the way (and once out,
     * when {@link Spacewalk} has them), FAILED if it can't be done: no EVA suit to be had, no
     * airlock out, no way in there, or {@code to} beyond {@link EvaSystem#CROSS_RANGE}.
     */
    public Status step(CrewAgent a, Entity to, boolean breaking) {
        EvaSystem system = a.service.eva;
        ShipGridComponent grid = a.grid(), there = a.service.grid(to);
        if (system == null || grid == null || there == null || EvaSystem.of(a.entity) != null) return Status.FAILED;
        float[] sep = new float[2];
        if (!system.separation(a.pos.ship, to, sep) || sep[0] > EvaSystem.CROSS_RANGE + 0.5f * (grid.width + there.width) * grid.tileSize) {
            return Status.FAILED;
        }
        boolean home = a.pos.ship == a.service.homeOf(a.entity);
        if (home ? !EvaTasks.fitToGoOut(a.crew) : !EvaTasks.inEvaSuit(a, 1f)) {
            // Into an EVA suit fit to go out in at home, out of any other first; away, they go in the
            // one they have on
            if (!home) return Status.FAILED;
            Status s = a.crew.suited ? SuitErrand.stow(a) : SuitErrand.suitUpEva(a, EvaTasks::fitToGoOut);
            if (s == Status.FAILED) return Status.FAILED;
            if (s == Status.RUNNING && a.crew.suiting > 0f) a.crew.activity = "getting into an EVA suit to cross to " + EvaTasks.craft(a, to);
            return Status.RUNNING;
        }
        MountFootprint in = EvaTasks.entryPort(a, system, there, to, a.pos.ship);
        if (in == null) return Status.FAILED;
        if (port == null || a.time - planned > EvaTasks.REPLAN || !Airlock.works(grid, port)) {
            if (port != null && port.airlock != null) port.airlock.release(a.entity);
            port = outPort(a, system, grid, to);
            planned = a.time;
            if (port == null) return Status.FAILED;
        }
        CrewAgent.Travel go = EvaTasks.toChamber(a, grid, port, tile);
        if (go == CrewAgent.Travel.UNREACHABLE) return (stuck += a.dt) > EvaTasks.STUCK ? Status.FAILED : Status.RUNNING;
        stuck = 0f;
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        a.crew.activity = "cycling out through the airlock";
        if (!system.beginCrossing(a.entity, port, to, in, breaking)) a.crew.activity = "waiting for the airlock";
        return Status.RUNNING;
    }

    /**
     * The airlock of the craft {@code a} is aboard to go out by for {@code to}: one that can cycle
     * and opens onto the hull, by the walk to it plus the crawl from its door to the side facing
     * {@code to}. Null if none.
     */
    private static MountFootprint outPort(CrewAgent a, EvaSystem system, ShipGridComponent grid, Entity to) {
        HullSurface hull = system.hull(grid);
        int[] launch = new int[2], chamber = new int[2], door = new int[2];
        if (!system.launchTile(a.entity, to, launch)) return null;
        Array<int[]> path = new Array<>();
        MountFootprint best = null;
        float bestCost = Float.MAX_VALUE;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint port = grid.mounts.get(i);
            if (port.kind != MountFootprint.MountKind.PORT || !Airlock.works(grid, port)) continue;
            EvaSystem.doorRing(port, door);
            if (!hull.path(door[0], door[1], launch[0], launch[1], path)) continue;
            DockingPortLayout.chamberTile(port, chamber);
            int x = a.pos.tileX(), y = a.pos.tileY(), walk;
            if (x == chamber[0] && y == chamber[1]) walk = 0;
            else if (a.findPath(x, y, chamber[0], chamber[1], a.pathScratch)) walk = a.pathScratch.size;
            else if (port.airlock != null && !port.airlock.isIn()) walk = Math.abs(chamber[0] - x) + Math.abs(chamber[1] - y);
            else continue;
            float cost = walk + path.size * EvaTasks.CRAWL_COST;
            if (cost < bestCost) {
                bestCost = cost;
                best = port;
            }
        }
        return best;
    }
}
