package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.orbital.warp.DropOutSource;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Pirates and faction patrols, as the game flies and fights them. macro-sim's
 * {@link HostileTrafficController} moves them on the rails; this spawns their ships (crewed, armed,
 * on their own layouts), keeps the population topped up as {@link #difficulty} asks, and runs them
 * in the bubble with ai-crew's combat pilot:
 * <ul>
 *   <li>A pirate goes for the player when it came hunting them or the player shot at it. Waiting for
 *       a freighter, it closes on it as it comes in and takes its cargo once it has held alongside for
 *       {@link #PLUNDER_SECONDS}; it never docks.</li>
 *   <li>A patrol engages pirates of another side in the bubble, and the player if they shot at it.</li>
 *   <li>Either fights to {@link Tactics}: standing off with missiles, rushing in with short guns,
 *       going round for the engines when it clearly outguns its enemy, or (a pirate that is losing)
 *       running. It breaks off below {@link #BREAK_OFF_HULL} of its hull, {@link #CROSSFIRE_BREAK_OFF_HULL}
 *       when it is under fire from several sides ({@link #CROSSFIRE_ANGLE}), and leaves when it has had
 *       nothing to do for {@link #IDLE_LIMIT}.</li>
 *   <li>Pirates are armed from {@code [loadout.*]} by hull and {@link #difficulty}; raiders fly in pairs,
 *       the second as the first's wingman, fighting whatever its leader fights.</li>
 *   <li>A pirate that gets away from a fight with the player, or robs {@link PirateAces#PLUNDERS_TO_ACE}
 *       freighters, becomes one of the {@link PirateAces}.</li>
 * </ul>
 * A pirate planning an intercept on the player stops time warp ({@link DropOutSource}) and says so in
 * {@link #notices}.
 *
 * <p>Its own state is saved with the game under {@link #SAVE_KEY}: the next top-up, the pirates'
 * name counter, the intercept alert, the player's grace and each hostile's bubble timers. Who shot
 * at whom is not: a hostile that was provoked fights on from its locked target.
 */
public class HostileTraffic extends EntitySystem implements BubbleTransitionListener, DropOutSource,
        HostileTrafficController.Listener, SaveParticipant {
    /** Key of this system's state in the save's modules section. */
    public static final String SAVE_KEY = "hostile-traffic";
    /** Seconds between looks at the bubble's hostiles. */
    static final float CHECK_SECONDS = 1f;
    /**
     * A hostile breaks off a fight below this share of its hull. Above {@link ShipDisablement#SURRENDER_HULL},
     * so a ship that is losing tries to get away first, and surrenders only if it is caught and hit again.
     */
    public static final double BREAK_OFF_HULL = 0.65;
    /** Under fire from several sides, a hostile breaks off sooner: below this share of its hull. */
    public static final double CROSSFIRE_BREAK_OFF_HULL = 0.8;
    /** Two attackers this far apart in bearing, degrees, put a ship in a crossfire. */
    public static final float CROSSFIRE_ANGLE = 60f;
    /** An attacker counts toward a crossfire for this long after it last fired, sim seconds. */
    public static final double CROSSFIRE_SECONDS = 20;
    /** How close a pirate holds alongside its prey to take its cargo, m. */
    public static final float PLUNDER_RANGE = 1_000f;
    /** How long it must hold there, sim seconds. */
    public static final double PLUNDER_SECONDS = 20;
    /** A hostile with nothing to do in the bubble for this long leaves it, sim seconds. */
    public static final double IDLE_LIMIT = HostileTrafficController.PATROL_HOLD;
    /** No pirate goes after the player for this long after a new game, sim seconds. */
    public static final double PLAYER_GRACE = 6 * 3600.0;
    /** Sim seconds between top-ups of the pirate population. */
    public static final double TOP_UP_INTERVAL = 3600;
    /** A pirate is never spawned at a lurk closer than this to the player, m. */
    public static final double SPAWN_CLEARANCE = 200_000;

    public static final String RAIDER = "raider_light";
    public static final String GUNSHIP = "gunship_heavy";
    /** Faction ids, as the factions work names them: the player's sponsor, Selene, and the pirates. */
    public static final String SPONSOR = "patrol", SELENE = "selene", PIRATES = "pirates";
    /** Crew factions (ai-crew numbers) of each; the sponsor's is the stations' own. */
    static final int SPONSOR_CREW = StationCrew.FACTION, PIRATE_CREW = 3, SELENE_CREW = 4;

    private static final CrewRole[] CREW_ROLES = {CrewRole.CREWMAN, CrewRole.MARINE, CrewRole.ENGINEER,
            CrewRole.MEDIC, CrewRole.MARINE, CrewRole.CREWMAN};

    private final GameWorld world;
    private final HostileTrafficController controller;
    /**
     * How hard the game is: 1 is the default. Scales the pirate population; the loop merge points it
     * at the game's difficulty setting.
     */
    public DoubleSupplier difficulty = () -> 1.0;
    /**
     * Raiders kept beyond what {@link #difficulty} asks for, and where they gather: the pirate
     * base's growing band ({@link PirateBase}). None without a base.
     */
    public java.util.function.IntSupplier extraRaiders = () -> 0;
    /** Where the extra raiders spawn; null spawns them at the lurks like the rest. */
    public String extraLurk;
    private boolean topUpDue;
    /** What the hostiles did that the player should hear of; the screen drains it. */
    public final List<String> notices = new ArrayList<>();

    private String[] lurks = new String[0];
    /** Each patrolling faction's route. */
    private final Map<String, String[]> routes = new HashMap<>();
    /** Patrols to spawn once the update is over. */
    private final List<String> reinforcements = new ArrayList<>();
    private int spawned;
    private double nextTopUp = Double.POSITIVE_INFINITY;
    /** Sim second a pirate started an intercept on the player; NaN when there is no alert. */
    private double alertAt = Double.NaN;
    private float since = CHECK_SECONDS;
    /** Who last shot at whom, victim to shooter: a hostile fights whoever attacked it. */
    private final Map<Entity, Entity> provoked = new IdentityHashMap<>();
    /** Sim second each hostile in the bubble last had something to do. */
    private final Map<String, Double> busySince = new HashMap<>();
    /** Sim seconds a pirate has held alongside its prey. */
    private final Map<String, Double> alongside = new HashMap<>();
    /** Each hostile's attackers in the bubble, and the sim second each last fired at it. */
    private final Map<Entity, Map<Entity, Double>> attackers = new IdentityHashMap<>();
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    private HostileTraffic(GameWorld world) {
        this.world = world;
        controller = world.macroSim.hostiles();
        controller.addListener(this);
        controller.playerFirepower = () -> firepower(world.getPlayer());
        world.macroSim.bubble().addListener(this);
        world.timeWarp.addDropOutSource(this);
        world.timeWarp.changes.add((signal, change) -> alertAt = Double.NaN);
        world.combatEvents.shots.add((signal, shot) -> onShotAt(shot.target, shot.shooter));
        world.combatEvents.impacts.add((signal, hit) -> onShotAt(hit.target, hit.owner));
        world.engine.getSerializationService().addParticipant(this);
    }

    /** Puts hostile traffic into {@code world}. */
    public static HostileTraffic install(GameWorld world) {
        HostileTraffic traffic = new HostileTraffic(world);
        world.engine.addSystem(traffic);
        return traffic;
    }

    /**
     * Starts the population for a new game: pirates lurking by {@code lurks} in turn, as many as
     * {@link #difficulty} asks for, the sponsor's patrol flying {@code sponsorRoute} and Selene's
     * guarding {@code seleneRoute}. No pirate goes after the player for {@link #PLAYER_GRACE}.
     */
    public void start(String[] lurks, String[] sponsorRoute, String[] seleneRoute) {
        this.lurks = lurks.clone();
        relinkRoutes(sponsorRoute, seleneRoute);
        double now = world.macroSim.now();
        controller.playerGraceUntil = now + PLAYER_GRACE;
        if (sponsorRoute.length > 0) spawnPatrol(SPONSOR, sponsorRoute);
        if (seleneRoute.length > 0) spawnPatrol(SELENE, seleneRoute);
        topUp(now, true);
        nextTopUp = now + TOP_UP_INTERVAL;
    }

    /** Points the lurks at a loaded game's stations, which have new uuids. */
    public void relink(String[] lurks) {
        this.lurks = lurks.clone();
        // A save from before this state was saved: top-ups start again from the load, and new
        // pirates are numbered on from the highest loaded
        if (nextTopUp == Double.POSITIVE_INFINITY && this.lurks.length > 0) {
            nextTopUp = world.macroSim.now() + TOP_UP_INTERVAL;
        }
        for (HostileComponent h : controller.all().values()) spawned = Math.max(spawned, number(h.displayName));
    }

    /** Forgets this system's own state, before a save is loaded over it. */
    public void reset() {
        spawned = 0;
        nextTopUp = Double.POSITIVE_INFINITY;
        topUpDue = false;
        alertAt = Double.NaN;
        controller.playerGraceUntil = 0;
        provoked.clear();
        busySince.clear();
        alongside.clear();
    }

    /** The number a hostile's name ends with ("Pirate raider 3"), or 0. */
    static int number(String name) {
        if (name == null) return 0;
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        if (i == name.length() || name.length() - i > 9) return 0;
        return Integer.parseInt(name.substring(i));
    }

    /** Sim seconds of the next top-up of the pirate population; infinite before {@link #start}. */
    public double nextTopUp() {
        return nextTopUp;
    }

    /** True while a pirate's intercept on the player holds time warp off. */
    public boolean alerted() {
        return !Double.isNaN(alertAt);
    }

    /** The routes the sponsor's and Selene's patrols fly, for reinforcements; a load points them at its stations. */
    public void relinkRoutes(String[] sponsorRoute, String[] seleneRoute) {
        routes.put(SPONSOR, sponsorRoute.clone());
        routes.put(SELENE, seleneRoute.clone());
    }

    /**
     * Sends faction {@code faction} one more patrol on its route, once the engine update is over (a
     * tension spike, say). False if the faction flies no patrols.
     */
    public boolean reinforce(String faction) {
        String[] route = routes.get(faction);
        if (route == null || route.length == 0) return false;
        reinforcements.add(faction);
        return true;
    }

    /** Patrols of each faction flying, reinforcements included. */
    public int patrols(String faction) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) {
            if (h.role == HostileComponent.Role.PATROL && faction.equals(h.faction)) n++;
        }
        return n;
    }

    /** Raiders the population keeps at difficulty {@code d}. */
    public static int raidersFor(double d) {
        return 1 + (int) Math.round(Math.max(0, d));
    }

    /** Gunships the population keeps at difficulty {@code d}. */
    public static int gunshipsFor(double d) {
        return (int) Math.floor(Math.max(0, d));
    }

    // ---------------------------------------------------------------------------------------------

    /** Runs a top-up {@link #update} found due, once the engine update is over; {@link GameWorld#update} calls it. */
    public void afterUpdate() {
        while (!reinforcements.isEmpty()) {
            String faction = reinforcements.remove(0);
            spawnPatrol(faction, routes.get(faction));
        }
        if (!topUpDue) return;
        topUpDue = false;
        topUp(world.macroSim.now(), false);
    }

    @Override
    public void update(float deltaTime) {
        double now = world.macroSim.now();
        if (now >= nextTopUp) {
            // Spawned here, mid-update, a ship would not be in the engine (or on the rails) until the
            // update ends; the top-up waits for afterUpdate
            topUpDue = true;
            nextTopUp = now + TOP_UP_INTERVAL;
        }
        since += deltaTime;
        if (since < CHECK_SECONDS) return;
        since = 0f;
        for (String id : world.macroSim.bubble().inBubble()) {
            Entity e = world.engine.getEntity(id);
            HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
            if (h == null || h.state != HostileComponent.State.IN_BUBBLE) continue;
            if (e.getComponent(PhysicsBodyComponent.class) == null) continue;
            if (h.ace && !h.partDropped && FirstPlay.beaten(world, e)) aceBeaten(e, h);
            if (ShipDisablement.isDisabled(e)) {
                // Beaten or surrendered, it drifts where it is to be boarded: no flying off with a prize crew aboard
                disengage(e);
                e.remove(NpcPilotComponent.class);
                continue;
            }
            fly(id, e, h, now);
        }
    }

    private void fly(String id, Entity e, HostileComponent h, double now) {
        h.hull = hull(e);
        boolean crossfire = crossfire(e, now);
        if (!h.brokeOff && h.hull < breakOffHull(h, crossfire)) {
            breakOff(id, h, now);
            notices.add(h.displayName + " is breaking off");
        }
        if (h.brokeOff) {
            leave(e);
            return;
        }
        Entity enemy = enemyOf(e, h);
        if (enemy != null) {
            if (enemy == world.getPlayer()) h.foughtPlayer = true;
            // An ace never runs on the odds: it fights to its break-off line
            Tactics.Plan plan = Tactics.choose(Tactics.arms(e), odds(id, e, h, enemy), h.hull, h.isPirate() && !aceAtLarge(h),
                    crossfire);
            if (plan == Tactics.Plan.RUN) {
                breakOff(id, h, now);
                world.notices.post(h.displayName + " is running", NoticeLog.Kind.INFO);
                leave(e);
                return;
            }
            engage(e, enemy, true, Tactics.HOLD_RANGE);
            Tactics.apply(e.getComponent(NpcCombatComponent.class), plan);
            busySince.put(id, now);
        } else if (h.isPirate() && h.targetUuid != null && !h.huntingPlayer()) {
            stalk(id, e, h, now);
        } else {
            disengage(e);
            coastWhenDone(e);
            if (busy(e)) busySince.put(id, now);
        }
        Double busy = busySince.get(id);
        if (busy == null) busySince.put(id, now);
        else if (!h.brokeOff && now - busy > IDLE_LIMIT) breakOff(id, h, now);
    }

    /**
     * The share of its hull below which {@code h} breaks off: {@link #BREAK_OFF_HULL}, or sooner under
     * crossfire; an ace holds on to {@link PirateAces#BREAK_OFF_HULL}, and under crossfire to a plain pirate's line.
     */
    static double breakOffHull(HostileComponent h, boolean crossfire) {
        if (aceAtLarge(h)) return crossfire ? BREAK_OFF_HULL : PirateAces.BREAK_OFF_HULL;
        return crossfire ? CROSSFIRE_BREAK_OFF_HULL : BREAK_OFF_HULL;
    }

    private static boolean aceAtLarge(HostileComponent h) {
        return h.ace && !h.partDropped;
    }

    /** Who the hostile fights in the bubble now, or null. */
    private Entity enemyOf(Entity e, HostileComponent h) {
        Entity player = world.getPlayer();
        Entity attacker = provoked.get(e);
        if (attacker != null && attacker.getComponent(PhysicsBodyComponent.class) != null && attacker != e) return attacker;
        Entity leaders = leadersEnemy(h);
        if (leaders != null) return leaders;
        if (h.isPirate()) {
            return h.huntingPlayer() && player != null && player.getComponent(PhysicsBodyComponent.class) != null
                    ? player : null;
        }
        // A patrol: the nearest pirate of another side in the bubble
        Body body = e.getComponent(PhysicsBodyComponent.class).body;
        Entity best = null;
        float bestDistance = Float.MAX_VALUE;
        for (String id : world.macroSim.bubble().inBubble()) {
            Entity other = world.engine.getEntity(id);
            HostileComponent o = other == null ? null : other.getComponent(HostileComponent.class);
            if (o == null || !o.isPirate() || (o.faction != null && o.faction.equals(h.faction))) continue;
            PhysicsBodyComponent pb = other.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null) continue;
            float d = pb.body.getPosition().dst(body.getPosition());
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    /** A wingman fights whatever its leader has its guns on in the bubble; null if nothing. */
    private Entity leadersEnemy(HostileComponent h) {
        Entity leader = h.wingOf == null ? null : world.engine.getEntity(h.wingOf);
        SensorComponent sensor = leader == null ? null : leader.getComponent(SensorComponent.class);
        Entity target = sensor == null ? null : sensor.lockedTarget;
        return target != null && target.getComponent(PhysicsBodyComponent.class) != null ? target : null;
    }

    /**
     * The odds a hostile fights at: its firepower times what is left of its hull, with its wing's
     * when they fight the same enemy, over the enemy's.
     */
    private double odds(String id, Entity e, HostileComponent h, Entity enemy) {
        double ours = firepower(e) * h.hull;
        for (String other : world.macroSim.bubble().inBubble()) {
            if (other.equals(id)) continue;
            Entity o = world.engine.getEntity(other);
            HostileComponent oh = o == null ? null : o.getComponent(HostileComponent.class);
            if (oh == null || !sameWing(id, h, other, oh)) continue;
            SensorComponent sensor = o.getComponent(SensorComponent.class);
            if (sensor != null && sensor.lockedTarget == enemy) ours += firepower(o) * oh.hull;
        }
        double theirs = firepower(enemy) * hull(enemy);
        return theirs <= 0 ? Double.POSITIVE_INFINITY : ours / theirs;
    }

    private static boolean sameWing(String a, HostileComponent ha, String b, HostileComponent hb) {
        return a.equals(hb.wingOf) || b.equals(ha.wingOf) || (ha.wingOf != null && ha.wingOf.equals(hb.wingOf));
    }

    /** True when attackers that fired at {@code e} lately are {@link #CROSSFIRE_ANGLE} apart in bearing from it. */
    private boolean crossfire(Entity e, double now) {
        Map<Entity, Double> by = attackers.get(e);
        if (by == null || by.size() < 2) return false;
        Body body = e.getComponent(PhysicsBodyComponent.class).body;
        List<Float> bearings = new ArrayList<>();
        by.entrySet().removeIf(en -> now - en.getValue() > CROSSFIRE_SECONDS);
        for (Entity a : by.keySet()) {
            PhysicsBodyComponent pb = a.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null) continue;
            float dx = pb.body.getPosition().x - body.getPosition().x, dy = pb.body.getPosition().y - body.getPosition().y;
            bearings.add((float) Math.toDegrees(Math.atan2(dy, dx)));
        }
        for (int i = 0; i < bearings.size(); i++) {
            for (int j = i + 1; j < bearings.size(); j++) {
                float d = Math.abs(bearings.get(i) - bearings.get(j)) % 360f;
                if (Math.min(d, 360f - d) >= CROSSFIRE_ANGLE) return true;
            }
        }
        return false;
    }

    /** A beaten ace: its rare part crated aboard, and the news. */
    private void aceBeaten(Entity e, HostileComponent h) {
        String part = PirateAces.dropPart(e, h, world.combatDefs);
        WeaponDef def = part == null ? null : world.combatDefs.weapons.get(part);
        world.notices.post("The ace " + h.displayName + " is beaten"
                + (def == null ? "" : ": a spare " + def.displayName() + " is crated in its hold"), NoticeLog.Kind.IMPORTANT);
    }

    /** A pirate waiting for its freighter: closes on it once it is here, and takes its cargo. */
    private void stalk(String id, Entity e, HostileComponent h, double now) {
        Entity prey = world.engine.getEntity(h.targetUuid);
        FreighterComponent f = prey == null ? null : prey.getComponent(FreighterComponent.class);
        if (!stillComing(f, h)) {
            breakOff(id, h, now);
            leave(e);
            return;
        }
        busySince.put(id, now);
        PhysicsBodyComponent pb = prey.getComponent(PhysicsBodyComponent.class);
        if (f.state != FreighterComponent.State.IN_BUBBLE || pb == null || pb.body == null) {
            disengage(e);
            coastWhenDone(e);
            return;
        }
        // Alongside, guns cold: it wants the cargo, not a wreck
        engage(e, prey, false, PLUNDER_RANGE * 0.5f);
        Body body = e.getComponent(PhysicsBodyComponent.class).body;
        if (body.getPosition().dst(pb.body.getPosition()) > PLUNDER_RANGE) {
            alongside.remove(id);
            return;
        }
        double held = alongside.merge(id, (double) CHECK_SECONDS, Double::sum);
        if (held < PLUNDER_SECONDS) return;
        alongside.remove(id);
        String commodity = f.cargoCommodity;
        double taken = controller.plunderInBubble(id, now);
        if (taken > 0) {
            notices.add(String.format("%s robbed %s of %.0f %s", h.displayName, f.displayName, taken,
                    commodity == null ? "cargo" : commodity));
        }
    }

    /** True while {@code f} is still on its way to where the pirate waits, with something to take. */
    private static boolean stillComing(FreighterComponent f, HostileComponent h) {
        if (f == null || f.cargoQuantity <= 0 || h.ambushStationUuid == null
                || !h.ambushStationUuid.equals(f.destinationUuid)) return false;
        FreighterComponent.State s = f.state == FreighterComponent.State.IN_BUBBLE ? f.resumeState : f.state;
        return s == FreighterComponent.State.TO_DELIVERY;
    }

    private void breakOff(String id, HostileComponent h, double now) {
        if (h.isPirate()) controller.breakOff(id, now);
        else controller.moveOn(id);
    }

    /** Turns the guns (or just the pilot, when {@code fire} is false) on {@code target}. */
    private static void engage(Entity e, Entity target, boolean fire, float range) {
        SensorComponent sensor = e.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = fire ? target : null;
        NpcCombatComponent combat = e.getComponent(NpcCombatComponent.class);
        if (combat == null) {
            combat = new NpcCombatComponent(target);
            e.add(combat);
        } else if (combat.target != target) {
            combat.setTarget(target);
        }
        combat.preferredRange = range;
    }

    private static void disengage(Entity e) {
        SensorComponent sensor = e.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = null;
        e.remove(NpcCombatComponent.class);
    }

    /** Flies off out of the bubble; macro-sim takes it back at the edge. */
    private static void leave(Entity e) {
        disengage(e);
        NpcPilotComponent pilot = e.getComponent(NpcPilotComponent.class);
        if (pilot == null || !pilot.burns.isEmpty() || pilot.seekTarget != null) e.add(new NpcPilotComponent());
    }

    /** Once its rails burns are flown it holds where it is instead of leaving. */
    private static void coastWhenDone(Entity e) {
        NpcPilotComponent pilot = e.getComponent(NpcPilotComponent.class);
        if (pilot != null && pilot.burns.isEmpty() && !pilot.burnActive) e.remove(NpcPilotComponent.class);
    }

    /** Still flying burns its rails plan left. */
    private static boolean busy(Entity e) {
        NpcPilotComponent pilot = e.getComponent(NpcPilotComponent.class);
        return pilot != null && (!pilot.burns.isEmpty() || pilot.burnActive);
    }

    /** {@code attacker} shot at {@code victim}: a hostile fights back whoever attacked it last. */
    public void onShotAt(Entity victim, Entity attacker) {
        if (victim == null || attacker == null || victim == attacker) return;
        HostileComponent h = victim.getComponent(HostileComponent.class);
        if (h == null) return;
        provoked.put(victim, attacker);
        attackers.computeIfAbsent(victim, k -> new IdentityHashMap<>()).put(attacker, world.macroSim.now());
        if (attacker == world.getPlayer()) h.foughtPlayer = true;
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
        Entity e = world.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        if (h == null) return;
        busySince.put(uuid, t);
        // Its loadout again, in case its weapons came back from a save as its ship's stock ones
        LoadoutDef loadout = h.loadout == null ? null : world.combatDefs.loadouts.get(h.loadout);
        if (loadout != null) Loadouts.apply(e, loadout, world.combatDefs);
        // Come to fight the player: guns on them from the start
        Entity player = world.getPlayer();
        if (h.huntingPlayer() && player != null) engage(e, player, true, 1_500f);
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        Entity e = world.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        if (h == null) return;
        boolean fought = h.foughtPlayer;
        h.foughtPlayer = false;
        if (fought && h.isPirate() && !FirstPlay.beaten(world, e)) escaped(e, h);
        disengage(e);
        // Rebuilt from its layout next time, never from a stale shape
        e.remove(BodyShapeComponent.class);
        provoked.remove(e);
        attackers.remove(e);
        busySince.remove(uuid);
        alongside.remove(uuid);
    }

    @Override
    public void intercepting(String hostile, String target, double t) {
        if (!HostileComponent.PLAYER.equals(target)) return;
        HostileComponent h = controller.get(hostile);
        notices.add((h == null ? "A pirate" : h.displayName) + " is moving to intercept you");
        alertAt = t;
    }

    @Override
    public void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
        HostileComponent h = controller.get(hostile);
        Entity e = world.engine.getEntity(hostile);
        if (h == null || e == null || h.ace || h.plunders < PirateAces.PLUNDERS_TO_ACE) return;
        String was = h.displayName;
        if (PirateAces.promote(world, e, h, controller.all().values(), difficulty.getAsDouble(), false)) {
            world.notices.post(String.format("%s has robbed %d freighters: they call it %s now, an ace", was, h.plunders,
                    h.displayName), NoticeLog.Kind.INFO);
        }
    }

    /** A pirate got away from a fight with the player: it becomes an ace, or an ace keeps a grudge. */
    private void escaped(Entity e, HostileComponent h) {
        h.escapes++;
        if (h.ace) {
            h.grudge = true;
            return;
        }
        String was = h.displayName;
        if (PirateAces.promote(world, e, h, controller.all().values(), difficulty.getAsDouble(), true)) {
            world.notices.post(was + " got away from you: it flies as the ace " + h.displayName
                    + " now, and it will come looking for you", NoticeLog.Kind.WARNING);
        }
    }

    @Override
    public double nextDropOutTime() {
        return Double.isNaN(alertAt) ? Double.NaN : world.engine.getSimSeconds();
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public String saveKey() {
        return SAVE_KEY;
    }

    @Override
    public void write(Json json) {
        json.writeValue("spawned", spawned);
        // JSON has no infinity or NaN: a missing value means none
        if (nextTopUp != Double.POSITIVE_INFINITY) json.writeValue("nextTopUp", nextTopUp);
        if (topUpDue) json.writeValue("topUpDue", true);
        if (!Double.isNaN(alertAt)) json.writeValue("alertAt", alertAt);
        json.writeValue("playerGraceUntil", controller.playerGraceUntil);
        writeTimes(json, "busySince", busySince);
        writeTimes(json, "alongside", alongside);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        spawned = data.getInt("spawned", 0);
        nextTopUp = data.getDouble("nextTopUp", Double.POSITIVE_INFINITY);
        topUpDue = data.getBoolean("topUpDue", false);
        alertAt = data.getDouble("alertAt", Double.NaN);
        controller.playerGraceUntil = data.getDouble("playerGraceUntil", 0);
        readTimes(data.get("busySince"), busySince);
        readTimes(data.get("alongside"), alongside);
    }

    private static void writeTimes(Json json, String name, Map<String, Double> times) {
        json.writeObjectStart(name);
        for (Map.Entry<String, Double> e : times.entrySet()) json.writeValue(e.getKey(), e.getValue());
        json.writeObjectEnd();
    }

    private static void readTimes(JsonValue data, Map<String, Double> into) {
        if (data == null) return;
        for (JsonValue v = data.child; v != null; v = v.next) into.put(v.name, v.asDouble());
    }

    // ---------------------------------------------------------------------------------------------

    /** Tops the population up now, out of the player's sight, as the hourly top-up does; for tests. */
    void topUpNow() {
        topUp(world.macroSim.now(), false);
    }

    /** Spawns pirates until the population is what {@link #difficulty} asks for. */
    private void topUp(double now, boolean anywhere) {
        if (lurks.length == 0) return;
        double d = difficulty.getAsDouble();
        int extra = extraRaiders.getAsInt();
        int raiders = raidersFor(d) + extra - count(RAIDER), gunships = gunshipsFor(d) - count(GUNSHIP);
        for (int i = 0; i < gunships; i++) spawnPirate(GUNSHIP, now, anywhere, null);
        // The base's band gathers at the base; the rest at the lurks in turn
        int atBase = extraLurk == null ? 0 : Math.max(0, extra - countAt(extraLurk));
        for (int i = 0; i < raiders; i++) spawnPirate(RAIDER, now, anywhere, i < atBase ? extraLurk : null);
    }

    private int count(String shipId) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && shipId.equals(h.shipId)) n++;
        return n;
    }

    private int countAt(String lurk) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && lurk.equals(h.stationUuid)) n++;
        return n;
    }

    /**
     * Spawns a pirate at {@code at} if given (and out of the player's sight), else at the next lurk that is.
     * A raider with no place given flies as the wingman of a raider on its own, by its lurk.
     */
    private void spawnPirate(String shipId, double now, boolean anywhere, String at) {
        String leader = RAIDER.equals(shipId) && at == null ? loneRaider() : null;
        String[] choices = at != null && (anywhere || !nearPlayer(at, now)) ? new String[]{at} : lurks;
        for (int k = 0; k < choices.length; k++) {
            String lurk = leader != null ? controller.get(leader).stationUuid : choices[(spawned + k) % choices.length];
            if (!anywhere && nearPlayer(lurk, now)) {
                if (leader == null) continue;
                leader = null;
                k = -1;
                continue;
            }
            spawnPirateAt(shipId, lurk, leader);
            return;
        }
    }

    /**
     * A pirate of {@code shipId} lurking by {@code lurk}, armed from its hull's loadouts and flying as
     * {@code leader}'s wingman if given; its uuid, or null if the data has no such ship.
     */
    private String spawnPirateAt(String shipId, String lurk, String leader) {
        Entity ship = spawnShip(shipId, PIRATE_CREW);
        if (ship == null) return null;
        String name = (GUNSHIP.equals(shipId) ? "Pirate gunship " : "Pirate raider ") + (++spawned);
        LoadoutDef loadout = Loadouts.pick(world.combatDefs, shipId, difficulty.getAsDouble(), false,
                new Random(spawned * 7_919L + shipId.hashCode()));
        if (loadout != null) Loadouts.apply(ship, loadout, world.combatDefs);
        HostileComponent h = new HostileComponent(name, shipId, PIRATES, HostileComponent.Role.PIRATE,
                firepower(ship));
        h.loadout = loadout == null ? null : loadout.id;
        h.wingOf = leader;
        // Spawned mid-update, the ship isn't registered by uuid until the update ends
        ship.add(h);
        String uuid = ship.getComponent(UuidComponent.class).id;
        world.macroSim.addPirate(uuid, h, lurk);
        return uuid;
    }

    /**
     * Raiders come to work {@code station}'s lanes: {@code count} more pirates of {@link #RAIDER}
     * lurking by it, on top of the usual population ({@link WorldEvents}' raid). Call outside the
     * engine's update. Returns their uuids.
     */
    public List<String> raid(String station, int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuid = spawnPirateAt(RAIDER, station, null);
            if (uuid != null) out.add(uuid);
        }
        return out;
    }

    /** A raider flying on its own, with no wingman: the first, by uuid order. */
    private String loneRaider() {
        Set<String> leaders = new HashSet<>();
        for (HostileComponent h : controller.all().values()) if (h.wingOf != null) leaders.add(h.wingOf);
        String best = null;
        for (Map.Entry<String, HostileComponent> en : controller.all().entrySet()) {
            HostileComponent h = en.getValue();
            if (!h.isPirate() || !RAIDER.equals(h.shipId) || h.wingOf != null || h.ace || leaders.contains(en.getKey())
                    || h.stationUuid == null) continue;
            if (best == null || en.getKey().compareTo(best) < 0) best = en.getKey();
        }
        return best;
    }

    private void spawnPatrol(String faction, String[] route) {
        Entity ship = spawnShip(GUNSHIP, SPONSOR.equals(faction) ? SPONSOR_CREW : SELENE_CREW);
        if (ship == null) return;
        String name = (SPONSOR.equals(faction) ? "Patrol cutter " : "Selene guard ") + (++spawned);
        HostileComponent h = new HostileComponent(name, GUNSHIP, faction, HostileComponent.Role.PATROL,
                firepower(ship));
        ship.add(h);
        world.macroSim.addPatrol(ship.getComponent(UuidComponent.class).id, h, route);
    }

    /** True if {@code station} is close enough to the player that a ship appearing there would be seen. */
    private boolean nearPlayer(String station, double now) {
        var rails = world.macroSim.rails();
        if (!rails.isOnRails(station)) return true;
        String parent = rails.parentOf(station);
        if (!parent.equals(world.macroSim.gateway().anchorParentUuid())) return false;
        rails.stateAt(station, now, a);
        world.macroSim.gateway().anchorStateAt(now, b);
        return Math.hypot(a.x - b.x, a.y - b.y) < SPAWN_CLEARANCE;
    }

    /**
     * A crewed, armed ship of {@code shipId}, in the engine but not in the bubble: macro-sim puts it on
     * the rails, and the bubble builds its body from its layout when it comes in.
     */
    private Entity spawnShip(String shipId, int crewFaction) {
        var content = world.getContent();
        if (content == null || !content.defs.has("ship", shipId)) return null;
        Entity ship = content.factory.spawnShip(shipId, 0f, 0f, 0f);
        ship.remove(PhysicsBodyComponent.class);
        ShipConsoles.install(ship);
        CrewFactory.ensureBoard(ship).faction = crewFaction;
        ShipDef def = content.defs.get("ship", shipId, ShipDef.class);
        List<int[]> floor = GameScenario.floorTiles(ship.getComponent(ShipGridComponent.class));
        int crew = def == null ? 0 : Math.min(def.crew, floor.size());
        for (int i = 0; i < crew; i++) {
            int[] t = floor.get(i * floor.size() / crew);
            CrewFactory.spawn(world.crew, ship, "Crew " + (i + 1), CREW_ROLES[i % CREW_ROLES.length], crewFaction,
                    t[0], t[1]);
        }
        return ship;
    }

    /**
     * How heavily armed a ship is: its weapons' prices, point defense at half (it guards, it does not
     * hurt). The corvette rates about 147k, a raider 18k and a gunship 132k.
     */
    public static double firepower(Entity ship) {
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return 0;
        double sum = 0;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def == null) continue;
            sum += m.def.pointDefense ? m.def.price * 0.5 : m.def.price;
        }
        return sum;
    }

    /** What is left of a ship's hull: the mean integrity of its walls, 0-1. */
    static double hull(Entity ship) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return 1;
        double sum = 0;
        int n = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || !t.isWall()) continue;
                sum += t.getIntegrity();
                n++;
            }
        }
        return n == 0 ? 1 : sum / n;
    }

    /** The marker a ship in the bubble gets: a hostile's name and side, else a freighter's. */
    public static HudMarkerComponent markerFor(Entity ship) {
        return markerFor(ship, SPONSOR);
    }

    /** As {@link #markerFor(Entity)}, for a player serving the faction {@code sponsorId}: its patrols are friendly. */
    public static HudMarkerComponent markerFor(Entity ship, String sponsorId) {
        HostileComponent h = ship.getComponent(HostileComponent.class);
        if (h == null) return new HudMarkerComponent("Freighter", HudMarkerComponent.Kind.FRIENDLY).radius(20f);
        HudMarkerComponent.Kind kind = h.isPirate() ? HudMarkerComponent.Kind.HOSTILE
                : h.faction != null && h.faction.equals(sponsorId) ? HudMarkerComponent.Kind.FRIENDLY : HudMarkerComponent.Kind.NEUTRAL;
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        float radius = g == null ? 20f : Math.max(g.width, g.height) * g.tileSize * 0.5f;
        return new HudMarkerComponent(h.displayName, kind).radius(radius);
    }
}
