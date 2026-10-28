package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * Prograde and retrograde markers around the player's ship in the exterior view: the direction it
 * is moving along its orbit, and the opposite, at {@link #RADIUS} pixels from the ship. Prograde is
 * a circle with three ticks, retrograde a circle with a cross, as flight sims draw them.
 *
 * <p>While a target is locked they show the velocity relative to it instead: drawn in
 * {@link #TARGET_PROGRADE} rather than {@link #PROGRADE}, each with a "TGT" tag beside it, so the
 * frame can be told at a glance and is never mistaken for the orbit's.
 *
 * <p>{@link com.ringsofiron.uihud.systems.NavMarkerSystem} places it each frame.
 */
public class NavMarkers extends Actor {
    public static final float RADIUS = 70f;
    public static final Color PROGRADE = new Color(0.85f, 1f, 0.3f, 1f);
    /** The markers' colour while they are relative to the target, as in the flight readout's frame line. */
    public static final Color TARGET_PROGRADE = new Color(1f, 0.45f, 0.95f, 1f);
    public static final String TARGET_TAG = "TGT";
    private static final float SYMBOL = 7f;

    private final TextureRegion white;
    private final BitmapFont font;
    private final GlyphLayout tag = new GlyphLayout();
    private float shipX, shipY, angle;
    private boolean shown, targetFrame;

    public NavMarkers(Skin skin) {
        white = skin.getRegion("white");
        font = skin.getFont("default-font");
        setTouchable(Touchable.disabled);
    }

    /** Shows the markers around a ship at UI point (x, y), moving at {@code angleRad} in screen terms. */
    public void show(float x, float y, float angleRad) {
        show(x, y, angleRad, false);
    }

    /** As {@link #show(float, float, float)}, the angle relative to the locked target when {@code relativeToTarget}. */
    public void show(float x, float y, float angleRad, boolean relativeToTarget) {
        shipX = x;
        shipY = y;
        angle = angleRad;
        targetFrame = relativeToTarget;
        shown = true;
    }

    public void hide() {
        shown = false;
    }

    public boolean isShown() {
        return shown;
    }

    /** Whether the shown markers are relative to the target rather than the orbit. */
    public boolean isTargetFrame() {
        return shown && targetFrame;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!shown) return;
        Color old = batch.getColor().cpy();
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        Color tint = targetFrame ? TARGET_PROGRADE : PROGRADE;
        HudDraw.color(batch, tint, parentAlpha);
        float px = shipX + cos * RADIUS, py = shipY + sin * RADIUS;
        HudDraw.ring(batch, white, px, py, SYMBOL, 1.5f);
        // Ticks out of the circle: forwards and to both sides, so it reads as pointing along the motion
        for (float a : new float[]{0f, MathUtils.HALF_PI, -MathUtils.HALF_PI}) {
            float c = MathUtils.cos(angle + a), s = MathUtils.sin(angle + a);
            HudDraw.line(batch, white, px + c * SYMBOL, py + s * SYMBOL, px + c * SYMBOL * 2f, py + s * SYMBOL * 2f, 1.5f);
        }
        float rx = shipX - cos * RADIUS, ry = shipY - sin * RADIUS;
        HudDraw.ring(batch, white, rx, ry, SYMBOL, 1.5f);
        float d = SYMBOL * 0.7f;
        HudDraw.line(batch, white, rx - d, ry - d, rx + d, ry + d, 1.5f);
        HudDraw.line(batch, white, rx - d, ry + d, rx + d, ry - d, 1.5f);
        if (targetFrame) {
            drawTag(batch, tint, parentAlpha, px, py);
            drawTag(batch, tint, parentAlpha, rx, ry);
        }
        batch.setColor(old);
    }

    /** "TGT" just off the marker at (x, y), on the side away from the ship so it never sits on the hull. */
    private void drawTag(Batch batch, Color c, float parentAlpha, float x, float y) {
        Color fontOld = font.getColor().cpy();
        font.setColor(c.r, c.g, c.b, c.a * parentAlpha);
        tag.setText(font, TARGET_TAG);
        float gap = SYMBOL * 2.4f;
        float tx = x >= shipX ? x + gap : x - gap - tag.width;
        float ty = y + tag.height / 2f;
        font.draw(batch, tag, tx, ty);
        font.setColor(fontOld);
    }
}
