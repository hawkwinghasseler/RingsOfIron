package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One save holding what round 3 adds at once: a seeded world, a crewman hired at Highport, an
 * operation under way, a pirate ace, and a taken raider in tow. Loaded into a fresh game, each comes
 * back as it was.
 */
class Round3SaveRoundTripTest {
    static final float FRAME = 1f / 60f;
    static final long SEED = 7;

    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(SEED, null));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private static boolean runUntil(GameWorld w, float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            w.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private static String id(Entity e) {
        return e.getComponent(UuidComponent.class).id;
    }

    private static Entity crewNamed(GameWorld w, Entity ship, String name) {
        for (Entity e : w.crewCareer.ownCrew(ship)) if (w.crew.crew(e).name.equals(name)) return e;
        return null;
    }

    @Test
    void aSeededWorldAHireAnOperationAnAceAndAPrizeInTowAllSurviveALoad(@TempDir Path dir) {
        game.beginCareer();
        runUntil(world, 2f, () -> false);
        // A Lieutenant, so Highport offers an operation
        world.standing.record().rank = 1;

        // A crewman hired at Highport
        game.player.getComponent(TraderComponent.class).credits = 20_000;
        CrewHiring.Candidate hire = world.crewHiring.pool(game.highport).get(0);
        assertNull(world.crewHiring.hire(hire.key));
        CrewMemberComponent hired = world.crew.crew(crewNamed(world, game.player, hire.name));

        // An operation taken there
        Contract op = null;
        for (Contract c : world.contracts.offers(game.highport)) if (c.type == Contract.Type.OPERATION && op == null) op = c;
        assertNotNull(op, "a lieutenant is offered an operation");
        assertNull(world.contracts.accept(op.id));
        String operation = world.contracts.operation().id;
        int step = world.contracts.active().get(0).step;

        // A raider on the rails made an ace with a grudge against the player
        String aceId = null;
        for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
            if (e.getValue().isPirate() && HostileTraffic.RAIDER.equals(e.getValue().shipId)) aceId = e.getKey();
        }
        HostileComponent ace = world.macroSim.hostiles().get(aceId);
        assertTrue(PirateAces.promote(world, world.engine.getEntity(aceId), ace,
                world.macroSim.hostiles().all().values(), 1, true));

        // The scenario's raider surrendered, taken, and latched on: the prize in tow
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        List<int[]> floor = GameScenario.floorTiles(game.raider.getComponent(ShipGridComponent.class));
        int[] t = floor.get(floor.size() - 1);
        CrewFactory.spawn(world.crew, game.raider, "Pirate", CrewRole.CREWMAN, pirates, t[0], t[1]);
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.undock(game.player);
        runUntil(world, 1f, () -> false);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        runUntil(world, 2f, () -> false);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.boarding.order(game.player);
        assertTrue(runUntil(world, 240f, () -> world.boarding.takenBy(game.raider, game.player)), "taken");
        assertTrue(runUntil(world, 120f, () -> world.shipyard.prize() == game.raider), "in tow");
        String raiderId = id(game.raider), playerId = id(game.player);

        FileHandle file = new FileHandle(dir.resolve("round3.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            runUntil(fresh, 2f, () -> false);

            // The seed
            assertTrue(loaded.newGame().seeded);
            assertEquals(SEED, loaded.newGame().seed);

            // The hire, aboard with the record they were hired with
            assertEquals(playerId, id(loaded.player));
            Entity back = crewNamed(fresh, loaded.player, hire.name);
            assertNotNull(back, "the hire is still aboard");
            CrewMemberComponent m = fresh.crew.crew(back);
            assertEquals(hired.role, m.role);
            assertEquals(hired.record().wage, m.record().wage, 1e-6);
            assertEquals(hired.record().gunnery, m.record().gunnery, 1e-6);
            assertEquals(hired.record().engineering, m.record().engineering, 1e-6);

            // The operation, at the same step
            assertNotNull(fresh.contracts.operation());
            assertEquals(operation, fresh.contracts.operation().id);
            assertEquals(1, fresh.contracts.active().size());
            assertEquals(step, fresh.contracts.active().get(0).step);

            // The ace, still an ace with a grudge, its name and its refit
            HostileComponent aceBack = fresh.macroSim.hostiles().get(aceId);
            assertNotNull(aceBack);
            assertTrue(aceBack.ace && aceBack.grudge);
            assertEquals(ace.displayName, aceBack.displayName);
            assertEquals(ace.loadout, aceBack.loadout);
            assertEquals(1, PirateAces.alive(fresh.macroSim.hostiles().all().values()));

            // The prize, still taken, latched and in tow
            Entity raider = fresh.engine.getEntity(raiderId);
            assertNotNull(raider);
            assertTrue(fresh.boarding.takenBy(raider, loaded.player));
            assertSame(raider, fresh.docking.dockedTo(loaded.player), "still latched");
            assertSame(raider, fresh.shipyard.prize(), "still in tow");
        } finally {
            fresh.dispose();
        }
    }
}
