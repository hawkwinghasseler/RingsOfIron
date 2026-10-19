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

import java.util.Collections;
import java.util.List;

/**
 * Gives ships spawned from {@code [ship.*]} data a Box2D body and engines, so they fly:
 * <pre>
 * new ModdingBootstrap(assets, mods)
 *         .beforeScripts(ctx -&gt; ShipPhysicsAssembler.register(ctx, physics))
 *         .load(gameEngine);
 * </pre>
 * The body's shape comes from a {@link HullSource} when one is given and has a shape for the ship
 * (the game's makes it from the ship's layout, or from its hull art's collision layer), else from
 * the def's {@code hull} polygon (3 to 8 convex vertices, counter-clockwise, local meters). Density
 * is chosen so the body weighs the def's {@code mass}. A ship with neither gets a circle of
 * {@link #FALLBACK_RADIUS}. It is placed at the entity's transform
 * (rotation in degrees). {@code max_thrust} drives the main engine and {@code max_torque} the RCS;
 * retro and lateral thrust are fixed fractions of the main engine until defs carry them.
 * {@code exhaust_velocity} goes on the thrusters for the game's propellant feed; thrust here is
 * free until something feeds it.
 *
 * A {@link MassSource} can replace the def's mass afterwards, e.g. with mass properties from the
 * ship's interior grid. The game wires that and the hull source, so this module does not depend on
 * interior-grid; the grid's own assembler must then run first (register this one after it).
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
    /** Exhaust velocity of a ship whose def gives none, m/s. */
    public static final float STANDARD_EXHAUST_VELOCITY = 1.0e5f;

    /** Supplies a ship's real mass properties, overriding the def's {@code mass}. */
    @FunctionalInterface
    public interface MassSource {
        /**
         * Writes {mass kg, center x m, center y m, inertia about the center kg*m^2} into
         * {@code out} and returns true, or returns false to keep the def's mass.
         */
        boolean massFor(Entity entity, DefEntry def, float[] out);
    }

    /** Supplies a ship's collision shape, in place of the def's {@code hull}. */
    @FunctionalInterface
    public interface HullSource {
        /**
         * The ship's shape as convex polygons, each {x0, y0, x1, y1, ...} with 3 to 8 vertices,
         * counter-clockwise, in body-local meters; or null (or empty) to use the def's {@code hull}.
         */
        List<float[]> hullFor(Entity entity, DefEntry def);
    }

    private final PhysicsManager physics;
    private final MassSource massSource;
    private final HullSource hullSource;
    private final float[] massOut = new float[4];

    public ShipPhysicsAssembler(PhysicsManager physics) {
        this(physics, null);
    }

    public ShipPhysicsAssembler(PhysicsManager physics, MassSource massSource) {
        this(physics, massSource, null);
    }

    public ShipPhysicsAssembler(PhysicsManager physics, MassSource massSource, HullSource hullSource) {
        this.physics = physics;
        this.massSource = massSource;
        this.hullSource = hullSource;
    }

    /** Registers the assembler for the "ship" def type. */
    public static ShipPhysicsAssembler register(ModContext ctx, PhysicsManager physics) {
        return register(ctx.factory, physics);
    }

    public static ShipPhysicsAssembler register(EntityFactory factory, PhysicsManager physics) {
        return register(factory, physics, null);
    }

    /** Registers the assembler with a mass source, e.g. the interior grid's mass properties. */
    public static ShipPhysicsAssembler register(EntityFactory factory, PhysicsManager physics, MassSource massSource) {
        return register(factory, physics, massSource, null);
    }

    /** Registers the assembler with a mass source and a hull source, e.g. the ship's layout. */
    public static ShipPhysicsAssembler register(EntityFactory factory, PhysicsManager physics, MassSource massSource,
                                                HullSource hullSource) {
        ShipPhysicsAssembler assembler = new ShipPhysicsAssembler(physics, massSource, hullSource);
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

        List<float[]> hull = hullSource == null ? null : hullSource.hullFor(entity, def);
        if (hull == null || hull.isEmpty()) {
            float[] polygon = flatten(ship.hull);
            hull = polygon == null ? null : Collections.singletonList(polygon);
        }
        Body body;
        if (hull != null) {
            float area = 0f;
            for (float[] piece : hull) area += Math.abs(GeometryUtils.polygonArea(piece, 0, piece.length));
            body = physics.createPolygonBody(entity, x, y, angle, hull, ship.mass / area,
                    CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        } else {
            float area = MathUtils.PI * FALLBACK_RADIUS * FALLBACK_RADIUS;
            body = physics.createCircleBody(entity, x, y, FALLBACK_RADIUS, ship.mass / area,
                    CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
            PhysicsManager.setPosition(body, x, y, angle);
        }
        if (massSource != null && massSource.massFor(entity, def, massOut) && massOut[0] > 0f) {
            PhysicsManager.setMassData(body, massOut[0], massOut[1], massOut[2], massOut[3]);
        }
        entity.add(new PhysicsBodyComponent(body));

        ThrusterComponent thrusters = new ThrusterComponent(ship.maxThrust, ship.maxThrust * REVERSE_THRUST_FRACTION,
                ship.maxThrust * LATERAL_THRUST_FRACTION, ship.maxTorque);
        thrusters.exhaustVelocity = ship.exhaustVelocity > 0f ? ship.exhaustVelocity : STANDARD_EXHAUST_VELOCITY;
        entity.add(thrusters);
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
