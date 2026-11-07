package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.ConditionLog;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.NoticeAlerts;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The notice log (the classic log panel; Context's M5 Log): a teleprinter roll, a line for each notice
 * with its day and time, the newest at the platen, and the fight's record among them: each condition
 * as it is raised and as it clears, and each missile fired at the ship ({@link ConditionLog}), with
 * its source. Two controls at its head pick what it shows: the least severity, a threshold (all,
 * notable and up, cautions and up, warnings), and one source or all. The notices carry no source yet,
 * so they show under all only.
 *
 * <p>Kept {@link #once(boolean) once} (the Task spec's book), each condition is one line from its
 * raising: its latest words at the worst it reached, and "cleared 14:31" once it has cleared. A
 * warning an alert carries is left out for the alert's line where the record has it ({@link
 * #carried}: the salvo's, a leak's, the ship disabled), and kept where it hasn't (a wound, the
 * boarders). The notices are news, so only an alert's line is marked with its level: a warning kept
 * is important news (a death, the ship wrecked), or info for a loss and nothing more ({@link
 * MessageFeed.Message#minor}). One control then picks alerts, important news (the game's good news and
 * those warnings) or the rest.
 *
 * <p>Keys, as every mode's controls: Up and Down move between the controls and the roll, Left and
 * Right step the control with the focus (from the roll, the first). On the roll, Up and Down feed it
 * a line, Page Up and Page Down a rollful, Home to the oldest and End back to the newest; Up past the
 * oldest goes up to the controls. The wheel feeds it too.
 */
final class LogPage extends Page {
    private static final String[] FILTERS = {"ALL", "NOTABLE", "CAUTIONS", "WARNINGS"};
    /** Kept once: everything, the alerts, the news that matters, the rest of the news. */
    private static final String[] KINDS = {"ALL", "ALERTS", "IMPORTANT", "INFO"};
    private static final int ALERTS = 1, IMPORTANT = 2, INFO = 3;
    private static final float LINE = 20f;
    private static final int SEVERITY = 0, SOURCE = 1, ROLL = 2;
    private static final int HIT_SOURCE_DOWN = 10, HIT_SOURCE_UP = 11, HIT_ROLL = 20;
    private static final Color ROLL_TOP = Yard.hex("F1ECDD"), ROLL_FOOT = Yard.hex("E4DCC8");
    private static final Color PLATEN = Yard.hex("1E1A17"), CASE = Yard.hex("5B5E50"), CASE_FOOT = Yard.hex("45483C");
    private Boards b;
    /** Each condition on one line, revised in place and stamped when it clears (the Task spec's book). */
    private boolean once;
    /** The least severity shown, or kept once the kind (an index in {@link #filters()}), and the source ("" for all). */
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
    private ConditionLog readRecord;

    /** One line on the roll: what it says, how bad, when, its source ("" for a notice), and when it cleared. */
    private static final class Row {
        String text, stamp, source;
        AlertRegistry.Level level;
        boolean important;
        double time, cleared = Double.NaN;
        /** In a side panel, its words broken to the roll's width ({@link #wrapChars} a line), or null until broken. */
        final Array<String> wrap = new Array<>();
        int wrapChars = -1;
    }

    /** Whether it drew in a side panel last frame: its controls in two rows, a line's words run on under it. */
    private boolean narrow;

    LogPage() {
        super("LOG", "LOG", ClassicWindow.LOG);
    }

    /** Keeps each condition on one line, with the kinds for its control (the Task spec's book); else as the Context spec's Log. */
    LogPage once(boolean once) {
        if (this.once != once) {
            this.once = once;
            filter = 0;
            source = "";
            readFilter = -1;
        }
        return this;
    }

    private String[] filters() {
        return once ? KINDS : FILTERS;
    }

    @Override
    boolean offered() {
        return true;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        if (b.data == null) return;
        Array<MessageFeed.Message> all = b.data.messages.all();
        ConditionLog record = b.data.record;
        // Read again only when a notice or a condition came, one repeated, or the controls changed
        MessageFeed.Message first = all.size == 0 ? null : all.first();
        int revision = record.revision;
        if (filter == readFilter && source.equals(readSource) && all.size == readSize && revision == readRevision && record == readRecord
                && (first == null ? readNewest == null : first.text.equals(readNewest) && first.count == readCount && first.time == readTime)) return;
        readFilter = filter;
        readSource = source;
        readSize = all.size;
        readRevision = revision;
        readRecord = record;
        readNewest = first == null ? null : first.text;
        readCount = first == null ? 0 : first.count;
        readTime = first == null ? 0 : first.time;
        pool.addAll(rows);
        rows.clear();
        sources.clear();
        if (once) readOnce(all, record);
        else readLines(all, record);
        sortSources();
        clamp();
    }

    /** The notices newest first, and the record's lines (oldest first) merged in by time. */
    private void readLines(Array<MessageFeed.Message> all, ConditionLog record) {
        Array<ConditionLog.Entry> lines = record.entries;
        int c = lines.size - 1;
        for (int i = 0; i <= all.size; i++) {
            MessageFeed.Message m = i < all.size ? all.get(i) : null;
            while (c >= 0 && (m == null || lines.get(c).time > m.time)) {
                ConditionLog.Entry e = lines.get(c--);
                if (!sources.contains(e.source, false)) sources.add(e.source);
                if (keep(e.level, false, e.source)) add(e.text, e.level, false, e.time, e.source, Double.NaN);
            }
            if (m != null && keep(m.level, m.important, "")) add(m.label(), m.level, m.important, m.time, "", Double.NaN);
        }
    }

    /** The notices newest first, and the record's courses (oldest first) merged in by when each began; the last {@link ConditionLog#KEEP}. */
    private void readOnce(Array<MessageFeed.Message> all, ConditionLog record) {
        Array<ConditionLog.Course> held = record.courses;
        int c = held.size - 1;
        for (int i = 0; i <= all.size && rows.size < ConditionLog.KEEP; i++) {
            MessageFeed.Message m = i < all.size ? all.get(i) : null;
            while (c >= 0 && (m == null || held.get(c).time > m.time) && rows.size < ConditionLog.KEEP) {
                ConditionLog.Course k = held.get(c--);
                if (kind(filter, true, k.level, false)) add(k.text, k.level, false, k.time, "", k.cleared);
            }
            if (m == null || rows.size >= ConditionLog.KEEP || carried(m, record)) continue;
            // A notice is news, marked by whether it matters: a warning does, unless it is of a loss and nothing more
            boolean important = m.important || m.level == AlertRegistry.Level.WARNING && !m.minor;
            if (kind(filter, false, AlertRegistry.Level.ADVISORY, important)) {
                add(m.label(), AlertRegistry.Level.ADVISORY, important, m.time, "", Double.NaN);
            }
        }
    }

    /** Seconds either side of a notice in which an alert's course carries it: the record notes a fault once it has held. */
    static final double CARRY_SLACK = ConditionLog.SETTLE + ConditionLog.HOLD;
    private static final String[] NONE = {}, MISSILE = {ConditionLog.MISSILES}, PROPELLANT = {"propellant"},
            DISABLED = {"disabled"}, ROOM_AIR = {"air:"}, AIR = {"oxygen", "air:", "smoke:"}, CAPTAIN = {"captain:"},
            CASUALTY = {"casualty:"};

    /**
     * Whether a warning is one an alert carries whose line the log has: its {@link MessageFeed.Message#alert}
     * names an alert the record keeps, and the record holds a course of it from around the notice's
     * time. Kept once, the log leaves such a notice out for the alert's line (the Task spec's 9.2: an
     * alert is logged as an alert, not as a notice).
     */
    static boolean carried(MessageFeed.Message m, ConditionLog record) {
        String[] keys = courses(m.alert);
        if (keys.length == 0) return false;
        Array<ConditionLog.Course> held = record.courses;
        for (int i = 0; i < held.size; i++) {
            ConditionLog.Course k = held.get(i);
            if (k.time > m.time + CARRY_SLACK || k.isCleared() && k.cleared < m.time - CARRY_SLACK) continue;
            for (String key : keys) if (key.endsWith(":") ? k.key.startsWith(key) : k.key.equals(key)) return true;
        }
        return false;
    }

    /** The record's courses ({@link ConditionLog#key}, a prefix when it ends in a colon) that carry what a warning names. */
    private static String[] courses(String alert) {
        switch (alert) {
            case NoticeAlerts.MISSILE: return MISSILE;
            case NoticeAlerts.PROPELLANT: return PROPELLANT;
            case NoticeAlerts.DISABLED: return DISABLED;
            // A leak is its room's air course: the breach, then the vacuum, then the low pressure
            case NoticeAlerts.VENTING: return ROOM_AIR;
            case NoticeAlerts.AIR: return AIR;
            case NoticeAlerts.CAPTAIN: return CAPTAIN;
            case NoticeAlerts.CRITICAL: return CASUALTY;
            default: return NONE;
        }
    }

    private void add(String text, AlertRegistry.Level level, boolean important, double time, String src, double cleared) {
        Row r = pool.size > 0 ? pool.pop() : new Row();
        r.text = text;
        r.stamp = Fmt.dayTime(time);
        r.level = level;
        r.important = important;
        r.time = time;
        r.source = src;
        r.cleared = cleared;
        r.wrapChars = -1;
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

    /**
     * Kept once, whether a line is of the kind picked: all; the alerts; the news that matters, the
     * game's good news and its warnings (a pirate moving to intercept); or the rest of the news.
     */
    static boolean kind(int kind, boolean alert, AlertRegistry.Level level, boolean important) {
        boolean matters = important || level == AlertRegistry.Level.WARNING;
        switch (kind) {
            case ALERTS:
                return alert;
            case IMPORTANT:
                return !alert && matters;
            case INFO:
                return !alert && !matters;
            default:
                return true;
        }
    }

    /** "cleared 14:31" the day it began, else with the day: "cleared 004 02:11". */
    static String cleared(double began, double cleared) {
        long s = Math.max(0L, (long) Math.floor(cleared));
        long day = (long) Math.floor(Math.max(0, began)) / 86400;
        if (s / 86400 != day) return "cleared " + Fmt.dayTime(cleared);
        return String.format(Locale.ROOT, "cleared %02d:%02d", (s / 3600) % 24, (s / 60) % 60);
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

    /** When the {@code i}th line back from the newest cleared, kept once: NaN for a notice or a condition still holding. */
    double lineCleared(int i) {
        return rows.get(i).cleared;
    }

    AlertRegistry.Level lineLevel(int i) {
        return rows.get(i).level;
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
                else if (focus == ROLL) focus = once ? SEVERITY : SOURCE;
                else if (focus > SEVERITY) focus--;
                break;
            case Input.Keys.DOWN:
                if (focus == ROLL) back--;
                else focus = once ? ROLL : focus + 1;
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
                int d = keycode == Input.Keys.LEFT ? -1 : 1, n = filters().length;
                if (focus == ROLL) focus = SEVERITY;
                if (focus == SEVERITY) setFilter((filter + n + d) % n);
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
        } else if (code >= 0 && code < filters().length) {
            focus = SEVERITY;
            setFilter(code);
        }
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        narrow = w < 700f;
        if (narrow) {
            drawNarrow(p, hits, x, y, w, h);
            return;
        }
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, "NOTICE LOG", false);
        // The least severity, or kept once the kind: interlocked keys at the bay's head, engraved
        String[] keys = filters();
        float lx = bx + 120f;
        float tw = p.legend(once ? "SHOW" : "AT LEAST", lx, by + 22f, Yard.INK);
        float kx = lx + tw + 10f, kw = once ? 112f : 100f, kh = 24f;
        p.rrect(kx - 3f, by + 3f, keys.length * (kw + 3f) + 3f, kh + 6f, 2.5f, Yard.BAKELITE);
        for (int i = 0; i < keys.length; i++) {
            float fx = kx + i * (kw + 3f);
            boolean dn = i == filter;
            float fy = by + 6f + (dn ? 2f : 0f);
            if (!dn) p.rrect(fx + 0.5f, fy + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
            p.rrect(fx, fy, kw, kh - 2f, 2f, dn ? BigConsole.KEY_DOWN : BigConsole.KEY_UP);
            if (dn) p.rect(fx, by + 6f + kh - 4f, kw, 3f, Yard.ORANGE);
            p.textC(YardFonts.Face.LEGEND, 12f, keys[i], fx + kw / 2f, by + 6f + kh / 2f + 4.3f + (dn ? 1f : -1f), Yard.INK);
            hits.add(p, fx, by + 6f, kw, kh, this, i);
        }
        float bankW = keys.length * (kw + 3f) + 3f;
        if (focus == SEVERITY) p.frame(kx - 6f, by + 0.5f, bankW + 6f, kh + 11f, 2f, Yard.ORANGE, 0.95f);
        if (!once) {
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
        }
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
        // Kept once there is no source column: the words start after the stamp
        float textX = rx + (once ? 140f : 250f), room = rw - (textX - rx) - 20f;
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
            if (Double.isNaN(m.cleared)) {
                p.typed(m.text.length() > chars ? Ledger.cut(m.text, chars) : m.text, textX, ly, 13f, ink);
            } else {
                // The clearing typed after the words, faded, the words cut to leave it room
                String tail = "  " + cleared(m.time, m.cleared);
                int fit = Math.max(8, chars - tail.length());
                String words = m.text.length() > fit ? Ledger.cut(m.text, fit) : m.text;
                p.typed(words, textX, ly, 13f, ink);
                p.typed(tail, textX + words.length() * 7.6f, ly, 13f, Paper.FADED);
            }
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

    /**
     * In a side panel (the slate's Log, Stations 10.4): the controls in two rows at the bay's head, the
     * least severity (or kept once, the kind) over the source; under them the roll, each line's words
     * run on under its stamp where the panel is too narrow for them, the source typed faded before them.
     */
    private void drawNarrow(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        String[] keys = filters();
        String label = once ? "SHOW" : "AT LEAST";
        float lx = bx + 16f, kh = 24f;
        float col = Math.max(p.width(YardFonts.Face.LEGEND, 12f, label), once ? 0f : p.width(YardFonts.Face.LEGEND, 12f, "FROM")) + 12f;
        float kx = lx + col, kw = Math.min(once ? 92f : 84f, (bx + bw - 16f - kx) / keys.length - 3f);
        float ry = by + 8f;
        p.legend(label, lx, ry + 16f, Yard.INK);
        p.rrect(kx - 3f, ry - 3f, keys.length * (kw + 3f) + 3f, kh + 6f, 2.5f, Yard.BAKELITE);
        for (int i = 0; i < keys.length; i++) {
            float fx = kx + i * (kw + 3f);
            boolean dn = i == filter;
            float fy = ry + (dn ? 2f : 0f);
            if (!dn) p.rrect(fx + 0.5f, fy + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
            p.rrect(fx, fy, kw, kh - 2f, 2f, dn ? BigConsole.KEY_DOWN : BigConsole.KEY_UP);
            if (dn) p.rect(fx, ry + kh - 4f, kw, 3f, Yard.ORANGE);
            p.textC(YardFonts.Face.LEGEND, 12f, keys[i], fx + kw / 2f, ry + kh / 2f + 4.3f + (dn ? 1f : -1f), Yard.INK);
            hits.add(p, fx, ry, kw, kh, this, i);
        }
        float bankW = keys.length * (kw + 3f) + 3f;
        if (focus == SEVERITY) p.frame(kx - 6f, ry - 5.5f, bankW + 6f, kh + 11f, 2f, Yard.ORANGE, 0.95f);
        float top = ry + kh + 14f;
        if (!once) {
            // The source on its flaps, under the severity, between a step down and a step up
            float sy = top;
            p.legend("FROM", lx, sy + 16f, Yard.INK);
            float fx = kx;
            p.keyButton(fx, sy, 22f, 22f, "-");
            hits.add(p, fx, sy, 22f, 22f, this, HIT_SOURCE_DOWN);
            String name = source.isEmpty() ? "ALL" : source;
            float cells = 11, cw = 11f, flapsX = fx + 28f;
            p.flaps(flapsX, sy, name, (int) cells, cw, 22f, 14f, Yard.FLAP_TEXT);
            float upX = flapsX + cells * cw + 6f;
            p.keyButton(upX, sy, 22f, 22f, "+");
            hits.add(p, upX, sy, 22f, 22f, this, HIT_SOURCE_UP);
            if (focus == SOURCE) p.frame(fx - 4f, sy - 4.5f, upX + 26f - fx, 31f, 2f, Yard.ORANGE, 0.95f);
            top = sy + 22f + 14f;
        }
        // The roll, rising out of the teleprinter's platen
        float rw = bw - 48f, rx = bx + 24f;
        float caseH = 42f, platen = by + bh - caseH - 8f;
        p.rect(rx + 2f, top + 3f, rw, platen - top, Yard.SHADOW, 0.5f);
        p.vgrad(rx, top, rw, platen - top, ROLL_TOP, ROLL_FOOT);
        p.rect(rx, top, rw, 10f, Color.BLACK, 0.08f);
        hits.add(p, rx, top, rw, platen - top, this, HIT_ROLL);
        // The stamp, then the words: run on under the stamp's end where they don't fit one line
        float textX = rx + 110f;
        int chars = Math.max(12, (int) ((rx + rw - 12f - textX) / 7.6f));
        float ly = platen - 12f, floor = top + 16f;
        clampTo(Math.max(1, shown));
        int k = back, drawn = 0;
        while (k < rows.size) {
            Row m = rows.get(k);
            wrap(m, chars);
            int n = m.wrap.size;
            float first = ly - (n - 1) * LINE;
            // The oldest in view shows from its first line, or not at all
            if (first < floor && drawn > 0) break;
            boolean warn = m.level == AlertRegistry.Level.WARNING, caut = m.level == AlertRegistry.Level.CAUTION;
            Color ink = warn ? Paper.STAMP : Paper.INK;
            for (int j = 0; j < n; j++) {
                float yy = first + j * LINE;
                if (yy < floor) continue;
                String t = m.wrap.get(j);
                float tx = textX;
                if (j == 0) {
                    if (warn) p.diamond(rx + 16f, yy - 4.5f, 4.5f, Paper.STAMP);
                    else if (caut) p.triangle(rx + 16f, yy - 4.5f, 5f, Paper.INK);
                    else if (m.important) p.circle(rx + 16f, yy - 4.5f, 3f, Yard.AVOCADO);
                    p.typed(m.stamp, rx + 28f, yy, 13f, Paper.FADED);
                    if (!m.source.isEmpty()) tx += p.typed(m.source + "  ", tx, yy, 13f, Paper.FADED);
                }
                // The clearing, kept once, is typed faded after the words on the last line
                boolean tail = j == n - 1 && !Double.isNaN(m.cleared);
                String words = tail && t.endsWith(TAIL_MARK) ? t.substring(0, t.length() - TAIL_MARK.length()) : t;
                float ww = p.typed(words, tx, yy, 13f, ink);
                if (tail) p.typed((words.isEmpty() ? "" : "  ") + cleared(m.time, m.cleared), tx + ww, yy, 13f, Paper.FADED);
            }
            ly = first - LINE - 4f;
            drawn++;
            k++;
            if (ly < floor) break;
        }
        shown = Math.max(1, drawn);
        if (rows.size == 0) p.typed(filter == 0 && source.isEmpty() ? "Nothing logged yet" : "Nothing of this kind logged", rx + 28f, platen - 12f, 13f, Paper.FADED);
        // The teleprinter's case and platen, shallower than the console's
        p.rrect(rx - 16f + 2f, platen + 2f, rw + 32f, caseH, 5f, Yard.SHADOW, 0.6f);
        p.vgrad(rx - 16f, platen - 2f, rw + 32f, caseH, CASE, CASE_FOOT);
        p.rect(rx - 12f, platen - 1.5f, rw + 24f, 1f, Color.WHITE, 0.1f);
        p.rrect(rx - 8f, platen, rw + 16f, 12f, 5f, PLATEN);
        p.rect(rx - 6f, platen + 2f, rw + 12f, 2f, Color.WHITE, 0.08f);
        if (focus == ROLL) p.rect(rx - 6f, platen + 9f, rw + 12f, 2f, Yard.ORANGE);
        p.textC(YardFonts.Face.LEGEND, 12f, "TELEPRINTER", rx + rw / 2f, platen + 31f, Yard.INK_LIGHT);
        p.screw(rx - 6f, platen + 27f);
        p.screw(rx + rw + 6f, platen + 27f);
        if (back > 0) p.legend("FED BACK " + back, rx + 8f, platen + 31f, Yard.MUSTARD);
    }

    /** Marks a broken line's end where the clearing is to be typed after its words. */
    private static final String TAIL_MARK = "\u0000";

    /**
     * Breaks {@code m}'s words to {@code chars} a line, the first line short by its source, and leaves
     * room on the last for the clearing ("cleared 14:31") or gives it a line of its own.
     */
    private static void wrap(Row m, int chars) {
        if (m.wrapChars == chars) return;
        m.wrapChars = chars;
        m.wrap.clear();
        String t = m.text;
        int room = chars - (m.source.isEmpty() ? 0 : m.source.length() + 2);
        while (!t.isEmpty()) {
            int at = CrewPage.breakAt(t, Math.max(6, room));
            m.wrap.add(t.substring(0, at));
            t = t.substring(at).trim();
            room = chars;
        }
        if (m.wrap.size == 0) m.wrap.add("");
        if (!Double.isNaN(m.cleared)) {
            int tail = 2 + cleared(m.time, m.cleared).length();
            String last = m.wrap.peek();
            int lastRoom = m.wrap.size == 1 && !m.source.isEmpty() ? chars - m.source.length() - 2 : chars;
            if (last.length() + tail <= lastRoom) m.wrap.set(m.wrap.size - 1, last + TAIL_MARK);
            else m.wrap.add(TAIL_MARK);
        }
    }

    /** Keeps the lines fed back within what is logged, for a roll that shows {@code n}. */
    private void clampTo(int n) {
        int max = Math.max(0, rows.size - n);
        if (back > max) back = max;
        if (back < 0) back = 0;
    }
}
