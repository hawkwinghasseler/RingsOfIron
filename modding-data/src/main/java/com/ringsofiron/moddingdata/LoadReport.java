package com.ringsofiron.moddingdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects what happened while loading data and mods. A broken mod is recorded here and
 * skipped instead of stopping the game; the UI can show the list to the player.
 */
public class LoadReport {

    public enum Severity { INFO, WARNING, ERROR }

    public static class Entry {
        public final Severity severity;
        /** Mod id, file path or "base" that the message is about. */
        public final String source;
        public final String message;

        Entry(Severity severity, String source, String message) {
            this.severity = severity;
            this.source = source;
            this.message = message;
        }

        @Override
        public String toString() {
            return severity + " [" + source + "] " + message;
        }
    }

    private static final Logger LOG = Logger.getLogger("RingsOfIron.modding");

    private final List<Entry> entries = new ArrayList<>();

    public synchronized void info(String source, String message) {
        add(Severity.INFO, source, message);
    }

    public synchronized void warn(String source, String message) {
        add(Severity.WARNING, source, message);
    }

    public synchronized void error(String source, String message) {
        add(Severity.ERROR, source, message);
    }

    private void add(Severity severity, String source, String message) {
        Entry entry = new Entry(severity, source, message);
        entries.add(entry);
        Level level = severity == Severity.ERROR ? Level.SEVERE
                : severity == Severity.WARNING ? Level.WARNING : Level.FINE;
        LOG.log(level, entry.toString());
    }

    public synchronized List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized List<Entry> errors() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.severity == Severity.ERROR) out.add(e);
        return out;
    }

    public synchronized boolean hasErrors() {
        for (Entry e : entries) if (e.severity == Severity.ERROR) return true;
        return false;
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) sb.append(e).append('\n');
        return sb.toString();
    }
}
