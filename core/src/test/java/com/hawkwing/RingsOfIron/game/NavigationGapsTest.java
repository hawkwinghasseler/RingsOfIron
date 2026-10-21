package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.events.ImpactEvent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Navigation's gaps: the rest of a Moon leg proposed after a load, a helm order dropped when nobody
 * comes to the helm or the ship is hit, and the approach order refused from too far.
 */
class NavigationGapsTest {
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
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void theMoonCaptureIsProposedAfterALoad(@TempDir Path dir) {
        world.nav.target.select(game.lunarMine);
        world.nav.transfer.plan();
        assertEquals(GameScenario.MOON, world.nav.transfer.proposal().nextParent);
        assertTrue(world.nav.transfer.accept(world.burns).startsWith("Transfer to Shackleton Ice planned"));
        FileHandle file = new FileHandle(dir.resolve("leg.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(1, fresh.burns.nodes().nodes.size, "the trans-lunar burn came back");
            fresh.nav.transfer.update(fresh.burns);
            assertNull(fresh.nav.transfer.proposal(), "nothing until the ship is at the Moon");

            // Flown: the burn done, and the ship on a low orbit around the Moon
            fresh.burns.nodes().nodes.clear();
            Entity moon = fresh.universe.entity(GameScenario.MOON);
            double mu = fresh.universe.celestial(moon).mu;
            double r = fresh.universe.celestial(moon).radius + 100_000;
            loaded.player.add(new OrbitComponent(GameScenario.MOON, KeplerOrbit.circular(mu, r, 0, fresh.universe.now(), 1)));
            fresh.nav.transfer.update(fresh.burns);
            PlayerTransfer.Proposal p = fresh.nav.transfer.proposal();
            assertNotNull(p, "at the Moon with no burns left, the capture is proposed");
            assertEquals(loaded.lunarMine, p.targetUuid);
            assertFalse(p.burns.isEmpty());
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void anOrderNobodyComesToFlyIsDropped() {
        world.nav.target.select(game.highport);
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION).startsWith("Helm: hold position"));
        killTheCrew();
        world.bridge.stand();
        double start = world.universe.now();
        while (world.universe.now() - start < HelmOrders.UNMANNED_LIMIT - 5) world.update(FRAME);
        assertNull(world.bridge.helmsman(game.player), "nobody fit to take it");
        assertEquals(HelmOrderComponent.Mode.HOLD_POSITION, world.nav.helm.mode(), "still waiting");
        while (world.universe.now() - start < HelmOrders.UNMANNED_LIMIT + 5) world.update(FRAME);
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());
        boolean said = false;
        for (NoticeLog.Notice n : world.notices.history()) said |= n.text.equals("Helm order dropped: nobody took the helm");
        assertTrue(said);
    }

    @Test
    void aHitDropsAnApproachButNotAVelocityMatch() {
        world.nav.target.select(game.highport);
        world.nav.helm.give(HelmOrderComponent.Mode.MATCH_VELOCITY);
        assertEquals(HelmOrderComponent.Mode.MATCH_VELOCITY, world.nav.helm.mode());
        ImpactEvent hit = new ImpactEvent();
        hit.owner = game.raider;
        hit.target = game.player;
        world.combatEvents.impacts.dispatch(hit);
        assertEquals(HelmOrderComponent.Mode.MATCH_VELOCITY, world.nav.helm.mode(), "round 8: a velocity match carries on");
        String said = world.nav.helm.give(HelmOrderComponent.Mode.APPROACH);
        assertEquals(HelmOrderComponent.Mode.APPROACH, world.nav.helm.mode(), said);
        world.combatEvents.impacts.dispatch(hit);
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());
        world.update(FRAME);
        assertEquals("Helm order dropped: we're hit", world.notices.latest().text);
    }

    @Test
    void approachIsRefusedFromTooFar() {
        world.nav.target.select(game.depot);
        String said = world.nav.helm.give(HelmOrderComponent.Mode.APPROACH);
        assertTrue(said.startsWith("Deepwell Depot is too far for the helm to approach"), said);
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());

        // Highport, 8 km ahead, is within reach
        world.nav.target.select(game.highport);
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.APPROACH).startsWith("Helm: approach Highport"));
    }

    /** Everyone aboard but the player, so nobody can come to the helm. */
    private void killTheCrew() {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e == game.avatar || e.getComponent(GridPositionComponent.class).ship != game.player) continue;
            world.crew.applyDamage(e, 1e6f, CrewDamageSink.DamageCause.BLAST);
        }
    }
}
