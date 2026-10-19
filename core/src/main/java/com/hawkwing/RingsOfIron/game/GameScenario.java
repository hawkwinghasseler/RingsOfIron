package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.OrbitMath;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;
import java.util.List;

/**
 * The starting situation of a new game: the player's corvette in low orbit around Earth with a crew
 * aboard, a raider close by, and a busy orbit. A station shares the player's orbit 8 km ahead with a
 * freighter docked at it, which macro-sim brings into the bubble; more traffic hauls between it and a
 * higher depot. The Moon orbits Earth with an ice mine around it that supplies Earth's stations with
 * water. Headless, so tests can start the same game.
 *
 * <p>Every station has a walkable interior from its type's blueprint and docking ports of all three
 * sizes. Highport is turned so its Small ports face the player coming up from behind.
 */
public class GameScenario {
    public static final String EARTH = "earth";
    public static final double MU_EARTH = 3.986004418e14;
    public static final double R_EARTH = 6.371e6;
    public static final double ORBIT_RADIUS = R_EARTH + 400e3;
    public static final String MOON = "moon";
    public static final double MU_MOON = 4.9048695e12;
    public static final double R_MOON = 1.7374e6;
    public static final double MOON_DISTANCE = 3.844e8;
    /** Where the Moon starts along its orbit, radians from Earth's +x axis. */
    public static final double MOON_ANGLE = 2.0;
    public static final String PLAYER_SHIP = "corvette_patrol";
    /** The ship the traffic flies, which sizes the freighters' holds. */
    public static final String FREIGHTER_SHIP = "freighter_hauler";
    /** The faction the raider flies for, when the data has it. */
    public static final String PIRATES = "pirates";
    /** What the player starts with for trading at station markets. Placeholder value. */
    public static final double STARTING_CREDITS = 5_000;

    public final GameWorld world;
    public final ModContext content;
    /** The player's ship and avatar; a load replaces them with the loaded entities. */
    public Entity player;
    public Entity avatar;
    /** A hostile corvette close by, armed like the player's ship; also replaced by a load. */
    public Entity raider;
    /** Ships the player can lock: hostiles and traffic in the bubble. */
    public final Array<Entity> contacts = new Array<>();
    public final String highport;
    public final String depot;
    /** The ice mine in low lunar orbit. */
    public final String lunarMine;
    /** Highport's attitude: half a turn, so its Small ports face back along the orbit, towards the player. */
    public static final float HIGHPORT_ANGLE = (float) Math.PI;

    private GameScenario(GameWorld world, Path assetsDir, Path modsDir) {
        this.world = world;
        content = world.loadContent(assetsDir, modsDir);

        Entity earth = world.engine.createEntity();
        earth.add(new UuidComponent(EARTH));
        earth.add(new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        world.engine.addEntity(earth);
        world.macroSim.registerMassiveBody(EARTH);
        Entity moon = world.engine.createEntity();
        moon.add(new UuidComponent(MOON));
        moon.add(new CelestialBodyComponent("Moon", MU_MOON, R_MOON, OrbitMath.soiRadius(MOON_DISTANCE, MU_MOON, MU_EARTH)));
        moon.add(new OrbitComponent(EARTH, KeplerOrbit.circular(MU_EARTH, MOON_DISTANCE, MOON_ANGLE, 0, 1)));
        world.engine.addEntity(moon);
        world.macroSim.registerMassiveBody(MOON);
        world.setAnchor(EARTH, KeplerOrbit.circular(MU_EARTH, ORBIT_RADIUS, 0, world.engine.getSimSeconds(), 1));

        player = content.factory.spawnShip(PLAYER_SHIP, 0f, 0f, 90f);
        player.add(new PlayerControllerComponent());
        ShipConsoles.install(player);
        // Trades go in and out of the corvette's cargo bays
        player.add(new TraderComponent(STARTING_CREDITS, 0));
        ShipCargo.attach(player);
        FlightControlComponent controls = player.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;

        ShipGridComponent grid = player.getComponent(ShipGridComponent.class);
        List<int[]> floor = floorTiles(grid);
        avatar = floor.isEmpty() ? null
                : CrewFactory.spawnPlayer(world.crew, player, "You", floor.get(0)[0], floor.get(0)[1]);
        CrewRole[] roles = {CrewRole.ENGINEER, CrewRole.MEDIC, CrewRole.MARINE};
        String[] names = {"Ada", "Bea", "Cole"};
        for (int i = 0; i < roles.length && i + 1 < floor.size(); i++) {
            int[] t = floor.get((i + 1) * floor.size() / (roles.length + 1));
            CrewFactory.spawn(world.crew, player, names[i], roles[i], 0, t[0], t[1]);
        }

        raider = content.factory.spawnShip(PLAYER_SHIP, 1_200f, 400f, 200f);
        ShipConsoles.install(raider);
        // Flies for the pirates, if the data has them, so beating it counts with the factions
        int pirates = world.standing.factions().index(PIRATES);
        if (pirates >= 0) CrewFactory.ensureBoard(raider).faction = pirates;
        raider.add(new HudMarkerComponent("Raider", HudMarkerComponent.Kind.HOSTILE).radius(10f));
        PhysicsBodyComponent rb = raider.getComponent(PhysicsBodyComponent.class);
        if (rb != null) PhysicsManager.setVelocity(rb.body, -12f, -3f);
        contacts.add(raider);
        // The raider's point defense is always up; it opens fire once the player shoots at it.
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.shooter == player && shot.target == raider) provokeRaider();
        });
        world.combatEvents.impacts.add((signal, hit) -> {
            if (hit.owner == player && hit.target == raider) provokeRaider();
        });

        // Traffic: shown and lockable while it is in the bubble.
        world.macroSim.bubble().addListener(new BubbleTransitionListener() {
            @Override
            public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
                Entity ship = world.engine.getEntity(uuid);
                if (ship == null) return;
                if (ship.getComponent(TransformComponent.class) == null) ship.add(new TransformComponent());
                ship.add(new HudMarkerComponent("Freighter", HudMarkerComponent.Kind.FRIENDLY).radius(20f));
                contacts.add(ship);
            }

            @Override
            public void onExitBubble(String uuid, double t) {
                Entity ship = world.engine.getEntity(uuid);
                if (ship == null) return;
                ship.remove(HudMarkerComponent.class);
                contacts.removeValue(ship, true);
            }
        });
        // A freighter at a station when it comes into the bubble is latched to a port there.
        world.macroSim.bubble().addListener(new StationTraffic(world));
        // Stations are marked while they are in the bubble.
        world.engine.getEcsEngine().addEntityListener(
                Family.all(StationComponent.class, KinematicTransientComponent.class).get(), new EntityListener() {
                    @Override
                    public void entityAdded(Entity station) {
                        if (station.getComponent(TransformComponent.class) == null) station.add(new TransformComponent());
                        ShipGridComponent g = station.getComponent(ShipGridComponent.class);
                        float radius = g == null ? 20f : Math.max(g.width, g.height) * g.tileSize * 0.5f;
                        station.add(new HudMarkerComponent(station.getComponent(StationComponent.class).name,
                                HudMarkerComponent.Kind.STATION).radius(radius));
                    }

                    @Override
                    public void entityRemoved(Entity station) {
                        station.remove(HudMarkerComponent.class);
                    }
                });
        // Water is mined only at the Moon; both Earth stations need it.
        highport = addStation("Highport", stationType("agri_dome", 0), EARTH,
                circular(ORBIT_RADIUS, 8e3 / ORBIT_RADIUS), HIGHPORT_ANGLE);
        depot = addStation("Deepwell Depot", stationType("fuel_depot", 1), EARTH,
                circular(ORBIT_RADIUS + 2_000e3, 2.5), 0f);
        lunarMine = addStation("Shackleton Ice", stationType("ice_mine", 2), MOON,
                circular(MU_MOON, R_MOON + 100e3, 0), 0f);
        double hold = InteriorGridData.cargoBayVolume(content.defs, FREIGHTER_SHIP);
        world.macroSim.addFreighter("Hauler Kestrel", hold, 20_000, highport);
        for (int i = 0; i < 4; i++) world.macroSim.addFreighter("Hauler " + (i + 1), hold, 20_000, depot);
        for (int i = 0; i < 2; i++) world.macroSim.addFreighter("Lunar Hauler " + (i + 1), hold, 20_000, lunarMine);
        content.hooks.fireWorldReady();
    }

    /** A macro-sim station with its interior and docking ports from its type's blueprint. */
    private String addStation(String name, String typeId, String parentUuid, StateVector state, float angle) {
        String uuid = world.macroSim.addStation(name, typeId, parentUuid, state, 1);
        StationComponent station = DockingData.buildStation(world.engine.getEntity(uuid), content.defs,
                world.engine, name, typeId);
        station.angle = angle;
        StationCrew.spawnCrew(world.crew, world.engine.getEntity(uuid), world.standing.factions().ofStation(name));
        return uuid;
    }

    /** Loads base data and mods and sets up a new game in {@code world}. */
    public static GameScenario start(GameWorld world, Path assetsDir, Path modsDir) {
        return new GameScenario(world, assetsDir, modsDir);
    }

    /**
     * Turns the raider on the player: its turrets and launcher engage the player's ship, and
     * ai-crew's combat pilot closes to fighting range, circles and dodges missiles.
     */
    public void provokeRaider() {
        SensorComponent sensor = raider.getComponent(SensorComponent.class);
        if (sensor != null && sensor.lockedTarget == null) sensor.lockedTarget = player;
        if (raider.getComponent(NpcCombatComponent.class) == null) raider.add(new NpcCombatComponent(player));
    }

    /** True once the raider is fighting back. */
    public boolean raiderHostile() {
        SensorComponent sensor = raider.getComponent(SensorComponent.class);
        return sensor != null && sensor.lockedTarget == player;
    }

    /** Next contact after {@code current} (wrapping), or null when there are none. */
    /**
     * Saves the whole game to {@code file}. Refused (returns a reason) during rails warp, when the
     * bubble is collapsed onto the rails; null when saved.
     */
    public String quickSave(FileHandle file) {
        if (world.timeWarp.isRailsWarp()) return "drop out of time warp first";
        world.engine.getSerializationService().saveToFile(file);
        return null;
    }

    /**
     * Replaces the game with the one saved in {@code file}, then points {@link #player},
     * {@link #avatar}, {@link #raider} and {@link #contacts} at the loaded entities.
     */
    public void quickLoad(FileHandle file) {
        // A save from before the career was saved loads as a new one: rank 0 with the sponsor
        world.standing.reset();
        // ...and with no contracts
        world.contracts.reset();
        world.engine.getSerializationService().loadFromFile(file);
        relink();
        world.standing.migrateLegacy(player);
    }

    private void relink() {
        player = world.getPlayer();
        if (player != null) ShipCargo.attach(player);
        avatar = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(PlayerAvatarComponent.class).get())) {
            avatar = e;
        }
        contacts.clear();
        raider = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(HudMarkerComponent.class).get())) {
            if (e == player || e.getComponent(StationComponent.class) != null) continue;
            contacts.add(e);
            if (e.getComponent(HudMarkerComponent.class).kind == HudMarkerComponent.Kind.HOSTILE) raider = e;
        }
    }

    public Entity nextContact(Entity current) {
        if (contacts.size == 0) return null;
        int i = current == null ? -1 : contacts.indexOf(current, true);
        return contacts.get((i + 1) % contacts.size);
    }

    /** The station type with this id from the economy data, or the n-th one if the data has no such type. */
    private String stationType(String id, int fallback) {
        var all = world.macroSim.economy().catalog().stationTypes();
        if (all.isEmpty()) throw new IllegalStateException("No station types in the economy data");
        String nth = null;
        int i = 0;
        for (var t : all) {
            if (t.id.equals(id)) return id;
            if (i++ <= fallback) nth = t.id;
        }
        return nth;
    }

    static StateVector circular(double radius, double angle) {
        return circular(MU_EARTH, radius, angle);
    }

    static StateVector circular(double mu, double radius, double angle) {
        var s = KeplerOrbit.circular(mu, radius, angle, 0, 1).stateAt(0);
        return new StateVector().set(s.x, s.y, s.vx, s.vy);
    }

    /** Open deck tiles clear of mounted systems, scanning from the middle row outwards. */
    static List<int[]> floorTiles(ShipGridComponent grid) {
        List<int[]> out = new java.util.ArrayList<>();
        if (grid == null) return out;
        for (int dy = 0; dy < grid.height; dy++) {
            int y = grid.height / 2 + ((dy % 2 == 0) ? dy / 2 : -(dy / 2 + 1));
            if (y < 0 || y >= grid.height) continue;
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor() && t.isOpen() && t.mount == null) out.add(new int[]{x, y});
            }
        }
        return out;
    }
}
