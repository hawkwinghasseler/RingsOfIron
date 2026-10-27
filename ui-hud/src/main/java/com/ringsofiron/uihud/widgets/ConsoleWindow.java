package com.ringsofiron.uihud.widgets;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;

import java.util.Locale;

/**
 * Window opened when the avatar uses a console. The prototype shows the console's subsystem
 * status and an on/off switch; real per-console layouts (helm, weapons, ...) plug in through
 * {@link ContentBuilder}s registered by console id.
 */
public class ConsoleWindow extends Window {

    /** Fills the window body for one kind of console. */
    public interface ContentBuilder {
        void build(Table body, Skin skin, Entity console, InteractableConsoleComponent data);
    }

    private final Skin skin;
    private final Table body = new Table();
    private final Runnable onClose;
    private Entity console;
    private SubsystemComponent subsystem;
    private Label status;
    private TextButton power;

    public ConsoleWindow(Skin skin, Runnable onClose) {
        super("", skin);
        this.skin = skin;
        this.onClose = onClose;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        add(body).grow().row();
        TextButton close = new TextButton("Close [Esc]", skin);
        close.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        add(close).right().padTop(10f);
        setVisible(false);
    }

    public boolean isOpen() {
        return isVisible();
    }

    public Entity getConsole() {
        return console;
    }

    public void open(Entity console, InteractableConsoleComponent data, ContentBuilder custom) {
        open(console, data, custom, null);
    }

    /**
     * Opens on {@code console}: {@code custom}'s layout, or the default status and on/off switch, then
     * {@code extra}'s rows under it (null for none), such as a gun's "take it" button.
     */
    public void open(Entity console, InteractableConsoleComponent data, ContentBuilder custom, ContentBuilder extra) {
        this.console = console;
        getTitleLabel().setText(data.title.toUpperCase(Locale.ROOT));
        body.clearChildren();
        body.top().left();
        subsystem = console.getComponent(SubsystemComponent.class);
        status = null;
        power = null;
        if (custom != null) {
            custom.build(body, skin, console, data);
        } else {
            buildDefault(data);
        }
        if (extra != null) extra.build(body, skin, console, data);
        setVisible(true);
        pack();
        setWidth(Math.max(getWidth(), 280f));
        if (getStage() != null) {
            setPosition((getStage().getWidth() - getWidth()) / 2f, (getStage().getHeight() - getHeight()) / 2f);
        }
        toFront();
    }

    private void buildDefault(InteractableConsoleComponent data) {
        body.add(new Label("Console: " + data.consoleId, skin, "dim")).left().row();
        if (subsystem == null) {
            body.add(new Label("No system attached.", skin)).left().row();
            return;
        }
        body.add(new Label("System: " + subsystem.type, skin)).left().padTop(6f).row();
        status = new Label("", skin);
        body.add(status).left().row();
        power = new TextButton("", skin);
        power.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                subsystem.switchedOn = !subsystem.switchedOn;
                refresh();
            }
        });
        body.add(power).left().padTop(6f).width(140f).row();
        refresh();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isVisible()) refresh();
    }

    private void refresh() {
        if (subsystem == null || status == null) return;
        String state = subsystem.isDestroyed() ? "DESTROYED"
                : !subsystem.switchedOn ? "OFF"
                : subsystem.powered ? "ONLINE" : "NO POWER";
        String text = String.format(Locale.ROOT, "Health %3.0f%%   %s", subsystem.health * 100f, state);
        if (!status.textEquals(text)) status.setText(text);
        power.setText(subsystem.switchedOn ? "Switch off" : "Switch on");
    }

    public void close() {
        if (!isVisible()) return;
        setVisible(false);
        console = null;
        subsystem = null;
        if (onClose != null) onClose.run();
    }
}
