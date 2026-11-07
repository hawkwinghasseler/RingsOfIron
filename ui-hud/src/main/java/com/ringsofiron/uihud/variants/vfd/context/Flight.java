package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * H1, the flight display, in the bottom rack's tower (functional-context 7, H1): the reference frame
 * with AUTO and its name, the speed in it in large seven segments, six thrust lamps round a hull
 * glyph, ASSIST, and delta-v with the propellant as its bar. Under warp it keeps the frame and delta-v.
 * In an approach H3, the docking display, takes its place and grows the tower: the port line, the
 * alignment picture, the closing gauge against the safe speed, the time to contact, the capture
 * state and the helm's dock order, with H1's thrust, assist and delta-v as a strip along its foot.
 */
final class Flight {
    static final String[] FRAMES = {"ORBIT", "NAV", "LOCK", "PORT"};
    static final int ORBIT = 0, NAV = 1, LOCK = 2, PORT = 3;
    /** H3's alignment picture: within tolerance under the first offset and angle, far off past the second. */
    static final float LATERAL_OK = 1.5f, LATERAL_FAR = 5f, ANGLE_OK = 6f, ANGLE_FAR = 20f;

    boolean on, docking, warp;
    int frame = ORBIT;
    String name = "", speed = "", dv = "", prop = "";
    float propFrac;
    boolean main, retro, strafeL, strafeR, turnL, turnR, assist;

    // H3
    String portLine = "", range = "", rangeUnit = "M", closing = "", safe = "", contact = "", lateral = "", angle = "", helmLine = "";
    boolean armed, cleared, refused, captured, docked;
    /** The closing gauge: closing against twice the safe speed, and the safe mark, 0..1. */
    float gauge, safeMark = 0.5f;
    /** The gauge's state: 0 safe, 1 fast, 2 danger (the corridor's three, W4). */
    int closingState;
    /** The alignment picture's dot, -1..1 each way, and its state (0 within tolerance, 1 off, 2 far off). */
    float alignX, alignY;
    int alignState;
    boolean aligned;

    void update(HudData d, Situations s) {
        on = s.seat == Situations.Seat.HELM && !s.docked && !s.latched && d.flightStatus.present;
        docking = on && s.approach;
        warp = s.warp;
        if (!on) return;
        FlightStatus fl = d.flightStatus;
        DockingApproach k = d.docking;
        // The reference frame (4.5): the port in an approach, else the game's frame
        if (s.approach) frame = PORT;
        else {
            switch (fl.frame) {
                case TARGET: frame = LOCK; break;
                case NAV:
                case STATION: frame = NAV; break;
                default: frame = ORBIT;
            }
        }
        name = frame == PORT ? Fmt.name(k.other, 16) : Fmt.name(fl.frameName, 16);
        float v = frame == PORT ? k.closing : fl.speed;
        speed = speed(v);
        main = fl.forward > 0.05f || fl.mainEngine;
        retro = fl.forward < -0.05f;
        strafeL = fl.strafe > 0.05f;
        strafeR = fl.strafe < -0.05f;
        turnL = fl.turn > 0.05f;
        turnR = fl.turn < -0.05f;
        assist = fl.rotationAssist;
        dv = Double.isInfinite(fl.deltaVLeft) ? "9999" : Double.isNaN(fl.deltaVLeft) ? "" : Fmt.fit(Math.round(fl.deltaVLeft), 4, 0);
        float p = d.shipStatus.propellant;
        propFrac = Float.isNaN(p) ? 0f : Math.max(0f, Math.min(1f, p));
        prop = Float.isNaN(p) ? "" : Integer.toString(Math.round(propFrac * 100f));
        if (docking) docking(d, k);
    }

    /** A speed in five cells: "4.2" under 10 m/s, whole metres a second above (5.4). */
    static String speed(float v) {
        if (Float.isNaN(v)) return "";
        float a = Math.abs(v);
        return a < 9.95f ? Fmt.fit(a, 5, 1) : Fmt.fit(Math.round(a), 5, 0);
    }

    private void docking(HudData d, DockingApproach k) {
        String ours = (k.ports > 1 ? k.port + 1 + " OF " + k.ports + " " : "") + Fmt.cut(k.portSize, 2);
        String theirs = Fmt.name(k.other, 12) + (k.otherPort > 0 ? " #" + k.otherPort : "");
        portLine = Fmt.cut((ours.isEmpty() ? "" : ours + " ") + "→ " + theirs, 27);
        armed = k.armed;
        cleared = k.clearance == Clearance.GRANTED;
        refused = k.clearance == Clearance.DENIED || k.clearance == Clearance.NO_PORT;
        captured = k.capture == Capture.CAPTURED;
        docked = k.capture == Capture.DOCKED;
        range = Fmt.range(k.range, 4);
        rangeUnit = Fmt.km(k.range) ? "KM" : "M";
        closing = Float.isNaN(k.closing) ? "" : Fmt.fit(Math.abs(k.closing), 4, 1);
        safe = Float.isNaN(k.safeSpeed) ? "" : Fmt.fit(k.safeSpeed, 3, 1);
        float c = Float.isNaN(k.closing) ? 0f : Math.max(0f, k.closing), sp = Float.isNaN(k.safeSpeed) || k.safeSpeed <= 0f ? 1f : k.safeSpeed;
        gauge = Math.min(1f, c / (2f * sp));
        safeMark = 0.5f;
        closingState = c >= 2f * sp ? 2 : c > sp ? 1 : 0;
        contact = c > 0.05f && !Float.isNaN(k.range) ? Fmt.secs(k.range / c) : "";
        float lat = Float.isNaN(k.lateral) ? 0f : k.lateral, ang = Float.isNaN(k.angleDeg) ? 0f : k.angleDeg;
        lateral = Float.isNaN(k.lateral) ? "" : Fmt.fit(Math.abs(k.lateral), 3, 1);
        angle = Float.isNaN(k.angleDeg) ? "" : Integer.toString(Math.round(Math.abs(k.angleDeg)));
        alignX = Math.max(-1f, Math.min(1f, lat / LATERAL_FAR));
        alignY = Math.max(-1f, Math.min(1f, ang / ANGLE_FAR));
        boolean far = Math.abs(lat) > LATERAL_FAR || Math.abs(ang) > ANGLE_FAR;
        aligned = Math.abs(lat) <= LATERAL_OK && Math.abs(ang) <= ANGLE_OK;
        alignState = aligned ? 0 : far ? 2 : 1;
        helmLine = d.orderState.helm == HelmOrder.DOCK ? Fmt.cut(d.orderState.helmLine.isEmpty() ? "HELM IS DOCKING"
                : d.orderState.helmLine.replaceFirst("^Helm: ", "Helm is "), 42).toUpperCase(Locale.ROOT) : "";
    }

    // ------------------------------------------------------------------ painting, on the grid

    /** The flight display's glass, lit or retired (docked or latched: the glass stays, unpowered). */
    void paint(Filament f, Rack.Tower tower) {
        if (tower == Rack.Tower.FLIGHT) paintH1(f);
        else if (tower == Rack.Tower.DOCKING) paintH3(f);
    }

    private void paintH1(Filament f) {
        float x = Geo.H1_X, y = Geo.H1_Y, w = Geo.H1_W;
        f.module(x, y, w, Geo.H1_H, f.o().filaments(3));
        if (!on) return;
        float ya = y + 20f;
        float ax = frames(f, x + 10f, ya);
        ax += f.legend(ax + 2f, ya, "AUTO", f.o().lit(true).dim().box());
        float nx = x + w - 10f - f.dmWidth(16, f.o().pitch(Geo.P));
        Kit.fits(ax + 6f, nx, "H1 frame row");
        f.dm(nx, ya - 10.5f, name, f.o().pitch(Geo.P).cells(16).norm());
        float yb = y + 30f;
        if (!warp) {
            float sw = f.seg7(x + 10f, yb, 34f, speed, f.o().cells(5).right().norm());
            f.legend(x + 16f + sw, yb + 34f, "M/S", f.o().dim());
            thrust(f, x + 238f, yb + 17f, 1.8f);
            f.legend(x + 306f, yb + 22f, "ASSIST", f.o().lit(assist).norm().box());
        }
        deltaV(f, x + 10f, y + 88f, x + w - 10f);
    }

    /** The frame tags: the one in use lit. Returns the end x. */
    private float frames(Filament f, float x, float y) {
        float[] lit = new float[FRAMES.length];
        for (int i = 0; i < lit.length; i++) lit[i] = i == frame ? Drive.NORM : Float.NaN;
        return Kit.states(f, x, y, FRAMES, lit, 13f, 8f);
    }

    /** ΔV in seven segments, the propellant as its bar with the pips, and its share. */
    private void deltaV(Filament f, float x, float b, float right) {
        float cx = x;
        cx += f.legend(cx, b, "ΔV", f.o().dim()) + 5f;
        cx += f.seg7(cx, b - 15f, 15f, dv, f.o().cells(4).right().norm()) + 5f;
        cx += f.legend(cx, b, "M/S", f.o().dim()) + 16f;
        float ex = right - 38f, bw = ex - 12f - cx;
        Kit.Sev ps = Kit.dbar(f, cx, b - 9f, bw, 7f, 36, propFrac, Drive.DIM, Float.NaN);
        Kit.pip(f, cx - 10f, b - 5.5f, Phosphor.AMBER, ps == Kit.Sev.CAUTION, 1.5f);
        Kit.pip(f, cx - 5f, b - 5.5f, Phosphor.RED, ps == Kit.Sev.WARNING, 1.5f);
        float pw = f.seg7(ex, b - 12f, 12f, prop, f.o().cells(3).right().dim());
        f.legend(ex + pw + 3f, b, "%", f.o().dim());
    }

    private static final String[] TRI_R = {"#..", "##.", "###", "##.", "#.."}, TRI_L = {"..#", ".##", "###", ".##", "..#"};
    private static final String[] TRI_U = {"..#..", ".###.", "#####"}, TRI_D = {"#####", ".###.", "..#.."};

    /** Six thrust lamps round a hull glyph: main fore and aft, the two turns, the two strafes. */
    private void thrust(Filament f, float tx, float ty, float p) {
        f.icon("ship", tx - 10f * p / 1.8f, ty - 10f * p / 1.8f, 20f * p / 1.8f, f.o().dim().lit(false));
        tri(f, tx - 20f * p / 1.8f, ty, TRI_R, main, p);
        tri(f, tx + 22f * p / 1.8f, ty, TRI_L, retro, p);
        tri(f, tx, ty - 15f * p / 1.8f, TRI_D, strafeR, p);
        tri(f, tx, ty + 15f * p / 1.8f, TRI_U, strafeL, p);
        tri(f, tx + 18f * p / 1.8f, ty - 13f * p / 1.8f, TRI_D, turnL, p);
        tri(f, tx + 18f * p / 1.8f, ty + 13f * p / 1.8f, TRI_U, turnR, p);
    }

    private static void tri(Filament f, float x, float y, String[] rows, boolean on, float p) {
        if (on) {
            f.sprite(x, y, rows, f.o().centred().pitch(p).norm());
            return;
        }
        float d = p * 0.8f, cx = x - rows[0].length() * p / 2f, cy = y - rows.length * p / 2f;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == '#') f.dot(cx + c * p + p / 2f, cy + r * p + p / 2f, f.o().size(d).ghostOnly().k(1.6f));
            }
        }
    }

    private void paintH3(Filament f) {
        float x = Geo.H3_X, y = Geo.H3_Y, w = Geo.H3_W, h = Geo.H3_H;
        f.module(x, y, w, h, f.o().filaments(4));
        if (!on) return;
        // The port line: ours n of m and its size, theirs; armed and the clearance
        float b1 = y + 18f;
        f.legend(x + 10f, b1, "PORT", f.o().dim());
        f.dm(x + 46f, b1 - 10.5f, portLine, f.o().pitch(Geo.P).cells(27).norm());
        float sx = Kit.states(f, x + 46f + 27 * Geo.CELL + 4f, b1, new String[] {"ARMED"}, new float[] {armed ? Drive.NORM : Float.NaN}, 13f, 6f);
        f.legend(sx, b1, refused ? "REFUSED" : "CLEARED", f.o().lit(cleared || refused).norm().color(refused ? Phosphor.AMBER : Phosphor.ZINC));

        // The alignment picture: a crosshair of dots, the port's offset as a dot, its state by colour
        float px = x + 10f, py = y + 28f, ps = 66f, pcx = px + ps / 2f, pcy = py + ps / 2f;
        f.module(px, py, ps, ps, f.o().filaments(0).noGlare().radius(1.5f));
        for (float t = -28f; t <= 28.1f; t += 4f) {
            f.dot(pcx + t, pcy, f.o().size(1.1f).dim());
            f.dot(pcx, pcy + t, f.o().size(1.1f).dim());
        }
        Kit.ringDots(f, pcx, pcy, ps * 0.5f * LATERAL_OK / LATERAL_FAR + 2f, 3f, 1.1f, Phosphor.ZINC, Drive.DIM);
        Phosphor ac = alignState == 0 ? Phosphor.ZINC : alignState == 1 ? Phosphor.AMBER : Phosphor.RED;
        float dx = pcx + alignX * 27f, dy = pcy + alignY * 27f;
        f.sprite(dx, dy, new String[] {".#.", "###", ".#."}, f.o().centred().pitch(2f).color(ac).full());

        // The closing gauge: the range, the closing speed, against twice the safe speed
        float gx = x + 90f, gw = w - 100f, b2 = y + 50f;
        float cx = gx;
        cx += f.seg7(cx, b2 - 22f, 22f, range, f.o().cells(4).right().norm()) + 4f;
        cx += f.legend(cx, b2, rangeUnit, f.o().dim()) + 16f;
        cx += f.legend(cx, b2, "CLOSING", f.o().dim()) + 6f;
        Phosphor cc = closingState == 0 ? Phosphor.ZINC : closingState == 1 ? Phosphor.AMBER : Phosphor.RED;
        cx += f.seg7(cx, b2 - 22f, 22f, closing, f.o().cells(4).right().color(cc).level(closingState == 0 ? Drive.NORM : Drive.FULL)) + 4f;
        Kit.fits(cx + f.legend(cx, b2, "M/S", f.o().dim()), x + w - 10f, "H3 closing row");
        Filament.Zone zone = (i, n) -> i >= n * 0.99f ? Phosphor.RED : i >= n * 0.5f ? Phosphor.AMBER : Phosphor.ZINC;
        f.bar(gx, y + 58f, gw, 8f, 40, gauge, f.o().zone(zone).norm());
        float mx = gx + gw * safeMark;
        f.rectLit(mx - 0.6f, y + 55f, 1.2f, 14f, f.o().norm());
        float b3 = y + 86f;
        cx = gx;
        cx += f.legend(cx, b3, "SAFE", f.o().dim()) + 4f;
        cx += f.seg7(cx, b3 - 12f, 12f, safe, f.o().cells(3).right().norm()) + 4f;
        cx += f.legend(cx, b3, "M/S", f.o().dim()) + 14f;
        cx += f.legend(cx, b3, "CONTACT T-", f.o().dim()) + 3f;
        cx += f.seg7(cx, b3 - 12f, 12f, contact, f.o().cells(3).right().norm()) + 3f;
        cx += f.legend(cx, b3, "S", f.o().dim()) + 14f;
        Kit.fits(Kit.states(f, cx, b3, new String[] {"CAPTURED", "DOCKED"},
                new float[] {captured ? Drive.NORM : Float.NaN, docked ? Drive.NORM : Float.NaN}, 13f, 6f), x + w - 4f, "H3 contact row");
        float b4 = y + 106f;
        cx = gx;
        cx += f.legend(cx, b4, "OFFSET", f.o().dim()) + 4f;
        cx += f.seg7(cx, b4 - 12f, 12f, lateral, f.o().cells(3).right().color(ac).norm()) + 3f;
        cx += f.legend(cx, b4, "M", f.o().dim()) + 14f;
        cx += f.legend(cx, b4, "ANGLE", f.o().dim()) + 4f;
        cx += f.seg7(cx, b4 - 12f, 12f, angle, f.o().cells(2).right().color(ac).norm()) + 2f;
        cx += f.legend(cx, b4, "°", f.o().dim()) + 14f;
        f.legend(cx, b4, "ALIGNED", f.o().lit(aligned).norm());
        // The helm's dock order, then H1's thrust, assist and delta-v along the foot
        f.dm(x + 10f, y + 109f, helmLine, f.o().pitch(Geo.P).cells(42).norm().noGhost());
        float b5 = y + 140f;
        thrust(f, x + 30f, b5 - 5f, 1.2f);
        f.legend(x + 60f, b5, "ASSIST", f.o().lit(assist).norm().box());
        deltaV(f, x + 120f, b5, x + w - 10f);
    }
}
