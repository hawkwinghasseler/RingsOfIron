package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The right rail of a station's console, on every tab (Context, section 10.3): the credits on drums,
 * the standing with the station's owner, the hold used of its capacity, and each tank as a column.
 */
final class StationRail {
    private static final Color SCALE = Yard.hex("F2ECDC"), FILL = Yard.hex("77705F");
    private final Boards b;
    private final StringBuilder sb = new StringBuilder();
    private String creditsText = "", standingText = "", heldText = "", capText = "";
    private double creditsAt = Double.NaN, heldAt = Double.NaN, capAt = Double.NaN;
    private int standingAt = Integer.MIN_VALUE;
    private final String[] tankText = new String[8];
    private final int[] tankAt = new int[8];

    StationRail(Boards b) {
        this.b = b;
        java.util.Arrays.fill(tankAt, -1);
    }

    void draw(YardPaint p, float x, float y, float w, float h) {
        BigConsole.bay(p, x, y, w, h, null, true);
        Color ink = BigConsole.LIV_INK;
        float rx = x + 16f;
        // Credits: the market's, or the yard's where there is no market
        double credits = b.trade.isDocked() ? b.trade.credits : b.yard.credits;
        if (credits != creditsAt) {
            creditsAt = credits;
            creditsText = String.format(Locale.ROOT, "%05d", Math.max(0L, Math.round(credits)));
        }
        p.legend("CREDITS", rx, y + 26f, ink);
        float cw = p.drums(rx, y + 34f, creditsText, 17f, 28f, 21f, true);
        p.legend("CR", rx + cw + 9f, y + 53f, ink);
        // Standing with the station's owner
        p.legend("STANDING", rx, y + 98f, ink);
        if (b.hasStanding) {
            if (b.standing != standingAt) {
                standingAt = b.standing;
                standingText = (b.standing >= 0 ? "+" : "") + b.standing;
            }
            float sw = p.drums(rx, y + 106f, standingText, 12f, 20f, 15f, false);
            if (!b.owner.isEmpty()) p.legendFit(b.owner.toUpperCase(Locale.ROOT), rx + sw + 12f, y + 121f, w - 44f - sw, ink);
        } else {
            p.legend("--", rx, y + 121f, ink);
        }
        // The hold
        p.legend("HOLD", rx, y + 160f, ink);
        if (b.trade.hasHold) {
            if (b.trade.cargoUsed != heldAt || b.trade.cargoCapacity != capAt) {
                heldAt = b.trade.cargoUsed;
                capAt = b.trade.cargoCapacity;
                heldText = String.valueOf(Math.round(heldAt));
                capText = "OF " + Math.round(capAt) + " M³";
            }
            float hw = p.drums(rx, y + 168f, heldText, 12f, 20f, 15f, false);
            p.legend(capText, rx + hw + 8f, y + 183f, ink);
            bar(p, rx + 100f, y + 174f, w - 132f, capAt <= 0 ? 0f : (float) (heldAt / capAt));
        } else {
            p.legend("NO HOLD", rx, y + 183f, ink);
        }
        // The tanks, a column each, side by side
        int n = Math.min(b.tanks.size, tankText.length);
        if (n == 0) return;
        p.legend("TANKS", rx, y + 226f, ink);
        float colH = Math.min(180f, h - 300f);
        if (colH < 60f) return;
        float span = (w - 64f) / n;
        for (int i = 0; i < n; i++) {
            float lx = rx + 32f + span * i + span / 2f - 9f;
            float f = b.tankFull.get(i);
            level(p, lx, y + 244f, colH, f, ink);
            p.textC(YardFonts.Face.LEGEND, 12f, b.tanks.get(i), lx + 9f, y + 244f + colH + 26f, ink);
            int pct = Math.round(f * 100f);
            if (pct != tankAt[i]) {
                tankAt[i] = pct;
                tankText[i] = String.valueOf(pct);
            }
            float dw = YardPaint.drumWidth(tankText[i], 12f);
            float dx = lx + 9f - (dw + 12f) / 2f;
            p.drums(dx, y + 244f + colH + 34f, tankText[i], 12f, 20f, 15f, false);
            p.legend("%", dx + dw + 5f, y + 244f + colH + 49f, ink);
        }
    }

    /** A horizontal bar: the share used in dark on a cream scale, ticked in tenths, its edge in orange. */
    static void bar(YardPaint p, float x, float y, float w, float frac) {
        float f = MathUtils.clamp(frac, 0f, 1f), h = 8f;
        p.rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2f, Yard.BAKELITE);
        p.rect(x, y, w, h, SCALE);
        p.rect(x, y, f * w, h, FILL);
        for (int i = 1; i < 10; i++) p.rect(x + w * i / 10f - 0.4f, y, i % 5 == 0 ? 1f : 0.6f, i % 5 == 0 ? 4.5f : 2.5f, Yard.INK, 0.8f);
        p.rect(x + f * w - 0.65f, y - 1.5f, 1.3f, h + 3f, Yard.ORANGE);
        p.glass(x, y, w, h);
    }

    /** A vertical level: the column filled to {@code frac}, ticked in tenths, a low mark, its scale beside it. */
    static void level(YardPaint p, float x, float y, float h, float frac, Color ink) {
        float w = 18f, f = MathUtils.clamp(frac, 0f, 1f), iy = y + h - f * h;
        p.rrect(x - 4f, y - 4f, w + 8f, h + 8f, 2.5f, Yard.BAKELITE);
        p.rect(x, y, w, h, SCALE);
        p.rect(x, iy, w, f * h, FILL);
        p.rect(x + w - 3f, y + h - 0.2f * h, 3f, 0.2f * h, Yard.MUSTARD);
        for (int i = 1; i < 10; i++) {
            float yy = y + h - i * h / 10f;
            p.rect(x, yy - 0.3f, i % 5 == 0 ? 7f : 4f, i % 5 == 0 ? 1f : 0.6f, Yard.INK);
        }
        p.rect(x - 2f, iy - 0.75f, w + 4f, 1.5f, Yard.ORANGE);
        p.glass(x, y, w, h);
        p.textR(YardFonts.Face.LEGEND, 12f, "0", x - 9f, y + h + 4.3f, ink);
        p.textR(YardFonts.Face.LEGEND, 12f, "50", x - 9f, y + h / 2f + 4.3f, ink);
        p.textR(YardFonts.Face.LEGEND, 12f, "100", x - 9f, y + 4.3f, ink);
    }
}
