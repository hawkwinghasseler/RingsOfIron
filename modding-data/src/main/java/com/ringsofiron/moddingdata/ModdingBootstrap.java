package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.moddingdata.defs.ShipSaleDef;
import com.ringsofiron.moddingdata.defs.ShipyardDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;
import com.ringsofiron.moddingdata.defs.CommodityDef;
import com.ringsofiron.moddingdata.defs.MaterialDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.moddingdata.defs.SoundEventData;
import com.ringsofiron.moddingdata.defs.StationTypeDef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The pre-game loading step. Typical use from the launcher:
 * <pre>
 * ModContext mods = new ModdingBootstrap(Paths.get("assets"), Paths.get("mods"))
 *         .trustPolicy(hudPrompt)
 *         .beforeScripts(ctx -&gt; CombatDefs.register(ctx.defs))   // modules bind their def classes
 *         .load(gameEngine);
 * </pre>
 * Order: discover mods; load base {@code data/}; load each mod's {@code data/}; read
 * {@code assets.toml} files; ask the trust policy; run {@code init.groovy} scripts; check
 * cross-references. Base data that fails to parse throws, since the game cannot run
 * without it. Problems in mods are recorded in {@link ModContext#report} and that mod's
 * broken part is skipped.
 */
public class ModdingBootstrap {

    /** Def types this module ships classes for. Other modules register theirs the same way. */
    public static void registerBuiltInTypes(DefRegistry defs) {
        defs.registerType("ship", ShipDef.class);
        defs.registerType("chassis", ChassisDef.class);
        defs.registerType("material", MaterialDef.class);
        defs.registerType("tile", TileTypeDef.class);
        defs.registerType("component", ShipComponentDef.class);
        defs.registerType("commodity", CommodityDef.class);
        defs.registerType("station_type", StationTypeDef.class);
        defs.registerType("sound_event", SoundEventData.class);
        defs.registerType("faction", FactionDef.class);
        defs.registerType("rank", RankDef.class);
        defs.registerType("shipyard", ShipyardDef.class);
        defs.registerType("ship_sale", ShipSaleDef.class);
    }

    private final Path assetsDir;
    private final Path modsDir;
    private ModTrustPolicy trustPolicy = ModTrustPolicy.WARN_AND_ALLOW;
    private final List<Consumer<ModContext>> beforeScripts = new ArrayList<>();

    /**
     * @param assetsDir the game's assets folder; holds {@code assets.toml} and {@code data/}
     * @param modsDir   folder of mod folders; may not exist
     */
    public ModdingBootstrap(Path assetsDir, Path modsDir) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
    }

    public ModdingBootstrap trustPolicy(ModTrustPolicy policy) {
        this.trustPolicy = policy;
        return this;
    }

    /** Runs after data is loaded and before mod scripts, so modules can register types and assemblers that scripts build on. */
    public ModdingBootstrap beforeScripts(Consumer<ModContext> step) {
        beforeScripts.add(step);
        return this;
    }

    public ModContext load(GameEngine engine) {
        LoadReport report = new LoadReport();
        DefRegistry defs = new DefRegistry();
        registerBuiltInTypes(defs);
        AssetRegistry assets = new AssetRegistry(report);
        EntityFactory factory = new EntityFactory(engine, defs);
        ModHooks hooks = new ModHooks(report);

        List<ModManifest> mods = new ModDiscovery(report).discover(modsDir);

        TomlDataLoader loader = new TomlDataLoader(defs, report);
        loader.loadDirectory(assetsDir.resolve("data"), "base", true);
        for (ModManifest mod : mods) loader.loadDirectory(mod.dataDir(), mod.id, false);

        assets.loadManifest(assetsDir.resolve("assets.toml"), null, "base");
        for (ModManifest mod : mods) assets.loadManifest(mod.assetsManifest(), mod.directory, mod.id);

        List<ModManifest> scriptMods = new ArrayList<>();
        for (ModManifest mod : mods) if (mod.hasScript()) scriptMods.add(mod);
        boolean allowed = scriptMods.isEmpty() || trustPolicy.allowScripts(scriptMods);

        ModContext ctx = new ModContext(engine, defs, assets, factory, hooks, report, mods, allowed);
        for (Consumer<ModContext> step : beforeScripts) step.accept(ctx);

        if (allowed) {
            new GroovyModLoader(engine, factory, defs, assets, hooks, report).runScripts(scriptMods);
        } else {
            ctx.deferScripts(scriptMods);
            report.warn("mods", "Mod scripts were not run: " + scriptMods);
        }

        // Fires hooks.onTick callbacks each engine update.
        engine.addSystem(hooks.asSystem());

        DefValidator.validate(defs, assets, report);
        return ctx;
    }
}
