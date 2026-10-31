package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ringsofiron.shipops.StationYard.EPS;
import static com.ringsofiron.shipops.StationYard.ORDERS_M;
import static com.ringsofiron.shipops.StationYard.REPAIR_RATE;

/**
 * {@link StationYard}'s repairs: the survey of what is damaged aboard, and the technicians' work on
 * it, a {@link YardTask} per piece of damage, paid for as it is done.
 */
final class YardRepairs {
    private final StationYard yard;
    private final CrewService crew;
    private final UuidRegistry uuids;
    /** Repair work in hand: the task for each piece of damage, while it is posted. */
    private final Map<Object, YardTask> repairing = new IdentityHashMap<>();
    /** Partial collar work, kept until the collar is whole again. */
    private final Map<DockingPort, Float> collarWork = new IdentityHashMap<>();

    YardRepairs(StationYard yard, CrewService crew, UuidRegistry uuids) {
        this.yard = yard;
        this.crew = crew;
        this.uuids = uuids;
    }

    /** Everything damaged aboard {@code ship} that a yard can put right. */
    List<StationYard.Damage> damage(Entity ship) {
        List<StationYard.Damage> out = new ArrayList<>();
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return out;
        for (HullPiece p : grid.hullPieces) {
            if (p.integrity >= 1f - EPS) continue;
            int[] t = YardReach.reachableTileOf(grid, p);
            out.add(new StationYard.Damage(StationYard.Damage.Kind.HULL, t[0], t[1], 1, 1, p, null, null, null, null));
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                // A holed deck plate too: it vents straight to space (round 4: the ace's 40 mm rounds hole them)
                if (t.piece == null && (t.isWall() || t.isFloor()) && t.integrity < 1f - EPS) {
                    out.add(new StationYard.Damage(StationYard.Damage.Kind.HULL, x, y, 1, 1, null, t, null, null, null));
                }
            }
        }
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall h = x < grid.width ? grid.edgeH[y][x] : null;
                if (h != null && h.integrity < 1f - EPS) {
                    out.add(new StationYard.Damage(StationYard.Damage.Kind.WALL, x, Math.min(y, grid.height - 1), 1, 1, null, null, h, null, null));
                }
                EdgeWall v = y < grid.height ? grid.edgeV[y][x] : null;
                if (v != null && v.integrity < 1f - EPS) {
                    out.add(new StationYard.Damage(StationYard.Damage.Kind.WALL, Math.min(x, grid.width - 1), y, 1, 1, null, null, v, null, null));
                }
            }
        }
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e.getComponent(SubsystemComponent.class);
                if (s == null || s.health >= 1f - EPS) continue;
                MountFootprint m = s.footprint;
                out.add(new StationYard.Damage(StationYard.Damage.Kind.SYSTEM, m.originX, m.originY, m.width(), m.height(), null, null, null, s, null));
            }
        }
        DockingPortsComponent ports = ship.getComponent(DockingPortsComponent.class);
        if (ports != null) {
            for (DockingPort p : ports.ports) {
                if (p.collarIntegrity >= 1f - EPS || p.gridX < 0) continue;
                MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
                int w = m == null ? 1 : m.width(), h = m == null ? 1 : m.height();
                int x = m == null ? p.gridX : m.originX, y = m == null ? p.gridY : m.originY;
                out.add(new StationYard.Damage(StationYard.Damage.Kind.COLLAR, x, y, w, h, null, null, null, null, p));
            }
        }
        return out;
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

    /** {@code reach}: the tiles reachable from the dock, or null without a walkway. */
    void repairs(Entity ship, Entity station, YardOrdersComponent orders, boolean[][] reach, float dt) {
        List<StationYard.Damage> damage = damage(ship);
        repairing.values().removeIf(t -> !t.isActive() && t.ship == ship);
        if (damage.isEmpty()) {
            orders.repairs = false;
            // Again at the end: the crew may have patched a leak from the stores while the yard worked
            restockPatches(ship);
            // The hull whole again, the station's air fills the cabin back up, on the same bill
            TraderComponent trader = ship.getComponent(TraderComponent.class);
            double factor = yard.priceFactor.applyAsDouble(station);
            double budget = trader == null ? Double.POSITIVE_INFINITY : trader.credits / factor;
            charge(trader, orders, YardSupplies.topUpAir(ship.getComponent(ShipGridComponent.class), budget) * factor);
            yard.notices.add(String.format(Locale.ROOT, "%s's yard finished the repairs: %,.0f cr",
                    ShipNames.name(station), orders.repairsPaid));
            return;
        }
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null && trader.credits < 1) {
            orders.repairs = false;
            yard.notices.add("The yard stopped work: you're out of credits");
            return;
        }
        int techs = yard.technicians(station);
        if (reach == null) {
            // On the rails together: the technicians' pace, without the walking
            float work = techs * REPAIR_RATE * dt;
            for (StationYard.Damage d : damage) {
                if (work <= 0) break;
                work -= work(ship, orders, d, work);
            }
            return;
        }
        // What no one can reach from the dock is worked from outside, by one technician at a time
        float outside = REPAIR_RATE * dt;
        for (Iterator<StationYard.Damage> it = damage.iterator(); it.hasNext(); ) {
            StationYard.Damage d = it.next();
            if (YardReach.reachable(reach, d)) continue;
            it.remove();
            if (outside > 0) outside -= work(ship, orders, d, outside);
        }
        int open = 0;
        for (YardTask t : repairing.values()) {
            if (t.ship == ship && t.status == YardTask.Status.OPEN) open++;
        }
        ShipTaskBoardComponent board = crew.board(station);
        for (StationYard.Damage d : damage) {
            if (open >= techs || board == null) break;
            if (repairing.containsKey(d.key())) continue;
            YardTask task = new YardTask(ship, d.x, d.y, d.w, d.h, new Repair(ship, d));
            if (!board.post(task)) continue;
            repairing.put(d.key(), task);
            open++;
        }
    }

    /** Puts up to {@code work} into {@code d}, charging for it; returns the work used. */
    float work(Entity ship, YardOrdersComponent orders, StationYard.Damage d, float work) {
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        float points = work / d.workPerPoint();
        // The yard's own prices: dearer where tension runs high
        double price = d.cost() * yard.priceFactor.applyAsDouble(orders.station == null ? null : orders.station.get(uuids));
        if (d.kind == StationYard.Damage.Kind.COLLAR) {
            // Whole or not at all: count the work until the collar can go back on
            float done = collarWork.getOrDefault(d.port, 0f) + points;
            float need = 1f - d.condition();
            if (done < need) {
                collarWork.put(d.port, done);
                charge(trader, orders, points * price);
                return work;
            }
            collarWork.remove(d.port);
            charge(trader, orders, Math.max(0, need - (done - points)) * price);
            d.restore(1f);
            return work;
        }
        if (trader != null) points = (float) Math.min(points, trader.credits / price);
        float rose = d.restore(points);
        charge(trader, orders, rose * price);
        if (d.kind == StationYard.Damage.Kind.HULL && d.condition() >= 1f - EPS && d.tile != null) d.tile.bulkheadSealed = false;
        return rose * d.workPerPoint();
    }

    private static void charge(TraderComponent trader, YardOrdersComponent orders, double credits) {
        if (credits <= 0) return;
        if (trader != null) trader.credits = Math.max(0, trader.credits - credits);
        orders.repairsPaid += credits;
    }

    /** The ship left: its repair tasks come off the board. */
    void callOff(Entity ship) {
        for (YardTask t : repairing.values()) {
            if (t.ship == ship && t.status == YardTask.Status.OPEN) t.status = YardTask.Status.CANCELLED;
        }
        repairing.values().removeIf(t -> t.ship == ship);
    }

    /** A technician's view of one piece of damage. */
    private final class Repair implements YardTask.Job {
        final Entity ship;
        final StationYard.Damage damage;

        Repair(Entity ship, StationYard.Damage damage) {
            this.ship = ship;
            this.damage = damage;
        }

        @Override
        public boolean work(float amount) {
            YardOrdersComponent orders = ORDERS_M.get(ship);
            if (orders == null || !orders.repairs) return true;
            YardRepairs.this.work(ship, orders, damage, amount);
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

        @Override
        public float progress() {
            return damage.condition();
        }
    }
}
