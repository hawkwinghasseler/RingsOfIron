package com.ringsofiron.stationsdocking;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;

/**
 * The collars on the hull for {@link DockingSystem}: a fixture for each intact port, half a tile
 * deep past the hull, so collars meet before hulls do; torn ones come off.
 */
final class HullCollars {
    private static final float U = PhysicsManager.UNITS_PER_METER;
    private static final float M = PhysicsManager.METERS_PER_UNIT;

    private final Array<Fixture> fixtureBuffer = new Array<>();
    private final MassData massCopy = new MassData();
    private final Vector2 v0 = new Vector2();

    // ------------------------------------------------------------------
    // Collars on the hull
    // ------------------------------------------------------------------

    /** Gives every intact port its collar fixture and takes torn ones off. Cheap when nothing changed. */
    void syncCollars(Body body, DockingPortsComponent ports) {
        int intact = 0;
        for (DockingPort p : ports.ports) if (p.intact()) intact++;
        int collars = 0;
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0) collars++;
        }
        if (collars == intact && !anyTornWithCollar(body, ports, collars)) return;
        boolean[] has = new boolean[ports.ports.length];
        fixtureBuffer.clear();
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) == 0) continue;
            int k = collarPort(f, ports);
            if (k >= 0 && ports.ports[k].intact() && !has[k]) has[k] = true;
            else fixtureBuffer.add(f);
        }
        for (Fixture f : fixtureBuffer) destroyFixtureKeepingMass(body, f);
        fixtureBuffer.clear();
        for (int k = 0; k < ports.ports.length; k++) {
            if (ports.ports[k].intact() && !has[k]) addCollar(body, ports.ports[k]);
        }
    }

    private boolean anyTornWithCollar(Body body, DockingPortsComponent ports, int collars) {
        if (collars == 0) return false;
        for (DockingPort p : ports.ports) if (!p.intact()) return true;
        return false;
    }

    /** The port whose collar this fixture is (its middle sits on the collar's middle), or -1. */
    private int collarPort(Fixture f, DockingPortsComponent ports) {
        if (!(f.getShape() instanceof PolygonShape)) return -1;
        PolygonShape poly = (PolygonShape) f.getShape();
        float cx = 0f, cy = 0f;
        int n = poly.getVertexCount();
        for (int i = 0; i < n; i++) {
            poly.getVertex(i, v0);
            cx += v0.x;
            cy += v0.y;
        }
        cx = cx / n * M;
        cy = cy / n * M;
        for (int k = 0; k < ports.ports.length; k++) {
            DockingPort p = ports.ports[k];
            float mx = p.edgeX + p.normalX * p.collarDepth * 0.5f, my = p.edgeY + p.normalY * p.collarDepth * 0.5f;
            if (Vector2.dst(cx, cy, mx, my) < p.collarDepth * 0.5f) return k;
        }
        return -1;
    }

    /** Adds the collar: half a tile deep past the hull, the port's width, no mass of its own. */
    static Fixture addCollar(Body body, DockingPort p) {
        PolygonShape shape = new PolygonShape();
        float d = p.collarDepth;
        PolygonSkin.insetBox(shape, p.width * 0.5f * U, d * 0.5f * U,
                new Vector2((p.edgeX + p.normalX * d * 0.5f) * U, (p.edgeY + p.normalY * d * 0.5f) * U),
                MathUtils.atan2(p.normalX, -p.normalY));
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        // Density 0 leaves the craft's mass (often set from its grid) alone.
        fd.density = 0f;
        fd.friction = DockingTuning.COLLAR_FRICTION;
        fd.restitution = 0f;
        fd.filter.categoryBits = (short) (ownerCategory(body) | CollisionCategories.CATEGORY_COLLAR);
        fd.filter.maskBits = CollisionCategories.MASK_ALL;
        Fixture f = body.createFixture(fd);
        shape.dispose();
        return f;
    }

    void removeCollar(Body body, DockingPort p) {
        DockingPortsComponent dummy = new DockingPortsComponent(p);
        fixtureBuffer.clear();
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0 && collarPort(f, dummy) == 0) {
                fixtureBuffer.add(f);
            }
        }
        for (Fixture f : fixtureBuffer) destroyFixtureKeepingMass(body, f);
        fixtureBuffer.clear();
    }

    /** Box2D recomputes mass from densities when a fixture goes; put back what the craft had. */
    private void destroyFixtureKeepingMass(Body body, Fixture f) {
        MassData m = body.getMassData();
        massCopy.mass = m.mass;
        massCopy.center.set(m.center);
        massCopy.I = m.I;
        body.destroyFixture(f);
        if (body.getType() == BodyDef.BodyType.DynamicBody) body.setMassData(massCopy);
    }

    /** The hull's collision category: the first fixture that is not a collar. */
    static short ownerCategory(Body body) {
        for (Fixture f : body.getFixtureList()) {
            short c = f.getFilterData().categoryBits;
            if ((c & CollisionCategories.CATEGORY_COLLAR) == 0) return c;
        }
        return CollisionCategories.CATEGORY_SHIP;
    }
}
