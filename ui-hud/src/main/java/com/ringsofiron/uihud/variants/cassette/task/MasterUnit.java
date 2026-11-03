package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Lamp;

import java.util.Locale;

/**
 * Z1 and Z4, the master repeater at top left: the chronometer (CHR) on top, the annunciator (ANN) under
 * it. Day and time are on drums, warp on flaps; the next stop and its countdown, or why warp is held,
 * on the second line. Ten push-light tiles in two rows of five, one per thing that can go wrong aboard,
 * with a blanking plate where the ship has no such system. Over a screen only the chronometer stays.
 */
final class MasterUnit extends YardPart {
    static final float W = 300f, H = 122f, H_CLOCK = 64f;
    private static final Color INK = Yard.Finish.YARD.ink, DIM = Yard.hex("A1967F");

    private final TaskFrame f;
    /** The lamps' half is up (not over a screen). */
    boolean annOn = true;
    /** The second line's next stop is shown: not over the map, whose plan owns the times. */
    boolean line2 = true;
    /** FLT is in BURN mode and owns the burn's countdown, so the chronometer leaves it. */
    boolean fltOwnsBurn;

    MasterUnit(TaskFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    float height() {
        return annOn ? H : H_CLOCK;
    }

    @Override
    protected void draw(YardPaint p) {
        UiContext ctx = f.data.ctx;
        p.unit(0f, 0f, W, height(), Yard.Finish.YARD, "tl");
        long s = (long) Math.max(0.0, ctx.simSeconds);
        p.legend("DAY", 12f, 24.5f, INK);
        p.drums(42f, 9f, String.format(Locale.ROOT, "%03d", Math.min(999L, s / 86400)), 12f, 18f, 15f, true);
        p.drums(90f, 9f, String.format(Locale.ROOT, "%02d:%02d:%02d", (s / 3600) % 24, (s / 60) % 60, s % 60), 12f, 18f, 15f, false);
        p.legend("WARP", 188f, 24.5f, INK);
        p.flaps(224f, 10f, warp(ctx), 6, 9.4f, 16f, 13.5f, Yard.FLAP_TEXT);

        // The second line: why warp is held (it replaces the next stop), or the next stop and its countdown
        if (!ctx.warpHold.isEmpty()) {
            p.lamp(16f, 42f, 4f, Yard.AMBER_LAMP, true);
            p.legend("HELD", 25f, 46.5f, INK);
            p.flaps(60f, 34f, holdWords(ctx.warpHold), 13, 8.4f, 16f, 13f, Yard.FLAP_TEXT);
        } else {
            String next = nextStop(ctx, f.data.navReadout);
            boolean on = line2 && next != null;
            p.legend("NEXT", 12f, 46.5f, line2 ? INK : DIM);
            p.flaps(46f, 34f, on ? next : "", 12, 8.4f, 16f, 13f, Yard.FLAP_TEXT);
            double t = ctx.warpNextStop;
            if (on && t >= 86400) {
                long n = (long) t;
                p.drums(154f, 33f, String.valueOf(Math.min(9L, n / 86400)), 11f, 18f, 15f, false);
                p.legend("D", 168f, 46.5f, INK);
                p.drums(178f, 33f, String.format(Locale.ROOT, "%02d:%02d", (n / 3600) % 24, (n / 60) % 60), 11f, 18f, 15f, false);
            } else {
                p.drums(154f, 33f, on ? countdown(t) : " :  :  ", 11f, 18f, 15f, false);
            }
        }
        p.lamp(240f, 42f, 4f, Yard.WHITE_LAMP, f.rails);
        p.legend("RAILS", 249f, 46.5f, INK);

        if (!annOn) return;
        p.rect(10f, 58f, 280f, 1f, Yard.hex("9F957C"));
        p.rect(10f, 59f, 280f, 0.8f, Yard.ENAMEL_HI);
        TaskAlerts a = f.alerts;
        for (Lamp l : Lamp.values()) {
            int i = l.ordinal();
            float x = tileX(i), y = tileY(i);
            if (l == Lamp.SHIELD && !a.shieldFitted) {
                p.blank(x, y, 52f, 22f);
                continue;
            }
            Level lv = a.level[i];
            if (lv == Level.ADVISORY) {
                p.softLens(x, y, 52f, 22f, l.name());
                continue;
            }
            boolean warning = lv == Level.WARNING, lit = lv != null;
            p.severityLens(x, y, 52f, 22f, l.name(), warning ? Yard.RED_LAMP : lit ? Yard.AMBER_LAMP : Yard.WHITE_LAMP, lit, warning, a.fresh[i]);
        }
    }

    static float tileX(int i) {
        return 12f + (i % 5) * 56f;
    }

    static float tileY(int i) {
        return 65f + (i / 5) * 26f;
    }

    /**
     * Why warp is held, in the few words thirteen flaps hold; the whole reason is in the hover. The game's
     * reasons are sentences ("Atmosphere venting: the crew are patching the leak"), so the known ones get a
     * short name, and any other keeps its first clause, cut at a word.
     */
    static String holdWords(String reason) {
        String r = reason.toLowerCase(Locale.ROOT);
        if (r.contains("fire")) return "FIRE ABOARD";
        if (r.contains("venting") || r.contains("leak")) return "AIR VENTING";
        if (r.contains("flying a burn")) return "FLYING BURN";
        if (r.contains("burn")) return "BURN DUE";
        if (r.contains("outside") || r.contains("adrift") || r.contains("eva")) return "CREW OUTSIDE";
        if (r.contains("aboard ") || r.contains("across")) return "CREW ACROSS";
        if (r.contains("loading")) return "LOADING";
        if (r.contains("dock")) return "DOCKING";
        String first = reason.split("[:;,.]")[0].trim().toUpperCase(Locale.ROOT);
        if (first.length() <= 13) return first;
        int cut = first.lastIndexOf(' ', 13);
        return cut > 3 ? first.substring(0, cut) : YardText.fit(first, 13);
    }

    /** The rate on six flaps: "1X", "10X", "10000X". */
    static String warp(UiContext ctx) {
        return YardText.fit(ctx.warp + "X", 6);
    }

    /**
     * What warp next drops out for, as far as the data names it: the next burn when the countdown is
     * the burn's, else a drop-out by itself. Null when nothing is known, or while FLT has the burn.
     */
    private String nextStop(UiContext ctx, NavReadout nav) {
        if (Float.isNaN(ctx.warpNextStop)) return null;
        boolean burn = nav.burnsLeft > 0 && !Float.isNaN(nav.burnIn) && ctx.warpNextStop <= nav.burnIn + 1f;
        if (burn) return fltOwnsBurn ? null : "BURN " + nav.burnNumber;
        return "DROP OUT";
    }

    private static String countdown(double t) {
        long n = (long) Math.ceil(Math.max(0.0, t));
        return String.format(Locale.ROOT, "%d:%02d:%02d", Math.min(9L, n / 3600), (n / 60) % 60, n % 60);
    }

    @Override
    public String hover(float px, float py) {
        float lx = localX(px), ly = localY(py);
        String hold = f.data.ctx.warpHold;
        if (!hold.isEmpty() && ly >= 30f && ly < 56f && lx < 232f) return "Warp held: " + hold;
        if (!annOn) return null;
        for (Lamp l : Lamp.values()) {
            int i = l.ordinal();
            float x = tileX(i), y = tileY(i);
            if (lx >= x - 3f && lx <= x + 55f && ly >= y - 3f && ly <= y + 25f) {
                if (l == Lamp.SHIELD && !f.alerts.shieldFitted) return "SHIELD: no emitter fitted";
                String why = f.alerts.why[i];
                return why == null ? l.name() + ": well" : l.name() + ": " + why;
            }
        }
        return null;
    }
}
