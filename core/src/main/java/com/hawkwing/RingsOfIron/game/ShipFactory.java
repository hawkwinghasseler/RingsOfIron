package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ships' bodies and mass, from their layouts and the loaded data: the one place the physics
 * assembler (ships spawned from data, the shipyard's among them), macro-sim's bubble gateway (craft
 * entering the bubble) and the yards' refits get a ship's hull shape and weight from.
 */
public final class ShipFactory {
    private final PhysicsManager physics;
    private final Supplier<ModContext> content;
    private final FactionStanding standing;
    private final CrewService crew;
    private final MacroSimulation macroSim;
    /** What ships collide on, once content has loaded. */
    private ShipHulls hulls;
    /** Materials from the loaded data, for ships' mass. */
    private MaterialTable materials;

    ShipFactory(PhysicsManager physics, Supplier<ModContext> content, FactionStanding standing, CrewService crew,
                MacroSimulation macroSim) {
        this.physics = physics;
        this.content = content;
        this.standing = standing;
        this.crew = crew;
        this.macroSim = macroSim;
    }

    /** Ships spawned from {@code ctx}'s data collide on their hull art's collision layer, else on their layout. */
    void registerAssembler(ModContext ctx, Path assetsDir) {
        ShipPhysicsAssembler.register(ctx.factory, physics, gridMass(ctx), hulls = new ShipHulls(assetsDir, ctx));
    }

    /**
     * Mass, center and inertia of a ship from its interior grid and the loaded materials, with the
     * cargo in its bays and what its tanks hold. Null for a ship without a grid, or before content has loaded.
     */
    public ShipGridComponent.MassProperties shipMass(Entity ship) {
        ModContext loaded = content.get();
        return loaded == null ? null : shipMass(ship, loaded.defs);
    }

    private ShipGridComponent.MassProperties shipMass(Entity ship, DefRegistry defs) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return null;
        if (materials == null) materials = InteriorGridData.toMaterialTable(defs);
        ShipGridComponent.MassProperties m = grid.computeMassProperties(materials);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (hold != null) hold.addMassTo(grid, m);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) TankSupply.addMassTo(grid, registry, m);
        return m;
    }

    /**
     * Gives a ship's body the collision shape of its current layout (see {@link ShipHulls}), as after
     * a yard rebuilds its decks. Its mass is left to the caller. Returns false if nothing changed:
     * no body, no grid, or content not loaded.
     */
    public boolean rebuildHull(Entity ship) {
        com.ringsofiron.physicsexterior.components.PhysicsBodyComponent pb =
                ship.getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class);
        if (hulls == null || pb == null || pb.body == null) return false;
        return PhysicsManager.replacePolygonFixtures(pb.body, hulls.hullFor(ship, null));
    }

    /**
     * A Box2D body for a ship with a layout, at the bubble origin: shaped as the physics assembler
     * shapes it ({@link ShipHulls}) and weighing what {@link #shipMass} says. A freighter macro-sim
     * made without one is built into a Hauler first ({@link FreighterShips#outfit}). Null for any
     * other craft without a grid, or before content has loaded.
     */
    public Body buildShipBody(Entity ship) {
        FreighterShips.outfit(content.get(), physics, standing, crew, macroSim, ship);
        ShipGridComponent.MassProperties m = shipMass(ship);
        List<float[]> hull = hulls == null || m == null ? null : hulls.hullFor(ship, null);
        if (hull == null || hull.isEmpty()) return null;
        Body body = physics.createPolygonBody(ship, 0f, 0f, 0f, hull, 1f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        PhysicsManager.setMassData(body, m.mass, m.center.x, m.center.y, m.inertia);
        return body;
    }

    /** A ship's mass for the physics assembler, from {@link #shipMass}. */
    private ShipPhysicsAssembler.MassSource gridMass(ModContext ctx) {
        return (entity, def, out) -> {
            ShipGridComponent.MassProperties m = shipMass(entity, ctx.defs);
            if (m == null) return false;
            out[0] = m.mass;
            out[1] = m.center.x;
            out[2] = m.center.y;
            out[3] = m.inertia;
            return true;
        };
    }
}
