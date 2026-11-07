package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The few pieces of Yard Standard hardware the stations mockups use beyond the drawing kit: the pod a band
 * unit stands in, its enamel plate, the overhead units' chassis bar, small fixed-legend lenses, a round
 * gauge, the condition's drum flag, engraved rules, reversed tube text, a quarter-turn fastener.
 */
final class Hardware {
    private Hardware() {}

    static final Color LIP = Yard.hex("6B5D50"), BAR_EDGE = Yard.hex("76685B"), ROD = Yard.hex("4A443F");
    static final Color DIM_INK = Yard.hex("8F8578"), TILE_BG = Yard.hex("0C0A09"), TILE_DARK = Yard.hex("2A2420");
    static final Color LEGEND_ON = Yard.hex("241406"), ENGRAVE_LO = Yard.hex("9F957C"), ENGRAVE_HI = Yard.hex("F4EEDD");
    static final Color SCALE = Yard.hex("F2ECDC"), KNOB = Yard.hex("8A857D");
    private static final Color GLOW_GREEN = Yard.hex("4F8A43");

    /** A pod: the chassis housing a band unit stands in, its lip, and the orange stripe along its top. */
    static void pod(YardPaint p, float x, float y, float w, float h, boolean stripe) {
        p.rrect(x + 1.5f, y + 3f, w, h, 5f, Yard.SHADOW, 0.7f);
        p.shade(x, y, w, h, 5f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.rrect(x, y, w, 5f, 2f, LIP);
        p.rect(x + 3f, y + 0.5f, w - 6f, 1f, Color.WHITE, 0.12f);
        if (stripe) p.rect(x, y + 5f, w, 2.5f, Yard.ORANGE);
    }

    /** An enamel plate, screwed down at its corners when {@code screws}. */
    static void plate(YardPaint p, float x, float y, float w, float h, boolean screws) {
        p.rrect(x + 1f, y + 2f, w, h, 3f, Yard.SHADOW, 0.5f);
        p.face(x, y, w, h, Yard.Finish.YARD, screws);
    }

    /** An overhead unit's chassis bar, clamped to the rack: a lit top edge and a shadow under it. */
    static void bar(YardPaint p, float x, float y, float w, float h) {
        p.rrect(x + 1f, y + 3f, w, h, 3f, Yard.SHADOW, 0.6f);
        p.shade(x, y, w, h, 3f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.rect(x + 2f, y, w - 4f, 1.2f, BAR_EDGE);
    }

    /** A small lens with a fixed legend, lit in {@code lamp} or dark (its legend then faint). */
    static void lens(YardPaint p, float x, float y, float w, float h, String text, Yard.Lamp lamp, boolean lit, boolean glow) {
        p.rrect(x - 2f, y - 2f, w + 4f, h + 4f, 2f, Yard.ALU);
        if (lit && glow) p.glowRect(x, y, w, h, 5f, lamp.on, 0.55f);
        p.rrect(x, y, w, h, 1.2f, lit ? lamp.on : lamp.off);
        p.glass(x, y, w, h);
        p.textC(Face.LEGEND, 12f, text, x + w / 2f, y + h / 2f + 4.2f, lit ? LEGEND_ON : Yard.LEGEND_FAINT);
    }

    /** An engraved legend, 12 units, ending at {@code right}. */
    static float legendR(YardPaint p, String s, float right, float baseline, Color ink) {
        return p.textR(Face.LEGEND, 12f, s, right, baseline, ink);
    }

    static float legendC(YardPaint p, String s, float cx, float baseline, Color ink) {
        return p.textC(Face.LEGEND, 12f, s, cx, baseline, ink);
    }

    /** A rule engraved in the enamel: a dark cut with a lit edge beside it. */
    static void engrave(YardPaint p, float x1, float y1, float x2, float y2) {
        boolean flat = Math.abs(y1 - y2) < 0.01f;
        p.line(x1, y1, x2, y2, 1f, ENGRAVE_LO);
        p.line(x1 + (flat ? 0f : 1f), y1 + (flat ? 1f : 0f), x2 + (flat ? 0f : 1f), y2 + (flat ? 1f : 0f), 0.8f, ENGRAVE_HI);
    }

    /** Reversed tube text: dark on a lit block {@code w} wide (or the text's width with a margin). */
    static float rev(YardPaint p, String s, float x, float baseline, float size, float w, Color c) {
        float tw = w > 0f ? w : p.width(Face.TUBE_BOLD, size, s) + 6f;
        p.glowRect(x - 3f, baseline - size + 1.5f, tw, size + 3f, 3f, c, 0.25f);
        p.rect(x - 3f, baseline - size + 1.5f, tw, size + 3f, c);
        p.text(Face.TUBE_BOLD, size, s, x, baseline, Yard.CRT_BLACK);
        return tw;
    }

    /**
     * A tube line that leads with a name, in at most {@code budget} characters: the name gives up its
     * letters first (to six at the least), so the figures after it still fit.
     */
    static String squeeze(String name, int budget, String rest) {
        int room = Math.max(6, budget - rest.length());
        String n = YardText.fit(name, room);
        // A cut that ends on a space reads as two words: "PIRATE…", not "PIRATE …"
        if (n.endsWith(" …")) n = n.substring(0, n.length() - 2).stripTrailing() + "…";
        return n + rest;
    }

    /**
     * {@code s} broken at spaces into rows of at most {@code width} characters, at most {@code rows} of
     * them; what does not fit ends the last row with an ellipsis. A word longer than a row is cut.
     */
    static java.util.List<String> wrap(String s, int width, int rows) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String rest = s.trim();
        while (!rest.isEmpty() && out.size() < rows) {
            if (rest.length() <= width) {
                out.add(rest);
                rest = "";
                break;
            }
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            out.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty() && !out.isEmpty()) {
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, (last.length() >= width ? last.substring(0, width - 1) : last) + "…");
        }
        return out;
    }

    /**
     * The characters of the mono tube face at {@code size} that fit in {@code w}, by the font's own advance:
     * a run's width is its advances less the last glyph's spare, so nine advances are measured, not ten.
     */
    static int chars(YardPaint p, float w, float size) {
        float advance = (p.width(Face.TUBE, size, "0000000000") - p.width(Face.TUBE, size, "0")) / 9f;
        return advance <= 0f ? 0 : (int) Math.floor(w / advance);
    }

    /** A line of amber tube text cut to {@code maxW}, never wrapped. */
    static float tubeFit(YardPaint p, String s, float x, float baseline, float size, Color c, float maxW) {
        if (s == null || s.isEmpty()) return 0f;
        String t = s;
        while (t.length() > 1 && p.width(Face.TUBE, size, t) > maxW) t = t.substring(0, t.length() - 2) + "…";
        return p.tube(t, x, baseline, size, c, false);
    }

    /**
     * A round gauge: a cream dial with a 240 degree scale, coloured zones round its rim and an orange
     * needle at {@code value}. Zones are pairs of values with a colour each.
     */
    static void gauge(YardPaint p, float cx, float cy, float r, float min, float max, float value, float[][] zones, Color[] colors) {
        p.circle(cx, cy, r + 5f, Yard.BAKELITE);
        p.circle(cx, cy, r, SCALE);
        if (zones != null) {
            for (int i = 0; i < zones.length; i++) {
                float a0 = angle(zones[i][0], min, max), a1 = angle(zones[i][1], min, max);
                int n = Math.max(2, (int) ((a1 - a0) / 6f));
                for (int k = 0; k < n; k++) {
                    float u = a0 + (a1 - a0) * k / n, v = a0 + (a1 - a0) * (k + 1) / n;
                    p.line(cx + (r - 4f) * MathUtils.cosDeg(u), cy + (r - 4f) * MathUtils.sinDeg(u),
                            cx + (r - 4f) * MathUtils.cosDeg(v), cy + (r - 4f) * MathUtils.sinDeg(v), 5f, colors[i]);
                }
            }
        }
        for (int i = 0; i <= 10; i++) {
            float a = 150f + i * 24f, in = i % 5 == 0 ? 8f : 4f;
            p.line(cx + r * MathUtils.cosDeg(a), cy + r * MathUtils.sinDeg(a), cx + (r - in) * MathUtils.cosDeg(a), cy + (r - in) * MathUtils.sinDeg(a),
                    i % 5 == 0 ? 1.5f : 0.8f, Yard.INK);
        }
        float a = angle(value, min, max);
        p.line(cx + 7f * MathUtils.cosDeg(a + 180f) + 0.6f, cy + 7f * MathUtils.sinDeg(a + 180f) + 1f,
                cx + (r - 4f) * MathUtils.cosDeg(a) + 0.6f, cy + (r - 4f) * MathUtils.sinDeg(a) + 1f, 2f, Yard.SHADOW);
        p.line(cx + 7f * MathUtils.cosDeg(a + 180f), cy + 7f * MathUtils.sinDeg(a + 180f), cx + (r - 4f) * MathUtils.cosDeg(a), cy + (r - 4f) * MathUtils.sinDeg(a), 2f, Yard.ORANGE);
        p.circle(cx, cy, 4f, KNOB);
        p.circle(cx - 1f, cy - 1.2f, 1.6f, Color.WHITE, 0.3f);
        p.glass(cx - r, cy - r, 2f * r, 2f * r);
    }

    /** The closing gauge's zones: green to the safe speed, mustard to half again, red past it. */
    static final Color[] CLOSING_COLORS = {GLOW_GREEN, Yard.MUSTARD, Yard.RED};

    private static float angle(float v, float min, float max) {
        return 150f + (MathUtils.clamp(v, min, max) - min) / (max - min) * 240f;
    }

    /** The condition on a drum flag: YELLOW or RED in its colour. */
    static void condFlag(YardPaint p, float x, float y, float w, float h, StationsFrame.Condition c) {
        p.rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2.5f, Yard.BAKELITE);
        if (c == StationsFrame.Condition.GREEN) {
            p.vgrad(x, y, w, h / 2f, Yard.hex("050505"), Yard.hex("34302D"));
            p.vgrad(x, y + h / 2f, w, h / 2f, Yard.hex("34302D"), Yard.hex("050505"));
            return;
        }
        boolean red = c == StationsFrame.Condition.RED;
        p.rect(x, y, w, h, red ? Yard.RED : Yard.MUSTARD);
        p.vgrad(x, y, w, h / 2f, new Color(0f, 0f, 0f, 0.25f), new Color(1f, 1f, 1f, 0.06f));
        p.vgrad(x, y + h / 2f, w, h / 2f, new Color(1f, 1f, 1f, 0.06f), new Color(0f, 0f, 0f, 0.25f));
        p.textC(Face.LEGEND, 13f, c.name(), x + w / 2f, y + h / 2f + 4.6f, red ? Yard.hex("FBE9DC") : Yard.hex("2A1C06"));
        p.glass(x, y, w, h);
    }

    /** A quarter-turn fastener on a blanking plate, its slot turned to {@code deg}. */
    static void dzus(YardPaint p, float cx, float cy, float deg) {
        p.circle(cx, cy, 4.4f, Yard.hex("8F8571"));
        p.circle(cx, cy, 3.6f, Yard.hex("BDB29A"));
        p.circle(cx, cy, 3.2f, Yard.SCREW);
        float c = MathUtils.cosDeg(deg) * 2.4f, s = MathUtils.sinDeg(deg) * 2.4f;
        p.line(cx - c, cy - s, cx + c, cy + s, 1.3f, Yard.SCREW_SLOT);
    }

    /** A blanking plate where a hull has no such system: no legend, four fasteners. */
    static void blankBay(YardPaint p, float x, float y, float w, float h) {
        p.rrect(x, y, w, h, 2f, Yard.hex("8F8571"));
        p.rrect(x + 0.8f, y + 0.8f, w - 1.6f, h - 1.6f, 1.6f, Yard.hex("BDB29A"));
        p.rect(x + 2f, y + 1.2f, w - 4f, 1f, Yard.hex("E7DFCA"), 0.8f);
        dzus(p, x + 7f, y + 7f, 20f);
        dzus(p, x + w - 7f, y + 7f, 75f);
        dzus(p, x + 7f, y + h - 7f, 130f);
        dzus(p, x + w - 7f, y + h - 7f, 160f);
    }

    /** The severity mark drawn before a line or a tag: the warning diamond, the caution triangle. */
    static void mark(YardPaint p, boolean warning, float cx, float cy, float r, Color c) {
        if (warning) p.diamond(cx, cy, r, c);
        else p.triangle(cx, cy, r, c);
    }

    /** A key cap's name for {@code action}, or {@code fallback} when the game has none. */
    static String key(com.ringsofiron.uihud.data.HudData d, String action, String fallback) {
        String k = d.key(action);
        return k == null || k.isEmpty() ? fallback : k;
    }
}
