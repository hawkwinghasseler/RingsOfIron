package com.ringsofiron.uihud.variants.vfd.stations;

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
 * The slate's Contracts (functional-stations 10.4): every contract held, soonest deadline first, each
 * with its time left, the one the HUD tracks and the one whose next place is the nav target marked; the
 * one in focus in full under the list, its terms, its giver, its next place, its progress and its
 * deadline. Set as nav makes its next place the nav target; Give up abandons it, on a second press.
 * It stands for the mission window, so J closes it, and opens it away from a station's board.
 */
final class ContractsPage extends ListPage {
    static final int NAV = 0, GIVE_UP = 1;
    static final float CONFIRM_FOR = 3f;
    /** The time left's cells at a row's end; the detail's fields, their pitch and where their values start. */
    static final int LEFT = 8;
    static final int FIELDS = 6;
    static final float STEP = 20f, VALUE = 78f, LIST = 24f;

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
    private float detailY;
    private final String[] wrapped = new String[2];

    ContractsPage() {
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
        HudData d = panels.data();
        if (d == null || focus < 0 || focus >= rows.size) return;
        Row r = rows.get(focus);
        if (k == NAV) {
            if (d.objectives != null) panels.say(d.objectives.navTo(r.id));
        } else if (k == GIVE_UP) {
            if (d.missions == null) return;
            if (arming != r.id) {
                arming = r.id;
                armLeft = CONFIRM_FOR;
                panels.say("Press Give up again to abandon " + r.title.toLowerCase(Locale.ROOT));
                keysFor(focus);
                return;
            }
            arming = -1;
            panels.say(d.missions.abandon(r.id));
        }
        panels.refresh();
    }

    // ------------------------------------------------------------------ layout and painting

    @Override
    void place(Page.Place p) {
        super.place(p);
        listX = p.left;
        listY = p.top + LIST;
        listCells = Math.max(16, Glass.cells(p.x1 - p.left));
        detailY = p.bottom - FIELDS * STEP;
        shown = Math.max(1, (int) Math.floor((detailY - 12f - listY) / Glass.ROW));
        pages.set(rows.size, shown);
    }

    @Override
    void paint(Filament f, float time) {
        float x0 = at.left, x1 = at.x1;
        String count = rows.size == 0 ? "NO CONTRACTS HELD" : rows.size == 1 ? "1 CONTRACT HELD" : rows.size + " CONTRACTS HELD";
        if (!operation.isEmpty()) {
            count += String.format(Locale.ROOT, ", OPERATION %s %d OF %d", operation.toUpperCase(Locale.ROOT),
                    Math.max(1, operationStep), Math.max(1, operationSteps));
        }
        float pw = pages.pages() > 1 ? Slate.pagerWidth(pages.page(), pages.pages()) + Glass.CW : 0f;
        Slate.text(f, x0, at.top, Glass.cut(count, Glass.cells(x1 - x0 - pw)));
        if (pages.pages() > 1) Slate.pager(f, x1 - pw + Glass.CW, at.top, pages.page(), pages.pages());
        if (rows.size == 0) {
            int n = Glass.wrap("TAKE ONE AT A STATION'S BOARD: J WHILE DOCKED", listCells, wrapped, wrapped.length);
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
        if (fi >= 0 && fi < rows.size) detail(f, rows.get(fi), x0, detailY);
    }

    /** The contract in focus, in full. */
    private void detail(Filament f, Row r, float x0, float dy) {
        Slate.rule(f, x0, dy - 6f, at.x1 - x0);
        dy += 2f;
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
        if (r.tracked && dy + Glass.RH <= at.bottom) Slate.dim(f, x0 + VALUE, dy, Glass.cut("TRACKED ON THE HUD", Glass.cells(at.x1 - x0 - VALUE)));
    }

    private float field(Filament f, float x0, float fy, String label, String value, boolean dim) {
        Slate.label(f, x0, fy + 9f, label);
        Slate.text(f, x0 + VALUE, fy, Glass.cut(value, Glass.cells(at.x1 - x0 - VALUE)), dim);
        return fy + STEP;
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
