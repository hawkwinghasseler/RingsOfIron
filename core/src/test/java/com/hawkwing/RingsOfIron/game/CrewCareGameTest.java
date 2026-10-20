package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 7 part 4 in the game: the gunship's and frigate's medbays, and a patient in one through a save. */
class CrewCareGameTest {
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
        game.beginCareer();
        run(world, 120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void theGunshipAndFrigateHaveMedbaysAndTheCorvetteKeepsItsCrewRoom() {
        assertEquals(4, beds("gunship"));
        assertEquals(4, beds("frigate"));
        assertEquals(0, beds("corvette"));
    }

    @Test
    void aPatientInTheMedbayStaysThereThroughASave(@TempDir Path dir) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        // A bed laid in the starter corvette, far from the crew, for the test
        List<int[]> floors = GameScenario.floorTiles(grid);
        int[] bed = floors.get(floors.size() - 1);
        grid.getTile(bed[0], bed[1]).covering = FloorCovering.MEDBAY;
        Entity bea = crewNamed(world, game.player, "Bea");
        world.crew.applyDamage(bea, 75f, DamageCause.FRAGMENT);
        assertTrue(runUntil(world, 120, () -> inBed(world, bea) && world.crew.crew(bea).carriedBy == null), "carried to the bed");
        run(world, 60 * 10);
        CrewMemberComponent c = world.crew.crew(bea);
        assertEquals(Wound.SERIOUS, c.wound);
        assertTrue(c.care > 0f, "being treated");
        float care = c.care;
        FileHandle file = new FileHandle(dir.resolve("medbay.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            Entity b = crewNamed(fresh, loaded.player, "Bea");
            assertTrue(inBed(fresh, b), "still in the bed");
            assertEquals(Wound.SERIOUS, fresh.crew.crew(b).wound);
            assertEquals(care, fresh.crew.crew(b).care, 1e-3f, "with the care she had");
            assertTrue(runUntil(fresh, 600, () -> fresh.crew.crew(b).wound != Wound.SERIOUS), "and is treated there");
            assertTrue(inBed(fresh, b));
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void theCrewTreatALightWoundOnceTheShipIsOffBattleStations() {
        Entity bea = crewNamed(world, game.player, "Bea");
        world.crew.applyDamage(bea, 25f, DamageCause.FRAGMENT);
        assertEquals(Wound.LIGHT, world.crew.crew(bea).wound);
        assertFalse(game.player.getComponent(ShipTaskBoardComponent.class).battleStations);
        int stores = game.player.getComponent(ShipGridComponent.class).medicalStores;
        assertTrue(runUntil(world, 600, () -> world.crew.crew(bea).wound == Wound.NONE),
                "treated well inside the " + world.crew.crew(bea).def.lightHealTime + " s it takes to heal alone");
        assertEquals(stores, game.player.getComponent(ShipGridComponent.class).medicalStores, "without stores");
    }

    // ---------------------------------------------------------------------------------------------

    private int beds(String chassis) {
        ChassisDef def = world.getContent().defs.get("chassis", chassis, ChassisDef.class);
        ShipGridComponent grid = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(def, InteriorGridData.catalog(world.getContent().defs))).grid;
        int n = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) if (TaskRules.isMedbay(grid, x, y)) n++;
        }
        return n;
    }

    private static boolean inBed(GameWorld w, Entity e) {
        GridPositionComponent p = w.crew.position(e);
        return TaskRules.isMedbay(w.crew.grid(p.ship), p.tileX(), p.tileY());
    }

    private static Entity crewNamed(GameWorld w, Entity ship, String name) {
        for (Entity e : w.crewCareer.ownCrew(ship)) if (name.equals(w.crew.crew(e).name)) return e;
        return null;
    }

    private static boolean runUntil(GameWorld w, float seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            if (done.getAsBoolean()) return true;
            w.update(FRAME);
        }
        return done.getAsBoolean();
    }

    private static void run(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(FRAME);
    }
}
