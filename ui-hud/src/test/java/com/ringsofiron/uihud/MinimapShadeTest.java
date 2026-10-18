package com.ringsofiron.uihud;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapShadeTest {
    private static final String[] SHIP = {
        "######",
        "#....#",
        "#....#",
        "######",
    };

    private ShipGridComponent grid;

    @BeforeEach
    void build() {
        grid = ChassisBlueprintLoader.load(SHIP, 1f).grid;
        CompartmentBuilder.rebuild(grid);
        for (Compartment c : grid.compartments) c.fillStandardAir();
    }

    @Test
    void pressurizedFloorIsBlueprintBlue() {
        Color c = TileShade.fill(grid.getTile(2, 1), true, new Color());
        assertEquals(TileShade.FLOOR.b, c.b, 0.01f);
        assertEquals(TileShade.FLOOR.a, c.a, 0.01f);
    }

    @Test
    void floorDarkensAsPressureFalls() {
        Color before = TileShade.fill(grid.getTile(2, 1), true, new Color());
        grid.getTile(2, 1).compartment.oxygen *= 0.1;
        grid.getTile(2, 1).compartment.nitrogen *= 0.1;
        Color after = TileShade.fill(grid.getTile(2, 1), true, new Color());
        assertTrue(after.b < before.b, "blue drops in vacuum");
    }

    @Test
    void fireTintsOrange() {
        Tile t = grid.getTile(2, 1);
        t.fireIntensity = 1f;
        Color c = TileShade.fill(t, true, new Color());
        assertTrue(c.r > 0.9f && c.b < 0.2f, "fire is orange: " + c);
    }

    @Test
    void wallsReddenWithDamage() {
        Color intact = TileShade.wall(1f, new Color());
        Color broken = TileShade.wall(0f, new Color());
        assertTrue(broken.r > intact.r && broken.b < intact.b);
    }

    @Test
    void alertsCountFiresBreachesAndVenting() {
        ShipAlerts a = new ShipAlerts().scan(grid);
        assertFalse(a.any());
        assertEquals(1f, a.hullIntegrity, 1e-4f);

        grid.getTile(2, 1).fireIntensity = 0.5f;
        Tile hull = grid.getTile(0, 1);
        hull.setIntegrity(0f);
        for (Compartment c : grid.compartments) {
            c.oxygen = 0;
            c.nitrogen = 0;
        }
        a.scan(grid);
        assertEquals(1, a.burningTiles);
        assertEquals(1, a.breaches);
        assertEquals(1, a.vacuumCompartments);
        assertTrue(a.hullIntegrity < 1f);
        assertTrue(a.any());
    }
}
