package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * Boarding and salvage (K, latched to another ship or standing off alongside one): how it stands, the
 * party's three orders (send it, storm, recall) each barred with the game's reason, the prisoners to
 * bring aboard as berths allow, the parts worth taking, most valuable first, to mark or leave, and
 * across a dock the cargo aboard it to carry over. A part worth nothing is left out, so the crew
 * never carry one home; a part the link can't reach says so before it is marked.
 */
public final class BoardingPage extends Page {
    public static final String SOURCE = "BOARDING";

    private final Array<Part> parts = new Array<>();

    public BoardingPage() {
        super("BOARDING", ClassicWindow.BOARDING);
    }

    @Override
    public boolean offered(Boards b) {
        return b.boarding.isOpen();
    }

    @Override
    protected void build(Boards b) {
        BoardingBoard k = b.boarding;
        Sect s = sect("BOARDING").sub(Fmt.caps(k.craftName));
        s.line(Line.pair("STATE", Fmt.caps(k.state), "", k.taken ? Cell.Tone.GREEN : k.disabled ? Cell.Tone.VALUE : Cell.Tone.AMBER));
        if (k.boarding || k.aboard > 0 || k.defenders > 0) {
            String party = k.aboard > 0 ? k.aboard + " ABOARD IT" : k.boarding ? "ON ITS WAY" : "NONE SENT";
            s.line(Line.pair("PARTY", party, "", k.boarding ? Cell.Tone.CYAN : Cell.Tone.VALUE)
                    .note(k.defenders > 0 ? k.defenders + (k.defenders == 1 ? " DEFENDER STANDING" : " DEFENDERS STANDING") : ""));
        }
        if (k.taken || k.prisoners > 0) {
            int free = b.berthsFree();
            s.line(Line.pair("HELD", Integer.toString(k.prisoners), "", Cell.Tone.VALUE)
                    .note(free < 0 ? "" : free == 1 ? "1 BERTH FREE" : free + " BERTHS FREE"));
        }
        String noBoard = k.salvage && k.boardRefusal.isEmpty() ? "TAKEN" : k.boarding ? "UNDER WAY" : Fmt.caps(k.boardRefusal);
        String noStorm = k.salvage && k.stormRefusal.isEmpty() ? "TAKEN" : k.boarding ? "UNDER WAY" : Fmt.caps(k.stormRefusal);
        s.act(k.byEva ? "SEND ON EVA" : "BOARD", !k.salvage && !k.boarding && k.boardRefusal.isEmpty(), noBoard,
                order(SOURCE, () -> provider().orderBoarding()));
        s.act("STORM", !k.salvage && !k.boarding && k.stormRefusal.isEmpty(), noStorm, order(SOURCE, () -> provider().storm()));
        s.act("RECALL", k.boarding && !k.salvage, "NO BOARDING UNDER WAY", order(SOURCE, () -> provider().recall())).on(k.boarding);
        if (b.crew.prisonersAlongside) {
            s.act("BRING ABOARD", true, "", order(SOURCE, () -> host.data().crew.takePrisoners()));
        }
        salvage(k);
        if (k.cargo.size > 0) cargo(k);
    }

    private void salvage(BoardingBoard k) {
        Sect s = sect("SALVAGE");
        if (!k.salvage) {
            s.empty(Fmt.caps(!k.salvageRefusal.isEmpty() ? k.salvageRefusal : k.boardRefusal.isEmpty() ? "TAKE IT TO STRIP IT" : k.boardRefusal));
            return;
        }
        parts.clear();
        int marked = 0, off = 0;
        double worth = 0;
        for (int i = 0; i < k.parts.size; i++) {
            Part p = k.parts.get(i);
            if (p.value <= 0 && !p.marked) continue;
            parts.add(p);
            if (p.off) off++;
            else if (p.marked) marked++;
            else worth += p.value;
        }
        parts.sort((a, c) -> a.off != c.off ? (a.off ? 1 : -1) : Double.compare(c.value, a.value));
        s.empty("NOTHING LEFT TO STRIP");
        if (marked == 0 && off == 0) s.sub(parts.size + (parts.size == 1 ? " PART · " : " PARTS · ") + Fmt.cr(worth));
        else s.sub(marked + " MARKED · " + off + " OFF");
        s.col(Col.start("PART", 190f)).col(Col.end("SELLS FOR", 90f)).col(Col.start("", 0f));
        for (int i = 0; i < parts.size; i++) {
            Part p = parts.get(i);
            Row r = s.row("part/" + p.x + "," + p.y + (p.off ? "/off" : ""));
            r.add(Fmt.caps(p.name), p.marked && !p.off ? Cell.Tone.CYAN : Cell.Tone.NAME);
            r.add(Fmt.credits(p.value), "CR", Cell.Tone.VALUE);
            r.add(Fmt.caps(p.status), p.off ? Cell.Tone.GREEN : p.marked ? Cell.Tone.CYAN : Cell.Tone.LABEL);
            r.dim = !p.toggleable && !p.marked;
            int x = p.x, y = p.y;
            String why = p.off ? "OFF ALREADY" : p.status.isEmpty() ? "OUT OF REACH FROM HERE" : Fmt.caps(p.status);
            r.act(p.marked ? "LEAVE IT" : "MARK", p.toggleable, why, order(SOURCE, () -> provider().toggleStrip(x, y))).on(p.marked && !p.off);
            r.detail.add(Line.pair("PART", Fmt.caps(p.name), "", Cell.Tone.NAME));
            r.detail.add(Line.pair("SELLS FOR", Fmt.credits(p.value), "CR", Cell.Tone.VALUE));
            if (!p.status.isEmpty()) r.detail.add(Line.pair("NOW", Fmt.caps(p.status), "", p.off ? Cell.Tone.GREEN : Cell.Tone.CYAN));
        }
        if (!k.salvageRefusal.isEmpty()) s.note(Fmt.caps(k.salvageRefusal));
    }

    private void cargo(BoardingBoard k) {
        Sect s = sect("CARGO ABOARD IT");
        s.col(Col.start("COMMODITY", 190f)).col(Col.end("UNITS", 90f)).col(Col.start("", 0f));
        for (int i = 0; i < k.cargo.size; i++) {
            Cargo c = k.cargo.get(i);
            Row r = s.row("cargo/" + c.commodity);
            r.add(Fmt.caps(c.commodity), Cell.Tone.NAME);
            r.add(Fmt.units(c.units), Cell.Tone.VALUE);
            boolean coming = c.onOrder > 0.05;
            r.add(coming ? Fmt.units(c.onOrder) + " COMING OVER" : "", Cell.Tone.CYAN);
            String id = c.commodity;
            r.act("TAKE ALL", !coming, "COMING OVER", order(SOURCE, () -> provider().takeCargo(id))).on(coming);
        }
    }

    private BoardingProvider provider() {
        return host.data().boarding;
    }
}
