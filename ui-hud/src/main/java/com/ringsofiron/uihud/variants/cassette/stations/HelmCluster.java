package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The helm cluster (spec 8.1, 8.2): five fixed slots engraved on one plate. REF and NEXT share a strip
 * tube (the docking approach takes both, the ports on the tube and the approach on instruments); then
 * VELOCITY with its frame, speed and the mode chips; the pilot's TRIGGER; DELTA-V with the home bug and the
 * plan's bracket. A slot with nothing to show stays where it is, dark. In warp everything but NEXT goes
 * dark; with the helm console unpowered the whole cluster is on its NO POWER face.
 */
final class HelmCluster extends YardPart {
    static final float X = 400f, Y = 616f, W = StationsLayout.CLUSTER_W, H = StationsLayout.UNIT_H;
    /** How long the orbit shows in REF after the engine or the RCS last fired. */
    static final float ORBIT_AFTER = 5f;

    private final StationsFrame f;
    private float firedAt = -100f;

    HelmCluster(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** Notes the engines' firing, for REF's orbit readout; called every frame the cluster is up or not. */
    void watch() {
        FlightStatus fl = f.data.flightStatus;
        if (fl.present && (fl.mainEngine || fl.rcs)) firedAt = f.time;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float x = X, y = Y;
        Hardware.pod(p, x, y, W, H, true);
        Hardware.plate(p, x + 5f, y + 10f, W - 10f, H - 14f, false);
        boolean dead = !f.live(Station.HELM);
        // Warp: nothing here changes fast enough to read but the next event, unless something is wrong
        boolean warpDark = f.warp && !f.alerts.annunciatorUp();
        boolean dark = dead || warpDark;

        p.legend("REF", x + 16f, y + 34f, Yard.INK);
        p.legend("NEXT", x + 16f, y + 52f, Yard.INK);
        if (!dead && approach(d)) approach(p, d, x, y);
        else {
            p.tube(x + 54f, y + 19f, 408f, 38f, 3f, 4f, dead);
            if (dead) p.tube("HELM: NO POWER", x + 62f, y + 42f, 13f, Yard.P_DIM, false);
            else {
                refChars = Hardware.chars(p, 392f, 13f);
                if (!warpDark) Hardware.tubeFit(p, reference(d), x + 62f, y + 33f, 13f, Yard.P_AMBER, 392f);
                Hardware.tubeFit(p, next(d), x + 62f, y + 51f, 13f, Yard.P_AMBER, 392f);
            }
            p.tubeOver(x + 54f, y + 19f, 408f, 38f);
        }
        Hardware.engrave(p, x + 12f, y + 66f, x + 468f, y + 66f);
        velocity(p, d, x, y, dark);
        Hardware.engrave(p, x + 12f, y + 110f, x + 468f, y + 110f);
        Hardware.engrave(p, x + 256f, y + 112f, x + 256f, y + 152f);
        trigger(p, d, x + 12f, y + 112f, !dark && (d.targetStatus.present || f.condition == StationsFrame.Condition.RED));
        deltaV(p, d, x, y, dark);
    }

    // --- REF and NEXT ------------------------------------------------------------------------------

    /** The docking approach (8.2): the selected port armed, or lining up with a station that is the nav target. */
    static boolean approach(HudData d) {
        DockingApproach a = d.docking;
        if (!a.present || a.capture == Capture.DOCKED || d.ctx.docked) return false;
        if (a.armed) return true;
        NavReadout n = d.navReadout;
        return a.capture != Capture.FREE && n.present && n.kind == Kind.STATION;
    }

    /** REF: the locked target, else the nav target: name, range, closing, closest approach; else the orbit while flying. */
    String reference(HudData d) {
        TargetStatus t = d.targetStatus;
        if (t.present) return ref(t.label, t.range, t.closing, t.cpaDistance, t.cpaTime, refChars);
        NavReadout n = d.navReadout;
        if (n.present) return ref(n.name, n.distance, n.closing, n.cpaDistance, n.cpaIn, refChars);
        if (f.time - firedAt <= ORBIT_AFTER && d.orbitInfo != null) {
            OrbitInfo o = d.orbitInfo.getPlayerOrbit();
            if (o != null) return o.bodyName + "  Ap " + Pages.dist(o.apoapsis) + "  Pe " + Pages.dist(o.periapsis);
        }
        return "";
    }

    /** The REF tube's width in characters at its size, measured as it is drawn. */
    private int refChars = 50;

    /**
     * "HIGHPORT  1.2 km  closing 3.9 m/s  CPA 140 m 2:10": the name gives way first, then the closest
     * approach's time and the closest approach; the range and the rate stay.
     */
    static String ref(String name, double range, double closing, double cpa, double cpaIn, int budget) {
        String r = Double.isNaN(range) ? "" : "  " + Pages.dist(range);
        String c = Double.isNaN(closing) || Math.abs(closing) < 0.05 ? ""
                : (closing > 0 ? "  closing " : "  opening ") + Pages.mps(Math.abs(closing));
        boolean hasCpa = !Double.isNaN(cpa) && !Double.isNaN(cpaIn) && cpaIn > 0;
        String a = hasCpa ? "  CPA " + Pages.dist(cpa) + " " + YardText.clock(cpaIn) : "";
        // When even six letters of name leave no room, the closest approach gives way: its time, then itself
        int least = Math.min(6, name.length());
        if (hasCpa && least + r.length() + c.length() + a.length() > budget) a = "  CPA " + Pages.dist(cpa);
        if (hasCpa && least + r.length() + c.length() + a.length() > budget) a = "";
        return Hardware.squeeze(name, budget, r + c + a);
    }

    /** NEXT: the next thing due. A burn: what, when, how much; within 60 s of ignition, the turn still to make. */
    static String next(HudData d) {
        NavReadout n = d.navReadout;
        if (n.burning) return "Burn " + n.burnNumber + ": burning, " + Pages.mps(n.burnToGo) + " to go";
        if (!Float.isNaN(n.burnIn)) {
            if (!Float.isNaN(n.burnStartIn) && n.burnStartIn <= 60f) {
                String align = Float.isNaN(n.pointingDeg) ? "" : String.format(Locale.ROOT, "  align %.1f°", Math.abs(n.pointingDeg));
                return "Burn " + n.burnNumber + " ignition in " + YardText.clock(n.burnStartIn) + align;
            }
            return "Burn " + n.burnNumber + " in " + YardText.clock(n.burnIn) + "  " + Pages.mps(n.burnDeltaV);
        }
        switch (d.orderState.autopilot) {
            case ON: return "Autopilot" + (d.orderState.autopilotTo.isEmpty() ? " on" : ": " + d.orderState.autopilotTo);
            case HELD: return "Autopilot held: " + d.orderState.autopilotHeld;
            default: break;
        }
        if (n.proposal) return "Transfer proposed: " + Math.round(n.planDeltaV) + " m/s";
        return "";
    }

    private void approach(YardPaint p, HudData d, float x, float y) {
        DockingApproach a = d.docking;
        p.tube(x + 54f, y + 19f, 352f, 18f, 3f, 4f, false);
        Hardware.tubeFit(p, ports(a), x + 62f, y + 32.5f, 13f, a.clearance == Clearance.DENIED || a.clearance == Clearance.NO_PORT ? Yard.P_RED : Yard.P_AMBER, 336f);
        p.tubeOver(x + 54f, y + 19f, 352f, 18f);
        float ax = x + 54f;
        ax += p.legend("RANGE", ax, y + 56f, Yard.INK) + 6f;
        String range = Float.isNaN(a.range) ? "   " : YardText.count(Math.min(a.range, 9999f), a.range >= 1000f ? 4 : 3).replace(' ', '0');
        ax += p.drums(ax, y + 42f, range, 11f, 17f, 14f, true) + 4f;
        ax += p.legend("M", ax, y + 56f, Yard.INK) + 12f;
        ax += p.legend("OFF", ax, y + 56f, Yard.INK) + 4f;
        p.flaps(ax, y + 44f, Float.isNaN(a.lateral) ? "" : String.format(Locale.ROOT, "%+.1f", MathUtils.clamp(a.lateral, -99f, 99f)), 4, 8.4f, 14f, 12.5f, Yard.FLAP_TEXT);
        ax += 4 * 8.4f + 4f;
        ax += p.legend("M", ax, y + 56f, Yard.INK) + 12f;
        ax += p.legend("ANG", ax, y + 56f, Yard.INK) + 4f;
        p.flaps(ax, y + 44f, Float.isNaN(a.angleDeg) ? "" : String.format(Locale.ROOT, "%+.1f°", MathUtils.clamp(a.angleDeg, -99f, 99f)), 5, 8.4f, 14f, 12.5f, Yard.FLAP_TEXT);
        ax += 5 * 8.4f + 14f;
        ax += p.legend("SAFE", ax, y + 56f, Yard.INK) + 4f;
        p.drums(ax, y + 42f, Float.isNaN(a.safeSpeed) ? " . " : String.format(Locale.ROOT, "%.1f", Math.min(a.safeSpeed, 9.9f)), 11f, 17f, 14f, false);
        // Closing as a share of the safe speed for this range: green to the limit, mustard to half again, red past it
        float gx = x + 440f, gy = y + 38f, r = 20f, max = 1.9f;
        float share = Float.isNaN(a.closing) || Float.isNaN(a.safeSpeed) || a.safeSpeed <= 0f ? 0f : Math.max(0f, a.closing) / a.safeSpeed;
        Hardware.gauge(p, gx, gy, r, 0f, max, share, new float[][]{{0f, 1f}, {1f, 1.5f}, {1.5f, max}}, Hardware.CLOSING_COLORS);
        float ang = 150f + 1f / max * 240f;
        p.line(gx + (r + 1f) * MathUtils.cosDeg(ang), gy + (r + 1f) * MathUtils.sinDeg(ang), gx + (r - 9f) * MathUtils.cosDeg(ang), gy + (r - 9f) * MathUtils.sinDeg(ang), 2f, Yard.INK);
    }

    /** The approach's Ports field: own port, the one it goes to, clearance. */
    static String ports(DockingApproach a) {
        StringBuilder b = new StringBuilder("PORT ").append(a.port).append('/').append(a.ports).append(' ').append(a.portSize);
        b.append(a.armed ? " ARMED" : " SAFE");
        if (!a.other.isEmpty()) {
            b.append(" > ").append(YardText.fit(a.other, 12));
            if (a.otherPort > 0) b.append(' ').append(a.otherPort).append(a.otherPortSize.isEmpty() ? "" : " (" + a.otherPortSize + ")");
        }
        switch (a.clearance) {
            case GRANTED: b.append("  GRANTED"); break;
            case DENIED: b.append("  REFUSED"); break;
            case NO_PORT: b.append("  NO PORT"); break;
            case YIELDS: b.append("  YIELDS"); break;
            default: b.append(a.armed ? "  ASKED" : "  NOT ASKED"); break;
        }
        return b.toString();
    }

    // --- VELOCITY ------------------------------------------------------------------------------------

    private void velocity(YardPaint p, HudData d, float x, float y, boolean dark) {
        FlightStatus fl = d.flightStatus;
        Color ink = dark ? Hardware.DIM_INK : Yard.INK;
        p.legend("VELOCITY", x + 16f, y + 81f, ink);
        boolean docked = d.ctx.docked || d.docking.present && d.docking.capture == Capture.DOCKED;
        boolean port = !docked && approach(d);
        String frame = dark ? "" : docked ? "DOCKED" : port ? "PORT" : frameWord(fl);
        p.flaps(x + 16f, y + 86f, frame, 7, 8.4f, 15f, 13f, Yard.FLAP_TEXT);
        float speed = port ? Math.abs(d.docking.closing) : fl.speed;
        if (dark || docked || !fl.present || Float.isNaN(speed)) {
            p.drums(x + 80f, y + 74f, "    ", 16f, 25f, 19f, false);
        } else {
            boolean km = speed >= 1000f;
            String s = km ? String.format(Locale.ROOT, "%.2f", Math.min(speed / 1000f, 99.99f)) : speed < 100f ? String.format(Locale.ROOT, "%.1f", speed) : String.valueOf(Math.round(speed));
            float dw = p.drums(x + 80f, y + 74f, s, 16f, 25f, 19f, false);
            p.legend(km ? "KM/S" : "M/S", x + 86f + dw, y + 88f, Yard.INK);
            float closing = port ? d.docking.closing : fl.closing;
            if (!Float.isNaN(closing) && Math.abs(closing) >= 0.05f && fl.frame != com.ringsofiron.uihud.providers.FlightStatusProvider.Frame.ORBIT) {
                p.legend(closing > 0f ? "CLOSING" : "OPENING", x + 86f + dw, y + 101f, Yard.INK);
            }
        }
        if (!dark && fl.present && !Float.isNaN(fl.spin) && Math.abs(fl.spin) > 0.5f) {
            rot(p, x + 200f, y + 88f, 12f, fl.spin);
            p.legend("ROT", x + 216f, y + 92f, Yard.INK);
        }
        String control = dark ? "" : control(d);
        p.flaps(x + 236f, y + 75f, control, 13, 8.4f, 16f, 13f, control.equals("MANUAL") ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
        p.legend("CONTROL", x + 236f, y + 103f, ink);
        boolean assist = fl.present && fl.rotationAssist;
        p.push(x + 356f, y + 76f, 54f, 18f, new String[]{"ASSIST"}, Yard.WHITE_LAMP, !dark && assist, YardPaint.Kind.INDICATOR, !dark && fl.present && !assist, false);
        if (!dark && fl.present && !assist) p.textC(Face.LEGEND, 12f, "OFF", x + 383f, y + 106f, Yard.INK);
        p.lamp(x + 430f, y + 81f, 3.6f, Yard.WHITE_LAMP, !dark && fl.mainEngine);
        p.legend("THR", x + 438f, y + 85.5f, ink);
        p.lamp(x + 430f, y + 97f, 3.6f, Yard.WHITE_LAMP, !dark && fl.rcs);
        p.legend("RCS", x + 438f, y + 101.5f, ink);
    }

    static String frameWord(FlightStatus fl) {
        switch (fl.frame) {
            case TARGET: return "TARGET";
            case NAV: return "NAV";
            case STATION: return "STATION";
            case ORBIT: return "ORBIT";
            default: return "";
        }
    }

    /**
     * The control chip (3.5): MANUAL while the player flies; BURN n while the flight computer flies a planned
     * burn; AUTOPILOT while it flies the transfer; HELD and the order while an order waits on the player's
     * hands; the helmsman's order when the crew fly.
     */
    static String control(HudData d) {
        if (d.ctx.burnFlown && d.navReadout.burnNumber > 0) return "BURN " + d.navReadout.burnNumber;
        if (d.orderState.autopilot == Autopilot.ON || d.flightStatus.autopilot) return "AUTOPILOT";
        if (d.orderState.autopilot == Autopilot.HELD) return "AUTOPILOT HELD";
        HelmOrder o = d.orderState.helm;
        if (o != HelmOrder.NONE) {
            String w = order(o);
            boolean you = d.ctx.helmsman.equalsIgnoreCase("you");
            return you ? YardText.fit("HELD " + w, 13) : YardText.fit(w, 13);
        }
        return "MANUAL";
    }

    private static String order(HelmOrder o) {
        switch (o) {
            case FLY_BURNS: return "BURNS";
            case MATCH_VELOCITY: return "MATCH";
            case HOLD_POSITION: return "HOLD";
            case APPROACH: return "APPROACH";
            case DOCK: return "DOCK";
            default: return "";
        }
    }

    /** A centre-zero rate needle: up is still, a full deflection 3 degrees a second either way. */
    private static void rot(YardPaint p, float cx, float cy, float r, float spin) {
        p.circle(cx, cy, r + 3f, Yard.BAKELITE);
        p.circle(cx, cy, r, Hardware.SCALE);
        for (int i = -2; i <= 2; i++) {
            float a = -90f + i * 45f;
            p.line(cx + r * MathUtils.cosDeg(a), cy + r * MathUtils.sinDeg(a), cx + (r - 3f) * MathUtils.cosDeg(a), cy + (r - 3f) * MathUtils.sinDeg(a), 0.9f, Yard.INK);
        }
        // Counter-clockwise spin turns the needle left
        float a = -90f - MathUtils.clamp(spin / 3f, -1f, 1f) * 90f;
        p.line(cx, cy, cx + (r - 2f) * MathUtils.cosDeg(a), cy + (r - 2f) * MathUtils.sinDeg(a), 1.6f, Yard.ORANGE);
        p.circle(cx, cy, 2f, Hardware.KNOB);
        p.glass(cx - r, cy - r, 2 * r, 2 * r);
    }

    // --- TRIGGER ---------------------------------------------------------------------------------------

    /** The pilot's trigger (8.1): the guns and how they fire besides it, missiles, decoy, and the shields when lowered. */
    private void trigger(YardPaint p, HudData d, float sx, float sy, boolean lit) {
        WeaponStatusProvider.WeaponFrame w = d.weaponFrame;
        Color ink = lit ? Yard.INK : Hardware.DIM_INK;
        p.legend("TRIGGER", sx + 4f, sy + 13f, ink);
        if (f.emitter) {
            boolean low = !d.shieldStatus.raised;
            Hardware.lens(p, sx + 58f, sy + 3f, 52f, 13f, "SHIELDS", Yard.AMBER_LAMP, lit && low, false);
        }
        Mount launcher = first(w, WeaponStatusProvider.Kind.LAUNCHER);
        p.legend("MSL", sx + 120f, sy + 13f, ink);
        String msl = lit && launcher != null ? String.valueOf(Math.min(9, Math.max(0, w.missilesLeft))) : " ";
        p.drums(sx + 147f, sy + 3f, msl, 11f, 13f, 12f, false);
        String mslState = !lit || launcher == null ? "" : launcherWord(launcher, w);
        p.lamp(sx + 169f, sy + 9.5f, 3.6f, mslState.equals("READY") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, lit && launcher != null && !mslState.equals("EMPTY"));
        p.flaps(sx + 178f, sy + 3f, mslState, 5, 7.4f, 13f, 12f, Yard.FLAP_TEXT);

        boolean reloading = false, any = false, free = false;
        for (int mi = 0; mi < w.mounts.size; mi++) {
            Mount m = w.mounts.get(mi);
            if (m.kind != WeaponStatusProvider.Kind.GUN && m.kind != WeaponStatusProvider.Kind.BEAM) continue;
            any = true;
            reloading |= m.state == WeaponStatusProvider.State.RELOADING || m.state == WeaponStatusProvider.State.CHARGING;
            free |= m.weaponsFree;
        }
        p.lamp(sx + 10f, sy + 29f, 3.6f, reloading ? Yard.AMBER_LAMP : Yard.GREEN_LAMP, lit && any);
        p.legend("GUNS", sx + 18f, sy + 33.5f, ink);
        String mode = !lit || !any ? "" : w.holdFire || d.ctx.holdFire ? "HOLD" : free ? "AT WILL" : "TRIGGER";
        p.flaps(sx + 52f, sy + 22f, mode, 7, 8.4f, 14f, 12.5f, mode.equals("HOLD") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        p.legend("DECOY", sx + 120f, sy + 33.5f, ink);
        boolean ready = w.decoyCooldown <= 0f;
        p.lamp(sx + 169f, sy + 29f, 3.6f, ready ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, lit);
        p.flaps(sx + 178f, sy + 22f, !lit ? "" : ready ? "READY" : String.format(Locale.ROOT, "%.1fS", w.decoyCooldown), 5, 7.4f, 14f, 12f, Yard.FLAP_TEXT);
    }

    static Mount first(WeaponStatusProvider.WeaponFrame w, WeaponStatusProvider.Kind kind) {
        for (int mi = 0; mi < w.mounts.size; mi++) {
            Mount m = w.mounts.get(mi);
            if (m.kind == kind) return m;
        }
        return null;
    }

    /** A launcher's state in five cells: READY, its seconds, EMPTY, or what stops it. */
    static String launcherWord(Mount m, WeaponStatusProvider.WeaponFrame w) {
        if (w.missilesLeft <= 0 || m.state == WeaponStatusProvider.State.EMPTY) return "EMPTY";
        switch (m.state) {
            case READY: return "READY";
            case RELOADING: return String.format(Locale.ROOT, "%.1fS", Math.max(0f, m.cooldown));
            case DESTROYED: return "WRECK";
            case NO_POWER: return "NO PW";
            case SHORTED: return "SHORT";
            case STUNNED: return "STUN";
            default: return YardText.fit(m.state.name(), 5);
        }
    }

    // --- DELTA-V ----------------------------------------------------------------------------------------

    private void deltaV(YardPaint p, HudData d, float x, float y, boolean dark) {
        FlightStatus fl = d.flightStatus;
        Color ink = dark ? Hardware.DIM_INK : Yard.INK;
        // Laid out by measure, left to right: the legend, the drums, the unit, the home bug and its figure
        float lx = x + 266f + p.legend("DELTA-V", x + 266f, y + 125f, ink) + 4f;
        double left = fl.deltaVLeft, home = fl.deltaVHome;
        boolean known = !dark && fl.present && !Double.isNaN(left);
        boolean free = known && Double.isInfinite(left);
        String dv = !known || free ? "    " : YardText.count(Math.min(left, 9999), 4).replace(' ', '0');
        lx += p.drums(lx, y + 113f, dv, 12f, 17f, 14f, true) + 4f;
        lx += p.legend(free ? "FREE" : "M/S", lx, y + 126f, ink) + 9f;
        boolean hasHome = known && !Double.isNaN(home) && !Double.isInfinite(home);
        if (hasHome) {
            p.homeBug(lx, y + 116f);
            p.legendFit("HOME " + Math.round(home), lx + 8f, y + 126f, x + 466f - (lx + 8f), left < home ? Yard.PAPER_RED : Yard.INK);
        }
        float value = known && !free ? (float) left : 0f;
        float max = Math.max(2500f, (float) Math.ceil(Math.max(value, hasHome ? home : 0) / 500.0) * 500f);
        double plan = d.navReadout.planDeltaV > 0 ? d.navReadout.planDeltaV : fl.deltaVPlanned;
        float[] bracket = known && !free && plan > 0 ? new float[]{(float) Math.max(0, left - plan), value} : null;
        p.meter(x + 268f, y + 132f, 194f, 11f, 0f, max, value, 5, null, null, bracket, hasHome ? (float) home : Float.NaN);
    }

    @Override
    public String hover(float px, float py) {
        float lx = localX(px), ly = localY(py);
        HudData d = f.data;
        if (ly >= Y + 112f && lx >= X + 266f) {
            double home = d.flightStatus.deltaVHome;
            if (Double.isNaN(home)) return "Delta-v left in the tanks; no station in reach takes the ship";
            return "Delta-v left in the tanks; the bug is what the trip home needs";
        }
        if (ly >= Y + 112f) return "The pilot's trigger: guns, missiles, decoy";
        if (ly >= Y + 66f && lx >= X + 236f && lx < X + 350f) return "Who flies: " + (d.ctx.helmsman.isEmpty() ? "nobody" : d.ctx.helmsman);
        return null;
    }
}
