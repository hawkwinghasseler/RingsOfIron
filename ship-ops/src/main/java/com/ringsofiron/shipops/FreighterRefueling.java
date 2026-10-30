package com.ringsofiron.shipops;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.function.Function;

/**
 * NPC freighters fill their tanks wherever they dock, as the player does at a yard: at the yard's
 * prices ({@link StationYard#pricePerKg}), paid out of the freighter's own credits, and short of
 * them as far as they go. A freighter is docked when it is latched to a station in the bubble, or
 * idle at a station on the rails. Checks every {@link #INTERVAL} seconds.
 */
public class FreighterRefueling extends EntitySystem {
    /** Seconds between checks. */
    public static final float INTERVAL = 5f;

    private static final Family FREIGHTERS = Family.all(FreighterComponent.class, ShipSubsystemRegistryComponent.class).get();
    private static final ComponentMapper<FreighterComponent> FREIGHTER = ComponentMapper.getFor(FreighterComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);

    /** The craft a vessel is latched to, or null (stations-docking's {@code DockingSystem.dockedTo}). */
    private final Function<Entity, Entity> dockedTo;
    private ImmutableArray<Entity> freighters;
    private float since;

    public FreighterRefueling(Function<Entity, Entity> dockedTo) {
        this.dockedTo = dockedTo;
    }

    @Override
    public void addedToEngine(Engine engine) {
        freighters = engine.getEntitiesFor(FREIGHTERS);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        freighters = null;
    }

    @Override
    public void update(float deltaTime) {
        if (freighters == null) return;
        since += deltaTime;
        if (since < INTERVAL) return;
        since = 0f;
        for (int i = 0; i < freighters.size(); i++) refuel(freighters.get(i));
    }

    /** Fills a docked freighter's tanks as far as its credits go. Returns the credits spent. */
    public double refuel(Entity ship) {
        FreighterComponent f = FREIGHTER.get(ship);
        if (f == null || !docked(ship, f)) return 0;
        double paid = StationYard.fill(REGISTRY.get(ship), f.credits);
        f.credits = Math.max(0, f.credits - paid);
        return paid;
    }

    private boolean docked(Entity ship, FreighterComponent f) {
        Entity other = dockedTo.apply(ship);
        if (other != null && other.getComponent(StationComponent.class) != null) return true;
        return f.state == FreighterComponent.State.IDLE && f.dockedAtUuid != null;
    }
}
