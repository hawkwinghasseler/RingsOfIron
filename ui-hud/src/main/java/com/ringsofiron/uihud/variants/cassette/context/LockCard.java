package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.List;
import java.util.Locale;

/**
 * H6 and F1, the lock card on the right rail: what is locked and whether a shot will land. An enamel
 * plate with LOCK and the name on flaps, its state in one word on flaps, FIRING ON YOU as a red lens of
 * its own, the range and the closing rate on drums. At the helm (H6) the best gun's chance to hit is a
 * number and three printed windows, POOR FAIR GOOD, the word lit; at fire control and a gun (F1) each
 * gun's chance is the rack's, so the card gives the speed relative to the ship instead, and a two-line
 * tube for what the target is doing and what the sensors can tell of its condition.
 */
final class LockCard extends YardPart {
    static final float W = 308f, H_COMPACT = 110f, H_FULL = 140f;
    /** A shot at or over this is GOOD, under {@link #FAIR} POOR (12.3's three words). */
    static final float GOOD = 0.7f, FAIR = 0.3f;
    static final String[] HIT_WORDS = {"POOR", "FAIR", "GOOD"};

    private final ContextFrame f;
    boolean full;

    LockCard(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    float measure(boolean full) {
        this.full = full;
        return full ? H_FULL : H_COMPACT;
    }

    /**
     * The lock's state in one word (H6): WRECK, SURRENDERED, SILENCED, DISABLED, FLEEING, or its side,
     * from the contact's state where the sensors give one and the marker's word where they don't.
     */
    static String state(TargetStatus t, Contact c) {
        String s = t.state == null ? "" : t.state.toLowerCase(Locale.ROOT);
        if (c != null) {
            if (c.state == ContactProvider.State.WRECKED || c.kind == ContactProvider.Kind.DERELICT) return "WRECK";
            switch (c.state) {
                case STRUCK: return "SURRENDERED";
                case SILENCED: return "SILENCED";
                case DISABLED: return "DISABLED";
                case RUNNING: return "FLEEING";
                default: break;
            }
        }
        if (s.equals("wrecked")) return "WRECK";
        if (s.equals("struck")) return "SURRENDERED";
        if (s.equals("silenced")) return "SILENCED";
        if (s.equals("running")) return "FLEEING";
        if (!s.isEmpty() && !s.equals("fit")) return "DISABLED";
        switch (t.relation) {
            case HOSTILE: return "HOSTILE";
            case FRIENDLY: return "FRIENDLY";
            case MISSILE: return "MISSILE";
            default: return "NEUTRAL";
        }
    }

    /** The best chance to hit among the guns (not launchers, not point defense), NaN with none. */
    static float bestHit(HudData d) {
        float best = Float.NaN;
        for (int i = 0; i < d.weaponFrame.mounts.size; i++) {
            Mount m = d.weaponFrame.mounts.get(i);
            if (m.kind == Kind.LAUNCHER || m.kind == Kind.POINT_DEFENSE || Float.isNaN(m.hitChance) || !m.working()) continue;
            if (Float.isNaN(best) || m.hitChance > best) best = m.hitChance;
        }
        return best;
    }

    /** Which of POOR, FAIR, GOOD a chance is: 0, 1 or 2; -1 with none. */
    static int hitWord(float chance) {
        if (Float.isNaN(chance)) return -1;
        return chance >= GOOD ? 2 : chance >= FAIR ? 1 : 0;
    }

    /** What the target is doing, as far as the sensors show it (F1's first tube line). */
    static String doing(TargetStatus t, String state) {
        switch (state) {
            case "WRECK": return "Wrecked";
            case "SURRENDERED": return "Surrendered: hold your fire";
            case "SILENCED": return "Silenced: hold your fire";
            case "DISABLED": return "Disabled" + reason(t.state);
            case "FLEEING": return "Breaking off" + (Float.isNaN(t.closing) || t.closing >= 0f ? "" : ", opening " + Kit.speed(t.closing));
            default: break;
        }
        if (Float.isNaN(t.closing)) return t.relation == Relation.HOSTILE ? "Hostile" : "Under way";
        if (t.closing > 1f) return "Closing the range, " + Kit.speed(t.closing);
        if (t.closing < -1f) return "Opening the range, " + Kit.speed(t.closing);
        return "Holding the range";
    }

    private static String reason(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("fit")) return "";
        return ": " + s.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /** Its condition as the sensors can tell it (F1's second line): the hull in four bands, a stale track. */
    static String condition(TargetStatus t) {
        String hull;
        if (Float.isNaN(t.hull)) hull = "Hull: sensors can't tell";
        else if (t.hull >= 0.9f) hull = "Hull intact";
        else if (t.hull >= 0.6f) hull = "Hull damaged";
        else if (t.hull >= 0.3f) hull = "Hull heavily damaged";
        else hull = "Hull crippled";
        if (!Float.isNaN(t.trackAge) && t.trackAge > 5f) hull += " · track " + Math.round(t.trackAge) + " s old";
        return hull;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        TargetStatus t = d.targetStatus;
        Contact c = d.contactFrame.locked();
        Kit.slideRail(p, f.screenW - x, false, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        p.face(6f, 6f, w - 12f, h - 12f, Yard.Finish.YARD, false);
        Color ink = Yard.INK;
        String st = state(t, c);
        boolean struck = st.equals("SURRENDERED") || st.equals("SILENCED");
        // The name and its state
        p.legend("LOCK", 16f, 26f, ink);
        p.flaps(54f, 12f, YardText.fit(t.label, 15), 15, 8.6f, 18f, 13f, Yard.FLAP_TEXT);
        p.flaps(190f, 12f, st, 11, 8.6f, 18f, 13f, struck || st.equals("FLEEING") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        // The range and the rate, each on drums ending at a fixed edge so the units stay put
        p.legend("RANGE", 16f, 53f, ink);
        p.drumsR(118f, 38f, YardText.km(t.range).trim(), 12f, 19f, 15f, 60f, false);
        p.legend("KM", 122f, 53f, ink);
        String sense = Float.isNaN(t.closing) ? "" : Math.abs(t.closing) < 0.5f ? "HOLDING" : t.closing > 0f ? "CLOSING" : "OPENING";
        p.flaps(148f, 39f, sense, 7, 8.4f, 17f, 13f, Yard.FLAP_TEXT);
        String rate = Float.isNaN(t.closing) ? "    " : YardText.count(Math.abs(t.closing), 4);
        p.drumsR(258f, 38f, rate.trim().isEmpty() ? "  " : rate.trim(), 12f, 19f, 15f, 48f, false);
        p.legend("M/S", 262f, 53f, ink);
        // FIRING ON YOU, a lens of its own
        p.severityLens(16f, 64f, 136f, 18f, "FIRING ON YOU", Yard.RED_LAMP, t.firingOnUs, true, false);
        if (!full) {
            hit(p, d);
            return;
        }
        // F1: the speed against the ship, then what it is doing and its condition
        p.legend("REL", 164f, 78f, ink);
        p.drumsR(246f, 63f, Float.isNaN(t.relativeSpeed) ? "  " : YardText.count(t.relativeSpeed, 4).trim(), 12f, 19f, 15f, 48f, false);
        p.legend("M/S", 250f, 78f, ink);
        float tx = 12f, ty = 90f, tw = w - 24f, th = 38f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        int budget = Kit.chars(p, tw - 16f, 13f);
        p.tube(YardText.cut(doing(t, st), budget), tx + 8f, ty + 16f, 13f, Yard.P_AMBER, false);
        p.tube(YardText.cut(condition(t), budget), tx + 8f, ty + 32f, 13f, Yard.P_DIM, false);
        p.tubeOver(tx, ty, tw, th);
    }

    /** H6's foot: the best gun's chance as a number, and the windows with its word lit. */
    private void hit(YardPaint p, HudData d) {
        float best = bestHit(d);
        int word = hitWord(best);
        p.legend("HIT", 164f, 78f, Yard.INK);
        p.drumsR(220f, 63f, Float.isNaN(best) ? "  " : String.valueOf(Math.round(best * 100f)), 12f, 19f, 15f, 36f, false);
        p.legend("%", 224f, 78f, Yard.INK);
        Kit.windows(p, 16f, 88f, 92f, 14f, HIT_WORDS, word, Yard.hex("E8E0C8"));
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        float lx = localX(px), ly = localY(py);
        String st = state(d.targetStatus, d.contactFrame.locked());
        if (ly < 34f && lx >= 186f) {
            if (st.equals("SURRENDERED")) return "Surrendered: firing on it lifts the surrender";
            if (st.equals("SILENCED")) return "Silenced: firing on it starts the fight again";
            return null;
        }
        if (ly >= 60f && ly < 86f && lx < 156f) return d.targetStatus.firingOnUs ? "It has you locked and its guns on you" : "Lights while it fires on you";
        if (!full && ly >= 60f && lx >= 156f) return Float.isNaN(bestHit(d)) ? "No gun has a solution on it" : "The best gun's chance to hit while it dodges";
        if (!full && ly >= 86f) return "The best gun's chance: poor under 30%, good from 70%";
        return null;
    }

    /** The tube lines F1 would print, for the text-budget tests. */
    static List<String> lines(TargetStatus t, Contact c) {
        String st = state(t, c);
        return List.of(doing(t, st), condition(t));
    }
}
