package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * What the menus stand in front of (documentation/ui-overhaul/cassette/shell): a yard office at
 * night, the drifting stars and the planet's limb in blue rings seen through its big window, the desk
 * console under it; and over a paused game, the game dimmed inside the ship's console. Drawn to the
 * mockups' 1280 by 800, stretched to the screen.
 */
final class YardOffice {
    private static final Color SKY_TOP = Yard.hex("04060A"), SKY_FOOT = Yard.hex("0A1220");
    private static final Color LIMB = Yard.hex("86B6DC"), BODY = Yard.hex("0A131B");
    private static final Color FRAME = Yard.hex("342B25"), FRAME_EDGE = Yard.hex("4A3F36"), FRAME_DEEP = Yard.hex("231D19");
    private static final Color SILL = Yard.hex("4A3F36"), SILL_TOP = Yard.hex("6B5D50");
    private static final Color WALL_TOP = Yard.hex("2E2722"), WALL_FOOT = Yard.hex("1A1613");
    private static final Color CONSOLE = Yard.hex("2B241F"), CONSOLE_LINE = Yard.hex("6E3216");

    /** Each star: across (0 to 1), down (0 to 1), brightness. */
    private final float[] stars = new float[460 * 3];
    private final Color star = new Color();

    YardOffice() {
        java.util.Random r = new java.util.Random(23);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = r.nextFloat();
            stars[i + 1] = r.nextFloat();
            float b = r.nextFloat();
            stars[i + 2] = 0.25f + 0.75f * b * b;
        }
    }

    /** The office at night over the whole screen, {@code w} by {@code h}; {@code time} drifts the stars. */
    void night(YardPaint p, float w, float h, float time) {
        float sx = w / 1280f, sy = h / 800f;
        float top = 22f * sy, sill = 596f * sy;
        p.vgrad(0f, 0f, w, sill, SKY_TOP, SKY_FOOT);
        float drift = time * 0.0035f;
        for (int i = 0; i < stars.length; i += 3) {
            float b = stars[i + 2];
            float x = ((stars[i] + drift * b) % 1f) * w, y = top + stars[i + 1] * (sill - top);
            star.set(0.7f + 0.3f * b, 0.8f + 0.2f * b, 1f, 1f);
            float s = b < 0.8f ? 1f : 1.8f;
            p.rect(x, y, s, s, star, b);
        }
        // The planet below the window's foot: dark, its lit limb in rings of falling brightness
        float r = 1500f * sy, cx = w / 2f, cy = 1900f * sy;
        p.circle(cx, cy, r, BODY);
        limb(p, cx, cy, r + 24f * sy, 1.2f, 0.12f, sill, w);
        limb(p, cx, cy, r + 11f * sy, 1.6f, 0.26f, sill, w);
        limb(p, cx, cy, r, 8f, 0.06f, sill, w);
        limb(p, cx, cy, r, 2.4f, 0.55f, sill, w);
        // Faint reflections on the glass
        p.fillConvex(new float[]{262f * sx, top, 330f * sx, top, 214f * sx, sill, 146f * sx, sill}, Color.WHITE, 0.022f);
        p.fillConvex(new float[]{1150f * sx, top, 1182f * sx, top, 1104f * sx, sill, 1072f * sx, sill}, Color.WHITE, 0.02f);
        // The window's painted steel: its frame and two mullions, screwed
        p.rect(0f, 0f, w, top, FRAME);
        p.rect(0f, 0f, 22f * sx, sill, FRAME);
        p.rect(w - 22f * sx, 0f, 22f * sx, sill, FRAME);
        float[] mullions = {196f * sx, 1060f * sx};
        for (int i = 0; i < mullions.length; i++) {
            float mx = mullions[i], mw = 24f * sx;
            p.rect(mx, 0f, mw, sill, FRAME);
            p.rect(mx, top, 1f, sill - top, FRAME_EDGE);
            p.rect(mx + mw - 1f, top, 1f, sill - top, FRAME_DEEP);
            for (float yy = 60f * sy; yy < sill - 10f; yy += 90f * sy) p.screw(mx + mw / 2f, yy);
        }
        p.rect(22f * sx, top - 1f, w - 44f * sx, 1f, FRAME_DEEP);
        p.rect(0f, sill, w, 16f * sy, SILL);
        p.rect(0f, sill, w, 3f * sy, SILL_TOP);
        p.vgrad(0f, sill + 16f * sy, w, h - sill - 16f * sy, WALL_TOP, WALL_FOOT);
    }

    /** A ring of the limb, {@code width} wide, only where it shows above the window's foot. */
    private static void limb(YardPaint p, float cx, float cy, float r, float width, float a, float sill, float w) {
        int n = 360;
        float span = 70f * MathUtils.degreesToRadians;
        float px = 0f, py = 0f;
        boolean have = false;
        for (int i = 0; i <= n; i++) {
            double ang = -Math.PI / 2 - span / 2 + span * i / n;
            float x = cx + (float) (Math.cos(ang) * r), y = cy + (float) (Math.sin(ang) * r);
            boolean shows = y < sill && x > -20f && x < w + 20f;
            if (have && shows) p.line(px, py, x, y, width, LIMB, a);
            px = x;
            py = y;
            have = shows;
        }
    }

    /** Over the paused game: the game dimmed, the ship's console framing it above and below. */
    void paused(YardPaint p, float w, float h) {
        p.rect(0f, 0f, w, h, Color.BLACK, 0.58f);
        p.rect(0f, 0f, w, 22f, CONSOLE);
        p.rect(0f, 21f, w, 2f, CONSOLE_LINE);
        p.rect(0f, h - 12f, w, 12f, CONSOLE);
        p.rect(0f, h - 12f, w, 1.5f, CONSOLE_LINE, 0.8f);
    }
}
