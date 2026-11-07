package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent.CargoStack;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.ShipSystems;

import java.util.Locale;

/**
 * The book's Ship (functional-task 7, BOOK): the ship's systems by kind, each with its health, its
 * power and its state (a kind the ship has more than one of is a group, and Open lists its members);
 * the damage and the work on it, each job with where it is and who is on it or what it waits on; the
 * power, each circuit's reactors, output of rating and store, what no reactor reaches and the conduits
 * cut; the shields; and the hold's manifest. Beside the list, what there is to say about the row in
 * focus. Switches stay at the consoles (open question 2): a system's detail says where to switch it.
 * A lamp or the schematic opens the book here.
 */
final class ShipTab extends SectionTab {
    static final int SYS = ITEM, JOB = ITEM + 1;
    static final int OPEN = 0;

    final ShipSystems sys = new ShipSystems();
    /** The alert behind each job row, by the row's ref. */
    private final Array<Alert> jobs = new Array<>();
    private final Glass.Cols c2 = new Glass.Cols();

    ShipTab() {
        super("SHIP", ClassicWindow.CREW, "");
        keys.define(1, 1);
        keys.set(OPEN, "OPEN", false);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return d.ctx.ship != null;
    }

    @Override
    void build(Boards b, HudData d) {
        sys.read(d.ctx.ship, d.weaponFrame.mounts);
        int fixed = 1 + 4 + 2 + 5 + 2 + 14;
        int name = Math.max(8, cells - 1 - fixed);
        head("SYSTEMS");
        for (int i = 0; i < sys.rows.size; i++) {
            ShipSystems.Row r = sys.rows.get(i);
            String n = r.kind == ShipSystems.Row.MEMBER ? "  " + member(r) : r.kind == ShipSystems.Row.ONE && !r.where.isEmpty()
                    && r.systems.size > 0 && r.systems.first().s.type == SubsystemComponent.SubsystemType.TURRET_MOUNT ? r.name + " (" + r.where + ")"
                    : r.kind == ShipSystems.Row.GROUP ? (r.open ? "- " : "+ ") + r.name : r.name;
            String health = Float.isNaN(r.health) ? "-" : Math.round(r.health * 100f) + "%";
            add(SYS, i, cols.clear().left(n, name).gap(1).right(health, 4).gap(2).left(r.power, 5).gap(2).left(r.state, 14).toString());
        }
        if (sys.rows.size == 0) note("NO SYSTEMS ABOARD");
        jobs(d, name + fixed);
        power(d);
        shields(d.shieldStatus);
        hold(d);
    }

    private static String member(ShipSystems.Row r) {
        String base = r.systems.size == 0 ? r.name : r.systems.first().name;
        return r.where.isEmpty() ? base : base + " (" + r.where + ")";
    }

    /** The crew's jobs from the alerts, the warnings first: what, where, and who has it or what it waits on. */
    private void jobs(HudData d, int w) {
        jobs.clear();
        head("DAMAGE AND WORK");
        Array<Alert> all = d.alerts.all();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.size; i++) {
                Alert a = all.get(i);
                if (a.level == Level.ADVISORY || (a.level == Level.WARNING) != (pass == 0)) continue;
                if (a.category != Category.ENGINEERING && a.category != Category.LIFE_SUPPORT) continue;
                String task = task(a);
                if (task.isEmpty()) continue;
                String who;
                if (a.timerKind == TimerKind.RECOVERY && !Float.isNaN(a.timer) && a.handling <= 0) who = "BACK IN " + ShipSystems.seconds(a.timer);
                else if (a.handling > 0) who = a.handling == 1 ? "1 CREW ON IT" : a.handling + " CREW ON IT";
                else if (a.handling == 0) who = "NOBODY ON IT";
                else who = "-";
                add(JOB, jobs.size, c2.clear().left(task, Math.max(8, w - 28)).gap(2).left(a.room.isEmpty() ? "-" : a.room.toUpperCase(Locale.ROOT), 10)
                        .gap(2).left(who, 14).toString());
                jobs.add(a);
            }
        }
        if (jobs.size == 0) note("NOTHING NEEDS WORK");
    }

    /** A job's words from its alert: "PUT OUT FIRE", "SEAL BREACH", "REPAIR AUTOCANNON MK1"; "" for none. */
    String task(Alert a) {
        switch (a.code) {
            case "FIRE":
                return a.count > 1 ? "PUT OUT FIRE, " + a.count + " TILES" : "PUT OUT FIRE";
            case "BREACH":
                return a.count > 1 ? "SEAL BREACH, " + a.count + " TILES" : "SEAL BREACH";
            case "VACUUM":
                return "RESTORE AIR (VACUUM)";
            case "PRESSURE":
                return "RESTORE AIR";
            case "SMOKE":
                return "";
            case "HULL":
                return "PATCH HULL, " + a.text.replaceAll("[^0-9%]", "");
            default:
        }
        if (a.id == null || !a.id.startsWith("sys:")) return a.code == null || a.code.isEmpty() ? "" : a.code;
        ShipSystems.Sys s = a.tileX >= 0 ? sys.at(a.tileX, a.tileY) : null;
        String name = s == null ? a.code
                : s.name + (s.where.isEmpty() || s.s.type != SubsystemComponent.SubsystemType.TURRET_MOUNT ? "" : " (" + s.where + ")");
        String t = a.text.toLowerCase(Locale.ROOT);
        if (t.endsWith("shorted")) return "RESET " + name;
        if (t.endsWith("stunned")) return name + " STUNNED";
        if (t.endsWith("no power")) return name + " NO POWER";
        return "REPAIR " + name;
    }

    /** Each circuit: its reactors, output of rating and store; what no reactor reaches; the conduits cut. */
    private void power(HudData d) {
        head("POWER");
        var p = d.powerFrame;
        if (p.circuits.size == 0) {
            note("NO POWER CIRCUITS");
            return;
        }
        for (int i = 0; i < p.circuits.size; i++) {
            Circuit c = p.circuits.get(i);
            String store = c.capacity > 0 ? String.format(Locale.ROOT, ", STORE %d%%", Math.round(c.charge() * 100f)) : "";
            note(String.format(Locale.ROOT, "CIRCUIT %d: %s, %s OF %s KW%s, %d SYSTEMS", i + 1, c.reactors == 1 ? "1 REACTOR" : c.reactors + " REACTORS",
                    kw(c.outputWatts), kw(c.ratedWatts), store, c.systems));
        }
        int unpowered = 0;
        for (int i = 0; i < p.faults.size; i++) {
            SystemRow r = p.faults.get(i);
            if (r.fault == Fault.NO_POWER) unpowered++;
        }
        if (unpowered > 0) note(unpowered == 1 ? "1 SYSTEM NO REACTOR REACHES" : unpowered + " SYSTEMS NO REACTOR REACHES");
        int cut = 0;
        if (sys.grid != null) {
            for (int y = 0; y < sys.grid.height; y++) {
                for (int x = 0; x < sys.grid.width; x++) {
                    if (sys.grid.getTileConduit(x, y) != null && sys.grid.getTileConduit(x, y).isCut()) cut++;
                }
            }
        }
        if (cut > 0) note(cut == 1 ? "1 CONDUIT CUT" : cut + " CONDUITS CUT");
    }

    static String kw(double watts) {
        double k = watts / 1000.0;
        return k < 10 && Math.abs(k - Math.rint(k)) > 0.05 ? String.format(Locale.ROOT, "%.1f", k) : Glass.thousands(k);
    }

    private void shields(ShieldStatus s) {
        if (!s.present || s.emitters == 0) return;
        head("SHIELDS");
        String state = s.raised ? "RAISED" : s.loweredForDock ? "LOWERED TO DOCK" : "LOWERED";
        note(String.format(Locale.ROOT, "%s, %d OF %d EMITTERS HOLDING%s, CLOUD %d%%", state, s.emittersWorking, s.emitters,
                s.emittersTripped > 0 ? ", " + s.emittersTripped + " TRIPPED" : "", Math.round(s.share() * 100f)));
        if (s.powerStunned) note("THE EMITTERS' POWER IS STUNNED");
    }

    /** The hold's manifest: what it carries and how much of it is used. */
    private void hold(HudData d) {
        CargoHoldComponent hold = d.ctx.ship == null ? null : d.ctx.ship.getComponent(CargoHoldComponent.class);
        if (hold == null || hold.bayTileCount() == 0) return;
        head("HOLD: " + Glass.units(hold.usedVolume()) + " OF " + Glass.units(hold.capacity()) + " M3");
        int n = 0;
        for (CargoStack s : hold.stacks) {
            if (s == null || s.commodity == null || s.units <= 1e-6) continue;
            note(c2.clear().left(MarketTab.name(s.commodity), Math.max(8, cells - 12)).right(Glass.units(hold.amount(s.commodity)), 8).toString());
            n++;
        }
        if (n == 0) note("EMPTY");
    }

    ShipSystems.Row focusRow() {
        int f = pages.focus();
        return kindAt(f) == SYS ? sys.rows.get(ref.get(f)) : null;
    }

    @Override
    void keysFor(int focus) {
        ShipSystems.Row r = focusRow();
        boolean group = r != null && r.kind == ShipSystems.Row.GROUP;
        keys.set(OPEN, group && r.open ? "FOLD" : "OPEN", group);
    }

    @Override
    void detailFor(int focus) {
        int k = kindAt(focus);
        if (k == SYS) {
            ShipSystems.Row r = sys.rows.get(ref.get(focus));
            tell(r.kind == ShipSystems.Row.MEMBER ? member(r) : r.name + (r.where.isEmpty() ? "" : ", " + r.where));
            if (!r.state.isEmpty()) tell(r.state);
            if (!Float.isNaN(r.health)) tell("Health " + Math.round(r.health * 100f) + "%");
            if (!r.power.isEmpty() && !r.power.equals("-")) tell("Power: circuit " + r.power);
            if (r.kind == ShipSystems.Row.GROUP) tell(r.systems.size + " aboard; Open lists each");
            if (r.kind == ShipSystems.Row.RCS) tell(sys.rcsFiring + " of " + sys.rcsBlocks + " blocks fire");
            else tell("Its switch is at its console: walk there and press E");
        } else if (k == JOB) {
            Alert a = jobs.get(ref.get(focus));
            tell(a.text);
            if (!a.room.isEmpty()) tell("In " + a.room);
            if (a.handling == 0) tell("Nobody is on it yet: the crew take jobs as they come free");
        }
    }

    @Override
    void press(int k, int focus) {
        ShipSystems.Row r = focusRow();
        if (k != OPEN || r == null || r.kind != ShipSystems.Row.GROUP) return;
        sys.openOrShut(ref.get(focus), !r.open);
        windows.refresh();
    }
}
