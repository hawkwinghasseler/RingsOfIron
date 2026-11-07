package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.uihud.data.NoticeAlerts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The notice stack and log, and the feeds that bring the systems' own notices into it. */
class NoticeLogTest {
    double now;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void contractDoneAndAPromotionInTheSameFrameAreBothOnScreen() {
        NoticeLog log = new NoticeLog(() -> now);
        log.post("Contract done: Deliver water. Paid 1,000 cr", NoticeLog.Kind.IMPORTANT);
        log.post("Promoted to Lieutenant", NoticeLog.Kind.IMPORTANT);
        assertEquals(2, log.showing().size());
        assertEquals("Promoted to Lieutenant", log.showing().get(0).text, "newest on top");
    }

    @Test
    void steppingThroughTargetsShowsOnlyTheLatest() {
        NoticeLog log = new NoticeLog(() -> now);
        log.post("Undocked from Highport");
        log.replace("Navigation target", "Navigation target: Highport");
        log.replace("Navigation target", "Navigation target: Deepwell Depot");
        log.replace("Navigation target", "Navigation target cleared");
        assertEquals(2, log.showing().size(), "one target line, and the other notice stays");
        assertEquals("Navigation target cleared", log.showing().get(0).text);
        assertEquals(4, log.history().size(), "the log keeps every step");
    }

    @Test
    void eachNoticeIsHeldForItsOwnTime() {
        NoticeLog log = new NoticeLog(() -> now);
        log.post("info");
        log.update(3f);
        log.post("important", NoticeLog.Kind.IMPORTANT);
        log.update(2.5f);
        assertEquals(1, log.showing().size(), "the info notice went after five seconds");
        assertEquals("important", log.showing().get(0).text);
        log.update(6f);
        assertTrue(log.showing().isEmpty());
        assertEquals(2, log.history().size(), "the log keeps them");
    }

    @Test
    void theStackKeepsTheNewestFewAndTheLogTheLastFiveHundred() {
        NoticeLog log = new NoticeLog(() -> now);
        int last = NoticeLog.KEEP + 9;
        for (int i = 0; i <= last; i++) log.post("n" + i);
        assertEquals(500, NoticeLog.KEEP, "the books of the Task and Stations specs keep 500");
        assertEquals(NoticeLog.STACK, log.showing().size());
        assertEquals("n" + last, log.showing().get(0).text);
        assertEquals(NoticeLog.KEEP, log.history().size());
        assertEquals("n" + last, log.history().get(0).text);
        assertEquals("n10", log.history().get(NoticeLog.KEEP - 1).text);
        assertEquals(NoticeLog.KEEP, log.log().size(), "read in place, the same");
    }

    @Test
    void theLogIsSavedWithTheGameAndNothingOnScreenComesBack() {
        NoticeLog log = new NoticeLog(() -> now);
        now = 60;
        log.post("Docked at Highport");
        now = 120;
        log.post("Promoted to Lieutenant", NoticeLog.Kind.IMPORTANT);
        log.post("Promoted to Lieutenant", NoticeLog.Kind.IMPORTANT);
        now = 180;
        log.post("Raider moves to intercept you", NoticeLog.Kind.WARNING);
        now = 190;
        log.post("The couch burned", NoticeLog.Kind.WARNING).minor = true;
        now = 200;
        NoticeLog.Notice salvo = log.post("MISSILE WARNING: 1 inbound", NoticeLog.Kind.WARNING);
        salvo.alert = GameAlerts.MISSILE;
        salvo = log.revise(salvo, "MISSILE WARNING: 2 inbound");
        assertEquals(GameAlerts.MISSILE, salvo.alert, "a revision is still the alert's");
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        java.io.StringWriter out = new java.io.StringWriter();
        json.setWriter(out);
        json.writeObjectStart();
        log.write(json);
        json.writeObjectEnd();
        assertEquals("notices", log.saveKey());

        NoticeLog loaded = new NoticeLog(() -> now);
        loaded.post("from before the load");
        loaded.read(json, new com.badlogic.gdx.utils.JsonReader().parse(out.toString()));
        assertEquals(5, loaded.history().size(), "the save's log, and only it");
        assertEquals("MISSILE WARNING: 2 inbound", loaded.history().get(0).text, "newest first");
        assertEquals(GameAlerts.MISSILE, loaded.history().get(0).alert, "with the alert that carries it");
        assertEquals("The couch burned", loaded.history().get(1).text);
        assertTrue(loaded.history().get(1).minor, "news of a loss and nothing more");
        assertFalse(loaded.history().get(0).minor);
        assertEquals("Raider moves to intercept you", loaded.history().get(2).text);
        assertEquals("", loaded.history().get(2).alert);
        assertEquals(NoticeLog.Kind.WARNING, loaded.history().get(2).kind);
        assertEquals(180, loaded.history().get(2).time, 1e-9);
        assertEquals(2, loaded.history().get(3).count, "the repeat counted");
        assertEquals("Promoted to Lieutenant (x2)", loaded.history().get(3).label());
        assertEquals(NoticeLog.Kind.IMPORTANT, loaded.history().get(3).kind);
        assertEquals("Docked at Highport", loaded.history().get(4).text);
        assertTrue(loaded.showing().isEmpty(), "the log comes back, not the screen");
        loaded.reset();
        assertTrue(loaded.history().isEmpty(), "a save without a log leaves none of the last game's");
    }

    @Test
    void aRepeatIsCountedNotStacked() {
        NoticeLog log = new NoticeLog(() -> now);
        List<NoticeLog.Notice> heard = new ArrayList<>();
        log.posted.add((s, n) -> heard.add(n));
        log.post("Launcher reloading");
        log.post("Decoy away");
        log.post("Launcher reloading");
        assertEquals(2, log.showing().size());
        assertEquals("Launcher reloading (x2)", log.showing().get(0).label());
        assertEquals(2, log.history().size());
        assertEquals(3, heard.size());
        log.post(null);
        log.post("");
        assertEquals(3, heard.size(), "nothing to say is not posted");
    }

    /** Round 8's window: R pressed through one reload logged "Launcher reloading" 6 times. */
    @Test
    void reloadingIsToldOncePerReloadNotOncePerPress() {
        NoticeLog log = new NoticeLog(() -> now);
        assertNotNull(log.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO));
        for (int i = 0; i < 5; i++) {
            log.update(10f);
            assertNull(log.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO), "pressed again in the same reload");
        }
        assertEquals(1, log.history().size());
        assertEquals(NoticeLog.RELOADING, log.history().get(0).label(), "not counted either");

        log.allowAgain(NoticeLog.RELOADING); // the launcher fired
        assertNotNull(log.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO), "the next reload is told");
        assertEquals(2, log.history().size());

        log.clear(); // another game loaded
        assertNotNull(log.postOnce(NoticeLog.RELOADING, NoticeLog.Kind.INFO));
    }

    @Test
    void aRevisionCanTakeAWorseKind() {
        NoticeLog log = new NoticeLog(() -> now);
        NoticeLog.Notice light = log.post("Cole is lightly wounded", NoticeLog.Kind.INFO);
        NoticeLog.Notice critical = log.revise(light, "Cole is critically wounded", NoticeLog.Kind.WARNING);
        assertEquals(1, log.history().size());
        assertEquals(NoticeLog.Kind.WARNING, critical.kind);
        assertSame(critical, log.showing().get(0));
    }

    @Test
    void theLogIsStampedWithSimTime() {
        NoticeLog log = new NoticeLog(() -> now);
        now = 86_400 * 2 + 3600;
        log.post("hello");
        assertEquals(now, log.latest().time, 1e-9);
    }

    @Test
    void contractOutcomesAreImportantAndTakingOneIsNot() {
        assertEquals(NoticeLog.Kind.IMPORTANT, NoticeFeeds.contractKind("Contract done: x. Paid 1 cr"));
        assertEquals(NoticeLog.Kind.IMPORTANT, NoticeFeeds.contractKind("Contract failed: x. Standing -2 with y"));
        assertEquals(NoticeLog.Kind.IMPORTANT, NoticeFeeds.contractKind("Contract called off: y"));
        assertEquals(NoticeLog.Kind.INFO, NoticeFeeds.contractKind("Contract taken: x"));
        assertEquals(NoticeLog.Kind.INFO, NoticeFeeds.contractKind("Delivered 5 water; 5 to go"));
    }

    @Test
    void aWarningPostedWithWhatCarriesItKeepsItThroughRepeatsAndRevisions() {
        NoticeLog log = new NoticeLog(() -> now);
        NoticeLog.Notice n = log.post("Hull leaking", NoticeLog.Kind.WARNING, NoticeAlerts.VENTING);
        assertEquals(NoticeAlerts.VENTING, n.alert);
        assertSame(n, log.post("Hull leaking", NoticeLog.Kind.WARNING), "a repeat");
        assertEquals(NoticeAlerts.VENTING, n.alert, "a plain repeat keeps it");
        n.minor = true;
        NoticeLog.Notice r = log.revise(n, "Hull leaking and no patches left");
        assertEquals(NoticeAlerts.VENTING, r.alert);
        assertTrue(r.minor);
        assertEquals("", log.post("Docked at Highport").alert, "news");
    }

    @Test
    void theHelmsWarningsNameWhatCarriesThem() {
        assertEquals(NoticeAlerts.BURN_MISSED, Navigation.helmAlert("Burn missed: nobody flew the planned 120 m/s burn in time"));
        assertEquals(NoticeAlerts.BURN_MISSED, Navigation.helmAlert("Burn cut short: nobody at the helm, 40 m/s to go"));
        assertEquals(NoticeAlerts.BURN_MISSED, Navigation.helmAlert("Out of propellant: burn cut short, 40 m/s to go"));
        assertEquals(NoticeAlerts.HELM, Navigation.helmAlert("Helm order dropped: shot at"));
        assertEquals("", Navigation.helmAlert("Docking refused"));
    }

    @Test
    void theFeedsBringEverySystemsNoticesIn() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario.start(world, assets, assets.resolve("no-mods-here"));
            world.contracts.notices.add("Contract done: x. Paid 1 cr");
            world.standing.notices.add("Promoted to Lieutenant of the Terran Patrol Authority");
            world.hostiles.notices.add("Raider is moving to intercept you");
            world.hostiles.notices.add("Raider robs a freighter");
            world.yard.notices.add("first");
            world.yard.notices.add("last");
            NoticeFeeds.collect(world);
            List<NoticeLog.Notice> log = world.notices.history();
            assertEquals(5, log.size(), "the yard says only how its work last went: " + log);
            assertEquals("Raider robs a freighter", log.get(0).text);
            assertEquals("", log.get(0).alert);
            assertEquals(NoticeLog.Kind.WARNING, log.get(1).kind);
            assertEquals(NoticeAlerts.INTERCEPT, log.get(1).alert, "INTERCEPT carries it (the Task spec's 9.2)");
            assertEquals("last", log.get(2).text);
            assertEquals(NoticeLog.Kind.IMPORTANT, log.get(3).kind);
            assertEquals(NoticeLog.Kind.IMPORTANT, log.get(4).kind);
            assertTrue(world.contracts.notices.isEmpty() && world.standing.notices.isEmpty()
                    && world.hostiles.notices.isEmpty() && world.yard.notices.isEmpty());
        } finally {
            world.dispose();
        }
    }
}
