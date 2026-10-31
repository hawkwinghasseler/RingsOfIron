package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.shipops.FreighterCargo;
import com.ringsofiron.shipops.MarketCargo;
import com.hawkwing.RingsOfIron.wiring.AvatarInteractor;
import com.ringsofiron.shipops.CargoLoading;
import com.ringsofiron.shipops.DoorForcing;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.shipops.SalvageSettlement;
import com.ringsofiron.career.ServiceBudget;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewDoors;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewSaveParticipant;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * ai-crew in {@link GameWorld}, and what the crew work on: the career that sets who they fight,
 * forcing doors, loading cargo, and the player's seats on the bridge.
 */
final class CrewInstaller {
    final CrewService crew;
    final FactionStanding standing;
    final ServiceBudget budget;
    final DoorForcing forcing;
    final SalvageSettlement cargoSettlement;
    final CargoLoading cargo;
    final AvatarInteractor avatar;
    final Bridge bridge;

    CrewInstaller(GameWorld world) {
        GameEngine engine = world.engine;
        DockingSystem docking = world.docking;
        crew = CrewFactory.install(engine.getEcsEngine());
        CrewSaveParticipant.install(crew, engine);
        // A ship taken out of the world (crashed on the rails, lost, wrecked) takes whoever is aboard
        // and its systems with it, so no crew go on working on a ship that is gone
        engine.getEcsEngine().addEntityListener(Family.all(ShipGridComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
            }

            @Override
            public void entityRemoved(Entity ship) {
                if (!ship.isRemoving() && !ship.isScheduledForRemoval()) return; // only its layout went (a refit)
                ImmutableArray<Entity> all = crew.aboard(ship);
                List<Entity> aboard = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) aboard.add(all.get(i));
                for (Entity e : aboard) {
                    crew.releaseTask(e);
                    engine.getEcsEngine().removeEntity(e);
                }
                // Only those still in the world: one already taken out (Shipyard.retire) went back to
                // the engine's pool, and removing it again would hand it out twice
                ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
                if (registry == null) return;
                ImmutableArray<Entity> systems = engine.getEcsEngine().getEntitiesFor(Family.all(SubsystemComponent.class).get());
                for (Entity system : registry.subsystems) {
                    if (system != null && !system.isScheduledForRemoval() && systems.contains(system, true)) {
                        engine.getEcsEngine().removeEntity(system);
                    }
                }
            }
        });
        // Crews fight by their factions' wars and the player's standing; the career saves with the game
        standing = new FactionStanding(world::getPlayer);
        standing.clock = engine::getSimSeconds;
        engine.getSerializationService().addParticipant(standing);
        // A save from before the career was saved loads as a new one: rank 0 with the sponsor
        world.addResettable(standing);
        budget = new ServiceBudget(standing, world::getPlayer, engine::getSimSeconds);
        engine.addSystem(budget);
        crew.hostility = standing::hostile;
        docking.events.add((signal, e) -> standing.onDockingEvent(e));
        // A port door held shut for pressure opens for crew walking through it
        docking.crossing = link -> CrewDoors.crossingPort(crew, link);
        docking.clearance = (station, vessel) -> {
            if (!station.getComponent(StationComponent.class).grantsClearance) return "";
            String refused = standing.refusal(station, vessel);
            return refused != null ? refused : world.tensionRefusal(station, vessel);
        };
        forcing = new DoorForcing(docking, crew, engine::getSimSeconds);
        engine.addSystem(forcing);
        cargoSettlement = new SalvageSettlement(new MarketCargo(world.macroSim), world.commodities,
                world::partPrice);
        // Freighters' loads only move goods: macro-sim settles their trades
        cargo = new CargoLoading(docking, crew, engine.getUuidRegistry(), engine::getSimSeconds,
                new FreighterCargo(cargoSettlement, world.commodities));
        engine.addSystem(cargo);
        avatar = new AvatarInteractor(engine.getEcsEngine());
        // Who sits at the player's consoles, and who flies the ship when the player isn't at the helm
        bridge = new Bridge(world.context, avatar, crew, docking, () -> world.nav);
        engine.addSystem(bridge);
    }
}
