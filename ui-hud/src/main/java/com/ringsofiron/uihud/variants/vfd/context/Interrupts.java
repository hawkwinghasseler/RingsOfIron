package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * The top centre's interrupts (functional-context 6, P3; 8, X3). P3, the threat alert: the count,
 * MISSILES INBOUND and the soonest impact on one line behind red glass, "1 DOWN" for a moment before
 * the count drops; under it the decoy (not on deck) and point defense. At fire control and a gun F4
 * has every impact time, so P3 keeps the count and the decoy. X3, the emergency card, under P3: a red
 * bar with the state cut out of it, and each way out on its own row with its cost, its key on the
 * bezel beside it with an LED that blinks while the first press holds.
 */
final class Interrupts {
    /** How long "1 DOWN" shows before the count drops, s. */
    static final float DOWN_FOR = 1.5f;
    static final float P3_W = Geo.TC_W, P3_H = Geo.P3_H, X3_GLASS = 444f, X3_ROW = 30f;
    static final int X3_ROWS = 3;

    // P3
    boolean threat;
    String count = "", time = "", downText = "", decoyKey = "F", decoyIn = "";
    boolean plural, showTime, decoy, decoyReady, pd;
    private int shown, last;
    private float downLeft;

    // X3
    boolean card;
    String title = "";
    final String[] what = new String[X3_ROWS], cost = new String[X3_ROWS], keys = new String[X3_ROWS];
    final boolean[] armed = new boolean[X3_ROWS], twice = new boolean[X3_ROWS];
    int rows;

    void update(HudData d, Situations s, float delta) {
        threat(d, s, delta);
        emergency(d, s);
    }

    private void threat(HudData d, Situations s, float delta) {
        int n = d.ctx.ship == null ? 0 : d.threatFrame.incoming();
        if (n < last && last > 0) {
            // One or more gone: say so, then let the count drop
            downText = (last - n) + " DOWN";
            downLeft = DOWN_FOR;
            shown = Math.max(shown, last);
        }
        last = n;
        if (downLeft > 0f) {
            downLeft -= delta;
            if (downLeft <= 0f) downText = "";
        }
        if (downLeft <= 0f) shown = n;
        threat = shown > 0;
        if (!threat) return;
        count = Integer.toString(Math.min(9, shown));
        plural = shown != 1;
        float t = d.threatFrame.soonestImpact();
        boolean listed = s.seat == Situations.Seat.FIRE_CONTROL || s.seat == Situations.Seat.GUN;
        showTime = !listed && !Float.isNaN(t) && n > 0;
        time = showTime ? Fmt.secs(Math.min(t, 99f)) : "";
        WeaponFrame w = d.weaponFrame;
        decoy = s.seat != Situations.Seat.DECK && s.seat != Situations.Seat.NONE;
        decoyReady = w.decoyCooldown <= 0f;
        decoyIn = decoyReady ? "" : Fmt.secs(Math.min(w.decoyCooldown, 9f));
        String k = d.key("DECOY");
        decoyKey = k.isEmpty() ? "F" : k.toUpperCase(Locale.ROOT);
        pd = w.pointDefenseActive;
        for (Mount m : w.mounts) if (m.kind == Kind.POINT_DEFENSE && m.engaging) pd = true;
    }

    private void emergency(HudData d, Situations s) {
        rows = 0;
        card = s.emergency;
        if (!card) return;
        Orders o = d.orderState;
        String pk = key(d, "ABANDON", "P"), bk = key(d, "CUT", "B");
        CaptainStatus c = d.captainStatus;
        // The suit's air first when it holds; then abandoning, which replaces disabled
        String second = "";
        if (s.suitAir) {
            title = "YOUR SUIT AIR " + Fmt.mmss(c.suitAir);
            row("FIND AIR", c.room.isEmpty() ? "OR A SUIT LOCKER" : "YOU ARE IN " + Fmt.cut(c.room, 10), "", false, false);
            if (s.abandoning) second = "ABANDONING · " + pk + " LAUNCHES";
            else if (s.disabled) second = "SHIP DISABLED · " + pk + " TWICE" + (o.towRefusal.isEmpty() ? " · " + bk + " TWICE" : "");
            if (!second.isEmpty()) row(second, "", "", false, false);
            return;
        }
        if (s.abandoning) {
            title = "ABANDONING SHIP";
            String crew = o.pods > 0 ? o.inPods + " OF " + Math.max(o.crewAboard, o.inPods) + " CREW" : "";
            String launch = Float.isNaN(o.podsLaunchIn) ? "" : "LAUNCH T-" + Rows.timeText(o.podsLaunchIn);
            row("IN THE PODS", crew + (crew.isEmpty() || launch.isEmpty() ? "" : " · ") + launch, "", false, false);
            row("LAUNCH NOW", "", pk, false, false);
            return;
        }
        String why = d.ctx.disabledReason.isEmpty() ? "" : ": " + d.ctx.disabledReason.toUpperCase(Locale.ROOT);
        title = Fmt.cut("SHIP DISABLED" + why, 30);
        row("ABANDON SHIP", o.abandonRefusal.isEmpty() ? "" : Fmt.cut(o.abandonRefusal, 21), pk, o.abandonArmed > 0f, true);
        if (o.towRefusal.isEmpty()) {
            String fee = o.towFee > 0 ? Cautions.group(o.towFee) + " CR" : "";
            String standing = o.towStanding > 0f ? Math.round(o.towStanding) + " STANDING" : "";
            row("CALL A TOW", fee + (fee.isEmpty() || standing.isEmpty() ? "" : " · ") + standing, bk, o.towArmed > 0f, true);
        }
    }

    private void row(String w, String c, String k, boolean arm, boolean guarded) {
        if (rows == X3_ROWS) return;
        what[rows] = w;
        cost[rows] = c;
        keys[rows] = k;
        armed[rows] = arm;
        twice[rows] = guarded;
        rows++;
    }

    private static String key(HudData d, String action, String fallback) {
        String k = d.key(action);
        return (k.isEmpty() ? fallback : k).toUpperCase(Locale.ROOT);
    }

    float p3Height() {
        return threat ? P3_H : 0f;
    }

    float x3Height() {
        return card ? 34f + rows * X3_ROW + 4f : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    void paintThreat(Filament f) {
        if (!threat) return;
        float x = Geo.TC_X, y = Geo.TOP_Y, w = P3_W;
        f.slab(x, y, w, P3_H, 5f, 1, true);
        // Line 1 at P3's size behind red glass: the count, MISSILES INBOUND, the soonest impact
        float cx = x + 10f;
        cx += f.seg7(cx, y + 5f, 22f, count, f.o().cells(1).red().full()) + 10f;
        cx += f.seg14(cx, y + 8f, 16f, plural ? "MISSILES INBOUND" : "MISSILE INBOUND", f.o().red().full()) + 14f;
        if (showTime) {
            cx += f.legend(cx, y + 26f, "T-", f.o().size(18f).red().norm()) + 2f;
            cx += f.seg7(cx, y + 5f, 22f, time, f.o().cells(2).right().red().full()) + 4f;
            cx += f.legend(cx, y + 26f, "S", f.o().size(18f).red().norm());
        }
        float dx = x + w - 10f - f.dmWidth(6, f.o().pitch(2f));
        Kit.fits(cx + 8f, dx, "P3 line 1");
        f.dm(dx, y + 10f, downText, f.o().pitch(2f).cells(6).red().norm());
        // Line 2: the decoy (not on deck), then point defense
        float b = y + 52f, lx = x + 10f;
        if (decoy) {
            lx += f.keycap(lx, b - 14f, decoyKey, 18f, 12f, decoyReady) + 6f;
            lx += f.legend(lx, b, "DECOY", f.o().dim()) + 6f;
            lx += f.legend(lx, b, "READY", f.o().lit(decoyReady).norm()) + 6f;
            if (!decoyReady) {
                lx += f.seg7(lx, b - 12f, 12f, decoyIn, f.o().cells(1).norm()) + 3f;
                lx += f.legend(lx, b, "S", f.o().dim());
            }
            lx += 20f;
        }
        f.legend(lx, b, "POINT DEFENSE ENGAGING", f.o().lit(pd).norm());
    }

    /** X3 under P3, with its top at {@code y}; {@code time} real seconds for the LEDs. */
    void paintCard(Filament f, float y, float time) {
        if (!card) return;
        float x = Geo.TC_X, w = Geo.TC_W, gw = X3_GLASS, h = x3Height();
        f.plate(x, y, w, h, 5f, 6f);
        f.module(x, y, gw, h, f.o().radius(2.5f));
        f.rectLit(x + 4f, y + 4f, gw - 8f, 28f, f.o().red().norm().r(1.5f));
        float tw = f.legendWidth(title, f.o().size(22f));
        f.knockout(x + gw / 2f - tw / 2f, y + 26f, title, f.o().size(22f));
        boolean blink = (time % 0.5f) < 0.25f;
        for (int i = 0; i < rows; i++) {
            float ry = y + 36f + i * X3_ROW, b = ry + 20f;
            boolean whole = what[i].length() <= 12;
            if (whole) {
                f.dm(x + 10f, b - 14f, what[i], f.o().pitch(2f).cells(12).norm());
            } else {
                // The second state as one line with its keys
                f.dm(x + 10f, b - 10.5f, Fmt.cut(what[i], 46), f.o().pitch(Geo.P).cells(46).norm());
            }
            float cx = x + 10f + f.dmWidth(12, f.o().pitch(2f)) + 14f;
            float twx = x + gw - 8f - f.legendWidth("TWICE", f.o());
            if (whole) Kit.fits(cx + f.dm(cx, b - 10.5f, cost[i], f.o().pitch(Geo.P).cells(21).dim()) + 8f, twx, "X3 row");
            if (twice[i]) f.legend(twx, b, "TWICE", f.o().dim());
            if (!keys[i].isEmpty()) {
                f.key(x + gw + 12f, ry + 2f, 40f, 24f, keys[i], f.o().size(14f).led(Filament.Led.AMBER, armed[i] && blink));
            }
        }
    }
}
