package com.hawkwing.RingsOfIron.art;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

/** Where an engine system's exhaust leaves it, outside the hull. */
class ExteriorArtTest {

    private final ExteriorArt exterior = new ExteriorArt(null);

    private static ShipGridComponent grid(ChassisBlueprint bp) {
        return ChassisBlueprintLoader.load(bp).grid;
    }

    @Test
    void anEngineAftOfTheHullLetsItsExhaustOutAtItsAftEnd() {
        ShipGridComponent grid = grid(
                new ChassisBlueprint("   ######",
                "   #....#",
                "   #....#",
                "   #....#",
                "   ######")
                .mount("engine", MountFootprint.MountSize.M, 0, 2, 1));
        MountFootprint m = grid.mounts.first();
        assertEquals(1, m.rotation, "nozzle aft");
        exterior.socket(grid, m);
        assertEquals(grid.gridOriginLocal.x, exterior.socket.x, 1e-5f, "at the slot's aft end");
        assertEquals(grid.gridOriginLocal.y + 3f * grid.tileSize, exterior.socket.y, 1e-5f, "on the slot's middle line");
        assertEquals(0f, exterior.socketDegrees, 1e-3f, "the plume's art already points aft");
    }

    @Test
    void anEngineUnderTheHullLetsItsExhaustOutBelowIt() {
        ShipGridComponent grid = grid(
                new ChassisBlueprint("#####",
                "#...#",
                "#...#",
                "#####",
                "     ",
                "     ")
                .mount("engine", MountFootprint.MountSize.S, 2, 0, 2));
        MountFootprint m = grid.mounts.first();
        assertEquals(2, m.rotation, "nozzle down");
        exterior.socket(grid, m);
        assertEquals(grid.gridOriginLocal.x + 2.5f * grid.tileSize, exterior.socket.x, 1e-5f);
        assertEquals(grid.gridOriginLocal.y, exterior.socket.y, 1e-5f);
        assertEquals(90f, ((exterior.socketDegrees % 360f) + 360f) % 360f, 1e-3f, "the plume turned from aft to down");
    }
}
