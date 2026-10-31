package com.hawkwing.RingsOfIron.app;

import com.hawkwing.RingsOfIron.debug.DebugScenarios;
import com.ringsofiron.career.NewGame;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.ringsofiron.coreengine.input.Action;
import com.hawkwing.RingsOfIron.shell.SaveSlots;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * What a game is started as, and what its host sets on it: most before it is created, a few (paused,
 * the opening guide) while it runs. The parts of the game read them from here.
 */
public final class GameOptions {
    /** Base data from {@code assetsDir}, mods from {@code modsDir}. */
    public final Path assetsDir, modsDir;
    /** Start with the scripted first play rather than free play. */
    public final boolean firstPlay;
    /** The career's seed and sponsor; First Play always takes the designed start. */
    public final NewGame newGame;
    /** Whether new games get the walkthrough; the settings turn it off. */
    public boolean openingWanted = true;
    /** The keys the hints name; the shell gives the player's own. */
    public KeyBindings bindings = new KeyBindings();
    /** Hosted by the game shell: Esc is its pause menu, and the game stands still while paused. */
    public boolean hosted;
    public boolean paused;
    /** Where F5 and F9 keep the quick save; the shell points it at its own saves folder. */
    public SaveSlots slots;
    /** The scenario this game was started as; null in a career. */
    public DebugScenarios.Entry scenarioEntry;
    /** Starts a new game with a scenario (null: a new career); the shell sets it. */
    public Consumer<DebugScenarios.Entry> scenarioLoader;

    public GameOptions(Path assetsDir, Path modsDir, boolean firstPlay, NewGame newGame) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
        this.firstPlay = firstPlay;
        this.newGame = firstPlay ? NewGame.designed() : newGame;
    }

    /** The name of the key bound to {@code action}. */
    public String key(Action action) {
        return KeyBindings.keyName(bindings.keyOf(action));
    }
}
