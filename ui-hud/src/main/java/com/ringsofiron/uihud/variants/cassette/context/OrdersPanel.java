package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C1, the orders panel that swings up over the seat's instrument on Alt+O: every order the captain can
 * give, with its key and whether it can be given now. The helm's orders are interlocked keys, like an old
 * radio's presets: the one flying stays down, and Cancel releases it; the approach distance is on a
 * thumbwheel. The autopilot is a latching button with its HELD lens, the shields a latching button and
 * five interlocked trim keys, the crew's two standing orders latching buttons, and the ship's two guarded
 * buttons, their covers lifted while armed, the tow's price on drums over them. An order that can't be
 * given now keeps its place, its legend greyed and a barber tab beside it, and the reasons tube at the
 * foot gives each reason once, with the keys it bars in reversed cells.
 *
 * <p>The panel shows the orders; the game's own keys give them, as they do with the panel closed.
 */
final class OrdersPanel extends YardPart {
    static final float W = 620f, H = 244f;
    /** One line of the reasons tube: the reason, and the keys it bars. */
    static final class Reason {
        final String text;
        final List<String> keys = new ArrayList<>();

        Reason(String text) {
            this.text = text;
        }
    }

    private static final String[][] HELM = {
            {"FLY", "BURNS"}, {"MATCH", "VELOCITY"}, {"HOLD", "POSITION"}, {"APPROACH", "NAV"}, {"DOCK", "AT NAV"}, {"CANCEL", ""}
    };
    private static final String[] HELM_ACTIONS = {"HELM_FLY_BURNS", "HELM_MATCH_VELOCITY", "HELM_HOLD", "HELM_APPROACH", "HELM_DOCK", "HELM_CANCEL"};
    private static final String[] HELM_KEYS = {"1", "2", "3", "4", "5", "0"};
    private static final String[] HELM_WORDS = {"fly the planned burns", "match velocity with Nav", "hold position on Nav", "approach Nav", "dock at Nav", "cancel the helm's order"};
    private static final String[] TRIMS = {"EVEN", "BOW", "PORT", "STERN", "STBD"};
    private static final String[] TRIM_NAMES = {"even", "bow", "port", "stern", "starboard"};

    private static final float KEY_W = 60f, KEY_H = 40f, TRIM_W = 50f, TRIM_H = 32f;
    private static final float ROW_A = 26f, ROW_B = 110f, TUBE_Y = 170f, TUBE_H = 66f;

    private final ContextFrame f;
    boolean open;
    final List<Reason> reasons = new ArrayList<>();

    OrdersPanel(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The key the game gives an order on, as its cap reads. */
    private static String key(HudData d, String action, String fallback) {
        return Kit.key(d, action, fallback);
    }

    /** Why each order can't be given now, each reason once with the keys it bars (C1's reasons). */
    static List<Reason> reasons(HudData d, boolean emitter) {
        Map<String, Reason> by = new LinkedHashMap<>();
        Orders o = d.orderState;
        if (!o.seated) {
            Reason r = by.computeIfAbsent("Given from a seat", Reason::new);
            for (int i = 0; i < HELM_ACTIONS.length; i++) r.keys.add(key(d, HELM_ACTIONS[i], HELM_KEYS[i]));
            r.keys.add(key(d, "AUTOPILOT", "6"));
            if (emitter) {
                r.keys.add(key(d, "SHIELD_TRIM", "8"));
                r.keys.add(key(d, "SHIELDS", "9"));
            }
        } else {
            for (int i = 1; i < HelmOrder.values().length; i++) {
                String why = o.helmRefusal[i];
                if (why == null || why.isEmpty()) continue;
                by.computeIfAbsent(sentence(why), Reason::new).keys.add(key(d, HELM_ACTIONS[i - 1], HELM_KEYS[i - 1]));
            }
            if (!o.autopilotRefusal.isEmpty()) by.computeIfAbsent(sentence(o.autopilotRefusal), Reason::new).keys.add(key(d, "AUTOPILOT", "6"));
        }
        if (!emitter) {
            Reason r = by.computeIfAbsent("No shield emitter aboard", Reason::new);
            r.keys.add(key(d, "SHIELD_TRIM", "8"));
            r.keys.add(key(d, "SHIELDS", "9"));
        }
        if (!o.abandonRefusal.isEmpty()) by.computeIfAbsent(sentence(o.abandonRefusal), Reason::new).keys.add(key(d, "ABANDON", "P"));
        if (!o.towRefusal.isEmpty()) by.computeIfAbsent(sentence(o.towRefusal), Reason::new).keys.add(key(d, "CUT", "B"));
        return new ArrayList<>(by.values());
    }

    /** The characters a reason has on its line of the tube, before the keys it bars. */
    static final int REASON_CHARS = 56;

    /**
     * A refusal as a line of the tube: its first letter up, no full stop, within {@link #REASON_CHARS}. One
     * too long gives up its aside in brackets first (the keys that would fix it, which the barred order's
     * hover still gives in full), so the reason itself reads whole.
     */
    static String sentence(String s) {
        String t = s.trim();
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        if (t.length() > REASON_CHARS) {
            int aside = t.indexOf(" (");
            if (aside > 0) t = t.substring(0, aside);
        }
        if (!t.isEmpty()) t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return YardText.cut(t, REASON_CHARS);
    }

    float measure(HudData d) {
        if (!open) return 0f;
        reasons.clear();
        reasons.addAll(reasons(d, f.emitter));
        return H;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Orders o = d.orderState;
        Kit.pod(p, 0f, 0f, w, h);
        p.face(6f, 9f, w - 12f, TUBE_Y - 16f, Yard.Finish.YARD, false);
        boolean seated = o.seated;
        // HELM: the interlocked keys
        p.legend("HELM", 14f, 20f, Yard.INK);
        for (int i = 0; i < HELM.length; i++) {
            float kx = 14f + i * (KEY_W + 4f);
            HelmOrder order = i < 5 ? HelmOrder.values()[i + 1] : HelmOrder.NONE;
            boolean down = i < 5 && o.helm == order;
            boolean barred = !seated || i < 5 && !o.helmRefusal[i + 1].isEmpty();
            preset(p, kx, ROW_A, KEY_W, KEY_H, HELM[i], down, barred);
            cap(p, kx + KEY_W / 2f, ROW_A + KEY_H + 4f, key(d, HELM_ACTIONS[i], HELM_KEYS[i]));
        }
        // The approach distance on its thumbwheel
        float wx = 14f + 6f * (KEY_W + 4f) + 2f;
        Kit.legendC(p, "DIST", wx + 20f, 20f, Yard.INK);
        p.rrect(wx - 2f, ROW_A - 2f, 44f, KEY_H + 4f, 3f, Yard.BAKELITE);
        float dist = o.approachDistance;
        String dt = Float.isNaN(dist) ? "   " : dist >= 1000f ? String.format(Locale.ROOT, "%.0f", dist / 1000f) : String.valueOf(Math.round(dist));
        p.drums(wx + 2f, ROW_A + 8f, dt.length() < 3 ? " ".repeat(3 - dt.length()) + dt : dt, 12f, 22f, 15f, false);
        Kit.legendC(p, !Float.isNaN(dist) && dist >= 1000f ? "KM" : "M", wx + 20f, ROW_A + KEY_H + 15f, Yard.INK);
        // AUTOPILOT: a latching button and its HELD lens
        float ax = 452f;
        Kit.legendC(p, "AUTOPILOT", ax + 37f, 20f, Yard.INK);
        boolean apBarred = !seated || !o.autopilotRefusal.isEmpty() && o.autopilot == Autopilot.OFF;
        p.push(ax, ROW_A, 74f, KEY_H, new String[]{"TO", "NAV"}, Yard.WHITE_LAMP, o.autopilot != Autopilot.OFF, YardPaint.Kind.ORDER, apBarred, false);
        cap(p, ax + 8f, ROW_A + KEY_H + 4f, key(d, "AUTOPILOT", "6"));
        Kit.lens(p, ax + 24f, ROW_A + KEY_H + 5f, 50f, 13f, "HELD", Yard.AMBER_LAMP, o.autopilot == Autopilot.HELD);
        // SHIELDS: raised or lowered
        float sx = 534f;
        boolean shBarred = !seated || !f.emitter;
        Kit.legendC(p, "SHIELDS", sx + 36f, 20f, Yard.INK);
        p.push(sx, ROW_A, 72f, KEY_H, new String[]{"RAISED"}, Yard.WHITE_LAMP, f.emitter && d.shieldStatus.raised, YardPaint.Kind.ORDER, shBarred, false);
        cap(p, sx + 36f, ROW_A + KEY_H + 4f, key(d, "SHIELDS", "9"));

        // TRIM: five interlocked keys, the one it leans to down
        p.legend("TRIM", 14f, 104f, Yard.INK);
        p.key(46f, 92f, key(d, "SHIELD_TRIM", "8"));
        String trim = d.shieldStatus.trim == null ? "even" : d.shieldStatus.trim.toLowerCase(Locale.ROOT);
        for (int i = 0; i < TRIMS.length; i++) {
            float tx = 14f + i * (TRIM_W + 3f);
            preset(p, tx, ROW_B, TRIM_W, TRIM_H, new String[]{TRIMS[i]}, f.emitter && trim.equals(TRIM_NAMES[i]), shBarred);
        }
        // CREW: two latching buttons; the crew window gives them
        float cx = 280f;
        p.legend("CREW", cx, 104f, Yard.INK);
        p.push(cx, ROW_B, 92f, TRIM_H, new String[]{"SUITS AT", "BATTLE STNS"}, Yard.WHITE_LAMP, o.suitsAtBattleStations, YardPaint.Kind.ORDER, false, false);
        p.push(cx + 98f, ROW_B, 92f, TRIM_H, new String[]{"HOLD CREW", "IN FROM EVA"}, Yard.WHITE_LAMP, o.evaHeld, YardPaint.Kind.ORDER, false, false);
        float ix = cx;
        ix += p.key(ix, ROW_B + TRIM_H + 4f, key(d, "CREW", "I")) + 5f;
        p.legend("IN THE CREW WINDOW", ix, ROW_B + TRIM_H + 16f, Kit.DIM_INK);
        // SHIP: the guarded buttons, and the tow's price over its button
        float gx = 480f;
        p.legend("SHIP", gx, 104f, Yard.INK);
        boolean towBarred = !o.towRefusal.isEmpty();
        if (!towBarred && o.towFee > 0) {
            String fee = String.valueOf(Math.min(99999L, Math.round(o.towFee)));
            float fw = YardPaint.drumWidth(fee, 9f);
            p.drums(588f - fw, 92f, fee, 9f, 14f, 12f, false);
            p.legend("CR", 591f, 104f, Yard.INK);
        }
        guarded(p, gx, ROW_B, 60f, TRIM_H, new String[]{"ABANDON", "SHIP"}, o.abandonArmed > 0f || o.abandoning, !o.abandonRefusal.isEmpty() && !o.abandoning);
        guarded(p, gx + 66f, ROW_B, 60f, TRIM_H, new String[]{"CALL", "A TOW"}, o.towArmed > 0f, towBarred);
        String pk = key(d, "ABANDON", "P"), bk = key(d, "CUT", "B");
        float kx = gx + 10f;
        kx += p.key(kx, ROW_B + TRIM_H + 4f, pk) + 2f;
        p.key(kx, ROW_B + TRIM_H + 4f, pk);
        kx = gx + 76f;
        kx += p.key(kx, ROW_B + TRIM_H + 4f, bk) + 2f;
        p.key(kx, ROW_B + TRIM_H + 4f, bk);

        // The reasons tube
        float tx = 10f, tw = w - 20f;
        p.tube(tx, TUBE_Y, tw, TUBE_H, 3f, 2f, false);
        if (reasons.isEmpty()) p.tube("Every order can be given now.", tx + 10f, TUBE_Y + 18f, 13f, Yard.P_DIM, false);
        for (int i = 0; i < reasons.size() && i < 3; i++) {
            Reason r = reasons.get(i);
            float base = TUBE_Y + 18f + i * 20f;
            float lx = tx + 10f;
            boolean last = i == 2 && reasons.size() > 3;
            String text = last ? "+" + (reasons.size() - 2) + " more reasons: hover a barred order" : r.text;
            lx += p.tube(text, lx, base, 13f, Yard.P_AMBER, false) + 10f;
            if (last) continue;
            for (int k = 0; k < r.keys.size(); k++) lx += Kit.rev(p, r.keys.get(k), lx, base, 12.5f, 0f, Yard.P_AMBER) + 4f;
        }
        p.tubeOver(tx, TUBE_Y, tw, TUBE_H);
    }

    /** One interlocked key: an ivory cap in its well, lower and lit along its top while its order runs. */
    private static void preset(YardPaint p, float x, float y, float w, float h, String[] lines, boolean down, boolean barred) {
        p.rrect(x - 2f, y - 2f, w + 4f, h + 4f, 2.5f, Yard.BAKELITE);
        float dy = down ? 2.5f : 0f, ch = h - 3f;
        Color top = down ? Yard.hex("C8BEA2") : Yard.ENAMEL_TOP, bottom = down ? Yard.hex("B0A588") : Yard.ENAMEL_BOTTOM;
        p.vgrad(x, y + dy, w, ch, top, bottom);
        if (!down) p.rect(x, y + ch, w, 3f, Yard.ENAMEL_LO);
        p.rect(x + 2f, y + dy + 0.8f, w - 4f, 1f, Color.WHITE, down ? 0.15f : 0.4f);
        if (down) {
            p.glowRect(x + 5f, y + dy + 3f, w - 10f, 3f, 4f, Yard.WHITE_LAMP.on, 0.5f);
            p.rect(x + 5f, y + dy + 3f, w - 10f, 3f, Yard.WHITE_LAMP.on);
        }
        Color ink = barred ? Yard.hex("9A9079") : Yard.INK;
        float rightPad = barred ? 8f : 0f;
        int n = lines.length > 1 && !lines[1].isEmpty() ? 2 : 1;
        for (int i = 0; i < n; i++) {
            float base = y + dy + ch / 2f + (i - (n - 1) / 2f) * 12.5f + 6f;
            float tw = p.width(Face.LEGEND, 12f, lines[i]);
            float size = tw > w - 6f - rightPad ? 12f * (w - 6f - rightPad) / tw : 12f;
            p.textC(Face.LEGEND, Math.max(10f, size), lines[i], x + (w - rightPad) / 2f, base, ink);
        }
        if (barred) p.barberTab(x + w - 6f, y + 3f, 4f, h - 6f);
    }

    /** A guarded push button: its clear red cover down over it, or lifted while the order is armed. */
    private static void guarded(YardPaint p, float x, float y, float w, float h, String[] lines, boolean armed, boolean barred) {
        p.push(x, y, w, h, lines, Yard.RED_LAMP, armed, YardPaint.Kind.ORDER, barred, armed);
        if (armed) {
            // The cover, lifted on its hinge along the top
            p.rrect(x - 2f, y - 9f, w + 4f, 6f, 2f, Yard.RED, 0.9f);
            p.rect(x - 2f, y - 4f, w + 4f, 1.5f, Yard.hex("7A221B"));
        } else {
            p.rrect(x - 3f, y - 3f, w + 6f, h + 6f, 3f, Yard.RED, 0.42f);
            p.glass(x - 3f, y - 3f, w + 6f, h + 6f);
            p.rect(x - 3f, y - 3f, w + 6f, 2f, Yard.hex("7A221B"), 0.9f);
        }
    }

    /** A key cap centred under a control. */
    private static void cap(YardPaint p, float cx, float y, String name) {
        p.key(cx - p.keyWidth(name) / 2f, y, name);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        Orders o = d.orderState;
        float lx = localX(px), ly = localY(py);
        if (ly >= ROW_A && ly < ROW_A + KEY_H + 20f) {
            int i = (int) ((lx - 14f) / (KEY_W + 4f));
            if (lx >= 14f && i >= 0 && i < HELM.length) {
                String k = key(d, HELM_ACTIONS[i], HELM_KEYS[i]);
                if (!o.seated) return "From a seat: " + k + " to " + HELM_WORDS[i];
                String why = i < 5 ? o.helmRefusal[i + 1] : "";
                if (!why.isEmpty()) return why;
                if (i == 3) return k + " to approach Nav · again steps the distance";
                return k + " to " + HELM_WORDS[i];
            }
            if (lx >= 452f && lx < 526f) {
                if (o.autopilot == Autopilot.HELD && !o.autopilotHeld.isEmpty()) return "Held: " + o.autopilotHeld;
                return !o.autopilotRefusal.isEmpty() ? o.autopilotRefusal : key(d, "AUTOPILOT", "6") + " to fly to Nav by autopilot";
            }
            if (lx >= 534f) return f.emitter ? key(d, "SHIELDS", "9") + " raises or lowers the shields" : "No shield emitter aboard";
        }
        if (ly >= ROW_B - 20f && ly < TUBE_Y) {
            if (lx < 270f) return f.emitter ? key(d, "SHIELD_TRIM", "8") + " steps the trim: even, bow, port, stern, starboard" : "No shield emitter aboard";
            if (lx < 470f) return "Standing orders for the crew: set them in the crew window (" + key(d, "CREW", "I") + ")";
            if (lx < 543f) return !o.abandonRefusal.isEmpty() ? o.abandonRefusal : key(d, "ABANDON", "P") + " twice within 3 s orders the crew to the pods";
            return !o.towRefusal.isEmpty() ? o.towRefusal : key(d, "CUT", "B") + " twice within 3 s calls a tow for " + YardText.thousands(o.towFee) + " cr";
        }
        return null;
    }
}
