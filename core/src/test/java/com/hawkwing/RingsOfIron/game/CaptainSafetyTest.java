package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 6 part 1: the player's figure suits up at battle stations and in thin air, and the suit is saved. */
class CaptainSafetyTest {
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
        run(120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private void runSeconds(float seconds) {
        run(Math.round(seconds / FRAME));
    }

    private CrewMemberComponent you() {
        return game.avatar.getComponent(CrewMemberComponent.class);
    }

    private ShipTaskBoardComponent board() {
        return game.player.getComponent(ShipTaskBoardComponent.class);
    }

    @Test
    void theCaptainSuitsUpAtBattleStationsAndTakesItOffOnceCalm() {
        assertFalse(you().suited);
        game.provokeRaider();
        run(2);
        assertTrue(board().battleStations, "the raider has us as its target");
        runSeconds(you().def.suitTime + 1);
        assertTrue(you().suited, "suited up with the crew");

        // The crew window's toggle covers the captain too
        world.medical.toggleSuits();
        runSeconds(CrewTasks.Idle.SUIT_OFF_DELAY + you().def.suitTime);
        assertFalse(you().suited, "out of the suit with suits at battle stations off");
        world.medical.toggleSuits();
        runSeconds(you().def.suitTime + 1);
        assertTrue(you().suited);

        // The raider beaten and no shot for the battle hold: the suit comes off
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        for (int i = 0; i < (CrewMedical.BATTLE_HOLD + 30) / FRAME && board().battleStations; i++) world.update(FRAME);
        assertFalse(board().battleStations);
        assertTrue(you().suited, "the suit stays on until calm");
        runSeconds(CrewTasks.Idle.SUIT_OFF_DELAY + you().def.suitTime);
        assertFalse(you().suited, "calm again");
    }

    @Test
    void inThinAirTheCaptainPullsTheSuitOnAndIsToldToMove() {
        GridPositionComponent p = game.avatar.getComponent(GridPositionComponent.class);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        Compartment room = grid.compartmentAt(p.tileX(), p.tileY());
        assertNotNull(room);
        assertFalse(world.captain.inThinAir());
        float until = you().def.suitTime + 1;
        for (float t = 0; t < until; t += FRAME) {
            // Hold the room at half its air, whatever life support puts back
            room.oxygen = Math.min(room.oxygen, 0.5 * room.volume * Compartment.STANDARD_O2_PRESSURE * 1000.0 / (Compartment.R * room.temperature));
            room.nitrogen = Math.min(room.nitrogen, 0.5 * room.volume * (Compartment.STANDARD_PRESSURE - Compartment.STANDARD_O2_PRESSURE) * 1000.0
                    / (Compartment.R * room.temperature));
            world.update(FRAME);
        }
        assertTrue(world.captain.inThinAir());
        assertTrue(you().suited, "the suit went on");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.equals(CaptainSafety.THIN_AIR)),
                () -> world.notices.history().stream().map(n -> n.text).toList().toString());
        assertTrue(world.hints.forContexts(java.util.EnumSet.of(HintRegistry.Context.DECK)).stream().anyMatch(h -> h.label.startsWith("walk to air")),
                "a hint to walk to air");
    }

    @Test
    void theCaptainsSuitIsSaved(@TempDir Path dir) {
        game.provokeRaider();
        run(2);
        runSeconds(you().def.suitTime + 1);
        assertTrue(you().suited);
        float air = you().suitAir;
        FileHandle file = new FileHandle(dir.resolve("captain.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            CrewMemberComponent c = loaded.avatar.getComponent(CrewMemberComponent.class);
            assertTrue(c.suited, "still suited after a load");
            assertEquals(air, c.suitAir, 1e-3f);
        } finally {
            fresh.dispose();
        }
    }
}
