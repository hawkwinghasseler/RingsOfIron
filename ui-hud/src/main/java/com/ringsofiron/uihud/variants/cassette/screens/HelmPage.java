package com.ringsofiron.uihud.variants.cassette.screens;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The helm panel's Helm tab (the Stations spec's 10.2, HELM and DOCK): who has the helm, the order in force
 * and how it goes, each order by its key with why the helm would refuse it now, the autopilot; then the
 * ship's docking port, the port it lines up with or is latched to, the clearance and the collar's strain.
 * Orders are given seated, by their keys, as the game has it: the soft key sits the captain at the helm.
 */
final class HelmPage extends FactsPage {
    /** The orders in key order, their actions and keys as the game binds them by default, and their words. */
    static final HelmOrder[] ORDERS = {HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH, HelmOrder.DOCK};
    static final String[] ACTIONS = {"HELM_FLY_BURNS", "HELM_MATCH_VELOCITY", "HELM_HOLD", "HELM_APPROACH", "HELM_DOCK"};
    static final String[] KEYS = {"1", "2", "3", "4", "5"};
    static final String[] WORDS = {"BURNS", "MATCH", "HOLD", "NEAR", "DOCK"};

    private HudData data;
    private boolean own;
    private String right;

    HelmPage() {
        super("HELM", "HELM");
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        own = data != null && ConsolePanels.ship(data) == data.ctx.ship;
        begin();
        if (!own) return;
        Orders o = data.orderState;
        String who = data.ctx.helmsman;
        right = who.isEmpty() ? "EMPTY" : who.equalsIgnoreCase("you") ? "YOU" : Fmt.upper(who);
        fact("HELMSMAN", who.isEmpty() ? "NOBODY AT THE HELM" : right, who.isEmpty() ? DIM : AMBER);
        fact("ORDER", order(data), AMBER);
        if (!o.helmLine.isEmpty()) text(Fmt.upper(o.helmLine), DIM);
        if (!data.ctx.helmFault.isEmpty()) text(Fmt.upper(data.ctx.helmFault), Yard.P_RED);

        head("ORDERS", o.seated ? "BY KEY" : "GIVEN SEATED");
        for (int i = 0; i < ORDERS.length; i++) {
            HelmOrder h = ORDERS[i];
            String refusal = o.helmRefusal[h.ordinal()];
            boolean standing = o.helm == h;
            String says = standing ? "IN FORCE" : refusal == null || refusal.isEmpty() ? "READY" : Fmt.upper(refusal);
            fact(key(ACTIONS[i], KEYS[i]) + " " + WORDS[i], says, standing ? AMBER : refusal == null || refusal.isEmpty() ? AMBER : DIM);
        }
        fact(key("AUTOPILOT", "6") + " AUTO", autopilot(o), o.autopilotRefusal.isEmpty() || o.autopilot != Autopilot.OFF ? AMBER : DIM);

        DockingApproach a = data.docking;
        head("DOCK", !a.other.isEmpty() ? Fmt.upper(a.other) : data.ctx.docked ? Fmt.upper(data.ctx.stationName) : "NO PORT");
        if (!a.present) {
            text("NO DOCKING PORT FITTED", DIM);
            return;
        }
        fact("OWN PORT", port(a), a.collarTorn || a.collarJammed ? Yard.P_RED : AMBER);
        if (a.otherPort > 0 || !a.other.isEmpty()) {
            fact("TO", (a.otherPort > 0 ? a.otherPort + " " + a.otherPortSize + "  " : "") + clearance(a),
                    a.clearance == Clearance.DENIED || a.clearance == Clearance.NO_PORT ? Yard.P_RED : AMBER);
        }
        if (!a.refusal.isEmpty()) text(Fmt.upper(a.refusal), Yard.P_RED);
        if (!Float.isNaN(a.range) && a.capture != Capture.DOCKED) {
            boolean fast = !Float.isNaN(a.closing) && !Float.isNaN(a.safeSpeed) && a.closing > a.safeSpeed;
            fact("APPROACH", ContactsPage.range(a.range) + (Float.isNaN(a.closing) ? "" : String.format(java.util.Locale.ROOT, ", %.1f M/S", a.closing))
                    + (fast ? " TOO FAST" : ""), fast ? Yard.P_RED : AMBER);
        }
        if (a.capture == Capture.DOCKED || a.capture == Capture.CAPTURED) {
            boolean hard = !Float.isNaN(a.strain) && a.strain > 0.6f;
            fact("STRAIN", (Float.isNaN(a.strain) ? "--" : Math.round(a.strain * 100f) + "%") + (a.rigid ? ", RIGID" : ""), hard ? Yard.P_RED : AMBER);
        }
        keepSelection();
    }

    private String key(String action, String fallback) {
        String k = data == null ? "" : data.key(action);
        return k.isEmpty() ? fallback : k.toUpperCase(java.util.Locale.ROOT);
    }

    /** The order in force, as the helm says it, or that the ship flies by hand. */
    static String order(HudData d) {
        if (!d.ctx.helmOrder.isEmpty()) return Fmt.upper(d.ctx.helmOrder);
        Orders o = d.orderState;
        if (o.helm == HelmOrder.NONE) return "NONE: BY HAND";
        String s = o.helm.name().replace('_', ' ');
        return o.helmTarget.isEmpty() ? s : s + " " + Fmt.upper(o.helmTarget);
    }

    /** The autopilot: ON to somewhere, HELD and why, else OFF or why it can't fly. */
    static String autopilot(Orders o) {
        switch (o.autopilot) {
            case ON:
                return o.autopilotTo.isEmpty() ? "ON" : "ON TO " + Fmt.upper(o.autopilotTo);
            case HELD:
                return "HELD: " + Fmt.upper(o.autopilotHeld);
            default:
                return o.autopilotRefusal.isEmpty() ? "OFF" : Fmt.upper(why(o.autopilotRefusal, "The autopilot "));
        }
    }

    /** A refusal without the subject the row's name already gives. */
    static String why(String refusal, String subject) {
        return refusal.regionMatches(true, 0, subject, 0, subject.length()) ? refusal.substring(subject.length()) : refusal;
    }

    /** The ship's own port: "1/2 M  ARMED". */
    static String port(DockingApproach a) {
        String state = a.collarTorn ? "COLLAR TORN" : a.collarJammed ? "JAMMED" : a.capture == Capture.DOCKED ? "LATCHED"
                : a.capture == Capture.CAPTURED ? "CAPTURED" : a.armed ? "ARMED" : "SAFE";
        return a.port + "/" + a.ports + " " + a.portSize + "  " + state;
    }

    /** What the station said: GRANTED and for how long, DENIED, NO FREE PORT, or not asked. */
    static String clearance(DockingApproach a) {
        switch (a.clearance) {
            case GRANTED:
                return "GRANTED" + (Float.isNaN(a.clearanceLeft) ? "" : " " + YardText.clock(a.clearanceLeft));
            case DENIED:
                return "DENIED";
            case NO_PORT:
                return "NO FREE PORT";
            case YIELDS:
                return "YIELDS";
            default:
                return a.armed ? "ASKED" : "NOT ASKED";
        }
    }

    /** The console's own order that seats the captain (V), or null. */
    private HudData.ConsoleAction take() {
        if (data == null) return null;
        for (int i = 0; i < data.console.actions.size; i++) {
            HudData.ConsoleAction a = data.console.actions.get(i);
            if ("VIEW".equals(a.action) && a.run != null) return a;
        }
        return null;
    }

    @Override
    String titleRight() {
        return own ? right : null;
    }

    @Override
    void keys() {
        if (!own) return;
        boolean seated = data.ctx.presence == Presence.HELM;
        softKey("TAKE HELM", seated ? "YOU HAVE THE HELM" : take() == null ? "THE HELM CAN'T BE TAKEN HERE" : null);
        note("SIT TO GIVE ORDERS BY KEY");
    }

    @Override
    void press(int k) {
        HudData.ConsoleAction a = take();
        if (a != null) a.run.run();
    }
}
