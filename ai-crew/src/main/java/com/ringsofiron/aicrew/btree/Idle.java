package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Stands by. Heads home if aboard another craft, and moves off a tile that is not safe to
 * breathe on. At battle stations ({@link ShipTaskBoardComponent#battleSuits}) they fetch a
 * pressure suit from a suit locker and keep it on; otherwise a suited crew member takes the suit
 * back to a locker with room for it after {@link #SUIT_OFF_DELAY} seconds standing by on a safe
 * tile aboard their own ship, and keeps it on while none can be reached ({@link SuitErrand}).
 * With nowhere aboard safe they fetch a suit, a pressure suit or an EVA suit, before anything
 * else. Aboard their own ship, out of their suit and with nothing to do, they use its
 * furnishings ({@link Downtime}). Never finishes.
 */
public class Idle extends CrewLeaf {
    /** Seconds on a safe tile with nothing to do before a suit goes back to its locker. */
    public static final float SUIT_OFF_DELAY = 5f;
    /** Seconds idle aboard another craft with no dock to their own before they go back across on EVA. */
    public static final float STRANDED_SECONDS = 3f;
    private float calm;
    /** On the way to hang their suit up, or taking it off. */
    private boolean stowing;
    private final Downtime downtime = new Downtime();
    /** Seconds idle aboard a craft with no dock to their own, before going back across on EVA. */
    private float stranded;
    private final EvaTasks.Departure homeward = new EvaTasks.Departure();

    @Override
    public void start() {
        calm = 0f;
        stowing = false;
        stranded = 0f;
        downtime.reset(getObject());
    }

    @Override
    public void end() {
        homeward.reset(getObject());
        if (getObject() != null) downtime.stop(getObject());
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        ShipGridComponent grid = a.grid();
        Entity home = a.service.homeOf(a.entity);
        // Sheltering aboard a docked craft while nowhere aboard their own is safe: they stay
        boolean sheltering = home != a.pos.ship && a.pos.crossing == null
                && TaskRules.isSafe(grid, a.pos.tileX(), a.pos.tileY()) && !CrewTasks.anywhereSafe(a.service.grid(home));
        if ((home != a.pos.ship || a.pos.crossing != null) && !sheltering) {
            ShipGridComponent homeGrid = a.service.grid(home);
            GridNavGraph nav = homeGrid == null ? null : a.service.pathfinder(homeGrid).graph;
            CrewAgent.Travel go = nav == null ? CrewAgent.Travel.UNREACHABLE : a.travel(home, nav::isWalkable);
            if (go == CrewAgent.Travel.GOING) {
                a.crew.activity = "heading back";
                stranded = 0f;
                return Status.RUNNING;
            }
            // No dock to walk back across: home on EVA, once it's clear nothing more is wanted here
            if (go == CrewAgent.Travel.UNREACHABLE && nav != null && a.pos.crossing == null && !a.service.joined(a.pos.ship, home)
                    && (stranded += a.dt) >= STRANDED_SECONDS && homeward.step(a, home, false) == Status.RUNNING) {
                return Status.RUNNING;
            }
        }
        int x = a.pos.tileX(), y = a.pos.tileY();
        // Nobody idles in a docked port's opening: it is the only way across, one tile wide; nor in
        // an airlock's chamber while someone wants it to cycle
        if (TaskRules.isSafe(grid, x, y) && (CrewTasks.onLink(grid, x, y) || EvaTasks.inAirlock(grid, x, y))) {
            if (!a.pos.isMoving() && a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty) && !CrewTasks.onLink(grid, tx, ty)
                    && !EvaTasks.inAirlock(grid, tx, ty), a.pathScratch)) {
                a.pos.setPath(a.pathScratch);
                a.crew.activity = "stepping clear of the port";
            }
            return Status.RUNNING;
        }
        if (TaskRules.isSafe(grid, x, y)) {
            ShipTaskBoardComponent board = a.board();
            boolean battle = board != null && board.battleSuits() && a.crew.wearsSuits();
            // Downtime: aboard their own ship, out of their suit, with nothing else to do (never a prisoner)
            if (!battle && !a.crew.suited && !a.crew.held && home == a.pos.ship && a.pos.crossing == null) {
                if (downtime.step(a)) return Status.RUNNING;
            } else if (downtime.using() != null) {
                downtime.stop(a);
            }
            if (!a.pos.isMoving()) a.crew.activity = "idle";
            if (battle) {
                // Battle stations: a suit from a locker, kept on
                calm = 0f;
                stowing = false;
                if (!a.crew.suited) SuitErrand.suitUp(a, false);
                return Status.RUNNING;
            }
            if (!a.crew.suited || home != a.pos.ship || a.pos.crossing != null) {
                calm = 0f;
                stowing = false;
                return Status.RUNNING;
            }
            if (!stowing && !a.pos.isMoving()) {
                calm += a.dt;
                stowing = calm >= SUIT_OFF_DELAY && a.time >= a.stowBlockedUntil;
            }
            if (stowing && SuitErrand.stow(a) != Status.RUNNING) {
                // Hung up; or nowhere to hang it, and kept on for now
                stowing = false;
                calm = 0f;
            }
            return Status.RUNNING;
        }
        calm = 0f;
        stowing = false;
        if (downtime.using() != null) downtime.stop(a);
        if (!a.pos.isMoving() || a.pos.pathBlocked) {
            if (a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
                a.pos.setPath(a.pathScratch);
                a.crew.activity = "leaving hazard";
                return Status.RUNNING;
            }
            // Nowhere aboard is safe (the ship holed past holding, its air gone): they fetch a suit,
            // and go aboard the craft it is docked to, a station's berth say, until it is
            if (!a.crew.suited && SuitErrand.suitUp(a, true) == Status.RUNNING) return Status.RUNNING;
            Entity shelter = CrewTasks.shelter(a);
            if (shelter == null) return Status.RUNNING;
            ShipGridComponent there = a.service.grid(shelter);
            if (a.travel(shelter, (tx, ty) -> TaskRules.isSafe(there, tx, ty)) == CrewAgent.Travel.GOING) {
                a.crew.activity = "going aboard " + CrewTasks.SHELTER;
            }
        }
        return Status.RUNNING;
    }
}
