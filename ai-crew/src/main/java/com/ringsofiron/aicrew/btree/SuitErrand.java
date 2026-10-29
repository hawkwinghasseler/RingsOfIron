package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * Getting into a suit and out of one, at a suit locker (interior-grid's {@link SuitLockers}). A suit
 * not being worn hangs in a locker, so a crew member who wants one walks to a locker with one in it,
 * stands at its front and takes the suit's time to get in ({@link CrewMemberComponent#suitTime}); one
 * whose suit can come off walks to a locker with room for it and hangs it up, in half that. A pressure
 * suit first; an EVA suit, slow to get into and clumsy indoors, only when the air is going and no
 * pressure suit is to be had.
 *
 * <p>Each call is one step: RUNNING while walking or dressing, SUCCEEDED once done, FAILED when no
 * locker aboard can be reached that has a suit (or room for one): the crew member then looks again
 * after {@link #RETRY}. Progress is kept on the crew member ({@link CrewMemberComponent#suiting},
 * {@link CrewMemberComponent#unsuiting}), so a save part way through keeps it.
 */
final class SuitErrand {
    /** Seconds before a crew member who found no suit to be had, or nowhere to hang theirs, looks again. */
    static final float RETRY = 20f;

    private static final Vector2 FRONT = new Vector2();

    private SuitErrand() {
    }

    /**
     * One step of getting {@code a} into a suit: a pressure suit, or with {@code evaToo} an EVA suit
     * when no pressure suit can be reached and they still have air to breathe while they get into it
     * (in thin air they would pass out first). SUCCEEDED at once if they have one on or wear none.
     */
    static Status suitUp(CrewAgent a, boolean evaToo) {
        CrewMemberComponent c = a.crew;
        if (c.suited || !c.wearsSuits()) {
            c.suiting = 0f;
            return Status.SUCCEEDED;
        }
        ShipGridComponent grid = a.grid();
        if (grid == null || a.time < a.suitBlockedUntil) return Status.FAILED;
        // An EVA suit takes too long to get into to be any use once the air is already going
        boolean eva = evaToo && TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY());
        Furnishing at = lockerAtHand(a, grid, Suit.Kind.IVA, true);
        Suit.Kind kind = Suit.Kind.IVA;
        CrewAgent.Travel go = null;
        if (at == null) {
            // A pressure suit a few steps off before an EVA suit at hand
            go = toLocker(a, grid, Suit.Kind.IVA, true);
            if (go == CrewAgent.Travel.UNREACHABLE && eva) {
                at = lockerAtHand(a, grid, Suit.Kind.EVA, true);
                if (at == null) {
                    go = toLocker(a, grid, Suit.Kind.EVA, true);
                    kind = Suit.Kind.EVA;
                }
            }
        }
        if (at != null) {
            kind = at.type.holds;
            if (c.suiting == 0f) a.service.physics.standAt(a.entity, SuitLockers.front(at, FRONT).x, FRONT.y);
            c.activity = dressing(kind);
            c.suiting += a.dt;
            c.showProgress(c.suiting, c.suitTime(kind));
            if (c.suiting < c.suitTime(kind)) return Status.RUNNING;
            c.wear(SuitLockers.take(at, kind));
            c.activity = "idle";
            return Status.SUCCEEDED;
        }
        c.suiting = 0f;
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.suitBlockedUntil = a.time + RETRY;
            return Status.FAILED;
        }
        c.activity = "going for " + article(kind);
        return Status.RUNNING;
    }

    /**
     * One step of getting {@code a} into an EVA suit with at least {@code minAir} seconds of air in it,
     * to go outside: the fullest at the locker they stand at, else they walk to the nearest locker
     * with one. Only where the air is fit to breathe while they get into it. SUCCEEDED at once if they
     * have one on; FAILED with another suit on, or no such suit to be had.
     */
    static Status suitUpEva(CrewAgent a, float minAir) {
        CrewMemberComponent c = a.crew;
        if (c.suited) return c.suitKind == Suit.Kind.EVA ? Status.SUCCEEDED : Status.FAILED;
        ShipGridComponent grid = a.grid();
        if (grid == null || !c.wearsSuits() || !TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY())) return Status.FAILED;
        Furnishing at = a.pos.isMoving() || a.pos.walkway != null || a.pos.crossing != null ? null
                : SuitLockers.within(grid, a.pos.x, a.pos.y, f -> hasEvaSuit(f, minAir));
        if (at != null) {
            if (c.suiting == 0f) a.service.physics.standAt(a.entity, SuitLockers.front(at, FRONT).x, FRONT.y);
            c.activity = dressing(Suit.Kind.EVA);
            c.suiting += a.dt;
            c.showProgress(c.suiting, c.suitTime(Suit.Kind.EVA));
            if (c.suiting < c.suitTime(Suit.Kind.EVA)) return Status.RUNNING;
            c.wear(SuitLockers.take(at, Suit.Kind.EVA));
            c.activity = "idle";
            return Status.SUCCEEDED;
        }
        c.suiting = 0f;
        GridNavGraph nav = a.pathfinder().graph;
        CrewAgent.Travel go = a.travel(a.pos.ship, (x, y) -> nav.isWalkable(x, y)
                && SuitLockers.on(grid, x, y, f -> hasEvaSuit(f, minAir)) != null);
        if (go == CrewAgent.Travel.UNREACHABLE) return Status.FAILED;
        c.activity = "going for " + article(Suit.Kind.EVA);
        return Status.RUNNING;
    }

    /** True if locker {@code f} works and its fullest EVA suit has at least {@code minAir} seconds of air. */
    static boolean hasEvaSuit(Furnishing f, float minAir) {
        if (!SuitLockers.hasSuit(f, Suit.Kind.EVA)) return false;
        for (int i = 0; i < f.suits.size; i++) {
            Suit s = f.suits.get(i);
            if (s.kind == Suit.Kind.EVA && s.air >= minAir) return true;
        }
        return false;
    }

    /**
     * One step of {@code a} taking their suit off and hanging it in a locker with room for it, aboard
     * the craft they work for. SUCCEEDED at once if they have none on.
     */
    static Status stow(CrewAgent a) {
        CrewMemberComponent c = a.crew;
        if (!c.suited) {
            c.unsuiting = 0f;
            return Status.SUCCEEDED;
        }
        ShipGridComponent grid = a.grid();
        // A suit goes back aboard the craft it belongs to, not into a station's locker
        if (grid == null || a.time < a.stowBlockedUntil || a.pos.ship != a.service.homeOf(a.entity)) return Status.FAILED;
        Suit.Kind kind = c.suitKind;
        // Only where the air is fit to take it off in
        Furnishing at = TaskRules.isSafe(grid, a.pos.tileX(), a.pos.tileY()) ? lockerAtHand(a, grid, kind, false) : null;
        if (at != null) {
            if (c.unsuiting == 0f) a.service.physics.standAt(a.entity, SuitLockers.front(at, FRONT).x, FRONT.y);
            c.activity = undressing(kind);
            c.unsuiting += a.dt;
            c.showProgress(c.unsuiting, c.suitTime(kind) * 0.5f);
            if (c.unsuiting < c.suitTime(kind) * 0.5f) return Status.RUNNING;
            Suit off = c.takeOff();
            if (!SuitLockers.stow(at, off)) c.wear(off);
            c.activity = "idle";
            return Status.SUCCEEDED;
        }
        c.unsuiting = 0f;
        CrewAgent.Travel go = toLocker(a, grid, kind, false);
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.stowBlockedUntil = a.time + RETRY;
            return Status.FAILED;
        }
        c.activity = "taking " + article(kind) + " back to its locker";
        return Status.RUNNING;
    }

    /**
     * The locker within {@code a}'s reach, standing still where they are, with a suit of {@code kind}
     * ({@code take}) or room for one; null if none, or while they walk.
     */
    static Furnishing lockerAtHand(CrewAgent a, ShipGridComponent grid, Suit.Kind kind, boolean take) {
        if (a.pos.isMoving() || a.pos.walkway != null || a.pos.crossing != null) return null;
        return SuitLockers.within(grid, a.pos.x, a.pos.y, f -> take ? SuitLockers.hasSuit(f, kind) : SuitLockers.hasRoom(f, kind));
    }

    /**
     * A step toward the nearest locker aboard with a suit of {@code kind} ({@code take}), or with room
     * for one on a tile safe to take a suit off on.
     */
    static CrewAgent.Travel toLocker(CrewAgent a, ShipGridComponent grid, Suit.Kind kind, boolean take) {
        GridNavGraph nav = a.pathfinder().graph;
        return a.travel(a.pos.ship, (x, y) -> nav.isWalkable(x, y) && (take || TaskRules.isSafe(grid, x, y))
                && SuitLockers.on(grid, x, y, f -> take ? SuitLockers.hasSuit(f, kind) : SuitLockers.hasRoom(f, kind)) != null);
    }

    /** "putting on a pressure suit", "getting into an EVA suit". */
    static String dressing(Suit.Kind kind) {
        return kind == Suit.Kind.EVA ? "getting into an EVA suit" : "putting on a pressure suit";
    }

    /** "taking off a pressure suit", "getting out of an EVA suit". */
    static String undressing(Suit.Kind kind) {
        return kind == Suit.Kind.EVA ? "getting out of an EVA suit" : "taking off a pressure suit";
    }

    /** "a pressure suit", "an EVA suit". */
    static String article(Suit.Kind kind) {
        return (kind == Suit.Kind.EVA ? "an " : "a ") + kind.noun;
    }
}
