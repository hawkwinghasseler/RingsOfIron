package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.GdxNativesLoader;
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
    void theStackKeepsTheNewestFewAndTheLogTheLastFifty() {
        NoticeLog log = new NoticeLog(() -> now);
        for (int i = 0; i < 60; i++) log.post("n" + i);
        assertEquals(NoticeLog.STACK, log.showing().size());
        assertEquals("n59", log.showing().get(0).text);
        assertEquals(NoticeLog.KEEP, log.history().size());
        assertEquals("n59", log.history().get(0).text);
        assertEquals("n10", log.history().get(NoticeLog.KEEP - 1).text);
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
    void theFeedsBringEverySystemsNoticesIn() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario.start(world, assets, assets.resolve("no-mods-here"));
            world.contracts.notices.add("Contract done: x. Paid 1 cr");
            world.standing.notices.add("Promoted to Lieutenant of the Terran Patrol Authority");
            world.hostiles.notices.add("Raider is moving to intercept you");
            world.yard.notices.add("first");
            world.yard.notices.add("last");
            NoticeFeeds.collect(world);
            List<NoticeLog.Notice> log = world.notices.history();
            assertEquals(4, log.size(), "the yard says only how its work last went: " + log);
            assertEquals(NoticeLog.Kind.WARNING, log.get(0).kind);
            assertEquals("last", log.get(1).text);
            assertEquals(NoticeLog.Kind.IMPORTANT, log.get(2).kind);
            assertEquals(NoticeLog.Kind.IMPORTANT, log.get(3).kind);
            assertTrue(world.contracts.notices.isEmpty() && world.standing.notices.isEmpty()
                    && world.hostiles.notices.isEmpty() && world.yard.notices.isEmpty());
        } finally {
            world.dispose();
        }
    }
}
