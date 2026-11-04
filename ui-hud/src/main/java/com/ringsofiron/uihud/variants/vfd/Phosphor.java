package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;

/**
 * The three colours FILAMENT lights (documentation/ui-overhaul/style-vfd.md, section 3). Zinc is the
 * bare phosphor; amber and red are filters printed on the glass over fixed regions, so a region's
 * colour never changes, only whether it is lit and how hard it is driven.
 *
 * <p>Each colour gives the packed vertex colour for an element at a drive level ({@link #lit}) and
 * for an unlit element seen through the smoked glass ({@link #ghost}), as the mockups' renderer
 * ({@code documentation/ui-overhaul/vfd/src/vfd.js}) does.
 */
public enum Phosphor {
    ZINC(70, 246, 204, 222, 255, 246),
    AMBER(255, 172, 52, 255, 238, 204),
    RED(255, 64, 46, 255, 216, 204);

    /** The phosphor's own colour, 0 to 1. */
    final float r, g, b;
    /** What it whitens towards when driven full. */
    final float hotR, hotG, hotB;

    Phosphor(int r, int g, int b, int hotR, int hotG, int hotB) {
        this.r = r / 255f;
        this.g = g / 255f;
        this.b = b / 255f;
        this.hotR = hotR / 255f;
        this.hotG = hotG / 255f;
        this.hotB = hotB / 255f;
    }

    /**
     * The packed colour of an element lit at {@code level} ({@link Drive}): full drive whitens it and
     * makes it opaque, normal drive warms it a little, and lower levels are the phosphor at that
     * opacity.
     */
    public float lit(float level) {
        if (level >= 0.99f) return mixed(0.42f, 1f);
        if (level >= 0.75f) return mixed(0.12f, Math.min(1f, level + 0.1f));
        return Color.toFloatBits(r, g, b, Math.max(0f, level));
    }

    /** {@link #lit} into {@code out}, for fonts, which take a {@link Color}. */
    public Color lit(float level, Color out) {
        Color.abgr8888ToColor(out, lit(level));
        return out;
    }

    /**
     * The packed colour of an unlit element: the phosphor greyed by the glass, at the ghost level
     * times {@code k} (legends' ghosts are a little stronger than segments', dots' a little weaker).
     */
    public float ghost(float k) {
        float t = 0.35f;
        return Color.toFloatBits(r + (GREY_R - r) * t, g + (GREY_G - g) * t, b + (GREY_B - b) * t,
                Math.min(1f, Drive.GHOST * k));
    }

    public Color ghost(float k, Color out) {
        Color.abgr8888ToColor(out, ghost(k));
        return out;
    }

    private float mixed(float t, float alpha) {
        return Color.toFloatBits(r + (hotR - r) * t, g + (hotG - g) * t, b + (hotB - b) * t, alpha);
    }

    /** The grey a ghost is pulled towards: the smoked glass's own tint. */
    private static final float GREY_R = 150 / 255f, GREY_G = 160 / 255f, GREY_B = 155 / 255f;
}
