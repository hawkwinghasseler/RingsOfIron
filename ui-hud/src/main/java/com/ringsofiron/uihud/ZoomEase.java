package com.ringsofiron.uihud;

import com.badlogic.gdx.math.MathUtils;

/**
 * A zoom eased from one view to another over {@link #SECONDS}, easing in and out, each step the
 * same ratio of the view rather than the same meters so a big change of zoom looks even.
 * {@link ViewportManager#easeViewMeters} drives one.
 */
public final class ZoomEase {
    /** How long an eased zoom takes, seconds: quick enough to feel immediate, slow enough to follow. */
    public static final float SECONDS = 0.3f;

    /** From and to, log meters, and how far along in time, 0 to 1. */
    private float from, to, t = 1f;

    /** Starts easing from {@code fromMeters} to {@code toMeters}. */
    public void start(float fromMeters, float toMeters) {
        from = (float) Math.log(fromMeters);
        to = (float) Math.log(toMeters);
        t = 0f;
    }

    /** Ends it where it is. */
    public void stop() {
        t = 1f;
    }

    public boolean isEasing() {
        return t < 1f;
    }

    /** Moves it on by {@code delta} seconds. */
    public void update(float delta) {
        if (t < 1f) t = Math.min(1f, t + delta / SECONDS);
    }

    /** How far along it is, eased in and out: 0 at its start, 1 once settled. */
    public float progress() {
        return t * t * (3f - 2f * t);
    }

    /** The view this far along, meters. */
    public float meters() {
        return (float) Math.exp(MathUtils.lerp(from, to, progress()));
    }

    /** Where it is heading, meters. */
    public float target() {
        return (float) Math.exp(to);
    }

    /** Multiplies both ends by {@code factor} (the mouse wheel mid-ease), the end held within {@code min} and {@code max}. */
    public void scale(float factor, float min, float max) {
        float by = (float) Math.log(factor);
        from += by;
        to = MathUtils.clamp(to + by, (float) Math.log(min), (float) Math.log(max));
    }
}
