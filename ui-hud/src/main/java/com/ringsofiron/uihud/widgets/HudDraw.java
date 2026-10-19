package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Lines, rings and dots drawn with a 1x1 white region, for the HUD widgets that draw shapes in the stage's batch. */
final class HudDraw {
    private HudDraw() {
    }

    static void color(Batch batch, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, c.a * alpha);
    }

    static void line(Batch batch, TextureRegion white, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f,
                MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    static void ring(Batch batch, TextureRegion white, float cx, float cy, float r, float width) {
        int n = MathUtils.clamp((int) (r * 0.6f), 12, 64);
        float seg = MathUtils.PI2 * r / n + 0.5f;
        for (int i = 0; i < n; i++) {
            float ang = MathUtils.PI2 * i / n;
            batch.draw(white, cx + MathUtils.cos(ang) * r, cy + MathUtils.sin(ang) * r - width / 2f, 0f, width / 2f,
                    seg, width, 1f, 1f, ang * MathUtils.radiansToDegrees + 90f);
        }
    }

    static void dot(Batch batch, TextureRegion white, float x, float y, float size) {
        batch.draw(white, x - size / 2f, y - size / 2f, size, size);
    }
}
