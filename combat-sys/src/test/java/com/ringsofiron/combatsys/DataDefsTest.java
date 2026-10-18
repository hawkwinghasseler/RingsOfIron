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
}
