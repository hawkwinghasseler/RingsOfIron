package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Factions from the data in the starting scenario: who owns what, who fights whom, standing, rank and saves. */
class FactionStandingTest {
    Path assets;
    GameWorld world;
    GameScenario game;
    FactionStanding standing;
    Factions factions;
    int patrol, selene, pirates;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        standing = world.standing;
        factions = standing.factions();
        patrol = factions.index("patrol");
        selene = factions.index("selene");
        pirates = factions.index("pirates");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void theSponsorIsFirstAndKeepsTheStationCrewNumber() {
        assertEquals("patrol", factions.sponsor().id);
        assertEquals(StationCrew.FACTION, patrol);
        assertEquals(patrol, standing.sponsor());
        assertTrue(selene > patrol && pirates > patrol);
    }

    @Test
    void eachStationBelongsToItsFactionAndSoDoItsCrew() {
        Entity highport = world.engine.getEntity(game.highport);
        Entity depot = world.engine.getEntity(game.depot);
        Entity mine = world.engine.getEntity(game.lunarMine);
        assertEquals(patrol, standing.factionOf(highport));
        assertEquals(patrol, standing.factionOf(depot));
        assertEquals(selene, standing.factionOf(mine));
        int mineCrew = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship != mine) continue;
            assertEquals(selene, e.getComponent(CrewMemberComponent.class).faction);
            mineCrew++;
        }
        assertTrue(mineCrew > 0, "the mine has hands of its own");
        assertEquals(pirates, standing.factionOf(game.raider), "the raider flies for the pirates");
    }

    @Test
    void whoFightsWhom() {
        assertTrue(standing.hostile(FactionStanding.PLAYER, pirates), "pirates fight everyone");
        assertTrue(standing.hostile(patrol, pirates));
        assertFalse(standing.hostile(patrol, selene), "the rivals are not at war");
        assertFalse(standing.hostile(FactionStanding.PLAYER, selene), "not until the player crosses them");
        standing.penalize(selene, 60f, "test");
        assertTrue(standing.hostile(FactionStanding.PLAYER, selene));
        assertFalse(standing.hostile(patrol, 1), "a data faction leaves crew of no one's alone");
        assertTrue(standing.hostile(1, 5000), "two crews of no one's fight");
    }

    @Test
    void destroyingAShipCostsStandingWithItsFactionAndGainsItWithItsEnemies() {
        standing.onShipDestroyed(game.raider);
        assertEquals(-FactionStanding.KILL_COST, standing.standing(pirates), 1e-4);
        float gain = FactionStanding.KILL_COST * FactionStanding.KILL_SHARE;
        assertEquals(5f, gain, 1e-4);
        assertEquals(gain, standing.standing(patrol), 1e-4, "the Patrol names the pirates its enemy");
        assertEquals(0f, standing.standing(selene), 1e-4, "Selene is only at war with them because they are hostile to all");
        assertEquals(gain, standing.standing("patrol"), 1e-4, "the same by id");
    }

    @Test
    void stationsRefuseAPlayerWithLowStandingAndSayWhy() {
        Entity mine = world.engine.getEntity(game.lunarMine);
        assertNull(standing.refusal(mine, game.player));
        standing.penalize("selene", 20f, "smuggled past their customs");
        assertEquals("you smuggled past their customs", standing.refusal(mine, game.player));
        assertNull(standing.refusal(world.engine.getEntity(game.highport), game.player), "the sponsor doesn't mind");
    }

    @Test
    void standingWithTheSponsorPromotesThePlayerAStepAtATime() {
        assertEquals(0, standing.record().rank);
        assertEquals("Ensign", standing.rank().name);
        standing.reward("patrol", 40f, "test");
        assertEquals("Lieutenant Commander", standing.rank().name);
        assertEquals(2, standing.notices.size, "one notice a step");
        assertTrue(standing.notices.peek().startsWith("Promoted to Lieutenant Commander"), standing.notices.peek());
        standing.penalize("patrol", 80f, "test");
        assertEquals("Lieutenant Commander", standing.rank().name, "promotions are never taken back");
        standing.reward("selene", 100f, "test");
        assertEquals(2, standing.record().rank, "only the sponsor promotes");
    }

    @Test
    void ranksLimitWhatTheyMayRequisition() {
        RankLadder ranks = standing.ranks();
        assertTrue(ranks.mayRequisition(0, GameScenario.PLAYER_SHIP));
        assertFalse(ranks.mayRequisition(0, GameScenario.FREIGHTER_SHIP), "an ensign gets no freighter");
        assertTrue(ranks.mayRequisition(2, "gunship_heavy"), "a Lieutenant Commander gets the gunship");
        assertFalse(ranks.mayRequisition(4, GameScenario.FREIGHTER_SHIP), "nor does a Captain: freighters are for haulers");
    }

    @Test
    void theCareerSavesWithTheGame(@TempDir Path dir) {
        standing.reward("patrol", 20f, "test");
        standing.penalize("selene", 5f, "test");
        FileHandle file = new FileHandle(dir.resolve("career.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(1, fresh.standing.record().rank);
            assertEquals(20f, fresh.standing.standing("patrol"), 1e-4);
            assertEquals(-5f, fresh.standing.standing("selene"), 1e-4);
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void anOldSaveLoadsAsRankZeroWithItsStandingKept(@TempDir Path dir) {
        // Before the career was saved, standing was kept on the player's ship by faction number
        FactionStandingComponent old = new FactionStandingComponent();
        old.add(patrol, -12f);
        game.player.add(old);
        JsonValue save = new JsonReader().parse(world.engine.getSerializationService().saveGame());
        save.get("modules").remove("factions");
        FileHandle file = new FileHandle(dir.resolve("old.json").toFile());
        file.writeString(save.toJson(JsonWriter.OutputType.json), false, "UTF-8");

        standing.reward("patrol", 50f, "since the old save");
        game.quickLoad(file);
        assertEquals(0, standing.record().rank, "rank 0");
        assertEquals("patrol", standing.record().sponsor, "with the sponsor");
        assertEquals(-12f, standing.standing(patrol), 1e-4, "the old standing carried over");
        assertNull(game.player.getComponent(FactionStandingComponent.class), "and taken off the ship");
    }
}
