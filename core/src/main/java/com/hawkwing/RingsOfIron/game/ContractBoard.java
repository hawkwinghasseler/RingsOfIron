package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.coreengine.EntityRemovalListener;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.ShipNames;
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
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.function.BooleanSupplier;

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
public class ContractBoard extends EntitySystem implements SaveParticipant, Resettable {
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

    static final Family SHIPS = Family.all(ShipTaskBoardComponent.class, UuidComponent.class)
            .exclude(StationComponent.class, PlayerControllerComponent.class).get();

    /** One station's posted offers and the day they were posted. */
    public static class StationOffers {
        public String station;
        public long day = -1;
        public Contract[] offers = new Contract[0];
        /** The player's rank when the board was posted or last looked at; -1 if not known (an older save). */
        public int rank = -1;
    }

    final GameContext ctx;
    private final BooleanSupplier crashed;
    final CrewService crew;
    private final PhysicsManager physics;
    final RailsUniverse universe;
    final MacroSimulation macroSim;
    final DockingSystem docking;
    final FactionStanding standing;
    private final ToLongFunction<String> stationDice;
    final Supplier<CrewHiring> crewHiring;
    final Supplier<Derelicts> derelicts;
    final Supplier<PirateBase> pirateBase;
    private final Supplier<FactionTension> tension;
    private final Map<String, StationOffers> boards = new LinkedHashMap<>();
    final List<Contract> active = new ArrayList<>();
    /** Ended contracts, newest last, at most {@link #HISTORY}. */
    private final List<Contract> history = new ArrayList<>();
    static final int HISTORY = 20;
    long nextId = 1;
    /** Offers posted outside the daily boards (world events, pods and wrecks), each on its station's board until taken or late. */
    private final List<Contract> extra = new ArrayList<>();
    /** The operation under way, or null. */
    Operation running;
    /** Ids of the operations done, never offered again. */
    final Set<String> operationsDone = new HashSet<>();
    /** Entities whose markers point at a contract waypoint; not saved, the markers are rebuilt. */
    private Set<Entity> marked = new HashSet<>();
    /** Contract news not yet shown; the screen drains it. */
    public final List<String> notices = new ArrayList<>();
    private final StateVector a = new StateVector(), b = new StateVector();
    private final double[] trueState = new double[4];
    final ContractPostings postings;
    final RescueContracts rescues;
    final GroupContracts groups;
    final ContractOperations operations;

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

    public ContractBoard(GameContext ctx, BooleanSupplier crashed, CrewService crew, PhysicsManager physics, RailsUniverse universe, MacroSimulation macroSim, DockingSystem docking, FactionStanding standing, ToLongFunction<String> stationDice, Supplier<CrewHiring> crewHiring, Supplier<Derelicts> derelicts, Supplier<PirateBase> pirateBase, Supplier<FactionTension> tension) {
        this.ctx = ctx;
        this.crashed = crashed;
        this.crew = crew;
        this.physics = physics;
        this.universe = universe;
        this.macroSim = macroSim;
        this.docking = docking;
        this.standing = standing;
        this.stationDice = stationDice;
        this.crewHiring = crewHiring;
        this.derelicts = derelicts;
        this.pirateBase = pirateBase;
        this.tension = tension;
        // A waypoint gone from the engine comes back from its pool as a new entity, whose marker isn't ours to unmark
        ctx.engine.getEcsEngine().addEntityListener((EntityRemovalListener) e -> marked.remove(e));
        postings = new ContractPostings(this);
        rescues = new RescueContracts(this);
        groups = new GroupContracts(this);
        operations = new ContractOperations(this);
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
            board.rank = standing.record().rank;
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
        Entity target = ctx.engine.getEntity(c.targetUuid);
        return target != null && !FirstPlay.beaten(ctx.engine, target) && !FirstPlay.aceBeaten(target) && !targeted(c.targetUuid);
    }

    /**
     * A promotion since {@code board} was posted adds the operations the new rank opens to it at once,
     * rather than the next day.
     */
    private void promoted(StationOffers board) {
        int rank = standing.record().rank;
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
        Entity station = ctx.engine.getEntity(board.station);
        EconomyNode node = macroSim.station(board.station);
        int giver = standing.factionOf(station);
        FactionDef def = standing.factions().def(giver);
        if (station == null || node == null || def == null || def.hostileToAll) return;
        // The operations the new rank shows take the place of those the board showed
        List<Contract> more = new ArrayList<>();
        List<Contract> kept = new ArrayList<>();
        List<Contract> ops = operations.operations(giver);
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
        Entity station = ctx.engine.getEntity(c.stationUuid);
        EconomyNode node = macroSim.station(c.stationUuid);
        int owner = standing.factionOf(station);
        FactionDef def = standing.factions().def(owner);
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
        EconomyNode node = macroSim.station(stationUuid);
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
            CommodityType type = macroSim.economy().catalog().commodity(d.commodity);
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
        FactionStanding s = standing;
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
                EconomyNode to = macroSim.station(c.targetUuid);
                if (order == null || order.claimedBy != null || to == null) return "A freighter has taken that cargo";
                order.claimedBy = PLAYER_CLAIM;
                order.quantity = c.quantity;
                to.expectIncoming(c.commodity, c.quantity);
                break;
            }
            case ESCORT: {
                FreighterComponent f = macroSim.freighters().get(c.targetUuid);
                if (f == null) return c.targetName + " is gone";
                c.progress = f.tripsCompleted;
                break;
            }
            case BOUNTY:
            case RECON:
                if (ctx.engine.getEntity(c.targetUuid) == null) return c.targetName + " is gone";
                break;
            case RESCUE:
            case RECOVERY:
                if (derelicts.get().get(c.targetUuid) == null) return c.targetName + " is lost";
                break;
            case RAID:
                if (!pirateBase.get().standing()) return c.targetName + " has already fallen";
                break;
            case CONVOY:
                for (int i = 0; i < c.targets.length; i++) {
                    FreighterComponent f = macroSim.freighters().get(c.targets[i]);
                    if (f == null) return c.targetNames[i] + " is gone";
                    c.trips[i] = f.tripsCompleted;
                }
                break;
            case OPERATION:
                return operations.startOperation(c);
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
    @Override
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
        Entity player = ctx.player();
        if (player == null || crashed.getAsBoolean()) return;
        for (Contract c : new ArrayList<>(active)) check(c, player);
        // Survivors whose rescue ended some other way go ashore at the next station the ship docks at
        if (docking.dockedWithClearance(player) && macroSim.station(universe.uuidOf(docking.dockedTo(player))) != null) {
            int n = rescues.landSurvivors(player, -1);
            if (n > 0) notices.add(n + (n == 1 ? " survivor goes" : " survivors go") + " ashore at "
                    + ShipNames.name(docking.dockedTo(player)));
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
                rescues.checkRescue(c, player);
                break;
            case RECOVERY:
                rescues.checkRecovery(c, player);
                break;
            case CONVOY:
                groups.checkConvoy(c, player);
                break;
            case DEFEND:
                groups.checkDefend(c);
                break;
            case RECON:
                checkRecon(c, player);
                break;
            case RAID:
                if (!pirateBase.get().standing()) end(c, Contract.Status.DONE, c.targetName + " has fallen");
                break;
            default:
                break;
        }
        if (c.isActive() && now() > c.deadline) {
            if (c.type == Contract.Type.CONVOY || c.type == Contract.Type.DEFEND) {
                // What is still open by then went badly; what went well is paid for
                for (int i = 0; i < c.marks.length; i++) if (c.marks[i] == Contract.MARK_OPEN) c.marks[i] = Contract.MARK_BAD;
                groups.settleGroup(c, "out of time");
            } else {
                end(c, Contract.Status.FAILED, "out of time");
            }
        }
    }

    /** Hands the goods over while docked at the buyer with clearance, paying the agreed price for each unit. */
    private void checkDelivery(Contract c, Entity player) {
        TradeOrder order = c.orderId < 0 ? null : order(c.orderId);
        EconomyNode to = macroSim.station(c.targetUuid);
        if ((order == null && c.orderId >= 0) || to == null) {
            end(c, Contract.Status.CANCELLED, to == null ? c.targetName + " is gone" : "the order was withdrawn");
            return;
        }
        Entity dock = docking.dockedWithClearance(player) ? docking.dockedTo(player) : null;
        TraderComponent trader = player.getComponent(TraderComponent.class);
        if (dock != ctx.engine.getEntity(c.targetUuid) || trader == null) return;
        double units = Math.min(trader.amount(c.commodity), c.quantity - c.delivered);
        if (units <= 1e-6) return;
        double before = trader.credits;
        double sold = macroSim.market().sell(to, trader, c.commodity, units);
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
        FreighterComponent f = macroSim.freighters().get(c.targetUuid);
        if (f == null) {
            end(c, Contract.Status.FAILED, c.targetName + " was lost");
            return;
        }
        if (f.tripsCompleted <= c.progress) {
            if (order(c.orderId) == null) end(c, Contract.Status.CANCELLED, "the haul was called off");
            return;
        }
        if (c.arrivedAt < 0) c.arrivedAt = now();
        Entity station = ctx.engine.getEntity(c.destinationUuid);
        if (distance(player, station) <= ESCORT_RANGE) end(c, Contract.Status.DONE, c.targetName + " docked safely");
        else if (now() - c.arrivedAt > ESCORT_GRACE) end(c, Contract.Status.FAILED, c.targetName + " docked without you");
    }

    private void checkBounty(Contract c) {
        Entity target = ctx.engine.getEntity(c.targetUuid);
        if (target == null) end(c, Contract.Status.DONE, c.targetName + " is gone");
        else if (FirstPlay.beaten(ctx.engine, target)) end(c, Contract.Status.DONE, c.targetName + " is beaten");
    }

    private void checkPatrol(Contract c, Entity player) {
        Entity point = ctx.engine.getEntity(c.waypointUuid());
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

    /** Found once the player comes within {@link #RECON_RANGE} of the pirate, on the rails or in the bubble. */
    private void checkRecon(Contract c, Entity player) {
        Entity pirate = ctx.engine.getEntity(c.targetUuid);
        if (pirate == null) {
            end(c, Contract.Status.CANCELLED, c.targetName + " is gone");
            return;
        }
        if (distance(player, pirate) > RECON_RANGE) return;
        HostileComponent h = pirate.getComponent(HostileComponent.class);
        EconomyNode lurk = h == null || h.stationUuid == null ? null : macroSim.station(h.stationUuid);
        // An intel step's pirate leads the way home to the base, before the raid after it is posted
        if (c.intel) pirateBase.get().reveal("Followed " + c.targetName + " home");
        end(c, Contract.Status.DONE, "found " + c.targetName + (lurk == null ? "" : " by " + name(lurk)));
    }

    /** True if the player is docked with clearance at the station {@code uuid}. */
    boolean dockedAt(Entity player, String uuid) {
        return docking.dockedWithClearance(player) && docking.dockedTo(player) == ctx.engine.getEntity(uuid);
    }

    /** Ends {@code c}: pays a success, charges a failure, releases a delivery's trade order. */
    void end(Contract c, Contract.Status status, String why) {
        active.remove(c);
        c.status = status;
        c.outcome = why;
        FactionStanding s = standing;
        TraderComponent trader = ctx.player() == null ? null : ctx.player().getComponent(TraderComponent.class);
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
        if (c.step > 0) operations.advanceOperation(c);
    }

    /** Hands a delivery's trade order back: what was not delivered is no longer on its way. */
    private void release(Contract c) {
        if (c.orderId < 0) return;
        TradeOrder order = order(c.orderId);
        if (order != null && PLAYER_CLAIM.equals(order.claimedBy)) macroSim.economy().close(order);
        EconomyNode to = macroSim.station(c.targetUuid);
        double undelivered = c.quantity - c.delivered;
        if (to != null && undelivered > 0) to.expectIncoming(c.commodity, -undelivered);
    }

    // ------------------------------------------------------------------ operations

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
        double d = distanceTo(ctx.player(), c.waypointUuid());
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
            Entity e = ctx.engine.getEntity(c.waypointUuid());
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
        Entity station = ctx.engine.getEntity(stationUuid);
        EconomyNode node = macroSim.station(stationUuid);
        int giver = standing.factionOf(station);
        FactionDef def = standing.factions().def(giver);
        if (station == null || node == null || def == null || def.hostileToAll) return out;
        Random rnd = new Random(NewGame.mix(stationDice.applyAsLong(stationUuid), day));
        List<List<Contract>> kinds = new ArrayList<>();
        kinds.add(postings.deliveries(node, giver));
        kinds.add(postings.escorts(node, giver));
        kinds.add(postings.bounties(node, giver));
        kinds.add(postings.patrols(node, giver));
        kinds.add(postings.convoys(node, giver));
        for (List<Contract> k : kinds) Collections.shuffle(k, rnd);
        // Named aces come first, then wanted ships close by (in the bubble), so a bounty can be flown at once
        kinds.get(2).sort(java.util.Comparator.<Contract, Boolean>comparing(c -> !postings.isAce(c.targetUuid))
                .thenComparing(c -> !postings.inBubble(c.targetUuid)));
        // ...and near lanes come first, so with many stations the patrol on show is one a newcomer may fly
        kinds.get(3).sort(java.util.Comparator.comparingInt(c -> c.tier));
        // An operation the player has earned heads the board, and a raid on the pirates' base after it
        out.addAll(operations.operations(giver));
        out.addAll(postings.raids(giver, false));
        // A rival of the sponsor that distrusts the player offers fewer jobs (round 6)
        int limit = tension.get().offersFor(giver, OFFERS_PER_STATION);
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
        int combat = tension.get().combatJobs(giver);
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
    void senior(Contract c) {
        if (c.type == Contract.Type.RAID || standing.record().rank < SENIOR_RANK) return;
        c.standing = Math.max(1, Math.round(c.standing * SENIOR_STANDING));
    }

    /** Ships at war with the giver, priced as bounties ({@link ContractPostings#bounties}). */
    List<Contract> bounties(EconomyNode node, int giver) {
        return postings.bounties(node, giver);
    }

    /** "robbed 2 freighters, last of Water": what a pirate's bounty says it has done. */
    String record(HostileComponent h) {
        return postings.record(h);
    }

    /** Pirates at war with the giver, to be found where they lurk ({@link ContractPostings#recons}). */
    List<Contract> recons(int giver, String skip) {
        return postings.recons(giver, skip);
    }

    /** How far a pod's air has run down, 0 (just cast off) to 1 (out of air). */
    double podExposure(Derelicts.Derelict pod) {
        return rescues.podExposure(pod);
    }

    /** Survivors aboard {@code ship} picked up for a rescue, alive. */
    public int survivorsAboard(Entity ship) {
        return rescues.survivorsAboard(ship);
    }

    // ------------------------------------------------------------------ helpers

    boolean targeted(String uuid) {
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

    TradeOrder order(long id) {
        for (TradeOrder o : macroSim.economy().orders()) if (o.id == id) return o;
        return null;
    }

    /** True if the two craft orbit different bodies, so the job means a transfer between them. */
    boolean far(String uuidA, String uuidB) {
        Entity ea = ctx.engine.getEntity(uuidA), eb = ctx.engine.getEntity(uuidB);
        Entity pa = ea == null ? null : universe.parentOf(ea), pb = eb == null ? null : universe.parentOf(eb);
        return pa != pb;
    }

    static String name(EconomyNode node) {
        return node.displayName == null ? "the station" : node.displayName;
    }

    double now() {
        return ctx.engine.getSimSeconds();
    }

    /** Meters from {@code from} to the craft or derelict {@code uuid}; NaN if it cannot be placed. */
    double distanceTo(Entity from, String uuid) {
        Entity point = uuid == null ? null : ctx.engine.getEntity(uuid);
        if (point != null) return distance(from, point);
        Derelicts.Derelict d = derelicts.get().get(uuid);
        if (from == null || d == null || !position(from, a) || !derelicts.get().position(d, now(), b)) return Double.NaN;
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
        if (universe.orbit(e) != null) {
            universe.absoluteState(e, t, out);
            return true;
        }
        PhysicsBodyComponent pb = e.getComponent(PhysicsBodyComponent.class);
        Entity parent = universe.entity(physics.getAnchorParentUuid());
        if (pb == null || pb.body == null || parent == null) return false;
        physics.getTrueState(pb.body, trueState);
        universe.absoluteState(parent, t, out);
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
