package com.ringsofiron.uihud.variants.cassette;

/**
 * One unit of a Yard Standard HUD: the master unit, the visor, a pod. A part is drawn from its mockup's
 * own coordinates ({@link #mx}, {@link #my} is where the mockup put its top left at 1280 x 800), and the
 * layout moves it to {@link #x}, {@link #y} each frame, so its drawing code reads like its mockup.
 *
 * <p>Bounds are design units, y down from the stage's top left. The bounds are the unit's housing, which
 * the layout test checks for overlaps; a unit may draw a little outside them (a shadow, a hinge).
 */
public abstract class YardPart {
    /** The mockup's top left for this unit. */
    protected final float mx, my;
    /** Where the layout put it this frame, and its size. */
    public float x, y, w, h;
    public boolean visible;

    protected YardPart(float mx, float my) {
        this.mx = mx;
        this.my = my;
    }

    public void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /**
     * The mockup's y for the unit's top as it stands this frame: {@link #my}, unless something riding on
     * top of the unit comes and goes (the helm line on the flight pod).
     */
    protected float top() {
        return my;
    }

    /** Draws the unit where the layout put it. */
    public final void paint(YardPaint p) {
        p.origin(x - mx, y - top());
        draw(p);
        p.origin(0f, 0f);
    }

    /** Draws the unit in its mockup's coordinates. */
    protected abstract void draw(YardPaint p);

    public boolean contains(float px, float py) {
        return visible && px >= x && px <= x + w && py >= y && py <= y + h;
    }

    /** Whether a press at design ({@code px}, {@code py}) would do something here. */
    public boolean pressable(float px, float py) {
        return false;
    }

    /** A press at design ({@code px}, {@code py}): true when it did something. */
    public boolean press(float px, float py) {
        return false;
    }

    /**
     * What the pointer over design ({@code px}, {@code py}) shows: a line of text, or null for nothing.
     * Hover never shows a number a keyboard player can't reach elsewhere.
     */
    public String hover(float px, float py) {
        return null;
    }

    /** The mockup's x and y for a design point, so a press can be tested in the mockup's coordinates. */
    protected float localX(float px) {
        return px - (x - mx);
    }

    protected float localY(float py) {
        return py - (y - top());
    }
}
