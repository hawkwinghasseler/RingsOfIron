package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Supplier;

import static com.hawkwing.RingsOfIron.game.ContractBoard.*;

/**
 * {@link ContractBoard}'s job postings, one method per kind: deliveries, escorts, bounties, convoys,
 * raids, recons and patrols, each drawn from the world as it is now.
 */
final class ContractPostings {
    private final ContractBoard board;
    private final GameContext ctx;
    private final RailsUniverse universe;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final Supplier<PirateBase> pirateBase;

    ContractPostings(ContractBoard board) {
        this.board = board;
        this.ctx = board.ctx;
        this.universe = board.universe;
        this.macroSim = board.macroSim;
        this.standing = board.standing;
        this.pirateBase = board.pirateBase;
    }

    /** Hauls from this station's open trade orders, sized to the player's hold. */
    List<Contract> deliveries(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        TraderComponent trader = ctx.player() == null ? null : ctx.player().getComponent(TraderComponent.class);
        if (trader == null || trader.cargoCapacity <= 0) return out;
        for (TradeOrder o : macroSim.economy().orders()) {
            if (o.claimedBy != null || !o.fromUuid.equals(node.stationUuid)) continue;
            EconomyNode to = macroSim.station(o.toUuid);
            CommodityType type = macroSim.economy().catalog().commodity(o.commodity);
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
            c.targetName = ContractBoard.name(to);
            double value = qty * o.sellPrice;
            c.tier = value < 20_000 ? 0 : value < 60_000 ? 1 : 2;
            c.reward = Math.round(Math.max(DELIVERY_MIN_FEE, DELIVERY_FEE_SHARE * value));
            c.standing = 3 + 2 * c.tier;
            c.deadline = board.now() + DAY * (board.far(node.stationUuid, o.toUuid) ? FAR_DAYS : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    /** Freighters hauling an order out of this station, to be seen safely to its buyer. */
    List<Contract> escorts(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        for (TradeOrder o : macroSim.economy().orders()) {
            if (o.claimedBy == null || !o.fromUuid.equals(node.stationUuid) || board.targeted(o.claimedBy)) continue;
            FreighterComponent f = macroSim.freighters().get(o.claimedBy);
            EconomyNode to = macroSim.station(o.toUuid);
            if (f == null || to == null) continue;
            boolean far = board.far(node.stationUuid, o.toUuid);
            Contract c = new Contract();
            c.type = Contract.Type.ESCORT;
            c.orderId = o.id;
            c.targetUuid = o.claimedBy;
            c.targetName = f.displayName == null ? "the freighter" : f.displayName;
            c.destinationUuid = o.toUuid;
            c.destinationName = ContractBoard.name(to);
            c.tier = far ? 1 : 0;
            c.reward = ESCORT_PAY * (far ? FAR_PAY : 1);
            c.standing = 4 + 2 * c.tier;
            c.deadline = dueAtBuyer(f, far) + DAY * (far ? FAR_DAYS : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }

    /**
     * About when a freighter claiming an order should reach the buyer, sim seconds. One already loaded
     * has its arrival time (the buyer, or an SOI crossing on the way there). One still flying in to
     * load has the rest of that leg and then the haul ahead of it, and while it crosses into another
     * body's sphere its arrival time is only the crossing. Round 9: seed 12 was offered an escort whose
     * freighter was a week from loading, with the deadline two days past the crossing, and lost
     * Operation Lifeline when it ran out of time.
     */
    double dueAtBuyer(FreighterComponent f, boolean far) {
        double eta = Double.isNaN(f.arrivalTime) ? board.now() : Math.max(board.now(), f.arrivalTime);
        FreighterComponent.State state = f.state == FreighterComponent.State.IN_BUBBLE ? f.resumeState : f.state;
        if (state != FreighterComponent.State.TO_PICKUP) return eta;
        if (f.transitBodyUuid != null) eta += DAY * FAR_DAYS;
        return eta + DAY * (far ? FAR_DAYS : NEAR_DAYS);
    }

    boolean isAce(String uuid) {
        Entity e = ctx.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        return h != null && h.ace && !h.partDropped;
    }

    boolean inBubble(String uuid) {
        Entity e = ctx.engine.getEntity(uuid);
        return e != null && e.getComponent(PhysicsBodyComponent.class) != null;
    }

    /** Ships at war with the giver, priced by how they measure up to the player's and by {@link Difficulty}. */
    List<Contract> bounties(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        Entity player = ctx.player();
        double own = player == null ? 0 : Difficulty.shipValue(player);
        for (Entity e : ctx.engine.getEcsEngine().getEntitiesFor(SHIPS)) {
            int faction = e.getComponent(ShipTaskBoardComponent.class).faction;
            String uuid = e.getComponent(UuidComponent.class).id;
            if (e == player || !standing.factions().known(faction) || !standing.factions().atWar(giver, faction)
                    || board.targeted(uuid) || FirstPlay.beaten(ctx.engine, e) || FirstPlay.aceBeaten(e)) continue;
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
            c.targetName = (label.isEmpty() ? "a ship" : label) + " of the " + standing.factions().name(faction);
            double ratio = own <= 0 ? 1 : Difficulty.shipValue(e) / own;
            c.tier = ratio <= 1.2 ? 0 : ratio <= 2 ? 1 : 2;
            c.reward = Math.round(BOUNTY_PAY * (1 + c.tier) * Difficulty.of(standing, ctx.player()));
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
                c.standing += PirateAces.BOUNTY_STANDING_PER_TIER * (1 + c.tier);
            }
            // Beating a ship far weaker than the player's earns less standing: a Commander in a frigate
            // climbs on what can hurt a frigate, not on light raiders (round 5's late career)
            if (ratio < WEAK_RATIO) c.standing = Math.round(c.standing * WEAK_STANDING);
            c.deadline = board.now() + 3 * DAY;
            out.add(c);
        }
        return out;
    }

    /** "robbed 2 freighters, last of Water": what a pirate's bounty says it has done. */
    String record(HostileComponent h) {
        String what = h.plunders == 1 ? "robbed a freighter" : "robbed " + h.plunders + " freighters";
        if (h.lootCommodity == null) return what;
        CommodityType type = macroSim.economy().catalog().commodity(h.lootCommodity);
        return what + ", last of " + (type == null || type.displayName == null ? h.lootCommodity : type.displayName);
    }

    /** Freighters hauling out of this station to the same buyer, two or more, to be seen in together. */
    List<Contract> convoys(EconomyNode node, int giver) {
        Map<String, List<TradeOrder>> byBuyer = new LinkedHashMap<>();
        for (TradeOrder o : macroSim.economy().orders()) {
            if (o.claimedBy == null || !o.fromUuid.equals(node.stationUuid) || board.targeted(o.claimedBy)) continue;
            if (macroSim.freighters().get(o.claimedBy) == null || macroSim.station(o.toUuid) == null) continue;
            List<TradeOrder> run = byBuyer.computeIfAbsent(o.toUuid, k -> new ArrayList<>());
            boolean again = false;
            for (TradeOrder r : run) again |= r.claimedBy.equals(o.claimedBy);
            if (!again) run.add(o);
        }
        List<Contract> out = new ArrayList<>();
        for (Map.Entry<String, List<TradeOrder>> e : byBuyer.entrySet()) {
            List<TradeOrder> run = e.getValue();
            if (run.size() < 2) continue;
            boolean far = board.far(node.stationUuid, e.getKey());
            Contract c = new Contract();
            c.type = Contract.Type.CONVOY;
            int n = run.size();
            c.targets = new String[n];
            c.targetNames = new String[n];
            c.marks = new int[n];
            c.trips = new int[n];
            double eta = board.now();
            for (int i = 0; i < n; i++) {
                FreighterComponent f = macroSim.freighters().get(run.get(i).claimedBy);
                c.targets[i] = run.get(i).claimedBy;
                c.targetNames[i] = f.displayName == null ? "a freighter" : f.displayName;
                eta = Math.max(eta, dueAtBuyer(f, far));
            }
            c.targetUuid = c.targets[0];
            c.targetName = c.targetNames[0];
            c.destinationUuid = e.getKey();
            c.destinationName = ContractBoard.name(macroSim.station(e.getKey()));
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
    List<Contract> raids(int giver, boolean step) {
        List<Contract> out = new ArrayList<>();
        PirateBase base = pirateBase.get();
        String uuid = base.uuid();
        if (uuid == null || !base.standing() || !base.found() || board.targeted(uuid)) return out;
        if (!step && giver != standing.sponsor()) return out;
        Contract c = new Contract();
        c.type = Contract.Type.RAID;
        c.targetUuid = uuid;
        c.targetName = base.name();
        c.tier = step ? 0 : RAID_RANK;
        c.reward = Math.round(RAID_PAY * Difficulty.of(standing, ctx.player()));
        c.standing = 15;
        c.deadline = board.now() + DAY * RAID_DAYS;
        c.reason = "Take it or leave no pirate alive aboard";
        out.add(c);
        return out;
    }

    /** Pirates at war with the giver, to be found where they lurk; {@code skip} (a pirate already found) last. */
    List<Contract> recons(int giver, String skip) {
        List<Contract> out = new ArrayList<>();
        for (Map.Entry<String, HostileComponent> e : macroSim.hostiles().all().entrySet()) {
            HostileComponent h = e.getValue();
            Entity ship = ctx.engine.getEntity(e.getKey());
            int faction = ship == null ? -1 : standing.factionOf(ship);
            if (!h.isPirate() || h.moored() || ship == null || e.getKey().equals(skip) || board.targeted(e.getKey())) continue;
            // Nothing to find in a beaten ship, or in an ace already beaten once (round 6's recons named both)
            if (FirstPlay.beaten(ctx.engine, ship) || FirstPlay.aceBeaten(ship)) continue;
            if (faction >= 0 && !standing.factions().atWar(giver, faction)) continue;
            Contract c = new Contract();
            c.type = Contract.Type.RECON;
            c.targetUuid = e.getKey();
            c.targetName = h.displayName == null ? "a pirate" : h.displayName;
            EconomyNode lurk = h.stationUuid == null ? null : macroSim.station(h.stationUuid);
            boolean far = lurk != null && ctx.player() != null && board.far(lurk.stationUuid, universe.uuidOf(ctx.player()));
            c.tier = far ? 1 : 0;
            c.reward = RECON_PAY * (far ? FAR_PAY : 1);
            c.standing = 3;
            if (h.ace) {
                // A named ace moves about and is worth knowing where it rests
                c.targetName = "the ace " + c.targetName;
                c.reward *= 2;
                c.reason = "A named ace: find where it rests";
            }
            c.deadline = board.now() + DAY * (far ? FAR_DAYS : NEAR_DAYS + 1);
            out.add(c);
        }
        return out;
    }

    /** Lanes from this station out to each other station and back; the giver's own stations first. */
    List<Contract> patrols(EconomyNode node, int giver) {
        List<Contract> out = new ArrayList<>();
        for (EconomyNode other : macroSim.stations()) {
            if (other == node) continue;
            int owner = standing.factionOf(ctx.engine.getEntity(other.stationUuid));
            if (standing.factions().atWar(giver, owner)) continue;
            boolean far = board.far(node.stationUuid, other.stationUuid);
            Contract c = new Contract();
            c.type = Contract.Type.PATROL;
            c.targetUuid = other.stationUuid;
            c.targetName = ContractBoard.name(other);
            c.destinationUuid = node.stationUuid;
            c.destinationName = ContractBoard.name(node);
            c.tier = far ? 1 : 0;
            c.reward = PATROL_PAY * (far ? FAR_PAY : 1) * (owner == giver ? 1 : 1.5);
            c.standing = 3 + 2 * c.tier;
            c.deadline = board.now() + DAY * (far ? FAR_DAYS + 2 : NEAR_DAYS);
            out.add(c);
        }
        return out;
    }
}
