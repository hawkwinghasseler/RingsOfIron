package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * HLM on deck and at gunnery: a pod of its own standing on the sill, with who has the helm on a name
 * flap and the standing order on an amber strip tube. It never counts down: the chronometer and FLT
 * own the times. At the helm the same line rides on top of FLT instead.
 *
 * <p>The autopilot is a standing order like the helm's ("AUTOPILOT TO SHACKLETON ICE"), and its hold
 * is this line's ("..., HOLDING: PIRATES ABOUT"): 9.2 sends the game's "Autopilot holds" warning here,
 * not to the band, so the telex leaves it out while the line says it ({@link TaskFrame#helmHolds}).
 */
final class HelmPod extends YardPart {
    static final float H = 36f;
    private static final float Y = 726f;
    private static final String WAITING = "WAITING: ";
    private static final String AUTOPILOT = "AUTOPILOT", HOLDING = ", HOLDING: ";

    private final TaskFrame f;
    Yard.Finish finish = Yard.Finish.GUN;

    HelmPod(TaskFrame f) {
        super(0f, Y);
        this.f = f;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        p.unit(0f, Y, w, H, finish, "b");
        p.legend("HELM", 14f, Y + 22.5f, finish.ink);
        p.flaps(50f, Y + 9f, YardText.fit(who(d), 8), 8, 8.4f, 18f, 13f, Yard.FLAP_TEXT);
        float tw = w - 138f;
        p.tube(124f, Y + 10f, tw, 16f, 2f, 2.5f, false);
        p.tube(orderFit(d, w - 154f), 132f, Y + 22.5f, 13f, Yard.P_AMBER, false);
        p.tubeOver(124f, Y + 10f, tw, 16f);
    }

    /** Who has the helm, as the name flap shows it: YOU, a helmsman's name or NOBODY. The autopilot is a helmsman's job. */
    static String who(HudData d) {
        String h = d.ctx.helmsman;
        if (h.isEmpty()) return "NOBODY";
        return h.toUpperCase(Locale.ROOT);
    }

    /** The standing order and its state, as the strip tube shows it. */
    static String order(HudData d) {
        UiContext c = d.ctx;
        NavReadout nav = d.navReadout;
        // A helmsman flies every planned burn ahead of any order
        if (c.burnFlown) {
            String s = "FLYING BURN" + (nav.burnNumber > 0 ? " " + nav.burnNumber : "");
            float dv = nav.burning && !Float.isNaN(nav.burnToGo) ? nav.burnToGo : nav.burnDeltaV;
            if (!Float.isNaN(dv)) s += ", " + YardText.thousands(dv) + " M/S TO GO";
            return s;
        }
        if (!c.helmFault.isEmpty()) return WAITING + c.helmFault.toUpperCase(Locale.ROOT);
        if (!c.helmOrder.isEmpty()) return c.helmOrder.toUpperCase(Locale.ROOT);
        String auto = autopilot(d.orderState);
        if (!auto.isEmpty()) return auto;
        if (c.helmsman.isEmpty()) return "";
        return "HOLDING HEADING";
    }

    /**
     * The autopilot as the line words it, or "" while it is off: where it flies, and while it holds off,
     * why ("AUTOPILOT TO SHACKLETON ICE, HOLDING: PIRATES ABOUT"), in the game's words.
     */
    static String autopilot(Orders o) {
        if (o.autopilot == Autopilot.OFF) return "";
        String s = o.autopilotTo.isEmpty() ? AUTOPILOT : AUTOPILOT + " TO " + o.autopilotTo.toUpperCase(Locale.ROOT);
        if (o.autopilot != Autopilot.HELD) return s;
        return o.autopilotHeld.isEmpty() ? s + ", HOLDING" : s + HOLDING + o.autopilotHeld.toUpperCase(Locale.ROOT);
    }

    /** Whether the line says the autopilot holds off this frame, where it is up: nothing flown or ordered ahead of it. */
    static boolean saysHold(HudData d) {
        return d.orderState.autopilot == Autopilot.HELD && order(d).startsWith(AUTOPILOT);
    }

    /**
     * The order cut to a strip tube {@code width} units across; a fault too long for it loses its
     * WAITING: first, since the amber strip and the fault say as much, and the autopilot held its
     * destination, since why it holds matters more (the hover has both).
     */
    static String orderFit(HudData d, float width) {
        int room = (int) (width / 7.8f);
        String o = order(d);
        if (o.length() > room && o.startsWith(WAITING)) o = o.substring(WAITING.length());
        int held = o.startsWith(AUTOPILOT) ? o.indexOf(HOLDING) : -1;
        if (o.length() > room && held > 0) o = AUTOPILOT + " HOLDING: " + o.substring(held + HOLDING.length());
        return YardText.fit(o, room);
    }

    /** Whether an order or the autopilot stands: the helm line rides on FLT at the helm. */
    static boolean orderStands(HudData d) {
        UiContext c = d.ctx;
        return c.burnFlown || !c.helmOrder.isEmpty() || !c.helmFault.isEmpty() || d.flightStatus.autopilot;
    }

    @Override
    public String hover(float px, float py) {
        String o = order(f.data);
        if (o.isEmpty()) return null;
        HudData d = f.data;
        String keys = d.key("HELM_FLY_BURNS").isEmpty() ? "" : "   (" + d.key("HELM_FLY_BURNS") + " to " + d.key("AUTOPILOT") + " give orders, " + d.key("HELM_CANCEL") + " cancels)";
        return "Helm: " + who(d).toLowerCase(Locale.ROOT) + ", " + o.toLowerCase(Locale.ROOT) + keys;
    }
}
