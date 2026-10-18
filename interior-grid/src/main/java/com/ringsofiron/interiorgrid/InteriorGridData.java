package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.MaterialDef;
import com.ringsofiron.moddingdata.defs.ShipDef;

/**
 * Reads interior-grid's chassis blueprints and material table from modding-data
 * definitions, and gives every ship spawned from data its interior.
 *
 * <pre>
 * new ModdingBootstrap(assetsDir, modsDir)
 *         .beforeScripts(InteriorGridData::install)
 *         .load(gameEngine);
 * </pre>
 */
public final class InteriorGridData {

    private InteriorGridData() {}

    /**
     * Registers the "ship" assembler: each ship gets a {@link ShipGridComponent} built from
     * its chassis, a {@link ShipSubsystemRegistryComponent}, one entity per installed
     * system, added to the engine at once, and an empty {@link CargoHoldComponent} over its
     * cargo bays when the chassis has any.
     */
    public static void install(ModContext ctx) {
        ctx.factory.registerAssembler("ship", (entity, def, factory) -> {
            ShipDef ship = factory.defs().convert(def, ShipDef.class);
            ChassisDef chassis = factory.defs().require("chassis", ship.chassis, ChassisDef.class);
            ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(toBlueprint(chassis));
            CompartmentBuilder.rebuildIfChanged(result.grid);

            ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(entity);
            entity.add(result.grid);
            entity.add(registry);
            CargoHoldComponent hold = CargoHoldComponent.forBays(result.grid.tileSize, result.subsystems);
            if (hold != null) entity.add(hold);
            for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
                Entity system = factory.engine().createEntityWithUuid();
                system.add(new SubsystemComponent(p.footprint, p.type));
                p.footprint.installed = system;
                factory.engine().addEntity(system);
                registry.registerSubsystem(system);
            }
        });
    }

    /** Cargo volume of a ship definition's chassis bays, m³. */
    public static double cargoBayVolume(DefRegistry defs, String shipId) {
        ShipDef ship = defs.require("ship", shipId, ShipDef.class);
        ChassisDef chassis = defs.require("chassis", ship.chassis, ChassisDef.class);
        return CargoHoldComponent.bayVolume(ChassisBlueprintLoader.load(toBlueprint(chassis)));
    }

    /** The blueprint for a chassis definition. */
    public static ChassisBlueprint toBlueprint(ChassisDef def) {
        ChassisBlueprint bp = new ChassisBlueprint(def.rows).tileSize(def.tileSize).origin(def.originX, def.originY);
        for (ChassisDef.EdgeWall w : def.edgeWalls) {
            bp.edgeWalls.add(new ChassisBlueprint.EdgeWallDef(w.x, w.y, Side.valueOf(w.side),
                    WeightClass.valueOf(w.weightClass), w.material, w.bulkhead));
        }
        for (ChassisDef.HullPiece p : def.hullPieces) {
            bp.hullPiece(new HullPiece(HullPiece.Shape.valueOf(p.shape), p.x, p.y, p.width, p.height, p.rotation,
                    WeightClass.valueOf(p.weightClass), p.material));
        }
        for (ChassisDef.Mount m : def.mounts) {
            SubsystemComponent.SubsystemType type = SubsystemComponent.SubsystemType.valueOf(m.type);
            bp.mount(new MountFootprint(type.mountKind, MountFootprint.MountSize.valueOf(m.size), m.x, m.y, m.rotation), type);
        }
        for (ChassisDef.Covering c : def.coverings) {
            bp.covering(FloorCovering.valueOf(c.covering), c.x, c.y, c.width, c.height);
        }
        return bp;
    }

    /** The material table for every material definition. */
    public static MaterialTable toMaterialTable(DefRegistry defs) {
        MaterialTable table = new MaterialTable();
        for (MaterialDef m : defs.all(MaterialDef.class)) {
            table.define(m.id, stats(m.light), stats(m.medium), stats(m.heavy));
        }
        return table;
    }

    private static MaterialTable.ClassStats stats(MaterialDef.ClassStats s) {
        return new MaterialTable.ClassStats(s.mass, s.hitPoints, s.armor);
    }
}
