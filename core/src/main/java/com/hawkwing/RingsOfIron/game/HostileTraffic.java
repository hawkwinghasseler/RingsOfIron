package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.coreengine.EntityRemovalListener;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.orbital.warp.DropOutSource;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.StationCrew;
import com.ringsofiron.shipops.Tactics;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Supplier;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.orbital.warp.TimeWarpController;

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
 *       running. It breaks off below {@link #BREAK_OFF_HULL} of its hull (a plain pirate in reach only after
 *       {@link #BREAK_OFF_HOLD}), {@link #CROSSFIRE_BREAK_OFF_HULL}
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
        HostileTrafficController.Listener, SaveParticipant, Resettable {
    /** Key of this system's state in the save's modules section. */
    public static final String SAVE_KEY = "hostile-traffic";
    /** Seconds between looks at the bubble's hostiles. */
    static final float CHECK_SECONDS = 1f;
    /**
     * A hostile breaks off a fight below this share of its hull. Above {@link ShipDisablement#SURRENDER_HULL},
     * so a ship that is losing tries to get away first, and surrenders only if it is caught and hit again.
     */
    public static final double BREAK_OFF_HULL = 0.65;
    /**
     * A plain pirate in its guns' reach, or closing to it ({@link #BREAK_OFF_HOLD_CLOSING}), holds past
     * {@link #BREAK_OFF_HULL}, and doesn't run on the odds ({@link Tactics.Plan#RUN}), for this long after it
     * would first have turned away there, sim seconds (round 10: a lone raider worn
     * to the line by a frigate's railguns just as it reached its chaingun's 3 km turned away unfired).
     * Not under crossfire, and not an ace, which has its own line.
     */
    public static final double BREAK_OFF_HOLD = 10;
    /** Within this share of its shortest gun's reach a pirate is closing to it, for {@link #BREAK_OFF_HOLD}. */
    public static final double BREAK_OFF_HOLD_CLOSING = 1.25;
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
    /** The gunship hull: the patrols' cutters and Selene's guards fly it. */
    public static final String GUNSHIP = "gunship_heavy";
    /** The pirates' own gunship (round 5): the hull stripped down and rearmed by the Void Raiders. */
    public static final String PIRATE_GUNSHIP = "gunship_raider";
    /** The pirate base's own guns (round 6): a gun platform moored beside it, which never flies. */
    public static final String BATTERY = "den_battery";
    /** Faction ids, as the factions work names them: the player's sponsor, Selene, and the pirates. */
    public static final String SPONSOR = "patrol", SELENE = "selene", PIRATES = "pirates", TRADERS = "traders";
    /** Crew factions (ai-crew numbers) of each; the sponsor's is the stations' own. */
    static final int SPONSOR_CREW = StationCrew.FACTION, PIRATE_CREW = 3, SELENE_CREW = 4;

    private final GameContext ctx;
    private final CrewService crewService;
    private final MacroSimulation macroSim;
    private final TimeWarpController timeWarp;
    private final FactionStanding standing;
    private final CombatDefs combatDefs;
    private final CombatEvents combatEvents;
    private final ShipDisablement disablement;
    private final ContractBoard contracts;
    /** Built after hostile traffic, as are the base and the boarders: read when used. */
    private final Supplier<WorldEvents> events;
    private final Supplier<PirateBase> pirateBase;
    private final Supplier<PirateBoarding> pirateBoarding;
    private final HostileTrafficController controller;
    /** Pirates' refuelling on the rails (round 8). */
    private final PirateUpkeep upkeep;
    private final HostileTargeting targeting;
    private final HostileSpawns spawns;
    private final Vector2 here = new Vector2(), there = new Vector2();
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

    String[] lurks = new String[0];
    /** Each patrolling faction's route. */
    private final Map<String, String[]> routes = new HashMap<>();
    /** Patrols to spawn once the update is over. */
    private final List<String> reinforcements = new ArrayList<>();
    /** Patrols sent on routes of their own ({@link #patrol}), each its faction then its stations, spawned after the update. */
    private final List<String[]> sentPatrols = new ArrayList<>();
    private double nextTopUp = Double.POSITIVE_INFINITY;
    /** Sim second a pirate started an intercept on the player; NaN when there is no alert. */
    private double alertAt = Double.NaN;
    private float since = CHECK_SECONDS;
    /** Pirates that left the beaten player this check, for one notice. */
    private final List<String> drifting = new ArrayList<>();
    /** Sim second each hostile in the bubble last had something to do. */
    private final Map<String, Double> busySince = new HashMap<>();
    /** When each plain pirate first held past its break-off line in reach of its enemy ({@link #BREAK_OFF_HOLD}). */
    private final Map<String, Double> inReachSince = new HashMap<>();
    /** Sim seconds a pirate has held alongside its prey. */
    private final Map<String, Double> alongside = new HashMap<>();

    private HostileTraffic(GameContext ctx, CrewService crewService, MacroSimulation macroSim, TimeWarpController timeWarp,
                           FactionStanding standing, CombatDefs combatDefs, CombatEvents combatEvents,
                           ShipDisablement disablement, ContractBoard contracts, Supplier<WorldEvents> events,
                           Supplier<PirateBase> pirateBase, Supplier<PirateBoarding> pirateBoarding) {
        this.ctx = ctx;
        this.crewService = crewService;
        this.macroSim = macroSim;
        this.timeWarp = timeWarp;
        this.standing = standing;
        this.combatDefs = combatDefs;
        this.combatEvents = combatEvents;
        this.disablement = disablement;
        this.contracts = contracts;
        this.events = events;
        this.pirateBase = pirateBase;
        this.pirateBoarding = pirateBoarding;
        controller = macroSim.hostiles();
        upkeep = new PirateUpkeep(ctx.engine);
        targeting = new HostileTargeting(ctx, macroSim, standing, disablement);
        spawns = new HostileSpawns(this, ctx, crewService, macroSim, standing, combatDefs, contracts, events, controller);
        controller.addListener(this);
        controller.playerFirepower = () -> firepower(ctx.player());
        macroSim.bubble().addListener(this);
        timeWarp.addDropOutSource(this);
        timeWarp.changes.add((signal, change) -> alertAt = Double.NaN);
        combatEvents.shots.add((signal, shot) -> onShotAt(shot.target, shot.shooter));
        combatEvents.impacts.add((signal, hit) -> onShotAt(hit.target, hit.owner, ctx.engine.getSimSeconds() - hit.sinceFired,
                !hit.pointDefense && hit.aimedAt == hit.target));
        disablement.changes.add((signal, change) -> surrendering(change));
        ctx.engine.getEcsEngine().addEntityListener((EntityRemovalListener) targeting::forget);
        ctx.engine.getEcsEngine().addEntityListener(UNFLOWN, new EntityListener() {
            @Override
            public void entityAdded(Entity e) {
                letGo(e);
            }

            @Override
            public void entityRemoved(Entity e) {
            }
        });
        ctx.engine.getSerializationService().addParticipant(this);
    }

    /** Seconds after a surrender in which the player's fire doesn't yet count as firing on it again. */
    static final double SURRENDER_GRACE = 10;
    /**
     * Whether stray fire brings a prize back into the fight: a point-defense round, or a round or
     * missile aimed at another ship, that hits it after the grace. Round 9: false, so only fire aimed
     * at the prize itself does (in round 8's window the point defense, firing at the base's darts, hit
     * the struck raiders behind them).
     */
    static final boolean STRAY_FIRE_REVIVES = false;

    /**
     * A hostile that surrenders is a prize (round 6): it lets go of the player, and stays passive
     * until they fire on it again. In the round 6 real-window run a surrendered raider fought again
     * once its guns were mended, followed the player through a tow and hit the docked ship.
     */
    private void surrendering(ShipDisablement.Change change) {
        HostileComponent h = change.ship.getComponent(HostileComponent.class);
        if (h == null) return;
        if (change.now == DisabledComponent.Reason.SURRENDERED) {
            h.surrendered = true;
            if (h.huntingPlayer()) h.targetUuid = null;
            targeting.provoked.remove(change.ship);
            // The player's guns let go of it: a lock left on it made the trigger aimed fire after the
            // grace (round 9, part 6's window: Space held after the fall brought Pirate raider 22 back)
            PirateBoarding.standDown(ctx.player(), change.ship);
        } else if (change.was == DisabledComponent.Reason.SURRENDERED && change.now == null) {
            h.surrendered = false;
        }
    }

    /** Puts hostile traffic into {@code world}. */
    public static HostileTraffic install(GameWorld world) {
        HostileTraffic traffic = new HostileTraffic(world.context, world.crew, world.macroSim, world.timeWarp, world.standing,
                world.combatDefs, world.combatEvents, world.disablement, world.contracts, () -> world.events,
                () -> world.pirateBase, () -> world.pirateBoarding);
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
        double now = macroSim.now();
        controller.playerGraceUntil = now + PLAYER_GRACE;
        if (sponsorRoute.length > 0) spawns.spawnPatrol(SPONSOR, sponsorRoute);
        if (seleneRoute.length > 0) spawns.spawnPatrol(SELENE, seleneRoute);
        spawns.topUp(now, true);
        nextTopUp = now + TOP_UP_INTERVAL;
    }

    /** Points the lurks at a loaded game's stations, which have new uuids. */
    public void relink(String[] lurks) {
        this.lurks = lurks.clone();
        // A save from before this state was saved: top-ups start again from the load, and new
        // pirates are numbered on from the highest loaded
        if (nextTopUp == Double.POSITIVE_INFINITY && this.lurks.length > 0) {
            nextTopUp = macroSim.now() + TOP_UP_INTERVAL;
        }
        for (HostileComponent h : controller.all().values()) spawns.spawned = Math.max(spawns.spawned, number(h.displayName));
    }

    /** Forgets this system's own state, before a save is loaded over it. */
    @Override
    public void reset() {
        spawns.spawned = 0;
        nextTopUp = Double.POSITIVE_INFINITY;
        topUpDue = false;
        alertAt = Double.NaN;
        controller.playerGraceUntil = 0;
        targeting.provoked.clear();
        sentPatrols.clear();
        busySince.clear();
        inReachSince.clear();
        alongside.clear();
        upkeep.last = Double.NaN;
    }

    /** Rails time pirates' refuelling last ran at ({@link PirateUpkeep}); NaN before it first runs. */
    double upkeepClock() {
        return upkeep.last;
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

    /**
     * Sends faction {@code faction} a patrol flying {@code route}'s stations, once the engine update is
     * over: the Free Traders watching lanes where tension runs high ({@link FactionTension}), say.
     * False for an empty route.
     */
    public boolean patrol(String faction, String... route) {
        if (route == null || route.length == 0) return false;
        String[] entry = new String[route.length + 1];
        entry[0] = faction;
        System.arraycopy(route, 0, entry, 1, route.length);
        sentPatrols.add(entry);
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

    /** Raiders the population keeps at difficulty {@code d} ({@link Difficulty#raiders}). */
    public static int raidersFor(double d) {
        return Difficulty.raiders(d);
    }

    /** Gunships the population keeps at difficulty {@code d} ({@link Difficulty#gunships}). */
    public static int gunshipsFor(double d) {
        return Difficulty.gunships(d);
    }

    /** True for a pirate gunship's hull: the pirates' own, or (in a save from before round 5) the patrols'. */
    static boolean gunshipHull(String shipId) {
        return PIRATE_GUNSHIP.equals(shipId) || GUNSHIP.equals(shipId);
    }

    // ---------------------------------------------------------------------------------------------

    /** Runs a top-up {@link #update} found due, once the engine update is over; {@link GameWorld#update} calls it. */
    public void afterUpdate() {
        while (!reinforcements.isEmpty()) {
            String faction = reinforcements.remove(0);
            spawns.spawnPatrol(faction, routes.get(faction));
        }
        while (!sentPatrols.isEmpty()) {
            String[] entry = sentPatrols.remove(0);
            spawns.spawnPatrol(entry[0], java.util.Arrays.copyOfRange(entry, 1, entry.length));
        }
        if (!topUpDue) return;
        topUpDue = false;
        spawns.topUp(macroSim.now(), false);
    }

    @Override
    public void update(float deltaTime) {
        double now = macroSim.now();
        if (now >= nextTopUp) {
            // Spawned here, mid-update, a ship would not be in the engine (or on the rails) until the
            // update ends; the top-up waits for afterUpdate
            topUpDue = true;
            nextTopUp = now + TOP_UP_INTERVAL;
        }
        // Every frame, not on the checks: the checks' phase isn't saved (PirateUpkeep keeps its own steps)
        upkeep.update(controller.all(), now);
        since += deltaTime;
        if (since < CHECK_SECONDS) return;
        since = 0f;
        for (String id : macroSim.bubble().inBubble()) {
            Entity e = ctx.engine.getEntity(id);
            HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
            if (h == null || h.state != HostileComponent.State.IN_BUBBLE) continue;
            if (e.getComponent(PhysicsBodyComponent.class) == null) continue;
            if (h.ace && !h.partDropped && FirstPlay.beaten(ctx.engine, e)) aceBeaten(e, h);
            if (ShipDisablement.isDisabled(e)) {
                // Beaten or surrendered, it drifts where it is to be boarded: no flying off with a prize crew aboard
                disengage(e);
                e.remove(NpcPilotComponent.class);
                continue;
            }
            fly(id, e, h, now);
        }
        // A wing that beat the player leaves together: one line, not one per pirate
        if (!drifting.isEmpty()) {
            notices.add(and(drifting) + (drifting.size() == 1 ? " leaves" : " leave") + " you drifting");
            drifting.clear();
        }
    }

    /** "A", "A and B", "A, B and C". */
    private static String and(List<String> names) {
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private void fly(String id, Entity e, HostileComponent h, double now) {
        h.hull = hull(e);
        if (h.moored()) {
            // A gun platform holds its mooring and fights to the last: it has nowhere to run
            man(e, h);
            busySince.put(id, now);
            return;
        }
        boolean crossfire = targeting.crossfire(e, now);
        if (!h.brokeOff && h.hull < breakOffHull(h, crossfire) && !holdsToFire(id, e, h, crossfire, now)) {
            breakOff(id, h, now);
            notices.add(h.displayName + " is breaking off");
        }
        if (!h.brokeOff && h.isPirate() && targeting.leaderDone(h)) {
            if (pirateBoarding.get().boarder() == e) {
                // Alongside the beaten player it finishes the boarding before it follows its leader
                // home (round 7: a raid's raider told the player it was boarding, then just left)
                h.wingOf = null;
            } else if (targeting.stillComing(e, h) || pirateBase.get().defending(h.displayName)) {
                // Still on its run in, it came to fight: it fights on alone rather than turn for home
                // unfired. Nor does the base's defence leave it while it stands
                h.wingOf = null;
                notices.add(h.displayName + " fights on without its leader");
            } else {
                // A wing fights together and breaks off together
                breakOff(id, h, now);
                notices.add(h.displayName + " is breaking off with its leader");
            }
        }
        if (h.brokeOff) {
            leave(e);
            return;
        }
        Entity enemy = targeting.enemyOf(e, h);
        if (enemy != null && h.isPirate() && enemy == ctx.player() && ShipDisablement.isDisabled(enemy)
                && FirstPlay.beaten(ctx.engine, enemy)) {
            // Beaten: a pirate leaves the player drifting rather than shoot a crippled ship and its
            // wounded crew to pieces (round 6: the railgun ace went on firing for minutes). Dark for
            // an ion stun's few seconds is not beaten: the fight goes on (seed 2026's ion raider left).
            // A base's defender leaves as the winner, not driven off: the base does not strike
            if (pirateBase.get().defending(h.displayName)) pirateBase.get().raidLost();
            // Round 7: one in good shape comes alongside first and takes the cargo (PirateBoarding)
            if (pirateBoarding.get().board(id, e, h, enemy, CHECK_SECONDS)) {
                busySince.put(id, now);
                return;
            }
            breakOff(id, h, now);
            // At a berth the player isn't left drifting: it just goes (round 8)
            if (!pirateBoarding.get().atBerth(enemy)) drifting.add(h.displayName);
            disengage(e);
            leave(e);
            return;
        }
        if (enemy != null) {
            if (enemy == ctx.player()) h.foughtPlayer = true;
            // An ace never runs on the odds: it fights to its break-off line
            Tactics.Arms arms = Tactics.arms(e);
            boolean wingman = targeting.besideLeader(h, enemy);
            double odds = targeting.odds(id, e, h, enemy);
            Tactics.Plan plan = Tactics.choose(arms, odds, h.hull, h.isPirate() && !aceAtLarge(h),
                    crossfire, aceAtLarge(h), wingman, targeting.range(e, enemy) > arms.reach);
            // Nor does it run before it has had its hold in reach to fire (round 10)
            if (plan == Tactics.Plan.RUN && holdsToFire(id, e, h, crossfire, now)) {
                plan = Tactics.choose(arms, odds, h.hull, false, crossfire, false, wingman, false);
            }
            if (plan == Tactics.Plan.RUN) {
                breakOff(id, h, now);
                ctx.notices.post(h.displayName + " is running", NoticeLog.Kind.INFO);
                leave(e);
                return;
            }
            engage(e, enemy, true, Tactics.HOLD_RANGE);
            NpcCombatComponent combat = e.getComponent(NpcCombatComponent.class);
            Tactics.apply(combat, plan, arms);
            // A wingman circles the other way from its leader, so the two come at the enemy from both sides
            combat.side = wingman ? -1f : 1f;
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
     * A moored gun platform's guns: on whoever shot at it, else on the player once its station has
     * called it to action ({@link HostileComponent#huntingPlayer}); nothing flies it.
     */
    private void man(Entity e, HostileComponent h) {
        e.remove(NpcPilotComponent.class);
        e.remove(NpcCombatComponent.class);
        Entity enemy = targeting.provoked.get(e);
        if (enemy == null || enemy == e || enemy.getComponent(PhysicsBodyComponent.class) == null) {
            Entity player = ctx.player();
            enemy = h.huntingPlayer() && player != null && player.getComponent(PhysicsBodyComponent.class) != null ? player : null;
        }
        // Nor does a gun shoot a beaten ship to pieces (round 7: the den's batteries went on firing
        // darts at a frigate dead in space after its raiders had left it)
        if (enemy != null && enemy == ctx.player() && ShipDisablement.isDisabled(enemy)
                && FirstPlay.beaten(ctx.engine, enemy)) enemy = null;
        // A struck base's guns strike with it, provoked or not (round 8, real window: two of Rustwake's
        // batteries went on launching darts for a minute after it fell)
        if (pirateBase.get().silenced(h.displayName)) enemy = null;
        SensorComponent sensor = e.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = enemy;
    }

    /**
     * True while a plain pirate that would break off or run holds to fire instead ({@link #BREAK_OFF_HOLD}): in
     * or closing to its guns' reach of its enemy, not under crossfire, for the hold's time from the first such check.
     */
    private boolean holdsToFire(String id, Entity e, HostileComponent h, boolean crossfire, double now) {
        if (!h.isPirate() || aceAtLarge(h) || crossfire) return false;
        Entity enemy = targeting.enemyOf(e, h);
        if (enemy == null || !(targeting.range(e, enemy) <= Tactics.arms(e).reach * BREAK_OFF_HOLD_CLOSING)) return false;
        Double since = inReachSince.get(id);
        if (since == null) inReachSince.put(id, since = now);
        return now - since < BREAK_OFF_HOLD;
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

    /** Hostiles that neither pilot flies, and that still could: no player at the helm, not disabled. */
    static final Family UNFLOWN = Family.all(HostileComponent.class, FlightControlComponent.class)
            .exclude(NpcPilotComponent.class, NpcCombatComponent.class, DisabledComponent.class, PlayerControllerComponent.class).get();

    /**
     * Takes {@code e}'s pilot's hands off the stick: no thrust, and rotation assist on so it stops
     * turning. Each pilot sets the controls every tick but none clears them when it's taken off, so a
     * ship left {@link #UNFLOWN} (disengaged, done with its burns, or loaded from a save like that)
     * kept its last stick and turned faster and faster, to tens of turns a second.
     */
    static void letGo(Entity e) {
        FlightControlComponent controls = e.getComponent(FlightControlComponent.class);
        if (controls == null) return;
        controls.clear();
        controls.rotationAssist = true;
    }

    /** A beaten ace: a spare of its best gun crated aboard, and the news. */
    private void aceBeaten(Entity e, HostileComponent h) {
        String part = PirateAces.dropPart(e, h, combatDefs);
        WeaponDef def = part == null ? null : combatDefs.weapons.get(part);
        ctx.notices.post("The ace " + h.displayName + " is beaten"
                + (def == null ? "" : ": a spare " + def.displayName() + " is crated in its hold"), NoticeLog.Kind.IMPORTANT);
    }

    /** A pirate waiting for its freighter: closes on it once it is here, and takes its cargo. */
    private void stalk(String id, Entity e, HostileComponent h, double now) {
        Entity prey = ctx.engine.getEntity(h.targetUuid);
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
        // Box2D works in its own units: the range is in meters
        if (PhysicsManager.getPosition(body, here).dst(PhysicsManager.getPosition(pb.body, there)) > PLUNDER_RANGE) {
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

    /**
     * Flies off out of the bubble; macro-sim takes it back at the edge. Its guns stay on whatever it
     * was fighting as it goes (round 5: a raider that ran used to go quiet).
     */
    private static void leave(Entity e) {
        SensorComponent sensor = e.getComponent(SensorComponent.class);
        Entity parting = sensor == null ? null : sensor.lockedTarget;
        disengage(e);
        if (parting != null && parting.getComponent(PhysicsBodyComponent.class) != null) sensor.lockedTarget = parting;
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
        onShotAt(victim, attacker, ctx.engine.getSimSeconds());
    }

    /** As {@link #onShotAt(Entity, Entity)}, for a shot fired at sim second {@code firedAt}. */
    void onShotAt(Entity victim, Entity attacker, double firedAt) {
        onShotAt(victim, attacker, firedAt, true);
    }

    /**
     * As {@link #onShotAt(Entity, Entity, double)}; {@code aimed} is false for stray fire (a
     * point-defense round, or one aimed at another ship), which doesn't bring a prize back.
     */
    void onShotAt(Entity victim, Entity attacker, double firedAt, boolean aimed) {
        targeting.onShotAt(victim, attacker, firedAt, aimed);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
        Entity e = ctx.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        if (h == null) return;
        busySince.put(uuid, t);
        // Its loadout again, in case its weapons came back from a save as its ship's stock ones
        LoadoutDef loadout = h.loadout == null ? null : combatDefs.loadouts.get(h.loadout);
        if (loadout != null) Loadouts.apply(e, loadout, combatDefs);
        // Come to fight the player: guns on them from the start
        Entity player = ctx.player();
        if (h.surrendered) {
            // Still a prize, whatever came back with it from the rails or a save
            if (ShipDisablement.reason(e) != DisabledComponent.Reason.SURRENDERED) disablement.set(e, DisabledComponent.Reason.SURRENDERED);
            disengage(e);
        } else if (h.moored()) man(e, h);
        else if (h.huntingPlayer() && player != null) engage(e, player, true, 1_500f);
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        Entity e = ctx.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        if (h == null) return;
        boolean fought = h.foughtPlayer;
        h.foughtPlayer = false;
        if (fought && h.isPirate() && !h.moored() && !FirstPlay.beaten(ctx.engine, e)) escaped(e, h);
        disengage(e);
        // Rebuilt from its layout next time, never from a stale shape
        e.remove(BodyShapeComponent.class);
        targeting.provoked.remove(e);
        targeting.attackers.remove(e);
        busySince.remove(uuid);
        inReachSince.remove(uuid);
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
        Entity e = ctx.engine.getEntity(hostile);
        // Round 8: it fills up from its prey's reaction mass before anything else
        if (e != null) PirateUpkeep.robbed(e, ctx.engine.getEntity(freighter));
        if (h == null || e == null || h.ace || h.plunders < PirateAces.PLUNDERS_TO_ACE) return;
        String was = h.displayName;
        if (PirateAces.promote(ctx.engine, combatDefs, () -> events.get().seed, e, h, controller.all().values(), difficulty.getAsDouble(), false)) {
            ctx.notices.post(String.format("%s has robbed %d freighters: they call it %s now, an ace", was, h.plunders,
                    h.displayName), NoticeLog.Kind.INFO);
            PirateAces.postBounty(ctx.engine, macroSim, standing, contracts, hostile);
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
        if (PirateAces.promote(ctx.engine, combatDefs, () -> events.get().seed, e, h, controller.all().values(), difficulty.getAsDouble(), true)) {
            ctx.notices.post(was + " got away from you: it flies as the ace " + h.displayName
                    + " now, and it will come looking for you", NoticeLog.Kind.WARNING);
            PirateAces.postBounty(ctx.engine, macroSim, standing, contracts, e.getComponent(UuidComponent.class).id);
        }
    }

    @Override
    public double nextDropOutTime() {
        return Double.isNaN(alertAt) ? Double.NaN : ctx.engine.getSimSeconds();
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public String saveKey() {
        return SAVE_KEY;
    }

    @Override
    public void write(Json json) {
        json.writeValue("spawned", spawns.spawned);
        // JSON has no infinity or NaN: a missing value means none
        if (nextTopUp != Double.POSITIVE_INFINITY) json.writeValue("nextTopUp", nextTopUp);
        if (topUpDue) json.writeValue("topUpDue", true);
        if (!Double.isNaN(alertAt)) json.writeValue("alertAt", alertAt);
        json.writeValue("playerGraceUntil", controller.playerGraceUntil);
        writeTimes(json, "busySince", busySince);
        writeTimes(json, "alongside", alongside);
        writeTimes(json, "inReachSince", inReachSince);
        if (!Double.isNaN(upkeep.last)) json.writeValue("upkeepAt", upkeep.last);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        spawns.spawned = data.getInt("spawned", 0);
        nextTopUp = data.getDouble("nextTopUp", Double.POSITIVE_INFINITY);
        topUpDue = data.getBoolean("topUpDue", false);
        alertAt = data.getDouble("alertAt", Double.NaN);
        controller.playerGraceUntil = data.getDouble("playerGraceUntil", 0);
        readTimes(data.get("busySince"), busySince);
        readTimes(data.get("alongside"), alongside);
        readTimes(data.get("inReachSince"), inReachSince);
        upkeep.last = data.getDouble("upkeepAt", Double.NaN);
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

    /** When {@code id} began to hold to fire ({@link #BREAK_OFF_HOLD}), or null; for tests. */
    Double holdingSince(String id) {
        return inReachSince.get(id);
    }

    /** Tops the population up now, out of the player's sight, as the hourly top-up does; for tests. */
    void topUpNow() {
        spawns.topUp(macroSim.now(), false);
    }

    /** The dice the {@code n}th spawn's loadout is drawn with ({@link HostileSpawns#loadoutDice}). */
    Random loadoutDice(String shipId, int n) {
        return spawns.loadoutDice(shipId, n);
    }

    /**
     * Raiders come to work {@code station}'s lanes: {@code count} more pirates of {@link #RAIDER}
     * lurking by it, on top of the usual population ({@link WorldEvents}' raid). Call outside the
     * engine's update. Returns their uuids.
     */
    public List<String> raid(String station, int count) {
        return spawns.raid(station, count);
    }

    /**
     * {@code count} gun platforms of {@link #BATTERY} moored at {@code station} (the pirate base's guns),
     * each {@code spacing} m further behind it along its orbit, armed from their loadouts like a spawn.
     * Call outside the engine's update. Returns their uuids.
     */
    public List<String> moorGuns(String station, int count, double spacing) {
        return spawns.moorGuns(station, count, spacing);
    }

    /**
     * A wing of {@code size} pirates of {@code shipId} lurking by {@code lurk}: the first leads and the
     * rest fly on its wing. Each is armed with {@code loadoutId}, or as the difficulty picks when null.
     * Call outside the engine's update. Returns their uuids, the leader first.
     */
    public List<String> spawnWing(String shipId, int size, String lurk, String loadoutId) {
        return spawns.spawnWing(shipId, size, lurk, loadoutId);
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
