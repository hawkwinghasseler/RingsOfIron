package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.shipops.StationYard;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 5's docking fixes at the berth, on the real new game docked at Highport: yard work goes
 * on through a moment's loss of the latch, and a ship venting at the berth is warned about and
 * kept out of time warp until it is patched or repaired.
 */
class BerthTest {
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
        assertSame(highport, world.docking.dockedTo(game.player), "the career starts docked at Highport");
        grid = game.player.getComponent(ShipGridComponent.class);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void yardWorkGoesOnThroughABlipInTheLatch() {
        holeADeckPlate();
        assertTrue(world.yard.orderRepairs(game.player, highport) != null);
        assertTrue(world.yard.repairing(game.player));
        run(60);
        // The latch lets go for a moment and takes hold again
        assertTrue(world.docking.undock(game.player));
        run(60 * 3);
        assertTrue(world.yard.repairing(game.player), "the yard waits for it");
        assertTrue(game.dockAtHighport());
        run(60 * 3);
        assertTrue(world.docking.docked(game.player, highport));
        assertTrue(world.yard.repairing(game.player) || world.yard.repairCost(game.player) <= 0.5, "and carries on");
        assertFalse(yardSaid("Undocked: the yard work is called off"), "never called off");
    }

    @Test
    void yardWorkIsCalledOffOnceTheShipStaysLooseOrIsUndockedOnPurpose() {
        holeADeckPlate();
        world.yard.orderRepairs(game.player, highport);
        assertTrue(world.docking.undock(game.player));
        run((int) ((StationYard.LOOSE_GRACE + 2) / FRAME));
        assertFalse(world.yard.repairing(game.player), "loose too long: called off");
        assertTrue(yardSaid("Undocked: the yard work is called off"));

        world.yard.notices.clear();
        assertTrue(game.dockAtHighport());
        run(60);
        String said = world.yard.orderRepairs(game.player, highport);
        assertTrue(world.yard.repairing(game.player), said);
        PlayerDocking docking = new PlayerDocking(world.avatar, world.docking, world.cargo, world.yard, () -> game.player, s -> {});
        assertTrue(docking.undock().startsWith("Undocked from Highport"), "U undocks");
        assertFalse(world.yard.repairing(game.player), "undocking on purpose calls the work off at once");
        assertTrue(yardSaid("Undocked: the yard work is called off"));
        docking.dispose();
    }

    @Test
    void aMomentsVentingAtTheBerthIsNotWarnedAbout() {
        // The real-window playtest: just after a load the dock's opening can read as open to space for
        // a few frames, until its link is rebuilt, and the station warned of venting that wasn't there
        grid.patchKits = 0;
        holeADeckPlate();
        run(BerthVenting.WARN_AFTER_TICKS / 2);
        assertNotNull(world.berthVenting.station(), "venting");
        assertFalse(noticed("Highport: your ship is venting at the berth"), "not yet");
        run(BerthVenting.WARN_AFTER_TICKS);
        assertTrue(noticed("Highport: your ship is venting at the berth"), "once it lasts");
    }

    @Test
    void aShipVentingAtTheBerthIsWarnedAboutAndKeptOutOfWarpUntilRepaired() {
        grid.patchKits = 0;
        holeADeckPlate();
        run(60);
        assertNotNull(world.berthVenting.station(), "venting at Highport's berth");
        String why = world.berthVenting.railsWarpBlockedReason();
        assertEquals(BerthVenting.REFUSAL + ": order repairs at Highport's yard", why);
        assertFalse(world.timeWarp.setWarp(1_000), "no time warp");
        assertTrue(noticed("Highport: your ship is venting at the berth"), "the station warns");
        long warnings = count("Highport: your ship is venting at the berth");
        run(600);
        assertEquals(warnings, count("Highport: your ship is venting at the berth"), "once, not every frame");

        assertTrue(world.yard.orderRepairs(game.player, highport) != null);
        assertEquals(BerthVenting.REFUSAL + ": the yard is repairing it", world.berthVenting.railsWarpBlockedReason());
        boolean mended = false;
        for (int i = 0; i < 60 * 600 && !mended; i++) {
            world.update(FRAME);
            mended = world.berthVenting.station() == null;
        }
        assertTrue(mended, "the yard closed the hole");
        run(60);
        assertNull(world.berthVenting.railsWarpBlockedReason(), "warp is clear once repaired");
        assertTrue(world.timeWarp.setWarp(1_000), "and warp goes: " + world.engine.getRailsWarpBlockedReason());
        world.timeWarp.setWarp(1);
    }

    @Test
    void anEmptiedRoomOpenToSpaceStillCountsAtTheBerth() {
        grid.patchKits = 0;
        holeADeckPlate();
        run(60);
        // However little air still goes out, a hole to space is a hole at the berth
        assertTrue(BerthVenting.openToSpace(grid));
        Tile plate = null;
        for (int y = 0; y < grid.height && plate == null; y++) for (int x = 0; x < grid.width; x++) if (grid.getTile(x, y).integrity == 0.2f) plate = grid.getTile(x, y);
        plate.integrity = 1f;
        run(60);
        assertFalse(BerthVenting.openToSpace(grid), "mended: nothing open to space");
        assertNull(world.berthVenting.station());
    }

    // ------------------------------------------------------------------

    /** A deck plate at 0.2 integrity, venting to space, as the ace's 40 mm rounds leave them. */
    private void holeADeckPlate() {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.piece == null && t.isFloor() && !t.isWall()) {
                    t.integrity = 0.2f;
                    assertTrue(t.leakFactor() > 0, "it vents");
                    return;
                }
            }
        }
        fail("no deck plate");
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** The yard's own notices, which the game screen brings into the notice log. */
    private boolean yardSaid(String text) {
        return world.yard.notices.stream().anyMatch(n -> n.startsWith(text));
    }

    private boolean noticed(String text) {
        return count(text) > 0;
    }

    private long count(String text) {
        return world.notices.history().stream().filter(n -> n.text.startsWith(text)).count();
    }
}
