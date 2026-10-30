package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
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
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.shipops.ShipCargo;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.StationCrew;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;
import java.util.List;

/**
 * The starting situation of a new game: the player's corvette in low orbit around Earth with a crew
 * aboard, a raider close by, and a busy orbit. A station shares the player's orbit 8 km ahead with a
 * freighter docked at it, which macro-sim brings into the bubble; more traffic hauls between it and a
 * higher depot. The Moon orbits Earth with an ice mine around it that supplies Earth's stations with
 * water. Further out are the sponsor's shipyard in high Earth orbit, Selene's foundry and ore mine
 * round the Moon, and the Free Traders' outpost half way to the Moon. Headless, so tests can start
 * the same game.
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
    /**
     * The raider outside Highport: the player's corvette refitted by pirates, a chaingun where the
     * railgun was. As the player's twin it won or lost on who fired first (round 2 playtest).
     */
    public static final String RAIDER_SHIP = "corvette_raider";
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
    /** A hostile corvette close by ({@link #RAIDER_SHIP}), outgunned by the player's; also replaced by a load. */
    public Entity raider;
    /** Ships the player can lock: hostiles and traffic in the bubble. */
    public final Array<Entity> contacts = new Array<>();
    /** The stations' uuids; a load replaces them with the loaded stations'. */
    public String highport;
    public String depot;
    /** The ice mine in low lunar orbit. */
    public String lunarMine;
    /** The sponsor's shipyard in high Earth orbit; null in a save from before it was built. */
    public String shipyard;
    /** Selene's foundry and ore mine, in higher lunar orbits; null in older saves. */
    public String foundry;
    public String oreMine;
    /** The Free Traders' outpost, far out in Earth orbit; null in older saves. */
    public String freehold;
    public static final String SHIPYARD_NAME = "Anvil Yards", FOUNDRY_NAME = "Selene Forge",
            ORE_MINE_NAME = "Crisium Ore Works", FREEHOLD_NAME = "Freehold";
    /** The pirates' hidden base; null in older saves. */
    public String pirateBase;
    public static final String PIRATE_BASE_NAME = "Rustwake";
    /** The base's eccentric orbit round Earth: periapsis and apoapsis radii, m. */
    public static final double PIRATE_BASE_PERIAPSIS = 60_000e3, PIRATE_BASE_APOAPSIS = 160_000e3;
    /** The shipyard's orbit radius: high Earth orbit, m. */
    public static final double SHIPYARD_ORBIT = 20_000e3;
    /**
     * Freehold's orbit radius, m: half way to the Moon. Not on the Moon's own orbit, where a station
     * would never drift into a transfer window with it (the freighters' planner waited forever).
     */
    public static final double FREEHOLD_ORBIT = 200_000e3;
    /** How far the home station starts ahead of the player along their shared orbit, m. */
    static final double HOME_LEAD = 8e3;
    public static final String LUNAR_MINE_NAME = "Shackleton Ice";
    /** The name of the station the career starts docked at: the sponsor's home. */
    private String homeName = "Highport";
    /** Highport's attitude: half a turn, so its Small ports face back along the orbit, towards the player. */
    public static final float HIGHPORT_ANGLE = (float) Math.PI;

    /** The seed and sponsor this career started with; a load replaces it with the save's. */
    private NewGame newGame;
    /** The world's own dice, from the seed: station phases, traffic and stock. */
    private final java.util.Random dice;

    /**
     * Earth, and the Moon on its orbit round it, into {@code world}'s rails and macro-sim: the game's
     * bodies, for a new game and for any scene that wants the same sky.
     */
    public static void addEarthAndMoon(GameWorld world) {
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
    }

    private GameScenario(GameWorld world, Path assetsDir, Path modsDir, NewGame newGame) {
        this.world = world;
        this.newGame = newGame;
        // A seeded world's stations and ships get the same UUIDs every time, so whatever is keyed or
        // rolled by UUID replays too. So do the designed start's, so every test and First Play
        // replays, unless whoever built the world seeded them already
        var ids = world.engine.getEntityIds();
        if (newGame.seeded) ids.seed(newGame.stream(7));
        else if (!ids.isSeeded()) ids.seed(NewGame.DESIGNED_IDS);
        dice = new java.util.Random(newGame.stream(3));
        content = world.loadContent(assetsDir, modsDir);
        // The designed start keeps whatever dice the world was built with
        if (newGame.seeded) world.reseed(newGame.combatSeed(), newGame.crewSeed());
        // ...and the world's events roll from it too
        if (newGame.seeded) world.events.seed = newGame.stream(6);
        world.engine.getSerializationService().addParticipant(new SeedRecord());

        addEarthAndMoon(world);
        // The career serves the sponsor the new game chose, and starts beside that sponsor's home
        world.standing.serve(newGame.sponsor);
        var sponsor = world.standing.factions().def(world.standing.sponsor());
        homeName = sponsor == null || sponsor.home == null ? "Highport" : sponsor.home;
        // Shackleton Ice's place is drawn first, so a career starting there can be put beside it
        double lunarPhase = phase(0);
        if (LUNAR_MINE_NAME.equals(homeName)) {
            double r = R_MOON + 100e3;
            world.setAnchor(MOON, KeplerOrbit.circular(MU_MOON, r, lunarPhase - HOME_LEAD / r, world.engine.getSimSeconds(), 1));
        } else {
            world.setAnchor(EARTH, KeplerOrbit.circular(MU_EARTH, ORBIT_RADIUS, 0, world.engine.getSimSeconds(), 1));
        }

        player = content.factory.spawnShip(PLAYER_SHIP, 0f, 0f, 90f);
        player.add(new PlayerControllerComponent());
        ShipConsoles.install(player);
        // Trades go in and out of the corvette's cargo bays
        player.add(new TraderComponent(STARTING_CREDITS, 0));
        ShipCargo.attach(player);
        FlightControlComponent controls = player.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;

        Bridge.install(player);

        ShipGridComponent grid = player.getComponent(ShipGridComponent.class);
        List<int[]> floor = FloorTiles.of(grid);
        // The player starts seated at the helm, so the game opens on the ship from outside
        int[] seat = besideHelm(player, floor);
        avatar = seat == null ? null : CrewFactory.spawnPlayer(world.crew, player, "You", seat[0], seat[1]);
        if (avatar != null) world.bridge.sit(SubsystemComponent.SubsystemType.HELM);
        // ... and nobody else starts in their seat
        if (seat != null && floor.size() > 1) floor.remove(seat);
        // Bea is the helmsman: she takes the helm whenever the player leaves it
        CrewRole[] roles = {CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE};
        String[] names = {"Ada", "Bea", "Cole"};
        for (int i = 0; i < roles.length && i + 1 < floor.size(); i++) {
            int[] t = floor.get((i + 1) * floor.size() / (roles.length + 1));
            CrewFactory.spawn(world.crew, player, names[i], roles[i], 0, t[0], t[1]);
        }

        raider = content.factory.spawnShip(content.defs.has("ship", RAIDER_SHIP) ? RAIDER_SHIP : PLAYER_SHIP, 1_200f, 400f, 200f);
        ShipConsoles.install(raider);
        // Flies for the pirates, if the data has them, so beating it counts with the factions
        int pirates = world.standing.factions().index(PIRATES);
        if (pirates >= 0) CrewFactory.ensureBoard(raider).faction = pirates;
        world.standing.factions().crewHabits(world.crew.board(raider));
        raider.add(new HudMarkerComponent("Raider", HudMarkerComponent.Kind.HOSTILE).radius(10f));
        PhysicsBodyComponent rb = raider.getComponent(PhysicsBodyComponent.class);
        if (rb != null) PhysicsManager.setVelocity(rb.body, -12f, -3f);
        contacts.add(raider);
        // The raider's point defense is always up; it opens fire once the player shoots at it.
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.shooter == player && shot.target != null && shot.target == raider) provokeRaider();
        });
        world.combatEvents.impacts.add((signal, hit) -> {
            if (hit.owner == player && hit.target != null && hit.target == raider) provokeRaider();
        });
        // Gone, the raider is forgotten: the engine pools its entities, and a later ship given the
        // same one was provoked by the player's first shot as if it were the raider (round 9: seed
        // 12's Black Sal flew at the player a frame early, or not, as the pool handed it out)
        world.engine.getEcsEngine().addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {}

            @Override
            public void entityRemoved(Entity entity) {
                if (entity == raider) raider = null;
            }
        });

        // Traffic: shown and lockable while it is in the bubble.
        world.macroSim.bubble().addListener(new BubbleTransitionListener() {
            @Override
            public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
                Entity ship = world.engine.getEntity(uuid);
                if (ship == null) return;
                if (ship.getComponent(TransformComponent.class) == null) ship.add(new TransformComponent());
                ship.add(HostileTraffic.markerFor(ship, world.standing.factions().id(world.standing.sponsor())));
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
                        String name = station.getComponent(StationComponent.class).name;
                        var owner = world.standing.factions().def(world.standing.factions().ofStation(name));
                        station.add(new HudMarkerComponent(name, owner != null && owner.hostileToAll
                                ? HudMarkerComponent.Kind.HOSTILE : HudMarkerComponent.Kind.STATION).radius(radius));
                    }

                    @Override
                    public void entityRemoved(Entity station) {
                        station.remove(HudMarkerComponent.class);
                    }
                });
        // Water is mined only at the Moon; both Earth stations need it.
        highport = addStation("Highport", stationType("agri_dome", 0), EARTH,
                circular(ORBIT_RADIUS, HOME_LEAD / ORBIT_RADIUS), HIGHPORT_ANGLE);
        depot = addStation("Deepwell Depot", stationType("fuel_depot", 1), EARTH,
                circular(ORBIT_RADIUS + 2_000e3, phase(2.5)), 0f);
        lunarMine = addStation(LUNAR_MINE_NAME, stationType("ice_mine", 2), MOON,
                circular(MU_MOON, R_MOON + 100e3, lunarPhase), LUNAR_MINE_NAME.equals(homeName) ? HIGHPORT_ANGLE : 0f);
        // The wider world: the sponsor's yard high above Earth, Selene's industry round the Moon, and
        // the Free Traders' outpost far out, half way to the Moon
        shipyard = addStation(SHIPYARD_NAME, stationType("shipyard", 4), EARTH,
                circular(SHIPYARD_ORBIT, phase(4.0)), 0f);
        foundry = addStation(FOUNDRY_NAME, stationType("foundry", 3), MOON,
                circular(MU_MOON, R_MOON + 400e3, phase(2.1)), 0f);
        oreMine = addStation(ORE_MINE_NAME, stationType("ore_mine", 2), MOON,
                circular(MU_MOON, R_MOON + 800e3, phase(4.2)), 0f);
        freehold = addStation(FREEHOLD_NAME, stationType("free_port", 5), EARTH,
                circular(FREEHOLD_ORBIT, phase(MOON_ANGLE + Math.PI)), 0f);
        // The pirates' hidden base, with no market worth the name and no one to clear the player in
        if (world.standing.factions().index(PIRATES) >= 0) {
            pirateBase = addStation(PIRATE_BASE_NAME, stationType("pirate_den", 0), EARTH,
                    periapsisState(MU_EARTH, PIRATE_BASE_PERIAPSIS, PIRATE_BASE_APOAPSIS, phase(5.0)), 0f);
            world.pirateBase.establish(pirateBase, world.macroSim.now());
        }
        double hold = InteriorGridData.cargoBayVolume(content.defs, FREIGHTER_SHIP);
        addFreighter("Hauler Kestrel", hold, highport);
        for (int i = 0; i < 4; i++) addFreighter("Hauler " + (i + 1), hold, depot);
        for (int i = 0; i < 2; i++) addFreighter("Lunar Hauler " + (i + 1), hold, lunarMine);
        addFreighter("Yard Tender", hold, shipyard);
        addFreighter("Ore Barge", hold, oreMine);
        addFreighter("Free Trader Halcyon", hold, freehold);
        if (newGame.seeded) {
            // A seeded world flies more or fewer extra haulers, based where the dice say
            String[] homes = {highport, depot, lunarMine, shipyard, foundry, oreMine, freehold};
            int extra = dice.nextInt(NewGame.EXTRA_FREIGHTERS + 1);
            for (int i = 0; i < extra; i++) {
                String home = homes[dice.nextInt(homes.length)];
                addFreighter(EXTRA_HAULERS[i % EXTRA_HAULERS.length], hold, home);
            }
        }
        // Rescued after abandoning ship: the player flies the ship requisitioned at the station
        world.rescue.rescues.add((signal, rescue) -> {
            player = rescue.newShip;
            seatAtHelm();
            contacts.clear();
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(HudMarkerComponent.class).get())) {
                if (e == player || e.getComponent(StationComponent.class) != null) continue;
                if (e.getComponent(PhysicsBodyComponent.class) != null) contacts.add(e);
            }
        });
        // A ship bought at a yard is the player's, crewed and seated as a rescue leaves it; a prize handed over is gone
        world.shipyard.deals.add((signal, deal) -> {
            contacts.removeValue(deal.gone, true);
            if (deal.newShip == null) return;
            player = deal.newShip;
            seatAtHelm();
        });
        // Pirates lurk by every station; the sponsor patrols Earth's stations, Selene guards the Moon's
        world.hostiles.extraLurk = pirateBase;
        world.hostiles.start(lurks(), present(depot, highport, shipyard),
                present(lunarMine, foundry, oreMine));
        content.hooks.fireWorldReady();
    }

    /** Names of the haulers a seeded world adds. */
    private static final String[] EXTRA_HAULERS = {"Hauler Osprey", "Hauler Tern", "Free Trader Mira", "Hauler Petrel"};
    /** What a freighter starts with, credits; a seeded world's stray by up to a quarter. */
    static final double FREIGHTER_CREDITS = 20_000;

    private void addFreighter(String name, double hold, String home) {
        double credits = FREIGHTER_CREDITS * (newGame.seeded ? 1 + NewGame.STOCK_SPREAD * (2 * dice.nextDouble() - 1) : 1);
        world.macroSim.addFreighter(name, hold, credits, home);
    }

    /** A station's place along its orbit: {@code designed} as laid out, anywhere in a seeded world. */
    private double phase(double designed) {
        return newGame.seeded ? dice.nextDouble() * 2 * Math.PI : designed;
    }

    /** A macro-sim station with its interior and docking ports from its type's blueprint. */
    private String addStation(String name, String typeId, String parentUuid, StateVector state, float angle) {
        double stock = newGame.seeded ? 1 + NewGame.STOCK_SPREAD * (2 * dice.nextDouble() - 1) : 1;
        String uuid = world.macroSim.addStation(name, typeId, parentUuid, state, stock);
        StationComponent station = DockingData.buildStation(world.engine.getEntity(uuid), content.defs,
                world.engine, name, typeId);
        station.angle = angle;
        StationCrew.spawnCrew(world.crew, world.engine.getEntity(uuid), world.standing.factions().ofStation(name));
        world.standing.factions().crewHabits(world.crew.board(world.engine.getEntity(uuid)));
        return uuid;
    }

    /**
     * Starts the career proper (the main game's New game, and First Play): the player is an
     * ensign of the sponsor, docked at Highport, where the mission board's first offer is the
     * bounty on the raider outside. Tests that want the ship free in orbit skip this.
     */
    public void beginCareer() {
        dockAt(home());
    }

    /** The uuid of the station the career starts docked at: the sponsor's home, else Highport. */
    public String home() {
        return stationNamed(homeName, highport);
    }

    /** The name of the sponsor's home station. */
    public String homeName() {
        return homeName;
    }

    /** Latches the player's first port onto a free port of the same size at Highport, at once. */
    public boolean dockAtHighport() {
        return dockAt(highport);
    }

    /** Latches the player's first port onto a free port of the same size at {@code stationUuid}, at once; true as it is when already latched there. */
    public boolean dockAt(String stationUuid) {
        Entity station = world.engine.getEntity(stationUuid);
        if (station == null) return false;
        DockingPortsComponent ports = station.getComponent(DockingPortsComponent.class);
        DockingPortsComponent own = player.getComponent(DockingPortsComponent.class);
        if (ports == null || own == null || own.ports.length == 0) return false;
        if (world.docking.docked(player, station)) return true;
        MountFootprint.MountSize size = own.ports[0].size;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.size == size && p.state != DockingPort.State.DOCKED) {
                world.docking.dockInPlace(player, 0, station, i);
                return true;
            }
        }
        return false;
    }

    /** The player flies {@code ship} from now on, seated at its helm: a debug scenario's swap of hulls. */
    public void takeShip(Entity ship) {
        contacts.removeValue(ship, true);
        player = ship;
        seatAtHelm();
    }

    /** Gives the player's (new) ship a helm and sits the avatar at it, as at the start of a game. */
    private void seatAtHelm() {
        Bridge.install(player);
        if (avatar == null) return;
        int[] seat = besideHelm(player, FloorTiles.of(player.getComponent(ShipGridComponent.class)));
        GridPositionComponent at = avatar.getComponent(GridPositionComponent.class);
        if (seat == null || at == null || at.ship != player) return;
        at.stop();
        at.setTile(seat[0], seat[1]);
        world.bridge.sit(SubsystemComponent.SubsystemType.HELM);
    }

    /** Loads base data and mods and sets up the designed start in {@code world}. */
    public static GameScenario start(GameWorld world, Path assetsDir, Path modsDir) {
        return start(world, assetsDir, modsDir, NewGame.designed());
    }

    /** Loads base data and mods and sets up the new game {@code newGame} asks for in {@code world}. */
    public static GameScenario start(GameWorld world, Path assetsDir, Path modsDir, NewGame newGame) {
        return new GameScenario(world, assetsDir, modsDir, newGame);
    }

    /** The seed and sponsor this career started with. */
    public NewGame newGame() {
        return newGame;
    }

    /** Saves the career's seed, so a loaded game still knows which world it is. */
    private final class SeedRecord implements com.ringsofiron.coreengine.SaveParticipant {
        @Override
        public String saveKey() {
            return "newGame";
        }

        @Override
        public void write(com.badlogic.gdx.utils.Json json) {
            json.writeValue("seed", newGame.seed);
            json.writeValue("seeded", newGame.seeded);
            json.writeValue("sponsor", newGame.sponsor);
        }

        @Override
        public void read(com.badlogic.gdx.utils.Json json, com.badlogic.gdx.utils.JsonValue data) {
            String sponsor = data.getString("sponsor", null);
            newGame = data.getBoolean("seeded", false)
                    ? NewGame.seeded(data.getLong("seed", 0), sponsor) : NewGame.designed().withSponsor(sponsor);
        }
    }

    /**
     * Turns the raider on the player: its turrets and launcher engage the player's ship, and
     * ai-crew's combat pilot closes to fighting range, circles and dodges missiles. A disabled
     * raider stays out of it.
     */
    public void provokeRaider() {
        // A save from after the first raider was dealt with has none (round 9, real window: a shot
        // down the barrel, target null, matched the null raider and crashed the game)
        if (raider == null || ShipDisablement.isDisabled(raider)) return;
        SensorComponent sensor = raider.getComponent(SensorComponent.class);
        if (sensor != null && sensor.lockedTarget == null) sensor.lockedTarget = player;
        if (raider.getComponent(NpcCombatComponent.class) == null) raider.add(new NpcCombatComponent(player));
    }

    /** True once the raider is fighting back. */
    public boolean raiderHostile() {
        // A save from after the first raider was dealt with has none
        if (raider == null) return false;
        SensorComponent sensor = raider.getComponent(SensorComponent.class);
        return sensor != null && sensor.lockedTarget == player;
    }

    /** Next contact after {@code current} (wrapping), or null when there are none. */
    /**
     * Saves the whole game to {@code file}. Refused (returns a reason) during rails warp, when the
     * bubble is collapsed onto the rails, while a tug is still bringing the ship in to dock, and once
     * the ship is lost or the career over, which a save would bring back as a live game; null when saved.
     */
    public String quickSave(FileHandle file) {
        return quickSave(file, null);
    }

    /** As {@link #quickSave(FileHandle)}, opening the save with {@code summary} for the load list. */
    public String quickSave(FileHandle file, Object summary) {
        if (world.getCrash() != null) return "the ship is lost";
        if (world.getCareerOver() != null) return "the career is over";
        if (world.timeWarp.isRailsWarp()) return "drop out of time warp first";
        if (world.rescue.bringingIn()) return "the tug is still bringing you in";
        world.engine.getSerializationService().saveToFile(file, summary);
        return null;
    }

    /** Why {@link #quickLoad} failed, and what state it left the game in. */
    public static final class LoadFailedException extends RuntimeException {
        /** True when the game was rebuilt (from the snapshot, or part loaded): its entities are new ones. */
        public final boolean replaced;
        /** True when the game is as it was before the load: never touched, or put back from the snapshot. */
        public final boolean restored;

        LoadFailedException(String message, Throwable cause, boolean replaced, boolean restored) {
            super(message, cause);
            this.replaced = replaced;
            this.restored = restored;
        }
    }

    /**
     * Replaces the game with the one saved in {@code file}, then points {@link #player},
     * {@link #avatar}, {@link #raider} and {@link #contacts} at the loaded entities. Returns what the
     * load had to leave out (component types or modules this game lacks, dangling references), for
     * the player to see.
     *
     * <p>The save is read and decoded before the game is touched, so an unreadable, malformed or
     * too-new save leaves the game as it was. If it fails after that (a module can't read its part),
     * the game is put back from a snapshot taken just before. Either way it throws
     * {@link LoadFailedException}.
     */
    public java.util.List<String> quickLoad(FileHandle file) {
        SerializationService serializer = world.engine.getSerializationService();
        SerializationService.PreparedLoad save;
        try {
            save = serializer.prepareLoad(file.readString("UTF-8"));
        } catch (RuntimeException e) {
            throw new LoadFailedException(reason(e), e, false, true);
        }
        String snapshot;
        try {
            snapshot = serializer.saveGame();
        } catch (RuntimeException e) {
            // Nothing to go back to, but the save itself read cleanly: go on without one
            snapshot = null;
        }
        try {
            replaceWith(save);
            return serializer.getLastLoadWarnings();
        } catch (RuntimeException e) {
            if (snapshot == null) throw new LoadFailedException(reason(e), e, true, false);
            try {
                replaceWith(serializer.prepareLoad(snapshot));
            } catch (RuntimeException again) {
                e.addSuppressed(again);
                throw new LoadFailedException(reason(e), e, true, false);
            }
            throw new LoadFailedException(reason(e), e, true, true);
        }
    }

    /** What went wrong, in a line for the player. */
    private static String reason(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    /** Resets what a save may not carry, then loads {@code save} over the game. */
    private void replaceWith(SerializationService.PreparedLoad save) {
        // Everything the save might not mention starts again as in a new game (GameWorld#addResettable)
        world.resetForLoad();
        // ...from the designed start, unless the save knows its seed
        newGame = NewGame.designed();
        world.engine.getSerializationService().applyLoad(save);
        world.loaded();
        // A seeded career's combat and crew dice come from its seed, as at its start
        if (newGame.seeded) world.reseed(newGame.combatSeed(), newGame.crewSeed());
        relink();
        world.standing.migrateLegacy(player);
        outfitLegacy();
    }

    /**
     * Saves from before suit lockers: each crewed craft (a ship or station with a task board) that
     * has none is fitted with them, full, for the crew it carries, as a new one is when built.
     */
    private void outfitLegacy() {
        for (Entity craft : world.engine.getEcsEngine().getEntitiesFor(
                Family.all(ShipGridComponent.class, com.ringsofiron.aicrew.ShipTaskBoardComponent.class).get())) {
            ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
            if (com.ringsofiron.interiorgrid.SuitLockers.any(grid)) continue;
            int crew = 0;
            // The captain's figure too: everyone who lives aboard
            for (Entity e : world.crew.crewEntities()) if (world.crew.homeOf(e) == craft) crew++;
            com.ringsofiron.interiorgrid.SuitOutfit.outfit(grid, com.ringsofiron.interiorgrid.InteriorGridData.furnishings(), Math.max(2, crew));
        }
    }

    /**
     * Where pirates lurk, in turn: the first three stations first, as before there were more. A
     * seeded world shuffles them by its seed, the same way every time, so a load keeps the order.
     */
    private String[] lurks() {
        String[] lurks = present(depot, lunarMine, highport, oreMine, freehold, foundry, shipyard);
        if (newGame.seeded) {
            java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of(lurks));
            java.util.Collections.shuffle(order, new java.util.Random(newGame.stream(4)));
            lurks = order.toArray(new String[0]);
        }
        return lurks;
    }

    /** The lurks, in turn, for tests. */
    String[] lurksForTest() {
        return lurks();
    }

    /** {@code uuids} without the nulls: stations a game from an older save lacks. */
    private static String[] present(String... uuids) {
        return java.util.Arrays.stream(uuids).filter(java.util.Objects::nonNull).toArray(String[]::new);
    }

    /** The uuid of the loaded station named {@code name}, or {@code fallback} if the save has none. */
    private String stationNamed(String name, String fallback) {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(StationComponent.class).get())) {
            UuidComponent u = e.getComponent(UuidComponent.class);
            if (u != null && name.equals(e.getComponent(StationComponent.class).name)) return u.id;
        }
        return fallback;
    }

    private void relink() {
        // A save's stations have their own uuids, not the ones this game started with
        highport = stationNamed("Highport", highport);
        depot = stationNamed("Deepwell Depot", depot);
        lunarMine = stationNamed(LUNAR_MINE_NAME, lunarMine);
        var sponsor = world.standing.factions().def(world.standing.sponsor());
        homeName = sponsor == null || sponsor.home == null ? "Highport" : sponsor.home;
        shipyard = stationNamed(SHIPYARD_NAME, null);
        foundry = stationNamed(FOUNDRY_NAME, null);
        oreMine = stationNamed(ORE_MINE_NAME, null);
        freehold = stationNamed(FREEHOLD_NAME, null);
        pirateBase = stationNamed(PIRATE_BASE_NAME, null);
        world.pirateBase.relink(pirateBase);
        world.hostiles.extraLurk = pirateBase;
        world.hostiles.relink(lurks());
        world.hostiles.relinkRoutes(present(depot, highport, shipyard), present(lunarMine, foundry, oreMine));
        player = world.getPlayer();
        if (player != null) ShipCargo.attach(player);
        // Saves from before the bridge: the player starts on deck, and the helm takes over from there
        if (player != null) Bridge.install(player);
        avatar = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(PlayerAvatarComponent.class).get())) {
            avatar = e;
        }
        contacts.clear();
        raider = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(HudMarkerComponent.class).get())) {
            if (e == player || e.getComponent(StationComponent.class) != null) continue;
            contacts.add(e);
            if (e.getComponent(HudMarkerComponent.class).kind == HudMarkerComponent.Kind.HOSTILE
                    && e.getComponent(HostileComponent.class) == null) raider = e;
        }
    }

    /**
     * Round 10: Tab passes over struck ships ({@link Struck}) while any hostile contact still fights,
     * so it no longer locks a prize mid-fight (round 9's trace: Tab onto raider 20, struck 138 s
     * before, and a harpoon brought it back). With nothing left fighting it cycles them too, so the
     * player can still choose to fire on a prize. False cycles every contact, as round 9 did.
     */
    public static final boolean TAB_SKIPS_STRUCK = true;

    /**
     * The contact after {@code current} for Tab, in the order they came into the bubble, wrapping
     * round; the first when {@code current} is null or gone. Struck ships are passed over while any
     * contact still fights ({@link #TAB_SKIPS_STRUCK}).
     */
    public Entity nextContact(Entity current) {
        if (contacts.size == 0) return null;
        boolean skip = TAB_SKIPS_STRUCK && anyFighting();
        int i = current == null ? -1 : contacts.indexOf(current, true);
        for (int n = 1; n <= contacts.size; n++) {
            Entity next = contacts.get((i + n + contacts.size) % contacts.size);
            if (!skip || !Struck.struck(world, next)) return next;
        }
        return null;
    }

    /**
     * The lock a new fight starts with (the game's start, a rescue, a load without one): the first
     * contact that has not struck, or null if every one has. Never a prize, whose lock under the
     * trigger would be aimed fire.
     */
    public Entity firstTarget() {
        for (Entity e : contacts) if (!Struck.struck(world, e)) return e;
        return null;
    }

    /** True while some hostile contact can still fight: not struck, and not beaten. */
    private boolean anyFighting() {
        for (Entity e : contacts) {
            HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
            if (m == null || m.kind != HudMarkerComponent.Kind.HOSTILE) continue;
            if (!Struck.struck(world, e) && !FirstPlay.beaten(world, e)) return true;
        }
        return false;
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

    /** At periapsis, {@code angle} radians round, on the orbit from {@code rp} to {@code ra}, circling the usual way. */
    static StateVector periapsisState(double mu, double rp, double ra, double angle) {
        double v = Math.sqrt(mu * (2 / rp - 2 / (rp + ra)));
        double c = Math.cos(angle), s = Math.sin(angle);
        return new StateVector().set(rp * c, rp * s, -v * s, v * c);
    }

    static StateVector circular(double radius, double angle) {
        return circular(MU_EARTH, radius, angle);
    }

    static StateVector circular(double mu, double radius, double angle) {
        var s = KeplerOrbit.circular(mu, radius, angle, 0, 1).stateAt(0);
        return new StateVector().set(s.x, s.y, s.vx, s.vy);
    }

    /** The floor tile nearest the ship's helm, if one is near enough to sit at it from; else the first floor tile. */
    static int[] besideHelm(Entity ship, List<int[]> floor) {
        if (floor.isEmpty()) return null;
        SubsystemComponent helm = Bridge.console(ship, SubsystemComponent.SubsystemType.HELM);
        if (helm == null) return floor.get(0);
        int[] best = floor.get(0);
        float nearest = Float.MAX_VALUE;
        GridPositionComponent at = new GridPositionComponent();
        for (int[] t : floor) {
            at.setTile(t[0], t[1]);
            float d = Bridge.reach(at, helm.footprint);
            if (d < nearest) {
                nearest = d;
                best = t;
            }
        }
        return best;
    }

}
