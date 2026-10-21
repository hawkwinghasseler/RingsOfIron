package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * The job blackboard of one ship, attached to the ship entity next to its
 * ShipGridComponent. Other systems post tasks; idle crew claim the one that suits them
 * best (see {@link #best}).
 *
 * Finished and cancelled tasks are dropped from {@link #tasks} by {@link #prune()}.
 *
 * Tasks are not saved. After a load the board starts empty and ShipHazardScanSystem
 * and CrewHealthSystem post the jobs that still exist.
 */
public class ShipTaskBoardComponent implements Component {

    /** A filter for {@link #best}, e.g. to skip tasks a crew member just found unreachable. */
    public interface TaskFilter {
        boolean accept(CrewTask task);
    }

    /** Distance penalty in {@link #best}, priority points per tile. */
    public static final float DISTANCE_WEIGHT = 0.5f;

    public final transient Array<CrewTask> tasks = new Array<>();
    /** The faction whose crew work this board. Boarders do not take the ship's jobs. */
    public int faction = 0;
    /**
     * Whether the crew suit up at battle stations, so a breach in their room doesn't suffocate them
     * (suits slow them: {@link CrewDef#suitSpeedFactor}, {@link CrewDef#suitWorkFactor}). On by
     * default; the player toggles it for their own ship.
     */
    public boolean suitsAtBattleStations = true;
    /** True while the ship is in a fight: set each tick by the game, so not saved. */
    public transient boolean battleStations;
    /**
     * True while the ship is flying and the player isn't at its helm: set each tick by the game, so
     * not saved. Then a crew member who mans the helm takes it ahead of sitting with a patient
     * ({@link #wantedHelm}). Round 7: with two crew knocked out, the one crewman treated them for
     * days, and the player standing up from the helm found nobody to hand it to.
     */
    public transient boolean helmWanted;

    /**
     * Tiles within this many (in x or y) of where a crew member was hit by weapon fire are under fire
     * for the rest of the fight ({@link #underFire}).
     */
    public static final int FIRE_ZONE_RADIUS = 2;
    /**
     * Where crew were hit by weapon fire in this fight, as x, y pairs: set by their wounds
     * ({@link CrewService#applyDamage}), cleared by {@link #standDown} once the ship is off battle
     * stations. Not saved.
     */
    public final transient IntArray hitTiles = new IntArray();

    /** Notes a crew member hit by weapon fire at (x, y). */
    public void hitAt(int x, int y) {
        for (int i = 0; i < hitTiles.size; i += 2) if (hitTiles.get(i) == x && hitTiles.get(i + 1) == y) return;
        hitTiles.add(x);
        hitTiles.add(y);
    }

    /**
     * True while at battle stations if (x, y) is within {@link #FIRE_ZONE_RADIUS} of where a crew
     * member was hit in this fight (round 8). Seed 7's ace raked one hull section: a hand went to patch
     * the leak there and was cut down, and the rest went in one by one, to stop her bleeding, seal the
     * breach and mend the turret, until all four were down and bled out.
     */
    public boolean underFire(int x, int y) {
        if (!battleStations) return false;
        for (int i = 0; i < hitTiles.size; i += 2) {
            if (Math.abs(hitTiles.get(i) - x) <= FIRE_ZONE_RADIUS && Math.abs(hitTiles.get(i + 1) - y) <= FIRE_ZONE_RADIUS) return true;
        }
        return false;
    }

    /** Forgets where the fire fell: the game calls it while the ship is off battle stations. */
    public void standDown() {
        hitTiles.clear();
    }

    /** True while the crew should keep their suits on for battle stations. */
    public boolean battleSuits() {
        return battleStations && suitsAtBattleStations;
    }

    /** Adds the task unless the same job is already active. */
    public boolean post(CrewTask task) {
        for (CrewTask t : tasks) {
            if (t.isActive() && t.sameJob(task)) return false;
        }
        task.status = CrewTask.Status.OPEN;
        task.claimedBy = null;
        tasks.add(task);
        return true;
    }

    /**
     * The open task a crew member of {@code role} at tile (x, y) should take: the highest
     * {@code priority × role affinity − distance × }{@link #DISTANCE_WEIGHT}, ignoring
     * tasks the role never does. Manning a console is what crew do when there is nothing
     * else: it is only chosen when no other task suits. Null if there is none.
     */
    public CrewTask best(CrewRole role, int x, int y, TaskFilter filter) {
        CrewTask t = best(role, x, y, filter, false);
        if (t == null || t.kind == CrewTask.Kind.TREAT) {
            CrewTask helm = wantedHelm(role, filter);
            if (helm != null) return helm;
        }
        return t != null ? t : best(role, x, y, filter, true);
    }

    /** The helm, open, while {@link #helmWanted} and {@code role} mans it; else null. */
    public CrewTask wantedHelm(CrewRole role, TaskFilter filter) {
        if (!helmWanted) return null;
        for (CrewTask t : tasks) {
            if (t.status != CrewTask.Status.OPEN || !(t instanceof ManConsoleTask)) continue;
            if (((ManConsoleTask) t).type() != SubsystemComponent.SubsystemType.HELM || role.affinity(t) <= 0f) continue;
            if (filter == null || filter.accept(t)) return t;
        }
        return null;
    }

    private CrewTask best(CrewRole role, int x, int y, TaskFilter filter, boolean consoles) {
        CrewTask best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (CrewTask t : tasks) {
            if (t.status != CrewTask.Status.OPEN || (t.kind == CrewTask.Kind.MAN_CONSOLE) != consoles) continue;
            float affinity = role.affinity(t);
            if (affinity <= 0f) continue;
            if (filter != null && !filter.accept(t)) continue;
            float score = t.priority * affinity - DISTANCE_WEIGHT * (Math.abs(t.x - x) + Math.abs(t.y - y));
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    public boolean claim(CrewTask task, Entity crew) {
        if (task.status != CrewTask.Status.OPEN) return false;
        task.status = CrewTask.Status.CLAIMED;
        task.claimedBy = crew;
        return true;
    }

    /** Puts a claimed task back on the board for someone else. */
    public void release(CrewTask task) {
        if (task.status == CrewTask.Status.CLAIMED) task.status = CrewTask.Status.OPEN;
        task.claimedBy = null;
    }

    public void complete(CrewTask task) {
        task.status = CrewTask.Status.DONE;
        task.claimedBy = null;
    }

    public void cancel(CrewTask task) {
        task.status = CrewTask.Status.CANCELLED;
        task.claimedBy = null;
    }

    /** Removes finished and cancelled tasks. */
    public void prune() {
        for (int i = tasks.size - 1; i >= 0; i--) {
            if (!tasks.get(i).isActive()) tasks.removeIndex(i);
        }
    }

    public int activeCount() {
        int n = 0;
        for (CrewTask t : tasks) if (t.isActive()) n++;
        return n;
    }
}
