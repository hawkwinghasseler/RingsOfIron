package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The crew (the classic {@code CrewWindow}). On the ship's console, Context's M2 Crew: the roster with
 * each one's role, skills, condition, morale and wage, their EVA orders, suits at battle stations and
 * EVA for the whole ship, and the prisoners with what can be done with them here. On a station's,
 * M3 Hire: who is looking for a berth, with the berths and the week's wages. Letting a crew member go
 * can't be undone, so its key is guarded.
 */
final class CrewPage extends ListPage {
    private static final int HEAD = 0, MEMBER = 1, SUITS = 2, EVA = 3, ALONGSIDE = 4, PRISONER = 5, HIRE = 6;
    private static final String[] HEADS = {"CREW", "ORDERS", "PRISONERS", "LOOKING FOR A BERTH"};
    /** True on a station's console (the hire), false on the ship's (the roster). */
    private final boolean hire;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();

    CrewPage(boolean hire) {
        super(hire ? "HIRE" : "CREW", hire ? "HIRE" : "CREW", ClassicWindow.CREW);
        this.hire = hire;
    }

    private CrewSheet sheet() {
        return b.crew;
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
            for (int i = 0; i < c.hires.size; i++) add(HIRE, i);
        } else {
            add(HEAD, 0);
            for (int i = 0; i < c.crew.size; i++) add(MEMBER, i);
            if (!c.suits.isEmpty() || !c.eva.isEmpty()) {
                add(HEAD, 1);
                if (!c.suits.isEmpty()) add(SUITS, 0);
                if (!c.eva.isEmpty()) add(EVA, 0);
            }
            if (c.prisoners.size > 0 || c.prisonersAlongside) {
                add(HEAD, 2);
                if (c.prisonersAlongside) add(ALONGSIDE, 0);
                for (int i = 0; i < c.prisoners.size; i++) add(PRISONER, i);
            }
        }
        keepSelection();
    }

    private void add(int kind, int ref) {
        kinds.add(kind);
        refs.add(ref);
    }

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) != HEAD;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
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

    /** "Gun 62  Eng 50  Mar 50  Med 40" with one space between, to fit its column. */
    static String skills(String s) {
        return s.replace("  ", " ");
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
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
                guardedKey("LET GO", m.dismissable ? null : "CREW ARE LET GO ONLY WHILE DOCKED");
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
}
