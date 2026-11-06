package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Yard Standard's map panels without a window: what they read from the lines the game writes for
 * the classic map panel (in the game's own wording), the timeline's events and labels, the contacts'
 * order, and where the console leaves the pointer to the map.
 */
class MapModeTest {

    @Test
    void timesReadAsTheGameWritesThem() {
        assertEquals(3723.0, MapText.seconds("1:02:03"), 1e-9);
        assertEquals(725.0, MapText.seconds(" 0:12:05 "), 1e-9);
        assertEquals(2.4 * 86400, MapText.seconds("2.4 d"), 1e-6);
        assertTrue(Double.isNaN(MapText.seconds("soon")));
        assertEquals("0:12:05", MapText.time(725));
        assertEquals("2.5 d", MapText.time(2.5 * 86400));
        assertEquals("T-2.4 D", MapText.tMinus("2.4 d"));
        assertEquals("2.4 d", MapText.timeIn("Enters Moon in 2.4 d"));
        assertEquals("", MapText.timeIn("Pe 397 km"));
        assertEquals("850 M", MapText.distance(850));
        assertEquals("8.2 KM", MapText.distance(8_240));
        assertEquals("8,995 KM", MapText.distance(8_995_000));
    }

    @Test
    void theOrbitNowLeavesOutAnApsisPastTheFirstBurn() {
        String text = "Around Earth: altitude 403 km, 7,670 m/s\nPe 397 km in 1:10:00\nAp 403 km in 0:24:10\n"
                + "Period 1:32:00, e 0.001\nON COURSE TO HIT EARTH";
        Array<MapText.Row> rows = new Array<>();
        String[] body = new String[1];
        int n = MapText.orbit(text, Double.NaN, rows, body);
        assertEquals("EARTH", body[0]);
        assertEquals(5, n);
        assertEquals("ALT 403 KM", rows.get(0).left);
        assertEquals("7,670 M/S", rows.get(0).right);
        assertEquals("PE 397 KM", rows.get(1).left);
        assertEquals("T-1:10:00", rows.get(1).right);
        assertEquals(4200.0, rows.get(1).at, 1e-9);
        assertEquals("PERIOD 1:32:00", rows.get(3).left);
        assertEquals("E 0.001", rows.get(3).right);
        assertTrue(rows.get(4).red);
        // A burn in half an hour: the periapsis an hour off comes after it, so it is left out
        n = MapText.orbit(text, 1800, rows, body);
        assertEquals(4, n);
        assertEquals("AP 403 KM", rows.get(1).left);
        n = MapText.orbit("Around Earth: altitude 900 km, 11,020 m/s\nPe 397 km\nEscaping, e 1.204", Double.NaN, rows, body);
        assertEquals(3, n);
        assertEquals("", rows.get(1).right);
        assertEquals("ESCAPING", rows.get(2).left);
        assertEquals("E 1.204", rows.get(2).right);
    }

    @Test
    void theSelectedBurnGivesItsCostAndTheOrbitItLeadsTo() {
        Array<MapText.Row> rows = new Array<>();
        int[] which = new int[1];
        int n = MapText.burn("Burn 1 of 2 in 0:12:05\nPrograde +3110.0, radial +0.0 m/s\n"
                + "3,110.0 m/s, about 0:05:10 on the main engine\nThen Pe 400 km, Ap 529,600 km", rows, which);
        assertEquals(1, which[0]);
        assertEquals(3, n);
        assertEquals("DV 3,110 M/S", rows.get(0).left);
        assertEquals("FIRES 0:05:10", rows.get(0).right);
        assertEquals("PE 400 KM", rows.get(1).left);
        assertEquals("AP 529,600 KM", rows.get(2).left);
        n = MapText.burn("Burn 2 of 2 in 2.9 d\nPrograde -846.0, radial +80.0 m/s\n850.0 m/s\nThen Pe 12 km, escaping, IMPACT", rows, which);
        assertEquals(2, which[0]);
        assertEquals(4, n);
        assertEquals("", rows.get(0).right);
        assertEquals("ESCAPING", rows.get(2).left);
        assertTrue(rows.get(3).red);
        assertEquals(0, MapText.burn("", rows, which));
        assertEquals(0, which[0]);
    }

    @Test
    void thePlansLinesSayWhatTheHelmAndAutopilotDoAndEachBurnsDeltaV() {
        MapText.Plan plan = new MapText.Plan();
        MapText.plan("Meets Moon in 4.1 d, Moon Pe 1,234 km\nTarget: Shackleton Ice  384,200 km  closing 12.3 m/s\n"
                + "Closest 0.6 km in 3.3 d, at 45 m/s\nNext burn in 0:12:05: 3110 m/s   B warps there\n"
                + "Then burns in 2.9 d (850 m/s), 3.0 d (12 m/s)\nHelm: flying the planned burns\n"
                + "Autopilot to Shackleton Ice: next burn in 0:12:05\nDelta-v needed 3,972 / left 3,100 m/s   NOT ENOUGH PROPELLANT", plan);
        assertTrue(plan.helmFlying);
        assertTrue(plan.autopilot);
        assertTrue(plan.short_);
        assertEquals(3, plan.burnDv.size);
        assertEquals(3110f, plan.burnDv.get(0), 1e-3f);
        assertEquals(850f, plan.burnDv.get(1), 1e-3f);
        assertEquals(12f, plan.burnDv.get(2), 1e-3f);
        assertEquals("Helm: flying the planned burns", plan.helm);
        assertFalse(plan.helmWaits);
        // Days on the next burn too; the helm has the order, though it waits for the player to hand over
        MapText.plan("Next burn in 2.4 d: 1,200 m/s   B warps there\n"
                + "Helm order (flying the planned burns) waits: you have the helm (V hands it over)", plan);
        assertEquals(1, plan.burnDv.size);
        assertEquals(1200f, plan.burnDv.get(0), 1e-3f);
        assertTrue(plan.helmFlying);
        assertTrue(plan.helmWaits);
        assertFalse(plan.autopilot);
        assertFalse(plan.short_);
        assertTrue(plan.helm.startsWith("Helm order"));
    }

    @Test
    void theTimelinePutsEveryEventThatSaysWhenInOrder() {
        OrbitPath path = new OrbitPath();
        path.nodeLabels.add("1: 0:12:05");
        path.nodeLabels.add("2: 2.9 d");
        path.addMark(MarkKind.SOI_ENTRY, 0, 0, "Enters Moon in 2.3 d");
        path.addMark(MarkKind.PERIAPSIS, 0, 0, "Moon Pe 1,234 km in 2.6 d");
        path.addMark(MarkKind.PERIAPSIS, 0, 0, "Pe 397 km");
        path.addMark(MarkKind.IMPACT, 0, 0, "Impact: Moon in 3.5 d");
        path.addMark(MarkKind.GHOST, 0, 0, "Moon");
        Array<MapText.Row> rows = new Array<>();
        int orbit = MapText.orbit("Around Earth: altitude 403 km, 7,670 m/s\nPe 397 km in 0:05:00\nAp 403 km in 0:50:00", Double.NaN, rows, new String[1]);
        Array<MapText.Event> events = new Array<>();
        int n = MapText.events(path.nodeLabels, rows, orbit, path.marks, 3.3 * 86400, "0.6 KM", events);
        assertEquals(7, n);
        // The periapsis in five minutes comes before the first burn; the apoapsis after it is left out
        assertEquals("PE", events.get(0).name);
        assertEquals("BURN 1", events.get(1).name);
        assertEquals("T-0:12:05", events.get(1).when);
        assertEquals(0, events.get(1).burn);
        assertEquals("ENTERS MOON", events.get(2).name);
        assertEquals("MOON PE", events.get(3).name);
        assertEquals("BURN 2", events.get(4).name);
        assertEquals(1, events.get(4).burn);
        assertEquals("CLOSEST 0.6 KM", events.get(5).name);
        assertTrue(events.get(5).closest);
        assertEquals("IMPACT MOON", events.get(6).name);
        assertTrue(events.get(6).red);
    }

    @Test
    void labelsKeepClearOfTheOneBeforeAndOneThatRunsOutOfRoomIsLeftOut() {
        float[] want = {0f, 10f, 200f, 950f}, width = {40f, 40f, 40f, 80f}, out = new float[4];
        MapText.spread(want, width, 4, 5f, 1000f, 10f, out);
        assertEquals(5f, out[0], 1e-4f);
        assertEquals(55f, out[1], 1e-4f);
        assertEquals(200f, out[2], 1e-4f);
        // Pulled back to end at the edge
        assertEquals(920f, out[3], 1e-4f);
        float[] tight = {0f, 0f, 0f}, w2 = {60f, 60f, 60f}, o2 = new float[3];
        MapText.spread(tight, w2, 3, 0f, 130f, 10f, o2);
        assertEquals(0f, o2[0], 1e-4f);
        assertEquals(70f, o2[1], 1e-4f);
        assertTrue(Float.isNaN(o2[2]));
    }

    @Test
    void theContactsGoNearestTheShipFirstOrByKindAndLeaveTheShipOut() {
        TrafficFrame f = new TrafficFrame();
        contact(f, "Gateway", ContactKind.STATION, 50_000, 0);
        contact(f, "You", ContactKind.PLAYER, 10_000, 0);
        contact(f, "Kestrel", ContactKind.HAULER, 12_000, 0);
        contact(f, "Raider", ContactKind.HOSTILE, 10_000, 3_000);
        contact(f, "Highport", ContactKind.STATION, 9_000, 0);
        IntArray order = new IntArray();
        FloatArray dist = new FloatArray();
        MapText.order(f.contacts, false, order, dist);
        assertEquals(4, order.size);
        assertEquals("Highport", f.contacts.get(order.get(0)).name);
        assertEquals("Kestrel", f.contacts.get(order.get(1)).name);
        assertEquals("Raider", f.contacts.get(order.get(2)).name);
        assertEquals("Gateway", f.contacts.get(order.get(3)).name);
        assertEquals(1000f, dist.get(order.get(0)), 1e-3f);
        MapText.order(f.contacts, true, order, dist);
        assertEquals("Highport", f.contacts.get(order.get(0)).name);
        assertEquals("Gateway", f.contacts.get(order.get(1)).name);
        assertEquals("Kestrel", f.contacts.get(order.get(2)).name);
        assertEquals("Raider", f.contacts.get(order.get(3)).name);
    }

    @Test
    void theConsoleLeavesTheWindowToTheMapAndKeepsTheRest() {
        MapMode m = new MapMode();
        m.layout(1280f, 800f, 0f);
        m.visible = true;
        // The bays a little over 300 wide, the window between, the timeline and the foot under it
        assertEquals(316f, m.winX, 1f);
        assertEquals(648f, m.winW, 2f);
        assertEquals(38f, m.winY, 1e-3f);
        assertTrue(m.winY + m.winH < 800f - MapMode.FOOT_H - MapMode.TIMELINE_H);
        assertFalse(m.contains(640f, 300f));
        assertTrue(m.contains(100f, 300f));
        assertTrue(m.contains(1200f, 300f));
        assertTrue(m.contains(640f, 10f));
        assertTrue(m.contains(640f, 790f));
        // Under a HUD's status strip it starts below it
        m.layout(1280f, 800f, 26f);
        assertFalse(m.contains(640f, 10f));
        assertEquals(64f, m.winY, 1e-3f);
        m.visible = false;
        assertFalse(m.contains(100f, 300f));
        // Wide screens keep the bays at most 380
        m.layout(1920f, 1080f, 0f);
        assertEquals(396f, m.winX, 1e-3f);
    }

    @Test
    void anOrderThatCannotActSaysWhy() {
        MapMode m = new MapMode();
        assertNotNull(m.refusal(MapMode.WARP, false, true, false));
        assertNull(m.refusal(MapMode.WARP, true, false, false));
        assertNotNull(m.refusal(MapMode.AUTO, true, false, false));
        assertNull(m.refusal(MapMode.AUTO, false, true, false));
        assertNotNull(m.refusal(MapMode.ACCEPT, true, true, false));
        assertNull(m.refusal(MapMode.DISCARD, true, true, true));
        assertNotNull(m.refusal(MapMode.DELETE, true, true, true));
        assertNull(m.refusal(MapMode.NEXT, false, false, false));
    }

    @Test
    void theNavCardSaysWhatTheTargetIsAndWhose() {
        NavReadout n = new NavReadout();
        n.present = true;
        n.kind = NavigationProvider.Kind.STATION;
        n.station.present = true;
        n.station.owner = "Free Traders";
        assertEquals("STATION · FREE TRADERS", MapMode.detail(n));
        n.station.clear();
        n.kind = NavigationProvider.Kind.SHIP;
        n.relation = Relation.HOSTILE;
        assertEquals("SHIP · HOSTILE", MapMode.detail(n));
        n.relation = Relation.UNKNOWN;
        assertEquals("SHIP", MapMode.detail(n));
        assertEquals("03960", MapMode.figures(3960));
        assertEquals("-846", MapMode.whole(-846.4));
        assertEquals("3,110", MapMode.whole(3110));
    }

    @Test
    void theScreensShowTheMapsPanelsAndLeaveTheDeckConsoleToTheGame() {
        WindowScreens s = new WindowScreens();
        assertTrue(s.replaces(ClassicWindow.MAP));
        assertTrue(s.replaces(ClassicWindow.TRADE));
        assertFalse(s.replaces(ClassicWindow.CONSOLE));
        // Not attached: nothing opens
        assertFalse(s.open(ClassicWindow.MAP));
        assertFalse(s.showing(ClassicWindow.MAP));
    }

    private static void contact(TrafficFrame f, String name, ContactKind kind, double x, double y) {
        Contact c = f.addContact();
        c.name = name;
        c.kind = kind;
        c.x = x;
        c.y = y;
        c.id = name;
    }
}
