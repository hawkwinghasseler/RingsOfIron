package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The wider world and its contracts through a save: a seeded career's dice, the events' seed of a
 * save without them, the pirate base's age, a defence's raiders, and a convoy run out of time.
 */
class WorldSaveGapsTest {
    static final float FRAME = 1f / 60f;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    private static GameScenario start(GameWorld world, NewGame newGame) {
        return GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"), newGame);
    }

    private static GameScenario start(GameWorld world) {
        return start(world, NewGame.designed());
    }

    /** Saves a new game started from {@code newGame} to {@code file}. */
    private static void save(NewGame newGame, FileHandle file) {
        GameWorld world = new GameWorld();
        try {
            assertNull(start(world, newGame).quickSave(file));
        } finally {
            world.dispose();
        }
    }

    /** The first draws of the crew's dice and of the damage dice for the player's ship, after loading {@code file} into a fresh world. */
    private static String diceAfterLoading(FileHandle file) {
        GameWorld fresh = new GameWorld();
        try {
            GameScenario game = start(fresh);
            game.quickLoad(file);
            return fresh.crew.random.nextLong() + " " + fresh.impacts.rngFor(game.player).nextLong();
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aSeededCareerLoadedIntoAFreshWorldRollsItsOwnDice(@TempDir Path dir) {
        FileHandle seven = new FileHandle(dir.resolve("seven.json").toFile());
        FileHandle eight = new FileHandle(dir.resolve("eight.json").toFile());
        save(NewGame.seeded(7, null), seven);
        save(NewGame.seeded(8, null), eight);
        String a = diceAfterLoading(seven), again = diceAfterLoading(seven), b = diceAfterLoading(eight);
        assertEquals(a, again, "the same save, the same dice");
        assertNotEquals(a.split(" ")[0], b.split(" ")[0], "another seed, other crew dice");
        long crew = new com.badlogic.gdx.math.RandomXS128(NewGame.seeded(7, null).crewSeed()).nextLong();
        assertEquals(String.valueOf(crew), a.split(" ")[0], "the crew's dice start from the career's crew seed");
    }

    @Test
    void aSaveWithoutWorldEventsLoadsWithTheDefaultSeed(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("old.json").toFile());
        save(NewGame.designed(), file);
        // As a build before world events wrote it
        JsonValue root = new JsonReader().parse(file.readString("UTF-8"));
        root.get("modules").remove("worldEvents");
        file.writeString(root.toJson(JsonWriter.OutputType.json), false, "UTF-8");

        GameWorld running = new GameWorld();
        try {
            start(running, NewGame.seeded(99, null)).quickLoad(file);
            assertEquals(WorldEvents.DEFAULT_SEED, running.events.seed, "not the running world's seed");
            assertTrue(running.events.log().isEmpty());
        } finally {
            running.dispose();
        }
    }

    @Test
    void thePirateBaseKeepsItsAgeThroughASave(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("base.json").toFile());
        GameWorld world = new GameWorld();
        int extra;
        try {
            GameScenario game = start(world);
            assertNotNull(world.pirateBase.uuid());
            // Founded three growth periods ago, the base keeps three more raiders
            world.pirateBase.establish(world.pirateBase.uuid(), world.macroSim.now() - 3 * PirateBase.GROWTH_DAYS * 86_400 - 1);
            extra = world.pirateBase.extraRaiders();
            assertEquals(3, extra);
            assertNull(game.quickSave(file));
        } finally {
            world.dispose();
        }
        GameWorld fresh = new GameWorld();
        try {
            start(fresh).quickLoad(file);
            assertTrue(fresh.pirateBase.standing());
            assertEquals(extra, fresh.pirateBase.extraRaiders(), "established when it was, not at the load");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aDefencesRaidersAreTheSameShipsAfterALoad(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("defence.json").toFile());
        GameWorld world = new GameWorld();
        Contract c = null;
        try {
            GameScenario game = start(world);
            world.update(FRAME);
            assertTrue(world.events.happen(WorldEvents.Kind.RAID, 1, new Random(3)));
            WorldEvents.Event e = world.events.log().get(world.events.log().size() - 1);
            for (Contract o : world.contracts.offers(e.station)) if (o.type == Contract.Type.DEFEND) c = o;
            assertNotNull(c);
            world.standing.record().rank = 4;
            assertNull(world.contracts.accept(c.id));
            assertNull(game.quickSave(file));
        } finally {
            world.dispose();
        }
        GameWorld fresh = new GameWorld();
        try {
            start(fresh).quickLoad(file);
            Contract back = fresh.contracts.active().stream().filter(o -> o.type == Contract.Type.DEFEND).findFirst().orElse(null);
            assertNotNull(back);
            assertArrayEquals(c.targets, back.targets);
            for (String raider : back.targets) assertNotNull(fresh.engine.getEntity(raider), "raider " + raider + " is in the loaded game");
            fresh.update(FRAME);
            assertEquals(Contract.Status.ACTIVE, back.status, "not paid at once: " + back.outcome);
            assertEquals(0, back.count(Contract.MARK_GOOD));
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aConvoyRunOutOfTimeWithNoneInFails() {
        GameWorld world = new GameWorld();
        try {
            GameScenario game = start(world);
            world.update(FRAME);
            String kestrel = freighterNamed(world, "Hauler Kestrel"), other = freighterNamed(world, "Hauler 1");
            List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
            TradeOrder a = new TradeOrder(9_200, "water", game.highport, game.depot, 50, 1, 2);
            a.claimedBy = kestrel;
            TradeOrder b = new TradeOrder(9_201, "water", game.highport, game.depot, 50, 1, 2);
            b.claimedBy = other;
            orders.add(a);
            orders.add(b);
            world.macroSim.economy().restoreOrders(orders, 9_202);
            Contract c = null;
            for (Contract o : world.contracts.offers(game.highport)) if (o.type == Contract.Type.CONVOY) c = o;
            assertNotNull(c);
            world.standing.record().rank = 1;
            assertNull(world.contracts.accept(c.id));
            float standing = world.standing.standing(c.giver);
            c.deadline = world.engine.getSimSeconds() - 1;
            world.update(FRAME);
            assertEquals(Contract.Status.FAILED, c.status);
            assertEquals("none of the convoy came in with you", c.outcome);
            assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing(c.giver), 1e-4);
        } finally {
            world.dispose();
        }
    }

    private static String freighterNamed(GameWorld world, String name) {
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return e.getKey();
        }
        fail("no freighter " + name);
        return null;
    }
}
