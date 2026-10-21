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
import com.ringsofiron.interiorgrid.Side;

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
 *   <li><b>Filling the suit</b> (round 10). Suited on thin air with {@link #REFILL_AT} or less
 *       left, the figure standing still beside an oxygen tank or working life support fills its suit
 *       there over the crew's refill time, as a crewman does.</li>
 *   <li><b>Warp gives way to the walk</b> (round 10). While the figure is walked to air, time warp
 *       drops to 1x and rails warp waits. At 10x the walk turned back at the walkway's halfway mark
 *       several times a frame, and round 9's captain stood in Highport's walkway at warp until the
 *       suit ran dry, with the crew ashore.</li>
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
    /** Suit air left, seconds, below which standing beside the ship's oxygen fills the figure's suit: when the warning shows. */
    public static final float REFILL_AT = AirWatch.WARN_AT;
    /** Notice text once the figure's suit is filled. */
    public static final String SUIT_FILLED = "Your suit is filled from the ship's oxygen";
    /** Notice text when warp drops for the walk to air. */
    public static final String WARP_DROPS = "Time warp drops: your suit is low, and you are walked to air";
    /** Each sim tick, before the crew move (their movement runs at priority 10). */
    static final int KEEP_WALKING_PRIORITY = 5;
    /** Whether time warp gives way while the figure is walked to air (round 10). */
    static final boolean WARP_GIVES_WAY = true;

    private final GameWorld world;
    /** Seconds spent putting the suit on, or on calm air before taking it off, or filling it. */
    private float suiting, calm, filling;
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
    /** Walking the figure to air, or home again, as of the last update; warp told once per walk. */
    private boolean walking, warpTold;
    /** Walked, and not yet breathing: what warp gives way to. */
    private boolean fromThinAir;
    /** A save was just loaded: whether the figure is ashore is read back at the next update. */
    private boolean readAshore;

    public CaptainSafety(GameWorld world) {
        this.world = world;
        world.engine.addRailsWarpGuard(this::railsWarpBlockedReason);
        // The walk is set after the engine's update, and the screen clears the figure's walk before
        // the next one: so it is set again before the crew move, unless the player walks it
        world.engine.addSystem(new com.ringsofiron.coreengine.systems.FixedLogicSystem(KEEP_WALKING_PRIORITY,
                (float) com.ringsofiron.coreengine.SimClock.TICK_SECONDS) {
            @Override
            protected void updateFixed(float deltaTime) {
                keepWalking();
            }
        });
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

    /**
     * The figure was laid aboard the craft {@code ship} is docked to, to breathe (round 10: carried
     * home by the crew while nowhere aboard could be breathed): it is walked home once the ship can be.
     */
    void ashore(Entity ship) {
        ashore = true;
        left = ship;
        Entity e = avatar();
        if (e != null) stopSteering(e.getComponent(PlayerAvatarComponent.class), true);
    }

    /**
     * A save was loaded: what this held belonged to the game before it. Sheltering aboard the craft
     * the player's ship is docked to, with nowhere aboard the ship to breathe, is taken up again at
     * the next update, so the figure is still walked home once the ship can be breathed (round 10:
     * not saved, it is read back from where the figure stands).
     */
    void loaded() {
        ashore = walking = warpTold = fromThinAir = false;
        left = null;
        across = null;
        path.clear();
        step = 0;
        steerX = steerY = 0f;
        repathAt = Double.NEGATIVE_INFINITY;
        last = Double.NaN;
        suiting = calm = filling = 0f;
        warned = false;
        readAshore = true;
    }

    /** True while the figure is aboard a docked craft to breathe, to be walked home once the ship can be. */
    public boolean ashore() {
        return ashore;
    }

    /** After a load: sheltering aboard the craft the player's ship is docked to, as {@link #loaded} says. */
    private void readAshore(Entity e) {
        readAshore = false;
        Entity ship = world.getPlayer();
        Entity docked = ship == null ? null : world.docking.dockedTo(ship);
        GridPositionComponent p = world.crew.position(e);
        if (docked != null && p != null && p.ship == docked && !CrewTasks.anywhereSafe(world.crew.grid(ship))) {
            ashore = true;
            left = ship;
        }
    }

    /**
     * Each tick before the crew move: the walk this class set, again, if the screen cleared it (round
     * 10: the game clears the figure's walk from the keys each frame before the world updates, and
     * this updates after the sim ticks, so in a real window the walk ashore never moved the figure).
     */
    void keepWalking() {
        if (!walking || (steerX == 0f && steerY == 0f)) return;
        Entity e = avatar();
        PlayerAvatarComponent walk = e == null ? null : e.getComponent(PlayerAvatarComponent.class);
        if (walk == null || walk.moveX != 0f || walk.moveY != 0f) return;
        walk.moveX = steerX;
        walk.moveY = steerY;
    }

    /** Why rails warp waits: while the figure is walked to air. Null when it need not. */
    public String railsWarpBlockedReason() {
        return WARP_GIVES_WAY && fromThinAir ? "you are being walked to air" : null;
    }

    /** True while the figure is walked to air from where it can't breathe. */
    public boolean walkingToAir() {
        return fromThinAir;
    }

    /** Once a frame. */
    public void update() {
        double now = world.engine.getSimSeconds();
        // A time skip (a tow home, a jump in warp) is not time spent putting a suit on or standing calm
        float dt = Double.isNaN(last) ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        Entity e = avatar();
        if (readAshore && e != null) readAshore(e);
        CrewMemberComponent c = e == null ? null : world.crew.crew(e);
        if (c == null || !c.isConscious() || c.held || c.def.suitAir <= 0f) {
            suiting = calm = 0f;
            warned = false;
            walking = warpTold = fromThinAir = false;
            return;
        }
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        if (grid == null) return;
        steerAshore(e, c, p, now);
        giveWayToTheWalk(p);
        int x = p.tileX(), y = p.tileY();
        boolean thin = !TaskRules.isBreathable(grid, x, y);
        fillSuit(e, c, p, grid, thin, dt);
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

    /** Fills the figure's suit standing still beside the ship's oxygen on thin air ({@link #REFILL_AT}). */
    private void fillSuit(Entity e, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid, boolean thin, float dt) {
        PlayerAvatarComponent walk = e.getComponent(PlayerAvatarComponent.class);
        boolean still = walk.moveX == 0f && walk.moveY == 0f && p.walkway == null;
        com.ringsofiron.interiorgrid.SubsystemComponent at = !thin || !c.suited || !still
                || c.suitAir > (filling > 0f ? c.def.suitAir : REFILL_AT) ? null
                : CrewTasks.suitOxygenBeside(p.ship, grid, p.tileX(), p.tileY());
        if (at == null) {
            filling = 0f;
            return;
        }
        filling += dt;
        if (filling < c.def.suitRefillTime) return;
        CrewTasks.drawSuitOxygen(at);
        c.suitAir = c.def.suitAir;
        filling = 0f;
        world.notices.post(SUIT_FILLED, NoticeLog.Kind.INFO);
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
            // Out on the walkway between the collars: on across it. Past halfway the figure is on
            // the far craft's collar, in its tiles, whose port faces back the way it came (round 10:
            // steered by the near port's side there, the figure turned back at halfway, and the
            // captain stood in Highport's walkway until the suit ran dry)
            Side side = p.walkway.grid() == across.grid() ? across.side : p.walkway.side.opposite();
            steer(walk, side.dx, side.dy);
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

    /**
     * While the figure is walked from thin air, warp drops to 1x, with a notice once each walk
     * ({@link #WARP_GIVES_WAY}). Once it breathes, a step clear of the port it came in by isn't a
     * reason to hold warp back.
     */
    private void giveWayToTheWalk(GridPositionComponent p) {
        ShipGridComponent grid = world.crew.grid(p.ship);
        fromThinAir = walking && (p.walkway != null || grid == null || !TaskRules.isBreathable(grid, p.tileX(), p.tileY()));
        if (!fromThinAir) {
            if (!walking) warpTold = false;
            return;
        }
        if (!WARP_GIVES_WAY || world.timeWarp.getWarp() <= 1) return;
        world.timeWarp.setWarp(1);
        if (!warpTold) world.notices.post(WARP_DROPS, NoticeLog.Kind.WARNING);
        warpTold = true;
    }

    private void steer(PlayerAvatarComponent walk, float dx, float dy) {
        walking = true;
        steerX = dx;
        steerY = dy;
        walk.moveX = dx;
        walk.moveY = dy;
    }

    /** Stops walking the figure; {@code ours} if the input on it is this class's to clear. */
    private void stopSteering(PlayerAvatarComponent walk, boolean ours) {
        if (ours && walk.moveX == steerX && walk.moveY == steerY) walk.moveX = walk.moveY = 0f;
        walking = false;
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
