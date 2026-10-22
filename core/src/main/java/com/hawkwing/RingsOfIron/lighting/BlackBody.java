package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * What hot steel looks like: the color a black body glows at a temperature, and how bright. It
 * starts to glow a dull red at the Draper point and brightens with the fourth power of its
 * temperature, through orange toward yellow-white near {@link #BRIGHT}, as a fire's tiles get.
 */
public final class BlackBody {
    /** Where a body first glows visibly, K. */
    public static final float DRAPER_POINT = 798f;
    /** Where the glow is at full strength, K: about a fully involved fire's tiles. */
    public static final float BRIGHT = 1500f;

    private static final float DRAPER4 = (float) Math.pow(DRAPER_POINT, 4), BRIGHT4 = (float) Math.pow(BRIGHT, 4);

    private BlackBody() {
    }

    /** How bright a body at {@code kelvin} glows, 0 below the Draper point to 1 at {@link #BRIGHT} and over. */
    public static float brightness(float kelvin) {
        if (kelvin <= DRAPER_POINT) return 0f;
        float t2 = kelvin * kelvin;
        return MathUtils.clamp((t2 * t2 - DRAPER4) / (BRIGHT4 - DRAPER4), 0f, 1f);
    }

    /**
     * The color of a black body at {@code kelvin} into {@code out}, alpha 1: Tanner Helland's fit
     * to the Planckian locus, held at its 1000 K red below that.
     */
    public static Color color(float kelvin, Color out) {
        float t = Math.max(1000f, Math.min(40000f, kelvin)) / 100f;
        float r = t <= 66f ? 255f : 329.698727446f * (float) Math.pow(t - 60f, -0.1332047592);
        float g = t <= 66f ? 99.4708025861f * (float) Math.log(t) - 161.1195681661f
                : 288.1221695283f * (float) Math.pow(t - 60f, -0.0755148492);
        float b = t >= 66f ? 255f : t <= 19f ? 0f : 138.5177312231f * (float) Math.log(t - 10f) - 305.0447927307f;
        return out.set(MathUtils.clamp(r, 0f, 255f) / 255f, MathUtils.clamp(g, 0f, 255f) / 255f,
                MathUtils.clamp(b, 0f, 255f) / 255f, 1f);
    }
}
