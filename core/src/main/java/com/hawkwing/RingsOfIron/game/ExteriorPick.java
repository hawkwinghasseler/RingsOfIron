package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

/**
 * What a click on the outside view picks: the ship or station under the cursor, the player's own
 * ship aside. A click on a hull picks it; a click near a craft drawn only a few pixels across picks
 * the nearest one within reach of where the HUD marks it. Positions are world meters.
 */
public final class ExteriorPick {
    /** How far from a craft's middle, in screen pixels, a click still picks it. */
    public static final float SLACK_PIXELS = 18f;

    private static final Family MARKED = Family.all(HudMarkerComponent.class, TransformComponent.class).get();

    private final GameWorld world;
    private final Array<Body> bodies = new Array<>();
    private final Vector2 point = new Vector2();
    private final Vector2 middle = new Vector2();
    private Entity best;
    private float bestDistance;

    public ExteriorPick(GameWorld world) {
        this.world = world;
    }

    /**
     * The craft at ({@code x}, {@code y}), or null. {@code metersPerPixel} sets how far off a click
     * may land; {@code self} (the player's ship) is never picked.
     */
    public Entity at(float x, float y, float metersPerPixel, Entity self) {
        float slack = SLACK_PIXELS * metersPerPixel;
        best = null;
        bestDistance = slack;
        // On a hull: as near as half the reach at most, so the hull under the cursor beats a neighbour's
        // middle, but a craft latched to a station (on the station's body) can still be picked by its own
        world.physics.getWorld().getBodies(bodies);
        point.set(x, y).scl(PhysicsManager.UNITS_PER_METER);
        for (Body body : bodies) {
            Entity e = craft(body.getUserData(), self);
            if (e == null || world.docking.bodyOf(e) != body) continue;
            for (Fixture f : body.getFixtureList()) {
                if (f.testPoint(point)) {
                    consider(e, Math.min(0.5f * slack, PhysicsManager.getPosition(body, middle).dst(x, y)));
                    break;
                }
            }
        }
        bodies.clear();
        // Near where the HUD marks it, which also finds a craft latched to a station (on the station's body)
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(MARKED)) {
            if (craft(e, self) == null) continue;
            TransformComponent t = e.getComponent(TransformComponent.class);
            consider(e, Vector2.dst(x, y, t.x, t.y));
        }
        return best;
    }

    private void consider(Entity e, float distance) {
        if (distance > bestDistance || (best != null && distance == bestDistance)) return;
        best = e;
        bestDistance = distance;
    }

    /** {@code owner} as a ship or station a click can pick, or null. */
    private static Entity craft(Object owner, Entity self) {
        if (!(owner instanceof Entity) || owner == self) return null;
        Entity e = (Entity) owner;
        return e.getComponent(StationComponent.class) != null || e.getComponent(ShipGridComponent.class) != null ? e : null;
    }
}
