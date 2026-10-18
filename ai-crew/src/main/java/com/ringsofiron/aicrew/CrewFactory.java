package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.btree.CrewAgent;
import com.ringsofiron.aicrew.btree.CrewBehaviorTrees;

/** Creates crew entities and installs the crew systems into an engine. */
public final class CrewFactory {

    private CrewFactory() {}

    /**
     * Adds the crew systems to {@code engine} and returns the {@link CrewService} that
     * combat and other modules call. The grid's own GridHazardSystem and
     * PowerRoutingSystem are added separately.
     */
    public static CrewService install(Engine engine) {
        CrewService service = new CrewService(engine);
        engine.addSystem(new ShipHazardScanSystem(service));
        engine.addSystem(new CrewDecisionSystem());
        engine.addSystem(new CrewMovementSystem(service));
        engine.addSystem(new CrewHealthSystem(service));
        return service;
    }

    /** Spawns an AI crew member on tile (x, y) of {@code ship} and adds them to the engine. */
    public static Entity spawn(CrewService service, Entity ship, String name, CrewRole role, int faction, int x, int y) {
        Engine engine = service.engine();
        Entity e = engine.createEntity();
        CrewMemberComponent c = new CrewMemberComponent(name, role, faction);
        GridPositionComponent p = new GridPositionComponent(ship, x, y);
        e.add(c);
        e.add(p);
        CrewAgent agent = new CrewAgent(e, c, p, service);
        e.add(new BehaviorTreeComponent(CrewBehaviorTrees.standard(agent), agent));
        engine.addEntity(e);
        return e;
    }

    /** Spawns the player's avatar, a crew member moved by input rather than AI. */
    public static Entity spawnPlayer(CrewService service, Entity ship, String name, int x, int y) {
        Engine engine = service.engine();
        Entity e = engine.createEntity();
        e.add(new CrewMemberComponent(name, CrewRole.CREWMAN, 0));
        e.add(new GridPositionComponent(ship, x, y));
        e.add(new PlayerAvatarComponent());
        engine.addEntity(e);
        return e;
    }

    /** Makes {@code ship} a crewed ship by giving it a task board, if it has none. */
    public static ShipTaskBoardComponent ensureBoard(Entity ship) {
        ShipTaskBoardComponent b = ship.getComponent(ShipTaskBoardComponent.class);
        if (b == null) {
            b = new ShipTaskBoardComponent();
            ship.add(b);
        }
        return b;
    }
}
