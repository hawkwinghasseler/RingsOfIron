package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The book's Crew (functional-task 7, BOOK; mockup tk-book): everyone aboard, each with their role,
 * condition, morale, EVA and wage; under them whether EVA is held and suits at battle stations; the
 * prisoners held and what can be done with them here, and those of a taken ship alongside; and, docked
 * at a station that hires, a line to the terminal's Crew. Beside the list, the one in focus in full:
 * skills, condition, morale, wage and history. The keys, walked with ← and → and pressed with Enter:
 * the member's EVA order (send out, call in, let out), Let go (docked, pressed twice), the suits and
 * the EVA hold; a prisoner's settling and recruiting; bringing the prisoners alongside aboard.
 */
final class CrewTab extends SectionTab {
    static final int MEMBER = ITEM, PRISONER = ITEM + 1, ALONGSIDE = ITEM + 2, HIRING = ITEM + 3;
    static final int K0 = 0, K1 = 1, SUITS = 2, HOLD = 3;
    static final float CONFIRM_FOR = 3f;

    private CrewSheet sheet;
    private String station = "";
    /** The member a first press of Let go waits on, and how long it still waits. */
    private long arming = -1;
    private float armLeft;

    CrewTab() {
        super("CREW", ClassicWindow.CREW, "I");
        // Let go is walked to, never landed on
        keys.define(4, 4).guard(K1);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.crew.isOpen();
    }

    @Override
    void shown() {
        arming = -1;
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        if (armLeft > 0f && (armLeft -= delta) <= 0f) {
            arming = -1;
            keysFor(pages.focus());
        }
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        station = windows.terminal.hires(b, d) ? b.station : "";
        int fixed = 1 + 10 + 1 + 12 + 1 + 4 + 1 + 9 + 1 + 10;
        int name = Math.max(6, cells - 1 - fixed);
        head(sheet.summary.isEmpty() ? "ABOARD" : "ABOARD: " + sheet.summary.toUpperCase(Locale.ROOT));
        for (int i = 0; i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            add(MEMBER, i, cols.clear().left(m.name.toUpperCase(Locale.ROOT), name).gap(1).left(m.role.toUpperCase(Locale.ROOT), 10).gap(1)
                    .left(HireTab.shortCondition(m.condition), 12).gap(1).right(morale(m.morale), 4).gap(1)
                    .left(m.evaAction.isEmpty() ? "" : m.evaAction.toUpperCase(Locale.ROOT), 9).gap(1).right(wage(m.wage), 10).toString());
        }
        if (sheet.crew.size == 0) note("NOBODY ABOARD");
        if (!sheet.eva.isEmpty()) note(sheet.eva.toUpperCase(Locale.ROOT));
        if (!sheet.suits.isEmpty()) note(sheet.suits.toUpperCase(Locale.ROOT));
        if (!station.isEmpty()) add(HIRING, 0, "HIRING AT " + station + ": THE TERMINAL'S CREW");
        if (sheet.prisoners.size > 0 || sheet.prisonersAlongside) {
            head(sheet.prisoners.size == 0 ? "PRISONERS" : "PRISONERS: " + sheet.prisoners.size + " HELD");
            for (int i = 0; i < sheet.prisoners.size; i++) {
                Prisoner p = sheet.prisoners.get(i);
                add(PRISONER, i, cols.clear().left(p.name.toUpperCase(Locale.ROOT), Math.max(6, cells - 26)).gap(1)
                        .left(HireTab.shortCondition(p.condition), 12).gap(1).left(p.option.isEmpty() ? "" : p.option.toUpperCase(Locale.ROOT), 10).toString());
            }
            if (sheet.prisonersAlongside) add(ALONGSIDE, 0, "THE TAKEN SHIP'S, ALONGSIDE: BRING THEM ABOARD");
        } else {
            note("PRISONERS: NONE ABOARD");
        }
    }

    /** "Morale 70%" to "70%". */
    static String morale(String m) {
        if (m == null) return "";
        int sp = m.lastIndexOf(' ');
        return (sp < 0 ? m : m.substring(sp + 1)).toUpperCase(Locale.ROOT);
    }

    /** "250 cr/wk" to "250 CR/WK"; the sponsor's service as "SERVICE". */
    static String wage(String w) {
        return w == null ? "" : w.toUpperCase(Locale.ROOT);
    }

    @Override
    void keysFor(int focus) {
        for (int i = 0; i < 4; i++) keys.set(i, "", false);
        keys.set(SUITS, "SUITS", sheet != null && !sheet.suits.isEmpty());
        keys.set(HOLD, "HOLD EVA", sheet != null && !sheet.eva.isEmpty());
        if (sheet == null) return;
        int k = kindAt(focus);
        if (k == MEMBER) {
            Member m = sheet.crew.get(ref.get(focus));
            keys.set(K0, m.evaAction.isEmpty() ? "EVA" : m.evaAction.toUpperCase(Locale.ROOT), !m.evaAction.isEmpty());
            keys.set(K1, arming == m.id ? "SURE?" : "LET GO", m.dismissable);
        } else if (k == PRISONER) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            keys.set(K0, p.option.isEmpty() ? "SETTLE" : p.option.toUpperCase(Locale.ROOT), !p.option.isEmpty());
            keys.set(K1, "RECRUIT", p.offersToJoin);
        } else if (k == ALONGSIDE) {
            keys.set(K0, "BRING ABOARD", true);
        } else if (k == HIRING) {
            keys.set(K0, "HIRE", true);
        }
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null) return;
        int k = kindAt(focus);
        if (k == MEMBER) {
            Member m = sheet.crew.get(ref.get(focus));
            tell(m.name + ", " + m.role);
            if (!m.skills.isEmpty()) tell(m.skills);
            tell(join(m.condition, m.morale));
            if (!m.wage.isEmpty()) tell("Wage " + m.wage);
            if (m.working) tell("At work");
            if (!m.evaAction.isEmpty()) tell("EVA: " + m.evaAction + " on the key");
            if (!m.history.isEmpty()) tell(m.history);
            if (m.dismissable) tell("Let go here, while docked, on the key pressed twice");
        } else if (k == PRISONER) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            tell(p.name + (p.detail.isEmpty() ? "" : ", " + p.detail));
            if (!p.condition.isEmpty()) tell(p.condition);
            tell(p.option.isEmpty() ? "Nothing can be done with them until the ship docks" : p.option + " them here");
            if (p.offersToJoin) tell("They offer to join the crew");
        } else if (k == ALONGSIDE) {
            tell("Brought aboard, a station takes them: hand them over, ransom them or let them go");
        } else if (k == HIRING) {
            tell("Who is looking for a berth here, on the station terminal's Crew");
        }
    }

    /** The parts that aren't empty, with commas. */
    static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        CrewProvider p = d == null ? null : d.crew;
        if (p == null || sheet == null) return;
        int kd = kindAt(focus);
        String said = null;
        if (k == SUITS) {
            said = p.toggleSuits();
        } else if (k == HOLD) {
            said = p.toggleEva();
        } else if (kd == MEMBER) {
            Member m = sheet.crew.get(ref.get(focus));
            if (k == K0) {
                said = p.evaOrder(m.id);
            } else if (k == K1) {
                if (arming != m.id) {
                    arming = m.id;
                    armLeft = CONFIRM_FOR;
                    windows.say("Press Let go again to let " + m.name + " go");
                    keysFor(focus);
                    return;
                }
                arming = -1;
                said = p.dismiss(m.id);
            }
        } else if (kd == PRISONER) {
            Prisoner q = sheet.prisoners.get(ref.get(focus));
            said = k == K0 ? p.settlePrisoner(q.id) : p.recruit(q.id);
        } else if (kd == ALONGSIDE && k == K0) {
            said = p.takePrisoners();
        } else if (kd == HIRING && k == K0) {
            windows.show(windows.terminal, windows.terminal.hire);
            return;
        }
        windows.say(said);
        windows.refresh();
    }
}
