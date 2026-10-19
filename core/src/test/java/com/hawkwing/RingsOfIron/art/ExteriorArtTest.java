package com.hawkwing.RingsOfIron.art;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

/** Where an engine system's exterior engine goes on the hull. */
class ExteriorArtTest {

    private final ExteriorArt exterior = new ExteriorArt(null);

    private static ShipGridComponent grid(String... rows) {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(rows)).grid;
    }

    @Test
    void anEngineLyingAlongXSitsOnTheAftHullEdgeBehindIt() {
        ShipGridComponent grid = grid(
                "######",
                "#EEE.#",
                "#EEE.#",
                "#....#",
                "######");
        MountFootprint m = grid.mounts.first();
        assertEquals(1, m.rotation);
        assertTrue(exterior.socket(grid, m));
        assertEquals(grid.gridOriginLocal.x, exterior.socket.x, 1e-5f, "on the outer face of the aft hull");
        assertEquals(grid.gridOriginLocal.y + 3f * grid.tileSize, exterior.socket.y, 1e-5f, "on the slot's middle line");
        assertEquals(0f, exterior.socketDegrees, 1e-3f, "the art's nozzle already points aft");
    }

    @Test
    void anEngineFacingIntoTheShipHasNoExteriorEngine() {
        ShipGridComponent grid = grid(
                "#####",
                "#...#",
                "#E..#",
                "#E..#",
                "#####");
        MountFootprint m = grid.mounts.first();
        assertEquals(0, m.rotation);
        assertFalse(exterior.socket(grid, m));
    }
}
