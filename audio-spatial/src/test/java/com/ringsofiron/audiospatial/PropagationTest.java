package com.ringsofiron.audiospatial;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.HeadlessAudioBackend;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The direct path plus the room path, on the demo ship's layout: engine room | middle (split
 * in two) | bridge, every internal wall with one door.
 */
class PropagationTest {
    private static final int W = 26, H = 15;

    private Engine engine;
    private ShipGridComponent grid;
    private Entity ship;
    private AudioListenerComponent listener;
    private AudioEmitterComponent reactor;
    private EdgeWall engineDoor, middleDoor, bridgeDoor;

    @BeforeEach
    void setUp() {
        grid = new ShipGridComponent(W, H, 1f);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                Tile t = grid.getTile(x, y);
                if (x == 0 || y == 0 || x == W - 1 || y == H - 1) t.wallType = Tile.WallType.HULL;
                else t.floorType = Tile.FloorType.FLOOR;
            }
        for (int col : new int[]{8, 16}) {
            for (int y = 1; y < H - 1; y++) {
                EdgeWall w = new EdgeWall(WeightClass.MEDIUM, "steel", y == 7);
                grid.setEdge(col, y, Side.RIGHT, w);
                if (y == 7) {
                    if (col == 8) engineDoor = w;
                    else bridgeDoor = w;
                }
            }
        }
        for (int x = 9; x <= 16; x++) {
            EdgeWall w = new EdgeWall(WeightClass.MEDIUM, "steel", x == 12);
            grid.setEdge(x, 7, Side.TOP, w);
            if (x == 12) middleDoor = w;
        }

        engine = new Engine();
        engine.addSystem(new AcousticOcclusionSystem());
        engine.addSystem(new AudioUpdateSystem(new AudioManager(new HeadlessAudioBackend())));
        ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(grid);
        engine.addEntity(ship);
        Entity l = new Entity();
        listener = new AudioListenerComponent();
        listener.mode = AudioListenerComponent.Mode.INTERIOR;
        listener.ship = ship;
        l.add(listener);
        engine.addEntity(l);

        Entity r = new Entity();
        reactor = AudioEmitterComponent.interior(AudioEventCatalog.REACTOR_HUM, ship, 4.5f, 7.5f);
        r.add(reactor);
        engine.addEntity(r);
    }

    private float gainAt(float x, float y) {
        listener.localX = x;
        listener.localY = y;
        engine.update(1f / 60f);
        return ((SimulatedInstance) reactor.instance).mix.gain;
    }

    private static float db(float a, float b) {
        return (float) Math.abs(20 * Math.log10(a / b));
    }

    /** Walking north-south through the bridge, one tile at a time, must not make the reactor jump in volume. */
    private void assertSmoothWalk(String doors) {
        float prev = -1f;
        for (int y = 2; y <= 12; y++) {
            float g = gainAt(17.5f, y + 0.5f);
            assertTrue(g > 0f, doors + ": audible at row " + y);
            if (prev > 0f) {
                assertTrue(db(g, prev) < 3f, doors + ": jump of " + db(g, prev) + " dB stepping to row " + y);
            }
            prev = g;
        }
    }

    @Test
    void loudnessChangesSmoothlyWithDoorsSealed() {
        engineDoor.bulkheadSealed = middleDoor.bulkheadSealed = bridgeDoor.bulkheadSealed = true;
        assertSmoothWalk("sealed");
    }

    @Test
    void loudnessChangesSmoothlyWithDoorsOpen() {
        assertSmoothWalk("open");
    }

    @Test
    void openDoorsLetMoreThroughThanSealedOnes() {
        float open = gainAt(20.5f, 3.5f);
        engineDoor.bulkheadSealed = bridgeDoor.bulkheadSealed = true;
        float sealed = gainAt(20.5f, 3.5f);
        assertTrue(open > sealed * 4f, "open " + open + " sealed " + sealed);
        assertTrue(reactor.occlusion > 0.2f, "sealed doors muffle");
    }

    @Test
    void eachRoomBetweenCostsVolume() {
        // Same straight-line distance (about 8 m), one boundary away versus two
        float oneRoom = gainAt(12.5f, 3.5f);   // lower middle: through the engine door
        float twoRooms = gainAt(12.5f, 12.5f);  // upper middle: engine door then middle door
        assertTrue(oneRoom > twoRooms, "one room " + oneRoom + " two rooms " + twoRooms);
    }

    @Test
    void directPathDominatesInTheSameRoom() {
        float near = gainAt(6.5f, 7.5f);
        assertTrue(reactor.occlusion < 0.05f, "bright in the same room: " + reactor.occlusion);
        assertTrue(near > 0.2f, "loud close by: " + near);
    }
}
