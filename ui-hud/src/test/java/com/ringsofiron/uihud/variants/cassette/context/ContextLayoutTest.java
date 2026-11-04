package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.variants.cassette.context.ContextFrame.Seat;
import com.ringsofiron.uihud.variants.cassette.context.ContextLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The context Cassette HUD's layout (framework rule 3, Variants.md; spec 5.1 and 5.2): at 1280 x 800, at the
 * largest scale a 1080p window allows (1422 x 800), at 1080p's own 1920 x 1080, at a 5:4 window and at
 * 2048 x 1152, at every seat and with any mix of units the visibility rules can put up at the heights they
 * can take, no two units overlap, all stay on screen, and nothing fixed enters the clear centre (the prompt
 * and the orders panel may, 5.1); a unit coming or going in one zone never moves a unit in another.
 */
class ContextLayoutTest {
    private static final float[][] SIZES = {{1280f, 800f}, {1422f, 800f}, {1920f, 1080f}, {1280f, 1024f}, {2048f, 1152f}};
    private static final Seat[] SEATS = {Seat.HELM, Seat.FIRE, Seat.GUN, Seat.DECK};
    private static final String[] NAMES = {"rail", "caution", "threat", "placard", "intercom", "clipboard", "nav",
            "threat list", "lock", "armament", "station", "boarding", "condition", "shield", "suit", "instrument",
            "scope", "cue", "prompt", "orders"};
    /** The units by zone, as indices into {@link #NAMES}: a unit in one never moves one in another. */
    private static final int[][] ZONES = {{0}, {1}, {2, 3}, {4}, {5, 6, 7}, {8, 9, 10, 11}, {12, 13, 14}, {16},
            {15, 17, 18, 19}};

    @Test
    void nothingOverlapsOrLeavesTheScreenAtAnySeat() {
        Random rnd = new Random(20261104L);
        int checked = 0;
        for (float[] size : SIZES) {
            for (Seat seat : SEATS) {
                for (int i = 0; i < 4000; i++) {
                    ContextLayout l = situation(rnd, size, seat);
                    l.solve();
                    check(l, seat + " #" + i + " at " + (int) size[0] + "x" + (int) size[1]);
                    checked++;
                }
                // And everything the seat can show at its tallest at once
                ContextLayout l = tallest(size, seat);
                l.solve();
                check(l, seat + " at its tallest at " + (int) size[0] + "x" + (int) size[1]);
            }
        }
        assertTrue(checked >= 80_000, "checked " + checked);
    }

    @Test
    void aUnitComingOrGoingNeverMovesAnotherZone() {
        Random rnd = new Random(7L);
        for (float[] size : SIZES) {
            for (Seat seat : SEATS) {
                for (int i = 0; i < 600; i++) {
                    long seed = rnd.nextLong();
                    ContextLayout a = situation(new Random(seed), size, seat);
                    a.solve();
                    // The same situation with one unit gone
                    for (int unit = 1; unit < NAMES.length; unit++) {
                        ContextLayout b = situation(new Random(seed), size, seat);
                        if (!drop(b, unit)) continue;
                        b.solve();
                        Rect[] ra = rects(a), rb = rects(b);
                        int zone = zoneOf(unit);
                        for (int j = 0; j < NAMES.length; j++) {
                            if (zoneOf(j) == zone) continue;
                            if (!same(ra[j], rb[j])) {
                                fail("taking away the " + NAMES[unit] + " moved the " + NAMES[j] + " from " + ra[j] + " to " + rb[j]
                                        + ", " + seat + " at " + (int) size[0] + "x" + (int) size[1]);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void theZonesStandWhereTheSpecPutsThemAt1280By800() {
        ContextLayout l = tallest(new float[]{1280f, 800f}, Seat.HELM);
        l.armamentH = 0f;
        l.stationH = 0f;
        l.boardingH = 0f;
        l.ordersH = l.ordersW = 0f;
        l.solve();
        assertRect(l.rail, 0, 0, 1280, 26);
        assertEquals(12f, l.caution.x);
        assertEquals(38f, l.caution.y);
        assertEquals(390f, l.threat.x, "the top centre is 390..890");
        assertEquals(960f, l.intercom.x);
        assertEquals(194f, l.clipboard.y, "the left middle starts at 194");
        assertEquals(194f, l.lock.y);
        assertEquals(960f, l.lock.x);
        assertEquals(788f, l.condition.bottom(), "the left foot stands on the coaming");
        assertEquals(1268f, l.scope.right(), "the scope is right-aligned");
        assertEquals(440f, l.instrument.x, "the flight pod is centred under the ship");
        assertEquals(788f, l.instrument.bottom());
        Rect clear = l.clear(new Rect());
        assertRect(clear, 360, 220, 560, 370);
    }

    @Test
    void theNavigationTubeTakesItsCompactFaceWhenTheLeftMiddleIsFull() {
        ContextLayout l = new ContextLayout();
        l.seat = Seat.HELM;
        l.clipboardH = 170f;
        l.navH = 176f;
        l.navCompactH = 36f;
        l.solve();
        assertFalse(l.navCompacted, "170 + 6 + 176 fits 358");
        l.clipboardH = 190f;
        l.solve();
        assertTrue(l.navCompacted);
        assertEquals(36f, l.nav.h);
        assertEquals(l.clipboard.bottom() + ContextLayout.GAP, l.nav.y);
    }

    @Test
    void theMasterCueRidesItsBracketOverTheRacksRightEnd() {
        ContextLayout l = new ContextLayout();
        l.seat = Seat.FIRE;
        l.instrumentW = WeaponsRack.W;
        l.instrumentH = 148f;
        l.cueBracket = true;
        l.cueW = MasterCue.W;
        l.cueH = MasterCue.H;
        l.solve();
        assertEquals(l.instrument.right() - 20f, l.cue.right());
        assertEquals(l.instrument.y - ContextLayout.GAP, l.cue.bottom());
        // On deck it stands alone at the bottom centre, and the prompt over it
        l = new ContextLayout();
        l.seat = Seat.DECK;
        l.cueAlone = true;
        l.cueW = MasterCue.W;
        l.cueH = MasterCue.H;
        l.promptW = PromptPlate.W;
        l.promptH = PromptPlate.H;
        l.solve();
        assertEquals(788f, l.cue.bottom());
        assertEquals(l.cue.y - ContextLayout.GAP, l.prompt.bottom());
    }

    @Test
    void theOrdersPanelStandsOnTheInstrumentAndTheSideColumnsStayClear() {
        for (float[] size : SIZES) {
            ContextLayout l = new ContextLayout();
            l.width = size[0];
            l.height = size[1];
            l.seat = Seat.HELM;
            l.instrumentW = FlightPod.W;
            l.instrumentH = FlightPod.H;
            l.ordersW = OrdersPanel.W;
            l.ordersH = OrdersPanel.H;
            l.promptW = PromptPlate.W;
            l.promptH = PromptPlate.H;
            l.solve();
            assertEquals(l.instrument.y, l.orders.bottom());
            assertFalse(l.prompt.on, "the panel takes the prompt's place");
            assertTrue(l.orders.x >= ContextLayout.MARGIN + ContextLayout.COL_W, "clear of the left column");
            assertTrue(l.orders.right() <= l.width - ContextLayout.MARGIN - ContextLayout.COL_W, "clear of the right column");
        }
    }

    // --- Situations -----------------------------------------------------------------------------------

    /** A situation the visibility rules can make at {@code seat}, each unit up or not, at a height it can take. */
    private static ContextLayout situation(Random r, float[] size, Seat seat) {
        ContextLayout l = new ContextLayout();
        l.width = size[0];
        l.height = size[1];
        l.seat = seat;
        boolean helm = seat == Seat.HELM, fire = seat == Seat.FIRE, gun = seat == Seat.GUN, deck = seat == Seat.DECK;
        l.cautionH = pick(r, 0f, CautionBoard.height(1), CautionBoard.height(3), CautionBoard.height(9));
        l.threatH = pick(r, 0f, ThreatUnit.H);
        float placardRoom = ContextLayout.TC_H - (l.threatH > 0f ? l.threatH + ContextLayout.GAP : 0f);
        l.placardH = pick(r, 0f, Math.min(placardRoom, 78f), Math.min(placardRoom, 166f));
        l.intercomH = pick(r, 0f, Intercom.height(1), Intercom.height(3), Intercom.height(1 + Intercom.NEWEST_ROWS + 1));
        l.clipboardH = pick(r, 0f, Clipboard.COMPACT_H, 100f, 190f);
        if (helm) {
            l.navH = pick(r, 0f, 36f, 96f, 176f);
            l.navCompactH = l.navH > 36f ? 36f : 0f;
        }
        if (fire || gun) l.threatListH = pick(r, 0f, 43f, ThreatList.MAX_H);
        l.lockH = deck ? 0f : helm ? pick(r, 0f, LockCard.H_COMPACT) : pick(r, 0f, LockCard.H_FULL);
        l.armamentH = helm ? pick(r, 0f, ArmamentPlate.H) : 0f;
        l.stationH = helm || deck ? pick(r, 0f, 168f, 264f) : 0f;
        l.boardingH = pick(r, 0f, 96f, BoardingPanel.MAX_H);
        // The left foot: the condition unit's faces, shield control beside it at fire control, the suit on deck
        if (deck) {
            l.suitH = pick(r, 0f, SuitUnit.H);
            float room = ContextLayout.BOTTOM_H - (l.suitH > 0f ? l.suitH + ContextLayout.GAP : 0f);
            l.conditionH = pick(r, 0f, Math.min(room, 72f), room);
        } else if (fire && r.nextBoolean()) {
            l.conditionH = pick(r, ConditionUnit.STRIP_H, 130f);
            l.shieldH = ShieldControl.measure(ContextLayout.BOTTOM_H - l.conditionH - ContextLayout.GAP);
        } else {
            l.conditionH = pick(r, ConditionUnit.STRIP_H, 136f, ContextLayout.BOTTOM_H);
        }
        // The instrument and the cue
        boolean pod = false, rack = false;
        if (helm) {
            int k = r.nextInt(4);
            if (k == 0) {
                l.instrumentW = FlightPod.W;
                l.instrumentH = FlightPod.H;
            } else if (k == 1) {
                l.instrumentW = FlightPod.W;
                l.instrumentH = FlightPod.WARP_H;
            } else if (k == 2) {
                l.instrumentW = DockingPod.W;
                l.instrumentH = DockingPod.H;
            }
            pod = k < 3;
        } else if (fire || gun) {
            rack = r.nextInt(8) > 0;
            if (rack) {
                l.instrumentW = WeaponsRack.W;
                l.instrumentH = pick(r, 44f, 96f, 148f, 144f);
            }
        }
        l.cueBracket = rack;
        l.cueAlone = !pod && !rack && r.nextBoolean();
        if (l.cueBracket || l.cueAlone) {
            l.cueW = MasterCue.W;
            l.cueH = MasterCue.H;
        }
        if (seat != Seat.GUN && seat != Seat.FIRE && r.nextBoolean()) {
            l.promptW = PromptPlate.W;
            l.promptH = PromptPlate.H;
        }
        if (r.nextInt(4) == 0) {
            l.ordersW = OrdersPanel.W;
            l.ordersH = OrdersPanel.H;
            l.promptW = l.promptH = 0f;
        }
        // The scope, at a seat
        if (!deck) {
            int k = r.nextInt(3);
            if (k == 1) {
                l.scopeW = 64f;
                l.scopeH = Scope.BUTTON;
            } else if (k == 2) {
                l.scopeW = Scope.W;
                l.scopeH = Scope.H;
            }
        }
        return l;
    }

    /** Every unit {@code seat} can show, at its tallest. */
    private static ContextLayout tallest(float[] size, Seat seat) {
        ContextLayout l = new ContextLayout();
        l.width = size[0];
        l.height = size[1];
        l.seat = seat;
        l.cautionH = CautionBoard.height(9);
        l.threatH = ThreatUnit.H;
        l.placardH = ContextLayout.TC_H - ThreatUnit.H - ContextLayout.GAP;
        l.intercomH = Intercom.height(Intercom.NEWEST_ROWS + 2);
        l.clipboardH = 190f;
        l.lockH = seat == Seat.HELM ? LockCard.H_COMPACT : seat == Seat.DECK ? 0f : LockCard.H_FULL;
        l.boardingH = BoardingPanel.MAX_H;
        if (seat == Seat.HELM) {
            l.navH = 176f;
            l.navCompactH = 36f;
            l.armamentH = ArmamentPlate.H;
            l.stationH = 264f;
            l.instrumentW = FlightPod.W;
            l.instrumentH = FlightPod.H;
            l.conditionH = ContextLayout.BOTTOM_H;
            l.promptW = PromptPlate.W;
            l.promptH = PromptPlate.H;
        } else if (seat == Seat.DECK) {
            l.stationH = 264f;
            l.suitH = SuitUnit.H;
            l.conditionH = ContextLayout.BOTTOM_H - SuitUnit.H - ContextLayout.GAP;
            l.cueAlone = true;
            l.cueW = MasterCue.W;
            l.cueH = MasterCue.H;
            l.promptW = PromptPlate.W;
            l.promptH = PromptPlate.H;
        } else {
            l.threatListH = ThreatList.MAX_H;
            l.instrumentW = WeaponsRack.W;
            l.instrumentH = 148f;
            l.cueBracket = true;
            l.cueW = MasterCue.W;
            l.cueH = MasterCue.H;
            l.conditionH = 130f;
            l.shieldH = seat == Seat.FIRE ? ShieldControl.measure(ContextLayout.BOTTOM_H - 130f - ContextLayout.GAP) : 0f;
        }
        if (seat != Seat.DECK) {
            l.scopeW = Scope.W;
            l.scopeH = Scope.H;
        }
        return l;
    }

    private static float pick(Random r, float... values) {
        return values[r.nextInt(values.length)];
    }

    /** Takes unit {@code i} away; false when it was not up. */
    private static boolean drop(ContextLayout l, int i) {
        switch (i) {
            case 1: return zero(l.cautionH > 0f, () -> l.cautionH = 0f);
            case 2: return zero(l.threatH > 0f, () -> l.threatH = 0f);
            case 3: return zero(l.placardH > 0f, () -> l.placardH = 0f);
            case 4: return zero(l.intercomH > 0f, () -> l.intercomH = 0f);
            case 5: return zero(l.clipboardH > 0f, () -> l.clipboardH = 0f);
            case 6: return zero(l.navH > 0f, () -> l.navH = l.navCompactH = 0f);
            case 7: return zero(l.threatListH > 0f, () -> l.threatListH = 0f);
            case 8: return zero(l.lockH > 0f, () -> l.lockH = 0f);
            case 9: return zero(l.armamentH > 0f, () -> l.armamentH = 0f);
            case 10: return zero(l.stationH > 0f, () -> l.stationH = 0f);
            case 11: return zero(l.boardingH > 0f, () -> l.boardingH = 0f);
            case 12: return zero(l.conditionH > 0f, () -> l.conditionH = 0f);
            case 13: return zero(l.shieldH > 0f, () -> l.shieldH = 0f);
            case 14: return zero(l.suitH > 0f, () -> l.suitH = 0f);
            case 15: return zero(l.instrumentH > 0f, () -> l.instrumentH = l.instrumentW = 0f);
            case 16: return zero(l.scopeH > 0f, () -> l.scopeH = l.scopeW = 0f);
            case 17: return zero(l.cueH > 0f, () -> l.cueH = l.cueW = 0f);
            case 18: return zero(l.promptH > 0f, () -> l.promptH = l.promptW = 0f);
            case 19: return zero(l.ordersH > 0f, () -> l.ordersH = l.ordersW = 0f);
            default: return false;
        }
    }

    private static boolean zero(boolean up, Runnable off) {
        if (up) off.run();
        return up;
    }

    private static int zoneOf(int unit) {
        for (int z = 0; z < ZONES.length; z++) for (int u : ZONES[z]) if (u == unit) return z;
        throw new IllegalArgumentException("unit " + unit);
    }

    private static Rect[] rects(ContextLayout l) {
        return new Rect[]{l.rail, l.caution, l.threat, l.placard, l.intercom, l.clipboard, l.nav, l.threatList, l.lock,
                l.armament, l.station, l.boarding, l.condition, l.shield, l.suit, l.instrument, l.scope, l.cue, l.prompt, l.orders};
    }

    private static boolean same(Rect a, Rect b) {
        if (a.on != b.on) return false;
        return !a.on || a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h;
    }

    private static void check(ContextLayout l, String where) {
        Rect[] all = rects(l);
        Rect clear = l.clear(new Rect());
        for (int i = 0; i < all.length; i++) {
            Rect r = all[i];
            if (!r.on) continue;
            if (r.x < 0f || r.y < 0f || r.right() > l.width + 0.01f || r.bottom() > l.height + 0.01f) {
                fail(NAMES[i] + " " + r + " is off screen, " + where);
            }
            if (r.h < PromptPlate.H || r.w < Scope.BUTTON) fail(NAMES[i] + " " + r + " is too small to read, " + where);
            boolean mayEnterCentre = NAMES[i].equals("prompt") || NAMES[i].equals("orders");
            if (!mayEnterCentre && r.overlaps(clear)) fail(NAMES[i] + " " + r + " enters the clear centre " + clear + ", " + where);
            for (int j = i + 1; j < all.length; j++) {
                if (r.overlaps(all[j])) fail(NAMES[i] + " " + r + " overlaps the " + NAMES[j] + " " + all[j] + ", " + where);
            }
        }
    }

    private static void assertRect(Rect r, float x, float y, float w, float h) {
        assertTrue(r.on, "on");
        assertEquals(x, r.x, "x of " + r);
        assertEquals(y, r.y, "y of " + r);
        assertEquals(w, r.w, "w of " + r);
        assertEquals(h, r.h, "h of " + r);
    }
}
