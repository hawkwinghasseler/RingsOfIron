package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The full-screen orbit map: the path's apsides and its encounter with the Moon as patched conics, and
 * planning burns with the mouse (a click on the path, a burn's delta-v set by its handles, a burn slid
 * along its orbit).
 */
class OrbitMapPlanningTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    PlayerOrbitPath path;
    MapManeuvers maneuvers;
    PlayerOrbitInfo orbits;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        for (int i = 0; i < 60; i++) world.update(FRAME);
        path = new PlayerOrbitPath(world.context, world.physics, world.universe, world.macroSim, world.pirateBase, world.nav,
                world.burns);
        maneuvers = new MapManeuvers(world.context, world.physics, world.universe, world.burns, world.nav, path);
        orbits = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    OrbitPathProvider.OrbitPath draw() {
        OrbitPathProvider.OrbitPath out = new OrbitPathProvider.OrbitPath();
        assertTrue(path.getPlayerPath(out));
        return out;
    }

    static OrbitPathProvider.PathMark mark(OrbitPathProvider.OrbitPath p, MarkKind kind) {
        for (OrbitPathProvider.PathMark m : p.marks) if (m.kind == kind) return m;
        return null;
    }

    @Test
    void theOrbitNowHasItsApsidesMarked() {
        OrbitPathProvider.OrbitPath p = draw();
        OrbitPathProvider.PathMark pe = mark(p, MarkKind.PERIAPSIS), ap = mark(p, MarkKind.APOAPSIS);
        assertNotNull(pe, "periapsis");
        assertNotNull(ap, "apoapsis");
        assertTrue(pe.text.startsWith("Pe "), pe.text);
        KeplerOrbit k = orbits.currentConic();
        assertEquals(k.periapsis(), Math.hypot(pe.x, pe.y), 1, "on the orbit's lowest point");
        assertEquals(k.apoapsis(), Math.hypot(ap.x, ap.y), 1, "and its highest");
        assertEquals(0, p.legs.size, "low orbit meets nothing");
    }

    @Test
    void aTripToTheMoonIsDrawnThroughItsSphereOfInfluence() {
        world.nav.target.select(game.lunarMine);
        world.nav.transfer.plan();
        world.nav.transfer.accept(world.burns);
        OrbitPathProvider.OrbitPath p = draw();
        OrbitPathProvider.PathMark entry = mark(p, MarkKind.SOI_ENTRY), ghost = mark(p, MarkKind.GHOST);
        assertNotNull(entry, "where the path enters the Moon's sphere: " + p.marks);
        assertTrue(entry.planned, "on the planned path");
        assertNotNull(ghost, "where the Moon will be at the closest approach");
        assertEquals(GameScenario.R_MOON, ghost.radius, 1);
        OrbitPathProvider.PathMark moonPe = null;
        for (OrbitPathProvider.PathMark m : p.marks) if (m.text.startsWith("Moon Pe")) moonPe = m;
        assertNotNull(moonPe, "the closest approach, labelled: " + p.marks);
        double peRadius = Math.hypot(moonPe.x - ghost.x, moonPe.y - ghost.y);
        assertTrue(peRadius > GameScenario.R_MOON && peRadius < ghost.soiRadius, "beside the Moon drawn then: " + peRadius);
        // The entry point is on the edge of the Moon's sphere, where the Moon is when the path gets there
        assertTrue(p.legs.size >= 1, "a leg on through the Moon's sphere");
        assertTrue(p.legs.first().planned);
        com.ringsofiron.orbital.math.StateVector moonAtEntry = world.universe.stateInFrame(world.universe.entity(GameScenario.MOON),
                world.universe.entity(GameScenario.EARTH), entryTime(), new com.ringsofiron.orbital.math.StateVector());
        assertEquals(ghost.soiRadius, Math.hypot(entry.x - moonAtEntry.x, entry.y - moonAtEntry.y), 0.01 * ghost.soiRadius);
        // And the path runs on unbroken: the leg through the Moon's sphere starts where the transfer ends
        com.badlogic.gdx.utils.FloatArray leg = p.legs.first().points;
        assertEquals(0, Math.hypot(leg.get(0) - entry.x, leg.get(1) - entry.y), 0.001 * ghost.soiRadius);
        if (p.legs.size > 1) {
            com.badlogic.gdx.utils.FloatArray next = p.legs.get(1).points;
            assertEquals(0, Math.hypot(next.get(0) - leg.get(leg.size - 2), next.get(1) - leg.get(leg.size - 1)), 0.001 * ghost.soiRadius,
                    "out of the Moon's sphere where the pass ends");
        }
        assertTrue(path.trajectorySummary().startsWith("Meets Moon"), path.trajectorySummary());
        assertTrue(p.caption.toString().contains("Meets Moon"), "and the small map says so too: " + p.caption);
        // The planned path stops at the Moon rather than going on round Earth
        float lastX = p.planned.get(p.planned.size - 2), lastY = p.planned.get(p.planned.size - 1);
        assertEquals(0, Math.hypot(lastX - entry.x, lastY - entry.y), 0.01 * ghost.soiRadius);
    }

    /** When the planned path enters the Moon's sphere, from the time on its label. */
    double entryTime() {
        KeplerOrbit k = orbits.currentConic();
        ManeuverNodeComponent.Node n = world.burns.nodes().next();
        java.util.List<MapTrajectory.Leg> legs = new MapTrajectory(world.universe)
                .legs(k.withBurn(n.time, n.prograde, n.radial), orbits.parent(), n.time, 1);
        return legs.get(0).to;
    }

    @Test
    void aBurnDeepIntoTheAtmosphereShowsTheImpact() {
        ManeuverNodeComponent.Node n = world.burns.addNode();
        world.burns.set(-400, 0);
        OrbitPathProvider.OrbitPath p = draw();
        OrbitPathProvider.PathMark impact = mark(p, MarkKind.IMPACT);
        assertNotNull(impact, "the path meets the ground");
        assertEquals(GameScenario.R_EARTH, Math.hypot(impact.x, impact.y), 1_000);
        assertTrue(path.trajectorySummary().contains("IMPACT on Earth"), path.trajectorySummary());
        assertNull(mark(p, MarkKind.PERIAPSIS), "no periapsis under the ground");
        assertNotNull(n);
    }

    @Test
    void aClickOnThePathPlansABurnWhereTheShipWillBe() {
        KeplerOrbit k = orbits.currentConic();
        double now = world.universe.now(), quarter = now + k.period() / 4;
        StateVector s = k.stateAt(quarter);
        assertFalse(maneuvers.addBurnNear(s.x * 1.5, s.y * 1.5, 10_000), "too far off the path: nothing planned");
        assertNull(world.burns.nodes() == null ? null : world.burns.nodes().next());
        assertTrue(maneuvers.addBurnNear(s.x + 2_000, s.y, 10_000));
        ManeuverNodeComponent.Node n = world.burns.selected();
        assertNotNull(n, "the new burn is selected");
        assertEquals(quarter, n.time, 5, "a quarter orbit on, where the click was");
        assertEquals(0, n.prograde, 0);

        // The handles set its delta-v; the map's axes for it run along the velocity there
        maneuvers.setSelectedBurn(120, -15);
        double[] dv = new double[2];
        assertTrue(maneuvers.selectedBurn(dv));
        assertArrayEquals(new double[]{120, -15}, dv, 1e-9);
        OrbitPathProvider.OrbitPath p = draw();
        assertEquals(0, p.selectedNode);
        StateVector v = k.stateAt(n.time);
        double speed = Math.hypot(v.vx, v.vy);
        assertEquals(v.vx / speed, p.nodeAxes.get(0), 1e-4);
        assertEquals(v.vy / speed, p.nodeAxes.get(1), 1e-4);
        assertTrue(p.planned.size > 0, "a planned path after the burn");
        assertTrue(maneuvers.burnText().contains("Prograde +120.0, radial -15.0"), maneuvers.burnText());

        // Dragged along its orbit to the far side
        double half = now + k.period() / 2;
        StateVector far = k.stateAt(half);
        maneuvers.moveSelectedNear(far.x, far.y);
        assertEquals(half, world.burns.selected().time, 5, "half an orbit on");
        assertEquals(120, world.burns.selected().prograde, 0, "keeping its delta-v");
    }

    @Test
    void aSecondClickLandsOnTheOrbitTheFirstBurnLeaves() {
        KeplerOrbit k = orbits.currentConic();
        double now = world.universe.now();
        StateVector s = k.stateAt(now + 600);
        assertTrue(maneuvers.addBurnNear(s.x, s.y, 5_000));
        maneuvers.setSelectedBurn(500, 0);
        ManeuverNodeComponent.Node first = world.burns.selected();
        // The burn raises the far side of the orbit: a click out there is on the new orbit, not the old
        KeplerOrbit after = k.withBurn(first.time, 500, 0);
        double apTime = MapTrajectory.nextApoapsis(after, first.time);
        StateVector ap = after.stateAt(apTime);
        assertTrue(maneuvers.addBurnNear(ap.x, ap.y, 20_000), "on the raised orbit");
        ManeuverNodeComponent.Node second = world.burns.selected();
        assertNotSame(first, second);
        assertEquals(apTime, second.time, 30, "at the new apoapsis");
        assertEquals(2, maneuvers.shown().size());
        maneuvers.selectBurn(0);
        assertSame(first, world.burns.selected());
        maneuvers.clearBurns();
        assertEquals(0, world.burns.nodes().nodes.size);
    }

    @Test
    void aClickOnTheLegPastTheMoonPlansABurnThere() {
        world.nav.target.select(game.lunarMine);
        world.nav.transfer.plan();
        world.nav.transfer.accept(world.burns);
        draw();
        // The path from the plan's last burn, as the map patches it
        KeplerOrbit k = orbits.currentConic();
        for (ManeuverNodeComponent.Node n : world.burns.nodes().nodes) k = k.withBurn(n.time, n.prograde, n.radial);
        ManeuverNodeComponent.Node last = world.burns.nodes().nodes.peek();
        java.util.List<MapTrajectory.Leg> legs = new MapTrajectory(world.universe).legs(k, orbits.parent(), last.time, 1);
        assertTrue(legs.size() >= 2, "the transfer meets the Moon");
        MapTrajectory.Leg toMoon = legs.get(0), pass = legs.get(1);
        double soi = world.universe.celestial(pass.frame).soiRadius, tolerance = 0.02 * soi;
        int before = world.burns.nodes().nodes.size;

        // Where the old orbit round Earth would have gone on, had the Moon not met it: nothing is drawn there
        double rest = toMoon.from + toMoon.conic.period() - toMoon.to;
        StateVector ghost = toMoon.conic.stateAt(toMoon.to + rest / 2);
        assertFalse(maneuvers.addBurnNear(ghost.x, ghost.y, tolerance), "no burn on the orbit the ship won't fly");
        assertEquals(before, world.burns.nodes().nodes.size);

        // Partway through the Moon's sphere, where the ship is then: the Moon moved on under it
        double mid = pass.from + (Double.isNaN(pass.to) ? 600 : (pass.to - pass.from) / 3);
        StateVector ship = pass.conic.stateAt(mid);
        StateVector moon = world.universe.stateInFrame(pass.frame, orbits.parent(), mid, new StateVector());
        assertTrue(maneuvers.addBurnNear(ship.x + moon.x, ship.y + moon.y, tolerance), "on the leg past the Moon");
        assertEquals(before + 1, world.burns.nodes().nodes.size);
        assertEquals(mid, world.burns.selected().time, 30, "when the ship is there");

        // And slid along that leg, it stays on it
        double later = pass.from + (Double.isNaN(pass.to) ? 1200 : (pass.to - pass.from) / 2);
        ship = pass.conic.stateAt(later);
        world.universe.stateInFrame(pass.frame, orbits.parent(), later, moon);
        maneuvers.moveSelectedNear(ship.x + moon.x, ship.y + moon.y);
        assertEquals(later, world.burns.selected().time, 30, "further on through the Moon's sphere");
    }

    @Test
    void thePanelDescribesTheOrbitAndThePlan() {
        String orbit = maneuvers.orbitText();
        assertTrue(orbit.startsWith("Around Earth"), orbit);
        assertTrue(orbit.contains("Period"), orbit);
        world.nav.target.select(game.lunarMine);
        maneuvers.planToTarget();
        assertTrue(maneuvers.hasProposal());
        maneuvers.acceptPlan();
        assertFalse(maneuvers.hasProposal());
        draw();
        String plan = maneuvers.planText();
        assertTrue(plan.startsWith("Meets Moon"), plan);
        assertTrue(plan.contains("Next burn in"), plan);
    }
}
