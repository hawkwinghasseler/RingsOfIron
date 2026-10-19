package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.stationsdocking.DockingSystem;

import java.util.Iterator;
import java.util.function.DoubleSupplier;

/**
 * Goods traded at a station cross the dock by hand. Each order on a ship's
 * {@link CargoOrdersComponent} becomes carry trips ({@link CarryTask}) on the station's task board,
 * which its loaders work a load at a time: lift it off a bay on one craft, walk it across the
 * collars and set it down by a bay on the other. The ship's own crew who carry (crewmen) help:
 * once every loader has a trip, further trips go on the ship's board, and a station with no
 * loaders is traded with by the ship's crew alone. Each load is settled (paid for, and moved from
 * hold to hold) only as it arrives, by the {@link Settlement}.
 *
 * <p>No more trips go out once the ship undocks or the goods run out; loads already in hand go
 * back. While the two craft are docked but have no walkway between them (riding the rails
 * together through time warp), the loading goes on in the background at the pace the loaders
 * would keep, {@link #TRIP_SECONDS} per load each.
 */
public class CargoLoading extends EntitySystem {
    /** Seconds a loader takes for one round trip, for loading in the background. */
    public static final double TRIP_SECONDS = 30;
    /** Most units of a commodity without a volume carried on one trip. */
    static final double UNITS_PER_TRIP = 50;
    private static final double EPS = 1e-6;

    private static final Family ORDERS = Family.all(CargoOrdersComponent.class).get();
    private static final ComponentMapper<CargoOrdersComponent> ORDERS_M = ComponentMapper.getFor(CargoOrdersComponent.class);
    private static final ComponentMapper<ShipGridComponent> GRIDS = ComponentMapper.getFor(ShipGridComponent.class);
    private static final ComponentMapper<CargoHoldComponent> HOLDS = ComponentMapper.getFor(CargoHoldComponent.class);

    /** How each load is settled, and how much can still go. */
    public interface Settlement {
        /** m³ a unit of {@code commodity} takes up; 0 if it has no bulk. */
        double unitVolume(String commodity);

        /** Units of the order that could change hands right now (stock, room, credits), loads in hand aside. */
        double available(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order);

        /** Settles a load of {@code units} that just arrived; returns the units that changed hands. */
        double settle(Entity ship, Entity station, CargoOrdersComponent.CargoOrder order, double units);
    }

    private final DockingSystem docking;
    private final CrewService crew;
    private final UuidRegistry uuids;
    private final DoubleSupplier simSeconds;
    private Settlement settlement;
    private ImmutableArray<Entity> ships;
    private double lastTime = Double.NaN;

    public CargoLoading(DockingSystem docking, CrewService crew, UuidRegistry uuids, DoubleSupplier simSeconds,
                        Settlement settlement) {
        this.docking = docking;
        this.crew = crew;
        this.uuids = uuids;
        this.simSeconds = simSeconds;
        this.settlement = settlement;
    }

    public void setSettlement(Settlement settlement) {
        this.settlement = settlement;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(ORDERS);
    }

    /**
     * True if goods can be carried between {@code ship} and {@code station}: the two are docked, both
     * have cargo bays, and the station has loaders or the ship has crew who carry.
     */
    public boolean canCarry(Entity ship, Entity station) {
        return ship != null && station != null && docking.docked(ship, station)
                && HOLDS.get(ship) != null && HOLDS.get(station) != null && loaders(station) + carriers(ship) > 0;
    }

    /**
     * Orders {@code units} of {@code commodity} carried aboard {@code ship} from {@code station}
     * ({@code toShip}) or off it, adding to an order already open. Returns the units ordered.
     */
    public double order(Entity ship, Entity station, String commodity, double units, boolean toShip) {
        if (units <= EPS || !canCarry(ship, station)) return 0;
        CargoOrdersComponent orders = ORDERS_M.get(ship);
        if (orders == null) {
            orders = new CargoOrdersComponent();
            ship.add(orders);
        }
        EntityRef ref = EntityRef.of(station);
        CargoOrdersComponent.CargoOrder o = orders.find(ref, commodity, toShip);
        if (o == null) {
            o = new CargoOrdersComponent.CargoOrder(ref, commodity, 0, toShip);
            orders.orders.add(o);
        }
        o.units += units;
        return units;
    }

    @Override
    public void update(float deltaTime) {
        double now = simSeconds.getAsDouble();
        double dt = Double.isNaN(lastTime) ? 0 : Math.max(0, now - lastTime);
        lastTime = now;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            Iterator<CargoOrdersComponent.CargoOrder> it = ORDERS_M.get(ship).orders.iterator();
            while (it.hasNext()) {
                CargoOrdersComponent.CargoOrder o = it.next();
                if (tick(ship, o, dt)) continue;
                for (CarryTask t : o.trips) {
                    if (t.status == CarryTask.Status.OPEN) t.status = CarryTask.Status.CANCELLED;
                }
                it.remove();
            }
        }
    }

    /** Keeps one order going; false once it is finished and can be dropped. */
    private boolean tick(Entity ship, CargoOrdersComponent.CargoOrder o, double dt) {
        Entity station = o.station.get(uuids);
        if (station == null || !docking.docked(ship, station)) o.closed = true;
        if (o.job == null) o.job = new Trip(ship, o);
        for (int i = o.trips.size - 1; i >= 0; i--) {
            if (!o.trips.get(i).isActive()) o.trips.removeIndex(i);
        }
        if (o.closed || o.units <= EPS) return o.inHand > EPS;
        double available = settlement.available(ship, station, o) - o.inHand;
        if (available <= EPS) {
            // Nothing more can change hands (the stock, the room or the credits ran out)
            if (o.inHand <= EPS) o.closed = true;
            return o.inHand > EPS;
        }
        if (!walkway(ship, station)) {
            background(ship, station, o, dt);
            return true;
        }
        int open = 0;
        for (CarryTask t : o.trips) {
            if (t.status == CarryTask.Status.OPEN) open++;
        }
        if (open == 0 && o.waiting() > EPS) post(ship, station, o);
        return true;
    }

    /** Docked but without a walkway (on the rails together): the loaders' pace, without the walking. */
    private void background(Entity ship, Entity station, CargoOrdersComponent.CargoOrder o, double dt) {
        if (dt <= 0) return;
        double perTrip = unitsPerTrip(o.commodity, crewDefVolume());
        double units = Math.min(o.waiting(), perTrip * (loaders(station) + carriers(ship)) * dt / TRIP_SECONDS);
        units = Math.min(units, settlement.available(ship, station, o) - o.inHand);
        if (units <= EPS) return;
        double done = settlement.settle(ship, station, o, units);
        o.units -= units;
        if (done < units - EPS) o.closed = true;
    }

    /**
     * Puts the next trip up: on the station's board while it has a loader without one, else on the
     * ship's while it has a carrier without one. Nothing once everyone has a trip.
     */
    private void post(Entity ship, Entity station, CargoOrdersComponent.CargoOrder o) {
        ShipTaskBoardComponent stationBoard = crew.board(station), shipBoard = crew.board(ship);
        int onStation = 0, onShip = 0;
        for (CarryTask t : o.trips) {
            if (stationBoard != null && stationBoard.tasks.contains(t, true)) onStation++;
            else onShip++;
        }
        ShipTaskBoardComponent board = onStation < loaders(station) ? stationBoard
                : onShip < carriers(ship) ? shipBoard : null;
        if (board == null) return;
        if (board == shipBoard) keepHome(ship);
        Entity from = o.toShip ? station : ship, to = o.toShip ? ship : station;
        CargoHoldComponent hold = HOLDS.get(from);
        int x = hold != null && hold.bayTileCount() > 0 ? hold.bayX[0] : 0;
        int y = hold != null && hold.bayTileCount() > 0 ? hold.bayY[0] : 0;
        CarryTask trip = new CarryTask(from, x, y, to, o.job);
        if (!board.post(trip)) return;
        o.trips.add(trip);
    }

    /** True if a walkway joins the two craft and is open to walk. */
    private static boolean walkway(Entity ship, Entity station) {
        ShipGridComponent g = GRIDS.get(ship);
        if (g == null) return false;
        for (GridLink l : g.links) {
            if (l.otherShip == station && l.canCross()) return true;
        }
        return false;
    }

    /**
     * {@code ship}'s own AI crew who carry cargo and are up to it: conscious, of the ship's faction,
     * in a role that takes carry trips (crewmen).
     */
    public int carriers(Entity ship) {
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return 0;
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (crew.homeOf(e) != ship || c.faction != board.faction || !c.isConscious()) continue;
            if (e.getComponent(PlayerAvatarComponent.class) != null || c.role.affinity(CrewTask.Kind.CARRY) <= 0f) continue;
            n++;
        }
        return n;
    }

    /** Makes {@code ship} the home of its crew aboard, so those who carry over to the station come back. */
    private void keepHome(Entity ship) {
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (c.home == null && crew.position(e).ship == ship) c.setHome(ship);
        }
    }

    /** {@code ship}'s own crew aboard another craft now (carrying cargo on the station, say). */
    public int crewAway(Entity ship) {
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            GridPositionComponent p = crew.position(e);
            if (crew.homeOf(e) == ship && p.ship != ship && e.getComponent(PlayerAvatarComponent.class) == null) n++;
        }
        return n;
    }

    /** Crew of other craft aboard {@code ship} now (dock hands carrying cargo, say). */
    public int visitorsAboard(Entity ship) {
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (crew.position(e).ship == ship && c.home != null && c.home != ship) n++;
        }
        return n;
    }

    /** Calls off every order {@code ship} has open: no more trips, and loads in hand go back. */
    public void callOff(Entity ship) {
        CargoOrdersComponent orders = ORDERS_M.get(ship);
        if (orders == null) return;
        for (CargoOrdersComponent.CargoOrder o : orders.orders) o.closed = true;
    }

    /** Conscious loaders working for {@code station}. */
    public int loaders(Entity station) {
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            CrewMemberComponent c = crew.crew(all.get(i));
            if (c.role == CrewRole.LOADER && c.home == station && c.isConscious()) n++;
        }
        return n;
    }

    private static final double CARRY_VOLUME = new CrewDef().carryVolume;

    private static double crewDefVolume() {
        return CARRY_VOLUME;
    }

    /** Units of {@code commodity} in a load of {@code volume} m³. */
    private double unitsPerTrip(String commodity, double volume) {
        double unit = settlement.unitVolume(commodity);
        return unit > EPS ? volume / unit : UNITS_PER_TRIP;
    }

    /** The loaders' view of an order: how much each trip takes, and settling it when it lands. */
    private final class Trip implements CarryTask.Job {
        final Entity ship;
        final CargoOrdersComponent.CargoOrder order;

        Trip(Entity ship, CargoOrdersComponent.CargoOrder order) {
            this.ship = ship;
            this.order = order;
        }

        @Override
        public String commodity() {
            return order.commodity;
        }

        @Override
        public double take(double volume) {
            if (!isOpen()) return 0;
            Entity station = order.station.get(uuids);
            double units = Math.min(order.waiting(), unitsPerTrip(order.commodity, volume));
            units = Math.min(units, settlement.available(ship, station, order) - order.inHand);
            if (units <= EPS) return 0;
            order.inHand += units;
            return units;
        }

        @Override
        public void deliver(double units) {
            order.inHand = Math.max(0, order.inHand - units);
            Entity station = order.station.get(uuids);
            double done = station == null ? 0 : settlement.settle(ship, station, order, units);
            order.units = Math.max(0, order.units - units);
            if (done < units - EPS) order.closed = true;
        }

        @Override
        public void abandon(double units) {
            order.inHand = Math.max(0, order.inHand - units);
        }

        @Override
        public boolean isOpen() {
            return !order.closed && order.units > EPS;
        }
    }
}
