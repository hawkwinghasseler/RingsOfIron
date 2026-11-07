package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The crew (the classic {@code CrewWindow}). On the ship's console, Context's M2 Crew: the roster with
 * each one's role, skills, condition, morale and wage, their EVA orders, suits at battle stations and
 * EVA for the whole ship, and the prisoners with what can be done with them here. On a station's,
 * M3 Hire: who is looking for a berth, with the berths and the week's wages. Letting a crew member go
 * can't be undone, so its key is guarded.
 *
 * <p>In a side panel (the Stations spec's slate and Station link, 10.3 and 10.4) each person takes two
 * lines: the name, role and morale, and under it, dim, the skills and the condition, which lights when
 * it is anything but OK. The selected one's wage and record, which the lines have no room for, are
 * typed on a sheet under the tube.
 */
final class CrewPage extends ListPage {
    /** Row kinds; in a side panel a person's or an order's second line is a row of its own ({@code DETAIL}). */
    private static final int HEAD = 0, MEMBER = 1, SUITS = 2, EVA = 3, ALONGSIDE = 4, PRISONER = 5, HIRE = 6, DETAIL = 7;
    private static final String[] HEADS = {"CREW", "ORDERS", "PRISONERS", "LOOKING FOR A BERTH"};
    private static final String ALONGSIDE_TEXT = "The taken ship alongside has prisoners to bring aboard";
    /** True on a station's console (the hire), false on the ship's (the roster). */
    private final boolean hire;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();
    /** Whether it drew in a side panel last frame: two lines for each person. */
    private boolean narrow;
    private final Array<String> lines = new Array<>(), wrapped = new Array<>(), part = new Array<>();
    private String wrappedFor;
    private float wrappedW;

    CrewPage(boolean hire) {
        super(hire ? "HIRE" : "CREW", hire ? "HIRE" : "CREW", ClassicWindow.CREW);
        this.hire = hire;
    }

    private CrewSheet sheet() {
        return b.crew;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    String windowKey() {
        // I opens the slate's crew, never the hiring hall
        return hire ? null : super.windowKey();
    }

    @Override
    boolean offered() {
        if (b == null || !sheet().isOpen()) return false;
        return !hire || !sheet().stationName.isEmpty();
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        CrewSheet c = sheet();
        kinds.clear();
        refs.clear();
        if (hire) {
            add(HEAD, 3);
            for (int i = 0; i < c.hires.size; i++) add2(HIRE, i);
        } else {
            add(HEAD, 0);
            for (int i = 0; i < c.crew.size; i++) add2(MEMBER, i);
            if (!c.suits.isEmpty() || !c.eva.isEmpty()) {
                add(HEAD, 1);
                if (!c.suits.isEmpty()) add(SUITS, 0);
                // The EVA order's sentence runs to two lines in a side panel
                if (!c.eva.isEmpty()) add2(EVA, 0);
            }
            if (c.prisoners.size > 0 || c.prisonersAlongside) {
                add(HEAD, 2);
                if (c.prisonersAlongside) add2(ALONGSIDE, 0);
                for (int i = 0; i < c.prisoners.size; i++) add2(PRISONER, i);
            }
        }
        keepSelection();
    }

    private void add(int kind, int ref) {
        kinds.add(kind);
        refs.add(ref);
    }

    /** A row with a second line under it in a side panel. */
    private void add2(int kind, int ref) {
        add(kind, ref);
        if (narrow) add(DETAIL, ref);
    }

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) != HEAD && kinds.get(i) != DETAIL;
    }

    @Override
    boolean belongsAbove(int i) {
        return kinds.get(i) == DETAIL;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        if (narrow) {
            if (kinds.get(i) == DETAIL) second(p, i - 1, r, s);
            else first(p, i, r, s);
            return;
        }
        CrewSheet c = sheet();
        int ref = refs.get(i);
        boolean wide = L.cols >= 124;
        switch (kinds.get(i)) {
            case HEAD:
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 0) {
                    if (c.crew.size == 0) L.at(p, 9, r, "No crew aboard", DIM);
                    else {
                        L.at(p, 21, r, "ROLE", DIM);
                        L.at(p, 34, r, "SKILLS", DIM);
                        L.at(p, 63, r, "CONDITION", DIM);
                        L.at(p, 87, r, "MORALE", DIM);
                        if (wide) L.at(p, 95, r, "WAGE", DIM);
                        if (wide) L.at(p, 109, r, "RECORD", DIM);
                    }
                } else if (ref == 3) {
                    if (c.hires.size == 0) L.at(p, 22, r, "No one here wants a berth", DIM);
                    else {
                        L.at(p, 22, r, "ROLE", DIM);
                        L.at(p, 39, r, "SKILLS", DIM);
                        L.at(p, 68, r, "TERMS", DIM);
                    }
                }
                return;
            case MEMBER: {
                Member m = c.crew.get(ref);
                put(p, s, 2, r, Ledger.cut(m.name, 18), AMBER);
                put(p, s, 21, r, Ledger.cut(m.role, 12), AMBER);
                put(p, s, 34, r, Ledger.cut(skills(m.skills), 27), AMBER);
                put(p, s, 63, r, Ledger.cut(m.condition, 22), AMBER);
                put(p, s, 87, r, Ledger.cut(m.morale.replace("Morale ", ""), 6), AMBER);
                if (wide) put(p, s, 95, r, Ledger.cut(m.wage, 12), AMBER);
                if (wide) put(p, s, 109, r, Ledger.cut(m.history, L.cols - 110), AMBER);
                return;
            }
            case SUITS:
                put(p, s, 2, r, Ledger.cut(c.suits, L.cols - 4), AMBER);
                return;
            case EVA:
                put(p, s, 2, r, Ledger.cut(c.eva, L.cols - 4), AMBER);
                return;
            case ALONGSIDE:
                put(p, s, 2, r, "The taken ship alongside has prisoners to bring aboard", AMBER);
                return;
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                put(p, s, 2, r, Ledger.cut(pr.name, 18), AMBER);
                put(p, s, 21, r, Ledger.cut(pr.detail, 40), AMBER);
                put(p, s, 63, r, Ledger.cut(pr.condition, 22), AMBER);
                put(p, s, 87, r, pr.offersToJoin ? "Offers to join" : "", AMBER);
                return;
            }
            case HIRE: {
                Hire h = c.hires.get(ref);
                put(p, s, 2, r, Ledger.cut(h.name, 19), AMBER);
                put(p, s, 22, r, Ledger.cut(h.role, 16), AMBER);
                put(p, s, 39, r, Ledger.cut(skills(h.skills), 27), AMBER);
                put(p, s, 68, r, Ledger.cut(h.terms, L.cols - 69), AMBER);
                return;
            }
            default:
        }
    }

    /** A row's first line in a side panel: the name, the role and the morale; an order's sentence. */
    private void first(YardPaint p, int i, int r, boolean s) {
        CrewSheet c = sheet();
        int ref = refs.get(i), right = L.cols - 1;
        switch (kinds.get(i)) {
            case HEAD:
                if (ref == 3 && c.hires.size == 0) {
                    L.at(p, 1, r, "NO ONE HERE WANTS A BERTH", DIM);
                    return;
                }
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 0) {
                    if (c.crew.size == 0) L.at(p, 9, r, "No crew aboard", DIM);
                    else {
                        L.at(p, 21, r, "ROLE", DIM);
                        L.right(p, right, r, "MORALE", DIM);
                    }
                } else if (ref == 3) L.right(p, right, r, "TERMS", DIM);
                return;
            case MEMBER: {
                Member m = c.crew.get(ref);
                String morale = m.morale.replace("Morale ", "");
                put(p, s, 2, r, Ledger.cut(m.name, 18), AMBER);
                put(p, s, 21, r, Ledger.cut(m.role, Math.max(4, right - 22 - morale.length())), AMBER);
                putRight(p, s, right, r, morale, AMBER);
                return;
            }
            case SUITS:
                put(p, s, 2, r, Ledger.cut(c.suits, L.cols - 3), AMBER);
                return;
            case EVA:
                put(p, s, 2, r, c.eva.substring(0, breakAt(c.eva, L.cols - 3)), AMBER);
                return;
            case ALONGSIDE:
                put(p, s, 2, r, ALONGSIDE_TEXT.substring(0, breakAt(ALONGSIDE_TEXT, L.cols - 3)), AMBER);
                return;
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                put(p, s, 2, r, Ledger.cut(pr.name, 18), AMBER);
                put(p, s, 21, r, Ledger.cut(pr.detail, Math.max(4, right - 21)), AMBER);
                return;
            }
            case HIRE: {
                // The terms where the crew's morale is: what a berth costs is what decides it
                Hire h = c.hires.get(ref);
                String terms = Ledger.cut(h.terms, Math.max(8, right - 14));
                put(p, s, 2, r, Ledger.cut(h.name, Math.max(6, right - 3 - terms.length())), AMBER);
                putRight(p, s, right, r, terms, AMBER);
                return;
            }
            default:
        }
    }

    /** The second line of row {@code i}, dim: the skills and the condition, or the rest of a sentence. */
    private void second(YardPaint p, int i, int r, boolean s) {
        CrewSheet c = sheet();
        int ref = refs.get(i), right = L.cols - 1;
        switch (kinds.get(i)) {
            case MEMBER: {
                Member m = c.crew.get(ref);
                String cond = Ledger.cut(m.condition, Math.max(8, right - 14));
                putRight(p, s, right, r, cond, ok(m.condition) ? DIM : AMBER);
                put(p, s, 4, r, Ledger.cut(skills(m.skills), Math.max(4, right - 6 - cond.length())), DIM);
                return;
            }
            case EVA:
                put(p, s, 4, r, Ledger.cut(rest(c.eva, L.cols - 3), L.cols - 5), DIM);
                return;
            case ALONGSIDE:
                put(p, s, 4, r, Ledger.cut(rest(ALONGSIDE_TEXT, L.cols - 3), L.cols - 5), DIM);
                return;
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                String join = pr.offersToJoin ? "Offers to join" : "";
                putRight(p, s, right, r, join, AMBER);
                put(p, s, 4, r, Ledger.cut(pr.condition, Math.max(4, right - 6 - join.length())), ok(pr.condition) ? DIM : AMBER);
                return;
            }
            case HIRE: {
                Hire h = c.hires.get(ref);
                String role = Ledger.cut(h.role, 12), sk = Ledger.cut(skills(h.skills), Math.max(8, right - 6 - role.length()));
                put(p, s, 4, r, role, DIM);
                putRight(p, s, right, r, sk, DIM);
                return;
            }
            default:
        }
    }

    /** Whether a condition is nominal, so dark on the tube: "OK 100%". */
    private static boolean ok(String condition) {
        return condition.startsWith("OK");
    }

    /** Where {@code t} breaks to fit {@code n} characters: at its last space within them, or at n. */
    static int breakAt(String t, int n) {
        if (t.length() <= n) return t.length();
        int k = t.lastIndexOf(' ', n);
        return k > 0 ? k : n;
    }

    /** What is left of {@code t} after its first {@code n} characters' worth, for a second line. */
    static String rest(String t, int n) {
        return t.substring(breakAt(t, n)).trim();
    }

    /** "Gun 62  Eng 50  Mar 50  Med 40" with one space between, to fit its column. */
    static String skills(String s) {
        return s.replace("  ", " ");
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    private void putRight(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.revRight(p, c, r, t);
        else L.right(p, c, r, t, ink);
    }

    @Override
    void keys() {
        if (sel < 0) return;
        CrewSheet c = sheet();
        int ref = refs.get(sel);
        switch (kinds.get(sel)) {
            case MEMBER: {
                Member m = c.crew.get(ref);
                if (!m.evaAction.isEmpty()) softKey(Fmt.upper(m.evaAction), null);
                // It can't be undone: the person leaves, and doesn't go back on the station's hiring list
                guardedKey("LET GO", m.dismissable ? null : "CREW ARE LET GO ONLY WHILE DOCKED",
                        "Let " + m.name + " go? Press again: " + m.name + " leaves the ship for good");
                return;
            }
            case SUITS:
                softKey(c.suits.contains(": on") ? "TURN OFF" : "TURN ON", null);
                return;
            case EVA:
                softKey(c.eva.contains("held in") ? "LET OUT" : "HOLD IN", null);
                return;
            case ALONGSIDE:
                softKey("TAKE ABOARD", null);
                return;
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                softKey(pr.option.isEmpty() ? "SETTLE" : Fmt.upper(pr.option), pr.option.isEmpty() ? "ONLY AT A STATION" : null);
                if (pr.offersToJoin) softKey("RECRUIT", null);
                return;
            }
            case HIRE:
                softKey("HIRE", null);
                return;
            default:
        }
    }

    @Override
    void press(int k) {
        CrewProvider provider = b.data == null ? null : b.data.crew;
        if (provider == null || sel < 0) return;
        CrewSheet c = sheet();
        int ref = refs.get(sel);
        String said = null;
        switch (kinds.get(sel)) {
            case MEMBER: {
                Member m = c.crew.get(ref);
                boolean eva = !m.evaAction.isEmpty() && k == 0;
                said = eva ? provider.evaOrder(m.id) : provider.dismiss(m.id);
                break;
            }
            case SUITS:
                said = provider.toggleSuits();
                break;
            case EVA:
                said = provider.toggleEva();
                break;
            case ALONGSIDE:
                said = provider.takePrisoners();
                break;
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                said = k == 0 ? provider.settlePrisoner(pr.id) : provider.recruit(pr.id);
                break;
            }
            case HIRE: {
                // The game answers "Hired": the feed names who
                Hire h = c.hires.get(ref);
                said = provider.hire(h.key);
                if ("Hired".equals(said)) said = "Hired " + h.name + ", " + h.role.toLowerCase(java.util.Locale.ROOT);
                break;
            }
            default:
        }
        say(said);
    }

    @Override
    String empty() {
        return hire ? "NO ONE HERE WANTS A BERTH" : "NO CREW";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean was = narrow;
        narrow = w < 700f;
        if (narrow != was && b != null) refresh(b);
        if (narrow) {
            drawNarrow(p, hits, x, y, w, h);
            return;
        }
        boolean liv = hire;
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        int cols = Math.max(90, Ledger.colsIn(bw - 42f)), rows = Math.max(12, Ledger.rowsIn(bh - 90f));
        L.set(bx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, hire ? "HIRE" : "CREW", Ledger.cut(Fmt.upper(sheet().summary), Math.max(10, cols - 12)));
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        // The selected crew member's record, typed on a label strip under the tube
        if (!hire && sel >= 0 && kinds.get(sel) == MEMBER) {
            Member m = sheet().crew.get(refs.get(sel));
            if (!m.history.isEmpty() && L.cols < 124) {
                float ty = L.y + L.h + 40f;
                p.paper(L.x, ty, Math.min(L.w, 520f), 24f);
                p.typed(Ledger.cut(m.name + ": " + m.history + ", " + m.wage, 64), L.x + 10f, ty + 17f, 13f, Paper.INK);
            }
        }
    }

    /**
     * In a side panel: the people on the tube, two lines each, the soft keys under it, and the selected
     * one typed on a sheet under them: the whole of the skills, the wage and the record for the crew,
     * the terms for a hand looking for a berth, what can be done with a prisoner.
     */
    private void drawNarrow(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean liv = hire;
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        float sheetH = Math.min(124f, Math.max(0f, bh - 330f));
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(8, Ledger.rowsIn(bh - 42f - sheetH));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, hire ? "HIRING" : "CREW", Fmt.clauses(Fmt.upper(sheet().summary), Math.max(10, cols - 9)));
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        float sy = L.y + L.h + 30f;
        if (sheetH >= 80f) card(p, L.x, sy, L.w, by + bh - 8f - sy, liv);
    }

    /** The selected row's particulars, a line each, for the sheet; none for an order. */
    private void particulars(Array<String> out) {
        out.clear();
        if (sel < 0 || sel >= kinds.size) return;
        CrewSheet c = sheet();
        int ref = refs.get(sel);
        switch (kinds.get(sel)) {
            case MEMBER: {
                Member m = c.crew.get(ref);
                out.add(m.name + ", " + m.role.toLowerCase(java.util.Locale.ROOT));
                out.add(m.skills);
                out.add(m.condition + ", " + m.morale.toLowerCase(java.util.Locale.ROOT));
                out.add("Wage: " + m.wage + ". Record: " + m.history);
                return;
            }
            case PRISONER: {
                Prisoner pr = c.prisoners.get(ref);
                out.add(pr.name + ", " + pr.detail);
                out.add(pr.condition);
                if (!pr.option.isEmpty()) out.add("Here: " + pr.option.toLowerCase(java.util.Locale.ROOT)
                        + (pr.offersToJoin ? ", or recruit: they offer to join" : ""));
                else if (pr.offersToJoin) out.add("Offers to join the crew");
                return;
            }
            case HIRE: {
                Hire h = c.hires.get(ref);
                out.add(h.name + ", " + h.role.toLowerCase(java.util.Locale.ROOT));
                out.add(h.skills);
                out.add(h.terms);
                return;
            }
            default:
        }
    }

    /** The selected one typed on a fanfold sheet; nothing for an order, whose line says it all. */
    private void card(YardPaint p, float x, float y, float w, float h, boolean liv) {
        particulars(lines);
        if (lines.size == 0 || h < 60f) return;
        Paper.fanfold(p, x, y, w, h, liv ? BigConsole.LIV_FOOT : Yard.ENAMEL_BOTTOM);
        float tx = x + 26f, tw = w - 52f;
        String text = String.join("\n", lines);
        if (!text.equals(wrappedFor) || tw != wrappedW) {
            wrappedFor = text;
            wrappedW = tw;
            wrapped.clear();
            for (int i = 0; i < lines.size; i++) {
                Paper.wrap(p, lines.get(i), tw, Paper.SIZE, part);
                wrapped.addAll(part);
            }
        }
        p.text(YardFonts.Face.PAPER, 12f, hire ? "LOOKING FOR A BERTH" : kinds.get(sel) == PRISONER ? "PRISONER" : "CREW RECORD",
                tx, y + 22f, Paper.FADED);
        float ly = y + 30f + 13f;
        for (int i = 0; i < wrapped.size && ly < y + h - 6f; i++, ly += Paper.LINE) p.typed(wrapped.get(i), tx, ly, Paper.SIZE, Paper.INK);
    }
}
