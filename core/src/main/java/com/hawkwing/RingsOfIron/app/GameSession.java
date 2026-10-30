package com.hawkwing.RingsOfIron.app;

import com.hawkwing.RingsOfIron.game.CombatTargetingProvider;
import com.hawkwing.RingsOfIron.game.FirstPlay;
import com.hawkwing.RingsOfIron.game.GameHud;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.OpeningGuide;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.PlayerGunnery;
import com.hawkwing.RingsOfIron.game.SilencedMarkers;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;

/**
 * The state the game's parts share once it is created: the world and scenario, the HUD, the
 * player's fire control and docking, the guides under way, and which text panel is open. Each part
 * ({@link GameInput}, {@link SaveController}, {@link RenderPipeline} ...) reads and changes it here.
 */
public final class GameSession {
    /** {@code -Dview.anywhere=true}: V switches the view anywhere and the ship flies from the deck, for testing. */
    static final boolean VIEW_ANYWHERE = Boolean.getBoolean("view.anywhere");

    final GameOptions options;
    final GameWorld world;
    final GameScenario game;
    final UiHud hud;
    final ViewportManager viewports;
    GameHud gameHud;
    /** Round 10: a fallen base's batteries greyed on the HUD. */
    SilencedMarkers silencedMarkers;
    /** The player's fire control; a new one for each ship the player takes. */
    PlayerGunnery gun;
    PlayerDocking docking;
    FirstPlay firstPlay;
    /** The light walkthrough for a new game, or null once it is done or off. */
    OpeningGuide opening;
    /** The notice log window (L): open, and how far it is scrolled back. */
    boolean logOpen;
    int logScroll;
    /** The controls panel (F1). */
    boolean helpOpen;

    GameSession(GameOptions options, GameScenario game, UiHud hud) {
        this.options = options;
        this.world = game.world;
        this.game = game;
        this.hud = hud;
        this.viewports = hud.getViewports();
    }

    public GameWorld world() {
        return world;
    }

    public GameScenario game() {
        return game;
    }

    public UiHud hud() {
        return hud;
    }

    public PlayerGunnery gun() {
        return gun;
    }

    /** True while the notice log or the controls panel is open; Esc closes it rather than pausing. */
    public boolean isOverlayOpen() {
        return logOpen || helpOpen;
    }

    void show(String message) {
        world.notices.post(message);
    }

    void show(String message, NoticeLog.Kind kind) {
        world.notices.post(message, kind);
    }

    String key(Action action) {
        return options.key(action);
    }

    boolean deckView() {
        return hud.getViewMode().getMode() == ViewMode.INTERIOR;
    }

    /** Fire control for the player's ship, locked on the scenario's first target. */
    void arm() {
        gun = new PlayerGunnery(world.bridge, world.combatDefs, world.sensors, world.weaponControl, world.missiles, game.player);
        gun.setTarget(game.firstTarget());
        hud.setTargeting(new CombatTargetingProvider(gun));
    }

    /** The player's (new) ship's own weapons, and the view for where the avatar sits on it. */
    void rearm() {
        arm();
        matchViewToSeat();
    }

    /** Points the gunnery, HUD, view and guides at the game's entities, after a load replaced them. */
    void rebindAfterLoad() {
        // The loaded ship's own weapons; its sensor lock comes back with the save.
        gun = new PlayerGunnery(world.bridge, world.combatDefs, world.sensors, world.weaponControl, world.missiles, game.player);
        if (gun.getTarget() == null) gun.setTarget(game.firstTarget());
        hud.setTargeting(new CombatTargetingProvider(gun));
        matchViewToSeat();
        // A save made in First Play carries on there; any other leaves it
        firstPlay = FirstPlay.resume(world, game);
        opening = firstPlay != null ? null
                : OpeningGuide.resume(world, () -> hud.getMissionWindow().isOpen(), options.openingWanted);
    }

    /** Outside while seated at a console, on deck otherwise. */
    void matchViewToSeat() {
        if (VIEW_ANYWHERE) return;
        hud.getViewMode().setMode(world.bridge.seat() != null ? ViewMode.EXTERIOR : ViewMode.INTERIOR);
    }

    /** Whether a new game gets the opening walkthrough; off also stops one under way. */
    public void setOpeningGuide(boolean wanted) {
        if (!wanted && opening != null) {
            opening.stop();
            opening = null;
        }
    }

    /** Moves First Play and the walkthrough on, saying each new objective or step. */
    void updateGuides() {
        if (firstPlay != null && firstPlay.update() != null) {
            String next = firstPlay.objective(this::key);
            show(next.substring(next.indexOf(':') + 1).trim(), NoticeLog.Kind.IMPORTANT);
        }
        updateOpening();
    }

    /** Moves the walkthrough on, says each new step, and lets it go once its last word is said. */
    private void updateOpening() {
        if (opening == null || options.paused) return;
        // Leaving the station before taking a job ends it quietly
        if (world.playerStation() == null && opening.step() != OpeningGuide.Step.DONE) {
            opening.stop();
            opening = null;
            return;
        }
        if (!opening.update()) return;
        show(opening.text(this::key), NoticeLog.Kind.IMPORTANT);
        if (opening.finished()) opening = null;
    }
}
