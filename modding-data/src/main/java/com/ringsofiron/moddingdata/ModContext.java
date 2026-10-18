package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything {@link ModdingBootstrap} built, handed to the rest of the game. */
public class ModContext {
    public final GameEngine engine;
    public final DefRegistry defs;
    public final AssetRegistry assets;
    public final EntityFactory factory;
    public final ModHooks hooks;
    public final LoadReport report;
    /** Mods that loaded, in load order. */
    public final List<ModManifest> mods;
    /** Whether mod scripts have been allowed, at load or later through {@link #runDeferredScripts()}. */
    public volatile boolean scriptsAllowed;

    /** Script mods held back because the trust policy said no at load time. */
    private List<ModManifest> deferredScripts = Collections.emptyList();

    ModContext(GameEngine engine, DefRegistry defs, AssetRegistry assets, EntityFactory factory, ModHooks hooks,
               LoadReport report, List<ModManifest> mods, boolean scriptsAllowed) {
        this.engine = engine;
        this.defs = defs;
        this.assets = assets;
        this.factory = factory;
        this.hooks = hooks;
        this.report = report;
        this.mods = Collections.unmodifiableList(mods);
        this.scriptsAllowed = scriptsAllowed;
    }

    void deferScripts(List<ModManifest> scriptMods) {
        this.deferredScripts = new ArrayList<>(scriptMods);
    }

    /** Script mods waiting on the player's permission; empty once they ran or if none were held back. */
    public synchronized List<ModManifest> deferredScripts() {
        return Collections.unmodifiableList(deferredScripts);
    }

    /**
     * Runs the scripts that were held back at load, for when the player allows them after the game
     * has started (e.g. the trust dialog was answered on the render thread). Call on the thread
     * that runs the engine. Data, hooks and later spawns pick up what the scripts add; entities
     * spawned before this call keep the components they were built with. World-ready hooks the
     * scripts add run straight away if the world is already up.
     *
     * @return true if any scripts ran; false if none were waiting
     */
    public synchronized boolean runDeferredScripts() {
        if (deferredScripts.isEmpty()) return false;
        List<ModManifest> toRun = deferredScripts;
        deferredScripts = Collections.emptyList();
        scriptsAllowed = true;
        new GroovyModLoader(engine, factory, defs, assets, hooks, report).runScripts(toRun);
        DefValidator.validate(defs, assets, report);
        return true;
    }
}
