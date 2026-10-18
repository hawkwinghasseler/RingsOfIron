package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import java.util.function.DoubleSupplier;

/**
 * ui-hud's orbit map from orbital-mechanics: the player's conic now and after its planned burns,
 * sampled evenly in eccentric (or hyperbolic) anomaly rather than in time, so the curve stays smooth
 * through a fast periapsis pass. Burns already due are left out; they fire this frame.
 */
public class PlayerOrbitPath implements OrbitPathProvider {
    public static final int SEGMENTS = 128;
    /** How far an open orbit is drawn, seconds. */
    public static final double OPEN_SPAN = 2 * 86400.0;
    /** And how far out at most, in body radii. */
    public static final double OPEN_RADII = 20;

    private final PlayerOrbitInfo orbits;
    private final BurnPlanner planner;
    private final DoubleSupplier clock;
    private final RailsUniverse universe;
    private final Array<Entity> children = new Array<>();
    private final StateVector s = new StateVector();

    public PlayerOrbitPath(GameWorld world, BurnPlanner planner) {
        this(world.universe, new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer), planner);
    }

    public PlayerOrbitPath(RailsUniverse universe, PlayerOrbitInfo orbits, BurnPlanner planner) {
        this.universe = universe;
        this.orbits = orbits;
        this.planner = planner;
        this.clock = universe::now;
    }

    @Override
    public boolean getPlayerPath(OrbitPath out) {
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        if (k == null) return false;
        double now = clock.getAsDouble();
        out.bodyName = body.name == null ? "" : body.name;
        out.bodyRadius = body.radius;
        out.shipX = orbits.state().x;
        out.shipY = orbits.state().y;
        sample(k, now, body, out.current);
        addBodies(now, out);

        ManeuverNodeComponent m = planner.nodes();
        ManeuverNodeComponent.Node selected = planner.selected();
        KeplerOrbit after = k;
        double t = now;
        int shown = 0;
        if (m != null) {
            for (ManeuverNodeComponent.Node n : m.nodes) {
                if (n.time < now) continue;
                after.stateAt(n.time, s);
                out.nodes.add((float) s.x, (float) s.y);
                if (n == selected) out.selectedNode = shown;
                shown++;
                after = after.withBurn(n.time, n.prograde, n.radial);
                t = n.time;
            }
        }
        if (shown > 0) sample(after, t, body, out.planned);
        if (selected != null) caption(out.caption, selected, planner.selectedIndex(), m.nodes.size, now, after, body);
        else out.caption.append("N plans a burn");
        return true;
    }

    /** The parent's moons with their orbits, and the body the parent itself orbits. */
    private void addBodies(double now, OrbitPath out) {
        Entity parent = orbits.parent();
        if (parent == null) return;
        for (Entity child : universe.childBodies(parent, children)) {
            MapBody b = addBody(child, parent, now, out);
            OrbitComponent o = universe.orbit(child);
            KeplerOrbit k = o.getOrbit();
            if (k.isElliptic()) {
                for (int i = 0; i <= SEGMENTS; i++) {
                    k.positionAtAnomaly(2 * Math.PI * i / SEGMENTS, s);
                    b.orbit.add((float) s.x, (float) s.y);
                }
            }
        }
        Entity grandparent = universe.parentOf(parent);
        if (grandparent != null) addBody(grandparent, parent, now, out);
    }

    private MapBody addBody(Entity body, Entity frame, double now, OrbitPath out) {
        CelestialBodyComponent c = universe.celestial(body);
        MapBody b = out.addBody();
        b.name = c.name == null ? "" : c.name;
        b.radius = c.radius;
        universe.stateInFrame(body, frame, now, s);
        b.x = s.x;
        b.y = s.y;
        return b;
    }

    private void sample(KeplerOrbit k, double t, CelestialBodyComponent body, FloatArray out) {
        double from, to;
        if (k.isElliptic()) {
            from = 0;
            to = 2 * Math.PI;
        } else {
            double edge = Math.min(body.soiRadius, OPEN_RADII * body.radius);
            double leave = k.nextTimeAtRadius(edge, t, KeplerOrbit.Crossing.OUTBOUND);
            double span = Double.isNaN(leave) ? OPEN_SPAN : Math.min(OPEN_SPAN, Math.max(1, leave - t));
            from = k.anomalyAt(t);
            to = k.anomalyAt(t + span);
        }
        for (int i = 0; i <= SEGMENTS; i++) {
            k.positionAtAnomaly(from + (to - from) * i / SEGMENTS, s);
            out.add((float) s.x, (float) s.y);
        }
    }

    private static void caption(StringBuilder out, ManeuverNodeComponent.Node n, int index, int count, double now,
                                KeplerOrbit after, CelestialBodyComponent body) {
        long in = Math.max(0, (long) (n.time - now));
        out.append(String.format("Burn %d/%d in %d:%02d:%02d%n", index + 1, count, in / 3600, (in / 60) % 60, in % 60));
        out.append(String.format("Pro %+.1f  Rad %+.1f m/s%n", n.prograde, n.radial));
        out.append(String.format("After: Pe %s", km(after.periapsis() - body.radius)));
        out.append(after.isElliptic() ? "  Ap " + km(after.apoapsis() - body.radius) : "  escaping");
        if (after.periapsis() < body.radius) out.append("  IMPACT");
        out.append("\nUp/Down pro  Left/Right rad\n[ ] time  B next  Del remove");
    }

    private static String km(double meters) {
        return String.format("%.0f km", meters / 1000.0);
    }
}
