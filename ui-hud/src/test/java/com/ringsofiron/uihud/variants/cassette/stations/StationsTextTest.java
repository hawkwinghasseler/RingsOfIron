package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words the Ship Stations Cassette HUD puts on its tubes: lines that keep their figures when the name
 * is long, the contact list's last column, and the POWER page in a blackout.
 */
class StationsTextTest {
    @Test
    void aLongNameGivesWayBeforeTheFigures() {
        String rest = "  6.5 km  closing 106 m/s  CPA 72 m 1:10";
        String line = Hardware.squeeze("Pirate raider 6", 50, rest);
        assertEquals(50, line.length(), line);
        assertEquals("PIRATE RA…" + rest, line);
        // A cut on the space between two words leaves no space before the ellipsis
        assertEquals("PIRATE…" + rest, Hardware.squeeze("Pirate raider 6", 48, rest));
        // With room, the whole name
        assertEquals("HIGHPORT  1.2 km", Hardware.squeeze("Highport", 50, "  1.2 km"));
        // Never under six characters of name, even when the figures alone fill the tube
        assertTrue(Hardware.squeeze("Pirate raider 6", 10, rest).startsWith("PIRAT…"));
    }

    @Test
    void theReferenceLineGivesUpTheClosestApproachBeforeItIsCut() {
        // Room for all of it
        assertEquals("RAIDER  10.7 km  closing 1.9 m/s  CPA 10.2 km 7:44",
                HelmCluster.ref("Raider", 10_700, 1.9, 10_200, 464, 60));
        // A short name can't give way, so the closest approach's time goes, then the closest approach
        assertEquals("RAIDER  10.7 km  closing 1.9 m/s  CPA 10.2 km", HelmCluster.ref("Raider", 10_700, 1.9, 10_200, 464, 49));
        assertEquals("RAIDER  10.7 km  closing 1.9 m/s", HelmCluster.ref("Raider", 10_700, 1.9, 10_200, 464, 40));
        // A long name gives its letters first
        assertEquals("PIRATE RAI…  6.5 km  closing 104 m/s  CPA 17 m 1:02",
                HelmCluster.ref("Pirate raider 6", 6_500, 104, 17, 62, 51));
    }

    @Test
    void theTargetLineDropsItsRelationThenItsHullBeforeItsFigures() {
        HudData d = new HudData();
        com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus t = d.targetStatus;
        t.present = true;
        t.label = "Pirate raider 6";
        t.relation = com.ringsofiron.uihud.providers.TargetStatusProvider.Relation.HOSTILE;
        t.range = 5000f;
        t.closing = 50f;
        t.hull = 0.45f;
        assertEquals("PIRATE RAIDER 6  hostile  5.0 km  closing 50 m/s  hull 45%", TacticalCluster.target(t, 80));
        assertEquals("PIRATE RAID…  5.0 km  closing 50 m/s  hull 45%", TacticalCluster.target(t, 46));
        assertEquals("PIRATE RAIDER 6  5.0 km  closing 50 m/s", TacticalCluster.target(t, 39));
        // Its state in the game's words, never the reason's code name
        t.hull = Float.NaN;
        t.state = "NO_POWER";
        assertEquals("PIRATE RAIDER 6  hostile  5.0 km  closing 50 m/s  reactor down", TacticalCluster.target(t, 80));
        t.state = "running";
        assertEquals("PIRATE RAIDER 6  hostile  5.0 km  closing 50 m/s  breaking off", TacticalCluster.target(t, 80));
        assertEquals("surrendered", TacticalCluster.state("struck"));
        assertEquals("", TacticalCluster.state("fit"));
    }

    @Test
    void aLongValueRunsOnUnderItselfAndTheLastRowSaysThereIsMore() {
        String why = Pages.why("The autopilot needs a navigation target (Q picks one)", "The autopilot ");
        assertEquals("needs a navigation target (Q picks one)", why);
        assertEquals(java.util.List.of("needs a navigation", "target (Q picks one)"), Hardware.wrap(why, 20, 2));
        assertEquals(java.util.List.of("needs a navigation…"), Hardware.wrap(why, 20, 1));
        assertEquals(java.util.List.of("your ship can still", "fly home"), Hardware.wrap("your ship can still fly home", 20, 3));
        // A word longer than the row is cut rather than lost
        assertEquals(java.util.List.of("ABCDEFGH", "IJ"), Hardware.wrap("ABCDEFGHIJ", 8, 2));
    }

    @Test
    void theListsLastColumnIsAMissilesTimeOrTheClosingRate() {
        HudData d = new HudData();
        Contact m = d.contactFrame.add();
        m.kind = Kind.MISSILE;
        m.threat = true;
        m.range = 2000f;
        m.closing = 100f;
        assertEquals("0:20", Pages.tail(m));
        Contact raider = d.contactFrame.add();
        raider.kind = Kind.SHIP;
        raider.range = 9000f;
        raider.closing = 104.4f;
        assertEquals("+104", Pages.tail(raider));
        raider.closing = -2400f;
        assertEquals("-2k", Pages.tail(raider), "four characters at most");
        Contact port = d.contactFrame.add();
        port.kind = Kind.STATION;
        port.range = 800f;
        port.closing = 3f;
        assertEquals("", Pages.tail(port));
    }

    @Test
    void aBlackoutListsTheReactorFirstAndTheRestComeBackWithIt() {
        Array<SystemRow> rows = new Array<>();
        rows.add(row("HELM", Fault.NO_POWER));
        rows.add(row("TURRET", Fault.NO_POWER));
        rows.add(row("REACTOR", Fault.DESTROYED));
        rows.sort(Pages::reactorFirst);
        assertEquals("REACTOR", rows.first().system);
        assertEquals("reactor", Pages.clears(rows.get(1), true));
        // Out of a blackout a system without power waits on its conduit
        assertEquals("conduit", Pages.clears(rows.get(1), false));
        rows.sort(Pages::comeBack);
        assertEquals("REACTOR", rows.peek().system, "otherwise the wrecked come back last");
    }

    private static SystemRow row(String system, Fault fault) {
        SystemRow r = new SystemRow();
        r.system = system;
        r.fault = fault;
        return r;
    }
}
