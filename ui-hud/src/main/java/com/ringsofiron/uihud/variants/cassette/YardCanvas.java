package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;

/**
 * The one actor a Yard Standard HUD puts on the stage: it fills the stage and paints every visible
 * {@link YardPart} in order, then the hover strip. It takes the pointer only over a part's pressable
 * places and over a part that has something to say on hover, so the rest of the screen stays the world's.
 */
public final class YardCanvas extends Actor {
    private final YardPaint paint;
    private final Array<YardPart> parts = new Array<>();
    private float time;
    private float hoverX = -1f, hoverY = -1f;
    private String hoverText;
    private final Color hoverInk = new Color(Yard.P_AMBER);

    public YardCanvas(YardPaint paint) {
        this.paint = paint;
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                float px = x, py = getHeight() - y;
                for (int i = parts.size - 1; i >= 0; i--) {
                    YardPart p = parts.get(i);
                    if (p.contains(px, py) && p.pressable(px, py) && p.press(px, py)) return true;
                }
                return false;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                hoverX = x;
                hoverY = getHeight() - y;
                return false;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1) hoverX = hoverY = -1f;
            }
        });
    }

    public void add(YardPart part) {
        parts.add(part);
    }

    public Array<YardPart> parts() {
        return parts;
    }

    public YardPaint paint() {
        return paint;
    }

    /** Seconds the canvas has been up, for blinking. */
    public float time() {
        return time;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
        hoverText = null;
        if (hoverX >= 0f) {
            for (int i = parts.size - 1; i >= 0 && hoverText == null; i--) {
                YardPart p = parts.get(i);
                if (p.contains(hoverX, hoverY)) hoverText = p.hover(hoverX, hoverY);
            }
        }
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() != Touchable.enabled) return null;
        if (!isVisible()) return null;
        float px = x, py = getHeight() - y;
        for (int i = parts.size - 1; i >= 0; i--) {
            YardPart p = parts.get(i);
            if (!p.contains(px, py)) continue;
            // Over a unit the pointer is the HUD's: hover shows what it can, a press may do something
            return this;
        }
        return null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color was = batch.getColor().cpy();
        paint.begin(batch, getHeight(), parentAlpha * getColor().a, time);
        for (YardPart p : parts) if (p.visible) p.paint(paint);
        if (hoverText != null) drawHover();
        batch.setColor(was);
    }

    /** The hover strip: a small amber tube by the pointer with the full text of what is under it. */
    private void drawHover() {
        float size = 13f;
        float tw = paint.width(YardFonts.Face.TUBE, size, hoverText) + 16f, th = 22f;
        float x = Math.min(hoverX + 14f, getWidth() - tw - 8f), y = hoverY + 20f;
        if (y + th > getHeight() - 44f) y = hoverY - th - 12f;
        paint.tube(x, y, tw, th, 3f, 3f, false);
        paint.tube(hoverText, x + 8f, y + 15.5f, size, hoverInk, false);
        paint.tubeOver(x, y, tw, th);
    }
}
