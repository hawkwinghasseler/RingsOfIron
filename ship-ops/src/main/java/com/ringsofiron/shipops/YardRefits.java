package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;

import java.util.List;
import java.util.Locale;

import static com.ringsofiron.shipops.StationYard.EPS;
import static com.ringsofiron.shipops.StationYard.FIT_WORK_PER_TILE;
import static com.ringsofiron.shipops.StationYard.ORDERS_M;
import static com.ringsofiron.shipops.StationYard.PART;
import static com.ringsofiron.shipops.StationYard.PART_TRIP_SECONDS;
import static com.ringsofiron.shipops.StationYard.REPAIR_RATE;
import static com.ringsofiron.shipops.StationYard.TRADE_IN;
import static com.ringsofiron.shipops.StationYard.USED_CONDITION;
import static com.ringsofiron.shipops.StationYard.hardpointName;
import static com.ringsofiron.shipops.StationYard.price;
import static com.ringsofiron.shipops.StationYard.tooSmall;

/**
 * {@link StationYard}'s refits: a weapon ordered for a hardpoint, carried aboard by the station's
 * loaders ({@link CarryTask}), paid for as it lands, then fitted by the technicians one mount tile at
 * a time; and the part refunded, or a salvaged one put back in the hold, when the work is called off.
 */
final class YardRefits {
    private final StationYard yard;
    private final CrewService crew;
    private final CombatDefs weapons;

    YardRefits(StationYard yard, CrewService crew, CombatDefs weapons) {
        this.yard = yard;
        this.crew = crew;
        this.weapons = weapons;
    }

    /**
     * Has {@code station}'s yard fit a salvaged {@code weaponId} out of the ship's own cargo bays
     * ({@link Salvage}) to hardpoint {@code hardpoint}, like a used one but with nothing to pay: the
     * crate is taken out of the hold now, and goes back if the work is called off. Returns what it says.
     */
    String orderSalvagedRefit(Entity ship, Entity station, int hardpoint, String weaponId) {
        if (!yard.canWork(ship, station)) return "No yard here to do the work";
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
        YardOrdersComponent orders = yard.orders(ship, station);
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
     * Has {@code station}'s yard fit {@code weaponId} (new, or used and cheaper but worn) to the
     * ship's hardpoint {@code hardpoint}. Returns what it says.
     */
    String orderRefit(Entity ship, Entity station, int hardpoint, String weaponId, boolean used) {
        if (!yard.canWork(ship, station)) return "No yard here to do the work";
        WeaponsComponent mounts = ship.getComponent(WeaponsComponent.class);
        WeaponDef def = weapons.weapons.get(weaponId);
        if (mounts == null || hardpoint < 0 || hardpoint >= mounts.mounts.size) return "No such hardpoint";
        if (def == null || def.price <= 0) return "The yard doesn't sell that";
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
        YardOrdersComponent orders = yard.orders(ship, station);
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

    /** A line on each refit under way on {@code ship}, for the screen. */
    void progress(Entity ship, YardOrdersComponent orders, List<String> out) {
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
    }

    /** One refit's progress; false once it is finished or dropped. */
    boolean refitStep(Entity ship, Entity station, YardOrdersComponent.Refit r, boolean[][] reach, float dt) {
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
                int[] drop = YardReach.nearest(reach, m.originX + m.width() / 2, m.originY + m.height() / 2);
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
        boolean inside = walkway && YardReach.reachable(reach, new StationYard.Damage(StationYard.Damage.Kind.SYSTEM, m.originX, m.originY, m.width(), m.height(),
                null, null, null, system, null));
        if (!inside) {
            // In the background, or a mount no one can get to from the dock: fitted from outside
            r.tileWork += (walkway ? 1 : yard.technicians(station)) * REPAIR_RATE * dt;
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
            yard.notices.add("Can't pay for the " + def.displayName() + ": the yard keeps it");
            r.weapon = "";
            return;
        }
        if (trader != null) trader.credits -= price;
        r.paid = price;
        r.aboard = true;
        r.tileWork = 0;
        yard.notices.add("The " + def.displayName() + " is aboard; the technicians are fitting it");
    }

    /** The last tile is done: the new weapon goes on, and the old one back to the yard. */
    private void fit(Entity ship, Entity station, WeaponMountComponent mount, SubsystemComponent system,
                     YardOrdersComponent.Refit r, WeaponDef def) {
        WeaponDef old = mount.def;
        mount.def = def;
        mount.cooldown = def.cooldown;
        mount.beamTime = 0f;
        // A launcher comes aboard loaded
        mount.ammo = -1;
        system.health = r.used ? Math.min(system.health, USED_CONDITION) : Math.max(system.health, 1f);
        double credit = old == null || old == def ? 0 : old.price * TRADE_IN;
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        if (trader != null) trader.credits += credit;
        yard.notices.add(String.format(Locale.ROOT, "%s fitted %s with a %s%s", ShipNames.name(station), hardpointName(mount),
                def.displayName(), credit > 0 ? String.format(Locale.ROOT, "; %,.0f cr for the old %s", credit, old.displayName()) : ""));
    }

    static void cancel(YardOrdersComponent.Refit r) {
        if (r.trip != null && r.trip.status == CarryTask.Status.OPEN) r.trip.status = CarryTask.Status.CANCELLED;
        if (r.task != null && r.task.status == YardTask.Status.OPEN) r.task.status = YardTask.Status.CANCELLED;
    }

    void refund(Entity ship, YardOrdersComponent.Refit r) {
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

        @Override
        public float progress() {
            return Math.min(1f, refit.tileWork / FIT_WORK_PER_TILE);
        }
    }
}
