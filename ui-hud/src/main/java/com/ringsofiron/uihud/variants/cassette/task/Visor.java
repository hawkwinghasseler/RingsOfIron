package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * Z2, the alert visor (ALR): 640 wide, hinged at the top edge, swung down while an alert stands and
 * folded away when none does. Two tubes that keep their places, each with its master lens: the warning
 * tube (one warning, its title large in red and its action under it) over the caution tube (three fixed
 * two-line slots and a narrow +n slot). Pressing anywhere on it acknowledges the band.
 */
final class Visor extends YardPart {
    static final float W = 640f, H = 104f;
    private static final float TX = 406f, TW = 542f, NW = 40f, SW = (TW - NW) / 3f;
    private static final Color SLOT_LINE = Yard.hex("6B6A63");

    private final TaskFrame f;
    /** 0 folded away, 1 down. */
    private float drop;

    Visor(TaskFrame f) {
        super(320f, 0f);
        this.f = f;
    }

    /** Swings the visor towards down while the band has something, else folds it. */
    void swing(float delta, boolean up) {
        drop = MathUtils.clamp(drop + (up ? delta : -delta) / 0.22f, 0f, 1f);
        visible = drop > 0f;
    }

    /** How far the visor is down, 0 to 1, so the layout can move what hangs under it. */
    float drop() {
        return drop;
    }

    @Override
    protected void draw(YardPaint p) {
        // Swinging: the whole unit rides up out of the top edge as it folds
        float lift = (1f - Interp.smooth(drop)) * -(H + 6f);
        p.origin(p.originX(), p.originY() + lift);
        TaskAlerts a = f.alerts;
        p.unit(320f, 0f, W, H, Yard.Finish.YARD, "t");
        for (float hx : new float[]{380f, 890f}) {
            p.rrect(hx, 0f, 10f, 5f, 1.5f, Yard.ALU);
            p.rect(hx, 0f, 10f, 1.4f, Color.WHITE, 0.4f);
        }
        masterLens(p, 332f, 10f, 64f, 40f, "MASTER", "WARNING", Yard.RED_LAMP, a.masterWarning, true, a.warningNew);
        masterLens(p, 332f, 58f, 64f, 36f, "MASTER", "CAUTION", Yard.AMBER_LAMP, a.masterCaution, false, false);

        // The warning tube: one warning, its title large in red, its action under it; empty, it stays dark
        p.tube(TX, 10f, TW, 40f, 3f, 4f, false);
        Alert w = a.warning;
        if (w != null) {
            float pulse = a.warningNew ? 0.6f + 0.4f * (0.5f + 0.5f * MathUtils.cos(p.time() * MathUtils.PI2 * 2f)) : 1f;
            Color red = tmp.set(Yard.P_RED);
            red.a = pulse;
            p.diamond(TX + 17f, 24f, 6f, red);
            p.title(TaskAlerts.title(w, f.data), TX + 28f, 32f, 25f, red);
            p.tubeLine(TX + 10f, 45.5f, 13f, Yard.P_AMBER, TaskAlerts.action(w, f.data, f.post));
        }
        p.tubeOver(TX, 10f, TW, 40f);

        // The caution tube: three slots and the +n slot, the slot lines etched on the faceplate
        p.tube(TX, 58f, TW, 36f, 3f, 4f, false);
        int shown = Math.min(3, a.cautions.size);
        for (int i = 0; i < shown; i++) {
            String[] l = TaskAlerts.lines(a.cautions.get(i), f.data);
            float cx = TX + i * SW + 8f;
            float cw = p.tube(l[0], cx, 72.5f, 13f, Yard.P_AMBER, true);
            p.tube(l[1], cx + cw + 7.8f, 72.5f, 13f, Yard.P_AMBER, false);
            p.tube(l[2], cx, 88f, 13f, Yard.P_AMBER, false);
        }
        int more = a.cautions.size - shown;
        if (more > 0) {
            String m = "+" + more;
            float kw = p.width(YardFonts.Face.TUBE_BOLD, 13f, m) + 8f;
            p.tubeKey(m, TX + TW - NW / 2f - kw / 2f, 81f, 13f, Yard.P_AMBER);
        }
        p.tubeOver(TX, 58f, TW, 36f);
        for (int i = 1; i <= 3; i++) p.rect(TX + i * SW - 0.4f, 60f, 0.8f, 32f, SLOT_LINE, 0.55f);
    }

    private final Color tmp = new Color();

    /** A master lens of two engraved lines; lit, the first carries the severity glyph. */
    private void masterLens(YardPaint p, float x, float y, float w, float h, String l1, String l2, Yard.Lamp lamp, boolean lit, boolean warning, boolean blink) {
        boolean on = lit && (!blink || (p.time() * 2.5f) % 1f < 0.62f);
        p.push(x, y, w, h, new String[]{"", l2}, lamp, on, YardPaint.Kind.INDICATOR, false, warning && on);
        Color tc = on ? Yard.LEGEND_ON_LIT : Yard.LEGEND_FAINT;
        float base = y + h / 2f - 6.5f + 4.3f, tw = p.width(YardFonts.Face.LEGEND, 12f, l1), gw = lit ? 9f : 0f;
        float x0 = x + (w - tw - gw) / 2f;
        if (lit) {
            if (warning) p.diamond(x0 + 3.5f, base - 4.2f, 3.6f, tc);
            else p.triangle(x0 + 3.5f, base - 4.4f, 3.8f, tc);
        }
        p.text(YardFonts.Face.LEGEND, 12f, l1, x0 + gw, base, tc);
    }

    @Override
    public boolean pressable(float px, float py) {
        return drop >= 1f;
    }

    /** Pressing the visor acknowledges the band: cautions leave for their lamps, the warning stops pulsing. */
    @Override
    public boolean press(float px, float py) {
        f.data.alerts.acknowledgeAll();
        return true;
    }

    @Override
    public String hover(float px, float py) {
        if (drop < 1f) return null;
        float lx = localX(px), ly = localY(py);
        TaskAlerts a = f.alerts;
        if (ly < 54f) return a.warning == null ? null : a.warning.text;
        if (lx >= TX && lx < TX + 3 * SW) {
            int i = (int) ((lx - TX) / SW);
            if (i < a.cautions.size) return a.cautions.get(i).text;
        }
        if (lx >= TX + 3 * SW && a.cautions.size > 3) {
            StringBuilder b = new StringBuilder();
            for (int i = 3; i < a.cautions.size; i++) b.append(i > 3 ? ";  " : "").append(a.cautions.get(i).text);
            return b.toString();
        }
        return "Press to acknowledge the alerts";
    }

    /** Easing for the visor's swing. */
    static final class Interp {
        static float smooth(float t) {
            return t * t * (3f - 2f * t);
        }
    }
}
