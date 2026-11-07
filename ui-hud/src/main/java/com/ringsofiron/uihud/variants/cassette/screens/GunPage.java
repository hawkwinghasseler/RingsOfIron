package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The gun panel's one tab, the mount (the Stations spec's 10.2): its weapon, its state, its ammunition and
 * reload, who works it and so how it fires, in the WEAPONS page's words (AT WILL, TRIGGER, HOLD, PD AUTO;
 * YOURS once in hand), its arc, its chance on the locked target, and its health. Its switch is on the first
 * soft key, Enter pressing it, as the console window has it; the second takes it in hand, as V does. For a
 * point-defense mount the panel says, before the captain sits, that in hand it hunts no missiles (the player
 * aims it), and names any side no other point defense can then see ("FWD PORT PD CAN'T SEE TO STARBOARD").
 *
 * <p>The mount is the player's ship's, as the weapon status has it; a prize's gun shows its system's state
 * and health, with its switch.
 */
final class GunPage extends ListPage {
    static final int WEAPON = 0, STATE = 1, AMMO = 2, RELOAD = 3, WHO = 4, MODE = 5, ARC = 6, TARGET = 7, HEALTH = 8,
            HAND = 9, COVER = 10, COVER_ON = 11;
    /** The four sides, as bearings off the nose (port positive), and how the panel says them. */
    static final float[] SIDES = {0f, 90f, 180f, -90f};
    static final String[] SIDE_WORDS = {"AHEAD", "TO PORT", "ASTERN", "TO STARBOARD"};

    final IntArray lines = new IntArray();
    private HudData data;
    private SubsystemComponent gun;
    private boolean own;
    /** Who works it, as the station board has it at refresh. */
    private String who = "";
    /** The mount on the gun, read again each frame (the weapon status reuses its rows). */
    private Mount mount;

    GunPage() {
        super("GUN", "GUN", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return gun != null;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        SubsystemComponent s = ConsolePanels.system(data);
        gun = s != null && s.type == SubsystemType.TURRET_MOUNT ? s : null;
        Entity ship = ConsolePanels.ship(data);
        own = data != null && ship != null && ship == data.ctx.ship;
        Mount m = mount();
        label = m != null && !m.name.isEmpty() ? m.name : "GUN";
        who = m == null ? "" : who(m, seat());
        lines.clear();
        if (gun == null) return;
        lines.add(WEAPON);
        lines.add(STATE);
        if (m != null) {
            if (m.magazine > 0) lines.add(AMMO);
            if (m.reload > 0f) lines.add(RELOAD);
            lines.add(WHO);
            lines.add(MODE);
            lines.add(ARC);
            lines.add(TARGET);
        }
        lines.add(HEALTH);
        if (m != null && m.kind == Kind.POINT_DEFENSE) {
            lines.add(HAND);
            lines.add(COVER);
            // The other mount's name can be long: the line runs on to the next row when it must
            lines.add(COVER_ON);
        }
        keepSelection();
    }

    /** The mount on the gun, the player's ship's only; null for a prize's, or none found. */
    private Mount mount() {
        if (gun == null || !own || data == null) return null;
        return ShipSystems.mount(gun, data.weaponFrame.mounts);
    }

    /** The gun's post on the station board, the player's ship's; null for none. */
    private Seat seat() {
        if (!own || data == null || data.console.console == null) return null;
        for (int i = 0; i < data.stations.seats.size; i++) {
            Seat st = data.stations.seats.get(i);
            if (st.console == data.console.console) return st;
        }
        return null;
    }

    /** The console's own order that takes the gun in hand (V), or null where the game offers none. */
    private HudData.ConsoleAction take() {
        if (data == null) return null;
        for (int i = 0; i < data.console.actions.size; i++) {
            HudData.ConsoleAction a = data.console.actions.get(i);
            if ("VIEW".equals(a.action) && a.run != null) return a;
        }
        return null;
    }

    /** Whether the captain holds fire. */
    private boolean hold() {
        return data != null && (data.weaponFrame.holdFire || data.ctx.holdFire);
    }

    // --- The words -----------------------------------------------------------------------------

    /**
     * A mount's state in the gun chips' words (the Stations spec's 8.3), a short or a stun with the seconds
     * its system {@code s} has left of it.
     */
    static String stateWord(Mount m, SubsystemComponent s) {
        switch (m.state) {
            case DESTROYED:
                return "DESTROYED";
            case REFIT:
                return "REFIT";
            case SHORTED:
            case STUNNED: {
                String w = m.state == WeaponStatusProvider.State.SHORTED ? "SHORTED" : "STUNNED";
                float t = s == null ? Float.NaN : ShipSystems.timer(s, ShipSystems.state(s));
                return Float.isNaN(t) ? w : w + " " + ShipSystems.seconds(t);
            }
            case NO_POWER:
                return "NO POWER";
            case EMPTY:
                return "EMPTY";
            case RELOADING:
                return String.format(Locale.ROOT, "RELOAD %.1f", Math.max(0f, m.cooldown));
            case CHARGING:
                return "CHARGING";
            default:
                if (m.engaging) return m.kind == Kind.BEAM ? "BURNING" : "FIRING";
                return "READY";
        }
    }

    /** How a mount fires (3.5): YOURS in the player's hand, PD AUTO, HOLD, AT WILL with a gunner on it, else TRIGGER. */
    static String mode(Mount m, boolean hold) {
        if (m.controller == Controller.PLAYER) return "YOURS";
        if (m.kind == Kind.POINT_DEFENSE || m.controller == Controller.AUTO) return "PD AUTO";
        if (hold) return "HOLD";
        if (m.weaponsFree) return "AT WILL";
        return "TRIGGER";
    }

    /** What a mode means, for the row after it. */
    static String means(String mode) {
        switch (mode) {
            case "YOURS":
                return "YOU AIM IT AND FIRE IT";
            case "PD AUTO":
                return "IT HUNTS MISSILES BY ITSELF";
            case "HOLD":
                return "THE CAPTAIN HOLDS FIRE";
            case "AT WILL":
                return "ITS GUNNER FIRES ON THE TARGET";
            default:
                return "IT FIRES ON THE TRIGGER ONLY";
        }
    }

    /** Who works it: you, its gunner by name (or on the way), the weapons console's crew, itself, nobody. */
    static String who(Mount m, Seat seat) {
        if (m.controller == Controller.PLAYER) return "YOU";
        if (m.controller == Controller.AUTO) return "ITSELF";
        if (seat != null && seat.hand == Hand.PLAYER) return "YOU";
        if (seat != null && seat.hand == Hand.CREW && !seat.who.isEmpty()) return Fmt.upper(seat.who);
        if (seat != null && seat.hand == Hand.COMING && !seat.who.isEmpty()) return Fmt.upper(seat.who) + ", ON THE WAY";
        if (m.controller == Controller.CREW) return "A GUNNER";
        if (m.controller == Controller.CONSOLE) return "THE WEAPONS CONSOLE";
        return "NOBODY";
    }

    /** Its arc and which way it faces: "120° ARC TO PORT", "ALL ROUND". */
    static String arc(Mount m) {
        if (m.arcDeg >= 359f) return "ALL ROUND";
        return Math.round(m.arcDeg) + "° ARC " + facing(m.facingDeg);
    }

    /** A bearing off the nose (port positive) as the nearest of eight: "AHEAD", "ASTERN TO STARBOARD". */
    static String facing(float deg) {
        int k = Math.floorMod(Math.round(wrap(deg) / 45f), 8);
        switch (k) {
            case 0:
                return "AHEAD";
            case 1:
                return "AHEAD TO PORT";
            case 2:
                return "TO PORT";
            case 3:
                return "ASTERN TO PORT";
            case 4:
                return "ASTERN";
            case 5:
                return "ASTERN TO STARBOARD";
            case 6:
                return "TO STARBOARD";
            default:
                return "AHEAD TO STARBOARD";
        }
    }

    /** Whether {@code m}'s arc reaches into the quarter of the sky centred {@code deg} off the nose. */
    static boolean sees(Mount m, float deg) {
        return m.arcDeg >= 359f || Math.abs(wrap(deg - m.facingDeg)) < 45f + m.arcDeg / 2f;
    }

    /**
     * What the rest of the point defense can't see once {@code m} is in hand and hunts nothing: the sides it
     * sees that no other working point-defense mount does, said by the other mount when there is one.
     */
    static String cover(Mount m, Array<Mount> mounts) {
        Mount only = null;
        int others = 0;
        for (int i = 0; i < mounts.size; i++) {
            Mount o = mounts.get(i);
            if (o == m || o.kind != Kind.POINT_DEFENSE || !o.working() || o.controller == Controller.PLAYER) continue;
            others++;
            only = o;
        }
        if (others == 0) return "NO OTHER POINT DEFENSE IS WORKING";
        Array<String> lost = new Array<>();
        for (int k = 0; k < SIDES.length; k++) {
            if (!sees(m, SIDES[k])) continue;
            boolean seen = false;
            for (int i = 0; i < mounts.size && !seen; i++) {
                Mount o = mounts.get(i);
                seen = o != m && o.kind == Kind.POINT_DEFENSE && o.working() && o.controller != Controller.PLAYER && sees(o, SIDES[k]);
            }
            if (!seen) lost.add(SIDE_WORDS[k]);
        }
        if (lost.size == 0) return others == 1 ? Fmt.upper(only.name) + " SEES ALL IT DOES" : "THE OTHER POINT DEFENSE SEES ALL IT DOES";
        // "AHEAD, ASTERN OR TO PORT"
        StringBuilder sides = new StringBuilder();
        for (int i = 0; i < lost.size; i++) {
            if (i > 0) sides.append(i == lost.size - 1 ? " OR " : ", ");
            sides.append(lost.get(i));
        }
        return others == 1 ? Fmt.upper(only.name) + " CAN'T SEE " + sides : "NO OTHER POINT DEFENSE SEES " + sides;
    }

    /** Whether {@link #cover}'s words say a side goes unwatched. */
    static boolean uncovered(String cover) {
        return cover.contains("CAN'T") || cover.startsWith("NO OTHER");
    }

    /**
     * Line {@code part} (0 or 1) of {@code text} wrapped to {@code room} columns at a space: the second has
     * the rest, cut to fit; "" when the first holds it all.
     */
    static String wrapped(String text, int room, int part) {
        if (text.length() <= room) return part == 0 ? text : "";
        int at = text.lastIndexOf(' ', room);
        if (at <= 0) at = room;
        return part == 0 ? text.substring(0, at) : Ledger.cut(text.substring(at).trim(), room);
    }

    private static float wrap(float deg) {
        float a = deg % 360f;
        if (a > 180f) a -= 360f;
        if (a <= -180f) a += 360f;
        return a;
    }

    // --- The list ------------------------------------------------------------------------------

    @Override
    int count() {
        return lines.size;
    }

    @Override
    boolean selectable(int i) {
        return false;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Mount m = mount;
        switch (lines.get(i)) {
            case WEAPON:
                L.at(p, 1, r, "WEAPON", DIM);
                L.at(p, 12, r, Ledger.cut(m != null && !m.weapon.isEmpty() ? Fmt.upper(m.weapon) : ShipSystems.name(gun, null), L.cols - 13), AMBER);
                return;
            case STATE: {
                String word;
                boolean bad;
                if (m != null) {
                    word = stateWord(m, gun);
                    bad = !m.working();
                } else {
                    ShipSystems.State st = ShipSystems.state(gun);
                    float t = ShipSystems.timer(gun, st);
                    word = Float.isNaN(t) ? PowerPage.word(st) : PowerPage.word(st) + " " + ShipSystems.seconds(t);
                    bad = st != ShipSystems.State.ON;
                }
                L.at(p, 1, r, "STATE", DIM);
                L.at(p, 12, r, Ledger.cut(word, L.cols - 13), bad ? Yard.P_RED : AMBER);
                return;
            }
            case AMMO: {
                if (m == null) return;
                Color ink = m.ammo == 0 ? Yard.P_RED : AMBER;
                L.at(p, 1, r, "AMMO", DIM);
                L.at(p, 12, r, Math.max(0, m.ammo) + " OF " + m.magazine, ink);
                return;
            }
            case RELOAD: {
                if (m == null) return;
                float ready = m.readiness();
                L.at(p, 1, r, "RELOAD", DIM);
                bar(p, 12, 10, r, ready, AMBER);
                String left = m.cooldown > 0f ? String.format(Locale.ROOT, "%.1f S", m.cooldown) : "LOADED";
                L.at(p, 23, r, Ledger.cut(left + String.format(Locale.ROOT, ", EVERY %.1f S", m.reload), L.cols - 24), AMBER);
                return;
            }
            case WHO:
                L.at(p, 1, r, "WORKED BY", DIM);
                L.at(p, 12, r, Ledger.cut(who, L.cols - 13), AMBER);
                return;
            case MODE: {
                if (m == null) return;
                String mode = mode(m, hold());
                L.at(p, 1, r, "MODE", DIM);
                L.at(p, 12, r, mode, AMBER);
                L.at(p, 21, r, Ledger.cut(means(mode), L.cols - 22), DIM);
                return;
            }
            case ARC:
                if (m == null) return;
                L.at(p, 1, r, "ARC", DIM);
                L.at(p, 12, r, Ledger.cut(arc(m), L.cols - 13), AMBER);
                return;
            case TARGET: {
                if (m == null) return;
                String target = data == null ? "" : data.weaponFrame.target;
                L.at(p, 1, r, "TARGET", DIM);
                if (target.isEmpty()) {
                    L.at(p, 12, r, "NO LOCK", DIM);
                    return;
                }
                L.at(p, 12, r, Ledger.cut(Fmt.upper(target), 13), AMBER);
                String odds = !m.inArc ? "OUT OF ARC" : Float.isNaN(m.hitChance) ? "IN ARC" : Math.round(m.hitChance * 100f) + "% TO HIT";
                L.at(p, 27, r, Ledger.cut(odds, L.cols - 28), m.inArc ? AMBER : DIM);
                return;
            }
            case HEALTH: {
                float h = Math.max(0f, Math.min(1f, gun.health));
                Color ink = gun.isDestroyed() || gun.isDisabled() ? Yard.P_RED : AMBER;
                L.at(p, 1, r, "HEALTH", DIM);
                bar(p, 12, 10, r, h, ink);
                L.right(p, 27, r, Math.round(h * 100f) + "%", ink);
                return;
            }
            case HAND:
                L.rule(p, r - 1);
                L.at(p, 1, r, Ledger.cut(m != null && m.controller == Controller.PLAYER ? "IN YOUR HAND IT HUNTS NO MISSILES"
                        : "IN HAND IT HUNTS NO MISSILES: YOU AIM IT", L.cols - 2), AMBER);
                return;
            default: {
                if (m == null || data == null) return;
                String c = cover(m, data.weaponFrame.mounts);
                L.at(p, 1, r, wrapped(c, L.cols - 2, lines.get(i) == COVER ? 0 : 1), uncovered(c) ? Yard.P_RED : DIM);
            }
        }
    }

    /** A bar {@code cols} wide from column {@code c} on row {@code r}, {@code share} of it lit. */
    private void bar(YardPaint p, int c, int cols, int r, float share, Color ink) {
        float bx = L.cx(c), bw = cols * Ledger.COL, top = L.rowTop(r) + 5f;
        p.frame(bx, top, bw, 9f, 1f, Ledger.DIM, 0.9f);
        float f = Math.max(0f, Math.min(1f, share));
        if (f > 0f) p.rect(bx + 1.5f, top + 1.5f, (bw - 3f) * f, 6f, ink);
    }

    @Override
    void keys() {
        if (gun == null) return;
        softKey(gun.switchedOn ? "SWITCH OFF" : "SWITCH ON", null);
        Mount m = mount();
        if (m != null && m.controller == Controller.PLAYER) softKey("TAKE GUN", "IT IS IN YOUR HAND");
        else softKey("TAKE GUN", take() == null ? "THIS GUN CAN'T BE TAKEN IN HAND HERE" : null);
    }

    @Override
    void press(int k) {
        if (gun == null) return;
        if (k == 0) {
            gun.switchedOn = !gun.switchedOn;
            say(Fmt.capital(label.toLowerCase(Locale.ROOT)) + " switched " + (gun.switchedOn ? "on" : "off"));
            return;
        }
        HudData.ConsoleAction a = take();
        if (a != null) a.run.run();
    }

    @Override
    String empty() {
        return "NO GUN AT THIS CONSOLE";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        mount = mount();
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, label, mount == null ? null : mode(mount, hold()));
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
