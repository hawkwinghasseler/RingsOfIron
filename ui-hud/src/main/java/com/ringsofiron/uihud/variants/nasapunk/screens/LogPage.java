package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;

import java.util.Locale;

/**
 * The notice log (L): the game's last notices, newest first, each with its time, its source and its
 * words, a severity's shape before the warnings and cautions and a dot before the notable, a divider
 * opening each day; over it two filters, the source and a severity threshold, each position with its
 * count, stepped with left and right or set by a click.
 */
public final class LogPage extends Page {
    public static final String[] SEVERITY = {"ALL", "NOTABLE", "CAUTION", "WARNING"};

    public final Filter source = new Filter("SOURCE"), severity = new Filter("SEVERITY");

    public LogPage() {
        super("LOG", ClassicWindow.LOG);
    }

    @Override
    public boolean offered(Boards b) {
        return true;
    }

    @Override
    protected void build(Boards b) {
        MessageFeed feed = b.data.messages;
        int total = feed.all().size;
        String src = source.chosen().isEmpty() ? "ALL" : source.chosen();
        int sev = Math.max(0, severity.at);
        source.clear();
        source.add("ALL", total);
        for (String s : Sources.ALL) {
            String up = s.toUpperCase(Locale.ROOT);
            int n = 0;
            for (int i = 0; i < total; i++) if (up.equals(Sources.of(feed.all().get(i).text))) n++;
            source.add(up, n);
        }
        source.set(src);
        severity.clear();
        int[] at = new int[SEVERITY.length];
        for (int i = 0; i < total; i++) {
            int rank = rank(feed.all().get(i));
            for (int r = 0; r <= rank; r++) at[r]++;
        }
        for (int i = 0; i < SEVERITY.length; i++) severity.add(SEVERITY[i], at[i]);
        severity.at = Math.min(sev, SEVERITY.length - 1);

        Sect f = sect("FILTERS");
        f.col(Col.start("", 0f));
        Row rs = f.row("filter/source");
        rs.filter = source;
        rs.step = source::step;
        Row rv = f.row("filter/severity");
        rv.filter = severity;
        rv.step = severity::step;

        Sect s = sect("LOG").empty(total == 0 ? "NOTHING YET" : "NOTHING AT THIS FILTER");
        s.col(Col.start("TIME", 92f)).col(Col.start("SOURCE", 132f)).col(Col.start("MESSAGE", 0f));
        int shown = 0;
        long day = Long.MIN_VALUE;
        String want = source.chosen();
        for (int i = 0; i < total; i++) {
            MessageFeed.Message m = feed.all().get(i);
            String from = Sources.of(m.text);
            if (!"ALL".equals(want) && !want.equals(from)) continue;
            if (rank(m) < severity.at) continue;
            shown++;
            Row r = s.row("msg/" + (long) (m.time * 10) + "/" + m.text.hashCode());
            long d = (long) Math.floor(m.time / 86400.0);
            if (d != day) r.divider = "DAY " + d;
            day = d;
            r.add(NpFormat.timeOfDay(m.time - d * 86400.0), Cell.Tone.VALUE);
            r.add(from, Cell.Tone.LABEL);
            r.add(Sources.body(m.label()), Cell.Tone.BODY);
            r.mark = m.level == Level.WARNING ? Row.WARNING : m.level == Level.CAUTION ? Row.CAUTION : m.important ? Row.NOTABLE : Row.NONE;
            r.notable = m.important;
        }
        s.sub("NEWEST FIRST · SHOWS " + shown + " OF " + total);
        s.note("EACH SEVERITY KEEPS WHAT IS MORE SERIOUS");
    }

    /** A notice's place on the severity filter: 0 routine, 1 notable, 2 a caution, 3 a warning. */
    static int rank(MessageFeed.Message m) {
        if (m.level == Level.WARNING) return 3;
        if (m.level == Level.CAUTION) return 2;
        return m.important ? 1 : 0;
    }
}
