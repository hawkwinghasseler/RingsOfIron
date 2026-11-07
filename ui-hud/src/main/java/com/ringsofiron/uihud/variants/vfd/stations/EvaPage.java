package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The slate's EVA (functional-stations 10.4): the crew, each with what can be done about their EVA
 * (send out, call in, let out), the order on the focused one; and holding everyone in, or letting them
 * go out for work outside as it comes, for the whole ship.
 */
final class EvaPage extends SectionPage {
    static final int MEMBER = ITEM;
    static final int ORDER = 0, HOLD = 1;

    private CrewSheet sheet;

    EvaPage() {
        super("EVA", ClassicWindow.CREW, "");
        keys.define(2, 2);
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
        head("EVA");
        if (!sheet.eva.isEmpty()) note(sheet.eva.toUpperCase(Locale.ROOT));
        for (int i = 0; i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            add(MEMBER, i, cols.clear().left(m.name.toUpperCase(Locale.ROOT), w - 11).gap(1)
                    .left(m.evaAction.isEmpty() ? "" : m.evaAction.toUpperCase(Locale.ROOT), 10).toString());
        }
        if (sheet.crew.size == 0) note("NOBODY ABOARD");
    }

    @Override
    void keysFor(int focus) {
        Member m = kindAt(focus) == MEMBER && sheet != null ? sheet.crew.get(ref.get(focus)) : null;
        keys.set(ORDER, m == null || m.evaAction.isEmpty() ? "EVA" : m.evaAction.toUpperCase(Locale.ROOT), m != null && !m.evaAction.isEmpty());
        keys.set(HOLD, "HOLD EVA", sheet != null && !sheet.eva.isEmpty());
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null || kindAt(focus) != MEMBER) return;
        Member m = sheet.crew.get(ref.get(focus));
        tell(m.name + ", " + m.role + ": " + m.condition);
        tell(m.evaAction.isEmpty() ? "NOTHING TO ORDER ABOUT THEIR EVA NOW" : m.evaAction.toUpperCase(Locale.ROOT) + " ON THE KEY");
    }

    @Override
    void press(int k, int focus) {
        HudData d = panels.data();
        CrewProvider p = d == null ? null : d.crew;
        if (p == null || sheet == null) return;
        String said = null;
        if (k == HOLD) said = p.toggleEva();
        else if (k == ORDER && kindAt(focus) == MEMBER) said = p.evaOrder(sheet.crew.get(ref.get(focus)).id);
        panels.say(said);
        panels.refresh();
    }
}
