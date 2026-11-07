package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * P2, the caution board: split-flap rows like a departure board, hung from the rail at the top left.
 * Each row is a severity lens (the warning diamond on red, the caution triangle on amber), the code on
 * eleven flaps, the detail on ten, the handling on three (YOU printed on yellow faces, BCK for what clears
 * by itself) and the timer on drums. Past five rows the sixth is "+n more" with the hidden codes. Up only
 * while something is wrong: the dark cockpit.
 */
final class CautionBoard extends YardPart {
    static final float ROW = 24f, PAD = 6f;
    /** Cell widths: the code's, the detail's and the handling's flaps. */
    private static final float CODE_CW = 9f, DETAIL_CW = 8f, HANDLING_CW = 8f;
    private static final Color YOU_FACE = Yard.hex("E0B43A"), YOU_INK = Yard.hex("2A1E08");

    private final ContextFrame f;
    private final Conditions c;

    CautionBoard(ContextFrame f, Conditions c) {
        super(0f, 0f);
        this.f = f;
        this.c = c;
    }

    /** The rows this frame: up to five, and one for the rest. */
    static int rows(int listed) {
        return listed <= Conditions.ROWS ? listed : Conditions.ROWS + 1;
    }

    /** The board's height for {@code listed} rows, 0 with none. */
    static float height(int listed) {
        return listed == 0 ? 0f : PAD * 2f + rows(listed) * ROW;
    }

    @Override
    protected void draw(YardPaint p) {
        Kit.rods(p, 0f, w, ContextLayout.RAIL_H - y, 0f);
        Kit.housing(p, 0f, 0f, w, h, false);
        p.rrect(3f, 3f, w - 6f, h - 6f, 3f, Yard.hex("1A1613"));
        Array<Conditions.Row> list = c.listed;
        int shown = Math.min(list.size, Conditions.ROWS);
        if (list.size > Conditions.ROWS) shown = Conditions.ROWS;
        for (int i = 0; i < shown; i++) row(p, list.get(i), PAD + i * ROW);
        if (list.size > Conditions.ROWS) {
            float ry = PAD + Conditions.ROWS * ROW;
            String more = Conditions.more(list, Conditions.ROWS);
            p.textC(Face.FLAP, 15f, ">", 15f, ry + 16f, Yard.FLAP_TEXT);
            int cells = (int) ((w - 34f - PAD) / 8f);
            p.flaps(28f, ry + 3f, YardText.fit(more, cells), cells, 8f, 18f, 13f, Yard.FLAP_TEXT);
        }
    }

    private void row(YardPaint p, Conditions.Row r, float ry) {
        boolean warning = r.warning;
        boolean pulse = c.pulsing(r) && (f.time * 2f) % 1f >= 0.6f;
        Yard.Lamp lamp = warning ? Yard.RED_LAMP : Yard.AMBER_LAMP;
        // The severity lens: lit while the row holds, dark for a beat of its pulse
        float lx = 6f, ly = ry + 3f, ls = 18f;
        p.rrect(lx - 2f, ly - 2f, ls + 4f, ls + 4f, 2f, Yard.ALU);
        boolean on = !pulse;
        if (on) p.glowRect(lx, ly, ls, ls, warning ? 6f : 4f, lamp.on, warning ? 0.55f : 0.4f);
        p.rrect(lx, ly, ls, ls, 1.2f, on ? lamp.on : lamp.off);
        p.glass(lx, ly, ls, ls);
        Kit.mark(p, warning, lx + ls / 2f, ly + ls / 2f, 4.6f, on ? Kit.LEGEND_ON : Yard.LEGEND_FAINT);

        float x = 30f, fy = ry + 3f;
        p.flaps(x, fy, YardText.fit(r.code, 11), 11, CODE_CW, 18f, 13f, Yard.FLAP_TEXT);
        x += 11 * CODE_CW + 6f;
        p.flaps(x, fy, YardText.fit(Conditions.detail(r), 10), 10, DETAIL_CW, 18f, 13f, Yard.FLAP_TEXT);
        x += 10 * DETAIL_CW + 5f;
        if (r.handling == Conditions.Handling.YOU) youFlaps(p, x, fy);
        else p.flaps(x, fy, r.handling == Conditions.Handling.BACK ? "BCK" : r.handlingText(), 3, HANDLING_CW, 18f, 13f, Yard.FLAP_TEXT);
        x += 3 * HANDLING_CW + 6f;
        String t = timerText(r);
        if (!t.isEmpty()) p.drumsR(w - PAD - 3f, fy, t, 8f, 18f, 14f, w - PAD - 3f - x, false);
    }

    /** The timer field on drums: the harm countdown, or the recovery time beside BCK. */
    static String timerText(Conditions.Row r) {
        if (Float.isNaN(r.timer)) return "";
        if (r.handling == Conditions.Handling.BACK || r.harm) return Kit.timer(r.timer);
        return "";
    }

    /** YOU, printed on yellow flaps: the seam pale, so it never reads as struck through. */
    private static void youFlaps(YardPaint p, float x, float y) {
        p.rrect(x - 2f, y - 2f, 3 * HANDLING_CW + 4f, 22f, 1.5f, Yard.FLAP_FRAME);
        String s = "YOU";
        for (int i = 0; i < 3; i++) {
            float cx = x + i * HANDLING_CW;
            p.rect(cx + 0.5f, y, HANDLING_CW - 1f, 18f, YOU_FACE);
            p.rect(cx + 0.5f, y + 8.5f, HANDLING_CW - 1f, 1f, Yard.hex("F2D78A"), 0.9f);
            p.textC(Face.FLAP, 13f, s.substring(i, i + 1), cx + HANDLING_CW / 2f, y + 9f + 13f * 0.36f, YOU_INK);
        }
    }

    @Override
    public String hover(float px, float py) {
        float ly = localY(py) - PAD;
        int i = (int) Math.floor(ly / ROW);
        Array<Conditions.Row> list = c.listed;
        if (i < 0 || i >= Math.min(list.size, Conditions.ROWS)) return null;
        Conditions.Row r = list.get(i);
        String where = r.detail.isEmpty() ? "" : " · " + r.detail + (r.places > 1 ? " and " + (r.places - 1) + " more" : "");
        String who = r.handling == Conditions.Handling.YOU ? " · needs you"
                : r.handling == Conditions.Handling.BACK ? " · clears by itself"
                : r.crew > 0 ? " · " + r.crew + (r.crew == 1 ? " crew member on it" : " crew on it") : "";
        return Conditions.longName(r.code) + where + who;
    }
}
