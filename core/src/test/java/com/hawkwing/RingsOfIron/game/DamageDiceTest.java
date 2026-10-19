package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.UuidComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A ship's damage dice (shorts, bursts, spall) replay: in a designed game, whose UUIDs are random, they
 * follow the ship's spawn order, and in a seeded career its (seeded) UUID, as before.
 */
class DamageDiceTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    /** The first draws of the player's and the raider's damage dice, and the player's UUID. */
    static String dice(NewGame newGame) {
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"), newGame);
            return world.impacts.rngFor(game.player).nextLong() + " " + world.impacts.rngFor(game.raider).nextLong()
                    + " " + game.player.getComponent(UuidComponent.class).id;
        } finally {
            world.dispose();
        }
    }

    @Test
    void aDesignedGameRollsTheSameDamageEveryRun() {
        String[] a = dice(NewGame.designed()).split(" "), b = dice(NewGame.designed()).split(" ");
        assertNotEquals(a[2], b[2], "a designed game's UUIDs are random");
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
