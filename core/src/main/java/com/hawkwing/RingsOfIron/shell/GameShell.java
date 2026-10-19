package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.hawkwing.RingsOfIron.RingsOfIronGame;
import com.hawkwing.RingsOfIron.game.NewGame;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.MixLevels;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.UiHud;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The game shell: the main menu (Continue, New game, First play, Load, Settings, Quit), the pause
 * menu on Esc (Resume, Save, Load, Settings, Quit to menu, Quit), named save slots and the settings
 * window, around {@link RingsOfIronGame}. Run with {@code gradlew :lwjgl3:runGame}.
 *
 * <p>The game runs inside it unchanged: the shell creates it on New game or Load and disposes it on
 * Quit to menu. Esc reaches the game's own windows first (map, market, yard, console); with none
 * open it pauses. Keys pass through {@link RemappedInput}, so the player's bindings and the gamepad
 * reach the game as its own keys.
 */
public class GameShell implements ApplicationListener {
    private final Path assetsDir;
    private final Path modsDir;
    private final FileHandle savesDir;

    private GameSettings settings;
    private FileHandle settingsFile;
    private SaveSlots slots;
    private KeyBindings bindings;
    private GamepadKeys pad;
    private RemappedInput input;

    private Skin skin;
    private Stage stage;
    private Table root;
    private ShellMenus menus;
    private SpriteBatch batch;
    private TextureRegion white;
    private final float[] stars = new float[3 * 220];

    private RingsOfIronGame game;
    /** Arrow-key and pad focus in the menus. */
    final com.ringsofiron.uihud.FocusNav menuNav = new com.ringsofiron.uihud.FocusNav();
    private boolean paused;
    /** Work for the start of the next frame, such as starting a game from a button. */
    private Runnable pending;
    /** The settings window waiting for a key to bind, or null. */
    private KeyCapture capture;

    interface KeyCapture {
        void captured(int keycode);
    }

    /** Base data from {@code assets/data} under the working directory, mods from {@code mods}, saves in {@code saves}. */
    public GameShell() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath(), null);
    }

    /** @param savesDir where saves and the settings file go; null for {@code saves} under the working directory */
    public GameShell(Path assetsDir, Path modsDir, FileHandle savesDir) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
        this.savesDir = savesDir;
    }

    @Override
    public void create() {
        FileHandle dir = savesDir != null ? savesDir : Gdx.files.local("saves");
        slots = new SaveSlots(dir);
        settingsFile = dir.child(GameSettings.FILE_NAME);
        settings = GameSettings.load(settingsFile);
        // OpenAL reads this when each game's audio starts
        if (!"auto".equals(settings.hrtf) && System.getProperty("audio.hrtf") == null) {
            System.setProperty("audio.hrtf", settings.hrtf);
        }
        bindings = settings.bindings();
        pad = new GamepadKeys();
        pad.setEnabled(settings.gamepad);

        skin = HudSkin.create();
        white = skin.getRegion("white");
        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport());
        root = new Table();
        root.setFillParent(true);
        stage.addActor(root);
        menus = new ShellMenus(this, skin);
        RandomXS128 random = new RandomXS128(7);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = random.nextFloat();
            stars[i + 1] = random.nextFloat();
            stars[i + 2] = 0.2f + random.nextFloat() * 0.8f;
        }

        input = new RemappedInput(Gdx.input, bindings, pad);
        InputProcessor front = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (capture != null) {
                    KeyCapture c = capture;
                    capture = null;
                    if (keycode != Input.Keys.ESCAPE) c.captured(keycode);
                    else c.captured(-1);
                    return true;
                }
                if (menuShown()) {
                    if (keycode == Input.Keys.ESCAPE) {
                        back();
                        return true;
                    }
                    // Arrow keys and Enter (and the pad, which sends them) move between the buttons
                    if (menuNav.key(root, keycode)) return true;
                    return stage.keyDown(keycode) || true;
                }
                if (keycode == Input.Keys.ESCAPE && game != null && !game.isRefitOpen() && !hudWindowOpen()) {
                    openPauseMenu();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyUp(int keycode) {
                return menuShown() && (stage.keyUp(keycode) || true);
            }

            @Override
            public boolean keyTyped(char character) {
                return menuShown() && (stage.keyTyped(character) || true);
            }

            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                return menuShown() && (stage.touchDown(x, y, pointer, button) || true);
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                return menuShown() && (stage.touchUp(x, y, pointer, button) || true);
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                return menuShown() && (stage.touchDragged(x, y, pointer) || true);
            }

            @Override
            public boolean mouseMoved(int x, int y) {
                return menuShown() && (stage.mouseMoved(x, y) || true);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return menuShown() && (stage.scrolled(amountX, amountY) || true);
            }
        };
        input.setFront(front);
        Gdx.input.setInputProcessor(new InputMultiplexer(front, input.processor()));
        showMain();
    }

    /** True while a menu is up: the main menu, or the pause menu over a game. */
    public boolean menuShown() {
        return game == null || paused;
    }

    /** A HUD window, the traffic map or the game's notice log or controls panel is open: Esc closes it first. */
    boolean hudWindowOpen() {
        UiHud hud = game.getHud();
        return hud.getTrafficMap().isVisible() || hud.getTradeWindow().isOpen() || hud.getYardWindow().isOpen()
                || hud.getMissionWindow().isOpen() || hud.getBoardingWindow().isOpen()
                || hud.getConsoleWindow().isOpen() || game.isOverlayOpen();
    }

    @Override
    public void render() {
        // The backend puts its own input back before every frame
        input.setReal(Gdx.input);
        Gdx.input = input;
        input.setRemapping(game != null && !paused && !game.isRefitOpen());
        // A menu or window takes the pad for moving between its buttons; the map does not
        pad.setMenuMode(menuShown() || (game != null && (game.getHud().openWindow() != null || game.isOverlayOpen())));
        input.update();
        if (pending != null) {
            Runnable r = pending;
            pending = null;
            r.run();
        }
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        if (game != null) {
            game.setPaused(paused);
            game.render();
            if (!paused) return;
            Gdx.gl.glEnable(GL20.GL_BLEND);
            stage.getViewport().apply(true);
            batch.setProjectionMatrix(stage.getCamera().combined);
            batch.begin();
            batch.setColor(0f, 0f, 0f, 0.55f);
            batch.draw(white, 0f, 0f, stage.getWidth(), stage.getHeight());
            batch.setColor(1f, 1f, 1f, 1f);
            batch.end();
        } else {
            Gdx.gl.glClearColor(0.01f, 0.01f, 0.03f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            drawBackdrop(delta);
        }
        stage.getViewport().apply(true);
        stage.act(delta);
        stage.draw();
    }

    /** A slowly drifting star field behind the main menu, and the limb of a planet. */
    private float drift;

    private void drawBackdrop(float delta) {
        drift += delta * 0.004f;
        stage.getViewport().apply(true);
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        float w = stage.getWidth(), h = stage.getHeight();
        for (int i = 0; i < stars.length; i += 3) {
            float x = ((stars[i] + drift * stars[i + 2]) % 1f) * w;
            float b = stars[i + 2];
            batch.setColor(0.7f + 0.3f * b, 0.8f + 0.2f * b, 1f, b);
            batch.draw(white, x, stars[i + 1] * h, b < 0.8f ? 1f : 2f, b < 0.8f ? 1f : 2f);
        }
        // The planet below: rings of falling brightness, as a lit limb
        float r = w * 0.9f, cx = w * 0.5f, cy = -r * 0.82f;
        for (int k = 0; k < 6; k++) {
            batch.setColor(0.25f, 0.5f, 0.9f, 0.10f - k * 0.015f);
            ring(cx, cy, r + k * 3f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private void ring(float cx, float cy, float r) {
        int n = 180;
        float seg = MathUtils.PI2 * r / n + 1f;
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float x = cx + MathUtils.cos(a) * r, y = cy + MathUtils.sin(a) * r;
            if (y < 0f) continue;
            batch.draw(white, x, y - 1.5f, 0f, 1.5f, seg, 3f, 1f, 1f, a * MathUtils.radiansToDegrees + 90f);
        }
    }

    // --- What the menus do -------------------------------------------------------------

    void showMain() {
        menus.showMain(root);
    }

    /** Opens the New game panel: the seed and the sponsor. */
    public void openNewGame() {
        menus.showNewGame(root);
    }

    /** Back out of the open menu: the pause menu resumes, a sub-panel goes back to its menu. */
    void back() {
        capture = null;
        if (menus.isTopLevel()) {
            if (game != null) resumeGame();
            return;
        }
        if (game == null) showMain();
        else menus.showPause(root);
    }

    /** Starts a new game: the current scenario, or the scripted first play. */
    public void newGame(boolean firstPlay) {
        pending = () -> start(firstPlay, null, NewGame.designed());
    }

    /** Starts a new career with the seed and sponsor {@code newGame} asks for. */
    public void newGame(NewGame newGame) {
        pending = () -> start(false, null, newGame);
    }

    /** Loads the newest save. False when there is none. */
    public boolean continueGame() {
        SaveSummary latest = slots.latest();
        if (latest == null) return false;
        load(latest);
        return true;
    }

    public void load(SaveSummary save) {
        pending = () -> {
            if (game == null) start(false, save.file, NewGame.designed());
            else if (game.load(save.file)) {
                resumeGame();
                game.showNotice("Loaded " + save.name);
            } else {
                menus.showMessage("Couldn't load " + save.name + "; see the log");
            }
        };
    }

    private void start(boolean firstPlay, FileHandle save, NewGame newGame) {
        if (game != null) disposeGame();
        game = new RingsOfIronGame(assetsDir, modsDir, firstPlay, newGame);
        game.setHosted(true);
        game.setSaveSlots(slots);
        game.setBindings(bindings);
        game.setOpeningGuide(settings.openingGuide);
        game.create();
        game.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        applyAudio();
        if (save != null && !game.load(save)) game.showNotice("Couldn't load that save; started a new game");
        paused = false;
        root.clear();
    }

    /** Which world the running career is ("Seed 1234"), or empty with no game running. */
    String worldDescription() {
        return game == null ? "" : game.worldDescription();
    }

    public void openPauseMenu() {
        if (game == null) return;
        paused = true;
        menus.showPause(root);
    }

    /** Opens the settings window over whichever menu is up. */
    public void openSettings() {
        if (!menuShown()) openPauseMenu();
        menus.showSettings(root);
    }

    public void resumeGame() {
        paused = false;
        capture = null;
        root.clear();
    }

    /** Saves the running game as {@code name}. Null when saved, otherwise what went wrong. */
    public String saveGame(String name) {
        if (game == null) return "No game running";
        String n = name == null || name.trim().isEmpty() ? slots.nextName() : name.trim();
        return game.save(slots.fileFor(n), n);
    }

    public void quitToMenu() {
        pending = () -> {
            disposeGame();
            showMain();
        };
    }

    private void disposeGame() {
        game.dispose();
        game = null;
        paused = false;
        input.setInputProcessor(null);
    }

    public void quit() {
        Gdx.app.exit();
    }

    public SaveSlots slots() {
        return slots;
    }

    public GameSettings settings() {
        return settings;
    }

    public KeyBindings bindings() {
        return bindings;
    }

    public boolean isPlaying() {
        return game != null;
    }

    public boolean isPaused() {
        return paused;
    }

    public RingsOfIronGame getGame() {
        return game;
    }

    void captureKey(KeyCapture capture) {
        this.capture = capture;
    }

    /** Applies and keeps changed settings: volumes and HRTF now, the window if it changed. */
    void applySettings(GameSettings changed, boolean hrtfChanged, boolean displayChanged) {
        settings = changed;
        settings.setBindings(bindings);
        pad.setEnabled(settings.gamepad);
        if (game != null) game.setOpeningGuide(settings.openingGuide);
        try {
            settings.save(settingsFile);
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Could not write " + settingsFile, e);
        }
        applyAudio();
        if (hrtfChanged && game != null) game.getWorld().audio.setHrtf(settings.hrtf);
        if (displayChanged) applyDisplay(settings);
    }

    private void applyAudio() {
        if (game == null) return;
        AudioManager audio = game.getWorld().audio;
        MixLevels levels = audio.getMixLevels();
        if (levels != null) levels.set(settings.masterVolume, settings.effectsVolume, settings.ambienceVolume);
    }

    static void applyDisplay(GameSettings s) {
        if (s.fullscreen) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        else Gdx.graphics.setWindowedMode(s.windowWidth, s.windowHeight);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        if (game != null) game.resize(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (game != null) game.dispose();
        stage.dispose();
        batch.dispose();
        skin.dispose();
    }
}
