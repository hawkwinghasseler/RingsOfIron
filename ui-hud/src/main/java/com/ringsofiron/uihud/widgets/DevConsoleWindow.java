package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Align;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.moddingdata.console.ScriptConsole;

import java.util.List;
import java.util.function.Supplier;

/**
 * The developer console's window: the scrollback above, a line to type Groovy into below, and what
 * is selected in the title. It floats over the game in the same window; drag it by the title and
 * resize it from any edge. Enter runs the line, Up and Down go through the history, Tab completes
 * (twice lists what fits), Page Up and Down scroll. While the line has the keyboard, keys go to
 * it and nowhere else, so typing "m" doesn't open the map.
 *
 * <p>The game decides what toggles it (~) and what Esc does (deselect, then close), since those
 * keys must reach it before the stage.
 */
public class DevConsoleWindow extends Window {
    private static final Color INPUT = new Color(0.75f, 0.9f, 1f, 1f);
    private static final Color RESULT = new Color(0.6f, 1f, 0.7f, 1f);

    private final ScriptConsole console;
    private final Supplier<String> status;
    private final Table lines = new Table();
    private final ScrollPane scroll;
    private final TextField field;
    private final Label.LabelStyle[] styles = new Label.LabelStyle[ScriptConsole.Kind.values().length];
    /** The newest scrollback line shown, and how many are shown, to add only what is new. */
    private ScriptConsole.Line lastShown;
    private int shown;
    private int revision = -1;
    private String lastStatus = "";
    private boolean open;

    /**
     * @param status what the title says after "Console", such as what is selected; read every frame
     */
    public DevConsoleWindow(Skin skin, ScriptConsole console, Supplier<String> status) {
        super("Console", skin);
        this.console = console;
        this.status = status;
        Label.LabelStyle base = skin.get(Label.LabelStyle.class);
        styles[ScriptConsole.Kind.INPUT.ordinal()] = new Label.LabelStyle(base.font, INPUT);
        styles[ScriptConsole.Kind.OUTPUT.ordinal()] = new Label.LabelStyle(base.font, Color.WHITE);
        styles[ScriptConsole.Kind.RESULT.ordinal()] = new Label.LabelStyle(base.font, RESULT);
        styles[ScriptConsole.Kind.INFO.ordinal()] = new Label.LabelStyle(base.font, HudSkin.HUD_DIM);
        styles[ScriptConsole.Kind.ERROR.ordinal()] = new Label.LabelStyle(base.font, HudSkin.HUD_ALERT);

        // Nearly opaque: the HUD's panels under it would otherwise read through the scrollback
        setBackground(skin.newDrawable("white", new Color(0.02f, 0.05f, 0.09f, 0.97f)));
        setMovable(true);
        setResizable(true);
        setResizeBorder(8);
        setKeepWithinStage(true);
        getTitleLabel().setEllipsis(true);
        padTop(24f);
        pad(24f, 6f, 6f, 6f);

        lines.top().left();
        lines.defaults().left().growX();
        scroll = new ScrollPane(lines, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        field = new TextField("", skin);
        // The key that opens the console doesn't type itself
        field.setTextFieldFilter((f, c) -> c != '`' && c != '~');
        field.setFocusTraversal(false);
        field.setMessageText("Groovy: kill(), heal(), help() ...");

        add(scroll).grow().row();
        add(field).growX().padTop(4f);
        setSize(640f, 320f);

        // Enter, history and completion, before the field does its own thing with the keys
        field.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                switch (keycode) {
                    case Input.Keys.ENTER:
                    case Input.Keys.NUMPAD_ENTER:
                        String text = field.getText();
                        field.setText("");
                        console.submit(text);
                        scrollToEnd = true;
                        break;
                    case Input.Keys.UP:
                        setLine(console.historyUp(field.getText()));
                        break;
                    case Input.Keys.DOWN:
                        setLine(console.historyDown());
                        break;
                    case Input.Keys.TAB:
                        complete();
                        break;
                    case Input.Keys.PAGE_UP:
                        scroll.setScrollY(scroll.getScrollY() - scroll.getScrollHeight() * 0.8f);
                        break;
                    case Input.Keys.PAGE_DOWN:
                        scroll.setScrollY(scroll.getScrollY() + scroll.getScrollHeight() * 0.8f);
                        break;
                    default:
                        return false;
                }
                event.cancel();
                return true;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                if (character != '\t' && character != '\r' && character != '\n') return false;
                event.cancel();
                return true;
            }
        });
        // Nothing typed here reaches the HUD's keys or the game's
        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                event.stop();
                return true;
            }

            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                event.stop();
                return true;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                event.stop();
                return true;
            }

            // The wheel over the window scrolls the scrollback, not the camera's zoom
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && getStage() != null) getStage().setScrollFocus(scroll);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1 && getStage() != null && (toActor == null || !toActor.isDescendantOf(DevConsoleWindow.this))
                        && getStage().getScrollFocus() == scroll) {
                    getStage().setScrollFocus(null);
                }
            }
        });
        setVisible(false);
    }

    private boolean scrollToEnd;
    /** The last Tab's text, so a second Tab with nothing more to add lists what fits. */
    private String lastTab;

    private void setLine(String text) {
        field.setText(text);
        field.setCursorPosition(text.length());
    }

    private void complete() {
        String before = field.getText();
        ScriptConsole.Completion c = console.complete(before, field.getCursorPosition());
        if (c.candidates.isEmpty()) return;
        field.setText(c.text);
        field.setCursorPosition(c.cursor);
        if (c.candidates.size() > 1 && c.text.equals(before) && before.equals(lastTab)) {
            List<String> fits = c.candidates;
            String listed = String.join("  ", fits.subList(0, Math.min(fits.size(), ScriptConsole.MAX_LISTED)));
            console.info(fits.size() > ScriptConsole.MAX_LISTED ? listed + "  ... " + fits.size() + " in all" : listed);
            scrollToEnd = true;
        }
        lastTab = c.text;
    }

    public boolean isOpen() {
        return open;
    }

    /** Shows or hides the console; opening it puts it on {@code stage}, in front, with the keyboard. */
    public void setOpen(boolean open, Stage stage) {
        this.open = open;
        setVisible(open);
        if (open) {
            if (getStage() != stage) {
                stage.addActor(this);
                // First time: across the top of the screen
                setPosition(12f, stage.getHeight() - getHeight() - 12f);
            }
            toFront();
            stage.setKeyboardFocus(field);
            scrollToEnd = true;
        } else if (getStage() != null) {
            if (getStage().getKeyboardFocus() != null && getStage().getKeyboardFocus().isDescendantOf(this)) {
                getStage().setKeyboardFocus(null);
            }
            if (getStage().getScrollFocus() == scroll) getStage().setScrollFocus(null);
        }
    }

    public void toggle(Stage stage) {
        setOpen(!open, stage);
    }

    /** True while the console is open with the keyboard: the game should read no keys of its own. */
    public boolean isTyping() {
        return open && getStage() != null && getStage().getKeyboardFocus() == field;
    }

    public TextField getField() {
        return field;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!open) return;
        // A click elsewhere in the game (selecting something) leaves the keyboard here
        if (getStage() != null && getStage().getKeyboardFocus() == null) getStage().setKeyboardFocus(field);
        String s = status == null ? null : status.get();
        if (s == null) s = "";
        if (!s.equals(lastStatus)) {
            lastStatus = s;
            getTitleLabel().setText(s.isEmpty() ? "Console" : "Console - " + s);
        }
        if (console.revision() != revision) {
            revision = console.revision();
            boolean atEnd = scroll.isBottomEdge() || scroll.getMaxY() <= 0f;
            syncLines();
            if (atEnd) scrollToEnd = true;
        }
        if (scrollToEnd) {
            scroll.layout();
            scroll.setScrollPercentY(1f);
            scroll.updateVisualScroll();
            scrollToEnd = false;
        }
    }

    /** Adds the lines written since last time; after a clear, or lines dropped off the top, starts again. */
    private void syncLines() {
        List<ScriptConsole.Line> all = console.lines();
        int from = -1;
        if (lastShown != null) {
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i) == lastShown) {
                    from = i + 1;
                    break;
                }
            }
        }
        if (from < 0) {
            lines.clearChildren();
            shown = 0;
            from = 0;
        }
        for (int i = from; i < all.size(); i++) {
            ScriptConsole.Line line = all.get(i);
            Label label = new Label(line.text.isEmpty() ? " " : line.text, styles[line.kind.ordinal()]);
            label.setWrap(true);
            label.setAlignment(Align.left);
            lines.add(label).row();
            shown++;
        }
        // The console drops its oldest lines past its limit; so does the window
        while (shown > ScriptConsole.MAX_LINES) {
            lines.removeActorAt(0, false);
            shown--;
        }
        lastShown = all.isEmpty() ? null : all.get(all.size() - 1);
    }
}
