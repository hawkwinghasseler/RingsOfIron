package com.ringsofiron.uihud;

/**
 * How large the HUD and menus are drawn: one UI unit is {@link #effective} screen pixels. Every layout
 * is written in UI units for a 1280x800 window, so a larger screen draws the same layout bigger
 * rather than smaller. The player picks a scale in Settings, or 0 for auto, which follows the
 * window's height: 1x at 1080 lines, 1.25x at 1440, 2x at 2160.
 *
 * The scale is never so large that the window has fewer than 1280x800 UI units, where the panels
 * would start to overlap, and never under 1x, where text would be smaller than it was drawn for.
 */
public final class UiScale {
    /** The design size every layout fits. */
    public static final float DESIGN_WIDTH = 1280f, DESIGN_HEIGHT = 800f;
    /** The scales Settings offers, after 0 for auto. */
    public static final float[] CHOICES = {0f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f};

    private static float chosen;

    private UiScale() {}

    /** 0 for auto, or a fixed scale. */
    public static void setChosen(float scale) {
        chosen = Float.isNaN(scale) || scale < 0f ? 0f : scale;
    }

    public static float getChosen() {
        return chosen;
    }

    /** The scale for a window this size: the chosen one, or auto's, kept within what fits. */
    public static float effective(int width, int height) {
        float want = chosen > 0f ? chosen : auto(height);
        float fits = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
        return Math.max(1f, Math.min(want, fits));
    }

    /** Auto's scale for a window {@code height} pixels tall, in quarter steps. */
    public static float auto(int height) {
        return Math.max(1f, Math.round(height / 1080f * 4f) / 4f);
    }

    /** "Auto" or "125%", as Settings shows a choice. */
    public static String label(float scale) {
        return scale <= 0f ? "Auto" : Math.round(scale * 100f) + "%";
    }
}
