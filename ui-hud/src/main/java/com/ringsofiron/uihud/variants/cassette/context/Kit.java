package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pieces of Yard Standard hardware the canopy needs beyond the drawing kit: how a unit hangs, slides
 * or stands (rods from the header rail, the slide rails, the coaming), its plate, small fixed-legend
 * lenses, engraved rules, a round gauge, the thruster group, the master cue, blanking plates, and the
 * text budgets every tube line is cut to (spec 5.6).
 */
final class Kit {
    private Kit() {}

    static final Color LIP = Yard.hex("6B5D50"), BAR_EDGE = Yard.hex("76685B"), ROD = Yard.hex("4A443F");
    static final Color ROD_HI = Yard.hex("8D857A"), RAIL_DARK = Yard.hex("231E1A");
    static final Color DIM_INK = Yard.hex("8F8578"), ENGRAVE_LO = Yard.hex("9F957C"), ENGRAVE_HI = Yard.hex("F4EEDD");
    static final Color LEGEND_ON = Yard.hex("241406"), SCALE = Yard.hex("F2ECDC"), KNOB = Yard.hex("8A857D");
    /** The tube's own row names, in dim phosphor (the nav tube's ORBIT, NAV, NEXT, PLAN). */
    static final Color ROW_NAME = Yard.hex("6E5124");
    static final Color GLOW_GREEN = Yard.hex("4F8A43");

    // --- How units are mounted -----------------------------------------------------------------

    /** Two rods a top unit hangs from, from the header rail's foot down to the unit's top. */
    static void rods(YardPaint p, float x, float w, float railFoot, float top) {
        if (top <= railFoot + 0.5f) return;
        for (float rx : new float[]{x + 26f, x + w - 26f}) {
            p.rect(rx - 1.5f, railFoot, 3f, top - railFoot, ROD);
            p.rect(rx - 1.5f, railFoot, 1f, top - railFoot, ROD_HI, 0.6f);
            p.rect(rx - 4f, top - 3f, 8f, 3f, ROD);
        }
    }

    /** The slide rail a side unit rides on, at the screen's edge: a dark channel with its carriage. */
    static void slideRail(YardPaint p, float edgeX, boolean left, float y, float h) {
        float rx = left ? edgeX : edgeX - 7f;
        p.rect(rx, y - 6f, 7f, h + 12f, RAIL_DARK);
        p.rect(left ? rx + 5f : rx, y - 6f, 2f, h + 12f, ROD_HI, 0.35f);
        // The carriage clamps the unit to the rail
        p.rect(left ? rx : rx, y + 6f, 7f, 10f, ROD);
        p.rect(left ? rx : rx, y + h - 16f, 7f, 10f, ROD);
    }

    /** A chassis housing open to the view on every side: a side or top unit's body, with its stripe on top. */
    static void housing(YardPaint p, float x, float y, float w, float h, boolean stripe) {
        p.rrect(x + 1.5f, y + 3f, w, h, 5f, Yard.SHADOW, 0.7f);
        p.shade(x, y, w, h, 5f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.rect(x + 3f, y + 0.5f, w - 6f, 1f, Color.WHITE, 0.12f);
        if (stripe) p.rect(x + 4f, y + 2f, w - 8f, 2.4f, Yard.ORANGE);
    }

    /** A pod standing on the coaming: the housing with its lip and the orange stripe along its top. */
    static void pod(YardPaint p, float x, float y, float w, float h) {
        p.rrect(x + 1.5f, y + 3f, w, h, 5f, Yard.SHADOW, 0.7f);
        p.shade(x, y, w, h, 5f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.rrect(x, y, w, 5f, 2f, LIP);
        p.rect(x + 3f, y + 0.5f, w - 6f, 1f, Color.WHITE, 0.12f);
        p.rect(x, y + 5f, w, 2.5f, Yard.ORANGE);
    }

    /** An enamel plate, screwed down at its corners when {@code screws}. */
    static void plate(YardPaint p, float x, float y, float w, float h, boolean screws) {
        p.rrect(x + 1f, y + 2f, w, h, 3f, Yard.SHADOW, 0.5f);
        p.face(x, y, w, h, Yard.Finish.YARD, screws);
    }

    // --- Lenses, rules and plates ---------------------------------------------------------------

    /** A small lens with a fixed legend, lit in {@code lamp} or dark (its legend then faint). */
    static void lens(YardPaint p, float x, float y, float w, float h, String text, Yard.Lamp lamp, boolean lit) {
        p.rrect(x - 2f, y - 2f, w + 4f, h + 4f, 2f, Yard.ALU);
        if (lit) p.glowRect(x, y, w, h, 5f, lamp.on, 0.55f);
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

    /**
     * Three printed windows (POOR FAIR GOOD; VACUUM THIN NORMAL), the one that applies lit: the word is
     * the colour-free channel.
     */
    static void windows(YardPaint p, float x, float y, float cellW, float h, String[] words, int lit, Color litColor) {
        p.rrect(x - 2f, y - 2f, cellW * words.length + 4f, h + 4f, 2f, Yard.BAKELITE);
        for (int i = 0; i < words.length; i++) {
            float cx = x + i * cellW;
            boolean on = i == lit;
            p.rect(cx + 0.5f, y, cellW - 1f, h, on ? litColor : Yard.hex("3A3632"));
            if (on) p.glowRect(cx, y, cellW, h, 4f, litColor, 0.35f);
            p.textC(Face.LEGEND, 12f, words[i], cx + cellW / 2f, y + h / 2f + 4.2f, on ? Yard.hex("231407") : Yard.hex("8A8277"));
        }
        p.glass(x, y, cellW * words.length, h);
    }

    // --- The thruster group ---------------------------------------------------------------------

    /**
     * The thrusters firing: notched darts round a ship drawn nose right, main and retro fore and aft,
     * strafe above and below, a turn as curved darts at the bow. Lamps are 14 units at {@code s} 1.
     */
    static void thrusters(YardPaint p, float cx, float cy, float s, boolean dark, float forward, float strafe, float turn, boolean main) {
        Color hull = dark ? Yard.hex("5D5850") : Yard.hex("6F695F");
        // The ship, nose right
        p.fillConvex(new float[]{cx - 15f * s, cy - 5f * s, cx + 8f * s, cy - 5f * s, cx + 15f * s, cy, cx + 8f * s, cy + 5f * s, cx - 15f * s, cy + 5f * s}, hull, 1f);
        boolean ahead = main || forward > 0.05f, astern = forward < -0.05f;
        boolean port = strafe > 0.05f, stbd = strafe < -0.05f, ccw = turn > 0.05f, cw = turn < -0.05f;
        // Main: a dart out of the stern pointing aft (the push is forward)
        dart(p, cx - 22f * s, cy, 180f, s, ahead);
        dart(p, cx + 22f * s, cy, 0f, s, astern);
        // Strafe: the dart on the side the ship moves away from
        dart(p, cx, cy - 13f * s, 90f, s, stbd);
        dart(p, cx, cy + 13f * s, 270f, s, port);
        // Turning: curved darts by the bow
        dart(p, cx + 17f * s, cy - 11f * s, 45f, s * 0.8f, cw);
        dart(p, cx + 17f * s, cy + 11f * s, 315f, s * 0.8f, ccw);
    }

    private static void dart(YardPaint p, float cx, float cy, float deg, float s, boolean lit) {
        float c = MathUtils.cosDeg(deg), sn = MathUtils.sinDeg(deg);
        // A dart 7 long pointing along deg, notched at its tail; y is down so the angle turns clockwise
        float tipX = cx + c * 7f * s, tipY = cy - sn * 7f * s;
        float bx = cx - c * 7f * s, by = cy + sn * 7f * s;
        float nx = -sn * 6f * s, ny = -c * 6f * s;
        Color col = lit ? Yard.WHITE_LAMP.on : Yard.hex("2C2824");
        if (lit) p.glow(cx, cy, 9f * s, Yard.WHITE_LAMP.on, 0.35f);
        float notchX = cx - c * 3f * s, notchY = cy + sn * 3f * s;
        p.fillConvex(new float[]{tipX, tipY, bx + nx, by + ny, notchX, notchY}, col, 1f);
        p.fillConvex(new float[]{tipX, tipY, notchX, notchY, bx - nx, by - ny}, col, 1f);
    }

    // --- The master cue -------------------------------------------------------------------------

    /**
     * The master cue: one MASTER lens with a red and an amber lamp behind it, the newest warning's code
     * (or CAUTION) on ten flaps, and the acknowledge key. {@code w} is its whole width.
     */
    static void cue(YardPaint p, float x, float y, float w, Conditions.Cue cue, String ackKey, float time) {
        boolean lit = cue.lit, warning = cue.warning;
        boolean on = lit && (!cue.flash || (time * 2f) % 1f < 0.6f);
        Yard.Lamp lamp = warning ? Yard.RED_LAMP : Yard.AMBER_LAMP;
        p.push(x + 3f, y + 3f, 54f, 18f, new String[]{"MASTER"}, lamp, on, YardPaint.Kind.INDICATOR, false, warning && on);
        float fw = w - 54f - 8f - 30f;
        int cells = Math.max(6, (int) (fw / 8f));
        String code = !lit ? "" : warning ? cue.code : "CAUTION";
        p.flaps(x + 64f, y + 2f, YardText.fit(code, cells), cells, fw / cells, 20f, 13f, Yard.FLAP_TEXT);
        p.key(x + w - 22f, y + 3f, ackKey);
    }

    // --- Text budgets (5.6) ---------------------------------------------------------------------

    /**
     * A tube line that leads with a name, in at most {@code budget} characters: the name gives up its
     * letters first (to six at the least), so the figures after it still fit.
     */
    static String squeeze(String name, int budget, String rest) {
        int room = Math.max(6, budget - rest.length());
        String n = YardText.fit(name, room);
        if (n.endsWith(" …")) n = n.substring(0, n.length() - 2).stripTrailing() + "…";
        return n + rest;
    }

    /**
     * {@code s} broken at spaces into rows of at most {@code width} characters, at most {@code rows} of
     * them; what does not fit ends the last row with an ellipsis. A word longer than a row is cut.
     */
    static List<String> wrap(String s, int width, int rows) {
        List<String> out = new ArrayList<>();
        String rest = s == null ? "" : s.trim();
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

    /** A line of tube text cut to {@code maxW}, never wrapped. */
    static float tubeFit(YardPaint p, String s, float x, float baseline, float size, Color c, float maxW) {
        if (s == null || s.isEmpty()) return 0f;
        String t = s;
        while (t.length() > 1 && p.width(Face.TUBE, size, t) > maxW) t = t.substring(0, t.length() - 2) + "…";
        return p.tube(t, x, baseline, size, c, false);
    }

    /**
     * The store's charge across every circuit, 0..1, walked by index: the frame's own {@code charge()}
     * walks the array with its shared iterator, which a caller inside another walk of it would break.
     */
    static float charge(com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame pf) {
        double s = 0, c = 0;
        for (int i = 0; i < pf.circuits.size; i++) {
            s += pf.circuits.get(i).stored;
            c += pf.circuits.get(i).capacity;
        }
        return c > 0 ? (float) Math.max(0, Math.min(1, s / c)) : 0f;
    }

    /** A key cap's name for {@code action}, or {@code fallback} when the game has none. */
    static String key(HudData d, String action, String fallback) {
        String k = d.key(action);
        return k == null || k.isEmpty() ? fallback : k;
    }

    // --- Number formats (5.4) -------------------------------------------------------------------

    /** A distance for a tube line: "850 m", "12.4 km", "1,240 km". */
    static String dist(double m) {
        if (Double.isNaN(m)) return "--";
        double a = Math.abs(m);
        if (a < 1000) return Math.round(a) + " m";
        if (a < 100_000) return String.format(Locale.ROOT, "%.1f km", a / 1000);
        return String.format(Locale.ROOT, "%,d km", Math.round(a / 1000));
    }

    /** A speed for a tube line: "4.2 m/s" under 10, "386 m/s" over. */
    static String speed(double v) {
        if (Double.isNaN(v)) return "--";
        double a = Math.abs(v);
        return a < 10 ? String.format(Locale.ROOT, "%.1f m/s", a) : String.format(Locale.ROOT, "%,d m/s", Math.round(a));
    }

    /** Closing or opening in words, never a bare sign: "closing 12 m/s". */
    static String rate(double closing) {
        if (Double.isNaN(closing)) return "";
        if (Math.abs(closing) < 0.05) return "holding";
        return (closing > 0 ? "closing " : "opening ") + speed(closing);
    }

    /** Time to go (5.4): "T-42 s", "T-12:05", "T-3 h 05 m", "T-2.4 d". */
    static String tMinus(double s) {
        if (Double.isNaN(s)) return "";
        double a = Math.max(0, s);
        if (a < 60) return "T-" + (long) Math.ceil(a) + " s";
        long t = (long) Math.ceil(a);
        if (a < 3600) return String.format(Locale.ROOT, "T-%d:%02d", t / 60, t % 60);
        if (a < 86400) return String.format(Locale.ROOT, "T-%d h %02d m", t / 3600, (t / 60) % 60);
        return String.format(Locale.ROOT, "T-%.1f d", a / 86400);
    }

    /** P2's timer field, five characters: "3:20", "12:05", "2 h". */
    static String timer(double s) {
        if (Double.isNaN(s)) return "";
        long t = (long) Math.ceil(Math.max(0, s));
        if (t < 3600) return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
        return (t / 3600) + " h";
    }
}
