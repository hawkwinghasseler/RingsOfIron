package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.stationsdocking.DockingEvent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The player's standing with each faction ({@link Factions}) and their career in the sponsor's
 * ranks ({@link RankLadder}), kept in a {@link ServiceRecord} saved with the game.
 *
 * <p>Deeds move standing through {@link #reward} and {@link #penalize}; contracts, combat and
 * boarding call them. A hostile act ({@link #hostileAct}), such as forcing a door or destroying a
 * ship ({@link #onShipDestroyed}), costs standing with the victim's faction and gains a share of it
 * with the factions sworn against it ({@link Factions#swornEnemiesOf}). Forcing a door costs more cut than pried or hacked; docking
 * without clearance costs nothing by itself. Below {@link #REFUSE_BELOW} a faction's stations refuse
 * the player docking clearance; at {@link #HOSTILE_AT} or below its crew fight the player's.
 *
 * <p>Standing with the sponsor climbs the rank ladder: crossing a rank's threshold promotes the
 * player, with a notice. Promotions are never taken back.
 */
public class FactionStanding implements SaveParticipant {
    public static final float MIN = -100f, MAX = 100f;
    /** Standing lost for forcing a door each way. */
    public static final float PRY_COST = 10f, HACK_COST = 10f, CUT_COST = 25f;
    /** Standing lost with a faction for destroying one of its ships. */
    public static final float KILL_COST = 30f;
    /** Share of a hostile act's cost its victim's sworn enemies give the player. */
    public static final float ENEMY_SHARE = 0.5f;
    /**
     * Share of a kill's cost its victim's sworn enemies give the player: 5 standing a ship, so a
     * bounty (6) and its kill come to 11, short of Lieutenant's 15 (round 2 playtest: one fight
     * was a promotion). Placeholder number.
     */
    public static final float KILL_SHARE = 1f / 6f;
    /** Below this, the faction's stations refuse the player clearance. */
    public static final float REFUSE_BELOW = -15f;
    /** At or below this, the faction's crew fight the player's. */
    public static final float HOSTILE_AT = -50f;
    /** The player's own crew. */
    public static final int PLAYER = 0;

    /** A hostile act the player did, for the screen. */
    public static final class Act {
        public final Entity victim;
        public final int faction;
        public final ForceMethod method;
        public final float standing;

        Act(Entity victim, int faction, ForceMethod method, float standing) {
            this.victim = victim;
            this.faction = faction;
            this.method = method;
            this.standing = standing;
        }
    }

    private final Supplier<Entity> player;
    private Factions factions = Factions.none();
    private RankLadder ranks = RankLadder.none();
    private ServiceRecord record = fresh();
    /** What the player last did to each faction to lose standing, for a refusal; not saved. */
    private final Map<Integer, String> grievances = new HashMap<>();
    /** Door forcings not yet shown; the screen drains them. */
    public final Array<Act> acts = new Array<>();
    /** Career news not yet shown (promotions, budget paid); the screen drains them. */
    public final Array<String> notices = new Array<>();

    public FactionStanding(Supplier<Entity> player) {
        this.player = player;
    }

    /** Takes the factions and ranks from loaded data. A record of a sponsor the data lacks moves to the data's sponsor. */
    public void setData(Factions factions, RankLadder ranks) {
        this.factions = factions;
        this.ranks = ranks;
        if (factions.index(record.sponsor) < 0) record.sponsor = factions.sponsor().id;
    }

    public Factions factions() {
        return factions;
    }

    public RankLadder ranks() {
        return ranks;
    }

    /** The player's career as it stands; live, so changes to it stick. */
    public ServiceRecord record() {
        return record;
    }

    /** Starts a new career: rank 0 with the sponsor, standing 0 with everyone. */
    public void reset() {
        record = fresh();
        grievances.clear();
    }

    private ServiceRecord fresh() {
        ServiceRecord r = new ServiceRecord();
        r.sponsor = factions == null ? null : factions.sponsor().id;
        return r;
    }

    /** The sponsor's crew faction number. */
    public int sponsor() {
        int i = factions.index(record.sponsor);
        return i < 0 ? factions.sponsorIndex() : i;
    }

    /** The player's current rank. */
    public RankDef rank() {
        return ranks.get(record.rank);
    }

    /** What the sponsor calls rank {@code i}: its own name for it, else the ladder's. */
    public String rankName(int i) {
        FactionDef s = factions.def(sponsor());
        int at = Math.max(0, Math.min(ranks.size() - 1, i));
        if (s != null && at < s.rankNames.size() && s.rankNames.get(at) != null) return s.rankNames.get(at);
        return ranks.get(at).name;
    }

    /** What the sponsor calls the player's current rank. */
    public String rankName() {
        return rankName(record.rank);
    }

    /**
     * Starts the career serving faction {@code id} instead of the data's sponsor; nothing if the
     * data has no such faction or it is not {@code sponsorable}. Call on a fresh career, before any
     * standing is earned.
     */
    public void serve(String id) {
        FactionDef f = factions.def(factions.index(id));
        if (f != null && (f.sponsor || f.sponsorable)) record.sponsor = id;
    }

    /** The faction a craft belongs to: a station's owner, or its crew's; -1 for a craft of no one's. */
    public int factionOf(Entity craft) {
        if (craft == null) return -1;
        StationComponent station = craft.getComponent(StationComponent.class);
        if (station != null) return factions.ofStation(station.name);
        ShipTaskBoardComponent board = craft.getComponent(ShipTaskBoardComponent.class);
        return board == null ? -1 : board.faction;
    }

    /** The player's standing with {@code faction} (0 if they have never dealt with it, or it is none of the data's). */
    public float standing(int faction) {
        String id = factions.id(faction);
        return id == null ? 0f : record.get(id);
    }

    /** The player's standing with the faction {@code id}. */
    public float standing(String id) {
        return record.get(id);
    }

    /** Changes the player's standing with {@code faction}; standing with the sponsor may promote them. */
    public void change(int faction, float by) {
        String id = factions.id(faction);
        if (id == null || by == 0f) return;
        record.set(id, record.get(id) + by);
        if (faction == sponsor()) checkPromotion();
    }

    /** Raises the player's standing with {@code faction} by {@code amount} for {@code why}. */
    public void reward(int faction, float amount, String why) {
        change(faction, Math.abs(amount));
    }

    /** Raises standing with the faction {@code id}; nothing if there is no such faction. */
    public void reward(String id, float amount, String why) {
        reward(factions.index(id), amount, why);
    }

    /** Lowers the player's standing with {@code faction} by {@code amount} for {@code why} (shown when its stations refuse them). */
    public void penalize(int faction, float amount, String why) {
        if (!factions.known(faction)) return;
        change(faction, -Math.abs(amount));
        if (why != null) grievances.put(faction, why);
    }

    /** Lowers standing with the faction {@code id}; nothing if there is no such faction. */
    public void penalize(String id, float amount, String why) {
        penalize(factions.index(id), amount, why);
    }

    /**
     * A hostile act against {@code faction}: costs {@code cost} standing with it, and gains
     * {@link #ENEMY_SHARE} of that with every faction sworn against it.
     */
    public void hostileAct(int faction, float cost, String why) {
        hostileAct(faction, cost, ENEMY_SHARE, why);
    }

    private void hostileAct(int faction, float cost, float share, String why) {
        if (!factions.known(faction)) return;
        penalize(faction, cost, why);
        for (int enemy : factions.swornEnemiesOf(faction)) reward(enemy, cost * share, why);
    }

    /**
     * The player destroyed (or disabled for good) {@code ship}: a hostile act against its faction,
     * {@link #KILL_SHARE} of it to the factions sworn against it.
     */
    public void onShipDestroyed(Entity ship) {
        hostileAct(factionOf(ship), KILL_COST, KILL_SHARE, "destroyed their ships");
    }

    /** Takes note of a docking event: a door the player's crew forced costs standing with its owners. */
    public void onDockingEvent(DockingEvent e) {
        if (e.kind != DockingEvent.Kind.DOOR_FORCED || e.vessel == null || e.vessel != player.get()) return;
        int faction = factionOf(e.other);
        hostileAct(faction, cost(e.method), "forced their doors");
        acts.add(new Act(e.other, faction, e.method, standing(faction)));
    }

    /** Standing a hostile act costs. */
    public static float cost(ForceMethod how) {
        switch (how) {
            case CUT:
                return CUT_COST;
            case HACK:
                return HACK_COST;
            default:
                return PRY_COST;
        }
    }

    /**
     * Whether crew of factions {@code a} and {@code b} fight. The player's crew fight a faction of
     * the data that is hostile to all, or once their standing with it is down to {@link #HOSTILE_AT};
     * two of the data's factions fight when at war; a data faction leaves crew of no one's alone
     * unless it is hostile to all; and any two crews of no one's always fight.
     */
    public boolean hostile(int a, int b) {
        if (a == b) return false;
        boolean knownA = factions.known(a), knownB = factions.known(b);
        if (knownA && knownB) return factions.atWar(a, b);
        if (knownA || knownB) {
            int known = knownA ? a : b, other = knownA ? b : a;
            if (factions.def(known).hostileToAll) return true;
            return other == PLAYER && standing(known) <= HOSTILE_AT;
        }
        return true;
    }

    /** Why {@code station} refuses the player clearance on account of standing, or null if it does not. */
    public String refusal(Entity station, Entity vessel) {
        if (vessel != player.get()) return null;
        int faction = factionOf(station);
        FactionDef owner = factions.def(faction);
        if (owner != null && owner.hostileToAll) return owner.name + " clear no one to dock";
        if (standing(faction) >= REFUSE_BELOW) return null;
        String why = grievances.get(faction);
        return why != null ? "you " + why : "your standing with " + factions.name(faction) + " is too low";
    }

    /** Promotes the player as far as their standing with the sponsor reaches, a notice each step. */
    private void checkPromotion() {
        int earned = ranks.earnedBy(standing(sponsor()));
        while (record.rank < earned) {
            record.rank++;
            RankDef r = ranks.get(record.rank);
            notices.add(String.format(Locale.ROOT, "Promoted to %s of the %s%s", rankName(), factions.name(sponsor()),
                    r.budget > 0 ? String.format(Locale.ROOT, ": budget %,.0f cr a week", r.budget) : ""));
        }
    }

    /**
     * Folds standing from a save made before factions had a record (kept on the player's ship by
     * faction number) into the record, and drops it from the ship. Call after a load.
     */
    public void migrateLegacy(Entity ship) {
        FactionStandingComponent old = ship == null ? null : ship.getComponent(FactionStandingComponent.class);
        if (old == null) return;
        for (int i = 0; i < old.standing.length; i++) {
            String id = factions.id(i);
            if (id != null && old.standing[i] != 0f) record.set(id, record.get(id) + old.standing[i]);
        }
        ship.remove(FactionStandingComponent.class);
    }

    @Override
    public String saveKey() {
        return "factions";
    }

    @Override
    public void write(Json json) {
        json.writeValue("record", record, ServiceRecord.class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        JsonValue r = data.get("record");
        record = r == null ? fresh() : json.readValue(ServiceRecord.class, r);
        if (record.factions == null || record.standing == null || record.factions.length != record.standing.length) {
            record.factions = new String[0];
            record.standing = new float[0];
        }
        if (factions.index(record.sponsor) < 0) record.sponsor = factions.sponsor().id;
        record.rank = Math.max(0, Math.min(ranks.size() - 1, record.rank));
        grievances.clear();
    }

    /** The sponsor's name and the player's rank, for the screen. */
    public String describeRank() {
        FactionDef s = factions.def(sponsor());
        return rankName() + ", " + (s == null ? "" : s.name);
    }
}
