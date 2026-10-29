package com.ringsofiron.uihud.systems;

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
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.widgets.NavMarkers;

/**
 * Places the {@link NavMarkers} around the player's ship along its velocity. With no target that is
 * the orbital velocity: the ship's velocity in the bubble plus the bubble's own (the floating
 * origin's), the same velocity the flight readout's speed is. While a target with a body in the
 * bubble is locked it is the velocity relative to that target, and the markers say so. Hidden below
 * {@link #MIN_SPEED}, where the direction means nothing.
 */
public class NavMarkerSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = HudRenderSystem.DEFAULT_PRIORITY + 2;
    public static final float MIN_SPEED = 0.5f;
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class, TransformComponent.class).get();
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final ViewportManager viewports;
    private final NavMarkers markers;
    private final Vector2 v = new Vector2();
    private final Vector2 tv = new Vector2();
    private final Vector2 at = new Vector2();
    private ImmutableArray<Entity> players;
    private PhysicsManager physics;
    private TargetingProvider targeting;

    public NavMarkerSystem(ViewportManager viewports, NavMarkers markers) {
        super(DEFAULT_PRIORITY);
        this.viewports = viewports;
        this.markers = markers;
    }

    public void setPhysics(PhysicsManager physics) {
        this.physics = physics;
    }

    public void setTargeting(TargetingProvider targeting) {
        this.targeting = targeting;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(PLAYER);
    }

    @Override
    public void update(float deltaTime) {
        Entity player = players.size() > 0 ? players.first() : null;
        PhysicsBodyComponent b = player == null ? null : BODIES.get(player);
        if (b == null || b.body == null) {
            markers.hide();
            return;
        }
        PhysicsManager.getVelocity(b.body, v);
        Entity target = relativeTarget(targeting, player);
        float angle;
        if (target != null) {
            // Both velocities are in the bubble, so the bubble's own cancels out
            PhysicsManager.getVelocity(BODIES.get(target).body, tv);
            angle = progradeAngle(v.x - tv.x, v.y - tv.y, 0, 0);
        } else {
            angle = progradeAngle(v.x, v.y, physics == null ? 0 : physics.getOriginVx(),
                    physics == null ? 0 : physics.getOriginVy());
        }
        if (Float.isNaN(angle)) {
            markers.hide();
            return;
        }
        TransformComponent t = TRANSFORMS.get(player);
        viewports.worldToUi(t.x, t.y, at);
        markers.show(at.x, at.y, angle, target != null);
    }

    /**
     * The locked target the markers are relative to, or null for the orbital frame: a target other
     * than the player with a body in the bubble, whose velocity can be read.
     */
    public static Entity relativeTarget(TargetingProvider targeting, Entity player) {
        Entity target = targeting == null ? null : targeting.getLockedTarget();
        if (target == null || target == player) return null;
        PhysicsBodyComponent tb = BODIES.get(target);
        return tb == null || tb.body == null ? null : target;
    }

    /**
     * Direction of travel, radians from +x, for a ship moving at (vx, vy) in a bubble that itself
     * moves at (originVx, originVy); NaN when the total is under {@link #MIN_SPEED}. The view is
     * north up, so world and screen directions agree.
     */
    public static float progradeAngle(float vx, float vy, double originVx, double originVy) {
        double x = vx + originVx, y = vy + originVy;
        if (Math.hypot(x, y) < MIN_SPEED) return Float.NaN;
        return (float) Math.atan2(y, x);
    }
}
