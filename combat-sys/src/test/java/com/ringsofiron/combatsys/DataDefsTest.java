package com.ringsofiron.combatsys;

import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.moddingdata.DataFileParser;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.LoadReport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped weapons.toml loads into the combat def classes through modding-data. */
class DataDefsTest {

    @Test
    void shippedWeaponDataLoads() throws Exception {
        Path file = Paths.get("..", "assets", "data", "weapons.toml");
        String toml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        DefRegistry registry = new DefRegistry();
        LoadReport report = new LoadReport();
        registry.mergeFile(DataFileParser.parseToml(toml, "weapons.toml"), "weapons.toml", report);
        assertFalse(report.hasErrors(), report.toString());

        CombatDefs defs = CombatDefs.fromRegistry(registry);

        WeaponDef railgun = defs.weapon("railgun_mk1");
        assertEquals(WeaponDef.MountType.TURRET, railgun.mount);
        assertEquals(40f, railgun.slewRateDeg);
        assertEquals("slug_tungsten_20kg", railgun.projectile);

        ProjectileDef slug = defs.projectile("slug_tungsten_20kg");
        assertEquals(DamageType.KINETIC, slug.damageType);
        assertEquals(6000f, slug.muzzleSpeed);
        assertEquals(6, slug.spall.count);
        assertEquals(25f, slug.spall.angleDeg);
        assertEquals(0.05f, slug.armingTime, "unset keys keep their defaults");

        MissileDef harpoon = defs.missile("harpoon");
        assertEquals(MissileDef.SeekerType.RADAR, harpoon.seeker.type);
        assertEquals(20000f, harpoon.seeker.range);
        assertEquals(Fuse.Type.PROXIMITY, harpoon.fuse.type);
        assertEquals(15f, harpoon.fuse.radius);
        assertNotNull(defs.warhead(harpoon.warhead));
        assertEquals(40, defs.warhead("frag_heavy").fragmentCount);
    }

    @Test
    void roundThreesWeaponsLoadWithTheirNewKeys() throws Exception {
        Path file = Paths.get("..", "assets", "data", "weapons.toml");
        DefRegistry registry = new DefRegistry();
        LoadReport report = new LoadReport();
        registry.mergeFile(DataFileParser.parseToml(new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                "weapons.toml"), "weapons.toml", report);
        assertFalse(report.hasErrors(), report.toString());
        CombatDefs defs = CombatDefs.fromRegistry(registry);

        WeaponDef flak = defs.weapon("flak_mk1");
        assertTrue(flak.pointDefense);
        assertEquals(Fuse.Type.PROXIMITY, defs.projectile(flak.projectile).fuse.type);
        assertEquals(25f, defs.projectile(flak.projectile).fuse.radius);

        WeaponDef darts = defs.weapon("dart_rack");
        assertEquals(WeaponDef.MountType.LAUNCHER, darts.mount);
        MissileDef dart = defs.missile(darts.missile);
        assertTrue(dart.hitPoints < defs.missile("harpoon").hitPoints, "lighter than a harpoon");
        assertNotNull(defs.warhead(dart.warhead));

        WeaponDef heavy = defs.weapon("railgun_heavy");
        assertEquals("M", heavy.minMountSize);
        assertFalse(heavy.fitsMount("S"), "too big for a corvette's turret mount");
        assertTrue(heavy.fitsMount("M") && heavy.fitsMount("L"));
        assertTrue(defs.weapon("railgun_mk1").fitsMount("S"), "a Small gun fits a Small mount");

        ProjectileDef bolt = defs.projectile(defs.weapon("ion_mk1").projectile);
        assertEquals(DamageType.ION, bolt.damageType);
        assertEquals(2f, bolt.stun.radiusTiles);
        assertEquals(10f, bolt.stun.minSeconds);
        assertEquals(30f, bolt.stun.maxSeconds);
    }

    /**
     * Speed against damage: every weapon has a size, and no gun's sustained muzzle power times its
     * round's speed passes its size's budget, so a faster round never means more damage per second.
     */
    @Test
    void everyGunKeepsToItsSizesSpeedBudget() throws Exception {
        Path file = Paths.get("..", "assets", "data", "weapons.toml");
        String toml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        DefRegistry registry = new DefRegistry();
        LoadReport report = new LoadReport();
        registry.mergeFile(DataFileParser.parseToml(toml, "weapons.toml"), "weapons.toml", report);
        CombatDefs defs = CombatDefs.fromRegistry(registry);
        for (WeaponDef w : defs.weapons.values()) {
            assertNotNull(w.minMountSize, w.id + " has a size");
            assertTrue(WeaponDef.speedBudget(w.minMountSize) > 0, w.id + "'s size " + w.minMountSize);
            ProjectileDef round = w.projectile == null ? null : defs.projectile(w.projectile);
            if (round == null || round.damageType == DamageType.ION) continue;
            double index = w.sustainedPower(round) * round.muzzleSpeed;
            double budget = WeaponDef.speedBudget(w.minMountSize);
            assertTrue(index <= budget * 1.05, String.format("%s: %.3g W*m/s over its %s budget of %.3g",
                    w.id, index, w.minMountSize, budget));
        }
    }
}
