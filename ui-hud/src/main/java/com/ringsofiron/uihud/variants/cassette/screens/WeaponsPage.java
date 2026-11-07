package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The tactical board's Weapons tab (the Stations spec's 10.2): the locked target, whether the gunners fire
 * at will or hold, then every mount by name, its state, how it fires and its chance on the target. Its soft
 * keys hold fire, or lift the hold, for every gun; switch the selected mount off or on; and take fire
 * control of every gun from the console's seat, as V does.
 *
 * <p>The mounts are the player's ship's, as the weapon status has it, read again each frame; at a prize's
 * weapons console the board falls back to Engineering's Systems.
 */
final class WeaponsPage extends ListPage {
    static final int TARGET = 0, FIRE = 1, HEAD = 2, MOUNT = 3;

    /** Each row's kind, and for a mount's row its index in the weapon status. */
    final IntArray kinds = new IntArray(), mounts = new IntArray();
    private HudData data;
    private boolean own;

    WeaponsPage() {
        super("WEAPONS", "TAC", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        own = data != null && ConsolePanels.ship(data) == data.ctx.ship;
        kinds.clear();
        mounts.clear();
        if (!own) return;
        add(TARGET, -1);
        add(FIRE, -1);
        add(HEAD, -1);
        for (int i = 0; i < data.weaponFrame.mounts.size; i++) add(MOUNT, i);
        keepSelection();
    }

    private void add(int kind, int mount) {
        kinds.add(kind);
        mounts.add(mount);
    }

    /** The mount on row {@code i}, read now; null when the row isn't a mount's or the mount has gone. */
    Mount mount(int i) {
        if (data == null || i < 0 || i >= kinds.size || kinds.get(i) != MOUNT) return null;
        int m = mounts.get(i);
        return m < data.weaponFrame.mounts.size ? data.weaponFrame.mounts.get(m) : null;
    }

    /** Whether the captain holds fire. */
    boolean hold() {
        return data != null && (data.weaponFrame.holdFire || data.ctx.holdFire);
    }

    /** The console's own order with {@code action}, or null where the game offers none. */
    private HudData.ConsoleAction action(String action) {
        if (data == null) return null;
        for (int i = 0; i < data.console.actions.size; i++) {
            HudData.ConsoleAction a = data.console.actions.get(i);
            if (action.equals(a.action) && a.run != null) return a;
        }
        return null;
    }

    /** The system of mount {@code m}, the player's ship's; null when none is found. */
    SubsystemComponent system(Mount m) {
        return system(data, m);
    }

    /** The system of mount {@code m} of the player's ship, as {@code data} has it; null when none is found. */
    static SubsystemComponent system(HudData data, Mount m) {
        if (m == null || data == null || data.ctx.ship == null) return null;
        Entity ship = data.ctx.ship;
        ShipSubsystemRegistryComponent reg = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (reg == null) return null;
        for (int i = 0; i < reg.subsystems.size; i++) {
            SubsystemComponent s = reg.subsystems.get(i).getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemType.TURRET_MOUNT && ShipSystems.mount(s, data.weaponFrame.mounts) == m) return s;
        }
        return null;
    }

    // --- The words -----------------------------------------------------------------------------

    /** Its chance on the target: "64%", OUT OF ARC, or nothing for point defense or with no lock. */
    static String odds(Mount m, boolean locked) {
        if (!locked || m.kind == Kind.POINT_DEFENSE) return "";
        if (!m.inArc) return "NO ARC";
        return Float.isNaN(m.hitChance) ? "" : Math.round(m.hitChance * 100f) + "%";
    }

    /** How the guns fire as a whole: HELD, or AT WILL where a gunner has one. */
    static String fire(boolean hold, WeaponStatusProvider.WeaponFrame w) {
        if (hold) return "HELD: THE GUNNERS HOLD FIRE";
        int free = 0;
        for (int i = 0; i < w.mounts.size; i++) if (w.mounts.get(i).weaponsFree && w.mounts.get(i).kind != Kind.POINT_DEFENSE) free++;
        return free == 0 ? "ON THE TRIGGER" : "AT WILL: " + free + (free == 1 ? " GUN" : " GUNS") + " MANNED";
    }

    // --- The list ------------------------------------------------------------------------------

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) == MOUNT;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        if (data == null) return;
        WeaponStatusProvider.WeaponFrame w = data.weaponFrame;
        switch (kinds.get(i)) {
            case TARGET:
                L.at(p, 1, r, "TARGET", DIM);
                if (w.target.isEmpty()) L.at(p, 10, r, "NO LOCK", DIM);
                else L.at(p, 10, r, Ledger.cut(Fmt.upper(w.target), L.cols - 11), AMBER);
                return;
            case FIRE: {
                boolean hold = hold();
                L.at(p, 1, r, "FIRE", DIM);
                L.at(p, 10, r, Ledger.cut(fire(hold, w), L.cols - 11), hold ? Yard.P_RED : AMBER);
                return;
            }
            case HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "MOUNT", DIM);
                L.at(p, 16, r, "STATE", DIM);
                L.at(p, 28, r, "MODE", DIM);
                L.right(p, L.cols - 1, r, "HIT", DIM);
                return;
            default: {
                Mount m = mount(i);
                if (m == null) return;
                SubsystemComponent sys = system(m);
                boolean off = sys != null && !sys.switchedOn;
                Color ink = !m.working() || off ? Yard.P_RED : AMBER;
                put(p, s, 1, r, Ledger.cut(m.name.isEmpty() ? "MOUNT " + (m.index + 1) : Fmt.upper(m.name), 14), ink);
                put(p, s, 16, r, Ledger.cut(off ? "OFF" : GunPage.stateWord(m, sys), 11), ink);
                put(p, s, 28, r, Ledger.cut(GunPage.mode(m, hold()), 8), s ? ink : DIM);
                String o = odds(m, !w.target.isEmpty());
                if (!o.isEmpty()) {
                    if (s) L.revRight(p, L.cols - 1, r, o);
                    else L.right(p, L.cols - 1, r, o, m.inArc ? AMBER : DIM);
                }
            }
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
        if (!own) return;
        boolean hold = hold();
        softKey(hold ? "LIFT HOLD" : "HOLD FIRE", action(HudData.ConsoleAction.HOLD_FIRE) == null ? "FIRE IS HELD FROM THE WEAPONS CONSOLE" : null);
        Mount m = mount(sel);
        SubsystemComponent s = system(m);
        if (m != null) softKey(s != null && !s.switchedOn ? "SWITCH ON" : "SWITCH OFF", s == null ? "THIS MOUNT HAS NO SWITCH" : null);
        softKey("TAKE GUNS", action("VIEW") == null ? "FIRE CONTROL CAN'T BE TAKEN HERE" : null);
    }

    @Override
    void press(int k) {
        if (k == 0) {
            HudData.ConsoleAction a = action(HudData.ConsoleAction.HOLD_FIRE);
            if (a == null) return;
            boolean was = hold();
            a.run.run();
            say(was ? "Hold lifted: the gunners fire at will" : "Holding fire: no gunner fires");
            return;
        }
        Mount m = mount(sel);
        if (k == 1 && m != null) {
            SubsystemComponent s = system(m);
            if (s == null) return;
            s.switchedOn = !s.switchedOn;
            String name = m.name.isEmpty() ? "The mount" : m.name;
            say(name + " switched " + (s.switchedOn ? "on" : "off"));
            return;
        }
        HudData.ConsoleAction a = action("VIEW");
        if (a != null) a.run.run();
    }

    @Override
    String empty() {
        return "NO WEAPONS FITTED";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        int n = data == null ? 0 : data.weaponFrame.mounts.size;
        String right = hold() ? "HOLD FIRE" : n == 1 ? "1 MOUNT" : n + " MOUNTS";
        title(p, "WEAPONS", right.toUpperCase(Locale.ROOT));
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
