package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.Contract;
import com.ringsofiron.uihud.providers.ObjectiveProvider;

import java.util.Comparator;

/**
 * ui-hud's {@link ObjectiveProvider}: the contracts the player holds from the {@link ContractBoard},
 * each with its deadline, where it sends the ship next and how far along it is, soonest deadline
 * first; and the operation running, if one is.
 */
public class PlayerObjectives implements ObjectiveProvider {
    private static final Comparator<Objective> SOONEST = (a, b) -> Double.compare(deadline(a), deadline(b));

    private final GameContext ctx;
    private final ContractBoard contracts;
    private final NavTarget navTarget;

    public PlayerObjectives(GameContext ctx, ContractBoard contracts, NavTarget navTarget) {
        this.ctx = ctx;
        this.contracts = contracts;
        this.navTarget = navTarget;
    }

    @Override
    public void collect(ObjectiveFrame out) {
        double now = ctx.engine.getSimSeconds();
        String nav = navTarget.uuid();
        for (Contract c : contracts.active()) {
            Objective o = out.add();
            o.title = c.title();
            o.kind = kind(c.type);
            o.giver = c.giver == null ? "" : c.giver;
            o.station = c.stationName == null ? "" : c.stationName;
            o.state = c.status == Contract.Status.DONE ? State.DONE
                    : c.status == Contract.Status.FAILED ? State.FAILED
                    : homeward(c) ? State.RETURN : State.ACTIVE;
            if (c.deadline > 0 && Double.isFinite(c.deadline)) {
                o.timeLeft = c.deadline - now;
                o.late = o.timeLeft < 0;
            }
            String next = c.waypointName();
            o.next = next == null ? "" : next;
            String to = c.waypointUuid();
            o.nextIsNav = to != null && to.equals(nav);
            progress(o, c);
            o.reward = c.reward;
        }
        out.objectives.sort(SOONEST);
        ContractBoard.Operation op = contracts.operation();
        if (op != null) {
            out.operation = op.name == null ? "" : op.name;
            out.operationStep = op.step;
            out.operationSteps = op.steps.length;
        }
    }

    /** Past its far end, on the way back: a patrol's return leg, a rescue or recovery carried home. */
    private static boolean homeward(Contract c) {
        switch (c.type) {
            case PATROL:
            case RESCUE:
            case RECOVERY:
                return c.progress > 0;
            default:
                return false;
        }
    }

    /** What the game counts: cargo delivered, ships of a convoy or raiders settled. */
    private static void progress(Objective o, Contract c) {
        switch (c.type) {
            case DELIVERY:
                o.progress = c.delivered;
                o.of = c.quantity;
                break;
            case CONVOY:
            case DEFEND:
                o.progress = c.targets.length - c.count(Contract.MARK_OPEN);
                o.of = c.targets.length;
                break;
            default:
                break;
        }
    }

    /** "DELIVERY" as "Delivery". */
    static String kind(Contract.Type type) {
        String n = type.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static double deadline(Objective o) {
        return Double.isNaN(o.timeLeft) ? Double.MAX_VALUE : o.timeLeft;
    }
}
