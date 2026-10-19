package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.ringsofiron.interiorgrid.HullShape;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;

/**
 * Bodies for stations in the bubble: physics-exterior's {@link KinematicTransientSystem} asks this
 * factory for each transient, and a station (an entity with a {@link StationComponent} and a grid)
 * gets a kinematic body shaped like its grid, which the system then moves along the station's orbit.
 * Collars are added by the {@link DockingSystem}. Everything else stays a swept fast pass.
 */
public final class StationBodies implements KinematicTransientSystem.BodyFactory {
    private static final ComponentMapper<StationComponent> STATIONS = ComponentMapper.getFor(StationComponent.class);
    private static final ComponentMapper<ShipGridComponent> GRIDS = ComponentMapper.getFor(ShipGridComponent.class);

    private final PhysicsManager physics;

    public StationBodies(PhysicsManager physics) {
        this.physics = physics;
    }

    @Override
    public Body build(Entity entity, KinematicTransientComponent transientBody) {
        StationComponent station = STATIONS.get(entity);
        ShipGridComponent grid = GRIDS.get(entity);
        if (station == null || grid == null) return null;
        transientBody.angle = station.angle;
        transientBody.radius = Math.max(grid.width, grid.height) * grid.tileSize * 0.5f;
        return build(physics, entity, grid);
    }

    /**
     * A kinematic body covering every tile of {@code grid} that is not void, as the few convex
     * pieces {@link HullShape} makes of it: boxes, and a triangle for each wedge.
     */
    public static Body build(PhysicsManager physics, Entity owner, ShipGridComponent grid) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.KinematicBody;
        def.allowSleep = false;
        Body body = physics.getWorld().createBody(def);
        body.setUserData(owner);
        for (float[] piece : HullShape.pieces(grid)) addPiece(body, piece);
        return body;
    }

    private static void addPiece(Body body, float[] piece) {
        float[] scaled = new float[piece.length];
        for (int i = 0; i < piece.length; i++) scaled[i] = piece[i] * PhysicsManager.UNITS_PER_METER;
        PolygonShape shape = new PolygonShape();
        shape.set(PolygonSkin.inset(scaled, PolygonSkin.SKIN_UNITS));
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = 1f;
        fd.friction = 0.4f;
        fd.restitution = 0.1f;
        fd.filter.categoryBits = CollisionCategories.CATEGORY_STATION;
        fd.filter.maskBits = CollisionCategories.MASK_ALL;
        body.createFixture(fd);
        shape.dispose();
    }
}
