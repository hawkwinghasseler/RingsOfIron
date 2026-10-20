package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.rails.RailsUniverse;
import java.util.function.Supplier;

/**
 * Plans impulsive burns on the player's ship as orbital-mechanics maneuver nodes. One node at a time
 * is selected for editing. On rails the orbital event system fires each node when it is due (and time
 * warp drops out there); in the bubble {@link BubbleBurnSystem} does.
 */
public class BurnPlanner {
    /** How far ahead a new burn goes when none is planned yet, seconds. */
    public static final double FIRST_LEAD = 300;
    /** Burns stay at least this far in the future while being edited, seconds. */
    public static final double MIN_LEAD = 5;
    /** Time steps per orbit when moving a burn along an elliptic orbit. */
    public static final int STEPS_PER_ORBIT = 72;
    public static final double OPEN_ORBIT_STEP = 60;

    private final RailsUniverse universe;
    private final OrbitalEventSystem events;
    private final Supplier<Entity> player;
    private final PlayerOrbitInfo orbits;
    private ManeuverNodeComponent.Node selected;

    public BurnPlanner(GameWorld world) {
        this(world.universe, world.orbitalEvents, world::getPlayer,
                new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer));
    }

    public BurnPlanner(RailsUniverse universe, OrbitalEventSystem events, Supplier<Entity> player, PlayerOrbitInfo orbits) {
        this.universe = universe;
        this.events = events;
        this.player = player;
        this.orbits = orbits;
    }

    /** The player's planned burns in time order, or null if none were ever planned. */
    public ManeuverNodeComponent nodes() {
        Entity ship = player.get();
        return ship == null ? null : ship.getComponent(ManeuverNodeComponent.class);
    }

    /** The burn being edited, or null. Moves to the next burn when the selected one has fired. */
    public ManeuverNodeComponent.Node selected() {
        ManeuverNodeComponent m = nodes();
        if (m == null || m.nodes.size == 0) return selected = null;
        if (selected == null || !m.nodes.contains(selected, true)) selected = m.nodes.first();
        return selected;
    }

    /** Index of {@link #selected()} among the planned burns, or -1. */
    public int selectedIndex() {
        ManeuverNodeComponent.Node n = selected();
        return n == null ? -1 : nodes().nodes.indexOf(n, true);
    }

    /** Plans a new empty burn after the last one (or a little ahead) and selects it. */
    public ManeuverNodeComponent.Node addNode() {
        Entity ship = player.get();
        if (ship == null) return null;
        ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
        if (m == null) {
            m = new ManeuverNodeComponent();
            ship.add(m);
        }
        double now = universe.now();
        double t = m.nodes.size == 0 ? now + FIRST_LEAD
                : Math.max(now, m.nodes.peek().time) + STEPS_PER_ORBIT / 4.0 * timeStep();
        selected = new ManeuverNodeComponent.Node(t, 0, 0);
        insertSorted(m, selected);
        changed();
        return selected;
    }

    /** Selects the next planned burn, wrapping around. */
    public void selectNext() {
        ManeuverNodeComponent.Node n = selected();
        if (n == null) return;
        ManeuverNodeComponent m = nodes();
        selected = m.nodes.get((m.nodes.indexOf(n, true) + 1) % m.nodes.size);
    }

    /** Moves the selected burn by whole {@link #timeStep()}s, never into the past. */
    public void shiftTime(int steps) {
        ManeuverNodeComponent.Node n = selected();
        if (n == null) return;
        ManeuverNodeComponent m = nodes();
        n.time = Math.max(universe.now() + MIN_LEAD, n.time + steps * timeStep());
        m.nodes.removeValue(n, true);
        insertSorted(m, n);
        changed();
    }

    /** Adds delta-v to the selected burn, m/s along the velocity and outward from the parent. */
    public void adjust(double prograde, double radial) {
        ManeuverNodeComponent.Node n = selected();
        if (n == null) return;
        n.prograde += prograde;
        n.radial += radial;
        changed();
    }

    /** Removes the selected burn and selects the next one. */
    public void deleteSelected() {
        ManeuverNodeComponent.Node n = selected();
        if (n == null) return;
        ManeuverNodeComponent m = nodes();
        int i = m.nodes.indexOf(n, true);
        m.nodes.removeIndex(i);
        selected = m.nodes.size == 0 ? null : m.nodes.get(Math.min(i, m.nodes.size - 1));
        changed();
    }

    /**
     * Replaces every planned burn with {@code planned} (time, prograde, radial each), as when the
     * player accepts a transfer; selects the first.
     */
    public void replace(java.util.List<double[]> planned) {
        Entity ship = player.get();
        if (ship == null) return;
        ManeuverNodeComponent m = ship.getComponent(ManeuverNodeComponent.class);
        if (m == null) {
            m = new ManeuverNodeComponent();
            ship.add(m);
        }
        m.nodes.clear();
        for (double[] b : planned) insertSorted(m, new ManeuverNodeComponent.Node(b[0], b[1], b[2]));
        selected = m.nodes.size == 0 ? null : m.nodes.first();
        changed();
    }

    /** One step of {@link #shiftTime}: a fraction of the orbit, or a minute on an open orbit. */
    public double timeStep() {
        KeplerOrbit k = orbits.currentConic();
        return k != null && k.isElliptic() ? k.period() / STEPS_PER_ORBIT : OPEN_ORBIT_STEP;
    }

    /** Keeps the list in time order: the event system fires the first node next. */
    private static void insertSorted(ManeuverNodeComponent m, ManeuverNodeComponent.Node n) {
        int i = 0;
        while (i < m.nodes.size && m.nodes.get(i).time <= n.time) i++;
        m.nodes.insert(i, n);
    }

    /** On rails the event queue holds the next node's time; redo it after every edit. */
    private void changed() {
        Entity ship = player.get();
        if (ship != null && universe.orbit(ship) != null) events.orbitChanged(ship);
    }
}
