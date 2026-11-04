package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * H4, the ship's condition at the bottom left: its health in one glance, the detail when it matters. The
 * compact face is a strip of four lights, HULL, SHIELDS, POWER and LIFE SUP, dark while nominal, with a
 * blanking plate where SHIELDS would be on a hull without an emitter. When a light comes on (or under the
 * pointer) a unit rises above the strip with the ship's plan on its green tube and a row for each light: a
 * level bar and its reading on drums. It stays up 10 s after the last light goes dark. On deck the deck is
 * the plan, so the rows rise without it, and only while a light is lit; at fire control shield control
 * has the shields, so the strip is three lights and the rows leave the plan to the rack's eye.
 */
final class ConditionUnit extends YardPart {
    enum Light {
        HULL, SHIELDS, POWER, LIFE_SUP
    }

    static final float W = 308f, STRIP_H = 40f, ROW_H = 26f, PLAN_H = 100f;
    /** Seconds the rows stay up after the last light goes dark. */
    static final float HOLD = 10f;

    private final ContextFrame f;
    private final Conditions c;
    private final ShipPlan plan = new ShipPlan();
    /** Each light's state: 0 dark, 1 caution, 2 warning. */
    final int[] level = new int[Light.values().length];
    /** The shields light and row are here (a hull with an emitter, and no shield control beside it). */
    boolean shields;
    boolean withPlan, expanded;
    /** Lights in the strip: four, or three when shield control has the shields. */
    int slots = 4;
    /** The rows that rise this frame (on deck only the lit ones), their pitch, and the plan's height. */
    final java.util.List<Light> rows = new java.util.ArrayList<>();
    float rowH = ROW_H, planH;
    private float litAt = -100f, hoverAt = -100f;

    ConditionUnit(ContextFrame f, Conditions c) {
        super(0f, 0f);
        this.f = f;
        this.c = c;
    }

    /** The lights from the conditions that hold, by P2's thresholds. */
    void update(boolean shieldsHere) {
        shields = shieldsHere;
        java.util.Arrays.fill(level, 0);
        Array<Conditions.Row> all = c.all;
        for (int i = 0; i < all.size; i++) {
            Conditions.Row r = all.get(i);
            Light l = light(r);
            if (l == null || l == Light.SHIELDS && !shields) continue;
            level[l.ordinal()] = Math.max(level[l.ordinal()], r.warning ? 2 : 1);
        }
        if (anyLit()) litAt = f.time;
    }

    /** Which light a condition belongs to, or null for one that is no light's (a fire, a casualty). */
    static Light light(Conditions.Row r) {
        switch (r.code) {
            case "HULL DAMAGE":
            case "BREACH": return Light.HULL;
            case "SHIELD DOWN":
            case "SHIELD WEAK": return Light.SHIELDS;
            case "NO POWER":
            case "POWER LOW": return Light.POWER;
            case "EMERG O2":
            case "O2 LOW":
            case "VACUUM":
            case "PRESS LOW": return Light.LIFE_SUP;
            case "SYSTEM DOWN": return "LIFE SUP".equals(r.detail) ? Light.LIFE_SUP : null;
            default: return null;
        }
    }

    boolean anyLit() {
        for (int v : level) if (v > 0) return true;
        return false;
    }

    /** Whether the rows are up: a light lit, the pointer over it, or within 10 s of the last. */
    boolean wantsRows(boolean deck) {
        if (deck) return anyLit();
        return anyLit() || f.time - litAt < HOLD || f.time - hoverAt < 0.25f;
    }

    /**
     * The unit's height this frame in {@code room}, and which face it shows. {@code shieldControl}: shield
     * control is up beside it, which has the shields and the eye, so the strip has three lights and the
     * rows no plan. On deck the deck is the plan, and only the lit lights' rows rise. The plan takes what
     * the zone has left, up to {@link #PLAN_H}, and goes when that is too little to read.
     */
    float measure(boolean deck, boolean shieldControl, float room) {
        expanded = wantsRows(deck);
        slots = shieldControl ? 3 : 4;
        rows.clear();
        for (Light l : Light.values()) {
            if (l == Light.SHIELDS && !shields) continue;
            if (deck && level[l.ordinal()] == 0) continue;
            rows.add(l);
        }
        if (deck && !expanded) return 0f;
        if (!expanded || rows.isEmpty()) {
            expanded = false;
            withPlan = false;
            return STRIP_H;
        }
        rowH = Math.max(20f, Math.min(ROW_H, (room - STRIP_H - 12f) / rows.size()));
        float base = STRIP_H + rows.size() * rowH + 12f;
        float left = room - base - 6f;
        withPlan = !deck && !shieldControl && left >= 60f;
        planH = withPlan ? Math.min(PLAN_H, left) : 0f;
        return Math.min(room, base + (withPlan ? planH + 6f : 0f));
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Kit.slideRail(p, -x, true, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, true);
        float y0 = 6f;
        if (expanded) {
            if (withPlan) {
                float tx = 8f, ty = y0 + 2f, tw = w - 16f, th = planH - 4f;
                p.tube(tx, ty, tw, th, 4f, 2f, false);
                plan.draw(p, d, tx + 6f, ty + 4f, tw - 12f, th - 8f, f.time);
                p.tubeOver(tx, ty, tw, th);
                y0 += planH + 6f;
            }
            float ph = rows.size() * rowH + 8f;
            p.face(6f, y0, w - 12f, ph, Yard.Finish.YARD, false);
            float ry = y0 + 4f + (rowH - ROW_H) / 2f;
            for (int i = 0; i < rows.size(); i++, ry += rowH) row(p, d, rows.get(i), ry);
        }
        strip(p, h - STRIP_H + 4f);
    }

    /** One light's row, as its instrument reads. */
    private void row(YardPaint p, HudData d, Light l, float ry) {
        int lit = level[l.ordinal()];
        switch (l) {
            case HULL:
                row(p, ry, "HULL", d.shipStatus.structure, pct(d.shipStatus.structure), "", "", lit);
                break;
            case SHIELDS: {
                ShieldStatus s = d.shieldStatus;
                float frac = s.raised && s.capacity > 0f ? s.cloud / s.capacity : 0f;
                row(p, ry, "SHIELDS", frac, s.raised ? pct(frac) : "", "", s.raised ? "" : "STOWED", lit);
                break;
            }
            case POWER: {
                float charge = d.powerFrame.circuits.size > 0 ? Kit.charge(d.powerFrame) : 0f;
                String gen = String.format(Locale.ROOT, "%.1f", d.powerFrame.outputWatts / 1e6);
                row(p, ry, "POWER", charge, pct(charge), "GEN", gen + "|MW", lit);
                break;
            }
            default: {
                float o2 = d.shipStatus.oxygen;
                boolean emergency = !Float.isNaN(o2) && o2 <= 0.001f && !Double.isNaN(d.shipStatus.emergencyOxygenKg);
                if (emergency) row(p, ry, "LIFE SUP", d.shipStatus.emergencyOxygen, "", "EMERG", YardText.count(d.shipStatus.emergencyOxygenKg, 2).trim() + "|KG", lit);
                else row(p, ry, "LIFE SUP", o2, "", "TANKS", pct(o2) + "|%", lit);
                break;
            }
        }
    }

    /** A row: its name, a level bar, the reading on drums with its unit; and a second reading after it. */
    private static void row(YardPaint p, float y, String name, float frac, String drum, String label2, String value2, int lit) {
        Color ink = lit == 2 ? Yard.RED : Yard.INK;
        p.legend(name, 16f, y + 16f, ink);
        p.level(86f, y + 6f, 66f, 10f, Float.isNaN(frac) ? 0f : frac, null, null);
        float x = 164f;
        if (!drum.isEmpty()) {
            float dw = p.drums(x, y + 2f, drum, 11f, 19f, 15f, false);
            p.legend("%", x + dw + 5f, y + 16f, Yard.INK);
            x += dw + 22f;
        } else if (label2.isEmpty() && !value2.isEmpty()) {
            p.flaps(x, y + 3f, value2, 6, 8f, 17f, 12f, Yard.FLAP_TEXT);
            return;
        }
        if (label2.isEmpty()) return;
        boolean flapsLabel = label2.equals("TANKS") || label2.equals("EMERG");
        if (flapsLabel) {
            x = 164f;
            p.flaps(x, y + 3f, label2, 5, 8.5f, 17f, 12f, lit > 0 && label2.equals("EMERG") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
            x += 5 * 8.5f + 8f;
        } else {
            x += p.legend(label2, x, y + 16f, Yard.INK) + 5f;
        }
        int bar = value2.indexOf('|');
        String v = bar < 0 ? value2 : value2.substring(0, bar), unit = bar < 0 ? "" : value2.substring(bar + 1);
        float dw = p.drums(x, y + 2f, v, 11f, 19f, 15f, false);
        if (!unit.isEmpty()) p.legend(unit, x + dw + 5f, y + 16f, Yard.INK);
    }

    private static String pct(float frac) {
        if (Float.isNaN(frac)) return "--";
        return String.valueOf(Math.round(Math.max(0f, Math.min(1f, frac)) * 100f));
    }

    /** The strip of lights along the unit's foot. */
    private void strip(YardPaint p, float y) {
        int n = slots;
        float gap = 4f, lw = (w - 16f - gap * (n - 1)) / n, x = 8f, lh = 26f;
        for (Light l : Light.values()) {
            if (l == Light.SHIELDS && !shields) {
                // A blanking plate where a hull has no emitter; at fire control the light is shield control's
                if (n == 4) {
                    Kit.blankBay(p, x, y + 1f, lw, lh);
                    x += lw + gap;
                }
                continue;
            }
            String name = l == Light.LIFE_SUP ? "LIFE SUP" : l.name();
            lightLens(p, x, y + 1f, lw, lh, name, level[l.ordinal()]);
            x += lw + gap;
        }
    }

    /** One light: dark amber glass while nominal; lit amber with the caution triangle, or red with the diamond. */
    private void lightLens(YardPaint p, float x, float y, float w, float h, String name, int lit) {
        boolean warn = lit == 2, on = lit > 0;
        Yard.Lamp lamp = warn ? Yard.RED_LAMP : Yard.AMBER_LAMP;
        p.rrect(x - 2f, y - 2f, w + 4f, h + 4f, 2f, Yard.ALU);
        if (on) p.glowRect(x, y, w, h, 5f, lamp.on, warn ? 0.6f : 0.45f);
        p.rrect(x, y, w, h, 1.5f, on ? lamp.on : Yard.hex("5A4126"));
        p.glass(x, y, w, h);
        Color ink = on ? Kit.LEGEND_ON : Yard.hex("C9A77A");
        float tw = p.width(Face.LEGEND, 12f, name), gw = on ? 11f : 0f;
        float tx = x + (w - tw - gw) / 2f, base = y + h / 2f + 4.3f;
        if (on) Kit.mark(p, warn, tx + 4f, base - 4.3f, 4f, ink);
        p.text(Face.LEGEND, 12f, name, tx + gw, base, ink);
    }

    @Override
    public String hover(float px, float py) {
        hoverAt = f.time;
        return null;
    }
}
