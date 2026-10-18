package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import java.util.function.Supplier;

/**
 * ui-hud's orbit readout from orbital-mechanics: the player's conic around its parent body.
 * Null while the player has no rails orbit (inside the bubble until the anchor orbit is exposed).
 */
public class PlayerOrbitInfo implements OrbitInfoProvider {
    private final RailsUniverse universe;
    private final Supplier<Entity> player;
    private final OrbitInfo info = new OrbitInfo();
    private final StateVector state = new StateVector();

    public PlayerOrbitInfo(RailsUniverse universe, Supplier<Entity> player) {
        this.universe = universe;
        this.player = player;
    }

    @Override
    public OrbitInfo getPlayerOrbit() {
        Entity ship = player.get();
        if (ship == null) return null;
        OrbitComponent orbit = universe.orbit(ship);
        Entity parent = orbit == null ? null : universe.parentOf(ship);
        CelestialBodyComponent body = parent == null ? null : universe.celestial(parent);
        if (body == null) return null;

        KeplerOrbit k = orbit.getOrbit();
        orbit.stateAt(universe.now(), state);
        info.bodyName = body.name == null ? "" : body.name;
        info.altitude = Math.hypot(state.x, state.y) - body.radius;
        info.periapsis = k.periapsis() - body.radius;
        if (k.isElliptic()) {
            info.apoapsis = k.apoapsis() - body.radius;
            info.period = k.period();
        } else {
            info.apoapsis = Double.POSITIVE_INFINITY;
            info.period = Double.POSITIVE_INFINITY;
        }
        info.impactWarning = k.periapsis() < body.radius;
        return info;
    }
}
