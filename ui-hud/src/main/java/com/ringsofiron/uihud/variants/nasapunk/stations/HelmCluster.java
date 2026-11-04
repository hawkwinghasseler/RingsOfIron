package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * The helm cluster (section 8.1), 480 x 160 in the band's middle: five fixed slots, top to bottom
 * Velocity, Reference, Next, Trigger and Δv, 28 apart. A slot with nothing to show goes dark in
 * place and the others never move. Through a docking approach Reference becomes the ports and Next
 * the approach (section 8.2); under warp only Next shows.
 */
final class HelmCluster extends NpDu {
    static final float W = StationsLayout.CLUSTER_W, H = StationsLayout.CLUSTER_H;

    enum Ref { NONE, LOCK, NAV, ORBIT, PORTS }

    enum Next { NONE, BURN, AUTOPILOT, APPROACH }

    boolean warp;
    /** The helm console has no power, or the ship none at all: the NO POWER face (section 5.1). */
    boolean noPower;
    // Velocity
    String frame = "ORBIT", speed = "", speedUnit = "", spin = "", control = "MANUAL";
    BoxState controlBox = BoxState.NORMAL;
    boolean assist = true, main, rcs;
    // Reference
    Ref ref = Ref.NONE;
    String refName = "", rng = "", closing = "", cpa = "", ap = "", pe = "";
    NpSymbols.Kind refKind = NpSymbols.Kind.NEUTRAL;
    String ports = "", clearance = "";
    BoxState clearanceBox = BoxState.OFF;
    // Next
    Next next = Next.NONE;
    String burn = "", burnWhen = "", burnDv = "", burnAlign = "", autopilot = "";
    boolean autopilotHeld, burning;
    /** The approach: range, closing and the safe closing at this range (m/s), offset, angle. */
    String approachRange = "", offset = "", angle = "";
    float approachClosing = Float.NaN, safe = Float.NaN;
    // Trigger
    boolean triggerLit, launchers = true, emitter, shieldsLowered;
    String guns = "", gunsMode = "", msl = "", mslWhen = "", decoy = "";
    Color gunsColor = NpColors.WHITE;
    BoxState gunsModeBox = BoxState.NORMAL;
    // Δv
    String dv = "--", home = "";
    float dvFrac = Float.NaN;
    final NpInstruments.Tape tape = new NpInstruments.Tape();

    HelmCluster(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "HELM";
        setSize(W, H);
        tape.notchColor = NpColors.WHITE;
    }

    private static float row(float gy, int i) {
        return gy + 2f + i * 28f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        if (noPower) {
            StDraw.dead(cv, gx, gy, gw, gh);
            return;
        }
        for (int i = 1; i < 5; i++) cv.line(gx + 4f, row(gy, i), gx + gw - 4f, row(gy, i), 1f, NpColors.GRATICULE);
        float L = gx + 10f, R = gx + gw - 10f;
        if (!warp) velocity(cv, L, R, row(gy, 0));
        if (!warp) reference(cv, L, R, row(gy, 1));
        nextEvent(cv, L, R, row(gy, 2));
        if (!warp) trigger(cv, L, R, row(gy, 3));
        if (!warp) deltaV(cv, L, R, row(gy, 4));
    }

    private static float bw(NpCanvas cv, String t) {
        return cv.width(t, 12f, true, 0.5f) + 12f;
    }

    private void velocity(NpCanvas cv, float L, float R, float y) {
        float fw = bw(cv, frame);
        NpHardware.box(cv, L, y + 5f, fw, 18f, frame, BoxState.NORMAL, 12f, false);
        float x = L + fw + 10f;
        float sw = cv.text(x, y + 22f, speed, 19f, true, NpColors.WHITE, 0f, NpCanvas.START);
        if (!speedUnit.isEmpty()) sw += 5f + cv.text(x + sw + 5f, y + 22f, speedUnit, 12f, false, NpColors.DIM, 0f, NpCanvas.START);
        if (!spin.isEmpty()) StDraw.lab(cv, x + sw + 12f, y + 21f, spin);
        // which thrusters fire, then assist and who has the stick
        StDraw.lens(cv, R - 36f, y + 6f, 36f, 16f, "RCS", rcs ? NpColors.GREEN : null);
        StDraw.lens(cv, R - 78f, y + 6f, 40f, 16f, "MAIN", main ? NpColors.GREEN : null);
        String as = assist ? "ASSIST" : "ASSIST OFF";
        float aw = bw(cv, as);
        NpHardware.box(cv, R - 86f - aw, y + 5f, aw, 18f, as, assist ? BoxState.ENGAGED : BoxState.CAUTION, 12f, false);
        float cw = bw(cv, control);
        NpHardware.box(cv, R - 92f - aw - cw, y + 5f, cw, 18f, control, controlBox, 12f, false);
    }

    private void reference(NpCanvas cv, float L, float R, float y) {
        float base = y + 19f;
        switch (ref) {
            case NAV:
            case LOCK: {
                float x = L;
                Color c = ref == Ref.NAV ? NpColors.MAGENTA : NpSymbols.colorOf(refKind);
                if (ref == Ref.NAV) {
                    cv.ring(x + 6f, y + 14f, 5.5f, 1.5f, NpColors.MAGENTA);
                    cv.disc(x + 6f, y + 14f, 2.8f, NpColors.MAGENTA);
                } else {
                    NpSymbols.shape(cv, refKind, x + 6f, y + 14f, 5f, c, 1.5f, true, 0f);
                }
                x += 18f;
                // what doesn't fit goes from the right: CPA first, then closing, then range
                String[][] parts = {{"RNG", rng}, {"CLOSING", closing}, {"CPA", cpa}};
                int keep = 3;
                float nameW = StDraw.bodyW(cv, refName);
                while (keep > 0 && x + Math.min(nameW, 150f) + 14f + partsW(cv, parts, keep) > R) keep--;
                float room = R - x - 14f - partsW(cv, parts, keep);
                String name = StDraw.fitName(cv, refName, room, StDraw.B, true, 0.3f);
                x += StDraw.body(cv, x, base, name, c) + 14f;
                for (int i = 0; i < keep; i++) {
                    if (parts[i][1].isEmpty()) continue;
                    x += StDraw.pair(cv, x, base, parts[i][0], parts[i][1], NpColors.WHITE) + 14f;
                }
                break;
            }
            case ORBIT: {
                float x = L + StDraw.pair(cv, L, base, "AP", ap, NpColors.WHITE) + 18f;
                StDraw.pair(cv, x, base, "PE", pe, NpColors.WHITE);
                break;
            }
            case PORTS: {
                float cw = clearance.isEmpty() ? 0f : StDraw.boxW(cv, clearance);
                StDraw.body(cv, L, base, StDraw.fitName(cv, ports, R - L - cw - 12f, StDraw.B, true, 0.3f), NpColors.WHITE);
                if (cw > 0f) StDraw.box(cv, R - cw, base, clearance, clearanceBox);
                break;
            }
            default: break;
        }
    }

    private static float partsW(NpCanvas cv, String[][] parts, int n) {
        float w = 0f;
        for (int i = 0; i < n; i++) if (!parts[i][1].isEmpty()) w += StDraw.pairW(cv, parts[i][0], parts[i][1]) + 14f;
        return w;
    }

    private void nextEvent(NpCanvas cv, float L, float R, float y) {
        float base = y + 19f;
        switch (next) {
            case BURN: {
                float lw = StDraw.lab(cv, L, base, "NEXT");
                NpSymbols.burnMark(cv, L + lw + 14f, y + 14f, 6f, NpColors.MAGENTA, 1.5f, burning);
                float x = L + lw + 28f;
                x += StDraw.body(cv, x, base, burn, NpColors.MAGENTA) + 10f;
                StDraw.body(cv, x, base, burnWhen, burning ? NpColors.GREEN : NpColors.WHITE);
                String right = burnAlign.isEmpty() ? burnDv : burnDv + "  " + burnAlign;
                StDraw.bodyEnd(cv, R, base, right, NpColors.WHITE);
                break;
            }
            case AUTOPILOT: {
                float lw = StDraw.lab(cv, L, base, "AUTOPILOT");
                StDraw.body(cv, L + lw + 8f, base, StDraw.fit(cv, autopilot, R - L - lw - 8f, StDraw.B, true, 0.3f), autopilotHeld ? NpColors.AMBER : NpColors.GREEN);
                break;
            }
            case APPROACH: {
                float x = L + StDraw.pair(cv, L, base, "RNG", approachRange, NpColors.WHITE) + 14f;
                // closing against the safe speed: the bar is twice the limit long, the limit marked
                float bx = x + StDraw.labW(cv, "CLOSING") + 6f, bwid = 96f;
                StDraw.lab(cv, x, base, "CLOSING");
                cv.fill(bx, y + 12f, bwid, 4f, NpColors.TRACK);
                if (!Float.isNaN(approachClosing) && !Float.isNaN(safe) && safe > 0f) {
                    float f = Math.max(0f, Math.min(1f, approachClosing / (2f * safe)));
                    Color c = approachClosing > 1.5f * safe ? NpColors.RED : approachClosing > safe ? NpColors.AMBER : NpColors.GREEN;
                    cv.fill(bx, y + 12f, bwid * f, 4f, c);
                }
                cv.line(bx + bwid / 2f, y + 8f, bx + bwid / 2f, y + 20f, 1.5f, NpColors.WHITE);
                float vx = bx + bwid + 6f;
                String cl = Float.isNaN(approachClosing) ? "--" : StDraw.speed(approachClosing);
                vx += StDraw.body(cv, vx, base, cl, NpColors.WHITE) + 14f;
                if (!offset.isEmpty()) vx += StDraw.pair(cv, vx, base, "OFF", offset, NpColors.WHITE) + 12f;
                if (!angle.isEmpty() && vx + StDraw.pairW(cv, "ANG", angle) <= R) StDraw.pair(cv, vx, base, "ANG", angle, NpColors.WHITE);
                break;
            }
            default: break;
        }
    }

    private void trigger(NpCanvas cv, float L, float R, float y) {
        if (!triggerLit) {
            // dark is not absent: the lenses sit unlit, a blank where the hull has no launcher
            StDraw.lens(cv, L, y + 6f, 64f, 16f, "GUNS", null);
            if (launchers) StDraw.lens(cv, L + 72f, y + 6f, 64f, 16f, "MSL", null);
            StDraw.lens(cv, L + 144f, y + 6f, 64f, 16f, "DECOY", null);
            return;
        }
        float base = y + 19f, x = L;
        if (!guns.isEmpty()) {
            x += StDraw.lab(cv, x, base, "GUNS") + 6f;
            x += StDraw.body(cv, x, base, guns, gunsColor) + 6f;
            if (!gunsMode.isEmpty()) x += StDraw.box(cv, x, base, gunsMode, gunsModeBox) + 14f;
            else x += 8f;
        }
        if (launchers && !msl.isEmpty()) {
            x += StDraw.lab(cv, x, base, "MSL") + 6f;
            x += StDraw.body(cv, x, base, msl, NpColors.WHITE) + 6f;
            x += StDraw.body(cv, x, base, mslWhen, NpColors.WHITE) + 14f;
        }
        x += StDraw.lab(cv, x, base, "DECOY") + 6f;
        StDraw.body(cv, x, base, decoy, NpColors.WHITE);
        if (emitter && shieldsLowered) {
            String t = "SHIELDS LOWERED";
            StDraw.box(cv, R - StDraw.boxW(cv, t), base, t, BoxState.CAUTION);
        }
    }

    private void deltaV(NpCanvas cv, float L, float R, float y) {
        float base = y + 19f;
        StDraw.lab(cv, L, base, "ΔV");
        cv.text(L + 120f, base + 0.5f, dv, 15f, true, NpColors.WHITE, 0f, NpCanvas.END);
        StDraw.lab(cv, L + 124f, base, "M/S");
        float bx = L + 168f, bwid = R - bx - 96f;
        if (!Float.isNaN(dvFrac)) NpInstruments.tape(cv, bx, base, bwid, dvFrac, tape);
        if (!home.isEmpty()) {
            StDraw.labEnd(cv, R - StDraw.bodyW(cv, home) - 6f, base, "HOME");
            StDraw.bodyEnd(cv, R, base, home, NpColors.WHITE);
        }
    }
}
