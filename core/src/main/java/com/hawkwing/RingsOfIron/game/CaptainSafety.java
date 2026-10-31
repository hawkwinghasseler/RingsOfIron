package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.uihud.systems.InteractPromptSystem;
import com.ringsofiron.aicrew.CrewService;

/**
 * The player's own figure in a holed ship (round 6 part 1). The crew's behaviour tree suits them up
 * and walks them out of thin air; the player's figure has no tree, and nothing here moves it or
 * dresses it: where it goes, and whether it wears a suit, is the player's alone. Suits hang in suit
 * lockers ({@link SuitLockers}): E at a locker within reach puts a suit on from it (a pressure suit
 * first) or hangs the figure's suit up in it, over the crew's suit times ({@link #lockerUse}).
 * <ul>
 *   <li><b>Losing air.</b> On a tile whose air is no longer fit to breathe the player is told to get
 *       to a pressurised room, or, unsuited, to a suit locker (a notice once each time, and a hint
 *       while it lasts). A suit's air is finite (a pressure suit holds {@code CrewDef#suitAir}
 *       seconds of it), and {@link AirWatch} warns as it runs low.</li>
 *   <li><b>Filling the suit</b> (round 10). Suited on thin air with {@link #REFILL_AT} or less
 *       left, the figure standing still beside an oxygen tank or working life support fills its suit
 *       there over the crew's refill time, as a crewman does.</li>
 *   <li><b>Filling at a console.</b> Seated at one of the ship's consoles ({@link SeatComponent}),
 *       a worn suit fills from empty in the crew's refill time.</li>
 * </ul>
 * Should the figure go down, the crew carry it to air as they would any casualty
 * ({@code CrewHealthSystem} posts the rescue), and put its suit on when nowhere aboard has air. The
 * suit is saved with the figure; a suit half on when the game is saved starts again after a load.
 */
public final class CaptainSafety {
    /** Notice text when the figure starts losing air. */
    public static final String THIN_AIR = "The air here is going: get to a pressurised room";
    /** Notice text when the figure starts losing air without a suit on. */
    public static final String NO_SUIT = "The air here is going: get to a pressurised room, or a suit locker to put on a suit";

    /** Most sim seconds one update counts toward suiting up or filling the suit. */
    static final float MAX_STEP = 0.5f;
    /** Suit air left, seconds, below which standing beside the ship's oxygen fills the figure's suit: when the warning shows. */
    public static final float REFILL_AT = AirWatch.WARN_AT;
    /** Notice text once the figure's suit is filled. */
    public static final String SUIT_FILLED = "Your suit is filled from the ship's oxygen";

    private final GameContext ctx;
    private final CrewService crew;
    /** Seconds spent putting the suit on or taking it off, or filling it. */
    private float suiting, filling;
    /** What the figure is doing at a suit locker, and the locker. */
    private enum AtLocker { NONE, ON, OFF }
    private AtLocker atLocker = AtLocker.NONE;
    /** Whether the bar over the figure is this class's ({@link #showProgress}). */
    private boolean showing;
    private Furnishing locker;
    /** On thin air at the last update: told once per spell. */
    private boolean warned;
    private double last = Double.NaN;

    public CaptainSafety(GameContext ctx, CrewService crew) {
        this.ctx = ctx;
        this.crew = crew;
        ctx.hints.register(HintRegistry.Context.DECK, "walk to air (losing air here)", 3,
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
                CrewMemberComponent c = crew.crew(avatar());
                return c.suited ? "hang up your " + c.suitKind.noun
                        : f.type.holds == Suit.Kind.EVA ? "get into an EVA suit" : "put on a pressure suit";
            }

            @Override
            public float distance2() {
                Furnishing f = usable();
                if (f == null) return Float.MAX_VALUE;
                GridPositionComponent p = crew.position(avatar());
                return SuitLockers.front(f, front).dst2(p.x, p.y);
            }

            @Override
            public void act() {
                Furnishing f = usable();
                if (f == null) return;
                boolean on = !crew.crew(avatar()).suited;
                startAt(on ? AtLocker.ON : AtLocker.OFF, f);
            }
        };
    }

    /** The locker within the figure's reach that E would use now, or null: none, or busy at one. */
    private Furnishing usable() {
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : crew.crew(e);
        if (c == null || !c.isConscious() || c.held || !c.wearsSuits() || atLocker != AtLocker.NONE) return null;
        GridPositionComponent p = crew.position(e);
        ShipGridComponent grid = crew.grid(p.ship);
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
        suiting = 0f;
    }

    /** The player's figure, or null. */
    Entity avatar() {
        for (Entity e : crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) return e;
        return null;
    }

    /** True while the player's figure stands, unsuited or suited, on air not fit to breathe. */
    public boolean inThinAir() {
        Entity e = avatar();
        if (e == null || !crew.crew(e).isConscious()) return false;
        GridPositionComponent p = crew.position(e);
        ShipGridComponent grid = p == null ? null : crew.grid(p.ship);
        return grid != null && !TaskRules.isBreathable(grid, p.tileX(), p.tileY());
    }

    /** A save was loaded: what this held belonged to the game before it. */
    void loaded() {
        last = Double.NaN;
        suiting = filling = 0f;
        atLocker = AtLocker.NONE;
        locker = null;
        warned = false;
    }

    /** Once a sim tick ({@link GameWorld#tickManagers}). */
    public void update() {
        step();
        showProgress();
    }

    /**
     * The bar over the figure while it puts a suit on, takes one off or fills one: it has no tree to
     * set it ({@link CrewMemberComponent#progress}). Cleared only when this set it, so it doesn't
     * wipe the bar {@link DoorForcing} sets.
     */
    private void showProgress() {
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : crew.crew(e);
        if (c == null) return;
        if (atLocker != AtLocker.NONE && locker != null) {
            Suit.Kind kind = atLocker == AtLocker.ON ? locker.type.holds : c.suitKind;
            c.showProgress(suiting, c.suitTime(kind) * (atLocker == AtLocker.ON ? 1f : 0.5f));
            showing = true;
        } else if (filling > 0f) {
            c.showProgress(filling, c.def.suitRefillTime);
            showing = true;
        } else if (showing) {
            c.progress = -1f;
            showing = false;
        }
    }

    private void step() {
        double now = ctx.engine.getSimSeconds();
        // A time skip (a tow home, a jump in warp) is not time spent putting a suit on or filling it
        float dt = Double.isNaN(last) ? 0f : (float) Math.min(MAX_STEP, Math.max(0, now - last));
        last = now;
        Entity e = avatar();
        CrewMemberComponent c = e == null ? null : crew.crew(e);
        if (c == null || !c.isConscious() || c.held || !c.wearsSuits()) {
            suiting = 0f;
            atLocker = AtLocker.NONE;
            warned = false;
            return;
        }
        GridPositionComponent p = crew.position(e);
        ShipGridComponent grid = crew.grid(p.ship);
        if (grid == null) return;
        boolean thin = !TaskRules.isBreathable(grid, p.tileX(), p.tileY());
        fillSuit(e, c, p, grid, thin, dt);
        fillAtConsole(e, c, dt);
        if (thin && !warned) ctx.notices.post(c.suited ? THIN_AIR : NO_SUIT, NoticeLog.Kind.WARNING);
        warned = thin;
        stepAtLocker(c, p, grid, dt);
    }

    /**
     * Fills the suit of a figure seated at one of its ship's consoles ({@link SeatComponent}), from
     * full in the crew's refill time: the ship's stations feed a worn suit while they are manned.
     */
    private void fillAtConsole(Entity e, CrewMemberComponent c, float dt) {
        if (!c.suited || e.getComponent(SeatComponent.class) == null) return;
        float full = c.suitCapacity();
        c.suitAir = Math.min(full, c.suitAir + full / c.def.suitRefillTime * dt);
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
        ctx.notices.post(SUIT_FILLED, NoticeLog.Kind.INFO);
    }
}
