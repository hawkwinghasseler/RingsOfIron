package com.ringsofiron.physicsexterior.modding;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.GeometryUtils;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.moddingdata.ComponentAssembler;
import com.ringsofiron.moddingdata.DefEntry;
import com.ringsofiron.moddingdata.EntityFactory;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Gives ships spawned from {@code [ship.*]} data a Box2D body and engines, so they fly:
 * <pre>
 * new ModdingBootstrap(assets, mods)
 *         .beforeScripts(ctx -&gt; ShipPhysicsAssembler.register(ctx, physics))
 *         .load(gameEngine);
 * </pre>
 * The body uses the def's {@code hull} polygon (3 to 8 convex vertices, counter-clockwise, local
 * meters), with density chosen so the body weighs the def's {@code mass}. A ship with no usable
 * hull gets a circle of {@link #FALLBACK_RADIUS}. It is placed at the entity's transform
 * (rotation in degrees). {@code max_thrust} drives the main engine and {@code max_torque} the RCS;
 * retro and lateral thrust are fixed fractions of the main engine until defs carry them.
 *
 * The body is created in the world at build time; an entity built but never added to the engine
 * should have its body destroyed by the caller.
 *
 * This class is the only part of physics-exterior that touches modding-data, which is a
 * compile-only dependency: the game, which has both, is what calls it.
 */
public class ShipPhysicsAssembler implements ComponentAssembler {
    public static final float REVERSE_THRUST_FRACTION = 0.4f;
    public static final float LATERAL_THRUST_FRACTION = 0.25f;
    public static final float FALLBACK_RADIUS = 5f;

    private final PhysicsManager physics;

    public ShipPhysicsAssembler(PhysicsManager physics) {
        this.physics = physics;
    }

    /** Registers the assembler for the "ship" def type. */
    public static ShipPhysicsAssembler register(ModContext ctx, PhysicsManager physics) {
        return register(ctx.factory, physics);
    }

    public static ShipPhysicsAssembler register(EntityFactory factory, PhysicsManager physics) {
        ShipPhysicsAssembler assembler = new ShipPhysicsAssembler(physics);
        factory.registerAssembler("ship", assembler);
        return assembler;
    }

    @Override
    public void assemble(Entity entity, DefEntry def, EntityFactory factory) {
        ShipDef ship = factory.defs().convert(def, ShipDef.class);
        TransformComponent t = entity.getComponent(TransformComponent.class);
        float x = t == null ? 0f : t.x;
        float y = t == null ? 0f : t.y;
        float angle = t == null ? 0f : t.rotation * MathUtils.degreesToRadians;

        float[] hull = flatten(ship.hull);
        Body body;
        if (hull != null) {
            float area = Math.abs(GeometryUtils.polygonArea(hull, 0, hull.length));
            body = physics.createPolygonBody(entity, x, y, angle, hull, ship.mass / area,
                    CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        } else {
            float area = MathUtils.PI * FALLBACK_RADIUS * FALLBACK_RADIUS;
            body = physics.createCircleBody(entity, x, y, FALLBACK_RADIUS, ship.mass / area,
                    CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
            PhysicsManager.setPosition(body, x, y, angle);
        }
        entity.add(new PhysicsBodyComponent(body));

        entity.add(new ThrusterComponent(ship.maxThrust, ship.maxThrust * REVERSE_THRUST_FRACTION,
                ship.maxThrust * LATERAL_THRUST_FRACTION, ship.maxTorque));
        entity.add(new FlightControlComponent());
    }

    /** The hull as {x0, y0, x1, y1, ...}, or null if it cannot be a single Box2D polygon. */
    private static float[] flatten(float[][] hull) {
        if (hull == null || hull.length < 3 || hull.length > 8) return null;
        float[] flat = new float[hull.length * 2];
        for (int i = 0; i < hull.length; i++) {
            if (hull[i] == null || hull[i].length < 2) return null;
            flat[i * 2] = hull[i][0];
            flat[i * 2 + 1] = hull[i][1];
        }
        return GeometryUtils.polygonArea(flat, 0, flat.length) > 0f ? flat : null;
    }
}
