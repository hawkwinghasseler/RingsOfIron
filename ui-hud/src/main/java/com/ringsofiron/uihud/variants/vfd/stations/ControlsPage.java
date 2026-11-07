package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpSection;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Ink;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * One side of the controls card ({@link ControlsCard}; mockup card-controls): the game's controls
 * panel, every key by where it applies, in columns of printed sections, each key a printed cap and what
 * it does wrapped in its column. On the Keyboard, the sections that hold where the captain is come
 * first, marked HERE, and the head names them; the keys that work anywhere come next, this HUD's own
 * with them (the master lamps, a panel's pages, Esc), then its MFD keys for a command seat; the rest
 * follow in the panel's order. The game's keys for its windows say what they open here (the slate, the
 * Station link, the Plot). A section runs on into the next column where it won't fit, and the card into
 * pages when the columns run out (Pg Up, Pg Dn and the wheel turn them). The Gamepad has the pad's
 * buttons alone.
 */
final class ControlsPage extends Page {
    /** A column's width and a hint's line, the wrapped lines' pitch, where the words start at the least. */
    static final float COL = 291f, LH = 20f, WRAP = 16f, WORDS = 92f;
    static final float KEY_H = 18f, KEY_SIZE = 12f, TEXT = 13f;
    /** From a section's title to its first key's line, and from its last key's line on to the next title. */
    static final float TITLE_H = 31f, AFTER = 34f;
    static final String ANYWHERE = "Anywhere", SEATED = "Seated: the MFDs";
    static final int MAX_PAGES = 9;
    /** A section split over two columns leaves at least this many of its lines in the first. */
    static final int KEEP = 3;

    /** The card's word on its index tab. */
    final String word;
    private final boolean pad;
    private final HelpFrame help = new HelpFrame();
    /** The sections in the order the card prints them. */
    final Array<HelpSection> order = new Array<>();
    /** This HUD's own keys: those that work anywhere, printed with the game's; and the MFDs' at a command seat. */
    private final HelpFrame own = new HelpFrame();
    private final HelpSection anywhereOwn, seated;
    /** The sections that hold where the captain is, as the head names them: "At the helm, docked". */
    final StringBuilder here = new StringBuilder();
    private final StringBuilder line = new StringBuilder();
    /** The page showing, and how many the sections took when the card was last printed. */
    int page, pages = 1;
    /** The card's face. */
    float x, y, w, h;

    ControlsPage(String word, boolean pad) {
        super(word.toUpperCase(Locale.ROOT), ClassicWindow.HELP, "F1");
        this.word = word;
        this.pad = pad;
        anywhereOwn = own.add(ANYWHERE);
        anywhereOwn.add("O", "while a master lamp flashes: acknowledge it", "");
        anywhereOwn.add("Tab", "a panel's next page (Shift: the one before)", "");
        anywhereOwn.add("Esc", "close what is open, else the menu", "");
        seated = own.add(SEATED);
        seated.add("Alt+1-6", "a station's page on the right MFD, again for its next", "");
        seated.add("Alt+Shift+1-6", "the same on the left MFD", "");
        seated.add("Alt+0", "pin the right MFD's page through alerts (Shift: the left's)", "");
        seated.add("Alt+-", "fold the right MFD (Shift: the left)", "");
    }

    @Override
    void shown() {
        page = 0;
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    void refresh(Boards b, HudData d) {
        help.clear();
        if (d.hints != null) d.hints.collectAll(help);
        seated.here = d.ctx.presence == Presence.HELM || d.ctx.presence == Presence.WEAPONS;
        order.clear();
        here.setLength(0);
        for (HelpSection s : help.sections) {
            if (s.gamepad != pad || !s.here) continue;
            order.add(s);
            named(s.title);
        }
        if (!pad && seated.here) {
            order.add(seated);
            named(seated.title);
        }
        if (here.length() > 0) here.setLength(here.length() - 2);
        for (HelpSection s : help.sections) if (s.gamepad == pad && !s.here && anywhere(s)) order.add(s);
        if (!pad && !seated.here) order.add(seated);
        for (HelpSection s : help.sections) if (s.gamepad == pad && !s.here && !anywhere(s)) order.add(s);
    }

    /** Adds a section's title to the head's "Here: ...". */
    private void named(String title) {
        if (title.isEmpty()) return;
        here.append(here.length() == 0 ? title : Character.toLowerCase(title.charAt(0)) + title.substring(1));
        here.append(", ");
    }

    /** Whether {@code s} is the keys that work anywhere, which this HUD's own join. */
    private boolean anywhere(HelpSection s) {
        return !pad && ANYWHERE.equalsIgnoreCase(s.title);
    }

    @Override
    boolean key(int keycode) {
        if (keycode == Keys.PAGE_DOWN || keycode == Keys.PAGE_UP) {
            turn(keycode == Keys.PAGE_DOWN ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    void scrolled(int amount) {
        turn(amount);
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages - 1, page + by));
    }

    @Override
    void paint(Filament f, float time) {
        float x0 = x + 30f;
        f.print(x0, y + 44f, pad ? "Gamepad" : "Controls", f.o().size(24f).track(0f));
        if (order.size > 0) {
            pages = flow(f, -1);
            if (page >= pages) page = pages - 1;
        }
        // The head's line stops short of the page count at its right: the sentence after "Here" goes first
        float room = x + w - 30f - (pages > 1 ? pageTurnWidth(f) + 16f : 0f) - x0;
        String sub = pad ? "The pad's buttons, wherever you are." : "Your keys by seat and situation, where you are first.";
        float hw = 0f;
        if (!pad && here.length() > 0) hw = f.print(x0, y + 68f, "Here: " + here + ".", f.o().size(TEXT)) + 6f;
        if (hw + f.printWidth(sub, TEXT, 0.02f) <= room) f.print(x0 + hw, y + 68f, sub, f.o().size(TEXT).ink(Ink.CARD_SOFT));
        f.ink(x0, y + 84f, w - 60f, 1.4f, 0f, Ink.CARD_INK, 1f);
        if (order.size == 0) {
            f.print(x0, y + 122f, "No controls to list.", f.o().size(TEXT).ink(Ink.CARD_SOFT));
            return;
        }
        flow(f, page);
        if (pages > 1) pageTurn(f);
    }

    /** How wide the head's page count and its keys print. */
    private float pageTurnWidth(Filament f) {
        float dn = f.printedKeyWidth("PG DN", KEY_H, KEY_SIZE), up = f.printedKeyWidth("PG UP", KEY_H, KEY_SIZE);
        return dn + 4f + up + 10f + f.printWidth("Page " + (page + 1) + " of " + pages, TEXT, 0.02f);
    }

    /** The head's page count and its keys, at the right: "Page 1 of 2", Pg Up, Pg Dn. */
    private void pageTurn(Filament f) {
        float rx = x + w - 30f, ky = y + 68f - 13f;
        float dn = f.printedKeyWidth("PG DN", KEY_H, KEY_SIZE), up = f.printedKeyWidth("PG UP", KEY_H, KEY_SIZE);
        f.printedKey(rx - dn, ky, "PG DN", KEY_H, KEY_SIZE);
        f.printedKey(rx - dn - 4f - up, ky, "PG UP", KEY_H, KEY_SIZE);
        f.print(rx - dn - 4f - up - 10f, y + 68f, "Page " + (page + 1) + " of " + pages, f.o().size(TEXT).right());
    }

    /**
     * Lays the sections down the columns and on over the pages, printing the ones on page {@code show}
     * (none for -1). A section runs on into the next column under its title again, leaving at least
     * {@link #KEEP} of its lines before the break and never one alone after it. Returns how many pages
     * they take.
     */
    private int flow(Filament f, int show) {
        int cols = Math.max(1, (int) Math.floor((w - 60f) / COL));
        float x0 = x + 30f, top = y + 122f, bottom = y + h - 20f;
        int pg = 0, c = 0;
        float sy = top;
        for (int i = 0; i < order.size; i++) {
            HelpSection s = order.get(i);
            boolean anywhere = anywhere(s);
            int n = s.hints.size + (anywhere ? anywhereOwn.hints.size : 0);
            if (n == 0) continue;
            int fit = fitting(f, s, anywhere, sy, bottom);
            if (sy > top && fit < n && fit < KEEP) {
                if (++c >= cols) {
                    c = 0;
                    pg++;
                }
                sy = top;
                fit = fitting(f, s, anywhere, sy, bottom);
            }
            // One line left over would stand alone under its title: take another with it
            int breakAt = n - fit == 1 && fit > KEEP ? fit - 1 : -1;
            if (pg == show) title(f, x0 + c * COL, sy, s, false);
            float ry = sy + TITLE_H;
            for (int j = 0; j < n; j++) {
                KeyHint k = key(s, anywhere, j);
                float more = hint(f, 0f, 0f, k, false);
                if (j == breakAt || ry + more > bottom && ry > top + TITLE_H) {
                    // The rest of it in the next column, under its title again
                    if (++c >= cols) {
                        c = 0;
                        pg++;
                    }
                    sy = top;
                    if (pg == show) title(f, x0 + c * COL, sy, s, true);
                    ry = sy + TITLE_H;
                    breakAt = -1;
                }
                if (pg == show) hint(f, x0 + c * COL, ry, k, true);
                ry += more + LH;
            }
            sy = ry - LH + AFTER;
            if (pg >= MAX_PAGES - 1 && sy > bottom) break;
        }
        return Math.min(MAX_PAGES, pg + 1);
    }

    /** How many of section {@code s}'s lines fit by {@code bottom} with its title at {@code sy}. */
    private int fitting(Filament f, HelpSection s, boolean anywhere, float sy, float bottom) {
        int n = s.hints.size + (anywhere ? anywhereOwn.hints.size : 0), fit = 0;
        float ry = sy + TITLE_H;
        for (int j = 0; j < n; j++) {
            float more = hint(f, 0f, 0f, key(s, anywhere, j), false);
            if (ry + more > bottom) break;
            fit++;
            ry += more + LH;
        }
        return fit;
    }

    /** Section {@code s}'s key {@code j}, this HUD's own after the game's where it works anywhere. */
    private KeyHint key(HelpSection s, boolean anywhere, int j) {
        return j < s.hints.size || !anywhere ? s.hints.get(j) : anywhereOwn.hints.get(j - s.hints.size);
    }

    /** A section's title from {@code sx, sy} (its baseline), HERE on a dark tag; CONT. where it runs on. */
    private void title(Filament f, float sx, float sy, HelpSection s, boolean cont) {
        String title = s.title.toUpperCase(Locale.ROOT);
        float tw = f.printWidth(title, TEXT, 0.06f);
        if (s.here) {
            String tag = cont ? "HERE, CONT." : "HERE";
            float gw = f.printWidth(tag, 12f, 0.12f);
            f.ink(sx - 6f, sy - 15f, tw + gw + 28f, 21f, 3f, Ink.CARD_INK, 1f);
            f.print(sx, sy, title, f.o().size(TEXT).track(0.06f).ink(Ink.CARD));
            f.print(sx + tw + 10f, sy, tag, f.o().size(12f).track(0.12f).ink(Ink.CARD_TAG));
        } else {
            f.print(sx, sy, title, f.o().size(TEXT).track(0.06f));
            if (cont) f.print(sx + tw + 10f, sy, "CONT.", f.o().size(12f).track(0.12f).ink(Ink.CARD_SOFT));
        }
        f.ink(sx, sy + 8f, COL - 21f, 0.8f, 0f, Ink.CARD_INK, 0.35f);
    }

    /**
     * One hint from {@code hx}, its baseline {@code hy}: its keys' caps, then what they do, wrapped at a
     * space when it runs past the column. Returns how much taller than one line it prints.
     */
    private float hint(Filament f, float hx, float hy, KeyHint k, boolean draw) {
        float kx = hx;
        String keys = k.key.toUpperCase(Locale.ROOT);
        // "BACK+A/B/X/Y": a cap for each key, a printed plus between a held key and the rest
        int start = 0;
        for (int i = 0; i <= keys.length(); i++) {
            boolean end = i == keys.length();
            char ch = end ? 0 : keys.charAt(i);
            if (!end && ch != '/' && ch != '+') continue;
            String cap = keys.substring(start, i).trim();
            // "/" alone is a key of its own, as "," and "." are
            if (cap.isEmpty() && !end && ch == '/' && (i + 1 >= keys.length() || keys.charAt(i + 1) == '/')) {
                cap = "/";
                i++;
            }
            if (!cap.isEmpty()) {
                kx += (draw ? f.printedKey(kx, hy - 13f, cap, KEY_H, KEY_SIZE) : f.printedKeyWidth(cap, KEY_H, KEY_SIZE)) + 3f;
            }
            if (!end && ch == '+') {
                if (draw) f.print(kx, hy, "+", f.o().size(TEXT).ink(Ink.CARD_SOFT));
                kx += f.printWidth("+", TEXT, 0.02f) + 3f;
            }
            start = i + 1;
        }
        float tx = Math.max(kx + 6f, hx + WORDS), room = hx + COL - 14f - tx;
        String words = label(k);
        int lines = 0;
        line.setLength(0);
        int at = 0;
        while (at <= words.length()) {
            int sp = words.indexOf(' ', at);
            if (sp < 0) sp = words.length();
            String wd = words.substring(at, sp);
            int before = line.length();
            if (before > 0) line.append(' ');
            line.append(wd);
            if (before > 0 && f.printWidth(line, TEXT, 0.02f) > room) {
                line.setLength(before);
                if (draw) f.print(tx, hy + lines * WRAP, line, f.o().size(TEXT));
                lines++;
                line.setLength(0);
                line.append(wd);
            }
            at = sp + 1;
        }
        if (line.length() > 0 && draw) f.print(tx, hy + lines * WRAP, line, f.o().size(TEXT));
        return lines * WRAP;
    }

    /**
     * What a hint does, as this HUD has it: the game's keys for its windows open the slate, the Station
     * link and the Plot here. Only their plain words change; one that says why it shows now keeps them.
     */
    static String label(KeyHint k) {
        switch (k.action) {
            case "CREW":
                if ("crew".equals(k.label)) return "the slate: the ship, crew, medical, EVA, contracts, career, log";
                if ("crew, hire".equals(k.label)) return "the slate's crew; hiring is on the Station link";
                return k.label;
            case "MISSIONS":
                if ("missions held".equals(k.label)) return "the contracts held, on the slate";
                if ("mission board".equals(k.label)) return "the contract board, on the Station link";
                return k.label;
            case "LOG":
                return "notice log".equals(k.label) ? "the slate's log" : k.label;
            case "BOARD":
                return "boarding".equals(k.label) ? "boarding, on the slate" : k.label;
            case "TRADE":
                return "market".equals(k.label) ? "the market, on the Station link" : k.label;
            case "YARD":
                return "yard".equals(k.label) ? "the yard, on the Station link" : k.label;
            case "MAP":
                return "map".equals(k.label) ? "the Plot: the orbit map, the plan, the targets" : k.label;
            default:
                return k.label;
        }
    }
}
