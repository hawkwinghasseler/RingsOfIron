package com.hawkwing.RingsOfIron.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shapes of the textures the shield is drawn with. */
class ShieldTexturesTest {
    @Test
    void theBubbleIsAThinLineOverASoftGlow() {
        assertTrue(ShieldTextures.bandAlpha(0f) > 0.9f, "bright at its middle");
        assertTrue(ShieldTextures.bandAlpha(0.3f) < 0.3f, "faint off it");
        assertTrue(ShieldTextures.bandAlpha(1f) < 0.01f, "nothing at its edge");
    }

    @Test
    void theFlecksAreSoft() {
        assertEquals(1f, ShieldTextures.dotAlpha(0f), 1e-6f);
        assertTrue(ShieldTextures.dotAlpha(0.5f) > 0.2f && ShieldTextures.dotAlpha(0.5f) < 0.8f, "a gradient, not a solid disc");
        assertEquals(0f, ShieldTextures.dotAlpha(1f), 1e-6f);
    }
}
