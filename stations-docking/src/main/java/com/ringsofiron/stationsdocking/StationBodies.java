package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
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
     * A kinematic body covering every tile of {@code grid} that is not void, as a few boxes: runs of
     * solid tiles along each row, merged with the identical runs of the rows above them.
     */
    public static Body build(PhysicsManager physics, Entity owner, ShipGridComponent grid) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.KinematicBody;
        def.allowSleep = false;
        Body body = physics.getWorld().createBody(def);
        body.setUserData(owner);

        int w = grid.width, h = grid.height;
        boolean[][] used = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (used[y][x] || grid.getTile(x, y).isVoid()) {
                    x++;
                    continue;
                }
                int x1 = x;
                while (x1 < w && !used[y][x1] && !grid.getTile(x1, y).isVoid()) x1++;
                int y1 = y + 1;
                while (y1 < h && runIsSolid(grid, used, x, x1, y1)) y1++;
                for (int yy = y; yy < y1; yy++) for (int xx = x; xx < x1; xx++) used[yy][xx] = true;
                addBox(body, grid, x, y, x1, y1);
                x = x1;
            }
        }
        return body;
    }

    /** True if tiles {@code x0..x1-1} of row {@code y} are all solid and unused, and the run ends there. */
    private static boolean runIsSolid(ShipGridComponent grid, boolean[][] used, int x0, int x1, int y) {
        for (int x = x0; x < x1; x++) {
            if (used[y][x] || grid.getTile(x, y).isVoid()) return false;
        }
        // Only merge runs of the same span, so the row's own run is not cut short
        boolean leftOpen = x0 == 0 || used[y][x0 - 1] || grid.getTile(x0 - 1, y).isVoid();
        boolean rightOpen = x1 == grid.width || used[y][x1] || grid.getTile(x1, y).isVoid();
        return leftOpen && rightOpen;
    }

    private static void addBox(Body body, ShipGridComponent grid, int x0, int y0, int x1, int y1) {
        float s = grid.tileSize;
        float hx = (x1 - x0) * s * 0.5f, hy = (y1 - y0) * s * 0.5f;
        float cx = grid.gridOriginLocal.x + x0 * s + hx, cy = grid.gridOriginLocal.y + y0 * s + hy;
        PolygonShape shape = new PolygonShape();
        float u = PhysicsManager.UNITS_PER_METER;
        shape.setAsBox(hx * u, hy * u, new Vector2(cx * u, cy * u), 0f);
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
