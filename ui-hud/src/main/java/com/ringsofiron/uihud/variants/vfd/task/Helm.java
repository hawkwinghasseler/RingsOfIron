package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * HLM, the helm status in the bottom middle (functional-task 6, HLM): a slab of two rows, HELM and who
 * has it (you, a helmsman by name, the autopilot, or nobody), then the standing order with its state in
 * a window as wide as the slab: "MATCHING VELOCITY WITH VEX", "AUTOPILOT TO SHACKLETON ICE, HOLDING:
 * RAIDER NEAR", "APPROACH HIGHPORT, WAITING: NO HELMSMAN". One longer than its window marquees, so the
 * reason a waiting order gives is read whole. A planned burn is flown whatever the order,
 * so while one is flown it says so. On deck in FLT's place and at gunnery beside the weapons pod
 * whenever the ship is undocked; at the helm over FLT while an order or the autopilot stands. A click
 * lists the orders with their number keys, each dimmed with why it can't be given now.
 */
final class Helm {
    static final int CLICK = 50, CLICK_LIST = 51;
    static final Action[] ACTIONS = {Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH,
            Action.HELM_DOCK, Action.HELM_CANCEL};
    static final HelmOrder[] ORDERS = {HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH,
            HelmOrder.DOCK, HelmOrder.NONE};
    static final String[] WORDS = {"FLY THE BURNS", "MATCH VELOCITY", "HOLD POSITION", "APPROACH", "DOCK", "CANCEL"};
    static final float LIST_ROW = 18f;

    boolean on, open;
    String who = "", order = "";
    /** HUD time the order line last changed, for its marquee. */
    float since;
    /** Where it sits this frame, on the bottom middle grid. */
    float x = Geo.FLT_X, y = Geo.HLM_Y, w = Geo.FLT_W;
    final String[] keys = new String[ACTIONS.length], why = new String[ACTIONS.length];
    final boolean[] running = new boolean[ACTIONS.length];

    void update(HudData d, Posts p, boolean flight, float now) {
        Orders o = d.orderState;
        NavReadout n = d.navReadout;
        boolean stands = o.helm != HelmOrder.NONE || o.autopilot != Autopilot.OFF || d.ctx.burnFlown || n.burning;
        on = p.helm(stands) && d.ctx.ship != null;
        if (!on) {
            open = false;
            return;
        }
        who = who(d.ctx.helmsman);
        String line = line(d);
        if (!line.equals(order)) {
            order = line;
            since = now;
        }
        // At gunnery beside the weapons pod; at the helm over FLT; on deck in FLT's place
        if (p.post == Posts.Post.GUNNERY) {
            x = Geo.HLM_GUN_X;
            w = Geo.HLM_GUN_W;
            y = Geo.HLM_Y;
        } else {
            x = Geo.FLT_X;
            w = Geo.FLT_W;
            y = flight ? Geo.FLT_Y - 12f - Geo.HLM_H : Geo.HLM_Y;
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            keys[i] = Catalogue.key(d, ACTIONS[i].name(), ACTIONS[i].defaultKeyName());
            String r = o.helmRefusal[ORDERS[i].ordinal()];
            why[i] = r == null || r.isEmpty() ? "" : refusal(r);
            running[i] = ORDERS[i] != HelmOrder.NONE && o.helm == ORDERS[i];
        }
    }

    /** Who has the helm: "YOU", a helmsman's name, "AUTOPILOT", or "NOBODY". */
    static String who(String helmsman) {
        if (helmsman == null || helmsman.isEmpty()) return "NOBODY";
        if ("you".equalsIgnoreCase(helmsman)) return "YOU";
        return Fmt.name(helmsman, 12);
    }

    /** The standing order and its state, in the order the ship follows them. */
    static String line(HudData d) {
        Orders o = d.orderState;
        NavReadout n = d.navReadout;
        if (o.autopilot != Autopilot.OFF) {
            String to = o.autopilotTo.isEmpty() ? "AUTOPILOT ON" : "AUTOPILOT TO " + up(o.autopilotTo);
            return o.autopilot == Autopilot.HELD && !o.autopilotHeld.isEmpty() ? to + ", HOLDING: " + up(o.autopilotHeld) : to;
        }
        String given = !o.helmLine.isEmpty() ? o.helmLine : d.ctx.helmOrder;
        if (o.helm != HelmOrder.NONE && !given.isEmpty()) return order(given);
        // A helmsman flies every planned burn ahead of any order
        if (d.ctx.burnFlown || n.burning) {
            String go = Float.isNaN(n.burnToGo) ? "" : ", " + Catalogue.group(n.burnToGo) + " M/S TO GO";
            return "FLYING BURN" + (n.burnNumber > 0 ? " " + n.burnNumber : "") + go;
        }
        String h = d.ctx.helmsman;
        if (h.isEmpty()) return "THE SHIP DRIFTS";
        return "you".equalsIgnoreCase(h) ? "" : "HOLDING HEADING";
    }

    /** The game's words for an order as HLM gives them: "Helm order (holding position on Vex) waits for a helmsman" is "HOLDING POSITION ON VEX, WAITING: NO HELMSMAN". */
    static String order(String line) {
        String s = line.trim();
        if (s.startsWith("Helm order (")) {
            int close = s.lastIndexOf(") wait");
            String what = close > 12 ? s.substring(12, close) : s;
            String lower = s.toLowerCase(Locale.ROOT);
            String waits = lower.contains("you have the helm") ? "WAITING: YOU ARE AT THE HELM"
                    : lower.contains("helmsman") ? "WAITING: NO HELMSMAN" : "WAITING";
            return up(what) + ", " + waits;
        }
        if (s.startsWith("Helm: ")) s = s.substring(6);
        return up(s);
    }

    /** Why an order can't be given, short: "NO TARGET", "TOO FAR", "NO BURNS PLANNED". */
    static String refusal(String r) {
        String l = r.toLowerCase(Locale.ROOT);
        if (l.contains("navigation target")) return "NO TARGET";
        if (l.contains("too far")) return "TOO FAR: PLAN A TRANSFER";
        if (l.contains("no burns planned")) return "NO BURNS PLANNED";
        if (l.contains("nobody aboard")) return "NO HELMSMAN";
        if (l.contains("in the bubble")) return "ONLY IN THE BUBBLE";
        if (l.contains("seat")) return "FROM A SEAT";
        int cut = r.indexOf('(');
        return Words.cut(cut > 0 ? r.substring(0, cut) : r, 26);
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    void toggle() {
        open = !open;
    }

    float listHeight() {
        return open ? 8f + ACTIONS.length * LIST_ROW + 4f : 0f;
    }

    float listY() {
        return y - 12f - listHeight();
    }

    void boxes(FilamentActor a, float ox, float oy) {
        if (!on) return;
        a.box(CLICK, ox + x, oy + y, w, Geo.HLM_H);
        if (open) a.box(CLICK_LIST, ox + x, oy + listY(), w, listHeight());
    }

    // ------------------------------------------------------------------ painting, from the bottom middle origin

    void paint(Filament f, float now) {
        if (!on) return;
        f.slab(x, y, w, Geo.HLM_H, 4f, 1, true);
        float cx = x + 6f + f.legend(x + 6f, y + 17f, "HELM", f.o().dim()) + 8f;
        f.dm(cx, y + 6.75f, who, f.o().pitch(Geo.P).cells(12).norm());
        int cells = Geo.fit(x + 6f, x + w - 6f);
        f.dm(x + 6f, y + 22.75f, Words.marquee(order, cells, now - since), f.o().pitch(Geo.P).cells(cells).norm());
        if (open) paintList(f);
    }

    private void paintList(Filament f) {
        float ly = listY(), lh = listHeight();
        f.slab(x, ly, w, lh, 4f, 1, false);
        int cells = Geo.fit(x + 40f, x + w - 6f);
        for (int i = 0; i < ACTIONS.length; i++) {
            float ry = ly + 6f + i * LIST_ROW;
            boolean can = why[i].isEmpty();
            f.keycap(x + 6f, ry, keys[i], 16f, 12f, running[i]);
            String text = can ? WORDS[i] : WORDS[i] + ": " + why[i];
            f.dm(x + 30f, ry + 2.75f, Words.cut(text, cells), f.o().pitch(Geo.P).cells(cells).level(running[i] ? Drive.FULL : can ? Drive.NORM : Drive.DIM));
        }
    }
}
