package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The notice log (the classic log panel; Context's M5 Log): a teleprinter roll, a line for each notice
 * with its day and time, the newest at the platen, and the fight's record among them: each condition
 * as it is raised and as it clears, and each missile fired at the ship ({@link ConditionLog}), with
 * its source. Two controls at its head pick what it shows: the least severity, a threshold (all,
 * notable and up, cautions and up, warnings), and one source or all. The notices carry no source yet,
 * so they show under all only.
 *
 * <p>Keys, as every mode's controls: Up and Down move between the two controls and the roll, Left and
 * Right step the control with the focus (from the roll, the severity). On the roll, Up and Down feed
 * it a line, Page Up and Page Down a rollful, Home to the oldest and End back to the newest; Up past
 * the oldest goes up to the controls. The wheel feeds it too.
 */
final class LogPage extends Page {
    private static final String[] FILTERS = {"ALL", "NOTABLE", "CAUTIONS", "WARNINGS"};
    private static final float LINE = 20f;
    private static final int SEVERITY = 0, SOURCE = 1, ROLL = 2;
    private static final int HIT_SOURCE_DOWN = 10, HIT_SOURCE_UP = 11, HIT_ROLL = 20;
    private static final Color ROLL_TOP = Yard.hex("F1ECDD"), ROLL_FOOT = Yard.hex("E4DCC8");
    private static final Color PLATEN = Yard.hex("1E1A17"), CASE = Yard.hex("5B5E50"), CASE_FOOT = Yard.hex("45483C");
    private final ConditionLog conditions;
    private Boards b;
    /** The least severity shown (an index in {@link #FILTERS}), and the source ("" for all). */
    private int filter;
    private String source = "";
    private int focus = ROLL;
    /** Lines fed back from the newest: 0 has the newest at the platen. */
    private int back;
    private int shown = 10;
    /**
     * The notices as of the last read, copied: the feed refills its messages from a pool, so an
     * entry held from one read carries another notice after the next.
     */
    private final Array<Row> rows = new Array<>();
    private final Array<Row> pool = new Array<>();
    /** The sources the record has lines from, in P4's order, for the source control's steps. */
    private final Array<String> sources = new Array<>();
    // What the roll was last read from: read again when any of it changes
    private int readFilter = -1, readSize = -1, readCount, readRevision = -1;
    private String readNewest, readSource;
    private double readTime;

    /** One line on the roll: what it says, how bad, when, and its source ("" for a notice). */
    private static final class Row {
        String text, stamp, source;
        AlertRegistry.Level level;
        boolean important;
        double time;
    }

    LogPage(ConditionLog conditions) {
        super("LOG", "LOG", ClassicWindow.LOG);
        this.conditions = conditions;
    }

    @Override
    boolean offered() {
        return true;
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        if (b.data == null) return;
        Array<MessageFeed.Message> all = b.data.messages.all();
        // Read again only when a notice or a condition came, one repeated, or the controls changed
        MessageFeed.Message first = all.size == 0 ? null : all.first();
        int revision = conditions == null ? 0 : conditions.revision;
        if (filter == readFilter && source.equals(readSource) && all.size == readSize && revision == readRevision
                && (first == null ? readNewest == null : first.text.equals(readNewest) && first.count == readCount && first.time == readTime)) return;
        readFilter = filter;
        readSource = source;
        readSize = all.size;
        readRevision = revision;
        readNewest = first == null ? null : first.text;
        readCount = first == null ? 0 : first.count;
        readTime = first == null ? 0 : first.time;
        pool.addAll(rows);
        rows.clear();
        sources.clear();
        // The notices newest first, and the record's lines (oldest first) merged in by time
        Array<ConditionLog.Entry> record = conditions == null ? null : conditions.entries;
        int c = record == null ? -1 : record.size - 1;
        for (int i = 0; i <= all.size; i++) {
            MessageFeed.Message m = i < all.size ? all.get(i) : null;
            while (c >= 0 && (m == null || record.get(c).time > m.time)) {
                ConditionLog.Entry e = record.get(c--);
                if (!sources.contains(e.source, false)) sources.add(e.source);
                if (keep(e.level, false, e.source)) add(e.text, e.level, false, e.time, e.source);
            }
            if (m != null && keep(m.level, m.important, "")) add(m.label(), m.level, m.important, m.time, "");
        }
        sortSources();
        clamp();
    }

    private void add(String text, AlertRegistry.Level level, boolean important, double time, String src) {
        Row r = pool.size > 0 ? pool.pop() : new Row();
        r.text = text;
        r.stamp = Fmt.dayTime(time);
        r.level = level;
        r.important = important;
        r.time = time;
        r.source = src;
        rows.add(r);
    }

    /** Whether a line passes the controls: its severity at the threshold or over, its source the one picked. */
    boolean keep(AlertRegistry.Level level, boolean important, String src) {
        if (!source.isEmpty() && !source.equals(src)) return false;
        return passes(filter, level, important);
    }

    /** The threshold: all; notable (good news, cautions and warnings); cautions and warnings; warnings. */
    static boolean passes(int threshold, AlertRegistry.Level level, boolean important) {
        switch (threshold) {
            case 1:
                return important || level != AlertRegistry.Level.ADVISORY;
            case 2:
                return level != AlertRegistry.Level.ADVISORY;
            case 3:
                return level == AlertRegistry.Level.WARNING;
            default:
                return true;
        }
    }

    private void sortSources() {
        Array<String> had = new Array<>(sources);
        sources.clear();
        for (String s : ConditionLog.SOURCES) if (had.contains(s, false)) sources.add(s);
        // The source picked stays a step even when nothing of it is left
        if (!source.isEmpty() && !sources.contains(source, false)) sources.add(source);
    }

    /** How many lines the roll has, and the {@code i}th back from the newest, as last read. */
    int lines() {
        return rows.size;
    }

    String line(int i) {
        return rows.get(i).text;
    }

    String lineSource(int i) {
        return rows.get(i).source;
    }

    private void clamp() {
        int max = Math.max(0, rows.size - shown);
        if (back > max) back = max;
        if (back < 0) back = 0;
    }

    @Override
    void shown() {
        back = 0;
        focus = ROLL;
    }

    @Override
    boolean key(int keycode) {
        int max = Math.max(0, rows.size - shown);
        switch (keycode) {
            case Input.Keys.UP:
                if (focus == ROLL && back < max) back++;
                else if (focus > SEVERITY) focus--;
                break;
            case Input.Keys.DOWN:
                if (focus == ROLL) back--;
                else focus++;
                break;
            case Input.Keys.PAGE_UP:
                focus = ROLL;
                back += Math.max(1, shown - 1);
                break;
            case Input.Keys.PAGE_DOWN:
                focus = ROLL;
                back -= Math.max(1, shown - 1);
                break;
            case Input.Keys.HOME:
                focus = ROLL;
                back = rows.size;
                break;
            case Input.Keys.END:
                focus = ROLL;
                back = 0;
                break;
            case Input.Keys.LEFT:
            case Input.Keys.RIGHT: {
                int d = keycode == Input.Keys.LEFT ? -1 : 1;
                if (focus == ROLL) focus = SEVERITY;
                if (focus == SEVERITY) setFilter((filter + FILTERS.length + d) % FILTERS.length);
                else stepSource(d);
                break;
            }
            default:
                return false;
        }
        clamp();
        return true;
    }

    private void setFilter(int f) {
        filter = f;
        back = 0;
        if (b != null) refresh(b);
    }

    /** Steps the source control round all and the sources the record has. */
    private void stepSource(int d) {
        int n = sources.size + 1, at = source.isEmpty() ? 0 : sources.indexOf(source, false) + 1;
        at = (at + d + n) % n;
        source = at == 0 ? "" : sources.get(at - 1);
        back = 0;
        if (b != null) refresh(b);
    }

    @Override
    void scroll(float amount) {
        back -= Math.round(amount * 3f);
        clamp();
    }

    @Override
    public void hit(int code) {
        if (code == HIT_ROLL) {
            focus = ROLL;
        } else if (code == HIT_SOURCE_DOWN || code == HIT_SOURCE_UP) {
            focus = SOURCE;
            stepSource(code == HIT_SOURCE_UP ? 1 : -1);
        } else {
            focus = SEVERITY;
            setFilter(code);
        }
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, "NOTICE LOG", false);
        // The least severity: interlocked keys at the bay's head, engraved as a threshold
        float lx = bx + 120f;
        float tw = p.legend("AT LEAST", lx, by + 22f, Yard.INK);
        float kx = lx + tw + 10f, kw = 100f, kh = 24f;
        p.rrect(kx - 3f, by + 3f, FILTERS.length * (kw + 3f) + 3f, kh + 6f, 2.5f, Yard.BAKELITE);
        for (int i = 0; i < FILTERS.length; i++) {
            float fx = kx + i * (kw + 3f);
            boolean dn = i == filter;
            float fy = by + 6f + (dn ? 2f : 0f);
            if (!dn) p.rrect(fx + 0.5f, fy + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
            p.rrect(fx, fy, kw, kh - 2f, 2f, dn ? BigConsole.KEY_DOWN : BigConsole.KEY_UP);
            if (dn) p.rect(fx, by + 6f + kh - 4f, kw, 3f, Yard.ORANGE);
            p.textC(YardFonts.Face.LEGEND, 12f, FILTERS[i], fx + kw / 2f, by + 6f + kh / 2f + 4.3f + (dn ? 1f : -1f), Yard.INK);
            hits.add(p, fx, by + 6f, kw, kh, this, i);
        }
        float bankW = FILTERS.length * (kw + 3f) + 3f;
        if (focus == SEVERITY) p.frame(kx - 6f, by + 0.5f, bankW + 6f, kh + 11f, 2f, Yard.ORANGE, 0.95f);
        // The source: its name on flaps between a step down and a step up
        float sx = kx + bankW + 34f;
        float sw = p.legend("FROM", sx, by + 22f, Yard.INK);
        float fx = sx + sw + 10f;
        p.keyButton(fx, by + 6f, 22f, 22f, "-");
        hits.add(p, fx, by + 6f, 22f, 22f, this, HIT_SOURCE_DOWN);
        String name = source.isEmpty() ? "ALL" : source;
        float cells = 11, cw = 11f, flapsX = fx + 28f;
        p.flaps(flapsX, by + 6f, name, (int) cells, cw, 22f, 14f, Yard.FLAP_TEXT);
        float upX = flapsX + cells * cw + 6f;
        p.keyButton(upX, by + 6f, 22f, 22f, "+");
        hits.add(p, upX, by + 6f, 22f, 22f, this, HIT_SOURCE_UP);
        if (focus == SOURCE) p.frame(fx - 4f, by + 1.5f, upX + 26f - fx, 31f, 2f, Yard.ORANGE, 0.95f);
        // The roll, rising out of the teleprinter's platen
        float rw = Math.min(1280f, bw - 80f), rx = bx + (bw - rw) / 2f;
        float caseH = 70f, platen = by + bh - caseH - 14f, top = by + 44f;
        p.rect(rx + 2f, top + 3f, rw, platen - top, Yard.SHADOW, 0.5f);
        p.vgrad(rx, top, rw, platen - top, ROLL_TOP, ROLL_FOOT);
        p.rect(rx, top, rw, 10f, Color.BLACK, 0.08f);
        hits.add(p, rx, top, rw, platen - top, this, HIT_ROLL);
        shown = Math.max(1, (int) ((platen - top - 24f) / LINE));
        clamp();
        int newest = back, n = Math.min(shown, rows.size - back);
        float textX = rx + 250f, room = rw - 270f;
        int chars = (int) (room / 7.6f);
        for (int k = 0; k < n; k++) {
            Row m = rows.get(newest + k);
            float ly = platen - 12f - k * LINE;
            boolean warn = m.level == AlertRegistry.Level.WARNING, caut = m.level == AlertRegistry.Level.CAUTION;
            Color ink = warn ? Paper.STAMP : Paper.INK;
            if (warn) p.diamond(rx + 22f, ly - 4.5f, 4.5f, Paper.STAMP);
            else if (caut) p.triangle(rx + 22f, ly - 4.5f, 5f, Paper.INK);
            else if (m.important) p.circle(rx + 22f, ly - 4.5f, 3f, Yard.AVOCADO);
            p.typed(m.stamp, rx + 36f, ly, 13f, Paper.FADED);
            if (!m.source.isEmpty()) p.typed(m.source, rx + 140f, ly, 13f, Paper.FADED);
            p.typed(m.text.length() > chars ? Ledger.cut(m.text, chars) : m.text, textX, ly, 13f, ink);
        }
        if (rows.size == 0) p.typed(filter == 0 && source.isEmpty() ? "Nothing logged yet" : "Nothing of this kind logged", rx + 36f, platen - 12f, 13f, Paper.FADED);
        // The teleprinter's case and platen
        p.rrect(rx - 24f + 2f, platen + 2f, rw + 48f, caseH, 5f, Yard.SHADOW, 0.6f);
        p.vgrad(rx - 24f, platen - 2f, rw + 48f, caseH, CASE, CASE_FOOT);
        p.rect(rx - 20f, platen - 1.5f, rw + 40f, 1f, Color.WHITE, 0.1f);
        p.rrect(rx - 8f, platen, rw + 16f, 12f, 5f, PLATEN);
        p.rect(rx - 6f, platen + 2f, rw + 12f, 2f, Color.WHITE, 0.08f);
        // The roll has the keys: an orange bar along the platen's foot
        if (focus == ROLL) p.rect(rx - 6f, platen + 9f, rw + 12f, 2f, Yard.ORANGE);
        p.textC(YardFonts.Face.LEGEND, 12f, "TELEPRINTER", rx + rw / 2f, platen + 40f, Yard.INK_LIGHT);
        p.screw(rx - 12f, platen + 50f);
        p.screw(rx + rw + 12f, platen + 50f);
        // Fed back from the newest: how far, on the case
        if (back > 0) p.legend("FED BACK " + back, rx, platen + 40f, Yard.MUSTARD);
    }
}
