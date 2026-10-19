package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.YardProvider;

import java.util.List;
import java.util.Locale;

/**
 * ui-hud's yard window from the {@link StationYard}: the station the player's ship is docked at,
 * if its yard can work on the ship, what is damaged, the ship's hardpoints and the weapons on sale.
 */
public class YardWork implements YardProvider {
    private final GameWorld world;

    public YardWork(GameWorld world) {
        this.world = world;
    }

    @Override
    public void collect(YardBoard out) {
        Entity ship = world.getPlayer(), station = station();
        if (station == null) return;
        out.stationName = PlayerDocking.name(station);
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        out.credits = trader == null ? 0 : trader.credits;
        out.damaged = world.yard.damage(ship).size();
        out.repairCost = world.yard.repairCost(ship);
        out.usedPrice = StationYard.USED_PRICE;
        out.refillCost = world.yard.refillCost(ship);
        out.tanks = tanks(ship);
        List<String> progress = world.yard.progress(ship);
        if (world.yard.repairing(ship) && !progress.isEmpty()) out.repairs = progress.get(0);
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons != null) {
            for (int i = 0; i < weapons.mounts.size; i++) {
                WeaponMountComponent m = weapons.mounts.get(i);
                Hardpoint h = out.addHardpoint();
                h.index = i;
                h.name = StationYard.hardpointName(m);
                h.weapon = m.def == null ? "-" : m.def.displayName();
                YardOrdersComponent.Refit r = world.yard.refit(ship, i);
                if (r != null) h.progress = r.aboard ? "fitting: " + r.tilesDone + " tiles done" : "part on its way";
            }
        }
        for (WeaponDef d : world.yard.offers()) {
            Offer o = out.addOffer();
            o.weaponId = d.id;
            o.name = d.displayName();
            o.price = d.price;
        }
    }

    @Override
    public String orderRepairs() {
        Entity station = station();
        return station == null ? "No yard here" : world.yard.orderRepairs(world.getPlayer(), station);
    }

    @Override
    public String orderRefill() {
        Entity station = station();
        return station == null ? "No yard here" : world.yard.orderRefill(world.getPlayer(), station);
    }

    /** How full each kind of tank aboard is, as one line. */
    static String tanks(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Fluid f : Fluid.values()) {
            double have = 0, full = 0;
            for (SubsystemComponent t : TankSupply.tanks(registry, f)) {
                have += t.contents;
                full += t.capacity;
            }
            if (full <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            String name = f.name().toLowerCase(Locale.ROOT);
            sb.append(sb.length() == 0 ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : name)
                    .append(String.format(Locale.ROOT, " %.0f%%", 100 * have / full));
        }
        return sb.toString();
    }

    @Override
    public String orderRefit(int hardpoint, String weaponId, boolean used) {
        Entity station = station();
        return station == null ? "No yard here" : world.yard.orderRefit(world.getPlayer(), station, hardpoint, weaponId, used);
    }

    /** The station the player's ship is docked at, if its yard can work on it. */
    private Entity station() {
        Entity ship = world.getPlayer();
        Entity station = ship == null ? null : world.docking.dockedTo(ship);
        return station != null && world.yard.canWork(ship, station) ? station : null;
    }
}
