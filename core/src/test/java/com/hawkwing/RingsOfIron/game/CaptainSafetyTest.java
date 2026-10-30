package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.btree.Idle;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The player's figure is never suited or unsuited by the game; its suit fills at a console, and is saved. */
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
    void theCaptainNeverPutsASuitOnOrTakesItOffByThemselves() {
        // Battle stations: the crew suit up, the captain doesn't unless the player does
        assertFalse(you().suited);
        game.provokeRaider();
        run(2);
        assertTrue(board().battleStations, "the raider has us as its target");
        runSeconds(you().def.suitTime + 1);
        assertFalse(you().suited, "no suit without the player");

        // Put on with E, it stays on once the fight is over and all is calm
        you().setSuited(true);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        for (int i = 0; i < (CrewMedical.BATTLE_HOLD + 30) / FRAME && board().battleStations; i++) world.update(FRAME);
        assertFalse(board().battleStations);
        runSeconds(Idle.SUIT_OFF_DELAY + you().def.suitTime + 5);
        assertTrue(you().suited, "the suit stays on until the player hangs it up");
    }

    @Test
    void inThinAirTheCaptainIsToldToMoveButNotSuitedUp() {
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
        assertFalse(you().suited, "no suit without the player");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.equals(CaptainSafety.NO_SUIT)),
                () -> world.notices.history().stream().map(n -> n.text).toList().toString());
        assertTrue(world.hints.forContexts(java.util.EnumSet.of(HintRegistry.Context.DECK)).stream().anyMatch(h -> h.label.startsWith("walk to air")),
                "a hint to walk to air");
    }

    @Test
    void seatedAtAConsoleTheCaptainsSuitFills() {
        assertNull(world.bridge.sit(com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.HELM), "seated at the helm");
        you().setSuited(true);
        you().suitAir = 30f;
        runSeconds(you().def.suitRefillTime / 2);
        assertTrue(you().suitAir > 30f + you().suitCapacity() / 2 - 15f, "half filled: " + you().suitAir);
        runSeconds(you().def.suitRefillTime / 2 + 1);
        assertEquals(you().suitCapacity(), you().suitAir, 1e-3f, "full");

        // Standing up, the console no longer feeds it
        world.bridge.stand();
        you().suitAir = 30f;
        runSeconds(5);
        assertTrue(you().suitAir <= 30f, "not filled away from the console: " + you().suitAir);
    }

    @Test
    void theCaptainsSuitIsSaved(@TempDir Path dir) {
        world.bridge.stand();
        you().setSuited(true);
        you().suitAir = 200f;
        run(2);
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
