package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * P1, the header rail across the top edge: the one unit always on screen. The seat on split flaps;
 * helm authority in a window let flush into the rail, plain while it has nothing to say; BATTLE STATIONS
 * as a lens; the day and time on drums; the warp on flaps with RAILS, and why warp is held in a second
 * window; PAUSED at the far end. Every other top unit hangs from this rail on rods.
 *
 * <p>Between BATTLE STATIONS and the clock is the chip's window: an empty slot at a seat, so nothing in
 * the rail moves when a mode opens. Over a mode it carries P2's counts: a red lens with the warning
 * diamond and an amber one with the caution triangle, each count on a drum (a closed window at zero), and
 * the {@code \} keycap that acknowledges.
 */
final class HeaderRail extends YardPart {
    static final float H = ContextLayout.RAIL_H;
    private static final Color INK = Yard.INK_LIGHT;

    /** The chip's window, and how far the fields after it stand right of where they stood before it. */
    static final float CHIP_X = 620f, CHIP_W = 104f, AFTER = CHIP_W + 10f;

    private final ContextFrame f;
    /** A mode is up: the chip shows P2's counts, and each lens is lit while the master cue is for its level. */
    boolean chip, warningLit, cautionLit;
    int warnings, cautions;

    HeaderRail(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float w = this.w;
        // The rail: a chassis bar on the screen's top edge, its orange stripe along its foot
        p.rect(0f, 0f, w, 24f, Yard.CHASSIS_BOTTOM);
        p.vgrad(0f, 0f, w, 24f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        p.rect(0f, 0.5f, w, 1f, Color.WHITE, 0.10f);
        p.rect(0f, 23.5f, w, 2.5f, Yard.ORANGE);
        p.rect(0f, 26f, w, 2f, Yard.SHADOW, 0.6f);

        p.legend("SEAT", 8f, 16.5f, INK);
        p.flaps(40f, 3f, YardText.fit(seat(d, f), 14), 14, 9f, 18f, 13f, Yard.FLAP_TEXT);

        String who = authority(d, f);
        boolean held = d.orderState.autopilot == Autopilot.HELD;
        window(p, 178f, 3f, 296f, who, held ? Yard.P_WHITE : Yard.P_AMBER, 36);

        Kit.lens(p, 490f, 4f, 120f, 16f, "BATTLE STATIONS", Yard.AMBER_LAMP, d.orderState.battleStations);
        chip(p, CHIP_X, 3f);

        // The clock: the day and the time of day on drums
        float a = AFTER;
        double s = Math.max(0, d.ctx.simSeconds);
        long day = (long) (s / 86400), hm = (long) (s % 86400) / 60;
        p.legend("DAY", 624f + a, 16.5f, INK);
        p.drums(650f + a, 3f, YardText.drums(day, 3, 0), 11f, 18f, 15f, true);
        p.drums(690f + a, 3f, String.format(Locale.ROOT, "%02d:%02d", hm / 60, hm % 60), 11f, 18f, 15f, false);

        p.legend("WARP", 758f + a, 16.5f, INK);
        p.flaps(792f + a, 3f, d.ctx.warp + "X", 5, 9f, 18f, 13f, Yard.FLAP_TEXT);
        Kit.lens(p, 842f + a, 4f, 46f, 16f, "RAILS", Yard.WHITE_LAMP, d.ctx.warp > 1 && d.ctx.railsWarp);
        float holdX = 896f + a, holdEnd = w - 92f;
        if (holdEnd - holdX >= 40f) window(p, holdX, 3f, holdEnd - holdX, holdText(d), Yard.P_WHITE, Kit.chars(p, holdEnd - holdX - 12f, 13f));

        Kit.lens(p, w - 80f, 4f, 72f, 16f, "PAUSED", Yard.WHITE_LAMP, d.orderState.paused);
    }

    /** The chip's window: dark glass at a seat; over a mode, the two lenses with their counts and the key. */
    private void chip(YardPaint p, float x, float y) {
        p.rrect(x, y, CHIP_W, 18f, 2f, Yard.hex("1B1714"));
        p.rect(x + 2f, y + 1f, CHIP_W - 4f, 1f, Yard.hex("0D0B0A"));
        if (!chip) return;
        markLens(p, x + 5f, y + 2f, true, warningLit);
        p.drums(x + 28f, y + 3f, count(warnings), 11f, 12f, 11f, false);
        markLens(p, x + 48f, y + 2f, false, cautionLit);
        p.drums(x + 71f, y + 3f, count(cautions), 11f, 12f, 11f, false);
        p.key(x + 88f, y + 1.5f, "\\");
    }

    /** A count on one drum: a closed window at zero, 9 for nine and more. */
    static String count(int n) {
        return n <= 0 ? " " : Integer.toString(Math.min(9, n));
    }

    /** A lens with a drawn severity mark for its legend: the warning diamond in red, the caution triangle in amber. */
    private static void markLens(YardPaint p, float x, float y, boolean warning, boolean lit) {
        Yard.Lamp lamp = warning ? Yard.RED_LAMP : Yard.AMBER_LAMP;
        float w = 16f, h = 14f;
        p.rrect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 2f, Yard.ALU);
        if (lit) p.glowRect(x, y, w, h, 5f, lamp.on, 0.55f);
        p.rrect(x, y, w, h, 1.2f, lit ? lamp.on : lamp.off);
        p.glass(x, y, w, h);
        Kit.mark(p, warning, x + w / 2f, y + h / 2f, 4.2f, lit ? Kit.LEGEND_ON : Yard.LEGEND_FAINT);
    }

    /** A window let flush into the rail: plain dark glass while empty, phosphor when it speaks. */
    private static void window(YardPaint p, float x, float y, float w, String text, Color ink, int chars) {
        boolean speaks = text != null && !text.isEmpty();
        if (!speaks) {
            p.rrect(x, y, w, 18f, 2f, Yard.hex("1B1714"));
            p.rect(x + 2f, y + 1f, w - 4f, 1f, Yard.hex("0D0B0A"));
            return;
        }
        p.tube(x, y, w, 18f, 2f, 2f, false);
        Kit.tubeFit(p, YardText.cut(text, Math.max(4, chars)).toUpperCase(Locale.ROOT), x + 8f, y + 13.5f, 13f, ink, w - 14f);
        p.tubeOver(x, y, w, 18f);
    }

    /** The seat field (14): HELM, FIRE CONTROL, GUN · FWD, DECK; aboard another craft, DECK · HIGHPORT. */
    static String seat(HudData d, ContextFrame f) {
        switch (f.seat) {
            case HELM: return "HELM";
            case FIRE: return "FIRE CONTROL";
            case GUN: {
                String g = gunName(d);
                return g.isEmpty() ? "GUN" : "GUN · " + g;
            }
            case DECK: return f.aboard.isEmpty() ? "DECK" : "DECK · " + f.aboard;
            default: return "";
        }
    }

    /** The gun the captain has taken, by its place ("FWD GUN" reads FWD), or "". */
    private static String gunName(HudData d) {
        for (int i = 0; i < d.stations.seats.size; i++) {
            Seat s = d.stations.seats.get(i);
            if (s.post == Post.GUN && s.hand == Hand.PLAYER) return s.name.replace("GUN", "").replaceAll("\\s+", " ").trim();
        }
        return "";
    }

    /**
     * Helm authority (P1): only when someone other than the captain flies, or nobody does. Empty when the
     * captain has the helm with no order running; while the autopilot is held for warp, the reason is the
     * warp field's.
     */
    static String authority(HudData d, ContextFrame f) {
        Orders o = d.orderState;
        UiContext c = d.ctx;
        if (o.autopilot == Autopilot.ON) return o.autopilotTo.isEmpty() ? "Autopilot on" : "Autopilot to " + o.autopilotTo;
        if (o.autopilot == Autopilot.HELD) {
            return !c.warpHold.isEmpty() || o.autopilotHeld.isEmpty() ? "Autopilot held" : "Autopilot held: " + o.autopilotHeld;
        }
        if (o.helm != HelmOrder.NONE && !o.helmLine.isEmpty()) return o.helmLine;
        String who = c.helmsman == null ? "" : c.helmsman;
        if (who.equalsIgnoreCase("you")) return "";
        if (who.equalsIgnoreCase("autopilot")) return "Autopilot";
        if (who.isEmpty()) return f.berthed() || f.seat == ContextFrame.Seat.NONE ? "" : "Helm: nobody";
        return "Helm: " + who + (c.helmOrder.isEmpty() ? "" : " · " + lowerFirst(c.helmOrder));
    }

    /** The warp field's reason, when warp is held or capped ("rails held: crew outside"), or "". */
    static String holdText(HudData d) {
        return d.ctx.warpHold == null ? "" : d.ctx.warpHold;
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
