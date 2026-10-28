package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/** Small ships for tests. */
final class TestShips {

    final Engine engine = new Engine();
    final CrewService crew;
    final Entity ship = new Entity();
    final ShipGridComponent grid;
    final ShipTaskBoardComponent board;
    final ShipSubsystemRegistryComponent registry;

    TestShips(ChassisBlueprint bp, boolean hazards) {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        grid = r.grid;
        CompartmentBuilder.rebuildIfChanged(grid);
        if (hazards) engine.addSystem(new GridHazardSystem());
        crew = CrewFactory.install(engine);
        ship.add(grid);
        registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(registry);
        board = CrewFactory.ensureBoard(ship);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity sys = new Entity();
            sys.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = sys;
            engine.addEntity(sys);
            registry.registerSubsystem(sys);
        }
    }

    TestShips(String... rows) {
        this(new ChassisBlueprint(rows), true);
    }

    /** A pressure suit locker on tile (x, y), against one of its walls, holding {@code suits} suits. */
    com.ringsofiron.interiorgrid.Furnishing locker(int x, int y, int suits) {
        com.ringsofiron.interiorgrid.Furnishing f = com.ringsofiron.interiorgrid.SuitOutfit.placeLocker(grid,
                com.ringsofiron.interiorgrid.crew.Suit.Kind.IVA, x, y, suits);
        if (f == null) throw new IllegalStateException("no locker fits on " + x + "," + y);
        return f;
    }

    Entity spawn(CrewRole role, int faction, int x, int y) {
        return CrewFactory.spawn(crew, ship, role.name(), role, faction, x, y);
    }

    void run(float seconds) {
        float dt = 1f / 30f;
        for (float t = 0; t < seconds; t += dt) engine.update(dt);
    }

    CrewMemberComponent c(Entity e) {
        return crew.crew(e);
    }

    GridPositionComponent p(Entity e) {
        return crew.position(e);
    }
}
