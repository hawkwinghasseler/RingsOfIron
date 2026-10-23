package com.ringsofiron.moddingdata;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.CommodityDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.moddingdata.defs.SoundEventData;
import com.ringsofiron.moddingdata.defs.StationTypeDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the real base data in /assets plus the example mods. Tests run from the modding-data folder. */
class ModdingBootstrapTest {

    static final Path ASSETS = Paths.get("../assets");
    static final Path EXAMPLE_MODS = Paths.get("examples/mods");

    public static class WeaponLike {
        public float cooldown;
        public int slewRateDeg;
    }

    private static ModContext loadExamples(ModTrustPolicy policy) {
        return new ModdingBootstrap(ASSETS, EXAMPLE_MODS).trustPolicy(policy).load(new GameEngine());
    }

    @Test
    void baseDataAndExampleModsLoadCleanly() {
        ModContext ctx = loadExamples(ModTrustPolicy.WARN_AND_ALLOW);
        for (LoadReport.Entry e : ctx.report.entries()) {
            assertNotEquals(LoadReport.Severity.ERROR, e.severity, ctx.report.toString());
            assertNotEquals(LoadReport.Severity.WARNING, e.severity, ctx.report.toString());
        }
        List<String> order = new ArrayList<>();
        for (ModManifest m : ctx.mods) order.add(m.id);
        assertEquals(List.of("better_railguns", "spice_trade"), order);
    }

    @Test
    void dataModPatchesOneFieldAndAddsDefs() {
        ModContext ctx = loadExamples(ModTrustPolicy.WARN_AND_ALLOW);
        WeaponLike railgun = ctx.defs.get("weapon", "railgun_mk1", WeaponLike.class);
        assertEquals(1.0f, railgun.cooldown, "patched by better_railguns");
        assertEquals(40, railgun.slewRateDeg, "kept from base data");
        assertTrue(ctx.defs.has("weapon", "railgun_mk2"));
        assertEquals("event:/mods/better_railguns/railgun", ctx.assets.getString("evt_railgun_fire"));
    }

    @Test
    void modSoundEventsCarryPlaybackRulesForTheirPath() {
        ModContext ctx = loadExamples(ModTrustPolicy.WARN_AND_ALLOW);
        List<SoundEventData> sounds = ctx.defs.all(SoundEventData.class);
        assertEquals(1, sounds.size());
        SoundEventData railgun = sounds.get(0);
        assertEquals(ctx.assets.getString("evt_railgun_fire"), railgun.path, "matches the key the mod re-pointed");
        assertFalse(railgun.looping);
        assertEquals(0.6f, railgun.duration);
        assertEquals(900f, railgun.maxDistance);
    }

    @Test
    void soundEventWithoutPathIsReported(@TempDir Path mods) throws IOException {
        Path data = Files.createDirectories(mods.resolve("quiet/data"));
        Files.writeString(data.resolve("audio.toml"), "[sound_event.nothing]\nvolume = 0.5\n");
        ModContext ctx = new ModdingBootstrap(ASSETS, mods).load(new GameEngine());
        assertTrue(ctx.report.toString().contains("sound_event.nothing has no path"), ctx.report.toString());
    }

    @Test
    void economyDefsLoadFromJsonForMacroSim() {
        ModContext ctx = loadExamples(ModTrustPolicy.WARN_AND_ALLOW);
        List<String> ids = new ArrayList<>();
        for (CommodityDef c : ctx.defs.all(CommodityDef.class)) ids.add(c.id);
        assertTrue(ids.containsAll(List.of("water", "food", "ore", "steel", "machinery", "fuel", "spice")), ids.toString());

        StationTypeDef foundry = ctx.defs.require("station_type", "foundry", StationTypeDef.class);
        assertEquals(30f, foundry.produces.get("steel"));
        assertEquals(60f, foundry.consumes.get("ore"));
        assertTrue(ctx.defs.has("station_type", "spice_farm"));
    }

    @Test
    void scriptDefinesDataAndUsesItsHelperClass() {
        ModContext ctx = loadExamples(ModTrustPolicy.WARN_AND_ALLOW);
        assertTrue(ctx.scriptsAllowed);
        CommodityDef contraband = ctx.defs.require("commodity", "spice_contraband", CommodityDef.class);
        assertTrue(contraband.illegal);
        assertEquals(450f * 1.8f, contraband.basePrice, 0.01f);

        ShipDef smuggler = ctx.defs.require("ship", "corvette_smuggler", ShipDef.class);
        assertEquals("corvette", smuggler.chassis, "copied from the base corvette");
        assertEquals(3.0e6f, smuggler.maxThrust);
        assertTrue(smuggler.hardpoints.isEmpty());
        assertEquals("spice_trade", ctx.defs.entry("ship", "corvette_smuggler").lastSource());
    }

    @Test
    void denyingScriptsStillLoadsModData() {
        ModContext ctx = loadExamples(ModTrustPolicy.DENY_SCRIPTS);
        assertFalse(ctx.scriptsAllowed);
        assertTrue(ctx.defs.has("commodity", "spice"), "spice_trade's data file still loads");
        assertFalse(ctx.defs.has("commodity", "spice_contraband"), "its script did not run");
    }

    @Test
    void scriptsDeniedAtLoadCanRunOnceAllowedLater() {
        ModContext ctx = loadExamples(ModTrustPolicy.DENY_SCRIPTS);
        ctx.hooks.fireWorldReady();
        assertEquals(1, ctx.deferredScripts().size());
        assertFalse(ctx.defs.has("commodity", "spice_contraband"));

        assertTrue(ctx.runDeferredScripts());
        assertTrue(ctx.scriptsAllowed);
        assertTrue(ctx.defs.has("commodity", "spice_contraband"), "the script ran");
        assertTrue(ctx.deferredScripts().isEmpty());
        assertFalse(ctx.runDeferredScripts(), "scripts run only once");
        assertFalse(ctx.report.hasErrors(), ctx.report.toString());
    }

    @Test
    void worldReadyHookAddedAfterTheWorldIsUpRunsAtOnce() {
        ModContext ctx = loadExamples(ModTrustPolicy.DENY_SCRIPTS);
        int[] calls = {0};
        ctx.hooks.onWorldReady(() -> calls[0]++);
        assertEquals(0, calls[0]);
        ctx.hooks.fireWorldReady();
        ctx.hooks.fireWorldReady();
        assertEquals(1, calls[0], "fires once");
        ctx.hooks.onWorldReady(() -> calls[0]++);
        assertEquals(2, calls[0], "late hook runs immediately");
    }

    @Test
    void factorySpawnsShipsWithCoreComponentsAndModuleAssemblers() {
        GameEngine engine = new GameEngine();
        ModContext ctx = new ModdingBootstrap(ASSETS, EXAMPLE_MODS)
                .beforeScripts(c -> c.factory.registerAssembler("ship", (entity, def, factory) -> {
                    ShipDef ship = factory.defs().convert(def, ShipDef.class);
                    entity.add(new MassComponent(ship.mass));
                }))
                .load(engine);

        Entity ship = ctx.factory.spawnShip("corvette_smuggler", 10f, -5f, 1.5f);
        // The base corvette's mass, which the mod's variant copies (ships.toml: 197.1 t with its thin walls)
        assertEquals(197100f, ship.getComponent(MassComponent.class).mass);
        TransformComponent t = ship.getComponent(TransformComponent.class);
        assertEquals(10f, t.x);
        assertEquals(-5f, t.y);
        DefRefComponent ref = ship.getComponent(DefRefComponent.class);
        assertEquals("ship", ref.defType);
        assertEquals("corvette_smuggler", ref.defId);
        String uuid = ship.getComponent(UuidComponent.class).id;
        assertSame(ship, engine.getUuidRegistry().getEntity(uuid), "registered for UUID lookups");

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ctx.factory.spawnShip("no_such_ship", 0, 0, 0));
        assertTrue(missing.getMessage().contains("corvette_patrol"), "lists known ids");
    }

    @Test
    void tickHooksRunWithTheEngine() {
        GameEngine engine = new GameEngine();
        ModContext ctx = new ModdingBootstrap(ASSETS, EXAMPLE_MODS).load(engine);
        float[] total = {0};
        ctx.hooks.onTick(dt -> total[0] += dt);
        engine.update(0.5f);
        engine.update(0.25f);
        assertEquals(0.75f, total[0], 1e-6f);
    }

    @Test
    void brokenModIsReportedAndOthersStillLoad(@TempDir Path mods) throws IOException {
        Path good = Files.createDirectories(mods.resolve("good/data"));
        Files.writeString(good.resolve("x.toml"), "[commodity.gold]\nname = \"Gold\"\nbase_price = 1000.0\n");
        Path bad = Files.createDirectories(mods.resolve("bad/data"));
        Files.writeString(bad.resolve("x.toml"), "[commodity.lead\nname = ");
        Path badScript = Files.createDirectories(mods.resolve("bad_script"));
        Files.writeString(badScript.resolve("init.groovy"), "throw new RuntimeException('boom')");
        Path typo = Files.createDirectories(mods.resolve("typo/data"));
        Files.writeString(typo.resolve("x.toml"), "[ship.ghost]\nchassis = \"no_such_chassis\"\n");

        ModContext ctx = new ModdingBootstrap(ASSETS, mods).load(new GameEngine());
        assertTrue(ctx.defs.has("commodity", "gold"));
        assertTrue(ctx.defs.has("commodity", "water"), "base data unaffected");
        String report = ctx.report.toString();
        assertTrue(report.contains("ERROR [bad:"), report);
        assertTrue(report.contains("ERROR [bad_script] init.groovy failed"), report);
        assertTrue(report.contains("unknown chassis 'no_such_chassis'"), report);
    }

    @Test
    void modAssetPathsResolveInsideTheModFolder(@TempDir Path mods) throws IOException {
        Path mod = Files.createDirectories(mods.resolve("reskin"));
        Files.writeString(mod.resolve("assets.toml"), "[texture]\ntex_corvette = \"art/corvette.png\"\n");
        ModContext ctx = new ModdingBootstrap(ASSETS, mods).load(new GameEngine());
        Path resolved = Paths.get(ctx.assets.path("tex_corvette"));
        assertTrue(resolved.isAbsolute());
        assertEquals(mod.resolve("art/corvette.png").toAbsolutePath().normalize(), resolved);
        assertEquals("libgdx.png", ctx.assets.path("tex_libgdx"), "base paths stay internal");
    }

    public static class MassComponent implements com.badlogic.ashley.core.Component {
        public final float mass;

        MassComponent(float mass) {
            this.mass = mass;
        }
    }
}
