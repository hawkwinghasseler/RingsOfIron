package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The Station link's Infirmary (functional-stations 10.3): who of the crew it is treating now, each with
 * their condition as the roster has it, and what it has treated and billed since the ship docked. It
 * takes no orders: the station's medics treat whoever comes aboard hurt. It stands for the crew roster,
 * so the roster's key closes it.
 */
final class InfirmaryPage extends SectionPage {
    static final int PATIENT = ITEM;

    private Boards boards;

    InfirmaryPage() {
        super("INFIRMARY", ClassicWindow.CREW, "");
        keys.define(0, 0);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && b.infirmary;
    }

    @Override
    void build(Boards b, HudData d) {
        boards = b;
        int w = cells - 1;
        head("TREATING NOW");
        for (int i = 0; i < b.treating.size; i++) {
            String name = b.treating.get(i);
            Member m = member(b, name);
            add(PATIENT, i, cols.clear().left(name.toUpperCase(Locale.ROOT), w - 13).gap(1)
                    .left(m == null ? "" : shortCondition(m.condition), 12).toString());
        }
        if (b.treating.size == 0) note("NOBODY ABOARD NEEDS TREATING");
        head("SINCE DOCKING");
        note(b.treated == 0 ? "NOBODY TREATED YET"
                : (b.treated == 1 ? "1 TREATED, " : b.treated + " TREATED, ") + Glass.thousands(b.treatedCost) + " CR BILLED");
    }

    /** The crew member named {@code name}, or null. */
    static Member member(Boards b, String name) {
        for (int i = 0; i < b.crew.crew.size; i++) if (b.crew.crew.get(i).name.equalsIgnoreCase(name)) return b.crew.crew.get(i);
        return null;
    }

    /** "Critical wound, 4:10 to live" to "CRITICAL"; "Light wound 100%" to "LIGHT WOUND". */
    static String shortCondition(String c) {
        if (c == null) return "";
        String s = c.toUpperCase(Locale.ROOT);
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ',' || Character.isDigit(ch)) {
                cut = i;
                break;
            }
        }
        s = s.substring(0, cut).trim();
        return s.startsWith("CRITICAL") ? "CRITICAL" : s;
    }

    @Override
    void keysFor(int focus) {
    }

    @Override
    void detailFor(int focus) {
        if (boards == null || kindAt(focus) != PATIENT) return;
        String name = boards.treating.get(ref.get(focus));
        Member m = member(boards, name);
        tell(m == null || m.condition.isEmpty() ? name : name + ": " + m.condition);
    }

    @Override
    void press(int k, int focus) {
    }
}
