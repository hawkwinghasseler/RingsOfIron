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

    @Test
    void trafficsOwnStateIsSavedWithTheGame(@TempDir Path dir) {
        // Some way into the game: the next top-up and the player's grace are not a new game's
        world.macroSim.advanceTo(world.hostiles.nextTopUp() + 1);
        for (int i = 0; i < 5; i++) world.update(FRAME);
        double now = world.macroSim.now();
        double topUp = world.hostiles.nextTopUp(), grace = hostiles.playerGraceUntil;
        assertTrue(topUp > now && grace > now);
        world.hostiles.intercepting(hostile(true, HostileTraffic.GUNSHIP), HostileComponent.PLAYER, now);
        assertTrue(world.hostiles.alerted());
        int pirates = hostiles.all().size();
        FileHandle file = new FileHandle(dir.resolve("traffic.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            // The fresh game's own timers, moved on so they can't pass for the saved ones
            fresh.hostiles.reset();
            loaded.quickLoad(file);
            assertEquals(topUp, fresh.hostiles.nextTopUp(), 1e-6, "the top-up is still due when it was");
            assertEquals(grace, fresh.macroSim.hostiles().playerGraceUntil, 1e-6, "and the grace still runs");
            assertTrue(fresh.hostiles.alerted(), "a pirate is still coming for the player");
            assertEquals(pirates, fresh.macroSim.hostiles().all().size());
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aSaveFromBeforeTrafficWasSavedStillTopsUpAndNumbersOn(@TempDir Path dir) {
        // Hours in, with more pirates than a new game has
        world.hostiles.difficulty = () -> 2.0;
        world.macroSim.advanceTo(world.hostiles.nextTopUp() + 1);
        for (int i = 0; i < 5; i++) world.update(FRAME);
        FileHandle file = new FileHandle(dir.resolve("old.json").toFile());
        assertNull(game.quickSave(file));
        // As an older build wrote it: no hostile-traffic entry among the modules
        com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(file.readString("UTF-8"));
        root.get("modules").remove(HostileTraffic.SAVE_KEY);
        file.writeString(root.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json), false, "UTF-8");

        GameWorld fresh = new GameWorld();
        try {
            GameScenario.start(fresh, assets, assets.resolve("no-mods-here")).quickLoad(file);
            double now = fresh.macroSim.now();
            assertEquals(now + HostileTraffic.TOP_UP_INTERVAL, fresh.hostiles.nextTopUp(), 1e-6,
                    "top-ups start again from the load");
            int highest = 0;
            for (HostileComponent h : fresh.macroSim.hostiles().all().values()) {
                highest = Math.max(highest, HostileTraffic.number(h.displayName));
            }
            assertTrue(highest > 0);
            // A harder game wants more pirates, and they take the next numbers, not a loaded one's
            int before = fresh.macroSim.hostiles().all().size();
            fresh.hostiles.difficulty = () -> 3.0;
            fresh.macroSim.advanceTo(fresh.hostiles.nextTopUp() + 1);
            for (int i = 0; i < 5; i++) fresh.update(FRAME);
            java.util.Set<String> names = new java.util.HashSet<>();
            int newest = 0;
            for (HostileComponent h : fresh.macroSim.hostiles().all().values()) {
                assertTrue(names.add(h.displayName), "two hostiles named " + h.displayName);
                newest = Math.max(newest, HostileTraffic.number(h.displayName));
            }
            int added = fresh.macroSim.hostiles().all().size() - before;
            assertTrue(added > 0, "a harder game tops up");
            assertEquals(highest + added, newest, "and the new pirates are numbered on");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aHostilesNumberIsTheEndOfItsName() {
        assertEquals(12, HostileTraffic.number("Pirate raider 12"));
        assertEquals(0, HostileTraffic.number("Kestrel"));
        assertEquals(0, HostileTraffic.number(null));
        assertEquals(0, HostileTraffic.number("Raider 12345678901"), "too long to be a counter");
    }
}
