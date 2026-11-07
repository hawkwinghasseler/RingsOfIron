package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The book's Contracts (functional-task 7, BOOK): the contracts held, the tracked one and the nav
 * target marked, each with its time left; beside the list, the one in focus in full: its terms, its
 * giver and station, where to steer next, its progress and its deadline as a date. Set as nav makes its
 * next stop the nav target, and Give up, walked to and pressed twice, abandons it. Away from a board J
 * opens it from anywhere. The game keeps no record of contracts finished, so the list is those held.
 */
final class HeldTab extends ListTab {
    static final int NAV = 0, GIVE_UP = 1;
    static final float CONFIRM_FOR = 3f;
    /** The time left's cells at a row's end; the detail's fields, their pitch and where their values start. */
    static final int LEFT = 8;
    static final float VALUE = 96f, LIST = 24f;

    /** A contract held, as read: the objective frame is the game's to refill each frame. */
    static final class Row {
        long id;
        String title = "", giver = "", station = "", next = "", terms = "";
        State state = State.ACTIVE;
        double timeLeft = Double.NaN, progress, of, reward;
        float standing;
        boolean late, nav, tracked;
    }

    private final Array<Row> rows = new Array<>(), pool = new Array<>();
    private final Glass.Cols cols = new Glass.Cols();
    private String operation = "";
    private int operationStep, operationSteps;
    private double now;
    /** The contract in focus, kept across reads; the one a first press of Give up waits on, and how long it still waits. */
    private long focusId = -1, arming = -1;
    private float armLeft;
    private boolean canNav, canGiveUp;
    private final String[] wrapped = new String[4];

    HeldTab() {
        super("CONTRACTS", ClassicWindow.MISSIONS, "J");
        // Set as nav done, Enter stays on it: giving up is walked to, never landed on
        keys.define(2, 2).guard(GIVE_UP);
    }

    @Override
    void shown() {
        arming = -1;
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        if (armLeft > 0f && (armLeft -= delta) <= 0f) {
            arming = -1;
            keysFor(pages.focus());
        }
    }

    @Override
    void refresh(Boards b, HudData d) {
        now = d.ctx.simSeconds;
        canNav = d.objectives != null;
        canGiveUp = d.missions != null;
        pool.addAll(rows);
        rows.clear();
        ObjectiveFrame of = d.objectiveFrame;
        Objective tracked = of.first();
        for (int i = 0; i < of.objectives.size; i++) {
            Objective o = of.objectives.get(i);
            Row r = pool.size > 0 ? pool.pop() : new Row();
            r.id = o.id;
            r.title = title(o);
            r.giver = o.giver;
            r.station = o.station;
            r.next = o.next;
            r.state = o.state;
            r.timeLeft = o.timeLeft;
            r.late = o.late;
            r.progress = o.progress;
            r.of = o.of;
            r.reward = o.reward;
            r.standing = o.standing;
            r.nav = o.nextIsNav;
            r.tracked = o == tracked;
            r.terms = "";
            // The board's line has the giver's name and the terms in full
            for (int k = 0; k < b.missions.held.size; k++) {
                Job j = b.missions.held.get(k);
                if (j.id != o.id) continue;
                if (!j.giver.isEmpty()) r.giver = j.giver;
                r.terms = j.terms;
                break;
            }
            rows.add(r);
        }
        operation = of.operation;
        operationStep = of.operationStep;
        operationSteps = of.operationSteps;
        if (arming >= 0 && index(arming) < 0) arming = -1;
        // The focus stays on its contract as the order changes
        long want = focusId;
        rows(rows.size);
        int at = index(want);
        if (at >= 0 && at != pages.focus()) {
            pages.focus(at);
            focusMoved();
        }
    }

    private int index(long id) {
        for (int i = 0; i < rows.size; i++) if (rows.get(i).id == id) return i;
        return -1;
    }

    /** The title as the HUD's tracker has it: its kind first, unless the title already starts with it ("Bounty: Raider"). */
    static String title(Objective o) {
        boolean named = o.title.regionMatches(true, 0, o.kind, 0, o.kind.length());
        String kind = o.kind.isEmpty() || named ? "" : o.kind + ": ";
        return (kind + o.title).toUpperCase(Locale.ROOT);
    }

    @Override
    void focusMoved() {
        super.focusMoved();
        int f = pages.focus();
        focusId = f >= 0 && f < rows.size ? rows.get(f).id : -1;
    }

    @Override
    void keysFor(int focus) {
        Row r = focus >= 0 && focus < rows.size ? rows.get(focus) : null;
        keys.set(NAV, "SET AS NAV", r != null && canNav && !r.next.isEmpty() && !r.nav);
        keys.set(GIVE_UP, r != null && arming == r.id ? "SURE?" : "GIVE UP", r != null && canGiveUp);
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        if (d == null || focus < 0 || focus >= rows.size) return;
        Row r = rows.get(focus);
        if (k == NAV) {
            if (d.objectives != null) windows.say(d.objectives.navTo(r.id));
        } else if (k == GIVE_UP) {
            if (d.missions == null) return;
            if (arming != r.id) {
                arming = r.id;
                armLeft = CONFIRM_FOR;
                windows.say("Press Give up again to abandon " + r.title.toLowerCase(Locale.ROOT));
                keysFor(focus);
                return;
            }
            arming = -1;
            windows.say(d.missions.abandon(r.id));
        }
        windows.refresh();
    }

    // ------------------------------------------------------------------ layout and painting

    @Override
    void place(Tab.Place p) {
        super.place(p);
        listX = p.lx;
        listY = p.ly + LIST;
        listCells = Math.max(16, Glass.cells(p.lw()));
        shown = fit(p.lb);
        pages.set(rows.size, shown);
    }

    @Override
    void paint(Filament f, float time) {
        float x0 = at.lx, x1 = at.lr;
        String count = rows.size == 0 ? "NO CONTRACTS HELD" : rows.size == 1 ? "1 CONTRACT HELD" : rows.size + " CONTRACTS HELD";
        if (!operation.isEmpty()) {
            count += String.format(Locale.ROOT, ", OPERATION %s %d OF %d", operation.toUpperCase(Locale.ROOT),
                    Math.max(1, operationStep), Math.max(1, operationSteps));
        }
        float pw = pages.pages() > 1 ? Slate.pagerWidth(pages.page(), pages.pages()) + Glass.CW : 0f;
        Slate.text(f, x0, at.ly, Glass.cut(count, Glass.cells(x1 - x0 - pw)));
        if (pages.pages() > 1) Slate.pager(f, x1 - pw + Glass.CW, at.ly, pages.page(), pages.pages());
        if (rows.size == 0) {
            int n = Glass.wrap("TAKE ONE ON A STATION TERMINAL'S CONTRACTS: J WHILE DOCKED", listCells, wrapped, wrapped.length);
            for (int i = 0; i < n; i++) Slate.dim(f, x0, listY + i * Glass.RH * 1.25f, wrapped[i]);
            return;
        }
        int w = listCells - 1;
        for (int i = pages.first(); i < pages.end(); i++) {
            Row r = rows.get(i);
            cols.clear().left(marks(r) + r.title, w - LEFT - 1).gap(1).right(left(r), LEFT);
            Slate.row(f, listX, rowY(i), cols.toString(), listCells, i == pages.focus(), r.state == State.DONE || r.state == State.FAILED);
        }
        int fi = pages.focus();
        if (fi >= 0 && fi < rows.size) detail(f, rows.get(fi), at.dx, at.dy);
    }

    /** The contract in focus, in full. */
    private void detail(Filament f, Row r, float x0, float dy) {
        int n = Glass.wrap(r.title, Glass.cells(at.dr - x0), wrapped, 2);
        for (int i = 0; i < n; i++) {
            Slate.text(f, x0, dy, wrapped[i]);
            dy += SectionTab.LINE;
        }
        dy += 10f;
        String terms = !r.terms.isEmpty() ? r.terms.toUpperCase(Locale.ROOT)
                : String.format(Locale.ROOT, "%s CR, %+d STANDING", Glass.thousands(r.reward), Math.round(r.standing));
        dy = field(f, x0, dy, "TERMS", terms, false);
        String from = r.giver.toUpperCase(Locale.ROOT) + (r.station.isEmpty() ? "" : ", AT " + r.station.toUpperCase(Locale.ROOT));
        dy = field(f, x0, dy, "FROM", from, false);
        String next = r.state == State.RETURN ? "BACK TO " + (r.station.isEmpty() ? "ITS STATION" : r.station.toUpperCase(Locale.ROOT)) + " TO BE PAID"
                : r.next.isEmpty() ? "NOWHERE TO STEER FOR" : r.next.toUpperCase(Locale.ROOT);
        if (r.nav && !r.next.isEmpty()) next += ": NAV TARGET";
        dy = field(f, x0, dy, "NEXT", next, r.next.isEmpty() && r.state != State.RETURN);
        if (r.of > 0) dy = field(f, x0, dy, "PROGRESS", Glass.units(r.progress) + " OF " + Glass.units(r.of), false);
        String deadline = Double.isNaN(r.timeLeft) ? "NONE"
                : r.late ? "LATE BY " + Fmt.span(-r.timeLeft) : Fmt.when(now + r.timeLeft) + ", IN " + Fmt.span(r.timeLeft);
        dy = field(f, x0, dy, "DEADLINE", deadline, Double.isNaN(r.timeLeft));
        if (r.tracked && dy + Glass.RH <= at.db) Slate.dim(f, x0 + VALUE, dy, Glass.cut("TRACKED ON THE HUD", Glass.cells(at.dr - x0 - VALUE)));
    }

    /** A label and its value wrapped in the room after it; returns the y after them. */
    private float field(Filament f, float x0, float fy, String label, String value, boolean dim) {
        if (fy + Glass.RH > at.db) return fy;
        Slate.label(f, x0, fy + 9f, label);
        int n = Glass.wrap(value, Math.max(4, Glass.cells(at.dr - x0 - VALUE)), wrapped, wrapped.length);
        for (int i = 0; i < n && fy + Glass.RH <= at.db; i++) {
            Slate.text(f, x0 + VALUE, fy, wrapped[i], dim);
            fy += SectionTab.LINE;
        }
        return fy + 6f;
    }

    /** The tracked one (a diamond) and the nav target's (a chevron), marked at the title's start. */
    private static String marks(Row r) {
        if (r.tracked && r.nav) return "\u2666\u00bb";
        if (r.tracked) return "\u2666 ";
        return r.nav ? "\u00bb " : "";
    }

    private static String left(Row r) {
        if (r.state == State.DONE) return "DONE";
        if (r.state == State.FAILED) return "FAILED";
        if (Double.isNaN(r.timeLeft)) return "-";
        return r.late ? "LATE" : Fmt.span(r.timeLeft);
    }
}
