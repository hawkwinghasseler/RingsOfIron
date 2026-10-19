package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * Flight aids drawn over the outside view, in world meters: an arrow from the ship along the next
 * planned burn, and, closing on a free docking port within {@link PlayerDocking#READOUT_RANGE}, the
 * approach corridor out from that port, green while the closing speed is safe for the distance,
 * amber over it and red well over it.
 */
public class NavOverlay {
    private static final Color BURN = new Color(1f, 0.7f, 0.2f, 1f);
    private static final Color SAFE = new Color(0.35f, 1f, 0.45f, 0.8f);
    private static final Color FAST = new Color(1f, 0.7f, 0.2f, 0.9f);
    private static final Color DANGER = new Color(1f, 0.25f, 0.2f, 1f);

    private final GameWorld world;
    private final PlayerDocking docking;
    private final double[] burn = new double[3];
    private final Vector2 at = new Vector2();

    public NavOverlay(GameWorld world, PlayerDocking docking) {
        this.world = world;
        this.docking = docking;
    }

    /**
     * Draws with {@code shapes}, whose projection is the world camera; {@code px} is meters per
     * pixel, so marks keep their size on screen. Begins and ends its own batch of lines.
     */
    public void draw(ShapeRenderer shapes, float px) {
        Entity ship = world.getPlayer();
        Body body = ship == null ? null : world.docking.bodyOf(ship);
        if (body == null) return;
        PhysicsManager.getPosition(body, at);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (world.nav.nextBurnDirection(burn)) {
            float len = 60f * px;
            float x1 = at.x + (float) burn[0] * len, y1 = at.y + (float) burn[1] * len;
            shapes.setColor(BURN);
            shapes.line(at.x + (float) burn[0] * 20f * px, at.y + (float) burn[1] * 20f * px, x1, y1);
            // The head, and a ring to line the nose up on
            float hx = -(float) burn[0] * 8f * px, hy = -(float) burn[1] * 8f * px;
            shapes.line(x1, y1, x1 + hx - hy * 0.5f, y1 + hy + hx * 0.5f);
            shapes.line(x1, y1, x1 + hx + hy * 0.5f, y1 + hy - hx * 0.5f);
            shapes.circle(x1, y1, 5f * px, 16);
        }
        DockingSystem.Approach a = docking.approach();
        if (a.target != null && a.distance > 1f) corridor(shapes, a, px);
        shapes.end();
    }

    /** Out along the target port's normal past the ship, widening with distance. */
    private void corridor(ShapeRenderer shapes, DockingSystem.Approach a, float px) {
        float safe = PlayerDocking.safeSpeed(a.distance);
        shapes.setColor(a.closing <= safe ? SAFE : a.closing <= 1.5f * safe ? FAST : DANGER);
        float length = Math.max(a.distance * 1.2f, 40f * px);
        float nx = a.normalX, ny = a.normalY;
        float sx = -ny, sy = nx;
        float near = Math.max(1.5f, 2f * px), far = Math.max(near, length * 0.08f);
        float ex = a.faceX + nx * length, ey = a.faceY + ny * length;
        shapes.line(a.faceX + sx * near, a.faceY + sy * near, ex + sx * far, ey + sy * far);
        shapes.line(a.faceX - sx * near, a.faceY - sy * near, ex - sx * far, ey - sy * far);
        // Range marks every tenth of the way out
        for (int i = 1; i <= 10; i++) {
            float d = length * i / 10f, w = near + (far - near) * i / 10f;
            float cx = a.faceX + nx * d, cy = a.faceY + ny * d;
            shapes.line(cx + sx * w, cy + sy * w, cx + sx * w * 0.6f, cy + sy * w * 0.6f);
            shapes.line(cx - sx * w, cy - sy * w, cx - sx * w * 0.6f, cy - sy * w * 0.6f);
        }
    }
}
