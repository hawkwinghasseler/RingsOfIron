package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.defs.OperationDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Supplier;

import static com.hawkwing.RingsOfIron.game.ContractBoard.*;

/**
 * {@link ContractBoard}'s operations: chains of steps a faction offers at rank milestones, one running
 * at a time, each step a contract taken as it is posted.
 */
final class ContractOperations {
    private final ContractBoard board;
    private final GameContext ctx;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final Supplier<PirateBase> pirateBase;

    ContractOperations(ContractBoard board) {
        this.board = board;
        this.ctx = board.ctx;
        this.macroSim = board.macroSim;
        this.standing = board.standing;
        this.pirateBase = board.pirateBase;
    }

    /** Takes an operation's offer: starts it with its first step, if the world has one to give. */
    String startOperation(Contract offer) {
        if (board.running != null) return "You are already on " + board.running.name;
        OperationDef def = operationDef(offer.operationId);
        if (def == null || def.steps.isEmpty()) return "That operation is no longer on";
        Operation op = new Operation();
        op.id = def.id;
        op.name = offer.operationName;
        op.giver = offer.giver;
        op.stationUuid = offer.stationUuid;
        op.steps = def.steps.toArray(new String[0]);
        op.reward = def.reward;
        op.standing = def.standing;
        op.step = 1;
        Contract first = stepContract(op);
        if (first == null) return "There is nothing for " + op.name + " to do just now";
        offer.status = Contract.Status.DONE;
        offer.outcome = "taken";
        board.running = op;
        board.active.add(first);
        board.notices.add("Operation " + op.name + " under way: " + first.heading());
        return null;
    }

    /** Moves the operation on after its step {@code c} ended: the next step, the reward, or its end. */
    void advanceOperation(Contract c) {
        Operation op = board.running;
        if (op == null || !op.id.equals(c.operationId) || op.step != c.step) return;
        FactionStanding s = standing;
        switch (c.status) {
            case DONE:
                if (c.type == Contract.Type.RECON) op.quarry = c.targetUuid;
                if (op.step < op.steps.length) {
                    op.step++;
                    Contract next = stepContract(op);
                    if (next == null) {
                        board.running = null;
                        board.notices.add("Operation " + op.name + " called off: nothing left to do");
                    } else {
                        board.active.add(next);
                        board.notices.add("Operation " + op.name + ": " + next.heading());
                    }
                    return;
                }
                board.running = null;
                board.operationsDone.add(op.id);
                TraderComponent trader = ctx.player() == null ? null : ctx.player().getComponent(TraderComponent.class);
                if (trader != null) trader.credits += op.reward;
                s.reward(op.giver, op.standing, "saw their operations through");
                board.notices.add(String.format(Locale.ROOT, "Operation %s done. Paid %,.0f cr, standing %+.0f with %s", op.name,
                        op.reward, op.standing, s.factions().name(s.factions().index(op.giver))));
                return;
            case CANCELLED:
                board.running = null;
                board.notices.add("Operation " + op.name + " called off");
                return;
            default:
                board.running = null;
                s.penalize(op.giver, op.standing * FAIL_SHARE, "failed their operations");
                board.notices.add(String.format(Locale.ROOT, "Operation %s failed. Standing %+.0f with %s", op.name,
                        -op.standing * FAIL_SHARE, s.factions().name(s.factions().index(op.giver))));
                return;
        }
    }

    /** The contract for {@code op}'s step under way, taken at once; null if the world has nothing of that kind now. */
    Contract stepContract(Operation op) {
        EconomyNode node = macroSim.station(op.stationUuid);
        int giver = standing.factions().index(op.giver);
        if (node == null || giver < 0) return null;
        String kind = op.steps[op.step - 1];
        List<Contract> options;
        switch (kind) {
            case "patrol":
                options = board.postings.patrols(node, giver);
                break;
            case "recon":
                options = board.postings.recons(giver, op.quarry);
                // A named ace is the pirate worth finding, so the bounty after it is on the ace
                List<Contract> aces = new ArrayList<>();
                for (Contract o : options) if (board.postings.isAce(o.targetUuid)) aces.add(o);
                if (!aces.isEmpty()) options = aces;
                break;
            case "intel":
                // Already known: nothing to find, on to the next step
                if (pirateBase.get().found() || !pirateBase.get().standing()) {
                    if (op.step >= op.steps.length) return null;
                    op.step++;
                    return stepContract(op);
                }
                options = board.postings.recons(giver, op.quarry);
                for (Contract o : options) {
                    o.intel = true;
                    o.reason = "Follow it home to the raiders' base";
                }
                break;
            case "raid":
                options = board.postings.raids(giver, true);
                break;
            case "bounty":
                options = board.postings.bounties(node, giver);
                // The pirate the recon found, if it is still out there
                Contract quarry = null;
                for (Contract b : options) if (b.targetUuid.equals(op.quarry)) quarry = b;
                if (quarry != null) options = new ArrayList<>(List.of(quarry));
                break;
            case "escort":
                options = board.postings.escorts(node, giver);
                break;
            case "convoy":
                options = board.postings.convoys(node, giver);
                if (options.isEmpty()) options = board.postings.escorts(node, giver);
                break;
            case "delivery":
                options = board.postings.deliveries(node, giver);
                break;
            default:
                options = new ArrayList<>();
                break;
        }
        options.removeIf(o -> !acceptable(o));
        if (options.isEmpty()) return null;
        // Near jobs first: an operation's steps follow on from each other
        options.sort(java.util.Comparator.comparing(o -> o.tier));
        Random rnd = new Random(NewGame.mix(op.id.hashCode() * 31L + op.step, (long) Math.floor(board.now() / DAY)));
        int nearest = 0;
        while (nearest < options.size() && options.get(nearest).tier == options.get(0).tier) nearest++;
        Contract c = options.get(rnd.nextInt(nearest));
        c.id = board.nextId++;
        c.giver = op.giver;
        c.stationUuid = op.stationUuid;
        c.stationName = ContractBoard.name(node);
        c.tier = 0;
        c.operationId = op.id;
        c.operationName = op.name;
        c.step = op.step;
        c.steps = op.steps.length;
        board.senior(c);
        // Taken as it is posted
        switch (c.type) {
            case DELIVERY: {
                TradeOrder order = board.order(c.orderId);
                EconomyNode to = macroSim.station(c.targetUuid);
                if (order == null || order.claimedBy != null || to == null) return null;
                order.claimedBy = PLAYER_CLAIM;
                order.quantity = c.quantity;
                to.expectIncoming(c.commodity, c.quantity);
                break;
            }
            case ESCORT:
                c.progress = macroSim.freighters().get(c.targetUuid).tripsCompleted;
                break;
            case CONVOY:
                for (int i = 0; i < c.targets.length; i++) c.trips[i] = macroSim.freighters().get(c.targets[i]).tripsCompleted;
                break;
            default:
                break;
        }
        c.status = Contract.Status.ACTIVE;
        return c;
    }

    /** Whether the world still has what {@code c} is about, so it can be taken now. */
    boolean acceptable(Contract c) {
        switch (c.type) {
            case ESCORT:
                return macroSim.freighters().get(c.targetUuid) != null;
            case CONVOY:
                for (String f : c.targets) if (macroSim.freighters().get(f) == null) return false;
                return true;
            case BOUNTY:
            case RECON:
                return ctx.engine.getEntity(c.targetUuid) != null;
            case RAID:
                return pirateBase.get().standing();
            default:
                return true;
        }
    }

    OperationDef operationDef(String id) {
        var content = ctx.content();
        return content == null || id == null || !content.defs.has("operation", id) ? null
                : content.defs.get("operation", id, OperationDef.class);
    }

    /** The operations the giver of this board offers today: unlocked by rank, not done, none under way. */
    List<Contract> operations(int giver) {
        List<Contract> out = new ArrayList<>();
        var content = ctx.content();
        FactionStanding s = standing;
        if (content == null || board.running != null) return out;
        for (OperationDef def : content.defs.all("operation", OperationDef.class)) {
            if (board.operationsDone.contains(def.id) || def.steps.isEmpty() || !ready(def)) continue;
            String giverId = def.giver == null || def.giver.isEmpty() ? s.factions().id(s.sponsor()) : def.giver;
            if (s.factions().index(giverId) != giver) continue;
            int rank = rankIndex(def.rank);
            if (rank < 0 || s.record().rank < rank) continue;
            Contract c = new Contract();
            c.type = Contract.Type.OPERATION;
            c.operationId = def.id;
            c.operationName = def.name == null ? def.id : def.name;
            c.targetName = c.operationName;
            c.reason = def.text == null ? "" : def.text;
            c.steps = def.steps.size();
            c.tier = giver == s.sponsor() ? rank : 0;
            c.reward = def.reward;
            c.standing = def.standing;
            c.deadline = board.now() + DAY;
            out.add(c);
        }
        // The newest the rank opens first, one the world has just opened (the base fallen) ahead of
        // its rank's others, and only a few, so a board still shows other work
        out.sort(java.util.Comparator.comparingInt((Contract c) -> -rankIndex(operationDef(c.operationId).rank))
                .thenComparing(c -> operationDef(c.operationId).requires == null || operationDef(c.operationId).requires.isEmpty()));
        while (out.size() > OPERATIONS_SHOWN) out.remove(out.size() - 1);
        return out;
    }

    /** Whether the world is as {@code def} waits for ({@link OperationDef#requires}): the pirates' base fallen, say. */
    boolean ready(OperationDef def) {
        if (def.requires == null || def.requires.isEmpty()) return true;
        if ("base_fallen".equals(def.requires)) return pirateBase.get().uuid() != null && !pirateBase.get().standing();
        return false;
    }

    int rankIndex(String id) {
        RankLadder ranks = standing.ranks();
        if (id == null) return 0;
        for (int i = 0; i < ranks.size(); i++) {
            RankDef r = ranks.get(i);
            if (id.equals(r.id)) return i;
        }
        return -1;
    }
}
