package com.hawkwing.RingsOfIron.debug;

import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.moddingdata.console.ScriptConsole;

import java.nio.file.Path;

/**
 * What a {@link DebugScenario} is given to set itself up with: the new game's world and career, and the game
 * screen's debug menu, overlays and console to add its own to. Everything added here lasts until the next load.
 */
public final class ScenarioSetup {
    /** What the game screen does for a scenario. */
    public interface Screen {
        /** True for the deck view, false for the outside view. */
        void setDeckView(boolean deck);

        /** The player has a different ship: the screen re-arms its fire control and seats the view, as after a rescue. */
        void playerShipChanged();

        /** Clicks and drags on the game go to {@code pointer} first while the console is closed; null stops that. */
        void setPointer(ScenarioPointer pointer);
    }

    public final DebugScenarios.Entry entry;
    public final GameWorld world;
    public final GameScenario game;
    public final Path assetsDir;
    public final DevConsole dev;
    public final DebugOverlays overlays;
    public final DebugMenuActions actions;
    private final Screen screen;

    public ScenarioSetup(DebugScenarios.Entry entry, GameWorld world, GameScenario game, Path assetsDir, DevConsole dev,
                         DebugOverlays overlays, DebugMenuActions actions, Screen screen) {
        this.entry = entry;
        this.world = world;
        this.game = game;
        this.assetsDir = assetsDir;
        this.dev = dev;
        this.overlays = overlays;
        this.actions = actions;
        this.screen = screen;
    }

    /** The console, to {@code define} the scenario's own commands on. */
    public ScriptConsole console() {
        return dev.console;
    }

    /** A drawn overlay, listed with the others in the debug menu and {@code overlay()}; {@code on} shows it at once. */
    public void overlay(String id, String description, DebugOverlay overlay, boolean on) {
        overlays.add(id, description, overlay, on);
    }

    /** A button in the debug menu's scenario section, which {@code action("name")} in the console also presses. */
    public void action(String name, String description, Runnable run) {
        actions.add(name, description, run);
    }

    /** The console's tools for hurting and mending the world ({@code dev.actions.tools}). */
    public com.hawkwing.RingsOfIron.game.WorldDebug tools() {
        return dev.actions.tools;
    }

    /** Call after giving the player another ship ({@code game.player} changed), so the guns and view follow it. */
    public void playerShipChanged() {
        if (screen != null) screen.playerShipChanged();
    }

    /**
     * Clicks and drags on the game (not on a window) go to {@code pointer} first while the developer console is
     * closed; with it open they select, as always. Null stops it.
     */
    public void pointer(ScenarioPointer pointer) {
        if (screen != null) screen.setPointer(pointer);
    }

    /** Posts a notice, as the game does. */
    public void notice(String text) {
        world.notices.post(text);
    }

    /** Starts in the deck view, beside the avatar. */
    public void deckView() {
        if (screen != null) screen.setDeckView(true);
    }

    /** Starts in the outside view. */
    public void exteriorView() {
        if (screen != null) screen.setDeckView(false);
    }
}
