package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Lamp;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Lit;

import java.util.Locale;

/**
 * The cluster, top left (functional-task 6, CHR and ANN): one plate, the chronometer's module over the
 * block of tell-tales. CHR gives the day and time in seven segments, the warp rate with RAILS, and on
 * its second line the next stop or why warp is held, each named by a fixed legend. ANN is ten
 * tell-tales in two rows, each an icon over its word: dark, advisory (lit softly), caution (an amber
 * frame) or warning (a red block with the icon and word cut out of it); a ship with no shield emitter
 * has blank glass where SHIELD would be.
 */
final class Cluster {
    /** The tell-tales' icons, in {@link Lamp} order. */
    static final String[] ICONS = {"breach", "fire", "o2", "power", "prop", "drive", "missile", "crew", "eva", "shield"};
    /** A blinking tell-tale is lit this long and dark as long: twice a second, under the 3 a second limit. */
    static final float BLINK = 0.25f;

    String day = "", time = "", warp = "1", next = "", held = "";
    boolean rails;

    /** Reads the clock and warp; {@code burnShown}: FLT shows the burn's countdown, so the clock leaves it. */
    void update(HudData d, boolean burnShown) {
        UiContext ctx = d.ctx;
        day = Integer.toString(Fmt.day(ctx.simSeconds));
        time = Fmt.clock(ctx.simSeconds);
        warp = Integer.toString(Math.max(1, ctx.warp));
        rails = ctx.railsWarp && ctx.warp > 1;
        held = ctx.warpHold.isEmpty() ? "" : ctx.warpHold.toUpperCase(Locale.ROOT);
        next = held.isEmpty() ? nextStop(d, burnShown) : "";
    }

    /**
     * The next stop with its countdown: the burn it drops out for, the autopilot's arrival, or the
     * stop the game knows only by its time. "" with none, or when it is the burn FLT is counting.
     */
    static String nextStop(HudData d, boolean burnShown) {
        float t = d.ctx.warpNextStop;
        if (Float.isNaN(t) || d.ctx.ship == null) return "";
        NavReadout n = d.navReadout;
        // The stop for a burn comes a few minutes before it starts (the turn, and a minute's lead)
        boolean burn = n.burnsLeft > 0 && !Float.isNaN(n.burnStartIn) && t <= n.burnStartIn && n.burnStartIn - t < 300f;
        if (burn && burnShown) return "";
        String what = burn ? "BURN " + Math.max(1, n.burnNumber)
                : d.orderState.autopilot == Autopilot.ON && !d.orderState.autopilotTo.isEmpty() ? Fmt.name(d.orderState.autopilotTo, 14)
                : "STOP";
        return what + " IN " + Fmt.mmss(t);
    }

    // ------------------------------------------------------------------ painting

    /** The plate and CHR, from the stage's top left; {@code withAnn}: the plate holds ANN too (not over a screen). */
    void paintChr(Filament f, boolean withAnn) {
        float x = Geo.LX, y = Geo.CHR_Y, w = Geo.CW, h = Geo.CHR_H, b1 = y + 22f, b2 = y + 44f;
        f.plate(x, y, w, withAnn ? Geo.ANN_Y + Geo.ANN_H - y : h, 6f, 8f);
        f.module(x, y, w, h, f.o().filaments(2));
        float cx = x + 8f;
        cx += f.legend(cx, b1, "DAY", f.o().dim()) + 5f;
        cx += f.seg7(cx, b1 - 15f, 15f, day, f.o().cells(3).right().norm()) + 10f;
        cx += f.seg7(cx, b1 - 15f, 15f, time, f.o().cells(6).colons(2).norm());
        // Warp: dim at 1x, the loudest thing on the module while warping
        boolean one = "1".equals(warp);
        float rx = x + w - 8f - f.legendWidth("RAILS", f.o());
        f.legend(rx, b1, "RAILS", f.o().lit(rails).full());
        rx -= 6f + f.legendWidth("X", f.o());
        f.legend(rx, b1, "X", f.o().level(one ? Drive.DIM : Drive.FULL));
        rx -= 3f + f.seg7Width(12f, 5, f.o());
        f.seg7(rx, b1 - 12f, 12f, warp, f.o().cells(5).right().level(one ? Drive.DIM : Drive.FULL));
        Kit.fits(cx + 8f, rx, "CHR first line");
        // Two fixed legends say what the words after them are: the next stop, or why warp is held
        float nx = x + 8f;
        nx += f.legend(nx, b2, "NEXT", f.o().lit(!next.isEmpty()).dim()) + 6f;
        nx += f.legend(nx, b2, "HELD", f.o().lit(!held.isEmpty()).full()) + 8f;
        f.dm(nx, b2 - 10.5f, held.isEmpty() ? next : held, f.o().pitch(Geo.P).cells(Geo.fit(nx, x + w - 8f))
                .level(held.isEmpty() ? Drive.NORM : Drive.FULL));
    }

    /** ANN's block of tell-tales under CHR; {@code time} runs the blink. */
    static void paintAnn(Filament f, Catalogue c, float time) {
        float x = Geo.LX, y = Geo.ANN_Y, w = Geo.CW, h = Geo.ANN_H, gap = 4f;
        f.module(x, y, w, h, f.o().filaments(0));
        float cw = (w - 8f - gap * 4f) / 5f, ch = (h - 8f - gap) / 2f;
        boolean on = (time % (2f * BLINK)) < BLINK;
        for (Lamp l : Lamp.values()) {
            int i = l.ordinal();
            if (l == Lamp.SHIELD && !c.shieldLamp) continue;
            Lit lit = c.blink[i] && !on ? Lit.DARK : c.lamps[i];
            lamp(f, x + 4f + (i % 5) * (cw + gap), y + 4f + (i / 5) * (ch + gap), cw, ch, l.name(), ICONS[i], lit);
        }
    }

    /** One tell-tale, printed in register: its frame behind amber, its block behind red, its icon and word clear. */
    static void lamp(Filament f, float x, float y, float w, float h, String tag, String icon, Lit lit) {
        float is = 14f, ix = x + w / 2f - is / 2f, iy = y + 4f, tx = x + w / 2f, ty = y + h - 5f;
        if (lit == Lit.WARNING) {
            f.rectLit(x + 1f, y + 1f, w - 2f, h - 2f, f.o().red().norm().r(1.2f));
            f.icon(icon, ix, iy, is, f.o().knock().stroke(1.7f));
            f.knockout(tx, ty, tag, f.o().size(12f).centre());
            return;
        }
        boolean caution = lit == Lit.CAUTION, shown = caution || lit == Lit.ADVISORY;
        f.frameLit(x, y, w, h, 1.4f, caution ? f.o().amber().full() : f.o().amber().ghostOnly().k(1.2f));
        float level = caution ? Drive.NORM : Drive.DIM;
        f.icon(icon, ix, iy, is, f.o().lit(shown).level(level).stroke(1.6f));
        f.legend(tx, ty, tag, f.o().size(12f).lit(shown).level(level).centre());
    }
}
