package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 5's navigation display: the orbit of a target around another body (the lunar mine, seen
 * from Earth orbit) is drawn where that body is, and every planned burn gets a countdown, on the
 * map and in the readout.
 */
class NavigationDisplayTest {
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aMoonStationsOrbitIsDrawnAroundTheMoon() {
        world.nav.target.select(game.lunarMine);
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(new PlayerOrbitPath(world, world.burns).getPlayerPath(path));
        assertTrue(path.targetOrbit.size > 0, "the mine's orbit, though it goes round the Moon");
        // Its middle is the Moon, where the map draws it
        Entity moon = world.universe.parentOf(world.engine.getEntity(game.lunarMine));
        StateVector m = world.universe.stateInFrame(moon, world.universe.entity(GameScenario.EARTH), world.universe.now(), new StateVector());
        double sx = 0, sy = 0;
        int n = path.targetOrbit.size / 2;
        for (int i = 0; i < path.targetOrbit.size; i += 2) {
            sx += path.targetOrbit.get(i);
            sy += path.targetOrbit.get(i + 1);
        }
        double off = Math.hypot(sx / n - m.x, sy / n - m.y);
        assertTrue(off < 0.05 * Math.hypot(m.x, m.y), "centred on the Moon: " + off + " m off");
    }

    @Test
    void everyPlannedBurnHasACountdown() {
        world.nav.target.select(game.depot);
        world.nav.transfer.plan();
        world.nav.transfer.accept(world.burns);
        int burns = world.burns.nodes().nodes.size;
        assertTrue(burns >= 2, burns + " burns");
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(new PlayerOrbitPath(world, world.burns).getPlayerPath(path));
        assertEquals(burns, path.nodeLabels.size, "a label by each burn on the map");
        assertTrue(path.nodeLabels.get(1).startsWith("2: "), path.nodeLabels.get(1));
        List<String> lines = world.nav.readout();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Next burn in")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Then burns in")), "and the ones after it: " + lines);
    }

    @Test
    void everyPlannedBurnHasAMarkerOnTheOutsideView() {
        world.nav.target.select(game.depot);
        world.nav.transfer.plan();
        world.nav.transfer.accept(world.burns);
        int burns = world.burns.nodes().nodes.size;
        assertTrue(burns >= 2, burns + " burns");
        com.badlogic.gdx.utils.FloatArray marks = new com.badlogic.gdx.utils.FloatArray();
        world.nav.plannedBurnMarkers(marks);
        assertEquals(4 * burns, marks.size, "a marker for each burn");
        double now = world.universe.now();
        for (int i = 0; i < burns; i++) {
            com.ringsofiron.orbital.components.ManeuverNodeComponent.Node n = world.burns.nodes().nodes.get(i);
            assertEquals(1f, Math.hypot(marks.get(4 * i), marks.get(4 * i + 1)), 1e-4, "a direction");
            assertEquals(Math.hypot(n.prograde, n.radial), marks.get(4 * i + 2), 0.5, "its delta-v");
            assertEquals(n.time - now, marks.get(4 * i + 3), 0.5, "its countdown");
        }
        assertTrue(marks.get(7) > marks.get(3), "in time order");
        assertEquals("2: 1,234 m/s in " + Navigation.formatTime(65), NavOverlay.label(2, 1234f, 65f));

        world.burns.replace(java.util.List.of());
        world.nav.plannedBurnMarkers(marks);
        assertEquals(0, marks.size, "none with nothing planned");
    }
}
