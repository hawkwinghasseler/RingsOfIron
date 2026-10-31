package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * A ship's place and motion in the physics world's frame, meters and radians; the identity at
 * rest for a ship with no body.
 */
final class ShipFrame {
    float x, y, angle, cos = 1f, sin, cx, cy, vx, vy, omega, ox, oy, tile = 1f;
    Body body;

    void read(Entity ship, ShipGridComponent grid) {
        ox = grid.gridOriginLocal.x;
        oy = grid.gridOriginLocal.y;
        tile = grid.tileSize;
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        body = pb == null ? null : pb.body;
        if (body == null) {
            x = y = angle = sin = cx = cy = vx = vy = omega = 0f;
            cos = 1f;
            return;
        }
        Vector2 p = body.getPosition();
        x = p.x * PhysicsManager.METERS_PER_UNIT;
        y = p.y * PhysicsManager.METERS_PER_UNIT;
        angle = body.getAngle();
        cos = (float) Math.cos(angle);
        sin = (float) Math.sin(angle);
        Vector2 c = body.getWorldCenter();
        cx = c.x * PhysicsManager.METERS_PER_UNIT;
        cy = c.y * PhysicsManager.METERS_PER_UNIT;
        Vector2 v = body.getLinearVelocity();
        vx = v.x * PhysicsManager.METERS_PER_UNIT;
        vy = v.y * PhysicsManager.METERS_PER_UNIT;
        omega = body.getAngularVelocity();
    }

    /** Tile point (tx, ty) in the world, meters: {@code out = {x, y}}. */
    void toWorld(float tx, float ty, float[] out) {
        float lx = ox + tx * tile, ly = oy + ty * tile;
        out[0] = x + cos * lx - sin * ly;
        out[1] = y + sin * lx + cos * ly;
    }

    /** World point (wx, wy), meters, in tiles: {@code out = {x, y}}. */
    void toTiles(float wx, float wy, float[] out) {
        float dx = wx - x, dy = wy - y;
        float lx = cos * dx + sin * dy, ly = -sin * dx + cos * dy;
        out[0] = (lx - ox) / tile;
        out[1] = (ly - oy) / tile;
    }

    /** The ship's velocity at world point (wx, wy), m/s: {@code out = {vx, vy}}. */
    void velocityAt(float wx, float wy, float[] out) {
        out[0] = vx - omega * (wy - cy);
        out[1] = vy + omega * (wx - cx);
    }

    /** A ship-frame vector in tiles as a world one in meters. */
    void vecToWorld(float tx, float ty, float[] out) {
        out[0] = (cos * tx - sin * ty) * tile;
        out[1] = (sin * tx + cos * ty) * tile;
    }

    /** A world vector in meters as a ship-frame one in tiles. */
    void vecToTiles(float wx, float wy, float[] out) {
        out[0] = (cos * wx + sin * wy) / tile;
        out[1] = (-sin * wx + cos * wy) / tile;
    }
}
