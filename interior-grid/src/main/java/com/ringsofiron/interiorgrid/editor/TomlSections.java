package com.ringsofiron.interiorgrid.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites one {@code [type.id]} section of a TOML file's text and leaves the rest of the file
 * as it was. Used by the ship editor so saving keeps a data file's layout and comments.
 *
 * <p>A section runs from its header to the next table header, less the comments and blank lines
 * just before that header (they introduce the next section). Inside it, each comment block
 * belongs to the key below it, and moves with that key when the section is rewritten.
 */
public final class TomlSections {

    private TomlSections() {
    }

    /** One {@code key = value} of a section, as written: the value text may span several lines. */
    public static final class Entry {
        public final String key;
        /** The value as TOML text, e.g. {@code "Corvette"} or a multi-line array. */
        public final String value;

        public Entry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    /** A key of an existing section with the comment lines above it. */
    private static final class OldEntry {
        final String key;
        final List<String> comments;
        final List<String> lines;

        OldEntry(String key, List<String> comments, List<String> lines) {
            this.key = key;
            this.comments = comments;
            this.lines = lines;
        }
    }

    /** Where a section sits in a file, as line indexes: header, and one past its last line. */
    private static final class Span {
        final int header, end;

        Span(int header, int end) {
            this.header = header;
            this.end = end;
        }
    }

    /** True if the text has a {@code [type.id]} section. */
    public static boolean has(String text, String type, String id) {
        return find(lines(text), type, id) != null;
    }

    /**
     * Replaces the {@code [type.id]} section with {@code entries}, or appends it at the end if the
     * file has none. Comments above a key stay above it; keys of the old section that are not in
     * {@code known} (written by something newer than the editor) are kept after the new ones;
     * known keys missing from {@code entries} are dropped with their comments.
     */
    public static String put(String text, String type, String id, List<Entry> entries, Set<String> known) {
        List<String> lines = lines(text);
        Span span = find(lines, type, id);
        List<String> body = new ArrayList<>();
        body.add("[" + type + "." + id + "]");
        if (span == null) {
            for (Entry e : entries) body.add(e.key + " = " + e.value);
            List<String> out = new ArrayList<>(lines);
            while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
            if (!out.isEmpty()) out.add("");
            out.addAll(body);
            return join(out, true);
        }
        Map<String, OldEntry> old = new LinkedHashMap<>();
        List<String> leading = parseSection(lines, span, old);
        body.addAll(leading);
        for (Entry e : entries) {
            OldEntry o = old.remove(e.key);
            if (o != null) body.addAll(o.comments);
            body.add(e.key + " = " + e.value);
        }
        for (OldEntry o : old.values()) {
            if (known.contains(o.key)) continue;
            body.addAll(o.comments);
            body.addAll(o.lines);
        }
        List<String> out = new ArrayList<>(lines.subList(0, span.header));
        out.addAll(body);
        out.addAll(lines.subList(span.end, lines.size()));
        return join(out, text.endsWith("\n") || text.isEmpty());
    }

    /** Removes the {@code [type.id]} section, if there is one. */
    public static String remove(String text, String type, String id) {
        List<String> lines = lines(text);
        Span span = find(lines, type, id);
        if (span == null) return text;
        List<String> out = new ArrayList<>(lines.subList(0, span.header));
        // Drop the blank line that separated it, so removals do not pile up gaps
        int after = span.end;
        while (after < lines.size() && lines.get(after).trim().isEmpty()) after++;
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
        if (!out.isEmpty() && after < lines.size()) out.add("");
        out.addAll(lines.subList(after, lines.size()));
        return join(out, text.endsWith("\n"));
    }

    /**
     * Splits a section's lines into keys with their comments. Returns the comment lines at the
     * top of the section that are followed by a blank line rather than a key.
     */
    private static List<String> parseSection(List<String> lines, Span span, Map<String, OldEntry> out) {
        List<String> leading = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int i = span.header + 1;
        boolean seenKey = false;
        while (i < span.end) {
            String line = lines.get(i);
            String t = line.trim();
            if (t.isEmpty()) {
                if (seenKey) {
                    pending.add(line);
                } else {
                    leading.addAll(pending);
                    leading.add(line);
                    pending = new ArrayList<>();
                }
                i++;
                continue;
            }
            if (t.startsWith("#")) {
                pending.add(line);
                i++;
                continue;
            }
            int eq = line.indexOf('=');
            String key = eq > 0 ? line.substring(0, eq).trim() : t;
            List<String> valueLines = new ArrayList<>();
            int depth = 0;
            do {
                String l = lines.get(i);
                valueLines.add(l);
                depth += depthChange(l);
                i++;
            } while (depth > 0 && i < span.end);
            out.put(key, new OldEntry(key, pending, valueLines));
            pending = new ArrayList<>();
            seenKey = true;
        }
        return leading;
    }

    private static Span find(List<String> lines, String type, String id) {
        int depth = 0;
        int header = -1;
        String want = type + "." + id;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (depth == 0) {
                String name = headerName(line);
                if (name != null) {
                    if (header >= 0) return new Span(header, trimTail(lines, header, i));
                    if (name.equals(want)) header = i;
                    continue;
                }
            }
            depth += depthChange(line);
            if (depth < 0) depth = 0;
        }
        return header >= 0 ? new Span(header, trimTail(lines, header, lines.size())) : null;
    }

    /** End of a section that runs up to {@code next}, less the comments and blanks just before it. */
    private static int trimTail(List<String> lines, int header, int next) {
        int end = next;
        while (end > header + 1) {
            String t = lines.get(end - 1).trim();
            if (t.isEmpty() || t.startsWith("#")) end--;
            else break;
        }
        return end;
    }

    /** The dotted name of a {@code [table]} or {@code [[array]]} header line, unquoted, or null. */
    static String headerName(String line) {
        String t = stripComment(line).trim();
        if (!t.startsWith("[") || !t.endsWith("]")) return null;
        String inner = t.startsWith("[[") && t.endsWith("]]") ? t.substring(2, t.length() - 2) : t.substring(1, t.length() - 1);
        if (inner.isEmpty() || inner.contains("[") || inner.contains("]") || inner.contains(",")) return null;
        StringBuilder sb = new StringBuilder();
        for (String part : inner.split("\\.")) {
            String p = part.trim();
            if (p.length() >= 2 && (p.startsWith("\"") || p.startsWith("'"))) p = p.substring(1, p.length() - 1);
            if (sb.length() > 0) sb.append('.');
            sb.append(p);
        }
        return sb.toString();
    }

    /** How many brackets and braces a line opens, less those it closes, outside strings and comments. */
    static int depthChange(String line) {
        int d = 0;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '#') break;
            else if (c == '[' || c == '{') d++;
            else if (c == ']' || c == '}') d--;
        }
        return d;
    }

    private static String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '#') return line.substring(0, i);
        }
        return line;
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        String[] parts = text.replace("\r\n", "\n").split("\n", -1);
        int n = parts.length;
        if (text.endsWith("\n")) n--;
        for (int i = 0; i < n; i++) out.add(parts[i]);
        return out;
    }

    private static String join(List<String> lines, boolean trailingNewline) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        if (trailingNewline && !lines.isEmpty()) sb.append('\n');
        return sb.toString();
    }

    // ---- Value text ----

    /** A TOML basic string. */
    public static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * A TOML float: always with a decimal point or exponent, so it reads back as a float. Large
     * values are written as the data files write them, {@code 2.4e6}.
     */
    public static String num(double v) {
        double abs = Math.abs(v);
        if (abs >= 1e5 && !Double.isInfinite(v)) {
            int exp = (int) Math.floor(Math.log10(abs));
            java.math.BigDecimal mantissa = new java.math.BigDecimal(v).movePointLeft(exp)
                    .round(new java.math.MathContext(12)).stripTrailingZeros();
            String m = mantissa.toPlainString();
            if (!m.contains(".")) m += ".0";
            return m + "e" + exp;
        }
        if (v == Math.rint(v)) return Long.toString((long) v) + ".0";
        return Double.toString(v).replace('E', 'e');
    }

    /** A float as written, without the noise of widening it to a double (1.4, not 1.399999976). */
    public static String num(float v) {
        return num(Double.parseDouble(Float.toString(v)));
    }

    /** An inline table of {@code key = value} pairs, values already TOML text. */
    public static String table(String... keyValues) {
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(keyValues[i]).append(" = ").append(keyValues[i + 1]);
        }
        return sb.append(" }").toString();
    }

    /** An array written one element per line, as data files list rows and mounts. */
    public static String multiLineArray(List<String> elements) {
        if (elements.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (String e : elements) sb.append("  ").append(e).append(",\n");
        return sb.append(']').toString();
    }

    /** An array on one line. */
    public static String inlineArray(List<String> elements) {
        return "[" + String.join(", ", elements) + "]";
    }
}
