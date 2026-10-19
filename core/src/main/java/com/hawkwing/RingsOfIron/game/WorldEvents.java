package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.moddingdata.defs.FactionDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Things that happen in the world on their own, each with jobs on the boards (GDD section 4). At
 * most one a day, rolled from {@link #seed} and the day, and drawn from what the world can give
 * that day, so two careers see different ones:
 * <ul>
 *   <li><b>Shortage</b>: a station loses most of a commodity it buys; its price climbs, and the
 *       stations that have it post urgent deliveries there at a premium.</li>
 *   <li><b>Raid</b>: raiders come to work a station's lanes ({@link HostileTraffic#raid}), and the
 *       station posts their defence.</li>
 *   <li><b>Freighter lost</b>: a hauler out on the rails is lost; its crew drift in a pod and its
 *       cargo in the wreck ({@link Derelicts}), and its stations post their rescue and recovery. A
 *       new hauler takes its place after {@link #REPLACE_DAYS}. No more than one in
 *       {@link #LOST_GAP_DAYS} days.</li>
 *   <li><b>Tension</b>: a rival faction flexes; the sponsor's stations post shows of force out to
 *       its stations, and bounties on its ships if the two are at war.</li>
 * </ul>
 * Besides the daily roll, a freighter a pirate robs may leave some of its crew in a pod
 * ({@link #POD_CHANCE}), with a rescue posted for them. Events go in {@link #log()} and saves, and
 * are told to the player through {@link GameWorld#notices}.
 *
 * <p>Round 5: events strain the lawful factions ({@link FactionTension}) as the player's deeds do.
 * A rival flexing raises its tension with the sponsor by {@link #FLEX_STRAIN}; a raid on a station,
 * a freighter lost on its lanes or robbed on its way there sets its owner against every other
 * lawful faction ({@link #RAID_STRAIN}, {@link #LOST_STRAIN}, {@link #ROBBED_STRAIN}), each side
 * blaming the other's patrols and the lanes they let the pirates work.
 */
public class WorldEvents implements SaveParticipant, HostileTrafficController.Listener {
    public static final double DAY = 86_400;
    /** Chance that a day brings an event. */
    public static final double EVENT_CHANCE = 0.6;
    /** Chance that a robbed freighter's crew put some of their own in a pod. */
    public static final double POD_CHANCE = 1.0 / 3.0;
    /** Days before a lost freighter is replaced. */
    public static final double REPLACE_DAYS = 3;
    /**
     * Days after a freighter is lost before another can be (round 4): two or three a fortnight at
     * most, never on neighbouring days. Pirates robbing haulers are not held to it.
     */
    public static final long LOST_GAP_DAYS = 5;
    /** Share of its stock a station short of something keeps. */
    public static final double SHORTAGE_LEFT = 0.15;
    /** What an urgent delivery pays per unit over the short station's price. */
    public static final double URGENT_PREMIUM = 1.5;
    /** How long each event's jobs stay up, days. */
    public static final double SHORTAGE_DAYS = 3, RAID_DAYS = 2, TENSION_DAYS = 3;
    /** Tension each kind of event adds (round 5): a rival flexing, with the sponsor; the rest, the station's owner with every other side. */
    public static final float FLEX_STRAIN = 6f, RAID_STRAIN = 2f, LOST_STRAIN = 2f, ROBBED_STRAIN = 0.5f;
    /** Events the log keeps. */
    public static final int LOG = 50;
    /** The seed until a new game sets one (round 3's seeded world). */
    public static final long DEFAULT_SEED = 0x5EED_0006L;

    public enum Kind { SHORTAGE, RAID, FREIGHTER_LOST, TENSION, POD }

    /** One event, for the log and the save. */
    public static class Event {
        public Kind kind;
        public long day;
        public double at;
        public String station;
        public String commodity;
        public String faction;
        public String text = "";
    }

    /** A lost freighter's replacement, due at {@link #at} at {@link #station}. */
    public static class Replacement {
        public String name;
        public double capacity;
        public String station;
        public double at;
    }

    private final GameWorld world;
    /** Sets which events a career sees; saved. */
    public long seed = DEFAULT_SEED;
    /** The last day rolled for; -1 before the first look. */
    private long rolled = -1;
    private final List<Event> log = new ArrayList<>();
    private final List<Replacement> replacements = new ArrayList<>();

    WorldEvents(GameWorld world) {
        this.world = world;
        world.engine.getSerializationService().addParticipant(this);
        world.macroSim.hostiles().addListener(this);
    }

    /** Events so far, oldest first, at most {@link #LOG}. */
    public List<Event> log() {
        return Collections.unmodifiableList(log);
    }

    /** Clears everything, for a new game. The seed is kept. */
    public void reset() {
        rolled = -1;
        log.clear();
        replacements.clear();
    }

    /** Rolls the day's event once the day turns, and brings in replacements that are due. Call after the engine's update. */
    void update() {
        if (world.getPlayer() == null || world.getCrash() != null) return;
        double now = world.engine.getSimSeconds();
        for (Replacement r : new ArrayList<>(replacements)) {
            if (now < r.at) continue;
            replacements.remove(r);
            if (world.macroSim.station(r.station) == null) continue;
            world.macroSim.addFreighter(r.name, r.capacity, 20_000, r.station);
            post(r.name + " is back on the lanes, a new hull out of " + stationName(r.station));
        }
        long day = (long) Math.floor(now / DAY);
        if (rolled < 0) {
            // The first day of a career is quiet
            rolled = day;
            return;
        }
        if (day <= rolled) return;
        // Days skipped in warp or a rescue bring one roll, not a burst
        rolled = day;
        roll(day);
    }

    /** Rolls for day {@code day}: maybe an event, of a kind the world can give now. */
    void roll(long day) {
        // Mixed, not seed + day: java.util.Random's first draw barely differs for neighbouring seeds,
        // so some careers rolled the same "no event" every day
        Random rnd = new Random(NewGame.mix(seed, day));
        if (rnd.nextDouble() >= EVENT_CHANCE) return;
        List<Kind> kinds = new ArrayList<>(List.of(Kind.SHORTAGE, Kind.RAID, Kind.FREIGHTER_LOST, Kind.TENSION));
        Collections.shuffle(kinds, rnd);
        for (Kind k : kinds) if (happen(k, day, rnd)) return;
    }

    /** Makes an event of kind {@code k} happen today; false if the world has nothing for it. */
    boolean happen(Kind k, long day, Random rnd) {
        switch (k) {
            case SHORTAGE:
                return shortage(day, rnd);
            case RAID:
                return raid(day, rnd);
            case FREIGHTER_LOST:
                return freighterLost(day, rnd);
            case TENSION:
                return tension(day, rnd);
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ events

    /** A station runs short of something it buys and another has: urgent deliveries there, at a premium. */
    private boolean shortage(long day, Random rnd) {
        List<EconomyNode[]> pairs = new ArrayList<>();
        List<String> goods = new ArrayList<>();
        for (EconomyNode short_ : world.macroSim.stations()) {
            if (!friendly(short_.stationUuid)) continue;
            for (CommodityType c : world.macroSim.economy().catalog().commodities()) {
                if (c.unitVolume <= 0 || !world.macroSim.market().stationBuys(short_, c.id) || short_.stock(c.id) <= 1) continue;
                for (EconomyNode from : world.macroSim.stations()) {
                    if (from == short_ || !friendly(from.stationUuid) || from.available(c.id) < 10) continue;
                    pairs.add(new EconomyNode[]{short_, from});
                    goods.add(c.id);
                }
            }
        }
        if (pairs.isEmpty()) return false;
        int pick = rnd.nextInt(pairs.size());
        EconomyNode node = pairs.get(pick)[0];
        String commodity = goods.get(pick);
        node.remove(commodity, node.stock(commodity) * (1 - SHORTAGE_LEFT));
        String what = commodityName(commodity);
        Event e = record(Kind.SHORTAGE, day, node.stationUuid, commodity, null,
                stationName(node.stationUuid) + " is short of " + what);
        int posted = 0;
        for (EconomyNode from : world.macroSim.stations()) {
            if (from == node || !friendly(from.stationUuid) || from.available(commodity) < 10) continue;
            Contract c = urgentDelivery(from, node, commodity, e.text);
            if (c != null && world.contracts.postEvent(c)) posted++;
        }
        post(e.text + (posted > 0 ? ": urgent deliveries are posted" : ""));
        return true;
    }

    private Contract urgentDelivery(EconomyNode from, EconomyNode to, String commodity, String why) {
        CommodityType type = world.macroSim.economy().catalog().commodity(commodity);
        TraderComponent trader = world.getPlayer().getComponent(TraderComponent.class);
        if (type == null || trader == null || trader.cargoCapacity <= 0) return null;
        double qty = Math.floor(Math.min(from.available(commodity), trader.cargoCapacity / type.unitVolume));
        if (qty < 1) return null;
        Contract c = new Contract();
        c.type = Contract.Type.DELIVERY;
        c.orderId = -1;
        c.stationUuid = from.stationUuid;
        c.giver = factionId(to.stationUuid);
        c.commodity = commodity;
        c.commodityName = commodityName(commodity);
        c.quantity = qty;
        c.unitPrice = to.price(commodity) * URGENT_PREMIUM;
        c.targetUuid = to.stationUuid;
        c.targetName = stationName(to.stationUuid);
        c.reward = Math.round(Math.max(ContractBoard.DELIVERY_MIN_FEE, ContractBoard.DELIVERY_FEE_SHARE * qty * c.unitPrice));
        c.standing = 5;
        c.deadline = world.engine.getSimSeconds() + DAY * SHORTAGE_DAYS;
        c.reason = why;
        return c;
    }

    /** Raiders come to work a station's lanes, and it posts their defence. */
    private boolean raid(long day, Random rnd) {
        List<EconomyNode> targets = new ArrayList<>();
        for (EconomyNode n : world.macroSim.stations()) if (friendly(n.stationUuid)) targets.add(n);
        if (targets.isEmpty()) return false;
        EconomyNode node = targets.get(rnd.nextInt(targets.size()));
        int count = 1 + rnd.nextInt(2);
        List<String> raiders = world.hostiles.raid(node.stationUuid, count);
        if (raiders.isEmpty()) return false;
        Event e = record(Kind.RAID, day, node.stationUuid, null, null,
                raiders.size() + (raiders.size() == 1 ? " raider is" : " raiders are") + " working "
                        + stationName(node.stationUuid) + "'s lanes");
        Contract c = new Contract();
        c.type = Contract.Type.DEFEND;
        c.stationUuid = node.stationUuid;
        int n = raiders.size();
        c.targets = raiders.toArray(new String[0]);
        c.targetNames = new String[n];
        c.marks = new int[n];
        for (int i = 0; i < n; i++) {
            HostileComponent h = world.macroSim.hostiles().get(raiders.get(i));
            c.targetNames[i] = h == null || h.displayName == null ? "a raider" : h.displayName;
        }
        c.targetUuid = c.targets[0];
        c.targetName = c.targetNames[0];
        c.destinationUuid = node.stationUuid;
        c.destinationName = stationName(node.stationUuid);
        c.reward = Math.round(ContractBoard.DEFEND_PAY * n * Difficulty.of(world));
        c.standing = 4 + 3 * n;
        c.deadline = world.engine.getSimSeconds() + DAY * RAID_DAYS;
        c.reason = e.text;
        world.contracts.postEvent(c);
        post(e.text + ": " + c.destinationName + " asks for help");
        strain(node.stationUuid, RAID_STRAIN, e.text);
        return true;
    }

    /** A hauler out on the rails is lost: a pod and a wreck drift where it was, and its stations post their rescue and recovery. */
    private boolean freighterLost(long day, Random rnd) {
        for (Event e : log) if (e.kind == Kind.FREIGHTER_LOST && day - e.day < LOST_GAP_DAYS) return false;
        List<String> candidates = new ArrayList<>();
        for (var entry : world.macroSim.freighters().all().entrySet()) {
            FreighterComponent f = entry.getValue();
            Entity e = world.engine.getEntity(entry.getKey());
            boolean flying = f.state == FreighterComponent.State.TO_DELIVERY || f.state == FreighterComponent.State.TO_PICKUP;
            if (!flying || e == null || world.universe.orbit(e) == null || contracted(entry.getKey())) continue;
            candidates.add(entry.getKey());
        }
        if (candidates.isEmpty()) return false;
        String uuid = candidates.get(rnd.nextInt(candidates.size()));
        Entity ship = world.engine.getEntity(uuid);
        FreighterComponent f = world.macroSim.freighters().get(uuid);
        String name = f.displayName == null ? "A hauler" : f.displayName;
        String home = f.destinationUuid != null && world.macroSim.station(f.destinationUuid) != null ? f.destinationUuid
                : f.dockedAtUuid;
        String post = friendly(f.destinationUuid) ? f.destinationUuid : friendly(f.dockedAtUuid) ? f.dockedAtUuid : null;
        if (post == null) post = nearestFriendly();
        if (post == null) return false;
        int survivors = 2 + rnd.nextInt(4);
        Derelicts.Derelict pod = world.derelicts.castOff(ship, Derelicts.Kind.POD, name + "'s pod", survivors, null, 0, rnd);
        Derelicts.Derelict wreck = null;
        if (f.cargoCommodity != null && f.cargoQuantity >= 1) {
            wreck = world.derelicts.castOff(ship, Derelicts.Kind.WRECK, "Wreck of " + name, 0, f.cargoCommodity,
                    holdable(f.cargoCommodity, f.cargoQuantity), rnd);
        }
        if (pod == null) return false;
        Replacement r = new Replacement();
        r.name = name;
        r.capacity = f.capacity;
        r.station = home == null ? post : home;
        r.at = world.engine.getSimSeconds() + DAY * REPLACE_DAYS;
        replacements.add(r);
        // With its crew and systems, which would otherwise go on working aboard a ship that is gone
        world.shipyard.retire(ship);
        Event e = record(Kind.FREIGHTER_LOST, day, post, wreck == null ? null : wreck.commodity, null,
                "Contact lost with " + name + "; pirates are suspected");
        Contract rescue = world.contracts.offerFor(pod, post, e.text);
        if (rescue != null) world.contracts.postEvent(rescue);
        Contract recovery = wreck == null ? null : world.contracts.offerFor(wreck, post, e.text);
        if (recovery != null) world.contracts.postEvent(recovery);
        post(e.text + ". " + stationName(post) + " posts a rescue" + (recovery == null ? "" : " and a recovery"));
        strain(post, LOST_STRAIN, e.text);
        return true;
    }

    /** A rival faction flexes: the sponsor posts shows of force out to its stations, and bounties if they are at war. */
    private boolean tension(long day, Random rnd) {
        FactionStanding s = world.standing;
        int sponsor = s.sponsor();
        List<Integer> rivals = new ArrayList<>();
        for (int i = 0; i < s.factions().all().size(); i++) {
            int f = Factions.FIRST + i;
            FactionDef def = s.factions().def(f);
            if (f == sponsor || def == null || def.hostileToAll || stationsOf(f).isEmpty()) continue;
            rivals.add(f);
        }
        if (rivals.isEmpty() || stationsOf(sponsor).isEmpty()) return false;
        int rival = rivals.get(rnd.nextInt(rivals.size()));
        String rivalName = s.factions().name(rival);
        Event e = record(Kind.TENSION, day, null, null, s.factions().id(rival),
                "Tension with the " + rivalName + " is running high");
        List<String> theirs = stationsOf(rival);
        int posted = 0;
        for (String own : stationsOf(sponsor)) {
            String far = theirs.get(rnd.nextInt(theirs.size()));
            Contract c = new Contract();
            c.type = Contract.Type.PATROL;
            c.stationUuid = own;
            c.targetUuid = far;
            c.targetName = stationName(far);
            c.destinationUuid = own;
            c.destinationName = stationName(own);
            c.tier = 1;
            c.reward = ContractBoard.PATROL_PAY * ContractBoard.FAR_PAY * 2;
            c.standing = 8;
            c.deadline = world.engine.getSimSeconds() + DAY * (ContractBoard.FAR_DAYS + TENSION_DAYS);
            c.reason = e.text + ": a show of force";
            if (world.contracts.postEvent(c)) posted++;
        }
        if (s.factions().atWar(sponsor, rival)) {
            for (var entry : world.macroSim.hostiles().all().entrySet()) {
                HostileComponent h = entry.getValue();
                if (h.isPirate() || !s.factions().id(rival).equals(h.faction)) continue;
                Contract c = new Contract();
                c.type = Contract.Type.BOUNTY;
                c.stationUuid = stationsOf(sponsor).get(0);
                c.targetUuid = entry.getKey();
                c.targetName = (h.displayName == null ? "a ship" : h.displayName) + " of the " + rivalName;
                c.tier = 1;
                c.reward = Math.round(ContractBoard.BOUNTY_PAY * 2 * Difficulty.of(world));
                c.standing = 9;
                c.deadline = world.engine.getSimSeconds() + DAY * TENSION_DAYS;
                c.reason = e.text;
                if (world.contracts.postEvent(c)) posted++;
            }
        }
        post(e.text + (posted > 0 ? ": the " + s.factions().name(sponsor) + " posts patrols" : ""));
        world.tension.raise(sponsor, rival, FLEX_STRAIN, e.text);
        return true;
    }

    // ------------------------------------------------------------------ pirates

    @Override
    public void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
        FreighterComponent f = world.macroSim.freighters().get(freighter);
        // A hauler robbed on its way somewhere strains that station's owner with the other sides
        if (f != null && friendly(f.destinationUuid)) strain(f.destinationUuid, ROBBED_STRAIN, null);
        Random rnd = new Random(seed ^ freighter.hashCode() * 0x9E3779B97F4A7C15L ^ (long) t);
        if (rnd.nextDouble() >= POD_CHANCE) return;
        Entity ship = world.engine.getEntity(freighter);
        HostileComponent h = world.macroSim.hostiles().get(hostile);
        if (ship == null || f == null) return;
        String post = friendly(f.destinationUuid) ? f.destinationUuid : nearestFriendly();
        if (post == null) return;
        String name = f.displayName == null ? "a hauler" : f.displayName;
        Derelicts.Derelict pod = world.derelicts.castOff(ship, Derelicts.Kind.POD, name + "'s pod", 1 + rnd.nextInt(3),
                null, 0, rnd);
        if (pod == null) return;
        Event e = record(Kind.POD, (long) Math.floor(t / DAY), post, commodity, null,
                (h == null || h.displayName == null ? "Pirates" : h.displayName) + " robbed " + name
                        + "; some of its crew took to a pod");
        Contract c = world.contracts.offerFor(pod, post, e.text);
        if (c != null) world.contracts.postEvent(c);
        post(e.text);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Raises tension between the owner of {@code station} and every other lawful faction with
     * stations by {@code amount}; {@code why} for the notice if a pair runs high.
     */
    private void strain(String station, float amount, String why) {
        FactionStanding s = world.standing;
        int owner = s.factionOf(world.engine.getEntity(station));
        FactionDef own = s.factions().def(owner);
        if (own == null || own.hostileToAll) return;
        for (int i = 0; i < s.factions().all().size(); i++) {
            int other = Factions.FIRST + i;
            FactionDef def = s.factions().def(other);
            if (other == owner || def == null || def.hostileToAll || stationsOf(other).isEmpty()) continue;
            world.tension.raise(owner, other, amount, why);
        }
    }

    /** Tells the player: a world event is worth noticing, a pod after a robbery is news. */
    private void post(String text) {
        world.notices.post(text, text.contains("robbed") || text.contains("back on the lanes") ? NoticeLog.Kind.INFO
                : NoticeLog.Kind.IMPORTANT);
    }

    private Event record(Kind kind, long day, String station, String commodity, String faction, String text) {
        Event e = new Event();
        e.kind = kind;
        e.day = day;
        e.at = world.engine.getSimSeconds();
        e.station = station;
        e.commodity = commodity;
        e.faction = faction;
        e.text = text;
        log.add(e);
        while (log.size() > LOG) log.remove(0);
        return e;
    }

    /** True if {@code uuid} is a station of a faction that posts jobs (not one hostile to all). */
    private boolean friendly(String uuid) {
        if (uuid == null || world.macroSim.station(uuid) == null) return false;
        FactionDef def = world.standing.factions().def(world.standing.factionOf(world.engine.getEntity(uuid)));
        return def != null && !def.hostileToAll;
    }

    private String nearestFriendly() {
        for (EconomyNode n : world.macroSim.stations()) if (friendly(n.stationUuid)) return n.stationUuid;
        return null;
    }

    private List<String> stationsOf(int faction) {
        List<String> out = new ArrayList<>();
        for (EconomyNode n : world.macroSim.stations()) {
            if (world.standing.factionOf(world.engine.getEntity(n.stationUuid)) == faction) out.add(n.stationUuid);
        }
        return out;
    }

    /** True if a contract the player holds is about the freighter {@code uuid}. */
    private boolean contracted(String uuid) {
        for (Contract c : world.contracts.active()) {
            if (uuid.equals(c.targetUuid)) return true;
            for (String t : c.targets) if (uuid.equals(t)) return true;
        }
        return false;
    }

    /** Of {@code quantity} units, what the player's hold could carry home; the rest goes down with the hull. */
    private double holdable(String commodity, double quantity) {
        CommodityType type = world.macroSim.economy().catalog().commodity(commodity);
        TraderComponent trader = world.getPlayer() == null ? null : world.getPlayer().getComponent(TraderComponent.class);
        if (type == null || type.unitVolume <= 0 || trader == null || trader.cargoCapacity <= 0) return Math.floor(quantity);
        return Math.max(1, Math.floor(Math.min(quantity, trader.cargoCapacity / type.unitVolume)));
    }

    private String factionId(String station) {
        return world.standing.factions().id(world.standing.factionOf(world.engine.getEntity(station)));
    }

    private String stationName(String uuid) {
        EconomyNode n = uuid == null ? null : world.macroSim.station(uuid);
        return n == null || n.displayName == null ? "the station" : n.displayName;
    }

    private String commodityName(String id) {
        CommodityType c = world.macroSim.economy().catalog().commodity(id);
        return c == null || c.displayName == null ? id : c.displayName;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public String saveKey() {
        return "worldEvents";
    }

    @Override
    public void write(Json json) {
        json.writeValue("seed", seed);
        json.writeValue("rolled", rolled);
        json.writeValue("log", log.toArray(new Event[0]), Event[].class);
        json.writeValue("replacements", replacements.toArray(new Replacement[0]), Replacement[].class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        seed = data.getLong("seed", seed);
        rolled = data.getLong("rolled", -1);
        Event[] events = data.has("log") ? json.readValue(Event[].class, data.get("log")) : null;
        if (events != null) for (Event e : events) if (e != null && e.kind != null) log.add(e);
        Replacement[] due = data.has("replacements") ? json.readValue(Replacement[].class, data.get("replacements")) : null;
        if (due != null) for (Replacement r : due) if (r != null && r.station != null) replacements.add(r);
    }

    /** Freighters waiting to replace lost ones, for tests. */
    List<Replacement> replacements() {
        return Collections.unmodifiableList(replacements);
    }
}
