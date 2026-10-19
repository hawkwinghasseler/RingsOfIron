package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.StationComponent;

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
    /** Venting at a berth at the last update: warned once per spell. */
    private boolean warned;

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
        Entity station = station();
        if (station == null) {
            warned = false;
            return;
        }
        if (warned) return;
        warned = true;
        world.notices.post(PlayerDocking.name(station) + ": your ship is venting at the berth. "
                + "Time warp waits until it is patched or repaired", NoticeLog.Kind.WARNING);
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
