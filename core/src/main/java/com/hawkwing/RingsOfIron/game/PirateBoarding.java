package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pirates who board a beaten player (round 7 part 4). Kept small: a pirate that has beaten the
 * player and still has {@link #BOARD_HULL} of its own hull comes alongside with its guns cold,
 * latches on within {@link #LATCH_RANGE}, and after {@link #BOARD_SECONDS} there has taken
 * everything in the player's hold and {@link #CREDIT_SHARE} of their credits (at most
 * {@link #CREDIT_CAP}); then it leaves. There is no fight on the interior grid and the player's crew
 * aren't harmed. One pirate boards at a time; the rest leave the player drifting as before.
 *
 * <p>{@link HostileTraffic} asks {@link #board} each look at a pirate whose enemy, the player, is
 * beaten; while it answers true the pirate is this class's to fly. The goods go into the pirate's
 * own hold as far as they fit, so taking it later gets them back. A boarding in progress (who, and
 * how long latched) is saved under {@link #SAVE_KEY}.
 *
 * <p>Round 8 part 4: a boarder the player's ship hits {@link #BREAK_OFF_HITS} times before the take
 * breaks off with nothing and doesn't board again while the player stays beaten; and nobody boards a
 * ship at a berth or in tow (seed 2026's boarding finished at Highport's berth and tore it loose).
 * Round 9: only hits within {@link #BREAK_OFF_RANGE} of the player count, so a boarding begins close in.
 */
public class PirateBoarding implements SaveParticipant, Resettable {
    /** Key of this state in the save's modules section. */
    public static final String SAVE_KEY = "pirate-boarding";
    /** A pirate with less of its hull than this leaves rather than board. */
    public static final double BOARD_HULL = 0.7;
    /** How close it latches on, m. */
    public static final float LATCH_RANGE = 300f;
    /** How long it stays latched before it is away with the take, sim seconds. */
    public static final double BOARD_SECONDS = 60;
    /** Share of the player's credits the boarders take. */
    public static final double CREDIT_SHARE = 0.10;
    /** Most credits they take, cr. */
    public static final double CREDIT_CAP = 5_000;
    /** A boarding nobody has flown for this long is over, sim seconds: a few of HostileTraffic's looks. */
    static final double STALE_SECONDS = 3 * HostileTraffic.CHECK_SECONDS;
    /** Hits from the player's ship on the boarder, before the take, that make it break off. */
    public static final int BREAK_OFF_HITS = 1;
    /**
     * Hits count towards breaking off only within this range of the player's ship, m; further out a
     * hit works as in any fight. Round 9: in round 8's window the frigate's guns hit the boarder about
     * 4.5 km out, so a player whose ship still had power and a gun never saw a boarding latch on.
     */
    public static final float BREAK_OFF_RANGE = 1_000f;

    /** What a boarding took. */
    public static final class Take {
        public final Entity pirate;
        public final String pirateName;
        /** Commodity and units, in hold order. */
        public final List<String> commodities = new ArrayList<>();
        public final List<Double> units = new ArrayList<>();
        final List<Double> volumes = new ArrayList<>(), masses = new ArrayList<>();
        public double credits;

        Take(Entity pirate, String pirateName) {
            this.pirate = pirate;
            this.pirateName = pirateName;
        }

        /** "40 ore, 12 food and 2,310 cr", or "nothing" when the hold and the account were empty. */
        public String describe() {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < commodities.size(); i++) {
                parts.add(String.format(Locale.ROOT, "%,.0f %s", units.get(i), commodities.get(i)));
            }
            if (credits > 0) parts.add(String.format(Locale.ROOT, "%,.0f cr", credits));
            if (parts.isEmpty()) return "nothing";
            if (parts.size() == 1) return parts.get(0);
            return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        }
    }

    /** A boarding is over: the pirate is away with what it took. */
    public final Signal<Take> robbed = new Signal<>();

    private final GameWorld world;
    /** Uuid of the pirate boarding now, or null. */
    private String boarder;
    /** Seconds it has been latched on. */
    private double latched;
    /** True once it has come within {@link #LATCH_RANGE} and said so. */
    private boolean aboard;
    /** Sim time {@link #board} last flew the boarder; NaN until the first tick after a load. */
    private double asked = Double.NaN;
    /** Hits the player's ship has put on the boarder in this boarding. */
    private int hits;
    /** Sim time this boarding began: rounds fired before it, still in flight, don't count. */
    private double began;
    /** Uuids of pirates driven off under fire: none boards again while the player stays beaten. */
    private final Set<String> drivenOff = new LinkedHashSet<>();

    PirateBoarding(GameWorld world) {
        this.world = world;
        world.combatEvents.impacts.add((signal, hit) -> hit(hit.owner, hit.target, hit.sinceFired));
        world.combatEvents.detonations.add((signal, d) -> hit(d.owner, d.target, d.sinceFired));
        // The tug takes the player away from whoever was alongside
        world.rescue.tows.add((signal, tow) -> {
            if (tow.ship == world.getPlayer()) castOff("casts off as you are taken in tow");
        });
    }

    /**
     * {@code pirate} ({@code id}, {@code h}) has beaten the player and looks at them every
     * {@code seconds}. True while it boards (this flies it); false to let it leave as before: it is
     * hurt too badly, another is boarding, or it has just finished.
     */
    public boolean board(String id, Entity pirate, HostileComponent h, Entity player, double seconds) {
        if (boarder != null && !boarder.equals(id)) return false;
        if (drivenOff.contains(id)) return false;
        if (atBerth(player)) {
            // Nobody boards a ship at a station's berth or in tow: one alongside casts off
            if (id.equals(boarder)) castOff("casts off: you are at a berth");
            return false;
        }
        if (h.hull < BOARD_HULL) {
            // Hurt too badly to board, or hurt since it began: it leaves
            if (boarder != null) reset();
            return false;
        }
        if (boarder == null) {
            boarder = id;
            latched = 0;
            aboard = false;
            hits = 0;
            began = world.engine.getSimSeconds();
            world.notices.post(h.displayName + " is coming alongside to board you", NoticeLog.Kind.WARNING);
        }
        asked = world.engine.getSimSeconds();
        // Alongside, guns cold: it wants the cargo, not a wreck
        SensorComponent sensor = pirate.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = null;
        NpcCombatComponent combat = pirate.getComponent(NpcCombatComponent.class);
        if (combat == null) pirate.add(combat = new NpcCombatComponent(player));
        else if (combat.target != player) combat.setTarget(player);
        combat.preferredRange = LATCH_RANGE * 0.5f;
        // Pulled apart (a load can leave them a few hundred meters off), it comes back and carries on
        if (range(pirate, player) > LATCH_RANGE) return true;
        if (!aboard) {
            aboard = true;
            world.notices.post(h.displayName + " has latched on: pirates are aboard", NoticeLog.Kind.WARNING);
        }
        latched += seconds;
        if (latched < BOARD_SECONDS) return true;
        Take take = take(pirate, h.displayName, player);
        reset();
        world.notices.post("The boarders from " + take.pirateName + " took " + take.describe() + ", and cast off",
                NoticeLog.Kind.WARNING);
        robbed.dispatch(take);
        return false;
    }

    /** Forgets any boarding, for a load: a save without one has none. */
    @Override
    public void reset() {
        asked = Double.NaN;
        boarder = null;
        latched = 0;
        aboard = false;
        hits = 0;
    }

    /** True once {@code id} has broken off a boarding under fire, until the player is no longer beaten. */
    public boolean drivenOff(String id) {
        return drivenOff.contains(id);
    }

    /** At a berth, or being brought in by a tug: no place for a boarding. */
    boolean atBerth(Entity player) {
        return world.docking.dockedTo(player) != null || world.rescue.bringingIn();
    }

    /**
     * A round or warhead from {@code owner}, fired {@code sinceFired} seconds ago, struck {@code target}:
     * the player's on the boarder, fired since it came to board, drives it off once it is within
     * {@link #BREAK_OFF_RANGE}.
     */
    private void hit(Entity owner, Entity target, float sinceFired) {
        if (boarder == null || owner == null || owner != world.getPlayer() || target == null || target != boarder()) return;
        if (world.engine.getSimSeconds() - sinceFired < began) return;
        if (range(target, owner) > BREAK_OFF_RANGE) return;
        if (++hits < BREAK_OFF_HITS) return;
        HostileComponent h = target.getComponent(HostileComponent.class);
        drivenOff.add(boarder);
        reset();
        world.notices.post((h == null ? "The pirate" : h.displayName) + " breaks off its boarding under fire", NoticeLog.Kind.IMPORTANT);
    }

    /** Ends a boarding in progress with nothing taken, saying {@code why} after the pirate's name. */
    private void castOff(String why) {
        if (boarder == null) return;
        Entity pirate = boarder();
        HostileComponent h = pirate == null ? null : pirate.getComponent(HostileComponent.class);
        reset();
        world.notices.post((h == null ? "The pirate" : h.displayName) + " " + why, NoticeLog.Kind.INFO);
        if (pirate != null) standDown(world.getPlayer(), pirate);
    }

    /**
     * The player's guns let go of {@code pirate}: docked with it alongside, a gun still locked on it
     * opens up once the power is back, and its recoil tears the ship off the berth (seed 2026, round
     * 7: torn loose seven times at Highport, the yard's work called off each time).
     */
    static void standDown(Entity player, Entity pirate) {
        if (player == null) return;
        SensorComponent sensor = player.getComponent(SensorComponent.class);
        if (sensor != null && sensor.lockedTarget == pirate) sensor.lockedTarget = null;
        WeaponsComponent weapons = player.getComponent(WeaponsComponent.class);
        if (weapons != null) for (WeaponMountComponent m : weapons.mounts) if (m.target == pirate) m.target = null;
    }

    /** The pirate boarding the player now, or null. */
    public Entity boarder() {
        return boarder == null ? null : world.engine.getEntity(boarder);
    }

    /**
     * Once a sim tick: a boarding ends once {@link HostileTraffic} stops asking (the player is no longer
     * beaten, or the pirate is beaten itself or gone from the bubble).
     */
    public void update() {
        Entity player = world.getPlayer();
        // A fight over: a pirate driven off may board another day
        if (!drivenOff.isEmpty() && (player == null || !ShipDisablement.isDisabled(player))) drivenOff.clear();
        if (boarder == null) return;
        if (player != null && atBerth(player)) {
            castOff("casts off: you are at a berth");
            return;
        }
        double now = world.engine.getSimSeconds();
        if (Double.isNaN(asked)) asked = now;
        Entity pirate = boarder();
        if (pirate != null && pirate.getComponent(PhysicsBodyComponent.class) != null && now - asked <= STALE_SECONDS) return;
        if (aboard && pirate != null) world.notices.post("The boarders cast off", NoticeLog.Kind.INFO);
        else if (pirate != null && pirate.getComponent(HostileComponent.class) != null) {
            // Told it was coming alongside, the player hears it isn't
            world.notices.post(pirate.getComponent(HostileComponent.class).displayName + " turns away without boarding", NoticeLog.Kind.INFO);
        }
        reset();
    }

    /** Empties the player's hold into the pirate's (what doesn't fit is lost) and takes the credits. */
    private Take take(Entity pirate, String name, Entity player) {
        Take take = new Take(pirate, name);
        CargoHoldComponent ours = player.getComponent(CargoHoldComponent.class);
        CargoHoldComponent theirs = pirate.getComponent(CargoHoldComponent.class);
        if (ours != null) {
            for (CargoHoldComponent.CargoStack s : ours.stacks) {
                if (s == null || s.commodity == null || s.units <= 0 || take.commodities.contains(s.commodity)) continue;
                take.commodities.add(s.commodity);
                take.volumes.add(s.unitVolume);
                take.masses.add(s.unitMass);
            }
            for (int i = 0; i < take.commodities.size(); i++) {
                String commodity = take.commodities.get(i);
                double units = ours.unload(commodity, ours.amount(commodity));
                take.units.add(units);
                if (theirs != null) theirs.load(commodity, units, take.volumes.get(i), take.masses.get(i));
            }
        }
        TraderComponent account = player.getComponent(TraderComponent.class);
        if (account != null && account.credits > 0) {
            take.credits = Math.min(CREDIT_CAP, Math.floor(account.credits * CREDIT_SHARE));
            account.credits -= take.credits;
        }
        return take;
    }

    private static double range(Entity a, Entity b) {
        PhysicsBodyComponent pa = a.getComponent(PhysicsBodyComponent.class), pb = b.getComponent(PhysicsBodyComponent.class);
        if (pa == null || pa.body == null || pb == null || pb.body == null) return Double.POSITIVE_INFINITY;
        return PhysicsManager.getPosition(pa.body, new Vector2()).dst(PhysicsManager.getPosition(pb.body, new Vector2()));
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public String saveKey() {
        return SAVE_KEY;
    }

    @Override
    public void write(Json json) {
        if (!drivenOff.isEmpty()) json.writeValue("drivenOff", new ArrayList<>(drivenOff), ArrayList.class, String.class);
        if (boarder == null) return;
        json.writeValue("boarder", boarder);
        json.writeValue("latched", latched);
        json.writeValue("aboard", aboard);
        json.writeValue("hits", hits);
        json.writeValue("began", began);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        boarder = data.getString("boarder", null);
        latched = data.getDouble("latched", 0);
        aboard = data.getBoolean("aboard", false);
        hits = data.getInt("hits", 0);
        began = data.getDouble("began", 0);
        drivenOff.clear();
        JsonValue off = data.get("drivenOff");
        if (off != null) for (JsonValue v = off.child; v != null; v = v.next) drivenOff.add(v.asString());
    }
}
