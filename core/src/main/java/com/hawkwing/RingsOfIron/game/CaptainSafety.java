package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.uihud.systems.InteractPromptSystem;

/**
 * The player's own figure in a holed ship (round 6 part 1). The crew's behaviour tree suits them up
 * at battle stations and walks them out of thin air; the player's figure has no tree, so this suits
 * it up the same way (but never walks it). Suits hang in suit lockers ({@link SuitLockers}): the figure takes one from a
 * locker within its reach, and hangs it back up in one, never anywhere else. E at a locker puts a
 * suit on or hangs it up ({@link #lockerUse}).
 * <ul>
 *   <li><b>Battle stations.</b> While their ship is at battle stations and the crew suit up for it
 *       ({@link ShipTaskBoardComponent#battleSuits}, the crew window's toggle), the figure standing at
 *       a locker suits up too, taking the crew's suit time, and keeps the suit on. Once calm on air
 *       fit to breathe beside a locker with room for it, it comes off again, as the crew's do.</li>
 *   <li><b>Losing air.</b> On a tile whose air is no longer fit to breathe the figure at a locker pulls
 *       a suit on (a pressure suit, or an EVA suit if that is all there is), and the player is told
 *       to get to a pressurised room (a notice once each time, and a hint while it lasts); away from
 *       one, the player is told to get to a suit locker ({@link #NO_SUIT}). The suit's air runs out;
 *       walking to air is the player's to do.</li>
 *   <li><b>Filling the suit</b> (round 10). Suited on thin air with {@link #REFILL_AT} or less
 *       left, the figure standing still beside an oxygen tank or working life support fills its suit
 *       there over the crew's refill time, as a crewman does.</li>
 * </ul>
 * The figure is never walked anywhere: where it goes is the player's alone. A suit's air is finite
 * (a pressure suit holds {@code CrewDef#suitAir} seconds of it), so the player is warned as it runs low
 * ({@link AirWatch}) and the walk to air, or ashore, is theirs to make.
 * Should the figure go down anyway, the crew carry it to air as they would any casualty
 * ({@code CrewHealthSystem} posts the rescue), and put its suit on when nowhere aboard has air. The
 * suit is saved with the figure; a suit half on when the game is saved starts again after a load. A
 * suit put on with E stays on, calm or not, until it is hung up with E.
 */
public final class CaptainSafety {
    /** Notice text when the figure starts losing air. */
    public static final String THIN_AIR = "The air here is going: you pull your suit on. Get to a pressurised room";
    /** Notice text when the figure wants a suit and there is none within reach. */
    public static final String NO_SUIT = "You need a suit: get to a suit locker";

    /** Most sim seconds one update counts toward suiting up or calming down. */
    static final float MAX_STEP = 0.5f;
    /** Suit air left, seconds, below which standing beside the ship's oxygen fills the figure's suit: when the warning shows. */
    public static final float REFILL_AT = AirWatch.WARN_AT;
    /** Notice text once the figure's suit is filled. */
    public static final String SUIT_FILLED = "Your suit is filled from the ship's oxygen";

    private final GameWorld world;
    /** Seconds spent putting the suit on or taking it off, or on calm air before taking it off, or filling it. */
    private float suiting, calm, filling;
    /** What the figure is doing at a suit locker, and the locker. */
    private enum AtLocker { NONE, ON, OFF }
    private AtLocker atLocker = AtLocker.NONE;
    private Furnishing locker;
    /** Told this spell that there is no suit within reach. */
    private boolean toldNoSuit;
    /** The suit on was put on with E: it stays on until taken off with E, whatever the calm. */
    private boolean kept;
    /** On thin air at the last update: told once per spell. */
    private boolean warned;
    private double last = Double.NaN;

    public CaptainSafety(GameWorld world) {
        this.world = world;
        world.hints.register(HintRegistry.Context.DECK, "walk to air (losing air here)", 3,
                Action.THRUST, Action.TURN_LEFT, Action.REVERSE, Action.TURN_RIGHT).onlyWhen(this::inThinAir);
    }

    /**
     * What E does at a suit locker: puts on a suit from it (a pressure suit first), or hangs the
     * figure's suit up in it on air fit to take it off in, over the crew's suit times.
     */
    public InteractPromptSystem.Interaction lockerUse() {
        return new InteractPromptSystem.Interaction() {
            private final com.badlogic.gdx.math.Vector2 front = new com.badlogic.gdx.math.Vector2();

            @Override
            public String prompt() {
                Furnishing f = usable();
                if (f == null) return null;
                CrewMemberComponent c = world.crew.crew(avatar());
                return c.suited ? "hang up your " + c.suitKind.noun
                        : f.type.holds == Suit.Kind.EVA ? "get into an EVA suit" : "put on a pressure suit";
            }

            @Override
            public float distance2() {
                Furnishing f = usable();
                if (f == null) return Float.MAX_VALUE;
                GridPositionComponent p = world.crew.position(avatar());
                return SuitLockers.front(f, front).dst2(p.x, p.y);
            }

            @Override
            public void act() {
                Furnishing f = usable();
                if (f == null) return;
                boolean on = !world.crew.crew(avatar()).suited;
                startAt(on ? AtLocker.ON : AtLocker.OFF, f);
                kept = on;
            }
        };
    }

    /** The locker within the figure's reach that E would use now, or null: none, or busy at one. */
    private Furnishing usable() {
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : world.crew.crew(e);
        if (c == null || !c.isConscious() || c.held || !c.wearsSuits() || atLocker != AtLocker.NONE) return null;
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        if (grid == null || p.walkway != null) return null;
        if (!c.suited) return lockerWithSuit(grid, p, true);
        return TaskRules.isSafe(grid, p.tileX(), p.tileY()) ? SuitLockers.within(grid, p.x, p.y, f -> SuitLockers.hasRoom(f, c.suitKind)) : null;
    }

    /** The locker within the figure's reach with a pressure suit in it, or with {@code evaToo} an EVA suit; null if none. */
    private static Furnishing lockerWithSuit(ShipGridComponent grid, GridPositionComponent p, boolean evaToo) {
        Furnishing f = SuitLockers.within(grid, p.x, p.y, l -> SuitLockers.hasSuit(l, Suit.Kind.IVA));
        if (f == null && evaToo) f = SuitLockers.within(grid, p.x, p.y, l -> SuitLockers.hasSuit(l, Suit.Kind.EVA));
        return f;
    }

    private void startAt(AtLocker what, Furnishing f) {
        atLocker = what;
        locker = f;
        suiting = 0f;
    }

    /** True while the figure puts a suit on at a locker, or takes one off. */
    public boolean atLocker() {
        return atLocker != AtLocker.NONE;
    }

    /**
     * A step of putting a suit on or taking one off at {@link #locker}: done after the crew's suit
     * time (half for taking it off), and given up if the figure walks out of its reach.
     */
    private void stepAtLocker(CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid, float dt) {
        if (atLocker == AtLocker.NONE) return;
        Suit.Kind kind = atLocker == AtLocker.ON ? locker.type.holds : c.suitKind;
        boolean still = grid.getTile(locker.tileX, locker.tileY) != null && grid.getTile(locker.tileX, locker.tileY).furnishings.contains(locker, true)
                && SuitLockers.reaches(locker, p.x, p.y) && p.walkway == null
                && (atLocker == AtLocker.ON ? !c.suited && SuitLockers.hasSuit(locker, kind) : c.suited && SuitLockers.hasRoom(locker, kind));
        if (!still) {
            atLocker = AtLocker.NONE;
            suiting = 0f;
            return;
        }
        suiting += dt;
        if (suiting < c.suitTime(kind) * (atLocker == AtLocker.ON ? 1f : 0.5f)) return;
        if (atLocker == AtLocker.ON) {
            c.wear(SuitLockers.take(locker, kind));
        } else {
            Suit off = c.takeOff();
            if (!SuitLockers.stow(locker, off)) c.wear(off);
        }
        atLocker = AtLocker.NONE;
        suiting = calm = 0f;
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

    /** A save was loaded: what this held belonged to the game before it. */
    void loaded() {
        last = Double.NaN;
        suiting = calm = filling = 0f;
        atLocker = AtLocker.NONE;
        locker = null;
        warned = toldNoSuit = kept = false;
    }

    /** Once a sim tick ({@link GameWorld#tickManagers}). */
    public void update() {
        double now = world.engine.getSimSeconds();
        // A time skip (a tow home, a jump in warp) is not time spent putting a suit on or standing calm
        float dt = Double.isNaN(last) ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : world.crew.crew(e);
        if (c == null || !c.isConscious() || c.held || !c.wearsSuits()) {
            suiting = calm = 0f;
            atLocker = AtLocker.NONE;
            warned = toldNoSuit = false;
            return;
        }
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        if (grid == null) return;
        int x = p.tileX(), y = p.tileY();
        boolean thin = !TaskRules.isBreathable(grid, x, y);
        fillSuit(e, c, p, grid, thin, dt);
        ShipTaskBoardComponent board = world.crew.board(world.crew.homeOf(e));
        boolean battle = board != null && board.battleSuits();
        if (thin && !warned) world.notices.post(THIN_AIR, NoticeLog.Kind.WARNING);
        warned = thin;
        if (battle || thin) {
            calm = 0f;
            // Not out of a suit now; into one from a locker within reach
            if (atLocker == AtLocker.OFF) atLocker = AtLocker.NONE;
            if (!c.suited && atLocker == AtLocker.NONE) {
                Furnishing f = lockerWithSuit(grid, p, thin);
                if (f != null) {
                    startAt(AtLocker.ON, f);
                } else if (!toldNoSuit) {
                    world.notices.post(NO_SUIT, NoticeLog.Kind.WARNING);
                    toldNoSuit = true;
                }
            }
            stepAtLocker(c, p, grid, dt);
            return;
        }
        toldNoSuit = false;
        if (!c.suited) kept = false;
        if (!c.suited || kept || !TaskRules.isSafe(grid, x, y)) {
            calm = 0f;
        } else if (atLocker == AtLocker.NONE) {
            calm += dt;
            // Calm: hung back up in a locker within reach with room for it
            Furnishing f = calm >= CrewTasks.Idle.SUIT_OFF_DELAY
                    ? SuitLockers.within(grid, p.x, p.y, l -> SuitLockers.hasRoom(l, c.suitKind)) : null;
            if (f != null) startAt(AtLocker.OFF, f);
        }
        stepAtLocker(c, p, grid, dt);
    }

    /** Fills the figure's suit standing still beside the ship's oxygen on thin air ({@link #REFILL_AT}). */
    private void fillSuit(Entity e, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid, boolean thin, float dt) {
        PlayerAvatarComponent walk = e.getComponent(PlayerAvatarComponent.class);
        boolean still = walk.moveX == 0f && walk.moveY == 0f && p.walkway == null;
        com.ringsofiron.interiorgrid.SubsystemComponent at = !thin || !c.suited || !still
                || c.suitAir > (filling > 0f ? c.suitCapacity() : REFILL_AT) ? null
                : CrewTasks.suitOxygenBeside(p.ship, grid, p.tileX(), p.tileY());
        if (at == null) {
            filling = 0f;
            return;
        }
        filling += dt;
        if (filling < c.def.suitRefillTime) return;
        CrewTasks.drawSuitOxygen(at, c.suitKind);
        c.suitAir = c.suitCapacity();
        filling = 0f;
        world.notices.post(SUIT_FILLED, NoticeLog.Kind.INFO);
    }
}
