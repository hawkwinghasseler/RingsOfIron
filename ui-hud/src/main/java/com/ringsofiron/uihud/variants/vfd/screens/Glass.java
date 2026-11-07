package com.ringsofiron.uihud.variants.vfd.screens;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * The terminal's parts (style-vfd.md, section 18.2), as the mockups' screens.js draws them, for the
 * game's windows under every FILAMENT HUD: panes of glass with their names, section heads with a ghost
 * rule, text on the character grid at pitch 1.5, rows with the focus inverse across the grid and its
 * cursor in column 0, severity pips, soft keys with their actions in the glass over them, key caps with
 * their words, the pager, seven-segment figures with their units, and choices between ◀ and ▶.
 *
 * <p>Coordinates are the painter's: UI units from the stage's top left, y down. Text is placed by the
 * top of its cells, legends by their baseline.
 */
public final class Glass {
    /** The character grid: 5x7 cells at pitch 1.5, 9 units a cell, 13.5 from row to row (6 x 9 dots). */
    public static final float P = 1.5f, CW = 9f, RH = 13.5f;
    /** A list's rows: a row and a half of the grid apart, as the mockups space them. */
    public static final float ROW = 20.25f;
    /** A rubber key's height, and the gap between a strip of keys and the glass it serves. */
    public static final float KEY_H = 24f, KEY_GAP = 5f;
    /** Room a strip of soft keys takes under a pane: the gap and the keys. */
    public static final float KEYS = KEY_H + KEY_GAP + 4f;

    private Glass() {
    }

    /** How wide {@code cells} cells set. */
    public static float width(int cells) {
        return cells * CW - P;
    }

    /** How many whole cells fit in {@code w}. */
    public static int cells(float w) {
        return Math.max(0, (int) Math.floor((w + P) / CW));
    }

    // ------------------------------------------------------------------ text on the grid

    /** Dot-matrix text at L2 from {@code x, y} (the top of its cells), without the cells' ghosts. Returns its width. */
    public static float text(Filament f, float x, float y, CharSequence s) {
        return f.dm(x, y, s, f.o().pitch(P).norm().noGhost());
    }

    public static float text(Filament f, float x, float y, CharSequence s, float level) {
        return f.dm(x, y, s, f.o().pitch(P).level(level).noGhost());
    }

    public static float text(Filament f, float x, float y, CharSequence s, float level, Phosphor color) {
        return f.dm(x, y, s, f.o().pitch(P).level(level).color(color).noGhost());
    }

    /** Dim text, at L1. */
    public static float dim(Filament f, float x, float y, CharSequence s) {
        return f.dm(x, y, s, f.o().pitch(P).dim().noGhost());
    }

    /** Text right-aligned so it ends at {@code right}. */
    public static float right(Filament f, float right, float y, CharSequence s, float level) {
        float w = width(s.length());
        f.dm(right - w, y, s, f.o().pitch(P).level(level).noGhost());
        return w;
    }

    /** Text in a field of {@code cells}, its unlit cells' ghosts showing; cut to the field. */
    public static float field(Filament f, float x, float y, CharSequence s, int cells, float level) {
        return f.dm(x, y, s, f.o().pitch(P).cells(cells).level(level));
    }

    /** The grid's unlit cells over {@code cols} by {@code rows} (a fraction rounds up), as the glass shows them behind a list. */
    public static void cells(Filament f, float x, float y, int cols, float rows) {
        int n = (int) Math.ceil(rows - 1e-3f);
        for (int r = 0; r < n; r++) f.dm(x, y + r * RH, "", f.o().pitch(P).cells(cols));
    }

    /**
     * A row of a list {@code cells} wide from {@code x}: the cursor's column, then {@code s}. Focus
     * lights the cursor and inverts the rest of the row; an unfocused row is at {@code level}.
     */
    public static void row(Filament f, float x, float y, CharSequence s, int cells, boolean focus, float level) {
        if (focus) {
            f.dm(x, y, "▶", f.o().pitch(P).cells(1).norm().noGhost());
            f.dm(x + CW, y, s, f.o().pitch(P).cells(cells - 1).inverse(0, cells - 1).norm().noGhost());
        } else {
            f.dm(x + CW, y, s, f.o().pitch(P).cells(cells - 1).level(level).noGhost());
        }
    }

    /** The severity pips in two cells from {@code x} on the row whose cells start at {@code y}: amber, then red. */
    public static void pips(Filament f, float x, float y, Kit.Sev sev) {
        float py = y + 3f * P;
        Kit.pip(f, x + 2f * P, py, Phosphor.AMBER, sev == Kit.Sev.CAUTION);
        Kit.pip(f, x + CW + 2f * P, py, Phosphor.RED, sev == Kit.Sev.WARNING);
    }

    // ------------------------------------------------------------------ glass

    /** A pane: a module of glass, with its name as a dim legend in its first row when it has one. */
    public static void pane(Filament f, float x, float y, float w, float h, String name) {
        f.module(x, y, w, h, f.o().filaments(Math.max(1, Math.round(h / 140f))));
        if (name != null && !name.isEmpty()) f.legend(x + 10f, y + 18f, name, f.o().size(13f).dim().noGhost());
    }

    /** A section's head in a pane: its dim legend, then a ghost rule to {@code x1}. */
    public static void head(Filament f, float x, float y, float x1, String t) {
        float lw = f.legendWidth(t, f.o().size(13f));
        f.legend(x, y + 9.5f, t, f.o().size(13f).dim().noGhost());
        rule(f, x + lw + 8f, y + 5f, x1 - x - lw - 8f);
    }

    /** A ghost rule: a line of the glass's own unlit phosphor. */
    public static void rule(Filament f, float x, float y, float w) {
        if (w > 0f) f.rectLit(x, y, w, 0.8f, f.o().ghostOnly().k(2.2f));
    }

    /** A dim legend, its baseline at {@code y}: a column's head or a row's label. */
    public static float label(Filament f, float x, float y, String t) {
        return f.legend(x, y, t, f.o().size(13f).dim().noGhost());
    }

    public static float labelRight(Filament f, float x, float y, String t) {
        return f.legend(x, y, t, f.o().size(13f).dim().noGhost().right());
    }

    // ------------------------------------------------------------------ keys

    /** One soft key's state. */
    public enum Key {
        /** Pressing it does something now. */
        ON,
        /** It can't be taken now: its action stays in its place as a dim label. */
        DIM,
        /** The one the keyboard's Enter presses, or the page showing: its action lit full. */
        FOCUS,
        /** A blank key over blank glass: a page this place doesn't offer. */
        BLANK
    }

    /**
     * A strip of {@code n} rubber keys in {@code slots} places across {@code w} from {@code x}, their tops at {@code y}, each
     * with its action in the glass row over it at {@code labelY}: {@code caps} print the keyboard keys
     * (or nothing), {@code words} the actions. Ghost ticks part the labels. A key with an LED
     * ({@code leds} not null) shows its lamp above its cap. Returns the slot width.
     */
    public static float keyStrip(Filament f, float x, float y, float w, int slots, int n, float labelY, String[] caps, String[] words,
                                 Key[] states, Filament.Led[] leds, boolean[] ledOn) {
        float slot = w / Math.max(1, slots);
        for (int i = 0; i < n; i++) {
            float sx = x + i * slot, kw = Math.min(slot - 8f, 64f), kx = sx + (slot - kw) / 2f;
            Key st = states == null ? Key.ON : states[i];
            String cap = caps == null || caps[i] == null ? "" : caps[i];
            Filament.Opt o = f.o().size(12f);
            if (leds != null && leds[i] != null) o.led(leds[i], ledOn != null && ledOn[i]);
            f.key(kx, y, kw, KEY_H, st == Key.BLANK ? "" : cap, o);
            String t = words[i];
            if (st == Key.BLANK || t == null || t.isEmpty()) continue;
            float lw = width(t.length()), lx = sx + (slot - lw) / 2f;
            float level = st == Key.FOCUS ? Drive.FULL : st == Key.DIM ? Drive.DIM : Drive.NORM;
            f.dm(lx, labelY, t, f.o().pitch(P).level(level).noGhost());
        }
        for (int i = 1; i < slots; i++) f.rectLit(x + i * slot - 0.4f, labelY - 1f, 0.8f, 7f * P + 2f, f.o().ghostOnly().k(1.5f));
        return slot;
    }

    /** Where key {@code i} of a strip like {@link #keyStrip}'s sits: its x and width, into {@code out}. */
    public static void keyBox(float x, float w, int slots, int i, float[] out) {
        float slot = w / Math.max(1, slots), kw = Math.min(slot - 8f, 64f);
        out[0] = x + i * slot + (slot - kw) / 2f;
        out[1] = kw;
    }

    /** Key caps and their word, for a row of keys printed in the glass (the foot's close keys). Returns the x after it. */
    public static float capWord(Filament f, float x, float y, String[] caps, String word, float level) {
        float cx = x;
        for (String c : caps) cx += f.keycap(cx, y, c, 18f, 12f, false) + 3f;
        cx += 3f;
        return cx + f.dm(cx, y + 4f, word, f.o().pitch(P).level(level).noGhost()) + 14f;
    }

    /** How wide {@link #capWord} sets. */
    public static float capWordWidth(Filament f, String[] caps, String word) {
        float w = 0f;
        for (String c : caps) w += f.keycapWidth(c, 18f, 12f) + 3f;
        return w + 3f + width(word.length()) + 14f;
    }

    /** "PG 1/3" with its arrows lit where there is more, from {@code x}; returns its width. */
    public static float pager(Filament f, float x, float y, int page, int of) {
        float cx = x + f.legend(x, y + 10f, "PG", f.o().size(13f).dim()) + 6f;
        String s = page + "/" + of;
        cx += f.dm(cx, y, s, f.o().pitch(P).norm().noGhost()) + 8f;
        cx += f.dm(cx, y, "▲", f.o().pitch(P).level(page > 1 ? Drive.NORM : Drive.GHOST)) + 5f;
        cx += f.dm(cx, y, "▼", f.o().pitch(P).level(page < of ? Drive.NORM : Drive.GHOST));
        return cx - x;
    }

    /** How wide {@link #pager} sets. */
    public static float pagerWidth(Filament f, int page, int of) {
        return f.legendWidth("PG", f.o().size(13f)) + 6f + width((page + "/" + of).length()) + 8f + width(1) + 5f + width(1);
    }

    /** A seven-segment figure {@code h} tall in {@code cells}, with its unit as a dim legend; returns the x after it. */
    public static float figure(Filament f, float x, float y, float h, String value, String unit, int cells, float level) {
        float cx = x + f.seg7(x, y, h, value, f.o().cells(cells).right().level(level)) + 5f;
        if (unit != null && !unit.isEmpty()) cx += f.legend(cx, y + h, unit, f.o().size(13f).dim().noGhost());
        return cx;
    }

    /** A choice: its value in {@code cells} between ◀ and ▶, inverse while it has the focus. Returns the x after it. */
    public static float choice(Filament f, float x, float y, String value, int cells, boolean focus) {
        f.dm(x, y, "◀", f.o().pitch(P).dim().noGhost());
        Filament.Opt o = f.o().pitch(P).cells(cells).norm();
        if (focus) o.inverse(0, cells);
        f.dm(x + 2f * CW, y, value, o);
        f.dm(x + CW * (cells + 3), y, "▶", f.o().pitch(P).dim().noGhost());
        return x + CW * (cells + 4);
    }

    // ------------------------------------------------------------------ words

    /** Columns into one padded line: each text in its cells, cut with "…" when too long, right-aligned where asked. */
    public static final class Cols {
        private final StringBuilder sb = new StringBuilder();

        public Cols clear() {
            sb.setLength(0);
            return this;
        }

        /** {@code t} left in {@code n} cells. */
        public Cols left(CharSequence t, int n) {
            return put(t, n, false);
        }

        /** {@code t} right-aligned in {@code n} cells. */
        public Cols right(CharSequence t, int n) {
            return put(t, n, true);
        }

        /** {@code n} blank cells. */
        public Cols gap(int n) {
            for (int i = 0; i < n; i++) sb.append(' ');
            return this;
        }

        private Cols put(CharSequence t, int n, boolean right) {
            if (n <= 0) return this;
            if (t == null) t = "";
            int len = t.length();
            if (len > n) {
                sb.append(t, 0, Math.max(0, n - 1)).append('…');
                return this;
            }
            if (right) gap(n - len);
            sb.append(t);
            if (!right) gap(n - len);
            return this;
        }

        public int length() {
            return sb.length();
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    /** {@code s} in capitals, cut to {@code cells} with "…" when it won't fit. */
    public static String cut(String s, int cells) {
        if (s == null || s.isEmpty() || cells <= 0) return "";
        String u = s.toUpperCase(Locale.ROOT);
        return u.length() <= cells ? u : u.substring(0, Math.max(0, cells - 1)) + "…";
    }

    /**
     * {@code s} in capitals broken into lines of at most {@code cells} at the spaces, into {@code out};
     * at most {@code max} lines, the last cut with "…". Returns the number of lines.
     */
    public static int wrap(String s, int cells, String[] out, int max) {
        if (s == null || s.isEmpty() || cells <= 0 || max <= 0) return 0;
        String u = s.trim().toUpperCase(Locale.ROOT);
        int n = 0, at = 0;
        while (at < u.length() && n < max) {
            int end = Math.min(u.length(), at + cells);
            if (end < u.length()) {
                int sp = u.lastIndexOf(' ', end);
                if (sp > at) end = sp;
            }
            String line = u.substring(at, end).trim();
            at = end;
            while (at < u.length() && u.charAt(at) == ' ') at++;
            if (n == max - 1 && at < u.length()) line = cut(line + " " + u.substring(at), cells);
            out[n++] = line;
        }
        return n;
    }

    /** Thousands with commas: "2,600". */
    public static String thousands(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    /** An amount of cargo: whole units with commas, or one decimal under ten when it isn't whole. */
    public static String units(double v) {
        double a = Math.abs(v);
        if (a < 10 && Math.abs(a - Math.rint(a)) > 0.05) return String.format(Locale.ROOT, "%.1f", v);
        return thousands(v);
    }
}
