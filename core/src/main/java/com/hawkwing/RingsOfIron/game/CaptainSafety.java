package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * The player's own figure in a holed ship (round 6 part 1). The crew's behaviour tree suits them up
 * at battle stations and walks them out of thin air; the player's figure has no tree, so this does
 * the same for it:
 * <ul>
 *   <li><b>Battle stations.</b> While their ship is at battle stations and the crew suit up for it
 *       ({@link ShipTaskBoardComponent#battleSuits}, the crew window's toggle), the figure suits up
 *       too, taking the crew's suit time, and keeps the suit on. Once calm on air fit to breathe it
 *       comes off again, as the crew's do.</li>
 *   <li><b>Losing air.</b> On a tile whose air is no longer fit to breathe the figure pulls its suit
 *       on, and the player is told to get to a pressurised room (a notice once each time, and a hint
 *       while it lasts). The suit's air runs out; walking to air is still the player's to do.</li>
 *   <li><b>Nowhere aboard to breathe, at a berth</b> (round 8). With {@link #ASHORE_AT} or less of
 *       suit air left, nowhere aboard safe and a friendly craft docked that has air, the figure is
 *       walked aboard it, as the crew go to shelter there, and walked home again once the ship can
 *       be breathed. The player's own input wins, as when abandoning ship. Round 5's seed 7 lost its
 *       captain at Highport's berth: the crew were safe ashore, and nobody was left aboard to carry
 *       the figure when its suit ran dry.</li>
 * </ul>
 * Should the figure go down anyway, the crew carry it to air as they would any casualty
 * ({@code CrewHealthSystem} posts the rescue), and put its suit on when nowhere aboard has air. The
 * suit is saved with the figure; a suit half on when the game is saved starts again after a load.
 */
public final class CaptainSafety {
    /** Notice text when the figure starts losing air. */
    public static final String THIN_AIR = "The air here is going: you pull your suit on. Get to a pressurised room";

    /** Most sim seconds one update counts toward suiting up or calming down. */
    static final float MAX_STEP = 0.5f;
    /** Suit air left, seconds, at which the figure is walked aboard a docked craft with air: when the warning shows. */
    public static final float ASHORE_AT = AirWatch.WARN_AT;

    private final GameWorld world;
    /** Seconds spent putting the suit on, or on calm air before taking it off. */
    private float suiting, calm;
    /** On thin air at the last update: told once per spell. */
    private boolean warned;
    private double last = Double.NaN;
    /**
     * Walked aboard a docked craft to breathe, so to be walked home again; and the ship left (the
     * figure has no home of its own: it belongs to whatever it is aboard).
     */
    private boolean ashore;
    private Entity left;
    /** The way being walked, the direction last set on the figure, and the walkway being crossed. */
    private final Array<GridNode> path = new Array<>();
    private int step;
    private double repathAt = Double.NEGATIVE_INFINITY;
    private float steerX, steerY;
    private GridLink across;

    public CaptainSafety(GameWorld world) {
        this.world = world;
        world.hints.register(HintRegistry.Context.DECK, "walk to air (losing air here)", 3,
                Action.THRUST, Action.TURN_LEFT, Action.REVERSE, Action.TURN_RIGHT).onlyWhen(this::inThinAir);
    }

    /** The player's figure, or null. */
    Entity avatar() {
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) return e;
        return null;
    }

    /** True while the player's figure stands, unsuited or suited, on air not fit to breathe. */
    public boolean inThinAir() {
        Entity e = avatar();
        if (e == null || !world.crew.crew(e).isConscious()) return false;
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = p == null ? null : world.crew.grid(p.ship);
        return grid != null && !TaskRules.isBreathable(grid, p.tileX(), p.tileY());
    }

    /** Once a frame. */
    public void update() {
        double now = world.engine.getSimSeconds();
        // A time skip (a tow home, a jump in warp) is not time spent putting a suit on or standing calm
        float dt = Double.isNaN(last) ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : world.crew.crew(e);
        if (c == null || !c.isConscious() || c.held || c.def.suitAir <= 0f) {
            suiting = calm = 0f;
            warned = false;
            return;
        }
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        if (grid == null) return;
        steerAshore(e, c, p, now);
        int x = p.tileX(), y = p.tileY();
        boolean thin = !TaskRules.isBreathable(grid, x, y);
        ShipTaskBoardComponent board = world.crew.board(world.crew.homeOf(e));
        boolean battle = board != null && board.battleSuits();
        if (thin && !warned) world.notices.post(THIN_AIR, NoticeLog.Kind.WARNING);
        warned = thin;
        if (battle || thin) {
            calm = 0f;
            if (c.suited) return;
            suiting += dt;
            if (suiting >= c.def.suitTime) {
                c.setSuited(true);
                suiting = 0f;
            }
            return;
        }
        suiting = 0f;
        if (!c.suited || !TaskRules.isSafe(grid, x, y)) {
            calm = 0f;
            return;
        }
        calm += dt;
        if (calm >= CrewTasks.Idle.SUIT_OFF_DELAY + c.def.suitTime * 0.5f) {
            c.setSuited(false);
            calm = 0f;
        }
    }

    /** Walks the figure aboard a docked craft with air, or home again from it ({@link #ASHORE_AT}). */
    private void steerAshore(Entity e, CrewMemberComponent c, GridPositionComponent p, double now) {
        PlayerAvatarComponent walk = e.getComponent(PlayerAvatarComponent.class);
        // The player's own input wins
        if ((walk.moveX != 0f || walk.moveY != 0f) && (walk.moveX != steerX || walk.moveY != steerY)) {
            stopSteering(walk, false);
            return;
        }
        Entity home = ashore && left != null ? left : world.crew.homeOf(e);
        ShipGridComponent grid = world.crew.grid(p.ship), homeGrid = world.crew.grid(home);
        Entity to = null;
        if (p.ship == home) {
            if (c.suitAir <= ASHORE_AT && !TaskRules.isBreathable(grid, p.tileX(), p.tileY()) && !CrewTasks.anywhereSafe(grid)) {
                for (GridLink l : grid.links) {
                    if (l.canCross() && world.crew.friendlyTo(c.faction, l.otherShip) && CrewTasks.anywhereSafe(l.other)) to = l.otherShip;
                }
            }
            // Back aboard: on off the port's opening to air
            if (to == null && ashore) to = home;
        } else if (ashore && CrewTasks.anywhereSafe(homeGrid)) {
            to = home;
        } else if (ashore && TaskRules.isSafe(grid, p.tileX(), p.tileY()) && !onLink(grid, p.tileX(), p.tileY())) {
            // Aboard and breathing: wait here
            stopSteering(walk, true);
            return;
        } else if (ashore) {
            to = p.ship;
        }
        if (to == null && across == null) {
            stopSteering(walk, true);
            return;
        }
        if (p.walkway != null && across != null) {
            // Out on the walkway between the collars: on across it
            steer(walk, across.side.dx, across.side.dy);
            return;
        }
        across = null;
        if (to != home && !ashore) {
            ashore = true;
            left = home;
        }
        int x = p.tileX(), y = p.tileY();
        GridPathfinder pf = world.crew.pathfinder(grid);
        if (p.ship == to) {
            if (TaskRules.isSafe(grid, x, y) && !onLink(grid, x, y)) {
                stopSteering(walk, true);
                if (to == home) ashore = false;
                return;
            }
            if (now >= repathAt || step >= path.size) {
                repathAt = now + 1.0;
                step = 0;
                if (!pf.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty) && !onLink(grid, tx, ty), path)) path.clear();
            }
        } else {
            GridLink link = null;
            for (GridLink l : grid.links) if (l.otherShip == to && l.canCross()) link = l;
            if (link == null) {
                // Undocked from the ship left: nothing to walk back to
                if (to == home) ashore = false;
                stopSteering(walk, true);
                return;
            }
            if (x == link.x && y == link.y) {
                across = link;
                steer(walk, link.side.dx, link.side.dy);
                return;
            }
            if (now >= repathAt || step >= path.size) {
                repathAt = now + 1.0;
                step = 0;
                if (!pf.findPath(x, y, link.x, link.y, path)) path.clear();
            }
        }
        while (step < path.size) {
            GridNode n = path.get(step);
            float dx = n.x + 0.5f - p.x, dy = n.y + 0.5f - p.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.15f) {
                step++;
                continue;
            }
            steer(walk, dx / d, dy / d);
            return;
        }
        stopSteering(walk, true);
    }

    private void steer(PlayerAvatarComponent walk, float dx, float dy) {
        steerX = dx;
        steerY = dy;
        walk.moveX = dx;
        walk.moveY = dy;
    }

    /** Stops walking the figure; {@code ours} if the input on it is this class's to clear. */
    private void stopSteering(PlayerAvatarComponent walk, boolean ours) {
        if (ours && walk.moveX == steerX && walk.moveY == steerY) walk.moveX = walk.moveY = 0f;
        steerX = steerY = 0f;
        path.clear();
        step = 0;
        across = null;
        repathAt = Double.NEGATIVE_INFINITY;
    }

    private static boolean onLink(ShipGridComponent grid, int x, int y) {
        for (GridLink l : grid.links) if (l.x == x && l.y == y) return true;
        return false;
    }
}
