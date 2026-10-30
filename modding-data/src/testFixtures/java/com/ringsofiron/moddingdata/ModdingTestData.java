package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;
import java.nio.file.Path;
import java.nio.file.Paths;

/** The game's data for tests in any module: each runs from its own folder beside assets and modding-data. */
public final class ModdingTestData {

    public static final Path ASSETS = Paths.get("../assets");
    public static final Path EXAMPLE_MODS = Paths.get("../modding-data/examples/mods");

    private ModdingTestData() {}

    /** The base data with the example mods loaded over it. */
    public static ModContext withExampleMods() {
        return new ModdingBootstrap(ASSETS, EXAMPLE_MODS).load(new GameEngine());
    }
}
