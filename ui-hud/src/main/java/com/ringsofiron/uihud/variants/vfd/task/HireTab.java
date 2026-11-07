package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The terminal's Crew (functional-task 7, TRM): who is looking for a berth, each with their role and
 * wage, Enter hiring the one in focus; beside the list their skills and terms, with the crew aboard in
 * that role, the berths and the week's wages against the budget. Where the station has an infirmary,
 * who of the crew it is treating and what it has billed since docking follow, read-only: its medics
 * treat whoever comes aboard hurt. It stands for the crew roster's hiring, so I closes it.
 */
final class HireTab extends SectionTab {
    static final int HIRE = ITEM, PATIENT = ITEM + 1;

    private CrewSheet sheet;
    private Boards boards;
    private final ObjectIntMap<String> roles = new ObjectIntMap<>();

    HireTab() {
        super("CREW", ClassicWindow.CREW, "I");
        keys.define(1, 1);
        keys.set(0, "HIRE", false).cap(0, "ENTER");
    }

    /** A station that hires or treats: the terminal's Crew. */
    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && (b.hiring && !b.crew.stationName.isEmpty() || b.infirmary);
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        boards = b;
        roles.clear();
        for (int i = 0; i < sheet.crew.size; i++) roles.getAndIncrement(sheet.crew.get(i).role.toUpperCase(Locale.ROOT), 0, 1);
        if (b.hiring && !sheet.stationName.isEmpty()) {
            head("LOOKING FOR A BERTH");
            if (!sheet.summary.isEmpty()) note(sheet.summary.toUpperCase(Locale.ROOT));
            for (int i = 0; i < sheet.hires.size; i++) {
                Hire h = sheet.hires.get(i);
                add(HIRE, i, cols.clear().left(h.name.toUpperCase(Locale.ROOT), Math.max(8, cells - 31)).left(h.role.toUpperCase(Locale.ROOT), 12)
                        .right(wage(h.terms), 18).toString());
            }
            if (sheet.hires.size == 0) note("NOBODY HERE IS LOOKING FOR A BERTH");
        }
        if (b.infirmary) {
            head("INFIRMARY: TREATING NOW");
            for (int i = 0; i < b.treating.size; i++) {
                String name = b.treating.get(i);
                Member m = member(b, name);
                add(PATIENT, i, cols.clear().left(name.toUpperCase(Locale.ROOT), Math.max(8, cells - 15)).gap(1)
                        .left(m == null ? "" : shortCondition(m.condition), 13).toString());
            }
            if (b.treating.size == 0) note("NOBODY ABOARD NEEDS TREATING");
            note(b.treated == 0 ? "NOBODY TREATED SINCE DOCKING"
                    : (b.treated == 1 ? "1 TREATED SINCE DOCKING, " : b.treated + " TREATED SINCE DOCKING, ") + Glass.thousands(b.treatedCost) + " CR BILLED");
        }
    }

    /** "250 cr/wk, 250 cr to sign" to "250 CR/WK". */
    static String wage(String terms) {
        if (terms == null) return "";
        int comma = terms.indexOf(',');
        return (comma < 0 ? terms : terms.substring(0, comma)).trim().toUpperCase(Locale.ROOT);
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
        keys.set(0, "HIRE", sheet != null && kindAt(focus) == HIRE);
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null) return;
        int k = kindAt(focus);
        if (k == HIRE) {
            Hire h = sheet.hires.get(ref.get(focus));
            tell(h.name + ", " + h.role);
            if (!h.skills.isEmpty()) tell(h.skills);
            if (!h.terms.isEmpty()) tell(h.terms);
            int n = roles.get(h.role.toUpperCase(Locale.ROOT), 0);
            tell(n == 0 ? "No " + h.role.toLowerCase(Locale.ROOT) + " aboard yet" : n + " " + h.role.toLowerCase(Locale.ROOT) + " aboard now");
            tell(byRole());
            if (!sheet.summary.isEmpty()) tell(sheet.summary);
            if (boards != null && boards.career.present && boards.career.budget > 0) {
                tell("Budget " + Glass.thousands(boards.career.budget) + " cr, the sponsor's pay toward it " + Glass.thousands(boards.career.payIn) + " cr");
            }
        } else if (k == PATIENT && boards != null) {
            String name = boards.treating.get(ref.get(focus));
            Member m = member(boards, name);
            tell(m == null || m.condition.isEmpty() ? name : name + ": " + m.condition);
            tell("The station's medics treat whoever comes aboard hurt, billed to the ship");
        }
    }

    /** "Aboard: 1 engineer, 2 gunners, 1 marine". */
    private String byRole() {
        if (roles.size == 0) return "Nobody aboard";
        StringBuilder sb = new StringBuilder("Aboard: ");
        boolean first = true;
        for (ObjectIntMap.Entry<String> e : roles) {
            if (!first) sb.append(", ");
            sb.append(e.value).append(' ').append(e.key.toLowerCase(Locale.ROOT));
            first = false;
        }
        return sb.toString();
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        if (d == null || d.crew == null || sheet == null || kindAt(focus) != HIRE) return;
        windows.say(d.crew.hire(sheet.hires.get(ref.get(focus)).key));
        windows.refresh();
    }
}
