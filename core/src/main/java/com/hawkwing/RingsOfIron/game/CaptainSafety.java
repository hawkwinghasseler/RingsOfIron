package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.btree.CrewTasks;
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

    private final GameWorld world;
    /** Seconds spent putting the suit on, or on calm air before taking it off. */
    private float suiting, calm;
    /** On thin air at the last update: told once per spell. */
    private boolean warned;
    private double last = Double.NaN;

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
}
