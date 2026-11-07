package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z8, the flight pod (FLT): one instrument with four modes, hidden in rails warp, and a mode strip that
 * reads like a gear indicator. DEPARTURE while docked (the berth, undock, four readiness checks), DOCK
 * while lining up on a port, BURN when a planned burn is near or being flown, else CRUISE (the frame,
 * delta-v left against home, the speed). At the helm, while an order stands, the helm line (HLM) rides
 * on top as its first line.
 */
final class FlightPod extends YardPart {
    enum Mode {
        DEPARTURE("DEPART", 51f), DOCK("DOCK", 36f), BURN("BURN", 36f), CRUISE("CRUISE", 51f);

        final String label;
        final float cw;

        Mode(String label, float cw) {
            this.label = label;
            this.cw = cw;
        }
    }

    static final float W = 460f, H = 120f, HLM_H = 26f;
    private static final float X0 = 410f, Y0 = 642f, SILL = 762f;
    private static final Color INK = Yard.Finish.YARD.ink, LABEL_DIM = Yard.hex("6B6254");

    private final TaskFrame f;
    Mode mode = Mode.CRUISE;
    /** The helm line rides on top: an order or the autopilot stands. */
    boolean helmLine;
    /** No reactor feeds anything: the pod runs on its battery. */
    boolean brown;

    FlightPod(TaskFrame f) {
        super(X0, Y0 - HLM_H);
        this.f = f;
    }

    /** The mode that applies, first in the spec's order: a burn being flown beats DOCK. */
    static Mode modeFor(TaskFrame f) {
        HudData d = f.data;
        if (f.docked) return Mode.DEPARTURE;
        NavReadout nav = d.navReadout;
        boolean burnNear = nav.burnsLeft > 0 && !Float.isNaN(nav.burnIn) && nav.burnIn <= 180f;
        if (d.ctx.burnFlown || nav.burning) return Mode.BURN;
        DockingApproach dk = d.docking;
        boolean dockWanted = dk.present && !Float.isNaN(dk.range) && dk.range <= DockingApproach.RANGE
                && (dk.armed || dk.clearance != Clearance.NONE || d.ctx.helmOrder.toLowerCase(Locale.ROOT).contains("dock"));
        if (dockWanted) return Mode.DOCK;
        if (burnNear) return Mode.BURN;
        return Mode.CRUISE;
    }

    /** The pod's height: the instrument, and the helm line when it rides on top. */
    float height() {
        return SILL - Y0 + (helmLine ? HLM_H : 0f);
    }

    @Override
    protected float top() {
        return helmLine ? Y0 - HLM_H : Y0;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float top = helmLine ? Y0 - HLM_H : Y0;
        p.unit(X0, top, W, SILL - top, Yard.Finish.YARD, "b");
        if (helmLine) {
            p.legend("HELM", X(14), Y(-7.5f), INK);
            p.flaps(X(52), Y(-20), YardText.fit(HelmPod.who(d), 8), 8, 8.4f, 16f, 13f, Yard.FLAP_TEXT);
            p.tube(X(126), Y(-20), 322f, 16f, 2f, 2.5f, false);
            p.tube(HelmPod.orderFit(d, W - 154f), X(134), Y(-7.5f), 13f, Yard.P_AMBER, false);
            p.tubeOver(X(126), Y(-20), 322f, 16f);
            p.rect(X(10), Y(1) - 0.5f, 440f, 1f, Yard.hex("9F957C"));
        }
        modeStrip(p, X(14), Y(8));
        switch (mode) {
            case CRUISE: cruise(p, d.flightStatus); break;
            case BURN: burn(p, d); break;
            case DOCK: dock(p, d); break;
            case DEPARTURE: departure(p, d); break;
        }
        if (brown) {
            // No reactor feeds anything: the instrument lighting fails, the drums hold, the pod runs on its battery
            p.rect(X(5), Y(3), 450f, SILL - Y(3) - 2f, Yard.hex("140D06"), 0.38f);
            p.lamp(X(232), Y(88), 4f, Yard.AMBER_LAMP, true);
            p.textR(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.LEGEND, 12f, "BATT", X(224), Y(92.5f), Yard.hex("EFE2C4"));
        }
    }

    private static float X(float v) {
        return X0 + v;
    }

    private static float Y(float v) {
        return Y0 + v;
    }

    private boolean lit(boolean on) {
        return !brown && on;
    }

    private void modeStrip(YardPaint p, float x, float y) {
        float w = 4f;
        for (Mode m : Mode.values()) w += m.cw;
        w += (Mode.values().length - 1) * 1.5f;
        p.rrect(x - 2f, y - 2f, w, 22f, 2.5f, Yard.BAKELITE);
        float xx = x + 1f;
        for (Mode m : Mode.values()) {
            boolean on = m == mode;
            if (on) p.glowRect(xx, y, m.cw, 18f, 5f, Yard.WHITE_LAMP.on, brown ? 0.15f : 0.45f);
            p.rrect(xx, y, m.cw, 18f, 1f, on ? (brown ? Yard.hex("8F8A80") : Yard.WHITE_LAMP.on) : Yard.hex("2C2824"));
            p.textC(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.LEGEND, 12f, m.label, xx + m.cw / 2f, y + 13.2f, on ? Yard.hex("241A10") : Yard.hex("6F685E"));
            xx += m.cw + 1.5f;
        }
    }

    // --- CRUISE ----------------------------------------------------------------------------

    private void cruise(YardPaint p, FlightStatus fs) {
        // Main engine and RCS are indicators, lit while firing; rotation assist lights only when it is off
        p.lamp(X(214), Y(17), 4.5f, Yard.WHITE_LAMP, lit(fs.mainEngine));
        p.legend("MAIN", X(223), Y(21.5f), INK);
        p.lamp(X(264), Y(17), 4.5f, Yard.WHITE_LAMP, lit(fs.rcs));
        p.legend("RCS", X(273), Y(21.5f), INK);
        p.push(X(318), Y(8), 130f, 18f, new String[]{"ROT ASSIST OFF"}, Yard.WHITE_LAMP, lit(fs.present && !fs.rotationAssist), YardPaint.Kind.INDICATOR, false, false);
        // The frame: what the speed is measured against (its kind and name on flaps), and how far it is
        p.legend("FRAME", X(14), Y(51.5f), INK);
        p.flaps(X(56), Y(38), frameKind(fs.frame), 3, 9f, 18f, 13f, Yard.FLAP_TEXT);
        p.flaps(X(88), Y(38), YardText.fit(fs.frameName, 10), 10, 7.6f, 18f, 12.5f, Yard.FLAP_TEXT);
        if (fs.present && !Float.isNaN(fs.frameDistance)) {
            // The distance's drums end at a fixed edge, so KM and the next field never move
            p.drumsR(X(214), Y(38), YardText.km(fs.frameDistance).trim(), 11f, 17f, 15f, 44f, false);
            p.legend("KM", X(219), Y(51.5f), INK);
        }
        p.legend("ΔV LEFT", X(244), Y(51.5f), INK);
        p.drums(X(293), Y(37), dv(fs.deltaVLeft), 12f, 19f, 15f, true);
        p.legend("M/S", X(345), Y(51.5f), INK);
        p.homeBug(X(377), Y(42));
        p.legend("HOME", X(384), Y(51.5f), INK);
        p.drums(X(419), Y(37), Double.isNaN(fs.deltaVHome) ? "   " : YardText.count(fs.deltaVHome, 3), 10f, 19f, 15f, true);
        p.legend("SPEED", X(14), Y(92), INK);
        String speed = fs.present ? YardText.drums(fs.speed, 4, 1) : YardText.blank(4, 1);
        float sw = p.drums(X(58), Y(72), speed, 16f, 28f, 21f, true);
        p.legend("M/S", X(58) + sw + 6f, Y(92), INK);
        double left = fs.deltaVLeft;
        float max = scaleMax(left);
        float[] bracket = fs.deltaVPlanned > 0 && !Double.isNaN(left) ? new float[]{(float) Math.max(0, left - fs.deltaVPlanned), (float) left} : null;
        p.meter(X(252), Y(76), 196f, 12f, 0f, max, Double.isNaN(left) ? 0f : (float) Math.min(left, max), 4, null, null, bracket,
                Double.isNaN(fs.deltaVHome) ? Float.NaN : (float) fs.deltaVHome);
    }

    static String frameKind(Frame frame) {
        switch (frame) {
            case TARGET: return "TGT";
            case NAV: return "NAV";
            case STATION: return "STN";
            case ORBIT: return "ORB";
            default: return "";
        }
    }

    /** Delta-v on four drums: infinite thrust shows full, unknown blank. */
    static String dv(double v) {
        if (Double.isNaN(v)) return "    ";
        if (Double.isInfinite(v)) return "9999";
        return YardText.drums(v, 4, 0);
    }

    /** The delta-v scale's end: a round step above what is left. */
    static float scaleMax(double left) {
        if (Double.isNaN(left) || Double.isInfinite(left)) return 8000f;
        float step = left > 8000 ? 4000f : 2000f;
        return (float) Math.max(2000.0, Math.ceil(left / step) * step);
    }

    // --- BURN ------------------------------------------------------------------------------

    private void burn(YardPaint p, HudData d) {
        NavReadout nav = d.navReadout;
        FlightStatus fs = d.flightStatus;
        p.legend("BURN", X(206), Y(21.5f), INK);
        p.drums(X(238), Y(7), String.valueOf(Math.max(0, Math.min(9, nav.burnNumber))), 12f, 19f, 15f, false);
        p.legend("OF", X(255), Y(21.5f), INK);
        p.drums(X(273), Y(7), String.valueOf(Math.max(0, Math.min(9, nav.burnsPlanned))), 12f, 19f, 15f, false);
        // Who flies it, only when that is you or the flight computer: a helmsman flying it is HLM's line
        boolean computer = fs.autopilot, you = !computer && f.helm() && d.ctx.helmsman.equalsIgnoreCase("you");
        p.legend("FLOWN BY", X(14), Y(107.5f), INK);
        p.lamp(X(78), Y(103), 4f, Yard.WHITE_LAMP, lit(you));
        p.legend("YOU", X(86), Y(107.5f), INK);
        p.lamp(X(122), Y(103), 4f, Yard.WHITE_LAMP, lit(computer));
        p.legend("COMPUTER", X(130), Y(107.5f), INK);
        // The engine's light-up, else the burn's centre less half its length
        float in = nav.burning ? 0f : !Float.isNaN(nav.burnStartIn) ? nav.burnStartIn
                : Float.isNaN(nav.burnIn) ? Float.NaN : nav.burnIn - (Float.isNaN(nav.burnTime) ? 0f : nav.burnTime / 2f);
        p.legend("BURN IN", X(14), Y(51.5f), INK);
        p.legend("T-", X(66), Y(51.5f), INK);
        p.drums(X(80), Y(37), Float.isNaN(in) ? " :  " : mss(Math.max(0f, in)), 12f, 19f, 15f, false);
        // The helm turns onto the burn ahead of it: TURN T- counts down to that, then the lamp says what is happening
        float turnIn = !Float.isNaN(nav.turnIn) ? nav.turnIn : Float.isNaN(in) ? Float.NaN : in - 120f;
        if (nav.burning || (!Float.isNaN(turnIn) && turnIn <= 0f)) {
            p.lamp(X(142), Y(47), 4.5f, Yard.WHITE_LAMP, lit(true));
            p.legend(nav.burning ? "BURNING" : "TURNING", X(151), Y(51.5f), INK);
        } else {
            p.legend("TURN T-", X(134), Y(51.5f), INK);
            p.drums(X(180), Y(37), Float.isNaN(turnIn) ? " :  " : mss(turnIn), 11f, 19f, 15f, false);
        }
        float toGo = nav.burning && !Float.isNaN(nav.burnToGo) ? nav.burnToGo : nav.burnDeltaV;
        p.legend("ΔV TO GO", X(250), Y(51.5f), INK);
        p.drums(X(306), Y(37), Float.isNaN(toGo) ? "    " : YardText.drums(toGo, 4, 0), 12f, 19f, 15f, true);
        p.legend("M/S", X(358), Y(51.5f), INK);
        // How far the nose is off the burn: the scale, and its OFF flag when the game can't say
        p.legend("NOSE OFF", X(14), Y(80), INK);
        float off = Float.isNaN(nav.pointingDeg) ? Float.NaN : Math.abs(nav.pointingDeg);
        p.meter(X(80), Y(70), 110f, 12f, 0f, 60f, Float.isNaN(off) ? 0f : Math.min(60f, off), 3, new float[][]{{0f, 4f}}, new Color[]{Yard.AVOCADO}, null, Float.NaN);
        if (Float.isNaN(off)) {
            p.barberTab(X(118), Y(70), 34f, 12f);
            p.drums(X(200), Y(64), "  ", 12f, 21f, 16f, false);
        } else {
            p.drums(X(200), Y(64), YardText.count(Math.min(99, Math.round(off)), 2), 12f, 21f, 16f, false);
        }
        p.legend("°", X(227), Y(80), INK);
        p.legend("BURN TIME", X(250), Y(80), INK);
        float t = !Float.isNaN(nav.burnTime) ? nav.burnTime
                : Float.isNaN(toGo) || Float.isNaN(fs.acceleration) || fs.acceleration <= 0f ? Float.NaN : toGo / fs.acceleration;
        p.drums(X(318), Y(65), Float.isNaN(t) ? " :  " : mss(t), 11f, 19f, 15f, false);
        p.key(X(252), Y(96), keyName(d, "WARP_TO_BURN", "/"));
        boolean held = !d.ctx.warpHold.isEmpty();
        p.push(X(274), Y(93), 174f, 20f, new String[]{"WARP TO BURN"}, Yard.WHITE_LAMP, false, YardPaint.Kind.ORDER, held, false);
    }

    static String mss(float seconds) {
        long s = (long) Math.ceil(Math.max(0f, seconds));
        if (s >= 6000) return "99:59";
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // --- DOCK ------------------------------------------------------------------------------

    private void dock(YardPaint p, HudData d) {
        DockingApproach dk = d.docking;
        String port = dk.port + "/" + dk.ports + " " + dk.portSize + " " + (dk.collarJammed ? "JAM" : dk.active ? "ACT" : "PAS");
        p.legend("PORT", X(204), Y(21.5f), INK);
        p.flaps(X(240), Y(8), YardText.fit(port, 9), 9, 8f, 18f, 13f, dk.collarJammed ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        p.key(X(322), Y(9.5f), keyName(d, "ARM_PORT", "G"));
        p.legend("ARM", X(341), Y(21.5f), INK);
        // One port: nothing to cycle
        if (dk.ports > 1) {
            p.key(X(378), Y(9.5f), keyName(d, "NEXT_PORT", "H"));
            p.legend("NEXT", X(397), Y(21.5f), INK);
        }
        // The port given, and clearance on flaps with its lamp
        p.legend("TO", X(14), Y(51.5f), INK);
        String theirs = dk.other.isEmpty() ? "" : dk.other + (dk.otherPort > 0 ? " " + dk.otherPort : "");
        p.flaps(X(34), Y(38), YardText.fit(theirs, 11), 11, 8f, 18f, 13f, Yard.FLAP_TEXT);
        String clear = clearance(dk);
        p.lamp(X(134), Y(47), 4f, clear.equals("CLEARED") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, lit(!clear.equals("NONE")));
        p.flaps(X(144), Y(38), clear, 7, 8f, 18f, 13f, Yard.FLAP_TEXT);
        p.legend("RANGE", X(214), Y(51.5f), INK);
        p.drums(X(254), Y(37), Float.isNaN(dk.range) ? "    " : YardText.drums(dk.range, 4, 0), 12f, 19f, 15f, true);
        p.legend("M", X(306), Y(51.5f), INK);
        p.key(X(326), Y(39), keyName(d, "HELM_DOCK", "5"));
        boolean helmDocks = d.ctx.helmOrder.toLowerCase(Locale.ROOT).contains("dock");
        p.push(X(346), Y(37), 102f, 18f, new String[]{"HELM DOCKS"}, Yard.WHITE_LAMP, lit(helmDocks), YardPaint.Kind.ORDER, false, false);
        // Sideways offset and angle: the pointer in the window, and their numbers on drums
        float off = Float.isNaN(dk.lateral) ? 0f : dk.lateral, ang = Float.isNaN(dk.angleDeg) ? 0f : dk.angleDeg;
        alignBar(p, X(14), Y(66), 120f, 40f, off, ang, 5f);
        p.legend("OFF", X(144), Y(80), INK);
        String offM = String.format(Locale.ROOT, "%.1f", Math.min(99.9f, Math.abs(off)));
        float ow = p.drums(X(176), Y(67), offM, 11f, 17f, 15f, false);
        p.legend("M", X(176) + ow + 4f, Y(80), INK);
        // + is port side as the approaching ship sees it: left
        p.flaps(X(220), Y(67), Math.abs(off) < 0.05f ? "" : off > 0 ? "L" : "R", 1, 9f, 18f, 13f, Yard.FLAP_TEXT);
        p.legend("TURN", X(144), Y(105), INK);
        String angD = String.valueOf(Math.min(99, Math.round(Math.abs(ang))));
        float aw = p.drums(X(176), Y(92), angD, 11f, 17f, 15f, false);
        p.legend("°", X(176) + aw + 2f, Y(105), INK);
        // + is counter-clockwise: a turn to the left
        p.flaps(X(220), Y(92), Math.round(Math.abs(ang)) == 0 ? "" : ang > 0 ? "L" : "R", 1, 9f, 18f, 13f, Yard.FLAP_TEXT);
        // Closing speed on a scale banded safe, caution (to 1.5x) and warning; the safe speed on its drum
        float sf = Float.isNaN(dk.safeSpeed) || dk.safeSpeed <= 0f ? 1f : dk.safeSpeed, mx = sf * 2f;
        float closing = Float.isNaN(dk.closing) ? 0f : dk.closing;
        p.meter(X(250), Y(68), 130f, 12f, 0f, mx, Math.max(0f, closing), 4,
                new float[][]{{0f, sf}, {sf, sf * 1.5f}, {sf * 1.5f, mx}}, new Color[]{Yard.AVOCADO, Yard.MUSTARD, Yard.RED}, null, Float.NaN);
        String ct = String.format(Locale.ROOT, "%.1f", Math.min(99.9f, Math.abs(closing)));
        float cw = p.drums(X(390), Y(64), ct, 12f, 21f, 16f, false);
        p.legend("M/S", X(390) + cw + 5f, Y(80), INK);
        p.legend(closing < 0f ? "OPENING" : "CLOSING", X(250), Y(105), INK);
        p.legend("SAFE UP TO", X(306), Y(105), INK);
        String st = String.format(Locale.ROOT, "%.1f", Math.min(99.9f, sf));
        float stw = p.drums(X(372), Y(92), st, 11f, 17f, 15f, false);
        p.legend("M/S", X(372) + stw + 5f, Y(105), INK);
    }

    static String clearance(DockingApproach dk) {
        switch (dk.clearance) {
            case GRANTED: return "CLEARED";
            case DENIED: return "REFUSED";
            case NO_PORT: return "NO PORT";
            case YIELDS: return "YIELDS";
            default: return dk.armed ? "ASKED" : "NONE";
        }
    }

    /** A centre-zero alignment window: the ship pointer sits off centre by the sideways offset and leans by the angle. */
    private static void alignBar(YardPaint p, float x, float y, float w, float h, float off, float ang, float range) {
        p.rrect(x - 3f, y - 3f, w + 6f, h + 6f, 2.5f, Yard.BAKELITE);
        p.rect(x, y, w, h, Yard.hex("F2ECDC"));
        float cx = x + w / 2f, k = (w / 2f - 8f) / range;
        p.rect(cx - k, y, 2 * k, h, Yard.AVOCADO, 0.25f);
        for (int m = (int) -range; m <= range; m++) {
            p.rect(cx + m * k - (m == 0 ? 0.75f : 0.4f), y + h - (m == 0 ? 9f : 4f), m == 0 ? 1.5f : 0.8f, m == 0 ? 9f : 4f, Yard.INK);
        }
        p.legend("L", x + 4f, y + 13f, LABEL_DIM);
        p.legend("R", x + w - 11f, y + 13f, LABEL_DIM);
        float px = cx + Math.max(-range, Math.min(range, off)) * k * -1f, py = y + h / 2f;
        // The ship pointer: a small hull, nose up, leaning by the angle. A turn still needed to the left
        // (+, counter-clockwise) means the nose now lies clockwise of the port's axis
        float rot = ang;
        float[] hull = {0f, -12f, 4.5f, -5f, 4.5f, 11f, -4.5f, 11f, -4.5f, -5f};
        float cs = com.badlogic.gdx.math.MathUtils.cosDeg(rot), sn = com.badlogic.gdx.math.MathUtils.sinDeg(rot);
        for (int i = 0; i < hull.length; i += 2) {
            float a = hull[i], b = hull[i + 1];
            hull[i] = px + a * cs - b * sn;
            hull[i + 1] = py + a * sn + b * cs;
        }
        p.fillConvex(hull, Yard.ORANGE, 1f);
        p.polyline(hull, true, 0.9f, Yard.hex("1A1614"), 1f);
        p.glass(x, y, w, h);
    }

    // --- DEPARTURE -------------------------------------------------------------------------

    private void departure(YardPaint p, HudData d) {
        DockingApproach dk = d.docking;
        String berth = d.ctx.stationName + (dk.otherPort > 0 ? " " + dk.otherPort : "");
        p.legend("BERTH", X(204), Y(21.5f), INK);
        p.flaps(X(248), Y(8), YardText.fit(berth, 13), 13, 8.4f, 18f, 13f, Yard.FLAP_TEXT);
        p.push(X(14), Y(38), 124f, 18f, new String[]{"UNDOCK CLEARED"}, Yard.WHITE_LAMP, lit(true), YardPaint.Kind.INDICATOR, false, false);
        p.toggle(X(30), Y(88), false, true, false);
        p.key(X(50), Y(70), keyName(d, "UNDOCK", "U"));
        p.legend("UNDOCK", X(50), Y(98), INK);
        p.key(X(110), Y(70), keyName(d, "TRADE", "T"));
        p.legend("TERMINAL", X(110), Y(98), INK);
        // Four readiness checks, each ok or what is short, with the key of the terminal tab that fixes it
        float cx = X(186);
        FlightStatus fs = d.flightStatus;
        double need = d.navReadout.planDeltaV > 0 ? d.navReadout.planDeltaV : Double.NaN;
        boolean dvOk = Double.isNaN(need) || Double.isNaN(fs.deltaVLeft) || fs.deltaVLeft >= need;
        check(p, cx, Y(32), dvOk, "ΔV");
        p.drums(cx + 78f, Y(32), dv(fs.deltaVLeft), 11f, 17f, 15f, true);
        p.legend("NEEDS", cx + 128f, Y(45.5f), INK);
        p.drums(cx + 168f, Y(32), Double.isNaN(need) ? "    " : YardText.drums(need, 4, 0), 11f, 17f, 15f, true);
        p.legend("M/S", cx + 216f, Y(45.5f), INK);
        boolean repairsOk = d.shipStatus.structure >= 0.995f && d.powerFrame.faults.size == 0;
        check(p, cx, Y(54), repairsOk, "REPAIRS");
        String repairs = repairsOk ? "NONE NEEDED" : d.shipStatus.structure < 0.995f ? String.format(Locale.ROOT, "HULL %d%%", Math.round(d.shipStatus.structure * 100f)) : "SYSTEMS";
        p.flaps(cx + 78f, Y(54), repairs, 11, 8f, 17f, 13f, Yard.FLAP_TEXT);
        if (!repairsOk) {
            p.key(cx + 174f, Y(55), keyName(d, "YARD", "Y"));
            p.legend("YARD", cx + 193f, Y(67.5f), INK);
        }
        check(p, cx, Y(76), true, "CREW");
        int aboard = d.stations.crewAboard;
        p.flaps(cx + 78f, Y(76), aboard > 0 ? aboard + " ABOARD" : "", 11, 8f, 17f, 13f, Yard.FLAP_TEXT);
        int ammo = 0, mag = 0;
        for (WeaponStatusProvider.Mount m : d.weaponFrame.mounts) {
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER && m.magazine > 0) {
                ammo += Math.max(0, m.ammo);
                mag += m.magazine;
            }
        }
        boolean mslOk = mag == 0 || ammo >= mag;
        check(p, cx, Y(98), mslOk, "MISSILES");
        if (mag > 0) {
            p.drums(cx + 78f, Y(98), String.valueOf(Math.min(9, ammo)), 11f, 17f, 15f, false);
            p.legend("/", cx + 93f, Y(111.5f), INK);
            p.drums(cx + 100f, Y(98), String.valueOf(Math.min(9, mag)), 11f, 17f, 15f, false);
            if (!mslOk) {
                p.key(cx + 124f, Y(99), keyName(d, "YARD", "Y"));
                p.legend("FILL", cx + 143f, Y(111.5f), INK);
            }
        } else {
            p.flaps(cx + 78f, Y(98), "NO LAUNCHER", 11, 8f, 17f, 13f, Yard.FLAP_TEXT);
        }
    }

    /** A departure check: its lamp (green ok, amber short) and the engraved item. */
    private void check(YardPaint p, float x, float y, boolean ok, String label) {
        p.lamp(x + 5f, y + 9f, 4f, ok ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, lit(true));
        p.legend(label, x + 15f, y + 13.5f, INK);
    }

    static String keyName(HudData d, String action, String fallback) {
        String k = d.key(action);
        return k.isEmpty() ? fallback : k.toUpperCase(Locale.ROOT);
    }
}
