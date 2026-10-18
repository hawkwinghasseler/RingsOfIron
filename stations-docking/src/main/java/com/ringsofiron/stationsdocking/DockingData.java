package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.moddingdata.defs.StationTypeDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Docking ports and station interiors from data: a ship gets the docking ports of its chassis
 * when it spawns, and a station gets its type's blueprint as a walkable grid with its ports.
 */
public final class DockingData {
    /** Layout for a station whose type names no blueprint (a mod's type, say). */
    public static final String DEFAULT_STATION_BLUEPRINT = "station_outpost";

    private DockingData() {
    }

    /**
     * Registers the ship assembler that adds a {@link DockingPortsComponent} for the chassis'
     * ports. Register after interior-grid's {@code InteriorGridData.install}: it reads the grid.
     */
    public static void install(ModContext ctx) {
        ctx.factory.registerAssembler("ship", (entity, def, factory) -> {
            ShipGridComponent grid = entity.getComponent(ShipGridComponent.class);
            if (grid == null) return;
            ShipDef ship = factory.defs().convert(def, ShipDef.class);
            if (ship.chassis == null || !factory.defs().has("chassis", ship.chassis)) return;
            DockingPortsComponent ports = portsFor(grid, factory.defs().require("chassis", ship.chassis, ChassisDef.class));
            if (ports.ports.length > 0) entity.add(ports);
        });
    }

    /** The docking ports on {@code grid}, active where {@code chassis} lists them so. */
    public static DockingPortsComponent portsFor(ShipGridComponent grid, ChassisDef chassis) {
        List<int[]> active = new ArrayList<>();
        for (ChassisDef.Port p : chassis.ports) {
            if (p.active) active.add(new int[]{p.x, p.y});
        }
        return DockingPortsComponent.fromGrid(grid, active);
    }

    /**
     * Builds a station's interior on its entity: the grid of its type's blueprint (or
     * {@link #DEFAULT_STATION_BLUEPRINT}) with fresh air, its systems (added to the engine), cargo
     * bays, docking ports and a {@link StationComponent}. The entity keeps whatever else it has
     * (macro-sim's economy node and orbit).
     */
    public static StationComponent buildStation(Entity station, DefRegistry defs, GameEngine engine,
                                                String name, String typeId) {
        StationTypeDef type = typeId != null && defs.has("station_type", typeId)
                ? defs.require("station_type", typeId, StationTypeDef.class) : null;
        String blueprint = type != null && type.blueprint != null && defs.has("chassis", type.blueprint)
                ? type.blueprint : DEFAULT_STATION_BLUEPRINT;
        ChassisDef chassis = defs.require("chassis", blueprint, ChassisDef.class);
        InteriorGridData.installInterior(station, chassis, engine);
        station.add(portsFor(station.getComponent(ShipGridComponent.class), chassis));
        StationComponent component = new StationComponent(name, typeId, blueprint);
        station.add(component);
        return component;
    }
}
