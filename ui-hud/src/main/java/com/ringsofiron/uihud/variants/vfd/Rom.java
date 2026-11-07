package com.ringsofiron.uihud.variants.vfd;

/**
 * FILAMENT's character tables, as the mockups' renderer has them: the 5x7 dot-matrix character ROM
 * (upper case only, by design, with the custom glyphs every module carries) and the segment masks of
 * the seven- and fourteen-segment digits. Every lit thing is drawn from these, never from a font, so
 * each segment and dot can be ghosted, dimmed or blinked on its own (style-vfd.md, section 5).
 */
public final class Rom {
    /** Columns and rows of a dot-matrix glyph. */
    public static final int COLS = 5, ROWS = 7;

    /** The fourteen segments, in mask bit order: a top, b and c right, d bottom, e and f left, g1 and g2 the middle halves, h i j the upper diagonal, centre and diagonal, k l m the lower ones. */
    public static final String[] SEG14_NAMES = {"a", "b", "c", "d", "e", "f", "g1", "g2", "h", "i", "j", "k", "l", "m"};

    private static final long[] ASCII = new long[128];
    private static final boolean[] ASCII_DEFINED = new boolean[128];
    private static final StringBuilder EXTRA_CHARS = new StringBuilder();
    private static long[] extraGlyphs = new long[24];

    private static final int[] SEG7_ASCII = new int[128];
    private static final boolean[] SEG7_DEFINED = new boolean[128];
    private static final int[] SEG14_ASCII = new int[128];
    private static final boolean[] SEG14_DEFINED = new boolean[128];
    private static int seg7Degree = -1, seg14Degree = -1;

    static {
        def(' ', "..... ..... ..... ..... ..... ..... .....");
        def('!', "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..");
        def('"', ".#.#. .#.#. .#.#. ..... ..... ..... .....");
        def('#', ".#.#. .#.#. ##### .#.#. ##### .#.#. .#.#.");
        def('%', "##... ##..# ...#. ..#.. .#... #..## ...##");
        def('&', ".##.. #..#. #.#.. .#... #.#.# #..#. .##.#");
        def('\'', ".##.. ..#.. .#... ..... ..... ..... .....");
        def('(', "...#. ..#.. .#... .#... .#... ..#.. ...#.");
        def(')', ".#... ..#.. ...#. ...#. ...#. ..#.. .#...");
        def('*', "..... ..#.. #.#.# .###. #.#.# ..#.. .....");
        def('+', "..... ..#.. ..#.. ##### ..#.. ..#.. .....");
        def(',', "..... ..... ..... ..... .##.. ..#.. .#...");
        def('-', "..... ..... ..... ##### ..... ..... .....");
        def('.', "..... ..... ..... ..... ..... .##.. .##..");
        def('/', "..... ....# ...#. ..#.. .#... #.... .....");
        def('0', ".###. #...# #..## #.#.# ##..# #...# .###.");
        def('1', "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.");
        def('2', ".###. #...# ....# ...#. ..#.. .#... #####");
        def('3', "##### ...#. ..#.. ...#. ....# #...# .###.");
        def('4', "...#. ..##. .#.#. #..#. ##### ...#. ...#.");
        def('5', "##### #.... ####. ....# ....# #...# .###.");
        def('6', "..##. .#... #.... ####. #...# #...# .###.");
        def('7', "##### ....# ...#. ..#.. .#... .#... .#...");
        def('8', ".###. #...# #...# .###. #...# #...# .###.");
        def('9', ".###. #...# #...# .#### ....# ...#. .##..");
        def(':', "..... .##.. .##.. ..... .##.. .##.. .....");
        def(';', "..... .##.. .##.. ..... .##.. ..#.. .#...");
        def('<', "...#. ..#.. .#... #.... .#... ..#.. ...#.");
        def('=', "..... ..... ##### ..... ##### ..... .....");
        def('>', ".#... ..#.. ...#. ....# ...#. ..#.. .#...");
        def('?', ".###. #...# ....# ...#. ..#.. ..... ..#..");
        def('@', ".###. #...# ....# .##.# #.#.# #.#.# .###.");
        def('A', ".###. #...# #...# #...# ##### #...# #...#");
        def('B', "####. #...# #...# ####. #...# #...# ####.");
        def('C', ".###. #...# #.... #.... #.... #...# .###.");
        def('D', "###.. #..#. #...# #...# #...# #..#. ###..");
        def('E', "##### #.... #.... ####. #.... #.... #####");
        def('F', "##### #.... #.... ####. #.... #.... #....");
        def('G', ".###. #...# #.... #.### #...# #...# .####");
        def('H', "#...# #...# #...# ##### #...# #...# #...#");
        def('I', ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.");
        def('J', "..### ...#. ...#. ...#. ...#. #..#. .##..");
        def('K', "#...# #..#. #.#.. ##... #.#.. #..#. #...#");
        def('L', "#.... #.... #.... #.... #.... #.... #####");
        def('M', "#...# ##.## #.#.# #.#.# #...# #...# #...#");
        def('N', "#...# #...# ##..# #.#.# #..## #...# #...#");
        def('O', ".###. #...# #...# #...# #...# #...# .###.");
        def('P', "####. #...# #...# ####. #.... #.... #....");
        def('Q', ".###. #...# #...# #...# #.#.# #..#. .##.#");
        def('R', "####. #...# #...# ####. #.#.. #..#. #...#");
        def('S', ".#### #.... #.... .###. ....# ....# ####.");
        def('T', "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..");
        def('U', "#...# #...# #...# #...# #...# #...# .###.");
        def('V', "#...# #...# #...# #...# #...# .#.#. ..#..");
        def('W', "#...# #...# #...# #.#.# #.#.# #.#.# .#.#.");
        def('X', "#...# #...# .#.#. ..#.. .#.#. #...# #...#");
        def('Y', "#...# #...# #...# .#.#. ..#.. ..#.. ..#..");
        def('Z', "##### ....# ...#. ..#.. .#... #.... #####");
        def('[', ".###. .#... .#... .#... .#... .#... .###.");
        def(']', ".###. ...#. ...#. ...#. ...#. ...#. .###.");
        def('_', "..... ..... ..... ..... ..... ..... #####");
        def('|', "..#.. ..#.. ..#.. ..#.. ..#.. ..#.. ..#..");
        def('\u2191', "..#.. .###. #.#.# ..#.. ..#.. ..#.. ..#..");
        def('\u2193', "..#.. ..#.. ..#.. ..#.. #.#.# .###. ..#..");
        def('\u2192', "..... ..#.. ...#. ##### ...#. ..#.. .....");
        def('\u2190', "..... ..#.. .#... ##### .#... ..#.. .....");
        def('\u00b0', ".##.. #..#. #..#. .##.. ..... ..... .....");
        def('\u0394', "..... ..#.. ..#.. .#.#. .#.#. #...# #####");
        def('\u25b2', "..... ..#.. ..#.. .###. .###. ##### .....");
        def('\u25bc', "..... ##### .###. .###. ..#.. ..#.. .....");
        def('\u25b6', "#.... ##... ###.. ####. ###.. ##... #....");
        def('\u25c0', "....# ...## ..### .#### ..### ...## ....#");
        def('\u2588', "##### ##### ##### ##### ##### ##### #####");
        def('\u00b7', "..... ..... ..... ..#.. ..... ..... .....");
        def('\u00bb', "..... #.#.. .#.#. ..#.# .#.#. #.#.. .....");
        def('\u00d7', "..... #...# .#.#. ..#.. .#.#. #...# .....");
        def('\u00b1', "..#.. ..#.. ##### ..#.. ..#.. ..... #####");
        def('\u00b3', ".##.. ...#. ..#.. ...#. .##.. ..... .....");
        def('\u2026', "..... ..... ..... ..... ..... #.#.# .....");
        def('\u00a2', "..#.. .###. #.#.. #.#.. #.#.. .###. ..#..");
        def('\u2666', "..... ..#.. .###. ##### .###. ..#.. .....");
        def('\u25cf', "..... .###. ##### ##### ##### .###. .....");
        // Held: a job that waits (the damage control board's "‖ WAITS: UNDER FIRE")
        def('\u2016', ".#.#. .#.#. .#.#. .#.#. .#.#. .#.#. .#.#.");
        def('\u25a1', "..... ##### #...# #...# #...# ##### .....");

        seg7('0', "abcdef");
        seg7('1', "bc");
        seg7('2', "abged");
        seg7('3', "abgcd");
        seg7('4', "fgbc");
        seg7('5', "afgcd");
        seg7('6', "afgedc");
        seg7('7', "abc");
        seg7('8', "abcdefg");
        seg7('9', "abcdfg");
        seg7('-', "g");
        seg7('_', "d");
        seg7(' ', "");
        seg7('A', "abcefg");
        seg7('b', "cdefg");
        seg7('C', "adef");
        seg7('c', "deg");
        seg7('d', "bcdeg");
        seg7('E', "adefg");
        seg7('F', "aefg");
        seg7('H', "bcefg");
        seg7('h', "cefg");
        seg7('L', "def");
        seg7('o', "cdeg");
        seg7('O', "abcdef");
        seg7('P', "abefg");
        seg7('r', "eg");
        seg7('t', "defg");
        seg7('U', "bcdef");
        seg7('u', "cde");
        seg7('n', "ceg");
        seg7('S', "afgcd");
        seg7('y', "bcdfg");
        seg7('J', "bcde");
        seg7('\u00b0', "abfg");

        seg14(' ', "");
        seg14('0', "a b c d e f j k");
        seg14('1', "b c j");
        seg14('2', "a b g1 g2 e d");
        seg14('3', "a b c d g2");
        seg14('4', "f g1 g2 b c");
        seg14('5', "a f g1 g2 c d");
        seg14('6', "a f e d c g1 g2");
        seg14('7', "a b c");
        seg14('8', "a b c d e f g1 g2");
        seg14('9', "a b c d f g1 g2");
        seg14('A', "a b c e f g1 g2");
        seg14('B', "a b c d i l g2");
        seg14('C', "a d e f");
        seg14('D', "a b c d i l");
        seg14('E', "a d e f g1 g2");
        seg14('F', "a e f g1");
        seg14('G', "a c d e f g2");
        seg14('H', "b c e f g1 g2");
        seg14('I', "a d i l");
        seg14('J', "b c d e");
        seg14('K', "e f g1 j m");
        seg14('L', "d e f");
        seg14('M', "b c e f h j");
        seg14('N', "b c e f h m");
        seg14('O', "a b c d e f");
        seg14('P', "a b e f g1 g2");
        seg14('Q', "a b c d e f m");
        seg14('R', "a b e f g1 g2 m");
        seg14('S', "a f g1 g2 c d");
        seg14('T', "a i l");
        seg14('U', "b c d e f");
        seg14('V', "e f k j");
        seg14('W', "b c e f k m");
        seg14('X', "h j k m");
        seg14('Y', "h j l");
        seg14('Z', "a d j k");
        seg14('-', "g1 g2");
        seg14('+', "g1 g2 i l");
        seg14('/', "j k");
        seg14('*', "g1 g2 h i j k l m");
        seg14('=', "g1 g2 d");
        seg14('_', "d");
        seg14('<', "j m");
        seg14('>', "h k");
        seg14('\'', "i");
        seg14('\u00b0', "a b g2 i");
        seg14('.', "");
        seg14(':', "i l");
    }

    private Rom() {
    }

    /**
     * The 5x7 glyph for {@code ch}, folded to upper case, as a mask: bit {@code row * 5 + col} is set
     * where the dot is lit, rows from the top and columns from the left. A character the ROM lacks
     * shows as '?', as a real module's would.
     */
    public static long glyph(char ch) {
        char up = Character.toUpperCase(ch);
        if (up < 128) return ASCII_DEFINED[up] ? ASCII[up] : ASCII['?'];
        int i = extra(up);
        return i >= 0 ? extraGlyphs[i] : ASCII['?'];
    }

    /** Whether the ROM holds {@code ch} (after folding to upper case). */
    public static boolean has(char ch) {
        char up = Character.toUpperCase(ch);
        if (up < 128) return ASCII_DEFINED[up];
        return extra(up) >= 0;
    }

    private static int extra(char ch) {
        for (int i = 0; i < EXTRA_CHARS.length(); i++) if (EXTRA_CHARS.charAt(i) == ch) return i;
        return -1;
    }

    /** The glyphs past ASCII that the ROM holds, in the order they were defined. */
    static String extras() {
        return EXTRA_CHARS.toString();
    }

    /** Whether the dot at {@code row}, {@code col} of {@code glyph} is lit. */
    public static boolean lit(long glyph, int row, int col) {
        return (glyph >>> (row * COLS + col) & 1L) != 0;
    }

    /**
     * The seven-segment mask for {@code ch}: bit 0 is segment a, through bit 6 for g. Seven segments
     * show digits and a few letters; anything else is blank.
     */
    public static int seg7(char ch) {
        if (ch == '\u00b0') return seg7Degree;
        if (ch < 128 && SEG7_DEFINED[ch]) return SEG7_ASCII[ch];
        // Upper and lower case differ on seven segments (b, c, d, h, o, r, t, u, n, y), so fold only when one is missing
        char other = Character.isUpperCase(ch) ? Character.toLowerCase(ch) : Character.toUpperCase(ch);
        if (other < 128 && SEG7_DEFINED[other]) return SEG7_ASCII[other];
        return 0;
    }

    /** The fourteen-segment mask for {@code ch}, folded to upper case, in {@link #SEG14_NAMES} order. */
    public static int seg14(char ch) {
        char up = Character.toUpperCase(ch);
        if (up == '\u00b0') return seg14Degree;
        return up < 128 && SEG14_DEFINED[up] ? SEG14_ASCII[up] : 0;
    }

    private static void def(char ch, String rows) {
        String[] r = rows.split(" ");
        if (r.length != ROWS) throw new IllegalStateException("glyph " + ch + " has " + r.length + " rows");
        long mask = 0;
        for (int row = 0; row < ROWS; row++) {
            if (r[row].length() != COLS) throw new IllegalStateException("glyph " + ch + " row " + row);
            for (int col = 0; col < COLS; col++) if (r[row].charAt(col) == '#') mask |= 1L << (row * COLS + col);
        }
        if (ch < 128) {
            ASCII[ch] = mask;
            ASCII_DEFINED[ch] = true;
        } else {
            int i = EXTRA_CHARS.length();
            if (i == extraGlyphs.length) extraGlyphs = java.util.Arrays.copyOf(extraGlyphs, i * 2);
            EXTRA_CHARS.append(ch);
            extraGlyphs[i] = mask;
        }
    }

    private static void seg7(char ch, String segments) {
        int mask = 0;
        for (int i = 0; i < segments.length(); i++) mask |= 1 << (segments.charAt(i) - 'a');
        if (ch == '\u00b0') {
            seg7Degree = mask;
            return;
        }
        SEG7_ASCII[ch] = mask;
        SEG7_DEFINED[ch] = true;
    }

    private static void seg14(char ch, String segments) {
        int mask = 0;
        if (!segments.isEmpty()) {
            for (String s : segments.split(" ")) {
                int bit = java.util.Arrays.asList(SEG14_NAMES).indexOf(s);
                if (bit < 0) throw new IllegalStateException("segment " + s + " of " + ch);
                mask |= 1 << bit;
            }
        }
        if (ch == '\u00b0') {
            seg14Degree = mask;
            return;
        }
        SEG14_ASCII[ch] = mask;
        SEG14_DEFINED[ch] = true;
    }
}
