package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * CRS, the crisis panel at the top in the middle under the band (functional-task 6, CRS). The band
 * already carries the title and the reason, so the panel starts at what the crew is doing about it and
 * how long, then the choices with their terms, each behind its key: wait; call a tow (B twice: the fee
 * and the standing, or why it is refused); abandon ship (P twice: the pods and their seats against the
 * crew). Stranded, with a tow on offer: the tow and the delta-v short. Abandoning: the pods' countdown,
 * who is in them, and where the captain is against the nearest free seat, with P to launch now. On
 * every post and over screens, since it is a crisis. A first press of a guarded key arms it; the row
 * then asks for the second.
 */
final class Crisis {
    enum Kind {
        NONE, DISABLED, STRANDED, ABANDONING
    }

    static final int ROWS = 5;
    static final float ROW = 20f;

    Kind kind = Kind.NONE;
    final String[] keys = new String[ROWS], lines = new String[ROWS];
    final boolean[] armed = new boolean[ROWS], refused = new boolean[ROWS], twice = new boolean[ROWS];
    int rows;
    String doing = "", countdown = "";

    void update(HudData d, Posts p) {
        rows = 0;
        doing = "";
        countdown = "";
        kind = p.abandoning ? Kind.ABANDONING : p.disabled ? Kind.DISABLED : p.stranded ? Kind.STRANDED : Kind.NONE;
        Orders o = d.orderState;
        String b = Catalogue.key(d, "CUT", "B"), pk = Catalogue.key(d, "ABANDON", "P");
        switch (kind) {
            case DISABLED:
                doing = doing(d);
                row("", "WAIT" + (doing.isEmpty() ? "" : ": " + doing), false, false, false);
                tow(o, b);
                abandon(o, pk);
                break;
            case STRANDED:
                double left = d.flightStatus.deltaVLeft, home = d.flightStatus.deltaVHome;
                doing = Double.isNaN(left) || Double.isNaN(home) ? "" : left >= home ? ""
                        : "ΔV " + Catalogue.group(left) + " M/S, HOME TAKES " + Catalogue.group(home);
                tow(o, b);
                break;
            case ABANDONING:
                countdown = Fmt.secs(o.podsLaunchIn);
                doing = o.inPods + " OF " + o.crewAboard + " IN THE PODS, " + o.freeSeats + (o.freeSeats == 1 ? " SEAT" : " SEATS") + " FREE";
                String you = o.captainInPod ? "YOU ARE IN A POD"
                        : Float.isNaN(o.podDistance) ? "NO POD WITH A FREE SEAT" : "YOU ARE " + Math.round(o.podDistance) + " M FROM THE POD";
                row("", you, false, false, false);
                row(pk, "LAUNCH NOW", false, false, false);
                break;
            default:
        }
    }

    /** What the crew is doing about a disabled ship, and how long: "1 RESETTING THE REACTOR, BACK IN 20 S". */
    static String doing(HudData d) {
        for (SystemRow r : d.powerFrame.faults) {
            if (!"REACTOR".equals(r.system)) continue;
            Alert a = d.alerts.get("sys:REACTOR:" + r.tileX + "," + r.tileY);
            String who = a == null ? "" : Catalogue.resetting(a.handling, r.fault);
            boolean back = (r.fault == Fault.SHORTED || r.fault == Fault.STUNNED) && !Float.isNaN(r.timer);
            String when = back ? "BACK IN " + Fmt.secs(r.timer) + " S" : "";
            if (who.isEmpty()) return when.isEmpty() ? "" : "REACTOR " + when;
            return who + " THE REACTOR" + (when.isEmpty() ? "" : ", " + when);
        }
        return "";
    }

    private void tow(Orders o, String key) {
        if (!o.towRefusal.isEmpty()) {
            row(key, "CALL A TOW: " + Words.cut(o.towRefusal, 40), false, true, true);
            return;
        }
        String terms = (o.towFee > 0 ? Catalogue.group(o.towFee) + " CR" : "")
                + (o.towStanding != 0f ? (o.towFee > 0 ? ", " : "") + Math.round(-Math.abs(o.towStanding)) + " STANDING" : "");
        row(key, o.towArmed > 0f ? "CALL A TOW: PRESS AGAIN" : "CALL A TOW" + (terms.isEmpty() ? "" : "  " + terms), o.towArmed > 0f, false, true);
    }

    private void abandon(Orders o, String key) {
        if (!o.abandonRefusal.isEmpty()) {
            row(key, "ABANDON SHIP: " + Words.cut(o.abandonRefusal, 40), false, true, true);
            return;
        }
        String seats = o.pods + (o.pods == 1 ? " POD, " : " PODS, ") + o.freeSeats + " SEATS FOR " + o.crewAboard + " CREW";
        row(key, o.abandonArmed > 0f ? "ABANDON SHIP: PRESS AGAIN" : "ABANDON SHIP  " + seats, o.abandonArmed > 0f, false, true);
    }

    private void row(String key, String line, boolean isArmed, boolean isRefused, boolean guarded) {
        if (rows == ROWS) return;
        keys[rows] = key;
        lines[rows] = line.toUpperCase(Locale.ROOT);
        armed[rows] = isArmed;
        refused[rows] = isRefused;
        twice[rows] = guarded;
        rows++;
    }

    /** The keys the panel shows, for the key strip. */
    void shownKeys(Array<String> out) {
        for (int i = 0; i < rows; i++) if (!keys[i].isEmpty()) out.add(keys[i]);
    }

    float height() {
        if (kind == Kind.NONE) return 0f;
        int n = rows + (doing.isEmpty() || kind == Kind.DISABLED ? 0 : 1);
        return 10f + Math.max(1, n) * ROW + (kind == Kind.ABANDONING ? 30f : 0f);
    }

    // ------------------------------------------------------------------ painting, from the top middle origin

    void paint(Filament f, float time) {
        if (kind == Kind.NONE) return;
        float x = Geo.CRS_X, y = Geo.CRS_Y, w = Geo.CRS_W, h = height();
        f.plate(x, y, w, h, 6f, 8f);
        f.module(x, y, w, h, f.o().filaments(Math.max(1, Math.round(h / 34f))));
        float ry = y + 5f;
        float tx = x + 54f;
        int cells = Geo.fit(tx, x + w - 8f);
        if (kind == Kind.ABANDONING) {
            float cx = x + 10f + f.legend(x + 10f, ry + 24f, "PODS LAUNCH IN", f.o().red().dim()) + 8f;
            cx += f.seg7(cx, ry + 2f, 22f, countdown, f.o().cells(3).right().red().full()) + 4f;
            f.legend(cx, ry + 24f, "S", f.o().red().dim());
            ry += 30f;
        }
        if (!doing.isEmpty() && kind != Kind.DISABLED) {
            f.dm(x + 10f, ry + 3f, Words.cut(doing, Geo.fit(x + 10f, x + w - 8f)), f.o().pitch(Geo.P).cells(Geo.fit(x + 10f, x + w - 8f)).red().norm());
            ry += ROW;
        }
        boolean blink = (time % 0.6f) < 0.3f;
        for (int i = 0; i < rows; i++) {
            // A guarded order's key twice: the first press arms it, the second gives it
            if (!keys[i].isEmpty()) {
                float kx = x + 10f + f.keycap(x + 10f, ry, keys[i], 16f, 12f, armed[i] && blink) + 3f;
                if (twice[i]) f.keycap(kx, ry, keys[i], 16f, 12f, false);
            }
            float level = refused[i] ? Drive.DIM : armed[i] ? (blink ? Drive.FULL : Drive.NORM) : Drive.NORM;
            f.dm(tx, ry + 2.75f, Words.cut(lines[i], cells), f.o().pitch(Geo.P).cells(cells).red().level(level));
            ry += ROW;
        }
    }
}
