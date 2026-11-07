package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The Station link's Hiring (functional-stations 10.3): who is looking for a berth, each with their
 * role (their skills and terms under the list), Hire on the focused one; the berths and the week's
 * wages over the list. It stands for the crew roster's hiring, so the roster's key closes it.
 */
final class HiringPage extends SectionPage {
    static final int HIRE = ITEM;

    private CrewSheet sheet;

    HiringPage() {
        super("HIRING", ClassicWindow.CREW, "");
        keys.define(1, 1);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && b.hiring && !b.crew.stationName.isEmpty();
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        int w = cells - 1;
        head("LOOKING FOR A BERTH");
        if (!sheet.summary.isEmpty()) note(sheet.summary.toUpperCase(Locale.ROOT));
        for (int i = 0; i < sheet.hires.size; i++) {
            Hire h = sheet.hires.get(i);
            add(HIRE, i, cols.clear().left(h.name.toUpperCase(Locale.ROOT), w - 11).gap(1).left(h.role.toUpperCase(Locale.ROOT), 10).toString());
        }
        if (sheet.hires.size == 0) note("NOBODY HERE IS LOOKING FOR A BERTH");
    }

    @Override
    void keysFor(int focus) {
        keys.set(0, "HIRE", sheet != null && kindAt(focus) == HIRE);
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null || kindAt(focus) != HIRE) return;
        Hire h = sheet.hires.get(ref.get(focus));
        tell(h.name + ", " + h.role);
        if (!h.skills.isEmpty()) tell(h.skills);
        if (!h.terms.isEmpty()) tell(h.terms);
    }

    @Override
    void press(int k, int focus) {
        HudData d = panels.data();
        if (d == null || d.crew == null || sheet == null || kindAt(focus) != HIRE) return;
        panels.say(d.crew.hire(sheet.hires.get(ref.get(focus)).key));
        panels.refresh();
    }
}
