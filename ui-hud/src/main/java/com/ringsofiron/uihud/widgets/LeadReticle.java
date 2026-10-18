package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * Lead indicator: a small diamond where to aim, with a faint line back to the target marker.
 * Position (x, y) is the aim point; {@link #setTargetPoint} sets the line's other end.
 */
public class LeadReticle extends Actor {
    private static final float SIZE = 9f;
    private final TextureRegion white;
    private float targetX, targetY;

    public LeadReticle(TextureRegion white) {
        this.white = white;
        setTouchable(Touchable.disabled);
        setColor(new Color(1f, 0.45f, 0.35f, 1f));
    }

    public void setTargetPoint(float x, float y) {
        targetX = x;
        targetY = y;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color c = getColor();
        Color old = batch.getColor().cpy();
        float x = getX();
        float y = getY();

        // Connector line from target to lead point.
        float dx = x - targetX;
        float dy = y - targetY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > SIZE * 1.5f) {
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            batch.setColor(c.r, c.g, c.b, 0.35f * c.a * parentAlpha);
            batch.draw(white, targetX, targetY - 0.5f, 0f, 0.5f, len - SIZE, 1f, 1f, 1f, angle);
        }

        // Diamond outline: vertices on the axes at distance r, each side runs to the next vertex.
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
        float r = SIZE;
        float side = r * 1.4142f;
        float t = 2f;
        for (int i = 0; i < 4; i++) {
            float vertex = i * 90f;
            float vx = x + r * (float) Math.cos(Math.toRadians(vertex));
            float vy = y + r * (float) Math.sin(Math.toRadians(vertex));
            batch.draw(white, vx, vy - t / 2f, 0f, t / 2f, side, t, 1f, 1f, vertex + 135f);
        }
        batch.draw(white, x - 1f, y - 1f, 2f, 2f);
        batch.setColor(old);
    }
}
