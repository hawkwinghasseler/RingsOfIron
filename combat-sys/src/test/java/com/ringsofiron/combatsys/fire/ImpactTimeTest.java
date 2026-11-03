package com.ringsofiron.combatsys.fire;

import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.defs.MissileDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactTimeTest {

    @Test
    void withNoBurnItIsRangeOverSpeed() {
        assertEquals(10f, ImpactTime.solve(1000f, 100f, 0f, 0f), 1e-4);
        assertTrue(Float.isNaN(ImpactTime.solve(1000f, -5f, 0f, 0f)));
    }

    @Test
    void aBurnThatLastsTheWayIsTheQuadratic() {
        // d = v t + a t^2 / 2: from rest at 30 m/s^2, 2.8 km takes sqrt(2 * 2800 / 30)
        float t = ImpactTime.solve(2800f, 0f, 30f, 1e6f);
        assertEquals((float) Math.sqrt(2 * 2800 / 30.0), t, 1e-3);
    }

    @Test
    void aBurnThatEndsFirstCoastsTheRest() {
        // 10 s at 10 m/s^2 from 0: 500 m covered at 100 m/s, then 500 m more at 100 m/s
        assertEquals(15f, ImpactTime.solve(1000f, 0f, 10f, 100f), 1e-4);
    }

    @Test
    void aBurningMissileLandsSoonerThanItsClosestApproachSays() {
        MissileDef def = new MissileDef();
        MissileComponent m = new MissileComponent();
        m.def = def;
        m.phase = MissileComponent.Phase.MIDCOURSE;
        m.propellant = def.propellantMass;
        float range = 7700f, closing = 150f;
        float burning = ImpactTime.of(m, range, closing);
        assertTrue(burning < range / closing, burning + " vs " + range / closing);
        // Out of fuel above the reserve, or coasting: no burn counted
        m.propellant = def.terminalReserve * def.propellantMass;
        assertEquals(range / closing, ImpactTime.of(m, range, closing), 1e-3);
        m.propellant = def.propellantMass;
        m.phase = MissileComponent.Phase.COAST;
        assertEquals(range / closing, ImpactTime.of(m, range, closing), 1e-3);
    }
}
