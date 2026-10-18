package com.ringsofiron.orbital.warp;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Signal;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;

import java.util.function.Supplier;

/**
 * Owns the warp level. Two kinds of warp, because Box2D cannot be fast-forwarded:
 * <ul>
 *   <li><b>Physics warp</b> (1x to 10x): Box2D runs several fixed ticks per frame.</li>
 *   <li><b>Rails warp</b> (50x to 1,000,000x): the bubble is collapsed onto rails, Box2D idles and
 *       the clock jumps forward. It drops back to 1x just before the next event that matters to the
 *       player (their own SOI change, maneuver or impact, or another body coming within
 *       {@link #encounterRadius}), and at once if a guard vetoes (e.g. a fire starts aboard).</li>
 * </ul>
 * Call {@link #update} once per rendered frame with the real frame time.
 */
public class TimeWarpController {
    public static final int[] PHYSICS_LEVELS = {1, 2, 3, 5, 10};
    public static final int[] RAILS_LEVELS = {50, 100, 1_000, 10_000, 100_000, 1_000_000};
    public static final int MAX_PHYSICS_WARP = 10;
    /** Matches the bubble entry radius in the design: a body this close would enter the bubble. */
    public static final double DEFAULT_ENCOUNTER_RADIUS = 20_000;

    public enum DropReason {
        /** The player asked for a lower warp. */
        REQUESTED,
        /** The player's own maneuver node, SOI change or impact is due. */
        PLAYER_EVENT,
        /** Another body is about to come within range. */
        ENCOUNTER,
        /** A {@link WarpHost} guard refused to keep warping. */
        VETOED
    }

    public static final class WarpChange {
        public int warp;
        public boolean rails;
        /** Set when rails warp ended. */
        public DropReason reason;
        /** The event or body that caused a drop, if any. */
        public OrbitalEvent event;
        public Entity encounter;
    }

    public final Signal<WarpChange> changes = new Signal<>();

    private final WarpHost host;
    private final BubbleHandoff bubble;
    private final OrbitalEventSystem events;
    private final RailsUniverse universe;
    private final EncounterPredictor encounters;
    private final Supplier<Entity> player;
    private final EncounterPredictor.Result encounter = new EncounterPredictor.Result();
    private final WarpChange change = new WarpChange();
    private double encounterRadius = DEFAULT_ENCOUNTER_RADIUS;

    private int warp = 1;
    private boolean rails;
    private double tickAccumulator;

    public TimeWarpController(WarpHost host, BubbleHandoff bubble, OrbitalEventSystem events,
                              RailsUniverse universe, EncounterPredictor encounters, Supplier<Entity> player) {
        this.host = host;
        this.bubble = bubble;
        this.events = events;
        this.universe = universe;
        this.encounters = encounters;
        this.player = player;
    }

    public int getWarp() {
        return warp;
    }

    public boolean isRailsWarp() {
        return rails;
    }

    public void setEncounterRadius(double meters) {
        this.encounterRadius = meters;
    }

    /**
     * Sets the warp multiplier. Values up to 10 are physics warp; 50 and above are rails warp.
     * Returns false (and changes nothing) if rails warp is not allowed right now.
     */
    public boolean setWarp(int multiplier) {
        if (multiplier < 1) multiplier = 1;
        if (multiplier <= MAX_PHYSICS_WARP) {
            if (rails) leaveRails(DropReason.REQUESTED, null, null);
            warp = multiplier;
            host.setPhysicsWarp(multiplier);
            notifyChange(null, null, null);
            return true;
        }
        if (!rails) {
            if (!host.railsWarpAllowed()) return false;
            host.setPhysicsWarp(1);
            bubble.collapseToRails();
            if (universe.orbit(player.get()) == null) {
                // The bubble side did not put the player on rails; nothing safe to warp.
                bubble.rebuildFromRails();
                return false;
            }
            rails = true;
            tickAccumulator = 0;
        }
        warp = multiplier;
        notifyChange(null, null, null);
        return true;
    }

    /** One step up or down the combined ladder of physics and rails levels. */
    public boolean step(int direction) {
        int[] all = new int[PHYSICS_LEVELS.length + RAILS_LEVELS.length];
        System.arraycopy(PHYSICS_LEVELS, 0, all, 0, PHYSICS_LEVELS.length);
        System.arraycopy(RAILS_LEVELS, 0, all, PHYSICS_LEVELS.length, RAILS_LEVELS.length);
        int index = 0;
        for (int i = 0; i < all.length; i++) if (all[i] <= warp) index = i;
        int next = Math.max(0, Math.min(all.length - 1, index + (direction > 0 ? 1 : -1)));
        return setWarp(all[next]);
    }

    /** Advances rails warp by one rendered frame. Does nothing at physics warp (the engine steps itself). */
    public void update(float frameSeconds) {
        if (!rails) return;
        tickAccumulator += (double) warp * frameSeconds * WarpHost.TICKS_PER_SECOND;
        long ticks = (long) tickAccumulator;
        if (ticks <= 0) return;
        tickAccumulator -= ticks;

        long now = host.getTick();
        double tNow = now / (double) WarpHost.TICKS_PER_SECOND;
        double tTarget = (now + ticks) / (double) WarpHost.TICKS_PER_SECOND;
        Entity p = player.get();
        String playerId = universe.uuidOf(p);

        DropReason reason = null;
        OrbitalEvent dueEvent = null;
        double stopAt = tTarget;

        OrbitalEvent next = events.getQueue().nextFor(playerId);
        if (next != null && next.time <= stopAt) {
            stopAt = next.time;
            reason = DropReason.PLAYER_EVENT;
            dueEvent = next;
        }
        encounters.find(p, encounterRadius, tNow, stopAt, encounter);
        if (!Double.isNaN(encounter.time)) {
            stopAt = encounter.time;
            reason = DropReason.ENCOUNTER;
            dueEvent = null;
        } else if (encounter.scannedUntil < stopAt) {
            // Not everything was checked this far ahead; go no further this frame.
            stopAt = encounter.scannedUntil;
            reason = null;
            dueEvent = null;
        }

        long advance = ticks;
        if (stopAt < tTarget) {
            long stopTick = (long) Math.ceil(stopAt * WarpHost.TICKS_PER_SECOND);
            advance = Math.max(0, Math.min(ticks, stopTick - now));
            tickAccumulator = 0;
        }
        if (!host.advanceRails(advance)) {
            leaveRails(DropReason.VETOED, null, null);
            return;
        }
        events.processUntil(host.getTick() / (double) WarpHost.TICKS_PER_SECOND);
        if (reason != null) leaveRails(reason, dueEvent, encounter.other);
    }

    private void leaveRails(DropReason reason, OrbitalEvent event, Entity other) {
        rails = false;
        warp = 1;
        host.setPhysicsWarp(1);
        bubble.rebuildFromRails();
        notifyChange(reason, event, reason == DropReason.ENCOUNTER ? other : null);
    }

    private void notifyChange(DropReason reason, OrbitalEvent event, Entity other) {
        change.warp = warp;
        change.rails = rails;
        change.reason = reason;
        change.event = event;
        change.encounter = other;
        changes.dispatch(change);
    }
}
