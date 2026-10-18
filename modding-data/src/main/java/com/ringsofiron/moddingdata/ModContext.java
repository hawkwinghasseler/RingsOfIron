package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;

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
    /** Whether the trust policy allowed mod scripts to run. */
    public final boolean scriptsAllowed;

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
}
