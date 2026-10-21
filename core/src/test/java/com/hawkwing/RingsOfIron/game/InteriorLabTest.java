package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.moddingdata.defs.ShipDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The interior demo's world headless: hulls and crews, each damage tool, and the wound watch. */
class InteriorLabTest {
    static final float FRAME = 1f / 60f;

    InteriorLab lab;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        lab = new InteriorLab(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
        lab.load(GameScenario.PLAYER_SHIP);
    }

    @AfterEach
    void tearDown() {
        lab.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) lab.update(FRAME);
    }

    private CrewMemberComponent crew(Entity e) {
        return lab.world.crew.crew(e);
    }

    private boolean logged(String text) {
        for (WoundWatch.Line l : lab.watch.lines()) if (l.text.contains(text)) return true;
        return false;
    }

    private ShipGridComponent grid() {
        return lab.ship.getComponent(ShipGridComponent.class);
    }

    /** Integrity summed over every wall and hull tile: falls as anything breaks them. */
    private float structure() {
        ShipGridComponent g = grid();
        float sum = 0f;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t.isWall()) sum += t.getIntegrity();
            }
        }
        return sum;
    }

    /** A hull tile on the ship's port side, about midships: the first wall from the top down the middle column. */
    private int[] portHull() {
        ShipGridComponent g = grid();
        int x = g.width / 2;
        for (int y = g.height - 1; y >= 0; y--) if (g.getTile(x, y).isWall()) return new int[]{x, y};
        throw new AssertionError("no hull");
    }

    @Test
    void everyHullComesWithItsCrewAndAMedic() {
        List<String> hulls = lab.hulls();
        assertTrue(hulls.size() >= 6, hulls.toString());
        for (String id : hulls) {
            lab.load(id);
            ShipDef def = lab.world.getContent().defs.get("ship", id, ShipDef.class);
            List<Entity> aboard = lab.debug.crewAboard(lab.ship);
            assertEquals(def.crew, aboard.size(), id);
            assertEquals(CrewRole.MEDIC, crew(aboard.get(0)).role, id);
            run(1f);
        }
        lab.load(hulls.get(0));
        lab.cycle(1);
        assertEquals(hulls.get(1), lab.hull);
        lab.cycle(-2);
        assertEquals(hulls.get(hulls.size() - 1), lab.hull);
    }

    @Test
    void spawnedCrewJoinTheShipAndCanBeTakenOff() {
        int before = lab.debug.crewAboard(lab.ship).size();
        Entity medic = lab.debug.spawnCrew(lab.ship, CrewRole.MEDIC);
        assertNotNull(medic);
        assertEquals(before + 1, lab.debug.crewAboard(lab.ship).size());
        run(1f);
        lab.debug.removeCrew(medic);
        run(0.1f);
        assertEquals(before, lab.debug.crewAboard(lab.ship).size());
    }

    @Test
    void theWatchTellsEachHitOnACriticalCasualtyHalvingTheirClock() {
        Entity e = lab.debug.crewAboard(lab.ship).get(2);
        CrewMemberComponent c = crew(e);
        lab.debug.hurt(e, 100f, DamageCause.SPALL);
        run(0.1f);
        assertEquals(Wound.CRITICAL, c.wound);
        float clock = c.bleedOut;
        assertTrue(logged(c.name + " spall: critical wound"), lab.watch.lines().toString());
        lab.debug.hurt(e, 80f, DamageCause.FRAGMENT);
        run(0.1f);
        assertEquals(clock * 0.5f, c.bleedOut, 0.5f);
        assertTrue(logged(c.name + " hit again while critical"), lab.watch.lines().toString());
        assertEquals(1, lab.watch.hitsWhileCritical(e));
        // A light hit adds injury without cutting the clock
        lab.debug.hurt(e, 20f, DamageCause.FRAGMENT);
        run(0.1f);
        assertEquals(1, lab.watch.hitsWhileCritical(e));
    }

    @Test
    void aMedicReachesACriticalCasualtyAndStopsTheBleeding() {
        Entity e = lab.debug.crewAboard(lab.ship).get(2);
        lab.debug.hurt(e, 100f, DamageCause.SPALL);
        run(90f);
        CrewMemberComponent c = crew(e);
        assertNotEquals(CrewState.DEAD, c.state, lab.watch.lines().toString());
        assertTrue(c.stabilised || c.wound != Wound.CRITICAL, lab.watch.lines().toString());
        assertTrue(logged("stabilised") || logged("treated"), lab.watch.lines().toString());
    }

    @Test
    void aRailgunRoundFromOutsideHolesTheHull() {
        int[] hull = portHull();
        float before = structure();
        String fired = lab.debug.fire(lab.ship, "railgun_mk1", hull[0], hull[1]);
        assertTrue(fired.endsWith("fired"), fired);
        run(0.5f);
        assertTrue(structure() < before - 0.05f, "structure " + before + " -> " + structure());
    }

    @Test
    void aLaserBurnsTheHull() {
        int[] hull = portHull();
        float before = structure();
        String fired = lab.debug.fire(lab.ship, "laser_mk1", hull[0], hull[1]);
        assertTrue(fired.endsWith("burning"), fired);
        run(3f);
        assertTrue(lab.debug.beams().isEmpty());
        assertTrue(structure() < before - 0.05f, "structure " + before + " -> " + structure());
    }

    @Test
    void aRockOnAFastPassRamsThroughTheHull() {
        int[] hull = portHull();
        float before = structure();
        String rammed = lab.debug.ram(lab.ship, hull[0], hull[1]);
        assertFalse(rammed.startsWith("Rams under"), rammed);
        run(3f);
        assertTrue(structure() < before - 0.05f, "structure " + before + " -> " + structure());
        assertTrue(lab.debug.ram(lab.ship, hull[0], hull[1], 0f, 100f, 1000f, 500f).startsWith("Rams under"));
    }

    @Test
    void aBlastInsideWoundsWhoeverStandsThere() {
        Entity e = lab.debug.crewAboard(lab.ship).get(1);
        GridPositionComponent p = lab.world.crew.position(e);
        String blast = lab.debug.blast(lab.ship, "frag_heavy", p.tileX(), p.tileY());
        assertTrue(blast.contains("went off"), blast);
        run(0.1f);
        assertNotEquals(Wound.NONE, crew(e).wound);
    }

    @Test
    void anAttackerOpensFireOnTheShip() {
        float before = structure();
        assertTrue(lab.attack(GameScenario.RAIDER_SHIP, 90f).endsWith("attacking"));
        run(30f);
        assertTrue(structure() < before - 0.05f, "structure " + before + " -> " + structure());
        lab.ceaseFire();
        assertNull(lab.attacker);
        run(1f);
    }

    @Test
    void healingTakesTheWoundAway() {
        Entity e = lab.debug.crewAboard(lab.ship).get(1);
        lab.debug.hurt(e, 100f, DamageCause.SPALL);
        run(0.1f);
        lab.debug.heal(e);
        run(0.1f);
        assertEquals(Wound.NONE, crew(e).wound);
        assertEquals(CrewState.CONSCIOUS, crew(e).state);
    }
}
