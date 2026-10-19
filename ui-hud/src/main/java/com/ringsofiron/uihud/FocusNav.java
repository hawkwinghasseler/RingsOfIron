package com.ringsofiron.uihud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;

/**
 * Keyboard and gamepad focus for Scene2D menus and windows: Up and Down move between the
 * buttons, check boxes, sliders and drop-downs under a root, in the order they were laid out;
 * Left and Right move a slider or a drop-down's choice, or move like Up and Down elsewhere; Enter
 * presses the focused one. The focused one is tinted, and scrolled into view inside a scroll pane.
 */
public final class FocusNav {
    private static final Color FOCUS = new Color(1f, 0.82f, 0.35f, 1f);

    private Actor focused;
    /** Where the focus was in the list, for when its control is rebuilt. */
    private int index;
    private final Color was = new Color(Color.WHITE);
    private final Array<Actor> found = new Array<>();

    /** Handles {@code keycode} for the controls under {@code root}; true when it was a navigation key. */
    public boolean key(Group root, int keycode) {
        if (keycode != Input.Keys.UP && keycode != Input.Keys.DOWN && keycode != Input.Keys.LEFT
                && keycode != Input.Keys.RIGHT && keycode != Input.Keys.ENTER) return false;
        Stage stage = root.getStage();
        // A text field being typed in keeps Left, Right and Enter
        boolean typing = stage != null && stage.getKeyboardFocus() instanceof TextField;
        if (typing && keycode != Input.Keys.UP && keycode != Input.Keys.DOWN) return false;
        found.clear();
        collect(root, found);
        if (found.size == 0) return false;
        int at = focused == null ? -1 : found.indexOf(focused, true);
        // The window rebuilt its rows: carry on from the same place
        if (at < 0 && focused != null) {
            at = Math.min(index, found.size - 1);
            focus(found.get(at));
        }
        switch (keycode) {
            case Input.Keys.UP:
                focus(found.get(at < 0 ? found.size - 1 : (at - 1 + found.size) % found.size));
                return true;
            case Input.Keys.DOWN:
                focus(found.get(at < 0 ? 0 : (at + 1) % found.size));
                return true;
            case Input.Keys.LEFT:
            case Input.Keys.RIGHT: {
                int dir = keycode == Input.Keys.RIGHT ? 1 : -1;
                if (at >= 0 && adjust(focused, dir)) return true;
                focus(found.get(at < 0 ? 0 : (at + dir + found.size) % found.size));
                return true;
            }
            default:
                if (at < 0) {
                    focus(found.get(0));
                    return true;
                }
                press(focused);
                return true;
        }
    }

    /** The control with focus, or null. */
    public Actor focused() {
        return focused;
    }

    /** Drops the focus, as when a window closes or a menu is rebuilt. */
    public void clear() {
        if (focused != null) focused.setColor(was);
        focused = null;
    }

    private void focus(Actor a) {
        clear();
        focused = a;
        index = Math.max(0, found.indexOf(a, true));
        was.set(a.getColor());
        a.setColor(FOCUS);
        for (Group g = a.getParent(); g != null; g = g.getParent()) {
            if (g instanceof ScrollPane) {
                ScrollPane pane = (ScrollPane) g;
                Actor child = a;
                float x = 0f, y = 0f;
                while (child != null && child.getParent() != pane) {
                    x += child.getX();
                    y += child.getY();
                    child = child.getParent();
                }
                pane.scrollTo(x, y, a.getWidth(), a.getHeight());
                break;
            }
        }
    }

    private static boolean adjust(Actor a, int dir) {
        if (a instanceof Slider) {
            Slider s = (Slider) a;
            s.setValue(s.getValue() + dir * s.getStepSize());
            return true;
        }
        if (a instanceof SelectBox) {
            SelectBox<?> box = (SelectBox<?>) a;
            int n = box.getItems().size;
            if (n == 0) return true;
            box.setSelectedIndex((box.getSelectedIndex() + dir + n) % n);
            return true;
        }
        return false;
    }

    private static void press(Actor a) {
        if (a instanceof CheckBox) {
            CheckBox c = (CheckBox) a;
            c.setChecked(!c.isChecked());
        } else if (a instanceof Button) {
            ChangeListener.ChangeEvent e = Pools.obtain(ChangeListener.ChangeEvent.class);
            a.fire(e);
            Pools.free(e);
        }
    }

    private static void collect(Actor a, Array<Actor> out) {
        if (!a.isVisible() || a.getTouchable() == Touchable.disabled) return;
        if (a instanceof Button) {
            if (!((Button) a).isDisabled()) out.add(a);
            return;
        }
        if (a instanceof Slider || a instanceof SelectBox) {
            out.add(a);
            return;
        }
        if (a instanceof Group) {
            for (Actor child : ((Group) a).getChildren()) collect(child, out);
        }
    }
}
