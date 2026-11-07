package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The slate's Crew (functional-stations 10.4): the roster, a member a row with their role and how they
 * are; the one in focus in full under the list, their skills, condition, morale, wage and record. The
 * suits at battle stations for everyone; and Let go while docked, on a second press since it can't be
 * undone, walked to and never landed on.
 */
final class CrewPage extends SectionPage {
    static final int MEMBER = ITEM;
    static final int SUITS = 0, LET_GO = 1;
    static final float CONFIRM_FOR = 3f;

    private CrewSheet sheet;
    /** The member a first press of Let go waits on, and how long it still waits. */
    private long arming = -1;
    private float armLeft;

    CrewPage() {
        super("CREW", ClassicWindow.CREW, "I");
        // Let go is walked to, never landed on
        keys.define(2, 2).guard(LET_GO);
        hasDetail = true;
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
        int w = cells - 1;
        head(sheet.summary.isEmpty() ? "ABOARD" : "ABOARD: " + sheet.summary.toUpperCase(Locale.ROOT));
        for (int i = 0; i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            add(MEMBER, i, cols.clear().left(m.name.toUpperCase(Locale.ROOT), w - 21).gap(1).left(m.role.toUpperCase(Locale.ROOT), 9).gap(1)
                    .left(InfirmaryPage.shortCondition(m.condition), 10).toString());
        }
        if (sheet.crew.size == 0) note("NOBODY ABOARD");
        if (!sheet.suits.isEmpty()) note(sheet.suits.toUpperCase(Locale.ROOT));
    }

    @Override
    void keysFor(int focus) {
        Member m = kindAt(focus) == MEMBER && sheet != null ? sheet.crew.get(ref.get(focus)) : null;
        keys.set(LET_GO, m != null && arming == m.id ? "SURE?" : "LET GO", m != null && m.dismissable);
        keys.set(SUITS, "SUITS", sheet != null && !sheet.suits.isEmpty());
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null || kindAt(focus) != MEMBER) return;
        Member m = sheet.crew.get(ref.get(focus));
        tell(m.name + ", " + m.role + (m.skills.isEmpty() ? "" : ": " + m.skills));
        tell(join(m.condition, m.morale, m.wage));
        if (!m.history.isEmpty()) tell(m.history);
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
        HudData d = panels.data();
        CrewProvider p = d == null ? null : d.crew;
        if (p == null || sheet == null) return;
        String said = null;
        if (k == SUITS) {
            said = p.toggleSuits();
        } else if (k == LET_GO && kindAt(focus) == MEMBER) {
            Member m = sheet.crew.get(ref.get(focus));
            if (arming != m.id) {
                arming = m.id;
                armLeft = CONFIRM_FOR;
                panels.say("Press Let go again to let " + m.name + " go");
                keysFor(focus);
                return;
            }
            arming = -1;
            said = p.dismiss(m.id);
        }
        panels.say(said);
        panels.refresh();
    }
}
