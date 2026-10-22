package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;

/**
 * Moves crew every sim tick. On their feet on an open deck tile, crew are bodies in the deck's
 * Box2D world ({@link CrewPhysics}): walls and shut doors stop them, and they push each other
 * apart softly around a solid core ({@link CrewBody}). AI crew walk their path from tile centre
 * to tile centre at their speed (slower while carrying); the player avatar walks where its input
 * points. Before each step it checks the step is still open; if a door shut or a wall went up it
 * stops the crew member on their tile and sets {@link GridPositionComponent#pathBlocked} so the
 * behavior tree re-plans, and the same happens to one held up for {@link #STALL_SECONDS}.
 *
 * Unconscious and dead crew do not move, and lie where others can step over them; carried crew
 * ride on their carrier's tile. Out on the walkway through a docked port ({@link GridLink}),
 * across the collars between two craft, and on a tile that isn't open, crew move along without a
 * body and take one up again once back on the deck.
 */
public class CrewMovementSystem extends FixedIteratingSystem {

    /** How long a walker can make no headway toward the next tile before giving the route up, seconds. */
    public static final float STALL_SECONDS = 3f;
    /** How close to the middle of a tile on the way counts as reaching it, tiles. */
    static final float WAYPOINT_REACH = 0.35f;
    /** How close to the middle of the last tile counts as there, tiles. */
    static final float ARRIVE_REACH = 0.25f;

    private final CrewService service;
    private final ComponentMapper<PlayerAvatarComponent> avatarM = ComponentMapper.getFor(PlayerAvatarComponent.class);
    private final EntityListener leaving = new EntityListener() {
        @Override
        public void entityAdded(Entity entity) {
        }

        @Override
        public void entityRemoved(Entity entity) {
            service.physics.release(entity);
        }
    };

    public CrewMovementSystem(CrewService service) {
        super(CrewService.CREW, 10, (float) SimClock.TICK_SECONDS);
        this.service = service;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        engine.addEntityListener(CrewService.CREW, leaving);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(leaving);
        super.removedFromEngine(engine);
    }

    @Override
    protected void updateFixed(float dt) {
        super.updateFixed(dt);
        if (getEntities() == null) return;
        service.physics.step(dt);
        for (int i = 0; i < getEntities().size(); i++) settle(getEntities().get(i), dt);
        service.doors.update(dt);
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

    /** Before the physics step: puts each crew member's body where it belongs and sets it walking. */
    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = service.crew(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid == null) {
            service.physics.release(e);
            return;
        }
        // The ship may have been given its UUID after the crew boarded
        if (p.shipRef == null) p.setShip(p.ship);
        keepAboard(p, grid);
        if (!c.isConscious() || c.carriedBy != null) {
            service.physics.release(e);
            p.stop();
            return;
        }
        PlayerAvatarComponent avatar = avatarM.get(e);
        if (avatar != null && avatar.seated && avatar.moveX == 0f && avatar.moveY == 0f) {
            // Seated at a console and not walking off: nothing for passing crew to push (round 7: a
            // yard technician shoved the captain off the helm, and the save after found them on deck)
            service.physics.release(e);
            p.stop();
            return;
        }
        GridNavGraph nav = service.pathfinder(grid).graph;
        float speed = c.def.moveSpeed * (c.carrying != null ? c.def.carrySpeedFactor : 1f)
                * (c.load != null ? c.def.loadSpeedFactor : 1f) * (c.suited ? c.def.suitSpeedFactor : 1f) * c.woundPace();
        boolean crossingOut = avatar == null && p.crossing != null && !p.isMoving();
        if (p.walkway != null || crossingOut || !nav.isWalkable(p.tileX(), p.tileY())) {
            // Off the deck proper: moved along without a body
            service.physics.release(e);
            if (avatar != null) moveAvatar(p, avatar, nav, speed * dt);
            else walkPath(e, p, nav, speed * dt, dt);
            return;
        }
        CrewBody b = service.physics.attach(e, nav, p.x, p.y);
        if (avatar != null) {
            float mx = MathUtils.clamp(avatar.moveX, -1f, 1f), my = MathUtils.clamp(avatar.moveY, -1f, 1f);
            float len = (float) Math.sqrt(mx * mx + my * my);
            if (len > 1f) {
                mx /= len;
                my /= len;
            }
            b.steer(mx * speed, my * speed);
            return;
        }
        if (!p.isMoving()) {
            if (p.path.size > 0) p.path.clear();
            b.hold(speed);
            return;
        }
        GridNode next = p.path.get(p.pathIndex);
        int cx = p.tileX(), cy = p.tileY();
        if ((next.x != cx || next.y != cy) && !nav.canStep(cx, cy, next.x, next.y)) {
            // A shut door: stand and open it. Else blocked: back to the centre of the current tile and ask for a new route
            if (!service.doors.open(e, p, nav, cx, cy, next.x, next.y, dt)) blocked(p, b, cx, cy);
            b.hold(speed);
            return;
        }
        b.seek(next.x + 0.5f, next.y + 0.5f, speed, p.pathIndex == p.path.size - 1);
    }

    /** After the physics step: reads each body back into its crew member's position. */
    private void settle(Entity e, float dt) {
        CrewBody b = service.physics.body(e);
        if (b == null) return;
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = b.world.grid;
        int cx = p.tileX(), cy = p.tileY();
        float x = b.x(), y = b.y();
        int tx = MathUtils.floor(x), ty = MathUtils.floor(y);
        if (grid.getTile(tx, ty) == null) {
            // Over the edge: out through a docked port onto the walkway, or put back
            Side side = tx < cx ? Side.LEFT : tx > cx ? Side.RIGHT : ty < cy ? Side.BOTTOM : Side.TOP;
            GridLink link = crossing(grid, cx, cy, side);
            // Only the player walks out on a body; AI crew cross bodiless when they mean to, so one
            // shoved over the edge by the crowd at a port is put back (a yard technician pushed out
            // there stepped back in, was pushed out again, and never reached the tile it was sent to)
            if (link == null || avatarM.get(e) == null) {
                b.teleport(p.x, p.y);
                return;
            }
            p.x = x;
            p.y = y;
            p.walkway = link;
            service.physics.release(e);
            return;
        }
        p.x = b.lastX = x;
        p.y = b.lastY = y;
        if (!p.isMoving()) return;
        GridNode next = p.path.get(p.pathIndex);
        boolean last = p.pathIndex == p.path.size - 1;
        float dx = next.x + 0.5f - x, dy = next.y + 0.5f - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (last ? d < ARRIVE_REACH && tx == next.x && ty == next.y : d < WAYPOINT_REACH) {
            p.pathIndex++;
            if (!p.isMoving()) {
                p.path.clear();
                b.anchor(next.x + 0.5f, next.y + 0.5f);
            }
            return;
        }
        // Held up: by a crowd, or round a corner it can't get past
        if (b.waypoint != p.pathIndex || d < b.bestDistance - 0.02f) {
            b.waypoint = p.pathIndex;
            b.bestDistance = d;
            b.stalled = 0f;
        } else if ((b.stalled += dt) >= STALL_SECONDS) {
            blocked(p, b, tx, ty);
        }
    }

    private static void blocked(GridPositionComponent p, CrewBody b, int x, int y) {
        p.stop();
        p.pathBlocked = true;
        b.anchor(x + 0.5f, y + 0.5f);
    }

    /** Walks the path without a body (off the deck proper), then out through a docked link if crossing. */
    private void walkPath(Entity e, GridPositionComponent p, GridNavGraph nav, float step, float dt) {
        float budget = step;
        while (budget > 0f && p.isMoving()) {
            GridNode next = p.path.get(p.pathIndex);
            int cx = p.tileX(), cy = p.tileY();
            if ((next.x != cx || next.y != cy) && !nav.canStep(cx, cy, next.x, next.y)) {
                // A shut door: stand and open it
                if (service.doors.open(e, p, nav, cx, cy, next.x, next.y, dt)) return;
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
            // Back on the deck: the body takes over next tick
            if (p.walkway == null && nav.isWalkable(p.tileX(), p.tileY())) return;
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

    /** Free movement from input off the deck proper (out on a walkway), one axis at a time so walls are slid along. */
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

    /** Body radius off the deck, tiles; it keeps this far from walls, as a {@link CrewBody} does. */
    static final float AVATAR_RADIUS = CrewBody.RADIUS;

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
            p.x = MathUtils.clamp(p.x, 0.01f, grid.width - 0.01f);
            p.y = MathUtils.clamp(p.y, 0.01f, grid.height - 0.01f);
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
