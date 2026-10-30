package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.shipsystems.RcsThrust;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * Where a craft's RCS blocks and their nozzles are in the world, and how hard each nozzle fires
 * this frame ({@link RcsThrust#firing}): what the exterior art draws its blocks and jets from,
 * and what the light map lights the firing ones with, outside and on deck.
 */
public final class RcsNozzles {
    /** A block on the hull. */
    public interface Blocks {
        /** Block {@code r} with its hull edge's middle at ({@code x}, {@code y}) and its main nozzle firing toward {@code degrees}. */
        void block(RcsThruster r, boolean works, float x, float y, float degrees);
    }

    /** A nozzle that fires this frame. */
    public interface Jets {
        /**
         * A nozzle whose mouth is at ({@code x}, {@code y}), firing toward {@code degrees} at {@code throttle}
         * (0 to 1] scaled by the thrust it gives ({@link RcsThrust#jetSize}): how big its jet is drawn.
         */
        void jet(float x, float y, float degrees, float throttle);
    }

    private final Vector2 center = new Vector2();
    private final Vector2 local = new Vector2();
    /** The pose being drawn at: the craft's frame's origin, meters, and its turn. */
    private float px, py, cos = 1f, sin, degrees;

    /** Each of {@code ship}'s RCS blocks, at {@code body}'s pose. */
    public void blocks(Entity ship, Body body, Blocks out) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null || body == null) return;
        pose(body);
        for (RcsThruster r : grid.rcsThrusters) {
            Vector2 w = world(grid, r.edgeX(), r.edgeY());
            out.block(r, r.works(grid), w.x, w.y, degrees + degrees(r.side));
        }
    }

    /** Each of {@code ship}'s nozzles that fires this frame, at {@code body}'s pose. */
    public void jets(Entity ship, Body body, Jets out) {
        if (body == null) return;
        Vector2 p = PhysicsManager.getPosition(body, local);
        jets(ship, body, p.x, p.y, body.getAngle(), out);
    }

    /**
     * Each of {@code ship}'s nozzles that fires this frame (how hard, from its {@code body}), drawn
     * with the craft's frame at ({@code x}, {@code y}) meters turned {@code radians}: a deck drawn
     * somewhere other than its body.
     */
    public void jets(Entity ship, Body body, float x, float y, float radians, Jets out) {
        each(ship, body, x, y, radians, false, out);
    }

    /**
     * Every nozzle of {@code ship}'s RCS blocks at {@code body}'s pose, always in the same order,
     * with how hard it fires this frame, 0 for one that does not: for what follows each nozzle
     * from frame to frame, as a flame dying down after it stops.
     */
    public void nozzles(Entity ship, Body body, Jets out) {
        if (body == null) return;
        Vector2 p = PhysicsManager.getPosition(body, local);
        each(ship, body, p.x, p.y, body.getAngle(), true, out);
    }

    private void each(Entity ship, Body body, float x, float y, float radians, boolean all, Jets out) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null || body == null || grid.rcsThrusters.size == 0) return;
        center.set(body.getLocalCenter()).scl(PhysicsManager.METERS_PER_UNIT);
        pose(x, y, radians);
        for (RcsThruster r : grid.rcsThrusters) {
            for (Side exhaust : r.nozzles()) {
                float throttle = RcsThrust.firing(ship, r, exhaust, center) * RcsThrust.jetSize(r);
                if (throttle <= 0f && !all) continue;
                Vector2 w = world(grid, r.nozzleX(exhaust), r.nozzleY(exhaust));
                out.jet(w.x, w.y, degrees + degrees(exhaust), throttle);
            }
        }
    }

    private void pose(Body body) {
        Vector2 p = PhysicsManager.getPosition(body, local);
        pose(p.x, p.y, body.getAngle());
    }

    private void pose(float x, float y, float radians) {
        px = x;
        py = y;
        cos = MathUtils.cos(radians);
        sin = MathUtils.sin(radians);
        degrees = radians * MathUtils.radiansToDegrees;
    }

    /** The direction out of {@code side}, degrees in the grid's frame. */
    static float degrees(Side side) {
        return MathUtils.atan2(side.dy, side.dx) * MathUtils.radiansToDegrees;
    }

    /** World meters of ({@code tx}, {@code ty}) tiles from {@code grid}'s corner, at the pose set last. */
    private Vector2 world(ShipGridComponent grid, float tx, float ty) {
        float lx = grid.gridOriginLocal.x + tx * grid.tileSize, ly = grid.gridOriginLocal.y + ty * grid.tileSize;
        return local.set(px + cos * lx - sin * ly, py + sin * lx + cos * ly);
    }
}
