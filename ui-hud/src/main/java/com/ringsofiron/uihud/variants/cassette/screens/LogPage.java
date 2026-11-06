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
 * with its day and time, the newest at the platen. It scrolls by feeding: the wheel, Up and Down, Page
 * Up and Page Down feed the roll through, Home to the oldest and End back to the newest. Interlocked
 * keys pick what it shows: every notice, the cautions and warnings, or the good news; Left and Right
 * turn them.
 */
final class LogPage extends Page {
    private static final String[] FILTERS = {"ALL", "CAUTIONS", "GOOD NEWS"};
    private static final float LINE = 20f;
    private static final Color ROLL_TOP = Yard.hex("F1ECDD"), ROLL_FOOT = Yard.hex("E4DCC8");
    private static final Color PLATEN = Yard.hex("1E1A17"), CASE = Yard.hex("5B5E50"), CASE_FOOT = Yard.hex("45483C");
    private Boards b;
    private int filter;
    /** Lines fed back from the newest: 0 has the newest at the platen. */
    private int back;
    private int shown = 10;
    /**
     * The notices as of the last read, copied: the feed refills its messages from a pool, so an
     * entry held from one read carries another notice after the next.
     */
    private final Array<Row> rows = new Array<>();
    private final Array<Row> pool = new Array<>();
    // What the roll was last read from: read again when any of it changes
    private int readFilter = -1, readSize = -1, readCount;
    private String readNewest;
    private double readTime;

    /** One notice on the roll: what it says, how bad, and when. */
    private static final class Row {
        String text, stamp;
        AlertRegistry.Level level;
        boolean important;
    }

    LogPage() {
        super("LOG", "LOG", ClassicWindow.LOG);
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
        // Read again only when a notice came, one repeated, or the keys changed what it shows
        MessageFeed.Message first = all.size == 0 ? null : all.first();
        if (filter == readFilter && all.size == readSize && (first == null ? readNewest == null
                : first.text.equals(readNewest) && first.count == readCount && first.time == readTime)) return;
        readFilter = filter;
        readSize = all.size;
        readNewest = first == null ? null : first.text;
        readCount = first == null ? 0 : first.count;
        readTime = first == null ? 0 : first.time;
        pool.addAll(rows);
        rows.clear();
        for (int i = 0; i < all.size; i++) {
            MessageFeed.Message m = all.get(i);
            boolean keep = filter == 0 || (filter == 1 ? m.level != AlertRegistry.Level.ADVISORY : m.important);
            if (!keep) continue;
            Row r = pool.size > 0 ? pool.pop() : new Row();
            r.text = m.label();
            r.stamp = Fmt.dayTime(m.time);
            r.level = m.level;
            r.important = m.important;
            rows.add(r);
        }
        clamp();
    }

    /** How many notices the roll has, and the {@code i}th back from the newest, as last read. */
    int lines() {
        return rows.size;
    }

    String line(int i) {
        return rows.get(i).text;
    }

    private void clamp() {
        int max = Math.max(0, rows.size - shown);
        if (back > max) back = max;
        if (back < 0) back = 0;
    }

    @Override
    void shown() {
        back = 0;
    }

    @Override
    boolean key(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
                back++;
                break;
            case Input.Keys.DOWN:
                back--;
                break;
            case Input.Keys.PAGE_UP:
                back += Math.max(1, shown - 1);
                break;
            case Input.Keys.PAGE_DOWN:
                back -= Math.max(1, shown - 1);
                break;
            case Input.Keys.HOME:
                back = rows.size;
                break;
            case Input.Keys.END:
                back = 0;
                break;
            case Input.Keys.LEFT:
                setFilter((filter + FILTERS.length - 1) % FILTERS.length);
                break;
            case Input.Keys.RIGHT:
                setFilter((filter + 1) % FILTERS.length);
                break;
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

    @Override
    void scroll(float amount) {
        back -= Math.round(amount * 3f);
        clamp();
    }

    @Override
    public void hit(int code) {
        setFilter(code);
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, "NOTICE LOG", false);
        // What it shows: interlocked keys at the bay's head
        float kx = bx + 120f, kw = 110f, kh = 24f;
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
        // The roll, rising out of the teleprinter's platen
        float rw = Math.min(1280f, bw - 80f), rx = bx + (bw - rw) / 2f;
        float caseH = 70f, platen = by + bh - caseH - 14f, top = by + 44f;
        p.rect(rx + 2f, top + 3f, rw, platen - top, Yard.SHADOW, 0.5f);
        p.vgrad(rx, top, rw, platen - top, ROLL_TOP, ROLL_FOOT);
        p.rect(rx, top, rw, 10f, Color.BLACK, 0.08f);
        shown = Math.max(1, (int) ((platen - top - 24f) / LINE));
        clamp();
        int newest = back, n = Math.min(shown, rows.size - back);
        float room = rw - 160f;
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
            p.typed(m.text.length() > chars ? Ledger.cut(m.text, chars) : m.text, rx + 140f, ly, 13f, ink);
        }
        if (rows.size == 0) p.typed(filter == 0 ? "Nothing logged yet" : "Nothing of this kind logged", rx + 36f, platen - 12f, 13f, Paper.FADED);
        // The teleprinter's case and platen
        p.rrect(rx - 24f + 2f, platen + 2f, rw + 48f, caseH, 5f, Yard.SHADOW, 0.6f);
        p.vgrad(rx - 24f, platen - 2f, rw + 48f, caseH, CASE, CASE_FOOT);
        p.rect(rx - 20f, platen - 1.5f, rw + 40f, 1f, Color.WHITE, 0.1f);
        p.rrect(rx - 8f, platen, rw + 16f, 12f, 5f, PLATEN);
        p.rect(rx - 6f, platen + 2f, rw + 12f, 2f, Color.WHITE, 0.08f);
        p.textC(YardFonts.Face.LEGEND, 12f, "TELEPRINTER", rx + rw / 2f, platen + 40f, Yard.INK_LIGHT);
        p.screw(rx - 12f, platen + 50f);
        p.screw(rx + rw + 12f, platen + 50f);
        // Fed back from the newest: how far, on the case
        if (back > 0) p.legend("FED BACK " + back, rx, platen + 40f, Yard.MUSTARD);
    }
}
