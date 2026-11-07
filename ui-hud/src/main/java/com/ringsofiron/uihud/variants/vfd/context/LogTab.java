package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

import java.util.Locale;

/**
 * The record's Log (functional-context 10.5; mockup cx-record): every message the game keeps (the last
 * {@link MessageFeed#KEEP}, as the log window has them), newest first, each its severity's square, its
 * time, its source and what happened, under the day it came on; a page at a time. Its two filters
 * are at the top, the source and the severity: ↑ from the newest row reaches them, and ← and → step
 * the one in focus. Gunnery lines a few seconds apart are one row, at the first one's time, as the
 * feed has them in a fight: → opens the row in focus to each of them, and a message too long for its
 * row to the rest of its words; ← folds it.
 */
final class LogTab extends ModeTab {
    /** Where a row's time, source and words start, in cells: its two severity cells and the cursor's come first. */
    static final int TIME = 3, SOURCE = 13, WHAT = 26;
    /** Where an open row's lines start: a merged row's each time, and the words. */
    static final int OPEN_TIME = 4, OPEN_WHAT = 27;
    /** Click ids: a line on the page, the filters' arrows and values, the slate's keys. */
    static final int LINE_IDS = Modes.TAB_IDS, FILTER_IDS = Modes.TAB_IDS + 60, KEY_IDS = Modes.TAB_IDS + 80;
    static final int OPEN = 0, FOLD = 1, PREV = 2, NEXT = 3;
    /** The filters: none in focus, the source's, the severity's. */
    static final int NO_FILTER = 0, BY_SOURCE = 1, BY_SEVERITY = 2;
    static final String[] SEVERITIES = {"ALL", "CAUTION AND UP", "WARNING ONLY"};
    static final int SOURCE_CELLS = 11, SEVERITY_CELLS = 15;
    /** Under the content's top: the filters' row, the legends' baseline, the day that heads the page. */
    static final float FILTERS = 8f, LEGENDS = 50f, LIST = 58f;
    /** Most lines an open row adds. */
    static final int MAX_OPEN = 12;

    /** A message as read, copied: the feed's rows are the game's to reuse. */
    static final class Line {
        double time;
        String source = "", body = "";
        Level level = Level.ADVISORY;
        boolean important;
        int count = 1;
    }

    /** A row: one message, or the gunnery messages of a few seconds, newest first from {@code from}. */
    static final class Entry {
        int from, count;
        /** Its first message's time, and day. */
        double time;
        int day;
        String source = "", what = "";
        Level level = Level.ADVISORY;
        /** Lines it adds while open: one a message for a merged row, else its words' further lines. */
        int extra;
    }

    private final Array<Line> lines = new Array<>(), linePool = new Array<>();
    /** The rows, newest first, as the filters let them through. */
    final Array<Entry> entries = new Array<>();
    private final Array<Entry> entryPool = new Array<>();
    /** The entry each page starts at. */
    private final IntArray pageStart = new IntArray();
    final SoftKeys keys = new SoftKeys();
    private final Glass.Cols cols = new Glass.Cols();
    private final String[] wrapped = new String[MAX_OPEN + 1];
    /** The source filter (0 for all, else {@link Feed#SOURCES} from 1), the severity filter, and which has the focus. */
    int source, severity, filter;
    /** The entry in focus, -1 with none; who it is, to keep it across reads; the open one's. */
    int focus = -1;
    private double focusTime = Double.NaN, openTime = Double.NaN;
    private String focusText = "", openText = "";
    /** Lines a page holds, and the cells of a row. */
    private int perPage = 1, cells = 1;
    /** Whether the feed holds as many as the game keeps, so older ones are gone. */
    private boolean full;
    /** The lines painted on the page: the entry each is, or -1; and where the filters' values are. */
    private final IntArray lineEntry = new IntArray();
    private float sourceX, severityX;

    LogTab() {
        super("LOG", ClassicWindow.LOG, "L");
        keys.define(4, 4);
        keys.cap(OPEN, "→").cap(FOLD, "←").cap(PREV, "PG UP").cap(NEXT, "PG DN");
    }

    @Override
    void shown() {
        focus = entries.size > 0 ? 0 : -1;
        filter = NO_FILTER;
        remember();
    }

    // ------------------------------------------------------------------ reading

    @Override
    void refresh(Boards b, HudData d) {
        read(d.messages);
        build();
    }

    /** Copies the feed's messages, newest first. */
    void read(MessageFeed feed) {
        linePool.addAll(lines);
        lines.clear();
        Array<Message> all = feed.all();
        for (int i = 0; i < all.size; i++) {
            Message m = all.get(i);
            if (m.text == null || m.text.isEmpty()) continue;
            Line l = linePool.size > 0 ? linePool.pop() : new Line();
            l.time = m.time;
            l.source = Feed.source(m.text);
            l.body = Feed.body(m.text);
            l.level = m.level == null ? Level.ADVISORY : m.level;
            l.important = m.important;
            l.count = Math.max(1, m.count);
            lines.add(l);
        }
        full = all.size >= MessageFeed.KEEP;
    }

    /** The rows from the lines: gunnery runs merged, the filters applied; the focus kept on the row it was on. */
    void build() {
        boolean followNewest = focus <= 0;
        entryPool.addAll(entries);
        entries.clear();
        for (int i = 0; i < lines.size; ) {
            int n = run(i);
            Entry e = entry(i, n);
            if (passes(e)) entries.add(e);
            else entryPool.add(e);
            i += n;
        }
        // The focus stays on its row as new ones come in above it, unless it was on the newest
        focus = entries.size == 0 ? -1 : followNewest ? 0 : Math.min(find(focusTime, focusText, focus), entries.size - 1);
        remember();
        paginate();
    }

    /** How many lines from {@code i} are one row: gunnery a few seconds apart, else one. */
    private int run(int i) {
        Line l = lines.get(i);
        if (!merges(l)) return 1;
        int j = i + 1;
        while (j < lines.size && merges(lines.get(j)) && lines.get(j - 1).time - lines.get(j).time <= Feed.MERGE) j++;
        return j - i;
    }

    private static boolean merges(Line l) {
        return !l.important && "GUNNERY".equals(l.source);
    }

    private Entry entry(int from, int n) {
        Entry e = entryPool.size > 0 ? entryPool.pop() : new Entry();
        Line first = lines.get(from + n - 1), newest = lines.get(from);
        e.from = from;
        e.count = n;
        e.time = first.time;
        e.day = Fmt.day(first.time);
        e.source = newest.source;
        Level level = Level.ADVISORY;
        for (int k = from; k < from + n; k++) if (lines.get(k).level.ordinal() > level.ordinal()) level = lines.get(k).level;
        e.level = level;
        // A merged row is its first message and how many came after it; one message says how often it came
        if (n > 1) e.what = first.body + " (+" + (n - 1) + " MORE)";
        else e.what = first.count > 1 ? first.body + " (×" + first.count + ")" : first.body;
        return e;
    }

    private boolean passes(Entry e) {
        if (source > 0 && !Feed.SOURCES[source - 1].equalsIgnoreCase(e.source)) return false;
        if (severity == 1) return e.level == Level.CAUTION || e.level == Level.WARNING;
        if (severity == 2) return e.level == Level.WARNING;
        return true;
    }

    /** The row whose first message came at {@code time} with these words, else {@code fallback}. */
    private int find(double time, String text, int fallback) {
        for (int i = 0; i < entries.size; i++) if (is(entries.get(i), time, text)) return i;
        return Math.max(0, fallback);
    }

    private boolean is(Entry e, double time, String text) {
        Line first = lines.get(e.from + e.count - 1);
        return first.time == time && first.body.equals(text);
    }

    private void remember() {
        if (focus < 0 || focus >= entries.size) {
            focusTime = Double.NaN;
            focusText = "";
            return;
        }
        Line first = lines.get(entries.get(focus).from + entries.get(focus).count - 1);
        focusTime = first.time;
        focusText = first.body;
    }

    boolean isOpen(Entry e) {
        return !Double.isNaN(openTime) && is(e, openTime, openText);
    }

    /** How many lines {@code e} adds open: a merged row's messages, else its words past the first line. */
    private int extra(Entry e) {
        if (e.count > 1) return Math.min(MAX_OPEN, e.count);
        int n = Glass.wrap(e.what, Math.max(1, cells - WHAT), wrapped, MAX_OPEN + 1);
        return Math.max(0, n - 1);
    }

    /** Each page's first row, the open row's lines and the days that head rows on a page counted. */
    private void paginate() {
        pageStart.clear();
        if (entries.size == 0) {
            pageStart.add(0);
            keysFor();
            return;
        }
        int used = 0;
        pageStart.add(0);
        for (int i = 0; i < entries.size; i++) {
            Entry e = entries.get(i);
            e.extra = extra(e);
            boolean first = pageStart.peek() == i;
            int need = 1 + (isOpen(e) ? e.extra : 0) + (!first && e.day != entries.get(i - 1).day ? 1 : 0);
            if (!first && used + need > perPage) {
                pageStart.add(i);
                used = 1 + (isOpen(e) ? e.extra : 0);
            } else {
                used += need;
            }
        }
        keysFor();
    }

    /** The page the focus is on, from 0. */
    int page() {
        int f = Math.max(0, focus);
        int p = 0;
        while (p + 1 < pageStart.size && pageStart.get(p + 1) <= f) p++;
        return p;
    }

    int pages() {
        return Math.max(1, pageStart.size);
    }

    private int pageEnd(int p) {
        return p + 1 < pageStart.size ? pageStart.get(p + 1) : entries.size;
    }

    // ------------------------------------------------------------------ keys

    private void keysFor() {
        boolean onFilter = filter != NO_FILTER;
        Entry e = focus >= 0 && focus < entries.size ? entries.get(focus) : null;
        boolean open = e != null && isOpen(e);
        keys.set(OPEN, onFilter ? "NEXT" : "OPEN", onFilter || e != null && !open && e.extra > 0);
        keys.set(FOLD, onFilter ? "PREVIOUS" : "FOLD", onFilter || open);
        keys.set(PREV, "PREV PAGE", page() > 0);
        keys.set(NEXT, "NEXT PAGE", page() < pages() - 1);
    }

    @Override
    boolean key(int keycode) {
        switch (keycode) {
            case Keys.UP:
                if (filter == BY_SEVERITY) filter = BY_SOURCE;
                else if (filter == NO_FILTER && focus > 0) focus(focus - 1);
                else if (filter == NO_FILTER) filter = BY_SEVERITY;
                keysFor();
                return true;
            case Keys.DOWN:
                if (filter == BY_SOURCE) filter = BY_SEVERITY;
                else if (filter == BY_SEVERITY) filter = NO_FILTER;
                else if (focus + 1 < entries.size) focus(focus + 1);
                keysFor();
                return true;
            case Keys.HOME:
                filter = NO_FILTER;
                focus(0);
                return true;
            case Keys.END:
                filter = NO_FILTER;
                focus(entries.size - 1);
                return true;
            case Keys.ENTER:
            case Keys.NUMPAD_ENTER:
                if (filter == NO_FILTER) toggleOpen();
                return true;
            default:
                break;
        }
        int k = keys.key(keycode);
        if (k >= 0) {
            press(k);
            return true;
        }
        return false;
    }

    void press(int k) {
        switch (k) {
            case OPEN:
                if (filter != NO_FILTER) step(1);
                else open(true);
                break;
            case FOLD:
                if (filter != NO_FILTER) step(-1);
                else open(false);
                break;
            case PREV:
                turn(-1);
                break;
            case NEXT:
                turn(1);
                break;
            default:
                break;
        }
    }

    private void focus(int i) {
        if (entries.size == 0) return;
        focus = Math.max(0, Math.min(entries.size - 1, i));
        remember();
        keysFor();
    }

    /** Steps the filter in focus, round its values. */
    private void step(int by) {
        if (filter == BY_SOURCE) source = Math.floorMod(source + by, Feed.SOURCES.length + 1);
        else if (filter == BY_SEVERITY) severity = Math.floorMod(severity + by, SEVERITIES.length);
        focus = 0;
        build();
    }

    private void open(boolean open) {
        Entry e = focus >= 0 && focus < entries.size ? entries.get(focus) : null;
        if (e == null) return;
        if (open && e.extra > 0) {
            Line first = lines.get(e.from + e.count - 1);
            openTime = first.time;
            openText = first.body;
        } else if (!open && isOpen(e)) {
            openTime = Double.NaN;
            openText = "";
        }
        paginate();
    }

    private void toggleOpen() {
        Entry e = focus >= 0 && focus < entries.size ? entries.get(focus) : null;
        if (e != null) open(!isOpen(e));
    }

    /** Turns {@code by} pages, the focus to the new page's first row. */
    private void turn(int by) {
        int p = Math.max(0, Math.min(pages() - 1, page() + by));
        filter = NO_FILTER;
        if (p < pageStart.size) focus(pageStart.get(p));
        keysFor();
    }

    @Override
    void scrolled(int amount) {
        turn(amount);
    }

    // ------------------------------------------------------------------ layout and painting

    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        int c = Math.max(WHAT + 10, Glass.cells(w));
        // The lines under the page's day, each a row's pitch, the last whole above the foot's rule
        int n = Math.max(1, (int) Math.floor((h - LIST - Glass.ROW - 7f * Glass.P) / Glass.ROW + 1e-3f) + 1);
        if (c != cells || n != perPage) {
            cells = c;
            perPage = n;
            paginate();
        }
        if (frame != null) keys.placeSlate(frame.slateKeysX(), frame.slateKeysY());
    }

    @Override
    void paint(Filament f, float time) {
        keys.paint(f);
        float x0 = x, x1 = x + w, fy = y + FILTERS;
        // The filters, the note that older messages are gone, and the pager
        sourceX = x0;
        float sx = Slate.choice(f, x0, fy, "SOURCE", source == 0 ? "ALL" : Feed.SOURCES[source - 1].toUpperCase(Locale.ROOT),
                SOURCE_CELLS, filter == BY_SOURCE);
        severityX = sx + 40f;
        Slate.choice(f, severityX, fy, "SEVERITY", SEVERITIES[severity], SEVERITY_CELLS, filter == BY_SEVERITY);
        int page = page();
        float pw = Slate.pagerWidth(page + 1, pages());
        Slate.pager(f, x1 - pw, fy, page + 1, pages());
        if (full) {
            String kept = "THE LAST " + MessageFeed.KEEP + " ARE KEPT";
            Slate.dim(f, x1 - pw - 3f * Glass.CW - Glass.width(kept.length()), fy, kept);
        }
        // The legends over the columns
        float ly = y + LEGENDS;
        Slate.label(f, x0 + TIME * Glass.CW, ly, "TIME");
        Slate.label(f, x0 + SOURCE * Glass.CW, ly, "SOURCE");
        Slate.label(f, x0 + WHAT * Glass.CW, ly, "WHAT HAPPENED");
        lineEntry.clear();
        float top = y + LIST;
        if (entries.size == 0) {
            Slate.dim(f, x0 + TIME * Glass.CW, top + Glass.ROW, empty());
            return;
        }
        int from = pageStart.get(page), to = pageEnd(page);
        // The day the page's first row came on heads it, and each day after it its own rows
        Slate.dim(f, x0 + TIME * Glass.CW, top, "DAY " + entries.get(from).day);
        float ry = top + Glass.ROW;
        int whatCells = Math.max(1, cells - WHAT);
        for (int i = from; i < to; i++) {
            Entry e = entries.get(i);
            if (i > from && e.day != entries.get(i - 1).day) {
                Slate.dim(f, x0 + TIME * Glass.CW, ry, "DAY " + e.day);
                lineEntry.add(-1);
                ry += Glass.ROW;
            }
            boolean open = isOpen(e), focused = i == focus && filter == NO_FILTER;
            Slate.pips(f, x0, ry, sev(e.level));
            String what = e.what;
            if (open && e.count == 1 && e.extra > 0) {
                Glass.wrap(e.what, whatCells, wrapped, MAX_OPEN + 1);
                what = wrapped[0];
            }
            cols.clear().left(Fmt.clock(e.time), 8).gap(2).left(e.source, SOURCE_CELLS).gap(2).left(Glass.cut(what, whatCells), whatCells);
            Slate.row(f, x0 + 2f * Glass.CW, ry, cols.toString(), cells - 2, focused, false);
            lineEntry.add(i);
            ry += Glass.ROW;
            if (!open) continue;
            if (e.count > 1) {
                // A merged row open: each of its messages, the first first, with its time
                int shown = 0;
                for (int k = e.from + e.count - 1; k >= e.from && shown < MAX_OPEN; k--, shown++) {
                    Line l = lines.get(k);
                    String body = l.count > 1 ? l.body + " (×" + l.count + ")" : l.body;
                    Slate.dim(f, x0 + OPEN_TIME * Glass.CW, ry, Fmt.clock(l.time));
                    Slate.dim(f, x0 + OPEN_WHAT * Glass.CW, ry, Glass.cut(body, Math.max(1, cells - OPEN_WHAT)));
                    lineEntry.add(i);
                    ry += Glass.ROW;
                }
            } else {
                int n = Glass.wrap(e.what, whatCells, wrapped, MAX_OPEN + 1);
                for (int k = 1; k < n; k++) {
                    Slate.dim(f, x0 + WHAT * Glass.CW, ry, wrapped[k]);
                    lineEntry.add(i);
                    ry += Glass.ROW;
                }
            }
        }
    }

    /** What an empty page says: nothing yet, or nothing the filters let through. */
    String empty() {
        if (lines.size == 0) return "NOTHING IN THE LOG YET";
        String from = source == 0 ? "" : " FROM " + Feed.SOURCES[source - 1].toUpperCase(Locale.ROOT);
        if (severity == 0) return "NOTHING" + from;
        return (severity == 1 ? "NO CAUTIONS OR WARNINGS" : "NO WARNINGS") + from;
    }

    static Kit.Sev sev(Level level) {
        if (level == Level.WARNING) return Kit.Sev.WARNING;
        if (level == Level.CAUTION) return Kit.Sev.CAUTION;
        return Kit.Sev.OFF;
    }

    @Override
    void boxes(FilamentActor a) {
        float fy = y + FILTERS - 3f, fh = Glass.ROW;
        // Each filter's ◀, its value, its ▶
        float vx = sourceX + 74f;
        a.box(FILTER_IDS, vx - 3f, fy, Glass.CW + 6f, fh);
        a.box(FILTER_IDS + 1, vx + 2f * Glass.CW, fy, SOURCE_CELLS * Glass.CW, fh);
        a.box(FILTER_IDS + 2, vx + (SOURCE_CELLS + 3) * Glass.CW - 3f, fy, Glass.CW + 6f, fh);
        vx = severityX + 74f;
        a.box(FILTER_IDS + 3, vx - 3f, fy, Glass.CW + 6f, fh);
        a.box(FILTER_IDS + 4, vx + 2f * Glass.CW, fy, SEVERITY_CELLS * Glass.CW, fh);
        a.box(FILTER_IDS + 5, vx + (SEVERITY_CELLS + 3) * Glass.CW - 3f, fy, Glass.CW + 6f, fh);
        float ry = y + LIST + Glass.ROW;
        for (int i = 0; i < lineEntry.size && i < FILTER_IDS - LINE_IDS; i++) {
            if (lineEntry.get(i) >= 0) a.box(LINE_IDS + i, x, ry - 3f, w, Glass.ROW);
            ry += Glass.ROW;
        }
        keys.boxes(a, KEY_IDS);
    }

    @Override
    void clicked(int id) {
        if (id >= KEY_IDS) {
            int k = id - KEY_IDS;
            if (keys.can(k)) press(k);
        } else if (id >= FILTER_IDS) {
            int k = id - FILTER_IDS;
            filter = k < 3 ? BY_SOURCE : BY_SEVERITY;
            if (k % 3 == 0) step(-1);
            else if (k % 3 == 2) step(1);
            keysFor();
        } else if (id >= LINE_IDS) {
            int i = id - LINE_IDS;
            if (i >= lineEntry.size || lineEntry.get(i) < 0) return;
            int e = lineEntry.get(i);
            boolean again = e == focus && filter == NO_FILTER;
            filter = NO_FILTER;
            focus(e);
            if (again) toggleOpen();
        }
    }
}
