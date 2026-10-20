package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.OperationDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Station mission boards and the player's contracts (GDD section 4). Each station's owning faction
 * posts up to {@link #OFFERS_PER_STATION} jobs a day, drawn from the world as it is: deliveries from
 * the economy's open trade orders out of that station, escorts of the freighters loading there,
 * bounties on ships at war with the giver, and patrols out to another station and back. Higher
 * {@link Contract#tier tiers} need rank with the sponsor, or standing with another giver.
 *
 * <p>Taken contracts are checked against the world every frame: a delivery hands its goods over
 * when the player docks at the buyer with them aboard, an escort ends when its freighter docks with
 * the player close by, a bounty when its ship is beaten ({@link FirstPlay#beaten}) or gone, and a
 * patrol on coming back. Success pays credits and calls {@link FactionStanding#reward}; a missed
 * deadline or an abandoned job costs {@link #FAIL_SHARE} of the standing it offered. A job the
 * world takes away (its station or its trade order gone) is called off at no cost.
 *
 * <p>Taking a delivery takes its trade order off the freighters: the order is claimed in the
 * player's name until the job ends. Offers, contracts and history are saved with the game.
 *
 * <p>Round 3 adds:
 * <ul>
 *   <li><b>Convoys</b>: two or more freighters hauling out of a station to the same buyer, paid by
 *       how many dock with the player close.</li>
 *   <li><b>Rescue and recovery</b>: escape pods and wrecks ({@link Derelicts}) a robbed or lost
 *       freighter leaves on the rails, posted with {@link #offerFor}. A pod is picked up with a
 *       slow close pass, a wreck by holding alongside while the crew carry its cargo over; either is
 *       done on docking at the station that posted it.</li>
 *   <li><b>Defence</b> of a station from raiders, and urgent deliveries, posted by
 *       {@link WorldEvents} through {@link #postEvent}.</li>
 *   <li><b>Operations</b> ({@code [operation.*]} in the data): chains of steps a faction offers at
 *       rank milestones, one running at a time, each step a contract of its own; the last pays the
 *       operation's reward. A <b>recon</b> step finds a pirate where it lurks, for the bounty after it.</li>
 *   <li><b>Records</b>: a pirate that has robbed freighters carries that in its bounty, for more pay.</li>
 *   <li><b>Raids</b> on the pirates' base ({@link PirateBase}), once it is found: the sponsor's
 *       boards offer one from {@link #RAID_RANK}, done when the base falls. An operation's
 *       <b>intel</b> step finds the base by following a pirate to it.</li>
 * </ul>
 */
public class ContractBoard extends EntitySystem implements SaveParticipant {
    public static final int OFFERS_PER_STATION = 5;
    /** Operations a board shows at most, the ones the highest rank opens first (round 5 has seven). */
    public static final int OPERATIONS_SHOWN = 2;
    /** Contracts the player can hold at once. */
    public static final int MAX_ACTIVE = 3;
    /** Boards are refreshed each in-game day. */
    public static final double DAY = 86_400;
    /** Share of a contract's standing lost for failing or abandoning it. */
    public static final float FAIL_SHARE = 0.5f;
    /** Standing with a non-sponsor giver each tier above 0 needs. */
    public static final float STANDING_PER_TIER = 20f;
    /** How close the player must be to an escorted freighter's station when it docks, meters. */
    public static final double ESCORT_RANGE = 50_000;
    /** How long after an escorted freighter docks the player may still turn up, sim seconds. */
    public static final double ESCORT_GRACE = 1_800;
    /** How close a patrol must pass each end of its lane, meters. */
    public static final double PATROL_RANGE = 20_000;
    /** Who a trade order taken as a delivery is claimed by. */
    public static final String PLAYER_CLAIM = "player";
    /** Added to a contract target's HUD marker while it is the waypoint. */
    public static final String MARKER_SUFFIX = " [contract]";

    // Placeholder pay and standing; tuned with the loop in category 9.
    static final double DELIVERY_FEE_SHARE = 0.15, DELIVERY_MIN_FEE = 500;
    static final double ESCORT_PAY = 1_500, BOUNTY_PAY = 7_500, PATROL_PAY = 1_000;
    /** Pay and time for a job that crosses to another body's orbit. */
    static final double FAR_PAY = 3, NEAR_DAYS = 2, FAR_DAYS = 8;
    /** Pay for each freighter of a convoy, and for each survivor of a pod; recovery's share of the cargo's value. */
    static final double CONVOY_PAY = 1_200, RESCUE_PAY = 1_500, RESCUE_PER_SURVIVOR = 400, RECOVERY_SHARE = 0.3,
            RECOVERY_MIN = 1_000, DEFEND_PAY = 4_000, RECON_PAY = 1_500;
    /** A bounty's pay rises by this share for each freighter its pirate has robbed, up to {@link #RECORD_CAP}. */
    static final double RECORD_PREMIUM = 0.5;
    static final int RECORD_CAP = 4;
    /** A bounty on a ship worth less than this share of the player's pays {@link #WEAK_STANDING} of its standing. */
    static final double WEAK_RATIO = 0.33;
    static final float WEAK_STANDING = 0.5f;
    /** From this rank (Commander) a single job pays {@link #SENIOR_STANDING} of its standing. */
    static final int SENIOR_RANK = 3;
    static final float SENIOR_STANDING = 0.5f;
    /** How close the player must come to a pirate to find it, meters: about a sensor sweep. */
    public static final double RECON_RANGE = 100_000;
    /**
     * Rank with the sponsor a raid on the pirate base needs: Commander since round 5, when the base
     * scrambles a wing against the raid ({@link PirateBase}); it was Lieutenant Commander.
     */
    public static final int RAID_RANK = 3;
    static final double RAID_PAY = 40_000, RAID_DAYS = 6;
    /** The note on a job a board posts because tension runs high at its stations (round 6). */
    static final String TENSION_JOB = "Tension runs high here: the station wants more armed ships on its lanes";

    private static final Family SHIPS = Family.all(ShipTaskBoardComponent.class, UuidComponent.class)
            .exclude(StationComponent.class, PlayerControllerComponent.class).get();

    /** One station's posted offers and the day they were posted. */
    public static class StationOffers {
        public String station;
        public long day = -1;
        public Contract[] offers = new Contract[0];
        /** The player's rank when the board was posted or last looked at; -1 if not known (an older save). */
        public int rank = -1;
    }

    private final GameWorld world;
    private final Map<String, StationOffers> boards = new LinkedHashMap<>();
    private final List<Contract> active = new ArrayList<>();
    /** Ended contracts, newest last, at most {@link #HISTORY}. */
    private final List<Contract> history = new ArrayList<>();
    static final int HISTORY = 20;
    private long nextId = 1;
    /** Offers posted outside the daily boards (world events, pods and wrecks), each on its station's board until taken or late. */
    private final List<Contract> extra = new ArrayList<>();
    /** The operation under way, or null. */
    private Operation running;
    /** Ids of the operations done, never offered again. */
    private final Set<String> operationsDone = new HashSet<>();
    /** Entities whose markers point at a contract waypoint; not saved, the markers are rebuilt. */
    private Set<Entity> marked = new HashSet<>();
    /** Contract news not yet shown; the screen drains it. */
    public final List<String> notices = new ArrayList<>();
    private final StateVector a = new StateVector(), b = new StateVector();
    private final double[] trueState = new double[4];

    /** An operation under way: which, where it was taken, and how far it has got. Saved as it is. */
    public static class Operation {
        public String id;
        public String name;
        public String giver;
        public String stationUuid;
        public String[] steps = new String[0];
        /** The step under way, from 1. */
        public int step;
        /** The pirate the last recon found, for the bounty after it. */
        public String quarry;
        public double reward;
        public float standing;
    }

    public ContractBoard(GameWorld world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ boards

    /** The station's offers today, posting a fresh board if the day has turned. Empty for a station of no faction. */
    public List<Contract> offers(String stationUuid) {
        long day = (long) Math.floor(now() / DAY);
        StationOffers board = boards.get(stationUuid);
        if (board == null || board.day != day) {
            board = new StationOffers();
            board.station = stationUuid;
            board.day = day;
            board.offers = post(stationUuid, day).toArray(new Contract[0]);
            board.rank = world.standing.record().rank;
            boards.put(stationUuid, board);
        }
        promoted(board);
        reshowIfStale();
        List<Contract> out = new ArrayList<>();
        for (Contract c : extra) {
            if (stationUuid.equals(c.stationUuid) && c.status == Contract.Status.OFFERED && now() <= c.deadline) out.add(c);
        }
        for (Contract c : board.offers) {
            // One operation at a time: the others leave the board while one runs
            if (c.status != Contract.Status.OFFERED || (running != null && c.type == Contract.Type.OPERATION)) continue;
            // A bounty whose ship was beaten, is gone, or is already being hunted leaves the board
            if (c.type == Contract.Type.BOUNTY && !stillWanted(c)) continue;
            out.add(c);
        }
        return out;
    }

    private boolean stillWanted(Contract c) {
        Entity target = world.engine.getEntity(c.targetUuid);
        return target != null && !FirstPlay.beaten(world, target) && !targeted(c.targetUuid);
    }

    /**
     * A promotion since {@code board} was posted adds the operations the new rank opens to it at once,
     * rather than the next day.
     */
    private void promoted(StationOffers board) {
        int rank = world.standing.record().rank;
        if (rank <= board.rank || board.rank < 0) {
            board.rank = Math.max(board.rank, rank);
            return;
        }
        board.rank = rank;
        reshowOperations(board);
    }

    /**
     * The world opened an operation a posted board does not show yet (the pirates' base has fallen):
     * every board shows the operations it would offer now, at once rather than the next day.
     */
    public void worldChanged() {
        operationsStale = true;
        reshowIfStale();
    }

    /** Boards whose operations {@link #worldChanged} made stale; not saved, since a load posts afresh by day. */
    private boolean operationsStale;

    /** Reshows every board's operations if the world changed, once none runs (while one runs the boards show none). */
    private void reshowIfStale() {
        if (!operationsStale || running != null) return;
        operationsStale = false;
        for (StationOffers board : boards.values()) reshowOperations(board);
    }

    /** Puts the operations {@code board}'s giver offers now in place of those it showed. */
    private void reshowOperations(StationOffers board) {
        Entity station = world.engine.getEntity(board.station);
        EconomyNode node = world.macroSim.station(board.station);
        int giver = world.standing.factionOf(station);
        FactionDef def = world.standing.factions().def(giver);
        if (station == null || node == null || def == null || def.hostileToAll) return;
        // The operations the new rank shows take the place of those the board showed
        List<Contract> more = new ArrayList<>();
        List<Contract> kept = new ArrayList<>();
        List<Contract> ops = operations(giver);
        for (Contract c : board.offers) {
            boolean still = c.type != Contract.Type.OPERATION;
            for (Contract op : ops) still |= op.operationId.equals(c.operationId);
            if (still) kept.add(c);
        }
        for (Contract op : ops) {
            boolean shown = false;
            for (Contract c : kept) shown |= op.operationId.equals(c.operationId);
            if (shown) continue;
            op.id = nextId++;
            op.giver = def.id;
            op.stationUuid = board.station;
            op.stationName = node.displayName;
            more.add(op);
        }
        if (more.isEmpty() && kept.size() == board.offers.length) return;
        more.addAll(kept);
        board.offers = more.toArray(new Contract[0]);
    }

    /**
     * Puts {@code c} on the board of station {@link Contract#stationUuid} until it is taken or its
     * deadline passes, beside the day's offers: a world event's job, or a pod or wreck to bring home.
     * Its giver is the station's owner unless set. Returns false (and posts nothing) for a station of
     * no faction, or one hostile to all.
     */
    public boolean postEvent(Contract c) {
        Entity station = world.engine.getEntity(c.stationUuid);
        EconomyNode node = world.macroSim.station(c.stationUuid);
        int owner = world.standing.factionOf(station);
        FactionDef def = world.standing.factions().def(owner);
        if (node == null || def == null || def.hostileToAll) return false;
        c.id = nextId++;
        if (c.giver == null) c.giver = def.id;
        c.stationName = name(node);
        c.status = Contract.Status.OFFERED;
        extra.add(c);
        // Late ones are dropped as new ones come
        extra.removeIf(o -> o.status != Contract.Status.OFFERED || now() > o.deadline);
        if (!extra.contains(c)) extra.add(c);
        return true;
    }

    /** Offers posted outside the daily boards and still open, for tests and the event log. */
    List<Contract> extraOffers() {
        List<Contract> out = new ArrayList<>();
        for (Contract c : extra) if (c.status == Contract.Status.OFFERED && now() <= c.deadline) out.add(c);
        return out;
    }

    /**
     * A rescue of pod {@code d}'s survivors, or a recovery of wreck {@code d}'s cargo, to be brought to
     * {@code stationUuid}, ready for {@link #postEvent}. Null for a wreck with no cargo, or a station
     * that is not in the economy.
     */
    public Contract offerFor(Derelicts.Derelict d, String stationUuid, String reason) {
        EconomyNode node = world.macroSim.station(stationUuid);
        if (node == null) return null;
        Contract c = new Contract();
        c.stationUuid = stationUuid;
        c.targetUuid = d.id;
        c.targetName = d.name;
        c.destinationUuid = stationUuid;
        c.destinationName = name(node);
        c.reason = reason == null ? "" : reason;
        c.deadline = d.isPod() ? d.expires : Math.min(d.expires, now() + 5 * DAY);
        if (d.isPod()) {
            c.type = Contract.Type.RESCUE;
            c.quantity = d.survivors;
            c.reward = RESCUE_PAY + RESCUE_PER_SURVIVOR * d.survivors;
            c.standing = 5;
        } else {
            if (d.commodity == null || d.quantity <= 0) return null;
            CommodityType type = world.macroSim.economy().catalog().commodity(d.commodity);
            c.type = Contract.Type.RECOVERY;
            c.commodity = d.commodity;
            c.commodityName = type == null || type.displayName == null ? d.commodity : type.displayName;
            c.quantity = d.quantity;
            c.reward = Math.round(Math.max(RECOVERY_MIN, RECOVERY_SHARE * d.quantity * node.price(d.commodity)));
            c.standing = 4;
        }
        return c;
    }

    /** The operation under way, or null. */
    public Operation operation() {
        return running;
    }

    /** Whether the operation {@code id} has been done. */
    public boolean operationDone(String id) {
        return operationsDone.contains(id);
    }

    /** The contracts the player holds, in the order taken. */
    public List<Contract> active() {
        return Collections.unmodifiableList(active);
    }

    /** Ended contracts, oldest first. */
    public List<Contract> history() {
        return Collections.unmodifiableList(history);
    }

    /** Why the player may not take {@code c} yet ("Needs rank Lieutenant"), or null if they may. */
    public String locked(Contract c) {
        if (c.tier <= 0) return null;
        FactionStanding s = world.standing;
        int giver = s.factions().index(c.giver);
        if (giver == s.sponsor()) {
            return s.record().rank >= c.tier ? null : "Needs rank " + s.rankName(c.tier);
        }
        float need = c.tier * STANDING_PER_TIER;
        return s.standing(giver) >= need ? null
                : String.format(Locale.ROOT, "Needs standing %.0f with %s", need, s.factions().name(giver));
    }

    /** Takes offer {@code id}; returns null, or why not. */
    public String accept(long id) {
        Contract c = findOffer(id);
        if (c == null) return "That job is no longer on the board";
        String why = locked(c);
        if (why != null) return why;
        if (held() >= MAX_ACTIVE) return "You already hold " + MAX_ACTIVE + " contracts";
        if (now() > c.deadline) return "Too late for that one";
        if (c.type == Contract.Type.BOUNTY && !stillWanted(c)) return "That ship is no longer wanted";
        switch (c.type) {
            case DELIVERY: {
                if (c.orderId < 0) break; // a world event's: no trade order behind it
                TradeOrder order = order(c.orderId);
                EconomyNode to = world.macroSim.station(c.targetUuid);
                if (order == null || order.claimedBy != null || to == null) return "A freighter has taken that cargo";
                order.claimedBy = PLAYER_CLAIM;
                order.quantity = c.quantity;
                to.expectIncoming(c.commodity, c.quantity);
                break;
            }
            case ESCORT: {
                FreighterComponent f = world.macroSim.freighters().get(c.targetUuid);
                if (f == null) return c.targetName + " is gone";
                c.progress = f.tripsCompleted;
                break;
            }
            case BOUNTY:
            case RECON:
                if (world.engine.getEntity(c.targetUuid) == null) return c.targetName + " is gone";
                break;
            case RESCUE:
            case RECOVERY:
                if (world.derelicts.get(c.targetUuid) == null) return c.targetName + " is lost";
                break;
            case RAID:
                if (!world.pirateBase.standing()) return c.targetName + " has already fallen";
                break;
            case CONVOY:
                for (int i = 0; i < c.targets.length; i++) {
                    FreighterComponent f = world.macroSim.freighters().get(c.targets[i]);
                    if (f == null) return c.targetNames[i] + " is gone";
                    c.trips[i] = f.tripsCompleted;
                }
                break;
            case OPERATION:
                return startOperation(c);
            default:
                break;
        }
        c.status = Contract.Status.ACTIVE;
        active.add(c);
        notices.add("Contract taken: " + c.title());
        return null;
    }

    /** Contracts held that count against {@link #MAX_ACTIVE}: an operation's step does not. */
    private int held() {
        int n = 0;
        for (Contract c : active) if (c.step == 0) n++;
        return n;
    }

    /**
     * Fails every contract the player holds, an operation's step with it, at the usual cost: what
     * happens when the player has to be rescued.
     */
    public void failAll(String why) {
        for (Contract c : new ArrayList<>(active)) end(c, Contract.Status.FAILED, why);
    }

    /** Gives up contract {@code id}, at {@link #FAIL_SHARE} of its standing; returns null, or why not. */
    public String abandon(long id) {
        for (Contract c : active) {
            if (c.id != id) continue;
            end(c, Contract.Status.ABANDONED, "abandoned");
            return null;
        }
        return "You hold no such contract";
    }

    /** Clears everything, for a new game or a save from before contracts. */
    public void reset() {
        boards.clear();
        active.clear();
        history.clear();
        extra.clear();
        running = null;
        operationsDone.clear();
        nextId = 1;
    }

    // ------------------------------------------------------------------ checking

    @Override
    public void update(float deltaTime) {
        Entity player = world.getPlayer();
        if (player == null || world.getCrash() != null) return;
        for (Contract c : new ArrayList<>(active)) check(c, player);
        // Survivors whose rescue ended some other way go ashore at the next station the ship docks at
        if (world.docking.dockedWithClearance(player) && world.macroSim.station(world.universe.uuidOf(world.docking.dockedTo(player))) != null) {
            int n = landSurvivors(player, -1);
            if (n > 0) notices.add(n + (n == 1 ? " survivor goes" : " survivors go") + " ashore at "
                    + PlayerDocking.name(world.docking.dockedTo(player)));
        }
        updateMarkers();
    }

    private void check(Contract c, Entity player) {
        switch (c.type) {
            case DELIVERY:
                checkDelivery(c, player);
                break;
            case ESCORT:
                checkEscort(c, player);
                break;
            case BOUNTY:
                checkBounty(c);
                break;
            case PATROL:
                checkPatrol(c, player);
                break;
            case RESCUE:
                checkRescue(c, player);
                break;
            case RECOVERY:
                checkRecovery(c, player);
                break;
            case CONVOY:
                checkConvoy(c, player);
                break;
            case DEFEND:
                checkDefend(c);
                break;
            case RECON:
                checkRecon(c, player);
                break;
            case RAID:
                if (!world.pirateBase.standing()) end(c, Contract.Status.DONE, c.targetName + " has fallen");
                break;
            default:
                break;
        }
        if (c.isActive() && now() > c.deadline) {
            if (c.type == Contract.Type.CONVOY || c.type == Contract.Type.DEFEND) {
                // What is still open by then went badly; what went well is paid for
                for (int i = 0; i < c.marks.length; i++) if (c.marks[i] == Contract.MARK_OPEN) c.marks[i] = Contract.MARK_BAD;
                settleGroup(c, "out of time");
            } else {
                end(c, Contract.Status.FAILED, "out of time");
            }
        }
    }

    /** Hands the goods over while docked at the buyer with clearance, paying the agreed price for each unit. */
    private void checkDelivery(Contract c, Entity player) {
        TradeOrder order = c.orderId < 0 ? null : order(c.orderId);
        EconomyNode to = world.macroSim.station(c.targetUuid);
        if ((order == null && c.orderId >= 0) || to == null) {
            end(c, Contract.Status.CANCELLED, to == null ? c.targetName + " is gone" : "the order was withdrawn");
            return;
        }
        Entity dock = world.docking.dockedWithClearance(player) ? world.docking.dockedTo(player) : null;
        TraderComponent trader = player.getComponent(TraderComponent.class);
        if (dock != world.engine.getEntity(c.targetUuid) || trader == null) return;
        double units = Math.min(trader.amount(c.commodity), c.quantity - c.delivered);
        if (units <= 1e-6) return;
        double before = trader.credits;
        double sold = world.macroSim.market().sell(to, trader, c.commodity, units);
        if (sold <= 0) return;
        // The giver pays the agreed price, whatever the market gave
        trader.credits = Math.max(trader.credits, before + sold * c.unitPrice);
        c.delivered += sold;
        if (order != null) to.expectIncoming(c.commodity, -sold);
        if (c.quantity - c.delivered < 0.5) {
            end(c, Contract.Status.DONE, "delivered");
        } else {
            notices.add(String.format(Locale.ROOT, "Delivered %.0f %s; %.0f to go", sold, c.commodityName,
                    c.quantity - c.delivered));
        }
    }

    private void checkEscort(Contract c, Entity player) {
        FreighterComponent f = world.macroSim.freighters().get(c.targetUuid);
        if (f == null) {
            end(c, Contract.Status.FAILED, c.targetName + " was lost");
            return;
        }
        if (f.tripsCompleted <= c.progress) {
            if (order(c.orderId) == null) end(c, Contract.Status.CANCELLED, "the haul was called off");
            return;
        }
        if (c.arrivedAt < 0) c.arrivedAt = now();
        Entity station = world.engine.getEntity(c.destinationUuid);
        if (distance(player, station) <= ESCORT_RANGE) end(c, Contract.Status.DONE, c.targetName + " docked safely");
        else if (now() - c.arrivedAt > ESCORT_GRACE) end(c, Contract.Status.FAILED, c.targetName + " docked without you");
    }

    private void checkBounty(Contract c) {
        Entity target = world.engine.getEntity(c.targetUuid);
        if (target == null) end(c, Contract.Status.DONE, c.targetName + " is gone");
        else if (FirstPlay.beaten(world, target)) end(c, Contract.Status.DONE, c.targetName + " is beaten");
    }

    private void checkPatrol(Contract c, Entity player) {
        Entity point = world.engine.getEntity(c.waypointUuid());
        if (point == null) {
            end(c, Contract.Status.CANCELLED, c.waypointName() + " is gone");
            return;
        }
        if (distance(player, point) > PATROL_RANGE) return;
        if (c.progress == 0) {
            c.progress = 1;
            notices.add("Patrol: passed " + c.targetName + "; now back to " + c.destinationName);
        } else {
            end(c, Contract.Status.DONE, "lane patrolled");
        }
    }

    /**
     * Picks the pod's survivors up with a slow close pass, as many as there are free berths for, and
     * brings them aboard as people ({@link SurvivorComponent}); docked at the station, those still alive
     * go ashore and are paid for. Any left in the pod for want of berths are left to the station's tug.
     */
    private void checkRescue(Contract c, Entity player) {
        if (c.progress == 0) {
            Derelicts.Derelict pod = world.derelicts.get(c.targetUuid);
            if (pod == null) {
                end(c, Contract.Status.FAILED, c.targetName + "'s air ran out");
                return;
            }
            if (!world.derelicts.closeTo(player, pod)) return;
            int room = CrewHiring.berths(player) - world.crewHiring.aboard(player);
            int n = (int) Math.min(Math.round(c.quantity), Math.max(0, room));
            if (n <= 0) {
                // Said once a pass, not every frame
                if (c.arrivedAt < 0) notices.add("No berth free for " + c.targetName + "'s survivors");
                c.arrivedAt = now();
                return;
            }
            int[] hurt = new int[2];
            int aboard = boardSurvivors(c, player, n, podExposure(pod), hurt);
            if (aboard <= 0) return;
            world.derelicts.remove(pod.id);
            c.progress = 1;
            c.arrivedAt = -1;
            c.carried = aboard;
            notices.add(String.format(Locale.ROOT, "Picked up %d survivors from %s; now to %s%s%s", aboard, c.targetName,
                    c.destinationName, aboard < c.quantity ? String.format(Locale.ROOT, " (no berths for %.0f more)",
                            c.quantity - aboard) : "",
                    hurt[0] == 0 ? "" : String.format(Locale.ROOT, ". %d hurt%s: your crew will tend them", hurt[0],
                            hurt[1] == 0 ? "" : String.format(Locale.ROOT, ", %d critically", hurt[1]))));
            return;
        }
        if (!dockedAt(player, c.destinationUuid)) return;
        if (c.carried < 0) {
            // Picked up before survivors came aboard as people: paid by the pod's count, as then
            end(c, Contract.Status.DONE, "survivors brought home");
            return;
        }
        int alive = landSurvivors(player, c.id);
        if (alive == 0) {
            end(c, Contract.Status.FAILED, "none of the survivors lived");
            return;
        }
        c.reward = RESCUE_PAY + RESCUE_PER_SURVIVOR * alive;
        end(c, Contract.Status.DONE, alive + (alive == 1 ? " survivor" : " survivors") + " brought home");
    }

    /** How far a pod's air has run down, 0 (just cast off) to 1 (out of air). */
    double podExposure(Derelicts.Derelict pod) {
        double left = pod.expires - now();
        return Math.max(0, Math.min(1, 1 - left / Derelicts.POD_AIR));
    }

    /**
     * Brings {@code n} of rescue {@code c}'s survivors aboard {@code ship}, on free deck tiles; returns
     * how many came. Round 6: they come aboard as the pod left them ({@link #hurtInPod}), the worse the
     * longer it drifted ({@code exposure}); {@code hurt} gets how many are wounded and how many of
     * them critically.
     */
    private int boardSurvivors(Contract c, Entity ship, int n, double exposure, int[] hurt) {
        String from = c.targetName == null ? "the pod" : c.targetName;
        String crewOf = from.endsWith("'s pod") ? from.substring(0, from.length() - 6) : from;
        int faction = world.standing.factionOf(world.engine.getEntity(c.stationUuid));
        int aboard = 0;
        for (int i = 0; i < n; i++) {
            int[] tile = world.crewHiring.freeTile(ship);
            if (tile == null) break;
            Entity e = com.ringsofiron.aicrew.CrewFactory.spawn(world.crew, ship, crewOf + " hand " + (i + 1),
                    com.ringsofiron.aicrew.CrewRole.CREWMAN, faction, tile[0], tile[1]);
            // Carried, not crew: no work, no fights, a berth taken
            world.crew.crew(e).held = true;
            SurvivorComponent s = new SurvivorComponent();
            s.contract = c.id;
            s.from = from;
            e.add(s);
            Wound w = hurtInPod(e, exposure, new Random(NewGame.mix(c.id, i)));
            if (w != Wound.NONE) hurt[0]++;
            if (w == Wound.CRITICAL) hurt[1]++;
            aboard++;
        }
        return aboard;
    }

    /** Share of a pod's survivors wounded when picked up at once, and the further share by the time its air runs out. */
    static final double POD_HURT = 0.25, POD_HURT_LATE = 0.5;
    /** Share of the wounded critically hurt by the time the air runs out (none at once), and seriously at any time. */
    static final double POD_CRITICAL_LATE = 0.3, POD_SERIOUS = 0.4;
    /** Share of their health the stale air of a pod about to run out has taken. */
    static final float POD_STALE_AIR = 0.4f;

    /**
     * A survivor as the pod leaves them: short of breath by how long it drifted, and wounded one time in
     * {@link #POD_HURT} (more the longer), seriously or critically more often late. Their wound is treated
     * aboard like a crew member's (they are cared for as held), so one left untreated can die before the
     * station. Returns the wound.
     */
    private Wound hurtInPod(Entity e, double exposure, Random dice) {
        com.ringsofiron.aicrew.CrewMemberComponent m = world.crew.crew(e);
        if (exposure > 0) world.crew.applyDamage(e, (float) (POD_STALE_AIR * exposure) * m.def.maxHealth,
                com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.ASPHYXIATION);
        if (dice.nextDouble() >= POD_HURT + POD_HURT_LATE * exposure) return Wound.NONE;
        double v = dice.nextDouble();
        Wound w = v < POD_CRITICAL_LATE * exposure ? Wound.CRITICAL : v < POD_CRITICAL_LATE * exposure + POD_SERIOUS ? Wound.SERIOUS : Wound.LIGHT;
        world.crew.applyDamage(e, m.def.injuryFor(w), com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.FRAGMENT);
        return m.wound;
    }

    /**
     * Sends ashore the survivors aboard {@code ship} picked up for contract {@code contract}, or with
     * {@code contract} negative, every survivor whose rescue is no longer held. Returns how many
     * went ashore alive; the dead are taken off too.
     */
    private int landSurvivors(Entity ship, long contract) {
        int alive = 0;
        var all = world.crew.crewEntities();
        List<Entity> ashore = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            SurvivorComponent s = e.getComponent(SurvivorComponent.class);
            if (s == null || world.crew.position(e).ship != ship) continue;
            if (contract >= 0 ? s.contract != contract : held(s.contract)) continue;
            ashore.add(e);
        }
        for (Entity e : ashore) {
            if (world.crew.crew(e).isAlive()) alive++;
            world.crew.releaseTask(e);
            world.engine.getEcsEngine().removeEntity(e);
        }
        return alive;
    }

    private boolean held(long contract) {
        for (Contract c : active) if (c.id == contract) return true;
        return false;
    }

    /** Survivors aboard {@code ship} picked up for a rescue, alive. */
    public int survivorsAboard(Entity ship) {
        int n = 0;
        var all = world.crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e.getComponent(SurvivorComponent.class) != null && world.crew.position(e).ship == ship
                    && world.crew.crew(e).isAlive()) n++;
        }
        return n;
    }

    /**
     * Holds alongside the wreck for {@link Derelicts#SALVAGE_SECONDS} while the crew carry its cargo into
     * the hold, then hands it over on docking at the station; a hold too full for all of it takes what fits.
     */
    private void checkRecovery(Contract c, Entity player) {
        TraderComponent trader = player.getComponent(TraderComponent.class);
        CommodityType type = world.macroSim.economy().catalog().commodity(c.commodity);
        if (c.progress == 0) {
            Derelicts.Derelict wreck = world.derelicts.get(c.targetUuid);
            if (wreck == null) {
                end(c, Contract.Status.FAILED, c.targetName + " drifted out of reach");
                return;
            }
            if (!world.derelicts.closeTo(player, wreck)) {
                c.arrivedAt = -1;
                return;
            }
            if (c.arrivedAt < 0) {
                c.arrivedAt = now();
                notices.add("Holding alongside " + c.targetName + ": the crew are carrying the " + c.commodityName + " across");
                return;
            }
            if (now() - c.arrivedAt < Derelicts.SALVAGE_SECONDS || trader == null || type == null) return;
            double stowed = trader.stow(type, c.quantity);
            if (stowed < 0.5) {
                c.arrivedAt = now();
                notices.add("No room in the hold for the " + c.commodityName);
                return;
            }
            if (stowed < c.quantity - 0.5) {
                // What did not fit is left; the pay is for what comes home
                c.reward = Math.round(c.reward * stowed / c.quantity);
                c.quantity = Math.floor(stowed);
            }
            world.derelicts.remove(wreck.id);
            c.progress = 1;
            notices.add(String.format(Locale.ROOT, "Recovered %.0f %s from %s; now to %s", c.quantity, c.commodityName,
                    c.targetName, c.destinationName));
            return;
        }
        if (!dockedAt(player, c.destinationUuid) || trader == null || type == null) return;
        double units = trader.handOver(type, c.quantity - c.delivered);
        if (units <= 0) return;
        c.delivered += units;
        if (c.delivered < c.quantity - 0.5) c.reward = Math.round(c.reward * c.delivered / c.quantity);
        end(c, Contract.Status.DONE, c.commodityName + " recovered");
    }

    /** Each freighter docking at the buyer is marked: with the player close by, or without them, or lost. */
    private void checkConvoy(Contract c, Entity player) {
        Entity station = world.engine.getEntity(c.destinationUuid);
        for (int i = 0; i < c.targets.length; i++) {
            if (c.marks[i] != Contract.MARK_OPEN) continue;
            FreighterComponent f = world.macroSim.freighters().get(c.targets[i]);
            if (f == null) {
                c.marks[i] = Contract.MARK_BAD;
                notices.add("Convoy: " + c.targetNames[i] + " was lost");
                continue;
            }
            if (f.tripsCompleted <= c.trips[i]) continue;
            if (distance(player, station) <= ESCORT_RANGE) {
                c.marks[i] = Contract.MARK_GOOD;
                notices.add("Convoy: " + c.targetNames[i] + " docked safely");
            } else if (now() - arrival(c, i) > ESCORT_GRACE) {
                c.marks[i] = Contract.MARK_BAD;
                notices.add("Convoy: " + c.targetNames[i] + " docked without you");
            }
        }
        if (c.count(Contract.MARK_OPEN) == 0) settleGroup(c, "convoy in");
    }

    /** Sim second the convoy's freighter {@code i} was first seen docked. */
    private double arrival(Contract c, int i) {
        if (c.arrivals.length != c.targets.length) {
            c.arrivals = new double[c.targets.length];
            java.util.Arrays.fill(c.arrivals, -1);
        }
        if (c.arrivals[i] < 0) c.arrivals[i] = now();
        return c.arrivals[i];
    }

    /** Each raider beaten, or gone, is marked; once all are, the station is safe. */
    private void checkDefend(Contract c) {
        for (int i = 0; i < c.targets.length; i++) {
            if (c.marks[i] != Contract.MARK_OPEN) continue;
            Entity raider = world.engine.getEntity(c.targets[i]);
            if (raider == null || FirstPlay.beaten(world, raider)) {
                c.marks[i] = Contract.MARK_GOOD;
                if (c.count(Contract.MARK_OPEN) > 0) notices.add("Defence: " + c.targetNames[i] + " is beaten");
            }
        }
        if (c.count(Contract.MARK_OPEN) == 0) settleGroup(c, c.destinationName + " is safe");
    }

    /** Found once the player comes within {@link #RECON_RANGE} of the pirate, on the rails or in the bubble. */
    private void checkRecon(Contract c, Entity player) {
        Entity pirate = world.engine.getEntity(c.targetUuid);
        if (pirate == null) {
            end(c, Contract.Status.CANCELLED, c.targetName + " is gone");
            return;
        }
        if (distance(player, pirate) > RECON_RANGE) return;
        HostileComponent h = pirate.getComponent(HostileComponent.class);
        EconomyNode lurk = h == null || h.stationUuid == null ? null : world.macroSim.station(h.stationUuid);
        // An intel step's pirate leads the way home to the base, before the raid after it is posted
        if (c.intel) world.pirateBase.reveal("Followed " + c.targetName + " home");
        end(c, Contract.Status.DONE, "found " + c.targetName + (lurk == null ? "" : " by " + name(lurk)));
    }

    /** Ends a convoy or defence whose targets are all marked: done if any went well, paid for those. */
    private void settleGroup(Contract c, String why) {
        int good = c.count(Contract.MARK_GOOD), n = c.targets.length;
        if (good == 0) {
            end(c, Contract.Status.FAILED, c.type == Contract.Type.CONVOY ? "none of the convoy came in with you"
                    : "the raiders got away");
            return;
        }
        if (good < n) {
            c.reward = Math.round(c.reward * good / n);
            c.standing = c.standing * good / n;
        }
        end(c, Contract.Status.DONE, String.format(Locale.ROOT, "%s, %d of %d", why, good, n));
    }

    /** True if the player is docked with clearance at the station {@code uuid}. */
    private boolean dockedAt(Entity player, String uuid) {
        return world.docking.dockedWithClearance(player) && world.docking.dockedTo(player) == world.engine.getEntity(uuid);
    }

    /** Ends {@code c}: pays a success, charges a failure, releases a delivery's trade order. */
    private void end(Contract c, Contract.Status status, String why) {
        active.remove(c);
        c.status = status;
        c.outcome = why;
        FactionStanding s = world.standing;
        TraderComponent trader = world.getPlayer() == null ? null : world.getPlayer().getComponent(TraderComponent.class);
        switch (status) {
            case DONE:
                if (trader != null) trader.credits += c.reward;
                s.reward(c.giver, c.standing, "kept their contracts");
                notices.add(String.format(Locale.ROOT, "Contract done: %s. Paid %,.0f cr, standing %+.0f with %s",
                        why, c.reward, c.standing, s.factions().name(s.factions().index(c.giver))));
                break;
            case CANCELLED:
                notices.add("Contract called off: " + why);
                break;
            default:
                s.penalize(c.giver, c.standing * FAIL_SHARE, "broke their contracts");
                notices.add(String.format(Locale.ROOT, "Contract %s: %s. Standing %+.0f with %s",
                        status == Contract.Status.ABANDONED ? "abandoned" : "failed", why, -c.standing * FAIL_SHARE,
                        s.factions().name(s.factions().index(c.giver))));
                break;
        }
        if (c.type == Contract.Type.DELIVERY) release(c);
        history.add(c);
        while (history.size() > HISTORY) history.remove(0);
        if (c.step > 0) advanceOperation(c);
    }

    /** Hands a delivery's trade order back: what was not delivered is no longer on its way. */
    private void release(Contract c) {
        if (c.orderId < 0) return;
        TradeOrder order = order(c.orderId);
        if (order != null && PLAYER_CLAIM.equals(order.claimedBy)) world.macroSim.economy().close(order);
        EconomyNode to = world.macroSim.station(c.targetUuid);
        double undelivered = c.quantity - c.delivered;
        if (to != null && undelivered > 0) to.expectIncoming(c.commodity, -undelivered);
    }

    // ------------------------------------------------------------------ operations

    /** Takes an operation's offer: starts it with its first step, if the world has one to give. */
    private String startOperation(Contract offer) {
        if (running != null) return "You are already on " + running.name;
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
        running = op;
        active.add(first);
        notices.add("Operation " + op.name + " under way: " + first.heading());
        return null;
    }

    /** Moves the operation on after its step {@code c} ended: the next step, the reward, or its end. */
    private void advanceOperation(Contract c) {
        Operation op = running;
        if (op == null || !op.id.equals(c.operationId) || op.step != c.step) return;
        FactionStanding s = world.standing;
        switch (c.status) {
            case DONE:
                if (c.type == Contract.Type.RECON) op.quarry = c.targetUuid;
                if (op.step < op.steps.length) {
                    op.step++;
                    Contract next = stepContract(op);
                    if (next == null) {
                        running = null;
                        notices.add("Operation " + op.name + " called off: nothing left to do");
                    } else {
                        active.add(next);
                        notices.add("Operation " + op.name + ": " + next.heading());
                    }
                    return;
                }
                running = null;
                operationsDone.add(op.id);
                TraderComponent trader = world.getPlayer() == null ? null : world.getPlayer().getComponent(TraderComponent.class);
                if (trader != null) trader.credits += op.reward;
                s.reward(op.giver, op.standing, "saw their operations through");
                notices.add(String.format(Locale.ROOT, "Operation %s done. Paid %,.0f cr, standing %+.0f with %s", op.name,
                        op.reward, op.standing, s.factions().name(s.factions().index(op.giver))));
                return;
            case CANCELLED:
                running = null;
                notices.add("Operation " + op.name + " called off");
                return;
            default:
                running = null;
                s.penalize(op.giver, op.standing * FAIL_SHARE, "failed their operations");
                notices.add(String.format(Locale.ROOT, "Operation %s failed. Standing %+.0f with %s", op.name,
                        -op.standing * FAIL_SHARE, s.factions().name(s.factions().index(op.giver))));
                return;
        }
    }

    /** The contract for {@code op}'s step under way, taken at once; null if the world has nothing of that kind now. */
    private Contract stepContract(Operation op) {
        EconomyNode node = world.macroSim.station(op.stationUuid);
        int giver = world.standing.factions().index(op.giver);
        if (node == null || giver < 0) return null;
        String kind = op.steps[op.step - 1];
        List<Contract> options;
        switch (kind) {
            case "patrol":
                options = patrols(node, giver);
                break;
            case "recon":
                options = recons(giver, op.quarry);
                // A named ace is the pirate worth finding, so the bounty after it is on the ace
                List<Contract> aces = new ArrayList<>();
                for (Contract o : options) if (isAce(o.targetUuid)) aces.add(o);
                if (!aces.isEmpty()) options = aces;
                break;
            case "intel":
                // Already known: nothing to find, on to the next step
                if (world.pirateBase.found() || !world.pirateBase.standing()) {
                    if (op.step >= op.steps.length) return null;
                    op.step++;
                    return stepContract(op);
                }
                options = recons(giver, op.quarry);
                for (Contract o : options) {
                    o.intel = true;
                    o.reason = "Follow it home to the raiders' base";
                }
                break;
            case "raid":
                options = raids(giver, true);
                break;
            case "bounty":
                options = bounties(node, giver);
                // The pirate the recon found, if it is still out there
                Contract quarry = null;
                for (Contract b : options) if (b.targetUuid.equals(op.quarry)) quarry = b;
                if (quarry != null) options = new ArrayList<>(List.of(quarry));
                break;
            case "escort":
                options = escorts(node, giver);
                break;
            case "convoy":
                options = convoys(node, giver);
                if (options.isEmpty()) options = escorts(node, giver);
                break;
            case "delivery":
                options = deliveries(node, giver);
                break;
            default:
                options = new ArrayList<>();
                break;
        }
        options.removeIf(o -> !acceptable(o));
        if (options.isEmpty()) return null;
        // Near jobs first: an operation's steps follow on from each other
        options.sort(java.util.Comparator.comparing(o -> o.tier));
        Random rnd = new Random(NewGame.mix(op.id.hashCode() * 31L + op.step, (long) Math.floor(now() / DAY)));
        int nearest = 0;
        while (nearest < options.size() && options.get(nearest).tier == options.get(0).tier) nearest++;
        Contract c = options.get(rnd.nextInt(nearest));
        c.id = nextId++;
        c.giver = op.giver;
        c.stationUuid = op.stationUuid;
        c.stationName = name(node);
        c.tier = 0;
        c.operationId = op.id;
        c.operationName = op.name;
        c.step = op.step;
        c.steps = op.steps.length;
        senior(c);
        // Taken as it is posted
        switch (c.type) {
            case DELIVERY: {
                TradeOrder order = order(c.orderId);
                EconomyNode to = world.macroSim.station(c.targetUuid);
                if (order == null || order.claimedBy != null || to == null) return null;
                order.claimedBy = PLAYER_CLAIM;
                order.quantity = c.quantity;
                to.expectIncoming(c.commodity, c.quantity);
                break;
            }
            case ESCORT:
                c.progress = world.macroSim.freighters().get(c.targetUuid).tripsCompleted;
                break;
            case CONVOY:
                for (int i = 0; i < c.targets.length; i++) c.trips[i] = world.macroSim.freighters().get(c.targets[i]).tripsCompleted;
                break;
            default:
                break;
        }
        c.status = Contract.Status.ACTIVE;
        return c;
    }

    /** Whether the world still has what {@code c} is about, so it can be taken now. */
    private boolean acceptable(Contract c) {
        switch (c.type) {
            case ESCORT:
                return world.macroSim.freighters().get(c.targetUuid) != null;
            case CONVOY:
                for (String f : c.targets) if (world.macroSim.freighters().get(f) == null) return false;
                return true;
            case BOUNTY:
            case RECON:
                return world.engine.getEntity(c.targetUuid) != null;
            case RAID:
                return world.pirateBase.standing();
            default:
                return true;
        }
    }

    private OperationDef operationDef(String id) {
        var content = world.getContent();
        return content == null || id == null || !content.defs.has("operation", id) ? null
                : content.defs.get("operation", id, OperationDef.class);
    }

    /** The operations the giver of this board offers today: unlocked by rank, not done, none under way. */
    private List<Contract> operations(int giver) {
        List<Contract> out = new ArrayList<>();
        var content = world.getContent();
        FactionStanding s = world.standing;
        if (content == null || running != null) return out;
        for (OperationDef def : content.defs.all("operation", OperationDef.class)) {
            if (operationsDone.contains(def.id) || def.steps.isEmpty() || !ready(def)) continue;
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
            c.deadline = now() + DAY;
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
    private boolean ready(OperationDef def) {
        if (def.requires == null || def.requires.isEmpty()) return true;
        if ("base_fallen".equals(def.requires)) return world.pirateBase.uuid() != null && !world.pirateBase.standing();
        return false;
    }

    private int rankIndex(String id) {
        RankLadder ranks = world.standing.ranks();
        if (id == null) return 0;
        for (int i = 0; i < ranks.size(); i++) {
            RankDef r = ranks.get(i);
            if (id.equals(r.id)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ waypoints

    /** True if {@code uuid} is where one of the player's contracts points now. */
    public boolean isWaypoint(String uuid) {
        if (uuid == null) return false;
        for (Contract c : active) if (uuid.equals(c.waypointUuid())) return true;
        return false;
    }

    /** "Deliver 120 Water to Highport: 1.4 d left, 340 km", for the HUD. */
    public String describe(Contract c) {
        return c.heading() + ": " + progress(c);
    }

    /** "1.4 d left, 340 km": the time left and how far off the waypoint is. */
    public String progress(Contract c) {
        StringBuilder out = new StringBuilder();
        out.append(RailsTrafficMap.formatEta(c.deadline - now())).append(" left");
        if (c.type == Contract.Type.CONVOY) {
            out.append(String.format(Locale.ROOT, ", %d of %d in", c.count(Contract.MARK_GOOD), c.targets.length));
        } else if (c.type == Contract.Type.DEFEND) {
            out.append(String.format(Locale.ROOT, ", %d of %d beaten", c.count(Contract.MARK_GOOD), c.targets.length));
        } else if (c.type == Contract.Type.RECOVERY && c.progress == 0 && c.arrivedAt >= 0) {
            out.append(String.format(Locale.ROOT, ", carrying across %.0f s", Math.max(0,
                    Derelicts.SALVAGE_SECONDS - (now() - c.arrivedAt))));
        }
        double d = distanceTo(world.getPlayer(), c.waypointUuid());
        if (!Double.isNaN(d) && !Double.isInfinite(d)) {
            out.append(", ").append(d < 10_000 ? String.format(Locale.ROOT, "%.1f km", d / 1000)
                    : String.format(Locale.ROOT, "%,.0f km", d / 1000));
        }
        return out.toString();
    }

    /** Marks each contract's waypoint on the HUD, and unmarks what no longer is one. */
    private void updateMarkers() {
        Set<Entity> now = new HashSet<>();
        for (Contract c : active) {
            Entity e = world.engine.getEntity(c.waypointUuid());
            HudMarkerComponent m = e == null ? null : e.getComponent(HudMarkerComponent.class);
            if (m == null) continue;
            now.add(e);
            if (!m.label.endsWith(MARKER_SUFFIX)) m.label += MARKER_SUFFIX;
            m.color.set(HudMarkerComponent.Kind.WAYPOINT.defaultColor);
        }
        for (Entity e : marked) {
            if (now.contains(e)) continue;
            HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
            if (m == null) continue;
            if (m.label.endsWith(MARKER_SUFFIX)) m.label = m.label.substring(0, m.label.length() - MARKER_SUFFIX.length());
            m.color.set(m.kind.defaultColor);
        }
        marked = now;
    }

    // ------------------------------------------------------------------ posting

    /** A day's board for a station: a few of each kind of job the world offers there, the first open to anyone. */
    private List<Contract> post(String stationUuid, long day) {
        List<Contract> out = new ArrayList<>();
        Entity station = world.engine.getEntity(stationUuid);
        EconomyNode node = world.macroSim.station(stationUuid);
        int giver = world.standing.factionOf(station);
        FactionDef def = world.standing.factions().def(giver);
        if (station == null || node == null || def == null || def.hostileToAll) return out;
        Random rnd = new Random(NewGame.mix(world.stationDice(stationUuid), day));
        List<List<Contract>> kinds = new ArrayList<>();
        kinds.add(deliveries(node, giver));
        kinds.add(escorts(node, giver));
        kinds.add(bounties(node, giver));
        kinds.add(patrols(node, giver));
        kinds.add(convoys(node, giver));
        for (List<Contract> k : kinds) Collections.shuffle(k, rnd);
        // Named aces come first, then wanted ships close by (in the bubble), so a bounty can be flown at once
        kinds.get(2).sort(java.util.Comparator.<Contract, Boolean>comparing(c -> !isAce(c.targetUuid))
                .thenComparing(c -> !inBubble(c.targetUuid)));
        // ...and near lanes come first, so with many stations the patrol on show is one a newcomer may fly
        kinds.get(3).sort(java.util.Comparator.comparingInt(c -> c.tier));
        // An operation the player has earned heads the board, and a raid on the pirates' base after it
        out.addAll(operations(giver));
        out.addAll(raids(giver, false));
        // A rival of the sponsor that distrusts the player offers fewer jobs (round 6)
        int limit = world.tension.offersFor(giver, OFFERS_PER_STATION);
        // Round robin over the kinds, so a board shows a spread
        for (int i = 0; out.size() < limit; i++) {
            boolean any = false;
            for (List<Contract> k : kinds) {
                if (i < k.size() && out.size() < limit) {
                    out.add(k.get(i));
                    any = true;
                }
            }
            if (!any) break;
        }
        // High tension at its stations: more work for an armed ship, bounties first, then escorts
        int combat = world.tension.combatJobs(giver);
        for (List<Contract> k : List.of(kinds.get(2), kinds.get(1))) {
            for (Contract c : k) {
                if (combat <= 0) break;
                if (out.contains(c)) continue;
                c.reason = c.reason == null || c.reason.isEmpty() ? TENSION_JOB : c.reason;
                out.add(c);
                combat--;
            }
        }
        // The board always has something a newcomer may take
        boolean open = false;
        for (Contract c : out) open |= c.tier == 0;
        if (!open && !out.isEmpty()) {
            for (Contract c : out) {
                if (c.type == Contract.Type.OPERATION || c.type == Contract.Type.RAID) continue;
                c.tier = 0;
                break;
            }
        }
        for (Contract c : out) {
            if (c.type != Contract.Type.OPERATION) senior(c);
            c.id = nextId++;
            c.giver = def.id;
            c.stationUuid = stationUuid;
            c.stationName = node.displayName;
        }
        return out;
    }

    /**
     * From {@link #SENIOR_RANK} on, a single job pays {@link #SENIOR_STANDING} of its standing: a senior
     * officer climbs on operations and raids (whose own standing is untouched), not on one more bounty.
     */
    private void senior(Contract c) {
        if (c.type == Contract.Type.RAID || world.standing.record().rank < SENIOR_RANK) return;
        c.standing = Math.max(1, Math.round(c.standing * SENIOR_STANDING));
    }

    /** Hauls from this station's open trade orders, sized to the player's hold. */
    private List<Contract> deliveries(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        TraderComponent trader = world.getPlayer() == null ? null : world.getPlayer().getComponent(TraderComponent.class);
        if (trader == null || trader.cargoCapacity <= 0) return out;
        for (TradeOrder o : world.macroSim.economy().orders()) {
            if (o.claimedBy != null || !o.fromUuid.equals(node.stationUuid)) continue;
            EconomyNode to = world.macroSim.station(o.toUuid);
            CommodityType type = world.macroSim.economy().catalog().commodity(o.commodity);
            if (to == null || type == null || type.unitVolume <= 0) continue;
            double qty = Math.floor(Math.min(o.quantity, trader.cargoCapacity / type.unitVolume));
            if (qty < 1) continue;
            Contract c = new Contract();
            c.type = Contract.Type.DELIVERY;
            c.orderId = o.id;
            c.commodity = o.commodity;
            c.commodityName = type.displayName == null ? o.commodity : type.displayName;
            c.quantity = qty;
            c.unitPrice = o.sellPrice;
            c.targetUuid = o.toUuid;
            c.targetName = name(to);
            double value = qty * o.sellPrice;
            c.tier = value < 20_000 ? 0 : value < 60_000 ? 1 : 2;
            c.reward = Math.round(Math.max(DELIVERY_MIN_FEE, DELIVERY_FEE_SHARE * value));
            c.standing = 3 + 2 * c.tier;
            c.deadline = now() + DAY * (far(node.stationUuid, o.toUuid) ? FAR_DAYS : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    /** Freighters hauling an order out of this station, to be seen safely to its buyer. */
    private List<Contract> escorts(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        for (TradeOrder o : world.macroSim.economy().orders()) {
            if (o.claimedBy == null || !o.fromUuid.equals(node.stationUuid) || targeted(o.claimedBy)) continue;
            FreighterComponent f = world.macroSim.freighters().get(o.claimedBy);
            EconomyNode to = world.macroSim.station(o.toUuid);
            if (f == null || to == null) continue;
            boolean far = far(node.stationUuid, o.toUuid);
            Contract c = new Contract();
            c.type = Contract.Type.ESCORT;
            c.orderId = o.id;
            c.targetUuid = o.claimedBy;
            c.targetName = f.displayName == null ? "the freighter" : f.displayName;
            c.destinationUuid = o.toUuid;
            c.destinationName = name(to);
            c.tier = far ? 1 : 0;
            c.reward = ESCORT_PAY * (far ? FAR_PAY : 1);
            c.standing = 4 + 2 * c.tier;
            double eta = Double.isNaN(f.arrivalTime) ? now() : f.arrivalTime;
            c.deadline = Math.max(eta, now()) + DAY * (far ? FAR_DAYS : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    private boolean isAce(String uuid) {
        Entity e = world.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        return h != null && h.ace;
    }

    private boolean inBubble(String uuid) {
        Entity e = world.engine.getEntity(uuid);
        return e != null && e.getComponent(PhysicsBodyComponent.class) != null;
    }

    /** Ships at war with the giver, priced by how they measure up to the player's and by {@link Difficulty}. */
    List<Contract> bounties(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        Entity player = world.getPlayer();
        double own = player == null ? 0 : Difficulty.shipValue(player);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(SHIPS)) {
            int faction = e.getComponent(ShipTaskBoardComponent.class).faction;
            String uuid = e.getComponent(UuidComponent.class).id;
            if (e == player || !world.standing.factions().known(faction) || !world.standing.factions().atWar(giver, faction)
                    || targeted(uuid) || FirstPlay.beaten(world, e)) continue;
            // The pirate base's moored guns go with the base's raid, not a bounty of their own
            HostileComponent moored = e.getComponent(HostileComponent.class);
            if (moored != null && moored.moored()) continue;
            HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
            Contract c = new Contract();
            c.type = Contract.Type.BOUNTY;
            c.targetUuid = uuid;
            String label = m == null ? "" : m.label.replace(MARKER_SUFFIX, "");
            // A pirate out on the rails has no marker yet, but it has a name
            HostileComponent hostile = e.getComponent(HostileComponent.class);
            if (label.isEmpty() && hostile != null && hostile.displayName != null) label = hostile.displayName;
            c.targetName = (label.isEmpty() ? "a ship" : label) + " of the " + world.standing.factions().name(faction);
            double ratio = own <= 0 ? 1 : Difficulty.shipValue(e) / own;
            c.tier = ratio <= 1.2 ? 0 : ratio <= 2 ? 1 : 2;
            c.reward = Math.round(BOUNTY_PAY * (1 + c.tier) * Difficulty.of(world));
            c.standing = 6 + 3 * c.tier;
            // A pirate known for robbing freighters is worth more
            int robbed = hostile == null ? 0 : hostile.plunders;
            if (robbed > 0) {
                c.record = record(hostile);
                // An ace's price already counts what made it an ace (round 5: the two stacked to twelve
                // times a bounty, and an ace that kept robbing paid a rank's standing at once)
                if (!hostile.ace) {
                    c.reward = Math.round(c.reward * (1 + RECORD_PREMIUM * Math.min(robbed, RECORD_CAP)));
                    c.standing += Math.min(robbed, RECORD_CAP);
                }
            }
            if (hostile != null && hostile.ace) {
                // A named ace pays far more, and the giver remembers who brought it in
                c.targetName = "the ace " + c.targetName;
                c.reward = Math.round(c.reward * PirateAces.BOUNTY_SCALE);
                c.standing += PirateAces.BOUNTY_STANDING;
            }
            // Beating a ship far weaker than the player's earns less standing: a Commander in a frigate
            // climbs on what can hurt a frigate, not on light raiders (round 5's late career)
            if (ratio < WEAK_RATIO) c.standing = Math.round(c.standing * WEAK_STANDING);
            c.deadline = now() + 3 * DAY;
            out.add(c);
        }
        return out;
    }

    /** "robbed 2 freighters, last of Water": what a pirate's bounty says it has done. */
    String record(HostileComponent h) {
        String what = h.plunders == 1 ? "robbed a freighter" : "robbed " + h.plunders + " freighters";
        if (h.lootCommodity == null) return what;
        CommodityType type = world.macroSim.economy().catalog().commodity(h.lootCommodity);
        return what + ", last of " + (type == null || type.displayName == null ? h.lootCommodity : type.displayName);
    }

    /** Freighters hauling out of this station to the same buyer, two or more, to be seen in together. */
    private List<Contract> convoys(EconomyNode node, int giver) {
        Map<String, List<TradeOrder>> byBuyer = new LinkedHashMap<>();
        for (TradeOrder o : world.macroSim.economy().orders()) {
            if (o.claimedBy == null || !o.fromUuid.equals(node.stationUuid) || targeted(o.claimedBy)) continue;
            if (world.macroSim.freighters().get(o.claimedBy) == null || world.macroSim.station(o.toUuid) == null) continue;
            List<TradeOrder> run = byBuyer.computeIfAbsent(o.toUuid, k -> new ArrayList<>());
            boolean again = false;
            for (TradeOrder r : run) again |= r.claimedBy.equals(o.claimedBy);
            if (!again) run.add(o);
        }
        List<Contract> out = new ArrayList<>();
        for (Map.Entry<String, List<TradeOrder>> e : byBuyer.entrySet()) {
            List<TradeOrder> run = e.getValue();
            if (run.size() < 2) continue;
            boolean far = far(node.stationUuid, e.getKey());
            Contract c = new Contract();
            c.type = Contract.Type.CONVOY;
            int n = run.size();
            c.targets = new String[n];
            c.targetNames = new String[n];
            c.marks = new int[n];
            c.trips = new int[n];
            double eta = now();
            for (int i = 0; i < n; i++) {
                FreighterComponent f = world.macroSim.freighters().get(run.get(i).claimedBy);
                c.targets[i] = run.get(i).claimedBy;
                c.targetNames[i] = f.displayName == null ? "a freighter" : f.displayName;
                if (!Double.isNaN(f.arrivalTime)) eta = Math.max(eta, f.arrivalTime);
            }
            c.targetUuid = c.targets[0];
            c.targetName = c.targetNames[0];
            c.destinationUuid = e.getKey();
            c.destinationName = name(world.macroSim.station(e.getKey()));
            c.tier = far || n >= 3 ? 1 : 0;
            c.reward = CONVOY_PAY * n * (far ? FAR_PAY : 1);
            c.standing = 3 + 2 * n;
            c.deadline = eta + DAY * (far ? FAR_DAYS : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    /**
     * A raid on the pirates' base, from the sponsor once it is found and while it stands; an
     * operation's raid step ({@code step}) needs no rank of its own.
     */
    private List<Contract> raids(int giver, boolean step) {
        List<Contract> out = new ArrayList<>();
        PirateBase base = world.pirateBase;
        String uuid = base.uuid();
        if (uuid == null || !base.standing() || !base.found() || targeted(uuid)) return out;
        if (!step && giver != world.standing.sponsor()) return out;
        Contract c = new Contract();
        c.type = Contract.Type.RAID;
        c.targetUuid = uuid;
        c.targetName = base.name();
        c.tier = step ? 0 : RAID_RANK;
        c.reward = Math.round(RAID_PAY * Difficulty.of(world));
        c.standing = 15;
        c.deadline = now() + DAY * RAID_DAYS;
        c.reason = "Take it or leave no pirate alive aboard";
        out.add(c);
        return out;
    }

    /** Pirates at war with the giver, to be found where they lurk; {@code skip} (a pirate already found) last. */
    private List<Contract> recons(int giver, String skip) {
        List<Contract> out = new ArrayList<>();
        for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
            HostileComponent h = e.getValue();
            Entity ship = world.engine.getEntity(e.getKey());
            int faction = ship == null ? -1 : world.standing.factionOf(ship);
            if (!h.isPirate() || h.moored() || ship == null || e.getKey().equals(skip) || targeted(e.getKey())) continue;
            if (faction >= 0 && !world.standing.factions().atWar(giver, faction)) continue;
            Contract c = new Contract();
            c.type = Contract.Type.RECON;
            c.targetUuid = e.getKey();
            c.targetName = h.displayName == null ? "a pirate" : h.displayName;
            EconomyNode lurk = h.stationUuid == null ? null : world.macroSim.station(h.stationUuid);
            boolean far = lurk != null && world.getPlayer() != null && far(lurk.stationUuid, world.universe.uuidOf(world.getPlayer()));
            c.tier = far ? 1 : 0;
            c.reward = RECON_PAY * (far ? FAR_PAY : 1);
            c.standing = 3;
            if (h.ace) {
                // A named ace moves about and is worth knowing where it rests
                c.targetName = "the ace " + c.targetName;
                c.reward *= 2;
                c.reason = "A named ace: find where it rests";
            }
            c.deadline = now() + DAY * (far ? FAR_DAYS : NEAR_DAYS + 1);
            out.add(c);
        }
        return out;
    }

    /** Lanes from this station out to each other station and back; the giver's own stations first. */
    private List<Contract> patrols(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        for (EconomyNode other : world.macroSim.stations()) {
            if (other == node) continue;
            int owner = world.standing.factionOf(world.engine.getEntity(other.stationUuid));
            if (world.standing.factions().atWar(giver, owner)) continue;
            boolean far = far(node.stationUuid, other.stationUuid);
            Contract c = new Contract();
            c.type = Contract.Type.PATROL;
            c.targetUuid = other.stationUuid;
            c.targetName = name(other);
            c.destinationUuid = node.stationUuid;
            c.destinationName = name(node);
            c.tier = far ? 1 : 0;
            c.reward = PATROL_PAY * (far ? FAR_PAY : 1) * (owner == giver ? 1 : 1.5);
            c.standing = 3 + 2 * c.tier;
            c.deadline = now() + DAY * (far ? FAR_DAYS + 2 : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private boolean targeted(String uuid) {
        for (Contract c : active) if (uuid.equals(c.targetUuid)) return true;
        return false;
    }

    private Contract findOffer(long id) {
        for (Contract c : extra) if (c.id == id && c.status == Contract.Status.OFFERED) return c;
        for (StationOffers board : boards.values()) {
            for (Contract c : board.offers) if (c.id == id && c.status == Contract.Status.OFFERED) return c;
        }
        return null;
    }

    private TradeOrder order(long id) {
        for (TradeOrder o : world.macroSim.economy().orders()) if (o.id == id) return o;
        return null;
    }

    /** True if the two craft orbit different bodies, so the job means a transfer between them. */
    private boolean far(String uuidA, String uuidB) {
        Entity ea = world.engine.getEntity(uuidA), eb = world.engine.getEntity(uuidB);
        Entity pa = ea == null ? null : world.universe.parentOf(ea), pb = eb == null ? null : world.universe.parentOf(eb);
        return pa != pb;
    }

    private static String name(EconomyNode node) {
        return node.displayName == null ? "the station" : node.displayName;
    }

    private double now() {
        return world.engine.getSimSeconds();
    }

    /** Meters from {@code from} to the craft or derelict {@code uuid}; NaN if it cannot be placed. */
    double distanceTo(Entity from, String uuid) {
        Entity point = uuid == null ? null : world.engine.getEntity(uuid);
        if (point != null) return distance(from, point);
        Derelicts.Derelict d = world.derelicts.get(uuid);
        if (from == null || d == null || !position(from, a) || !world.derelicts.position(d, now(), b)) return Double.NaN;
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /** Meters between two craft, on the rails or in the bubble; infinite if either cannot be placed. */
    double distance(Entity from, Entity to) {
        if (from == null || to == null || !position(from, a) || !position(to, b)) return Double.POSITIVE_INFINITY;
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /** A craft's position relative to the root body: its orbit on the rails, or its body in the bubble. */
    private boolean position(Entity e, StateVector out) {
        double t = now();
        if (world.universe.orbit(e) != null) {
            world.universe.absoluteState(e, t, out);
            return true;
        }
        PhysicsBodyComponent pb = e.getComponent(PhysicsBodyComponent.class);
        Entity parent = world.universe.entity(world.physics.getAnchorParentUuid());
        if (pb == null || pb.body == null || parent == null) return false;
        world.physics.getTrueState(pb.body, trueState);
        world.universe.absoluteState(parent, t, out);
        out.x += trueState[0];
        out.y += trueState[1];
        return true;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public String saveKey() {
        return "contracts";
    }

    @Override
    public void write(Json json) {
        json.writeValue("nextId", nextId);
        json.writeValue("boards", boards.values().toArray(new StationOffers[0]), StationOffers[].class);
        json.writeValue("active", active.toArray(new Contract[0]), Contract[].class);
        json.writeValue("history", history.toArray(new Contract[0]), Contract[].class);
        json.writeValue("extra", extra.toArray(new Contract[0]), Contract[].class);
        if (running != null) json.writeValue("operation", running, Operation.class);
        json.writeValue("operationsDone", operationsDone.toArray(new String[0]), String[].class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        nextId = data.getLong("nextId", 1);
        StationOffers[] saved = data.has("boards") ? json.readValue(StationOffers[].class, data.get("boards")) : null;
        if (saved != null) for (StationOffers s : saved) if (s != null && s.station != null) boards.put(s.station, s);
        Contract[] taken = data.has("active") ? json.readValue(Contract[].class, data.get("active")) : null;
        if (taken != null) for (Contract c : taken) if (c != null && c.type != null) active.add(c);
        Contract[] old = data.has("history") ? json.readValue(Contract[].class, data.get("history")) : null;
        if (old != null) for (Contract c : old) if (c != null && c.type != null) history.add(c);
        Contract[] posted = data.has("extra") ? json.readValue(Contract[].class, data.get("extra")) : null;
        if (posted != null) for (Contract c : posted) if (c != null && c.type != null) extra.add(c);
        running = data.has("operation") ? json.readValue(Operation.class, data.get("operation")) : null;
        String[] done = data.has("operationsDone") ? json.readValue(String[].class, data.get("operationsDone")) : null;
        if (done != null) operationsDone.addAll(java.util.Arrays.asList(done));
        marked = new HashSet<>();
    }
}
