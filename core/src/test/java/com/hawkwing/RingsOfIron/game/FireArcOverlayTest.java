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
    void theDodgeLinesStraddleTheInterceptByHowFarTheTargetCouldBurn() {
        WeaponMountComponent coilgun = mount("coilgun_mk1");
        sensor.lockedTarget = game.raider;
        for (int i = 0; i < 60; i++) world.update(FRAME);
        FireArcOverlay.Dodge d = new FireArcOverlay.Dodge();
        com.badlogic.gdx.math.Vector2 muzzle = FireArcOverlay.muzzle(coilgun, world.docking.bodyOf(game.player), new com.badlogic.gdx.math.Vector2());
        assertTrue(arcs.dodge(coilgun, muzzle, game.raider, new com.badlogic.gdx.math.Vector2(), d), "the raider is tracked");
        assertTrue(d.flight > 0f);
        float aside = com.ringsofiron.combatsys.fire.HitChance.BROADSIDE_SHARE * world.weaponControl.radiusOf(game.raider)
                + 0.5f * world.weaponControl.evasionOf(game.raider) * d.flight * d.flight;
        assertEquals(aside, d.left.dst(d.intercept), 0.01f * aside + 0.01f);
        assertEquals(aside, d.right.dst(d.intercept), 0.01f * aside + 0.01f);
        // Square to the line of fire
        com.badlogic.gdx.math.Vector2 fire = new com.badlogic.gdx.math.Vector2(d.intercept).sub(muzzle);
        com.badlogic.gdx.math.Vector2 across = new com.badlogic.gdx.math.Vector2(d.left).sub(d.right);
        assertEquals(0f, fire.nor().dot(across.nor()), 1e-3f);
    }

    @Test
    void theArcsStayOnTheirGunsAtSpeed() {
        WeaponMountComponent coilgun = mount("coilgun_mk1");
        com.badlogic.gdx.physics.box2d.Body body = world.docking.bodyOf(game.player);
        com.ringsofiron.physicsexterior.PhysicsManager.setVelocity(body, 150f, 0f); // under the rebase speed, so it moves through the bubble
        for (int i = 0; i < 10; i++) world.update(FRAME);
        com.badlogic.gdx.math.Vector2 hull = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(body, new com.badlogic.gdx.math.Vector2());
        com.badlogic.gdx.math.Vector2 onHull = new com.badlogic.gdx.math.Vector2(coilgun.hardpoint).rotateRad(body.getAngle()).add(hull);
        com.badlogic.gdx.math.Vector2 muzzle = FireArcOverlay.muzzle(coilgun, body, new com.badlogic.gdx.math.Vector2());
        assertEquals(0f, muzzle.dst(onHull), 0.05f, "where the hull is drawn");
        assertTrue(coilgun.muzzle.dst(onHull) > 1f, "weapon control's muzzle trails by a tick's travel: " + coilgun.muzzle.dst(onHull));
    }

    @Test
    void theLinesAreGreenCloseInAndRedFarOut() {
        WeaponMountComponent coilgun = mount("coilgun_mk1");
        float radius = world.weaponControl.radiusOf(game.raider), evasion = world.weaponControl.evasionOf(game.raider);
        var round = world.weaponControl.roundOf(coilgun.def);
        java.util.function.DoubleUnaryOperator tint = range -> com.ringsofiron.combatsys.fire.HitChance.tint(
                com.ringsofiron.combatsys.fire.HitChance.rated(coilgun.def, com.ringsofiron.combatsys.fire.HitChance.perShot(
                        coilgun.def, round, 0f, coilgun.gunnerySkill, (float) range, radius, evasion)),
                com.ringsofiron.combatsys.fire.HitChance.rated(coilgun.def, com.ringsofiron.combatsys.fire.HitChance.perShot(
                        coilgun.def, round, 0f, coilgun.gunnerySkill, (float) range, radius, 0f)));
        assertEquals(1.0, tint.applyAsDouble(500), 1e-3, "point blank: green");
        assertTrue(tint.applyAsDouble(40_000) < 0.25, "far out: red");
        assertEquals(new com.badlogic.gdx.graphics.Color(0.35f, 1f, 0.45f, 1f), FireArcOverlay.tint(1f, new com.badlogic.gdx.graphics.Color()));
    }

    private WeaponMountComponent mount(String weapon) {
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) if (m.def.id.equals(weapon)) return m;
        throw new AssertionError("no " + weapon);
    }
}
