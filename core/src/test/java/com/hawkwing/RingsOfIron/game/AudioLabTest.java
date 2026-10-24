package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.audiospatial.backend.AcousticModel;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The audio scenario's bench on a real new game: the demo hull, the game's hazards as sound sources, every hull. */
class AudioLabTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    WorldDebug tools;
    AudioLab lab;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        tools = new WorldDebug(world);
        lab = new AudioLab(world, game, tools);
        lab.show(AudioLab.DEMO_HULL);
    }

    @AfterEach
    void tearDown() {
        tools.dispose();
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) {
            world.update(FRAME);
            lab.update();
        }
    }

    private List<AudioEmitterComponent> sounds(String event) {
        List<AudioEmitterComponent> out = new ArrayList<>();
        for (AudioEmitterComponent em : lab.soundsAboard(new ArrayList<>())) if (event.equals(em.eventPath)) out.add(em);
        return out;
    }

    @Test
    void thePlayerIsAboardTheDemoHullWithTheCrew() {
        assertEquals(AudioLab.DEMO_HULL, game.player.getComponent(DefRefComponent.class).defId);
        assertSame(game.player, world.getPlayer());
        assertSame(game.player, lab.craft());
        int aboard = 0;
        for (Entity e : world.crew.crewEntities()) if (world.crew.position(e).ship == game.player) aboard++;
        assertTrue(aboard >= 2, "the avatar and crew came across: " + aboard);
        assertNull(world.bridge.seat(), "the avatar stands");
        ShipGridComponent g = lab.grid();
        EdgeWall labDoor = g.edgeH[AudioLab.DEMO_LAB_DOOR[1] + 1][AudioLab.DEMO_LAB_DOOR[0]];
        assertTrue(labDoor.hasBulkhead && labDoor.bulkheadSealed, "the lab starts shut");
    }

    @Test
    void theDemoHullHasItsRooms() {
        run(0.5f);
        // Engineering, corridor, quarters, bridge, hold, lab and the forward room
        assertTrue(lab.grid().compartments.size >= 7, "rooms: " + lab.grid().compartments.size);
        assertEquals(1, lab.hums.size(), "one reactor");
        AudioEmitterComponent hum = lab.hums.get(0).emitter;
        assertTrue(hum.instance != null && hum.instance.isPlaying());
        assertTrue(hum.wallCount >= 1, "engineering is heard from the bridge through its wall");
    }

    @Test
    void theReactorsHumStopsWithIt() {
        run(0.2f);
        assertEquals("Reactor off", lab.toggleReactor());
        run(0.2f);
        assertFalse(lab.hums.get(0).emitter.instance != null && lab.hums.get(0).emitter.instance.isPlaying());
        assertEquals("Reactor on", lab.toggleReactor());
        run(0.2f);
        assertTrue(lab.hums.get(0).emitter.instance.isPlaying());
    }

    @Test
    void aFireInTheQuartersIsTheGamesFireAndAlarm() {
        assertEquals("Fire", lab.toggleFire());
        // Soon, before the crew put it out
        run(0.5f);
        assertFalse(sounds(AudioEventCatalog.FIRE).isEmpty(), "the game's fire loop");
        assertFalse(sounds(AudioEventCatalog.ALARM).isEmpty(), "the game's alarm");
        assertEquals("Fire out", lab.toggleFire());
        assertFalse(lab.burning());
    }

    @Test
    void ventingTheLabThinsItsAirAndMufflesItsHiss() {
        run(0.5f);
        lab.toggleVent();
        assertTrue(lab.venting());
        run(3f);
        List<AudioEmitterComponent> hiss = sounds(AudioEventCatalog.BREACH_HISS);
        assertFalse(hiss.isEmpty(), "the breach hisses");
        AudioEmitterComponent h = hiss.get(0);
        assertTrue(h.pressure < 0.9f, "the lab's air is going: " + h.pressure);
        SimulatedInstance inst = (SimulatedInstance) h.instance;
        assertTrue(inst.mix.cutoffHz < AcousticModel.OPEN_CUTOFF_HZ - 1f, "thin air muffles: " + inst.mix.cutoffHz);
        assertEquals("Hull patched, fresh air", lab.toggleVent());
    }

    @Test
    void theNearestDoorSealsAndOpens() {
        // The bridge door onto the corridor, from just inside the bridge
        GridPositionComponent pos = world.crew.position(game.avatar);
        pos.stop();
        pos.setTile(18, 7);
        EdgeWall door = lab.grid().edgeH[7][18];
        assertTrue(door.hasBulkhead);
        boolean was = door.bulkheadSealed;
        assertEquals(was ? "Door open" : "Door sealed", lab.toggleNearestDoor());
        assertEquals(!was, door.bulkheadSealed);
    }

    @Test
    void everyHullCanBeBoarded() {
        List<String> hulls = lab.hulls();
        assertEquals(AudioLab.DEMO_HULL, hulls.get(0));
        assertTrue(hulls.contains(GameScenario.PLAYER_SHIP));
        assertTrue(hulls.stream().anyMatch(lab::isStation), "station layouts too: " + hulls);
        for (int i = 0; i < hulls.size(); i++) {
            String said = lab.cycle(1);
            assertTrue(said.startsWith("Aboard"), said);
            Entity craft = lab.craft();
            if (lab.isStation(lab.hull)) {
                assertEquals(lab.hull, craft.getComponent(StationComponent.class).blueprint);
                assertTrue(world.docking.docked(game.player, craft), "the player's ship docked there");
            } else {
                assertSame(game.player, craft);
                assertEquals(lab.hull, craft.getComponent(DefRefComponent.class).defId);
            }
            run(0.2f);
        }
        assertEquals(AudioLab.DEMO_HULL, lab.hull, "round to the start");
    }

    @Test
    void laserAndHullHitPlayOutside() {
        run(0.1f);
        lab.laser();
        lab.hullHit();
        run(0.1f);
    }
}
