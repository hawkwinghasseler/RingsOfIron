package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
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
 */
public class ContractBoard extends EntitySystem implements SaveParticipant {
    public static final int OFFERS_PER_STATION = 5;
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
    static final double ESCORT_PAY = 1_500, BOUNTY_PAY = 5_000, PATROL_PAY = 1_000;
    /** Pay and time for a job that crosses to another body's orbit. */
    static final double FAR_PAY = 3, NEAR_DAYS = 2, FAR_DAYS = 8;

    private static final Family SHIPS = Family.all(ShipTaskBoardComponent.class, UuidComponent.class)
            .exclude(StationComponent.class, PlayerControllerComponent.class).get();

    /** One station's posted offers and the day they were posted. */
    public static class StationOffers {
        public String station;
        public long day = -1;
        public Contract[] offers = new Contract[0];
    }

    private final GameWorld world;
    private final Map<String, StationOffers> boards = new LinkedHashMap<>();
    private final List<Contract> active = new ArrayList<>();
    /** Ended contracts, newest last, at most {@link #HISTORY}. */
    private final List<Contract> history = new ArrayList<>();
    static final int HISTORY = 20;
    private long nextId = 1;
    /** Entities whose markers point at a contract waypoint; not saved, the markers are rebuilt. */
    private Set<Entity> marked = new HashSet<>();
    /** Contract news not yet shown; the screen drains it. */
    public final List<String> notices = new ArrayList<>();
    private final StateVector a = new StateVector(), b = new StateVector();
    private final double[] trueState = new double[4];

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
            boards.put(stationUuid, board);
        }
        List<Contract> out = new ArrayList<>();
        for (Contract c : board.offers) if (c.status == Contract.Status.OFFERED) out.add(c);
        return out;
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
            return s.record().rank >= c.tier ? null : "Needs rank " + s.ranks().get(c.tier).name;
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
        if (active.size() >= MAX_ACTIVE) return "You already hold " + MAX_ACTIVE + " contracts";
        if (now() > c.deadline) return "Too late for that one";
        switch (c.type) {
            case DELIVERY: {
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
                if (world.engine.getEntity(c.targetUuid) == null) return c.targetName + " is gone";
                break;
            default:
                break;
        }
        c.status = Contract.Status.ACTIVE;
        active.add(c);
        notices.add("Contract taken: " + c.title());
        return null;
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
        nextId = 1;
    }

    // ------------------------------------------------------------------ checking

    @Override
    public void update(float deltaTime) {
        Entity player = world.getPlayer();
        if (player == null || world.getCrash() != null) return;
        for (Contract c : new ArrayList<>(active)) check(c, player);
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
            default:
                checkPatrol(c, player);
                break;
        }
        if (c.isActive() && now() > c.deadline) end(c, Contract.Status.FAILED, "out of time");
    }

    /** Hands the goods over while docked at the buyer with clearance, paying the agreed price for each unit. */
    private void checkDelivery(Contract c, Entity player) {
        TradeOrder order = order(c.orderId);
        EconomyNode to = world.macroSim.station(c.targetUuid);
        if (order == null || to == null) {
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
        to.expectIncoming(c.commodity, -sold);
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
    }

    /** Hands a delivery's trade order back: what was not delivered is no longer on its way. */
    private void release(Contract c) {
        TradeOrder order = order(c.orderId);
        if (order != null && PLAYER_CLAIM.equals(order.claimedBy)) world.macroSim.economy().close(order);
        EconomyNode to = world.macroSim.station(c.targetUuid);
        double undelivered = c.quantity - c.delivered;
        if (to != null && undelivered > 0) to.expectIncoming(c.commodity, -undelivered);
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
        return c.title() + ": " + progress(c);
    }

    /** "1.4 d left, 340 km": the time left and how far off the waypoint is. */
    public String progress(Contract c) {
        StringBuilder out = new StringBuilder();
        out.append(RailsTrafficMap.formatEta(c.deadline - now())).append(" left");
        Entity point = world.engine.getEntity(c.waypointUuid());
        double d = point == null ? Double.NaN : distance(world.getPlayer(), point);
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
        Random rnd = new Random(stationUuid.hashCode() * 31L + day);
        List<List<Contract>> kinds = new ArrayList<>();
        kinds.add(deliveries(node, giver));
        kinds.add(escorts(node, giver));
        kinds.add(bounties(node, giver));
        kinds.add(patrols(node, giver));
        for (List<Contract> k : kinds) Collections.shuffle(k, rnd);
        // Named aces come first, then wanted ships close by (in the bubble), so a bounty can be flown at once
        kinds.get(2).sort(java.util.Comparator.<Contract, Boolean>comparing(c -> !isAce(c.targetUuid))
                .thenComparing(c -> !inBubble(c.targetUuid)));
        // Round robin over the kinds, so a board shows a spread
        for (int i = 0; out.size() < OFFERS_PER_STATION; i++) {
            boolean any = false;
            for (List<Contract> k : kinds) {
                if (i < k.size() && out.size() < OFFERS_PER_STATION) {
                    out.add(k.get(i));
                    any = true;
                }
            }
            if (!any) break;
        }
        // The board always has something a newcomer may take
        boolean open = false;
        for (Contract c : out) open |= c.tier == 0;
        if (!open && !out.isEmpty()) out.get(0).tier = 0;
        for (Contract c : out) {
            c.id = nextId++;
            c.giver = def.id;
            c.stationUuid = stationUuid;
            c.stationName = node.displayName;
        }
        return out;
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
    private List<Contract> bounties(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        Entity player = world.getPlayer();
        double own = player == null ? 0 : Difficulty.shipValue(player);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(SHIPS)) {
            int faction = e.getComponent(ShipTaskBoardComponent.class).faction;
            String uuid = e.getComponent(UuidComponent.class).id;
            if (e == player || !world.standing.factions().known(faction) || !world.standing.factions().atWar(giver, faction)
                    || targeted(uuid) || FirstPlay.beaten(world, e)) continue;
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
            if (hostile != null && hostile.ace) {
                // A named ace pays far more, and the giver remembers who brought it in
                c.targetName = "the ace " + c.targetName;
                c.reward = Math.round(c.reward * PirateAces.BOUNTY_SCALE);
                c.standing += PirateAces.BOUNTY_STANDING;
            }
            c.deadline = now() + 3 * DAY;
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
        marked = new HashSet<>();
    }
}
