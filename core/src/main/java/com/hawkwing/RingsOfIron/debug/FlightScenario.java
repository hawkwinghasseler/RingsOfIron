package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.hawkwing.RingsOfIron.game.FlightField;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * What the flight demo was, in the game: the corvette taken well clear of Highport and the raider,
 * drifting and tumbling among asteroids in the career's orbit, with nothing to slow it. The menu's
 * Fast pass sends a rock across the bubble at 2.5 km/s, and Scatter rocks puts the ship back in a
 * fresh field. The rocks overlay draws the asteroids (the game has no rock art); it, the physics grid
 * and the velocity overlay start on. The flying, warp and views are the game's own.
 */
final class FlightScenario implements DebugScenario {
    /** How far the ship is moved from the career's start, meters: clear of the station and the raider. */
    static final float AWAY = 10_000f;

    /** The overlay that draws the rocks. */
    static final String ROCKS = "rocks";
    private static final Color STONE = new Color(0.72f, 0.66f, 0.58f, 1f);

    FlightField field;
    private GameWorld world;
    private final Vector2 tmp = new Vector2(), vertex = new Vector2();

    @Override
    public void setUp(ScenarioSetup setup) {
        world = setup.world;
        Body body = world.docking.bodyOf(setup.game.player);
        Vector2 at = PhysicsManager.getPosition(body, new Vector2());
        PhysicsManager.setPosition(body, at.x + AWAY, at.y, body.getAngle());
        field = new FlightField(world.engine, world.physics, world.docking, setup.game.player, setup::notice);
        field.scatter();
        setup.overlay(ROCKS, "the scenario's asteroids, outlined in stone: the game has no rock art, and the light map hides"
                + " bare outlines", new DebugOverlay() {
                    @Override
                    public void drawWorld(OverlayView v) {
                        drawRocks(v);
                    }
                }, true);
        setup.overlays.setShown(BuiltInOverlays.GRID, true);
        setup.overlays.setShown(BuiltInOverlays.VELOCITY, true);
        setup.action("Fast pass", "a rock across the bubble at 2.5 km/s, passing within a few meters of the ship",
                field::spawnFastPass);
        setup.action("Scatter rocks", "a fresh asteroid field around the ship, which drifts and tumbles again",
                field::scatter);
        setup.exteriorView();
        setup.notice("Flight: adrift among asteroids, nothing to slow you. F3 for fast passes and overlays");
    }

    @Override
    public void update(float delta) {
        field.update(delta * world.timeWarp.getWarp());
    }

    private void drawRocks(OverlayView v) {
        v.shapes.begin(ShapeRenderer.ShapeType.Line);
        v.shapes.setColor(STONE);
        for (Entity rock : field.asteroids) {
            PhysicsBodyComponent pb = rock.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null) continue;
            Body body = pb.body;
            Vector2 at = PhysicsManager.getPosition(body, tmp);
            float cos = MathUtils.cos(body.getAngle()), sin = MathUtils.sin(body.getAngle());
            for (Fixture f : body.getFixtureList()) {
                if (f.getShape() instanceof CircleShape) {
                    v.shapes.circle(at.x, at.y, f.getShape().getRadius() * PhysicsManager.METERS_PER_UNIT, 20);
                } else if (f.getShape() instanceof PolygonShape) {
                    PolygonShape poly = (PolygonShape) f.getShape();
                    int n = poly.getVertexCount();
                    float[] xy = new float[n * 2];
                    for (int i = 0; i < n; i++) {
                        poly.getVertex(i, vertex);
                        float lx = vertex.x * PhysicsManager.METERS_PER_UNIT, ly = vertex.y * PhysicsManager.METERS_PER_UNIT;
                        xy[i * 2] = at.x + cos * lx - sin * ly;
                        xy[i * 2 + 1] = at.y + sin * lx + cos * ly;
                    }
                    v.shapes.polygon(xy);
                }
            }
        }
        v.shapes.end();
    }
}
