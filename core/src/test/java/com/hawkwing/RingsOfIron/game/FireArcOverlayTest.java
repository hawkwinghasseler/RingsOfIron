package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Which guns' firing arcs show, and their tint by the chance to hit. */
class FireArcOverlayTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    FireArcOverlay arcs;
    SensorComponent sensor;
    final Array<WeaponMountComponent> shown = new Array<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.update(FRAME);
        arcs = new FireArcOverlay(world);
        sensor = game.player.getComponent(SensorComponent.class);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void flyingTheShipTheArcsShowOnceATargetIsLocked() {
        assertTrue(world.bridge.playerAtHelm());
        sensor.lockedTarget = null;
        arcs.shown(shown);
        assertEquals(0, shown.size, "weapons not armed: nothing locked");
        sensor.lockedTarget = game.raider;
        arcs.shown(shown);
        assertTrue(shown.size > 0);
        for (WeaponMountComponent m : shown) assertTrue(PlayerGunnery.isMainGun(m), m.def.id + ": no point defense or launchers");
        int guns = 0;
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) if (PlayerGunnery.isMainGun(m)) guns++;
        assertEquals(guns, shown.size, "every gun");
    }

    @Test
    void atAGunJustThatOnesArcShows() {
        sensor.lockedTarget = null;
        WeaponMountComponent laser = null;
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) if (m.def.id.equals("laser_mk1")) laser = m;
        SubsystemComponent mount = laser.subsystem.getComponent(SubsystemComponent.class);
        world.bridge.stand();
        game.avatar.getComponent(GridPositionComponent.class).setTile(mount.footprint.originX - 1, mount.footprint.originY);
        assertNull(world.bridge.sit(SubsystemType.TURRET_MOUNT));
        arcs.shown(shown);
        assertEquals(1, shown.size);
        assertSame(laser, shown.first());
    }

    @Test
    void theArcIsGreenNearTheGunAndRedOutAtItsEnd() {
        WeaponMountComponent coilgun = null;
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) if (m.def.id.equals("coilgun_mk1")) coilgun = m;
        float far = FireArcOverlay.reach(world.weaponControl, coilgun, FireArcOverlay.REFERENCE_RADIUS);
        assertTrue(far > 5_000f, "past its 5 km guide: " + far);
        float[] tints = new float[FireArcOverlay.BANDS];
        FireArcOverlay.tints(world.weaponControl, coilgun, FireArcOverlay.REFERENCE_RADIUS, 10f, far, tints);
        assertEquals(1f, tints[0], 1e-3f, "point blank: green");
        assertTrue(tints[tints.length - 1] < 0.25f, "at the far end, where the round is spent: red, " + tints[tints.length - 1]);
        for (int i = 1; i < tints.length; i++) assertTrue(tints[i] <= tints[i - 1] + 1e-4f, "it only falls off");
    }
}
