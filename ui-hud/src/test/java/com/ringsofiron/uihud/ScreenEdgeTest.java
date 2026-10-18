package com.ringsofiron.uihud;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenEdgeTest {

    @Test
    void pointOffRightClampsToRightMargin() {
        Vector2 p = new Vector2(5000f, 400f);
        float angle = ScreenEdge.clampToEdge(p, 1000f, 800f, 20f);
        assertEquals(980f, p.x, 1e-3f);
        assertEquals(400f, p.y, 1e-3f);
        assertEquals(0f, angle, 1e-3f);
    }

    @Test
    void diagonalPointKeepsDirection() {
        Vector2 p = new Vector2(-1500f, -1100f); // far down-left of a 1000x800 screen
        float angle = ScreenEdge.clampToEdge(p, 1000f, 800f, 20f);
        assertTrue(p.x >= 20f - 1e-3f && p.y >= 20f - 1e-3f);
        // Same direction from the center as before clamping.
        float clampedAngle = (float) Math.toDegrees(Math.atan2(p.y - 400f, p.x - 500f));
        assertEquals(angle, clampedAngle, 0.01f);
    }

    @Test
    void onScreenPointsAreLeftAlone() {
        Vector2 p = new Vector2(300f, 200f);
        ScreenEdge.clampToEdge(p, 1000f, 800f, 20f);
        assertEquals(300f, p.x, 1e-3f);
        assertTrue(ScreenEdge.isOnScreen(300f, 200f, 1000f, 800f));
        assertFalse(ScreenEdge.isOnScreen(-1f, 200f, 1000f, 800f));
    }
}
