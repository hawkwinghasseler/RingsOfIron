package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.FurnishingType;
import com.ringsofiron.interiorgrid.Furnishings;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.YardProvider;

import java.util.List;
import java.util.Locale;

/**
 * ui-hud's yard window from the {@link StationYard}: the station the player's ship is docked at,
 * if its yard can work on the ship, what is damaged, the ship's hardpoints and the weapons on sale,
 * and the furnishings on sale ({@link FurnishingStore}).
 */
public class YardWork implements YardProvider {
    private final GameWorld world;
    /** Opens the ship editor for a refit; set by the game screen. */
    private Runnable layoutEditor;
    /** Starts placing furnishings on the deck; set by the game screen. */
    private java.util.function.Consumer<String> furnisher;

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
        out.repairCost = world.yard.repairCost(ship, station);
        out.usedPrice = StationYard.USED_PRICE;
        out.refillCost = world.yard.refillCost(ship, station);
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
        for (FurnishingType t : world.furnishings.offers()) {
            YardProvider.Furnishing f = out.addFurnishing();
            f.id = t.id;
            f.name = t.name;
            f.use = t.use.name().toLowerCase(Locale.ROOT);
            f.price = world.furnishings.price(t, station);
        }
        out.furnished = furnished(ship);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        for (WeaponDef d : world.yard.offers()) {
            Offer o = out.addOffer();
            o.weaponId = d.id;
            o.name = d.displayName();
            o.price = d.price;
            o.salvaged = hold == null ? 0 : (int) Math.floor(hold.amount(StationYard.PART + d.id) + 1e-6);
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

    /** "4 furnishings aboard, 1 on its way", or empty with none. */
    private String furnished(Entity ship) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        int aboard = grid == null ? 0 : Furnishings.all(grid).size, coming = world.furnishings.pending(ship).size();
        if (aboard == 0 && coming == 0) return "";
        String s = aboard + (aboard == 1 ? " furnishing" : " furnishings") + " aboard";
        return coming == 0 ? s : s + ", " + coming + " on " + (coming == 1 ? "its" : "their") + " way";
    }

    /** What starts placing furnishings on the deck: a type's id to buy one, or null to arrange those aboard. */
    public void setFurnisher(java.util.function.Consumer<String> furnisher) {
        this.furnisher = furnisher;
    }

    @Override
    public String placeFurnishing(String furnishingId) {
        if (station() == null) return "No yard here";
        FurnishingType type = world.furnishings.catalog().get(furnishingId);
        if (type == null) return "The yard doesn't sell that";
        if (furnisher == null) return YardProvider.super.placeFurnishing(furnishingId);
        furnisher.accept(furnishingId);
        return null;
    }

    @Override
    public String arrangeFurnishings() {
        if (station() == null) return "No yard here";
        if (furnisher == null) return YardProvider.super.arrangeFurnishings();
        ShipGridComponent grid = world.getPlayer().getComponent(ShipGridComponent.class);
        if (grid == null || Furnishings.all(grid).size == 0) return "Nothing aboard to arrange";
        furnisher.accept(null);
        return null;
    }

    /** What opens the ship editor for the yard's deck rebuild ({@link ShipRefit}). */
    public void setLayoutEditor(Runnable layoutEditor) {
        this.layoutEditor = layoutEditor;
    }

    @Override
    public String openLayoutEditor() {
        if (station() == null) return "No yard here";
        if (layoutEditor == null) return YardProvider.super.openLayoutEditor();
        String no = ShipRefit.refused(world, world.getPlayer());
        if (no != null) return no;
        layoutEditor.run();
        return null;
    }

    @Override
    public String orderRefit(int hardpoint, String weaponId, boolean used) {
        Entity station = station();
        return station == null ? "No yard here" : world.yard.orderRefit(world.getPlayer(), station, hardpoint, weaponId, used);
    }

    @Override
    public String orderSalvagedRefit(int hardpoint, String weaponId) {
        Entity station = station();
        return station == null ? "No yard here" : world.yard.orderSalvagedRefit(world.getPlayer(), station, hardpoint, weaponId);
    }

    /** The station the player's ship is docked at, if its yard can work on it. */
    private Entity station() {
        Entity ship = world.getPlayer();
        Entity station = ship == null ? null : world.docking.dockedTo(ship);
        return station != null && world.yard.canWork(ship, station) ? station : null;
    }
}
