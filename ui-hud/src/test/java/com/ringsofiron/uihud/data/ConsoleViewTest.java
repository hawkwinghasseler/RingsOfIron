package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.HudData.ConsoleView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the open console and its ship's deck stand, and how far the captain is, for a panel drawn beside it. */
class ConsoleViewTest {
    @Test
    void theConsoleStandsAtTheMiddleOfItsTileAsTheShipIsTurnedAndMoved() {
        Entity ship = new Entity();
        ship.add(new TransformComponent(100f, 50f, 90f));
        // Tile (2, 1) of 2 m tiles from the body's origin is centred at (5, 3) in the ship's own frame
        ship.add(new ShipGridComponent(6, 4, 2f));
        InteractableConsoleComponent c = new InteractableConsoleComponent("reactor", "Reactor", ship, 2, 1);
        Entity console = new Entity();
        console.add(c);
        ConsoleView v = new ConsoleView();
        v.set(console, c, null);
        assertTrue(Float.isNaN(v.reach) && Float.isNaN(v.worldX) && Float.isNaN(v.hullX[0]));
        v.track(1.2f);
        assertEquals(1.2f, v.reach);
        // Turned a quarter turn left: (5, 3) becomes (-3, 5)
        assertEquals(97f, v.worldX, 1e-4f);
        assertEquals(55f, v.worldY, 1e-4f);
        // The deck, 12 x 8 m from the body's origin, turned with it: (12, 0) becomes (0, 12), and so on
        float[] x = {100f, 100f, 92f, 92f}, y = {50f, 62f, 62f, 50f};
        for (int i = 0; i < 4; i++) {
            assertEquals(x[i], v.hullX[i], 1e-4f, "corner " + i);
            assertEquals(y[i], v.hullY[i], 1e-4f, "corner " + i);
        }
        // A ship with no pose can't say where
        ship.remove(TransformComponent.class);
        v.track(Float.NaN);
        assertTrue(Float.isNaN(v.worldX) && Float.isNaN(v.worldY) && Float.isNaN(v.reach) && Float.isNaN(v.hullY[2]));
        v.track(1f);
        v.clear();
        assertTrue(Float.isNaN(v.reach));
    }
}
