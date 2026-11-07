package com.ringsofiron.uihud.variants.vfd.task;

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
 * WPN, the weapons cluster in the bottom right pod beside the scope (functional-task 6, WPN): the
 * trip computer's table. Expanded at gunnery: what is in hand, then a row per mount (pips, where, what,
 * who works it, its state, and a gun's time of flight or a launcher's lock), then the decoy, READY or
 * its seconds, and on a ship with an emitter the shields. Compact at the helm in a fight: a pip per
 * mount, the missiles left and ready, the decoy, and the shields up or down.
 */
final class Weapons {
    static final int MOUNTS = 8;
    /** The table's columns, from the module's left, and their cells. */
    static final float PIPS = 5f, POS = 15f, GUN = 91f, WHO = 158f, ST = 225f, TOF = 337f;
    static final int POS_CELLS = 8, GUN_CELLS = 7, WHO_CELLS = 7, ST_CELLS = 12, TOF_CELLS = 4;
    static final float ROW = 14f, HEAD = 38f, DECOY = 28f, SHIELD = 18f;
    /** The compact form, at the helm: beside FLT, clear of it on any stage at least the grid's width. */
    static final float COMPACT_X = 880f, COMPACT_W = 192f, COMPACT_H = 66f;
    /** The module's foot: it grows up the pod as mounts are added. */
    static final float FOOT = 756f;

    boolean on, expanded, shields, decoyReady, noTarget;
    String head = "", decoy = "", shieldLine = "", missiles = "", trim = "";
    int rows;
    final String[] pos = new String[MOUNTS], gun = new String[MOUNTS], who = new String[MOUNTS], state = new String[MOUNTS],
            tof = new String[MOUNTS];
    /** A row's pips: amber can't fire for now, red broken; a compact pip's state: 0 ready, 1 waiting, 2 not working. */
    final boolean[] amber = new boolean[MOUNTS], red = new boolean[MOUNTS], hand = new boolean[MOUNTS];
    final int[] pip = new int[MOUNTS];
    boolean missileReady, raised;
    final float[] sectors = new float[4];
    float cloud;

    void update(HudData d, Posts p) {
        on = p.weapons() && d.weaponFrame.mounts.size > 0;
        if (!on) return;
        expanded = p.weaponsExpanded();
        boolean lock = d.targetStatus.present;
        float flight = d.targeting == null ? Float.NaN : d.targeting.getTimeToImpact();
        boolean solution = lock && !Float.isNaN(flight) && flight > 0f && !Float.isInfinite(flight);
        rows = 0;
        boolean anyHand = false, pdAuto = false;
        int left = 0;
        missileReady = false;
        for (Mount m : d.weaponFrame.mounts) {
            if (rows == MOUNTS) break;
            int i = rows++;
            pos[i] = place(m.name, POS_CELLS);
            gun[i] = Words.cut(m.weapon, GUN_CELLS);
            who[i] = who(m);
            hand[i] = m.controller == Controller.PLAYER;
            anyHand |= hand[i] && m.kind != Kind.POINT_DEFENSE;
            pdAuto |= m.kind == Kind.POINT_DEFENSE && m.controller != Controller.PLAYER;
            state[i] = state(m, lock, p.docked);
            boolean broken = m.state == State.DESTROYED || m.state == State.SHORTED;
            red[i] = broken;
            amber[i] = !broken && !m.working() && m.state != State.EMPTY;
            pip[i] = !m.working() ? 2 : m.state == State.READY ? 0 : 1;
            tof[i] = m.kind == Kind.LAUNCHER ? (lock && m.working() ? "LOCK" : "")
                    : hand[i] && m.kind != Kind.POINT_DEFENSE && solution ? Fmt.fit(flight, 2, 1) + "S" : "";
            if (m.kind == Kind.LAUNCHER) {
                left += Math.max(0, m.ammo);
                if (m.state == State.READY && m.ammo != 0) missileReady = true;
            }
        }
        head = p.atGun ? "THIS GUN" + (pdAuto ? ", PD AUTOMATIC" : "")
                : anyHand ? "GUNS IN HAND" + (pdAuto ? ", PD AUTOMATIC" : "") : pdAuto ? "PD AUTOMATIC" : "";
        missiles = Integer.toString(Math.min(99, Math.max(left, d.weaponFrame.missilesLeft)));
        // The decoy: unlimited, one every few seconds, so READY or the seconds until it is
        float cd = d.weaponFrame.decoyCooldown;
        decoyReady = !(cd > 0f);
        decoy = decoyReady ? "" : Fmt.secs(cd);
        ShieldStatus sh = d.shieldStatus;
        shields = sh.present && sh.emitters > 0;
        raised = sh.raised;
        cloud = sh.share();
        trim = sh.trim == null ? "" : sh.trim.toUpperCase(Locale.ROOT).startsWith("STAR") ? "STBD" : sh.trim.toUpperCase(Locale.ROOT);
        for (int i = 0; i < 4; i++) sectors[i] = sh.capacity > 0f ? Math.min(1f, sh.sectors[i] / (sh.capacity / 4f)) : 0f;
        shieldLine = !shields ? "" : sh.emittersTripped > 0 ? "TRIPPED" : sh.loweredForDock ? "STOWED" : sh.raised ? "UP" : "DOWN";
    }

    /** Where a mount sits, from the game's name without its kind ("FWD PORT PD 2" is "FWD PORT 2"), in {@code cells}. */
    static String place(String name, int cells) {
        String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        String[] w = n.split("\\s+");
        StringBuilder s = new StringBuilder();
        for (String word : w) {
            if (word.equals("GUN") || word.equals("PD") || word.equals("MSL") || word.equals("BEAM")) continue;
            if (s.length() > 0) s.append(' ');
            s.append(word);
        }
        String p = s.length() == 0 ? n : s.toString();
        if (p.length() <= cells) return p;
        // Keep a number it ends with, so two mounts on one side read apart
        int sp = p.lastIndexOf(' ');
        boolean numbered = sp > 0 && p.substring(sp + 1).chars().allMatch(Character::isDigit);
        if (!numbered) return Fmt.cut(p, cells);
        String tail = p.substring(sp);
        return Fmt.cut(p.substring(0, sp), cells - tail.length()).stripTrailing() + tail;
    }

    /** Who works a mount: HAND, GUNNER, CONSOLE, AUTO (point defense), or TRIGGER (nobody: it waits for the captain's). */
    static String who(Mount m) {
        switch (m.controller) {
            case PLAYER: return "HAND";
            case CREW: return "GUNNER";
            case CONSOLE: return "CONSOLE";
            case AUTO: return "AUTO";
            default: return m.kind == Kind.POINT_DEFENSE ? "AUTO" : "TRIGGER";
        }
    }

    /** A mount's state in the table's words. */
    static String state(Mount m, boolean lock, boolean docked) {
        switch (m.state) {
            case DESTROYED: return "DAMAGED";
            case REFIT: return "REFIT";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED " + Fmt.secs(m.cooldown) + " S";
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case CHARGING: return "CHARGING";
            default:
        }
        if (docked) return "HOLD";
        if (m.kind == Kind.POINT_DEFENSE) return m.engaging || m.defending ? "ENGAGING" : "IDLE";
        String rounds = m.kind == Kind.LAUNCHER && m.magazine > 0 && m.ammo >= 0 ? m.ammo + "/" + m.magazine + " " : "";
        if (m.state == State.RELOADING) return rounds + "RELOAD " + Fmt.fit(m.cooldown, 3, 1) + " S";
        if (!lock && m.kind != Kind.LAUNCHER) return "NO TARGET";
        if (lock && !m.inArc && m.kind != Kind.LAUNCHER) return "OUT OF ARC";
        return rounds + "READY";
    }

    /** The module's height. */
    float height() {
        if (!on) return 0f;
        if (!expanded) return COMPACT_H;
        return HEAD + rows * ROW + DECOY + (shields ? SHIELD : 0f);
    }

    /** The module's top on the grid. */
    float top() {
        return FOOT - height();
    }

    // ------------------------------------------------------------------ painting, from the right-bottom group's origin

    void paint(Filament f) {
        if (!on) return;
        if (expanded) paintTable(f);
        else paintCompact(f);
    }

    private void paintTable(Filament f) {
        float x = Geo.WPN_X, w = Geo.WPN_W, h = height(), y = top();
        f.module(x, y, w, h, f.o().filaments(4));
        f.dm(x + 6f, y + 6f, head, f.o().pitch(Geo.P).cells(Geo.fit(x + 6f, x + w - 6f)).norm());
        hd(f, x + POS, y, "MOUNT", POS_CELLS);
        hd(f, x + GUN, y, "GUN", GUN_CELLS);
        hd(f, x + WHO, y, "WHO", WHO_CELLS);
        hd(f, x + ST, y, "STATE", ST_CELLS);
        hd(f, x + TOF, y, "TOF", TOF_CELLS);
        for (int i = 0; i < rows; i++) {
            float ry = y + HEAD + i * ROW;
            // Pips first: amber for a gun that can't fire for now, red for one that is broken
            Kit.pip(f, x + PIPS, ry + 5.25f, Phosphor.AMBER, amber[i], 1.5f);
            Kit.pip(f, x + PIPS + 5f, ry + 5.25f, Phosphor.RED, red[i], 1.5f);
            f.dm(x + POS, ry, pos[i], f.o().pitch(Geo.P).cells(POS_CELLS).norm());
            f.dm(x + GUN, ry, gun[i], f.o().pitch(Geo.P).cells(GUN_CELLS).dim());
            f.dm(x + WHO, ry, who[i], f.o().pitch(Geo.P).cells(WHO_CELLS).level(hand[i] ? Drive.FULL : Drive.NORM));
            f.dm(x + ST, ry, Fmt.cut(state[i], ST_CELLS), f.o().pitch(Geo.P).cells(ST_CELLS).norm());
            f.dm(x + TOF, ry, tof[i], f.o().pitch(Geo.P).cells(TOF_CELLS).dim());
        }
        Kit.fits(x + TOF + f.dmWidth(TOF_CELLS, f.o().pitch(Geo.P)), x + w - 6f, "WPN columns");
        float by = y + HEAD + rows * ROW + 2f;
        f.rectLit(x + 6f, by, w - 12f, 0.7f, f.o().ghostOnly().k(2f));
        float b = by + 19f;
        float cx = x + POS + f.legend(x + POS, b, "DECOY", f.o().dim()) + 10f;
        cx += f.legend(cx, b, "READY", f.o().lit(decoyReady).norm()) + 10f;
        cx += f.seg7(cx, b - 12f, 12f, decoy, f.o().cells(1).right().norm()) + 4f;
        f.legend(cx, b, "S", f.o().dim().lit(!decoyReady));
        if (shields) paintShields(f, x, b + SHIELD, w);
    }

    private static void hd(Filament f, float x, float y, String t, int cells) {
        f.dm(x, y + 22f, t, f.o().pitch(Geo.P).cells(cells).dim().noGhost());
    }

    /** The shields' row: up, down, stowed or tripped; the cloud's share; the trim; four sectors by thickness. */
    private void paintShields(Filament f, float x, float b, float w) {
        float cx = x + POS + f.legend(x + POS, b, "SHIELDS", f.o().dim()) + 8f;
        Filament.Opt o = f.o().pitch(Geo.P).cells(7).norm();
        if (shieldLine.equals("TRIPPED")) o.amber().full();
        cx += f.dm(cx, b - 10.5f, shieldLine, o) + 6f;
        cx += f.seg7(cx, b - 12f, 12f, raised ? Integer.toString(Math.round(cloud * 100f)) : "", f.o().cells(3).right().norm()) + 3f;
        cx += f.legend(cx, b, "%", f.o().dim().lit(raised)) + 10f;
        cx += f.dm(cx, b - 10.5f, trim, f.o().pitch(Geo.P).cells(5).dim()) + 8f;
        for (int i = 0; i < 4; i++) {
            f.bar(cx, b - 9f, 22f, 8f, 4, raised ? sectors[i] : 0f, f.o().norm().gap(1.2f));
            cx += 26f;
        }
        Kit.fits(cx, x + w - 6f, "WPN shields row");
    }

    private void paintCompact(Filament f) {
        float x = COMPACT_X, w = COMPACT_W, h = COMPACT_H, y = top();
        f.module(x, y, w, h, f.o().filaments(2));
        float b1 = y + 17f, b2 = y + 38f, b3 = y + 59f;
        // A pip a mount: ready lit, waiting amber, not working red
        float cx = x + 8f + f.legend(x + 8f, b1, "GUNS", f.o().dim()) + 8f;
        for (int i = 0; i < rows; i++) {
            Phosphor c = pip[i] == 2 ? Phosphor.RED : pip[i] == 1 ? Phosphor.AMBER : Phosphor.ZINC;
            Kit.pip(f, cx + 3f, b1 - 4.5f, c, true, 2.4f);
            cx += 9f;
        }
        Kit.fits(cx, x + w - 6f, "WPN compact pips");
        float mx = x + 8f + f.legend(x + 8f, b2, "MSL", f.o().dim()) + 6f;
        mx += f.seg7(mx, b2 - 12f, 12f, missiles, f.o().cells(2).right().norm()) + 6f;
        mx += f.legend(mx, b2, "READY", f.o().lit(missileReady).norm());
        if (shields) {
            // The shields up or down, at the right: their icon, then the word
            float sw = f.dmWidth(7, f.o().pitch(Geo.P)), sx = x + w - 6f - sw;
            Filament.Opt o = f.o().pitch(Geo.P).cells(7).norm();
            if (shieldLine.equals("TRIPPED")) o.amber().full();
            f.dm(sx, b2 - 10.5f, shieldLine, o);
            f.icon("shield", sx - 18f, b2 - 13f, 14f, f.o().lit(raised).norm().stroke(1.5f));
            Kit.fits(mx + 8f, sx - 18f, "WPN compact missiles and shields");
        }
        float dx = x + 8f + f.legend(x + 8f, b3, "DECOY", f.o().dim()) + 8f;
        dx += f.legend(dx, b3, "READY", f.o().lit(decoyReady).norm()) + 8f;
        dx += f.seg7(dx, b3 - 12f, 12f, decoy, f.o().cells(1).right().norm()) + 4f;
        f.legend(dx, b3, "S", f.o().dim().lit(!decoyReady));
    }
}
