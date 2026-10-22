package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The audio demo's world headless: the game's hull, crew and audio systems, and the demo's own sounds. */
class AudioDemoSceneTest {
    static final float FRAME = 1f / 60f;

    AudioDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new AudioDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
        demo.load();
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) demo.update(FRAME);
    }

    private AudioListenerComponent listener() {
        return demo.ship.getComponent(AudioListenerComponent.class);
    }

    @Test
    void theGamesHullWithTheAvatarAndCrewAboard() {
        assertEquals(GameScenario.PLAYER_SHIP, demo.hull);
        assertNotNull(demo.avatar.getComponent(PlayerAvatarComponent.class));
        assertSame(demo.avatar, demo.world.avatar.getAvatar());
        assertSame(demo.ship, demo.world.getPlayer());
        assertTrue(demo.world.crew.crewEntities().size() >= 2, "the hull's crew are aboard with the avatar");
    }

    @Test
    void fourSoundsOnTheDeckSpreadApart() {
        assertEquals(4, demo.sources.size());
        ShipGridComponent g = demo.grid();
        for (AudioDemoScene.Source s : demo.sources) {
            int[] t = g.localToTile(s.emitter.localX, s.emitter.localY);
            assertTrue(t[0] >= 0, s.name + " is on the deck");
            assertSame(demo.ship, s.emitter.ship);
            for (AudioDemoScene.Source o : demo.sources) {
                if (o != s) assertNotEquals(0f, Vector2.dst(s.emitter.localX, s.emitter.localY, o.emitter.localX, o.emitter.localY));
            }
        }
    }

    @Test
    void theGamesListenerFollowsTheAvatarAcrossTheDeck() {
        run(0.2f);
        Vector2 start = demo.avatarLocal(new Vector2()).cpy();
        assertEquals(AudioListenerComponent.Mode.INTERIOR, listener().mode);
        assertEquals(start.x, listener().localX, 0.6f);
        assertEquals(start.y, listener().localY, 0.6f);
        // Walk whichever way the deck allows; the listener goes along
        float[][] ways = {{1f, 0f}, {-1f, 0f}, {0f, 1f}, {0f, -1f}};
        Vector2 now = new Vector2();
        for (float[] w : ways) {
            for (int i = 0; i < 90; i++) {
                demo.walk(w[0], w[1]);
                demo.update(FRAME);
            }
            demo.walk(0f, 0f);
            if (demo.avatarLocal(now).dst(start) > 0.5f) break;
        }
        assertTrue(now.dst(start) > 0.5f, "the avatar walked");
        assertEquals(now.x, listener().localX, 0.6f);
        assertEquals(now.y, listener().localY, 0.6f);
    }

    @Test
    void soundsSwitchOffAndOn() {
        run(0.2f);
        AudioDemoScene.Source hum = demo.sources.get(0);
        assertTrue(hum.playing(), "the sounds start on");
        demo.toggleSource(0);
        run(0.1f);
        assertFalse(hum.playing());
        demo.toggleSource(0);
        run(0.1f);
        assertTrue(hum.playing());
    }

    @Test
    void theFittedBulkheadsMakeRoomsAndTheirSealedDoorsMuffle() {
        run(0.5f);
        int walls = 0;
        float before = 0f;
        for (AudioDemoScene.Source s : demo.sources) {
            walls += s.emitter.wallCount;
            before += s.emitter.occlusion;
        }
        assertTrue(walls > 0, "some sounds are heard through a bulkhead");
        ShipGridComponent g = demo.grid();
        for (int[] b : AudioDemoScene.BULKHEADS) {
            EdgeWall door = g.edgeV[b[1]][b[0]];
            assertTrue(door.hasBulkhead);
            door.bulkheadSealed = true;
        }
        g.gridDirty = true;
        run(0.5f);
        float after = 0f;
        for (AudioDemoScene.Source s : demo.sources) after += s.emitter.occlusion;
        assertTrue(after > before, "sealed doors muffle: " + before + " -> " + after);
    }

    @Test
    void theNearestDoorSealsAndOpensWithinReach() {
        ShipGridComponent g = demo.grid();
        // Stand the avatar right beside a door
        EdgeWall door = null;
        int dx = -1, dy = -1;
        for (int y = 0; y < g.height && door == null; y++) {
            for (int x = 1; x < g.width && door == null; x++) {
                EdgeWall w = g.edgeV[y][x];
                if (w != null && w.hasBulkhead && g.getTile(x, y).isFloor()) {
                    door = w;
                    dx = x;
                    dy = y;
                }
            }
        }
        assertNotNull(door, "the demo fits the corvette with doors between rooms");
        demo.avatar.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class).setTile(dx, dy);
        boolean was = door.bulkheadSealed;
        assertEquals(was ? "Door open" : "Door sealed", demo.toggleNearestDoor());
        assertEquals(!was, door.bulkheadSealed);
        demo.toggleNearestDoor();
        assertEquals(was, door.bulkheadSealed);
    }

    @Test
    void exteriorListeningHearsFromTheShipAndStopsTheWalk() {
        demo.toggleListening();
        assertFalse(demo.listeningInside());
        run(0.1f);
        assertEquals(AudioListenerComponent.Mode.EXTERIOR, listener().mode);
        demo.walk(1f, 0f);
        PlayerAvatarComponent walker = demo.avatar.getComponent(PlayerAvatarComponent.class);
        assertEquals(0f, walker.moveX);
        assertEquals(0f, walker.moveY);
        demo.laser();
        demo.hullHit();
        run(0.1f);
        demo.toggleListening();
        assertTrue(demo.listeningInside());
    }

    @Test
    void theCrewAreHeardThroughTheGamesCrewAudio() {
        AudioEmitterComponent any = demo.sources.get(1).emitter;
        assertNotNull(any);
        run(20f);
        assertTrue(demo.world.crewAudio.footstepsPlayed() + demo.world.crewAudio.voicesPlayed() > 0,
                "the crew going about the ship make the game's sounds");
    }
}
