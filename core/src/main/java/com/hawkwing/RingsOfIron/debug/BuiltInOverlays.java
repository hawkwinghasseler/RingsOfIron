package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;

/** The overlays every game screen has: the physics frame, velocities, Box2D's own shapes and a frame readout. */
public final class BuiltInOverlays implements Disposable {
    public static final String GRID = "grid", VELOCITY = "velocity", BODIES = "bodies", STATS = "stats";

    private final Array<Body> bodies = new Array<>();
    private final Vector2 tmp = new Vector2();
    private Box2DDebugRenderer box2d;

    /** Adds them to {@code overlays}, all off. */
    public void install(DebugOverlays overlays) {
        overlays.add(GRID, "the physics bubble's own frame: Box2D meters, which moves with the bubble's origin",
                new DebugOverlay() {
                    @Override
                    public void drawWorld(OverlayView v) {
                        drawGrid(v);
                    }
                }, false);
        overlays.add(VELOCITY, "each moving body's velocity in the bubble frame, as far as it goes in 5 s",
                new DebugOverlay() {
                    @Override
                    public void drawWorld(OverlayView v) {
                        drawVelocities(v);
                    }
                }, false);
        overlays.add(BODIES, "Box2D's own drawing of every fixture and joint", new DebugOverlay() {
            @Override
            public void drawWorld(OverlayView v) {
                if (box2d == null) box2d = new Box2DDebugRenderer(true, true, false, true, false, true);
                box2d.render(v.world.physics.getWorld(), v.worldMatrix);
            }
        }, false);
        overlays.add(STATS, "frame rate, entities, bodies, the clock and the bubble's origin", new DebugOverlay() {
            @Override
            public void drawScreen(OverlayView v) {
                drawStats(v);
            }
        }, false);
    }

    /** Lines every 10^n meters, so that a few dozen cross the screen, the axes brighter. */
    private void drawGrid(OverlayView v) {
        float across = Math.max(v.worldBounds.width, v.worldBounds.height);
        float step = (float) Math.pow(10, Math.floor(Math.log10(Math.max(across, 1e-3f) / 4f)));
        float x0 = (float) Math.floor(v.worldBounds.x / step) * step;
        float y0 = (float) Math.floor(v.worldBounds.y / step) * step;
        float x1 = v.worldBounds.x + v.worldBounds.width, y1 = v.worldBounds.y + v.worldBounds.height;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        v.shapes.begin(ShapeRenderer.ShapeType.Line);
        float fine = step / 10f;
        // Tenths of a step faintly, once they are a few pixels apart
        if (fine / v.metersPerPixel > 6f) {
            v.shapes.setColor(0.3f, 0.6f, 0.8f, 0.08f);
            for (float x = x0; x <= x1; x += fine) v.shapes.line(x, v.worldBounds.y, x, y1);
            for (float y = y0; y <= y1; y += fine) v.shapes.line(v.worldBounds.x, y, x1, y);
        }
        v.shapes.setColor(0.3f, 0.6f, 0.8f, 0.25f);
        for (float x = x0; x <= x1; x += step) v.shapes.line(x, v.worldBounds.y, x, y1);
        for (float y = y0; y <= y1; y += step) v.shapes.line(v.worldBounds.x, y, x1, y);
        v.shapes.setColor(0.4f, 0.9f, 1f, 0.6f);
        v.shapes.line(0f, v.worldBounds.y, 0f, y1);
        v.shapes.line(v.worldBounds.x, 0f, x1, 0f);
        v.shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        lastGridStep = step;
    }

    private float lastGridStep;

    private void drawVelocities(OverlayView v) {
        v.world.physics.getWorld().getBodies(bodies);
        v.shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body b : bodies) {
            if (b.getType() == BodyDef.BodyType.StaticBody) continue;
            Vector2 p = b.getPosition(), vel = b.getLinearVelocity();
            float speed = vel.len();
            if (speed < 0.01f) continue;
            // Green when slow, red from 100 m/s
            float hot = MathUtils.clamp(speed / 100f, 0f, 1f);
            v.shapes.setColor(hot, 1f - hot * 0.7f, 0.2f, 1f);
            float ex = p.x + vel.x * 5f, ey = p.y + vel.y * 5f;
            v.shapes.line(p.x, p.y, ex, ey);
            // An arrowhead a few pixels long
            float head = 8f * v.metersPerPixel, a = MathUtils.atan2(vel.y, vel.x);
            v.shapes.line(ex, ey, ex - head * MathUtils.cos(a - 0.4f), ey - head * MathUtils.sin(a - 0.4f));
            v.shapes.line(ex, ey, ex - head * MathUtils.cos(a + 0.4f), ey - head * MathUtils.sin(a + 0.4f));
        }
        v.shapes.end();
    }

    private void drawStats(OverlayView v) {
        PhysicsManager physics = v.world.physics;
        TimeWarpController warp = v.world.timeWarp;
        Entity player = v.world.getPlayer();
        Body pb = player == null ? null : v.world.docking.bodyOf(player);
        StringBuilder s = new StringBuilder();
        s.append(Gdx.graphics.getFramesPerSecond()).append(" fps   ")
                .append(String.format("%.1f ms", v.delta * 1000f)).append('\n');
        s.append(v.world.engine.getEcsEngine().getEntities().size()).append(" entities   ")
                .append(physics.getWorld().getBodyCount()).append(" bodies   tick ").append(physics.getTick()).append('\n');
        s.append(String.format("sim %.1f s   warp %dx%s", v.world.engine.getSimSeconds(), warp.getWarp(),
                warp.isRailsWarp() ? " (rails)" : "")).append('\n');
        s.append(String.format("bubble origin %.0f, %.0f km   %.0f, %.0f m/s", physics.getOriginX() / 1000.0,
                physics.getOriginY() / 1000.0, physics.getOriginVx(), physics.getOriginVy()));
        String parent = physics.getAnchorParentUuid();
        if (parent != null) s.append("   around ").append(parent);
        s.append('\n');
        if (pb != null) {
            s.append(String.format("player at %.1f, %.1f m   %.2f m/s   %.1f deg/s", pb.getPosition().x, pb.getPosition().y,
                    pb.getLinearVelocity().len(), pb.getAngularVelocity() * MathUtils.radiansToDegrees)).append('\n');
        }
        if (lastGridStep > 0f) s.append(String.format("grid %s m", trim(lastGridStep))).append('\n');
        v.batch.begin();
        v.font.setColor(Color.WHITE);
        v.font.draw(v.batch, s, 10f, v.uiHeight * 0.5f);
        v.batch.end();
    }

    private static String trim(float meters) {
        return meters >= 1f ? String.valueOf(Math.round(meters)) : String.valueOf(meters);
    }

    @Override
    public void dispose() {
        if (box2d != null) box2d.dispose();
        box2d = null;
    }
}
