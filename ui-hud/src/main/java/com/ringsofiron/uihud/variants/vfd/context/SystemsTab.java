package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.ShipSystems;

import java.util.Locale;

/**
 * The ship's Systems (functional-context 10.2, version 18; mockup 11.11): the plan at large scale, the
 * systems by kind with their health, power and state and the switch on each row (a group switches
 * together; Open lists its members, each with its own switch), the damage control board (every job
 * the crew have, where, and who is on it or why it waits), and the power (each circuit's reactors,
 * output of rating, its store, what no reactor reaches, the conduits cut). The reactor's switch is
 * guarded: the first press sets its key's LED blinking amber, the second switches it.
 */
final class SystemsTab extends ListTab {
    static final int SWITCH = 0, OPEN = 1, FOLD = 2;
    /** How long the reactor's guard waits for the second press. */
    static final float GUARD_FOR = 3f;
    /** The list's fixed columns after the name: health, power, state, and their gaps. */
    static final int FIXED = 1 + 4 + 2 + 4 + 2 + 16;

    final ShipSystems sys = new ShipSystems();
    final SystemsPlan plan = new SystemsPlan();
    private final Glass.Cols cols = new Glass.Cols();
    private final Array<String> lines = new Array<>();
    private final Array<Kit.Sev> sevs = new Array<>();
    private String nextPage = "";
    /** Damage control's jobs, from the alerts. */
    private final Array<String> jobs = new Array<>();
    private final Array<Kit.Sev> jobSev = new Array<>();
    private int waiting;
    private boolean cutConduit;
    private final Array<String> powerLines = new Array<>();
    private final Array<String> cutOff = new Array<>();
    private int cutConduits;
    /** The guarded row armed by a first press, and how long it waits. */
    private int armed = -1;
    private float armLeft;
    private float time;
    /** The layout: the column split, the plan's and the list's panes, damage control and power. */
    private float lw, rx, rw, mid, sh;
    private int nameCells = 21, listCols = 53;

    SystemsTab() {
        super("SYSTEMS", ClassicWindow.CREW, "I");
        keys.define(3, 4);
        keys.set(SWITCH, "SWITCH", false).cap(SWITCH, "ENTER").set(OPEN, "OPEN", false).cap(OPEN, "→").set(FOLD, "FOLD", false).cap(FOLD, "←");
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return d.ctx.ship != null;
    }

    @Override
    void refresh(Boards b, HudData d) {
        read(d);
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        time += delta;
        if (armLeft > 0f && (armLeft -= delta) <= 0f) armed = -1;
        // The plan and its callouts move with the ship, every frame; the guard's LED blinks
        plan.update(d, sys, focusRow(), b.crew.crew);
        keysFor(pages.focus());
    }

    private ShipSystems.Row focusRow() {
        int f = pages.focus();
        return f >= 0 && f < sys.rows.size ? sys.rows.get(f) : null;
    }

    /** Reads the systems again, the focus kept on the same system. */
    void read(HudData d) {
        ShipSystems.Row was = focusRow();
        SubsystemComponent keep = was == null || was.systems.size == 0 ? null : was.systems.first().s;
        int keepKind = was == null ? -1 : was.kind;
        sys.read(d.ctx.ship, d.weaponFrame.mounts);
        lines.clear();
        sevs.clear();
        for (int i = 0; i < sys.rows.size; i++) {
            ShipSystems.Row r = sys.rows.get(i);
            String name = r.kind == ShipSystems.Row.MEMBER ? "  " + memberName(r) : r.kind == ShipSystems.Row.ONE && !r.where.isEmpty()
                    && r.systems.first().s.type == SubsystemComponent.SubsystemType.TURRET_MOUNT ? r.name + " (" + r.where + ")" : r.name;
            String health = Float.isNaN(r.health) ? "-" : Math.round(r.health * 100f) + "%";
            lines.add(cols.clear().left(name, nameCells).gap(1).right(health, 4).gap(2).left(r.power, 4).gap(2).left(r.state, 16).toString());
            sevs.add(r.worst == ShipSystems.State.WRECKED ? Kit.Sev.WARNING
                    : r.worst == ShipSystems.State.ON || r.worst == ShipSystems.State.OFF ? Kit.Sev.OFF : Kit.Sev.CAUTION);
        }
        pages.set(sys.rows.size, shown);
        if (keep != null) {
            int at = keepKind == ShipSystems.Row.GROUP ? groupRow(keep) : sys.rowOf(keep);
            if (at >= 0) pages.focus(at);
        }
        nextPage();
        damageControl(d);
        power(d);
        focusMoved();
    }

    private int groupRow(SubsystemComponent s) {
        for (int i = 0; i < sys.rows.size; i++) {
            ShipSystems.Row r = sys.rows.get(i);
            if (r.kind == ShipSystems.Row.GROUP && r.systems.contains(sys.all.get(indexOf(s)), true)) return i;
        }
        return sys.rowOf(s);
    }

    private int indexOf(SubsystemComponent s) {
        for (int i = 0; i < sys.all.size; i++) if (sys.all.get(i).s == s) return i;
        return 0;
    }

    private static String memberName(ShipSystems.Row r) {
        String base = r.systems.size == 0 ? r.name : r.systems.first().name;
        return r.where.isEmpty() ? base : base + " (" + r.where + ")";
    }

    /** What the next page holds, named under the list: "PG 2: TANKS, CARGO BAYS". */
    private void nextPage() {
        nextPage = "";
        if (pages.page() >= pages.pages()) return;
        int from = pages.page() * pages.size(), to = Math.min(sys.rows.size, from + pages.size());
        StringBuilder sb = new StringBuilder("PG ").append(pages.page() + 1).append(": ");
        String last = "";
        for (int i = from; i < to; i++) {
            ShipSystems.Row r = sys.rows.get(i);
            if (r.kind == ShipSystems.Row.MEMBER) continue;
            String n = r.systems.size == 0 ? r.name : r.systems.first().name;
            n = n.endsWith(" TANK") ? "TANKS" : n.equals("CARGO BAY") ? "CARGO BAYS" : n;
            if (n.equals(last)) continue;
            if (!last.isEmpty()) sb.append(", ");
            sb.append(n);
            last = n;
        }
        nextPage = Glass.cut(sb.toString(), listCols - 3);
    }

    /** The crew's jobs from the alerts: what, where, and who has it or what it waits on. */
    private void damageControl(HudData d) {
        jobs.clear();
        jobSev.clear();
        waiting = 0;
        cutConduit = false;
        Array<Alert> all = d.alerts.all();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.size; i++) {
                Alert a = all.get(i);
                if (a.level == Level.ADVISORY || (a.level == Level.WARNING) != (pass == 0)) continue;
                if (a.category != Category.ENGINEERING && a.category != Category.LIFE_SUPPORT) continue;
                String task = task(a);
                if (task.isEmpty()) continue;
                String who;
                if (a.timerKind == TimerKind.RECOVERY && !Float.isNaN(a.timer) && a.handling <= 0) {
                    who = "BACK IN " + ShipSystems.seconds(a.timer);
                } else if (a.handling > 0) {
                    who = a.handling == 1 ? "1 CREW" : a.handling + " CREW";
                } else if (a.handling == 0) {
                    who = "UNASSIGNED";
                    waiting++;
                } else {
                    who = "-";
                }
                jobs.add(cols.clear().left(task, 30).gap(2).left(a.room.isEmpty() ? "-" : a.room, 10).gap(2).left(who, 24).toString());
                jobSev.add(a.level == Level.WARNING ? Kit.Sev.WARNING : Kit.Sev.CAUTION);
            }
        }
    }

    /** A job's words from its alert: "PUT OUT FIRE", "SEAL BREACH", "REPAIR AUTOCANNON MK1"; "" for none. */
    private String task(Alert a) {
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
        if (!a.id.startsWith("sys:")) return a.code.isEmpty() ? "" : a.code;
        ShipSystems.Sys s = a.tileX >= 0 ? sys.at(a.tileX, a.tileY) : null;
        String name = s == null ? a.code : s.name + (s.where.isEmpty() || s.s.type != SubsystemComponent.SubsystemType.TURRET_MOUNT ? "" : " (" + s.where + ")");
        String t = a.text.toLowerCase(Locale.ROOT);
        if (t.endsWith("shorted")) return "RESET " + name;
        if (t.endsWith("stunned")) return name + " STUNNED";
        if (t.endsWith("no power")) return name + " NO POWER";
        return "REPAIR " + name;
    }

    /** The power board: each circuit, what no reactor reaches, the conduits cut. */
    private void power(HudData d) {
        powerLines.clear();
        cutOff.clear();
        for (int i = 0; i < d.powerFrame.circuits.size; i++) {
            Circuit c = d.powerFrame.circuits.get(i);
            powerLines.add(c.reactors == 1 ? "1 REACTOR" : c.reactors + " REACTORS");
        }
        for (int i = 0; i < d.powerFrame.faults.size; i++) {
            SystemRow r = d.powerFrame.faults.get(i);
            if (r.fault != Fault.NO_POWER) continue;
            ShipSystems.Sys s = r.tileX >= 0 ? sys.at(r.tileX, r.tileY) : null;
            cutOff.add(s == null ? r.system : s.name + (s.where.isEmpty() ? "" : " (" + s.where + ")"));
        }
        cutConduits = 0;
        if (sys.grid != null) {
            for (int y = 0; y < sys.grid.height; y++) {
                for (int x = 0; x < sys.grid.width; x++) {
                    if (sys.grid.getTileConduit(x, y) != null && sys.grid.getTileConduit(x, y).isCut()) cutConduits++;
                }
            }
        }
        cutConduit = cutConduits > 0;
    }

    @Override
    void keysFor(int focus) {
        ShipSystems.Row r = focus >= 0 && focus < sys.rows.size ? sys.rows.get(focus) : null;
        boolean switchable = r != null && r.kind != ShipSystems.Row.RCS && r.systems.size > 0;
        keys.set(SWITCH, armed == focus && armed >= 0 ? "SURE?" : "SWITCH", switchable);
        keys.set(OPEN, "OPEN", r != null && r.kind == ShipSystems.Row.GROUP && !r.open);
        keys.set(FOLD, "FOLD", r != null && (r.kind == ShipSystems.Row.GROUP && r.open || r.kind == ShipSystems.Row.MEMBER));
        boolean on = r != null && switchable && r.allOn;
        boolean blink = armed >= 0 && armed == focus && (time % 0.5f) < 0.25f;
        keys.led(SWITCH, armed >= 0 && armed == focus ? Filament.Led.AMBER : Filament.Led.GREEN, armed >= 0 && armed == focus ? blink : on);
    }

    @Override
    void press(int k, int focus) {
        if (focus < 0 || focus >= sys.rows.size) return;
        ShipSystems.Row r = sys.rows.get(focus);
        HudData d = modes.data();
        if (d == null) return;
        if (k == SWITCH) {
            if (r.guarded && r.allOn && armed != focus) {
                armed = focus;
                armLeft = GUARD_FOR;
                modes.say("ENGINEERING", "Press Switch again to switch the reactor off: it darkens the ship");
                keysFor(focus);
                return;
            }
            armed = -1;
            modes.say("ENGINEERING", sys.toggle(focus));
        } else if (k == OPEN) {
            sys.openOrShut(focus, true);
        } else if (k == FOLD) {
            if (r.kind == ShipSystems.Row.MEMBER) {
                // Fold the group it is in, the focus back on the group's row
                for (int i = focus; i >= 0; i--) {
                    if (sys.rows.get(i).kind == ShipSystems.Row.GROUP) {
                        sys.openOrShut(i, false);
                        pages.focus(i);
                        break;
                    }
                }
            } else {
                sys.openOrShut(focus, false);
            }
        }
        read(d);
    }

    @Override
    boolean key(int keycode) {
        // The LED blinks while the guard waits: the keys are set again each press and each frame
        boolean used = super.key(keycode);
        if (keycode == Keys.PAGE_DOWN || keycode == Keys.PAGE_UP) nextPage();
        return used;
    }

    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        // The plan is as tall as the hull lets it be, so the list takes the width the systems' names want
        lw = Math.round((w - 6f) * 0.54f);
        rx = x + lw + 6f;
        rw = x + w - rx;
        mid = y + Math.max(300f, Math.min(h - 200f, Math.min(440f, h * 0.587f)));
        sh = mid - y - Glass.KEYS;
        listCols = Glass.cells(rw - 24f);
        nameCells = Math.max(12, listCols - 3 - FIXED);
        listX = rx + 12f + 2f * Glass.CW;
        listY = y + 46f;
        listCells = listCols - 2;
        shown = Math.max(1, (int) Math.floor((sh - 46f - 52f) / Glass.ROW + 1e-3f));
        pages.set(sys.rows.size, shown);
        keys.place(rx + 8f, y + sh + Glass.KEY_GAP, rw - 16f, y + sh - 7f * Glass.P - 6f);
        keysFor(pages.focus());
    }

    @Override
    void paint(Filament f, float time) {
        // The plan
        Glass.pane(f, x, y, lw, mid - y, "PLAN");
        plan.paint(f, x, y, lw, mid - y);
        // The systems
        Glass.pane(f, rx, y, rw, sh, "SYSTEMS");
        keys.paint(f);
        float s0 = rx + 12f;
        if (pages.pages() > 1) {
            float pw = Glass.pagerWidth(f, pages.page(), pages.pages());
            Glass.pager(f, rx + rw - 12f - pw, y + 8f, pages.page(), pages.pages());
        }
        float hy = y + 39f;
        Glass.label(f, s0 + 3 * Glass.CW, hy, "SYSTEM");
        Glass.labelRight(f, s0 + (3 + nameCells + 1 + 4) * Glass.CW - Glass.P, hy, "HEALTH");
        Glass.label(f, s0 + (3 + nameCells + 1 + 4 + 2) * Glass.CW, hy, "POWER");
        Glass.label(f, s0 + (3 + nameCells + 1 + 4 + 2 + 4 + 2) * Glass.CW, hy, "STATE");
        int n = Math.max(1, pages.end() - pages.first());
        Glass.cells(f, s0, listY, listCols, n * 1.5f);
        for (int i = pages.first(); i < pages.end(); i++) {
            float ry = rowY(i);
            Glass.pips(f, s0, ry, sevs.get(i));
            Glass.row(f, s0 + 2f * Glass.CW, ry, lines.get(i), listCols - 2, i == pages.focus(), Drive.NORM);
        }
        if (!nextPage.isEmpty()) Glass.dim(f, s0 + 3 * Glass.CW, listY + shown * Glass.ROW + 12f, nextPage);
        paintDamageControl(f);
        paintPower(f);
    }

    private void paintDamageControl(Filament f) {
        float dy0 = mid + 6f, dh = y + h - dy0, x0 = x + 10f, x1 = x + lw - 12f;
        Glass.pane(f, x, dy0, lw, dh, "DAMAGE CONTROL");
        String count = jobs.size == 0 ? "NO JOBS" : (jobs.size == 1 ? "1 JOB" : jobs.size + " JOBS") + (waiting > 0 ? " · " + waiting + " WAIT" : "");
        Glass.right(f, x1, dy0 + 9f, count, Drive.DIM);
        float hy = dy0 + 39f;
        Glass.label(f, x0 + 3 * Glass.CW, hy, "TASK");
        Glass.label(f, x0 + 35 * Glass.CW, hy, "WHERE");
        Glass.label(f, x0 + 47 * Glass.CW, hy, "WHO");
        int fit = Math.max(1, (int) Math.floor((dh - 46f - 40f) / Glass.ROW));
        int n = Math.min(fit, jobs.size);
        Glass.cells(f, x0, dy0 + 46f, Glass.cells(x1 - x0), Math.max(1, n) * 1.5f);
        if (jobs.size == 0) Glass.text(f, x0 + 3 * Glass.CW, dy0 + 46f, "NOTHING TO DO: THE SHIP IS SOUND", Drive.DIM);
        for (int i = 0; i < n; i++) {
            float ry = dy0 + 46f + i * Glass.ROW;
            Glass.pips(f, x0, ry, jobSev.get(i));
            Glass.text(f, x0 + 3 * Glass.CW, ry, Glass.cut(jobs.get(i), Glass.cells(x1 - x0) - 3));
        }
        if (jobs.size > n) Glass.dim(f, x0 + 3 * Glass.CW, dy0 + 46f + n * Glass.ROW, "AND " + (jobs.size - n) + " MORE");
        float ny = dy0 + dh - 22f;
        if (cutConduit) note(f, x0 + 3 * Glass.CW, ny, x1, "CUT CONDUIT:", "ONLY A YARD REPAIRS ONE, SO IT SHOWS UNDER POWER");
        else if (waiting > 0) note(f, x0 + 3 * Glass.CW, ny, x1, "UNASSIGNED:", "NO ONE IS ON IT YET; THE CREW TAKE THE WORST FIRST");
    }

    private static void note(Filament f, float x, float y, float x1, String term, String why) {
        float tw = Glass.text(f, x, y, term);
        Glass.dim(f, x + tw + Glass.CW, y, Glass.cut(why, Glass.cells(x1 - x - tw - Glass.CW)));
    }

    private void paintPower(Filament f) {
        HudData d = modes.data();
        float dy0 = mid + 6f, dh = y + h - dy0, p0 = rx + 12f, pv = p0 + 104f, r1 = rx + rw - 12f;
        Glass.pane(f, rx, dy0, rw, dh, "POWER");
        if (d == null) return;
        float py = dy0 + 36f;
        for (int i = 0; i < d.powerFrame.circuits.size && py < dy0 + dh - 60f; i++) {
            Circuit c = d.powerFrame.circuits.get(i);
            Glass.label(f, p0, py + 14f, "CIRCUIT " + (i + 1));
            float px = pv + Glass.text(f, pv, py + 4f, powerLines.get(i)) + 14f;
            boolean mega = c.ratedWatts >= 1e6;
            double k = mega ? 1e6 : 1e3;
            px = Glass.figure(f, px, py, 14f, String.format(Locale.ROOT, "%.1f", c.outputWatts / k), null, 3, Drive.NORM);
            px += f.legend(px + 2f, py + 14f, "OF", f.o().size(13f).dim().noGhost()) + 7f;
            Glass.figure(f, px, py, 14f, String.format(Locale.ROOT, "%.1f", c.ratedWatts / k), mega ? "MW" : "KW", 3, Drive.NORM);
            py += 30f;
            if (c.capacity > 0) {
                Glass.label(f, p0, py + 12f, "STORE");
                float bw = Math.min(200f, r1 - pv - 60f);
                f.bar(pv, py + 4f, bw, 7f, 20, c.charge(), f.o().norm().gap(1.2f));
                Glass.figure(f, pv + bw + 12f, py, 12f, Integer.toString(Math.round(c.charge() * 100f)), "%", 3, Drive.NORM);
                py += 32f;
            }
        }
        if (d.powerFrame.circuits.size == 0) {
            Glass.text(f, pv, py, "NO REACTOR IS WORKING", Drive.NORM);
            py += 32f;
        }
        if (cutOff.size > 0 && py < dy0 + dh - 40f) {
            Glass.label(f, p0, py + 10f, "CUT OFF");
            Kit.pip(f, pv + 3f, py + 4.5f, com.ringsofiron.uihud.variants.vfd.Phosphor.AMBER, true);
            Glass.text(f, pv + 2f * Glass.CW + 4f, py, Glass.cut(cutOff.first() + (cutOff.size > 1 ? " AND " + (cutOff.size - 1) + " MORE" : ""),
                    Glass.cells(r1 - pv) - 3));
            Glass.dim(f, pv + 2f * Glass.CW + 4f, py + 15f, "NO REACTOR REACHES IT");
            py += 42f;
        }
        if (py < dy0 + dh - 40f) {
            Glass.label(f, p0, py + 10f, "CONDUITS");
            if (cutConduits == 0) {
                Glass.text(f, pv, py, "NONE CUT");
            } else {
                Glass.text(f, pv, py, cutConduits == 1 ? "1 CUT" : cutConduits + " CUT");
                Glass.dim(f, pv, py + 15f, Glass.cut("THE CREW CAN ONLY RESET A SHORT;", Glass.cells(r1 - pv)));
                Glass.dim(f, pv, py + 30f, Glass.cut("A YARD REPAIRS A CUT", Glass.cells(r1 - pv)));
            }
        }
    }
}
