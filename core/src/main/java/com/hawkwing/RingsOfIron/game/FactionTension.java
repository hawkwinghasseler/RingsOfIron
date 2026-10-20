package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.moddingdata.defs.FactionDef;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * How strained things are between each pair of the data's factions (pirates aside, who are at war
 * with everyone anyway), 0 to {@link #MAX}, and the slow weekly drifts of the career:
 * <ul>
 *   <li>The player's hostile acts against a faction raise its tension with the player's sponsor by
 *       {@link #DEED_SHARE} of the standing they cost; world events raise it through {@link #raise}.</li>
 *   <li>Tension relaxes by {@link #RELAX_PER_WEEK} a week.</li>
 *   <li>At {@link #HIGH} or above, each side flies one more patrol, and a rival's stations refuse a
 *       player of the other side whose standing with them is below {@link #RIVAL_CLEARANCE}. The
 *       Free Traders, neutral and living off the lanes, send a patrol of their own to fly between
 *       the two sides' stations (round 5).</li>
 *   <li>The pirates' base falling eases every pair ({@link #easeAll}).</li>
 *   <li>Negative standing with any faction drifts {@link #RECOVERY_PER_WEEK} a week back towards
 *       zero; positive standing never decays.</li>
 * </ul>
 */
public class FactionTension extends EntitySystem implements SaveParticipant {
    public static final float MAX = 100f;
    /** Tension at which patrols are reinforced and rival stations turn choosy. */
    public static final float HIGH = 50f;
    /** Below this standing, a rival's stations refuse the player while tension is high. */
    public static final float RIVAL_CLEARANCE = 10f;
    /** Share of a hostile act's standing cost that becomes tension with the player's sponsor. */
    public static final float DEED_SHARE = 0.5f;
    /** Tension each pair loses a week (round 5: 5, so world events keep it moving rather than pinned high). */
    public static final float RELAX_PER_WEEK = 5f;
    /** Negative standing regained each week, with every faction. */
    public static final float RECOVERY_PER_WEEK = 1f;
    static final double WEEK = 7 * 86_400.0;
    /** Faction whose patrols watch the lanes between two strained sides. */
    public static final String WATCHERS = HostileTraffic.TRADERS;

    private final GameWorld world;
    /** Tension by pair, "a|b" with the ids in order. */
    private final Map<String, Float> tension = new TreeMap<>();
    /** Pairs whose patrols have been reinforced this time tension ran high. */
    private final Map<String, Boolean> reinforced = new TreeMap<>();
    private double nextWeek = Double.NaN;

    FactionTension(GameWorld world) {
        this.world = world;
        world.standing.hostileActs = (faction, cost) -> {
            int sponsor = world.standing.sponsor();
            if (faction != sponsor) raise(sponsor, faction, cost * DEED_SHARE, null);
        };
    }

    /** Tension between factions {@code a} and {@code b} (crew faction numbers); 0 for pirates or unknowns. */
    public float get(int a, int b) {
        String key = key(a, b);
        return key == null ? 0f : tension.getOrDefault(key, 0f);
    }

    /** True while tension between {@code a} and {@code b} is {@link #HIGH} or above. */
    public boolean high(int a, int b) {
        return get(a, b) >= HIGH;
    }

    /**
     * Raises tension between {@code a} and {@code b} by {@code amount} (a negative amount eases it),
     * {@code why} for the notice when it runs high (null for a plain one).
     */
    public void raise(int a, int b, float amount, String why) {
        String key = key(a, b);
        if (key == null || amount == 0f) return;
        boolean was = high(a, b);
        tension.put(key, Math.max(0f, Math.min(MAX, get(a, b) + amount)));
        if (!was && high(a, b)) runsHigh(a, b, key, why);
    }

    /** Raises tension between the factions {@code a} and {@code b} by id. */
    public void raise(String a, String b, float amount, String why) {
        Factions f = world.standing.factions();
        raise(f.index(a), f.index(b), amount, why);
    }

    private void runsHigh(int a, int b, String key, String why) {
        Factions f = world.standing.factions();
        if (!reinforced.getOrDefault(key, false)) {
            reinforced.put(key, true);
            world.hostiles.reinforce(f.id(a));
            world.hostiles.reinforce(f.id(b));
            watch(a, b);
        }
        world.notices.post(String.format(Locale.ROOT, "%sTension between the %s and the %s runs high: more patrols, and their stations clear only friends",
                why == null ? "" : why + ". ", f.name(a), f.name(b)), NoticeLog.Kind.WARNING);
    }

    /**
     * The Free Traders send a patrol to fly between a station of {@code a} and one of {@code b}, by way
     * of their own, if they have stations and are neither side.
     */
    private void watch(int a, int b) {
        Factions f = world.standing.factions();
        int watchers = f.index(WATCHERS);
        if (watchers < 0 || watchers == a || watchers == b) return;
        String home = firstStation(watchers), sa = firstStation(a), sb = firstStation(b);
        if (home == null || sa == null || sb == null) return;
        if (world.hostiles.patrol(WATCHERS, home, sa, sb)) {
            world.notices.post(String.format(Locale.ROOT, "The %s send a patrol to watch the lanes between the %s and the %s",
                    f.name(watchers), f.name(a), f.name(b)), NoticeLog.Kind.INFO);
        }
    }

    /** The station of {@code faction} first by name (not by the economy's order, which a load may change). */
    private String firstStation(int faction) {
        String best = null, bestName = null;
        for (var n : world.macroSim.stations()) {
            if (world.standing.factionOf(world.engine.getEntity(n.stationUuid)) != faction) continue;
            String name = n.displayName == null ? n.stationUuid : n.displayName;
            if (bestName == null || name.compareTo(bestName) < 0) {
                best = n.stationUuid;
                bestName = name;
            }
        }
        return best;
    }

    /** Eases every pair by {@code amount}: a weight off all sides, such as the pirates' base falling. */
    public void easeAll(float amount) {
        for (Map.Entry<String, Float> e : tension.entrySet()) {
            float v = Math.max(0f, e.getValue() - amount);
            e.setValue(v);
            if (v < HIGH) reinforced.remove(e.getKey());
        }
    }

    /**
     * "Tension: Selene Combine 34, Free Traders 4 (high at 50)": the strain between {@code faction}
     * and each other lawful faction, for the mission board; empty if there is none to show.
     */
    public String describe(int faction) {
        Factions f = world.standing.factions();
        FactionDef own = f.def(faction);
        if (own == null || own.hostileToAll) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < f.all().size(); i++) {
            int other = Factions.FIRST + i;
            if (other == faction || key(faction, other) == null) continue;
            out.append(out.length() == 0 ? "Tension: " : ", ").append(f.name(other))
                    .append(String.format(Locale.ROOT, " %.0f", get(faction, other)));
            if (high(faction, other)) out.append(" HIGH");
        }
        if (out.length() == 0) return "";
        return out.append(String.format(Locale.ROOT, " (high at %.0f)", HIGH)).toString();
    }

    /**
     * Why {@code station} refuses the player clearance on account of tension, or null: its owner is
     * strained with the player's sponsor and the player's standing with the owner is below
     * {@link #RIVAL_CLEARANCE}.
     */
    public String refusal(Entity station, Entity vessel) {
        if (vessel != world.getPlayer()) return null;
        int owner = world.standing.factionOf(station), sponsor = world.standing.sponsor();
        if (owner == sponsor || !high(owner, sponsor) || world.standing.standing(owner) >= RIVAL_CLEARANCE) return null;
        return "tension with the " + world.standing.factions().name(sponsor) + " is high; they clear only friends";
    }

    @Override
    public void update(float deltaTime) {
        double now = world.macroSim.now();
        if (Double.isNaN(nextWeek)) nextWeek = now + WEEK;
        // A long warp can pass several weeks in one frame
        while (now >= nextWeek) {
            weekPasses();
            nextWeek += WEEK;
        }
    }

    /** A week's drift: tension relaxes, and grudges against the player fade a little. */
    void weekPasses() {
        for (Map.Entry<String, Float> e : tension.entrySet()) {
            float v = Math.max(0f, e.getValue() - RELAX_PER_WEEK);
            e.setValue(v);
            if (v < HIGH) reinforced.remove(e.getKey());
        }
        ServiceRecord record = world.standing.record();
        for (FactionDef f : world.standing.factions().all()) {
            float s = record.get(f.id);
            if (s < 0f) record.set(f.id, Math.min(0f, s + RECOVERY_PER_WEEK));
        }
    }

    /** "a|b" for two of the data's factions that may grow strained, or null. */
    private String key(int a, int b) {
        Factions f = world.standing.factions();
        FactionDef fa = f.def(a), fb = f.def(b);
        if (fa == null || fb == null || a == b || fa.hostileToAll || fb.hostileToAll) return null;
        return fa.id.compareTo(fb.id) < 0 ? fa.id + "|" + fb.id : fb.id + "|" + fa.id;
    }

    @Override
    public String saveKey() {
        return "tension";
    }

    @Override
    public void write(Json json) {
        json.writeObjectStart("pairs");
        for (Map.Entry<String, Float> e : tension.entrySet()) json.writeValue(e.getKey(), e.getValue());
        json.writeObjectEnd();
        json.writeArrayStart("reinforced");
        for (String k : reinforced.keySet()) json.writeValue(k);
        json.writeArrayEnd();
        json.writeValue("nextWeek", nextWeek);
    }

    @Override
    public void read(Json json, JsonValue data) {
        tension.clear();
        reinforced.clear();
        JsonValue pairs = data.get("pairs");
        if (pairs != null) for (JsonValue p = pairs.child; p != null; p = p.next) tension.put(p.name, p.asFloat());
        JsonValue r = data.get("reinforced");
        if (r != null) for (JsonValue p = r.child; p != null; p = p.next) reinforced.put(p.asString(), true);
        nextWeek = data.getDouble("nextWeek", Double.NaN);
    }

    /** Forgets every strain, for a new career or a save from before tension. */
    public void reset() {
        tension.clear();
        reinforced.clear();
        nextWeek = Double.NaN;
    }
}
