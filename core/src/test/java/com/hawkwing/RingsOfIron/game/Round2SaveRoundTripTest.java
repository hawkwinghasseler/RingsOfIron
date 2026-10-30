package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.career.Contract;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One save holding everything round 2 adds: a career with rank, a contract held, the raider beaten
 * and boarded, the player's old ship abandoned as a derelict, and the ship requisitioned at rescue.
 * Loaded into a fresh game, each comes back as it was.
 */
class Round2SaveRoundTripTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
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

    @Test
    void aContractADerelictABoardedShipAndARequisitionedOneAllSurviveALoad(@TempDir Path dir) {
        game.beginCareer();
        runUntil(world, 2f, () -> false);
        // A Lieutenant Commander, so the rescue issues the gunship
        world.standing.reward(world.standing.sponsor(), 40f, "test");
        assertEquals(2, world.standing.record().rank);

        // The raider beaten and taken, with two of its crew held
        String raiderId = id(game.raider);
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            CrewFactory.spawn(world.crew, game.raider, "Raider " + i, CrewRole.CREWMAN, RAIDERS, t[0], t[1]);
        }
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.undock(game.player);
        runUntil(world, 1f, () -> false);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        assertTrue(runUntil(world, 5f, () -> ShipDisablement.isDisabled(game.raider)));
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.boarding.order(game.player);
        assertTrue(runUntil(world, 240f, () -> world.boarding.takenBy(game.raider, game.player)), "taken");

        // Abandon the old ship; rescued into a requisitioned gunship at a sponsor station
        world.docking.undock(game.player);
        runUntil(world, 1f, () -> false);
        game.player.getComponent(TraderComponent.class).credits = 12_345;
        Entity oldShip = game.player;
        String oldId = id(oldShip);
        List<Rescue.Result> rescues = new ArrayList<>();
        world.rescue.rescues.add((signal, r) -> rescues.add(r));
        assertNull(world.abandon.order(game.player));
        PlayerToPod.board(world, game.player);
        assertTrue(runUntil(world, 60f, () -> !rescues.isEmpty()), "rescued");
        Rescue.Result rescue = rescues.get(0);
        runUntil(world, 2f, () -> false);
        String newId = id(game.player);
        String newShipDef = game.player.getComponent(DefRefComponent.class).defId;
        assertEquals("gunship_heavy", newShipDef);
        String stationId = world.universe.uuidOf(rescue.station);

        // A contract taken at the station the player woke at (not an operation, which round 3 puts first
        // for a lieutenant commander and which is held as its first step)
        Contract held = world.contracts.offers(stationId).stream()
                .filter(c -> world.contracts.locked(c) == null && c.type != Contract.Type.OPERATION).findFirst().orElseThrow();
        assertNull(world.contracts.accept(held.id));
        float standing = world.standing.standing(world.standing.sponsor());
        double now = world.macroSim.now();

        FileHandle file = new FileHandle(dir.resolve("round2.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);

            // The career
            assertEquals(2, fresh.standing.record().rank);
            assertEquals(standing, fresh.standing.standing(fresh.standing.sponsor()), 1e-3);
            assertEquals(now, fresh.macroSim.now(), 1.0, "the rescue's time skip is kept");

            // The requisitioned ship is the player's, docked where they woke
            assertEquals(newId, id(loaded.player));
            assertSame(loaded.player, fresh.getPlayer());
            assertEquals(newShipDef, loaded.player.getComponent(DefRefComponent.class).defId);
            assertEquals(12_345, loaded.player.getComponent(TraderComponent.class).credits, 1e-6);
            runUntil(fresh, 2f, () -> false);
            assertEquals(stationId, fresh.universe.uuidOf(fresh.docking.dockedTo(loaded.player)), "still docked there");

            // The contract held
            assertEquals(1, fresh.contracts.active().size());
            Contract back = fresh.contracts.active().get(0);
            assertEquals(held.id, back.id);
            assertEquals(held.type, back.type);
            assertEquals(held.reward, back.reward, 1e-6);
            assertEquals(held.deadline, back.deadline, 1e-6);
            assertEquals(Contract.Status.ACTIVE, back.status);

            // The old ship, a derelict nobody flies
            Entity derelict = fresh.engine.getEntity(oldId);
            assertNotNull(derelict);
            assertNull(derelict.getComponent(PlayerControllerComponent.class));
            assertEquals(DisabledComponent.Reason.ABANDONED, ShipDisablement.reason(derelict));

            // The raider, still surrendered and still the old ship's prize, its crew held
            Entity raider = fresh.engine.getEntity(raiderId);
            assertNotNull(raider);
            assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(raider));
            assertTrue(fresh.boarding.takenBy(raider, derelict), "taken by the ship that boarded it");
            assertEquals(2, Boarding.of(raider).prisoners);
        } finally {
            fresh.dispose();
        }
    }
}
