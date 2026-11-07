package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.ForceDoorTask;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.DoorPower;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.uihud.providers.DoorProvider;
import com.ringsofiron.uihud.systems.InteractPromptSystem;

/**
 * The player's hand on the doors aboard: [E] at a bulkhead door (an airlock's door or hatch
 * included) opens the door menu ({@link DoorProvider}), showing the pressure on either side of it,
 * and locks or unlocks it ({@link EdgeWall#lockedShut}). Crew never open a locked door: unlocking
 * one is the captain's order to use it, as for a broken airlock's door its failsafe locked
 * ({@link Airlock#failsafe}). Working the lock takes power ({@link DoorPower}); a locked door with
 * none can only be cut open, which the player orders the crew to do from the menu ({@link Cut}).
 */
public final class DoorControls implements DoorProvider {
    /** Tiles from the avatar to a door's middle within which [E] uses it. */
    public static final float REACH = 1.0f;
    /** Standard pressure, kPa: a pressure ratio of 1. */
    static final float STANDARD_KPA = 101.325f;

    private final GameContext ctx;
    private final CrewService crew;
    /** The door in use, while the menu is open: on the avatar's side (x, y), across {@link #side}. */
    private ShipGridComponent grid;
    private Entity ship;
    private int x, y;
    private Side side;
    private final int[] found = new int[3];

    public DoorControls(GameContext ctx, CrewService crew) {
        this.ctx = ctx;
        this.crew = crew;
    }

    /** What [E] does at a door: opens the door menu through {@code open}, which opens the window. */
    public InteractPromptSystem.Interaction doorUse(Runnable open) {
        return new InteractPromptSystem.Interaction() {
            @Override
            public String prompt() {
                Entity a = avatar();
                if (a == null || !usable(a)) return null;
                float d = nearest(a);
                if (d > REACH * REACH) return null;
                return "use the " + name(crew.grid(crew.position(a).ship), found[0], found[1], Side.values()[found[2]]).toLowerCase();
            }

            @Override
            public float distance2() {
                Entity a = avatar();
                return a == null ? Float.MAX_VALUE : nearest(a);
            }

            @Override
            public void act() {
                if (use()) open.run();
            }
        };
    }

    /** Takes the door nearest the avatar in reach into use; false if there is none. */
    public boolean use() {
        Entity a = avatar();
        if (a == null || !usable(a) || nearest(a) > REACH * REACH) return false;
        GridPositionComponent p = crew.position(a);
        ship = p.ship;
        grid = crew.grid(ship);
        x = found[0];
        y = found[1];
        side = Side.values()[found[2]];
        return true;
    }

    private boolean usable(Entity a) {
        CrewMemberComponent c = crew.crew(a);
        GridPositionComponent p = crew.position(a);
        return c != null && c.isConscious() && !c.held && p != null && p.ship != null && p.walkway == null
                && crew.grid(p.ship) != null && com.ringsofiron.aicrew.EvaSystem.of(a) == null;
    }

    /**
     * The squared distance, tiles, from the avatar to the middle of the nearest whole bulkhead door
     * about its tile, which goes in {@link #found} as (x, y, side) from the avatar's side.
     */
    private float nearest(Entity a) {
        GridPositionComponent p = crew.position(a);
        ShipGridComponent g = crew.grid(p.ship);
        float best = Float.MAX_VALUE;
        if (g == null) return best;
        int tx = p.tileX(), ty = p.tileY();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = tx + dx, cy = ty + dy;
                for (Side s : Side.values()) {
                    EdgeWall w = g.getEdge(cx, cy, s);
                    if (w == null || !w.hasBulkhead || w.isDestroyed()) continue;
                    float mx = cx + 0.5f + s.dx * 0.5f, my = cy + 0.5f + s.dy * 0.5f;
                    float d = (p.x - mx) * (p.x - mx) + (p.y - my) * (p.y - my);
                    if (d >= best) continue;
                    // From the avatar's side of it
                    boolean here = (p.x - mx) * s.dx + (p.y - my) * s.dy <= 0f;
                    best = d;
                    found[0] = here ? cx : cx + s.dx;
                    found[1] = here ? cy : cy + s.dy;
                    found[2] = (here ? s : s.opposite()).ordinal();
                }
            }
        }
        return best;
    }

    /** The door in use, or null once it is gone (wrecked, the ship gone, or walked away from). */
    EdgeWall door() {
        Entity a = avatar();
        if (grid == null || a == null || !usable(a) || crew.position(a).ship != ship) return null;
        EdgeWall w = grid.getEdge(x, y, side);
        if (w == null || !w.hasBulkhead || w.isDestroyed()) return null;
        GridPositionComponent p = crew.position(a);
        float mx = x + 0.5f + side.dx * 0.5f, my = y + 0.5f + side.dy * 0.5f;
        float d = (p.x - mx) * (p.x - mx) + (p.y - my) * (p.y - my);
        return d <= (REACH + 0.5f) * (REACH + 0.5f) ? w : null;
    }

    @Override
    public void collect(Door out) {
        EdgeWall w = door();
        if (w == null) return;
        out.name = name(grid, x, y, side);
        out.nearKpa = grid.pressureRatioAt(x, y) * STANDARD_KPA;
        out.farKpa = grid.pressureRatioAt(x + side.dx, y + side.dy) * STANDARD_KPA;
        out.locked = w.lockedShut;
        boolean powered = DoorPower.powered(grid, x, y, side);
        out.lockBlocked = powered ? "" : "no power";
        out.canCut = w.lockedShut && !powered && cutOrdered(w) == null;
        out.state = !w.lockedShut ? (w.locked ? "Held shut for its airlock's cycle" : w.bulkheadSealed ? "Unlocked, shut" : "Unlocked, open")
                : w.lockedByAirlock ? "Locked by its airlock: the door paired with it is wrecked" : "Locked";
        out.note = cutOrdered(w) != null ? "The crew are ordered to cut it open" : "";
        if (!out.note.isEmpty()) out.state += ". " + out.note;
    }

    @Override
    public String toggleLock() {
        EdgeWall w = door();
        if (w == null) return "There is no door here";
        if (!DoorPower.powered(grid, x, y, side)) return "The door has no power to work its lock";
        if (w.lockedShut) {
            w.unlock();
            return Airlock.broken(grid, portOf(grid, x, y, side)) && portOf(grid, x, y, side) != null
                    ? "Unlocked: the airlock is broken, so this door opens onto what is past it"
                    : "Unlocked";
        }
        w.lock(false);
        grid.gridDirty = true;
        return "Locked: the crew won't open it till you unlock it";
    }

    @Override
    public String orderCut() {
        EdgeWall w = door();
        if (w == null) return "There is no door here";
        if (!w.lockedShut) return "It isn't locked";
        if (DoorPower.powered(grid, x, y, side)) return "It has power: unlock it instead";
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return "There is no crew to order";
        board.post(new ForceDoorTask(ship, x, y, new Cut(grid, w)));
        return "The crew are ordered to cut it open";
    }

    @Override
    public void close() {
        grid = null;
        ship = null;
    }

    /** The open cut order on {@code door}, or null. */
    private Cut cutOrdered(EdgeWall door) {
        ShipTaskBoardComponent board = ship == null ? null : crew.board(ship);
        if (board == null) return null;
        for (com.ringsofiron.interiorgrid.crew.CrewTask t : board.tasks) {
            if (t instanceof ForceDoorTask && t.isActive() && ((ForceDoorTask) t).job instanceof Cut
                    && ((Cut) ((ForceDoorTask) t).job).door == door) return (Cut) ((ForceDoorTask) t).job;
        }
        return null;
    }

    /** "Door", or for one of a docking port's, "Port 1 airlock outer door" or "Port 1 airlock hatch". */
    String name(ShipGridComponent g, int x, int y, Side side) {
        MountFootprint m = portOf(g, x, y, side);
        if (m == null) return "Door";
        boolean outer = false;
        for (int k = 0; k < 2; k++) {
            int tx = k == 0 ? x : x + side.dx, ty = k == 0 ? y : y + side.dy;
            if (m.covers(tx, ty) && DockingPortLayout.door(g, m, tx, ty) == g.getEdge(x, y, side)) outer = true;
        }
        int index = portIndex(m);
        return (index < 0 ? "Airlock " : "Port " + (index + 1) + " airlock ") + (outer ? "outer door" : "hatch");
    }

    /** The docking port whose door or hatch the {@code side} edge of (x, y) is, or null. */
    static MountFootprint portOf(ShipGridComponent g, int x, int y, Side side) {
        for (int k = 0; k < 2; k++) {
            int tx = k == 0 ? x : x + side.dx, ty = k == 0 ? y : y + side.dy;
            Tile t = g.getTile(tx, ty);
            MountFootprint m = t == null ? null : t.mount;
            if (m == null || m.kind != MountFootprint.MountKind.PORT) continue;
            EdgeWall w = g.getEdge(x, y, side);
            if (DockingPortLayout.door(g, m, tx, ty) == w || DockingPortLayout.hatch(g, m, tx, ty) == w) return m;
        }
        return null;
    }

    private int portIndex(MountFootprint m) {
        DockingPortsComponent ports = ship == null ? null : ship.getComponent(DockingPortsComponent.class);
        Entity a = avatar();
        if (ports == null && a != null) ports = crew.position(a).ship.getComponent(DockingPortsComponent.class);
        if (ports == null) return -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.gridX >= 0 && m.covers(p.gridX, p.gridY)) return i;
        }
        return -1;
    }

    private Entity avatar() {
        for (Entity e : crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) return e;
        return null;
    }

    /**
     * Cutting a locked door with no power open, on the captain's order: from the spot it was ordered
     * from, {@link #SECONDS} of work, and the door is wrecked ({@link DoorPower#cutOpen}) until a
     * yard rebuilds it.
     */
    static final class Cut implements ForceDoorTask.Job {
        /** Seconds of cutting through a door. */
        static final float SECONDS = 20f;

        final ShipGridComponent grid;
        final EdgeWall door;
        float done;

        Cut(ShipGridComponent grid, EdgeWall door) {
            this.grid = grid;
            this.door = door;
        }

        @Override
        public boolean work(Entity crew, float dt) {
            done += dt;
            if (done < SECONDS) return false;
            DoorPower.cutOpen(grid, door);
            return true;
        }

        @Override
        public boolean isOpen() {
            return !door.isDestroyed() && door.lockedShut;
        }

        @Override
        public String what() {
            return "cutting a locked door open";
        }

        @Override
        public float progress(Entity crew) {
            return done / SECONDS;
        }
    }
}
