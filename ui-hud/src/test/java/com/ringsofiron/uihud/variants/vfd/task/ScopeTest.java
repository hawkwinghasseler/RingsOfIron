package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SNS's automatic range, its range by hand, and what it plots where. */
class ScopeTest {
    private final HudData d = new HudData();
    private final Posts p = new Posts();
    private final Scope s = new Scope();

    @BeforeEach
    void atTheHelm() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        d.ctx.ship = ship;
        d.ctx.presence = Presence.HELM;
    }

    private void frame() {
        p.update(d, false);
        s.update(d, p);
    }

    private Contact contact(String name, Kind kind, Relation r, float x) {
        Contact c = d.contactFrame.add();
        c.label = name;
        c.kind = kind;
        c.relation = r;
        c.x = x;
        c.range = Math.abs(x);
        return c;
    }

    @Test
    void theStepHoldsTheLockAndEveryInboundMissileWithRoomToSpare() {
        // A raider 3.2 km off and its two harpoons 1.4 km out: 5 km, the spec's sample
        contact("Raider", Kind.SHIP, Relation.HOSTILE, 3_200f).locked = true;
        for (int i = 0; i < 2; i++) {
            MissileTrack m = d.threatFrame.add();
            m.side = Side.INCOMING;
            m.x = 1_400f;
            m.range = 1_400f;
        }
        contact("Highport", Kind.STATION, Relation.NEUTRAL, -40_000f);
        frame();
        assertEquals(2, s.step);
        assertEquals("5 KM", Scope.rangeText(s.step));
        assertTrue(s.auto);
        assertEquals(1, s.hostiles);
        // The raider and the two harpoons inside; the station past the rim, on it
        int rim = 0, inside = 0;
        for (int i = 0; i < s.count; i++) {
            if (s.marks[i].rim) rim++;
            else inside++;
        }
        assertEquals(1, rim);
        assertEquals(3, inside);
    }

    @Test
    void withNeitherTheNearestHostileElseTheNearestContactElseFiftyKilometres() {
        assertEquals(Scope.REST, Scope.autoStep(0f, Float.NaN, Float.NaN));
        assertEquals(4, Scope.autoStep(0f, 30_000f, 1_000f), "the hostile, not the nearer neutral");
        assertEquals(1, Scope.autoStep(0f, Float.NaN, 1_000f));
        assertEquals(0, Scope.holding(300f));
        assertEquals(1, Scope.holding(401f));
        assertEquals(Scope.STEPS.length - 1, Scope.holding(5e9f));
    }

    @Test
    void aRangeSetByHandLastsUntilThePostChanges() {
        contact("Highport", Kind.STATION, Relation.NEUTRAL, 3_000f);
        frame();
        assertEquals(2, s.step);
        s.zoom(1);
        frame();
        assertEquals(3, s.step);
        assertFalse(s.auto);
        d.ctx.presence = Presence.WEAPONS;
        frame();
        assertEquals(2, s.step);
        assertTrue(s.auto);
    }

    @Test
    void dockedOnlyWhileAHostileIsWithinFiftyKilometres() {
        d.ctx.docked = true;
        contact("Highport", Kind.STATION, Relation.NEUTRAL, 300f);
        frame();
        assertFalse(s.on);
        contact("Raider", Kind.SHIP, Relation.HOSTILE, 30_000f);
        frame();
        assertTrue(s.on);
    }

    @Test
    void relationsDifferByShapeNotColourAlone() {
        Contact own = d.contactFrame.add();
        own.kind = Kind.EVA;
        assertEquals(Scope.OWN, Scope.kind(own));
        assertEquals(Scope.HOSTILE, Scope.kind(contact("A", Kind.SHIP, Relation.HOSTILE, 1f)));
        assertEquals(Scope.FRIENDLY, Scope.kind(contact("B", Kind.SHIP, Relation.FRIENDLY, 1f)));
        assertEquals(Scope.NEUTRAL, Scope.kind(contact("C", Kind.SHIP, Relation.NEUTRAL, 1f)));
        assertEquals(Scope.INERT, Scope.kind(contact("D", Kind.DERELICT, Relation.NEUTRAL, 1f)));
        assertEquals(Scope.STATION, Scope.kind(contact("E", Kind.STATION, Relation.FRIENDLY, 1f)));
        assertFalse(java.util.Arrays.equals(Scope.TRI, Scope.FILLED));
    }
}
