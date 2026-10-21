package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;

/**
 * What each kind of task needs: whether it still needs doing, where a crew member stands
 * to do it, and which tiles are safe to breathe on.
 */
public final class TaskRules {

    /** Minimum pressure ratio of a safe tile. */
    public static final float SAFE_PRESSURE = 0.75f;
    /** Minimum oxygen ratio of a safe tile. */
    public static final float SAFE_OXYGEN = 0.75f;
    /** Above this temperature a tile is not safe, K. */
    public static final float SAFE_TEMPERATURE = 320f;

    private TaskRules() {}

    /**
     * False once the job is done (fire out, breach sealed, leak patched, system repaired, casualty
     * safe or dead), or can't be done (no patch kits left).
     */
    public static boolean isStillNeeded(CrewTask task, ShipGridComponent grid, CrewService crew) {
        Tile t = grid.getTile(task.x, task.y);
        switch (task.kind) {
            case EXTINGUISH:
                return t != null && t.isBurning();
            case SEAL_BREACH:
                return t != null && t.isHullBreach();
            case PATCH_LEAK: {
                PatchLeakTask p = (PatchLeakTask) task;
                return grid.patchKits > 0 && LeakPatches.leaks(grid, p.x, p.y, p.side);
            }
            case REPAIR: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && s.health < 1f;
            }
            case MAN_CONSOLE: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && !s.isDestroyed();
            }
            case RESET_SHORT: {
                if (((ResetShortTask) task).subsystem == null) return t != null && t.shorted;
                SubsystemComponent s = subsystemOf(task);
                return s != null && s.shorted;
            }
            case ISOLATE_TANK: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && DamageStates.needsIsolating(s);
            }
            case RESCUE: {
                CrewMemberComponent c = crew.crew(((RescueTask) task).casualty);
                // Still needed while being carried: the rescue is under way.
                if (c == null || c.state != CrewState.UNCONSCIOUS) return false;
                if (!(task instanceof MedbayCarryTask) || c.carriedBy != null) return true;
                GridPositionComponent p = crew.position(((RescueTask) task).casualty);
                return !isMedbay(grid, p.tileX(), p.tileY()) && freeBed(grid, crew, p.ship, null) != null;
            }
            case TREAT: {
                Entity patient = ((TreatTask) task).patient;
                CrewMemberComponent c = crew.crew(patient);
                if (c == null || c.carriedBy != null) return false;
                if (c.state == CrewState.UNCONSCIOUS) return true;
                // A light wound on someone on their feet waits for the fight to be over
                return c.isConscious() && c.wound == Wound.LIGHT && !atBattleStations(crew, patient);
            }
            case STABILISE: {
                CrewMemberComponent c = crew.crew(((StabiliseTask) task).patient);
                return c != null && c.state != CrewState.DEAD && c.wound == Wound.CRITICAL && !c.stabilised
                        && c.carriedBy == null;
            }
            case CARRY:
                return ((CarryTask) task).job.isOpen();
            case YARD:
                return ((YardTask) task).job.isOpen();
            case FORCE_DOOR:
                return ((ForceDoorTask) task).job.isOpen();
            case EVACUATE:
                return ((EvacuateTask) task).isOpen();
            case BOARD:
                return ((BoardTask) task).job.isOpen();
            default:
                return true;
        }
    }

    static SubsystemComponent subsystemOf(CrewTask task) {
        Entity e = task instanceof RepairTask ? ((RepairTask) task).subsystem
                : task instanceof ManConsoleTask ? ((ManConsoleTask) task).console
                : task instanceof ResetShortTask ? ((ResetShortTask) task).subsystem
                : task instanceof IsolateTankTask ? ((IsolateTankTask) task).tank : null;
        return e == null ? null : e.getComponent(SubsystemComponent.class);
    }

    /** The tile the task is about right now (a casualty moves while carried). */
    public static int targetX(CrewTask task, CrewService crew) {
        Entity e = mobileTarget(task);
        GridPositionComponent p = e == null ? null : crew.position(e);
        return p != null ? p.tileX() : task.x;
    }

    public static int targetY(CrewTask task, CrewService crew) {
        Entity e = mobileTarget(task);
        GridPositionComponent p = e == null ? null : crew.position(e);
        return p != null ? p.tileY() : task.y;
    }

    /**
     * The craft the task's (x, y) is on, when the task names one: yard work and boarding on the
     * craft worked on, a carry's pick-up on the craft it comes from. Null for a task on the crew
     * member's own craft (or wherever they stand).
     */
    public static Entity siteOf(CrewTask task) {
        if (task instanceof YardTask) return ((YardTask) task).ship;
        if (task instanceof CarryTask) return ((CarryTask) task).from;
        if (task instanceof BoardTask) return ((BoardTask) task).ship;
        return null;
    }

    private static Entity mobileTarget(CrewTask task) {
        if (task instanceof RescueTask) return ((RescueTask) task).casualty;
        if (task instanceof TreatTask) return ((TreatTask) task).patient;
        if (task instanceof StabiliseTask) return ((StabiliseTask) task).patient;
        return null;
    }

    /** True if a crew member standing on (x, y) can work the task. */
    public static boolean isWorkSpot(CrewTask task, ShipGridComponent grid, GridNavGraph nav, CrewService crew, int x, int y) {
        if (!nav.isWalkable(x, y)) return false;
        int tx = targetX(task, crew), ty = targetY(task, crew);
        switch (task.kind) {
            case EXTINGUISH: {
                // From the tile itself or a neighbour that air (and so a hose) reaches
                if (x == tx && y == ty) return true;
                Tile here = grid.getTile(x, y);
                return isAdjacent(x, y, tx, ty) && grid.canPass(x, y, tx, ty) && !here.isBurning();
            }
            case SEAL_BREACH:
                // From a tile beside it, or reaching across a machine that stands against it (a
                // breach in the hull behind an engine has no free tile beside it)
                if (isAdjacent(x, y, tx, ty)) return !grid.isEdgeBlocked(x, y, tx, ty);
                return reachesAcrossMount(grid, x, y, tx, ty);
            case PATCH_LEAK: {
                // A thin wall from the air tile beside it; a wall from a tile next to it, or reaching
                // across a machine that stands against it; a deck plate from on it or next to it
                Side side = ((PatchLeakTask) task).side;
                if (side != null) return x == tx && y == ty;
                Tile t = grid.getTile(tx, ty);
                if (t != null && t.isFloor() && x == tx && y == ty) return true;
                if (isAdjacent(x, y, tx, ty)) return !grid.isEdgeBlocked(x, y, tx, ty);
                return reachesAcrossMount(grid, x, y, tx, ty);
            }
            case RESET_SHORT:
                if (((ResetShortTask) task).subsystem == null) {
                    // A conduit tile: from on it, or beside it
                    if (x == tx && y == ty) return true;
                    return isAdjacent(x, y, tx, ty) && !grid.isEdgeBlocked(x, y, tx, ty);
                }
                // fall through: a system is worked like a repair
            case ISOLATE_TANK:
            case REPAIR:
            case MAN_CONSOLE: {
                SubsystemComponent s = subsystemOf(task);
                if (s == null) return false;
                for (Side side : Side.values()) {
                    int nx = x + side.dx, ny = y + side.dy;
                    if (s.covers(nx, ny) && !grid.isEdgeBlocked(x, y, nx, ny)) return true;
                }
                return false;
            }
            case TREAT:
            case STABILISE:
                if (isAdjacent(x, y, tx, ty) && grid.canPass(x, y, tx, ty)) return true;
                return x == tx && y == ty;
            case EVACUATE:
                return ((EvacuateTask) task).inPod(x, y);
            default:
                return x == tx && y == ty;
        }
    }

    /** A predicate for {@link GridPathfinder#nearest} over the task's work spots. */
    public static GridPathfinder.TilePredicate workSpots(CrewTask task, ShipGridComponent grid, GridNavGraph nav, CrewService crew) {
        return (x, y) -> isWorkSpot(task, grid, nav, crew, x, y);
    }

    /** True if the air on (x, y) is fit to breathe: pressure and oxygen enough, whatever the heat. */
    public static boolean isBreathable(ShipGridComponent grid, int x, int y) {
        return grid.pressureRatioAt(x, y) >= SAFE_PRESSURE && grid.oxygenRatioAt(x, y) >= SAFE_OXYGEN;
    }

    /**
     * True if working at (x, y) means being out of breathable air: a tile whose air is too thin or
     * foul, or one with no air (a hull breach, a sealed tile) beside such a tile or open to space.
     */
    public static boolean needsSuit(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null) return false;
        if (t.holdsAir()) return !isBreathable(grid, x, y);
        if (t.isSpaceExposed()) return true;
        for (Side s : Side.values()) {
            Tile n = grid.getTile(x + s.dx, y + s.dy);
            if (n != null && n.holdsAir() && !isBreathable(grid, x + s.dx, y + s.dy)) return true;
        }
        return false;
    }

    /**
     * Room pressure, kPa, below which a hand at a console whose room is losing air puts their suit on
     * where they sit off battle stations ({@link #suitsUpAtPost}). Round 9.
     */
    public static final float SUIT_AT_POST_KPA = 50f;
    /**
     * The faction whose crews get round 9's suit care ({@link #suitCare}): the player's. Given to the
     * aces' crews too, it kept them alive and won the railgun ace four more of twenty fights.
     */
    public static final int SUIT_CARE_FACTION = 0;

    /**
     * What resetting a short on a system the ship fights with ({@link #vital}) is worth to the player's
     * crews at battle stations ({@link #priority}): above sealing a breach, below a shorted reactor
     * and rescues. Round 10: seed 9's career lost Iron Dagny with its helm and two gun mounts shorted
     * at 16 s and still dark 45 s later, while all four crew sealed breaches (80) ahead of the resets (50).
     */
    public static final float VITAL_RESET_PRIORITY = 84f;

    /**
     * The priority {@code task} is chosen by on {@code board}: its own, except a reset of a short on a
     * system the ship fights with, while the player's crews are at battle stations
     * ({@link #VITAL_RESET_PRIORITY}).
     */
    public static float priority(ShipTaskBoardComponent board, CrewTask task) {
        if (task instanceof ResetShortTask && board.battleStations && board.faction == SUIT_CARE_FACTION
                && task.priority < VITAL_RESET_PRIORITY && vital(task)) return VITAL_RESET_PRIORITY;
        if (task instanceof RepairTask && board.faction == SUIT_CARE_FACTION && task.priority < ResetShortTask.REACTOR_PRIORITY
                && reactorOut(((RepairTask) task).subsystem)) return ResetShortTask.REACTOR_PRIORITY;
        if (task instanceof StabiliseTask && board.faction == SUIT_CARE_FACTION && task.priority < URGENT_STABILISE_PRIORITY) {
            CrewMemberComponent c = ((StabiliseTask) task).patient.getComponent(CrewMemberComponent.class);
            if (c != null && c.wound == Wound.CRITICAL && !c.stabilised && c.bleedOut <= LAST_CHANCE) return URGENT_STABILISE_PRIORITY;
        }
        return task.priority;
    }

    /**
     * What stopping the bleeding of one of the player's crews with no more than {@link #LAST_CHANCE}
     * to live is worth ({@link #priority}): before anything else, an engineer's breach (80 at 1.5) or
     * shorted reactor (85 at 1.5) and a medic's rescue (90 at 1.5) included. Round 10: in the frigate's
     * raids on seeds 10 and 12 three crew went critical, were hit again where they lay (each hit halves what is left, and opens a
     * wound stopped before), and bled out with 34 to 66 s left, one of them after the fight.
     */
    public static final float URGENT_STABILISE_PRIORITY = 110f;

    /**
     * True if {@code subsystem} is a reactor damaged past working ({@link SubsystemComponent#isDisabled}):
     * the player's crews mend it as they would reset its short ({@link #priority}). Round 10: with the
     * guns' shorts reset first, seed 9's corvette sat with its reactor wrecked and the crew on the turret,
     * and was towed in after a fight it had won (25,500 cr, and 22,202 cr short for the gunship).
     */
    static boolean reactorOut(Entity subsystem) {
        SubsystemComponent s = subsystem == null ? null : subsystem.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemComponent.SubsystemType.REACTOR && s.isDisabled();
    }

    /**
     * Whether the player's crews sheltering aboard a docked craft go back aboard their own ship for a
     * casualty ({@link #goesBackAboard}). Round 10's seed 11: the medic and two hands stood idle on
     * Highport with full suits while one crewmate bled out aboard, never stabilised, and another's
     * suit ran dry where he lay.
     */
    public static final boolean SHELTER_GOES_BACK = true;

    /** Suit air, seconds, a crew member sheltering ashore needs to go back aboard for a casualty. */
    public static final float BACK_ABOARD_AIR = 240f;

    /**
     * True if {@code crew}, of {@code board}'s ship but aboard a docked craft, may go back aboard for
     * {@code task}: the player's crews only ({@link #suitCare}), to stop a bleed or carry a casualty
     * out, with a suit and {@link #BACK_ABOARD_AIR} or more in it.
     */
    public static boolean goesBackAboard(CrewMemberComponent crew, CrewTask task, ShipTaskBoardComponent board) {
        return SHELTER_GOES_BACK && suitCare(board) && (task.kind == CrewTask.Kind.STABILISE || task.kind == CrewTask.Kind.RESCUE)
                && crew.def.suitAir > 0f && crew.suitAir >= BACK_ABOARD_AIR;
    }

    /** True if {@code board}'s crew suit up at their posts and fill a casualty's suit (round 9). */
    public static boolean suitCare(ShipTaskBoardComponent board) {
        return board != null && board.faction == SUIT_CARE_FACTION;
    }

    /**
     * True if an unsuited hand at a post on (x, y) should put their suit on there, still holding the
     * post (round 9): at battle stations with suits ordered ({@link ShipTaskBoardComponent#battleSuits}),
     * as everyone else aboard already has, or with their room losing air ({@link #losingAir}) and below
     * {@link #SUIT_AT_POST_KPA}. Round 8's wound survey: a gunner sat at the weapons from before the
     * fight began, so never suited up for it ({@code SuitUp} runs only as a task is taken), and
     * suffocated there as the cabin vented. A steady cabin, however thin, never empties a console.
     */
    public static boolean suitsUpAtPost(ShipGridComponent grid, ShipTaskBoardComponent board, int x, int y) {
        if (!suitCare(board)) return false;
        if (board.battleSuits()) return true;
        Compartment room = grid.compartmentAt(x, y);
        if (room == null) return needsSuit(grid, x, y);
        return room.pressure() < SUIT_AT_POST_KPA && losingAir(grid, room);
    }

    /** True while more air leaves {@code room} than comes in, by more than {@link ShipGridComponent#VENTING_HAZARD_FLOW}, as of the last hazard tick. */
    public static boolean losingAir(ShipGridComponent grid, Compartment room) {
        float out = 0f;
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            // Each opening is listed from both its sides, so its own side's flow is enough
            if (e.room == room) out += e.flow;
        }
        return out > ShipGridComponent.VENTING_HAZARD_FLOW;
    }

    /** True if a crew member can breathe safely on (x, y): pressurized, oxygenated, not burning or hot. */
    public static boolean isSafe(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.holdsAir() || t.isBurning() || t.temperature > SAFE_TEMPERATURE) return false;
        return grid.pressureRatioAt(x, y) >= SAFE_PRESSURE && grid.oxygenRatioAt(x, y) >= SAFE_OXYGEN;
    }

    /** True if (x, y) is a medbay bed: medbay deck that is safe to lie on. */
    public static boolean isMedbay(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        return t != null && t.covering == FloorCovering.MEDBAY;
    }

    /**
     * A free bed in {@code ship}'s medbay: a safe medbay tile nobody else lies on ({@code patient}
     * may), as {x, y}; null if every bed is taken or the ship has none.
     */
    public static int[] freeBed(ShipGridComponent grid, CrewService crew, Entity ship, Entity patient) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (isMedbay(grid, x, y) && isSafe(grid, x, y) && !bedTaken(crew, ship, patient, x, y)) return new int[]{x, y};
            }
        }
        return null;
    }

    /** True if someone other than {@code patient} lies down on (x, y) aboard {@code ship}. */
    public static boolean bedTaken(CrewService crew, Entity ship, Entity patient, int x, int y) {
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e == patient) continue;
            CrewMemberComponent c = crew.crew(e);
            GridPositionComponent p = crew.position(e);
            if (c.state == CrewState.UNCONSCIOUS && c.carriedBy == null && p.ship == ship && p.tileX() == x && p.tileY() == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * A casualty bleeding out or suffocating with this long or less left is gone in after under fire all the same
     * ({@link #waitsOutFire}), seconds.
     */
    public static final float LAST_CHANCE = 120f;

    /**
     * True if {@code task} lies where the fire is falling on its ship ({@link
     * ShipTaskBoardComponent#underFire}) and waits for the fight to end: damage control, repairs and
     * casualties alike, except a casualty with no more than {@link #LAST_CHANCE} to live, bleeding out
     * or down in bad air with that much suit air or none. Manning a
     * console, orders, work away from the ship and damage control on what the ship fights with
     * ({@link #vital}) never wait.
     */
    public static boolean waitsOutFire(CrewTask task, ShipTaskBoardComponent board, CrewService crew) {
        if (board != null && !board.keepsClearOfFire) return false;
        if (board == null || !board.battleStations || board.hitTiles.size == 0) return false;
        switch (task.kind) {
            case MAN_CONSOLE: case CARRY: case YARD: case FORCE_DOOR: case EVACUATE: case BOARD: return false;
            default: break;
        }
        if (vital(task)) return false;
        if (!board.underFire(targetX(task, crew), targetY(task, crew))) return false;
        Entity casualty = mobileTarget(task);
        CrewMemberComponent c = crew.crew(casualty);
        if (c == null) return true;
        boolean bleeding = c.wound == Wound.CRITICAL && !c.stabilised && c.treatedBy == null && c.bleedOut <= LAST_CHANCE;
        // Down on air not fit to breathe, with little or no suit air: suffocating
        GridPositionComponent p = crew.position(casualty);
        ShipGridComponent grid = p == null ? null : crew.grid(p.ship);
        boolean suffocating = grid != null && !c.isConscious() && !isBreathable(grid, p.tileX(), p.tileY())
                && (!c.breathesFromSuit() || c.suitAir <= LAST_CHANCE);
        return !bleeding && !suffocating;
    }

    /**
     * Resetting a short or repairing a system the ship fights with: its reactor and power conduits,
     * shields, guns, helm, weapons console and engines. Round 8: kept out of the fire too, the crew
     * left the corvette's shorted power dark and Red Kestrel, beaten in 36 s before, won in 69 s.
     */
    static boolean vital(CrewTask task) {
        Entity subsystem;
        if (task instanceof ResetShortTask) subsystem = ((ResetShortTask) task).subsystem;
        else if (task instanceof RepairTask) subsystem = ((RepairTask) task).subsystem;
        else return false;
        // A shorted conduit carries the power to all of them
        if (subsystem == null) return task instanceof ResetShortTask;
        SubsystemComponent s = subsystem.getComponent(SubsystemComponent.class);
        if (s == null) return false;
        switch (s.type) {
            case REACTOR: case SHIELD_EMITTER: case TURRET_MOUNT: case HELM: case WEAPONS: case ENGINE: return true;
            default: return false;
        }
    }

    /** True while {@code e}'s ship is at battle stations. */
    static boolean atBattleStations(CrewService crew, Entity e) {
        GridPositionComponent p = crew.position(e);
        ShipTaskBoardComponent board = p == null ? null : crew.board(p.ship);
        return board != null && board.battleStations;
    }

    /** How far, in tiles, a crew member can reach across machinery to a wall behind it. */
    public static final int REACH_ACROSS = 4;

    /**
     * True if (x, y) lies in a straight line from (tx, ty), at most {@link #REACH_ACROSS} tiles
     * off, with only machines' footprints between them and no thin wall in the way: someone
     * standing there can reach over the machinery to the wall behind it.
     */
    static boolean reachesAcrossMount(ShipGridComponent grid, int x, int y, int tx, int ty) {
        int dx = Integer.signum(tx - x), dy = Integer.signum(ty - y);
        int dist = Math.abs(tx - x) + Math.abs(ty - y);
        if ((dx != 0 && dy != 0) || dist < 2 || dist > REACH_ACROSS) return false;
        int px = x, py = y;
        for (int i = 1; i < dist; i++) {
            int mx = px + dx, my = py + dy;
            Tile mid = grid.getTile(mx, my);
            if (mid == null || mid.mount == null || mid.isWall() || grid.isEdgeBlocked(px, py, mx, my)) return false;
            px = mx;
            py = my;
        }
        return !grid.isEdgeBlocked(px, py, tx, ty);
    }

    private static boolean isAdjacent(int x, int y, int nx, int ny) {
        return Math.abs(x - nx) + Math.abs(y - ny) == 1;
    }
}
