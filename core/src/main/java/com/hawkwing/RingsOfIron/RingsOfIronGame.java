package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.hawkwing.RingsOfIron.app.DevTools;
import com.hawkwing.RingsOfIron.app.GameBootstrap;
import com.hawkwing.RingsOfIron.app.GameInput;
import com.hawkwing.RingsOfIron.app.GameOptions;
import com.hawkwing.RingsOfIron.app.GameSession;
import com.hawkwing.RingsOfIron.app.RenderPipeline;
import com.hawkwing.RingsOfIron.app.SaveController;
import com.hawkwing.RingsOfIron.game.Bridge;
import com.hawkwing.RingsOfIron.game.FirstPlay;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.career.NewGame;
import com.hawkwing.RingsOfIron.game.PlayerGunnery;
import com.hawkwing.RingsOfIron.render.PictureInPicture;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.ringsofiron.coreengine.input.Action;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import com.hawkwing.RingsOfIron.shell.SaveSummary;
import com.ringsofiron.uihud.UiHud;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The game: every module running together through {@link GameWorld}, starting from
 * {@link GameScenario}. Run with {@code gradlew :lwjgl3:runGame}, or {@code gradlew :lwjgl3:runFirstPlay}
 * for the scripted first play ({@link FirstPlay}): undock, fly, fight, dock for repair.
 *
 * <p>Controls, outside: W/S main engine, A/D turn, Z/C strafe, X rotation assist, Tab cycle
 * target, Space fire the guns and laser (the turrets lead the locked target themselves), R launch
 * a missile at it, F drop a decoy, comma/period time warp down/up, mouse wheel zoom. Point defense
 * shoots down incoming missiles by itself. The raider fights back once you shoot at it. A click on a
 * ship or station selects it: a ship in the bubble becomes the locked target, and either becomes the
 * navigation target. Zoomed out, the player's ship and its targets each show in a picture-in-picture
 * bubble ({@link PictureInPicture}), magnified, and a target off screen shows at the edge.
 *
 * <p>The outside view is what you see seated at a console ({@link Bridge}): you start at the helm,
 * which flies the ship and fires it too. V stands you up into the deck view, where WASD walks your
 * character; V again beside the helm or the weapons console sits you there and looks outside. At the
 * weapons console only the guns, missiles, decoys and Tab answer, and the helm flies the ship: Bea, the
 * helmsman, holds the heading you left it on, and with nobody at the helm the ship drifts. Run with
 * {@code -Dview.anywhere=true} to switch views anywhere and fly from the deck, as before. In the deck
 * view E uses the console you stand at, and F or B
 * forces the shut door across a dock (pry or hack, or cut). O turns the lighting off and back
 * on, inside and out. P twice abandons ship: the crew make for the escape pods (your character walks there
 * by itself unless you steer it), and P again launches them; you wake days later at a station in a
 * requisitioned ship. F5
 * quick-saves, F9 loads the quick save. L opens the notice log, F1 the controls. Esc quits, or opens
 * the pause menu when the game runs inside {@link com.hawkwing.RingsOfIron.shell.GameShell}.
 *
 * <p>Keys are read as {@link Action}s, so the player's bindings apply, and the hints on the bottom
 * line come from {@link GameWorld#hints}. Notices go through {@link GameWorld#notices}.
 *
 * <p>Docking: G arms the selected docking port and asks the nearest station for clearance (a
 * station that gives it reaches out with its collar and draws the ship in), or disarms it; H
 * selects the next port; U undocks. Docked at a station, T opens its market and Y its yard, where
 * Rebuild decks opens the ship editor on the ship's layout for the yard to rebuild it to (the game
 * waits meanwhile; Ctrl+S rebuilds, Esc comes back). The readout under the
 * notices shows how the port lines up with the nearest free port of its size.
 *
 * <p>Boarding: G with no station near latches the port onto a ship alongside. Latched to a disabled
 * ship, K opens the boarding window: send the boarding party across (they force the door first if
 * it is shut), and once the ship is taken, mark systems to strip and cargo to carry over. Parts
 * come home as crates in the cargo bays; stations buy them in the market, and a yard fits a salvaged
 * weapon for nothing.
 *
 * <p>Burn planner, shown on the orbit map: N plans a burn, B selects the next one, Up/Down add
 * prograde/retrograde delta-v, Left/Right radial in/out, [ and ] move it along the orbit (Shift for
 * ten times as much), Delete removes it. Burns fire on their own when due.
 *
 * <p>Navigation ({@link com.hawkwing.RingsOfIron.game.Navigation}), from either console: Q picks the
 * next navigation target (Shift+Q the one before; a click on the traffic map picks one too), ; plans
 * a transfer to it for Enter to accept or Backspace to discard, and / warps to the next burn. Helm
 * orders, flown by the helmsman while you are away from the helm: 1 fly the planned burns, 2 match
 * the target's velocity, 3 hold position on it, 4 approach it (4 again changes the distance), 0 cancel.
 *
 * <p>Every key named here is its {@link Action}'s default.
 *
 * <p>This is the application listener only: {@link GameBootstrap} creates the game and runs its frame,
 * {@link GameInput} reads the keys, {@link SaveController} saves and loads, {@link DevTools} is the
 * developer console and debug menu, and {@link RenderPipeline} draws, in passes, with the HUD's text
 * from ui-hud. They share {@link GameSession}; what the host sets goes into {@link GameOptions}.
 */
public class RingsOfIronGame extends ApplicationAdapter {
    private final GameOptions options;
    /** The game's parts once {@link #create} has made them; null before. */
    private GameBootstrap parts;
    private GameSession session;

    /** Base data from {@code assets/data} under the working directory, mods from {@code mods}. */
    public RingsOfIronGame() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath());
    }

    public RingsOfIronGame(Path assetsDir, Path modsDir) {
        this(assetsDir, modsDir, false);
    }

    /** With {@code firstPlay}, the game starts docked at Highport with {@link FirstPlay}'s objectives. */
    public RingsOfIronGame(Path assetsDir, Path modsDir, boolean firstPlay) {
        this(assetsDir, modsDir, firstPlay, NewGame.designed());
    }

    /** A new career with the seed and sponsor {@code newGame} asks for; First Play always takes the designed start. */
    public RingsOfIronGame(Path assetsDir, Path modsDir, boolean firstPlay, NewGame newGame) {
        this.options = new GameOptions(assetsDir, modsDir, firstPlay, newGame);
    }

    @Override
    public void create() {
        parts = new GameBootstrap(options).create(() -> resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        session = parts.session();
    }

    @Override
    public void render() {
        parts.render();
    }

    /**
     * Esc in play, before it pauses: a transfer plan waiting on Enter or Delete is discarded, as a
     * player backing out of the planner expects. True when Esc was used up here.
     */
    public boolean backOut() {
        return parts.input().backOut();
    }

    /** True while the notice log or the controls panel is open; Esc closes it rather than pausing. */
    public boolean isOverlayOpen() {
        return session != null && session.isOverlayOpen();
    }

    /** The developer console is open over the game. */
    public boolean isDevConsoleOpen() {
        return parts != null && parts.dev() != null && parts.dev().isConsoleOpen();
    }

    /** The developer console has the keyboard: keys are text, not the player's bindings. */
    public boolean isDevConsoleTyping() {
        return parts != null && parts.dev() != null && parts.dev().isTyping();
    }

    public com.hawkwing.RingsOfIron.debug.DevConsole getDevConsole() {
        return parts == null || parts.dev() == null ? null : parts.dev().console();
    }

    /** The debug menu (F3) is open over the game. */
    public boolean isDebugMenuOpen() {
        return parts != null && parts.dev() != null && parts.dev().isMenuOpen();
    }

    public com.hawkwing.RingsOfIron.debug.DebugMenu getDebugMenu() {
        return parts == null || parts.dev() == null ? null : parts.dev().menu();
    }

    /** Starts the game as {@code entry} rather than a career; before {@link #create}. */
    public void setScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry entry) {
        options.scenarioEntry = entry;
    }

    /** The scenario this game is, or null in a career. */
    public com.hawkwing.RingsOfIron.debug.DebugScenario getLoadedScenario() {
        return parts == null || parts.dev() == null ? null : parts.dev().scenario();
    }

    /** How the debug menu's scenarios load: the shell throws this game away and starts another. */
    public void setScenarioLoader(java.util.function.Consumer<com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry> loader) {
        options.scenarioLoader = loader;
    }

    /** Throws this game away for a new one set up as {@code entry}, or a new career for null. */
    public void loadScenario(com.hawkwing.RingsOfIron.debug.DebugScenarios.Entry entry) {
        parts.dev().loadScenario(entry);
    }

    /**
     * Saves the game to {@code file}, opening with a {@link SaveSummary} named {@code name} for the
     * load list. Null when saved, otherwise what to tell the player.
     */
    public String save(FileHandle file, String name) {
        return parts.saves().save(file, name);
    }

    /**
     * Replaces the game with the one saved in {@code file}. True when it loaded. A save that fails
     * to load leaves the game as it was, and says why; one that loads with parts left out (a removed
     * mod's ships, say) says how many, and lists them in the log.
     */
    public boolean load(FileHandle file) {
        return parts.saves().load(file);
    }

    /** Run by {@link com.hawkwing.RingsOfIron.shell.GameShell}: Esc no longer quits, and the shell pauses the game. */
    public void setHosted(boolean hosted) {
        options.hosted = hosted;
    }

    public void setSaveSlots(SaveSlots slots) {
        options.slots = slots;
    }

    /** Whether a new game gets the opening walkthrough; off also stops one under way. */
    public void setOpeningGuide(boolean wanted) {
        options.openingWanted = wanted;
        if (session != null) session.setOpeningGuide(wanted);
    }

    /** The player's key bindings, for the hints; the defaults until the shell sets them. */
    public void setBindings(KeyBindings bindings) {
        options.bindings = bindings;
    }

    /** Paused, the game draws but neither reads input nor moves on. */
    public void setPaused(boolean paused) {
        options.paused = paused;
    }

    /** True while the ship editor is open for a yard rebuild; it has the keyboard then. */
    public boolean isRefitOpen() {
        return parts != null && parts.isRefitOpen();
    }

    public UiHud getHud() {
        return session == null ? null : session.hud();
    }

    public GameWorld getWorld() {
        return session == null ? null : session.world();
    }

    public GameScenario getScenario() {
        return session == null ? null : session.game();
    }

    /** The player's fire control: what the guns are aimed at. */
    public PlayerGunnery getGun() {
        return session == null ? null : session.gun();
    }

    /** "Seed 1234" or "Designed start": which world this career is, for the pause menu. */
    public String worldDescription() {
        return session == null ? "" : session.game().newGame().describe();
    }

    public void showNotice(String message) {
        session.world().notices.post(message);
    }

    /** B while stranded, or the pause menu: the first press asks and names the fee, a second calls the tug. */
    public void callTow() {
        parts.input().callTow();
    }

    /** True when the beacon can be called now, for the pause menu. */
    public boolean canCallTow() {
        return session.world().beacon.refusal() == null;
    }

    /** From the pause menu, where the menu itself is the confirmation. */
    public String callTowNow() {
        return session.world().beacon.call();
    }

    @Override
    public void resize(int width, int height) {
        parts.resize(width, height);
    }

    @Override
    public void dispose() {
        parts.dispose();
    }
}
