package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * P1, the status strip (functional-context 6, P1): a thin rack across the top. From the left the seat,
 * who flies the ship when it is not the captain, BATTLE STATIONS behind amber glass, and the caution
 * and warning chip, which is the master alert cue while a full-screen mode is open; from the right the
 * clock, warp with its rails and the reason it is held, and PAUSED.
 */
final class TopRack {
    static final int SEAT = 14, AUTHORITY = 36, HOLD = 20;

    String seat = "", authority = "", hold = "";
    boolean authHeld, battle, paused, rails, chip;
    int warnings, cautions;
    String day = "", time = "", warp = "1";
    /** The sim minute the clock was last set for. */
    private long minute = -1;
    /** Where the chip sits (it takes a click while lit), on the grid. */
    float chipX;

    void update(HudData d, Situations s, Cautions c) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        seat = seat(d, s);
        authority = authority(d, s);
        authHeld = o.autopilot == Autopilot.HELD;
        battle = o.battleStations;
        paused = o.paused;
        warp = Integer.toString(Math.max(1, ctx.warp));
        rails = ctx.railsWarp && ctx.warp > 1;
        hold = Fmt.cut(ctx.warpHold, HOLD);
        long secs = (long) Math.floor(Math.max(0, ctx.simSeconds));
        if (secs / 60 != minute) {
            minute = secs / 60;
            day = Integer.toString(Fmt.day(ctx.simSeconds));
            time = String.format(Locale.ROOT, "%02d:%02d", secs % 86_400 / 3600, secs % 3600 / 60);
        }
        // In a full-screen mode P2 folds into the chip, which is the master alert cue there
        chip = s.fullScreen && (c.warn || c.caut || !c.panel.isEmpty());
        warnings = cautions = 0;
        if (s.fullScreen) {
            for (Cautions.Row r : c.rows) {
                if (!r.listed) continue;
                if (r.level == Level.WARNING) warnings++;
                else if (r.level == Level.CAUTION) cautions++;
            }
        }
    }

    /** HELM, FIRE CONTROL, GUN NOSE, DECK; aboard another craft its name. */
    static String seat(HudData d, Situations s) {
        switch (s.seat) {
            case HELM: return "HELM";
            case FIRE_CONTROL: return "FIRE CONTROL";
            case GUN:
                for (Mount m : d.weaponFrame.mounts) {
                    if (m.controller == Controller.PLAYER && !m.name.isEmpty()) return Fmt.cut("GUN " + m.name, SEAT);
                }
                return "GUN";
            case DECK: return s.aboard.isEmpty() ? "DECK" : "DECK " + Fmt.name(s.aboard, SEAT - 5);
            default: return "";
        }
    }

    /**
     * Who flies the ship, only when it is not simply the captain at the helm: the autopilot, a helm
     * order, a crew member, or nobody. Empty with the captain at the helm and no order running.
     */
    static String authority(HudData d, Situations s) {
        Orders o = d.orderState;
        UiContext ctx = d.ctx;
        if (ctx.ship == null || s.docked || s.latched) return "";
        if (o.autopilot == Autopilot.ON) return Fmt.cut("AUTOPILOT → " + o.autopilotTo, AUTHORITY);
        if (o.autopilot == Autopilot.HELD) {
            // When warp's hold is the reason it shows once, in the warp field
            boolean warpHeld = !ctx.warpHold.isEmpty();
            return warpHeld || o.autopilotHeld.isEmpty() ? "AUTOPILOT HELD" : Fmt.cut("AUTOPILOT HELD: " + o.autopilotHeld, AUTHORITY);
        }
        if (o.helm != HelmOrder.NONE) return Fmt.cut("HELM ORDER: " + order(o), AUTHORITY);
        if (s.seat == Situations.Seat.HELM) return "";
        String who = ctx.helmsman;
        if (who.isEmpty()) return "HELM: NOBODY";
        if (who.equalsIgnoreCase("you")) return "";
        if (who.equalsIgnoreCase("autopilot")) return "AUTOPILOT";
        return Fmt.cut("HELM: " + who, AUTHORITY);
    }

    /** A helm order in the strip's words: "APPROACH HIGHPORT 500 M". */
    static String order(Orders o) {
        String what;
        switch (o.helm) {
            case FLY_BURNS: what = "FLY THE BURNS"; break;
            case MATCH_VELOCITY: what = "MATCH"; break;
            case HOLD_POSITION: what = "HOLD ON"; break;
            case APPROACH: what = "APPROACH"; break;
            case DOCK: what = "DOCK AT"; break;
            default: what = ""; break;
        }
        String to = o.helmTarget.isEmpty() || o.helm == HelmOrder.FLY_BURNS ? "" : " " + o.helmTarget;
        String dist = o.helm == HelmOrder.APPROACH && !Float.isNaN(o.approachDistance) ? " " + Fmt.rangeText(o.approachDistance) : "";
        return (what + to + dist).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ painting

    private static final float Y = Geo.STRIP_Y, H = Geo.STRIP_H, B = Y + 14.75f;

    private static void window(Filament f, float x, float w) {
        f.module(x, Y, w, H, f.o().filaments(0).noGlare().radius(2f));
    }

    /** The rack across the whole stage, {@code width} wide. */
    static void paintRack(Filament f, float width) {
        f.rack(-8f, -8f, width + 16f, Geo.STRIP_B + 8f, f.o().radius(6f).shadowY(3f));
    }

    /** The left group, on the grid from the stage's left. */
    void paintLeft(Filament f) {
        float x = 8f;
        window(f, x, SEAT * Geo.CELL + 9f);
        f.dm(x + 6f, B - 10.5f, seat, f.o().pitch(Geo.P).cells(SEAT).norm());
        x += SEAT * Geo.CELL + 15f;
        window(f, x, AUTHORITY * Geo.CELL + 9f);
        f.dm(x + 6f, B - 10.5f, authority, f.o().pitch(Geo.P).cells(AUTHORITY).level(authHeld ? Drive.FULL : Drive.NORM));
        x += AUTHORITY * Geo.CELL + 15f;
        float bw = f.legendWidth("BATTLE STATIONS", f.o()) + 14f;
        window(f, x, bw);
        f.legend(x + 7f, B, "BATTLE STATIONS", f.o().amber().lit(battle).norm());
        x += bw + 6f;
        // The chip: dark but in full-screen modes, where it stands for P2 and is the master alert cue
        chipX = x;
        window(f, x, 66f);
        f.dm(x + 5f, B - 10.5f, chip && warnings > 0 ? "♦" : "", f.o().pitch(Geo.P).cells(1).red().full());
        f.seg7(x + 16f, B - 12f, 12f, chip ? Integer.toString(Math.min(9, warnings)) : "", f.o().cells(1).red().norm());
        f.dm(x + 36f, B - 10.5f, chip && cautions > 0 ? "▲" : "", f.o().pitch(Geo.P).cells(1).amber().full());
        f.seg7(x + 47f, B - 12f, 12f, chip ? Integer.toString(Math.min(9, cautions)) : "", f.o().cells(1).amber().norm());
        Kit.fits(x + 66f, Geo.W - 600f, "P1 left");
    }

    /** The right group, on the grid from the stage's right. */
    void paintRight(Filament f) {
        float rx = 1272f;
        float pw = f.legendWidth("PAUSED", f.o()) + 14f;
        rx -= pw;
        window(f, rx, pw);
        f.legend(rx + 7f, B, "PAUSED", f.o().lit(paused).norm());
        boolean one = "1".equals(warp);
        float ww = 6f + f.seg7Width(12f, 5, f.o()) + 3f + f.legendWidth("X", f.o()) + 8f + f.legendWidth("RAILS", f.o()) + 8f
                + f.dmWidth(HOLD, f.o().pitch(Geo.P)) + 6f;
        rx -= ww + 6f;
        window(f, rx, ww);
        float wx = rx + 6f;
        wx += f.seg7(wx, B - 12f, 12f, warp, f.o().cells(5).right().level(one ? Drive.DIM : Drive.NORM)) + 3f;
        wx += f.legend(wx, B, "X", f.o().level(one ? Drive.DIM : Drive.NORM)) + 8f;
        wx += f.legend(wx, B, "RAILS", f.o().lit(rails).norm()) + 8f;
        f.dm(wx, B - 10.5f, hold, f.o().pitch(Geo.P).cells(HOLD).full());
        float cw = 7f + f.legendWidth("DAY", f.o()) + 5f + f.seg7Width(12f, 3, f.o()) + 10f + f.seg7Width(12f, 4, f.o().colons(1)) + 7f;
        rx -= cw + 6f;
        window(f, rx, cw);
        float cx = rx + 7f;
        cx += f.legend(cx, B, "DAY", f.o().dim()) + 5f;
        cx += f.seg7(cx, B - 12f, 12f, day, f.o().cells(3).right().norm()) + 10f;
        f.seg7(cx, B - 12f, 12f, time, f.o().cells(4).colons(1).norm());
    }
}
