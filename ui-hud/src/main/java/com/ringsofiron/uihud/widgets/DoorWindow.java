package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.ringsofiron.uihud.providers.DoorProvider;
import com.ringsofiron.uihud.providers.DoorProvider.Door;
import java.util.Locale;

/**
 * The door menu, opened with [E] at a door (an airlock's door or hatch included): the pressure on
 * this side of it, on the far side and the difference, whether it is locked, and buttons to lock
 * or unlock it, or with no power to work the lock, to order it cut open. Refreshes from the {@link
 * DoorProvider} a few times a second, and closes when the avatar walks away from the door.
 */
public class DoorWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.25f;

    private final Door door = new Door();
    private final Label near, far, diff, state, status;
    private final TextButton lock, cut;
    private DoorProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;

    public DoorWindow(Skin skin) {
        super("", skin);
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        near = new Label("", skin);
        far = new Label("", skin);
        diff = new Label("", skin);
        state = new Label("", skin, "dim");
        status = new Label("", skin, "dim");
        add(near).left().row();
        add(far).left().row();
        add(diff).left().row();
        add(state).left().padTop(8f).row();
        lock = button(skin, "Lock", () -> say(provider.toggleLock()));
        cut = button(skin, "Order it cut open", () -> say(provider.orderCut()));
        add(lock).left().padTop(8f).row();
        add(cut).left().padTop(4f).row();
        add(status).left().padTop(8f).row();
        add(button(skin, "Close [E]", () -> setOpen(false))).right().padTop(10f);
        setVisible(false);
    }

    public void setProvider(DoorProvider provider) {
        this.provider = provider;
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** Opens the window on the door the provider has in use; closing tells the provider. Returns whether it is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            status.setText("");
            refresh();
            if (!door.isOpen()) return false;
            setVisible(true);
            pack();
            if (getStage() != null) {
                setPosition((getStage().getWidth() - getWidth()) / 2f, (getStage().getHeight() - getHeight()) / 2f);
            }
            toFront();
        } else {
            if (isVisible() && provider != null) provider.close();
            setVisible(false);
        }
        return isVisible();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!isVisible()) return;
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_INTERVAL) refresh();
    }

    /** Pulls the door from the provider; closes the window once there is none in use. */
    public void refresh() {
        sinceRefresh = 0f;
        door.clear();
        if (provider != null) provider.collect(door);
        if (!door.isOpen()) {
            if (isVisible()) setOpen(false);
            return;
        }
        getTitleLabel().setText(door.name.toUpperCase(Locale.ROOT));
        near.setText("This side: " + kpa(door.nearKpa));
        far.setText("Far side: " + kpa(door.farKpa));
        diff.setText("Difference: " + kpa(Math.abs(door.nearKpa - door.farKpa)));
        state.setText(door.state);
        lock.setText(door.locked ? "Unlock" : "Lock");
        lock.setDisabled(!door.lockBlocked.isEmpty());
        if (!door.lockBlocked.isEmpty()) lock.setText((door.locked ? "Unlock" : "Lock") + " (" + door.lockBlocked + ")");
        cut.setVisible(door.canCut);
        if (isVisible()) pack();
    }

    static String kpa(float kpa) {
        return String.format(Locale.ROOT, "%.1f kPa", kpa);
    }

    public Door getDoor() {
        return door;
    }

    private void say(String message) {
        refresh();
        status.setText(message == null ? "" : message);
        if (isVisible()) pack();
    }

    private TextButton button(Skin skin, String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.pad(2f, 8f, 2f, 8f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!b.isDisabled() && provider != null) action.run();
            }
        });
        return b;
    }
}
