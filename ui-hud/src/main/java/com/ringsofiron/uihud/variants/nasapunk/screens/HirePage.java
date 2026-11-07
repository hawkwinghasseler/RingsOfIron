package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The station's crew looking for a berth, while docked at one that hires: a row each with role,
 * skills and terms, and HIRE, as the classic crew window lists them.
 */
public final class HirePage extends Page {
    public static final String SOURCE = "STATION";

    public HirePage() {
        super("HIRE", ClassicWindow.CREW);
    }

    @Override
    public boolean offered(Boards b) {
        return b.crew.isOpen() && !b.crew.stationName.isEmpty();
    }

    @Override
    protected void build(Boards b) {
        int free = b.berthsFree();
        Sect s = sect("LOOKING FOR A BERTH").empty("NO ONE TODAY");
        s.sub(free < 0 ? Fmt.caps(b.crew.summary) : free == 1 ? "1 BERTH FREE" : free + " BERTHS FREE");
        s.col(Col.start("NAME", 130f)).col(Col.start("ROLE", 96f)).col(Col.start("SKILLS", 190f)).col(Col.start("TERMS", 0f));
        for (int i = 0; i < b.crew.hires.size; i++) {
            Hire h = b.crew.hires.get(i);
            Row r = s.row("hire/" + h.key);
            r.add(Fmt.caps(h.name), Cell.Tone.NAME);
            r.add(Fmt.caps(h.role), Cell.Tone.LABEL);
            r.add(Fmt.caps(h.skills), Cell.Tone.BODY);
            r.add(Fmt.caps(h.terms), Cell.Tone.BODY);
            String key = h.key;
            r.act("HIRE", free != 0, "NO BERTH FREE", order(SOURCE, () -> host.data().crew.hire(key)));
            r.detail.add(Line.pair("NAME", Fmt.caps(h.name), "", Cell.Tone.NAME));
            r.detail.add(Line.pair("ROLE", Fmt.caps(h.role), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("SKILLS", Fmt.caps(h.skills), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("TERMS", Fmt.caps(h.terms), "", Cell.Tone.VALUE));
        }
    }
}
