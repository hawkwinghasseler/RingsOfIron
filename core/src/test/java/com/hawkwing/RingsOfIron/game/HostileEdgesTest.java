package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hostile traffic at its edges: which pirates are bold enough to hunt the player, the side each
 * hostile's marker shows, and a hostile saved while it is in the bubble.
 */
class HostileEdgesTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private String hostile(boolean pirate, String shipOrFaction) {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            HostileComponent h = e.getValue();
            if (h.isPirate() != pirate) continue;
            if (pirate ? shipOrFaction.equals(h.shipId) : shipOrFaction.equals(h.faction)) return e.getKey();
        }
        fail("no such hostile: " + shipOrFaction);
        return null;
    }

    @Test
    void onlyAGunshipIsBoldEnoughToHuntTheStarterCorvette() {
        double player = HostileTraffic.firepower(game.player);
        double raider = HostileTraffic.firepower(world.engine.getEntity(hostile(true, HostileTraffic.RAIDER)));
        double gunship = HostileTraffic.firepower(world.engine.getEntity(hostile(true, HostileTraffic.GUNSHIP)));
        assertTrue(raider > 0 && gunship > raider && player > 0);
        assertTrue(raider < HostileTrafficController.PLAYER_NERVE * player, "a raider leaves the player alone");
        assertTrue(gunship >= HostileTrafficController.PLAYER_NERVE * player, "a gunship dares");
        assertEquals(0, HostileTraffic.firepower(null));
    }

    @Test
    void markersShowWhoseSideEachHostileIsOn() {
        Entity pirate = world.engine.getEntity(hostile(true, HostileTraffic.RAIDER));
        Entity sponsor = world.engine.getEntity(hostile(false, HostileTraffic.SPONSOR));
        Entity selene = world.engine.getEntity(hostile(false, HostileTraffic.SELENE));
        assertEquals(HudMarkerComponent.Kind.HOSTILE, HostileTraffic.markerFor(pirate).kind);
        assertEquals(HudMarkerComponent.Kind.FRIENDLY, HostileTraffic.markerFor(sponsor).kind);
        assertEquals(HudMarkerComponent.Kind.NEUTRAL, HostileTraffic.markerFor(selene).kind);
        assertEquals(hostiles.get(world.engine.getEntity(hostile(true, HostileTraffic.RAIDER))
                .getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id).displayName,
                HostileTraffic.markerFor(pirate).label);
        assertEquals("Freighter", HostileTraffic.markerFor(new Entity()).label, "anything else is traffic");
    }

    @Test
    void aHostileSavedInTheBubbleComesBackInIt(@TempDir Path dir) {
        String id = hostile(true, HostileTraffic.RAIDER);
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(id, GameScenario.EARTH, s.set(s.x + 3_000, s.y, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(id);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertEquals(HostileComponent.State.IN_BUBBLE, hostiles.get(id).state);
        HostileComponent.State resume = hostiles.get(id).resumeState;
        FileHandle file = new FileHandle(dir.resolve("inBubble.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario.start(fresh, assets, assets.resolve("no-mods-here")).quickLoad(file);
            HostileTrafficController back = fresh.macroSim.hostiles();
            assertEquals(hostiles.all().size(), back.all().size(), "the same hostiles");
            HostileComponent h = back.get(id);
            assertNotNull(h);
            for (int i = 0; i < 120; i++) fresh.update(FRAME);
            Entity loaded = fresh.engine.getEntity(id);
            assertNotNull(loaded.getComponent(PhysicsBodyComponent.class), "still in the bubble");
            assertEquals(HostileComponent.State.IN_BUBBLE, h.state);
            assertEquals(resume, h.resumeState, "and knows what it was doing before");
            assertNotNull(loaded.getComponent(HudMarkerComponent.class));
            assertEquals(HudMarkerComponent.Kind.HOSTILE, loaded.getComponent(HudMarkerComponent.class).kind);
        } finally {
            fresh.dispose();
        }
    }
}
