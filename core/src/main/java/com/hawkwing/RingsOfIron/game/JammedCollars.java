package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Docking collars jammed by hits ({@link CollarWear}): the port can't arm or latch until its crew
 * free it from outside, or a yard refits the collar. Each crewed craft's jammed ports go on its task
 * board as work outside ({@link OutsideTask}, {@link CrewTask.Kind#FREE_COLLAR}): out on EVA to the
 * ring of hull just outside the port's door, and {@link CrewDef#collarTime} of work at the collar
 * there. Not at battle stations, when nobody goes out. The player hears when one of their ports
 * jams, and when it is freed.
 */
public final class JammedCollars {
    private static final Family SHIPS = Family.all(DockingPortsComponent.class, ShipGridComponent.class, ShipTaskBoardComponent.class).get();

    private final GameWorld world;
    private final ImmutableArray<Entity> ships;
    /** The job of each port jammed, while it is. */
    private final Map<DockingPort, Free> jobs = new IdentityHashMap<>();

    public JammedCollars(GameWorld world) {
        this.world = world;
        ships = world.engine.getEcsEngine().getEntitiesFor(SHIPS);
    }

    /** Once a frame: each jammed port's job on its craft's board, and the jobs of ports freed or gone dropped. */
    public void update() {
        for (Free f : jobs.values()) f.seen = false;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            DockingPortsComponent ports = ship.getComponent(DockingPortsComponent.class);
            ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
            ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
            for (int k = 0; k < ports.ports.length; k++) {
                DockingPort p = ports.ports[k];
                if (!p.jammed || p.gridX < 0) continue;
                Tile origin = grid.getTile(p.gridX, p.gridY);
                MountFootprint m = origin == null ? null : origin.mount;
                if (m == null || m.kind != MountFootprint.MountKind.PORT) continue;
                Free job = jobs.get(p);
                if (job == null) {
                    job = new Free(ship, k, p, m);
                    jobs.put(p, job);
                    if (ship == world.getPlayer()) {
                        world.notices.post("Port " + (k + 1) + "'s docking collar is jammed: it won't arm till the crew free it on EVA",
                                NoticeLog.Kind.WARNING);
                    }
                }
                job.seen = true;
                if (!board.battleStations) board.post(new OutsideTask(CrewTask.Kind.FREE_COLLAR, m.originX, m.originY, job));
            }
        }
        for (Iterator<Free> it = jobs.values().iterator(); it.hasNext(); ) {
            if (!it.next().seen) it.remove();
        }
    }

    /** The job on {@code port} while it is jammed, or null: for tests. */
    OutsideTask.Job job(DockingPort port) {
        return jobs.get(port);
    }

    /** Freeing one jammed collar. */
    private final class Free implements OutsideTask.Job {
        final Entity ship;
        final int index;
        final DockingPort port;
        final MountFootprint mount;
        boolean seen;

        Free(Entity ship, int index, DockingPort port, MountFootprint mount) {
            this.ship = ship;
            this.index = index;
            this.port = port;
            this.mount = mount;
        }

        @Override
        public boolean isOpen() {
            return port.jammed;
        }

        @Override
        public boolean workTile(HullSurface hull, int[] out) {
            EvaSystem.doorRing(mount, out);
            return hull.isRing(out[0], out[1]);
        }

        @Override
        public float seconds(CrewDef def) {
            return def.collarTime;
        }

        @Override
        public void done(Entity worker) {
            port.jammed = false;
            if (ship != world.getPlayer()) return;
            CrewMemberComponent c = worker == null ? null : world.crew.crew(worker);
            world.notices.post((c == null ? "The crew have" : c.name + " has") + " freed port " + (index + 1) + "'s jammed docking collar",
                    NoticeLog.Kind.INFO);
        }

        @Override
        public String doing() {
            return "freeing a jammed docking collar";
        }

        @Override
        public String where() {
            return "the jammed docking collar";
        }
    }
}
