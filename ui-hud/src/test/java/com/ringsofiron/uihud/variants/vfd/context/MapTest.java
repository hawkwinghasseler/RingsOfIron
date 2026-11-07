package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MapMarker;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.OrbitPathProvider.PathMark;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1, the map's panels: what the Plan pane, the Target and traffic pane and the timeline read from
 * the game's map at the moment of 11.1 (burn 1 planned to Shackleton Ice, the capture at the Moon an
 * estimate until the crossing plans it), and the orders a click gives.
 */
class MapTest {
    static final float H = 3600f, D = 86_400f;

    private final HudData d = new HudData();
    private final VfdContextHud hud = new VfdContextHud();
    private final Modes modes = hud.modes;
    private final MapTab tab = modes.map.tab0;
    private final List<String> orders = new ArrayList<>();
    private final OrbitPath path = new OrbitPath();
    private final TrafficFrame traffic = new TrafficFrame();

    /** The trip to Shackleton Ice as 11.1 has it: burn 1 in 12:05, the Moon's sphere at 3.3 days, the capture at 4.1, arrival 4.2. */
    private void theTripToTheIceMine() {
        path.bodyName = "Earth";
        path.nodes.add(6_778e3f, 0f);
        path.nodeIn.add(12 * 60 + 5);
        path.nodeDv.add(3_084f, 0f);
        path.selectedNode = 0;
        path.selectedPe = 397e3;
        path.selectedAp = 378_000e3;
        PathMark soi = path.addMark(MarkKind.SOI_ENTRY, 320_000e3, 10e6, "Enters Moon in 3.3 d");
        soi.in = 3.3f * D;
        soi.body = "Moon";
        PathMark pe = path.addMark(MarkKind.PERIAPSIS, 380_000e3, 2e6, "Moon Pe 100 km");
        pe.in = 4.1f * D;
        pe.body = "Moon";
        path.addMark(MarkKind.GHOST, 380_000e3, 0, "Moon").in = 4.1f * D;
        MapMarker mine = path.addMarker();
        mine.name = "Shackleton Ice";
        mine.target = true;
        mine.x = 384_000e3;
        d.navReadout.present = true;
        d.navReadout.name = "Shackleton Ice";
        d.navReadout.kind = com.ringsofiron.uihud.providers.NavigationProvider.Kind.STATION;
        d.navReadout.station.type = "Ice Mine";
        d.navReadout.around = "Moon";
        d.navReadout.distance = 384_000e3;
        d.navReadout.planDeltaV = 3_084;
        d.navReadout.onwardBody = "Moon";
        d.navReadout.onwardDeltaV = 793f;
        d.navReadout.arrivalIn = 4.2f * D;
        d.flightStatus.deltaVLeft = 5_606;
        d.orderState.helm = HelmOrder.FLY_BURNS;
        Objective o = d.objectiveFrame.add();
        o.title = "Food to Shackleton Ice";
        o.timeLeft = 5.2 * D;
        o.next = "Shackleton Ice";
        d.orbitInfo = () -> {
            OrbitInfoProvider.OrbitInfo i = new OrbitInfoProvider.OrbitInfo();
            i.bodyName = "Earth";
            i.apoapsis = 403e3;
            i.periapsis = 397e3;
            i.period = 92 * 60;
            i.peIn = 8 * 60 + 40;
            i.apIn = 54 * 60 + 40;
            return i;
        };
        traffic.frameName = "Earth";
        contact("You", ContactKind.PLAYER, 6_778e3, 0);
        contact("Deepwell Depot", ContactKind.STATION, 6_778e3, 9_800e3);
        contact("Highport", ContactKind.STATION, 6_778e3, 3e3);
        contact("Hauler Kestrel", ContactKind.HAULER, 6_778e3, 3.1e3);
        contact("Pirate raider 6", ContactKind.HOSTILE, 6_778e3, 50e3);
        d.planner = new Recorder();
        tab.read(d, path, traffic, null);
    }

    private void contact(String name, ContactKind kind, double x, double y) {
        Contact c = traffic.addContact();
        c.name = name;
        c.kind = kind;
        c.x = x;
        c.y = y;
        c.id = name;
    }

    @Test
    void theMapIsAModeWithNoTabsSoTheNumberKeysStayTheHelms() {
        assertTrue(modes.replaces(ClassicWindow.MAP));
        assertTrue(modes.open(ClassicWindow.MAP, d));
        assertSame(modes.map, modes.current);
        assertTrue(modes.showing(ClassicWindow.MAP));
        assertFalse(modes.showing(ClassicWindow.LOG));
        assertFalse(modes.keyDown(Keys.NUM_1), "1 is the helm's: helm flies burns");
        assertFalse(modes.keyDown(Keys.NUM_6), "6, the autopilot");
        assertFalse(modes.keyDown(Keys.ENTER), "Enter accepts the plan, the game's");
        assertFalse(modes.keyDown(Keys.SEMICOLON));
        assertEquals("M", modes.map.closeKey());
        // Another window's key turns from the map to its mode, and the game closes the canvas after it
        assertTrue(modes.open(ClassicWindow.HELP, d));
        assertFalse(modes.showing(ClassicWindow.MAP));
    }

    @Test
    void thePlotIsAWindowThroughTheFrameBetweenThePanesAsTheMockupHasIt() {
        modes.open(ClassicWindow.MAP, d);
        modes.map.refresh(modes.boards, d);
        modes.map.layout(1280f, 800f);
        float[] o = modes.map.frame.hole;
        assertEquals(338f, o[0], 0.01f);
        assertEquals(604f, o[2], 0.01f);
        assertEquals(106f, o[1], 0.01f);
        assertEquals(660f - 106f, o[3], 0.01f);
        assertEquals(666f, tab.timeline.y, 0.01f, "the timeline under the plot, 90 tall");
        // The plot takes no clicks: they are the map's
        FilamentActor a = new FilamentActor(f -> { }, id -> { });
        modes.map.boxes(a);
        assertEquals(-1, a.idAt(640f, 400f), "the middle of the plot");
        assertEquals(Modes.PLATE, a.idAt(640f, 50f), "the title strip");
    }

    @Test
    void thePlanPaneReadsTheOrbitTheBurnTheLegToComeAndThePlan() {
        theTripToTheIceMine();
        MapPlan p = tab.plan;
        assertEquals("EARTH", p.body);
        assertEquals("AP 403 KM       T-54:40", p.orbit[0]);
        assertEquals("PE 397 KM       T-8:40", p.orbit[1]);
        assertEquals("PERIOD 1 H 32 M", p.orbit[2]);
        assertEquals(1, p.burns);
        assertEquals("1 OF 1", p.burnsNote);
        assertEquals("1 T-12:05    3,084  3,084     0", p.burnRows[0]);
        assertEquals("THEN AT THE MOON: CAPTURE,", p.onward[0]);
        assertEquals("ABOUT 793 M/S AT T-4.1 D,", p.onward[1]);
        assertEquals("PLANNED AT THE CROSSING", p.onward[2]);
        assertEquals("BURN 1", p.selName);
        assertEquals("→ PE 397 · AP 378,000 KM", p.selLeads);
        assertEquals("MOON SOI T-3.3 D", p.selNext, "what comes after it on the path");
        assertEquals("3877", p.need, "the burn and the capture's estimate");
        assertEquals("5606", p.have);
        assertEquals("LEAVES 1,729 M/S", p.leaves);
        assertEquals("ARRIVAL T-4.2 D", p.arrival);
        assertEquals("ON", p.orderState[MapPlan.HELM]);
        assertTrue(p.orderOn[MapPlan.HELM]);
        assertEquals("OFF", p.orderState[MapPlan.AUTOPILOT]);
    }

    @Test
    void theTimelineRunsFromTheBurnToTheDeadlineTheCaptureAnEstimate() {
        theTripToTheIceMine();
        MapTimeline t = tab.timeline;
        String[] names = new String[t.events.size];
        for (int i = 0; i < names.length; i++) names[i] = t.events.get(i).name;
        assertEquals(String.join(",", "BURN 1", "MOON SOI", "CAPTURE", "ARRIVAL", "DEADLINE"), String.join(",", names),
                "in time order, the ghost left out");
        assertTrue(t.capture().estimate);
        assertEquals("T-12:05", t.events.get(0).when);
        assertEquals("T-5.2 D", t.events.get(4).when);
        assertTrue(t.events.get(4).placed, "the deadline is where its contract sends the ship");
        assertEquals(384_000e3, t.events.get(4).x, 1);
        t.place(338f, 666f, 604f);
        float prev = Float.MAX_VALUE;
        for (int i = t.events.size - 1; i >= 0; i--) {
            MapTimeline.Event e = t.events.get(i);
            assertTrue(e.labelled, e.name + " has room");
            assertTrue(e.lx + e.lw <= prev, e.name + " clears the one after it");
            assertTrue(e.lx >= t.stripX() + MapTimeline.LABELS_FROM, e.name + " clears NOW");
            prev = e.lx;
        }
        assertEquals(MapTimeline.CELLS - 1, t.events.get(4).cell, "the last event at the strip's end");
        assertTrue(t.events.get(0).cell < 8, "the burn near now, on the log scale");
    }

    @Test
    void whenTheLabelsCrowdTheApsidesGoFirst() {
        theTripToTheIceMine();
        for (int i = 0; i < 6; i++) {
            PathMark m = path.addMark(i % 2 == 0 ? MarkKind.PERIAPSIS : MarkKind.APOAPSIS, 0, 0, "");
            m.in = (i + 1) * 3 * H;
        }
        tab.read(d, path, traffic, null);
        MapTimeline t = tab.timeline;
        t.place(338f, 666f, 604f);
        for (MapTimeline.Event e : t.events) {
            if (e.kind != MapTimeline.Kind.APSIS) assertTrue(e.labelled, e.name + " keeps its label");
        }
    }

    @Test
    void theTargetPaneReadsTheNavTheTransferAndTheContactsNearestFirst() {
        theTripToTheIceMine();
        MapTraffic t = tab.traffic;
        assertEquals("SHACKLETON ICE", t.navName);
        assertEquals("ICE MINE · 384,000 KM", t.navLine);
        assertEquals("AT THE MOON · ARRIVAL T-4.2 D", t.navWhere);
        assertEquals("ACCEPT, DISCARD: NOTHING PROPOSED", t.transferNote);
        assertEquals(4, t.contacts, "every contact but the ship");
        assertEquals("HIGHPORT                 3.0 KM", t.contactRow[0]);
        assertEquals("HAULER KESTREL           3.1 KM", t.contactRow[1]);
        assertEquals("PIRATE RAIDER 6         50.0 KM", t.contactRow[2]);
        assertEquals("DEEPWELL DEPOT         9,800 KM", t.contactRow[3]);
        // By kind: the stations, then the haulers, then the pirates
        tab.act(MapTraffic.SORT, d, null);
        tab.read(d, path, traffic, null);
        assertEquals("SORT ◀ KIND ▶", t.sortWord());
        assertTrue(t.contactRow[0].startsWith("HIGHPORT") && t.contactRow[1].startsWith("DEEPWELL"), t.contactRow[1]);
        assertTrue(t.contactRow[2].startsWith("HAULER"));
    }

    @Test
    void theNavCardSaysWhereATargetIsAsTheHelmsNavLinesDo() {
        com.ringsofiron.uihud.providers.NavigationProvider.NavReadout n = d.navReadout;
        n.around = "Moon";
        n.closing = 12f;
        n.cpaDistance = 376_576e3;
        n.cpaIn = 3_900f;
        assertEquals("AT THE MOON", MapTraffic.where(n), "round another body: no closest approach, which says nothing there");
        n.arrivalIn = 4.2f * D;
        assertEquals("AT THE MOON · ARRIVAL T-4.2 D", MapTraffic.where(n));
        // Round this body: closing and the closest approach, or the closest approach and when
        n.around = "";
        n.arrivalIn = Float.NaN;
        n.cpaDistance = 2_400;
        assertEquals("CLOSING 12 M/S · CLOSEST 2.4 KM", MapTraffic.where(n));
        n.closing = -1_250f;
        n.cpaDistance = 376_576e3;
        assertEquals("CLOSEST 376,576 KM T-1 H 05 M", MapTraffic.where(n), "too long with the rate");
    }

    @Test
    void aLayerKeyTakesItsContactsOffTheListAndTheTensionStartsOff() {
        theTripToTheIceMine();
        MapTraffic t = tab.traffic;
        assertFalse(t.layer[MapTraffic.TENSION], "the tension leaves the map's face, a layer off at first");
        assertTrue(t.layer[MapTraffic.HAULERS]);
        tab.act(MapTraffic.LAYER + MapTraffic.HAULERS, d, null);
        tab.read(d, path, traffic, null);
        assertFalse(t.layer[MapTraffic.HAULERS]);
        assertEquals(3, t.contacts, "no hauler listed");
        for (int i = 0; i < t.contacts; i++) assertFalse(t.contactRow[i].startsWith("HAULER"));
    }

    @Test
    void aClickGivesTheOrderItsKeyWould() {
        theTripToTheIceMine();
        tab.act(MapPlan.ORDER + MapPlan.HELM, d, null);
        tab.act(MapPlan.ORDER + MapPlan.AUTOPILOT, d, null);
        tab.act(MapPlan.ORDER + MapPlan.WARP, d, null);
        tab.act(MapPlan.ORDER + MapPlan.DELETE, d, null);
        tab.act(MapPlan.ORDER + MapPlan.CLEAR, d, null);
        tab.act(MapPlan.BURN, d, null);
        tab.act(MapTraffic.NEXT_TARGET, d, null);
        tab.act(MapTraffic.PLAN, d, null);
        tab.act(MapTraffic.ACCEPT, d, null);
        assertEquals(List.of("helm", "autopilot", "warpToBurn", "delete", "clear", "select 0", "nextTarget", "plan"), orders,
                "Accept with nothing proposed does nothing");
        orders.clear();
        // The timeline's warps: the next burn's own, else on to the event
        tab.act(MapTab.WARP, d, null);
        tab.act(MapTab.WARP + 1, d, null);
        assertEquals(List.of("warpToBurn", "warpTo moon soi in " + Math.round(3.3f * D)), orders);
    }

    @Test
    void theKeysCapsAreTheKeyboardsLegends() {
        assertEquals("BKSP", MapPlan.cap("Delete"), "libGDX's name for Backspace");
        assertEquals("DEL", MapPlan.cap("Forward Delete"));
        assertEquals("ENTER", MapPlan.cap("Enter"));
        assertEquals(";", MapPlan.cap(";"));
        assertEquals("T-42 S", MapPlan.when(42));
        assertEquals("T-12:05", MapPlan.when(725));
        assertEquals("T-3H 05M", MapPlan.when(3 * H + 300));
        assertEquals("T-2.4 D", MapPlan.when(2.4 * D));
    }

    /** The planner, saying what it was told. */
    private final class Recorder implements ManeuverPlannerProvider {
        @Override
        public boolean addBurnNear(double x, double y, double tolerance) {
            return false;
        }

        @Override
        public void selectBurn(int index) {
            orders.add("select " + index);
        }

        @Override
        public boolean selectedBurn(double[] out) {
            return false;
        }

        @Override
        public void setSelectedBurn(double prograde, double radial) {
        }

        @Override
        public void moveSelectedNear(double x, double y) {
        }

        @Override
        public void deleteSelected() {
            orders.add("delete");
        }

        @Override
        public void clearBurns() {
            orders.add("clear");
        }

        @Override
        public void warpToBurn() {
            orders.add("warpToBurn");
        }

        @Override
        public void warpTo(double in, String what) {
            orders.add("warpTo " + what + " in " + Math.round(in));
        }

        @Override
        public void helmFlyBurns() {
            orders.add("helm");
        }

        @Override
        public void autopilot() {
            orders.add("autopilot");
        }

        @Override
        public void nextTarget() {
            orders.add("nextTarget");
        }

        @Override
        public void planToTarget() {
            orders.add("plan");
        }

        @Override
        public void acceptPlan() {
            orders.add("accept");
        }

        @Override
        public void discardPlan() {
            orders.add("discard");
        }

        @Override
        public boolean hasTarget() {
            return true;
        }

        @Override
        public boolean hasProposal() {
            return false;
        }

        @Override
        public String orbitText() {
            return "";
        }

        @Override
        public String burnText() {
            return "";
        }

        @Override
        public String planText() {
            return "";
        }
    }
}
