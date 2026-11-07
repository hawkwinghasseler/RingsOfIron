package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z4 under the annunciator, the captain's own kit (PER), in avocado: health as a word, the suit and its
 * air on drums, the room underfoot and its air. Read, never pressed: its warnings come through the band.
 * Up on deck only, while suited, hurt, or standing in air that is not good.
 */
final class SuitUnit extends YardPart {
    static final float W = 300f, H = 84f;
    private static final Color LEGEND = Yard.hex("DFE3C8");

    private final TaskFrame f;

    SuitUnit(TaskFrame f) {
        super(0f, 122f);
        this.f = f;
    }

    /** The spec's trigger: suited, hurt, or the room's air not breathable, smoky or burning. */
    static boolean wanted(HudData d) {
        CaptainStatus c = d.captainStatus;
        if (!c.present) return false;
        return c.suited || hurt(c) || !"BREATHABLE".equals(air(d));
    }

    static boolean hurt(CaptainStatus c) {
        return c.health < 0.95f || !c.wound.isEmpty() || !Float.isNaN(c.bleedOut) || !"conscious".equals(c.state);
    }

    /** Health as the flap words it. */
    static String health(CaptainStatus c) {
        if ("dead".equals(c.state)) return "DEAD";
        if ("unconscious".equals(c.state)) return "UNCONSCIOUS";
        if (!Float.isNaN(c.bleedOut)) return "BLEEDING";
        if (c.health >= 0.95f && c.wound.isEmpty()) return "UNHURT";
        if (c.health >= 0.6f) return "LIGHT WOUND";
        if (c.health >= 0.3f) return "SERIOUS WOUND";
        return "CRITICAL";
    }

    static String suit(CaptainStatus c) {
        if (!c.suited) return "NONE";
        return "EVA".equals(c.suitKind) ? "EVA" : "PRESSURE";
    }

    /** The air where the captain stands, worst first: FIRE, VACUUM, SMOKE, THIN, BREATHABLE; OUTSIDE out of a room. */
    static String air(HudData d) {
        CaptainStatus c = d.captainStatus;
        if (Float.isNaN(c.roomPressure)) return "OUTSIDE";
        if (!c.room.isEmpty() && d.alerts.get("fire:" + c.room) != null) return "FIRE";
        if (c.roomPressure < ShipAlerts.VACUUM_PRESSURE) return "VACUUM";
        if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.3f) return "SMOKE";
        if (c.roomPressure < ShipAlerts.LOW_PRESSURE || (!Float.isNaN(c.roomOxygen) && c.roomOxygen < ShipAlerts.LOW_PRESSURE)) return "THIN";
        return "BREATHABLE";
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        CaptainStatus c = d.captainStatus;
        Yard.Finish fin = Yard.Finish.AVOCADO;
        p.unit(0f, 122f, W, H, fin, "l");
        p.legend("YOU", 12f, 143.5f, fin.ink);
        p.line(40f, 139f, 56f, 139f, 1f, Yard.INK_LIGHT, 0.5f);

        p.legend("HEALTH", 64f, 143.5f, LEGEND);
        String h = health(c);
        p.flaps(118f, 131f, h, 13, 8.4f, 16f, 13f, "UNHURT".equals(h) ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);

        p.legend("SUIT", 64f, 166.5f, LEGEND);
        p.flaps(118f, 154f, suit(c), 8, 8.4f, 16f, 13f, Yard.FLAP_TEXT);
        p.legend("AIR", 194f, 166.5f, LEGEND);
        if (c.suited && !Float.isNaN(c.suitAir)) {
            float secs = Math.max(0f, c.suitAir);
            if (secs < 600f) {
                float w = p.drums(220f, 153f, YardText.clock(secs), 12f, 18f, 15f, false);
                p.legend("LEFT", 224f + w, 166.5f, LEGEND);
            } else {
                float w = p.drums(220f, 153f, String.valueOf((int) (secs / 60f)), 12f, 18f, 15f, false);
                p.legend("MIN LEFT", 224f + w, 166.5f, LEGEND);
            }
        } else {
            // No suit: no suit air to read, so the drums are plated over
            p.blank(220f, 152f, 68f, 20f);
        }

        p.legend("HERE", 64f, 189.5f, LEGEND);
        String room = c.room.isEmpty() ? "" : c.room.toUpperCase(Locale.ROOT);
        p.flaps(118f, 177f, YardText.fit(room, 10), 10, 8.4f, 16f, 13f, Yard.FLAP_TEXT);
        String air = air(d);
        p.flaps(207f, 177f, air, 10, 8.4f, 16f, 13f, "BREATHABLE".equals(air) ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
    }

    @Override
    public String hover(float px, float py) {
        CaptainStatus c = f.data.captainStatus;
        float ly = localY(py);
        if (ly < 150f && !c.wound.isEmpty()) return "Wound: " + c.wound;
        if (ly >= 150f && ly < 173f && c.suited && !Float.isNaN(c.suitAir) && !Float.isNaN(c.suitAirCapacity) && c.suitAirCapacity > 0f) {
            return String.format(Locale.ROOT, "Suit air %.0f%% of a full bottle", 100f * c.suitAir / c.suitAirCapacity);
        }
        if (ly >= 173f && !Float.isNaN(c.roomPressure)) {
            return String.format(Locale.ROOT, "Pressure %.0f%%, oxygen %.0f%%", 100f * c.roomPressure, 100f * (Float.isNaN(c.roomOxygen) ? 0f : c.roomOxygen));
        }
        return null;
    }
}
