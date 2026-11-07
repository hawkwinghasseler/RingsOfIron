package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * D1, the forearm suit unit on deck: the captain's own survival, in the avocado of the captain's own
 * kit. The condition on flaps with the bleed-out on drums (a shutter closes over them when not
 * bleeding), the SUIT ON lens and the suit's air on drums, the air where the captain stands as three
 * printed windows (VACUUM THIN NORMAL), and what is carried on flaps. Up on deck whenever a field is not
 * normal or the suit is on; the emergency placard takes the air when it runs under four minutes.
 */
final class SuitUnit extends YardPart {
    static final float W = 308f, H = 90f;
    static final String[] AIR = {"VACUUM", "THIN", "NORMAL"};

    private final ContextFrame f;

    SuitUnit(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Whether D1 is up: a wound, bleeding, the suit on, the captain out cold, or the air here not normal. */
    static boolean wanted(CaptainStatus c) {
        if (!c.present) return false;
        return c.suited || !c.wound.isEmpty() || !Float.isNaN(c.bleedOut) || c.health < 0.9f
                || !"conscious".equals(c.state) || air(c) != 2;
    }

    /** The condition word: OK, LIGHT, SERIOUS, CRITICAL, OUT COLD, DEAD. */
    static String condition(CaptainStatus c) {
        if ("dead".equals(c.state)) return "DEAD";
        if ("unconscious".equals(c.state)) return "OUT COLD";
        if (!Float.isNaN(c.bleedOut) && c.bleedOut < 120f || c.health < 0.3f) return "CRITICAL";
        if (c.health < 0.6f || !Float.isNaN(c.bleedOut)) return "SERIOUS";
        if (c.health < 0.9f || !c.wound.isEmpty()) return "LIGHT";
        return "OK";
    }

    /** The air where the captain stands: 0 vacuum, 1 thin, 2 normal. Outside a room is vacuum. */
    static int air(CaptainStatus c) {
        if (Float.isNaN(c.roomPressure)) return 0;
        if (c.roomPressure < 0.1f) return 0;
        boolean thin = c.roomPressure < 0.7f || !Float.isNaN(c.roomOxygen) && c.roomOxygen < 0.7f;
        return thin ? 1 : 2;
    }

    /** The suit's air shows here: suited, not breathing ship air, and not yet the placard's (under 240 s). */
    static boolean airShown(CaptainStatus c) {
        return c.suited && !Float.isNaN(c.suitAir) && !ContextFrame.suitCritical(c) && air(c) != 2;
    }

    @Override
    protected void draw(YardPaint p) {
        CaptainStatus c = f.data.captainStatus;
        Yard.Finish fin = Yard.Finish.AVOCADO;
        // The strap the unit is worn on, either side of it
        p.rect(-6f, 18f, 6f, h - 36f, Yard.RUBBER);
        p.rect(w, 18f, 6f, h - 36f, Yard.RUBBER);
        p.rrect(1.5f, 3f, w, h, 6f, Yard.SHADOW, 0.6f);
        p.shade(0f, 0f, w, h, 6f, fin.top, fin.bottom);
        p.rect(4f, 1f, w - 8f, 1f, fin.hi, 0.6f);
        Color ink = fin.ink;
        // YOU: the condition, the bleed-out on drums or the shutter over them
        String cond = condition(c);
        p.legend("YOU", 12f, 20f, ink);
        p.flaps(52f, 7f, cond, 8, 8.4f, 17f, 12.5f, cond.equals("OK") ? Yard.FLAP_TEXT : cond.equals("LIGHT") ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
        p.legend("BLEED", 132f, 20f, ink);
        if (!Float.isNaN(c.bleedOut)) p.drums(176f, 6f, YardText.clock(c.bleedOut), 11f, 18f, 15f, false);
        else shutter(p, 176f, 6f, 46f, 18f);
        // SUIT: the lens, and its air while it is on the suit's own
        p.push(12f, 29f, 64f, 16f, new String[]{"SUIT ON"}, Yard.WHITE_LAMP, c.suited, YardPaint.Kind.INDICATOR, false, false);
        p.legend("AIR", 132f, 42f, ink);
        if (airShown(c)) p.drums(176f, 28f, YardText.clock(c.suitAir), 11f, 18f, 15f, false);
        else shutter(p, 176f, 28f, 46f, 18f);
        // HERE: the air where the captain stands
        int here = air(c);
        p.legend("HERE", 12f, 64f, ink);
        Kit.windows(p, 52f, 52f, 56f, 16f, AIR, here, here == 0 ? Yard.hex("E98A6A") : here == 1 ? Yard.hex("EFC56A") : Yard.hex("D8E2B4"));
        // CARRY
        p.legend("CARRY", 12f, 84f, ink);
        p.flaps(60f, 72f, c.carrying.isEmpty() ? "" : YardText.fit(c.carrying, 18), 18, 8.4f, 15f, 12f, Yard.FLAP_TEXT);
        if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.2f) {
            Kit.lens(p, 230f, 52f, 64f, 16f, "SMOKE", Yard.AMBER_LAMP, true);
        }
    }

    /** A closed shutter over drums that have nothing to say. */
    private static void shutter(YardPaint p, float x, float y, float w, float h) {
        p.rrect(x - 2f, y - 2f, w + 4f, h + 4f, 2f, Yard.FLAP_FRAME);
        p.vgrad(x, y, w, h, Yard.hex("4A4F33"), Yard.hex("353924"));
        for (float yy = y + 3f; yy < y + h - 1f; yy += 4f) p.rect(x + 1f, yy, w - 2f, 1f, Color.BLACK, 0.3f);
    }

    @Override
    public String hover(float px, float py) {
        CaptainStatus c = f.data.captainStatus;
        float ly = localY(py);
        if (ly < 26f) {
            if (!c.wound.isEmpty()) return "Wound: " + c.wound.toLowerCase(Locale.ROOT) + (Float.isNaN(c.bleedOut) ? "" : " · bleeding out");
            return null;
        }
        if (ly < 48f) {
            if (!c.suited) return "No suit on";
            if (air(c) == 2) return "Breathing ship air: the suit's own air is kept";
            return null;
        }
        if (ly < 70f) return c.room.isEmpty() ? "Outside" : c.room;
        return null;
    }
}
