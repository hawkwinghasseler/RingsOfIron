package com.ringsofiron.moddingdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Broken references in mod data show up as warnings in the load report, and the asset key table a mod extends. */
class DefValidatorTest {

    @TempDir
    Path temp;

    private DefRegistry defs(String toml) {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        LoadReport report = new LoadReport();
        defs.mergeFile(DataFileParser.parseToml(toml, "mod.toml"), "mod", report);
        assertFalse(report.hasErrors(), report.toString());
        return defs;
    }

    private static List<String> messages(LoadReport report) {
        return report.entries().stream().map(e -> e.severity + " " + e.message).collect(Collectors.toList());
    }

    private static boolean said(LoadReport report, String fragment) {
        return report.entries().stream().anyMatch(e -> e.message.contains(fragment));
    }

    @Test
    void theBaseGameValidatesClean() {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        LoadReport report = new LoadReport();
        new TomlDataLoader(defs, report).loadDirectory(Path.of("../assets/data"), "base", true);
        DefValidator.validate(defs, null, report);
        assertTrue(report.entries().stream().noneMatch(e -> e.severity != LoadReport.Severity.INFO), report.toString());
    }

    @Test
    void aShipNamingAMissingChassisWeaponOrTextureIsReported() {
        DefRegistry defs = defs(
                "[weapon.laser]\n" +
                "[ship.skiff]\n" +
                "chassis = \"nope\"\n" +
                "texture = \"tex_missing\"\n" +
                "hardpoints = [ { weapon = \"laser\" }, { weapon = \"plasma\" } ]\n");
        LoadReport report = new LoadReport();
        AssetRegistry assets = new AssetRegistry(report);
        DefValidator.validate(defs, assets, report);
        assertTrue(said(report, "unknown chassis 'nope'"), messages(report).toString());
        assertTrue(said(report, "unknown texture key 'tex_missing'"));
        assertTrue(said(report, "unknown weapon 'plasma'"));
        assertFalse(said(report, "'laser'"), "a known weapon is fine");
        assertFalse(report.hasErrors(), "warnings only: nothing is removed");
        assertTrue(defs.has("ship", "skiff"));
    }

    @Test
    void stationTypesMustTradeKnownGoodsAndUseAStationBlueprint() {
        DefRegistry defs = defs(
                "[commodity.water]\n" +
                "[chassis.corvette_like]\n" +
                "rows = [\"###\"]\n" +
                "[chassis.dock]\n" +
                "station = true\n" +
                "rows = [\"###\"]\n" +
                "[station_type.good]\n" +
                "blueprint = \"dock\"\n" +
                "produces = { water = 1.0 }\n" +
                "[station_type.bad]\n" +
                "blueprint = \"corvette_like\"\n" +
                "produces = { unobtainium = 1.0 }\n" +
                "consumes = { water = 2.0 }\n" +
                "[station_type.lost]\n" +
                "blueprint = \"nowhere\"\n");
        LoadReport report = new LoadReport();
        DefValidator.validate(defs, null, report);
        List<String> m = messages(report);
        assertTrue(said(report, "unknown commodity 'unobtainium'"), m.toString());
        assertTrue(said(report, "'corvette_like', which is not marked station = true"), m.toString());
        assertTrue(said(report, "unknown blueprint 'nowhere'"), m.toString());
        assertEquals(3, m.size(), "the good station type says nothing: " + m);
    }

    @Test
    void aSoundWithNoPathCannotBePlayed() {
        DefRegistry defs = defs("[sound_event.click]\nlooping = false\n[sound_event.ok]\npath = \"audio/ok.ogg\"\n");
        LoadReport report = new LoadReport();
        DefValidator.validate(defs, null, report);
        assertEquals(1, report.entries().size(), report.toString());
        assertTrue(said(report, "has no path"));
    }

    @Test
    void aDefThatDoesNotConvertIsAnError() {
        DefRegistry defs = defs("[ship.broken]\nmass = \"heavy\"\n");
        LoadReport report = new LoadReport();
        DefValidator.validate(defs, null, report);
        assertTrue(report.hasErrors(), report.toString());
        assertEquals("mod", report.errors().get(0).source, "names where it came from");
    }

    // ------------------------------------------------------------------
    // Asset keys
    // ------------------------------------------------------------------

    @Test
    void aModsAssetsOverrideTheBaseByKeyAndResolveToItsFolder() throws IOException {
        LoadReport report = new LoadReport();
        AssetRegistry assets = new AssetRegistry(report);
        Path base = temp.resolve("base.toml");
        Files.write(base, ("[texture]\ntex_hull = \"textures/hull.png\"\n[audio_event]\nevt_laser = \"event:/laser\"\n")
                .getBytes(StandardCharsets.UTF_8));
        assets.loadManifest(base, null, "base");
        assertEquals("textures/hull.png", assets.path("tex_hull"), "base paths stay internal");

        Path modDir = temp.resolve("mods/shiny");
        Files.createDirectories(modDir);
        Path mod = modDir.resolve("assets.toml");
        Files.write(mod, ("[texture]\ntex_hull = \"art/hull.png\"\n[audio_event]\nevt_laser = \"event:/laser_mod\"\n")
                .getBytes(StandardCharsets.UTF_8));
        assets.loadManifest(mod, modDir, "shiny");
        assertEquals(modDir.resolve("art/hull.png").toAbsolutePath().normalize().toString(), assets.path("tex_hull"));
        assertEquals("shiny", assets.entry("tex_hull").source);
        assertEquals("event:/laser_mod", assets.getString("evt_laser"), "plain values are not paths");
        assertEquals(1, assets.countsByKind().get("texture"));
        assertTrue(report.entries().isEmpty(), report.toString());
    }

    @Test
    void reusingAKeyForAnotherKindIsWarnedAbout() {
        LoadReport report = new LoadReport();
        AssetRegistry assets = new AssetRegistry(report);
        assets.register("thing", "texture", "a.png", "base");
        assets.register("thing", "sound", "a.ogg", "mod");
        assertEquals("sound", assets.entry("thing").kind);
        assertEquals(1, report.entries().size());
        assertEquals(LoadReport.Severity.WARNING, report.entries().get(0).severity);
    }

    @Test
    void anUnknownKeyIsAClearError() {
        AssetRegistry assets = new AssetRegistry(new LoadReport());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> assets.path("tex_nope"));
        assertTrue(e.getMessage().contains("tex_nope"));
        assertThrows(IllegalStateException.class, () -> assets.getTexture("tex_nope"), "no AssetManager yet");
    }

    @Test
    void aBrokenOrMissingManifestIsReportedNotThrown() throws IOException {
        LoadReport report = new LoadReport();
        AssetRegistry assets = new AssetRegistry(report);
        assets.loadManifest(temp.resolve("absent.toml"), null, "base");
        assertTrue(report.entries().isEmpty(), "a mod without assets is fine");
        Path bad = temp.resolve("bad.toml");
        Files.write(bad, "[texture\nbroken = ".getBytes(StandardCharsets.UTF_8));
        assets.loadManifest(bad, null, "shiny");
        assertTrue(report.hasErrors());
        Path notTable = temp.resolve("flat.toml");
        Files.write(notTable, "texture = \"x.png\"\n".getBytes(StandardCharsets.UTF_8));
        assets.loadManifest(notTable, null, "shiny");
        assertTrue(report.entries().stream().anyMatch(e -> e.message.contains("is not a table")));
        assertFalse(assets.has("texture"));
    }
}
