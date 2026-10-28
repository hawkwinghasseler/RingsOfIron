package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.FurnishingCatalog;
import com.ringsofiron.interiorgrid.FurnishingType;
import com.ringsofiron.interiorgrid.Furnishings;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.ModContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A station yard's furnishings counter: furniture and decor ({@code furnishings.toml}) the player
 * buys from the yard window and puts exactly where they want it on their deck, at any spot and
 * angle ({@link Furnishing}). The order holds that spot; one of the station's loaders carries the
 * piece across the dock to it ({@link CarryTask}) and it is paid for and set down when it lands.
 * Docked on the rails, with no walkway, it comes aboard after {@link #TRIP_SECONDS}, as a refit
 * part does; and a station whose loaders never come for it has its yard hands bring it after
 * {@link #LATE_SECONDS}.
 *
 * <p>The yard also moves what is already aboard to a new spot, for nothing, and takes a piece back
 * for {@link #BUY_BACK} of its price as new times what a fire left of it.
 *
 * <p>Orders on their way are called off, with nothing paid, when the ship leaves; they are not
 * saved, so a load starts with none.
 */
public class FurnishingStore extends EntitySystem {
    /** Seconds a piece takes to come aboard without a walkway. */
    public static final double TRIP_SECONDS = StationYard.PART_TRIP_SECONDS;
    /** Seconds before the yard's hands bring a piece no loader came for. */
    public static final double LATE_SECONDS = 120;
    /** Share of the new price the yard pays for a piece it takes back, times its condition. */
    public static final double BUY_BACK = 0.4;

    /** One piece on its way. */
    public static final class Order {
        public final Entity ship, station;
        /** Where it goes, as it will stand. */
        public final Furnishing furnishing;
        /** Credits it costs when it lands. */
        public final double price;
        CarryTask trip;
        double waited;
        boolean done;

        Order(Entity ship, Entity station, Furnishing furnishing, double price) {
            this.ship = ship;
            this.station = station;
            this.furnishing = furnishing;
            this.price = price;
        }

        public boolean isOpen() {
            return !done;
        }
    }

    private final GameWorld world;
    private final Supplier<ModContext> content;
    private FurnishingCatalog catalog;
    private ModContext catalogFrom;
    private final List<Order> orders = new ArrayList<>();
    private double lastTime = Double.NaN;

    public FurnishingStore(GameWorld world, Supplier<ModContext> content) {
        this.world = world;
        this.content = content;
    }

    /** What yards sell, from the game's content ({@code [furnishing.*]}). */
    public FurnishingCatalog catalog() {
        ModContext ctx = content.get();
        if (catalog == null || ctx != catalogFrom) {
            catalog = ctx == null ? new FurnishingCatalog() : FurnishingCatalog.from(ctx.defs);
            catalogFrom = ctx;
        }
        return catalog;
    }

    /** Uses {@code catalog} instead of the content's, for a test. */
    public void setCatalog(FurnishingCatalog catalog) {
        this.catalog = catalog;
        this.catalogFrom = content.get();
    }

    public Collection<FurnishingType> offers() {
        return catalog().all();
    }

    /** What {@code station} asks for a new {@code type}. */
    public double price(FurnishingType type, Entity station) {
        return type.price * world.yard.priceFactor.applyAsDouble(station);
    }

    /** The pieces on their way to {@code ship}. */
    public List<Order> pending(Entity ship) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.ship == ship && !o.done) out.add(o);
        return out;
    }

    public List<Order> orders() {
        return Collections.unmodifiableList(orders);
    }

    /** Why a {@code type} can't go at ({@code x}, {@code y}) on {@code ship}, turned {@code rotation}; null if it fits. */
    public String refusal(Entity ship, FurnishingType type, float x, float y, float rotation) {
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        if (grid == null || type == null) return "Nothing to put it on";
        return Furnishings.refusal(grid, Furnishing.at(type, x, y, rotation), spots(ship, null));
    }

    /**
     * Orders a {@code typeId} set down on {@code ship} with its middle at ({@code x}, {@code y}),
     * tiles from the grid's corner, turned {@code rotation} degrees; returns what the yard says.
     */
    public String order(Entity ship, Entity station, String typeId, float x, float y, float rotation) {
        FurnishingType type = catalog().get(typeId);
        if (type == null) return "The yard doesn't sell that";
        if (station == null || !world.yard.canWork(ship, station)) return "No yard here";
        double price = price(type, station);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        double owed = price;
        for (Order o : pending(ship)) owed += o.price;
        if (trader != null && trader.credits < owed) return "Not enough credits for " + type.the();
        String no = refusal(ship, type, x, y, rotation);
        if (no != null) return no;
        orders.add(new Order(ship, station, Furnishing.at(type, x, y, rotation), price));
        return String.format(Locale.ROOT, "%s ordered, %s; the loaders are bringing it", capitalized(type.the()), credits(price));
    }

    /** Moves {@code f}, aboard {@code ship}, to ({@code x}, {@code y}) turned {@code rotation}, for nothing; returns what the yard says. */
    public String move(Entity ship, Entity station, Furnishing f, float x, float y, float rotation) {
        if (station == null || !world.yard.canWork(ship, station)) return "No yard here";
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null || !Furnishings.all(grid).contains(f, true)) return "That isn't aboard";
        Furnishing moved = Furnishing.at(f.type, x, y, rotation);
        moved.fuel = f.fuel;
        moved.suits.addAll(f.suits);
        Furnishings.remove(grid, f);
        String no = Furnishings.refusal(grid, moved, spots(ship, null));
        if (no != null) {
            Furnishings.restore(grid, f);
            return no;
        }
        Furnishings.restore(grid, moved);
        return capitalized(f.type.the()) + " moved";
    }

    /** Share of a full suit locker's price that is its suits: the yard pays less for one with suits missing. */
    public static final double SUITS_SHARE = 0.75;

    /** What the yard pays for {@code f}: for a suit locker, as much less as it has suits missing. */
    public static double buyBack(Furnishing f) {
        double worth = f.type.price;
        if (f.type.holds != null && f.type.capacity > 0) {
            worth *= 1 - SUITS_SHARE + SUITS_SHARE * Math.min(f.suits.size, f.type.capacity) / f.type.capacity;
        }
        return worth * BUY_BACK * f.condition();
    }

    /** The yard takes {@code f} off {@code ship} and pays for it; returns what the yard says. */
    public String sell(Entity ship, Entity station, Furnishing f) {
        if (station == null || !world.yard.canWork(ship, station)) return "No yard here";
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null || !Furnishings.remove(grid, f)) return "That isn't aboard";
        double paid = buyBack(f);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null) trader.credits += paid;
        return String.format(Locale.ROOT, "The yard took %s for %s", f.type.the(), credits(paid));
    }

    /** Calls off {@code order}, with nothing paid; true if it was still on its way. */
    public boolean cancel(Order order) {
        if (order.done) return false;
        close(order);
        return true;
    }

    @Override
    public void update(float deltaTime) {
        double now = world.engine.getSimSeconds();
        double dt = Double.isNaN(lastTime) ? 0 : Math.max(0, now - lastTime);
        lastTime = now;
        Iterator<Order> it = orders.iterator();
        while (it.hasNext()) {
            Order o = it.next();
            if (!o.done) step(o, dt);
            if (o.done) it.remove();
        }
        // A fire aboard the player's ship that burned a furnishing away says so
        Entity player = world.getPlayer();
        ShipGridComponent grid = player == null ? null : player.getComponent(ShipGridComponent.class);
        if (grid != null) {
            for (Furnishing f : grid.burntFurnishings) {
                world.notices.post(capitalized(f.type.the()) + " burned", NoticeLog.Kind.WARNING);
            }
            grid.burntFurnishings.clear();
        }
    }

    private void step(Order o, double dt) {
        if (!world.docking.docked(o.ship, o.station)) {
            world.notices.post("The yard kept " + o.furnishing.type.the() + ": you left before it came aboard");
            close(o);
            return;
        }
        o.waited += dt;
        if (!StationYard.walkway(o.ship, o.station)) {
            // On the rails together: brought aboard in the background
            if (o.waited >= TRIP_SECONDS) arrives(o);
            return;
        }
        boolean carried = o.trip != null && o.trip.isActive() && ((Trip) o.trip.job).inHand;
        if (o.waited >= LATE_SECONDS && !carried) {
            // No loader came for it: the yard's hands bring it
            arrives(o);
            return;
        }
        if (o.trip == null || !o.trip.isActive()) {
            ShipGridComponent grid = o.ship.getComponent(ShipGridComponent.class);
            CargoHoldComponent hold = o.station.getComponent(CargoHoldComponent.class);
            int x = hold != null && hold.bayTileCount() > 0 ? hold.bayX[0] : 0;
            int y = hold != null && hold.bayTileCount() > 0 ? hold.bayY[0] : 0;
            boolean[][] reach = StationYard.reachable(grid, world.crew, o.station);
            int[] drop = StationYard.nearest(reach, o.furnishing.tileX, o.furnishing.tileY);
            o.trip = new CarryTask(o.station, x, y, o.ship, drop[0], drop[1], new Trip(o));
            ShipTaskBoardComponent board = world.crew.board(o.station);
            if (board == null || !board.post(o.trip)) o.trip = null;
        }
    }

    /** The piece reaches its spot: paid for and set down, if the spot is still clear and the credits are there. */
    private void arrives(Order o) {
        FurnishingType type = o.furnishing.type;
        ShipGridComponent grid = o.ship.getComponent(ShipGridComponent.class);
        TraderComponent trader = o.ship.getComponent(TraderComponent.class);
        close(o);
        if (grid == null) return;
        if (trader != null && trader.credits < o.price) {
            world.notices.post("Can't pay for " + type.the() + ": the yard keeps it");
            return;
        }
        String no = Furnishings.place(grid, o.furnishing);
        if (no != null) {
            world.notices.post("The yard took " + type.the() + " back. " + no);
            return;
        }
        // A suit locker comes full
        for (int i = o.furnishing.suits.size; type.holds != null && i < type.capacity; i++) {
            o.furnishing.suits.add(com.ringsofiron.interiorgrid.crew.Suit.full(type.holds));
        }
        if (trader != null) trader.credits -= o.price;
        world.notices.post(String.format(Locale.ROOT, "%s is in place (%s)", capitalized(type.the()), credits(o.price)));
    }

    private void close(Order o) {
        o.done = true;
        if (o.trip != null && o.trip.isActive()) {
            ShipTaskBoardComponent board = world.crew.board(o.station);
            if (board != null) board.cancel(o.trip);
        }
    }

    /** The spots held by pieces on their way to {@code ship}, but {@code except}'s. */
    private List<Furnishing> spots(Entity ship, Order except) {
        List<Furnishing> out = new ArrayList<>();
        for (Order o : orders) if (o.ship == ship && !o.done && o != except) out.add(o.furnishing);
        return out;
    }

    /** A loader's trip with the piece: one load, set down at the drop tile. */
    private final class Trip implements CarryTask.Job {
        final Order order;
        boolean inHand;

        Trip(Order order) {
            this.order = order;
        }

        @Override
        public String commodity() {
            return order.furnishing.type.name.toLowerCase(Locale.ROOT);
        }

        @Override
        public double take(double volume) {
            if (!isOpen() || inHand) return 0;
            inHand = true;
            return 1;
        }

        @Override
        public void deliver(double units) {
            inHand = false;
            if (isOpen()) arrives(order);
        }

        @Override
        public void abandon(double units) {
            inHand = false;
        }

        @Override
        public boolean isOpen() {
            return !order.done;
        }
    }

    static String capitalized(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String credits(double c) {
        return String.format(Locale.ROOT, "%,.0f cr", c);
    }
}
