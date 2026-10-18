package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Moves crew along their paths every frame: interpolates the float position toward the
 * centre of the next tile at the crew member's speed (slower while carrying). Before
 * each step it checks the step is still open; if a door shut or a wall went up it stops
 * the crew member on their tile and sets {@link GridPositionComponent#pathBlocked} so the
 * behavior tree re-plans.
 *
 * Unconscious and dead crew do not move; carried crew ride on their carrier's tile. The
 * player avatar moves from its input instead of a path.
 */
public class CrewMovementSystem extends IteratingSystem {

    private final CrewService service;
    private final ComponentMapper<PlayerAvatarComponent> avatarM = ComponentMapper.getFor(PlayerAvatarComponent.class);

    public CrewMovementSystem(CrewService service) {
        super(CrewService.CREW, 10);
        this.service = service;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        // Carried crew follow their carriers after everyone has moved
        for (int i = 0; i < getEntities().size(); i++) {
            Entity e = getEntities().get(i);
            CrewMemberComponent c = service.crew(e);
            if (c.carriedBy == null) continue;
            GridPositionComponent p = service.position(e), cp = service.position(c.carriedBy);
            p.x = cp.x;
            p.y = cp.y;
        }
    }

    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = service.crew(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid == null) return;
        if (!c.isConscious() || c.carriedBy != null) {
            p.stop();
            return;
        }
        GridNavGraph nav = service.pathfinder(grid).graph;
        float speed = c.def.moveSpeed * (c.carrying != null ? c.def.carrySpeedFactor : 1f);
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
        }
        if (!p.isMoving() && p.path.size > 0) p.path.clear();
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
        float nx = p.x + dx, ny = p.y + dy;
        int cx = p.tileX(), cy = p.tileY();
        // The tile the leading edge of the body would enter
        int ex = MathUtils.floor(nx + Math.signum(dx) * AVATAR_RADIUS);
        int ey = MathUtils.floor(ny + Math.signum(dy) * AVATAR_RADIUS);
        if (dx != 0f && ex != cx && !nav.canStep(cx, cy, ex, cy)) nx = dx > 0 ? cx + 1 - AVATAR_RADIUS : cx + AVATAR_RADIUS;
        if (dy != 0f && ey != cy && !nav.canStep(cx, cy, cx, ey)) ny = dy > 0 ? cy + 1 - AVATAR_RADIUS : cy + AVATAR_RADIUS;
        p.x = nx;
        p.y = ny;
    }
}
