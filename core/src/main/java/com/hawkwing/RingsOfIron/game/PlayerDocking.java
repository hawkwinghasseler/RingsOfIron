package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.stationsdocking.DockingEvent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The player's side of docking, for a screen's keys and readout: arm the selected port and ask
 * the nearest station for clearance, pick another port, undock, and a few lines on how the port
 * lines up with where it is going. Messages for the player (clearance given or refused, latched,
 * torn loose) go to {@code notices}.
 */
public class PlayerDocking {
    /** How far the readout looks for a port to line up with, meters. */
    public static final float READOUT_RANGE = 500f;

    private final GameWorld world;
    private final Supplier<Entity> ship;
    private final Consumer<String> notices;
    private final DockingSystem.Approach approach = new DockingSystem.Approach();
    private final Listener<DockingEvent> listener;

    public PlayerDocking(GameWorld world, Supplier<Entity> ship, Consumer<String> notices) {
        this.world = world;
        this.ship = ship;
        this.notices = notices;
        listener = (signal, e) -> onEvent(e);
        world.docking.events.add(listener);
    }

    public void dispose() {
        world.docking.events.remove(listener);
    }

    /**
     * Arms the selected port and asks the nearest station for clearance, or disarms it when armed.
     * Returns what happened, for the player.
     */
    public String toggleArm() {
        Entity player = ship.get();
        DockingPortsComponent ports = ports(player);
        if (ports == null) return "No docking port";
        DockingPort port = ports.selectedPort();
        if (port.armed) {
            world.docking.disarm(player, ports.selected);
            return "Port disarmed";
        }
        String refused = world.docking.arm(player, ports.selected);
        if (refused != null) return "Can't arm the port: " + refused;
        DockingSystem.Clearance c = world.docking.requestClearance(player, ports.selected);
        String station = c.station == null ? null : name(c.station);
        switch (c.outcome) {
            case GRANTED:
                return station + " gives clearance: port " + (c.port + 1) + " is armed for you";
            case DENIED:
                return station + " refuses docking clearance" + (c.reason.isEmpty() ? "" : " (" + c.reason + ")") + "; port armed";
            case NO_FREE_PORT:
                return station + " has no free " + port.size + " port; port armed";
            default:
                return "Port armed";
        }
    }

    /** Selects the next port. Returns its description. */
    public String nextPort() {
        DockingPortsComponent ports = ports(ship.get());
        if (ports == null) return "No docking port";
        ports.selected = (ports.selected + 1) % ports.ports.length;
        return "Port " + (ports.selected + 1) + ": " + describe(ports.selectedPort());
    }

    public String undock() {
        Entity player = ship.get();
        Entity from = player == null ? null : world.docking.dockedTo(player);
        if (from == null) return "Not docked";
        Entity aboard = world.avatar.getAvatarShip();
        if (aboard != null && aboard != player) return "You're aboard " + name(aboard) + ": walk back to your ship first";
        if (world.cargo.visitorsAboard(player) > 0) {
            // Not flying off with them: the loading stops and they head home
            world.cargo.callOff(player);
            return name(from) + "'s dock hands are still aboard: the loading is called off, undock once they're off";
        }
        if (world.cargo.crewAway(player) > 0) {
            // Nor leaving our own behind
            world.cargo.callOff(player);
            return "Your crew are still aboard " + name(from) + ": the loading is called off, undock once they're back";
        }
        if (!world.docking.undock(player)) return "Not docked";
        return "Undocked from " + name(from);
    }

    /** Lines for the screen: the port, what it is doing, and how it lines up or how hard the latch strains. */
    public String[] readout() {
        Entity player = ship.get();
        DockingPortsComponent ports = ports(player);
        if (ports == null) return new String[0];
        DockingPort port = ports.selectedPort();
        String first = String.format(Locale.ROOT, "Dock port %d/%d: %s%s", ports.selected + 1, ports.ports.length,
                describe(port), port.armed ? ", armed" : "");
        if (port.state == DockingPort.State.DOCKED) {
            Entity other = world.docking.dockedTo(player);
            return new String[]{first, String.format(Locale.ROOT, "Docked at %s%s   strain %.0f%%",
                    other == null ? "?" : name(other), world.docking.isRigid(player) ? "" : " (settling)",
                    100f * world.docking.strain(player))};
        }
        if (port.state == DockingPort.State.CAPTURED) return new String[]{first, "Collar has hold: drawing in"};
        world.docking.approach(player, ports.selected, READOUT_RANGE, approach);
        if (approach.target == null) return new String[]{first, "No free " + port.size + " port within " + (int) READOUT_RANGE + " m"};
        return new String[]{first, String.format(Locale.ROOT,
                "To %s #%d: %.1f m  off %.1f m  turn %+.0f deg  in %.2f m/s",
                name(approach.target), approach.port + 1, approach.distance, approach.lateral,
                approach.angle * MathUtils.radiansToDegrees, Math.round(approach.closing * 100f) / 100f + 0f)};
    }

    private void onEvent(DockingEvent e) {
        Entity player = ship.get();
        if (player == null || (e.vessel != player && e.other != player)) return;
        Entity other = e.vessel == player ? e.other : e.vessel;
        switch (e.kind) {
            case CAPTURED:
                notices.accept(name(other) + "'s collar has you; drawing in");
                break;
            case DOCKED:
                notices.accept("Docked at " + name(other) + (e.permitted ? "" : " (without clearance)"));
                break;
            case TORN:
                notices.accept("Torn loose from " + name(other) + ": the collar is gone and the hull damaged");
                break;
            default:
                break;
        }
    }

    private static DockingPortsComponent ports(Entity e) {
        DockingPortsComponent ports = e == null ? null : e.getComponent(DockingPortsComponent.class);
        return ports == null || ports.ports.length == 0 ? null : ports;
    }

    private static String describe(DockingPort p) {
        String state = p.state == DockingPort.State.IDLE ? "free" : p.state == DockingPort.State.CAPTURED ? "captured" : "docked";
        return p.size + (p.active ? " active" : " passive") + ", " + (p.intact() ? state : "collar torn off");
    }

    /** What the player calls a station or ship. */
    public static String name(Entity e) {
        StationComponent s = e.getComponent(StationComponent.class);
        if (s != null && s.name != null) return s.name;
        FreighterComponent f = e.getComponent(FreighterComponent.class);
        if (f != null && f.displayName != null) return f.displayName;
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        return m != null && m.label != null && !m.label.isEmpty() ? m.label : "the other ship";
    }
}
