package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;

/**
 * What every unit of the context Cassette HUD reads this frame: the data, where the captain is (the
 * seat, spec 4.1) and the situations that hold (4.2), each with its entry and exit rule so that units
 * don't flicker as a reading crosses a line.
 */
final class ContextFrame {
    /** Where the captain is (4.1). */
    enum Seat {
        /** No ship and no avatar: nothing is drawn. */
        NONE,
        HELM,
        /** The weapons console. */
        FIRE,
        GUN,
        /** On foot aboard any craft, or outside in a suit. */
        DECK
    }

    /** Suit air under this, away from breathable air, is the captain's emergency (AirWatch.WARN_AT). */
    static final float SUIT_WARN_AT = 240f;
    /** The approach starts within this of a free port, and ends beyond {@link #APPROACH_LEAVE}. */
    static final float APPROACH_ENTER = 5_000f, APPROACH_LEAVE = 5_500f;
    /** The approach outlasts its trigger by this, and combat outlasts its causes by {@link #COMBAT_HOLD}. */
    static final float APPROACH_HOLD = 3f, COMBAT_HOLD = 10f;

    HudData data;
    /** Seconds the HUD has been up, real time. */
    float time;
    Seat seat = Seat.NONE;
    /** Aboard a craft not the player's own: the rail names it ("DECK · Highport"). */
    String aboard = "";
    /** The map is open: everything here is under it. */
    boolean map;
    /** The stage's size in UI units, so a unit on the right rail can find the screen's edge. */
    float screenW = 1280f, screenH = 800f;

    // The situations (4.2)
    boolean warp, approach, docked, latched, combat, boarding, emergency, guided;
    /** Disabled, and not merely waiting for power that is due back by itself (X3, 11.7). */
    boolean disabled;
    /** No working reactor feeds the ship. */
    boolean blackout;
    /** The power is due back by itself: every reactor is stunned or shorted on a timer. */
    boolean powerDueBack;
    /** The captain's suit air under 240 s away from breathable air (X3's first state). */
    boolean suitCritical;
    /** The hull has a shield emitter (footnote 3). */
    boolean emitter;
    /** Latched to another ship or within reach of a beaten one: the boarding board. */
    final BoardingBoard board = new BoardingBoard();
    boolean boardKnown;

    private float approachSince = -1f, combatQuiet = -1f;

    void read(HudData d, boolean mapOpen, BoardingBoard boardingBoard, float delta) {
        data = d;
        time += delta;
        map = mapOpen;
        UiContext c = d.ctx;
        Presence p = c.presence;
        if (p == Presence.NONE || c.ship == null) seat = Seat.NONE;
        else if (p == Presence.HELM) seat = c.exteriorView ? Seat.HELM : Seat.DECK;
        else if (p == Presence.WEAPONS) seat = c.exteriorView ? Seat.FIRE : Seat.DECK;
        else if (p == Presence.GUN) seat = c.exteriorView ? Seat.GUN : Seat.DECK;
        // Outside in a suit is the deck's, whatever the view (4.1)
        else if (p == Presence.EVA) seat = Seat.DECK;
        // -PviewAnywhere: the outside view from the deck is the helm's (4.1)
        else seat = c.exteriorView ? Seat.HELM : Seat.DECK;
        aboard = p == Presence.ABOARD_OTHER ? otherCraft(d) : "";

        warp = c.warp > 1;
        docked = c.docked;
        latched = c.latched;
        emitter = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        blackout = d.powerFrame.ratedWatts > 0 && d.powerFrame.outputWatts <= 0;
        powerDueBack = blackout && reactorsDueBack(d);
        disabled = !c.disabledReason.isEmpty() && !powerDueBack;
        suitCritical = suitCritical(d.captainStatus);
        emergency = disabled || c.abandoning || d.orderState.abandoning || suitCritical;
        guided = !d.guideText.isEmpty() && !d.guideDone;

        boardKnown = boardingBoard != null;
        board.clear();
        if (boardingBoard != null) copy(boardingBoard, board);
        // Latched, or a beaten craft in reach on EVA: a boarding, not a fight
        boarding = !board.craftName.isEmpty() && (latched || board.disabled || board.taken || board.boarding);

        approach(d);
        combat(d, delta);
    }

    /** Approach (4.2): a free port within 5 km and the nav on that craft, a port armed, or a dock order. */
    private void approach(HudData d) {
        DockingApproach k = d.docking;
        boolean inRange = k.present && !Float.isNaN(k.range) && k.capture != Capture.DOCKED && !docked && !latched;
        HelmOrder order = d.orderState.helm;
        boolean trigger = inRange && (k.armed || k.clearance == Clearance.GRANTED || k.capture == Capture.CAPTURED
                || order == HelmOrder.DOCK || order == HelmOrder.APPROACH
                || d.navReadout.present && !k.other.isEmpty() && k.other.equalsIgnoreCase(d.navReadout.name));
        if (!inRange || k.range > APPROACH_LEAVE) {
            approach = false;
            approachSince = -1f;
        } else if (trigger && k.range <= APPROACH_ENTER) {
            approach = true;
            approachSince = time;
        } else if (approach && !trigger && time - approachSince > APPROACH_HOLD) {
            approach = false;
        }
    }

    /** Combat (4.2): battle stations, missiles inbound or a hostile engaged; it ends 10 s after the last. */
    private void combat(HudData d, float delta) {
        boolean cause = d.orderState.battleStations || d.ctx.missilesInbound > 0 || d.ctx.combat
                || d.targetStatus.present && d.targetStatus.firingOnUs;
        if (cause) {
            combat = true;
            combatQuiet = 0f;
        } else if (combat) {
            combatQuiet += delta;
            if (combatQuiet > COMBAT_HOLD) combat = false;
        }
    }

    private static boolean reactorsDueBack(HudData d) {
        boolean any = false;
        for (int i = 0; i < d.powerFrame.faults.size; i++) {
            SystemRow r = d.powerFrame.faults.get(i);
            if (!"REACTOR".equals(r.system)) continue;
            any = true;
            boolean timed = (r.fault == Fault.SHORTED || r.fault == Fault.STUNNED) && !Float.isNaN(r.timer);
            if (!timed) return false;
        }
        return any;
    }

    /** Suit air under 240 s, away from breathable air (X3): the room's air thin or gone. */
    static boolean suitCritical(CaptainStatus c) {
        if (!c.present || !c.suited || Float.isNaN(c.suitAir)) return false;
        boolean away = Float.isNaN(c.roomPressure) || c.roomPressure < 0.5f || !Float.isNaN(c.roomOxygen) && c.roomOxygen < 0.5f;
        return away && c.suitAir < SUIT_WARN_AT;
    }

    /** The craft the captain has walked aboard: the docked station, the latched ship, or "". */
    private static String otherCraft(HudData d) {
        if (d.ctx.docked && !d.ctx.stationName.isEmpty()) return d.ctx.stationName;
        if (!d.docking.other.isEmpty()) return d.docking.other;
        return "";
    }

    private static void copy(BoardingBoard from, BoardingBoard to) {
        to.craftName = from.craftName;
        to.state = from.state;
        to.boardRefusal = from.boardRefusal;
        to.stormRefusal = from.stormRefusal;
        to.byEva = from.byEva;
        to.boarding = from.boarding;
        to.salvage = from.salvage;
        to.taken = from.taken;
        to.disabled = from.disabled;
        to.aboard = from.aboard;
        to.defenders = from.defenders;
        to.prisoners = from.prisoners;
        to.salvageRefusal = from.salvageRefusal;
        for (int i = 0; i < from.parts.size; i++) {
            com.ringsofiron.uihud.providers.BoardingProvider.Part s = from.parts.get(i), t = to.addPart();
            t.x = s.x;
            t.y = s.y;
            t.name = s.name;
            t.value = s.value;
            t.status = s.status;
            t.marked = s.marked;
            t.toggleable = s.toggleable;
            t.off = s.off;
        }
        for (int i = 0; i < from.cargo.size; i++) {
            com.ringsofiron.uihud.providers.BoardingProvider.Cargo s = from.cargo.get(i), t = to.addCargo();
            t.commodity = s.commodity;
            t.units = s.units;
            t.onOrder = s.onOrder;
        }
    }

    boolean seated() {
        return seat == Seat.HELM || seat == Seat.FIRE || seat == Seat.GUN;
    }

    /** Docked at a station or latched to a ship: the flight instruments retire (8). */
    boolean berthed() {
        return docked || latched;
    }

    boolean deck() {
        return seat == Seat.DECK;
    }
}
