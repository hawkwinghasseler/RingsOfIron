package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * PER, the captain's own kit (functional-task 6, PER): a reflective LCD under the cluster, on deck
 * while the captain is suited, hurt, or standing in air that is not breathable, smoky or burning. It
 * gives their health as a state, their suit and its air as time, and the room with its air. It is read,
 * never clicked; its warnings come through the band.
 */
final class Personal {
    /** Breathable: at least this share of cabin pressure and of its oxygen (AirWatch's rule). */
    static final float BREATHABLE_OXYGEN = 0.75f;
    /** Smoke over this share is smoky. */
    static final float SMOKY = 0.3f;

    boolean on, alert;
    String health = "", suit = "", here = "", air = "";

    void update(HudData d, Posts p, boolean fireHere) {
        CaptainStatus c = d.captainStatus;
        on = false;
        if (!p.deck() || !c.present) return;
        health = health(c);
        suit = !c.suited ? "NO SUIT" : "EVA".equalsIgnoreCase(c.suitKind) ? "EVA SUIT" : "PRESSURE SUIT";
        String airWord = airWord(c, fireHere);
        String room = c.room.isEmpty() ? "OUTSIDE" : c.room.toUpperCase(Locale.ROOT);
        here = Fmt.cut(room + ": " + airWord, 24);
        air = c.suited ? Fmt.mmss(c.suitAir) : "";
        boolean hurt = !"UNHURT".equals(health);
        boolean bad = !"BREATHABLE".equals(airWord);
        on = c.suited || hurt || bad;
        alert = hurt && !"LIGHT WOUND".equals(health) || bad && !c.suited;
    }

    /** "UNHURT", "LIGHT WOUND", "SERIOUS WOUND", "CRITICAL", "BLEEDING", or "UNCONSCIOUS". */
    static String health(CaptainStatus c) {
        if ("unconscious".equalsIgnoreCase(c.state)) return "UNCONSCIOUS";
        if (!Float.isNaN(c.bleedOut)) return "BLEEDING";
        String w = c.wound == null ? "" : c.wound.toLowerCase(Locale.ROOT);
        if (w.contains("crit")) return "CRITICAL";
        if (w.contains("serious")) return "SERIOUS WOUND";
        if (!w.isEmpty()) return "LIGHT WOUND";
        return "UNHURT";
    }

    /** The air where the captain stands: "BREATHABLE", "THIN AIR", "VACUUM", "SMOKE" or "HOT". */
    static String airWord(CaptainStatus c, boolean fireHere) {
        if (Float.isNaN(c.roomPressure) || c.roomPressure < ShipAlerts.VACUUM_PRESSURE) return "VACUUM";
        boolean thin = c.roomPressure < ShipAlerts.LOW_PRESSURE || !Float.isNaN(c.roomOxygen) && c.roomOxygen < BREATHABLE_OXYGEN;
        if (thin) return "THIN AIR";
        if (fireHere) return "HOT";
        if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > SMOKY) return "SMOKE";
        return "BREATHABLE";
    }

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.LX, y = Geo.PER_Y, w = Geo.CW, h = Geo.PER_H;
        f.plate(x, y, w, h, 6f, 8f);
        Filament.Opt panel = f.o();
        if (alert) panel.backlight();
        f.lcdPanel(x, y, w, h, panel);
        f.lcdText(x + 10f, y + 19f, health, f.o().size(13f));
        f.lcdText(x + 10f, y + 38f, suit, f.o().size(13f));
        f.lcdText(x + 10f, y + 57f, here, f.o().size(13f));
        if (!air.isEmpty()) {
            f.lcdText(x + 216f, y + 19f, "AIR", f.o().size(13f));
            f.lcdSeg7(x + 216f, y + 26f, 24f, air, f.o().slant(6f));
        }
    }
}
