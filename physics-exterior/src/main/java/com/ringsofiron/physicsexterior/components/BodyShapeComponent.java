package com.ringsofiron.physicsexterior.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * Everything needed to rebuild a Box2D body that left the bubble: its fixtures and mass, in SI
 * units. Plain fields, so it is saved with the entity while the body is on rails.
 *
 * Captured by {@link #capture} when a body goes onto rails, used by {@link #build} when it comes
 * back. Mass, center of mass and inertia are restored exactly, including values set with
 * {@link PhysicsManager#setMassData} rather than from fixture densities.
 */
public class BodyShapeComponent implements Component {
    /** One fixture: a circle ({@link #radius} > 0, center {@link #vertices}[0..1]) or a convex polygon. */
    public static class FixtureShape {
        public float[] vertices = new float[0];
        public float radius;
        /** kg per square meter. */
        public float density;
        public float friction;
        public float restitution;
        public short category;
        public short mask;
        public boolean sensor;
    }

    public FixtureShape[] fixtures = new FixtureShape[0];
    public float mass;
    public float centerX, centerY;
    /** About the center of mass, kg*m^2. */
    public float inertia;
    public boolean bullet;

    /** Records a body's fixtures and mass, in meters and kilograms. */
    public static BodyShapeComponent capture(Body body) {
        BodyShapeComponent out = new BodyShapeComponent();
        out.fixtures = new FixtureShape[body.getFixtureList().size];
        Vector2 v = new Vector2();
        for (int i = 0; i < out.fixtures.length; i++) {
            Fixture fixture = body.getFixtureList().get(i);
            FixtureShape f = new FixtureShape();
            Shape shape = fixture.getShape();
            if (shape instanceof CircleShape) {
                CircleShape circle = (CircleShape) shape;
                f.radius = circle.getRadius() * PhysicsManager.METERS_PER_UNIT;
                Vector2 c = circle.getPosition();
                f.vertices = new float[]{c.x * PhysicsManager.METERS_PER_UNIT, c.y * PhysicsManager.METERS_PER_UNIT};
            } else if (shape instanceof PolygonShape) {
                PolygonShape poly = (PolygonShape) shape;
                f.vertices = new float[poly.getVertexCount() * 2];
                for (int k = 0; k < poly.getVertexCount(); k++) {
                    poly.getVertex(k, v);
                    f.vertices[k * 2] = v.x * PhysicsManager.METERS_PER_UNIT;
                    f.vertices[k * 2 + 1] = v.y * PhysicsManager.METERS_PER_UNIT;
                }
            }
            f.density = fixture.getDensity() * PhysicsManager.UNITS_PER_METER * PhysicsManager.UNITS_PER_METER;
            f.friction = fixture.getFriction();
            f.restitution = fixture.getRestitution();
            f.category = fixture.getFilterData().categoryBits;
            f.mask = fixture.getFilterData().maskBits;
            f.sensor = fixture.isSensor();
            out.fixtures[i] = f;
        }
        out.mass = PhysicsManager.getMass(body);
        out.centerX = body.getLocalCenter().x * PhysicsManager.METERS_PER_UNIT;
        out.centerY = body.getLocalCenter().y * PhysicsManager.METERS_PER_UNIT;
        out.inertia = PhysicsManager.getInertia(body);
        out.bullet = body.isBullet();
        return out;
    }

    /** Creates the body again at a local position (meters) and angle, owned by {@code owner}. Never sleeps, no damping. */
    public Body build(PhysicsManager physics, Entity owner, float x, float y, float angleRad) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER);
        def.angle = angleRad;
        def.allowSleep = false;
        def.bullet = bullet;
        Body body = physics.getWorld().createBody(def);
        for (FixtureShape f : fixtures) {
            Shape shape;
            if (f.radius > 0f) {
                CircleShape circle = new CircleShape();
                circle.setRadius(f.radius * PhysicsManager.UNITS_PER_METER);
                circle.setPosition(new Vector2(f.vertices[0], f.vertices[1]).scl(PhysicsManager.UNITS_PER_METER));
                shape = circle;
            } else {
                float[] scaled = new float[f.vertices.length];
                for (int i = 0; i < scaled.length; i++) scaled[i] = f.vertices[i] * PhysicsManager.UNITS_PER_METER;
                PolygonShape poly = new PolygonShape();
                poly.set(scaled);
                shape = poly;
            }
            FixtureDef fd = new FixtureDef();
            fd.shape = shape;
            fd.density = f.density * PhysicsManager.METERS_PER_UNIT * PhysicsManager.METERS_PER_UNIT;
            fd.friction = f.friction;
            fd.restitution = f.restitution;
            fd.filter.categoryBits = f.category;
            fd.filter.maskBits = f.mask;
            fd.isSensor = f.sensor;
            body.createFixture(fd);
            shape.dispose();
        }
        PhysicsManager.setMassData(body, mass, centerX, centerY, inertia);
        body.setUserData(owner);
        return body;
    }
}
