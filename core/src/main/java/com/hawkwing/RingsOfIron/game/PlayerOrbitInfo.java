package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import java.util.function.Supplier;

/**
 * ui-hud's orbit readout from orbital-mechanics: the player's conic around its parent body. On
 * rails it is the player's orbit; flying in the bubble it is the conic through the ship's true state
 * around the body the bubble falls around.
 */
public class PlayerOrbitInfo implements OrbitInfoProvider {
    private final RailsUniverse universe;
    private final Supplier<Entity> player;
    private final OrbitInfo info = new OrbitInfo();
    private final StateVector state = new StateVector();
    private final PhysicsManager physics;
    private final double[] trueState = new double[4];

    /** Rails orbits only. */
    public PlayerOrbitInfo(RailsUniverse universe, Supplier<Entity> player) {
        this(universe, null, player);
    }

    public PlayerOrbitInfo(RailsUniverse universe, PhysicsManager physics, Supplier<Entity> player) {
        this.universe = universe;
        this.physics = physics;
        this.player = player;
    }

    @Override
    public OrbitInfo getPlayerOrbit() {
        Entity ship = player.get();
        if (ship == null) return null;
        OrbitComponent orbit = universe.orbit(ship);
        if (orbit == null) return bubbleOrbit(ship);
        Entity parent = universe.parentOf(ship);
        CelestialBodyComponent body = parent == null ? null : universe.celestial(parent);
        if (body == null) return null;
        orbit.stateAt(universe.now(), state);
        return fill(body, orbit.getOrbit());
    }

    private OrbitInfo bubbleOrbit(Entity ship) {
        PhysicsBodyComponent pb = physics == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        String parentUuid = physics == null ? null : physics.getAnchorParentUuid();
        if (pb == null || pb.body == null || parentUuid == null) return null;
        Entity parent = universe.entity(parentUuid);
        CelestialBodyComponent body = parent == null ? null : universe.celestial(parent);
        if (body == null) return null;
        physics.getTrueState(pb.body, trueState);
        state.set(trueState[0], trueState[1], trueState[2], trueState[3]);
        return fill(body, KeplerOrbit.fromState(state, body.mu, universe.now()));
    }

    private OrbitInfo fill(CelestialBodyComponent body, KeplerOrbit k) {
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
