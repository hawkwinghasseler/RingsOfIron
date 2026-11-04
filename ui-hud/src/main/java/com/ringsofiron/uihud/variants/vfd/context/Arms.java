package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * The ship's arms (functional-context 7, H7 and F2). H7, the armament strip, under the lock card at
 * the helm in a fight: the guns' state and who fires them, the missiles left across every launcher
 * and ready while any can fire, and on a hull with an emitter the shields raised or lowered and their
 * trim. F2, the weapons rack, in fire control's tower: a row per mount, the ones in hand marked, with
 * its weapon, state, hit chance on the Lock, missiles left for a launcher, and who fires it; at a gun
 * seat it shrinks to that gun's row and any launcher's. A launcher has no hit chance, so its missiles
 * left take that column ("4 LEFT"), and a name is never cut inside a word or off its number.
 */
final class Arms {
    static final int MOUNTS = 8;
    /**
     * F2's columns, cells. The game names a mount by its place and kind, numbered when two share both,
     * so a name runs to fifteen ("FWD STBD BEAM 2"), thirteen kept at a gun seat's narrower rack; a
     * weapon to sixteen ("HARPOON LAUNCHER"); a state to thirteen ("RELOAD 10.5 S").
     */
    static final int NAME = 15, NAME_AT_GUN = 13, WEAPON = 16, STATE = 13;
    static final float HEADER = 16f, ROW = 24f;
    static final String[] WHO = {"YOU", "GUNNER", "PD"}, READY = {"READY", "RELOAD"}, RAISED = {"RAISED", "LOWERED"};

    // H7
    boolean strip, guns, launchers, shields;
    String gunState = "", missiles = "", reload = "", trim = "";
    int who = -1;
    boolean missileReady, raised;

    // F2
    boolean rack, narrow;
    final String[] name = new String[MOUNTS], weapon = new String[MOUNTS], state = new String[MOUNTS], hit = new String[MOUNTS],
            word = new String[MOUNTS], by = new String[MOUNTS];
    /** The row is a launcher's: its hit column holds the missiles left. */
    final boolean[] inHand = new boolean[MOUNTS], bad = new boolean[MOUNTS], left = new boolean[MOUNTS];
    int rows;

    private final float[] lit3 = new float[3], lit2 = new float[2];

    void update(HudData d, Situations s) {
        strip = s.seat == Situations.Seat.HELM && s.combat && !s.warp && !s.docked && !s.latched && !s.fullScreen;
        rack = (s.seat == Situations.Seat.FIRE_CONTROL && !s.docked && !s.latched || s.seat == Situations.Seat.GUN) && !s.fullScreen;
        narrow = s.seat == Situations.Seat.GUN;
        if (strip) strip(d);
        rows = 0;
        if (rack) rack(d, narrow);
    }

    private void strip(HudData d) {
        guns = launchers = false;
        boolean readyInArc = false, ready = false, noPower = false, empty = false, stunned = false;
        float soonest = Float.NaN;
        boolean you = false, gunner = false, pd = false;
        int left = 0;
        boolean anyLeft = false, launcherReady = false;
        float launcherIn = Float.NaN;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind == Kind.POINT_DEFENSE) {
                pd = true;
                continue;
            }
            if (m.kind == Kind.LAUNCHER) {
                launchers = true;
                if (m.ammo > 0) {
                    left += m.ammo;
                    anyLeft = true;
                }
                if (m.state == State.READY && m.ammo != 0) launcherReady = true;
                else if ((m.state == State.RELOADING || m.state == State.CHARGING) && (Float.isNaN(launcherIn) || m.cooldown < launcherIn)) launcherIn = m.cooldown;
                continue;
            }
            guns = true;
            if (m.controller == Controller.PLAYER) you = true;
            else if (m.controller == Controller.CREW || m.controller == Controller.CONSOLE) gunner = true;
            switch (m.state) {
                case READY:
                    ready = true;
                    readyInArc |= m.inArc;
                    break;
                case RELOADING:
                case CHARGING:
                    if (Float.isNaN(soonest) || m.cooldown < soonest) soonest = m.cooldown;
                    break;
                case NO_POWER: noPower = true; break;
                case EMPTY: empty = true; break;
                case STUNNED: stunned = true; break;
                default:
            }
        }
        if (readyInArc || ready && !d.targetStatus.present) gunState = "READY";
        else if (ready) gunState = "OUT OF ARC";
        else if (!Float.isNaN(soonest)) gunState = "RELOAD " + Fmt.secs(soonest) + "S";
        else if (noPower) gunState = "NO POWER";
        else if (empty) gunState = "EMPTY";
        else if (stunned) gunState = "STUNNED";
        else gunState = "DAMAGED";
        who = you ? 0 : gunner ? 1 : pd ? 2 : -1;
        int total = Math.max(left, d.weaponFrame.missilesLeft);
        missiles = launchers ? Integer.toString(Math.min(99, total)) : "";
        missileReady = launcherReady && (anyLeft || total > 0);
        reload = !missileReady && !Float.isNaN(launcherIn) ? Fmt.secs(Math.min(launcherIn, 99f)) : "";
        ShieldStatus sh = d.shieldStatus;
        shields = sh.present && sh.emitters > 0;
        raised = sh.raised;
        trim = trimWord(sh.trim);
    }

    static String trimWord(String trim) {
        String t = trim == null ? "" : trim.toUpperCase(Locale.ROOT);
        return t.startsWith("STAR") ? "STBD" : t;
    }

    private void rack(HudData d, boolean gunSeat) {
        for (Mount m : d.weaponFrame.mounts) {
            if (rows == MOUNTS) break;
            if (gunSeat && m.kind != Kind.LAUNCHER && m.controller != Controller.PLAYER) continue;
            int i = rows++;
            name[i] = mountName(m.name.isEmpty() ? "MOUNT " + (m.index + 1) : m.name, gunSeat ? NAME_AT_GUN : NAME);
            weapon[i] = Fmt.cut(m.weapon, WEAPON);
            inHand[i] = m.kind != Kind.POINT_DEFENSE && (m.controller == Controller.PLAYER || m.controller == Controller.CONSOLE);
            state[i] = mountState(m, d.targetStatus.present);
            bad[i] = !m.working();
            boolean hits = m.kind != Kind.POINT_DEFENSE && m.kind != Kind.LAUNCHER && !Float.isNaN(m.hitChance) && d.targetStatus.present;
            left[i] = m.kind == Kind.LAUNCHER && m.ammo >= 0;
            hit[i] = hits ? Integer.toString(Math.min(99, Math.round(m.hitChance * 100f)))
                    : left[i] ? Integer.toString(Math.min(99, m.ammo)) : "";
            word[i] = hits ? Lock.WORDS[m.hitChance >= Lock.GOOD ? 0 : m.hitChance >= Lock.FAIR ? 1 : 2] : "";
            by[i] = controller(m);
        }
    }

    /** A mount's state in the rack's words: ready, reloading, no power, damaged, stunned, out of arc, engaging. */
    static String mountState(Mount m, boolean lock) {
        String s;
        switch (m.state) {
            case READY:
                if (m.kind == Kind.POINT_DEFENSE) s = m.engaging ? "ENGAGING MSL" : "READY";
                else s = lock && !m.inArc && m.kind != Kind.LAUNCHER ? "OUT OF ARC" : "READY";
                break;
            case RELOADING:
            case CHARGING:
                s = "RELOAD " + Fmt.fit(m.cooldown, 3, 1) + " S";
                break;
            case NO_POWER: s = "NO POWER"; break;
            case STUNNED: s = "STUNNED " + Fmt.secs(m.cooldown) + " S"; break;
            case EMPTY: s = "EMPTY"; break;
            case REFIT: s = "REFIT"; break;
            default: s = "DAMAGED";
        }
        return s;
    }

    /**
     * A mount's name in {@code cells}: whole when it fits, else cut at its end but keeping a number it
     * ends with, so two launchers on one side still read apart ("FWD STBD MS 2").
     */
    static String mountName(String name, int cells) {
        name = name.toUpperCase(Locale.ROOT);
        if (name.length() <= cells) return name;
        int sp = name.lastIndexOf(' ');
        boolean numbered = sp > 0 && sp < name.length() - 1;
        for (int i = sp + 1; numbered && i < name.length(); i++) numbered = Character.isDigit(name.charAt(i));
        if (!numbered || name.length() - sp >= cells) return Fmt.cut(name, cells);
        String tail = name.substring(sp);
        return name.substring(0, cells - tail.length()).stripTrailing() + tail;
    }

    static String controller(Mount m) {
        if (m.kind == Kind.POINT_DEFENSE) return "PD";
        switch (m.controller) {
            case PLAYER:
            case CONSOLE: return "YOU";
            case CREW: return "CREW";
            case AUTO: return "AUTO";
            default: return "";
        }
    }

    float stripHeight() {
        if (!strip) return 0f;
        int n = (guns ? 1 : 0) + (launchers ? 1 : 0) + (shields ? 1 : 0);
        return n == 0 ? 0f : n * Geo.ROW + 6f;
    }

    /** The rack's height: its header and a row a mount. */
    float rackHeight() {
        return rack && rows > 0 ? HEADER + rows * ROW + 6f : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    void paintStrip(Filament f, float y) {
        float h = stripHeight();
        if (h == 0f) return;
        float x = Geo.RX;
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        float ry = y + 3f, cx;
        if (guns) {
            float b = Geo.base(ry);
            f.legend(x + 6f, b, "GUNS", f.o().dim());
            f.dm(x + 52f, Geo.dmTop(b), gunState, f.o().pitch(Geo.P).cells(10).norm());
            for (int i = 0; i < 3; i++) lit3[i] = i == who ? Drive.NORM : Float.NaN;
            cx = Kit.states(f, x + 52f + 10 * Geo.CELL + 8f, b, WHO, lit3, 13f, 7f);
            Kit.fits(cx - 7f, x + Geo.CW - 4f, "H7 guns");
            ry += Geo.ROW;
        }
        if (launchers) {
            float b = Geo.base(ry);
            f.legend(x + 6f, b, "MSL", f.o().dim());
            cx = Rows.num(f, x + 52f, b, missiles, 2);
            cx = Rows.lab(f, cx, b, "LEFT") + 8f;
            lit2[0] = missileReady ? Drive.NORM : Float.NaN;
            lit2[1] = !reload.isEmpty() ? Drive.NORM : Float.NaN;
            cx = Kit.states(f, cx, b, READY, lit2, 13f, 6f);
            cx = Rows.num(f, cx, b, reload, 2) - 1f;
            Kit.fits(Rows.lab(f, cx, b, "S"), x + Geo.CW - 4f, "H7 missiles");
            ry += Geo.ROW;
        }
        if (shields) {
            float b = Geo.base(ry);
            f.legend(x + 6f, b, "SHLD", f.o().dim());
            lit2[0] = raised ? Drive.NORM : Float.NaN;
            lit2[1] = !raised ? Drive.NORM : Float.NaN;
            cx = Kit.states(f, x + 52f, b, RAISED, lit2, 13f, 6f) + 6f;
            cx = Rows.lab(f, cx, b, "TRIM");
            Kit.fits(Rows.txt(f, cx, b, trim, 5), x + Geo.CW - 4f, "H7 shields");
        }
    }

    /** F2 in the tower, its top at {@code y}: 620 wide at fire control, 400 at a gun without the weapon column. */
    void paintRack(Filament f, float y) {
        float h = rackHeight();
        if (h == 0f) return;
        float x = narrow ? Geo.H1_X : Geo.F2_X, w = narrow ? Geo.H1_W : Geo.F2_W;
        f.module(x, y, w, h, f.o().filaments(3));
        // A gun seat's rack is H1's width: its gaps close up to keep the name and the state whole
        int names = narrow ? NAME_AT_GUN : NAME;
        float gap = narrow ? 6f : 10f;
        float nx = x + 22f, wx = nx + names * Geo.CELL + gap, sx = narrow ? wx : wx + WEAPON * Geo.CELL + gap;
        float hx = sx + STATE * Geo.CELL + gap, bx = x + w - 8f - 4 * Geo.CELL;
        float hb = y + 13f;
        f.legend(nx, hb, "MOUNT", f.o().size(11f).dim());
        if (!narrow) f.legend(wx, hb, "WEAPON", f.o().size(11f).dim());
        f.legend(sx, hb, "STATE", f.o().size(11f).dim());
        f.legend(hx, hb, "HIT", f.o().size(11f).dim());
        f.legend(bx, hb, "BY", f.o().size(11f).dim());
        for (int i = 0; i < rows; i++) {
            float ry = y + HEADER + i * ROW, ty = ry + (ROW - 7f * Geo.P) / 2f, b = ty + 10.5f;
            f.dm(x + 8f, ty, inHand[i] ? "▶" : "", f.o().pitch(Geo.P).cells(1).norm());
            f.dm(nx, ty, name[i], f.o().pitch(Geo.P).cells(names).norm());
            if (!narrow) f.dm(wx, ty, weapon[i], f.o().pitch(Geo.P).cells(WEAPON).dim());
            f.dm(sx, ty, state[i], f.o().pitch(Geo.P).cells(STATE).level(bad[i] ? Drive.FULL : Drive.NORM)
                    .color(bad[i] ? Phosphor.AMBER : Phosphor.ZINC));
            // "%" hugs its number; "LEFT" is a word and stands a space off
            float cx = hx + f.seg7(hx, b - 12f, 12f, hit[i], f.o().cells(2).right().norm()) + (left[i] ? 6f : 3f);
            cx += f.legend(cx, b, left[i] ? "LEFT" : "%", f.o().dim()) + 6f;
            if (!left[i]) {
                f.dm(cx, ty, word[i], f.o().pitch(Geo.P).cells(4).norm());
                cx += 4 * Geo.CELL;
            }
            f.dm(bx, ty, by[i], f.o().pitch(Geo.P).cells(4).norm());
            Kit.fits(cx, bx - 4f, "F2 row");
        }
    }
}
