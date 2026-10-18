package com.ringsofiron.moddingdata;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefRegistryTest {

    /** Stands in for a class owned by another module (e.g. combat-sys's WeaponDef). */
    public static class WeaponLike {
        public String id;
        public float cooldown;
        public int slewRateDeg;
        public String projectile;
        public Spall spall;

        public static class Spall {
            public int count;
            public float angleDeg;
        }
    }

    private static final String BASE =
            "[weapon.railgun]\n" +
            "cooldown = 1.5\n" +
            "slew_rate_deg = 40\n" +
            "projectile = \"slug\"\n" +
            "spall = { count = 6, angle_deg = 25 }\n";

    private DefRegistry loaded(String... tomlFiles) {
        DefRegistry defs = new DefRegistry();
        LoadReport report = new LoadReport();
        for (int i = 0; i < tomlFiles.length; i++) {
            defs.mergeFile(DataFileParser.parseToml(tomlFiles[i], "f" + i), "f" + i, report);
        }
        assertFalse(report.hasErrors(), report.toString());
        return defs;
    }

    @Test
    void snakeCaseKeysFillCamelCaseFieldsAndIdIsInjected() {
        WeaponLike w = loaded(BASE).get("weapon", "railgun", WeaponLike.class);
        assertEquals("railgun", w.id);
        assertEquals(1.5f, w.cooldown);
        assertEquals(40, w.slewRateDeg);
        assertEquals("slug", w.projectile);
        assertEquals(6, w.spall.count);
        assertEquals(25f, w.spall.angleDeg);
    }

    @Test
    void laterFilesPatchOnlyTheFieldsTheyName() {
        DefRegistry defs = loaded(BASE, "[weapon.railgun]\ncooldown = 1.0\nspall = { count = 10 }\n");
        WeaponLike w = defs.get("weapon", "railgun", WeaponLike.class);
        assertEquals(1.0f, w.cooldown);
        assertEquals(40, w.slewRateDeg, "untouched field keeps base value");
        assertEquals(10, w.spall.count, "nested table merges key by key");
        assertEquals(25f, w.spall.angleDeg);
        assertEquals(List.of("f0", "f1"), defs.entry("weapon", "railgun").sources());
    }

    @Test
    void replaceAndRemoveKeys() {
        DefRegistry defs = loaded(BASE,
                "[weapon.railgun]\n_replace = true\ncooldown = 9.0\n",
                "[weapon.other]\ncooldown = 1.0\n",
                "[weapon.other]\n_remove = true\n");
        WeaponLike w = defs.get("weapon", "railgun", WeaponLike.class);
        assertEquals(9.0f, w.cooldown);
        assertEquals(0, w.slewRateDeg, "replace dropped the old fields");
        assertFalse(defs.has("weapon", "other"));
    }

    @Test
    void typeRegisteredAfterLoadingStillConverts() {
        DefRegistry defs = loaded(BASE);
        assertThrows(IllegalStateException.class, () -> defs.get("weapon", "railgun"));
        defs.registerType("weapon", WeaponLike.class);
        Object w = defs.get("weapon", "railgun");
        assertTrue(w instanceof WeaponLike);
        assertEquals(1, defs.all(WeaponLike.class).size());
    }

    @Test
    void conversionCacheIsDroppedWhenADefChanges() {
        DefRegistry defs = loaded(BASE);
        WeaponLike first = defs.get("weapon", "railgun", WeaponLike.class);
        assertSame(first, defs.get("weapon", "railgun", WeaponLike.class));
        defs.define("weapon", "railgun", Map.of("cooldown", 0.5), "script");
        assertEquals(0.5f, defs.get("weapon", "railgun", WeaponLike.class).cooldown);
    }

    @Test
    void jsonAndTomlProduceTheSameDefinition() {
        DefRegistry defs = new DefRegistry();
        LoadReport report = new LoadReport();
        defs.mergeFile(DataFileParser.parseJson(
                "{\"weapon\":{\"railgun\":{\"cooldown\":1.5,\"slew_rate_deg\":40,\"projectile\":\"slug\","
                        + "\"spall\":{\"count\":6,\"angle_deg\":25}}}}", "json"), "json", report);
        WeaponLike fromJson = defs.get("weapon", "railgun", WeaponLike.class);
        WeaponLike fromToml = loaded(BASE).get("weapon", "railgun", WeaponLike.class);
        assertEquals(fromToml.cooldown, fromJson.cooldown);
        assertEquals(fromToml.slewRateDeg, fromJson.slewRateDeg);
        assertEquals(fromToml.spall.angleDeg, fromJson.spall.angleDeg);
    }

    @Test
    void badShapeNamesTheDefinitionAndItsSource() {
        DefRegistry defs = loaded("[weapon.broken]\nspall = \"not a table\"\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> defs.get("weapon", "broken", WeaponLike.class));
        assertTrue(e.getMessage().contains("weapon.broken"), e.getMessage());
        assertTrue(e.getMessage().contains("f0"), e.getMessage());
    }

    @Test
    void invalidTomlReportsTheFile() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataFileParser.parseToml("[weapon.x\ncooldown = ", "bad.toml"));
        assertTrue(e.getMessage().contains("bad.toml"));
    }
}
