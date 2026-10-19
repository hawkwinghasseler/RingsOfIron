package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
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
 * <p>{@link com.ringsofiron.uihud.systems.NavMarkerSystem} places it each frame.
 */
public class NavMarkers extends Actor {
    public static final float RADIUS = 70f;
    public static final Color PROGRADE = new Color(0.85f, 1f, 0.3f, 1f);
    private static final float SYMBOL = 7f;

    private final TextureRegion white;
    private float shipX, shipY, angle;
    private boolean shown;

    public NavMarkers(Skin skin) {
        white = skin.getRegion("white");
        setTouchable(Touchable.disabled);
    }

    /** Shows the markers around a ship at UI point (x, y), moving at {@code angleRad} in screen terms. */
    public void show(float x, float y, float angleRad) {
        shipX = x;
        shipY = y;
        angle = angleRad;
        shown = true;
    }

    public void hide() {
        shown = false;
    }

    public boolean isShown() {
        return shown;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!shown) return;
        Color old = batch.getColor().cpy();
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        HudDraw.color(batch, PROGRADE, parentAlpha);
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
        batch.setColor(old);
    }
}
