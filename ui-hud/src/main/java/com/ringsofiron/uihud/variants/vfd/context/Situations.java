package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;

/**
 * The context model (functional-context 4.1, 4.2): the seat, and the situations that hold, each with
 * its entry and exit rule and the hysteresis that keeps panels from flickering. Pure: read from the
 * data each frame, so it is tested without a screen.
 */
final class Situations {
    enum Seat {
        NONE, HELM, FIRE_CONTROL, GUN, DECK
    }

    /** Approach holds within this of a port, and leaves past the second (4.2). */
    static final float APPROACH_RANGE = 5_000f, APPROACH_LEAVE = 5_500f;
    /** Seconds an approach waits after its trigger goes, and combat after its last cause. */
    static final float APPROACH_HOLD = 3f, COMBAT_HOLD = 10f;
    /** A beaten ship this close can be reached on EVA (4.2, Boarding). */
    static final float BOARD_REACH = 300f;
    /** Suit air under this, away from breathable air, is an emergency (AirWatch.WARN_AT). */
    static final float SUIT_WARN = 240f;
    /** A lock within this is in weapons range when no gun says its own range. */
    static final float GUN_RANGE = 5_000f;

    Seat seat = Seat.NONE;
    /** Aboard a craft that is not the player's own ship: P1 names it. */
    String aboard = "";
    boolean warp, approach, docked, latched, combat, boarding, emergency, cruise;
    /** Why the emergency holds: the ship disabled, abandoning, the captain's suit air running out. */
    boolean disabled, abandoning, suitAir;
    /** A full-screen mode is open (M1 to M6): P2 folds into P1's chip. */
    boolean fullScreen;

    private float approachGone = Float.NaN, combatGone = Float.NaN;

    /**
     * Reads this frame's situations. {@code boardable}: a beaten ship can be boarded from here (the
     * boarding window has one); {@code powerDueBack}: the ship's only fault is power coming back by
     * itself, so being disabled waits (X3); {@code fullScreen}: a mode or window is open.
     */
    void update(HudData d, boolean boardable, boolean powerDueBack, boolean fullScreen, float delta) {
        UiContext ctx = d.ctx;
        seat = seat(ctx);
        aboard = ctx.presence == Presence.ABOARD_OTHER ? (ctx.docked ? ctx.stationName : d.docking.other) : "";
        this.fullScreen = fullScreen;
        boolean ship = ctx.ship != null;
        warp = ctx.warp > 1;
        docked = ship && ctx.docked;
        latched = ship && ctx.latched;

        // Approach: a free port near, and the player means to dock there; 3 s grace when that goes
        DockingApproach k = d.docking;
        boolean near = ship && k.present && !docked && !latched && !Float.isNaN(k.range);
        boolean trigger = near && approachTrigger(k, d.navReadout, d.orderState.helm);
        if (approach) {
            if (!near || k.range > APPROACH_LEAVE) {
                approach = false;
            } else if (trigger) {
                approachGone = Float.NaN;
            } else {
                approachGone = Float.isNaN(approachGone) ? delta : approachGone + delta;
                if (approachGone >= APPROACH_HOLD) approach = false;
            }
        } else if (trigger && k.range <= APPROACH_RANGE) {
            approach = true;
            approachGone = Float.NaN;
        }

        // Combat: battle stations, a missile inbound, or a hostile locked in weapons range; 10 s to stand down
        boolean cause = ship && (d.orderState.battleStations || d.threatFrame.incoming() > 0 || hostileInRange(d));
        if (cause) {
            combat = true;
            combatGone = Float.NaN;
        } else if (combat) {
            combatGone = Float.isNaN(combatGone) ? delta : combatGone + delta;
            if (combatGone >= COMBAT_HOLD) combat = false;
        }

        boarding = ship && (latched || boardable);

        disabled = ship && !ctx.disabledReason.isEmpty() && !powerDueBack;
        abandoning = ship && (ctx.abandoning || d.orderState.abandoning);
        suitAir = suitAirLow(d.captainStatus);
        emergency = disabled || abandoning || suitAir;
        cruise = !warp && !approach && !docked && !latched && !combat && !boarding && !emergency;
    }

    static Seat seat(UiContext ctx) {
        switch (ctx.presence) {
            case HELM: return Seat.HELM;
            case WEAPONS: return Seat.FIRE_CONTROL;
            case GUN: return Seat.GUN;
            case DECK:
            case ABOARD_OTHER:
            case EVA:
                // -PviewAnywhere: the outside view from the deck is treated as the helm (4.1)
                return ctx.exteriorView && ctx.presence == Presence.DECK ? Seat.HELM : Seat.DECK;
            default: return Seat.NONE;
        }
    }

    /**
     * The approach's trigger (4.2): a port in reach, and one of: the nav target is that craft, a port
     * is armed, or the helm has a dock order. A hostile's port near by is no approach.
     */
    static boolean approachTrigger(DockingApproach k, NavReadout nav, HelmOrder helm) {
        if (!k.present || k.capture == Capture.FREE || k.capture == Capture.DOCKED) return false;
        if (k.capture == Capture.CAPTURED || k.armed || helm == HelmOrder.DOCK) return true;
        return nav.present && (nav.kind == NavigationProvider.Kind.STATION || nav.kind == NavigationProvider.Kind.SHIP)
                && nav.relation != Relation.HOSTILE && nav.name.equalsIgnoreCase(k.other);
    }

    /** The lock is hostile and inside the reach of the ship's guns. */
    static boolean hostileInRange(HudData d) {
        TargetStatus t = d.targetStatus;
        if (!t.present || t.relation != Relation.HOSTILE || Float.isNaN(t.range)) return false;
        return t.range <= weaponsRange(d);
    }

    /** The furthest any working gun or launcher reaches, or {@link #GUN_RANGE} when none says. */
    static float weaponsRange(HudData d) {
        float best = 0f;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind == Kind.POINT_DEFENSE || !m.working() || Float.isNaN(m.range)) continue;
            best = Math.max(best, m.range);
        }
        return best > 0f ? best : GUN_RANGE;
    }

    /** The captain's suit has under {@link #SUIT_WARN} of air left, away from air fit to breathe. */
    static boolean suitAirLow(CaptainStatus c) {
        if (!c.present || !c.suited || Float.isNaN(c.suitAir) || c.suitAir >= SUIT_WARN) return false;
        return Float.isNaN(c.roomPressure) || c.roomPressure < ShipAlerts.LOW_PRESSURE;
    }

    /** The helm's instruments: flying from the helm, not docked or latched. */
    boolean flying() {
        return seat == Seat.HELM && !docked && !latched;
    }

    /** An exterior seat: the helm, fire control or a gun. */
    boolean exterior() {
        return seat == Seat.HELM || seat == Seat.FIRE_CONTROL || seat == Seat.GUN;
    }
}
