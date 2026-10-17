package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Keeps the player near local (0, 0) and near rest, so single-precision positions stay precise and
 * the player never approaches Box2D's speed limit.
 *
 * When the player is further than the rebase distance from the origin, or moving faster than the
 * rebase speed relative to it, the origin jumps to the player's position and takes on the player's
 * velocity. Every {@link TransformComponent} and Box2D body is shifted to match, and
 * {@link PhysicsManager#worldShifts} fires. The change of frame is exact, so motion is seamless.
 *
 * Runs after {@link PhysicsUpdateSystem} so it sees this frame's positions.
 */
public class FloatingOriginSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = PhysicsUpdateSystem.DEFAULT_PRIORITY + 10;
    public static final float DEFAULT_REBASE_DISTANCE = 2000f;
    public static final float DEFAULT_REBASE_SPEED = 200f;

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);

    private final PhysicsManager physics;
    private final float rebaseDistance;
    private final float rebaseSpeed;
    private final Vector2 position = new Vector2();
    private final Vector2 velocity = new Vector2();
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> transforms;
    private int rebaseCount;

    public FloatingOriginSystem(PhysicsManager physics) {
        this(physics, DEFAULT_REBASE_DISTANCE, DEFAULT_REBASE_SPEED);
    }

    /**
     * @param rebaseDistance distance from local (0, 0), in meters, past which the origin moves to the player
     * @param rebaseSpeed    player speed relative to the bubble, in m/s, past which the origin matches velocity
     */
    public FloatingOriginSystem(PhysicsManager physics, float rebaseDistance, float rebaseSpeed) {
        super(DEFAULT_PRIORITY);
        this.physics = physics;
        this.rebaseDistance = rebaseDistance;
        this.rebaseSpeed = rebaseSpeed;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerControllerComponent.class).one(
                PhysicsBodyComponent.class, TransformComponent.class).get());
        transforms = engine.getEntitiesFor(Family.all(TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
        transforms = null;
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) return;
        Entity player = players.first();

        PhysicsBodyComponent body = bodyMapper.get(player);
        if (body != null && body.body != null) {
            PhysicsManager.getPosition(body.body, position);
            PhysicsManager.getVelocity(body.body, velocity);
        } else {
            TransformComponent t = transformMapper.get(player);
            position.set(t.x, t.y);
            velocity.setZero();
        }

        if (position.len2() > rebaseDistance * rebaseDistance || velocity.len2() > rebaseSpeed * rebaseSpeed) {
            rebase(position.x, position.y, velocity.x, velocity.y);
        }
    }

    private void rebase(float dx, float dy, float dvx, float dvy) {
        for (Entity entity : transforms) {
            TransformComponent t = transformMapper.get(entity);
            t.x -= dx;
            t.y -= dy;
        }
        physics.rebaseOrigin(dx, dy, dvx, dvy);
        rebaseCount++;
    }

    public float getRebaseDistance() {
        return rebaseDistance;
    }

    public float getRebaseSpeed() {
        return rebaseSpeed;
    }

    /** How many times the origin has been rebased since this system was created. */
    public int getRebaseCount() {
        return rebaseCount;
    }
}
