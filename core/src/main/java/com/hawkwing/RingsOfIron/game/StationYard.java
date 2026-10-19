package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * share of what it lacks.
 *
 * <p>Undocking calls the work off: repairs stop, and a part not yet fitted goes back, refunded.
 * Docked but on the rails together (no walkway), the work goes on in the background at the pace
 * the technicians would keep.
 */
public class StationYard extends EntitySystem {
    /** Credits per whole point of condition restored, by what is repaired. */
    public static final double HULL_COST = 400, WALL_COST = 150, SYSTEM_COST = 3_000, COLLAR_COST = 5_000;
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
    private static final float EPS = 1e-4f;
    /** A technician's pace, for work in the background. */
    private static final float REPAIR_RATE = new CrewDef().repairRate;

    private static final Family ORDERS = Family.all(YardOrdersComponent.class).get();
    private static final ComponentMapper<YardOrdersComponent> ORDERS_M = ComponentMapper.getFor(YardOrdersComponent.class);

    /** Something damaged aboard a ship, and how to put it right. */
    public static final class Damage {
        public enum Kind { HULL, WALL, SYSTEM, COLLAR }

        public final Kind kind;
        /** The tiles the work covers. */
        public final int x, y, w, h;
        final HullPiece piece;
        final Tile tile;
        final EdgeWall wall;
        final SubsystemComponent system;
        final DockingPort port;

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
                case WALL:
                    wall.integrity = after;
                    if (after >= Tile.LEAK_START) wall.patchedAt = 0f;
                    break;
                case SYSTEM:
                    system.health = after;
                    break;
                default:
                    // A collar is whole or torn: it goes back on only when the work is done
                    port.collarIntegrity = after >= 1f - EPS ? 1f : before;
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
            return port;
        }
    }

    private final DockingSystem docking;
    private final CrewService crew;
    private final UuidRegistry uuids;
    private final DoubleSupplier simSeconds;
    private final CombatDefs weapons;
    private ImmutableArray<Entity> ships;
    private double lastTime = Double.NaN;
    /** Repair work in hand: the task for each piece of damage, while it is posted. */
    private final Map<Object, YardTask> repairing = new IdentityHashMap<>();
    /** Partial collar work, kept until the collar is whole again. */
    private final Map<DockingPort, Float> collarWork = new IdentityHashMap<>();
    /** What the yard last said, for the screen; drained by the caller. */
    public final List<String> notices = new ArrayList<>();

    public StationYard(DockingSystem docking, CrewService crew, UuidRegistry uuids, DoubleSupplier simSeconds,
                       CombatDefs weapons) {
        this.docking = docking;
        this.crew = crew;
        this.uuids = uuids;
        this.simSeconds = simSeconds;
        this.weapons = weapons;
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

    /** True if {@code station} can work on {@code ship}: the two are docked and it has technicians. */
    public boolean canWork(Entity ship, Entity station) {
        return ship != null && station != null && docking.docked(ship, station) && technicians(station) > 0;
    }

    /** Everything damaged aboard {@code ship} that a yard can put right. */
    public List<Damage> damage(Entity ship) {
        List<Damage> out = new ArrayList<>();
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return out;
        for (HullPiece p : grid.hullPieces) {
            if (p.integrity >= 1f - EPS) continue;
            int[] t = reachableTileOf(grid, p);
            out.add(new Damage(Damage.Kind.HULL, t[0], t[1], 1, 1, p, null, null, null, null));
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                // A holed deck plate too: it vents straight to space (round 4: the ace's 40 mm rounds hole them)
                if (t.piece == null && (t.isWall() || t.isFloor()) && t.integrity < 1f - EPS) {
                    out.add(new Damage(Damage.Kind.HULL, x, y, 1, 1, null, t, null, null, null));
                }
            }
        }
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall h = x < grid.width ? grid.edgeH[y][x] : null;
                if (h != null && h.integrity < 1f - EPS) {
                    out.add(new Damage(Damage.Kind.WALL, x, Math.min(y, grid.height - 1), 1, 1, null, null, h, null, null));
                }
                EdgeWall v = y < grid.height ? grid.edgeV[y][x] : null;
                if (v != null && v.integrity < 1f - EPS) {
                    out.add(new Damage(Damage.Kind.WALL, Math.min(x, grid.width - 1), y, 1, 1, null, null, v, null, null));
                }
            }
        }
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e.getComponent(SubsystemComponent.class);
                if (s == null || s.health >= 1f - EPS) continue;
                MountFootprint m = s.footprint;
                out.add(new Damage(Damage.Kind.SYSTEM, m.originX, m.originY, m.width(), m.height(), null, null, null, s, null));
            }
        }
        DockingPortsComponent ports = ship.getComponent(DockingPortsComponent.class);
        if (ports != null) {
            for (DockingPort p : ports.ports) {
                if (p.collarIntegrity >= 1f - EPS || p.gridX < 0) continue;
                MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
                int w = m == null ? 1 : m.width(), h = m == null ? 1 : m.height();
                int x = m == null ? p.gridX : m.originX, y = m == null ? p.gridY : m.originY;
                out.add(new Damage(Damage.Kind.COLLAR, x, y, w, h, null, null, null, null, p));
            }
        }
        return out;
    }

    /** Credits to put all of {@code ship}'s damage right. */
    public double repairCost(Entity ship) {
        double total = 0;
        for (Damage d : damage(ship)) total += (1 - d.condition()) * d.cost();
        return total;
    }

    /** Credits a kilogram of each fluid costs at a yard. */
    public static final double PROPELLANT_PRICE = 0.5, OXYGEN_PRICE = 2, NITROGEN_PRICE = 1;

    public static double pricePerKg(Fluid fluid) {
        switch (fluid) {
            case OXYGEN: return OXYGEN_PRICE;
            case NITROGEN: return NITROGEN_PRICE;
            default: return PROPELLANT_PRICE;
        }
    }

    /** Credits to fill every tank aboard {@code ship}. */
    public double refillCost(Entity ship) {
        double total = 0;
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return 0;
        for (SubsystemComponent t : TankSupply.tanks(registry, null)) {
            total += Math.max(0, t.capacity - t.contents) * pricePerKg(t.fluid);
        }
        return total;
    }

    /** Has {@code station}'s yard fill {@code ship}'s tanks, as far as its credits go. Returns what it says. */
    public String orderRefill(Entity ship, Entity station) {
        if (!canWork(ship, station)) return "No yard here to do the work";
        double cost = refillCost(ship);
        if (cost < 0.5) return "The tanks are full";
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        double budget = trader == null ? Double.POSITIVE_INFINITY : trader.credits;
        if (budget <= 0) return "You're out of credits";
        double paid = fill(ship.getComponent(ShipSubsystemRegistryComponent.class), budget);
        if (trader != null) trader.credits = Math.max(0, trader.credits - paid);
        return paid >= cost - 1e-6 ? String.format(Locale.ROOT, "Tanks filled: %,.0f cr", cost)
                : String.format(Locale.ROOT, "Tanks filled to what %,.0f cr buys", paid);
    }

    /**
     * Fills every tank in {@code registry} at the yard's prices, as far as {@code budget} credits
     * go: short of them, every tank gets the same share of what it lacks. Returns the credits spent.
     */
    public static double fill(ShipSubsystemRegistryComponent registry, double budget) {
        if (registry == null || budget <= 0) return 0;
        Array<SubsystemComponent> tanks = TankSupply.tanks(registry, null);
        double cost = 0;
        for (SubsystemComponent t : tanks) cost += Math.max(0, t.capacity - t.contents) * pricePerKg(t.fluid);
        if (cost <= 0) return 0;
        double share = Math.min(1, budget / cost);
        for (SubsystemComponent t : tanks) t.contents += Math.max(0, t.capacity - t.contents) * share;
        return cost * share;
    }

    /** Credits a {@code def} costs at the yard, new or used. */
    public static double price(WeaponDef def, boolean used) {
        return def.price * (used ? USED_PRICE : 1);
    }

    /** Weapons the yard sells, cheapest first; never a rare one. */
    public List<WeaponDef> offers() {
        List<WeaponDef> out = new ArrayList<>();
        for (WeaponDef d : weapons.weapons.values()) {
            if (d.price > 0 && !d.rare) out.add(d);
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
        if (!canWork(ship, station)) return "No yard here to do the work";
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        WeaponDef def = weapons.weapons.get(weaponId);
        if (mounts == null || hardpoint < 0 || hardpoint >= mounts.mounts.size) return "No such hardpoint";
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (def == null || hold == null || hold.amount(PART + weaponId) < 1 - EPS) return "No salvaged one in the hold";
        WeaponMountComponent mount = mounts.mounts.get(hardpoint);
        if (mount.subsystem == null || mount.subsystem.getComponent(SubsystemComponent.class) == null) {
            return "That hardpoint has no mount aboard to fit it to";
        }
        String small = tooSmall(mount, def);
        if (small != null) return small;
        YardOrdersComponent orders = orders(ship, station);
        if (orders == null) return "The yard is working for another station's ship";
        YardOrdersComponent.Refit old = orders.refit(hardpoint);
        if (old != null) {
            if (old.aboard) return "A part is already being fitted there";
            cancel(old);
            orders.refits.remove(old);
        }
        hold.unload(PART + weaponId, 1);
        YardOrdersComponent.Refit r = new YardOrdersComponent.Refit(hardpoint, weaponId, true);
        r.salvaged = true;
        r.aboard = true;
        orders.refits.add(r);
        return String.format(Locale.ROOT, "The technicians are fitting the salvaged %s to %s", def.displayName(), hardpointName(mount));
    }

    /**
     * Puts the ship's hull patches and medical stores back to a full set, as part of a repair order
     * (no charge), and when it is done. Returns true if anything was short.
     */
    static boolean restockPatches(Entity ship) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return false;
        boolean short_ = grid.patchKits < LeakPatches.DEFAULT_KITS || grid.medicalStores < ShipGridComponent.DEFAULT_MEDICAL_STORES;
        grid.patchKits = Math.max(grid.patchKits, LeakPatches.DEFAULT_KITS);
        grid.medicalStores = Math.max(grid.medicalStores, ShipGridComponent.DEFAULT_MEDICAL_STORES);
        return short_;
    }

    /**
     * Has {@code station}'s yard repair everything damaged aboard {@code ship}, patched leaks
     * included, and restock its hull patches and medical stores. With nothing to repair it still
     * restocks them. Returns what it says.
     */
    public String orderRepairs(Entity ship, Entity station) {
        if (!canWork(ship, station)) return "No yard here to do the work";
        double cost = repairCost(ship);
        if (cost <= 0.5) return restockPatches(ship) ? "Nothing aboard needs repairing: stores restocked" : "Nothing aboard needs repairing";
        YardOrdersComponent orders = orders(ship, station);
        if (orders == null) return "The yard is working for another station's ship";
        orders.repairs = true;
        orders.repairsPaid = 0;
        restockPatches(ship);
        return String.format(Locale.ROOT, "Repairs ordered: about %,.0f cr, paid as the work is done", cost);
    }

    /**
     * Has {@code station}'s yard fit {@code weaponId} (new, or used and cheaper but worn) to the
     * ship's hardpoint {@code hardpoint}. Returns what it says.
     */
    public String orderRefit(Entity ship, Entity station, int hardpoint, String weaponId, boolean used) {
        if (!canWork(ship, station)) return "No yard here to do the work";
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        WeaponDef def = weapons.weapons.get(weaponId);
        if (mounts == null || hardpoint < 0 || hardpoint >= mounts.mounts.size) return "No such hardpoint";
        if (def == null || def.price <= 0 || def.rare) return "The yard doesn't sell that";
        WeaponMountComponent mount = mounts.mounts.get(hardpoint);
        if (mount.subsystem == null || mount.subsystem.getComponent(SubsystemComponent.class) == null) {
            return "That hardpoint has no mount aboard to fit it to";
        }
        String small = tooSmall(mount, def);
        if (small != null) return small;
        if (mount.def == def && !used) return "That hardpoint already has one";
        double price = price(def, used);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null && trader.credits < price) return String.format(Locale.ROOT, "%s costs %,.0f cr", def.displayName(), price);
        YardOrdersComponent orders = orders(ship, station);
        if (orders == null) return "The yard is working for another station's ship";
        YardOrdersComponent.Refit old = orders.refit(hardpoint);
        if (old != null) {
            if (old.aboard) return "A part is already being fitted there";
            cancel(old);
            orders.refits.remove(old);
        }
        orders.refits.add(new YardOrdersComponent.Refit(hardpoint, weaponId, used));
        return String.format(Locale.ROOT, "Ordered a %s %s for %s: %,.0f cr when it comes aboard",
                used ? "used" : "new", def.displayName(), hardpointName(mount), price);
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
    private YardOrdersComponent orders(Entity ship, Entity station) {
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

    /** A line on each piece of yard work under way on {@code ship}, for the screen. */
    public List<String> progress(Entity ship) {
        List<String> out = new ArrayList<>();
        YardOrdersComponent orders = ship == null ? null : ORDERS_M.get(ship);
        if (orders == null) return out;
        if (orders.repairs) {
            out.add(String.format(Locale.ROOT, "Repairing: %d left, %,.0f cr paid so far",
                    damage(ship).size(), orders.repairsPaid));
        }
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        for (YardOrdersComponent.Refit r : orders.refits) {
            WeaponDef def = weapons.weapons.get(r.weapon);
            if (def == null || mounts == null || r.hardpoint >= mounts.mounts.size) continue;
            WeaponMountComponent mount = mounts.mounts.get(r.hardpoint);
            SubsystemComponent system = mount.subsystem == null ? null : mount.subsystem.getComponent(SubsystemComponent.class);
            int tiles = system == null ? 1 : system.footprint.width() * system.footprint.height();
            String head = "Refitting " + hardpointName(mount) + " with a " + (r.used ? "used " : "new ") + def.displayName();
            out.add(r.aboard ? String.format(Locale.ROOT, "%s: fitted %d of %d tile%s", head, r.tilesDone, tiles, tiles == 1 ? "" : "s")
                    : head + ": the part is on its way");
        }
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
        for (int i = ships.size() - 1; i >= 0; i--) {
            Entity ship = ships.get(i);
            YardOrdersComponent orders = ORDERS_M.get(ship);
            Entity station = orders.station == null ? null : orders.station.get(uuids);
            if (station == null || !docking.docked(ship, station)) {
                callOff(ship, orders);
                continue;
            }
            boolean walkway = walkway(ship, station);
            boolean[][] reach = walkway ? reachable(ship.getComponent(ShipGridComponent.class), crew, station) : null;
            if (orders.repairs) repairs(ship, station, orders, reach, dt);
            Iterator<YardOrdersComponent.Refit> it = orders.refits.iterator();
            while (it.hasNext()) {
                YardOrdersComponent.Refit r = it.next();
                if (!refitStep(ship, station, r, reach, dt)) it.remove();
            }
        }
    }

    /** {@code reach}: the tiles reachable from the dock, or null without a walkway. */
    private void repairs(Entity ship, Entity station, YardOrdersComponent orders, boolean[][] reach, float dt) {
        List<Damage> damage = damage(ship);
        repairing.values().removeIf(t -> !t.isActive() && t.ship == ship);
        if (damage.isEmpty()) {
            orders.repairs = false;
            // Again at the end: the crew may have patched a leak from the stores while the yard worked
            restockPatches(ship);
            notices.add(String.format(Locale.ROOT, "%s's yard finished the repairs: %,.0f cr",
                    PlayerDocking.name(station), orders.repairsPaid));
            return;
        }
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null && trader.credits < 1) {
            orders.repairs = false;
            notices.add("The yard stopped work: you're out of credits");
            return;
        }
        int techs = technicians(station);
        if (reach == null) {
            // On the rails together: the technicians' pace, without the walking
            float work = techs * REPAIR_RATE * dt;
            for (Damage d : damage) {
                if (work <= 0) break;
                work -= work(ship, orders, d, work);
            }
            return;
        }
        // What no one can reach from the dock is worked from outside, by one technician at a time
        float outside = REPAIR_RATE * dt;
        for (Iterator<Damage> it = damage.iterator(); it.hasNext(); ) {
            Damage d = it.next();
            if (reachable(reach, d)) continue;
            it.remove();
            if (outside > 0) outside -= work(ship, orders, d, outside);
        }
        int open = 0;
        for (YardTask t : repairing.values()) {
            if (t.ship == ship && t.status == YardTask.Status.OPEN) open++;
        }
        ShipTaskBoardComponent board = crew.board(station);
        for (Damage d : damage) {
            if (open >= techs || board == null) break;
            if (repairing.containsKey(d.key())) continue;
            YardTask task = new YardTask(ship, d.x, d.y, d.w, d.h, new Repair(ship, d));
            if (!board.post(task)) continue;
            repairing.put(d.key(), task);
            open++;
        }
    }

    /** Puts up to {@code work} into {@code d}, charging for it; returns the work used. */
    private float work(Entity ship, YardOrdersComponent orders, Damage d, float work) {
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        float points = work / d.workPerPoint();
        if (d.kind == Damage.Kind.COLLAR) {
            // Whole or not at all: count the work until the collar can go back on
            float done = collarWork.getOrDefault(d.port, 0f) + points;
            float need = 1f - d.condition();
            if (done < need) {
                collarWork.put(d.port, done);
                charge(trader, orders, points * d.cost());
                return work;
            }
            collarWork.remove(d.port);
            charge(trader, orders, Math.max(0, need - (done - points)) * d.cost());
            d.restore(1f);
            return work;
        }
        if (trader != null) points = (float) Math.min(points, trader.credits / d.cost());
        float rose = d.restore(points);
        charge(trader, orders, rose * d.cost());
        if (d.kind == Damage.Kind.HULL && d.condition() >= 1f - EPS && d.tile != null) d.tile.bulkheadSealed = false;
        return rose * d.workPerPoint();
    }

    private static void charge(TraderComponent trader, YardOrdersComponent orders, double credits) {
        if (credits <= 0) return;
        if (trader != null) trader.credits = Math.max(0, trader.credits - credits);
        orders.repairsPaid += credits;
    }

    /** One refit's progress; false once it is finished or dropped. */
    private boolean refitStep(Entity ship, Entity station, YardOrdersComponent.Refit r, boolean[][] reach, float dt) {
        boolean walkway = reach != null;
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        WeaponDef def = weapons.weapons.get(r.weapon);
        WeaponMountComponent mount = mounts == null || r.hardpoint >= mounts.mounts.size ? null : mounts.mounts.get(r.hardpoint);
        SubsystemComponent system = mount == null || mount.subsystem == null ? null
                : mount.subsystem.getComponent(SubsystemComponent.class);
        if (def == null || system == null) {
            cancel(r);
            refund(ship, r);
            return false;
        }
        MountFootprint m = system.footprint;
        int tiles = m.width() * m.height();
        if (!r.aboard) {
            if (!walkway) {
                // In the background: the part comes aboard after a loader's trip
                r.tileWork += dt;
                if (r.tileWork >= PART_TRIP_SECONDS) {
                    r.tileWork = 0;
                    partArrives(ship, r, def);
                }
                return !r.weapon.isEmpty();
            }
            if (r.trip == null || !r.trip.isActive()) {
                CargoHoldComponent hold = station.getComponent(CargoHoldComponent.class);
                int x = hold != null && hold.bayTileCount() > 0 ? hold.bayX[0] : 0;
                int y = hold != null && hold.bayTileCount() > 0 ? hold.bayY[0] : 0;
                int[] drop = nearest(reach, m.originX + m.width() / 2, m.originY + m.height() / 2);
                r.trip = new CarryTask(station, x, y, ship, drop[0], drop[1], new PartTrip(ship, r, def));
                ShipTaskBoardComponent board = crew.board(station);
                if (board == null || !board.post(r.trip)) r.trip = null;
            }
            return !r.weapon.isEmpty();
        }
        if (r.tilesDone >= tiles) {
            fit(ship, station, mount, system, r, def);
            return false;
        }
        boolean inside = walkway && reachable(reach, new Damage(Damage.Kind.SYSTEM, m.originX, m.originY, m.width(), m.height(),
                null, null, null, system, null));
        if (!inside) {
            // In the background, or a mount no one can get to from the dock: fitted from outside
            r.tileWork += (walkway ? 1 : technicians(station)) * REPAIR_RATE * dt;
            while (r.tileWork >= FIT_WORK_PER_TILE && r.tilesDone < tiles) {
                r.tileWork -= FIT_WORK_PER_TILE;
                r.tilesDone++;
            }
            return true;
        }
        if (r.task == null || !r.task.isActive()) {
            int tx = m.originX + r.tilesDone % m.width(), ty = m.originY + r.tilesDone / m.width();
            r.task = new YardTask(ship, tx, ty, new Fitting(ship, r, def));
            ShipTaskBoardComponent board = crew.board(station);
            if (board == null || !board.post(r.task)) r.task = null;
        }
        return true;
    }

    private void partArrives(Entity ship, YardOrdersComponent.Refit r, WeaponDef def) {
        double price = price(def, r.used);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null && trader.credits < price) {
            notices.add("Can't pay for the " + def.displayName() + ": the yard keeps it");
            r.weapon = "";
            return;
        }
        if (trader != null) trader.credits -= price;
        r.paid = price;
        r.aboard = true;
        r.tileWork = 0;
        notices.add("The " + def.displayName() + " is aboard; the technicians are fitting it");
    }

    /** The last tile is done: the new weapon goes on, and the old one back to the yard. */
    private void fit(Entity ship, Entity station, WeaponMountComponent mount, SubsystemComponent system,
                     YardOrdersComponent.Refit r, WeaponDef def) {
        WeaponDef old = mount.def;
        mount.def = def;
        mount.cooldown = def.cooldown;
        mount.beamTime = 0f;
        system.health = r.used ? Math.min(system.health, USED_CONDITION) : Math.max(system.health, 1f);
        double credit = old == null || old == def ? 0 : old.price * TRADE_IN;
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null) trader.credits += credit;
        notices.add(String.format(Locale.ROOT, "%s fitted %s with a %s%s", PlayerDocking.name(station), hardpointName(mount),
                def.displayName(), credit > 0 ? String.format(Locale.ROOT, "; %,.0f cr for the old %s", credit, old.displayName()) : ""));
    }

    /** The ship left: tasks come off the board, and a part paid for but not fitted is refunded. */
    private void callOff(Entity ship, YardOrdersComponent orders) {
        for (YardOrdersComponent.Refit r : orders.refits) {
            cancel(r);
            if (r.aboard) refund(ship, r);
        }
        boolean any = orders.repairs || !orders.refits.isEmpty();
        orders.refits.clear();
        orders.repairs = false;
        for (YardTask t : repairing.values()) {
            if (t.ship == ship && t.status == YardTask.Status.OPEN) t.status = YardTask.Status.CANCELLED;
        }
        repairing.values().removeIf(t -> t.ship == ship);
        if (any) notices.add("Undocked: the yard work is called off");
        ship.remove(YardOrdersComponent.class);
    }

    private static void cancel(YardOrdersComponent.Refit r) {
        if (r.trip != null && r.trip.status == CarryTask.Status.OPEN) r.trip.status = CarryTask.Status.CANCELLED;
        if (r.task != null && r.task.status == YardTask.Status.OPEN) r.task.status = YardTask.Status.CANCELLED;
    }

    private void refund(Entity ship, YardOrdersComponent.Refit r) {
        if (r.salvaged) {
            // The salvaged part goes back in the hold
            CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
            if (hold != null && !r.weapon.isEmpty()) hold.load(PART + r.weapon, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
            r.salvaged = false;
            return;
        }
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null && r.paid > 0) trader.credits += r.paid;
        r.paid = 0;
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

    private static boolean walkway(Entity ship, Entity station) {
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        if (g == null) return false;
        for (GridLink l : g.links) {
            if (l.otherShip == station && l.canCross()) return true;
        }
        return false;
    }

    /**
     * The tiles of {@code ship}'s grid crew can walk to from its open links onto {@code from}: where
     * someone coming aboard across the dock can get to.
     */
    static boolean[][] reachable(ShipGridComponent grid, CrewService crew, Entity from) {
        boolean[][] seen = new boolean[grid.height][grid.width];
        com.ringsofiron.aicrew.nav.GridNavGraph nav = crew.pathfinder(grid).graph;
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        for (GridLink l : grid.links) {
            if (l.otherShip == from && l.canCross() && nav.isWalkable(l.x, l.y) && !seen[l.y][l.x]) {
                seen[l.y][l.x] = true;
                open.add(new int[]{l.x, l.y});
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] t = open.poll();
            for (int[] s : steps) {
                int nx = t[0] + s[0], ny = t[1] + s[1];
                if (!grid.inBounds(nx, ny) || seen[ny][nx] || !nav.isWalkable(nx, ny) || !grid.canPass(t[0], t[1], nx, ny)) continue;
                seen[ny][nx] = true;
                open.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    /** True if some tile from which {@code d} is worked (inside it or beside it) is in {@code reach}. */
    private static boolean reachable(boolean[][] reach, Damage d) {
        for (int y = d.y - 1; y <= d.y + d.h; y++) {
            for (int x = d.x - 1; x <= d.x + d.w; x++) {
                boolean corner = (x < d.x || x >= d.x + d.w) && (y < d.y || y >= d.y + d.h);
                if (corner || y < 0 || x < 0 || y >= reach.length || x >= reach[0].length) continue;
                if (reach[y][x]) return true;
            }
        }
        return false;
    }

    /** The tile in {@code reach} nearest (x, y), where a part for a mount there is set down. */
    static int[] nearest(boolean[][] reach, int x, int y) {
        int[] best = {x, y};
        int bestD = Integer.MAX_VALUE;
        for (int ty = 0; ty < reach.length; ty++) {
            for (int tx = 0; tx < reach[ty].length; tx++) {
                int d = Math.abs(tx - x) + Math.abs(ty - y);
                if (reach[ty][tx] && d < bestD) {
                    bestD = d;
                    best = new int[]{tx, ty};
                }
            }
        }
        return best;
    }

    /** A tile of {@code piece} with an open floor tile beside it, where a technician can work it from. */
    private static int[] reachableTileOf(ShipGridComponent grid, HullPiece piece) {
        int[] any = null;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).piece != piece) continue;
                if (any == null) any = new int[]{x, y};
                int[][] n = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
                for (int[] p : n) {
                    Tile t = grid.getTile(p[0], p[1]);
                    if (t != null && t.isFloor()) return new int[]{x, y};
                }
            }
        }
        return any != null ? any : new int[]{piece.originX, piece.originY};
    }

    /** A technician's view of one piece of damage. */
    private final class Repair implements YardTask.Job {
        final Entity ship;
        final Damage damage;

        Repair(Entity ship, Damage damage) {
            this.ship = ship;
            this.damage = damage;
        }

        @Override
        public boolean work(float amount) {
            YardOrdersComponent orders = ORDERS_M.get(ship);
            if (orders == null || !orders.repairs) return true;
            StationYard.this.work(ship, orders, damage, amount);
            return damage.condition() >= 1f - EPS;
        }

        @Override
        public boolean isOpen() {
            YardOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.repairs && damage.condition() < 1f - EPS;
        }

        @Override
        public String what() {
            return damage.what();
        }
    }

    /** A loader bringing a refit part aboard: one crate, paid for as it lands. */
    private final class PartTrip implements CarryTask.Job {
        final Entity ship;
        final YardOrdersComponent.Refit refit;
        final WeaponDef def;
        boolean inHand;

        PartTrip(Entity ship, YardOrdersComponent.Refit refit, WeaponDef def) {
            this.ship = ship;
            this.refit = refit;
            this.def = def;
        }

        @Override
        public String commodity() {
            return PART + def.id;
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
            if (isOpen()) partArrives(ship, refit, def);
        }

        @Override
        public void abandon(double units) {
            inHand = false;
        }

        @Override
        public boolean isOpen() {
            YardOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.refits.contains(refit) && !refit.aboard && !refit.weapon.isEmpty();
        }
    }

    /** A technician fitting one tile of a refit's mount. */
    private final class Fitting implements YardTask.Job {
        final Entity ship;
        final YardOrdersComponent.Refit refit;
        final WeaponDef def;

        Fitting(Entity ship, YardOrdersComponent.Refit refit, WeaponDef def) {
            this.ship = ship;
            this.refit = refit;
            this.def = def;
        }

        @Override
        public boolean work(float amount) {
            refit.tileWork += amount;
            if (refit.tileWork < FIT_WORK_PER_TILE) return false;
            refit.tileWork = 0;
            refit.tilesDone++;
            return true;
        }

        @Override
        public boolean isOpen() {
            YardOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.refits.contains(refit) && refit.aboard;
        }

        @Override
        public String what() {
            return "fitting a " + def.displayName();
        }
    }
}
