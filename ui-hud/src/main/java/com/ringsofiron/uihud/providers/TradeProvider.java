package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Station trading for the trade window: whether the player can trade right now, the station's board,
 * and buy/sell. Implemented in the game wiring from macro-sim's market, so ui-hud does not depend on
 * macro-sim. Called on the render thread.
 */
public interface TradeProvider {

    /** Fills {@code out} (already cleared). Leave {@link TradeBoard#stationName} empty when not docked. */
    void collect(TradeBoard out);

    /** Buys up to {@code units}; returns the units actually bought (limited by stock, hold and credits). */
    double buy(String commodityId, double units);

    /** Sells up to {@code units} from the hold; returns the units actually sold. */
    double sell(String commodityId, double units);

    final class TradeLine {
        public String commodityId = "";
        public String name = "";
        /** Units the station will sell now. */
        public double available;
        /** Units in the player's hold. */
        public double held;
        /** Credits per unit to buy, and to sell (0 when the station does not buy it). */
        public double buyPrice, sellPrice;
        public boolean stationBuys;
    }

    final class TradeBoard {
        /** Station the player is docked at and can trade with; empty when not docked. */
        public String stationName = "";
        public double credits;
        /** Hold volume used and total, m³. */
        public double cargoUsed, cargoCapacity;
        /** False when the player's ship has no hold (nothing to trade with). */
        public boolean hasHold;
        public final Array<TradeLine> lines = new Array<>();
        private final Array<TradeLine> pool = new Array<>();

        public TradeLine addLine() {
            TradeLine l = pool.size > 0 ? pool.pop() : new TradeLine();
            lines.add(l);
            return l;
        }

        public void clear() {
            pool.addAll(lines);
            lines.clear();
            stationName = "";
            credits = cargoUsed = cargoCapacity = 0;
            hasHold = false;
        }

        public boolean isDocked() {
            return !stationName.isEmpty();
        }

        public double freeCargo() {
            return Math.max(0, cargoCapacity - cargoUsed);
        }
    }
}
