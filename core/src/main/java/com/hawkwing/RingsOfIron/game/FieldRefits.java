package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.hawkwing.RingsOfIron.game.FieldRefitComponent.Order;
import com.hawkwing.RingsOfIron.game.FieldRefitComponent.Stage;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaEvent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Mounting a salvaged weapon from a ship's hold on one of its hardpoints away from a yard: work
 * outside for its crew ({@link OutsideTask}, {@link CrewTask.Kind#MOUNT_PART}), ordered from the
 * yard window where no yard can work on the ship ({@link YardWork}). An engineer lifts the weapon's
 * crate out of the hold, suits up and takes it out through an airlock (its {@link Salvage#CRATE_MASS}
 * with theirs: their hold on the hull, their line and their jetpack bear it), along the hull to the
 * ring tile nearest the hardpoint's turret mount, and clips it on there; then unbolts the weapon on
 * the hardpoint and bolts the new one on, {@link CrewDef#mountTime} for each tile of the mount at
 * their suited pace. Called away part way, they leave the crate clipped on and the work done
 * stands, for whoever goes out next. Mounted, the weapon is in at a used part's condition ({@link
 * StationYard#USED_CONDITION}), and the one it replaced comes off in a crate they bring in to the
 * hold.
 *
 * <p>Ordered at most one per hardpoint, from the crates in the hold not already promised to
 * another. Called off while its crate is still inside, it goes back in the hold; once out at the
 * hardpoint, the work goes on. A crate lost with whoever had it (left behind adrift) ends the order.
 */
public final class FieldRefits {
    private static final Family SHIPS = Family.all(FieldRefitComponent.class).get();
    private static final float EPS = 1e-4f;
    /** The mounting time the progress shown is reckoned against. */
    private static final float MOUNT_TIME = new CrewDef().mountTime;

    private final GameWorld world;
    private final ImmutableArray<Entity> ships;
    /** The job of each order, while it stands. */
    private final Map<Order, Mount> jobs = new IdentityHashMap<>();
    private final Vector2 hug = new Vector2();

    public FieldRefits(GameWorld world) {
        this.world = world;
        ships = world.engine.getEcsEngine().getEntitiesFor(SHIPS);
        world.crew.eva.events.add((signal, e) -> {
            if (e.kind == EvaEvent.Kind.LEFT_BEHIND) lostWith(e.crew);
        });
    }

    /**
     * Has the crew of {@code ship} mount a salvaged {@code weaponId} out of its hold on hardpoint
     * {@code hardpoint}, from outside, in place of the weapon there. Returns what it says.
     */
    public String order(Entity ship, int hardpoint, String weaponId) {
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        WeaponDef def = world.combatDefs.weapons.get(weaponId);
        if (mounts == null || hardpoint < 0 || hardpoint >= mounts.mounts.size) return "No such hardpoint";
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        String part = StationYard.PART + weaponId;
        if (def == null || hold == null || hold.amount(part) < 1 - EPS) return "No salvaged one in the hold";
        WeaponMountComponent mount = mounts.mounts.get(hardpoint);
        if (turretMount(mount) == null) return "That hardpoint has no mount aboard to fit it to";
        String small = StationYard.tooSmall(mount, def);
        if (small != null) return small;
        if (ship.getComponent(ShipTaskBoardComponent.class) == null) return "No crew aboard to do the work";
        FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
        Order was = refits == null ? null : refits.order(hardpoint);
        if (was != null && was.stage != Stage.HOLD) return "A part is already being mounted there";
        int promised = 0;
        if (refits != null) {
            for (Order o : refits.orders) {
                if (o != was && o.weapon.equals(weaponId) && o.stage == Stage.HOLD) promised++;
            }
        }
        if (hold.amount(part) < promised + 1 - EPS) {
            return "Every salvaged " + def.displayName() + " in the hold is to be mounted already";
        }
        if (refits == null) ship.add(refits = new FieldRefitComponent());
        if (was != null) refits.orders.remove(was);
        refits.orders.add(new Order(hardpoint, weaponId));
        return String.format(Locale.ROOT, "An engineer will take the salvaged %s out on EVA and mount it on %s",
                def.displayName(), where(mount));
    }

    /** Calls off the order on {@code ship}'s hardpoint {@code hardpoint} while its part is still inside. Returns what it says. */
    public String callOff(Entity ship, int hardpoint) {
        FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
        Order o = refits == null ? null : refits.order(hardpoint);
        if (o == null) return "Nothing is to be mounted there";
        if (o.stage != Stage.HOLD && o.stage != Stage.CARRIED) return "The part is out at the hardpoint already";
        // A crate in hand goes back in the hold: whoever has it carries it back there
        refits.orders.remove(o);
        return "The mounting on " + where(mount(ship, hardpoint)) + " is called off";
    }

    /** True if a part of an order on {@code ship} is out of the hold: in hand, or out at its hardpoint. */
    public static boolean partsOut(Entity ship) {
        FieldRefitComponent refits = ship == null ? null : ship.getComponent(FieldRefitComponent.class);
        if (refits == null) return false;
        for (Order o : refits.orders) {
            if (o.stage != Stage.HOLD) return true;
        }
        return false;
    }

    /** The order on {@code ship}'s hardpoint {@code hardpoint}, or null. */
    public Order order(Entity ship, int hardpoint) {
        FieldRefitComponent refits = ship == null ? null : ship.getComponent(FieldRefitComponent.class);
        return refits == null ? null : refits.order(hardpoint);
    }

    /** How the order on {@code ship}'s hardpoint {@code hardpoint} is going, for the yard window; empty with none. */
    public String progress(Entity ship, int hardpoint) {
        Order o = order(ship, hardpoint);
        if (o == null) return "";
        WeaponDef def = world.combatDefs.weapons.get(o.weapon);
        String name = def == null ? o.weapon : def.displayName();
        String who = carrierName(o);
        switch (o.stage) {
            case HOLD:
                return name + ": in the hold, for an engineer to take out";
            case CARRIED:
                return name + ": " + who + " is taking it out";
            case AT_MOUNT: {
                MountFootprint m = footprint(mount(ship, hardpoint));
                float seconds = m == null ? 1f : MOUNT_TIME * m.width() * m.height();
                return String.format(Locale.ROOT, "%s: at the hardpoint, %.0f%% mounted", name, 100f * Math.min(1f, o.worked / seconds));
            }
            default: {
                WeaponDef old = world.combatDefs.weapons.get(o.old);
                return name + " mounted: " + who + " is bringing the old " + (old == null ? o.old : old.displayName()) + " in";
            }
        }
    }

    /** Where the crates clipped on outside {@code ship}'s hardpoints are, tiles ({@code at}: x, y pairs), and their cargo ids. */
    public void cratesOutside(Entity ship, FloatArray at, Array<String> commodities) {
        FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (refits == null || grid == null) return;
        HullSurface hull = world.crew.eva.hull(grid);
        int[] tile = new int[2];
        for (Order o : refits.orders) {
            Mount job = jobs.get(o);
            if (o.stage != Stage.AT_MOUNT || job == null || !job.workTile(hull, tile)) continue;
            hull.hug(tile[0], tile[1], hug);
            at.add(hug.x, hug.y);
            commodities.add(StationYard.PART + o.weapon);
        }
    }

    /** Once a frame: each order's job on its ship's board, and crates gone astray set right. */
    public void update() {
        for (Mount m : jobs.values()) m.seen = false;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
            ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
            for (Iterator<Order> it = refits.orders.iterator(); it.hasNext(); ) {
                Order o = it.next();
                Mount job = jobs.get(o);
                if (job == null) jobs.put(o, job = new Mount(ship, o));
                if (!job.check()) {
                    it.remove();
                    continue;
                }
                job.seen = true;
                MountFootprint m = footprint(job.mount());
                if (board != null && job.isOpen()) board.post(new OutsideTask(CrewTask.Kind.MOUNT_PART, m.originX, m.originY, job));
            }
        }
        for (Iterator<Mount> it = jobs.values().iterator(); it.hasNext(); ) {
            if (!it.next().seen) it.remove();
        }
    }

    /** {@code crew} was left behind adrift: a crate they had goes with them. */
    private void lostWith(Entity crew) {
        for (Mount job : jobs.values()) {
            Order o = job.order;
            if (o.carrier != crew || o.stage != Stage.CARRIED && o.stage != Stage.BRINGING_OLD) continue;
            FieldRefitComponent refits = job.ship.getComponent(FieldRefitComponent.class);
            if (refits != null) refits.orders.remove(o);
            if (job.ship != world.getPlayer()) continue;
            CrewMemberComponent c = world.crew.crew(crew);
            WeaponDef def = world.combatDefs.weapons.get(o.stage == Stage.CARRIED ? o.weapon : o.old);
            String name = def == null ? "part" : def.displayName();
            world.notices.post((o.stage == Stage.CARRIED ? "The salvaged " : "The old ") + name + " was lost with "
                    + (c == null ? "them" : c.name), NoticeLog.Kind.WARNING);
        }
    }

    private String carrierName(Order o) {
        CrewMemberComponent c = o.carrier == null ? null : world.crew.crew(o.carrier);
        return c == null ? "the crew" : c.name;
    }

    private static WeaponMountComponent mount(Entity ship, int hardpoint) {
        WeaponsComponent w = ship.getComponent(WeaponsComponent.class);
        return w == null || hardpoint < 0 || hardpoint >= w.mounts.size ? null : w.mounts.get(hardpoint);
    }

    /** The turret mount aboard {@code mount} is wired to, or null. */
    static SubsystemComponent turretMount(WeaponMountComponent mount) {
        return mount == null || mount.subsystem == null ? null : mount.subsystem.getComponent(SubsystemComponent.class);
    }

    private static MountFootprint footprint(WeaponMountComponent mount) {
        SubsystemComponent s = turretMount(mount);
        return s == null ? null : s.footprint;
    }

    /** "the nose hardpoint", "the port hardpoint". */
    static String where(WeaponMountComponent mount) {
        if (mount == null) return "the hardpoint";
        String name = StationYard.hardpointName(mount);
        return (name.startsWith("the ") ? name : "the " + name) + " hardpoint";
    }

    /** Mounting one salvaged weapon from outside. */
    private final class Mount implements OutsideTask.Job {
        final Entity ship;
        final Order order;
        boolean seen;
        /** The ring tile it is worked from, for {@link #tileHull}. */
        private HullSurface tileHull;
        private final int[] tile = new int[2];
        private boolean hasTile;

        Mount(Entity ship, Order order) {
            this.ship = ship;
            this.order = order;
        }

        WeaponMountComponent mount() {
            return FieldRefits.mount(ship, order.hardpoint);
        }

        WeaponDef def() {
            return world.combatDefs.weapons.get(order.weapon);
        }

        boolean alive() {
            FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
            return refits != null && refits.orders.contains(order);
        }

        CargoHoldComponent hold() {
            return ship.getComponent(CargoHoldComponent.class);
        }

        /**
         * Sets right a crate gone astray, in the hand of nobody (after a load, or its carrier aboard
         * out cold or dead), back in the hold. False once the order is over: its weapon or hardpoint
         * gone, or no salvaged one left in the hold.
         */
        boolean check() {
            CargoHoldComponent hold = hold();
            if (def() == null || footprint(mount()) == null) {
                if (order.stage != Stage.HOLD && hold != null) {
                    hold.load(StationYard.PART + (order.stage == Stage.BRINGING_OLD ? order.old : order.weapon), 1,
                            Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
                }
                return false;
            }
            switch (order.stage) {
                case HOLD:
                    if (hold != null && hold.amount(StationYard.PART + order.weapon) >= 1 - EPS) return true;
                    if (ship == world.getPlayer()) {
                        world.notices.post("No salvaged " + def().displayName() + " left in the hold: the mounting on "
                                + FieldRefits.where(mount()) + " is called off", NoticeLog.Kind.WARNING);
                    }
                    return false;
                case CARRIED:
                case BRINGING_OLD: {
                    if (!astray()) return true;
                    boolean old = order.stage == Stage.BRINGING_OLD;
                    if (hold != null) {
                        hold.load(StationYard.PART + (old ? order.old : order.weapon), 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
                    }
                    CrewMemberComponent c = order.carrier == null ? null : world.crew.crew(order.carrier);
                    if (c != null && c.crate != null && c.crate.job == this) c.crate = null;
                    order.carrier = null;
                    if (old) return false;
                    order.stage = Stage.HOLD;
                    return true;
                }
                default:
                    return true;
            }
        }

        /** Nobody has the crate in hand: its carrier gone, or aboard and down, or no longer holding it. */
        private boolean astray() {
            CrewMemberComponent c = order.carrier == null ? null : world.crew.crew(order.carrier);
            if (c == null || c.crate == null || c.crate.job != this) return true;
            return !c.isConscious() && EvaSystem.of(order.carrier) == null;
        }

        @Override
        public boolean isOpen() {
            if (!alive()) return false;
            switch (order.stage) {
                case HOLD:
                case AT_MOUNT:
                    return true;
                case CARRIED: {
                    // Only for the one taking it out: put down, it goes back in the hold first
                    CrewMemberComponent c = order.carrier == null ? null : world.crew.crew(order.carrier);
                    return c != null && c.crate != null && c.crate.job == this && c.task instanceof OutsideTask
                            && ((OutsideTask) c.task).job == this && c.task.isActive();
                }
                default:
                    return false;
            }
        }

        @Override
        public boolean workTile(HullSurface hull, int[] out) {
            if (hull != tileHull) {
                tileHull = hull;
                MountFootprint m = footprint(mount());
                int[] n = m == null ? null : hull.nearest(m.originX + m.width() / 2f, m.originY + m.height() / 2f);
                hasTile = n != null;
                if (hasTile) {
                    tile[0] = n[0];
                    tile[1] = n[1];
                }
            }
            out[0] = tile[0];
            out[1] = tile[1];
            return hasTile;
        }

        @Override
        public float seconds(CrewDef def) {
            MountFootprint m = footprint(mount());
            return def.mountTime * (m == null ? 1 : m.width() * m.height());
        }

        @Override
        public String part() {
            return order.stage == Stage.HOLD || order.stage == Stage.CARRIED ? StationYard.PART + order.weapon : null;
        }

        @Override
        public CrewMemberComponent.Crate take(Entity worker) {
            CargoHoldComponent hold = hold();
            String id = StationYard.PART + order.weapon;
            if (!alive() || order.stage != Stage.HOLD || hold == null || hold.amount(id) < 1 - EPS) return null;
            hold.unload(id, 1);
            order.stage = Stage.CARRIED;
            order.carrier = worker;
            return new CrewMemberComponent.Crate(id, "the salvaged " + def().displayName(), Salvage.CRATE_VOLUME,
                    (float) Salvage.CRATE_MASS, this);
        }

        @Override
        public void setDown(Entity worker, CrewMemberComponent.Crate crate) {
            order.stage = Stage.AT_MOUNT;
            order.carrier = null;
        }

        @Override
        public void stowed(Entity worker, CrewMemberComponent.Crate crate, boolean inHold) {
            FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
            if (alive() && order.carrier == worker) {
                if (order.stage == Stage.BRINGING_OLD || !inHold) {
                    refits.orders.remove(order);
                } else if (order.stage == Stage.CARRIED) {
                    order.stage = Stage.HOLD;
                    order.carrier = null;
                }
            }
            if (!inHold && ship == world.getPlayer()) {
                world.notices.post("No room in the hold for " + crate.name + ": the crew cast it off", NoticeLog.Kind.WARNING);
            }
        }

        @Override
        public float worked() {
            return order.worked;
        }

        @Override
        public void work(Entity worker, float seconds) {
            order.worked += seconds;
        }

        @Override
        public void done(Entity worker) {
            WeaponMountComponent mount = mount();
            SubsystemComponent system = turretMount(mount);
            WeaponDef def = def();
            if (system == null || def == null || !alive()) return;
            WeaponDef old = mount.def;
            mount.def = def;
            mount.cooldown = def.cooldown;
            mount.beamTime = 0f;
            // A launcher comes out loaded, as from a yard
            mount.ammo = -1;
            system.health = Math.min(system.health, StationYard.USED_CONDITION);
            CrewMemberComponent c = worker == null ? null : world.crew.crew(worker);
            FieldRefitComponent refits = ship.getComponent(FieldRefitComponent.class);
            String said = String.format(Locale.ROOT, "%s mounted the salvaged %s on %s", c == null ? "The crew have" : c.name + " has",
                    def.displayName(), FieldRefits.where(mount));
            if (old == null) {
                refits.orders.remove(order);
            } else if (c == null) {
                // Nobody to bring it in: it goes in the hold all the same
                refits.orders.remove(order);
                CargoHoldComponent hold = hold();
                if (hold != null) hold.load(StationYard.PART + old.id, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
            } else {
                order.stage = Stage.BRINGING_OLD;
                order.old = old.id;
                order.carrier = worker;
                c.crate = new CrewMemberComponent.Crate(StationYard.PART + old.id, "the old " + old.displayName(),
                        Salvage.CRATE_VOLUME, (float) Salvage.CRATE_MASS, this);
                said += ", and is bringing the old " + old.displayName() + " in to the hold";
            }
            if (ship == world.getPlayer()) world.notices.post(said, NoticeLog.Kind.INFO);
        }

        @Override
        public String doing() {
            WeaponDef def = def();
            return "mounting the salvaged " + (def == null ? "part" : def.displayName()) + " on " + FieldRefits.where(mount());
        }

        @Override
        public String where() {
            return FieldRefits.where(mount());
        }
    }
}
