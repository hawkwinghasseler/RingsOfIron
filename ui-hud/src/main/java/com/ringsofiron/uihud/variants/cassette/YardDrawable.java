package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/**
 * A Scene2D drawable painted with {@link YardPaint}, so the game's own menus (built from a skin) can
 * wear the Yard Standard's hardware: a faceplate, a push button, a label holder, a toggle.
 *
 * <p>The painter draws in the drawable's own coordinates, measured down from its top-left corner as
 * the mockups are, so a part's code reads like its drawing. A control the keyboard has focused is
 * tinted by the game's focus colour ({@code FocusNav}); a Yard Standard control shows the orange
 * pointer tab instead (style guide 10.2), so the painter is told and the tint goes unused.
 */
public final class YardDrawable extends BaseDrawable {
    /** What a part paints, in its own coordinates (0, 0 its top left), {@code w} by {@code h}. */
    public interface Painter {
        void paint(YardPaint p, float w, float h, boolean focus);
    }

    /**
     * The colour {@code com.ringsofiron.uihud.FocusNav} tints the focused control with, which reaches
     * a drawable as the batch's colour; a test keeps the two the same.
     */
    static final Color FOCUS = new Color(1f, 0.82f, 0.35f, 1f);

    private final YardPaint paint;
    private final Painter painter;
    private final Color was = new Color();

    public YardDrawable(YardPaint paint, Painter painter) {
        this.paint = paint;
        this.painter = painter;
    }

    /** Sets the room it leaves round what sits on it (a label's text, a button's legend), and its least size. */
    public YardDrawable pad(float left, float top, float right, float bottom) {
        setLeftWidth(left);
        setTopHeight(top);
        setRightWidth(right);
        setBottomHeight(bottom);
        return this;
    }

    public YardDrawable min(float width, float height) {
        setMinWidth(width);
        setMinHeight(height);
        return this;
    }

    @Override
    public void draw(Batch batch, float x, float y, float width, float height) {
        was.set(batch.getColor());
        boolean focus = isFocus(was);
        // Design (0, 0) is the drawable's top-left corner: the stage's "top" is its top edge
        paint.begin(batch, y + height, was.a, paint.time());
        paint.origin(x, 0f);
        painter.paint(paint, width, height, focus);
        paint.origin(0f, 0f);
        batch.setColor(was);
    }

    /** Whether {@code c} is the focus tint, at any alpha. */
    static boolean isFocus(Color c) {
        return Math.abs(c.r - FOCUS.r) < 0.01f && Math.abs(c.g - FOCUS.g) < 0.01f && Math.abs(c.b - FOCUS.b) < 0.01f;
    }
}
