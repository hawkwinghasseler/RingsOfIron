package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>Either breaks off below {@link #BREAK_OFF_HULL} of its hull, and leaves when it has had nothing
 *       to do for {@link #IDLE_LIMIT}.</li>
 * </ul>
 * A pirate planning an intercept on the player stops time warp ({@link DropOutSource}) and says so in
 * {@link #notices}.
 */
public class HostileTraffic extends EntitySystem implements BubbleTransitionListener, DropOutSource,
        HostileTrafficController.Listener {
    /** Seconds between looks at the bubble's hostiles. */
    static final float CHECK_SECONDS = 1f;
    /** A hostile breaks off a fight below this share of its hull. */
    public static final double BREAK_OFF_HULL = 0.5;
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
    /** What the hostiles did that the player should hear of; the screen drains it. */
    public final List<String> notices = new ArrayList<>();

    private String[] lurks = new String[0];
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
        double now = world.macroSim.now();
        controller.playerGraceUntil = now + PLAYER_GRACE;
        if (sponsorRoute.length > 0) spawnPatrol(SPONSOR, sponsorRoute);
        if (seleneRoute.length > 0) spawnPatrol(SELENE, seleneRoute);
        topUp(now, true);
        nextTopUp = now + TOP_UP_INTERVAL;
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

    @Override
    public void update(float deltaTime) {
        double now = world.macroSim.now();
        if (now >= nextTopUp) {
            topUp(now, false);
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
            fly(id, e, h, now);
        }
    }

    private void fly(String id, Entity e, HostileComponent h, double now) {
        h.hull = hull(e);
        if (!h.brokeOff && h.hull < BREAK_OFF_HULL) {
            breakOff(id, h, now);
            notices.add(h.displayName + " is breaking off");
        }
        if (h.brokeOff) {
            leave(e);
            return;
        }
        Entity enemy = enemyOf(e, h);
        if (enemy != null) {
            engage(e, enemy, true, 1_500f);
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

    /** Who the hostile fights in the bubble now, or null. */
    private Entity enemyOf(Entity e, HostileComponent h) {
        Entity player = world.getPlayer();
        Entity attacker = provoked.get(e);
        if (attacker != null && attacker.getComponent(PhysicsBodyComponent.class) != null && attacker != e) return attacker;
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
        if (victim.getComponent(HostileComponent.class) != null) provoked.put(victim, attacker);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
        Entity e = world.engine.getEntity(uuid);
        HostileComponent h = e == null ? null : e.getComponent(HostileComponent.class);
        if (h == null) return;
        busySince.put(uuid, t);
        // Come to fight the player: guns on them from the start
        Entity player = world.getPlayer();
        if (h.huntingPlayer() && player != null) engage(e, player, true, 1_500f);
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        Entity e = world.engine.getEntity(uuid);
        if (e == null || e.getComponent(HostileComponent.class) == null) return;
        disengage(e);
        // Rebuilt from its layout next time, never from a stale shape
        e.remove(BodyShapeComponent.class);
        provoked.remove(e);
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
    }

    @Override
    public double nextDropOutTime() {
        return Double.isNaN(alertAt) ? Double.NaN : world.engine.getSimSeconds();
    }

    // ---------------------------------------------------------------------------------------------

    /** Spawns pirates until the population is what {@link #difficulty} asks for. */
    private void topUp(double now, boolean anywhere) {
        if (lurks.length == 0) return;
        double d = difficulty.getAsDouble();
        int raiders = raidersFor(d) - count(RAIDER), gunships = gunshipsFor(d) - count(GUNSHIP);
        for (int i = 0; i < gunships; i++) spawnPirate(GUNSHIP, now, anywhere);
        for (int i = 0; i < raiders; i++) spawnPirate(RAIDER, now, anywhere);
    }

    private int count(String shipId) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && shipId.equals(h.shipId)) n++;
        return n;
    }

    private void spawnPirate(String shipId, double now, boolean anywhere) {
        for (int k = 0; k < lurks.length; k++) {
            String lurk = lurks[(spawned + k) % lurks.length];
            if (!anywhere && nearPlayer(lurk, now)) continue;
            Entity ship = spawnShip(shipId, PIRATE_CREW);
            if (ship == null) return;
            String name = (GUNSHIP.equals(shipId) ? "Pirate gunship " : "Pirate raider ") + (++spawned);
            HostileComponent h = new HostileComponent(name, shipId, PIRATES, HostileComponent.Role.PIRATE,
                    firepower(ship));
            world.macroSim.addPirate(ship.getComponent(UuidComponent.class).id, h, lurk);
            return;
        }
    }

    private void spawnPatrol(String faction, String[] route) {
        Entity ship = spawnShip(GUNSHIP, SPONSOR.equals(faction) ? SPONSOR_CREW : SELENE_CREW);
        if (ship == null) return;
        String name = (SPONSOR.equals(faction) ? "Patrol cutter " : "Selene guard ") + (++spawned);
        HostileComponent h = new HostileComponent(name, GUNSHIP, faction, HostileComponent.Role.PATROL,
                firepower(ship));
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
        HostileComponent h = ship.getComponent(HostileComponent.class);
        if (h == null) return new HudMarkerComponent("Freighter", HudMarkerComponent.Kind.FRIENDLY).radius(20f);
        HudMarkerComponent.Kind kind = h.isPirate() ? HudMarkerComponent.Kind.HOSTILE
                : SPONSOR.equals(h.faction) ? HudMarkerComponent.Kind.FRIENDLY : HudMarkerComponent.Kind.NEUTRAL;
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        float radius = g == null ? 20f : Math.max(g.width, g.height) * g.tileSize * 0.5f;
        return new HudMarkerComponent(h.displayName, kind).radius(radius);
    }
}
