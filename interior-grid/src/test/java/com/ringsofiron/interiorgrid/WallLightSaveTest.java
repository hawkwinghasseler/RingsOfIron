package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A deck's wall lights go into a save and come back. */
class WallLightSaveTest {
    static ShipGridComponent roundTrip(ShipGridComponent grid) {
        Json json = new Json();
        json.setSerializer(ShipGridComponent.class, new ShipGridSerializer());
        return json.fromJson(ShipGridComponent.class, json.toJson(grid, ShipGridComponent.class));
    }

    static ShipGridComponent room() {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(
                "#####",
                "#...#",
                "#####")).grid;
    }

    @Test
    void placedLightsAreSaved() {
        ShipGridComponent g = room();
        g.wallLights = new Array<>();
        g.wallLights.add(new WallLight(2, 1, Side.TOP));
        ShipGridComponent back = roundTrip(g);
        assertEquals(1, back.wallLights.size);
        assertEquals(new WallLight(2, 1, Side.TOP), back.wallLights.first());
    }

    @Test
    void aGridWithoutPlacedLightsStillHasNone() {
        assertNull(roundTrip(room()).wallLights);
    }
}
