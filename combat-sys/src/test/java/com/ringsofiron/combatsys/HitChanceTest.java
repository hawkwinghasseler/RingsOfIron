package com.ringsofiron.combatsys;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.HitChance;
import org.junit.jupiter.api.Test;

/** The rough chance a shot hits, the effective range it sets, and the arcs' tint. */
class HitChanceTest {
    final CombatDefs defs = CombatDefs.createDefault();
    final WeaponDef railgun = defs.weapons.get("railgun_mk1");
    final ProjectileDef slug = defs.projectiles.get(railgun.projectile);

    float chance(float range, float evasion) {
        return HitChance.perShot(railgun, slug, 0f, 0.5f, range, 12f, evasion);
    }

    @Test
    void theChanceFallsWithRangeAndWithATargetThatDodges() {
        assertTrue(chance(1_000, 10) > 0.99f, "point blank");
        assertTrue(chance(20_000, 10) < chance(10_000, 10));
        assertTrue(chance(20_000, 10) < chance(20_000, 0), "a dodging target is harder to hit");
        assertEquals(0f, chance(slug.muzzleSpeed * slug.maxLifetime + 1, 0), "past where the round is spent");
    }

    @Test
    void aBeamHitsNothingPastItsReach() {
        WeaponDef laser = defs.weapons.get("laser_mk1");
        assertTrue(HitChance.perShot(laser, null, 3000f, 0.5f, 2000f, 12f, 10f) > 0.5f);
        assertEquals(0f, HitChance.perShot(laser, null, 3000f, 0.5f, 3100f, 12f, 0f));
    }

    @Test
    void aFastGunCountsASecondsRoundsTogether() {
        WeaponDef autocannon = defs.weapons.get("autocannon_mk1");
        assertEquals(0.5f, HitChance.rated(autocannon, 0.05f), 1e-6f, "ten rounds a second");
        assertEquals(1f, HitChance.rated(autocannon, 0.5f), 1e-6f, "never more than certain");
        assertEquals(0.05f, HitChance.rated(railgun, 0.05f), 1e-6f, "a round every 4 s counts alone");
    }

    @Test
    void theEffectiveRangeIsWhereTheChanceFallsToTheOpeningOne() {
        float range = HitChance.effectiveRange(railgun, slug, 0f, 0.5f, 12f, 10f, HitChance.OPEN_FIRE);
        assertTrue(range > 3_000f && range < 30_000f, "some km: " + range);
        assertEquals(HitChance.OPEN_FIRE, chance(range, 10), 0.01f);
        assertTrue(HitChance.effectiveRange(railgun, slug, 0f, 1f, 12f, 10f, HitChance.OPEN_FIRE) > range,
                "a veteran opens up further out");
    }

    @Test
    void theTintRunsFromGreenOnASureHitToRedOnAHopelessOne() {
        assertEquals(1f, HitChance.tint(0.9f, 0.95f), 1e-6f, "90% even dodging: green");
        assertEquals(0f, HitChance.tint(0.001f, 0.01f), 1e-6f, "1% even holding still: red");
        float between = HitChance.tint(0.1f, 0.5f);
        assertTrue(between > 0.2f && between < 0.8f, "yellow between: " + between);
    }
}
