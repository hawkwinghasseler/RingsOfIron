package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The contracts held (J away from a station's board, or the captain's record): a row a contract with
 * its terms, each of which can be given up, pressed twice, as the classic window's Abandon.
 */
public final class HeldPage extends Page {
    public static final String SOURCE = "SPONSOR";

    public HeldPage() {
        super("CONTRACTS", ClassicWindow.MISSIONS);
    }

    @Override
    public boolean offered(Boards b) {
        return b.missions.isOpen();
    }

    @Override
    protected void build(Boards b) {
        Sect s = sect("YOUR CONTRACTS");
        held(this, s, b);
        if (!b.missions.career.isEmpty()) s.note(Fmt.caps(b.missions.career));
        if (!b.missions.tension.isEmpty()) s.note(Fmt.caps(b.missions.tension));
    }

    /** Fills {@code s} with the contracts held, for this page or another's. */
    static void held(Page p, Sect s, Boards b) {
        s.empty("NONE HELD").sub(b.missions.held.size == 0 ? "" : b.missions.held.size + " HELD");
        s.col(Col.start("CONTRACT", 300f)).col(Col.start("TERMS", 0f));
        for (int i = 0; i < b.missions.held.size; i++) {
            Job j = b.missions.held.get(i);
            Row r = s.row("held/" + j.id);
            r.add(Fmt.caps(j.title), Cell.Tone.NAME);
            r.add(Fmt.caps(j.terms), late(j.terms) ? Cell.Tone.AMBER : Cell.Tone.BODY);
            r.sub = Fmt.caps(j.note);
            long id = j.id;
            r.act("GIVE UP", true, "", p.order(SOURCE, () -> p.host.data().missions.abandon(id))).guarded();
            OffersPage.detail(r, j);
        }
        if (b.missions.held.size > 0) s.note("GIVE UP IS PRESSED TWICE: IT FAILS THE CONTRACT");
    }

    /** Whether a contract's terms say it is late or nearly so. */
    static boolean late(String terms) {
        String t = terms == null ? "" : terms.toLowerCase(java.util.Locale.ROOT);
        return t.contains("late") || t.contains("overdue") || t.contains("expired");
    }
}
