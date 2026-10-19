package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.ForceMethod;

import java.util.function.DoubleSupplier;

/**
 * Crew forcing the shut port door of a craft they are docked with: standing in their own port's
 * opening, facing the other craft's door across the collars, they pry, cut or hack at it
 * ({@link ForceMethod}) for as long as the method takes. Stepping away, being knocked out or the
 * door opening some other way stops the work. Once through, {@link DockingSystem#forceDoor} opens
 * the door and reports the hostile act.
 */
public class DoorForcing extends EntitySystem {

    /** One crew member at work on one door. */
    public static final class Job {
        public final Entity crew;
        /** The craft whose door it is, and its port. */
        public final Entity craft;
        public final int port;
        public final ForceMethod method;
        /** Sim seconds of work done. */
        public float done;
        final Entity ship;
        final int x, y;

        Job(Entity crew, Entity ship, int x, int y, Entity craft, int port, ForceMethod method) {
            this.crew = crew;
            this.ship = ship;
            this.x = x;
            this.y = y;
            this.craft = craft;
            this.port = port;
            this.method = method;
        }

        /** Share of the work done, 0 to 1. */
        public float progress() {
            return Math.min(1f, done / method.seconds);
        }
    }

    /** A shut door across the dock from where a crew member stands. */
    public static final class Target {
        public Entity craft;
        public int port = -1;
        public ForceMethod.DoorState state = ForceMethod.DoorState.NONE;
    }

    private final DockingSystem docking;
    private final CrewService crew;
    private final DoubleSupplier simSeconds;
    private final Array<Job> jobs = new Array<>();
    private double lastTime = Double.NaN;
    /** What the last finished or stopped job came to, for the screen. */
    public String lastOutcome = "";

    public DoorForcing(DockingSystem docking, CrewService crew, DoubleSupplier simSeconds) {
        this.docking = docking;
        this.crew = crew;
        this.simSeconds = simSeconds;
    }

    /**
     * The door across the dock from where {@code member} stands, or one with a null craft when
     * they are not standing in a docked port's opening.
     */
    public Target target(Entity member, Target out) {
        out.craft = null;
        out.port = -1;
        out.state = ForceMethod.DoorState.NONE;
        GridPositionComponent pos = crew.position(member);
        ShipGridComponent grid = pos == null ? null : crew.grid(pos.ship);
        if (grid == null || pos.walkway != null) return out;
        int x = pos.tileX(), y = pos.tileY();
        for (GridLink l : grid.links) {
            if (l.x != x || l.y != y) continue;
            int port = portAt(l.otherShip, l.ox, l.oy);
            if (port < 0) continue;
            out.craft = l.otherShip;
            out.port = port;
            out.state = docking.doorState(l.otherShip, port);
            return out;
        }
        return out;
    }

    /**
     * Sets {@code member} to force the door across the dock {@code how}. Returns what they are
     * doing, or why they can't.
     */
    public String start(Entity member, ForceMethod how) {
        Target t = target(member, new Target());
        if (t.craft == null) return "Stand in your port's opening, at the door across the dock";
        if (t.state == ForceMethod.DoorState.OPEN) return "That door is open";
        if (!how.works(t.state)) {
            return t.state == ForceMethod.DoorState.LOCKED ? "The door is powered and locked: hack it or cut it"
                    : "The door has no power: pry it or cut it";
        }
        cancel(member);
        GridPositionComponent pos = crew.position(member);
        jobs.add(new Job(member, pos.ship, pos.tileX(), pos.tileY(), t.craft, t.port, how));
        return Character.toUpperCase(how.doing.charAt(0)) + how.doing.substring(1) + " the door of "
                + PlayerDocking.name(t.craft) + " (" + (int) how.seconds + " s)";
    }

    /** The job {@code member} is at, or null. */
    public Job jobOf(Entity member) {
        for (Job j : jobs) {
            if (j.crew == member) return j;
        }
        return null;
    }

    /** Stops {@code member}'s work, if any. */
    public void cancel(Entity member) {
        for (int i = jobs.size - 1; i >= 0; i--) {
            if (jobs.get(i).crew == member) jobs.removeIndex(i);
        }
    }

    @Override
    public void update(float deltaTime) {
        double now = simSeconds.getAsDouble();
        float dt = Double.isNaN(lastTime) ? 0f : (float) Math.max(0, now - lastTime);
        lastTime = now;
        for (int i = jobs.size - 1; i >= 0; i--) {
            Job j = jobs.get(i);
            String stop = check(j);
            if (stop != null) {
                lastOutcome = stop;
                jobs.removeIndex(i);
                continue;
            }
            j.done += dt;
            if (j.done < j.method.seconds) continue;
            jobs.removeIndex(i);
            String refused = docking.forceDoor(crew.homeOf(j.crew), j.craft, j.port, j.method);
            lastOutcome = refused != null ? "Couldn't " + j.method.verb + " the door: " + refused
                    : "The door of " + PlayerDocking.name(j.craft) + " is forced open";
        }
    }

    /** Why the job has to stop, or null while it can go on. */
    private String check(Job j) {
        CrewMemberComponent c = crew.crew(j.crew);
        GridPositionComponent pos = crew.position(j.crew);
        if (c == null || pos == null || !c.isConscious()) return "The work on the door stopped";
        if (pos.ship != j.ship || pos.tileX() != j.x || pos.tileY() != j.y || pos.walkway != null) {
            return "Stepped away from the door";
        }
        ForceMethod.DoorState state = docking.doorState(j.craft, j.port);
        if (state == ForceMethod.DoorState.OPEN) return "The door opened";
        if (!j.method.works(state)) return state == ForceMethod.DoorState.NONE ? "Undocked" : "The door changed: try another way";
        return null;
    }

    /** Index of the port of {@code craft} whose footprint holds tile (x, y), or -1. */
    static int portAt(Entity craft, int x, int y) {
        DockingPortsComponent ports = craft.getComponent(DockingPortsComponent.class);
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        if (ports == null || grid == null) return -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.gridX < 0 || !grid.inBounds(p.gridX, p.gridY)) continue;
            MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
            if (m != null && x >= m.originX && x < m.originX + m.width() && y >= m.originY && y < m.originY + m.height()) return i;
        }
        return -1;
    }
}
