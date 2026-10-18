package com.ringsofiron.moddingdata;

import java.util.List;
import java.util.logging.Logger;

/**
 * Decides whether mod scripts may run. Scripts get unrestricted JVM access (see Technical
 * Design Decisions, "Modding Security"), so the UI should ask the player before running
 * them; ui-hud supplies that prompt as an implementation of this interface.
 */
@FunctionalInterface
public interface ModTrustPolicy {

    /**
     * @param scriptMods the enabled mods that have an {@code init.groovy}
     * @return true to run their scripts; false loads their data and assets only
     */
    boolean allowScripts(List<ModManifest> scriptMods);

    /** Logs a warning naming each script mod, then runs them. The default until the UI prompt exists. */
    ModTrustPolicy WARN_AND_ALLOW = mods -> {
        if (!mods.isEmpty()) {
            Logger.getLogger("RingsOfIron.modding").warning(
                    "Running mod scripts with full access to your computer: " + mods
                            + ". Only use mods from people you trust.");
        }
        return true;
    };

    /** Safe mode: data and assets from mods still load, scripts never run. */
    ModTrustPolicy DENY_SCRIPTS = mods -> false;
}
