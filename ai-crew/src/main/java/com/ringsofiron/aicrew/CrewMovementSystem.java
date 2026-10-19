package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;

/**
 * Moves crew along their paths every sim tick: interpolates the float position toward the
 * centre of the next tile at the crew member's speed (slower while carrying). Before
 * each step it checks the step is still open; if a door shut or a wall went up it stops
 * the crew member on their tile and sets {@link GridPositionComponent#pathBlocked} so the
 * behavior tree re-plans.
 *
 * Unconscious and dead crew do not move; carried crew ride on their carrier's tile. The
 * player avatar moves from its input instead of a path, and can walk through an open docked
 * port, across the collars between the two craft, onto the other one ({@link GridLink}).
 */
public class CrewMovementSystem extends FixedIteratingSystem {

    private final CrewService service;
    private final ComponentMapper<PlayerAvatarComponent> avatarM = ComponentMapper.getFor(PlayerAvatarComponent.class);

    public CrewMovementSystem(CrewService service) {
        super(CrewService.CREW, 10, (float) SimClock.TICK_SECONDS);
        this.service = service;
    }

    @Override
    protected void updateFixed(float dt) {
        super.updateFixed(dt);
        // Carried crew follow their carriers after everyone has moved
        for (int i = 0; i < getEntities().size(); i++) {
            Entity e = getEntities().get(i);
            CrewMemberComponent c = service.crew(e);
            if (c.carriedBy == null) continue;
            GridPositionComponent p = service.position(e), cp = service.position(c.carriedBy);
            if (p.ship != cp.ship) p.setShip(cp.ship);
            p.x = cp.x;
            p.y = cp.y;
            p.walkway = cp.walkway;
        }
    }

    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = service.crew(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid == null) return;
        // The ship may have been given its UUID after the crew boarded
        if (p.shipRef == null) p.setShip(p.ship);
        keepAboard(p, grid);
        if (!c.isConscious() || c.carriedBy != null) {
            p.stop();
            return;
        }
        GridNavGraph nav = service.pathfinder(grid).graph;
        float speed = c.def.moveSpeed * (c.carrying != null ? c.def.carrySpeedFactor : 1f)
                * (c.load != null ? c.def.loadSpeedFactor : 1f);
        PlayerAvatarComponent avatar = avatarM.get(e);
        if (avatar != null) {
            moveAvatar(p, avatar, nav, speed * dt);
            return;
        }
        float budget = speed * dt;
        while (budget > 0f && p.isMoving()) {
            GridNode next = p.path.get(p.pathIndex);
            int cx = p.tileX(), cy = p.tileY();
            if ((next.x != cx || next.y != cy) && !nav.canStep(cx, cy, next.x, next.y)) {
                // Blocked: settle back on the centre of the current tile and ask for a new route
                p.setTile(cx, cy);
                p.stop();
                p.pathBlocked = true;
                return;
            }
            float tx = next.x + 0.5f, ty = next.y + 0.5f;
            float dx = tx - p.x, dy = ty - p.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= budget) {
                p.x = tx;
                p.y = ty;
                budget -= dist;
                p.pathIndex++;
            } else {
                p.x += dx / dist * budget;
                p.y += dy / dist * budget;
                budget = 0f;
            }
            // Walking back in off the walkway it stopped on
            if (p.walkway != null && p.walkway.past(p.x, p.y) <= 0f) p.walkway = null;
        }
        if (!p.isMoving() && p.path.size > 0) p.path.clear();
        if (!p.isMoving() && p.crossing != null && budget > 0f) crossOver(p, nav, budget);
    }

    /**
     * Walks an AI crew member through the docked link they pathed to: straight out over the edge,
     * across the collars, and in over the far craft's edge, where the crossing ends.
     */
    private void crossOver(GridPositionComponent p, GridNavGraph nav, float step) {
        GridLink link = p.crossing;
        Entity here = link.mate.otherShip;
        if (p.ship == link.otherShip) {
            if (p.walkway == null) {
                p.crossing = null;
                return;
            }
            // On the far side's walkway: in along its facing
            Side in = p.walkway.side;
            walkAcross(p, p.walkway, -in.dx * step, -in.dy * step);
            if (p.walkway == null) p.crossing = null;
        } else if (p.ship == here) {
            Side out = link.side;
            tryMove(p, nav, out.dx * step, out.dy * step);
        } else {
            p.crossing = null;
        }
    }

    /** Free movement from input, one axis at a time so walls are slid along rather than stuck on. */
    private void moveAvatar(GridPositionComponent p, PlayerAvatarComponent a, GridNavGraph nav, float step) {
        float mx = MathUtils.clamp(a.moveX, -1f, 1f), my = MathUtils.clamp(a.moveY, -1f, 1f);
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1f) {
            mx /= len;
            my /= len;
        }
        tryMove(p, nav, mx * step, 0f);
        tryMove(p, nav, 0f, my * step);
    }

    /** Body radius of the avatar, tiles; it keeps this far from walls. */
    static final float AVATAR_RADIUS = 0.3f;

    private void tryMove(GridPositionComponent p, GridNavGraph nav, float dx, float dy) {
        if (dx == 0f && dy == 0f) return;
        if (p.walkway != null) {
            walkAcross(p, p.walkway, dx, dy);
            return;
        }
        float nx = p.x + dx, ny = p.y + dy;
        int cx = p.tileX(), cy = p.tileY();
        // The tile the leading edge of the body would enter
        int ex = MathUtils.floor(nx + Math.signum(dx) * AVATAR_RADIUS);
        int ey = MathUtils.floor(ny + Math.signum(dy) * AVATAR_RADIUS);
        GridLink link = null;
        if (dx != 0f && ex != cx && !nav.canStep(cx, cy, ex, cy)) {
            link = crossing(nav.grid, cx, cy, dx > 0 ? Side.RIGHT : Side.LEFT);
            if (link == null) nx = dx > 0 ? cx + 1 - AVATAR_RADIUS : cx + AVATAR_RADIUS;
        }
        if (dy != 0f && ey != cy && !nav.canStep(cx, cy, cx, ey)) {
            link = crossing(nav.grid, cx, cy, dy > 0 ? Side.TOP : Side.BOTTOM);
            if (link == null) ny = dy > 0 ? cy + 1 - AVATAR_RADIUS : cy + AVATAR_RADIUS;
        }
        p.x = nx;
        p.y = ny;
        // Through a docked port: once the middle of the body is over the edge, it is out on the walkway
        if (link != null && (MathUtils.floor(nx) != cx || MathUtils.floor(ny) != cy)) p.walkway = link;
    }

    /**
     * A step out on the walkway between two docked craft: the two port collars, one {@link GridLink#gap}
     * long, as wide as the opening. Past halfway across, the walker is aboard the far craft; back over
     * the edge they came out of, aboard this one again.
     */
    private void walkAcross(GridPositionComponent p, GridLink link, float dx, float dy) {
        Side side = link.side;
        float past = link.past(p.x, p.y) + dx * side.dx + dy * side.dy;
        float along = link.along(p.x, p.y) - dx * side.dy + dy * side.dx;
        float edge = 0.5f - AVATAR_RADIUS;
        if (Math.abs(along) > edge) {
            // Sideways: on along the opening if the next tile over is joined too, else against the collar wall
            int sign = along > 0f ? 1 : -1;
            GridLink next = link.grid().linkAt(link.x - side.dy * sign, link.y + side.dx * sign, side);
            if (next == null || !next.canCross()) {
                along = sign * edge;
            } else if (Math.abs(along) > 0.5f) {
                link = next;
                along -= sign;
            }
        }
        link.point(past, along, across);
        p.x = across[0];
        p.y = across[1];
        p.walkway = link;
        if (past <= 0f) {
            // Back over the edge, on the tile it stepped out of
            p.walkway = null;
        } else if (past > link.gap * 0.5f) {
            // Over halfway: the rest of the way is the other craft's collar
            link.carryAcross(p.x, p.y, across);
            p.setShip(link.otherShip);
            p.x = across[0];
            p.y = across[1];
            p.walkway = link.mate.past(p.x, p.y) > 0f ? link.mate : null;
        }
    }

    /**
     * Steps a walker back onto their tile when the walkway they were out on is gone (undocked, or the
     * door shut), or after a load, which keeps where they stood but not the walkway.
     */
    private static void keepAboard(GridPositionComponent p, ShipGridComponent grid) {
        GridLink c = p.crossing;
        if (c != null && (!c.grid().links.contains(c, true) || !c.canCross())) p.crossing = null;
        GridLink w = p.walkway;
        if (w != null && (w.grid() != grid || !grid.links.contains(w, true) || !w.canCross())) {
            p.setTile(w.x, w.y);
            p.stop();
        } else if (w == null && !grid.inBounds(MathUtils.floor(p.x), MathUtils.floor(p.y))) {
            p.x = MathUtils.clamp(p.x, 0.5f, grid.width - 0.5f);
            p.y = MathUtils.clamp(p.y, 0.5f, grid.height - 0.5f);
        }
    }

    private final float[] across = new float[2];

    /** The docking link out of tile (x, y) across its {@code side} edge, if it is open to walk through. */
    private static GridLink crossing(ShipGridComponent grid, int x, int y, Side side) {
        if (grid.links.size == 0 || grid.getTile(x + side.dx, y + side.dy) != null) return null;
        GridLink link = grid.linkAt(x, y, side);
        return link != null && link.canCross() ? link : null;
    }
}
