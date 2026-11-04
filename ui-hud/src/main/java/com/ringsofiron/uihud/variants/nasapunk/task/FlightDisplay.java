package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * FLT, the flight instrument (task spec section 6): one display, its mode named FMA style in a box,
 * in three rows split by graticule rules. CRUISE gives the frame and the speed in it, delta-v left on
 * a tape notched at what home takes and bracketed by what the plan needs, and the main engine and RCS
 * as lenses, lit while they fire. BURN gives the burn, its countdown, the delta-v still to go and who
 * flies it; DOCK the port, clearance, range and closing on the corridor's bands; DEPARTURE the berth
 * and the readiness checks, with the mode's keys.
 */
final class FlightDisplay extends NpDu {
    enum Mode { CRUISE, BURN, DOCK, DEPARTURE }

    /** A readiness check while docked: what it is, and "OK" or what is short. */
    static final class Check {
        final String label, value;
        final boolean ok;

        Check(String label, String value, boolean ok) {
            this.label = label;
            this.value = value;
            this.ok = ok;
        }
    }

    Mode mode = Mode.CRUISE;
    /** CRUISE: the frame the speed is in ("HIGHPORT", "ORBIT EARTH") and how far, or "". */
    String frame = "", frameRange = "";
    double speed = Double.NaN;
    /** Delta-v left, what the trip home takes and what the plan needs, m/s; NaN when unknown or none. */
    double dv = Double.NaN, home = Double.NaN, plan = Double.NaN;
    /** Thrust costs nothing: no tape, the figure reads FREE. */
    boolean freeThrust;
    boolean mainLit, rcsLit, assistOff;
    /** BURN: its name ("BURN 1/2"), who flies it when that is you or the computer (else ""). */
    String burn = "BURN", burnWho = "";
    /** Seconds to the engine lighting (negative or 0 once it burns), to the turn onto it, and the burn's time. */
    double burnIn = Double.NaN, turnIn = Double.NaN, burnTime = Double.NaN;
    /** Delta-v still to go on it, m/s, and the turn still needed, degrees. */
    double burnToGo = Double.NaN, pointing = Double.NaN;
    boolean burning;
    /** DOCK: the port ("PORT 1/2 M"), the clearance word and its colour. */
    String port = "", clearance = "";
    Color clearanceColor = NpColors.DIM;
    /** Range, m; closing, m/s; the safe closing speed; sideways offset, m; angle off, degrees. */
    double dockRange = Double.NaN, dockClosing = Double.NaN, safeSpeed = 1.0, lateral = Double.NaN, angle = Double.NaN;
    /** DEPARTURE: the station berthed at, the port, and the checks. */
    String berth = "", berthPort = "";
    final List<Check> checks = new ArrayList<>();
    /** The mode's keys, as key and verb pairs (DOCK, DEPARTURE). */
    final List<String[]> keys = new ArrayList<>();

    /** Its size, units. */
    static final float W = 440f, H = 116f;

    FlightDisplay(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "FLIGHT";
        setSize(W, H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 10f, R = gx + gw - 10f;
        switch (mode) {
            case BURN: burn(cv, gx, gy, gw, L, R); break;
            case DOCK: dock(cv, gx, gy, gw, L, R); break;
            case DEPARTURE: departure(cv, gx, gy, gw, L, R); break;
            default: cruise(cv, gx, gy, gw, L, R); break;
        }
    }

    /** The mode box at the top left; returns its width. */
    private float head(NpCanvas cv, float L, float gy, String name, NpHardware.BoxState state) {
        float bw = Math.max(76f, NpHardware.boxWidth(cv, name, 12f, 10f));
        NpHardware.box(cv, L, gy + 7f, bw, 20f, name, state, 12f, false);
        return bw;
    }

    private void rules(NpCanvas cv, float gx, float gy, float gw) {
        cv.line(gx + 4f, gy + 34f, gx + gw - 4f, gy + 34f, 1f, NpColors.GRATICULE);
        cv.line(gx + 4f, gy + 73f, gx + gw - 4f, gy + 73f, 1f, NpColors.GRATICULE);
    }

    private void engines(NpCanvas cv, float gy, float R) {
        NpHardware.lens(cv, R - 100f, gy + 78f, 48f, 18f, "MAIN", mainLit ? NpColors.GREEN : null, false, 12f);
        NpHardware.lens(cv, R - 48f, gy + 78f, 48f, 18f, "RCS", rcsLit ? NpColors.GREEN : null, false, 12f);
    }

    /** The mode's keys along the bottom row from {@code x}; returns where they end. */
    private float modeKeys(NpCanvas cv, float x, float gy) {
        for (String[] k : keys) x += NpHardware.keyHint(cv, x, gy + 79f, k[0], k[1], 12f, NpColors.DIM) + 16f;
        return x;
    }

    /** A body-size figure then its unit at the floor size, from {@code x}; returns the width drawn. */
    private static float num(NpCanvas cv, float x, float y, String v, String unit, Color c) {
        float w = cv.text(x, y, v, 15f, true, c, 0f, NpCanvas.START);
        return w + 4f + cv.text(x + w + 4f, y, unit, 12f, false, NpColors.DIM, 0f, NpCanvas.START);
    }

    private void cruise(NpCanvas cv, float gx, float gy, float gw, float L, float R) {
        head(cv, L, gy, "CRUISE", NpHardware.BoxState.ENGAGED);
        float fx = L + 90f;
        if (!frame.isEmpty()) {
            float fw = cv.text(fx, gy + 22f, frame, 12.5f, true, NpColors.WHITE, 0.4f, NpCanvas.START);
            if (!frameRange.isEmpty()) cv.text(fx + fw + 10f, gy + 22f, frameRange, 12f, true, NpColors.DIM, 0.3f, NpCanvas.START);
        }
        if (!Double.isNaN(speed)) {
            String[] v = NpFormat.speed(speed);
            cv.text(R, gy + 23f, v[1], 12f, false, NpColors.DIM, 0f, NpCanvas.END);
            cv.text(R - 40f, gy + 23f, v[0], 19f, true, NpColors.WHITE, 0f, NpCanvas.END);
        }
        rules(cv, gx, gy, gw);
        cv.text(L, gy + 62f, "ΔV", 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
        if (freeThrust) {
            cv.text(L + 104f, gy + 63f, "FREE", 15f, true, NpColors.WHITE, 0.3f, NpCanvas.END);
        } else if (Double.isNaN(dv)) {
            cv.text(L + 104f, gy + 63f, "--", 15f, true, NpColors.DIM, 0f, NpCanvas.END);
        } else {
            boolean low = !Double.isNaN(home) && dv < home * 1.25;
            cv.text(L + 104f, gy + 63f, NpFormat.mps(dv), 15f, true, low ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.END);
            cv.text(L + 110f, gy + 63f, "M/S", 12f, false, NpColors.DIM, 0f, NpCanvas.START);
            float bx = L + 160f, bw = gw - 200f;
            // the tape's full scale: the next round thousand above what is left, so the pointer has room
            double max = Math.max(1000.0, Math.ceil(Math.max(dv, Double.isNaN(home) ? 0.0 : home) / 1000.0 + 0.0001) * 1000.0);
            NpInstruments.Tape t = new NpInstruments.Tape();
            if (!Double.isNaN(home) && home > 0) t.notch = (float) (home / max);
            if (!Double.isNaN(plan) && plan > 0) {
                t.bracketA = (float) (Math.max(0.0, dv - plan) / max);
                t.bracketB = (float) (dv / max);
            }
            t.pointer = low ? NpColors.AMBER : NpColors.WHITE;
            NpInstruments.tape(cv, bx, gy + 64f, bw, (float) (dv / max), t);
            if (!Double.isNaN(home) && home > 0) {
                // the label sits right of the notch, or left of it when the notch is near the tape's end
                String hl = "HOME " + NpFormat.mps(home);
                float nx = bx + bw * t.notch, hw = cv.width(hl, 12f, true, 0.3f);
                if (nx + 6f + hw > R) cv.text(nx - 6f, gy + 51f, hl, 12f, true, NpColors.DIM, 0.3f, NpCanvas.END);
                else cv.text(nx + 6f, gy + 51f, hl, 12f, true, NpColors.DIM, 0.3f, NpCanvas.START);
            }
        }
        if (assistOff) cv.text(L, gy + 91f, "ASSIST OFF", 12f, true, NpColors.AMBER, 0.5f, NpCanvas.START);
        engines(cv, gy, R);
    }

    private void burn(NpCanvas cv, float gx, float gy, float gw, float L, float R) {
        float bw = head(cv, L, gy, burn, NpHardware.BoxState.GUIDANCE);
        NpSymbols.burnMark(cv, L + bw + 16f, gy + 17f, 6f, NpColors.MAGENTA, 1.5f, burning);
        String when = burning ? "BURNING" : Double.isNaN(burnIn) ? "" : NpFormat.tMinus(Math.max(0.0, burnIn));
        cv.text(L + bw + 30f, gy + 23f, when, 19f, true, NpColors.MAGENTA, 0f, NpCanvas.START);
        if (!burnWho.isEmpty()) cv.text(R, gy + 22f, burnWho, 12f, true, NpColors.DIM, 0.4f, NpCanvas.END);
        rules(cv, gx, gy, gw);
        cv.text(L, gy + 62f, "TO GO", 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
        if (!Double.isNaN(burnToGo)) num(cv, L + 50f, gy + 63f, NpFormat.mps(burnToGo), "M/S", NpColors.MAGENTA);
        cv.text(L + 166f, gy + 62f, "TIME", 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
        if (!Double.isNaN(burnTime)) cv.text(L + 206f, gy + 63f, NpFormat.clock(burnTime), 15f, true, NpColors.WHITE, 0f, NpCanvas.START);
        cv.text(L + 280f, gy + 62f, "ΔV", 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
        if (freeThrust) cv.text(L + 304f, gy + 63f, "FREE", 15f, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        else if (!Double.isNaN(dv)) {
            boolean short_ = !Double.isNaN(burnToGo) && dv < burnToGo;
            num(cv, L + 304f, gy + 63f, NpFormat.mps(dv), "M/S", short_ ? NpColors.AMBER : NpColors.WHITE);
        }
        // the bottom row: the turn onto the burn while it is still to come, else the pointing error
        if (!burning && !Double.isNaN(turnIn) && turnIn > 0) {
            cv.text(L, gy + 91f, "TURN " + NpFormat.tMinus(turnIn), 12f, true, NpColors.MAGENTA, 0.4f, NpCanvas.START);
        } else if (!Double.isNaN(pointing)) {
            boolean off = Math.abs(pointing) > 5.0;
            cv.text(L, gy + 91f, "POINTING " + Math.round(Math.abs(pointing)) + "°", 12f, true, off ? NpColors.AMBER : NpColors.GREEN, 0.4f, NpCanvas.START);
        }
        engines(cv, gy, R);
    }

    private void dock(NpCanvas cv, float gx, float gy, float gw, float L, float R) {
        head(cv, L, gy, "DOCK", NpHardware.BoxState.ENGAGED);
        float fx = L + 90f;
        float pw = cv.text(fx, gy + 22f, port, 12.5f, true, NpColors.WHITE, 0.4f, NpCanvas.START);
        if (!clearance.isEmpty()) cv.text(fx + pw + 10f, gy + 22f, clearance, 12f, true, clearanceColor, 0.3f, NpCanvas.START);
        if (!Double.isNaN(dockRange)) {
            String[] r = NpFormat.range(dockRange);
            NpInstruments.value(cv, R, gy + 23f, r[0], r[1], 19f, true, NpColors.WHITE);
        }
        rules(cv, gx, gy, gw);
        cv.text(L, gy + 62f, "CLOSING", 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
        if (!Double.isNaN(dockClosing)) {
            double safe = Double.isNaN(safeSpeed) || safeSpeed <= 0 ? 1.0 : safeSpeed;
            Color c = dockClosing > safe * 1.5 ? NpColors.RED : dockClosing > safe ? NpColors.AMBER : NpColors.WHITE;
            cv.text(L + 124f, gy + 63f, NpFormat.tenths(dockClosing), 15f, true, c, 0f, NpCanvas.END);
            cv.text(L + 130f, gy + 63f, "M/S", 12f, false, NpColors.DIM, 0f, NpCanvas.START);
            float bx = L + 170f, bw = gw - 210f;
            // the corridor's bands: safe up to the safe speed, caution to one and a half times it, warning beyond
            double max = safe * 2.0;
            NpInstruments.Tape t = new NpInstruments.Tape();
            t.band(0f, 0.5f, NpColors.GREEN).band(0.5f, 0.75f, NpColors.AMBER).band(0.75f, 1f, NpColors.RED);
            NpInstruments.tape(cv, bx, gy + 64f, bw, (float) Math.max(0.0, Math.min(1.0, dockClosing / max)), t);
        }
        float x = L;
        if (!Double.isNaN(lateral)) {
            x += cv.text(x, gy + 91f, "OFFSET", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START) + 6f;
            x += cv.text(x, gy + 91f, NpFormat.tenths(Math.abs(lateral)) + " M", 12f, true, Math.abs(lateral) > 2.0 ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.START) + 14f;
        }
        if (!Double.isNaN(angle)) {
            x += cv.text(x, gy + 91f, "ANGLE", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START) + 6f;
            x += cv.text(x, gy + 91f, Math.round(Math.abs(angle)) + "°", 12f, true, Math.abs(angle) > 10.0 ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.START) + 16f;
        }
        modeKeys(cv, x, gy);
        engines(cv, gy, R);
    }

    private void departure(NpCanvas cv, float gx, float gy, float gw, float L, float R) {
        head(cv, L, gy, "DOCKED", NpHardware.BoxState.ENGAGED);
        float fx = L + 90f;
        float bw = cv.text(fx, gy + 22f, berth, 12.5f, true, NpColors.WHITE, 0.4f, NpCanvas.START);
        if (!berthPort.isEmpty()) cv.text(fx + bw + 10f, gy + 22f, berthPort, 12f, true, NpColors.DIM, 0.3f, NpCanvas.START);
        rules(cv, gx, gy, gw);
        // the readiness checks across the middle row, each its label and OK or what is short
        float x = L;
        for (Check c : checks) {
            x += cv.text(x, gy + 62f, c.label, 12f, true, NpColors.DIM, 0.5f, NpCanvas.START) + 7f;
            x += cv.text(x, gy + 62.5f, c.value, 13f, true, c.ok ? NpColors.GREEN : NpColors.AMBER, 0.2f, NpCanvas.START) + 20f;
        }
        modeKeys(cv, L, gy);
    }
}
