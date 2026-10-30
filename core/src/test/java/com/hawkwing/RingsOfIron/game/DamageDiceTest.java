package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.coreengine.components.UuidComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A ship's damage dice (shorts, bursts, spall) replay. Seeded careers and the designed start hand out
 * seeded UUIDs and roll by them; a world on random UUIDs rolls by each ship's spawn order.
 */
class DamageDiceTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    /**
     * The first draws of the player's and the raider's damage dice, and the player's UUID. With
     * {@code randomIds}, the world is put back on random UUIDs once started and the player given a new
     * random one, as a world built without {@link GameScenario} has.
     */
    static String dice(NewGame newGame, boolean randomIds) {
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"), newGame);
            if (randomIds) {
                world.engine.getEntityIds().unseed();
                game.player.getComponent(UuidComponent.class).id = java.util.UUID.randomUUID().toString();
                world.impacts.reseed(newGame.combatSeed());
            }
            return world.impacts.rngFor(game.player).nextLong() + " " + world.impacts.rngFor(game.raider).nextLong()
                    + " " + game.player.getComponent(UuidComponent.class).id;
        } finally {
            world.dispose();
        }
    }

    static String dice(NewGame newGame) {
        return dice(newGame, false);
    }

    @Test
    void aDesignedGameHandsOutTheSameUuidsAndDamageEveryRun() {
        assertEquals(dice(NewGame.designed()), dice(NewGame.designed()));
    }

    @Test
    void randomUuidsRollByTheShipsSpawnOrder() {
        String[] a = dice(NewGame.designed(), true).split(" "), b = dice(NewGame.designed(), true).split(" ");
        assertNotEquals(a[2], b[2], "the UUIDs are random");
        assertEquals(a[0], b[0], "the player's damage dice are the same");
        assertEquals(a[1], b[1], "the raider's damage dice are the same");
        assertNotEquals(a[0], a[1], "two ships roll apart");
    }

    @Test
    void aSeededCareerRollsByItsSeed() {
        assertEquals(dice(NewGame.seeded(2026, "patrol")), dice(NewGame.seeded(2026, "patrol")));
        assertNotEquals(dice(NewGame.seeded(2026, "patrol")), dice(NewGame.seeded(7, "patrol")));
    }
}
