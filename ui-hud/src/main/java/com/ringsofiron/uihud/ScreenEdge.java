package com.ringsofiron.uihud;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** Clamps off-screen marker positions to the screen border, for edge arrows. */
public final class ScreenEdge {
    private ScreenEdge() {}

    public static boolean isOnScreen(float x, float y, float width, float height) {
        return x >= 0f && y >= 0f && x <= width && y <= height;
    }

    /**
     * Moves {@code p} toward the screen center until it lies inside the rectangle inset by
     * {@code margin} on every side, along the line from the center. Returns the angle in degrees
     * (counter-clockwise from +x) from the screen center toward the original point.
     */
    public static float clampToEdge(Vector2 p, float width, float height, float margin) {
        float cx = width / 2f;
        float cy = height / 2f;
        float dx = p.x - cx;
        float dy = p.y - cy;
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        float halfW = Math.max(1f, cx - margin);
        float halfH = Math.max(1f, cy - margin);
        float scale = Math.max(Math.abs(dx) / halfW, Math.abs(dy) / halfH);
        if (scale > 1f) {
            p.set(cx + dx / scale, cy + dy / scale);
        }
        return angle;
    }
}
