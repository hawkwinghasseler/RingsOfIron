package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Page;
import com.ringsofiron.uihud.variants.vfd.stations.Pages.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagesTest {
    private final HudData d = new HudData();
    private final Pages pages = new Pages();

    private Contact contact(String label, Kind kind, Relation relation, float range) {
        Contact c = d.contactFrame.add();
        c.label = label;
        c.kind = kind;
        c.relation = relation;
        c.range = range;
        c.bearingDeg = 0f;
        return c;
    }

    /** The row's spans joined as the page shows them, a space for every cell between. */
    private static String row(Screen s, int r) {
        char[] cells = new char[s.cols];
        java.util.Arrays.fill(cells, ' ');
        for (int i = 0; i < s.spans[r]; i++) {
            String t = s.text[r][i];
            for (int k = 0; k < t.length() && s.at[r][i] + k < s.cols; k++) cells[s.at[r][i] + k] = t.charAt(k);
        }
        return new String(cells).stripTrailing();
    }

    @Test
    void contactsAreNamedByTheWordsThatTellThemApart() {
        assertEquals("RAIDE 6", Pages.contactName(contact("Pirate raider 6", Kind.SHIP, Relation.HOSTILE, 2_100f)));
        assertEquals("FREIGHT", Pages.contactName(contact("Freighter", Kind.SHIP, Relation.NEUTRAL, 3_000f)));
        assertEquals("OUR MSL", Pages.contactName(contact("OUR MISSILE", Kind.MISSILE, Relation.MISSILE, 400f)));
        assertEquals("MISSILE", Pages.contactName(contact("MISSILE", Kind.MISSILE, Relation.MISSILE, 400f)));
    }

    @Test
    void rangesTakeSixCharacters() {
        assertEquals("140 M", Pages.range6(140f));
        assertEquals("2.4 KM", Pages.range6(2_400f));
        assertEquals("123 KM", Pages.range6(123_000f));
        assertEquals("", Pages.range6(Float.NaN));
    }

    @Test
    void theSensorsListPutsTheLockAndThreatsFirstAndOpensOnTheSmallestStepThatHoldsThem() {
        d.ctx.condition = Condition.YELLOW;
        contact("Freighter", Kind.SHIP, Relation.NEUTRAL, 900f);
        Contact raider = contact("Pirate raider 6", Kind.SHIP, Relation.HOSTILE, 2_100f);
        raider.locked = true;
        Contact missile = contact("MISSILE", Kind.MISSILE, Relation.MISSILE, 3_600f);
        missile.threat = true;
        pages.read(Page.SENSORS, d);
        Screen s = pages.screen(Page.SENSORS);
        assertTrue(row(s, 0).startsWith("MISSILE"), row(s, 0));
        assertTrue(row(s, 1).startsWith("RAIDE 6 2.1 KM"), row(s, 1));
        assertTrue(s.marked[1], "the lock is marked");
        assertFalse(s.marked[0]);
        assertTrue(row(s, 2).startsWith("FREIGHT  900 M"), row(s, 2));
        // The missile at 3.6 km is the farthest that must show: the 5 km step
        assertEquals("5 KM", pages.setting(Page.SENSORS));
        // Ranges end at column 14 whatever their width
        assertTrue(row(s, 1).contains("2.1 KM"));
        assertEquals(14, row(s, 1).indexOf("2.1 KM") + "2.1 KM".length());
    }

    @Test
    void theHelmPageSaysWhoFliesWhyItWontAndKeysEveryOrder() {
        Orders o = d.orderState;
        d.ctx.presence = Presence.WEAPONS;
        d.ctx.helmsman = "Bea";
        d.ctx.helmFault = "the helm has no power";
        o.helm = HelmOrder.APPROACH;
        o.helmTarget = "Highport";
        o.helmLine = "Helm: approaching Highport to 500 m";
        for (int i = 0; i < o.helmRefusal.length; i++) o.helmRefusal[i] = "";
        o.helmRefusal[HelmOrder.DOCK.ordinal()] = "no clearance";
        pages.read(Page.HELM, d);
        Screen s = pages.screen(Page.HELM);
        assertEquals("HELM BEA" + " ".repeat(14) + "NO POWER", row(s, 0));
        assertEquals("APPROACHING HIGHPORT TO 500 M", row(s, 1));
        assertEquals("1BURNS 2MATCH 3HOLD 4APPROACH", row(s, 2));
        assertEquals("5DOCK 6AUTO 0CANCEL", row(s, 3));
        // The keys are cut out of lit blocks; the order in force is at full, a refused one ghosted
        int keys = 0;
        for (int r = 2; r < 4; r++) {
            for (int i = 0; i < s.spans[r]; i++) {
                if (!s.key[r][i]) continue;
                keys++;
                if (s.text[r][i].equals("4")) assertEquals(Drive.FULL, s.level[r][i]);
                if (s.text[r][i].equals("5")) assertTrue(s.level[r][i] < Drive.DIM);
                if (s.text[r][i].equals("1")) assertEquals(Drive.NORM, s.level[r][i]);
            }
        }
        assertEquals(7, keys);
    }

    @Test
    void anOrderThatWaitsIsNamedByWhatItWillDo() {
        Orders o = d.orderState;
        o.helm = HelmOrder.HOLD_POSITION;
        o.helmTarget = "Highport";
        o.helmLine = "Helm order (holding position on Highport) waits: you have the helm (V hands it over)";
        assertTrue(Pages.helmWaits(o.helmLine));
        assertEquals("HOLDING POSITION ON HIGHPORT", Pages.helmProgress(o));
        o.helmLine = "";
        assertEquals("HOLD POSITION HIGHPORT", Pages.helmProgress(o));
        o.helmLine = "Flight computer: flying the planned burns";
        assertEquals("FLYING THE PLANNED BURNS", Pages.helmProgress(o));
    }

    @Test
    void helmFaultsAreAWordOrTwo() {
        assertEquals("NO POWER", Pages.helmFault("the helm has no power"));
        assertEquals("WRECKED", Pages.helmFault("the helm is wrecked"));
        assertEquals("SWITCHED OFF", Pages.helmFault("the helm is switched off"));
        assertEquals("NO HELM", Pages.helmFault("no helm"));
        assertEquals("", Pages.helmFault(""));
    }

    @Test
    void everyPageKeepsToItsGrid() {
        d.ctx.presence = Presence.HELM;
        d.ctx.condition = Condition.RED;
        contact("Pirate gunship 12 of the Red Hand", Kind.SHIP, Relation.HOSTILE, 1_234_567f).locked = true;
        for (Page p : Page.values()) {
            pages.read(p, d);
            Screen s = pages.screen(p);
            for (int r = 0; r < s.rows; r++) {
                for (int i = 0; i < s.spans[r]; i++) {
                    assertTrue(s.at[r][i] + s.text[r][i].length() <= s.cols, p + " row " + r + " runs past its " + s.cols + " cells");
                }
            }
        }
    }
}
