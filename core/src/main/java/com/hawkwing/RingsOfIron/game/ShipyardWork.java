package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.uihud.providers.ShipyardProvider;

import java.util.Locale;

/**
 * ui-hud's shipyard window from the {@link Shipyard}: the ships on sale where the player is docked,
 * priced after the trade-in, and the prize in tow when a station's tugs can take it in.
 */
public class ShipyardWork implements ShipyardProvider {
    private final GameContext ctx;
    private final FactionStanding standing;
    private final Shipyard shipyard;

    public ShipyardWork(GameContext ctx, FactionStanding standing, Shipyard shipyard) {
        this.ctx = ctx;
        this.standing = standing;
        this.shipyard = shipyard;
    }

    @Override
    public void collect(Yard out) {
        Shipyard yard = shipyard;
        Entity ship = ctx.player();
        Entity station = yard.dockedStation();
        if (ship != null && station != null && yard.sellsShips(station)) {
            out.stationName = PlayerDocking.name(station);
            out.current = String.format(Locale.ROOT, "Your %s: trade-in %,.0f cr (condition %.0f%%).  %s", yard.name(ship),
                    Shipyard.tradeIn(ship), 100 * Shipyard.condition(ship), standing.describeRank());
            for (Shipyard.Listing l : yard.listings(station)) {
                Ship s = new Ship();
                s.id = l.id;
                s.name = l.name;
                s.seller = standing.factions().name(l.seller);
                double cost = yard.cost(l, ship);
                s.terms = String.format(Locale.ROOT, "%,.0f cr, %s", l.price, cost >= 0
                        ? String.format(Locale.ROOT, "%,.0f cr after the trade-in", cost)
                        : String.format(Locale.ROOT, "%,.0f cr to you after the trade-in", -cost));
                String locked = yard.refusal(l);
                s.locked = locked == null ? "" : locked;
                out.ships.add(s);
            }
        }
        Entity prize = yard.prize();
        if (prize != null && yard.prizeStation() != null) {
            out.prize = yard.name(prize) + " (taken), off " + PlayerDocking.name(yard.prizeStation());
            out.sellTerms = String.format(Locale.ROOT, "%+,.0f cr", Shipyard.prizeValue(prize));
            out.turnInTerms = String.format(Locale.ROOT, "%+.0f standing with the %s", yard.prizeStanding(prize),
                    standing.factions().name(standing.sponsor()));
            String sell = yard.prizeRefusal(false), turnIn = yard.prizeRefusal(true);
            out.sellLocked = sell == null ? "" : sell;
            out.turnInLocked = turnIn == null ? "" : turnIn;
        }
    }

    @Override
    public String buy(String id) {
        return shipyard.buy(id);
    }

    @Override
    public String sellPrize() {
        return shipyard.sellPrize();
    }

    @Override
    public String turnInPrize() {
        return shipyard.turnInPrize();
    }
}
