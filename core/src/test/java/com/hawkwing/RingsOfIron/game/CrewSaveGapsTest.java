package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The crew at the edges of a save: a ransom, prisoners and a recruit through a save, a save without
 * hires, and morale's clock when a later save is loaded into a running game.
 */
class CrewSaveGapsTest {
    static final float FRAME = 1f / 60f;

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

    private void run(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(FRAME);
    }

    /** A new career, docked at Highport. */
    private void docked() {
        game.beginCareer();
        run(world, 120);
        assertNotNull(world.crewHiring.hiringStation(), "Highport hires");
    }

    /** A crewman of {@code faction} held aboard the player's ship. */
    private Entity prisoner(String name, int faction, int tile) {
        int[] t = FloorTiles.of(game.player.getComponent(ShipGridComponent.class)).get(tile);
        Entity p = CrewFactory.spawn(world.crew, game.player, name, CrewRole.CREWMAN, faction, t[0], t[1]);
        world.crew.crew(p).held = true;
        return p;
    }

    /** A pirate's name that offers to join, or that won't. */
    private static String pirateName(boolean offers) {
        for (int i = 0; ; i++) {
            String name = "Pirate " + i;
            if ((Math.floorMod((name + CrewRole.CREWMAN).hashCode(), Prisoners.JOIN_ODDS) == 0) == offers) return name;
        }
    }

    private CrewMemberComponent named(GameWorld w, String name) {
        for (Entity e : w.crew.crewEntities()) if (name.equals(w.crew.crew(e).name)) return w.crew.crew(e);
        return null;
    }

    /** {@code file}'s save without its {@code key} entry, as an older build wrote it. */
    private static void strip(FileHandle file, String key) {
        JsonValue root = new JsonReader().parse(file.readString("UTF-8"));
        root.get("modules").remove(key);
        file.writeString(root.toJson(JsonWriter.OutputType.json), false, "UTF-8");
    }

    @Test
    void prisonersAreRansomedToTheirOwnFactionAtItsStation() {
        docked();
        // Highport stands in for a Selene station
        Entity station = world.docking.dockedTo(game.player);
        station.getComponent(StationComponent.class).name = "Shackleton Ice";
        int selene = world.standing.factions().index("selene");
        assertEquals(selene, world.standing.factionOf(station));
        Entity p = prisoner("Miner", selene, 0);
        assertEquals("ransom", world.prisoners.option(p));
        TraderComponent account = game.player.getComponent(TraderComponent.class);
        double credits = account.credits;
        float standing = world.standing.standing(selene);
        assertNull(world.prisoners.settle(p));
        assertEquals(credits + Prisoners.RANSOM_PAY, account.credits, 1e-6);
        assertEquals(standing + Prisoners.RANSOM_STANDING, world.standing.standing(selene), 1e-3);
        assertNull(world.crew.crew(p), "gone home");
        assertTrue(world.prisoners.aboard(game.player).isEmpty());
    }

    @Test
    void prisonersAndARecruitComeThroughASave(@TempDir Path dir) {
        docked();
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        String held = pirateName(false), joins = pirateName(true);
        prisoner(held, pirates, 0);
        Entity j = prisoner(joins, pirates, 1);
        assertNull(world.prisoners.recruit(j));
        float wage = world.crew.crew(j).record().wage;
        FileHandle file = new FileHandle(dir.resolve("prisoners.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            run(fresh, 60);
            List<Entity> aboard = fresh.prisoners.aboard(loaded.player);
            assertEquals(1, aboard.size(), "the prisoner is still held");
            CrewMemberComponent p = fresh.crew.crew(aboard.get(0));
            assertEquals(held, p.name);
            assertEquals(pirates, p.faction);
            CrewMemberComponent r = named(fresh, joins);
            assertNotNull(r);
            assertFalse(r.held);
            assertEquals(FactionStanding.PLAYER, r.faction);
            assertEquals(Prisoners.JOIN_SKILL, r.record().engineering, 1e-6);
            assertEquals(wage, r.record().wage, 1e-6);
            assertTrue(fresh.crewCareer.ownCrew(loaded.player).stream().anyMatch(e -> fresh.crew.crew(e) == r),
                    "one of the crew");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aSaveWithoutHiresLoadsWithNobodyHired(@TempDir Path dir) {
        docked();
        FileHandle file = new FileHandle(dir.resolve("before.json").toFile());
        assertNull(game.quickSave(file));
        strip(file, "crew-hiring");
        game.player.getComponent(TraderComponent.class).credits = 10_000;
        CrewHiring.Candidate c = world.crewHiring.pool(game.highport).get(0);
        assertNull(world.crewHiring.hire(c.key));

        game.quickLoad(file);
        assertNull(named(world, c.name), "the save is from before the hire");
        assertTrue(world.crewHiring.pool(game.highport).stream().anyMatch(o -> o.key.equals(c.key)),
                "so they are looking for a berth again");
    }

    @Test
    void aLaterSaveLoadedIntoARunningGameDoesNotJumpMorale(@TempDir Path dir) {
        docked();
        CrewMemberComponent ada = named(world, "Ada");
        ada.record().morale = 0.3f;
        // Saved two days on
        long tick = world.engine.getTick();
        world.engine.setTick(tick + SimClock.secondsToTicks(2 * CrewCareer.DAY));
        FileHandle file = new FileHandle(dir.resolve("later.json").toFile());
        assertNull(game.quickSave(file));
        world.engine.setTick(tick);

        GameWorld running = new GameWorld();
        try {
            GameScenario other = GameScenario.start(running, assets, assets.resolve("no-mods-here"));
            run(running, 120);
            other.quickLoad(file);
            run(running, 120);
            float morale = named(running, "Ada").record().morale;
            assertEquals(0.3f, morale, 0.01f, "not two days' drift");
        } finally {
            running.dispose();
        }
    }
}
