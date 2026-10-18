package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;
import java.util.List;

/**
 * The starting situation of a new game: the player's corvette in low orbit around Earth with a crew
 * aboard, a raider close by, and a busy orbit. A station shares the player's orbit 8 km ahead with a
 * freighter docked at it, which macro-sim brings into the bubble; more traffic hauls between it and a
 * higher depot. Headless, so tests can start the same game.
 */
public class GameScenario {
    public static final String EARTH = "earth";
    public static final double MU_EARTH = 3.986004418e14;
    public static final double R_EARTH = 6.371e6;
    public static final double ORBIT_RADIUS = R_EARTH + 400e3;
    public static final String PLAYER_SHIP = "corvette_patrol";
    /** What the player starts with for trading at station markets. Placeholder values. */
    public static final double STARTING_CREDITS = 5_000;
    public static final double STARTING_HOLD = 100;

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

    private GameScenario(GameWorld world, Path assetsDir, Path modsDir) {
        this.world = world;
        content = world.loadContent(assetsDir, modsDir);

        Entity earth = world.engine.createEntity();
        earth.add(new UuidComponent(EARTH));
        earth.add(new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        world.engine.addEntity(earth);
        world.macroSim.registerMassiveBody(EARTH);
        world.setAnchor(EARTH, KeplerOrbit.circular(MU_EARTH, ORBIT_RADIUS, 0, world.engine.getSimSeconds(), 1));

        player = content.factory.spawnShip(PLAYER_SHIP, 0f, 0f, 90f);
        player.add(new PlayerControllerComponent());
        ShipConsoles.install(player);
        player.add(new TraderComponent(STARTING_CREDITS, STARTING_HOLD));
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
        String[] types = stationTypes();
        highport = world.macroSim.addStation("Highport", types[0], EARTH, circular(ORBIT_RADIUS, 8e3 / ORBIT_RADIUS), 1);
        depot = world.macroSim.addStation("Deepwell Depot", types[1], EARTH, circular(ORBIT_RADIUS + 2_000e3, 2.5), 1);
        world.macroSim.addFreighter("Hauler Kestrel", 300, 20_000, highport);
        for (int i = 0; i < 4; i++) world.macroSim.addFreighter("Hauler " + (i + 1), 300, 20_000, depot);
        content.hooks.fireWorldReady();
    }

    /** Loads base data and mods and sets up a new game in {@code world}. */
    public static GameScenario start(GameWorld world, Path assetsDir, Path modsDir) {
        return new GameScenario(world, assetsDir, modsDir);
    }

    /** Turns the raider on the player: its turrets and launcher engage the player's ship. */
    public void provokeRaider() {
        SensorComponent sensor = raider.getComponent(SensorComponent.class);
        if (sensor != null && sensor.lockedTarget == null) sensor.lockedTarget = player;
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
        world.engine.getSerializationService().loadFromFile(file);
        relink();
    }

    private void relink() {
        player = world.getPlayer();
        avatar = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(PlayerAvatarComponent.class).get())) {
            avatar = e;
        }
        contacts.clear();
        raider = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(HudMarkerComponent.class).get())) {
            if (e == player) continue;
            contacts.add(e);
            if (e.getComponent(HudMarkerComponent.class).kind == HudMarkerComponent.Kind.HOSTILE) raider = e;
        }
    }

    public Entity nextContact(Entity current) {
        if (contacts.size == 0) return null;
        int i = current == null ? -1 : contacts.indexOf(current, true);
        return contacts.get((i + 1) % contacts.size);
    }

    private String[] stationTypes() {
        var all = world.macroSim.economy().catalog().stationTypes();
        String[] out = new String[2];
        int i = 0;
        for (var t : all) {
            if (i < 2) out[i++] = t.id;
        }
        if (i == 0) throw new IllegalStateException("No station types in the economy data");
        if (i == 1) out[1] = out[0];
        return out;
    }

    static StateVector circular(double radius, double angle) {
        var s = KeplerOrbit.circular(MU_EARTH, radius, angle, 0, 1).stateAt(0);
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
