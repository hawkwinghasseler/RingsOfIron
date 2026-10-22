package com.hawkwing.RingsOfIron.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The debug menu (F3): the scenarios to load into a new game, the overlays to toggle, the loaded scenario's own
 * buttons, and stopping and stepping the world. It floats over the game beside the developer console and works
 * through the same {@link DevConsole}, so whatever it does the console can do by name, and the other way round.
 * The game keeps running under it; clicks on the game still select for the console.
 */
public final class DebugMenu extends Window {
    private static final float WIDTH = 380f;

    private final DevConsole dev;
    private final Supplier<String> loaded;
    private final Table body = new Table();
    private final ScrollPane scroll;
    private final List<Runnable> syncs = new ArrayList<>();
    private int overlaysSeen = -1, actionsSeen = -1;
    private boolean open;

    /** @param loaded the loaded scenario's name for the title, or null in a career; read every frame */
    public DebugMenu(Skin skin, DevConsole dev, Supplier<String> loaded) {
        super("Debug menu", skin);
        this.dev = dev;
        this.loaded = loaded;
        setBackground(skin.newDrawable("white", new Color(0.02f, 0.05f, 0.09f, 0.95f)));
        setMovable(true);
        setResizable(true);
        setResizeBorder(8);
        setKeepWithinStage(true);
        getTitleLabel().setEllipsis(true);
        pad(24f, 8f, 8f, 8f);
        body.top().left();
        body.defaults().left().growX();
        scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        add(scroll).grow();
        setSize(WIDTH, 520f);
        // The wheel over the menu scrolls it, not the camera's zoom
        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && getStage() != null) getStage().setScrollFocus(scroll);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1 && getStage() != null && (toActor == null || !toActor.isDescendantOf(DebugMenu.this))
                        && getStage().getScrollFocus() == scroll) {
                    getStage().setScrollFocus(null);
                }
            }
        });
        setVisible(false);
    }

    public boolean isOpen() {
        return open;
    }

    /** Shows or hides the menu; the first time, by the right edge of {@code stage}. */
    public void setOpen(boolean open, Stage stage) {
        this.open = open;
        setVisible(open);
        if (open) {
            if (getStage() != stage) {
                stage.addActor(this);
                setPosition(stage.getWidth() - getWidth() - 12f, stage.getHeight() - getHeight() - 60f);
            }
            toFront();
        } else if (getStage() != null && getStage().getScrollFocus() == scroll) {
            getStage().setScrollFocus(null);
        }
    }

    public void toggle(Stage stage) {
        setOpen(!open, stage);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!open) return;
        if (dev.overlays.revision() != overlaysSeen || dev.menuActions.revision() != actionsSeen) rebuild();
        for (Runnable r : syncs) r.run();
        String name = loaded == null ? null : loaded.get();
        getTitleLabel().setText(name == null ? "Debug menu" : "Debug menu - " + name);
    }

    private void rebuild() {
        overlaysSeen = dev.overlays.revision();
        actionsSeen = dev.menuActions.revision();
        syncs.clear();
        body.clearChildren();
        Skin skin = getSkin();

        heading("Scenarios");
        for (DebugScenarios.Entry e : DebugScenarios.all()) entry(e.name, e.description, () -> dev.loadScenario(e));
        entry("New career", "Throw this game away and start a new career, docked at home", () -> dev.loadScenario(null));

        heading("Overlays");
        if (dev.overlays.all().isEmpty()) body.add(new Label("None here", skin, "dim")).row();
        for (DebugOverlays.Toggle t : dev.overlays.all()) {
            CheckBox box = new CheckBox(" " + t.id, skin);
            box.left();
            box.setChecked(t.isShown());
            box.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (box.isChecked() != t.isShown()) t.setShown(box.isChecked());
                }
            });
            // The console may have turned it on or off
            syncs.add(() -> {
                if (box.isChecked() == t.isShown()) return;
                box.setProgrammaticChangeEvents(false);
                box.setChecked(t.isShown());
                box.setProgrammaticChangeEvents(true);
            });
            body.add(box).padTop(4f).row();
            note(t.description);
        }

        if (!dev.menuActions.all().isEmpty()) {
            heading("This scenario");
            for (DebugMenuActions.Action a : dev.menuActions.all()) entry(a.name, a.description, a.run);
        }

        heading("World");
        Table row = new Table();
        row.left();
        row.defaults().padRight(6f);
        TextButton pause = button("Stop", () -> dev.console.submit("pause()"));
        syncs.add(() -> pause.setText(dev.isFrozen() ? "Run" : "Stop"));
        row.add(pause);
        row.add(button("Step 1/60 s", () -> dev.console.submit("step()")));
        row.add(button("Step 1 s", () -> dev.console.submit("step(1)")));
        body.add(row).padTop(4f).row();
        note("~ opens the console: click to select, help() lists its commands");
    }

    private void heading(String text) {
        body.add(new Label(text, getSkin(), "title")).padTop(body.getChildren().size == 0 ? 0f : 12f).row();
    }

    private void entry(String name, String description, Runnable run) {
        body.add(button(name, run)).left().fill(0f, 0f).expand(0, 0).padTop(4f).row();
        note(description);
    }

    private void note(String text) {
        Label l = new Label(text, getSkin(), "dim");
        l.setWrap(true);
        l.setAlignment(Align.left);
        body.add(l).width(WIDTH - 40f).padLeft(4f).row();
    }

    private TextButton button(String text, Runnable run) {
        TextButton b = new TextButton(text, getSkin());
        b.pad(2f, 8f, 2f, 8f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                run.run();
            }
        });
        return b;
    }
}
