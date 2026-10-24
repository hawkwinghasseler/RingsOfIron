package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.defs.FactionDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;

/**
 * Hiring and dismissing crew while docked. Each station of a faction (not an outlaw's) has a small
 * pool of crew looking for a berth, posted afresh each in-game day and seeded by the station and
 * the day like its mission board, so a pool is the same whenever it is looked at that day. A hire
 * pays a week's wage up front ({@link #hire}) and draws that wage each payday after
 * ({@link CrewCareer#payWages}). Crew walk aboard as soon as they are hired, and a dismissed crew
 * member stays at the station.
 *
 * <p>A ship berths {@link #berths} people: twice its escape pod seats, at least
 * {@link #MIN_BERTHS}. Everyone aboard counts, the player and prisoners too, so a crew larger than
 * the pods can carry is the player's choice to make. Who has been hired from today's pools is saved.
 */
public class CrewHiring implements SaveParticipant, Resettable {
    /** Crew a station offers each day: at least {@link #MIN_POOL}, at most {@link #MAX_POOL}. */
    public static final int MIN_POOL = 3, MAX_POOL = 5;
    /** Fewest people any ship berths. */
    public static final int MIN_BERTHS = 4;
    static final double DAY = 86_400;

    private static final String[] FIRST = {"Abe", "Ines", "Tomas", "Mira", "Juno", "Rafe", "Sana", "Ilya", "Noor", "Pell",
            "Kit", "Oren", "Lise", "Dag", "Yara", "Bram", "Esme", "Cato", "Wren", "Hale", "Tova", "Rook", "Nell", "Ivo"};
    private static final String[] LAST = {"Okafor", "Lindqvist", "Marsh", "Tanaka", "Reyes", "Volkov", "Achebe", "Brandt",
            "Castell", "Duarte", "Haldane", "Ivers", "Kowal", "Moreau", "Nakamura", "Osei", "Quill", "Sato", "Varga", "Weller"};
    private static final CrewRole[] ROLES = {CrewRole.CREWMAN, CrewRole.CREWMAN, CrewRole.ENGINEER, CrewRole.ENGINEER,
            CrewRole.MARINE, CrewRole.MARINE, CrewRole.MEDIC};

    /** Someone looking for a berth. */
    public static final class Candidate {
        /** Station uuid, day and place in the day's pool, so a candidate can be hired once. */
        public final String key;
        public final String name;
        public final CrewRole role;
        public final CrewRecord record;
        /** What hiring them costs now: a week's wage. */
        public final float signing;

        Candidate(String key, String name, CrewRole role, CrewRecord record) {
            this.key = key;
            this.name = name;
            this.role = role;
            this.record = record;
            this.signing = record.wage;
        }
    }

    private final GameWorld world;
    /** Candidates already hired, by key. */
    private final TreeSet<String> hired = new TreeSet<>();

    CrewHiring(GameWorld world) {
        this.world = world;
    }

    /** Forgets who has been hired, for a load: a save without hires has none. */
    @Override
    public void reset() {
        hired.clear();
    }

    /** The station the player's ship is docked at with clearance, if it hires; else null. */
    public Entity hiringStation() {
        Entity player = world.getPlayer();
        if (player == null || !world.docking.dockedWithClearance(player)) return null;
        Entity station = world.docking.dockedTo(player);
        UuidComponent u = station == null ? null : station.getComponent(UuidComponent.class);
        if (u == null || world.macroSim.station(u.id) == null) return null;
        FactionDef def = world.standing.factions().def(world.standing.factionOf(station));
        return def == null || def.hostileToAll ? null : station;
    }

    /** The crew on offer at {@code stationUuid} today, those already hired left out. */
    public List<Candidate> pool(String stationUuid) {
        long day = (long) Math.floor(world.engine.getSimSeconds() / DAY);
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : post(stationUuid, world.stationDice(stationUuid), day)) if (!hired.contains(c.key)) out.add(c);
        return out;
    }

    /** A station's pool for a day; the same every time for the same station and day. */
    static List<Candidate> post(String stationUuid, long day) {
        return post(stationUuid, stationUuid.hashCode(), day);
    }

    /** A station's pool for a day, drawn from {@code dice} ({@link GameWorld#stationDice}). */
    static List<Candidate> post(String stationUuid, long dice, long day) {
        Random rnd = new Random(NewGame.mix(dice, day));
        int n = MIN_POOL + rnd.nextInt(MAX_POOL - MIN_POOL + 1);
        List<Candidate> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CrewRole role = ROLES[rnd.nextInt(ROLES.length)];
            String name = FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
            CrewRecord r = CrewRecord.from(CrewDef.forRole(role));
            // Most are about average; a few are green or seasoned at what they are hired for
            float main = clamp(0.2f + rnd.nextFloat() * 0.6f);
            float other = clamp(0.25f + rnd.nextFloat() * 0.35f);
            r.gunnery = r.tracking = other;
            r.engineering = other;
            r.marine = other;
            r.medicine = other;
            switch (role) {
                case ENGINEER: r.engineering = main; break;
                case MARINE: r.marine = main; r.gunnery = r.tracking = Math.max(other, main * 0.9f); break;
                case CREWMAN: r.gunnery = r.tracking = main; break;
                case MEDIC: r.medicine = main; break;
                default: break;
            }
            r.wage = CrewCareer.wageFor(role, r);
            out.add(new Candidate(stationUuid + ":" + day + ":" + i, name, role, r));
        }
        return out;
    }

    private static float clamp(float s) {
        return Math.max(0.05f, Math.min(CrewRecord.CAP, s));
    }

    /** Hires the candidate {@code key} from the pool of the station the player is docked at. Null on success, else why not. */
    public String hire(String key) {
        Entity station = hiringStation();
        if (station == null) return "Dock at a station to hire crew";
        String uuid = station.getComponent(UuidComponent.class).id;
        Candidate c = null;
        for (Candidate o : pool(uuid)) if (o.key.equals(key)) c = o;
        if (c == null) return "They have found another berth";
        Entity ship = world.getPlayer();
        if (aboard(ship) >= berths(ship)) return "No berth free: " + berths(ship) + " aboard already";
        TraderComponent account = ship.getComponent(TraderComponent.class);
        if (account == null || account.credits < c.signing) {
            return String.format(Locale.ROOT, "Hiring %s takes a week's wage up front: %,.0f cr", c.name, c.signing);
        }
        int[] tile = freeTile(ship);
        if (tile == null) return "No room aboard";
        Entity e = CrewFactory.spawn(world.crew, ship, c.name, c.role, FactionStanding.PLAYER, tile[0], tile[1]);
        CrewMemberComponent m = world.crew.crew(e);
        m.record = c.record;
        c.record.applyTo(m);
        account.credits -= c.signing;
        hired.add(c.key);
        world.notices.post(String.format(Locale.ROOT, "%s (%s) signs on for %,.0f cr a week", c.name, roleName(c.role), c.record.wage));
        return null;
    }

    /** Lets {@code e}, one of the player's crew, go at the station. Null on success, else why not. */
    public String dismiss(Entity e) {
        if (hiringStation() == null) return "Dock at a station to let crew go";
        Entity ship = world.getPlayer();
        if (!world.crewCareer.ownCrew(ship).contains(e)) return "Not one of your crew";
        CrewMemberComponent c = world.crew.crew(e);
        world.crew.releaseTask(e);
        world.engine.getEcsEngine().removeEntity(e);
        world.notices.post(c.name + " leaves the ship");
        return null;
    }

    /** People a ship berths: twice its pod seats, at least {@link #MIN_BERTHS}. */
    public static int berths(Entity ship) {
        return ship == null ? 0 : Math.max(MIN_BERTHS, 2 * AbandonShip.seats(ship));
    }

    /** People aboard {@code ship} who take a berth: its own crew, the player and prisoners, alive. */
    public int aboard(Entity ship) {
        int n = 0;
        ImmutableArray<Entity> all = world.crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = world.crew.crew(e);
            if (!c.isAlive()) continue;
            if (world.crew.homeOf(e) == ship || (c.held && world.crew.position(e).ship == ship)) n++;
        }
        return n;
    }

    /** An open deck tile of {@code ship} with no one standing on it, else any open deck tile; null if it has none. */
    int[] freeTile(Entity ship) {
        List<int[]> floor = GameScenario.floorTiles(ship.getComponent(ShipGridComponent.class));
        if (floor.isEmpty()) return null;
        ImmutableArray<Entity> all = world.crew.crewEntities();
        for (int[] t : floor) {
            boolean taken = false;
            for (int i = 0; i < all.size() && !taken; i++) {
                GridPositionComponent p = world.crew.position(all.get(i));
                taken = p.ship == ship && p.tileX() == t[0] && p.tileY() == t[1];
            }
            if (!taken) return t;
        }
        return floor.get(0);
    }

    static String roleName(CrewRole role) {
        String s = role.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public String saveKey() {
        return "crew-hiring";
    }

    @Override
    public void write(Json json) {
        // Only today's pools matter: older hires can't be offered again
        long day = (long) Math.floor(world.engine.getSimSeconds() / DAY);
        List<String> keep = new ArrayList<>();
        for (String k : hired) if (dayOf(k) >= day) keep.add(k);
        json.writeValue("hired", keep.toArray(new String[0]), String[].class);
    }

    /** The day in a candidate's key. */
    private static long dayOf(String key) {
        String[] parts = key.split(":");
        try {
            return parts.length < 3 ? -1 : Long.parseLong(parts[parts.length - 2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void read(Json json, JsonValue data) {
        hired.clear();
        JsonValue h = data == null ? null : data.get("hired");
        if (h != null) for (JsonValue v = h.child; v != null; v = v.next) hired.add(v.asString());
    }
}
