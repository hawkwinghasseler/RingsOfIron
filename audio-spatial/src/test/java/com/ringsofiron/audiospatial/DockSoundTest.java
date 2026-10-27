package com.ringsofiron.audiospatial;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

/** Sound from one craft heard aboard another docked to it, through the port doors. */
class DockSoundTest {

    private final Engine engine = new Engine();
    private final Entity a = new Entity(), b = new Entity();
    private final ShipGridComponent ga = TestGrids.floor(6, 3), gb = TestGrids.floor(6, 3);
    private final EdgeWall doorA = door(), doorB = door();
    private final AudioListenerComponent listener = new AudioListenerComponent();
    private AudioEmitterComponent hum;

    private static EdgeWall door() {
        return new EdgeWall(WeightClass.MEDIUM, MaterialTable.DEFAULT_MATERIAL, true);
    }

    DockSoundTest() {
        engine.addSystem(new AcousticOcclusionSystem());
        a.add(ga);
        b.add(gb);
        engine.addEntity(a);
        engine.addEntity(b);
        ga.setEdge(5, 1, Side.RIGHT, doorA);
        gb.setEdge(0, 1, Side.LEFT, doorB);
        Entity l = new Entity();
        listener.ship = b;
        listener.mode = AudioListenerComponent.Mode.INTERIOR;
        listener.localX = 3.5f;
        listener.localY = 1.5f;
        l.add(listener);
        engine.addEntity(l);
        Entity e = new Entity();
        hum = AudioEmitterComponent.interior(AudioEventCatalog.REACTOR_HUM, a, 1.5f, 1.5f);
        e.add(hum);
        engine.addEntity(e);
    }

    @Test
    void undockedItIsNotHeardAtAll() {
        engine.update(1f / 60f);
        assertEquals(1f, hum.occlusion);
        assertEquals(0f, hum.transmission, "nothing carries from inside another ship");

        listener.mode = AudioListenerComponent.Mode.EXTERIOR;
        engine.update(1f / 60f);
        assertEquals(0f, hum.transmission, "nor from outside ours");
    }

    @Test
    void dockedButOutsideItIsHeardMuffledThroughTheHull() {
        GridLink.join(a, ga, 5, 1, Side.RIGHT, b, gb, 0, 1, Side.LEFT).setGap(1f);
        listener.mode = AudioListenerComponent.Mode.EXTERIOR;
        engine.update(1f / 60f);
        assertEquals(1f, hum.occlusion);
        assertTrue(hum.transmission > 0f, "docked, it carries through the hulls: " + hum.transmission);
    }

    @Test
    void aCraftDockedThroughAnotherIsHeard() {
        Entity c = new Entity();
        ShipGridComponent gc = TestGrids.floor(6, 3);
        c.add(gc);
        engine.addEntity(c);
        AudioEmitterComponent alarm = AudioEmitterComponent.interior(AudioEventCatalog.REACTOR_HUM, c, 1.5f, 1.5f);
        Entity e = new Entity();
        e.add(alarm);
        engine.addEntity(e);
        engine.update(1f / 60f);
        assertEquals(0f, alarm.transmission);

        // c docks to a, which docks to the listener's b
        GridLink.join(a, ga, 5, 1, Side.RIGHT, b, gb, 0, 1, Side.LEFT).setGap(1f);
        GridLink.join(c, gc, 5, 1, Side.RIGHT, a, ga, 0, 1, Side.LEFT).setGap(1f);
        engine.update(1f / 60f);
        assertTrue(alarm.transmission > 0f, "heard through the station between: " + alarm.transmission);
    }

    @Test
    void openDoorsLetItThroughAndShutOnesMuffleIt() {
        GridLink.join(a, ga, 5, 1, Side.RIGHT, b, gb, 0, 1, Side.LEFT).setGap(1f);
        engine.update(1f / 60f);
        float open = hum.transmission;
        assertTrue(open > 0.3f, "two open doorways: " + open);
        assertTrue(hum.occlusion < 0.1f, "and bright: " + hum.occlusion);
        assertEquals(2 * com.ringsofiron.audiospatial.SoundField.OPEN_DOOR_DB, hum.roomLossDb, 1e-3f);

        doorA.bulkheadSealed = true;
        doorB.bulkheadSealed = true;
        engine.update(1f / 60f);
        assertTrue(hum.transmission < open * 0.1f, "two sealed doors: " + hum.transmission);
        assertTrue(hum.occlusion > 0.3f, "and muffled: " + hum.occlusion);
    }
}
