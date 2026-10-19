package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.orbital.math.StateVector;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A planned transfer to a station around the Moon, flown under time warp: the trans-lunar burn, the
 * capture proposed in the Moon's sphere, and arrival. Found in round 3's integration playtest: the
 * corvette's tanks gave about 1 km/s against the 3.9 km/s trans-lunar burn, so the burn was cut short
 * at every warp rate and the ship never reached the Moon.
 */
class MoonTransferTest {
    static final float FRAME = 1f / 60f;
    static final double DAY = 86_400;

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
        for (int i = 0; i < 60; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 10_000, 100_000})
    void aPlannedTripToTheMoonArrivesAtEveryWarpRate(int warp) {
        world.nav.target.select(game.lunarMine);
        String said = world.nav.transfer.plan();
        assertNotNull(world.nav.transfer.proposal(), said);
        assertFalse(said.contains("Not enough propellant"), said);
        assertTrue(world.nav.deltaVLeft() > world.nav.deltaVNeeded() + 1_500, "the tanks cover the trip and a capture: "
                + world.nav.deltaVLeft() + " m/s against " + world.nav.deltaVNeeded());
        world.nav.transfer.accept(world.burns);

        double t0 = world.universe.now(), closest = Double.MAX_VALUE;
        boolean captured = false;
        Entity station = world.engine.getEntity(game.lunarMine);
        for (int frame = 0; world.universe.now() - t0 < 8 * DAY && frame < 400_000; frame++) {
            if (world.timeWarp.getWarp() == 1) world.timeWarp.setWarp(warp);
            world.update(FRAME);
            assertNull(world.getCrash(), "no crash on the way");
            if (world.nav.transfer.proposal() != null) {
                world.nav.transfer.accept(world.burns);
                captured = true;
            }
            Entity parent = world.universe.parentOf(game.player);
            if (parent != null && GameScenario.MOON.equals(world.universe.uuidOf(parent))) {
                closest = Math.min(closest, distance(game.player, station, parent));
            }
        }
        assertTrue(captured, "the capture was proposed in the Moon's sphere");
        assertTrue(closest < 50_000, "came within 50 km of " + world.nav.target.name(game.lunarMine) + ": " + closest / 1000 + " km");
    }

    @Test
    void aPlanTheTanksCannotCoverSaysSo() {
        ShipSubsystemRegistryComponent registry = game.player.getComponent(ShipSubsystemRegistryComponent.class);
        for (SubsystemComponent t : TankSupply.tanks(registry, Fluid.PROPELLANT)) t.contents *= 0.2f;
        world.update(FRAME);
        world.nav.target.select(game.lunarMine);
        String said = world.nav.transfer.plan();
        assertNotNull(world.nav.transfer.proposal(), "still proposed, to be flown part way or edited");
        assertTrue(said.contains("Not enough propellant: "), said);
    }

    private double distance(Entity a, Entity b, Entity frame) {
        StateVector sa = new StateVector(), sb = new StateVector();
        world.universe.stateInFrame(a, frame, world.universe.now(), sa);
        world.universe.stateInFrame(b, frame, world.universe.now(), sb);
        return Math.hypot(sa.x - sb.x, sa.y - sb.y);
    }
}
