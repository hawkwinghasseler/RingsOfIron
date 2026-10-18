package com.ringsofiron.combatsys.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Beams (combat-sys/Design.md 2.5 and 5.6). Each tick, every beam {@link WeaponControlSystem}
 * has burning is cast from its muzzle along its direction to its reach; the first ship, asteroid
 * or station hull it meets takes {@code beamPower * dt} (less on short power) through
 * {@link ImpactResolutionSystem#burn}. Light has no travel time, so this bypasses ballistics, and
 * a beam that keeps moving across a hull spreads its heat over many tiles.
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}. Add it to the engine after
 * {@link WeaponControlSystem}.
 */
public class BeamSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY = Family.all(WeaponsComponent.class, PhysicsBodyComponent.class).get();

    private final ComponentMapper<WeaponsComponent> weaponsMapper = ComponentMapper.getFor(WeaponsComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final PhysicsManager physics;
    private final ImpactResolutionSystem impacts;
    private ImmutableArray<Entity> ships;

    // Closest hit of the current cast.
    private Body ownBody;
    private Fixture hitFixture;
    private float hitX, hitY, hitFraction;

    public BeamSystem(PhysicsManager physics, ImpactResolutionSystem impacts) {
        this.physics = physics;
        this.impacts = impacts;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(FAMILY);
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float dt) {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            if (ship.isScheduledForRemoval()) continue;
            Body body = bodyMapper.get(ship).body;
            WeaponsComponent weapons = weaponsMapper.get(ship);
            for (int m = 0; m < weapons.mounts.size; m++) {
                WeaponMountComponent mount = weapons.mounts.get(m);
                if (mount.def == null || mount.def.mount != WeaponDef.MountType.BEAM) continue;
                if (!mount.beamOn || body == null) {
                    mount.beamContact = null;
                    continue;
                }
                fire(body, mount, dt);
            }
        }
    }

    private void fire(Body body, WeaponMountComponent mount, float dt) {
        WeaponDef def = mount.def;
        float reach = def.maxRange > 0f ? def.maxRange : WeaponControlSystem.DEFAULT_BEAM_RANGE;
        float dx = mount.beamDir.x, dy = mount.beamDir.y;
        float sx = mount.muzzle.x + dx * def.muzzleOffset, sy = mount.muzzle.y + dy * def.muzzleOffset;
        float ex = mount.muzzle.x + dx * reach, ey = mount.muzzle.y + dy * reach;

        ownBody = body;
        hitFixture = null;
        hitFraction = 2f;
        physics.rayCast(sx, sy, ex, ey, this::closest);
        ownBody = null;

        Entity target = hitFixture == null ? null
                : hitFixture.getBody().getUserData() instanceof Entity ? (Entity) hitFixture.getBody().getUserData() : null;
        if (hitFixture == null) {
            mount.beamEnd.set(ex, ey);
            mount.beamContact = null;
            return;
        }
        mount.beamEnd.set(hitX, hitY);
        if (target != null && !target.isScheduledForRemoval()) {
            impacts.burn(target, hitX, hitY, dx, dy, def.beamPower * mount.beamStrength * dt,
                    target != mount.beamContact);
        }
        mount.beamContact = target;
    }

    private float closest(Fixture fixture, float x, float y, float nx, float ny, float fraction) {
        if (fixture.getBody() == ownBody) return -1f;
        if ((fixture.getFilterData().categoryBits & BallisticsSystem.TARGET_MASK) == 0) return -1f;
        if (fraction < hitFraction) {
            hitFraction = fraction;
            hitFixture = fixture;
            hitX = x;
            hitY = y;
        }
        return fraction;
    }
}
