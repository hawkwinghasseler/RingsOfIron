package com.ringsofiron.shipops;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.CargoHold;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.function.Function;

/**
 * Puts traded goods in the ship's cargo bays and their weight on its hull. A trader aboard a ship
 * with an interior-grid {@link CargoHoldComponent} trades out of that hold, and whenever the cargo
 * changes the ship's Box2D mass, center of mass and inertia are recomputed from its structure plus
 * the loaded bay tiles. Runs every frame; both links are rebuilt on their own after a load.
 */
public class ShipCargo extends EntitySystem {
    private static final Family TRADERS = Family.all(TraderComponent.class, CargoHoldComponent.class).get();
    private static final Family SHIPS = Family.all(CargoHoldComponent.class, ShipGridComponent.class).get();
    private static final ComponentMapper<TraderComponent> TRADER = ComponentMapper.getFor(TraderComponent.class);
    private static final ComponentMapper<CargoHoldComponent> HOLD = ComponentMapper.getFor(CargoHoldComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODY = ComponentMapper.getFor(PhysicsBodyComponent.class);

    /** The ship's mass properties, structure and cargo, or null when they cannot be worked out. */
    private final Function<Entity, ShipGridComponent.MassProperties> massOf;
    private ImmutableArray<Entity> traders;
    private ImmutableArray<Entity> ships;

    public ShipCargo(Function<Entity, ShipGridComponent.MassProperties> massOf) {
        this.massOf = massOf;
    }

    @Override
    public void addedToEngine(Engine engine) {
        traders = engine.getEntitiesFor(TRADERS);
        ships = engine.getEntitiesFor(SHIPS);
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < traders.size(); i++) {
            Entity e = traders.get(i);
            TraderComponent trader = TRADER.get(e);
            CargoHoldComponent hold = HOLD.get(e);
            if (!(trader.hold instanceof Bays) || ((Bays) trader.hold).hold != hold) trader.attach(new Bays(hold));
        }
        for (int i = 0; i < ships.size(); i++) {
            Entity e = ships.get(i);
            CargoHoldComponent hold = HOLD.get(e);
            if (hold.massRevision == hold.revision) continue;
            PhysicsBodyComponent body = BODY.get(e);
            // On rails there is no body; the change waits until the ship is back in the bubble
            if (body == null || body.body == null) continue;
            ShipGridComponent.MassProperties m = massOf.apply(e);
            if (m == null || m.mass <= 0f) continue;
            PhysicsManager.setMassData(body.body, m.mass, m.center.x, m.center.y, m.inertia);
            hold.massRevision = hold.revision;
        }
    }

    /** Attaches the ship's bays to its trader at once, without waiting for the next frame. */
    public static void attach(Entity ship) {
        TraderComponent trader = TRADER.get(ship);
        CargoHoldComponent hold = HOLD.get(ship);
        if (trader != null && hold != null) trader.attach(new Bays(hold));
    }

    /** A trader's view of the bays. */
    static final class Bays implements CargoHold {
        final CargoHoldComponent hold;

        Bays(CargoHoldComponent hold) {
            this.hold = hold;
        }

        @Override
        public double capacity() {
            return hold.capacity();
        }

        @Override
        public double usedVolume() {
            return hold.usedVolume();
        }

        @Override
        public double amount(String commodity) {
            return hold.amount(commodity);
        }

        @Override
        public double room(CommodityType commodity) {
            return hold.room(commodity.id, commodity.unitVolume);
        }

        @Override
        public double load(CommodityType commodity, double units) {
            return hold.load(commodity.id, units, commodity.unitVolume, commodity.unitMass);
        }

        @Override
        public double unload(String commodity, double units) {
            return hold.unload(commodity, units);
        }
    }
}
