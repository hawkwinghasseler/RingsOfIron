package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent.EvaOrder;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * The player's say over who goes out on EVA. Left alone, crew go out for work outside as it comes
 * (an RCS block or a holed hull tile to mend, a jammed docking collar, a shipmate adrift), but not
 * at battle stations. Over that:
 * <ul>
 *   <li>The ship's hold ({@link ShipTaskBoardComponent#evaHeld}): nobody goes out on their own, and
 *       anyone out comes back in.</li>
 *   <li>Send one out ({@link EvaOrder#OUT}): they put down what they are doing and go out for the
 *       work outside that is in their trade, at battle stations or held in all the same, and take
 *       nothing else until they are back in. With no work outside for them, the order lapses.</li>
 *   <li>Call one in, or keep one in ({@link EvaOrder#IN}): back in now, and out for nothing until
 *       they are sent out or let out again.</li>
 * </ul>
 */
public final class EvaOrders {

    private EvaOrders() {
    }

    /** Sends {@code e} out for the work outside. Returns why not, or null. */
    public static String sendOut(CrewService service, Entity e) {
        CrewMemberComponent c = service.crew(e);
        if (c == null) return "No longer aboard";
        if (EvaSystem.of(e) != null && c.evaOrder != EvaOrder.IN) return c.name + " is out already";
        if (!c.isConscious()) return c.name + " is in no state to go out";
        if (!c.wearsSuits()) return c.name + " doesn't go out in a suit";
        if (!workFor(service, e)) return "There is no work outside for " + c.name;
        c.evaOrder = EvaOrder.OUT;
        return null;
    }

    /** Calls {@code e} back in from EVA, and keeps them in. Returns why not, or null. */
    public static String callIn(CrewService service, Entity e) {
        CrewMemberComponent c = service.crew(e);
        if (c == null) return "No longer aboard";
        c.evaOrder = EvaOrder.IN;
        return null;
    }

    /** Lets {@code e}, kept in, go out for work outside as it comes again. */
    public static void letOut(CrewService service, Entity e) {
        CrewMemberComponent c = service.crew(e);
        if (c != null && c.evaOrder == EvaOrder.IN) c.evaOrder = EvaOrder.NONE;
    }

    /** Holds everyone on {@code ship} in, or lets them out again. Returns whether they are held now. */
    public static boolean toggleHold(CrewService service, Entity ship) {
        ShipTaskBoardComponent board = service.board(ship);
        if (board == null) return false;
        board.evaHeld = !board.evaHeld;
        return board.evaHeld;
    }

    /**
     * True if there is work outside on the board of the ship {@code e} works for, open or theirs,
     * in their trade (their role takes it on at all), and not to fetch themselves.
     */
    public static boolean workFor(CrewService service, Entity e) {
        CrewMemberComponent c = service.crew(e);
        ShipTaskBoardComponent board = c == null ? null : service.board(service.homeOf(e));
        if (board == null) return false;
        for (CrewTask t : board.tasks) {
            if (!t.kind.isEva() || !t.isActive() || c.role.affinity(t) <= 0f) continue;
            if (t.status == CrewTask.Status.CLAIMED && t.claimedBy != e) continue;
            if (t instanceof RetrieveTask && ((RetrieveTask) t).castaway == e) continue;
            return true;
        }
        return false;
    }
}
