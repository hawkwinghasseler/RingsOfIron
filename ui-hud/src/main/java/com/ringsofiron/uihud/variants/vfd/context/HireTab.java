package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The station's Hire (functional-context 10.3): who is looking for a berth, with their role, skills
 * and terms, Hire on the focused one; the berths free and the week's wages over the list. It stands
 * for the crew roster's hiring, so the roster's key closes it.
 */
final class HireTab extends SectionTab {
    static final int HIRE = ITEM;

    private CrewSheet sheet;

    HireTab() {
        super("HIRE", ClassicWindow.CREW, "");
        keys.define(1, 6);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && b.hiring && !b.crew.stationName.isEmpty();
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        head(sheet.summary.isEmpty() ? "LOOKING FOR A BERTH" : "LOOKING FOR A BERTH · " + sheet.summary.toUpperCase(Locale.ROOT));
        for (int i = 0; i < sheet.hires.size; i++) {
            Hire h = sheet.hires.get(i);
            add(HIRE, i, cols.clear().left(h.name.toUpperCase(Locale.ROOT), 17).left(h.role.toUpperCase(Locale.ROOT), 10)
                    .left(h.skills.toUpperCase(Locale.ROOT), 31).left(h.terms.toUpperCase(Locale.ROOT), 26).toString());
        }
        if (sheet.hires.size == 0) note("NOBODY HERE IS LOOKING FOR A BERTH");
    }

    @Override
    void keysFor(int focus) {
        keys.set(0, "HIRE", sheet != null && focus >= 0 && focus < kind.size && kind.get(focus) == HIRE);
    }

    @Override
    void press(int k, int focus) {
        HudData d = modes.data();
        if (d == null || d.crew == null || sheet == null || focus < 0 || focus >= kind.size || kind.get(focus) != HIRE) return;
        modes.say("CREW", d.crew.hire(sheet.hires.get(ref.get(focus)).key));
        modes.refresh();
    }
}
