package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.shipops.StationCrew;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round 8: a station's hands who worked aboard the player's ship never leave with it. Seed 7's
 * career undocked as the yard finished, and two of Highport's technicians flew off aboard.
 */
class YardHandsTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    Entity highport;
    ShipGridComponent grid;

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
        highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        grid = game.player.getComponent(ShipGridComponent.class);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void handsWalkHomeWhenTheJobIsDone() {
        repairUntilTechniciansAreAboard();
        assertTrue(runUntil(600, () -> !world.yard.repairing(game.player)), "the yard finishes");
        assertTrue(runUntil(120, () -> handsAboard().isEmpty()), "and its hands walk home: " + handsAboard());
        assertSame(highport, world.docking.dockedTo(game.player));
    }

    @Test
    void handsAboardWhenTheShipUndocksAreBackOnTheStation() {
        List<Entity> hands = repairUntilTechniciansAreAboard();
        assertTrue(world.docking.undock(game.player));
        run(2);
        assertTrue(handsAboard().isEmpty(), "none leaves with the ship: " + handsAboard());
        ShipGridComponent station = highport.getComponent(ShipGridComponent.class);
        for (Entity e : hands) {
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            assertSame(highport, p.ship, "back aboard Highport");
            assertNull(p.crossing);
            assertTrue(TaskRules.isSafe(station, p.tileX(), p.tileY()), "where they can breathe");
        }
        // and they stay there
        run(60 * 30);
        assertTrue(handsAboard().isEmpty());
    }

    @Test
    void handsAboardWhenTheShipIsTornLooseAreBackOnTheStation() {
        repairUntilTechniciansAreAboard();
        // Gone without an undock: the ship jumped elsewhere, as a tow or the scripted career does
        world.docking.undock(game.player);
        world.rescue.jumpBeside(game.player, world.engine.getEntity(game.shipyard), 2_000);
        run(2);
        assertTrue(handsAboard().isEmpty(), "none leaves with the ship: " + handsAboard());
    }

    @Test
    void aSaveWithHandsBackHomeLoadsThemAtHome(@TempDir Path dir) {
        repairUntilTechniciansAreAboard();
        assertTrue(world.docking.undock(game.player));
        run(2);
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        run(2);
        assertTrue(handsAboard().isEmpty(), handsAboard().toString());
    }

    /**
     * Highport's two technicians died aboard a gunship an ace had shredded, and its yard held the order
     * open with nobody to work it, the ship at the berth and warp refused, for good (seed 7's round 5
     * career ran CI out of time). A yard whose technicians have all died takes on new ones.
     */
    @Test
    void techniciansWhoDieAreReplaced() {
        List<Entity> hands = repairUntilTechniciansAreAboard();
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = world.crew.crew(e);
            if (c.home == highport && c.role == com.ringsofiron.aicrew.CrewRole.TECHNICIAN) {
                c.health = 0;
                world.crew.updateState(e);
            }
        }
        assertEquals(0, world.yard.technicians(highport));
        assertTrue(runUntil(600, () -> !world.yard.repairing(game.player)), "the yard finishes with new hands");
        assertEquals(0, world.yard.repairCost(game.player), 0.5);
        assertEquals(StationCrew.TECHNICIANS, world.yard.technicians(highport));
        assertTrue(world.yard.notices.contains("Highport's yard took on new technicians"), world.yard.notices.toString());
        for (Entity e : hands) assertFalse(world.crew.crew(e).isAlive());
    }

    /** Holes the ship, orders repairs at Highport, and runs until the yard's technicians are aboard. */
    private List<Entity> repairUntilTechniciansAreAboard() {
        int n = 6;
        for (int y = 0; y < grid.height && n > 0; y++) {
            for (int x = 0; x < grid.width && n > 0; x++) {
                Tile t = grid.getTile(x, y);
                if (t.piece == null && t.isWall()) {
                    t.setIntegrity(0.5f);
                    n--;
                }
            }
        }
        assertNotNull(world.yard.orderRepairs(game.player, highport));
        assertTrue(world.yard.repairing(game.player));
        assertTrue(runUntil(120, () -> !handsAboard().isEmpty()), "the technicians come aboard");
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) {
            if (e.getComponent(CrewMemberComponent.class).home == highport && aboard(e)) out.add(e);
        }
        return out;
    }

    private boolean aboard(Entity e) {
        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
        return p.ship == game.player;
    }

    /** Names of crew aboard the player's ship whose home is a station. */
    private List<String> handsAboard() {
        List<String> out = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (aboard(e) && c.home != null && c.home != game.player) out.add(c.name + " (" + c.activity + ")");
        }
        return out;
    }

    private boolean runUntil(float seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            if (done.getAsBoolean()) return true;
            world.update(FRAME);
        }
        return done.getAsBoolean();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }
}
