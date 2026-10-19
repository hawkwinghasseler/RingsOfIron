package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The shell's menus, built with the HUD's Scene2D skin: the main menu, the pause menu, the load
 * list, the save panel and the settings window. Each fills the shell's root table in place of the
 * last; {@link GameShell} does what their buttons ask.
 */
class ShellMenus {
    enum Panel { MAIN, PAUSE, LOAD, SAVE, SETTINGS, NEW_GAME }

    /** Window sizes the settings offer, filtered to what fits the screen. */
    static final int[][] RESOLUTIONS = {
            {1024, 768}, {1280, 720}, {1280, 800}, {1366, 768}, {1440, 900}, {1600, 900}, {1680, 1050},
            {1920, 1080}, {1920, 1200}, {2560, 1440}};

    private final GameShell shell;
    private final Skin skin;
    private Panel panel = Panel.MAIN;
    private Label message;

    ShellMenus(GameShell shell, Skin skin) {
        this.shell = shell;
        this.skin = skin;
    }

    boolean isTopLevel() {
        return panel == Panel.MAIN || panel == Panel.PAUSE;
    }

    Panel panel() {
        return panel;
    }

    void showMessage(String text) {
        if (message != null) message.setText(text == null ? "" : text);
    }

    private Table frame(Table root, Panel p, String title) {
        panel = p;
        root.clear();
        shell.menuNav.clear();
        Table t = new Table();
        t.setBackground(skin.getDrawable("panel"));
        t.pad(24f);
        t.defaults().pad(4f);
        Label heading = new Label(title, skin, "title");
        heading.setFontScale(p == Panel.MAIN ? 3f : 1.6f);
        t.add(heading).colspan(3).padBottom(p == Panel.MAIN ? 4f : 14f).row();
        root.add(t);
        message = new Label("", skin, "warn");
        return t;
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, skin, "menu");
        b.pad(6f, 24f, 6f, 24f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!b.isDisabled()) action.run();
            }
        });
        return b;
    }

    private void endFrame(Table t) {
        t.add(message).colspan(3).padTop(10f).row();
    }

    // --- Main and pause ----------------------------------------------------------------

    void showMain(Table root) {
        Table t = frame(root, Panel.MAIN, "RINGS OF IRON");
        t.add(new Label("A Newtonian ship sim", skin, "dim")).colspan(3).padBottom(24f).row();
        SaveSummary latest = shell.slots().latest();
        TextButton cont = button("Continue", () -> {
            if (!shell.continueGame()) showMessage("No saves yet");
        });
        cont.setDisabled(latest == null);
        t.add(cont).width(260f).colspan(3).row();
        if (latest != null) t.add(new Label(latest.name + "   " + latest.details(), skin, "dim")).colspan(3).row();
        t.add(button("New game", () -> showNewGame(root))).width(260f).colspan(3).row();
        t.add(button("First play (guided)", () -> shell.newGame(true))).width(260f).colspan(3).row();
        TextButton load = button("Load", () -> showLoad(root));
        load.setDisabled(latest == null);
        t.add(load).width(260f).colspan(3).row();
        t.add(button("Settings", () -> showSettings(root))).width(260f).colspan(3).row();
        t.add(button("Quit", shell::quit)).width(260f).colspan(3).row();
        endFrame(t);
    }

    void showPause(Table root) {
        Table t = frame(root, Panel.PAUSE, "PAUSED");
        t.add(button("Resume", shell::resumeGame)).width(260f).colspan(3).row();
        if (shell.getGame() != null && shell.getGame().canCallTow()) {
            // The distress beacon, for a ship that can't get home
            t.add(button("Call a tow", () -> {
                String said = shell.getGame().callTowNow();
                shell.resumeGame();
                shell.getGame().showNotice(said);
            })).width(260f).colspan(3).row();
        }
        t.add(button("Save", () -> showSave(root))).width(260f).colspan(3).row();
        TextButton load = button("Load", () -> showLoad(root));
        load.setDisabled(shell.slots().latest() == null);
        t.add(load).width(260f).colspan(3).row();
        t.add(button("Settings", () -> showSettings(root))).width(260f).colspan(3).row();
        t.add(button("Quit to menu", shell::quitToMenu)).width(260f).colspan(3).row();
        t.add(button("Quit game", shell::quit)).width(260f).colspan(3).row();
        t.add(new Label("Esc resumes   Arrow keys or the pad choose", skin, "dim")).colspan(3).padTop(8f).row();
        String world = shell.worldDescription();
        if (!world.isEmpty()) t.add(new Label(world, skin, "dim")).colspan(3).row();
        endFrame(t);
    }

    // --- New game ----------------------------------------------------------------------

    /** A dim line of explanation, wrapped to the panel's width. */
    private Label note(String text) {
        Label l = new Label(text, skin, "dim");
        l.setWrap(true);
        l.setAlignment(com.badlogic.gdx.utils.Align.center);
        return l;
    }

    /** The factions a new career may serve: id, then the button's label. */
    static final String[][] SPONSORS = {{"patrol", "Terran Patrol"}, {"selene", "Selene Combine"}};

    /** A new career: a typed seed replays that world, an empty one rolls a new world. */
    void showNewGame(Table root) {
        Table t = frame(root, Panel.NEW_GAME, "NEW GAME");
        TextField seed = new TextField("", skin);
        seed.setMaxLength(24);
        seed.setMessageText("random");
        Table seedRow = new Table();
        seedRow.add(new Label("Seed", skin, "dim")).padRight(12f);
        seedRow.add(seed).width(300f);
        t.add(seedRow).colspan(3).row();
        t.add(note("Leave it empty for a new world; the same seed gives the same start")).width(440f).colspan(3).padBottom(10f).row();
        // Who to serve: the Patrol from Highport, or the Combine from its ice mine on the Moon
        String[] sponsor = {null};
        Table sponsors = new Table();
        com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup<TextButton> group = new com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup<>();
        for (String[] s : SPONSORS) {
            TextButton b = new TextButton(s[1], skin, "toggle");
            b.pad(6f, 16f, 6f, 16f);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (b.isChecked()) sponsor[0] = s[0];
                }
            });
            group.add(b);
            sponsors.add(b).padRight(8f);
        }
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        group.getButtons().first().setChecked(true);
        Table serveRow = new Table();
        serveRow.add(new Label("Serve", skin, "dim")).padRight(12f);
        serveRow.add(sponsors);
        t.add(serveRow).colspan(3).row();
        t.add(note("The Patrol starts you at Highport in Earth orbit; the Combine at Shackleton Ice above the Moon")).width(440f)
                .colspan(3).padBottom(10f).row();
        Table buttons = new Table();
        buttons.add(button("Start", () -> shell.newGame(com.hawkwing.RingsOfIron.game.NewGame.fromText(seed.getText(), sponsor[0]))))
                .padRight(10f);
        buttons.add(button("Back", shell::back));
        t.add(buttons).colspan(3).padTop(10f).row();
        endFrame(t);
        root.getStage().setKeyboardFocus(seed);
    }

    // --- Saves -------------------------------------------------------------------------

    void showLoad(Table root) {
        Table t = frame(root, Panel.LOAD, "LOAD GAME");
        Table list = new Table();
        list.top().left();
        list.defaults().pad(3f);
        for (SaveSummary s : shell.slots().list()) {
            Table info = new Table();
            info.add(new Label(s.name, skin, "default")).left().row();
            info.add(new Label(s.details(), skin, "dim")).left().row();
            info.add(new Label(savedAt(s.savedAt), skin, "dim")).left();
            list.add(info).left().expandX().fillX().padRight(16f);
            list.add(button("Load", () -> shell.load(s)));
            list.add(button("Delete", () -> {
                shell.slots().delete(s);
                showLoad(root);
            })).row();
        }
        if (list.getCells().size == 0) list.add(new Label("No saves yet", skin, "dim"));
        ScrollPane scroll = new ScrollPane(list, skin);
        scroll.setFadeScrollBars(false);
        t.add(scroll).width(560f).height(Math.min(420f, Gdx.graphics.getHeight() * 0.6f)).colspan(3).row();
        t.add(button("Back", shell::back)).colspan(3).padTop(10f).row();
        endFrame(t);
    }

    void showSave(Table root) {
        Table t = frame(root, Panel.SAVE, "SAVE GAME");
        TextField name = new TextField(shell.slots().nextName(), skin);
        name.setMaxLength(40);
        t.add(new Label("Name", skin, "dim")).left();
        t.add(name).width(360f).colspan(2).row();
        Table list = new Table();
        list.top().left();
        for (SaveSummary s : shell.slots().list()) {
            if (SaveSlots.QUICK_SAVE_NAME.equals(s.name)) continue;
            TextButton pick = new TextButton(s.name + "   " + s.details(), skin, "menu");
            pick.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            pick.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    name.setText(s.name);
                }
            });
            list.add(pick).left().fillX().expandX().pad(2f).row();
        }
        if (list.getCells().size > 0) {
            t.add(new Label("Overwrite", skin, "dim")).left().top();
            ScrollPane scroll = new ScrollPane(list, skin);
            scroll.setFadeScrollBars(false);
            t.add(scroll).width(360f).height(Math.min(260f, Gdx.graphics.getHeight() * 0.4f)).colspan(2).row();
        }
        Table buttons = new Table();
        buttons.add(button("Save", () -> {
            String failed = shell.saveGame(name.getText());
            if (failed == null) {
                showPause(root);
                showMessage("Saved \"" + name.getText().trim() + "\"");
            } else {
                showMessage(failed);
            }
        })).padRight(10f);
        buttons.add(button("Back", shell::back));
        t.add(buttons).colspan(3).padTop(10f).row();
        endFrame(t);
        root.getStage().setKeyboardFocus(name);
        name.selectAll();
    }

    static String savedAt(long millis) {
        return millis <= 0 ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(millis));
    }

    // --- Settings ----------------------------------------------------------------------

    void showSettings(Table root) {
        Table t = frame(root, Panel.SETTINGS, "SETTINGS");
        GameSettings was = shell.settings();
        GameSettings s = copy(was);
        ObjectMap<String, String> keysWere = new ObjectMap<>(shell.bindings().toMap());

        t.add(new Label("Sound", skin, "title")).left().colspan(3).row();
        volume(t, "Master volume", s.masterVolume, v -> s.masterVolume = v);
        volume(t, "Effects", s.effectsVolume, v -> s.effectsVolume = v);
        volume(t, "Ambience", s.ambienceVolume, v -> s.ambienceVolume = v);
        SelectBox<String> hrtf = new SelectBox<>(skin);
        hrtf.setItems(GameSettings.HRTF_MODES);
        hrtf.setSelected(s.hrtf);
        hrtf.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.hrtf = hrtf.getSelected();
            }
        });
        t.add(new Label("HRTF (3D sound in headphones)", skin, "dim")).left();
        t.add(hrtf).width(120f).left().colspan(2).row();

        t.add(new Label("Display", skin, "title")).left().colspan(3).padTop(10f).row();
        CheckBox fullscreen = new CheckBox(" Fullscreen", skin);
        fullscreen.setChecked(s.fullscreen);
        fullscreen.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.fullscreen = fullscreen.isChecked();
            }
        });
        t.add(fullscreen).left().colspan(3).row();
        SelectBox<String> resolution = new SelectBox<>(skin);
        Array<String> sizes = resolutions(s);
        resolution.setItems(sizes);
        resolution.setSelected(size(s.windowWidth, s.windowHeight));
        resolution.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int[] wh = parseSize(resolution.getSelected());
                if (wh != null) {
                    s.windowWidth = wh[0];
                    s.windowHeight = wh[1];
                }
            }
        });
        t.add(new Label("Window size", skin, "dim")).left();
        t.add(resolution).width(160f).left().colspan(2).row();

        t.add(new Label("Controls", skin, "title")).left().colspan(3).padTop(10f).row();
        CheckBox gamepad = new CheckBox(" Gamepad", skin);
        gamepad.setChecked(s.gamepad);
        gamepad.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.gamepad = gamepad.isChecked();
            }
        });
        t.add(gamepad).left().colspan(3).row();
        CheckBox guide = new CheckBox(" Walkthrough at the start of a new game", skin);
        guide.setChecked(s.openingGuide);
        guide.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.openingGuide = guide.isChecked();
            }
        });
        t.add(guide).left().colspan(3).row();
        Table keys = new Table();
        keys.top().left();
        Array<TextButton> keyButtons = new Array<>();
        for (KeyBindings.Action a : KeyBindings.Action.values()) {
            keys.add(new Label(a.label, skin, "dim")).left().expandX().padRight(12f);
            TextButton b = new TextButton(KeyBindings.keyName(shell.bindings().keyOf(a)), skin, "menu");
            keyButtons.add(b);
            b.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    b.setText("press a key (Esc cancels)");
                    capture(a, keyButtons);
                }
            });
            keys.add(b).width(200f).pad(1f).row();
        }
        ScrollPane scroll = new ScrollPane(keys, skin);
        scroll.setFadeScrollBars(false);
        t.add(scroll).width(520f).height(Math.min(240f, Gdx.graphics.getHeight() * 0.3f)).colspan(3).row();

        Table buttons = new Table();
        buttons.add(button("Reset keys", () -> {
            shell.bindings().reset();
            refreshKeys(keyButtons);
        })).padRight(10f);
        buttons.add(button("Apply", () -> {
            boolean hrtfChanged = !s.hrtf.equals(was.hrtf);
            boolean displayChanged = s.fullscreen != was.fullscreen
                    || (!s.fullscreen && (s.windowWidth != was.windowWidth || s.windowHeight != was.windowHeight));
            shell.applySettings(s, hrtfChanged, displayChanged);
            shell.back();
        })).padRight(10f);
        buttons.add(button("Back", () -> {
            shell.bindings().fromMap(keysWere);
            shell.back();
        }));
        t.add(buttons).colspan(3).padTop(10f).row();
        endFrame(t);
    }

    /**
     * Waits for the key to bind {@code a} to. A key another action has asks first: the same key
     * again swaps the two, another key is taken as the choice instead, Esc cancels.
     */
    private void capture(KeyBindings.Action a, Array<TextButton> keyButtons) {
        shell.captureKey(key -> choose(a, key, keyButtons));
    }

    private void choose(KeyBindings.Action a, int key, Array<TextButton> keyButtons) {
        KeyBindings.Action other = key < 0 ? null : shell.bindings().actionOn(key);
        if (other == null || other == a) {
            if (key >= 0) bindAndSay(a, key);
            else showMessage("");
            refreshKeys(keyButtons);
            return;
        }
        String name = KeyBindings.keyName(key);
        showMessage(name + " is " + other.label + ". " + name + " again swaps them (" + other.label + " moves to "
                + KeyBindings.keyName(shell.bindings().keyOf(a)) + "); another key picks that instead; Esc cancels");
        shell.captureKey(again -> {
            if (again == key) {
                bindAndSay(a, key);
                refreshKeys(keyButtons);
            } else {
                choose(a, again, keyButtons);
            }
        });
    }

    private void bindAndSay(KeyBindings.Action a, int key) {
        KeyBindings.Action moved = shell.bindings().bind(a, key);
        showMessage(moved == null ? (KeyBindings.reserved(key) ? KeyBindings.keyName(key) + " can't be bound" : "")
                : moved.label + " moved to " + KeyBindings.keyName(shell.bindings().keyOf(moved)));
    }

    private void refreshKeys(Array<TextButton> buttons) {
        KeyBindings.Action[] actions = KeyBindings.Action.values();
        for (int i = 0; i < buttons.size; i++) buttons.get(i).setText(KeyBindings.keyName(shell.bindings().keyOf(actions[i])));
    }

    interface FloatSetter {
        void set(float v);
    }

    private void volume(Table t, String name, float value, FloatSetter setter) {
        Slider slider = new Slider(0f, 1f, 0.05f, false, skin);
        slider.setValue(value);
        Label pct = new Label(percent(value), skin, "default");
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(slider.getValue());
                pct.setText(percent(slider.getValue()));
            }
        });
        t.add(new Label(name, skin, "dim")).left();
        t.add(slider).width(200f);
        t.add(pct).width(50f).left().row();
    }

    static String percent(float v) {
        return Math.round(v * 100f) + "%";
    }

    static String size(int w, int h) {
        return w + " x " + h;
    }

    static int[] parseSize(String s) {
        if (s == null) return null;
        String[] p = s.split(" x ");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Array<String> resolutions(GameSettings s) {
        Graphics.DisplayMode screen = Gdx.graphics.getDisplayMode();
        Array<String> out = new Array<>();
        for (int[] r : RESOLUTIONS) {
            if (screen == null || (r[0] <= screen.width && r[1] <= screen.height)) out.add(size(r[0], r[1]));
        }
        String current = size(s.windowWidth, s.windowHeight);
        if (!out.contains(current, false)) out.add(current);
        return out;
    }

    static GameSettings copy(GameSettings s) {
        GameSettings c = new GameSettings();
        c.masterVolume = s.masterVolume;
        c.effectsVolume = s.effectsVolume;
        c.ambienceVolume = s.ambienceVolume;
        c.hrtf = s.hrtf;
        c.fullscreen = s.fullscreen;
        c.windowWidth = s.windowWidth;
        c.windowHeight = s.windowHeight;
        c.gamepad = s.gamepad;
        c.openingGuide = s.openingGuide;
        c.keys = new ObjectMap<>(s.keys);
        return c;
    }
}
