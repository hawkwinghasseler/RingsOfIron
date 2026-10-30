package com.hawkwing.RingsOfIron.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.hawkwing.RingsOfIron.game.AutosaveClock;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import com.hawkwing.RingsOfIron.shell.SaveSummary;

import java.util.List;

/**
 * Saving and loading the game: the quick save (F5, F9), the rotating autosaves and when they are
 * due, and the shell's named slots. Says how each went in the notices. Needs no window: the slots
 * are files, and after a load {@code rebind} points the rest of the game at the new entities.
 */
public final class SaveController {
    private final GameOptions options;
    private final GameWorld world;
    private final GameScenario game;
    private final Runnable rebind;
    /** When to autosave: docking, and every ten minutes of flight outside warp. */
    private final AutosaveClock autosaves = new AutosaveClock();
    /** Whether there is an autosave to offer when the ship is lost. */
    private boolean haveAutosave;

    public SaveController(GameOptions options, GameScenario game, Runnable rebind) {
        this.options = options;
        this.world = game.world;
        this.game = game;
        this.rebind = rebind;
    }

    /** Where the saves go: the shell's folder, or {@code saves} under the working directory. */
    SaveSlots slots() {
        if (options.slots == null) options.slots = new SaveSlots(Gdx.files.local("saves"));
        return options.slots;
    }

    /** Looks for an autosave to offer, once the game has started. */
    void start() {
        haveAutosave = slots().latestAutosave() != null;
    }

    /** True when there is an autosave to load once the ship is lost. */
    public boolean haveAutosave() {
        return haveAutosave;
    }

    /** Saves to the next autosave slot if one is due ({@code why}: what made it due). */
    void autosaveIfDue() {
        String why = autosaves.due(world, game.player);
        if (why != null) autosave(why);
    }

    /** Saves to the next of the rotating autosave slots; says so only if it failed. */
    public void autosave(String why) {
        int n = slots().nextAutosave();
        String failed = save(slots().autosave(n), SaveSlots.autosaveName(n));
        if (failed != null) show("Autosave " + why + " skipped: " + failed);
        else haveAutosave = true;
        restartClock();
    }

    public void loadLatestAutosave() {
        SaveSummary latest = slots().latestAutosave();
        if (latest == null) show("No autosave yet");
        else load(latest.file);
    }

    public void quickSave() {
        String failed = save(slots().quickSave(), SaveSlots.QUICK_SAVE_NAME);
        show(failed == null ? "Game saved" : failed);
    }

    public void quickLoad() {
        FileHandle file = slots().quickSave();
        if (!file.exists()) {
            show("No quick save yet (" + options.key(Action.QUICK_SAVE) + " saves)");
            return;
        }
        load(file);
    }

    /**
     * Saves the game to {@code file}, opening with a {@link SaveSummary} named {@code name} for the
     * load list. Null when saved, otherwise what to tell the player.
     */
    public String save(FileHandle file, String name) {
        try {
            String refused = game.quickSave(file, SaveSummary.of(game, name));
            if (refused != null) return "Can't save: " + refused;
            return null;
        } catch (RuntimeException e) {
            error("Save failed", e);
            return "Save failed; see the log";
        }
    }

    /**
     * Replaces the game with the one saved in {@code file}. True when it loaded. A save that fails
     * to load leaves the game as it was, and says why; one that loads with parts left out (a removed
     * mod's ships, say) says how many, and lists them in the log.
     */
    public boolean load(FileHandle file) {
        try {
            List<String> warnings = game.quickLoad(file);
            world.clearCrash();
            rebindAfterLoad();
            for (String warning : warnings) error("Load of " + file.name() + ": " + warning, null);
            show(warnings.isEmpty() ? "Game loaded"
                    : "Game loaded, but " + warnings.size() + (warnings.size() == 1 ? " part" : " parts")
                    + " of the save couldn't be; see the log");
            return true;
        } catch (GameScenario.LoadFailedException e) {
            error("Load of " + file.name() + " failed", e);
            // Put back from the snapshot, the entities are new ones: point everything at them
            if (e.replaced) rebindAfterLoad();
            show(e.restored ? "Load failed (" + e.getMessage() + "); the game is as it was"
                    : "Load failed and the game couldn't be put back; load another save");
            return false;
        } catch (RuntimeException e) {
            error("Load failed", e);
            show("Load failed; see the log");
            return false;
        }
    }

    /** Points the gunnery, HUD, view and guides at the game's entities, after they were replaced. */
    private void rebindAfterLoad() {
        rebind.run();
        restartClock();
    }

    private void show(String message) {
        world.notices.post(message);
    }

    private void restartClock() {
        autosaves.restart(world.playerStation() != null, world.engine.getSimSeconds());
    }

    /** To the application's log, or standard error with no application (a test). */
    private static void error(String message, Throwable e) {
        if (Gdx.app == null) {
            System.err.println("RingsOfIron: " + message + (e == null ? "" : ": " + e));
        } else if (e == null) {
            Gdx.app.error("RingsOfIron", message);
        } else {
            Gdx.app.error("RingsOfIron", message, e);
        }
    }
}
