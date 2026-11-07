package com.ringsofiron.uihud.variants.nasapunk.screens;

/**
 * The one-line feed at a screen's foot: what the last order came to, under the source that said it
 * (STATION, YARD, CREW), for five seconds.
 */
public final class Feed {
    /** How long a line shows, s. */
    public static final float SHOW = 5f;

    public String source = "", text = "";
    private float age = Float.MAX_VALUE;

    /** Shows {@code text} under {@code source}; nothing for no text. */
    public void say(String source, String text) {
        if (text == null || text.trim().isEmpty()) return;
        this.source = source == null ? "" : source;
        this.text = text.trim();
        age = 0f;
    }

    public void act(float delta) {
        if (age < Float.MAX_VALUE) age += delta;
    }

    public boolean showing() {
        return age < SHOW && !text.isEmpty();
    }

    public void clear() {
        age = Float.MAX_VALUE;
        text = "";
        source = "";
    }
}
