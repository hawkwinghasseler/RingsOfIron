package com.ringsofiron.macrosim.rails.stub;

import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsSnapshot;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Self-contained rails simulation used until {@code orbital-mechanics} lands. One level of hierarchy:
 * massive bodies are registered with {@link #addBody}, and everything else orbits one of them.
 * No SOI transitions. Replace with an adapter over {@code OrbitComponent}/{@code OrbitalEventSystem}.
 */
public class StubRailsModel implements RailsModel {

    private static final class PendingBurn implements Comparable<PendingBurn> {
        final String uuid;
        final Burn burn;
        final long seq;

        PendingBurn(String uuid, Burn burn, long seq) {
            this.uuid = uuid;
            this.burn = burn;
            this.seq = seq;
        }

        @Override
        public int compareTo(PendingBurn o) {
            int c = Double.compare(burn.time, o.burn.time);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    private final Map<String, Double> bodyMu = new HashMap<>();
    private final Map<String, StubKeplerOrbit> orbits = new HashMap<>();
    private final Map<String, String> parents = new HashMap<>();
    private final PriorityQueue<PendingBurn> burns = new PriorityQueue<>();
    private final StateVector scratch = new StateVector();
    private long seq;
    private double lastProcessed;

    /** Registers a massive body others can orbit. The body itself is fixed (it is the frame origin). */
    public void addBody(String uuid, double mu) {
        bodyMu.put(uuid, mu);
    }

    /** Puts an entity directly on a known orbit. */
    public void attach(String uuid, String parentUuid, StubKeplerOrbit orbit) {
        orbits.put(uuid, orbit);
        parents.put(uuid, parentUuid);
    }

    /** Current elements of a rails entity, or null. */
    public StubKeplerOrbit orbitOf(String uuid) {
        return orbits.get(uuid);
    }

    @Override
    public double muOf(String bodyUuid) {
        Double mu = bodyMu.get(bodyUuid);
        if (mu == null) throw new IllegalArgumentException("Not a massive body: " + bodyUuid);
        return mu;
    }

    @Override
    public boolean isOnRails(String uuid) {
        return orbits.containsKey(uuid);
    }

    @Override
    public String parentOf(String uuid) {
        return parents.get(uuid);
    }

    @Override
    public StateVector stateAt(String uuid, double t, StateVector out) {
        return require(uuid).stateAt(t, out);
    }

    @Override
    public Trajectory trajectoryOf(String uuid) {
        return require(uuid);
    }

    @Override
    public void attach(String uuid, String parentUuid, StateVector state, double t) {
        attach(uuid, parentUuid, StubKeplerOrbit.fromState(muOf(parentUuid), state, t));
    }

    @Override
    public List<Burn> detach(String uuid) {
        orbits.remove(uuid);
        parents.remove(uuid);
        List<Burn> cancelled = new ArrayList<>();
        burns.removeIf(p -> {
            if (!p.uuid.equals(uuid)) return false;
            cancelled.add(p.burn);
            return true;
        });
        cancelled.sort((x, y) -> Double.compare(x.time, y.time));
        return cancelled;
    }

    @Override
    public void scheduleBurn(String uuid, Burn burn) {
        require(uuid);
        burns.add(new PendingBurn(uuid, burn, seq++));
    }

    @Override
    public void cancelBurns(String uuid) {
        burns.removeIf(p -> p.uuid.equals(uuid));
    }

    @Override
    public void processEventsUntil(double t) {
        while (!burns.isEmpty() && burns.peek().burn.time <= t) {
            PendingBurn p = burns.poll();
            StubKeplerOrbit orbit = orbits.get(p.uuid);
            if (orbit == null) continue;
            fire(p.uuid, orbit, p.burn);
        }
        lastProcessed = Math.max(lastProcessed, t);
    }

    private void fire(String uuid, StubKeplerOrbit orbit, Burn burn) {
        StateVector s = orbit.stateAt(burn.time, scratch);
        // Same axes as orbital-mechanics' KeplerOrbit.withBurn: along the velocity, and the velocity
        // turned 90 degrees away from the parent.
        double speed = s.speed();
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double px = s.vx / speed, py = s.vy / speed;
        double qx = py * dir, qy = -px * dir;
        s.vx += burn.prograde * px + burn.radial * qx;
        s.vy += burn.prograde * py + burn.radial * qy;
        orbits.put(uuid, StubKeplerOrbit.fromState(orbit.mu, s, burn.time));
    }

    @Override
    public double nextEventTime() {
        return burns.isEmpty() ? Double.POSITIVE_INFINITY : burns.peek().burn.time;
    }

    /** Pending burns of one entity, in time order (for tests and debug views). */
    public List<Burn> pendingBurns(String uuid) {
        List<Burn> out = new ArrayList<>();
        for (PendingBurn p : burns) if (p.uuid.equals(uuid)) out.add(p.burn);
        out.sort((x, y) -> Double.compare(x.time, y.time));
        return Collections.unmodifiableList(out);
    }

    @Override
    public RailsSnapshot snapshot() {
        List<RailsSnapshot.Entry> entries = new ArrayList<>(orbits.size());
        for (Map.Entry<String, StubKeplerOrbit> e : orbits.entrySet()) {
            entries.add(new RailsSnapshot.Entry(e.getKey(), parents.get(e.getKey()), e.getValue()));
        }
        return new RailsSnapshot(lastProcessed, entries);
    }

    private StubKeplerOrbit require(String uuid) {
        StubKeplerOrbit o = orbits.get(uuid);
        if (o == null) throw new IllegalArgumentException("Not on rails: " + uuid);
        return o;
    }
}
