package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The slate's Prisoners (functional-stations 10.4), while some are held or a taken ship's are
 * alongside: who, their faction and how they are, and what can be done with them here (hand them
 * over, ransom them, let them go, or take one who offers to join into the crew); and the taken ship's
 * to bring aboard.
 */
final class PrisonersPage extends SectionPage {
    static final int PRISONER = ITEM, ALONGSIDE = ITEM + 1;
    static final int SETTLE = 0, RECRUIT = 1;

    private CrewSheet sheet;

    PrisonersPage() {
        super("PRISONERS", ClassicWindow.CREW, "");
        keys.define(2, 2);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.crew.isOpen() && (b.crew.prisoners.size > 0 || b.crew.prisonersAlongside);
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        int w = cells - 1;
        head(sheet.prisoners.size == 0 ? "HELD ABOARD" : "HELD ABOARD: " + sheet.prisoners.size);
        for (int i = 0; i < sheet.prisoners.size; i++) {
            Prisoner p = sheet.prisoners.get(i);
            add(PRISONER, i, cols.clear().left(p.name.toUpperCase(Locale.ROOT), w - 11).gap(1)
                    .left(p.option.isEmpty() ? "" : p.option.toUpperCase(Locale.ROOT), 10).toString());
        }
        if (sheet.prisoners.size == 0) note("NONE HELD");
        if (sheet.prisonersAlongside) {
            head("ALONGSIDE");
            add(ALONGSIDE, 0, "THE TAKEN SHIP'S, TO BRING ABOARD");
        }
    }

    @Override
    void keysFor(int focus) {
        keys.set(SETTLE, "", false).set(RECRUIT, "", false);
        if (sheet == null) return;
        int k = kindAt(focus);
        if (k == PRISONER) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            keys.set(SETTLE, p.option.isEmpty() ? "SETTLE" : p.option.toUpperCase(Locale.ROOT), !p.option.isEmpty());
            keys.set(RECRUIT, "RECRUIT", p.offersToJoin);
        } else if (k == ALONGSIDE) {
            keys.set(SETTLE, "BRING ABOARD", true);
        }
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null) return;
        int k = kindAt(focus);
        if (k == PRISONER) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            tell(p.name + (p.detail.isEmpty() ? "" : ", " + p.detail));
            if (!p.condition.isEmpty()) tell(p.condition);
            tell(p.option.isEmpty() ? "NOTHING CAN BE DONE WITH THEM UNTIL THE SHIP DOCKS" : p.option + " THEM HERE");
            if (p.offersToJoin) tell("THEY OFFER TO JOIN THE CREW");
        } else if (k == ALONGSIDE) {
            tell("BROUGHT ABOARD, A STATION TAKES THEM: HAND THEM OVER, RANSOM THEM OR LET THEM GO");
        }
    }

    @Override
    void press(int k, int focus) {
        HudData d = panels.data();
        CrewProvider p = d == null ? null : d.crew;
        if (p == null || sheet == null) return;
        String said = null;
        int kd = kindAt(focus);
        if (kd == PRISONER) {
            Prisoner q = sheet.prisoners.get(ref.get(focus));
            said = k == SETTLE ? p.settlePrisoner(q.id) : p.recruit(q.id);
        } else if (kd == ALONGSIDE && k == SETTLE) {
            said = p.takePrisoners();
        }
        panels.say(said);
        panels.refresh();
    }
}
