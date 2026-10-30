package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The interior demo's world, headless: one ship from the game's data with its crew aboard, in the
 * game's own {@link GameWorld} with every module running, and nothing else around it. The demo
 * cycles through the hulls, puts more crew aboard, hurts the crew and the ship with
 * {@link WorldDebug}'s tools, and can set an armed ship on it; {@link WoundWatch} keeps the account
 * of everyone's wounds. A screen draws it; tests run it.
 *
 * <p>The ship is the player's (it has the player's controls), so the crew behave as the player's
 * crew do and the game's notices about the wounded are posted for it. Nobody is at its helm or
 * guns, so it never fires except its point defense at missiles. It is {@link #pinned} by default:
 * held still where it started, so hits and rams don't knock it off the screen.
 */
public class InteriorLab {
    /** Who crews each hull, in order, up to its def's crew: a medic first, so every hull has one. */
    static final CrewRole[] ROLES = {CrewRole.MEDIC, CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE,
            CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE, CrewRole.MEDIC};
    static final String[] NAMES = {"Ada", "Bea", "Cole", "Dana", "Eli", "Fay", "Gus", "Hal", "Ivy", "Jo", "Kit", "Lou"};
    /** How far off an attacker holds, meters: inside every gun's range. */
    public static final float ATTACK_RANGE = 1_500f;
    /** Slowest and fastest the demo runs the sim, times real time. */
    public static final float MIN_SPEED = 0.25f, MAX_SPEED = 8f;

    public final Path assetsDir;
    public GameWorld world;
    public WorldDebug debug;
    public WoundWatch watch;
    /** The ship being tested, and its ship id. */
    public Entity ship;
    public String hull;
    /** The armed ship set on it, or null, and its ship id. */
    public Entity attacker;
    public String attackerHull;
    public boolean paused;
    /** Sim seconds per real second. */
    public float speed = 1f;
    /** Holds the ship (and an attacker) still where they started. */
    public boolean pinned = true;
    private final Vector2 shipHome = new Vector2(), attackerHome = new Vector2();
    private float shipAngle, attackerAngle;

    public InteriorLab(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    /** Throws the world away and starts again with a fresh {@code shipId} and its crew. */
    public void load(String shipId) {
        dispose();
        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        if (!world.getContent().defs.has("ship", shipId)) shipId = GameScenario.PLAYER_SHIP;
        hull = shipId;
        debug = new WorldDebug(world);
        watch = new WoundWatch(world.crew, world.engine::getSimSeconds);
        ship = world.getContent().factory.spawnShip(shipId, 0f, 0f, 0f);
        ship.add(new PlayerControllerComponent());
        ShipConsoles.install(ship);
        Bridge.install(ship);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        ShipDef def = world.getContent().defs.get("ship", shipId, ShipDef.class);
        List<int[]> floor = FloorTiles.of(ship.getComponent(ShipGridComponent.class));
        int crew = def == null ? 0 : Math.min(def.crew, floor.size());
        for (int i = 0; i < crew; i++) {
            int[] t = floor.get(i * floor.size() / crew);
            CrewFactory.spawn(world.crew, ship, NAMES[i % NAMES.length], ROLES[i % ROLES.length], 0, t[0], t[1]);
        }
        Body body = WorldDebug.body(ship);
        shipHome.set(body.getPosition());
        shipAngle = body.getAngle();
        watch.update();
    }

    /** Starts the same hull again. */
    public void reload() {
        load(hull);
    }

    /** The hulls the lab can load: every ship in the data with a layout to walk, in data order. */
    public List<String> hulls() {
        List<String> out = new ArrayList<>();
        for (String id : world.getContent().defs.ids("ship")) {
            ShipDef def = world.getContent().defs.get("ship", id, ShipDef.class);
            if (def != null && def.chassis != null) out.add(id);
        }
        return out;
    }

    /** Loads the hull {@code step} along from this one in {@link #hulls()}, wrapping round. */
    public void cycle(int step) {
        List<String> all = hulls();
        int i = Math.max(0, all.indexOf(hull));
        load(all.get(Math.floorMod(i + step, all.size())));
    }

    /**
     * Sets an uncrewed {@code shipId} on the lab ship from {@code bearingDeg} (counter-clockwise
     * from the ship's bow) at {@link #ATTACK_RANGE}: its sensors lock the ship and every gun, beam
     * and launcher fires at will, aimed as a middling gunner aims. Replaces any attacker already
     * there. Returns what happened, or why not.
     */
    public String attack(String shipId, float bearingDeg) {
        ceaseFire();
        if (!world.getContent().defs.has("ship", shipId)) return "No ship " + shipId;
        float a = shipAngle + bearingDeg * MathUtils.degreesToRadians;
        Vector2 at = new Vector2(shipHome).scl(com.ringsofiron.physicsexterior.PhysicsManager.METERS_PER_UNIT)
                .add(MathUtils.cos(a) * ATTACK_RANGE, MathUtils.sin(a) * ATTACK_RANGE);
        attackerHull = shipId;
        attacker = world.getContent().factory.spawnShip(shipId, at.x, at.y, a * MathUtils.radiansToDegrees + 180f);
        ShipConsoles.install(attacker);
        SensorComponent sensor = attacker.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = ship;
        Body body = WorldDebug.body(attacker);
        if (body != null) {
            attackerHome.set(body.getPosition());
            attackerAngle = body.getAngle();
        }
        String name = world.getContent().defs.get("ship", shipId, ShipDef.class).name;
        watch.note(String.format("%s opens fire from %.0f deg, %.1f km", name, bearingDeg, ATTACK_RANGE / 1000f));
        return name + " attacking";
    }

    /** Takes the attacker away; its rounds and missiles already in flight fly on. */
    public void ceaseFire() {
        if (attacker == null) return;
        world.engine.removeEntity(attacker);
        attacker = null;
        watch.note("Attacker gone");
    }

    /** One rendered frame of {@code delta} real seconds, at {@link #speed}; nothing moves while {@link #paused}. */
    public void update(float delta) {
        if (paused || world == null) return;
        float left = delta * speed;
        // Small enough slices that a fast speed never hits the engine's frame cap
        while (left > 1e-6f) {
            float step = Math.min(left, 0.1f);
            world.update(step);
            left -= step;
            if (pinned) {
                hold(ship, shipHome, shipAngle);
                hold(attacker, attackerHome, attackerAngle);
            }
            watch.update();
        }
    }

    private static void hold(Entity e, Vector2 home, float angle) {
        Body body = WorldDebug.body(e);
        if (body == null) return;
        body.setTransform(home, angle);
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
    }

    /** Steps the speed up (+1) or down (-1) by doubling or halving it. */
    public void changeSpeed(int step) {
        speed = MathUtils.clamp(step > 0 ? speed * 2f : speed / 2f, MIN_SPEED, MAX_SPEED);
    }

    public void dispose() {
        if (debug != null) debug.dispose();
        if (world != null) world.dispose();
        debug = null;
        world = null;
        ship = attacker = null;
    }
}
