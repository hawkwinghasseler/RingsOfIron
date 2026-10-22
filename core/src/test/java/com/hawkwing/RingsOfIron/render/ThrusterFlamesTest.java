package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ExteriorArt;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.hawkwing.RingsOfIron.lighting.LightSpot;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Flames spool up and die down, kick when a jet opens, and light as strong as they burn. */
class ThrusterFlamesTest {
    GameWorld world;
    ThrusterFlames flames;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        world.loadContent(assets, assets.resolve("none"));
        flames = new ThrusterFlames(new ExteriorArt(ArtLibrary.none()));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aDriveSpoolsUpOverATenthsOfASecondAndDiesDownFaster() {
        float level = 0f;
        level = ThrusterFlames.step(level, 1f, ThrusterFlames.DRIVE_RISE, ThrusterFlames.DRIVE_FALL, 0.1f);
        assertTrue(level > 0.2f && level < 0.4f, "a tenth of a second in, it is still coming up: " + level);
        for (int i = 0; i < 10; i++) level = ThrusterFlames.step(level, 1f, ThrusterFlames.DRIVE_RISE, ThrusterFlames.DRIVE_FALL, 0.1f);
        assertEquals(1f, level, 1e-6f, "then it holds at full");
        level = ThrusterFlames.step(level, 0f, ThrusterFlames.DRIVE_RISE, ThrusterFlames.DRIVE_FALL, 0.1f);
        assertEquals(0.5f, level, 1e-5f, "cut, it dies down over a fifth of a second");
        assertEquals(level, ThrusterFlames.step(level, 0f, ThrusterFlames.DRIVE_RISE, ThrusterFlames.DRIVE_FALL, 0f),
                "nothing moves while paused");
    }

    @Test
    void theFlickerStaysCloseToSteadyAndDiffersBetweenNozzles() {
        float lo = 2f, hi = 0f;
        for (int i = 0; i < 1000; i++) {
            float f = ThrusterFlames.flicker(i * 0.0071f, 3f, ThrusterFlames.RCS_FLICKER);
            lo = Math.min(lo, f);
            hi = Math.max(hi, f);
        }
        assertTrue(lo >= 1f - ThrusterFlames.RCS_FLICKER - 1e-5f && hi <= 1f + ThrusterFlames.RCS_FLICKER + 1e-5f);
        assertTrue(hi - lo > ThrusterFlames.RCS_FLICKER, "but it does waver");
        assertNotEquals(ThrusterFlames.flicker(1f, 0f, 0.1f), ThrusterFlames.flicker(1f, 1.618f, 0.1f));
    }

    @Test
    void aJetOpensFastKicksPastFullThenSettlesAndItsLightFollowsIt() {
        Entity ship = world.getContent().factory.spawnShip("corvette_patrol", 0, 0, 0);
        Body body = world.docking.bodyOf(ship);
        FlightControlComponent c = ship.getComponent(FlightControlComponent.class);
        c.rotationAssist = false;
        c.strafe = 1f;

        frame(ship, body, 0.02f);
        assertFalse(flames.flames().isEmpty(), "the strafe jets light at once");
        float first = maxLevel();
        assertTrue(first > 0.4f && first < 1f + ThrusterFlames.RCS_KICK, "half open, with the kick on: " + first);

        frame(ship, body, 0.03f);
        assertTrue(maxLevel() > 1f, "fully open and kicking past full: " + maxLevel());
        for (int i = 0; i < 10; i++) frame(ship, body, 0.02f);
        assertEquals(1f, maxLevel(), 1e-5f, "then it settles at full");

        // Its light is as strong as its flame
        ExteriorLighting light = new ExteriorLighting(null);
        light.begin();
        flames.light(light, null);
        assertEquals(flames.flames().size, light.spots().size(), "one light for each flame");
        for (int i = 0; i < light.spots().size(); i++) {
            LightSpot s = light.spots().get(i);
            assertEquals(ExteriorLighting.THRUSTER, s.color, "the jets' cold blue-white");
        }

        c.strafe = 0f;
        frame(ship, body, 0.035f);
        assertFalse(flames.flames().isEmpty(), "shut, a jet takes a moment to go out");
        assertTrue(maxLevel() < 0.6f);
        frame(ship, body, 0.05f);
        assertTrue(flames.flames().isEmpty(), "and then it is out");
    }

    @Test
    void aCraftNoLongerInViewIsForgotten() {
        Entity ship = world.getContent().factory.spawnShip("corvette_patrol", 0, 0, 0);
        Body body = world.docking.bodyOf(ship);
        ship.getComponent(FlightControlComponent.class).strafe = 1f;
        frame(ship, body, 0.05f);
        assertFalse(flames.flames().isEmpty());
        flames.begin(0.05f);
        flames.end();
        assertTrue(flames.flames().isEmpty());
        // Back in view it starts from cold, kick and all
        frame(ship, body, 0.02f);
        assertTrue(maxLevel() < 1f + ThrusterFlames.RCS_KICK);
    }

    private void frame(Entity ship, Body body, float delta) {
        flames.begin(delta);
        flames.collect(ship, body);
        flames.end();
    }

    /** The longest flame's level, without its flicker. */
    private float maxLevel() {
        float max = 0f;
        for (ThrusterFlames.Flame f : flames.flames()) max = Math.max(max, f.level);
        return max;
    }
}
