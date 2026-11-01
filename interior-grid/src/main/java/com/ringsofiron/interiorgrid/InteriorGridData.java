package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
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

    /** The definitions {@link #install} last ran on; their tiles and components build every chassis. */
    private static volatile DefRegistry activeDefs;
    private static DefRegistry cachedDefs;
    private static int cachedRevision = -1;
    private static ShipPartCatalog cachedCatalog;

    /**
     * The tile types and components of the installed definitions, or the built-ins when nothing
     * is installed. Rebuilt whenever the definitions change.
     */
    public static synchronized ShipPartCatalog catalog() {
        DefRegistry defs = activeDefs;
        if (defs == null) return ShipPartCatalog.builtIn();
        return catalog(defs);
    }

    /** The furnishings of the installed definitions ({@code [furnishing.*]}), or none when nothing is installed. */
    public static FurnishingCatalog furnishings() {
        DefRegistry defs = activeDefs;
        return defs == null ? new FurnishingCatalog() : FurnishingCatalog.from(defs);
    }

    /** The tile types and components of {@code defs}. */
    public static synchronized ShipPartCatalog catalog(DefRegistry defs) {
        if (defs != cachedDefs || defs.revision() != cachedRevision || cachedCatalog == null) {
            cachedCatalog = ShipPartCatalog.from(defs);
            cachedDefs = defs;
            cachedRevision = defs.revision();
        }
        return cachedCatalog;
    }

    /**
     * Registers the "ship" assembler: each ship gets a {@link ShipGridComponent} built from
     * its chassis, a {@link ShipSubsystemRegistryComponent}, one entity per installed
     * system, added to the engine at once, an empty {@link CargoHoldComponent} over its
     * cargo bays when the chassis has any, and its suit lockers, full, for its crew
     * ({@link SuitOutfit}).
     */
    public static void install(ModContext ctx) {
        activeDefs = ctx.defs;
        ctx.factory.registerAssembler("ship", (entity, def, factory) -> {
            ShipDef ship = factory.defs().convert(def, ShipDef.class);
            ChassisDef chassis = factory.defs().require("chassis", ship.chassis, ChassisDef.class);
            installInterior(entity, chassis, factory.engine());
            SuitOutfit.outfit(entity.getComponent(ShipGridComponent.class), FurnishingCatalog.from(factory.defs()), ship.crew);
        });
    }

    /**
     * Gives {@code entity} the interior of a chassis, as the ship assembler does: its grid with
     * fresh air, a subsystem registry, one entity per installed system (added to the engine at once)
     * and an empty cargo hold over its bays when it has any. Stations get theirs this way too.
     */
    public static ShipSubsystemRegistryComponent installInterior(Entity entity, ChassisDef chassis, GameEngine engine) {
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(toBlueprint(chassis, catalog()));
        CompartmentBuilder.rebuildIfChanged(result.grid);
        result.grid.chassis = chassis.id;

        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(entity);
        entity.add(result.grid);
        entity.add(registry);
        CargoHoldComponent hold = CargoHoldComponent.forBays(result.grid.tileSize, result.subsystems);
        if (hold != null) entity.add(hold);
        for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
            Entity system = engine.createEntityWithUuid();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            system.add(sub);
            p.footprint.installed = system;
            engine.addEntity(system);
            registry.registerSubsystem(system);
        }
        // Power and pipes route on the first frame, so the ship can thrust and breathe at once
        result.grid.gridDirty = true;
        return registry;
    }

    /** Cargo volume of a ship definition's chassis bays, m³. */
    public static double cargoBayVolume(DefRegistry defs, String shipId) {
        ShipDef ship = defs.require("ship", shipId, ShipDef.class);
        ChassisDef chassis = defs.require("chassis", ship.chassis, ChassisDef.class);
        return CargoHoldComponent.bayVolume(ChassisBlueprintLoader.load(toBlueprint(chassis, catalog(defs))));
    }

    /** The blueprint for a chassis definition, with the built-in tile types and components. */
    public static ChassisBlueprint toBlueprint(ChassisDef def) {
        return toBlueprint(def, ShipPartCatalog.builtIn());
    }

    /** The blueprint for a chassis definition, drawn with {@code catalog}'s tile types and components. */
    public static ChassisBlueprint toBlueprint(ChassisDef def, ShipPartCatalog catalog) {
        ChassisBlueprint bp = new ChassisBlueprint(def.rows).tileSize(def.tileSize).origin(def.originX, def.originY)
                .catalog(catalog);
        int width = bp.width(), height = def.rows.length;
        for (ChassisDef.EdgeWall w : def.edgeWalls) {
            WeightClass weight = WeightClass.valueOf(w.weightClass);
            for (LayoutRuns.Edge e : LayoutRuns.edges(w.path, width, height)) {
                bp.edgeWalls.add(new ChassisBlueprint.EdgeWallDef(e.x, e.y, e.side, weight, w.material, false));
            }
        }
        for (ChassisDef.Door d : def.doors) bp.door(d.x, d.y, Side.valueOf(d.side));
        for (int[][] run : def.conduits) {
            for (int[] t : LayoutRuns.tiles(run)) bp.conduit(t[0], t[1]);
        }
        for (int[][] run : def.edgeConduits) {
            for (LayoutRuns.Edge e : LayoutRuns.edges(run, width, height)) bp.edgeConduit(e.x, e.y, e.side);
        }
        for (int[][] run : def.pipes) {
            for (int[] t : LayoutRuns.tiles(run)) bp.pipe(t[0], t[1]);
        }
        for (int[][] run : def.edgePipes) {
            for (LayoutRuns.Edge e : LayoutRuns.edges(run, width, height)) bp.pipe(e.x, e.y, e.side);
        }
        for (ChassisDef.HullPiece p : def.hullPieces) {
            bp.hullPiece(new HullPiece(HullPiece.Shape.valueOf(p.shape), p.x, p.y, p.width, p.height, p.rotation,
                    WeightClass.valueOf(p.weightClass), p.material));
        }
        for (ChassisDef.Mount m : def.mounts) {
            if (m.component == null) throw new IllegalArgumentException("A mount needs a component");
            ShipPartCatalog.ComponentType component = catalog.component(m.component);
            if (component == null) throw new IllegalArgumentException("Unknown component '" + m.component + "'");
            bp.mount(new MountFootprint(component.mountKind(), MountFootprint.MountSize.valueOf(m.size), m.x, m.y,
                    m.rotation), component);
        }
        for (ChassisDef.Covering c : def.coverings) {
            bp.covering(FloorCovering.valueOf(c.covering), c.x, c.y, c.width, c.height);
        }
        for (ChassisDef.Rcs r : def.rcs) {
            bp.rcsThrusters.add(new RcsThruster(r.x, r.y, Side.valueOf(r.side), RcsThruster.Kind.valueOf(r.kind), r.thrust));
        }
        if (def.lights != null) {
            bp.wallLights = new Array<>();
            for (ChassisDef.Light l : def.lights) bp.wallLights.add(new WallLight(l.x, l.y, Side.valueOf(l.side)));
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
