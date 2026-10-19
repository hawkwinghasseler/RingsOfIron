package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The scripted first play, driven headless by doing what each objective asks. */
class FirstPlayTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    FirstPlay play;
    Entity highport;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        play = FirstPlay.start(world, game);
        highport = world.engine.getEntity(game.highport);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(FRAME);
            play.update();
        }
    }

    private Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    /** Puts the player {@code meters} out from Highport, moving with it. */
    private void moveAway(float meters) {
        Body station = world.docking.bodyOf(highport);
        Vector2 at = PhysicsManager.getPosition(station, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(station, new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, 0f);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    private void redock() {
        assertTrue(FirstPlay.dockAtHighport(world, game));
        run(120);
    }

    @Test
    void startsDockedAtHighportAndWalksThroughEveryObjective() {
        run(60);
        assertSame(highport, world.docking.dockedTo(game.player), "starts docked");
        assertEquals(FirstPlay.Step.UNDOCK, play.step());
        assertTrue(play.objective().startsWith("1/5"), play.objective());

        assertTrue(world.docking.undock(game.player));
        run(10);
        assertEquals(FirstPlay.Step.FLY_CLEAR, play.step());
        assertFalse(game.raiderHostile(), "the raider waits");

        moveAway(FirstPlay.CLEAR_DISTANCE + 500f);
        run(2);
        assertEquals(FirstPlay.Step.FIGHT, play.step());
        assertTrue(game.raiderHostile(), "and turns on you once you are clear");

        // Its reactor knocked out: the raider can't fight
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub.type == SubsystemComponent.SubsystemType.REACTOR) sub.health = 0f;
        }
        run(30);
        assertEquals(FirstPlay.Step.DOCK, play.step());

        redock();
        assertSame(highport, world.docking.dockedTo(game.player));
        // Undamaged, there is nothing to repair: the loop is done
        if (play.step() == FirstPlay.Step.REPAIR) {
            assertNotNull(world.yard.orderRepairs(game.player, highport));
            run(2);
        }
        assertEquals(FirstPlay.Step.DONE, play.step());
    }

    @Test
    void dockingAgainMidFightCountsAsGettingAway() {
        world.docking.undock(game.player);
        run(10);
        moveAway(FirstPlay.CLEAR_DISTANCE + 500f);
        run(2);
        assertEquals(FirstPlay.Step.FIGHT, play.step());
        redock();
        assertTrue(play.step() == FirstPlay.Step.REPAIR || play.step() == FirstPlay.Step.DONE, play.step().name());
    }

    @Test
    void theRepairObjectiveWaitsForTheOrder() {
        world.docking.undock(game.player);
        run(10);
        moveAway(FirstPlay.CLEAR_DISTANCE + 500f);
        run(2);
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (sub.type == SubsystemComponent.SubsystemType.REACTOR) sub.health = 0f;
        }
        run(2);
        // Battle damage aboard
        com.ringsofiron.interiorgrid.ShipGridComponent grid =
                game.player.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class);
        grid.getTile(0, 0).setIntegrity(0.5f);
        grid.gridDirty = true;
        redock();
        assertEquals(FirstPlay.Step.REPAIR, play.step());
        assertFalse(world.yard.damage(game.player).isEmpty());
        world.yard.orderRepairs(game.player, highport);
        run(2);
        assertEquals(FirstPlay.Step.DONE, play.step());
    }
}
