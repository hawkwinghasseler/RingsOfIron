package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * A console panel's page that is read rather than worked down: a column of facts, each a name and what it
 * says, under headings, read again at each refresh (a few times a second), its own soft keys under the
 * tube. The helm's tabs are these: their orders are given seated, by key.
 */
abstract class FactsPage extends ListPage {
    static final int FACT = 0, HEAD = 1, TEXT = 2;

    /** A row: a fact (a name and what it says), a heading with a word at its right, or a line of text. */
    static final class Fact {
        int kind;
        String name = "", value = "";
        Color ink = AMBER;
    }

    final Array<Fact> facts = new Array<>();
    private final Array<Fact> pool = new Array<>();
    /** The column a fact's value starts at. */
    int valueCol = 11;
    /** The tube's columns as last drawn, to break long values at refresh; a side panel's until then. */
    int cols = 45;

    FactsPage(String label, String tag) {
        super(label, tag, ClassicWindow.CONSOLE);
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    /** Starts the rows again, for a refresh. */
    final void begin() {
        pool.addAll(facts);
        facts.clear();
    }

    private Fact add(int kind, String name, String value, Color ink) {
        Fact f = pool.size > 0 ? pool.pop() : new Fact();
        f.kind = kind;
        f.name = name == null ? "" : name;
        f.value = value == null ? "" : value;
        f.ink = ink;
        facts.add(f);
        return f;
    }

    /** A fact; a value too long for its row runs on to the next, under itself, broken at a space. */
    final void fact(String name, String value, Color ink) {
        int room = Math.max(8, cols - valueCol - 1);
        String rest = value == null ? "" : value;
        add(FACT, name, GunPage.wrapped(rest, room, 0), ink);
        for (int n = 0; n < 2 && rest.length() > room; n++) {
            int at = rest.lastIndexOf(' ', room);
            rest = rest.substring(at <= 0 ? room : at).trim();
            add(FACT, "", GunPage.wrapped(rest, room, 0), ink);
        }
    }

    final void head(String name, String right) {
        add(HEAD, name, right, DIM);
    }

    final void text(String text, Color ink) {
        add(TEXT, "", text, ink);
    }

    /** The value of the fact named {@code name}, or null: for the tests. */
    final String value(String name) {
        for (int i = 0; i < facts.size; i++) {
            if (facts.get(i).kind != FACT || !facts.get(i).name.equals(name)) continue;
            StringBuilder v = new StringBuilder(facts.get(i).value);
            for (int k = i + 1; k < facts.size && facts.get(k).kind == FACT && facts.get(k).name.isEmpty(); k++) v.append(' ').append(facts.get(k).value);
            return v.toString();
        }
        return null;
    }

    /** Whether a line of text says {@code text}: for the tests. */
    final boolean says(String text) {
        for (int i = 0; i < facts.size; i++) if (facts.get(i).kind == TEXT && facts.get(i).value.equals(text)) return true;
        return false;
    }

    @Override
    int count() {
        return facts.size;
    }

    @Override
    boolean selectable(int i) {
        return false;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Fact f = facts.get(i);
        switch (f.kind) {
            case HEAD:
                if (i > 0) L.rule(p, r - 1);
                L.at(p, 1, r, Ledger.cut(f.name, L.cols - 2), DIM);
                if (!f.value.isEmpty()) L.right(p, L.cols - 1, r, Ledger.cut(f.value, Math.max(1, L.cols - f.name.length() - 4)), f.ink);
                return;
            case TEXT:
                L.at(p, 1, r, Ledger.cut(f.value, L.cols - 2), f.ink);
                return;
            default:
                L.at(p, 1, r, Ledger.cut(f.name, valueCol - 2), DIM);
                L.at(p, valueCol, r, Ledger.cut(f.value, L.cols - valueCol - 1), f.ink);
        }
    }

    @Override
    void press(int k) {
    }

    /** The page's title and the word at its right, or null. */
    abstract String titleRight();

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        this.cols = cols;
        L.under(p, false);
        title(p, label, titleRight());
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
