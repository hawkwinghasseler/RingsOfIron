package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * X3, the emergency placard hanging from the rail in hazard stripes: the ship needs saving, or the
 * captain does. Its headline is on a red tube at the threat unit's size; under it the way out. Disabled,
 * the two ways out are guarded switches (lifting the guard is the first press of P or B, throwing it the
 * second within 3 s), the tow's price on drums; abandoning, the pods and who is in them; the captain's
 * suit air under four minutes leads when it holds. At most two states hold, the suit air and one other:
 * the first in full, the second as one line with its keys.
 */
final class EmergencyPlacard extends YardPart {
    enum State {
        NONE, SUIT, DISABLED, ABANDONING
    }

    static final float W = 500f, HEAD = 46f, SWITCHES = 84f, PODS = 52f, SUIT_BODY = 26f, LINE = 22f;

    private final ContextFrame f;
    State first = State.NONE, second = State.NONE;
    /** The switches fit: the full disabled body, else its one-line form. */
    private boolean roomy;

    EmergencyPlacard(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The states that hold this frame, and the height in {@code room}; 0 with none. */
    float measure(float room) {
        HudData d = f.data;
        boolean abandoning = d.orderState.abandoning || d.ctx.abandoning;
        State ship = abandoning ? State.ABANDONING : f.disabled ? State.DISABLED : State.NONE;
        first = f.suitCritical ? State.SUIT : ship;
        second = f.suitCritical ? ship : State.NONE;
        if (first == State.NONE) return 0f;
        float full = HEAD + body(first, true) + (second == State.NONE ? 0f : LINE + 4f) + 10f;
        roomy = full <= room;
        if (roomy) return full;
        return Math.min(room, HEAD + body(first, false) + (second == State.NONE ? 0f : LINE + 4f) + 10f);
    }

    private static float body(State s, boolean roomy) {
        switch (s) {
            case SUIT: return SUIT_BODY;
            case DISABLED: return roomy ? SWITCHES : LINE + 6f;
            case ABANDONING: return PODS;
            default: return 0f;
        }
    }

    /** The headline for a state, at most 34 characters. */
    static String headline(State s, HudData d, ContextFrame f) {
        switch (s) {
            case SUIT: return "YOUR SUIT AIR " + YardText.clock(d.captainStatus.suitAir);
            case DISABLED: return YardText.cut("SHIP DISABLED: " + reason(d.ctx.disabledReason), 34);
            case ABANDONING: return "ABANDONING SHIP";
            default: return "";
        }
    }

    /** The disablement's reason in plain words: "no power". */
    static String reason(String r) {
        if (r == null || r.isEmpty()) return "dead in space";
        return r.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /** The second state's one line with its keys: "SHIP DISABLED · P twice · B twice". */
    static String oneLine(State s, HudData d) {
        String p = Kit.key(d, "ABANDON", "P"), b = Kit.key(d, "CUT", "B");
        switch (s) {
            case DISABLED: {
                Orders o = d.orderState;
                String tow = o.towRefusal.isEmpty() ? " · " + b + (o.towArmed > 0f ? " again: tow" : " twice: tow") : "";
                return "SHIP DISABLED · " + p + (o.abandonArmed > 0f ? " again: abandon" : " twice: abandon") + tow;
            }
            case ABANDONING: return "ABANDONING SHIP · " + p + " launches the pods now";
            default: return "";
        }
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Kit.rods(p, 0f, w, ContextLayout.RAIL_H - y, 0f);
        // The hazard-striped frame and its dark plate
        p.rrect(1.5f, 3f, w, h, 4f, Yard.SHADOW, 0.7f);
        p.hazard(0f, 0f, w, h);
        p.shade(7f, 7f, w - 14f, h - 14f, 3f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        // The headline, on a red tube
        float tx = 14f, ty = 12f, tw = w - 28f, th = 30f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        p.tube(headline(first, d, f), tx + 10f, ty + 24f, 22f, Yard.P_RED, true);
        p.tubeOver(tx, ty, tw, th);
        float y = HEAD + 2f;
        switch (first) {
            case SUIT: suit(p, d, y); break;
            case DISABLED: if (roomy) switches(p, d, y); else keysLine(p, d, y); break;
            case ABANDONING: pods(p, d, y); break;
            default: break;
        }
        if (second != State.NONE) {
            float ly = h - 10f - LINE;
            p.tube(tx, ly, tw, LINE - 2f, 2f, 2f, false);
            Kit.tubeFit(p, oneLine(second, d), tx + 10f, ly + 15f, 13f, Yard.P_AMBER, tw - 20f);
            p.tubeOver(tx, ly, tw, LINE - 2f);
        }
    }

    /** The suit air's line: where to breathe. The game can't yet say where the nearest air is. */
    private void suit(YardPaint p, HudData d, float y) {
        CaptainStatus c = d.captainStatus;
        String where = c.room.isEmpty() ? "Get back aboard, or to a suit locker" : "Find air: a room that holds it, or a suit locker";
        p.text(Face.LEGEND, 13f, where.toUpperCase(Locale.ROOT), 18f, y + 15f, Yard.INK_LIGHT);
    }

    /** The two ways out on guarded switches, the tow's price on drums. */
    private void switches(YardPaint p, HudData d, float y) {
        Orders o = d.orderState;
        String pk = Kit.key(d, "ABANDON", "P"), bk = Kit.key(d, "CUT", "B");
        // ABANDON SHIP: the guard lifts on the first press, the switch throws on the second
        float sx = 52f, sy = y + 50f;
        boolean abArmed = o.abandonArmed > 0f, towArmed = o.towArmed > 0f;
        boolean abBarred = !o.abandonRefusal.isEmpty(), towBarred = !o.towRefusal.isEmpty();
        p.toggle(sx, sy, false, true, abArmed);
        Color ink = Yard.INK_LIGHT;
        p.legend("ABANDON SHIP", sx + 24f, y + 22f, abBarred ? Kit.DIM_INK : ink);
        float kx = sx + 24f;
        kx += p.key(kx, y + 30f, pk) + 3f;
        kx += p.key(kx, y + 30f, pk) + 6f;
        p.legend(abArmed ? "ARMED" : "TWICE", kx, y + 42f, abArmed ? Yard.hex("FFB23F") : Kit.DIM_INK);
        if (abBarred) p.barberTab(sx + 24f, y + 52f, 100f, 6f);
        // CALL A TOW, with its price
        float tx = 290f;
        p.toggle(tx, sy, false, true, towArmed);
        p.legend("CALL A TOW", tx + 24f, y + 22f, towBarred ? Kit.DIM_INK : ink);
        kx = tx + 24f;
        kx += p.key(kx, y + 30f, bk) + 3f;
        kx += p.key(kx, y + 30f, bk) + 6f;
        p.legend(towArmed ? "ARMED" : "TWICE", kx, y + 42f, towArmed ? Yard.hex("FFB23F") : Kit.DIM_INK);
        if (towBarred) {
            p.barberTab(tx + 24f, y + 52f, 100f, 6f);
            String why = YardText.cut(o.towRefusal, 24);
            p.text(Face.LEGEND, 12f, why, tx + 24f, y + 74f, Kit.DIM_INK);
        } else {
            float dw = p.drums(tx + 24f, y + 56f, String.valueOf(Math.round(o.towFee)), 11f, 18f, 15f, false);
            p.legend("CR", tx + 24f + dw + 4f, y + 70f, ink);
            if (o.towStanding > 0f) p.legend("· " + Math.round(o.towStanding) + " STANDING", tx + 24f + dw + 24f, y + 70f, ink);
        }
    }

    /** No room for the switches (under the threat unit): the ways out as one line. */
    private void keysLine(YardPaint p, HudData d, float y) {
        float tx = 14f, tw = w - 28f;
        p.tube(tx, y, tw, LINE, 2f, 2f, false);
        Kit.tubeFit(p, keysLine(d), tx + 10f, y + 16f, 13f, Yard.P_AMBER, tw - 20f);
        p.tubeOver(tx, y, tw, LINE);
    }

    /**
     * The ways out in words: "P twice: abandon ship · B twice: tow, 4,200 cr", "P again" once the first
     * press has lifted the guard. A way that is shut says why in the game's words, which name the way out
     * it leaves ("Docked: walk off instead"), and no tug coming says why ("no tow: missiles are inbound").
     */
    static String keysLine(HudData d) {
        String pk = Kit.key(d, "ABANDON", "P"), bk = Kit.key(d, "CUT", "B");
        Orders o = d.orderState;
        String abandon = !o.abandonRefusal.isEmpty() ? upperFirst(clause(o.abandonRefusal))
                : pk + (o.abandonArmed > 0f ? " again: abandon ship" : " twice: abandon ship");
        String tow = !o.towRefusal.isEmpty() ? "no tow: " + clause(o.towRefusal)
                : bk + (o.towArmed > 0f ? " again: tow, " : " twice: tow, ") + YardText.thousands(o.towFee) + " cr";
        return abandon + " · " + tow;
    }

    private static String upperFirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A refusal's first clause, lower case: "missiles are inbound; no tug will come into that" is "missiles are inbound". */
    static String clause(String why) {
        String t = why.trim();
        int stop = t.indexOf(';');
        if (stop > 0) t = t.substring(0, stop);
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t.isEmpty() ? t : Character.toLowerCase(t.charAt(0)) + t.substring(1);
    }

    /** Abandoning: who is in the pods, and when they go. */
    private void pods(YardPaint p, HudData d, float y) {
        Orders o = d.orderState;
        Color ink = Yard.INK_LIGHT;
        p.legend("IN THE PODS", 18f, y + 16f, ink);
        float dw = p.drums(104f, y + 2f, String.valueOf(Math.min(99, o.inPods)), 11f, 18f, 15f, false);
        p.legend("OF " + o.crewAboard, 104f + dw + 5f, y + 16f, ink);
        Kit.lens(p, 200f, y + 3f, 90f, 15f, "YOU'RE IN", Yard.GREEN_LAMP, o.captainInPod);
        if (!Float.isNaN(o.podsLaunchIn)) {
            p.legend("LAUNCH IN", 310f, y + 16f, ink);
            p.drums(374f, y + 2f, YardText.clock(o.podsLaunchIn), 11f, 18f, 15f, false);
        }
        String pk = Kit.key(d, "ABANDON", "P");
        float kx = 18f;
        kx += p.key(kx, y + 28f, pk) + 6f;
        p.legend("LAUNCHES THE PODS NOW", kx, y + 40f, ink);
        if (!o.captainInPod && !Float.isNaN(o.podDistance)) {
            p.legend("NEAREST SEAT " + Math.round(o.podDistance) + " M", 290f, y + 40f, Kit.DIM_INK);
        }
    }

    @Override
    public String hover(float px, float py) {
        if (first == State.DISABLED && roomy) {
            Orders o = f.data.orderState;
            float lx = localX(px);
            if (lx < 280f) return o.abandonRefusal.isEmpty() ? "The crew take to the pods: the ship is lost" : o.abandonRefusal;
            return o.towRefusal.isEmpty() ? "A tug tows you home for the fee and some standing" : o.towRefusal;
        }
        return null;
    }
}
