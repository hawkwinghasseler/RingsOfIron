package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.ShipyardProvider.Ship;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * Ships for sale (the yard's SHIPS FOR SALE, or Y with a prize and no yard): the ship flown now and
 * what it trades in for, a row a ship with its seller and terms, barred with the game's reason when
 * it can't be bought, and a prize in tow to sell or turn over to the sponsor.
 */
public final class ShipyardPage extends Page {
    public static final String SOURCE = "STATION";

    public ShipyardPage() {
        super("SHIPYARD", ClassicWindow.SHIPYARD);
    }

    @Override
    public boolean offered(Boards b) {
        return b.shipyard.isOpen();
    }

    @Override
    protected void build(Boards b) {
        ShipyardProvider.Yard y = b.shipyard;
        if (!y.stationName.isEmpty()) {
            Sect s = sect("SHIPS FOR SALE").empty("NONE TODAY").sub(Fmt.caps(y.current));
            s.col(Col.start("SHIP", 200f)).col(Col.start("SELLER", 170f)).col(Col.start("TERMS", 0f));
            for (int i = 0; i < y.ships.size; i++) {
                Ship sh = y.ships.get(i);
                Row r = s.row("ship/" + sh.id);
                r.add(Fmt.caps(sh.name), Cell.Tone.NAME);
                r.add(Fmt.caps(sh.seller), Cell.Tone.LABEL);
                r.add(Fmt.caps(sh.terms), Cell.Tone.BODY);
                r.dim = !sh.locked.isEmpty();
                String id = sh.id;
                r.act("BUY", sh.locked.isEmpty(), Fmt.caps(sh.locked), order(SOURCE, () -> provider().buy(id)));
                r.detail.add(Line.pair("SHIP", Fmt.caps(sh.name), "", Cell.Tone.NAME));
                r.detail.add(Line.pair("SELLER", Fmt.caps(sh.seller), "", Cell.Tone.VALUE));
                r.detail.add(Line.pair("TERMS", Fmt.caps(sh.terms), "", Cell.Tone.VALUE));
                if (!sh.locked.isEmpty()) r.detail.add(Line.pair("BARRED", Fmt.caps(sh.locked), "", Cell.Tone.AMBER));
                if (!y.current.isEmpty()) r.detail.add(Line.pair("NOW", Fmt.caps(y.current), "", Cell.Tone.LABEL));
            }
        }
        if (!y.prize.isEmpty()) {
            Sect s = sect("PRIZE IN TOW").sub(Fmt.caps(y.prize));
            s.col(Col.start("", 200f)).col(Col.start("TERMS", 0f));
            Row sell = s.row("prize/sell");
            sell.add("SELL IT", Cell.Tone.NAME).add(Fmt.caps(y.sellTerms), Cell.Tone.BODY);
            sell.dim = !y.sellLocked.isEmpty();
            sell.act("SELL", y.sellLocked.isEmpty(), Fmt.caps(y.sellLocked), order(SOURCE, () -> provider().sellPrize()));
            sell.detail.add(Line.pair("PRIZE", Fmt.caps(y.prize), "", Cell.Tone.NAME));
            sell.detail.add(Line.pair("SELL IT", Fmt.caps(y.sellTerms), "", Cell.Tone.VALUE));
            Row turn = s.row("prize/turn");
            turn.add("TURN IT IN", Cell.Tone.NAME).add(Fmt.caps(y.turnInTerms), Cell.Tone.BODY);
            turn.dim = !y.turnInLocked.isEmpty();
            turn.act("TURN IN", y.turnInLocked.isEmpty(), Fmt.caps(y.turnInLocked), order(SOURCE, () -> provider().turnInPrize()));
            turn.detail.add(Line.pair("PRIZE", Fmt.caps(y.prize), "", Cell.Tone.NAME));
            turn.detail.add(Line.pair("TURN IT IN", Fmt.caps(y.turnInTerms), "", Cell.Tone.VALUE));
        }
    }

    private ShipyardProvider provider() {
        return host.data().shipyard;
    }
}
