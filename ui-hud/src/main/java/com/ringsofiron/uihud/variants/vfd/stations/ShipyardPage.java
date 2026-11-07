package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.ShipyardProvider.Ship;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The Station link's Shipyard (functional-stations 10.3), across the screen: the ships for sale with
 * their seller and price after the trade-in (locked, and why, under the list), the ship's own
 * trade-in, and a prize in tow to sell or turn in, with its terms. With a prize in tow off a station
 * that buys it, the link opens on this page alone.
 */
final class ShipyardPage extends SectionPage {
    static final int SHIP = ITEM, PRIZE = ITEM + 1;
    static final int BUY = 0, SELL = 0, TURN_IN = 1;

    private ShipyardProvider.Yard yard;

    ShipyardPage() {
        super("SHIPYARD", ClassicWindow.SHIPYARD, "");
        keys.define(2, 2);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.shipyard.isOpen();
    }

    @Override
    boolean wide() {
        return true;
    }

    @Override
    void build(Boards b, HudData d) {
        yard = b.shipyard;
        if (!yard.stationName.isEmpty()) {
            head("SHIPS FOR SALE");
            for (int i = 0; i < yard.ships.size; i++) {
                Ship s = yard.ships.get(i);
                add(SHIP, i, cols.clear().left(s.name.toUpperCase(Locale.ROOT), 30).left(s.seller.toUpperCase(Locale.ROOT), 30)
                        .left(s.locked.isEmpty() ? price(s.terms) : "LOCKED", 24).toString());
            }
            if (yard.ships.size == 0) note("NO SHIPS FOR SALE TODAY");
            if (!yard.current.isEmpty()) note(yard.current.toUpperCase(Locale.ROOT));
        }
        if (!yard.prize.isEmpty()) {
            head("PRIZE IN TOW");
            add(PRIZE, 0, yard.prize.toUpperCase(Locale.ROOT));
        }
    }

    /** "48,000 cr, 36,000 cr after the trade-in" to "48,000 CR". */
    private static String price(String terms) {
        int comma = terms.indexOf(" cr,");
        return (comma < 0 ? terms : terms.substring(0, comma + 3)).toUpperCase(Locale.ROOT);
    }

    @Override
    void keysFor(int focus) {
        keys.set(0, "", false).set(1, "", false);
        if (yard == null || focus < 0 || focus >= kind.size) return;
        if (kind.get(focus) == SHIP) {
            keys.set(BUY, "BUY", yard.ships.get(ref.get(focus)).locked.isEmpty());
        } else if (kind.get(focus) == PRIZE) {
            keys.set(SELL, "SELL IT", yard.sellLocked.isEmpty()).set(TURN_IN, "TURN IT IN", yard.turnInLocked.isEmpty());
        }
    }

    @Override
    void detailFor(int focus) {
        if (yard == null || focus >= kind.size) return;
        if (kind.get(focus) == SHIP) {
            Ship s = yard.ships.get(ref.get(focus));
            tell(s.terms);
            if (!s.locked.isEmpty()) tell("LOCKED: " + s.locked);
        } else if (kind.get(focus) == PRIZE) {
            tell("SELL IT: " + (yard.sellLocked.isEmpty() ? yard.sellTerms : yard.sellLocked));
            tell("TURN IT IN: " + (yard.turnInLocked.isEmpty() ? yard.turnInTerms : yard.turnInLocked));
        }
    }

    @Override
    void press(int k, int focus) {
        HudData d = panels.data();
        if (d == null || d.shipyard == null || yard == null || focus < 0 || focus >= kind.size) return;
        String said;
        if (kind.get(focus) == SHIP) said = d.shipyard.buy(yard.ships.get(ref.get(focus)).id);
        else said = k == TURN_IN ? d.shipyard.turnInPrize() : d.shipyard.sellPrize();
        panels.say(said);
        panels.refresh();
    }
}
