package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.macrosim.economy.TraderComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 8 part 5: towed home from seed 2026's lost ace fight, the crew lay unconscious and untreated
 * at Highport's berth for days. A station's infirmary treats them, warp or no warp, and bills it.
 */
class StationInfirmaryTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
        assertNotNull(world.docking.dockedTo(game.player), "docked at Highport");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private double credits() {
        return game.player.getComponent(TraderComponent.class).credits;
    }

    /** Wounds the whole crew: the first critically, the rest seriously, so nobody aboard can treat. */
    private List<Entity> woundEveryone() {
        List<Entity> crew = world.crewCareer.ownCrew(game.player);
        assertTrue(crew.size() >= 2);
        for (int i = 0; i < crew.size(); i++) world.crew.applyDamage(crew.get(i), i == 0 ? 95f : 75f, DamageCause.PENETRATOR);
        assertEquals(Wound.CRITICAL, world.crew.crew(crew.get(0)).wound);
        for (Entity e : crew) assertFalse(world.crew.crew(e).isConscious());
        return crew;
    }

    @Test
    void theStationsMedicsTreatTheWoundedAtTheBerthAndBillIt() {
        List<Entity> crew = woundEveryone();
        double before = credits();
        run(2);
        assertTrue(world.crew.crew(crew.get(0)).stabilised, "the critical wound stabilised at once");
        // Critical to light at a medbay's pace, (60 + 120) s / 1.5; a light wound is the crew's own
        run(60 * 150);
        for (Entity e : crew) {
            CrewMemberComponent c = world.crew.crew(e);
            assertTrue(c.isAlive(), c.name);
            assertTrue(c.wound.compareTo(Wound.LIGHT) <= 0, c.name + ": " + CrewMedical.condition(c));
            assertTrue(c.isConscious(), c.name + " back on their feet");
        }
        double expected = StationInfirmary.CRITICAL_PRICE + crew.size() * StationInfirmary.SERIOUS_PRICE;
        // A level the first of them back on their feet finished first isn't the infirmary's to bill
        assertTrue(before - credits() > expected - StationInfirmary.CRITICAL_PRICE && before - credits() <= expected,
                "billed a level at a time: " + (before - credits()) + " of " + expected);
    }

    @Test
    void inTimeWarpAtTheBerthTheyAreTreatedAllTheSame() {
        List<Entity> crew = woundEveryone();
        run(2);
        // The crew's own care stops in rails warp; the infirmary's doesn't
        double until = world.engine.getSimSeconds() + 3_600;
        for (int i = 0; i < 10_000 && world.engine.getSimSeconds() < until; i++) {
            if (world.timeWarp.getWarp() == 1) world.timeWarp.setWarp(1_000);
            world.update(FRAME);
        }
        world.timeWarp.setWarp(1);
        assertTrue(world.engine.getSimSeconds() >= until, "an hour passed");
        for (Entity e : crew) {
            CrewMemberComponent c = world.crew.crew(e);
            assertTrue(c.isAlive(), c.name);
            assertTrue(c.wound.compareTo(Wound.LIGHT) <= 0, c.name + ": " + CrewMedical.condition(c));
            assertTrue(c.isConscious(), c.name + " back on their feet");
        }
    }
}
