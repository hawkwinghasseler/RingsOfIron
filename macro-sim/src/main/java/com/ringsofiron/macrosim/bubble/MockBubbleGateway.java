package com.ringsofiron.macrosim.bubble;

import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stand-in for the physics-exterior bubble. The anchor coasts on a fixed Kepler orbit, and each body
 * "in Box2D" coasts on the conic through its entry state, which is exactly what Box2D plus tidal
 * gravity approximates for an unpowered body. Used by tests and the headless demo.
 */
public class MockBubbleGateway implements BubbleGateway {
    private final String parentUuid;
    private StubKeplerOrbit anchor;
    private final Map<String, StubKeplerOrbit> bodies = new LinkedHashMap<>();
    private final Set<String> transients = new LinkedHashSet<>();
    public int spawns;
    public int destroys;

    public MockBubbleGateway(String parentUuid, StubKeplerOrbit anchor) {
        this.parentUuid = parentUuid;
        this.anchor = anchor;
    }

    public void setAnchor(StubKeplerOrbit anchor) {
        this.anchor = anchor;
    }

    /** Simulates a thrusting in-bubble pilot: changes a body's velocity by (dvx, dvy) at {@code t}. */
    public void kick(String uuid, double t, double dvx, double dvy) {
        StateVector s = bodies.get(uuid).stateAt(t, new StateVector());
        s.vx += dvx;
        s.vy += dvy;
        bodies.put(uuid, StubKeplerOrbit.fromState(anchor.mu, s, t));
    }

    public boolean hasBody(String uuid) {
        return bodies.containsKey(uuid);
    }

    public Set<String> transients() {
        return transients;
    }

    @Override
    public String anchorParentUuid() {
        return parentUuid;
    }

    @Override
    public StateVector anchorStateAt(double t, StateVector out) {
        return anchor.stateAt(t, out);
    }

    @Override
    public Trajectory anchorTrajectory() {
        return anchor;
    }

    @Override
    public void spawnFromTrueState(String uuid, StateVector trueState, double t) {
        bodies.put(uuid, StubKeplerOrbit.fromState(anchor.mu, trueState, t));
        spawns++;
    }

    @Override
    public StateVector trueStateOf(String uuid, double t, StateVector out) {
        return bodies.get(uuid).stateAt(t, out);
    }

    @Override
    public void destroyBody(String uuid) {
        bodies.remove(uuid);
        destroys++;
    }

    @Override
    public void beginKinematicTransient(String uuid, Trajectory path) {
        transients.add(uuid);
    }

    @Override
    public void endKinematicTransient(String uuid) {
        transients.remove(uuid);
    }
}
