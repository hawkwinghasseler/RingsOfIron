package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.career.NewGame;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 9 part 2: the raked tiles of a fight and a suit half off come through a save. */
class AirSaveTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;

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
        for (int i = 0; i < 120; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private CrewMemberComponent ada() {
        for (Entity e : world.crew.crewEntities()) if ("Ada".equals(world.crew.crew(e).name)) return world.crew.crew(e);
        return fail("Ada aboard");
    }

    @Test
    void theRakedTilesAndASuitHalfOffAreSaved(@TempDir Path dir) {
        ShipTaskBoardComponent board = game.player.getComponent(ShipTaskBoardComponent.class);
        board.hitAt(3, 4);
        board.hitAt(7, 2);
        ada().setSuited(true);
        ada().unsuiting = 1.5f;
        FileHandle file = new FileHandle(dir.resolve("air.json").toFile());
        assertNull(game.quickSave(file));
        board.standDown();
        ada().unsuiting = 0f;

        game.quickLoad(file);
        board = game.player.getComponent(ShipTaskBoardComponent.class);
        assertArrayEquals(new int[]{3, 4, 7, 2}, board.hitTiles.toArray(), "where the fire fell");
        assertTrue(ada().suited);
        assertEquals(1.5f, ada().unsuiting, 1e-6f, "part way out of her suit");
    }
}
