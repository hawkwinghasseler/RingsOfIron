package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * C1, the orders menu (functional-context 6, C1): a plate of keys opened over the world above the
 * seat's primary instrument, every order the captain can give in its group with its key printed on
 * it. A key's lamp says how the order stands: green while it runs, amber blinking while a guarded
 * order is armed, red while it can't be given; and the foot lists each reason once with the keys it
 * blocks. The keys give the orders as they always have: the menu shows them, so the player learns
 * what exists. It opens and closes on 7 (O stays the lights), and an order's own key closes it; not
 * Esc, which the game reads itself and which quits it with no window open. The foot says so.
 */
final class OrdersMenu {
    static final float W = 620f, ROW = 42f, KEY_H = 26f, REASON = 18f;
    static final int REASONS = 6;
    static final Action[] HELM = {Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH,
            Action.HELM_DOCK, Action.HELM_CANCEL};
    static final HelmOrder[] ORDERS = {HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH,
            HelmOrder.DOCK, HelmOrder.NONE};
    static final String[] HELM_WORDS = {"FLY BURNS", "MATCH", "HOLD", "APPROACH", "DOCK", "CANCEL"};
    /** The keys an order menu's own keys are: the ones that close it after giving their order. */
    static final Action[] KEYS = {Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH,
            Action.HELM_DOCK, Action.HELM_CANCEL, Action.AUTOPILOT, Action.SHIELDS, Action.SHIELD_TRIM, Action.ABANDON, Action.CUT};
    static final String[] TRIMS = {"EVEN", "BOW", "PORT", "STERN", "STBD"};
    /** The way out, on the foot's right: the menu's own key, since Esc belongs to the game. */
    static final String CLOSE = "7 CLOSES";
    private static final Color DIM_INK = new Color(0.42f, 0.44f, 0.46f, 1f);

    /** A key's lamp: dark, running, armed, or can't be given. */
    static final int IDLE = 0, ON = 1, ARMED = 2, NA = 3;

    boolean open;
    final String[] helmLabel = new String[6];
    final int[] helm = new int[6];
    String approach = "", autopilotLabel = "", autopilotState = "", shieldLabel = "", trimLabel = "", abandonLabel = "", towLabel = "";
    int autopilot, suits, eva, shields, trimState, abandon, tow;
    int trim = -1;
    final String[] reasons = new String[REASONS], blocked = new String[REASONS];
    int reasonCount;
    private final StringBuilder sb = new StringBuilder();

    void toggle() {
        open = !open;
    }

    /** Whether {@code keycode} is one of the orders' own keys. */
    static boolean orderKey(HudData d, int keycode) {
        String name = Action.keyName(keycode);
        for (Action a : KEYS) if (name.equalsIgnoreCase(key(d, a))) return true;
        return false;
    }

    static String key(HudData d, Action a) {
        String k = d.key(a.name());
        return (k.isEmpty() ? a.defaultKeyName() : k).toUpperCase(Locale.ROOT);
    }

    void update(HudData d, Situations s) {
        if (s.fullScreen || d.ctx.ship == null) open = false;
        if (!open) return;
        Orders o = d.orderState;
        reasonCount = 0;
        for (int i = 0; i < HELM.length; i++) {
            String k = key(d, HELM[i]);
            HelmOrder h = ORDERS[i];
            String refusal = o.helmRefusal[h.ordinal()];
            boolean running = h != HelmOrder.NONE && o.helm == h;
            helm[i] = running ? ON : refusal != null && !refusal.isEmpty() ? NA : IDLE;
            if (helm[i] == NA) reason(refusal, k);
            helmLabel[i] = k + " " + HELM_WORDS[i];
        }
        approach = o.helm == HelmOrder.APPROACH && !Float.isNaN(o.approachDistance) ? "TO " + Fmt.rangeText(o.approachDistance) : "";
        String ak = key(d, Action.AUTOPILOT);
        autopilotLabel = ak + " TO NAV";
        autopilot = o.autopilot == Autopilot.ON ? ON : o.autopilot == Autopilot.HELD ? ARMED : IDLE;
        autopilotState = o.autopilot == Autopilot.HELD ? "HELD" : o.autopilot == Autopilot.ON ? "ON" : "OFF";
        if (!o.autopilotRefusal.isEmpty() && o.autopilot == Autopilot.OFF) {
            autopilot = NA;
            reason(o.autopilotRefusal, ak);
        }
        suits = o.suitsAtBattleStations ? ON : IDLE;
        eva = o.evaHeld ? ON : IDLE;
        ShieldStatus sh = d.shieldStatus;
        String sk = key(d, Action.SHIELDS), tk = key(d, Action.SHIELD_TRIM);
        shieldLabel = sk + (sh.raised ? " LOWER" : " RAISE");
        trimLabel = tk + " TRIM";
        trim = -1;
        String t = Arms.trimWord(sh.trim);
        for (int i = 0; i < TRIMS.length; i++) if (TRIMS[i].equals(t)) trim = i;
        if (!sh.present || sh.emitters == 0) {
            shields = trimState = NA;
            trim = -1;
            reason("No shield emitter aboard", tk + " " + sk);
        } else if (!o.seated) {
            shields = trimState = NA;
            String seat = o.helmRefusal[HelmOrder.FLY_BURNS.ordinal()];
            reason(seat == null || seat.isEmpty() ? "From a seat" : seat, tk + " " + sk);
        } else {
            shields = sh.raised ? ON : IDLE;
            trimState = IDLE;
        }
        String pk = key(d, Action.ABANDON), bk = key(d, Action.CUT);
        abandonLabel = pk + " ABANDON SHIP";
        abandon = o.abandoning ? ON : o.abandonArmed > 0f ? ARMED : !o.abandonRefusal.isEmpty() ? NA : IDLE;
        if (abandon == NA) reason(o.abandonRefusal, pk);
        towLabel = bk + " CALL A TOW" + (o.towFee > 0 ? " " + Cautions.group(o.towFee) + " CR" : "");
        tow = o.towArmed > 0f ? ARMED : !o.towRefusal.isEmpty() ? NA : IDLE;
        if (tow == NA) reason(o.towRefusal, bk);
    }

    /** Lists {@code why} once with {@code keys} among the keys it blocks. */
    private void reason(String why, String keys) {
        if (why == null || why.isEmpty()) return;
        String w = words(why);
        for (int i = 0; i < reasonCount; i++) {
            if (reasons[i].equals(w)) {
                blocked[i] = blocked[i] + " " + keys;
                return;
            }
        }
        if (reasonCount == REASONS) return;
        reasons[reasonCount] = w;
        blocked[reasonCount] = keys;
        reasonCount++;
    }

    /** A refusal as the foot words it: capitals, without a closing stop. */
    static String words(String s) {
        String t = s.trim();
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t.toUpperCase(Locale.ROOT);
    }

    float height() {
        return open ? 4 * ROW + 12f + Math.max(1, reasonCount) * REASON + 8f : 0f;
    }

    // ------------------------------------------------------------------ painting, centred on the grid

    /** The menu with its foot at {@code foot}. */
    void paint(Filament f, float foot, float time) {
        if (!open) return;
        float h = height(), x = Geo.W / 2f - W / 2f, y = foot - h;
        f.plate(x, y, W, h, 6f, 7f);
        boolean blink = (time % 0.5f) < 0.25f;
        float lx = x + 12f, kx0 = x + 100f;
        // HELM: interlocked keys, one down at a time; CANCEL releases it
        float ry = y + 12f;
        group(f, lx, ry, "HELM");
        float kx = kx0, kw = (W - 100f - 12f - 5 * 4f) / 6f;
        for (int i = 0; i < 6; i++) {
            orderKey(f, kx, ry, kw, helmLabel[i], helm[i], blink);
            kx += kw + 4f;
        }
        if (!approach.isEmpty()) f.silk(kx0 + 3 * (kw + 4f) + kw / 2f, ry + KEY_H + 9f, approach, f.o().centre());
        // AUTOPILOT, with its state; the CREW's two toggles
        ry += ROW;
        group(f, lx, ry, "AUTOPILOT");
        kx = kx0 + orderKey(f, kx0, ry, 0f, autopilotLabel, autopilot, blink) + 8f;
        f.legend(kx, ry + 18f, autopilotState, f.o().norm().lit(autopilot != NA));
        float cx = x + 330f;
        group(f, cx, ry, "CREW");
        cx += 46f;
        cx += orderKey(f, cx, ry, 0f, "SUITS AT BATTLE", suits, blink) + 6f;
        orderKey(f, cx, ry, 0f, "HOLD IN FROM EVA", eva, blink);
        // SHIELDS: raise or lower, and the trim as a five-way selector
        ry += ROW;
        group(f, lx, ry, "SHIELDS");
        kx = kx0 + orderKey(f, kx0, ry, 0f, shieldLabel, shields, blink) + 8f;
        kx += orderKey(f, kx, ry, 0f, trimLabel, trimState, blink) + 10f;
        for (int i = 0; i < TRIMS.length; i++) {
            kx += f.legend(kx, ry + 18f, TRIMS[i], f.o().lit(i == trim).norm()) + 10f;
        }
        // SHIP: the guarded orders, last and furthest from the helm keys the hand uses most
        ry += ROW;
        group(f, lx, ry, "SHIP");
        kx = kx0 + orderKey(f, kx0, ry, 0f, abandonLabel, abandon, blink) + 8f;
        orderKey(f, kx, ry, 0f, towLabel, tow, blink);
        // The foot: each reason once, with the keys it blocks
        float fy = y + 4 * ROW + 12f;
        f.module(x + 8f, fy - 4f, W - 16f, Math.max(1, reasonCount) * REASON + 6f, f.o().filaments(0).noGlare().radius(2f));
        float closeW = f.legendWidth(CLOSE, f.o());
        f.legend(x + W - 16f - closeW, fy + 12f, CLOSE, f.o().dim());
        int first = (int) ((W - 32f - closeW - 12f) / Geo.CELL);
        for (int i = 0; i < reasonCount; i++) {
            int cells = i == 0 ? first : 64;
            sb.setLength(0);
            sb.append(blocked[i]).append("  ").append(reasons[i]);
            Rows.fit(sb, cells);
            f.dm(x + 16f, fy + i * REASON + 2f, sb, f.o().pitch(Geo.P).cells(cells).level(Drive.DIM).noGhost());
        }
    }

    private static void group(Filament f, float x, float ry, String name) {
        f.legend(x, ry + 18f, name, f.o().dim());
    }

    /** An order's key, {@code w} wide or fitted to its label, its lamp above it; returns the width. */
    private static float orderKey(Filament f, float x, float ry, float w, String label, int state, boolean blink) {
        float kw = w > 0f ? w : f.legendWidth(label, f.o().size(11f)) + 16f;
        Filament.Opt o = f.o().size(11f);
        if (state == NA) o.ink(DIM_INK).led(Filament.Led.RED, true);
        else if (state == ON) o.led(Filament.Led.GREEN, true);
        else if (state == ARMED) o.led(Filament.Led.AMBER, blink);
        else o.led(Filament.Led.GREEN, false);
        f.key(x, ry + 4f, kw, KEY_H - 4f, label, o);
        return kw;
    }
}
