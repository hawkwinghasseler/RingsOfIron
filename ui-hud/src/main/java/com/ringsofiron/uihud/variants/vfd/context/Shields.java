package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * F3, shield control (functional-context 7, F3), at fire control on a hull with an emitter, in the
 * left column's bottom: a ring round the ship's outline, a sector a side, its bands lit by how thick
 * the cloud is there, so a dead emitter's gap shows, with the trim's side marked; and beside it the
 * cloud's mass against its capacity, what is loose, raised or lowered, the trim with its key, and the
 * emitters with the hottest's heat. The game keeps the cloud in four sides, so the ring has four
 * sectors where the spec asks eight.
 */
final class Shields {
    static final float FULL_H = 186f, MIN_H = 96f, RING_R = 62f;
    static final int BANDS = 4;
    static final String[] RAISED = {"RAISED", "LOWERED"};
    /** The ring's cells clockwise from the top, the ship's nose to the right: port, bow, starboard, stern. */
    static final int[] CELL_SECTOR = {1, 0, 3, 2};
    /** Where each trim leans, in the ring's degrees (0 up, clockwise), or NaN for even. */
    static final String[] TRIMS = {"BOW", "PORT", "STERN", "STBD"};
    static final float[] TRIM_DEG = {90f, 0f, 270f, 180f};

    boolean on, raised, dock;
    final float[] thick = new float[4];
    String cloud = "", capacity = "", loose = "", trim = "", trimKey = "8", working = "", emitters = "", tripped = "", heat = "";
    float heatFrac, trimDeg = Float.NaN;
    boolean anyTripped;
    private final float[] lit2 = new float[2];
    /** Each band of the ring, outermost first: a side's band is lit while the cloud there is thicker than it. */
    private final Filament.RingLevels[] bands = new Filament.RingLevels[BANDS];

    Shields() {
        for (int b = 0; b < BANDS; b++) {
            final float past = (BANDS - 1 - b) / (float) BANDS + 0.01f;
            bands[b] = (i, n) -> thick[CELL_SECTOR[i]] > past ? Drive.NORM : Float.NaN;
        }
    }

    void update(HudData d, Situations s) {
        ShieldStatus sh = d.shieldStatus;
        on = s.seat == Situations.Seat.FIRE_CONTROL && sh.present && sh.emitters > 0 && !s.fullScreen && !s.docked && !s.latched;
        if (!on) return;
        raised = sh.raised;
        dock = sh.loweredForDock;
        float quarter = sh.capacity > 0f ? sh.capacity / 4f : 0f;
        for (int i = 0; i < 4; i++) thick[i] = quarter > 0f ? Math.max(0f, Math.min(1f, sh.sectors[i] / quarter)) : 0f;
        cloud = Fmt.fit(Math.round(sh.cloud), 3, 0);
        capacity = Fmt.fit(Math.round(sh.capacity), 3, 0);
        float l = sh.scattered + sh.adrift;
        loose = l >= 0.5f ? Fmt.fit(Math.round(l), 3, 0) : "";
        trim = Arms.trimWord(sh.trim);
        trimDeg = Float.NaN;
        for (int i = 0; i < TRIMS.length; i++) if (TRIMS[i].equals(trim)) trimDeg = TRIM_DEG[i];
        String k = d.key("SHIELD_TRIM");
        trimKey = k.isEmpty() ? "8" : k.toUpperCase(Locale.ROOT);
        working = Integer.toString(Math.min(9, sh.emittersWorking));
        emitters = Integer.toString(Math.min(9, sh.emitters));
        anyTripped = sh.emittersTripped > 0;
        tripped = anyTripped ? Integer.toString(Math.min(9, sh.emittersTripped)) : "";
        heatFrac = Float.isNaN(sh.hottest) ? 0f : Math.max(0f, Math.min(1f, sh.hottest));
        heat = Float.isNaN(sh.hottest) ? "" : Integer.toString(Math.round(heatFrac * 100f));
    }

    // ------------------------------------------------------------------ painting, on the grid from the bottom

    /** Paints F3 with its foot at {@code foot}, {@code h} tall (it shrinks to give H4 its face). */
    void paint(Filament f, float foot, float h) {
        if (!on) return;
        h = Math.max(MIN_H, Math.min(FULL_H, h));
        float x = Geo.LX, y = foot - h;
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        float r = Math.min(RING_R, (h - 16f) / 2f), cx = x + 10f + r, cy = y + h / 2f;
        float band = (r * 0.36f) / BANDS;
        for (int b = 0; b < BANDS; b++) {
            float r0 = r - (b + 1) * band + 0.5f, r1 = r - b * band - 0.5f;
            f.ring(cx, cy, r0, r1, 4, bands[b], f.o().start(-45f).span(360f).gapDeg(8f));
        }
        float s = r * 0.9f;
        f.icon("ship", cx - s / 2f, cy - s / 2f, s, f.o().norm());
        if (!Float.isNaN(trimDeg)) {
            // The trim leans the cloud to a side: a mark outside the ring there
            float a = (float) Math.toRadians(trimDeg), mx = cx + (float) Math.sin(a) * (r + 5f), my = cy - (float) Math.cos(a) * (r + 5f);
            f.dot(mx, my, f.o().size(3f).full());
        }
        float tx = x + 2f * r + 22f, limit = x + Geo.CW - 4f;
        int rows = (int) ((h - 8f) / Geo.ROW);
        float ry = y + 4f + Math.max(0f, (h - 8f - rows * Geo.ROW) / 2f);
        for (int i = 0; i < Math.min(rows, 6); i++) {
            row(f, tx, Geo.base(ry), i, limit);
            ry += Geo.ROW;
        }
    }

    private void row(Filament f, float x, float b, int i, float limit) {
        float cx = x;
        switch (i) {
            case 0:
                lit2[0] = raised ? Drive.NORM : Float.NaN;
                lit2[1] = !raised ? Drive.NORM : Float.NaN;
                cx = Kit.states(f, cx, b, RAISED, lit2, 13f, 6f);
                break;
            case 1:
                cx = Rows.num(f, cx, b, cloud, 3) - 2f;
                cx = Rows.lab(f, cx, b, "OF");
                cx = Rows.num(f, cx, b, capacity, 3) - 2f;
                cx = Rows.lab(f, cx, b, "KG");
                break;
            case 2:
                cx = Rows.lab(f, cx, b, "TRIM");
                cx = Rows.txt(f, cx, b, trim, 5);
                cx += f.keycap(cx, b - 13f, trimKey, 16f, 11f, false);
                break;
            case 3:
                cx = Rows.lab(f, cx, b, "EMIT");
                cx = Rows.num(f, cx, b, working, 1) - 2f;
                cx = Rows.lab(f, cx, b, "OF");
                cx = Rows.num(f, cx, b, emitters, 1) + 2f;
                cx = Rows.state(f, cx, b, "TRIP", anyTripped, Phosphor.AMBER) - 3f;
                cx = Rows.num(f, cx, b, tripped, 1, Phosphor.AMBER, Drive.FULL);
                break;
            case 4:
                cx = Rows.lab(f, cx, b, "HEAT");
                f.bar(cx, b - 9f, 54f, 7f, 16, heatFrac, f.o().zone(Kit.RISING).norm());
                cx = Rows.num(f, cx + 60f, b, heat, 3) - 2f;
                cx = Rows.lab(f, cx, b, "%");
                break;
            default:
                cx = Rows.lab(f, cx, b, "LOOSE");
                cx = Rows.num(f, cx, b, loose, 3) - 2f;
                cx = Rows.lab(f, cx, b, "KG");
                if (dock) cx = Rows.state(f, cx + 4f, b, "DOCK", true, Phosphor.ZINC);
        }
        Kit.fits(cx, limit, "F3 row");
    }
}
