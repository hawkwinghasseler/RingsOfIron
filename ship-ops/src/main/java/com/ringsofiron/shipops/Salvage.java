package com.ringsofiron.shipops;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.HullStripTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.aicrew.btree.EvaTasks;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.HullSurface;
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
 * <p>Standing off alongside a craft instead ({@link Boarding#alongside}), with no dock between, the
 * parts on its hull ({@link #onHull}: engines, turrets, anything with a side to space) can be
 * stripped from outside: an engineer goes across on EVA ({@link HullStripTask}), takes the part off
 * where it sits and carries its crate back across and in to the hold. Parts inside the hull wait
 * for a latch, and latched on, parts no one can get to from inside (an engine, say) wait for the
 * craft to stand off and strip them on EVA ({@link SalvageOrdersComponent.Strip#unreachable}).
 *
 * <p>Work stops while the craft are apart and goes on when they latch again or stand off
 * alongside; a crate in hand when they part goes back where it was.
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
     * The craft {@code ship}'s crew would strip: the ship it is latched to, else the one it stands off
     * alongside to strip on EVA ({@link Boarding#boardable}); null if neither.
     */
    public Entity craftFor(Entity ship) {
        Entity docked = boarding.target(ship);
        if (docked != null) return docked;
        Entity other = boarding.boardable(ship);
        return other != null && boarding.alongside(ship, other) ? other : null;
    }

    /** True if {@code ship} strips {@code craft} from outside, standing off alongside it on EVA, not latched on. */
    public boolean byEva(Entity ship, Entity craft) {
        return craft != null && !docking.docked(ship, craft);
    }

    /**
     * Why {@code ship}'s crew can't strip {@code craft}, or null if they can. A check only: an
     * undefended wreck is taken when work is ordered ({@link #mark}, {@link #takeCargo}), not here.
     */
    public String refusal(Entity ship, Entity craft) {
        if (craft == null) return "Latch onto another ship first, or stand off alongside one";
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
        Entity craft = craftFor(ship);
        String no = refusal(ship, craft);
        if (no != null) return no;
        SalvageOrdersComponent orders = ORDERS_M.get(ship);
        EntityRef ref = EntityRef.of(craft);
        SalvageOrdersComponent.Strip s = orders == null ? null : orders.find(ref, x, y);
        if (s != null) {
            String name = partName(s.part);
            if (s.off) return "The " + name + " is already off; it is being carried home";
            cancel(s);
            orders.strips.remove(s);
            return "The " + name + " stays where it is";
        }
        Part part = null;
        for (Part p : parts(craft)) {
            if (p.system.footprint.originX == x && p.system.footprint.originY == y) part = p;
        }
        if (part == null) return "Nothing there to strip";
        boolean eva = byEva(ship, craft);
        if (eva && !onHull(craft, x, y)) return "The " + part.name + " is inside the hull: latch on to strip it";
        if (eva && boarding.evaSuits(ship) == 0) return "No EVA suits aboard to go across in";
        boarding.takeUndefended(ship, craft);
        if (orders == null) {
            orders = new SalvageOrdersComponent();
            ship.add(orders);
        }
        orders.strips.add(new SalvageOrdersComponent.Strip(ref, x, y, part.part));
        return (eva ? "Engineers ordered across on EVA to strip the " : "Engineers ordered to strip the ") + part.name
                + String.format(Locale.ROOT, " (sells for %,.0f cr)", part.price * SALE_SHARE);
    }

    /**
     * True if the system whose mount starts at ({@code x}, {@code y}) on {@code craft} sits on its
     * hull, with a side to space that a crew member on EVA can get at it from.
     */
    public boolean onHull(Entity craft, int x, int y) {
        return hullTile(craft, x, y, new int[2]);
    }

    /** The ring tile outside {@code craft}'s hull the mount at (x, y) is worked from on EVA: {@code out = {x, y}}; false if none. */
    private boolean hullTile(Entity craft, int x, int y, int[] out) {
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        SubsystemComponent system = systemAt(craft, x, y);
        if (grid == null || system == null || crew.eva == null) return false;
        HullSurface hull = crew.eva.hull(grid);
        MountFootprint m = system.footprint;
        for (int ty = m.originY; ty < m.originY + m.height(); ty++) {
            for (int tx = m.originX; tx < m.originX + m.width(); tx++) {
                if (EvaTasks.beside(hull, tx, ty, out) && besideEdge(hull, tx, ty)) return true;
            }
        }
        return false;
    }

    /** True if tile (x, y) has a ring tile straight beyond one of its edges: a face to space, not just a corner. */
    private static boolean besideEdge(HullSurface hull, int x, int y) {
        for (com.ringsofiron.interiorgrid.Side side : com.ringsofiron.interiorgrid.Side.values()) {
            if (hull.outside(x, y, side) != null) return true;
        }
        return false;
    }

    /**
     * How a strip stands, for the screen: "marked", "stripping 40%", "carrying it home", or why it
     * waits.
     */
    public String status(Entity ship, Entity craft, SalvageOrdersComponent.Strip s) {
        boolean eva = byEva(ship, craft);
        if (s.off) {
            if (s.inHand) return eva ? "bringing it home on EVA" : "carrying it home";
            if (!eva && s.unreachable) return "off, but out of reach from inside: stand off alongside to fetch it on EVA";
            if (eva && s.inside) return "off, inside the hull: latch on to fetch it";
            return "off, waiting to be carried home";
        }
        if (!eva && s.unreachable) return "can't be reached from inside: stand off alongside to strip it on EVA";
        if (eva && s.inside) return "inside the hull: latch on to strip it";
        float done = progress(craft, s);
        if (done > 0) return String.format(Locale.ROOT, eva ? "stripping on EVA %.0f%%" : "stripping %.0f%%", 100 * done);
        return eva && s.eva != null && s.eva.status == HullStripTask.Status.CLAIMED ? "crossing over on EVA" : "marked";
    }

    /** True while the player may still call the strip off: the part not yet off. */
    public static boolean cancellable(SalvageOrdersComponent.Strip s) {
        return !s.off;
    }

    /**
     * Where the crates of parts stripped off {@code craft} wait by their mounts to be carried home,
     * tiles ({@code at}: x, y pairs, the middle of each mount), and their cargo ids.
     */
    public void cratesWaiting(Entity craft, FloatArray at, Array<String> parts) {
        if (ships == null || craft == null) return;
        EntityRef ref = null;
        for (int i = 0; i < ships.size(); i++) {
            SalvageOrdersComponent orders = ORDERS_M.get(ships.get(i));
            for (SalvageOrdersComponent.Strip s : orders.strips) {
                if (!s.off || s.inHand) continue;
                if (ref == null) ref = EntityRef.of(craft);
                if (!ref.equals(s.craft)) continue;
                SubsystemComponent system = systemAt(craft, s.x, s.y);
                MountFootprint m = system == null ? null : system.footprint;
                at.add(s.x + (m == null ? 0.5f : m.width() * 0.5f), s.y + (m == null ? 0.5f : m.height() * 0.5f));
                parts.add(s.part);
            }
        }
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
        if (s.carrier != null && !inHand(s)) return false; // lost with its carrier: the strip is over
        boolean docked = docking.docked(ship, craft);
        if (!docked) s.unreachable = false;
        if (docked) s.inside = false;
        if (!boarding.takenBy(craft, ship) || docked && !walkway(ship, craft)
                || !docked && !boarding.alongside(ship, craft)) {
            // Apart, on the rails, or not theirs (yet, or any more): the work waits
            cancel(s);
            return true;
        }
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return true;
        SubsystemComponent system = systemAt(craft, s.x, s.y);
        if (!docked) return stepEva(ship, craft, s, board, system);
        cancelEva(s);
        if (s.carrier != null) return true; // on its way home from an EVA
        if (s.unreachable) return true; // waits for them to stand off and strip it on EVA
        boolean posting = s.off ? s.trip == null || !s.trip.isActive() : s.task == null || !s.task.isActive();
        if (posting && system != null && !reachable(ship, craft, system.footprint)) {
            s.unreachable = true;
            cancel(s);
            notices.add("No one can get to the " + partName(s.part) + " on " + Boarding.name(craft)
                    + " from inside: stand off alongside to strip it on EVA");
            return true;
        }
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

    /** Keeps a strip going standing off alongside {@code craft}: across on EVA for the part, and home with it. */
    private boolean stepEva(Entity ship, Entity craft, SalvageOrdersComponent.Strip s, ShipTaskBoardComponent board,
                            SubsystemComponent system) {
        cancelInside(s);
        if (!s.off && system == null) return false;
        if (s.inHand) return true;
        s.inside = !onHull(craft, s.x, s.y);
        if (s.inside) {
            cancelEva(s);
            return true; // waits for a latch
        }
        if (s.eva != null && s.eva.isActive()) return true;
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (hold == null) return false;
        if (hold.room(s.part, CRATE_VOLUME) < 1 - 1e-6) return true; // waits for room in the bays
        s.eva = new HullStripTask(craft, s.x, s.y, new HullStrip(ship, craft, s));
        if (!board.post(s.eva)) s.eva = null;
        return true;
    }

    /**
     * True while {@code s}'s crate is in its carrier's hands on the way home from an EVA; false, and
     * the strip over, if it was lost with them (dead, or gone).
     */
    private boolean inHand(SalvageOrdersComponent.Strip s) {
        CrewMemberComponent c = crew.crew(s.carrier);
        boolean holding = c != null && c.crate != null && c.crate.job instanceof HullStrip && ((HullStrip) c.crate.job).strip == s;
        if (holding && c.isAlive()) return true;
        if (holding) c.crate = null;
        notices.add("The " + partName(s.part) + " was lost with " + (c == null ? "its carrier" : c.name));
        s.carrier = null;
        s.inHand = false;
        return false;
    }

    /**
     * True if someone crossing from {@code ship} onto {@code craft} by the dock could walk to a tile
     * beside mount {@code m}, through any shut door aboard.
     */
    private boolean reachable(Entity ship, Entity craft, MountFootprint m) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class), there = craft.getComponent(ShipGridComponent.class);
        if (grid == null || there == null) return true;
        GridPathfinder pf = crew.pathfinder(there);
        if (pf == null) return true;
        int x = m.originX, y = m.originY, w = m.width(), h = m.height();
        GridPathfinder.TilePredicate near = (px, py) -> {
            if (!pf.graph.isWalkable(px, py)) return false;
            int dx = px < x ? x - px : px >= x + w ? px - (x + w - 1) : 0;
            int dy = py < y ? y - py : py >= y + h ? py - (y + h - 1) : 0;
            return dx + dy <= 1;
        };
        Array<GridNode> path = new Array<>();
        for (GridLink l : grid.links) {
            if (l.otherShip != craft || !l.canCross()) continue;
            if (pf.nearest(l.ox, l.oy, near, path, (ax, ay, bx, by) -> true)) return true;
        }
        return false;
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
        cancelInside(s);
        cancelEva(s);
    }

    private static void cancelInside(SalvageOrdersComponent.Strip s) {
        if (s.task != null && s.task.status == YardTask.Status.OPEN) s.task.status = YardTask.Status.CANCELLED;
        if (s.trip != null && s.trip.status == CarryTask.Status.OPEN) s.trip.status = CarryTask.Status.CANCELLED;
    }

    /** Calls off the work on EVA unless it is under way: one out on the hull hears it from the job ({@link HullStrip#isOpen}). */
    private static void cancelEva(SalvageOrdersComponent.Strip s) {
        if (s.eva != null && s.eva.status == HullStripTask.Status.OPEN) s.eva.status = HullStripTask.Status.CANCELLED;
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

        @Override
        public void unreachable(Entity worker) {
            // No way to it from inside after all: it waits for an EVA, rather than sending them back and forth
            if (strip.unreachable) return;
            strip.unreachable = true;
            notices.add("No one can get to the " + partName(strip.part) + " on " + Boarding.name(craft)
                    + " from inside: stand off alongside to strip it on EVA");
        }
    }

    /** Across on EVA for one part on a craft's hull: taken off where it sits (if it isn't already off) and carried home. */
    private final class HullStrip implements HullStripTask.Job {
        final Entity ship, craft;
        final SalvageOrdersComponent.Strip strip;
        private HullSurface tileHull;
        private final int[] tile = new int[2];
        private boolean hasTile;
        /** The worker's work rate, from {@link #seconds}. */
        private float rate = 0.1f;

        HullStrip(Entity ship, Entity craft, SalvageOrdersComponent.Strip strip) {
            this.ship = ship;
            this.craft = craft;
            this.strip = strip;
        }

        private boolean alive() {
            SalvageOrdersComponent orders = ORDERS_M.get(ship);
            return orders != null && orders.strips.contains(strip);
        }

        @Override
        public boolean isOpen() {
            return alive() && !strip.inHand && boarding.takenBy(craft, ship) && boarding.alongside(ship, craft)
                    && (strip.off || systemAt(craft, strip.x, strip.y) != null);
        }

        @Override
        public boolean workTile(HullSurface hull, int[] out) {
            if (hull != tileHull) {
                tileHull = hull;
                hasTile = hullTile(craft, strip.x, strip.y, tile);
            }
            out[0] = tile[0];
            out[1] = tile[1];
            return hasTile;
        }

        @Override
        public float seconds(com.ringsofiron.aicrew.CrewDef def) {
            rate = Math.max(1e-3f, def.repairRate);
            if (strip.off) return def.loadTime;
            SubsystemComponent system = systemAt(craft, strip.x, strip.y);
            return system == null ? def.loadTime : Salvage.work(system) / rate + def.loadTime;
        }

        @Override
        public float worked() {
            return strip.off ? 0f : strip.work / rate;
        }

        @Override
        public void work(Entity worker, float seconds) {
            if (strip.off) return;
            SubsystemComponent system = systemAt(craft, strip.x, strip.y);
            if (system == null) return;
            strip.work = Math.min(Salvage.work(system), strip.work + seconds * rate);
        }

        @Override
        public CrewMemberComponent.Crate takeOff(Entity worker) {
            if (!alive()) return null;
            if (!strip.off) strip(ship, craft, strip);
            strip.inHand = true;
            strip.carrier = worker;
            return new CrewMemberComponent.Crate(strip.part, "the " + partName(strip.part), CRATE_VOLUME, (float) CRATE_MASS, this);
        }

        @Override
        public void stowed(Entity worker, CrewMemberComponent.Crate crate, boolean inHold) {
            strip.inHand = false;
            strip.carrier = null;
            if (!alive()) return;
            SalvageOrdersComponent orders = ORDERS_M.get(ship);
            orders.strips.remove(strip);
            if (orders.strips.isEmpty()) ship.remove(SalvageOrdersComponent.class);
            notices.add(inHold
                    ? String.format(Locale.ROOT, "The %s is in the hold (sells for %,.0f cr)", partName(strip.part), salePrice(strip.part))
                    : "No room in the hold for the " + partName(strip.part) + ": it was cast off");
        }

        @Override
        public String doing() {
            return (strip.off ? "picking up the " : "stripping the ") + partName(strip.part);
        }

        @Override
        public String where() {
            return "the " + partName(strip.part);
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
