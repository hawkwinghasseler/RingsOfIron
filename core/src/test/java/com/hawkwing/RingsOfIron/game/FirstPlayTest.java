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
        assertEquals(FirstPlay.Step.ACCEPT, play.step());
        assertTrue(play.objective().startsWith("1/9"), play.objective());

        // The board's first offer is the bounty on the raider outside
        Contract bounty = world.contracts.offers(game.highport).stream()
                .filter(c -> c.type == Contract.Type.BOUNTY).findFirst().orElseThrow();
        assertEquals(world.universe.uuidOf(game.raider), bounty.targetUuid);
        assertNull(world.contracts.accept(bounty.id));
        run(2);
        assertEquals(FirstPlay.Step.UNDOCK, play.step());

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
        assertEquals(FirstPlay.Step.BOARD, play.step());
        assertEquals(Contract.Status.DONE, bounty.status, "the bounty is paid once it is beaten");

        // Latched on, the raider (nobody aboard) is taken as soon as the party is sent
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(120);
        assertTrue(world.boarding.takeUndefended(game.player, game.raider));
        run(2);
        assertEquals(FirstPlay.Step.DOCK, play.step());
        world.docking.undock(game.player);
        run(10);

        redock();
        assertSame(highport, world.docking.dockedTo(game.player));
        // Undamaged, there is nothing to repair
        if (play.step() == FirstPlay.Step.REPAIR) {
            assertNotNull(world.yard.orderRepairs(game.player, highport));
            run(2);
        }
        assertEquals(FirstPlay.Step.NOTICES, play.step());
        assertTrue(play.objective().contains("notice log"), play.objective());
        run(2);
        assertEquals(FirstPlay.Step.NOTICES, play.step(), "waits for the log to be opened");
        play.logOpened();
        run(2);
        assertEquals(FirstPlay.Step.PLAN, play.step());

        // A transfer to the ice mine at the Moon, planned from Highport's dock
        world.nav.target.select(game.lunarMine);
        run(2);
        assertEquals(FirstPlay.Step.PLAN, play.step(), "picking a target is not yet a plan");
        String said = world.nav.transfer.plan();
        assertNotNull(world.nav.transfer.proposal(), said);
        run(2);
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
        assertTrue(play.step() == FirstPlay.Step.REPAIR || play.step() == FirstPlay.Step.NOTICES, play.step().name());
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
        grid.getTile(1, 0).setIntegrity(0.5f);
        grid.gridDirty = true;
        redock();
        assertEquals(FirstPlay.Step.REPAIR, play.step());
        assertFalse(world.yard.damage(game.player).isEmpty());
        world.yard.orderRepairs(game.player, highport);
        run(2);
        assertEquals(FirstPlay.Step.NOTICES, play.step());
    }
}
