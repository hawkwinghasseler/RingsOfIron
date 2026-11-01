package com.ringsofiron.shipops;

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
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * A station's yard: its technicians repair a docked ship and refit its weapons, aboard and tile by
 * tile. Repairs ({@link #orderRepairs}) work through every damaged hull tile, wall, system and
 * torn docking collar, each a {@link YardTask} on the station's board, and are paid for as the work
 * is done. A refit ({@link #orderRefit}) is a new or used weapon for one hardpoint: the station's
 * loaders carry the part from its bays to the hardpoint's mount ({@link CarryTask}), where it is
 * paid for, and the technicians then fit it one mount tile at a time; the weapon it replaces goes
 * back to the yard for part of its price. A salvaged weapon in the ship's own bays is fitted the same
 * way, as a used one with nothing to pay ({@link #orderSalvagedRefit}).
 *
 * <p>The yard also refills the ship's tanks ({@link #orderRefill}): propellant, oxygen and nitrogen,
 * at once, paid by the kilogram ({@link #pricePerKg}); short of credits, every tank gets the same
 * share of what it lacks. It makes good the emergency oxygen life support used too, at the oxygen price.
 *
 * <p>A repair order ends with the air put back (round 8): every room aboard is brought up to
 * station pressure from the station's own air ({@link #topUpAir}), at the refill's oxygen and
 * nitrogen prices, inside the repair bill.
 *
 * <p>Undocking calls the work off: repairs stop, and a part not yet fitted goes back, refunded.
 * Docked but on the rails together (no walkway), the work goes on in the background at the pace
 * the technicians would keep.
 */
public class StationYard extends EntitySystem {
    /** Credits per whole point of condition restored, by what is repaired. */
    public static final double HULL_COST = 400, WALL_COST = 150, SYSTEM_COST = 3_000, COLLAR_COST = 5_000;
    /** Credits per whole point of a power conduit across a tile, which is mended apart from the tile. */
    public static final double CONDUIT_COST = 400;
    /** A torn collar takes this much more work than a tile. */
    public static final float COLLAR_WORK = 3f;
    /** Work (at a technician's repair rate) to fit one tile of a mount. */
    public static final float FIT_WORK_PER_TILE = 1f;
    /** A used part costs this share of a new one, and comes in at this condition. */
    public static final double USED_PRICE = 0.5;
    public static final float USED_CONDITION = 0.6f;
    /** Share of its new price the yard gives for the weapon a refit takes out. */
    public static final double TRADE_IN = 0.25;
    /** Seconds for a loader to bring a part aboard, for refits in the background. */
    public static final double PART_TRIP_SECONDS = 30;
    /** Commodity id prefix a part is carried as. */
    public static final String PART = "part:";
    /**
     * How long the work waits for a ship that came loose of the station to latch again before it
     * is called off, sim seconds. The player undocking calls it off at once ({@link #undocked}).
     */
    public static final double LOOSE_GRACE = 10;
    static final float EPS = 1e-4f;
    /** A technician's pace, for work in the background. */
    static final float REPAIR_RATE = new CrewDef().repairRate;

    private static final Family ORDERS = Family.all(YardOrdersComponent.class).get();
    static final ComponentMapper<YardOrdersComponent> ORDERS_M = ComponentMapper.getFor(YardOrdersComponent.class);

    /** Something damaged aboard a ship, and how to put it right. */
    public static final class Damage {
        public enum Kind { HULL, WALL, SYSTEM, COLLAR, CONDUIT }

        public final Kind kind;
        /** The tiles the work covers. */
        public final int x, y, w, h;
        final HullPiece piece;
        final Tile tile;
        final EdgeWall wall;
        final SubsystemComponent system;
        final DockingPort port;
        final TileConduit conduit;

        /** A power conduit across tile (x, y). */
        Damage(int x, int y, TileConduit conduit) {
            this.kind = Kind.CONDUIT;
            this.x = x;
            this.y = y;
            this.w = 1;
            this.h = 1;
            this.piece = null;
            this.tile = null;
            this.wall = null;
            this.system = null;
            this.port = null;
            this.conduit = conduit;
        }

        Damage(Kind kind, int x, int y, int w, int h, HullPiece piece, Tile tile, EdgeWall wall,
               SubsystemComponent system, DockingPort port) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.piece = piece;
            this.tile = tile;
            this.wall = wall;
            this.system = system;
            this.port = port;
            this.conduit = null;
        }

        /** 0 (wrecked) to 1 (sound). */
        public float condition() {
            switch (kind) {
                case HULL:
                    return piece != null ? piece.integrity : tile.integrity;
                case WALL:
                    return wall.integrity;
                case SYSTEM:
                    return system.health;
                case CONDUIT:
                    return conduit.integrity;
                default:
                    return port.collarIntegrity;
            }
        }

        /** Credits a whole point of condition costs. */
        public double cost() {
            switch (kind) {
                case HULL:
                    return HULL_COST;
                case WALL:
                    return WALL_COST;
                case SYSTEM:
                    return SYSTEM_COST;
                case CONDUIT:
                    return CONDUIT_COST;
                default:
                    return COLLAR_COST;
            }
        }

        /** Repair work per point of condition. */
        float workPerPoint() {
            return kind == Kind.COLLAR ? COLLAR_WORK : 1f;
        }

        public String what() {
            switch (kind) {
                case HULL:
                    return "repairing hull";
                case WALL:
                    return "repairing a wall";
                case SYSTEM:
                    return "repairing the " + system.type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                case CONDUIT:
                    return "repairing a power conduit";
                default:
                    return "refitting a docking collar";
            }
        }

        /** Raises the condition by up to {@code by}; returns how much it rose. */
        float restore(float by) {
            float before = condition();
            float after = Math.min(1f, before + by);
            switch (kind) {
                case HULL:
                    if (piece != null) piece.integrity = after;
                    else tile.integrity = after;
                    // Mended past where it leaks, a field patch comes off
                    if (after >= Tile.LEAK_START) {
                        if (piece != null) piece.patchedAt = 0f;
                        else tile.patchedAt = 0f;
                    }
                    break;
                case CONDUIT:
                    conduit.integrity = after;
                    // A conduit mended whole carries power again, short or no short
                    if (after >= 1f - EPS) conduit.shorted = false;
                    break;
                case WALL:
                    wall.integrity = after;
                    if (after >= Tile.LEAK_START) wall.patchedAt = 0f;
                    break;
                case SYSTEM:
                    system.health = after;
                    // Mended whole: a short is cleared and an isolated tank opened again
                    if (after >= 1f - EPS) {
                        system.shorted = false;
                        system.isolated = false;
                    }
                    break;
                default:
                    // A collar is whole or torn: it goes back on only when the work is done, freed of any jam
                    port.collarIntegrity = after >= 1f - EPS ? 1f : before;
                    if (after >= 1f - EPS) port.jammed = false;
                    return after - before;
            }
            return after - before;
        }

        /** The same thing (two scans find equal records for one piece of damage). */
        Object key() {
            if (piece != null) return piece;
            if (tile != null) return tile;
            if (wall != null) return wall;
            if (system != null) return system;
            if (conduit != null) return conduit;
            return port;
        }
    }

    private final DockingSystem docking;
    private final CrewService crew;
    private final UuidRegistry uuids;
    private final DoubleSupplier simSeconds;
    private final CombatDefs weapons;
    /**
     * What a station charges against the base prices for repairs and refills (round 6: more where
     * tension runs high, {@link FactionTension#priceFactor}); 1 everywhere unless the game sets it.
     */
    public java.util.function.ToDoubleFunction<Entity> priceFactor = station -> 1;
    private ImmutableArray<Entity> ships;
    private double lastTime = Double.NaN;
    /** What the yard last said, for the screen; drained by the caller. */
    public final List<String> notices = new ArrayList<>();
    private final YardRepairs repairs;
    private final YardRefits refits;

    public StationYard(DockingSystem docking, CrewService crew, UuidRegistry uuids, DoubleSupplier simSeconds,
                       CombatDefs weapons) {
        this.docking = docking;
        this.crew = crew;
        this.uuids = uuids;
        this.simSeconds = simSeconds;
        this.weapons = weapons;
        repairs = new YardRepairs(this, crew, uuids);
        refits = new YardRefits(this, crew, weapons);
    }


    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(ORDERS);
    }

    // ------------------------------------------------------------------
    // What there is to do, and what it costs
    // ------------------------------------------------------------------

    /** Conscious technicians working for {@code station}. */
    public int technicians(Entity station) {
        return count(station, CrewRole.TECHNICIAN);
    }

    /**
     * True if {@code station} can work on {@code ship}: the two are docked and it has technicians. A
     * yard whose technicians have all died takes on new ones first ({@link #restaff}).
     */
    public boolean canWork(Entity ship, Entity station) {
        return ship != null && station != null && docking.docked(ship, station) && (technicians(station) > 0 || restaff(ship, station));
    }

    /**
     * Takes on new technicians at {@code station}, a station friendly to {@code ship}, once all of its
     * own have died; true if it did. A yard whose hands died aboard a wreck otherwise held its order
     * open with nobody to work it, and the ship at the berth, warp refused, for good.
     */
    private boolean restaff(Entity ship, Entity station) {
        ShipTaskBoardComponent board = crew.board(station);
        if (board == null || !crew.friendlyTo(board.faction, ship)) return false;
        if (StationCrew.replaceTechnicians(crew, station).isEmpty()) return false;
        notices.add(ShipNames.name(station) + "'s yard took on new technicians");
        return true;
    }

    /** Everything damaged aboard {@code ship} that a yard can put right. */
    public List<Damage> damage(Entity ship) {
        return repairs.damage(ship);
    }

    /** Credits to put all of {@code ship}'s damage right. */
    public double repairCost(Entity ship) {
        double total = 0;
        for (Damage d : damage(ship)) total += (1 - d.condition()) * d.cost();
        return total;
    }

    /** Credits to put all of {@code ship}'s damage right at {@code station}'s yard, at its {@link #priceFactor}. */
    public double repairCost(Entity ship, Entity station) {
        return repairCost(ship) * priceFactor.applyAsDouble(station);
    }

    /** Credits a kilogram of each fluid costs at a yard. */
    public static final double PROPELLANT_PRICE = 0.5, OXYGEN_PRICE = 2, NITROGEN_PRICE = 1;

    public static double pricePerKg(Fluid fluid) {
        return YardSupplies.pricePerKg(fluid);
    }

    /** Credits to fill every tank aboard {@code ship}. */
    public double refillCost(Entity ship) {
        return YardSupplies.refillCost(ship);
    }

    /** Credits to fill every tank aboard {@code ship} at {@code station}'s yard, at its {@link #priceFactor}. */
    public double refillCost(Entity ship, Entity station) {
        return refillCost(ship) * priceFactor.applyAsDouble(station);
    }

    /** Has {@code station}'s yard fill {@code ship}'s tanks, as far as its credits go. Returns what it says. */
    public String orderRefill(Entity ship, Entity station) {
        if (!canWork(ship, station)) return "No yard here to do the work";
        double factor = priceFactor.applyAsDouble(station);
        double cost = refillCost(ship) * factor;
        if (cost < 0.5) return "The tanks are full";
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        double budget = trader == null ? Double.POSITIVE_INFINITY : trader.credits;
        if (budget <= 0) return "You're out of credits";
        double paid = fill(ship.getComponent(ShipSubsystemRegistryComponent.class), budget / factor) * factor;
        if (trader != null) trader.credits = Math.max(0, trader.credits - paid);
        return paid >= cost - 1e-6 ? String.format(Locale.ROOT, "Tanks filled: %,.0f cr", cost)
                : String.format(Locale.ROOT, "Tanks filled to what %,.0f cr buys", paid);
    }

    /**
     * Fills every tank in {@code registry} at the yard's prices, as far as {@code budget} credits
     * go: short of them, every tank gets the same share of what it lacks. Returns the credits spent.
     */
    public static double fill(ShipSubsystemRegistryComponent registry, double budget) {
        return YardSupplies.fill(registry, budget);
    }

    /** Credits, at the base prices, to bring every room aboard {@code ship} back to station pressure. */
    public static double airCost(Entity ship) {
        return YardSupplies.airCost(ship);
    }

    /** {@link #airCost(Entity)} at {@code station}'s yard, at its {@link #priceFactor}. */
    public double airCost(Entity ship, Entity station) {
        return airCost(ship) * priceFactor.applyAsDouble(station);
    }

    /**
     * Brings every room in {@code grid} up to station pressure and the standard oxygen share, with
     * station air at the refill's prices, as far as {@code budget} credits go: short of them, every
     * room gets the same share of what it lacks. Returns the credits spent, at the base prices.
     */
    public static double topUpAir(ShipGridComponent grid, double budget) {
        return YardSupplies.topUpAir(grid, budget);
    }

    /** Credits a {@code def} costs at the yard, new or used. */
    public static double price(WeaponDef def, boolean used) {
        return def.price * (used ? USED_PRICE : 1);
    }

    /** Weapons the yard sells, cheapest first. */
    public List<WeaponDef> offers() {
        List<WeaponDef> out = new ArrayList<>();
        for (WeaponDef d : weapons.weapons.values()) {
            if (d.price > 0) out.add(d);
        }
        out.sort((a, b) -> Double.compare(a.price, b.price));
        return out;
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    /**
     * Has {@code station}'s yard fit a salvaged {@code weaponId} out of the ship's own cargo bays
     * ({@link Salvage}) to hardpoint {@code hardpoint}, like a used one but with nothing to pay: the
     * crate is taken out of the hold now, and goes back if the work is called off. Returns what it says.
     */
    public String orderSalvagedRefit(Entity ship, Entity station, int hardpoint, String weaponId) {
        return refits.orderSalvagedRefit(ship, station, hardpoint, weaponId);
    }

    /**
     * Puts the ship's hull patches and medical stores back to a full set, as part of a repair order
     * (no charge), and when it is done. Returns true if anything was short.
     */
    static boolean restockPatches(Entity ship) {
        return YardRepairs.restockPatches(ship);
    }

    /**
     * Has {@code station}'s yard repair everything damaged aboard {@code ship}, patched leaks
     * included, and restock its hull patches and medical stores. With nothing to repair it still
     * restocks them. Returns what it says.
     */
    public String orderRepairs(Entity ship, Entity station) {
        if (!canWork(ship, station)) return "No yard here to do the work";
        double cost = repairCost(ship, station);
        if (cost <= 0.5) return restockPatches(ship) ? "Nothing aboard needs repairing: stores restocked" : "Nothing aboard needs repairing";
        YardOrdersComponent orders = orders(ship, station);
        if (orders == null) return "The yard is working for another station's ship";
        orders.repairs = true;
        orders.repairsPaid = 0;
        restockPatches(ship);
        return String.format(Locale.ROOT, "Repairs ordered: about %,.0f cr, paid as the work is done", cost + airCost(ship, station));
    }

    /**
     * Has {@code station}'s yard fit {@code weaponId} (new, or used and cheaper but worn) to the
     * ship's hardpoint {@code hardpoint}. Returns what it says.
     */
    public String orderRefit(Entity ship, Entity station, int hardpoint, String weaponId, boolean used) {
        return refits.orderRefit(ship, station, hardpoint, weaponId, used);
    }

    /** Why {@code def} won't go on {@code mount}'s turret mount, too small for it; null when it fits. */
    public static String tooSmall(WeaponMountComponent mount, WeaponDef def) {
        if (def.minMountSize == null) return null;
        SubsystemComponent system = mount.subsystem == null ? null : mount.subsystem.getComponent(SubsystemComponent.class);
        String size = system == null ? null : system.footprint.size.name();
        return def.fitsMount(size) ? null
                : String.format(Locale.ROOT, "A %s needs a mount of size %s or bigger; %s is %s", def.displayName(),
                        def.minMountSize, hardpointName(mount), size);
    }

    /** "the nose", "port", "starboard" or "aft", from where the hardpoint faces. */
    public static String hardpointName(WeaponMountComponent m) {
        float deg = (float) Math.toDegrees(m.facing);
        deg = ((deg % 360) + 540) % 360 - 180;
        if (Math.abs(deg) < 45) return "the nose";
        if (Math.abs(deg) > 135) return "aft";
        return deg > 0 ? "port" : "starboard";
    }

    /** The order on {@code ship} with {@code station}, made if there is none; null if it is with another. */
    YardOrdersComponent orders(Entity ship, Entity station) {
        YardOrdersComponent orders = ORDERS_M.get(ship);
        EntityRef ref = EntityRef.of(station);
        if (orders == null) {
            orders = new YardOrdersComponent();
            orders.station = ref;
            ship.add(orders);
        } else if (!ref.equals(orders.station)) {
            if (!orders.isEmpty()) return null;
            orders.station = ref;
        }
        return orders;
    }

    /** The refit under way at {@code hardpoint} of {@code ship}, or null. */
    public YardOrdersComponent.Refit refit(Entity ship, int hardpoint) {
        YardOrdersComponent orders = ORDERS_M.get(ship);
        return orders == null ? null : orders.refit(hardpoint);
    }

    /** True while repairs are ordered on {@code ship}. */
    public boolean repairing(Entity ship) {
        YardOrdersComponent orders = ORDERS_M.get(ship);
        return orders != null && orders.repairs;
    }

    /**
     * True while {@code station}'s yard has work under way on {@code ship}: its berth clamps hold the
     * ship however hard it pulls (round 8). A ship holed by an ace vented hard enough at the berth to
     * tear loose again and again, each time calling the work off, and its crew suffocated there.
     */
    public boolean holds(Entity ship, Entity station) {
        YardOrdersComponent orders = ship == null ? null : ORDERS_M.get(ship);
        if (orders == null || !(orders.repairs || !orders.refits.isEmpty()) || orders.station == null) return false;
        return orders.station.get(uuids) == station;
    }

    /** A line on each piece of yard work under way on {@code ship}, for the screen. */
    public List<String> progress(Entity ship) {
        List<String> out = new ArrayList<>();
        YardOrdersComponent orders = ship == null ? null : ORDERS_M.get(ship);
        if (orders == null) return out;
        if (orders.repairs) {
            out.add(String.format(Locale.ROOT, "Repairing: %d left, %,.0f cr paid so far",
                    damage(ship).size(), orders.repairsPaid));
        }
        refits.progress(ship, orders, out);
        return out;
    }

    // ------------------------------------------------------------------
    // The work
    // ------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        double now = simSeconds.getAsDouble();
        float dt = Double.isNaN(lastTime) ? 0f : (float) Math.max(0, now - lastTime);
        lastTime = now;
        // Round 8: none of a station's hands leaves with a ship (StationHands)
        StationHands.sendHome(crew);
        for (int i = ships.size() - 1; i >= 0; i--) {
            Entity ship = ships.get(i);
            YardOrdersComponent orders = ORDERS_M.get(ship);
            Entity station = orders.station == null ? null : orders.station.get(uuids);
            if (station == null) {
                callOff(ship, orders);
                continue;
            }
            if (!docking.docked(ship, station)) {
                // Come loose: a ship latched again within a few seconds (a seat lost and found, a
                // collar drawing it back in) carries on; one gone elsewhere or for longer does not
                Entity other = docking.dockedTo(ship);
                if (orders.looseSince < 0) orders.looseSince = now;
                if (other != null || now - orders.looseSince > LOOSE_GRACE) callOff(ship, orders);
                continue;
            }
            orders.looseSince = -1;
            if (technicians(station) == 0) restaff(ship, station);
            boolean walkway = walkway(ship, station);
            boolean[][] reach = walkway ? reachable(ship.getComponent(ShipGridComponent.class), crew, station) : null;
            if (orders.repairs) repairs.repairs(ship, station, orders, reach, dt);
            Iterator<YardOrdersComponent.Refit> it = orders.refits.iterator();
            while (it.hasNext()) {
                YardOrdersComponent.Refit r = it.next();
                if (!refits.refitStep(ship, station, r, reach, dt)) it.remove();
            }
        }
    }

    /** The player undocked {@code ship} on purpose: its yard work is called off now, not after {@link #LOOSE_GRACE}. */
    public void undocked(Entity ship) {
        YardOrdersComponent orders = ship == null ? null : ORDERS_M.get(ship);
        if (orders != null) callOff(ship, orders);
    }

    /** The ship left: tasks come off the board, and a part paid for but not fitted is refunded. */
    private void callOff(Entity ship, YardOrdersComponent orders) {
        for (YardOrdersComponent.Refit r : orders.refits) {
            YardRefits.cancel(r);
            if (r.aboard) refits.refund(ship, r);
        }
        boolean any = orders.repairs || !orders.refits.isEmpty();
        orders.refits.clear();
        orders.repairs = false;
        repairs.callOff(ship);
        if (any) notices.add("Undocked: the yard work is called off");
        ship.remove(YardOrdersComponent.class);
    }

    private int count(Entity station, CrewRole role) {
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            CrewMemberComponent c = crew.crew(all.get(i));
            if (c.role == role && c.home == station && c.isConscious()) n++;
        }
        return n;
    }

    public static boolean walkway(Entity ship, Entity station) {
        return YardReach.walkway(ship, station);
    }

    /**
     * The tiles of {@code ship}'s grid crew can walk to from its open links onto {@code from}: where
     * someone coming aboard across the dock can get to.
     */
    public static boolean[][] reachable(ShipGridComponent grid, CrewService crew, Entity from) {
        return YardReach.reachable(grid, crew, from);
    }

    /** The tile in {@code reach} nearest (x, y), where a part for a mount there is set down. */
    public static int[] nearest(boolean[][] reach, int x, int y) {
        return YardReach.nearest(reach, x, y);
    }
}
