package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.variants.vfd.DeckPlan;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

/**
 * H4's expanded face (functional-context 7, H4): a slab that rises above the lamp cells on the rack's
 * wing when any light is lit, and stays up for 10 s after the last goes dark. The ship's own plan on
 * a graphic dot panel, then a row per light, its severity leading: the hull's integrity, the shield
 * cloud as a share of its capacity, the power store with the reactors' output (and their rating
 * beside it only while damage holds the output under it), and the oxygen tanks or the emergency
 * reserve. At fire control F3 has the shields and the plan gives F3 the room; on deck there is no
 * plan (the deck is the plan) and the face shows only while a light is lit.
 */
final class Condition {
    /** The face stays up this long after the last light goes dark, s. */
    static final float HOLD = 10f;
    static final float PLAN_H = 88f;

    /** The rows: each light's label, bar or word, value and unit, its level, and power's output. */
    static final class Row {
        String label = "", text = "", value = "", unit = "%", out = "", rating = "";
        float frac;
        boolean power;
        Level level;
    }

    final Row[] rows = new Row[Cautions.Light.values().length];
    int count;
    boolean on, plan, pinned;
    private float held;
    final DeckPlan deck = new DeckPlan();

    Condition() {
        for (int i = 0; i < rows.length; i++) rows[i] = new Row();
    }

    void update(HudData d, Situations s, Cautions c, float delta) {
        count = 0;
        on = false;
        if (d.ctx.ship == null || s.fullScreen || s.seat == Situations.Seat.NONE) return;
        boolean lit = false;
        for (Level l : c.lights) lit |= l != null;
        held = lit ? HOLD : Math.max(0f, held - delta);
        boolean deckSeat = s.seat == Situations.Seat.DECK;
        on = lit || (!deckSeat && (held > 0f || pinned));
        if (!on) return;
        boolean fireControl = s.seat == Situations.Seat.FIRE_CONTROL;
        ShieldStatus sh = d.shieldStatus;
        boolean shields = sh.present && sh.emitters > 0 && !fireControl;
        plan = !deckSeat && !fireControl;
        if (plan) {
            deck.update(d);
            plan = deck.present();
        }
        ShipStatus ss = d.shipStatus;
        PowerFrame p = d.powerFrame;
        for (Cautions.Light l : Cautions.Light.values()) {
            if (l == Cautions.Light.SHIELDS && !shields) continue;
            Row r = rows[count++];
            r.level = c.lights[l.ordinal()];
            r.text = r.out = r.rating = "";
            r.power = false;
            r.unit = "%";
            switch (l) {
                case HULL:
                    r.label = "HULL";
                    share(r, ss.structure);
                    break;
                case SHIELDS:
                    r.label = "SHIELDS";
                    if (!sh.raised && sh.cloud <= 0f) {
                        r.frac = 0f;
                        r.value = "";
                    } else {
                        share(r, sh.share());
                    }
                    break;
                case POWER: {
                    r.label = "POWER";
                    r.power = true;
                    double stored = 0, capacity = 0;
                    for (Circuit ci : p.circuits) {
                        stored += ci.stored;
                        capacity += ci.capacity;
                    }
                    share(r, capacity > 0 ? (float) (stored / capacity) : Float.NaN);
                    r.out = p.circuits.size == 0 ? "" : Fmt.fit(p.outputWatts / 1e6, 2, 1);
                    // The rating beside it only while damage holds the output under it
                    r.rating = p.ratedWatts > 0 && p.outputWatts < p.ratedWatts * 0.99 ? Fmt.fit(p.ratedWatts / 1e6, 2, 1) : "";
                    break;
                }
                default:
                    r.label = "LIFE SUP";
                    boolean emergency = !Double.isNaN(ss.emergencyOxygenKg) && (Float.isNaN(ss.oxygen) || ss.oxygen <= 0.001f);
                    if (emergency) {
                        r.text = "EMERGENCY";
                        r.value = Fmt.fit(Math.round(ss.emergencyOxygenKg), 3, 0);
                        r.unit = "KG";
                        r.frac = Float.isNaN(ss.emergencyOxygen) ? 0f : ss.emergencyOxygen;
                    } else {
                        share(r, ss.oxygen);
                    }
            }
        }
    }

    private static void share(Row r, float frac) {
        if (Float.isNaN(frac)) {
            r.frac = 0f;
            r.value = "";
            return;
        }
        r.frac = Math.max(0f, Math.min(1f, frac));
        r.value = Integer.toString(Math.round(r.frac * 100f));
    }

    float height() {
        return on ? (plan ? PLAN_H + 10f : 4f) + count * Geo.ROW + 2f : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    /** Paints the face with its foot at {@code foot} in the left column. */
    void paint(Filament f, float foot) {
        if (!on) return;
        float x = Geo.LX, h = height(), y = foot - h;
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        if (plan) deck.paint(f, x + 8f, y + 6f, Geo.CW - 16f, PLAN_H);
        float ry = y + (plan ? PLAN_H + 10f : 4f);
        for (int i = 0; i < count; i++) {
            row(f, x, ry, rows[i]);
            ry += Geo.ROW;
        }
    }

    private static void row(Filament f, float x, float ry, Row r) {
        float b = Geo.base(ry), lv = r.level != null ? Drive.NORM : Drive.DIM, bx = x + 88f, vx = x + 168f;
        CautionPanel.glyph(f, x + 5f, Geo.dmTop(b), r.level, false);
        f.legend(x + 27f, b, r.label, f.o().level(lv));
        if (!r.text.isEmpty()) f.dm(bx, Geo.dmTop(b), r.text, f.o().pitch(Geo.P).cells(9).full());
        else Kit.dbar(f, bx, b - 9f, 72f, 7f, 22, r.frac, lv, Float.NaN);
        float cx = vx + f.seg7(vx, b - 12f, 12f, r.value, f.o().cells(3).right().level(lv)) + 5f;
        cx += f.legend(cx, b, r.unit, f.o().dim()) + 8f;
        if (r.power) {
            cx += f.seg7(cx, b - 12f, 12f, r.out, f.o().cells(2).right().level(lv)) + 5f;
            cx += f.legend(cx, b, "OF", f.o().dim().lit(!r.rating.isEmpty())) + 4f;
            cx += f.seg7(cx, b - 12f, 12f, r.rating, f.o().cells(2).right().level(lv)) + 5f;
            cx += f.legend(cx, b, "MW", f.o().dim());
        }
        Kit.fits(cx, x + Geo.CW - 4f, "H4 row");
    }
}
