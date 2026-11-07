package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

import java.util.Locale;

/**
 * Boarding (functional-context 10.4; mockup 11.13): today's boarding window with the same orders, in
 * four panes. The plan of the other ship, with our party where they stand; the party, how it stands
 * and its orders (board, storm, recall); the parts to strip, what each sells for and how its strip
 * stands, the focused one's key marking it or leaving it; and the cargo aboard to take, with the
 * prisoners alongside to bring aboard, against the berths free for them.
 *
 * <p>↑ and ↓ move the focus down the parts and on into the cargo. The keys of all three strips are
 * walked as one, with ← and →, and Enter presses the one walked to; moving the focus walks to the
 * focused row's own key.
 */
final class BoardingTab extends ModeTab {
    static final int ROW_IDS = Modes.TAB_IDS, KEY_IDS = Modes.TAB_IDS + 80;
    /** The strips, in the walk's order, and their keys. */
    static final int PARTY = 0, SALVAGE = 1, CARGO = 2;
    static final int BOARD = 0, STORM = 1, RECALL = 2, MARK = 0, TAKE = 0, BRING = 1;
    static final float GAP = 8f;

    private final BoardingPlan plan = new BoardingPlan();
    private final SoftKeys party = new SoftKeys(), salvage = new SoftKeys(), cargo = new SoftKeys();
    private final SoftKeys[] strips = {party, salvage, cargo};
    private final Glass.Cols cols = new Glass.Cols();
    private final RoomNames rooms = new RoomNames();
    private ShipGridComponent roomsOf;
    private BoardingBoard board;
    private CrewProvider.CrewSheet crew;
    /** The focus, down the parts then on into the cargo; the first part on the page showing; parts a page. */
    private int focus = -1, partFirst, partRows = 1, lotRows = 1;
    /** The strip the walk is in. */
    private int walkStrip = SALVAGE;
    private final Array<Member> aboard = new Array<>();
    private final String[] wrapped = new String[3];

    /** The layout: the columns, the plan's height, the lower panes' top and height. */
    private float lw, rx, rw, ph, sy, sh;

    BoardingTab() {
        super("BOARDING", ClassicWindow.BOARDING, "K");
        party.define(3, 3);
        salvage.define(1, 5);
        cargo.define(2, 2);
    }

    @Override
    void refresh(Boards b, HudData d) {
        board = b.boarding;
        crew = b.crew;
        aboard.clear();
        for (int i = 0; i < crew.crew.size; i++) {
            Member m = crew.crew.get(i);
            if (board.craft != null && m.craft == board.craft) aboard.add(m);
        }
        int n = rows();
        if (n == 0) focus = -1;
        else focus = Math.max(0, Math.min(n - 1, focus));
        show();
        keysFor();
        settle(false);
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        if (board != null) plan.update(d, board);
    }

    private int rows() {
        return board == null ? 0 : (board.salvage ? board.parts.size : 0) + board.cargo.size;
    }

    private Part focusedPart() {
        return board != null && board.salvage && focus >= 0 && focus < board.parts.size ? board.parts.get(focus) : null;
    }

    private Cargo focusedLot() {
        int parts = board == null || !board.salvage ? 0 : board.parts.size;
        int i = focus - parts;
        return board != null && i >= 0 && i < board.cargo.size ? board.cargo.get(i) : null;
    }

    /** Turns the parts' page to the focus, when the focus is on a part. */
    private void show() {
        if (focusedPart() != null) partFirst = focus / partRows * partRows;
        int parts = board == null || !board.salvage ? 0 : board.parts.size;
        if (partFirst >= parts) partFirst = Math.max(0, (parts - 1) / partRows * partRows);
    }

    /** Each strip's keys for how things stand and the row in focus. */
    private void keysFor() {
        BoardingBoard b = board;
        boolean open = b != null && b.isOpen();
        boolean noParty = !open || b.salvage || b.boarding;
        party.set(BOARD, open && b.byEva ? "BOARD ON EVA" : "BOARD", !noParty && b.boardRefusal.isEmpty());
        party.set(STORM, "STORM", !noParty && b.stormRefusal.isEmpty());
        party.set(RECALL, "RECALL", open && b.boarding);
        Part p = focusedPart();
        salvage.set(MARK, p != null && p.marked ? "LEAVE IT" : "STRIP", p != null && p.toggleable);
        Cargo c = focusedLot();
        cargo.set(TAKE, "TAKE ALL", c != null && c.onOrder <= 0.05);
        cargo.set(BRING, "BRING ABOARD", crew != null && crew.prisonersAlongside);
    }

    /**
     * Keeps the walk on a key that can be taken: with {@code toRow}, on the focused row's own key
     * (a part's or a lot's) when it can be; else where it is, or the first that can.
     */
    private void settle(boolean toRow) {
        if (toRow) {
            if (focusedPart() != null && salvage.walkable(MARK)) {
                walk(SALVAGE, MARK);
                return;
            }
            if (focusedLot() != null && cargo.walkable(TAKE)) {
                walk(CARGO, TAKE);
                return;
            }
        }
        SoftKeys s = strips[walkStrip];
        if (s.walkable(s.walked())) {
            walk(walkStrip, s.walked());
            return;
        }
        for (int k = 0; k < strips.length; k++) {
            for (int i = 0; i < strips[k].count(); i++) {
                if (strips[k].walkable(i)) {
                    walk(k, i);
                    return;
                }
            }
        }
        // Nothing can be taken: the walk rests on the focused row's key, dim
        walk(focusedLot() != null ? CARGO : SALVAGE, 0);
    }

    private void walk(int strip, int i) {
        walkStrip = strip;
        for (int k = 0; k < strips.length; k++) {
            if (k == strip) strips[k].walkTo(i);
            else strips[k].unwalk();
        }
    }

    /** ← and →: the walk to the next key that can be taken, across the strips. */
    private void step(int dir) {
        int s = walkStrip, i = strips[s].walked();
        while (true) {
            i += dir;
            if (i < 0 || i >= strips[s].count()) {
                s += dir;
                if (s < 0 || s >= strips.length) return;
                i = dir > 0 ? 0 : strips[s].count() - 1;
            }
            if (strips[s].walkable(i)) {
                walk(s, i);
                return;
            }
        }
    }

    @Override
    boolean key(int keycode) {
        int n = rows();
        switch (keycode) {
            case Keys.UP:
            case Keys.DOWN:
                if (n > 0) focus = Math.max(0, Math.min(n - 1, focus + (keycode == Keys.UP ? -1 : 1)));
                break;
            case Keys.PAGE_UP:
            case Keys.PAGE_DOWN:
                if (n > 0) focus = Math.max(0, Math.min(n - 1, focus + (keycode == Keys.PAGE_UP ? -partRows : partRows)));
                break;
            case Keys.HOME:
                if (n > 0) focus = 0;
                break;
            case Keys.END:
                if (n > 0) focus = n - 1;
                break;
            case Keys.LEFT:
            case Keys.RIGHT:
                step(keycode == Keys.LEFT ? -1 : 1);
                return true;
            case Keys.ENTER:
            case Keys.NUMPAD_ENTER: {
                SoftKeys s = strips[walkStrip];
                int k = s.walked();
                if (k >= 0 && s.can(k)) press(walkStrip, k);
                return true;
            }
            default:
                return false;
        }
        show();
        keysFor();
        settle(true);
        return true;
    }

    @Override
    void scrolled(int amount) {
        key(amount > 0 ? Keys.PAGE_DOWN : Keys.PAGE_UP);
    }

    private void press(int strip, int k) {
        HudData d = modes.data();
        BoardingProvider bp = d == null ? null : d.boarding;
        if (bp == null || board == null) return;
        String said = null;
        if (strip == PARTY) {
            said = k == BOARD ? bp.orderBoarding() : k == STORM ? bp.storm() : bp.recall();
        } else if (strip == SALVAGE) {
            Part p = focusedPart();
            if (p != null) said = bp.toggleStrip(p.x, p.y);
        } else if (k == TAKE) {
            Cargo c = focusedLot();
            if (c != null) said = bp.takeCargo(c.commodity);
        } else if (d.crew != null) {
            said = d.crew.takePrisoners();
        }
        modes.say("BOARDING", said);
        modes.refresh();
    }

    // ------------------------------------------------------------------ layout and paint

    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        lw = Math.round(w * 0.669f);
        rx = x + lw + GAP;
        rw = x + w - rx;
        ph = Math.max(200f, Math.min(320f, Math.round(h * 0.356f)));
        sy = y + ph + 6f;
        sh = y + h - Glass.KEYS - sy;
        partRows = Math.max(1, (int) Math.floor((sh - 50f - 34f) / Glass.ROW + 1e-3f));
        lotRows = Math.max(1, (int) Math.floor((sh - 48f - 150f) / Glass.ROW + 1e-3f));
        show();
        float pgh = ph - Glass.KEYS;
        party.place(rx + 8f, y + pgh + Glass.KEY_GAP, rw - 16f, y + pgh - 7f * Glass.P - 6f);
        salvage.place(x + 8f, sy + sh + Glass.KEY_GAP, lw - 16f, sy + sh - 7f * Glass.P - 6f);
        cargo.place(rx + 8f, sy + sh + Glass.KEY_GAP, rw - 16f, sy + sh - 7f * Glass.P - 6f);
    }

    @Override
    void paint(Filament f, float time) {
        if (board == null) return;
        Glass.pane(f, x, y, lw, ph, "PLAN");
        plan.paint(f, x, y, lw, ph, board, crew.crew, focusedPart(), crew.shipName);
        paintParty(f);
        paintSalvage(f);
        paintCargo(f);
        party.paint(f);
        salvage.paint(f);
        cargo.paint(f);
    }

    /**
     * The party: how many of ours are across and how it stands, who they are and where, and what holds
     * them back. Once nobody defends the ship it is who is across stripping it (ACROSS, as the task
     * spec has it), since no party is needed then.
     */
    private void paintParty(Filament f) {
        float pgh = ph - Glass.KEYS, q0 = rx + 12f, q1 = rx + rw - 12f;
        Glass.pane(f, rx, y, rw, pgh, board.salvage ? "ACROSS" : "PARTY");
        float py = y + 30f;
        int working = 0;
        for (int i = 0; i < aboard.size; i++) if (aboard.get(i).working) working++;
        int n = board.salvage ? aboard.size : board.aboard;
        String state = board.salvage ? (n == 0 ? "NOBODY ACROSS" : working > 0 ? "AT WORK" : "ACROSS")
                : board.boarding ? (board.aboard > 0 ? "ABOARD" : "ON THE WAY")
                : board.boardRefusal.isEmpty() ? "READY" : "STANDING BY";
        float cx = q0 + f.seg7(q0, py, 16f, String.valueOf(n), f.o().cells(n > 9 ? 2 : 1).norm()) + 10f;
        Glass.text(f, cx, py + 3f, state, Drive.FULL);
        py += 28f;
        ShipGridComponent grid = board.craft == null ? null : board.craft.getComponent(ShipGridComponent.class);
        if (grid != roomsOf) {
            rooms.reset();
            roomsOf = grid;
        }
        int cells = Glass.cells(q1 - q0);
        float bottom = y + pgh - 24f;
        for (int i = 0; i < aboard.size && py + Glass.RH <= bottom; i++) {
            Member m = aboard.get(i);
            String where = grid == null || Float.isNaN(m.x) ? "" : rooms.at(grid, (int) Math.floor(m.x), (int) Math.floor(m.y));
            where = where.isEmpty() ? "ABOARD" : "IN THE " + where.toUpperCase(Locale.ROOT);
            Glass.text(f, q0, py, Glass.cut(cols.clear().left(m.name.toUpperCase(Locale.ROOT), 8).left(m.role.toUpperCase(Locale.ROOT), 10)
                    .left(m.working ? where + ", AT WORK" : where, 24).toString(), cells), Drive.NORM);
            py += Glass.ROW;
        }
        py += 4f;
        String why = board.salvage ? (board.taken ? "Taken: the crew go across to strip what is marked, and carry it home"
                : "No one aboard defends it: marking a part to strip takes it")
                : board.boarding ? "Recall brings the party home"
                : board.boardRefusal.isEmpty() ? (board.stormRefusal.isEmpty()
                ? "Board sends the party across; Storm forces every shut door on the way" : "Board sends the party across")
                : board.boardRefusal;
        int lines = Glass.wrap(why, cells, wrapped, wrapped.length);
        for (int i = 0; i < lines && py + Glass.RH <= bottom; i++, py += Glass.RH) Glass.text(f, q0, py, wrapped[i], Drive.DIM);
    }

    /** The parts to strip, a page at a time, or why none can be stripped yet. */
    private void paintSalvage(Filament f) {
        float x0 = x + 10f, x1 = x + lw - 12f;
        Glass.pane(f, x, sy, lw, sh, "SALVAGE");
        if (!board.salvage) {
            String why = board.salvageRefusal.isEmpty() ? board.boardRefusal : board.salvageRefusal;
            if (why.isEmpty()) why = "Take it to strip it";
            int n = Glass.wrap(why, Glass.cells(x1 - x0) - 1, wrapped, wrapped.length);
            for (int i = 0; i < n; i++) Glass.text(f, x0 + Glass.CW, sy + 40f + i * Glass.RH, wrapped[i], Drive.DIM);
            return;
        }
        int marked = 0, off = 0;
        for (int i = 0; i < board.parts.size; i++) {
            Part p = board.parts.get(i);
            if (p.off) off++;
            else if (p.marked) marked++;
        }
        String count = marked + " MARKED · " + off + " OFF";
        Glass.right(f, x1, sy + 9f, count, Drive.DIM);
        if (board.parts.size == 0) {
            Glass.text(f, x0 + Glass.CW, sy + 50f, "NOTHING LEFT TO STRIP", Drive.DIM);
            return;
        }
        int pages = (board.parts.size + partRows - 1) / partRows;
        if (pages > 1) {
            int page = partFirst / partRows + 1;
            float pw = Glass.pagerWidth(f, page, pages);
            Glass.pager(f, x1 - pw - Glass.width(count.length()) - 16f, sy + 8f, page, pages);
        }
        float hy = sy + 43f;
        Glass.label(f, x0 + Glass.CW, hy, "SYSTEM");
        Glass.labelRight(f, x0 + (1 + 30) * Glass.CW - Glass.P, hy, "SELLS FOR");
        Glass.label(f, x0 + (1 + 34) * Glass.CW, hy, "STATUS");
        int cells = Glass.cells(x1 - x0);
        int end = Math.min(board.parts.size, partFirst + partRows);
        for (int i = partFirst; i < end; i++) {
            Part p = board.parts.get(i);
            String line = cols.clear().left(p.name.toUpperCase(Locale.ROOT), 18).right(Glass.thousands(p.value), 11).gap(4)
                    .left(p.status.toUpperCase(Locale.ROOT), cells - 34).toString();
            Glass.row(f, x0, sy + 50f + (i - partFirst) * Glass.ROW, line, cells, i == focus, p.status.isEmpty() ? Drive.DIM : Drive.NORM);
        }
    }

    /** The cargo aboard to take, and the prisoners. */
    private void paintCargo(Filament f) {
        float q0 = rx + 10f, q1 = rx + rw - 12f;
        Glass.pane(f, rx, sy, rw, sh, "CARGO");
        int cells = Glass.cells(q1 - q0), parts = board.salvage ? board.parts.size : 0;
        float cy = sy + 30f;
        if (board.cargo.size == 0) {
            Glass.text(f, q0 + Glass.CW, cy + 18f, board.byEva ? "LATCH ON TO TAKE ITS CARGO" : "NO CARGO ABOARD", Drive.DIM);
            cy += 18f + Glass.ROW;
        } else {
            Glass.label(f, q0 + Glass.CW, cy + 9.5f, "LOT");
            Glass.labelRight(f, q0 + (1 + 18) * Glass.CW - Glass.P, cy + 9.5f, "UNITS");
            cy += 18f;
            int shown = Math.min(board.cargo.size, lotRows);
            boolean all = true;
            for (int i = 0; i < shown; i++) {
                Cargo c = board.cargo.get(i);
                if (c.onOrder <= 0.05) all = false;
                String line = cols.clear().left(c.commodity.replace('_', ' ').toUpperCase(Locale.ROOT), 12).right(Glass.units(c.units), 6).gap(2)
                        .left(c.onOrder > 0.05 ? Glass.units(c.onOrder) + " COMING OVER" : "", cells - 21).toString();
                Glass.row(f, q0, cy + i * Glass.ROW, line, cells, focus == parts + i, Drive.NORM);
            }
            cy += shown * Glass.ROW + 4f;
            if (board.cargo.size > shown) {
                Glass.text(f, q0 + Glass.CW, cy, "+" + (board.cargo.size - shown) + " MORE: ↓ TO THEM", Drive.DIM);
                cy += Glass.RH + 4f;
            }
            Glass.text(f, q0 + Glass.CW, cy, all ? "EVERY LOT IS ON ITS WAY" : "TAKE ALL: THE LOT IN FOCUS, CARRIED OVER", Drive.DIM);
            cy += Glass.ROW + 8f;
        }
        float bottom = sy + sh - 30f;
        if (cy + 22f > bottom) return;
        Glass.head(f, q0, cy, q1, "PRISONERS");
        cy += 22f;
        String first, second, room = "";
        if (crew.prisonersAlongside) {
            first = board.prisoners > 0 ? board.prisoners + " SURRENDERED, ABOARD IT" : "ITS PRISONERS, TO BRING ABOARD";
            room = berths(board);
            second = "Brought aboard, a station takes them: hand them over, ransom them or let them go";
        } else if (board.taken && board.prisoners > 0) {
            first = board.prisoners + " SURRENDERED, HELD ABOARD IT";
            room = berths(board);
            second = "";
        } else {
            first = board.taken || board.defenders == 0 ? "NONE ABOARD" : "NONE YET: THEY SURRENDER WHEN IT IS TAKEN";
            second = "";
        }
        int k = Glass.wrap(first, cells - 1, wrapped, 2);
        for (int i = 0; i < k; i++, cy += Glass.RH) Glass.text(f, q0 + Glass.CW, cy, wrapped[i], Drive.NORM);
        if (!room.isEmpty() && cy + Glass.RH <= bottom) {
            Glass.text(f, q0 + Glass.CW, cy, Glass.cut(room, cells - 1), Drive.NORM);
            cy += Glass.RH;
        }
        cy += 2f;
        int n = Glass.wrap(second, cells - 1, wrapped, 2);
        for (int i = 0; i < n && cy + Glass.RH <= bottom; i++, cy += Glass.RH) Glass.text(f, q0 + Glass.CW, cy, wrapped[i], Drive.DIM);
    }

    /**
     * The berths free aboard for its prisoners, since only as many come across (X2's rule,
     * {@code Prisoners.takeAboard}): "4 BERTHS FREE", or "2 BERTHS FREE: ONLY 2 COME ACROSS".
     */
    static String berths(BoardingBoard b) {
        int free = Math.max(0, b.berthsFree);
        if (free == 0) return b.prisoners > 0 ? "NO BERTHS FREE: NONE COME ACROSS" : "NO BERTHS FREE";
        String n = free == 1 ? "1 BERTH FREE" : free + " BERTHS FREE";
        if (b.prisoners <= free) return n;
        return n + ": ONLY " + free + (free == 1 ? " COMES ACROSS" : " COME ACROSS");
    }

    // ------------------------------------------------------------------ clicks

    @Override
    void boxes(FilamentActor a) {
        if (board == null) return;
        float x0 = x + 10f;
        if (board.salvage) {
            int cells = Glass.cells(x + lw - 12f - x0);
            int end = Math.min(board.parts.size, partFirst + partRows);
            for (int i = partFirst; i < end; i++) a.box(ROW_IDS + i, x0, sy + 50f + (i - partFirst) * Glass.ROW - 3f, Glass.width(cells), Glass.ROW);
        }
        int parts = board.salvage ? board.parts.size : 0, shown = Math.min(board.cargo.size, lotRows);
        float q0 = rx + 10f;
        int cells = Glass.cells(rx + rw - 12f - q0);
        for (int i = 0; i < shown; i++) a.box(ROW_IDS + parts + i, q0, sy + 48f + i * Glass.ROW - 3f, Glass.width(cells), Glass.ROW);
        for (int k = 0; k < strips.length; k++) strips[k].boxes(a, KEY_IDS + k * SoftKeys.MAX);
    }

    @Override
    void clicked(int id) {
        if (id >= KEY_IDS) {
            int k = (id - KEY_IDS) / SoftKeys.MAX, i = (id - KEY_IDS) % SoftKeys.MAX;
            if (k >= strips.length) return;
            walk(k, i);
            if (strips[k].can(i)) press(k, i);
        } else if (id >= ROW_IDS) {
            focus = Math.max(0, Math.min(rows() - 1, id - ROW_IDS));
            show();
            keysFor();
            settle(true);
        }
    }
}
