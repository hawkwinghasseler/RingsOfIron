package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Runs each mod's {@code init.groovy}, in load order.
 *
 * Scripts see these variables:
 * <ul>
 *   <li>{@code engine}: the {@link GameEngine}</li>
 *   <li>{@code factory}: the {@link EntityFactory} (register assemblers, spawn entities)</li>
 *   <li>{@code defs}: the {@link DefRegistry} (read or define data)</li>
 *   <li>{@code assets}: the {@link AssetRegistry}</li>
 *   <li>{@code hooks}: {@link ModHooks} lifecycle callbacks</li>
 *   <li>{@code mod}: this mod's {@link ModManifest}</li>
 *   <li>{@code log}: a {@link java.util.logging.Logger} named after the mod</li>
 * </ul>
 * The mod's folder is on the script classpath, so {@code init.groovy} can use other
 * {@code .groovy} classes shipped in the same mod. A failing script is reported and
 * skipped; other mods still run.
 *
 * Scripts are not sandboxed (see {@link ModTrustPolicy}).
 */
public class GroovyModLoader {

    private final GameEngine engine;
    private final EntityFactory factory;
    private final DefRegistry defs;
    private final AssetRegistry assets;
    private final ModHooks hooks;
    private final LoadReport report;
    private final List<String> ran = new ArrayList<>();

    public GroovyModLoader(GameEngine engine, EntityFactory factory, DefRegistry defs, AssetRegistry assets,
                           ModHooks hooks, LoadReport report) {
        this.engine = engine;
        this.factory = factory;
        this.defs = defs;
        this.assets = assets;
        this.hooks = hooks;
        this.report = report;
    }

    public void runScripts(List<ModManifest> mods) {
        for (ModManifest mod : mods) {
            if (mod.hasScript()) runScript(mod);
        }
    }

    /** @return true if the script ran without throwing */
    public boolean runScript(ModManifest mod) {
        Binding binding = new Binding();
        binding.setVariable("engine", engine);
        binding.setVariable("factory", factory);
        binding.setVariable("defs", defs);
        binding.setVariable("assets", assets);
        binding.setVariable("hooks", hooks);
        binding.setVariable("mod", mod);
        binding.setVariable("log", Logger.getLogger("RingsOfIron.mod." + mod.id));

        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        classLoader.addClasspath(mod.directory.toAbsolutePath().toString());
        GroovyShell shell = new GroovyShell(classLoader, binding);
        hooks.setCurrentMod(mod.id);
        try {
            shell.evaluate(mod.initScript().toFile());
            ran.add(mod.id);
            report.info(mod.id, "Ran init.groovy");
            return true;
        } catch (Exception | LinkageError e) {
            report.error(mod.id, "init.groovy failed: " + e);
            return false;
        } finally {
            hooks.setCurrentMod("game");
        }
    }

    /** Ids of mods whose scripts ran successfully, in order. */
    public List<String> ranScripts() {
        return new ArrayList<>(ran);
    }
}
