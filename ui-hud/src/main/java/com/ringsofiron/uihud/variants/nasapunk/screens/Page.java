package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.variants.ClassicWindow;

import java.util.function.Supplier;

/**
 * A page of a screen: what one of the game's windows shows (the market, the yard, the crew), as
 * parts of rows with the orders each row and the page give, built afresh from the {@link Boards} a
 * few times a second and after each order. The cursor stays on the row it was on, by the row's id,
 * across rebuilds. Up and down move it, through every part in turn; left and right step its row's
 * choice (a refit's weapon), or else pick among its orders; Enter gives the order picked, the first
 * that can be given unless left or right picked another. A guarded order is given on a second press
 * within {@link #ARM_FOR} seconds of the first.
 */
public abstract class Page {
    /** How long a guarded order stays armed after its first press, s. */
    public static final float ARM_FOR = 3f;

    /** The page's name, as its tab has it ("MARKET"). */
    public final String name;
    /** The game's window it stands for, or null for a page that is part of another's screen. */
    public final ClassicWindow window;
    public final Array<Sect> sects = new Array<>();
    /** The page's own orders (REPAIR ALL, FILL TANKS), in their order. */
    public final Array<Act> acts = new Array<>();
    /** A line over the page's own orders, or "". */
    public String status = "";
    protected PageHost host;
    protected Boards boards;

    private String cursorId = "";
    private int cursor;
    /** Which of the cursor row's orders left and right picked, or -1 for the first that can be given. */
    private int actAt = -1;
    private String armed = "";
    private float armedLeft;
    private final IntArray first = new IntArray(), shown = new IntArray();
    /** Where each part was last drawn (x, y, w, h), for the wheel. */
    private final com.badlogic.gdx.utils.FloatArray areas = new com.badlogic.gdx.utils.FloatArray();

    protected Page(String name, ClassicWindow window) {
        this.name = name;
        this.window = window;
    }

    public void attach(PageHost host) {
        this.host = host;
    }

    /** Whether the page has anything to show here and now (a market while docked at one). */
    public abstract boolean offered(Boards b);

    /** Fills {@link #sects}, {@link #acts} and {@link #status} from {@code b}; they are cleared before. */
    protected abstract void build(Boards b);

    /** Builds the page again from {@code b}, keeping the cursor on its row and an armed order armed. */
    public final void refresh(Boards b) {
        boards = b;
        sects.clear();
        acts.clear();
        status = "";
        if (b != null) build(b);
        arm(acts);
        for (int s = 0; s < sects.size; s++) {
            Sect sect = sects.get(s);
            arm(sect.acts);
            for (int r = 0; r < sect.rows.size; r++) arm(sect.rows.get(r).acts);
        }
        while (first.size < sects.size) first.add(0);
        while (shown.size < sects.size) shown.add(0);
        int n = rowCount();
        int at = -1;
        if (!cursorId.isEmpty()) for (int i = 0; i < n && at < 0; i++) if (cursorId.equals(row(i).id)) at = i;
        if (at < 0) {
            at = Math.max(0, Math.min(cursor, n - 1));
            actAt = -1;
        }
        cursor = at;
        cursorId = n == 0 ? "" : row(at).id;
        for (int s = 0; s < sects.size; s++) {
            int rows = sects.get(s).rows.size;
            if (first.get(s) > Math.max(0, rows - 1)) first.set(s, Math.max(0, rows - 1));
        }
    }

    private void arm(Array<Act> list) {
        for (int i = 0; i < list.size; i++) {
            Act a = list.get(i);
            if (a.guard && a.live && armedLeft > 0f && a.id.equals(armed)) a.state = Act.State.ARMED;
        }
    }

    /** Counts down an armed order. */
    public void act(float delta) {
        if (armedLeft <= 0f) return;
        armedLeft -= delta;
        if (armedLeft <= 0f) {
            armed = "";
            if (boards != null) refresh(boards);
        }
    }

    /** Whether an order is armed now, and so how far its time has run (0 to 1), else NaN. */
    public float armedShare() {
        return armedLeft > 0f ? 1f - armedLeft / ARM_FOR : Float.NaN;
    }

    // ---- rows and the cursor ----------------------------------------------------------------------

    /** Every row of every part, in order. */
    public int rowCount() {
        int n = 0;
        for (int s = 0; s < sects.size; s++) n += sects.get(s).rows.size;
        return n;
    }

    /** Row {@code flat} of {@link #rowCount}. */
    public Row row(int flat) {
        for (int s = 0; s < sects.size; s++) {
            Array<Row> rows = sects.get(s).rows;
            if (flat < rows.size) return rows.get(flat);
            flat -= rows.size;
        }
        return null;
    }

    /** The part row {@code r} is in, or null when it is none of this page's. */
    public Sect sectOf(Row r) {
        for (int i = 0; i < sects.size; i++) if (sects.get(i).rows.contains(r, true)) return sects.get(i);
        return null;
    }

    /** The row under the cursor, or null with none. */
    public Row current() {
        return rowCount() == 0 ? null : row(cursor);
    }

    public boolean isCursor(Row r) {
        return r != null && r == current();
    }

    /** The order Enter gives on {@code r}: the one left and right picked on the cursor's row, else its first that can be given. */
    public Act picked(Row r) {
        if (r == null) return null;
        if (r == current() && actAt >= 0 && actAt < r.acts.size) return r.acts.get(actAt);
        return r.enter();
    }

    /** Whether left and right picked {@code a} on the cursor's row, so it is drawn picked. */
    public boolean isPicked(Act a) {
        Row r = current();
        return r != null && actAt >= 0 && actAt < r.acts.size && r.acts.get(actAt) == a;
    }

    /** Moves the cursor {@code d} rows, stopping at either end. */
    public void move(int d) {
        int n = rowCount();
        if (n == 0) return;
        cursor = Math.max(0, Math.min(n - 1, cursor + d));
        cursorId = row(cursor).id;
        actAt = -1;
        reveal();
    }

    /** Puts the cursor on {@code r} (a click). */
    public void moveTo(Row r) {
        int n = rowCount();
        for (int i = 0; i < n; i++) {
            if (row(i) != r) continue;
            if (i != cursor) actAt = -1;
            cursor = i;
            cursorId = r.id;
            reveal();
            return;
        }
    }

    /** Left (−1) or right (+1): steps the cursor row's choice, or picks among its orders. */
    public void step(int d) {
        Row r = current();
        if (r == null) return;
        if (r.step != null) {
            r.step.accept(d);
            if (boards != null) refresh(boards);
            return;
        }
        if (r.acts.size < 2) return;
        int at = actAt >= 0 ? actAt : Math.max(0, r.acts.indexOf(r.enter(), true));
        actAt = Math.max(0, Math.min(r.acts.size - 1, at + d));
    }

    /** Enter: the order picked on the cursor's row. */
    public boolean enter() {
        return press(picked(current()));
    }

    /**
     * Gives order {@code a}: nothing while it is barred, which says why on the feed line; a guarded
     * order arms on its first press and is given on a second while armed. True when it did something.
     */
    public boolean press(Act a) {
        if (a == null) return false;
        if (!a.live) {
            if (!a.why.isEmpty() && host != null) host.say("", a.label + ": " + a.why);
            return false;
        }
        if (a.guard && !(armedLeft > 0f && a.id.equals(armed))) {
            armed = a.id;
            armedLeft = ARM_FOR;
            a.state = Act.State.ARMED;
            return true;
        }
        armed = "";
        armedLeft = 0f;
        a.run.run();
        return true;
    }

    /** The keys a page takes: the arrows, Enter, Page Up and Page Down, Home and End. True when used. */
    public boolean key(int keycode) {
        switch (keycode) {
            case Keys.UP:
                move(-1);
                return true;
            case Keys.DOWN:
                move(1);
                return true;
            case Keys.LEFT:
                step(-1);
                return true;
            case Keys.RIGHT:
                step(1);
                return true;
            case Keys.ENTER:
            case Keys.NUMPAD_ENTER:
                enter();
                return true;
            case Keys.PAGE_UP:
                move(-Math.max(1, shownAtCursor()));
                return true;
            case Keys.PAGE_DOWN:
                move(Math.max(1, shownAtCursor()));
                return true;
            case Keys.HOME:
                move(-rowCount());
                return true;
            case Keys.END:
                move(rowCount());
                return true;
            default:
                return false;
        }
    }

    // ---- scrolling ------------------------------------------------------------------------------

    /** The first row of part {@code s} that shows. */
    public int first(int s) {
        return s >= 0 && s < first.size ? first.get(s) : 0;
    }

    /** How many rows of part {@code s} showed when it was last drawn: the drawing tells the page. */
    public void shown(int s, int n) {
        if (s < 0) return;
        while (shown.size <= s) shown.add(0);
        while (first.size <= s) first.add(0);
        shown.set(s, n);
        int rows = s < sects.size ? sects.get(s).rows.size : 0;
        // a taller screen shows more: no gap left under the last row
        if (n > 0 && first.get(s) + n > rows) first.set(s, Math.max(0, rows - n));
    }

    /** How many rows of part {@code s} showed when it was last drawn. */
    public int shownIn(int s) {
        return s >= 0 && s < shown.size ? shown.get(s) : 0;
    }

    /** Scrolls part {@code s} by {@code d} rows (the wheel), the cursor staying where it is. */
    public void scroll(int s, int d) {
        if (s < 0 || s >= sects.size) return;
        int rows = sects.get(s).rows.size, n = s < shown.size ? shown.get(s) : 0;
        first.set(s, Math.max(0, Math.min(Math.max(0, rows - Math.max(1, n)), first(s) + d)));
    }

    /** Where part {@code s} was drawn, so the wheel over it scrolls it. */
    public void area(int s, float x, float y, float w, float h) {
        while (areas.size < (s + 1) * 4) areas.add(0f);
        areas.set(s * 4, x);
        areas.set(s * 4 + 1, y);
        areas.set(s * 4 + 2, w);
        areas.set(s * 4 + 3, h);
    }

    /** The part drawn over ({@code x}, {@code y}), or −1. */
    public int sectAt(float x, float y) {
        for (int s = 0; s * 4 + 3 < areas.size && s < sects.size; s++) {
            float ax = areas.get(s * 4), ay = areas.get(s * 4 + 1), aw = areas.get(s * 4 + 2), ah = areas.get(s * 4 + 3);
            if (x >= ax && x < ax + aw && y >= ay && y < ay + ah) return s;
        }
        return -1;
    }

    /** Forgets where the parts were drawn: a screen draws its page afresh each frame. */
    public void clearAreas() {
        areas.clear();
    }

    private int shownAtCursor() {
        int flat = cursor;
        for (int s = 0; s < sects.size; s++) {
            int rows = sects.get(s).rows.size;
            if (flat < rows) return s < shown.size ? shown.get(s) : 0;
            flat -= rows;
        }
        return 0;
    }

    /** Scrolls the cursor's part so its row shows. */
    private void reveal() {
        int flat = cursor;
        for (int s = 0; s < sects.size; s++) {
            int rows = sects.get(s).rows.size;
            if (flat < rows) {
                int n = s < shown.size ? shown.get(s) : 0;
                if (flat < first(s)) first.set(s, flat);
                else if (n > 0 && flat >= first(s) + n) first.set(s, flat - n + 1);
                return;
            }
            flat -= rows;
        }
    }

    // ---- building -------------------------------------------------------------------------------

    protected Sect sect(String title) {
        Sect s = new Sect(title);
        sects.add(s);
        return s;
    }

    /** One of the page's own orders. */
    protected Act act(String label, boolean live, String why, Runnable run) {
        Act a = new Act().set(label, live, why, run);
        acts.add(a);
        return a;
    }

    /** An order that asks the game and says what came of it under {@code source}. */
    protected Runnable order(String source, Supplier<String> give) {
        return () -> {
            String said = give.get();
            if (host == null) return;
            host.reread();
            host.say(source, said);
        };
    }

    /** As {@link #order}, for one whose answer is null when it took the player to the deck or the editor: the screen closes then. */
    protected Runnable orderOrClose(String source, Supplier<String> give) {
        return () -> {
            String said = give.get();
            if (host == null) return;
            if (said == null) {
                host.closeScreen();
                return;
            }
            host.reread();
            host.say(source, said);
        };
    }
}
