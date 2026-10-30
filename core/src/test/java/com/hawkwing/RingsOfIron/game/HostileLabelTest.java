package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 10 (part 6): the playtest's {@code hostiles} labels. Round 9's window found a fallen base's
 * batteries still read "fit", and struck raiders read SURRENDERED.
 */
class HostileLabelTest {
    static final float FRAME = 1f / 60f;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private Entity ship(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return world.engine.getEntity(e.getKey());
        }
        return null;
    }

    @Test
    void aFallenBasesBatteriesReadSilenced() {
        world.docking.undock(game.player);
        run(1);
        world.rescue.jumpBeside(game.player, world.engine.getEntity(world.pirateBase.uuid()), 8_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
        List<String> guns = world.pirateBase.guns();
        assertFalse(guns.isEmpty(), "the base manned its guns");
        for (String name : guns) assertEquals(HostileLabel.FIT, HostileLabel.of(world, ship(name)), name + " while the base stands");
        SilencedMarkers markers = new SilencedMarkers(world.engine, world.macroSim, world.pirateBase);
        markers.update();
        String label = ship(guns.get(0)).getComponent(HudMarkerComponent.class).label;
        assertFalse(label.endsWith(SilencedMarkers.SUFFIX), "red and unmarked while the base stands");
        world.pirateBase.fall("a test");
        for (String name : guns) assertEquals(HostileLabel.SILENCED, HostileLabel.of(world, ship(name)), name + " once it has struck");
        // The HUD greys them as it does a struck ship, and gives them back if the base rises
        markers.update();
        markers.update();
        for (String name : guns) {
            HudMarkerComponent marker = ship(name).getComponent(HudMarkerComponent.class);
            assertTrue(marker.label.endsWith(SilencedMarkers.SUFFIX), marker.label);
            assertFalse(marker.label.endsWith(SilencedMarkers.SUFFIX + SilencedMarkers.SUFFIX), marker.label);
            assertEquals(ShipDisablement.DISABLED_COLOR, marker.color);
        }
        world.pirateBase.rise();
        markers.update();
        HudMarkerComponent first = ship(guns.get(0)).getComponent(HudMarkerComponent.class);
        assertEquals(label, first.label);
        assertEquals(first.kind.defaultColor, first.color);
    }

    @Test
    void aSurrenderedShipReadsStruckAndARunningOneRunning() {
        List<String> wing = world.hostiles.spawnWing(HostileTraffic.RAIDER, 3, game.highport, "raider_chaingun");
        run(1);
        Entity struck = world.engine.getEntity(wing.get(0)), running = world.engine.getEntity(wing.get(1)), dark = world.engine.getEntity(wing.get(2));
        assertEquals(HostileLabel.FIT, HostileLabel.of(world, struck));
        struck.add(new DisabledComponent(DisabledComponent.Reason.SURRENDERED, 0));
        running.getComponent(HostileComponent.class).brokeOff = true;
        dark.add(new DisabledComponent(DisabledComponent.Reason.NO_POWER, 0));
        assertEquals(HostileLabel.STRUCK, HostileLabel.of(world, struck));
        assertEquals(HostileLabel.RUNNING, HostileLabel.of(world, running));
        assertEquals("NO_POWER", HostileLabel.of(world, dark), "other reasons keep their names");
    }
}
