package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnunciatorTest {
    private final AlertRegistry alerts = new AlertRegistry();
    private final PowerFrame power = new PowerFrame();
    private final Annunciator ann = new Annunciator();

    @Test
    void warningsFillFromTheWarnLampAndCautionsFromTheCautLamp() {
        alerts.begin(10);
        alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "2 missiles inbound").count(2).timer(10.2f, TimerKind.HARM);
        alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", "Hull at 52%");
        alerts.raise("fire:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in BRIDGE").room("BRIDGE");
        alerts.end();
        ann.fill(alerts.all(), power, false);

        assertEquals(2, ann.warnings);
        assertEquals(1, ann.cautions);
        assertTrue(ann.slots[0].warning);
        assertTrue(ann.slots[1].warning);
        assertNull(ann.slots[2]);
        assertNull(ann.slots[4]);
        assertEquals("HULL", ann.slots[5].tag);
        assertEquals("52%", ann.slots[5].value);
        assertFalse(ann.slots[5].warning);
        assertTrue(ann.warnUnacked);
        assertTrue(ann.cautUnacked);
    }

    @Test
    void theNewestOfARoleComesFirst() {
        alerts.begin(1);
        alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
        alerts.end();
        alerts.begin(2);
        alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
        alerts.raise("missile", Level.WARNING, Category.TACTICAL).set("MISSILE", "Missile inbound").timer(8f, TimerKind.HARM);
        alerts.end();
        ann.fill(alerts.all(), power, false);
        assertEquals("MISSILE", ann.slots[0].tag);
        assertEquals("1  0:08", ann.slots[0].value);
        assertEquals("FIRE", ann.slots[1].tag);
        assertEquals("HOLD", ann.slots[1].value);
    }

    @Test
    void severalRoomsOfAKindShareOneTileThatCountsTheRest() {
        alerts.begin(0);
        alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD").room("HOLD");
        alerts.raise("fire:BRIDGE", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in BRIDGE").room("BRIDGE");
        alerts.end();
        ann.fill(alerts.all(), power, false);
        assertEquals(1, ann.warnings);
        assertEquals(2, ann.slots[0].count);
        assertTrue(ann.slots[0].value.endsWith("+1"), ann.slots[0].value);
    }

    @Test
    void pastSixKindsTheTileWhereTheyMeetCountsTheRest() {
        alerts.begin(0);
        String[] codes = {"FIRE", "BREACH", "VACUUM"};
        for (String c : codes) alerts.raise(c, Level.WARNING, Category.LIFE_SUPPORT).set(c, c).room("HOLD");
        String[] cautions = {"SMOKE", "PRESSURE", "HULL", "HOSTILE"};
        for (String c : cautions) alerts.raise(c, Level.CAUTION, Category.ENGINEERING).set(c, c);
        alerts.end();
        ann.fill(alerts.all(), power, false);
        assertEquals(3, ann.warnings);
        assertEquals(4, ann.cautions);
        // Three warnings from the left, then the meeting tile, then two cautions from the right
        assertTrue(ann.slots[0].warning && ann.slots[1].warning && ann.slots[2].warning);
        assertEquals("+2", ann.slots[3].tag);
        assertEquals("MORE", ann.slots[3].value);
        assertFalse(ann.slots[3].warning);
        assertFalse(ann.slots[4].warning);
        assertFalse(ann.slots[5].warning);
    }

    @Test
    void advisoriesAndTheShipsStateTakeNoTile() {
        alerts.begin(0);
        alerts.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", "Crew outside");
        alerts.raise("disabled", Level.WARNING, Category.SHIP).set("DISABLED", "Ship disabled");
        alerts.end();
        ann.fill(alerts.all(), power, false);
        assertFalse(ann.any());
    }

    @Test
    void aBlackoutIsAPowerWarningReadingNone() {
        alerts.begin(0);
        alerts.end();
        ann.fill(alerts.all(), power, true);
        assertEquals("POWER", ann.slots[0].tag);
        assertEquals("NONE", ann.slots[0].value);
        assertTrue(ann.slots[0].warning);
    }

    @Test
    void aSystemsFaultIsNamedByWhatIsWrongWithIt() {
        SystemRow stunned = power.addFault();
        stunned.system = "REACTOR";
        stunned.fault = Fault.STUNNED;
        stunned.timer = 21.4f;
        stunned.tileX = 3;
        stunned.tileY = 4;
        SystemRow wrecked = power.addFault();
        wrecked.system = "TURRET";
        wrecked.fault = Fault.DESTROYED;
        wrecked.tileX = 9;
        wrecked.tileY = 2;
        alerts.begin(0);
        alerts.raise("sys:REACTOR:3,4", Level.WARNING, Category.ENGINEERING).set("REACTOR", "Reactor stunned");
        alerts.raise("sys:TURRET:9,2", Level.WARNING, Category.ENGINEERING).set("TURRET", "Turret destroyed");
        alerts.end();
        ann.fill(alerts.all(), power, false);
        boolean stun = false, turret = false;
        for (Annunciator.Tile t : ann.slots) {
            if (t == null) continue;
            if (t.tag.equals("STUN")) {
                stun = true;
                assertEquals("RCTR 22", t.value);
            }
            if (t.tag.equals("TURRET")) {
                turret = true;
                assertEquals("WRECKED", t.value);
            }
        }
        assertTrue(stun && turret);
    }

    @Test
    void theWordsOfTheCatalogue() {
        alerts.begin(0);
        alerts.raise("o2", Level.WARNING, Category.LIFE_SUPPORT).set("O2", "Oxygen tanks dry: emergency oxygen 40%");
        alerts.raise("helm", Level.CAUTION, Category.FLIGHT).set("HELM", "Helm: the helm is wrecked");
        alerts.raise("fuel", Level.CAUTION, Category.FLIGHT).set("FUEL", "Propellant low");
        alerts.end();
        ann.fill(alerts.all(), power, false);
        for (Annunciator.Tile t : ann.slots) {
            if (t == null) continue;
            switch (t.tag) {
                case "O2 TANKS": assertEquals("DRY", t.value); break;
                case "HELM": assertEquals("WRECKED", t.value); break;
                case "DELTA-V": assertEquals("LOW", t.value); break;
                default: throw new AssertionError("unexpected tile " + t.tag);
            }
        }
        assertEquals("52%", Annunciator.percent("Hull at 52%"));
        assertEquals("HOLD+2", Annunciator.place("HOLD", 2));
        assertEquals("ENGINE+1", Annunciator.place("ENGINEERING", 1));
    }
}
