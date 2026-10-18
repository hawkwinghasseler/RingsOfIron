package com.ringsofiron.macrosim.bubble;

import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves bodies between the rails simulation and the Box2D bubble, measured against the bubble anchor
 * ({@code documentation/macro-sim/Design.md}, "Bubble Transitions"):
 * <ul>
 *   <li>Entry: within {@link #ENTER_RADIUS} and slower than {@link #ENTRY_SPEED} relative to the anchor.</li>
 *   <li>Fast pass: within the radius but faster; stays on rails as a kinematic transient.</li>
 *   <li>Exit: beyond {@link #EXIT_RADIUS}; back on rails from the true state.</li>
 * </ul>
 * Only bodies named by the latest {@link EncounterForecast} are tested for entry, so each tick checks a
 * short list. Main thread, once per fixed tick.
 */
public class BubbleTransitionSystem {
    public static final double ENTER_RADIUS = 20_000;
    public static final double EXIT_RADIUS = 25_000;
    public static final double ENTRY_SPEED = 1_000;

    private final RailsModel rails;
    private final BubbleGateway gateway;
    private final List<BubbleTransitionListener> listeners = new ArrayList<>();
    private final Set<String> inBubble = new LinkedHashSet<>();
    private final Set<String> fastPasses = new LinkedHashSet<>();
    private final Set<String> railsOnly = new LinkedHashSet<>();
    private EncounterForecast forecast;
    private final StateVector body = new StateVector();
    private final StateVector anchor = new StateVector();
    private final StateVector rel = new StateVector();
    private final List<String> work = new ArrayList<>();

    public BubbleTransitionSystem(RailsModel rails, BubbleGateway gateway) {
        this.rails = rails;
        this.gateway = gateway;
    }

    public void addListener(BubbleTransitionListener listener) {
        listeners.add(listener);
    }

    /**
     * Keeps a body on rails even when it is slow and close (stations: their orbit is authoritative and
     * the economy reads it). Inside the radius it is shown as a kinematic transient instead.
     */
    public void markRailsOnly(String uuid) {
        railsOnly.add(uuid);
    }

    /** Uses this forecast's candidates from now on. */
    public void setForecast(EncounterForecast forecast) {
        this.forecast = forecast;
    }

    public EncounterForecast forecast() {
        return forecast;
    }

    public Set<String> inBubble() {
        return Collections.unmodifiableSet(inBubble);
    }

    public Set<String> fastPasses() {
        return Collections.unmodifiableSet(fastPasses);
    }

    /** Runs exits, fast-pass updates and entries for sim second {@code t}. */
    public void update(double t, double lookAhead) {
        String parent = gateway.anchorParentUuid();
        gateway.anchorStateAt(t, anchor);

        work.clear();
        work.addAll(inBubble);
        for (String uuid : work) {
            gateway.trueStateOf(uuid, t, body);
            rel.setDifference(body, anchor);
            if (rel.distance() > EXIT_RADIUS) exit(uuid, parent, t);
        }

        work.clear();
        work.addAll(fastPasses);
        for (String uuid : work) {
            if (!rails.isOnRails(uuid)) {
                fastPasses.remove(uuid);
                continue;
            }
            rails.stateAt(uuid, t, body);
            rel.setDifference(body, anchor);
            if (rel.distance() > EXIT_RADIUS) {
                endFastPass(uuid, t);
            } else if (rel.speed() < ENTRY_SPEED && !railsOnly.contains(uuid)) {
                endFastPass(uuid, t);
                enter(uuid, t);
            }
        }

        if (forecast == null) return;
        for (var e : forecast.entries.entrySet()) {
            String uuid = e.getKey();
            if (e.getValue() > t + lookAhead || inBubble.contains(uuid) || fastPasses.contains(uuid)) continue;
            if (!rails.isOnRails(uuid) || !parent.equals(rails.parentOf(uuid))) continue;
            rails.stateAt(uuid, t, body);
            rel.setDifference(body, anchor);
            if (rel.distance() >= ENTER_RADIUS) continue;
            if (rel.speed() < ENTRY_SPEED && !railsOnly.contains(uuid)) {
                enter(uuid, t);
            } else {
                fastPasses.add(uuid);
                gateway.beginKinematicTransient(uuid, rails.trajectoryOf(uuid));
                for (BubbleTransitionListener l : listeners) l.onFastPassStart(uuid, t);
            }
        }
    }

    /**
     * Forgets bodies this system handed to the bubble that are back on rails by another route (the
     * bubble collapsing for rails warp and not rebuilding them on drop-out), and tells listeners they
     * left. Call after the bubble is rebuilt.
     */
    public void reconcile(double t) {
        work.clear();
        work.addAll(inBubble);
        for (String uuid : work) {
            if (!rails.isOnRails(uuid)) continue;
            inBubble.remove(uuid);
            for (BubbleTransitionListener l : listeners) l.onExitBubble(uuid, t);
        }
    }

    private void enter(String uuid, double t) {
        rails.stateAt(uuid, t, body);
        List<Burn> goals = rails.detach(uuid);
        gateway.spawnFromTrueState(uuid, body, t);
        inBubble.add(uuid);
        for (BubbleTransitionListener l : listeners) l.onEnterBubble(uuid, goals, t);
    }

    private void exit(String uuid, String parent, double t) {
        gateway.trueStateOf(uuid, t, body);
        rails.attach(uuid, parent, body, t);
        gateway.destroyBody(uuid);
        inBubble.remove(uuid);
        for (BubbleTransitionListener l : listeners) l.onExitBubble(uuid, t);
    }

    private void endFastPass(String uuid, double t) {
        fastPasses.remove(uuid);
        gateway.endKinematicTransient(uuid);
        for (BubbleTransitionListener l : listeners) l.onFastPassEnd(uuid, t);
    }
}
