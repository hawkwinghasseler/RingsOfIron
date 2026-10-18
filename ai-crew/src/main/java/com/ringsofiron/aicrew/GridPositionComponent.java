package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Where a crew member is on their ship's interior grid, in tile units: the centre of
 * tile (i, j) is (i + 0.5, j + 0.5). The grid is in the ship body's local frame, so
 * {@link #toShipLocal} gives body-local meters.
 *
 * Also holds the path the crew member is walking, set by the behavior tree and
 * followed by {@link CrewMovementSystem}.
 */
public class GridPositionComponent implements Component {

    /** The ship entity (holding the ShipGridComponent) this crew member is aboard. */
    public Entity ship;
    public float x, y;

    /** Tiles to walk through, in order; empty when standing still. */
    public final Array<GridNode> path = new Array<>();
    /** Index in {@link #path} of the tile being walked to. */
    public int pathIndex;
    /** Set by the movement system when the next step became impassable (door shut, wall rebuilt). */
    public boolean pathBlocked;

    public GridPositionComponent() {}

    public GridPositionComponent(Entity ship, int tileX, int tileY) {
        this.ship = ship;
        setTile(tileX, tileY);
    }

    public int tileX() {
        return MathUtils.floor(x);
    }

    public int tileY() {
        return MathUtils.floor(y);
    }

    public void setTile(int tileX, int tileY) {
        x = tileX + 0.5f;
        y = tileY + 0.5f;
    }

    public boolean isMoving() {
        return pathIndex < path.size;
    }

    public void setPath(Array<GridNode> nodes) {
        path.clear();
        path.addAll(nodes);
        // Skip the start tile if we are already standing on it
        pathIndex = (path.size > 0 && path.first().x == tileX() && path.first().y == tileY()) ? 1 : 0;
        pathBlocked = false;
    }

    public void stop() {
        path.clear();
        pathIndex = 0;
    }

    /** The final tile of the current path, or null. */
    public GridNode destination() {
        return path.size == 0 ? null : path.peek();
    }

    /** Body-local position in meters. */
    public Vector2 toShipLocal(ShipGridComponent grid, Vector2 out) {
        return out.set(grid.gridOriginLocal.x + x * grid.tileSize, grid.gridOriginLocal.y + y * grid.tileSize);
    }
}
