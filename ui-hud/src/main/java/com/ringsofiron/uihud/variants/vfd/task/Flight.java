package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * FLT, the flight instrument in the bottom middle (functional-task 6, FLT): one instrument with four
 * modes, the one in use inverse on a fixed row of legends like a deck's PLAY / REC / FF. DEPARTURE
 * while docked: the berth, then the readiness the game can say (delta-v against the way home, repairs,
 * the launcher's rounds) and the undock and terminal keys. DOCK while a port is armed, clearance asked
 * or the helm docks, with the dockable target within 5 km: the two ports and the clearance, range and
 * closing on a bar banded by the safe speed, offset and angle, and the arm, next-port and dock keys.
 * BURN from three minutes before a planned burn, and while one is flown (which beats DOCK): burn n of
 * N, the turn's and the burn's countdowns, delta-v to go, its time, the pointing error, who flies it,
 * and the warp-to-burn key. CRUISE otherwise: the frame and its distance, the speed in it, ASSIST OFF
 * only when it is off, MAIN and RCS while they fire, and delta-v left on a bar with what home takes.
 */
final class Flight {
    enum Mode {
        DEPARTURE, DOCK, BURN, CRUISE
    }

    static final String[] MODES = {"DEPARTURE", "DOCK", "BURN", "CRUISE"};
    /** BURN shows from this long before the engine lights, s: the helmsman's 120 s turn lead and a minute. */
    static final float BURN_SOON = 180f;
    /** The dockable target is within this, m (PlayerDocking.READOUT_RANGE). */
    static final float DOCK_RANGE = DockingApproach.RANGE;
    /** Closing faster than the safe speed is caution, past this many times it a warning. */
    static final float CLOSING_CAUTION = 1.5f;

    boolean on;
    Mode mode = Mode.CRUISE;
    // CRUISE
    String frame = "", frameKm = "", frameUnit = "KM", speed = "", dv = "", home = "";
    boolean assistOff, main, rcs, homeShort;
    float dvFrac, homeFrac = Float.NaN, planFrac = Float.NaN;
    // DOCK
    String ours = "", theirs = "", clearance = "", range = "", rangeUnit = "M", closing = "", lateral = "", angle = "";
    boolean jammed, refused, cleared;
    float gauge;
    int closingState;
    // BURN
    String burnN = "", burnOf = "", turnIn = "", burnIn = "", toGo = "", burnTime = "", pointing = "", flier = "";
    boolean flown;
    // DEPARTURE
    String berth = "", repairs = "", rounds = "";
    boolean repairsOk, roundsOk;
    /** The keys the mode shows, which the key strip then leaves out. */
    final String[] keys = new String[3], words = new String[3];
    final String[] keyActions = new String[3];
    int keyCount;

    void update(HudData d, Posts p) {
        FlightStatus fl = d.flightStatus;
        on = p.flight() && fl.present;
        keyCount = 0;
        if (!on) return;
        mode = mode(d, p);
        cruise(d, fl);
        switch (mode) {
            case DEPARTURE: departure(d); break;
            case DOCK: dock(d); break;
            case BURN: burn(d, p); break;
            default:
        }
    }

    /** The first mode that applies, a burn being flown beating DOCK. */
    static Mode mode(HudData d, Posts p) {
        NavReadout n = d.navReadout;
        boolean flown = n.burning || d.ctx.burnFlown || !Float.isNaN(n.turnIn) && n.turnIn <= 0f;
        if (flown && !p.docked) return Mode.BURN;
        if (p.docked) return Mode.DEPARTURE;
        DockingApproach k = d.docking;
        boolean near = n.present && n.kind == Kind.STATION && !Double.isNaN(n.distance) && n.distance <= DOCK_RANGE;
        boolean engaged = k.present && (k.armed || k.clearance != Clearance.NONE || d.orderState.helm == HelmOrder.DOCK);
        if (near && engaged) return Mode.DOCK;
        if (!Float.isNaN(n.burnStartIn) && n.burnStartIn <= BURN_SOON) return Mode.BURN;
        return Mode.CRUISE;
    }

    private void cruise(HudData d, FlightStatus fl) {
        // The frame by name, and how far off it is when it is not the orbit
        boolean orbit = fl.frame == Frame.ORBIT || fl.frame == Frame.NONE;
        frame = Fmt.name(orbit && !fl.frameName.isEmpty() ? "ORBIT, " + fl.frameName : fl.frameName, 16);
        frameKm = orbit ? "" : Fmt.range(fl.frameDistance, 4);
        frameUnit = Fmt.km(fl.frameDistance) ? "KM" : "M";
        speed = speed(fl.speed);
        assistOff = !fl.rotationAssist;
        main = fl.mainEngine;
        rcs = fl.rcs;
        dv = Double.isInfinite(fl.deltaVLeft) ? "99999" : Double.isNaN(fl.deltaVLeft) ? "" : Fmt.fit(Math.round(fl.deltaVLeft), 5, 0);
        home = Double.isNaN(fl.deltaVHome) ? "" : Fmt.fit(Math.round(fl.deltaVHome), 4, 0);
        homeShort = !Double.isNaN(fl.deltaVHome) && !Double.isNaN(fl.deltaVLeft) && fl.deltaVLeft < fl.deltaVHome;
        // The bar is the tanks: delta-v left at the propellant's share, home and the plan marked on that scale
        double left = fl.deltaVLeft;
        float prop = d.shipStatus.propellant;
        double plan = Math.max(fl.deltaVPlanned, d.navReadout.planDeltaV);
        double scale = Double.isNaN(left) || Double.isInfinite(left) ? Double.NaN
                : !Float.isNaN(prop) && prop > 0.01f ? left / prop
                : Math.max(left, Math.max(Double.isNaN(fl.deltaVHome) ? 0 : fl.deltaVHome, plan)) * 1.25;
        dvFrac = Double.isNaN(scale) || scale <= 0 ? 0f : (float) Math.min(1, left / scale);
        homeFrac = Double.isNaN(scale) || scale <= 0 || Double.isNaN(fl.deltaVHome) ? Float.NaN : (float) Math.min(1, fl.deltaVHome / scale);
        planFrac = Double.isNaN(scale) || scale <= 0 || plan <= 0 ? Float.NaN : (float) Math.min(1, plan / scale);
    }

    /** A speed in five cells: tenths under 100 m/s, whole metres a second above. */
    static String speed(float v) {
        if (Float.isNaN(v)) return "";
        float a = Math.abs(v);
        return a < 99.95f ? Fmt.fit(a, 5, 1) : Fmt.fit(Math.round(a), 5, 0);
    }

    private void dock(HudData d) {
        DockingApproach k = d.docking;
        String size = Fmt.cut(k.portSize, 1);
        ours = (k.ports > 1 ? k.port + 1 + " OF " + k.ports + " " : "") + (size.isEmpty() ? "" : size + " ")
                + (k.active ? "ACTIVE" : "PASSIVE");
        jammed = k.collarJammed;
        theirs = Fmt.name(k.other, 12) + (k.otherPort > 0 ? " " + k.otherPort : "");
        refused = k.clearance == Clearance.DENIED || k.clearance == Clearance.NO_PORT;
        cleared = k.clearance == Clearance.GRANTED || k.clearance == Clearance.YIELDS;
        clearance = cleared ? "CLEARED" : refused ? "REFUSED" : k.armed ? "ASKED" : "NONE";
        range = Fmt.range(k.range, 4);
        rangeUnit = Fmt.km(k.range) ? "KM" : "M";
        closing = Float.isNaN(k.closing) ? "" : Fmt.fit(k.closing, 4, 1);
        float c = Float.isNaN(k.closing) ? 0f : Math.max(0f, k.closing), safe = Float.isNaN(k.safeSpeed) || k.safeSpeed <= 0f ? 1f : k.safeSpeed;
        // The bar runs to twice the safe speed: safe to half, caution to three quarters, warning past it
        gauge = Math.min(1f, c / (2f * safe));
        closingState = c > CLOSING_CAUTION * safe ? 2 : c > safe ? 1 : 0;
        lateral = Float.isNaN(k.lateral) ? "" : Fmt.fit(Math.abs(k.lateral), 3, 1);
        angle = Float.isNaN(k.angleDeg) ? "" : Integer.toString(Math.min(99, Math.round(Math.abs(k.angleDeg))));
        key(d, Action.ARM_PORT, "ARM");
        if (k.ports > 1) key(d, Action.NEXT_PORT, "PORT");
        key(d, Action.HELM_DOCK, "DOCK");
    }

    private void burn(HudData d, Posts p) {
        NavReadout n = d.navReadout;
        flown = n.burning || d.ctx.burnFlown;
        burnN = n.burnNumber > 0 ? Integer.toString(Math.min(9, n.burnNumber)) : "";
        burnOf = n.burnsPlanned > 0 ? Integer.toString(Math.min(9, n.burnsPlanned)) : "";
        turnIn = !flown && !Float.isNaN(n.turnIn) && n.turnIn > 0f ? Fmt.mmss(n.turnIn) : "";
        burnIn = !n.burning && !Float.isNaN(n.burnStartIn) && n.burnStartIn > 0f ? Fmt.mmss(n.burnStartIn) : "";
        float go = n.burning ? n.burnToGo : n.burnDeltaV;
        toGo = Float.isNaN(go) ? "" : Fmt.fit(Math.round(go), 5, 0);
        burnTime = Float.isNaN(n.burnTime) ? "" : Fmt.secs(n.burnTime);
        pointing = Float.isNaN(n.pointingDeg) ? "" : Integer.toString(Math.min(180, Math.round(Math.abs(n.pointingDeg))));
        // Who flies it, when that is you or the flight computer; a helmsman flying it is HLM's line
        String helm = d.ctx.helmsman;
        boolean you = "you".equalsIgnoreCase(helm) || p.post == Posts.Post.HELM && helm.isEmpty();
        boolean handsOff = Math.abs(d.flightStatus.forward) < 0.05f && Math.abs(d.flightStatus.turn) < 0.05f;
        flier = you ? handsOff && d.orderState.helm == HelmOrder.FLY_BURNS ? "FLIGHT COMPUTER, HANDS OFF" : "YOU FLY IT"
                : "autopilot".equalsIgnoreCase(helm) ? "AUTOPILOT FLIES IT" : "";
        if (!n.burning) key(d, Action.WARP_TO_BURN, "WARP TO BURN");
    }

    private void departure(HudData d) {
        String at = d.navReadout.docked.present ? d.navReadout.docked.name : d.ctx.stationName;
        berth = Fmt.name(at, 22);
        // Repairs: any system damaged; missiles: the launchers' rounds against their magazines
        int damaged = 0;
        for (SystemRow r : d.powerFrame.faults) if (r.fault == Fault.DESTROYED || r.fault == Fault.DISABLED || r.health < 0.999f) damaged++;
        repairsOk = damaged == 0 && d.shipStatus.structure >= 0.999f;
        repairs = repairsOk ? "OK" : damaged > 0 ? damaged + " DAMAGED" : "HULL " + Math.round(d.shipStatus.structure * 100f) + "%";
        int ammo = 0, mag = 0;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind != com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.LAUNCHER || m.magazine <= 0) continue;
            ammo += Math.max(0, m.ammo);
            mag += m.magazine;
        }
        roundsOk = mag == 0 || ammo >= mag;
        rounds = mag == 0 ? "" : roundsOk ? "OK" : ammo + "/" + mag;
        key(d, Action.UNDOCK, "UNDOCK");
        key(d, Action.TRADE, "TERMINAL");
    }

    private void key(HudData d, Action a, String word) {
        if (keyCount == keys.length) return;
        keys[keyCount] = Catalogue.key(d, a.name(), a.defaultKeyName());
        words[keyCount] = word;
        keyActions[keyCount] = a.name();
        keyCount++;
    }

    /** Whether the mode shows the key for {@code action}. */
    boolean shows(String action) {
        for (int i = 0; i < keyCount; i++) if (keyActions[i].equals(action)) return true;
        return false;
    }

    // ------------------------------------------------------------------ painting, from the bottom middle origin

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.FLT_X, y = Geo.FLT_Y, w = Geo.FLT_W, h = Geo.FLT_H;
        f.plate(x, y, w, h, 6f, 9f);
        f.module(x, y, w, h, f.o().filaments(4));
        // The four modes as fixed legends, the one in use inverse
        float mx = x + 10f;
        for (int i = 0; i < MODES.length; i++) {
            float mw = f.legendWidth(MODES[i], f.o());
            if (i == mode.ordinal()) {
                f.rectLit(mx - 4f, y + 5f, mw + 8f, 17f, f.o().norm().r(1f));
                f.knockout(mx, y + 18f, MODES[i], f.o());
            } else {
                f.legend(mx, y + 18f, MODES[i], f.o().lit(false));
            }
            mx += mw + 18f;
        }
        switch (mode) {
            case DEPARTURE: paintDeparture(f, x, y, w); break;
            case DOCK: paintDock(f, x, y, w); break;
            case BURN: paintBurn(f, x, y, w); break;
            default: paintCruise(f, x, y, w);
        }
    }

    private void paintCruise(Filament f, float x, float y, float w) {
        // The frame, its distance, and the speed in it
        float fx = x + 10f + f.legend(x + 10f, y + 40f, "FRAME", f.o().dim()) + 6f;
        fx += f.seg7(fx, y + 28f, 12f, frameKm, f.o().cells(4).right().norm()) + 5f;
        f.legend(fx, y + 40f, frameUnit, f.o().dim().lit(!frameKm.isEmpty()));
        f.dm(x + 10f, y + 46f, frame, f.o().pitch(Geo.P).cells(16).norm());
        float sx = x + 176f, sw = f.seg7(sx, y + 27f, 30f, speed, f.o().cells(5).right().norm());
        f.legend(sx + sw + 6f, y + 57f, "M/S", f.o().dim());
        // Indicators, dark at rest: assist only when it is off, the drives while they fire
        float ix = x + 336f;
        f.legend(ix, y + 40f, "ASSIST OFF", f.o().lit(assistOff).full().box());
        Kit.states(f, ix, y + 60f, new String[] {"MAIN", "RCS"}, new float[] {main ? Drive.NORM : Float.NaN, rcs ? Drive.NORM : Float.NaN}, 13f, 10f);
        deltaV(f, x, y + 86f, w);
    }

    /** Delta-v left on the tanks' bar, with what home takes as a notch and what the plan needs as a bracket. */
    private void deltaV(Filament f, float x, float b, float w) {
        float vx = x + 10f + f.legend(x + 10f, b, "ΔV", f.o().dim()) + 6f;
        vx += f.seg7(vx, b - 15f, 15f, dv, f.o().cells(5).right().norm()) + 8f;
        vx += f.legend(vx, b, "M/S", f.o().dim()) + 10f;
        float hx0 = x + w - 10f - f.seg7Width(12f, 4, f.o()), hl = f.legendWidth("HOME", f.o());
        float bx = vx, bw = hx0 - 6f - hl - 12f - bx, by = b - 9f;
        f.bar(bx, by, bw, 8f, 40, dvFrac, f.o().dim().gap(1.1f));
        if (!Float.isNaN(homeFrac)) {
            float hx = bx + bw * homeFrac;
            Phosphor c = homeShort ? Phosphor.AMBER : Phosphor.ZINC;
            f.rectLit(hx - 0.7f, by + 10f, 1.4f, 5f, f.o().norm().color(c));
            f.dot(hx - 2.2f, by + 12f, f.o().size(1.1f).norm().color(c));
            f.dot(hx + 2.2f, by + 12f, f.o().size(1.1f).norm().color(c));
        }
        if (!Float.isNaN(planFrac)) {
            float px = bx + bw * planFrac;
            f.rectLit(px - 0.6f, by - 3f, 1.2f, 14f, f.o().norm());
            f.rectLit(px - 3f, by - 3f, 3f, 1.2f, f.o().norm());
        }
        f.legend(hx0 - 6f - hl, b, "HOME", f.o().dim().color(homeShort ? Phosphor.AMBER : Phosphor.ZINC));
        f.seg7(hx0, b - 12f, 12f, home, f.o().cells(4).right().level(homeShort ? Drive.FULL : Drive.DIM).color(homeShort ? Phosphor.AMBER : Phosphor.ZINC));
    }

    private void paintDock(Filament f, float x, float y, float w) {
        // The two ports and the clearance
        float b1 = y + 40f;
        float cx = x + 10f + f.legend(x + 10f, b1, "PORT", f.o().dim()) + 6f;
        cx += f.dm(cx, b1 - 10.5f, ours, f.o().pitch(Geo.P).cells(14).norm()) + 4f;
        cx += f.legend(cx, b1, "JAMMED", f.o().amber().full().lit(jammed)) + 10f;
        cx += f.dm(cx, b1 - 10.5f, theirs, f.o().pitch(Geo.P).cells(13).norm()) + 8f;
        Filament.Opt co = f.o().lit(!"NONE".equals(clearance)).norm();
        if (refused) co.amber().full();
        Kit.fits(cx + f.legend(cx, b1, "NONE".equals(clearance) ? "CLEARED" : clearance, co), x + w - 10f, "FLT dock port row");
        // Range and closing, the bar banded safe, caution and warning
        float b2 = y + 68f;
        float rx = x + 10f;
        rx += f.seg7(rx, b2 - 22f, 22f, range, f.o().cells(4).right().norm()) + 4f;
        rx += f.legend(rx, b2, rangeUnit, f.o().dim()) + 14f;
        rx += f.legend(rx, b2, "CLOSING", f.o().dim()) + 6f;
        Phosphor cc = closingState == 0 ? Phosphor.ZINC : closingState == 1 ? Phosphor.AMBER : Phosphor.RED;
        rx += f.seg7(rx, b2 - 22f, 22f, closing, f.o().cells(4).right().color(cc).level(closingState == 0 ? Drive.NORM : Drive.FULL)) + 4f;
        rx += f.legend(rx, b2, "M/S", f.o().dim()) + 12f;
        float gw = x + w - 10f - rx;
        Filament.Zone zone = (i, n) -> i >= n * 0.75f ? Phosphor.RED : i >= n * 0.5f ? Phosphor.AMBER : Phosphor.ZINC;
        f.bar(rx, b2 - 14f, gw, 8f, 30, gauge, f.o().zone(zone).norm());
        f.rectLit(rx + gw * 0.5f - 0.6f, b2 - 17f, 1.2f, 14f, f.o().norm());
        // Offset and angle, then the mode's keys
        float b3 = y + 88f;
        float ox = x + 10f;
        ox += f.legend(ox, b3, "OFFSET", f.o().dim()) + 4f;
        ox += f.seg7(ox, b3 - 12f, 12f, lateral, f.o().cells(3).right().norm()) + 3f;
        ox += f.legend(ox, b3, "M", f.o().dim()) + 12f;
        ox += f.legend(ox, b3, "ANGLE", f.o().dim()) + 4f;
        ox += f.seg7(ox, b3 - 12f, 12f, angle, f.o().cells(2).right().norm()) + 2f;
        ox += f.legend(ox, b3, "°", f.o().dim()) + 14f;
        Kit.fits(keys(f, ox, b3 - 14f), x + w - 10f, "FLT dock keys");
    }

    private void paintBurn(Filament f, float x, float y, float w) {
        float b1 = y + 40f;
        float cx = x + 10f + f.legend(x + 10f, b1, "BURN", f.o().dim()) + 6f;
        cx += f.seg7(cx, b1 - 12f, 12f, burnN, f.o().cells(1).norm()) + 4f;
        cx += f.legend(cx, b1, "OF", f.o().dim()) + 4f;
        cx += f.seg7(cx, b1 - 12f, 12f, burnOf, f.o().cells(1).norm()) + 16f;
        cx += f.legend(cx, b1, "TURN", f.o().dim().lit(!turnIn.isEmpty())) + 4f;
        cx += f.seg7(cx, b1 - 12f, 12f, turnIn, f.o().cells(4).right().colons(1).norm()) + 14f;
        cx += f.legend(cx, b1, "LIGHT", f.o().dim().lit(!burnIn.isEmpty())) + 4f;
        cx += f.seg7(cx, b1 - 12f, 12f, burnIn, f.o().cells(4).right().colons(1).norm()) + 14f;
        Kit.fits(cx + f.legend(cx, b1, "BURNING", f.o().lit(flown && burnIn.isEmpty()).full()), x + w - 10f, "FLT burn row");
        // Delta-v to go, the burn's time and the pointing error
        float b2 = y + 72f;
        float gx = x + 10f + f.legend(x + 10f, b2, "TO GO", f.o().dim()) + 6f;
        gx += f.seg7(gx, b2 - 26f, 26f, toGo, f.o().cells(5).right().norm()) + 5f;
        gx += f.legend(gx, b2, "M/S", f.o().dim()) + 14f;
        gx += f.legend(gx, b2, "TIME", f.o().dim()) + 4f;
        gx += f.seg7(gx, b2 - 15f, 15f, burnTime, f.o().cells(3).right().norm()) + 3f;
        gx += f.legend(gx, b2, "S", f.o().dim()) + 14f;
        gx += f.legend(gx, b2, "POINT", f.o().dim()) + 4f;
        gx += f.seg7(gx, b2 - 15f, 15f, pointing, f.o().cells(3).right().norm()) + 2f;
        Kit.fits(gx + f.legend(gx, b2, "°", f.o().dim()), x + w - 10f, "FLT burn numbers");
        // Who flies it, then the warp key
        float b3 = y + 90f;
        float wx = x + 10f + f.dm(x + 10f, b3 - 10.5f, flier, f.o().pitch(Geo.P).cells(26).norm().noGhost()) + 12f;
        keys(f, Math.max(wx, x + w - 10f - keysWidth(f)), b3 - 14f);
    }

    private void paintDeparture(Filament f, float x, float y, float w) {
        float b1 = y + 40f;
        float cx = x + 10f + f.legend(x + 10f, b1, "BERTH", f.o().dim()) + 6f;
        f.dm(cx, b1 - 10.5f, berth, f.o().pitch(Geo.P).cells(22).norm());
        // The readiness the game can say: delta-v against home, repairs, the launcher's rounds
        float b2 = y + 62f;
        float rx = x + 10f + f.legend(x + 10f, b2, "REPAIRS", f.o().dim()) + 6f;
        Filament.Opt ro = f.o().pitch(Geo.P).cells(10).norm();
        if (!repairsOk) ro.amber();
        rx += f.dm(rx, b2 - 10.5f, repairs, ro) + 14f;
        if (!rounds.isEmpty()) {
            rx += f.legend(rx, b2, "MISSILES", f.o().dim()) + 6f;
            Filament.Opt mo = f.o().pitch(Geo.P).cells(5).norm();
            if (!roundsOk) mo.amber();
            f.dm(rx, b2 - 10.5f, rounds, mo);
        }
        float b3 = y + 88f;
        float vx = x + 10f + f.legend(x + 10f, b3, "ΔV", f.o().dim()) + 6f;
        vx += f.seg7(vx, b3 - 15f, 15f, dv, f.o().cells(5).right().norm()) + 6f;
        vx += f.legend(vx, b3, "HOME", f.o().dim().color(homeShort ? Phosphor.AMBER : Phosphor.ZINC)) + 4f;
        vx += f.seg7(vx, b3 - 12f, 12f, home, f.o().cells(4).right().norm().color(homeShort ? Phosphor.AMBER : Phosphor.ZINC)) + 12f;
        keys(f, Math.max(vx, x + w - 10f - keysWidth(f)), b3 - 14f);
    }

    /** The mode's keys, each a cap and its word, from {@code x}; returns the end x. */
    private float keys(Filament f, float x, float y) {
        float cx = x;
        for (int i = 0; i < keyCount; i++) {
            cx += f.keycap(cx, y, keys[i], 16f, 12f, false) + 4f;
            cx += f.dm(cx, y + 2.75f, words[i], f.o().pitch(Geo.P).norm().noGhost()) + 10f;
        }
        return cx - 10f;
    }

    private float keysWidth(Filament f) {
        float w = 0f;
        for (int i = 0; i < keyCount; i++) w += f.keycapWidth(keys[i], 16f, 12f) + 4f + f.dmWidth(words[i].length(), f.o().pitch(Geo.P)) + 10f;
        return Math.max(0f, w - 10f);
    }
}
