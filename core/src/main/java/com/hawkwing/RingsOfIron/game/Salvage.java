package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CarryTask;
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
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.stationsdocking.DockingSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Stripping a taken craft (GDD section 3). Latched onto a craft its crew have taken (or a disabled
 * one no one aboard defends, taken on the spot), the player marks systems on it to strip
 * ({@link #mark}). An engineer goes across and takes each one off its mount, a tile's work at a
 * time ({@link YardTask}); the part then waits by its mount as a crate, {@code part:<id>}, until a
 * crew member who carries brings it home to the ship's cargo bays ({@link CarryTask}). A weapon
 * comes off as {@code part:<weapon id>}, anything else as {@code part:<system>} (reactor, engine...).
 * A stripped system is dead for good. Cargo in the craft's bays can be carried over too
 * ({@link #takeCargo}).
 *
 * <p>Stations buy parts at {@link #SALE_SHARE} of their price ({@link #salePrice}); a salvaged
 * weapon can be fitted at a yard like a used one ({@link StationYard#orderSalvagedRefit}).
 *
 * <p>Work stops while the craft are apart and goes on when they latch again; a crate in hand when
 * they part goes back where it was.
 */
public class Salvage extends EntitySystem {
    /** Prefix of the commodity a part is carried and stowed as; the same as a yard's refit parts. */
    public static final String PART = StationYard.PART;
    /**
     * Share of a part's price a station pays for it: a twentieth, so one stripped laser (2,250 cr)
     * pays under half a first bounty (5,000), and stripping a whole raider about two bounties for
     * many minutes of crew work. At a quarter, one laser paid over twice the bounty (round 2
     * playtest). Placeholder number.
     */
    public static final double SALE_SHARE = 0.05;
    /** Work (at an engineer's repair rate) to strip one tile of a mount. */
    public static final float STRIP_WORK_PER_TILE = 1f;
    /** A crate's bulk, m³, and its weight, kg. */
    public static final double CRATE_VOLUME = 1, CRATE_MASS = 250;

    private static final Family ORDERS = Family.all(SalvageOrdersComponent.class).get();
    private static final ComponentMapper<SalvageOrdersComponent> ORDERS_M = ComponentMapper.getFor(SalvageOrdersComponent.class);

    /** A system that could come off a craft, for the screen. */
    public static final class Part {
        public final SubsystemComponent system;
        /** Commodity the crate is: {@code part:<id>}. */
        public final String part;
        public final String name;
        /** What a new one costs. */
        public final double price;

        Part(SubsystemComponent system, String part, String name, double price) {
            this.system = system;
            this.part = part;
            this.name = name;
            this.price = price;
        }
    }

    private final DockingSystem docking;
    private final CrewService crew;
    private final Boarding boarding;
    private final CargoLoading cargo;
    private final UuidRegistry uuids;
    private final CombatDefs weapons;
    private ImmutableArray<Entity> ships;
    /** What the salvage came to, for the screen; drained by the caller. */
    public final List<String> notices = new ArrayList<>();

    public Salvage(DockingSystem docking, CrewService crew, Boarding boarding, CargoLoading cargo, UuidRegistry uuids,
                   CombatDefs weapons) {
        this.docking = docking;
        this.crew = crew;
        this.boarding = boarding;
        this.cargo = cargo;
        this.uuids = uuids;
        this.weapons = weapons;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(ORDERS);
    }

    // ------------------------------------------------------------------
    // Parts and prices
    // ------------------------------------------------------------------

    /** Credits a new system of each kind costs, for parts that are not weapons. Placeholder numbers. */
    public static double systemPrice(SubsystemComponent.SubsystemType type) {
        switch (type) {
            case REACTOR: return 40_000;
            case SHIELD_EMITTER: return 30_000;
            case ENGINE: return 25_000;
            case LIFE_SUPPORT: return 15_000;
            case HELM: case WEAPONS: return 8_000;
            case AIRLOCK: return 5_000;
            case TANK: return 4_000;
            default: return 0;
        }
    }

    /** What a new {@code part} (a {@code part:<id>} commodity) costs; 0 if it is not a part anyone prices. */
    public double price(String part) {
        if (part == null || !part.startsWith(PART)) return 0;
        String id = part.substring(PART.length());
        WeaponDef w = weapons == null ? null : weapons.weapons.get(id);
        if (w != null) return w.price;
        try {
            return systemPrice(SubsystemComponent.SubsystemType.valueOf(id.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** What a station pays for one {@code part}. */
    public double salePrice(String part) {
        return price(part) * SALE_SHARE;
    }

    /** A name for {@code part} on the screen: "Railgun", "Reactor". */
    public String partName(String part) {
        String id = part.startsWith(PART) ? part.substring(PART.length()) : part;
        WeaponDef w = weapons == null ? null : weapons.weapons.get(id);
        if (w != null) return w.displayName();
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Every system on {@code craft} that could still come off: not stripped, not a bay or a port. */
    public List<Part> parts(Entity craft) {
        List<Part> out = new ArrayList<>();
        ShipSubsystemRegistryComponent registry = craft.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return out;
        SalvagedComponent done = craft.getComponent(SalvagedComponent.class);
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s == null || s.type == SubsystemComponent.SubsystemType.CARGO
                    || s.type == SubsystemComponent.SubsystemType.DOCKING_PORT) continue;
            if (done != null && done.has(s.footprint.originX, s.footprint.originY)) continue;
            WeaponMountComponent mount = mountOf(craft, e);
            if (mount != null) {
                // An empty hardpoint has nothing on it worth the work
                if (mount.def == null) continue;
                out.add(new Part(s, PART + mount.def.id, mount.def.displayName(), mount.def.price));
                continue;
            }
            String id = PART + s.type.name().toLowerCase(Locale.ROOT);
            out.add(new Part(s, id, partName(id), systemPrice(s.type)));
        }
        return out;
    }

    private static WeaponMountComponent mountOf(Entity craft, Entity system) {
        WeaponsComponent w = craft.getComponent(WeaponsComponent.class);
        if (w == null) return null;
        for (WeaponMountComponent m : w.mounts) {
            if (m.subsystem == system) return m;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    /**
     * Why {@code ship}'s crew can't strip {@code craft}, or null if they can. A check only: an
     * undefended wreck is taken when work is ordered ({@link #mark}, {@link #takeCargo}), not here.
     */
    public String refusal(Entity ship, Entity craft) {
        if (craft == null) return "Latch onto another ship first";
        if (!boarding.canTakeUndefended(ship, craft)) {
            BoardedComponent b = Boarding.of(craft);
            if (!ShipDisablement.isDisabled(craft)) return Boarding.name(craft) + " can still fight";
            return b != null && !b.taken ? "Take " + Boarding.name(craft) + " first: its crew still fight"
                    : "Board " + Boarding.name(craft) + " and take it first";
        }
        return null;
    }

    /**
     * Marks the system at mount origin ({@code x}, {@code y}) of the craft {@code ship} is latched
     * to for stripping, or unmarks it if it is already marked. Returns what was ordered, or why not.
     */
    public String mark(Entity ship, int x, int y) {
        Entity craft = boarding.target(ship);
        String no = refusal(ship, craft);
        if (no != null) return no;
        boarding.takeUndefended(ship, craft);
        Part part = null;
        for (Part p : parts(craft)) {
            if (p.system.footprint.originX == x && p.system.footprint.originY == y) part = p;
        }
        if (part == null) return "Nothing there to strip";
        SalvageOrdersComponent orders = ORDERS_M.get(ship);
        if (orders == null) {
            orders = new SalvageOrdersComponent();
            ship.add(orders);
        }
        EntityRef ref = EntityRef.of(craft);
        SalvageOrdersComponent.Strip s = orders.find(ref, x, y);
        if (s != null) {
            if (s.off) return "The " + part.name + " is already off; it is being carried home";
            cancel(s);
            orders.strips.remove(s);
            return "The " + part.name + " stays where it is";
        }
        orders.strips.add(new SalvageOrdersComponent.Strip(ref, x, y, part.part));
        return "Engineers ordered to strip the " + part.name + String.format(Locale.ROOT, " (sells for %,.0f cr)",
                part.price * SALE_SHARE);
    }

    /** The strip ordered for the system at mount origin (x, y) of {@code craft}, or null. */
    public SalvageOrdersComponent.Strip strip(Entity ship, Entity craft, int x, int y) {
        SalvageOrdersComponent orders = ORDERS_M.get(ship);
        return orders == null || craft == null ? null : orders.find(EntityRef.of(craft), x, y);
    }

    /** Work done on a strip, 0 to 1. */
    public float progress(Entity craft, SalvageOrdersComponent.Strip s) {
        SubsystemComponent sys = craft == null ? null : systemAt(craft, s.x, s.y);
        if (sys == null) return s.off ? 1f : 0f;
        return Math.min(1f, s.work / work(sys));
    }

    /**
     * Has {@code ship}'s crew carry every unit of {@code commodity} in the bays of the craft it is
     * latched to over to its own. Returns what was ordered, or why not.
     */
    public String takeCargo(Entity ship, String commodity) {
        Entity craft = boarding.target(ship);
        String no = refusal(ship, craft);
        if (no != null) return no;
        boarding.takeUndefended(ship, craft);
        CargoHoldComponent hold = craft.getComponent(CargoHoldComponent.class);
        double units = hold == null ? 0 : hold.amount(commodity);
        if (units <= 1e-6) return "No " + commodity + " aboard " + Boarding.name(craft);
        if (!cargo.canCarry(ship, craft)) return "No one aboard to carry it";
        double ordered = cargo.order(ship, craft, commodity, units, true);
        return String.format(Locale.ROOT, "Crew ordered to carry %,.0f %s over", ordered, commodity);
    }

    // ------------------------------------------------------------------
    // Each tick
    // ------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            SalvageOrdersComponent orders = ORDERS_M.get(ship);
            Iterator<SalvageOrdersComponent.Strip> it = orders.strips.iterator();
            while (it.hasNext()) {
                SalvageOrdersComponent.Strip s = it.next();
                if (!step(ship, s)) {
                    cancel(s);
                    it.remove();
                }
            }
        }
    }

    /** Keeps one strip going; false once it is finished or can never be. */
    private boolean step(Entity ship, SalvageOrdersComponent.Strip s) {
        Entity craft = s.craft == null ? null : s.craft.get(uuids);
        if (craft == null) return false;
        if (!docking.docked(ship, craft) || !walkway(ship, craft) || !boarding.takenBy(craft, ship)) {
            // Apart, on the rails, or not theirs (yet, or any more): the work waits
            cancel(s);
            return true;
        }
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return true;
        SubsystemComponent system = systemAt(craft, s.x, s.y);
        if (!s.off) {
            if (system == null) return false;
            if (s.task == null || !s.task.isActive()) {
                MountFootprint m = system.footprint;
                s.task = new YardTask(craft, m.originX, m.originY, m.width(), m.height(), new Stripping(ship, craft, s));
                if (!board.post(s.task)) s.task = null;
            }
            return true;
        }
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (hold == null) return false;
        if (s.trip == null || !s.trip.isActive()) {
            if (hold.room(s.part, CRATE_VOLUME) < 1 - 1e-6) return true; // waits for room in the bays
            s.trip = CarryTask.fromTile(craft, s.x, s.y, ship, new Crate(ship, s));
            if (!board.post(s.trip)) s.trip = null;
        }
        return true;
    }

    /** The part is off: its system is dead, and the crate waits by the mount. */
    private void strip(Entity ship, Entity craft, SalvageOrdersComponent.Strip s) {
        SubsystemComponent system = systemAt(craft, s.x, s.y);
        if (system != null) {
            system.health = 0f;
            system.isOperational = false;
            system.switchedOn = false;
            ShipSubsystemRegistryComponent registry = craft.getComponent(ShipSubsystemRegistryComponent.class);
            for (Entity e : registry.subsystems) {
                if (e != null && e.getComponent(SubsystemComponent.class) == system) {
                    WeaponMountComponent mount = mountOf(craft, e);
                    if (mount != null) {
                        mount.def = null;
                        mount.target = null;
                    }
                }
            }
        }
        SalvagedComponent done = craft.getComponent(SalvagedComponent.class);
        if (done == null) {
            done = new SalvagedComponent();
            craft.add(done);
        }
        done.add(s.x, s.y);
        s.off = true;
        notices.add("The " + partName(s.part) + " is off " + Boarding.name(craft) + "; it's being carried home");
    }

    private void stowed(Entity ship, SalvageOrdersComponent.Strip s) {
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        double in = hold == null ? 0 : hold.load(s.part, 1, CRATE_VOLUME, CRATE_MASS);
        if (in < 1 - 1e-6) {
            if (in > 0) hold.unload(s.part, in);
            return; // no room after all: it is set down and fetched again once there is
        }
        SalvageOrdersComponent orders = ORDERS_M.get(ship);
        if (orders != null) orders.strips.remove(s);
        if (orders != null && orders.strips.isEmpty()) ship.remove(SalvageOrdersComponent.class);
        notices.add(String.format(Locale.ROOT, "The %s is in the hold (sells for %,.0f cr)", partName(s.part), salePrice(s.part)));
    }

    private static void cancel(SalvageOrdersComponent.Strip s) {
        if (s.task != null && s.task.status == YardTask.Status.OPEN) s.task.status = YardTask.Status.CANCELLED;
        if (s.trip != null && s.trip.status == CarryTask.Status.OPEN) s.trip.status = CarryTask.Status.CANCELLED;
    }

    /** Work to strip {@code system}: its mount's tiles. */
    private static float work(SubsystemComponent system) {
        return STRIP_WORK_PER_TILE * system.footprint.width() * system.footprint.height();
    }

    /** The system whose mount starts at (x, y) on {@code craft}, or null. */
    static SubsystemComponent systemAt(Entity craft, int x, int y) {
        ShipSubsystemRegistryComponent registry = craft.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.footprint.originX == x && s.footprint.originY == y) return s;
        }
        return null;
    }

    private static boolean walkway(Entity ship, Entity craft) {
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        if (g == null) return false;
        for (GridLink l : g.links) {
            if (l.otherShip == craft && l.canCross()) return true;
        }
        return false;
    }

    /** An engineer taking a system off its mount. */
    private final class Stripping implements YardTask.Job {
        final Entity ship, craft;
        final SalvageOrdersComponent.Strip strip;

        Stripping(Entity ship, Entity craft, SalvageOrdersComponent.Strip strip) {
            this.ship = ship;
            this.craft = craft;
            this.strip = strip;
        }

        @Override
        public boolean work(float amount) {
            SubsystemComponent system = systemAt(craft, strip.x, strip.y);
            if (system == null) return true;
            strip.work += amount;
            if (strip.work < Salvage.work(system)) return false;
            strip(ship, craft, strip);
            return true;
        }

        @Override
        public boolean isOpen() {
            SalvageOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.strips.contains(strip) && !strip.off;
        }

        @Override
        public String what() {
            return "stripping the " + partName(strip.part);
        }
    }

    /** Carrying a stripped part home: one crate. */
    private final class Crate implements CarryTask.Job {
        final Entity ship;
        final SalvageOrdersComponent.Strip strip;

        Crate(Entity ship, SalvageOrdersComponent.Strip strip) {
            this.ship = ship;
            this.strip = strip;
        }

        @Override
        public String commodity() {
            return strip.part;
        }

        @Override
        public double take(double volume) {
            if (!isOpen() || strip.inHand || volume < CRATE_VOLUME) return 0;
            strip.inHand = true;
            return 1;
        }

        @Override
        public void deliver(double units) {
            strip.inHand = false;
            if (isOpen()) stowed(ship, strip);
        }

        @Override
        public void abandon(double units) {
            strip.inHand = false;
        }

        @Override
        public boolean isOpen() {
            SalvageOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.strips.contains(strip) && strip.off;
        }
    }
}
