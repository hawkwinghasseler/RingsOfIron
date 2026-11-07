package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The station's mission board (J while docked at one): a row an offer with its giver and terms and
 * the game's note under it, TAKE barred with the game's reason when it can't be taken, then the
 * contracts held, each of which can be given up (pressed twice).
 */
public final class OffersPage extends Page {
    public static final String SOURCE = "STATION";

    public OffersPage() {
        super("MISSIONS", ClassicWindow.MISSIONS);
    }

    @Override
    public boolean offered(Boards b) {
        return !b.missions.stationName.isEmpty();
    }

    @Override
    protected void build(Boards b) {
        Sect s = sect("ON OFFER").empty("NOTHING TODAY").sub(Fmt.caps(b.missions.tension));
        s.col(Col.start("JOB", 300f)).col(Col.start("GIVER", 170f)).col(Col.start("TERMS", 0f));
        for (int i = 0; i < b.missions.offers.size; i++) {
            Job j = b.missions.offers.get(i);
            Row r = s.row("offer/" + j.id);
            r.add(Fmt.caps(j.title), Cell.Tone.NAME);
            r.add(Fmt.caps(j.giver), Cell.Tone.LABEL);
            r.add(Fmt.caps(j.terms), Cell.Tone.BODY);
            r.sub = Fmt.caps(j.note);
            r.dim = !j.locked.isEmpty();
            long id = j.id;
            r.act("TAKE", j.locked.isEmpty(), Fmt.caps(j.locked), order(SOURCE, () -> provider().accept(id)));
            detail(r, j);
            if (!j.locked.isEmpty()) r.detail.add(Line.pair("BARRED", Fmt.caps(j.locked), "", Cell.Tone.AMBER));
        }
        HeldPage.held(this, sect("YOUR CONTRACTS"), b);
        if (!b.missions.career.isEmpty()) s.note(Fmt.caps(b.missions.career));
    }

    static void detail(Row r, Job j) {
        r.detail.add(Line.pair("JOB", Fmt.caps(j.title), "", Cell.Tone.NAME));
        r.detail.add(Line.pair("GIVER", Fmt.caps(j.giver), "", Cell.Tone.VALUE));
        r.detail.add(Line.pair("TERMS", Fmt.caps(j.terms), "", Cell.Tone.VALUE));
        if (!j.note.isEmpty()) r.detail.add(Line.pair("NOTE", Fmt.caps(j.note), "", Cell.Tone.LABEL));
    }

    private MissionProvider provider() {
        return host.data().missions;
    }
}
