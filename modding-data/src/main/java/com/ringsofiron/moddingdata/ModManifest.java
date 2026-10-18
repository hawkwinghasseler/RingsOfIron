package com.ringsofiron.moddingdata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A mod's {@code mod.toml}. Every field is optional; a folder without a manifest is a mod
 * whose id is the folder name.
 *
 * <pre>
 * id = "better_railguns"
 * name = "Better Railguns"
 * version = "1.2.0"
 * author = "someone"
 * description = "Faster railgun reload."
 * depends = ["core_weapons_pack"]   # must be present; loaded first
 * load_after = ["other_mod"]        # loaded first if present
 * enabled = true
 * </pre>
 */
public class ModManifest {
    public String id;
    public String name;
    public String version = "0.0.0";
    public String author = "";
    public String description = "";
    public List<String> depends = new ArrayList<>();
    public List<String> loadAfter = new ArrayList<>();
    public boolean enabled = true;

    /** Folder the mod was found in. Not read from the file. */
    public transient Path directory;

    public Path dataDir() {
        return directory.resolve("data");
    }

    public Path assetsManifest() {
        return directory.resolve("assets.toml");
    }

    public Path initScript() {
        return directory.resolve("init.groovy");
    }

    public boolean hasScript() {
        return java.nio.file.Files.isRegularFile(initScript());
    }

    @Override
    public String toString() {
        return id + " " + version;
    }
}
