package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.Locale;

/**
 * P3, the threat unit: the one interrupt for incoming fire, dropped from the rail at the top centre the
 * moment a missile is inbound. A red line at 24 units, the count and the soonest impact ("2 MISSILES
 * INBOUND · T-4 S"), and an amber line under it: the decoy, with its key reversed, and point defense
 * engaging. On deck there is no decoy to throw, so it is left out; at fire control and a gun the threat
 * list has every missile and point defense, so the second line keeps only the decoy.
 */
final class ThreatUnit extends YardPart {
    static final float W = 500f, H = 60f;

    private final ContextFrame f;

    ThreatUnit(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Missiles inbound now (indexed: the frame's own count walks the array with its shared iterator). */
    static int inbound(HudData d) {
        int n = 0;
        for (int i = 0; i < d.threatFrame.missiles.size; i++) if (d.threatFrame.missiles.get(i).side == Side.INCOMING) n++;
        return n;
    }

    /** The soonest impact among them, NaN when none is closing. */
    static float soonest(HudData d) {
        float best = Float.NaN;
        for (int i = 0; i < d.threatFrame.missiles.size; i++) {
            MissileTrack m = d.threatFrame.missiles.get(i);
            if (m.side != Side.INCOMING || Float.isNaN(m.timeToImpact)) continue;
            if (Float.isNaN(best) || m.timeToImpact < best) best = m.timeToImpact;
        }
        return best;
    }

    /** The first line: the count and the soonest impact, in at most 34 characters. */
    static String headline(int n, float impact) {
        String count = n == 1 ? "1 MISSILE INBOUND" : n + " MISSILES INBOUND";
        if (Float.isNaN(impact)) return count;
        return count + " · " + Kit.tMinus(impact).toUpperCase(Locale.ROOT);
    }

    /** The decoy's state: "DECOY READY", or "DECOY IN 2 S". */
    static String decoy(WeaponFrame w) {
        if (w.decoyCooldown > 0.05f) return "DECOY IN " + (int) Math.ceil(w.decoyCooldown) + " S";
        return "DECOY READY";
    }

    /** Point defense firing at one of them. */
    static boolean pdEngaging(WeaponFrame w) {
        if (w.pointDefenseActive) return true;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (m.kind == Kind.POINT_DEFENSE && m.engaging) return true;
        }
        return false;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Kit.rods(p, 0f, w, ContextLayout.RAIL_H - y, 0f);
        Kit.housing(p, 0f, 0f, w, h, false);
        // The red lip along its foot: the colour of what it carries
        p.rect(4f, h - 3f, w - 8f, 2.5f, Yard.RED);
        float tx = 8f, ty = 6f, tw = w - 16f, th = h - 14f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        int n = inbound(d);
        float soonest = soonest(d);
        String head = headline(n, soonest);
        float size = 24f;
        float hw = p.width(Face.TITLE, size, head);
        boolean flash = (f.time * 2f) % 1f < 0.75f || !Float.isNaN(soonest) && soonest > 10f;
        p.title(head, tx + (tw - hw) / 2f, ty + 22f, size, flash ? Yard.P_RED : Yard.hex("C9452E"));

        // The second line: the decoy at any seat but the deck, then point defense where nothing else says it
        boolean deck = f.deck();
        boolean pd = !deck && f.seat != ContextFrame.Seat.FIRE && f.seat != ContextFrame.Seat.GUN && pdEngaging(d.weaponFrame);
        String decoy = deck ? "" : decoy(d.weaponFrame);
        String key = Kit.key(d, "DECOY", "F");
        float s2 = 13f, base = ty + th - 7f;
        float lw = 0f;
        if (!decoy.isEmpty()) lw += p.width(Face.TUBE, s2, decoy + " ") + p.width(Face.TUBE_BOLD, s2, key) + 6f;
        if (pd) lw += p.width(Face.TUBE, s2, (decoy.isEmpty() ? "" : " · ") + "POINT DEFENSE ENGAGING");
        float lx = tx + (tw - lw) / 2f;
        if (!decoy.isEmpty()) {
            lx += p.tube(decoy + " ", lx, base, s2, Yard.P_AMBER, false);
            if (decoy.equals("DECOY READY")) lx += Kit.rev(p, key, lx + 3f, base, s2, 0f, Yard.P_AMBER);
            else lx += p.tube(key, lx, base, s2, Yard.P_DIM, true) + 6f;
        }
        if (pd) p.tube((decoy.isEmpty() ? "" : " · ") + "POINT DEFENSE ENGAGING", lx, base, s2, Yard.P_AMBER, false);
        p.tubeOver(tx, ty, tw, th);
    }
}
