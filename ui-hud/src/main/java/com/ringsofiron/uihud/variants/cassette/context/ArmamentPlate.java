package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.Locale;

/**
 * H7, the armament plate under the lock card at the helm in combat: can I shoot, and with what. Three
 * rows, each a lamp, an engraved name and its state on flaps: the guns and who fires them, the missiles
 * left across every launcher and when one can go, and the shields, raised or lowered with the trim on a
 * rose of five jewels. A hull without an emitter has a blanking plate where the shields row would be.
 * The decoy is the threat unit's.
 */
final class ArmamentPlate extends YardPart {
    static final float W = 308f, H = 70f;

    private final ContextFrame f;

    ArmamentPlate(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The guns as one state (H7): READY, RELOAD, CHARGING, OUT OF ARC, NO POWER, DOWN; "" with none. */
    static String guns(WeaponFrame w, boolean locked) {
        boolean any = false, ready = false, reloading = false, charging = false, outOfArc = false, unpowered = false;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (m.kind != Kind.GUN && m.kind != Kind.BEAM) continue;
            any = true;
            switch (m.state) {
                case READY:
                    if (locked && !m.inArc) outOfArc = true;
                    else ready = true;
                    break;
                case RELOADING: reloading = true; break;
                case CHARGING: charging = true; break;
                case NO_POWER: unpowered = true; break;
                default: break;
            }
        }
        if (!any) return "";
        if (ready) return "READY";
        if (reloading) return "RELOAD";
        if (charging) return "CHARGING";
        if (outOfArc) return "OUT OF ARC";
        if (unpowered) return "NO POWER";
        return "DOWN";
    }

    /** Who fires the guns: YOU FIRE (only the captain's trigger), GUNNER, CONSOLE, or HOLD FIRE. */
    static String who(HudData d) {
        if (d.ctx.holdFire || d.weaponFrame.holdFire) return "HOLD FIRE";
        boolean gunner = false, console = false;
        for (int i = 0; i < d.weaponFrame.mounts.size; i++) {
            Mount m = d.weaponFrame.mounts.get(i);
            if (m.kind != Kind.GUN && m.kind != Kind.BEAM) continue;
            if (m.controller == Controller.CREW) gunner = true;
            if (m.controller == Controller.CONSOLE) console = true;
        }
        return console ? "CONSOLE" : gunner ? "GUNNER" : "YOU FIRE";
    }

    /** Only point defense: the ship has guns, but none in anyone's hand. */
    static boolean pdOnly(WeaponFrame w) {
        boolean pd = false;
        for (int i = 0; i < w.mounts.size; i++) {
            Kind k = w.mounts.get(i).kind;
            if (k == Kind.GUN || k == Kind.BEAM) return false;
            if (k == Kind.POINT_DEFENSE) pd = true;
        }
        return pd;
    }

    static boolean launchers(WeaponFrame w) {
        for (int i = 0; i < w.mounts.size; i++) if (w.mounts.get(i).kind == Kind.LAUNCHER) return true;
        return false;
    }

    /** The missiles' state: READY while any launcher can throw one, else the seconds until one can, or EMPTY. */
    static String missiles(WeaponFrame w) {
        if (w.missilesLeft == 0) return "EMPTY";
        float soonest = Float.NaN;
        boolean unpowered = false;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (m.kind != Kind.LAUNCHER) continue;
            if (m.state == State.READY) return "READY";
            if (m.state == State.RELOADING || m.state == State.CHARGING) {
                if (Float.isNaN(soonest) || m.cooldown < soonest) soonest = m.cooldown;
            }
            if (m.state == State.NO_POWER) unpowered = true;
        }
        if (!Float.isNaN(soonest)) return "IN " + Math.max(1, (int) Math.ceil(soonest)) + " S";
        return unpowered ? "NO POWER" : "DOWN";
    }

    /** The shields' state in one word, as shield control words it. */
    static String shields(ShieldStatus s) {
        if (s.emittersTripped > 0 && s.emittersWorking == 0) return "TRIPPED";
        if (s.powerStunned) return "STUNNED";
        if (s.raised) return "RAISED";
        if (s.loweredForDock) return "DOCKING";
        return s.cloud > 0.5f ? "STOWING" : "LOWERED";
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        WeaponFrame wf = d.weaponFrame;
        Kit.slideRail(p, f.screenW - x, false, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        p.face(6f, 4f, w - 12f, h - 8f, Yard.Finish.YARD, false);
        boolean locked = d.targetStatus.present;
        // GUNS
        String g = pdOnly(wf) ? "PD ONLY" : guns(wf, locked);
        p.lamp(18f, 16f, 3.2f, g.equals("READY") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, !g.isEmpty() && !g.equals("PD ONLY"));
        p.legend("GUNS", 28f, 20f, Yard.INK);
        p.flaps(74f, 8f, g.isEmpty() ? "NONE" : g, 10, 8.2f, 16f, 12.5f, g.equals("OUT OF ARC") || g.equals("NO POWER") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        if (!g.isEmpty() && !g.equals("PD ONLY")) p.flaps(166f, 8f, who(d), 9, 8.2f, 16f, 12.5f, d.ctx.holdFire ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        // MSL
        if (launchers(wf)) {
            String m = missiles(wf);
            p.lamp(18f, 36f, 3.2f, m.equals("READY") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, !m.equals("EMPTY"));
            p.legend("MSL", 28f, 40f, Yard.INK);
            String left = wf.missilesLeft < 0 ? "--" : String.valueOf(Math.min(99, wf.missilesLeft));
            float dw = p.drums(74f, 27f, left.length() < 2 ? " " + left : left, 11f, 17f, 14f, false);
            p.legend("LEFT", 74f + dw + 5f, 40f, Yard.INK);
            p.flaps(166f, 28f, m, 9, 8.2f, 16f, 12.5f, m.equals("EMPTY") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        } else {
            p.legend("MSL", 28f, 40f, Kit.DIM_INK);
            Kit.blankBay(p, 72f, 27f, 226f, 18f);
        }
        // SHLD, or the blanking plate
        if (f.emitter) {
            ShieldStatus s = d.shieldStatus;
            String st = shields(s);
            p.lamp(18f, 56f, 3.2f, st.equals("RAISED") ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, !st.equals("LOWERED"));
            p.legend("SHLD", 28f, 60f, Yard.INK);
            p.flaps(74f, 48f, st, 8, 8.2f, 16f, 12.5f, st.equals("TRIPPED") || st.equals("STUNNED") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
            p.legend("TRIM", 154f, 60f, Yard.INK);
            p.flaps(186f, 48f, s.trim.toUpperCase(Locale.ROOT).replace("STARBOARD", "STBD"), 5, 8.2f, 16f, 12.5f, Yard.FLAP_TEXT);
            rose(p, 268f, 56f, s.trim);
        } else {
            Kit.blankBay(p, 12f, 47f, 286f, 18f);
        }
    }

    /** The trim's rose: five jewels, the bow up, the one the trim leans to lit. */
    static void rose(YardPaint p, float cx, float cy, String trim) {
        String t = trim == null ? "even" : trim.toLowerCase(Locale.ROOT);
        float k = 8f, r = 2.4f;
        p.circle(cx, cy, k + 5f, Yard.BAKELITE);
        p.lamp(cx, cy, r, Yard.WHITE_LAMP, t.equals("even"));
        p.lamp(cx, cy - k, r, Yard.WHITE_LAMP, t.equals("bow"));
        p.lamp(cx + k, cy, r, Yard.WHITE_LAMP, t.equals("starboard"));
        p.lamp(cx, cy + k, r, Yard.WHITE_LAMP, t.equals("stern"));
        p.lamp(cx - k, cy, r, Yard.WHITE_LAMP, t.equals("port"));
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        float ly = localY(py);
        if (ly < 26f) {
            String g = guns(d.weaponFrame, d.targetStatus.present);
            if (g.equals("OUT OF ARC")) return "No gun bears on the lock: turn the ship";
            String who = who(d);
            if (who.equals("YOU FIRE")) return "Space fires the guns: no gunner has them";
            if (who.equals("HOLD FIRE")) return "The crew hold their fire";
            return null;
        }
        if (ly < 46f) return d.weaponFrame.missilesLeft > 0 ? Kit.key(d, "MISSILE", "R") + " fires a missile at the lock · only a station restocks them" : null;
        if (!f.emitter) return "No shield emitter aboard";
        return Kit.key(d, "SHIELDS", "9") + " raises or lowers the shields · " + Kit.key(d, "SHIELD_TRIM", "8") + " steps the trim";
    }
}
