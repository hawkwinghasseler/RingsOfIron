package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.OrbitMath;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.physicsexterior.anchor.InertialAnchor;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The bubble anchor kept in step with orbital-mechanics' event system, including SOI changes. */
class OrbitalAnchorBridgeTest {
    private static final double MU_EARTH = 3.986004418e14;
    private static final double R_EARTH = 6.371e6;
    private static final double MU_MOON = 4.9048695e12;
    private static final double MOON_A = 3.844e8;
    private static final double LEO = R_EARTH + 400e3;

    private GameEngine game;
    private OrbitalEventQueue queue;
    private OrbitalEventSystem events;
    private PhysicsManager physics;
    private Entity moon;
    private RailsUniverse universe;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        universe = new RailsUniverse(game);
        queue = new OrbitalEventQueue();
        events = new OrbitalEventSystem(universe, queue);
        game.addSystem(events);
        add(new UuidComponent("earth"), new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        moon = add(new UuidComponent("moon"),
                new CelestialBodyComponent("Moon", MU_MOON, 1.7374e6, OrbitMath.soiRadius(MOON_A, MU_MOON, MU_EARTH)),
                new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, MOON_A, 0, 0, 1)));
        physics = new PhysicsManager();
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
        game.clear();
    }

    private Entity add(Component... components) {
        Entity e = game.createEntity();
        for (Component c : components) e.add(c);
        game.addEntity(e);
        return e;
    }

    @Test
    void anchorChangesReachRails() {
        KeplerOrbit leo = KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1);
        physics.setAnchorModel(new KeplerAnchor(leo), "earth");
        OrbitalAnchorBridge bridge = new OrbitalAnchorBridge(physics, events);
        assertEquals("earth", events.getAnchorParentUuid(), "current anchor pushed on construction");
        assertSame(leo, events.getAnchorOrbit());

        physics.rebaseOrigin(0, 0, 0, 25);
        assertNotSame(leo, events.getAnchorOrbit(), "rebase hands over the new conic");
        assertEquals(physics.getAnchorModel() instanceof KeplerAnchor
                ? ((KeplerAnchor) physics.getAnchorModel()).getOrbit() : null, events.getAnchorOrbit());

        physics.clearAnchor();
        assertNull(events.getAnchorOrbit());
        assertTrue(queue.eventsFor(OrbitalEventSystem.ANCHOR_ID).isEmpty());

        physics.setAnchorModel(new InertialAnchor(), null);
        assertNull(events.getAnchorOrbit(), "deep space is not tracked on rails");

        bridge.dispose();
        physics.setAnchorModel(new KeplerAnchor(leo), "earth");
        assertNull(events.getAnchorOrbit(), "disposed bridge forwards nothing");
    }

    @Test
    void bubbleAdoptsSoiChangeWithoutMovingBodies() {
        double r = 2e6;
        KeplerOrbit escape = KeplerOrbit.fromState(r, 0, 0, OrbitMath.escapeSpeed(MU_MOON, r) * 1.3, MU_MOON, 0);
        physics.setAnchorModel(new KeplerAnchor(escape), "moon");
        new OrbitalAnchorBridge(physics, events);
        Entity ship = game.createEntityWithUuid();
        com.badlogic.gdx.physics.box2d.Body body = physics.createCircleBody(ship, 120f, -40f, 5f, 100f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);

        List<String> anchorChanges = new ArrayList<>();
        physics.anchorChanges.add((sig, e) -> anchorChanges.add(e.kind + " " + e.parentUuid));

        double exit = queue.nextFor(OrbitalEventSystem.ANCHOR_ID).time;
        game.setTick((long) Math.ceil((exit + 1) * 60));
        events.processUntil(game.getSimSeconds());

        assertEquals(List.of("SET earth"), anchorChanges);
        assertEquals("earth", physics.getAnchorParentUuid());
        KeplerAnchor anchor = (KeplerAnchor) physics.getAnchorModel();
        assertSame(events.getAnchorOrbit(), anchor.getOrbit(), "rails and bubble share the new conic");
        assertEquals(MU_EARTH, anchor.getMu(), 1.0);
        assertEquals("earth", events.getAnchorParentUuid(), "no echo back to rails");
        assertEquals(120f, body.getPosition().x * PhysicsManager.METERS_PER_UNIT, 1e-3);
        assertEquals(-40f, body.getPosition().y * PhysicsManager.METERS_PER_UNIT, 1e-3);
    }
}
