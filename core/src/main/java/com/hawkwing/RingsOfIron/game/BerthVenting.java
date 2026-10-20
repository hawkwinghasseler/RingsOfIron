package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * A ship docked at a station with a hole to space aboard. Out in the black the hazard guard holds
 * warp back only while air goes out fast, so a slow leak, or a room already emptied, let a long wait
 * at the berth run life support down until the crew suffocated. Docked at a station, any opening to
 * space counts: the station warns ("Your ship is venting at the berth") and rails warp is refused
 * until the crew patch it or the yard repairs it.
 */
public final class BerthVenting {
    /** Start of every refusal, for tests and the screen. */
    public static final String REFUSAL = "Your ship is venting at the berth";

    private final GameWorld world;
    /**
     * Updates in a row a berth must vent before the station warns: just after a load or a latch
     * the dock's opening counts as open to space for a few frames, until its link is rebuilt.
     */
    static final int WARN_AFTER_FRAMES = 30;

    /** Venting at a berth at the last update: warned once per spell. */
    private boolean warned;
    private int ventingFrames;

    public BerthVenting(GameWorld world) {
        this.world = world;
        world.engine.addRailsWarpGuard(this::railsWarpBlockedReason);
        world.hints.register(HintRegistry.Context.DOCKED, "station yard (venting at the berth)", 4,
                com.hawkwing.RingsOfIron.shell.KeyBindings.Action.YARD).onlyWhen(() -> station() != null);
    }

    /** The station the player's ship is docked at while something aboard is open to space, or null. */
    public Entity station() {
        Entity player = world.getPlayer();
        Entity other = player == null ? null : world.docking.dockedTo(player);
        if (other == null || other.getComponent(StationComponent.class) == null) return null;
        return openToSpace(player.getComponent(ShipGridComponent.class)) ? other : null;
    }

    /** Why warp waits, naming what will stop it; null when it need not. */
    public String railsWarpBlockedReason() {
        Entity station = station();
        if (station == null) return null;
        Entity player = world.getPlayer();
        ShipGridComponent grid = player.getComponent(ShipGridComponent.class);
        if (world.yard.repairing(player)) return REFUSAL + ": the yard is repairing it";
        if (LeakPatches.leaks(grid).size > 0 && grid.patchKits > 0) return REFUSAL + ": the crew are patching it";
        return REFUSAL + ": order repairs at " + PlayerDocking.name(station) + "'s yard";
    }

    /** Once a frame: the station's warning, once each time the ship starts venting at a berth. */
    public void update() {
        bringShelteredHome();
        Entity station = station();
        if (station == null) {
            warned = false;
            ventingFrames = 0;
            return;
        }
        if (warned || ++ventingFrames < WARN_AFTER_FRAMES) return;
        warned = true;
        world.notices.post(PlayerDocking.name(station) + ": your ship is venting at the berth. "
                + "Time warp waits until it is patched or repaired", NoticeLog.Kind.WARNING);
    }

    /**
     * Crew carried aboard the craft the player's ship is docked to while nowhere aboard could be
     * breathed (round 8), still knocked out, are carried back aboard once somewhere can, so nobody is
     * left behind at the berth; those who come to walk back by themselves.
     */
    private void bringShelteredHome() {
        Entity player = world.getPlayer();
        Entity other = player == null ? null : world.docking.dockedTo(player);
        if (other == null) return;
        List<Entity> ashore = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = world.crew.crew(e);
            if (world.crew.position(e).ship == other && world.crew.homeOf(e) == player && c.isAlive() && !c.isConscious()
                    && !c.held && c.carriedBy == null) ashore.add(e);
        }
        if (ashore.isEmpty()) return;
        ShipGridComponent grid = player.getComponent(ShipGridComponent.class);
        List<int[]> safe = new ArrayList<>();
        for (int[] t : GameScenario.floorTiles(grid)) if (TaskRules.isSafe(grid, t[0], t[1])) safe.add(t);
        if (safe.isEmpty()) return;
        int n = 0;
        for (Entity e : ashore) {
            world.crew.releaseTask(e);
            GridPositionComponent p = world.crew.position(e);
            p.stop();
            p.crossing = null;
            p.setShip(player);
            int[] t = safe.get(n++ % safe.size());
            p.setTile(t[0], t[1]);
            world.notices.post(world.crew.crew(e).name + " is carried back aboard", NoticeLog.Kind.INFO);
        }
    }

    /**
     * True while any opening aboard leads to space, however little air still goes through it: a
     * patched or sealed leak, or a docked port's opening, is not one.
     */
    public static boolean openToSpace(ShipGridComponent grid) {
        if (grid == null) return false;
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.other == null && e.width > 0f) return true;
        }
        return false;
    }
}
