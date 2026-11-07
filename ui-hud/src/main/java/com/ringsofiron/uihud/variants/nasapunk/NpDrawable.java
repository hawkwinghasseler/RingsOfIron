package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/**
 * A Scene2D drawable painted through the kit's canvas, so the game's own menus, built from a skin,
 * can wear the style's hardware and glass: the flight computer's bezel, its prompts and fields, the
 * cursor. The painter draws in the drawable's own coordinates, y down from its top left corner, as
 * the mockups do.
 *
 * <p>A control the keyboard or the pad has reached is tinted by the game's focus colour
 * ({@code FocusNav}), and the tint reaches a drawable as the batch's colour. A Nasa-punk control
 * shows the computer's cyan cursor instead (the shell's README, "The cursor"), so the painter is
 * told, and the tint itself goes unused.
 */
public final class NpDrawable extends BaseDrawable {
    /** What a part paints, {@code w} by {@code h} from its top left corner; {@code focus} when the cursor is on it. */
    public interface Painter {
        void paint(NpCanvas cv, float w, float h, boolean focus);
    }

    /** The colour {@code com.ringsofiron.uihud.FocusNav} tints the focused control with; a test keeps the two the same. */
    static final Color FOCUS = new Color(1f, 0.82f, 0.35f, 1f);

    private final NpCanvas cv;
    private final Painter painter;
    private final Color was = new Color();

    public NpDrawable(NpCanvas cv, Painter painter) {
        this.cv = cv;
        this.painter = painter;
    }

    /** The room it leaves round what sits on it (a button's legend, a field's text). */
    public NpDrawable pad(float left, float top, float right, float bottom) {
        setLeftWidth(left);
        setTopHeight(top);
        setRightWidth(right);
        setBottomHeight(bottom);
        return this;
    }

    public NpDrawable min(float width, float height) {
        setMinWidth(width);
        setMinHeight(height);
        return this;
    }

    @Override
    public void draw(Batch batch, float x, float y, float width, float height) {
        was.set(batch.getColor());
        cv.begin(batch, x, y + height, was.a);
        painter.paint(cv, width, height, isFocus(was));
        cv.end();
        batch.setColor(was);
    }

    /** Whether {@code c} is the focus tint, at any alpha (a menu fading in still shows its cursor). */
    static boolean isFocus(Color c) {
        return Math.abs(c.r - FOCUS.r) < 0.01f && Math.abs(c.g - FOCUS.g) < 0.01f && Math.abs(c.b - FOCUS.b) < 0.01f;
    }
}
