package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The ways out of a stuck game: the distress beacon, the crew bringing a dead captain home, and a crash survived in the pods. */
class NoSoftLocksTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        if (world.docking.dockedTo(game.player) != null) world.docking.undock(game.player);
        for (int i = 0; i < 10; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private void drainPropellant(float share) {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent t = e.getComponent(SubsystemComponent.class);
            if (t != null && t.type == SubsystemComponent.SubsystemType.TANK
                    && t.fluid == com.ringsofiron.interiorgrid.Fluid.PROPELLANT) t.contents = t.capacity * share;
        }
    }

    private Entity avatar() {
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) return e;
        return null;
    }

    private int crewAboard() {
        int n = 0;
        for (Entity e : world.crew.crewEntities()) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player
                    && e.getComponent(CrewMemberComponent.class).isAlive()) n++;
        }
        return n;
    }

    @Test
    void aShipThatCanFlyHomeCantCallATow() {
        assertEquals("your ship can still fly home", world.beacon.refusal());
        assertTrue(world.beacon.call().startsWith("Can't call a tow"));
    }

    @Test
    void aStrandedShipIsTowedHomeWithItsCrewForAFeeAndStanding() {
        drainPropellant(0.05f);
        assertTrue(world.beacon.stranded());
        assertNull(world.beacon.refusal());
        Entity ship = game.player;
        int crew = crewAboard();
        double credits = ship.getComponent(TraderComponent.class).credits;
        float standing = world.standing.standing(world.standing.sponsor());
        double fee = world.beacon.fee();
        assertTrue(fee > 0);
        double before = world.engine.getSimSeconds();
        assertTrue(world.beacon.call().startsWith("Towed in"));
        run(3f);
        assertSame(ship, world.getPlayer(), "the same ship, not a requisition");
        assertNotNull(world.playerStation(), "docked at the station");
        assertEquals(Rescue.TIME_SKIP, world.engine.getSimSeconds() - before, 60, "days passed");
        assertEquals(credits - fee, ship.getComponent(TraderComponent.class).credits, 1e-6);
        assertEquals(standing - DistressBeacon.STANDING_COST, world.standing.standing(world.standing.sponsor()), 1e-4);
        assertEquals(crew, crewAboard(), "the crew came too");
        assertEquals("you are docked at a station", world.beacon.refusal());
    }

    @Test
    void theTowCanPutTheAccountInTheRed() {
        drainPropellant(0.05f);
        game.player.getComponent(TraderComponent.class).credits = 0;
        world.beacon.call();
        assertTrue(game.player.getComponent(TraderComponent.class).credits < 0);
    }

    @Test
    void aKilledCaptainIsBroughtHomeByTheCrew() {
        Entity avatar = avatar();
        Tows tows = new Tows();
        world.carriedHome.add((s, t) -> tows.n++);
        world.crew.applyDamage(avatar, 1e6f, CrewDamageSink.DamageCause.BLAST);
        world.update(FRAME);
        assertNull(world.getCareerOver());
        assertEquals(1, tows.n);
        assertTrue(avatar.getComponent(CrewMemberComponent.class).isConscious(), "hurt, but alive");
        run(3f);
        assertNotNull(world.playerStation());
    }

    @Test
    void withNoCrewLeftTheCareerIsOver() {
        for (Entity e : world.crew.crewEntities()) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player) {
                world.crew.applyDamage(e, 1e6f, CrewDamageSink.DamageCause.BLAST);
            }
        }
        world.update(FRAME);
        assertEquals("nobody was left to bring you home", world.getCareerOver());
        double t = world.engine.getSimSeconds();
        world.update(FRAME);
        assertEquals(t, world.engine.getSimSeconds(), 1e-9, "the world stops");
        world.clearCrash();
        assertNull(world.getCareerOver());
    }

    @Test
    void crashingAfterTheCaptainReachedAPodIsARescue() {
        Rescue.Result[] rescued = new Rescue.Result[1];
        world.rescue.rescues.add((s, r) -> rescued[0] = r);
        assertNull(world.abandon.order(game.player));
        world.update(FRAME);
        // The captain is in a pod; the rest are still on the way
        Entity pod = AbandonShip.usablePods(game.player).get(0);
        com.ringsofiron.interiorgrid.MountFootprint f = pod.getComponent(SubsystemComponent.class).footprint;
        GridPositionComponent p = avatar().getComponent(GridPositionComponent.class);
        p.stop();
        p.setTile(f.originX, f.originY);
        assertNull(rescued[0]);
        PhysicsBodyComponent pb = game.player.getComponent(PhysicsBodyComponent.class);
        world.physics.setTrueState(pb.body, GameScenario.R_EARTH - 10, 0, 0, 0);
        world.update(FRAME);
        assertNull(world.getCrash(), "not lost");
        assertNotNull(rescued[0], "rescued");
    }

    @Test
    void crashingWithNobodyInThePodsIsStillLost() {
        PhysicsBodyComponent pb = game.player.getComponent(PhysicsBodyComponent.class);
        world.physics.setTrueState(pb.body, GameScenario.R_EARTH - 10, 0, 0, 0);
        world.update(FRAME);
        assertNotNull(world.getCrash());
    }

    static final class Tows {
        int n;
    }
}
