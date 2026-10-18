package com.ringsofiron.audiospatial;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.audiospatial.backend.AcousticModel;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.HeadlessAudioBackend;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.events.CollisionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioSystemsTest {
    private static final float DT = 1f / 60f;

    private Engine engine;
    private HeadlessAudioBackend backend;
    private AudioManager audio;
    private AudioUpdateSystem updateSystem;
    private Entity ship;
    private ShipGridComponent grid;
    private AudioListenerComponent listener;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        backend = new HeadlessAudioBackend();
        audio = new AudioManager(backend);
        engine.addSystem(new AcousticOcclusionSystem());
        updateSystem = new AudioUpdateSystem(audio);
        engine.addSystem(updateSystem);

        // Two rooms, 10 x 4 m each, split by a wall on the right edge of column 9
        grid = TestGrids.floor(20, 4);
        TestGrids.edgeColumn(grid, 9);
        ship = new Entity();
        ship.add(new TransformComponent(100f, 50f, 0f));
        ship.add(grid);
        engine.addEntity(ship);

        Entity l = new Entity();
        listener = new AudioListenerComponent();
        listener.ship = ship;
        l.add(listener);
        engine.addEntity(l);
    }

    private void walkTo(float localX, float localY) {
        listener.mode = AudioListenerComponent.Mode.INTERIOR;
        listener.localX = localX;
        listener.localY = localY;
    }

    private AudioEmitterComponent addInterior(String path, float lx, float ly) {
        Entity e = new Entity();
        AudioEmitterComponent em = AudioEmitterComponent.interior(path, ship, lx, ly);
        e.add(em);
        engine.addEntity(e);
        return em;
    }

    private SimulatedInstance instanceOf(AudioEmitterComponent em) {
        return (SimulatedInstance) em.instance;
    }

    @Test
    void wallBetweenRoomsMufflesTheSound() {
        AudioEmitterComponent hum = addInterior(AudioEventCatalog.REACTOR_HUM, 15.5f, 2f);
        walkTo(12.5f, 2f); // same room, 3 m away
        engine.update(DT);
        SimulatedInstance inst = instanceOf(hum);
        assertTrue(inst.isPlaying());
        assertEquals(0, hum.wallCount);
        float clearGain = inst.mix.gain;
        assertEquals(AcousticModel.OPEN_CUTOFF_HZ, inst.mix.cutoffHz, 1f);

        walkTo(18.5f, 2f); // 3 m the other way, same room: just as loud
        engine.update(DT);
        assertEquals(clearGain, inst.mix.gain, clearGain * 0.02f);

        // Now stand 3 m away but through the wall
        hum.localX = 11.5f;
        listener.localX = 8.5f;
        engine.update(DT);
        assertEquals(1, hum.wallCount);
        assertTrue(hum.transmission < 0.2f, "a wall costs well over 10 dB: " + hum.transmission);
        assertTrue(inst.mix.cutoffHz < 10000f, "high frequencies cut: " + inst.mix.cutoffHz);
        assertTrue(inst.mix.gain < clearGain * 0.2f, "much quieter through a wall");
    }

    @Test
    void interiorSoundsAreClampedWhenListeningFromOutside() {
        AudioEmitterComponent hum = addInterior(AudioEventCatalog.REACTOR_HUM, 5f, 2f);
        listener.mode = AudioListenerComponent.Mode.EXTERIOR;
        engine.update(DT);
        assertEquals(1f, hum.occlusion);
        assertEquals(AcousticModel.MUFFLED_CUTOFF_HZ, instanceOf(hum).mix.cutoffHz, 1f);
    }

    @Test
    void exteriorSoundsAreNeverOccluded() {
        Entity gun = new Entity();
        gun.add(new TransformComponent(120f, 50f, 0f));
        AudioEmitterComponent em = new AudioEmitterComponent(AudioEventCatalog.THRUSTER);
        gun.add(em);
        engine.addEntity(gun);
        walkTo(1f, 1f);
        engine.update(DT);
        assertEquals(0f, em.occlusion);
        assertEquals(120f, instanceOf(em).x, 1e-4f);
    }

    @Test
    void interiorEmittersFollowTheShip() {
        AudioEmitterComponent hum = addInterior(AudioEventCatalog.REACTOR_HUM, 10f, 0f);
        ship.getComponent(TransformComponent.class).rotation = 90f;
        engine.update(DT);
        assertEquals(100f, instanceOf(hum).x, 1e-3f);
        assertEquals(60f, instanceOf(hum).y, 1e-3f);

        walkTo(0f, 0f);
        engine.update(DT);
        assertEquals(100f, backend.getListenerX(), 1e-3f);
        assertEquals(50f, backend.getListenerY(), 1e-3f);
    }

    @Test
    void ventedRoomGoesSilent() {
        CompartmentBuilder.rebuild(grid);
        AudioEmitterComponent fire = addInterior(AudioEventCatalog.FIRE, 15f, 2f);
        walkTo(5f, 2f);
        engine.update(DT);
        assertEquals(1f, fire.pressure, 0.05f);

        Compartment room = grid.compartmentAt(15, 2);
        room.oxygen = room.nitrogen = room.carbonDioxide = 0;
        engine.update(DT);
        assertEquals(0f, fire.pressure, 1e-6f);
        assertEquals(0f, instanceOf(fire).mix.gain, 1e-6f);
    }

    @Test
    void removingTheEntityStopsAndFreesTheSound() {
        AudioEmitterComponent hum = addInterior(AudioEventCatalog.REACTOR_HUM, 5f, 2f);
        engine.update(DT);
        SimulatedInstance inst = instanceOf(hum);
        assertEquals(1, backend.getInstances().size);
        Entity e = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(AudioEmitterComponent.class).get()).first();
        engine.removeEntity(e);
        engine.update(DT);
        assertFalse(inst.isPlaying());
        assertNull(hum.instance);
        assertEquals(0, backend.getInstances().size);
    }

    @Test
    void spawnedOneShotRemovesItselfWhenDone() {
        AudioEmitters.spawnInteriorOneShot(engine, AudioEventCatalog.DOOR, ship, 9.5f, 1f, 1f);
        walkTo(9f, 1f);
        engine.update(DT);
        assertEquals(1, backend.find(AudioEventCatalog.DOOR).size);
        for (int i = 0; i < 60; i++) engine.update(DT);
        assertEquals(0, backend.find(AudioEventCatalog.DOOR).size);
        assertEquals(0, engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(AudioEmitterComponent.class).get()).size());
    }

    @Test
    void worldShiftDoesNotLookLikeSpeed() {
        Entity rock = new Entity();
        TransformComponent t = new TransformComponent(500f, 0f, 0f);
        rock.add(t);
        AudioEmitterComponent em = new AudioEmitterComponent(AudioEventCatalog.THRUSTER);
        rock.add(em);
        engine.addEntity(rock);
        engine.update(DT);
        // Floating origin moves everything back by 1000 m
        t.x -= 1000f;
        ship.getComponent(TransformComponent.class).x -= 1000f;
        updateSystem.onWorldShift(1000f, 0f);
        engine.update(DT);
        assertEquals(0f, instanceOf(em).vx, 1e-3f);
    }

    @Test
    void collisionsPlayHullImpacts() {
        ImpactAudio impacts = new ImpactAudio(audio);
        CollisionEvent hit = new CollisionEvent();
        hit.pointX = 110f;
        hit.pointY = 50f;
        hit.approachSpeed = 10f;
        impacts.receive(null, hit);
        hit.approachSpeed = 0.1f; // a scrape: silent
        impacts.receive(null, hit);
        engine.update(DT);
        assertEquals(1, backend.find(AudioEventCatalog.HULL_IMPACT).size);
        assertEquals(0.5f, backend.find(AudioEventCatalog.HULL_IMPACT).first().getParameter("Intensity", 0f), 1e-6f);
    }

    @Test
    void assetTableEventsAreRegistered() {
        // Every path in assets.toml's [audio_event] table has playback rules, so none falls back to the beep
        try {
            String toml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("../assets/assets.toml")));
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(event:/[^\"]+)\"").matcher(toml);
            int found = 0;
            while (m.find()) {
                found++;
                assertTrue(backend.getCatalog().contains(m.group(1)), "no definition for " + m.group(1));
            }
            assertTrue(found > 0);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
