package com.ringsofiron.uihud.data;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The record's courses, each condition once from its raising to its clearing, and the record saved with the game. */
class ConditionLogTest {
    @Test
    void aRoomsAirIsOneCourseAtItsLatestWordsAndWorstLevel() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        frame(alerts, log, 10, "breach:HOLD", "BREACH", Level.WARNING, "air:HOLD", "VACUUM", Level.WARNING);
        frame(alerts, log, 20, "air:HOLD", "VACUUM", Level.WARNING, null, null, null);
        frame(alerts, log, 30, "air:HOLD", "PRESSURE", Level.CAUTION, null, null, null);
        assertEquals(1, log.courses.size, "one course, however many steps");
        ConditionLog.Course c = log.courses.first();
        assertEquals("Low pressure: HOLD", c.text, "its latest words");
        assertEquals(Level.WARNING, c.level, "the worst it reached");
        assertEquals(10, c.time, 1e-9, "from when it began");
        assertFalse(c.isCleared());
        frame(alerts, log, 40, null, null, null, null, null, null);
        frame(alerts, log, 43, null, null, null, null, null, null);
        assertTrue(c.isCleared());
        assertEquals(40, c.cleared, 1e-9, "when it went, once it stayed gone");
        assertEquals(4, log.entries.size, "the lines for each step and the clearing, as before");
    }

    @Test
    void aConditionThatWorsensIsRevisedInPlace() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        frame(alerts, log, 5, "fire:BRIDGE", "FIRE", Level.CAUTION, null, null, null);
        int revision = log.revision;
        frame(alerts, log, 11, "fire:BRIDGE", "FIRE", Level.WARNING, null, null, null);
        assertEquals(1, log.courses.size);
        assertEquals(Level.WARNING, log.courses.first().level);
        assertTrue(log.revision > revision, "so a log reads it again");
        frame(alerts, log, 15, "fire:BRIDGE", "FIRE", Level.CAUTION, null, null, null);
        assertEquals(Level.WARNING, log.courses.first().level, "the worst it reached stays");
        assertEquals(1, log.entries.size, "the line-by-line record logs no change of level");
    }

    @Test
    void aSalvoIsOneCourseUntilNoneIsLeftInbound() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        missiles(alerts, log, 5, 2);
        assertEquals(1, log.courses.size);
        ConditionLog.Course salvo = log.courses.first();
        assertEquals("2 missiles fired at the ship", salvo.text);
        assertEquals(Level.WARNING, salvo.level);
        missiles(alerts, log, 8, 1);
        assertFalse(salvo.isCleared(), "one still inbound");
        missiles(alerts, log, 9, 3);
        assertEquals("4 missiles fired at the ship", salvo.text, "two more fired");
        missiles(alerts, log, 14, 0);
        assertEquals(14, salvo.cleared, 1e-9);
        missiles(alerts, log, 20, 1);
        assertEquals(2, log.courses.size, "a new salvo");
        assertEquals("Missile fired at the ship", log.courses.peek().text);
    }

    @Test
    void theRecordIsSavedWithTheGameAndAConditionStillHoldingTakesItsCourseUpAgain() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        frame(alerts, log, 10, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        frame(alerts, log, 20, null, null, null, null, null, null);
        frame(alerts, log, 23, null, null, null, null, null, null);
        frame(alerts, log, 30, "breach:BRIDGE", "BREACH", Level.WARNING, null, null, null);
        frame(alerts, log, 31, "breach:BRIDGE", "BREACH", Level.WARNING, "missile", "MISSILE", Level.WARNING);
        assertEquals(3, log.courses.size);
        assertEquals("hud.record", log.saveKey());
        JsonValue save = save(log);

        ConditionLog loaded = new ConditionLog();
        frame(alerts, loaded, 5, "smoke:LOCKER", "SMOKE", Level.CAUTION, null, null, null);
        loaded.read(new Json(), save);
        assertEquals(log.entries.size, loaded.entries.size, "the save's lines, and only them");
        assertEquals(3, loaded.courses.size);
        assertEquals("Fire: HOLD", loaded.courses.get(0).text);
        assertEquals(20, loaded.courses.get(0).cleared, 1e-9);
        assertEquals("Hull breach: BRIDGE", loaded.courses.get(1).text);
        assertFalse(loaded.courses.get(1).isCleared());
        assertEquals(Level.WARNING, loaded.courses.get(1).level);
        assertEquals("ENGINEERING", loaded.courses.get(1).source);
        // The breach still holds after the load and the missile still flies: no second line, no second course
        int lines = loaded.entries.size;
        frame(alerts, loaded, 40, "breach:BRIDGE", "BREACH", Level.WARNING, "missile", "MISSILE", Level.WARNING);
        frame(alerts, loaded, 45, "breach:BRIDGE", "BREACH", Level.WARNING, "missile", "MISSILE", Level.WARNING);
        assertEquals(lines, loaded.entries.size);
        assertEquals(3, loaded.courses.size);
        // Then sealed and the missile gone: the breach's course clears where it was
        frame(alerts, loaded, 50, null, null, null, null, null, null);
        frame(alerts, loaded, 53, null, null, null, null, null, null);
        assertEquals(50, loaded.courses.get(1).cleared, 1e-9);
        assertEquals(50, loaded.courses.get(2).cleared, 1e-9, "the salvo too");
        assertEquals("Hull breach: BRIDGE, cleared after 0:20", loaded.entries.peek().text, "timed from the first");
    }

    @Test
    void aCourseALoadLeftHoldingThatNothingTakesUpClearsAtTheLoad() {
        AlertRegistry alerts = new AlertRegistry();
        ConditionLog log = new ConditionLog();
        frame(alerts, log, 10, "fire:HOLD", "FIRE", Level.WARNING, null, null, null);
        JsonValue save = save(log);
        ConditionLog loaded = new ConditionLog();
        loaded.read(new Json(), save);
        ConditionLog.Course fire = loaded.courses.first();
        frame(alerts, loaded, 60, null, null, null, null, null, null);
        assertFalse(fire.isCleared(), "the load settles first: the game raises its alerts again over a frame or two");
        frame(alerts, loaded, 62, null, null, null, null, null, null);
        assertEquals(60, fire.cleared, 1e-9, "when it was last known to hold");
        assertEquals("Fire: HOLD, cleared after 0:50", loaded.entries.peek().text);
        loaded.reset();
        assertEquals(0, loaded.courses.size);
        assertEquals(0, loaded.entries.size);
    }

    @Test
    void theHudKeepsTheRecordAndReadsItAtEachRefresh() {
        HudData d = new HudData();
        ConditionLog record = d.record;
        d.afterRefresh = () -> {
            d.alerts.begin(d.ctx.simSeconds);
            d.alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
            d.alerts.end();
        };
        d.refresh();
        assertSame(record, d.record);
        assertEquals(1, d.record.courses.size, "the game's alerts, read once they are raised");
        assertEquals("Fire: HOLD", d.record.courses.first().text);
    }

    private static JsonValue save(ConditionLog log) {
        Json json = new Json();
        StringWriter out = new StringWriter();
        json.setWriter(out);
        json.writeObjectStart();
        log.write(json);
        json.writeObjectEnd();
        return new JsonReader().parse(out.toString());
    }

    private static void missiles(AlertRegistry alerts, ConditionLog log, double now, int count) {
        alerts.begin(now);
        if (count > 0) alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", count + " inbound").count(count);
        alerts.end();
        log.update(alerts, now);
    }

    /** One frame of the game's alerts: up to two raised, then the log reads them. */
    private static void frame(AlertRegistry alerts, ConditionLog log, double now, String id1, String code1, Level l1,
                              String id2, String code2, Level l2) {
        alerts.begin(now);
        if (id1 != null) raise(alerts, id1, code1, l1);
        if (id2 != null) raise(alerts, id2, code2, l2);
        alerts.end();
        log.update(alerts, now);
    }

    private static void raise(AlertRegistry alerts, String id, String code, Level level) {
        if (id.equals("missile")) {
            alerts.raise(id, level, Category.TACTICAL).set(code, "1 inbound").count(1);
            return;
        }
        String room = id.substring(id.indexOf(':') + 1);
        alerts.raise(id, level, Category.LIFE_SUPPORT).set(code, code + " in " + room).room(room);
    }
}
