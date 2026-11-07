package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The slate's Medical (functional-stations 10.4): the wounded, the worst first, each with how they are
 * and, while they bleed, the time they have left; the one in focus in full under the list. Docked at a
 * station with an infirmary, its medics treat them (the Station link's Infirmary). It takes no orders.
 */
final class MedicalPage extends SectionPage {
    static final int WOUNDED = ITEM;

    private CrewSheet sheet;

    MedicalPage() {
        super("MEDICAL", ClassicWindow.CREW, "");
        keys.define(0, 0);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.crew.isOpen();
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        int w = cells - 1;
        int hurt = 0;
        for (int i = 0; i < sheet.crew.size; i++) if (hurt(sheet.crew.get(i))) hurt++;
        head(hurt == 0 ? "THE WOUNDED" : "THE WOUNDED: " + hurt);
        // The worst first: the dying, then the unconscious, then the rest
        for (int rank = 0; rank < 3; rank++) {
            for (int i = 0; i < sheet.crew.size; i++) {
                Member m = sheet.crew.get(i);
                if (!hurt(m) || severity(m.condition) != rank) continue;
                add(WOUNDED, i, cols.clear().left(m.name.toUpperCase(Locale.ROOT), w - 13).gap(1).left(timeLeft(m.condition), 12).toString());
            }
        }
        if (hurt == 0) note("NOBODY ABOARD IS HURT");
        if (b.docked() && b.infirmary) note("THE STATION'S INFIRMARY TREATS THE WOUNDED");
    }

    /** Whether the roster has them hurt: anything but OK. */
    static boolean hurt(Member m) {
        String c = m.condition == null ? "" : m.condition.trim().toUpperCase(Locale.ROOT);
        return !c.isEmpty() && !c.startsWith("OK");
    }

    /** 0 for one bleeding out, 1 for one out cold, 2 for the rest. */
    static int severity(String condition) {
        String c = condition == null ? "" : condition.toUpperCase(Locale.ROOT);
        if (c.contains("TO LIVE") || c.startsWith("CRITICAL")) return 0;
        if (c.startsWith("UNCONSCIOUS")) return 1;
        return 2;
    }

    /** "Critical wound, 4:10 to live" to "4:10 TO LIVE"; else the condition, short. */
    static String timeLeft(String condition) {
        String c = condition == null ? "" : condition.toUpperCase(Locale.ROOT);
        int comma = c.indexOf(',');
        if (comma >= 0 && c.contains("TO LIVE")) return c.substring(comma + 1).trim();
        return InfirmaryPage.shortCondition(condition);
    }

    @Override
    void keysFor(int focus) {
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null || kindAt(focus) != WOUNDED) return;
        Member m = sheet.crew.get(ref.get(focus));
        tell(m.name + ", " + m.role + ": " + m.condition);
        if (!m.morale.isEmpty()) tell(m.morale);
    }

    @Override
    void press(int k, int focus) {
    }
}
