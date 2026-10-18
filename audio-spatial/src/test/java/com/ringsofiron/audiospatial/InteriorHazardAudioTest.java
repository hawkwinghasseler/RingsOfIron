package com.ringsofiron.audiospatial;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.systems.InteriorHazardAudioSystem;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InteriorHazardAudioTest {

    private static final float DT = GridHazardSystem.HAZARD_TICK;

    private Engine engine;
    private InteriorHazardAudioSystem sounds;
    private ShipGridComponent grid;
    private Entity ship;

    /** Two rooms split by a light wall at x = 10 with a door at y = 3. */
    @BeforeEach
    void setUp() {
        ChassisBlueprint bp = new ChassisBlueprint(new String[] {
            "####################",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "####################"
        }).tileSize(1f);
        for (int y = 1; y <= 6; y++) bp.edgeWall(10, y, Side.LEFT, WeightClass.LIGHT, y == 3);
        bp.covering(FloorCovering.CARPET, 1, 1, 9, 6);
        grid = ChassisBlueprintLoader.load(bp).grid;

        engine = new Engine();
        engine.addSystem(new GridHazardSystem(1));
        sounds = new InteriorHazardAudioSystem();
        engine.addSystem(sounds);
        ship = new Entity();
        ship.add(grid);
        engine.addEntity(ship);
        run(1);
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) engine.update(DT);
    }

    private Array<AudioEmitterComponent> emitters(String event) {
        Array<AudioEmitterComponent> out = new Array<>();
        for (Entity e : engine.getEntitiesFor(Family.all(AudioEmitterComponent.class).get())) {
            AudioEmitterComponent a = e.getComponent(AudioEmitterComponent.class);
            if (a.eventPath.equals(event)) {
                out.add(a);
                assertNotNull(e.getComponent(TransientComponent.class), "hazard sounds are never saved");
            }
        }
        return out;
    }

    @Test
    void aQuietShipMakesNoHazardSounds() {
        assertEquals(0, sounds.loopCount(ship));
    }

    @Test
    void aFireCracklesAndSoundsTheAlarmUntilItIsOut() {
        grid.getTile(5, 3).ignite();
        run(5);
        Array<AudioEmitterComponent> fire = emitters(AudioEventCatalog.FIRE);
        assertEquals(1, fire.size);
        assertEquals(5.5f, fire.first().localX, 1f);
        assertEquals(3.5f, fire.first().localY, 1f);
        assertSame(ship, fire.first().ship);
        assertEquals(1, emitters(AudioEventCatalog.ALARM).size, "only the burning room's alarm sounds");

        for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
            grid.getTile(x, y).extinguish();
            grid.getTile(x, y).temperature = 293f;
        }
        run(0.5f);
        assertEquals(0, emitters(AudioEventCatalog.FIRE).size);
        assertEquals(0, emitters(AudioEventCatalog.ALARM).size);
    }

    @Test
    void aBreachHissesAtTheHoleUntilItIsPatched() {
        grid.getTile(19, 3).setIntegrity(0f);
        run(1);
        Array<AudioEmitterComponent> hiss = emitters(AudioEventCatalog.BREACH_HISS);
        assertEquals(1, hiss.size);
        assertEquals(19f, hiss.first().localX, 0.01f, "on the hull edge");
        assertEquals(3.5f, hiss.first().localY, 0.01f);
        float loud = hiss.first().parameters.get(AudioParameters.INTENSITY, 0f);
        assertTrue(loud > 0.5f);
        assertEquals(1, emitters(AudioEventCatalog.ALARM).size);

        run(20);
        assertTrue(emitters(AudioEventCatalog.BREACH_HISS).first().parameters.get(AudioParameters.INTENSITY, 0f) < loud,
                "the hiss fades as the room empties");

        grid.getTile(19, 3).bulkheadSealed = true;
        run(1);
        assertEquals(0, emitters(AudioEventCatalog.BREACH_HISS).size);
    }

    @Test
    void doorsSoundWhenTheyMoveButNotWhenTheyBreak() {
        EdgeWall door = grid.getEdge(10, 3, Side.LEFT);
        door.bulkheadSealed = true;
        run(DT);
        assertEquals(1, emitters(AudioEventCatalog.DOOR).size);
        assertEquals(10f, emitters(AudioEventCatalog.DOOR).first().localX, 0.01f);
        assertEquals(3.5f, emitters(AudioEventCatalog.DOOR).first().localY, 0.01f);
        run(1);
        assertEquals(1, emitters(AudioEventCatalog.DOOR).size, "a door that stays shut is silent");

        door.integrity = 0f;
        run(DT);
        assertEquals(1, emitters(AudioEventCatalog.DOOR).size);
    }

    @Test
    void removingTheShipStopsItsSounds() {
        grid.getTile(5, 3).ignite();
        run(2);
        assertTrue(sounds.loopCount(ship) > 0);
        engine.removeEntity(ship);
        run(DT);
        assertEquals(0, emitters(AudioEventCatalog.FIRE).size);
        assertEquals(0, emitters(AudioEventCatalog.ALARM).size);
    }
}
