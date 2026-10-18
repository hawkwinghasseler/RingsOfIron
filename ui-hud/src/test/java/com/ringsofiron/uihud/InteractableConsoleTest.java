package com.ringsofiron.uihud;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractableConsoleTest {
    private static Entity ship(String uuid) {
        Entity e = new Entity();
        e.add(new UuidComponent(uuid));
        return e;
    }

    @Test
    void aConsoleIsOnTheShipItWasPlacedOn() {
        Entity ship = ship("a");
        InteractableConsoleComponent console = new InteractableConsoleComponent("helm", "Helm", ship, 1, 4);
        assertTrue(console.isOn(ship));
        assertFalse(console.isOn(ship("b")));
        assertFalse(console.isOn(null));
    }

    @Test
    void afterALoadItFindsItsShipByUuid() {
        // As loaded from a save: the UUID survives, the live reference does not.
        InteractableConsoleComponent console = new InteractableConsoleComponent();
        console.shipUuid = "a";
        assertFalse(console.isOn(ship("b")));
        Entity ship = ship("a");
        assertTrue(console.isOn(ship));
        assertSame(ship, console.ship);
    }
}
