package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.coreengine.EntityRef;

import java.util.ArrayList;

/**
 * Goods a ship has bought or sold at a station that are still to be carried across the dock
 * ({@link CargoLoading}). Saved with the ship; the loads in hand are not, so after a load they are
 * carried again from the start.
 */
public class CargoOrdersComponent implements Component {

    /** One commodity going one way between the ship and a station. */
    public static class CargoOrder {
        public EntityRef station;
        public String commodity;
        /** Units still to arrive. */
        public double units;
        /** True for goods going aboard the ship (bought), false for goods going to the station (sold). */
        public boolean toShip;

        /** Units in loaders' hands, set aside from {@link #units} until they arrive. */
        public transient double inHand;
        /** The ship undocked or the goods ran out: no more trips. */
        public transient boolean closed;
        /** Trips posted for this order and not yet finished. */
        public final transient Array<CarryTask> trips = new Array<>();
        /** The carriers' view of this order. */
        public transient CarryTask.Job job;

        public CargoOrder() {
        }

        public CargoOrder(EntityRef station, String commodity, double units, boolean toShip) {
            this.station = station;
            this.commodity = commodity;
            this.units = units;
            this.toShip = toShip;
        }

        /** Units ordered and not yet carried off: what is left less what is in hand. */
        public double waiting() {
            return Math.max(0, units - inHand);
        }
    }

    public ArrayList<CargoOrder> orders = new ArrayList<>();

    /** The open order for {@code commodity} each way at {@code station}, or null. */
    public CargoOrder find(EntityRef station, String commodity, boolean toShip) {
        for (CargoOrder o : orders) {
            if (!o.closed && o.toShip == toShip && o.commodity.equals(commodity) && o.station.equals(station)) return o;
        }
        return null;
    }

    /** Units of {@code commodity} still coming aboard (positive) or going off (negative) at {@code station}. */
    public double pending(EntityRef station, String commodity) {
        double n = 0;
        for (CargoOrder o : orders) {
            if (o.closed || !o.commodity.equals(commodity) || !o.station.equals(station)) continue;
            n += o.toShip ? o.units : -o.units;
        }
        return n;
    }
}
