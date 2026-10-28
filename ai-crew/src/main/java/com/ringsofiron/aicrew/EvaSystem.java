package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.physicsexterior.BubbleLimits;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Crew out on EVA ({@link EvaComponent}) and the airlocks they go out and come back in through
 * ({@link Airlock}), every sim tick.
 *
 * <h2>Out and back in</h2>
 * A crew member going out stands in an airlock's chamber ({@link #begin}) and asks for the door; when
 * it opens they step out onto the ring of empty tiles hugging the hull just outside it ({@link
 * HullSurface}), clipped to the reel beside the door. Coming back they wait at that ring tile for the
 * door, step into the chamber, and wait for the air and the hatch. Their behavior picks where they go
 * ({@link EvaComponent#goalX}) and when they head back ({@link EvaComponent#returning}); this system
 * moves them and works the airlocks for them.
 *
 * <h2>On the hull</h2>
 * A crew member on the hull goes where it goes. They crawl along the ring from tile to tile, hand
 * over hand, at {@link #CRAWL_SPEED}, and take a jetpack hop instead when the straight way is clear
 * and saves {@link #HOP_SAVES} tiles of crawling and the pack has the fuel to spare over {@link
 * #JET_RESERVE}. Holding on takes the force that keeps them moving with the hull: their mass times
 * the acceleration of the hull point they hold (the ship's thrust, its turning and spin, a collision)
 * less their line's pull, but for the part the hull itself gives by pushing on them: a burn that
 * drives the hull into them only presses them to it. Too far past {@link #GRIP} for too long
 * ({@link #GRIP_SLIP}) their hold breaks and they come off with the hull point's velocity: an engine
 * burn away from them throws them off, a gentle RCS strafe does not.
 *
 * <h2>Off the hull</h2>
 * Off it, they are a point mass of {@link #MASS} in the physics world's frame, moved only by their
 * line, their jetpack and the hull they hit. The jetpack ({@link #JET_THRUST}, spending the suit's
 * {@link Suit#JET_DV} of delta-v) flies them to where they are going, or back to the nearest point
 * of the hull after a fall; without fuel they pull themselves in hand over hand along the line.
 * Hitting the hull stops the part of their speed into it and some along it; past {@link
 * #HURT_SPEED} it wounds them, and slow enough they catch hold, unless it is dragging past them
 * harder than their grip could hold (a burn still going): then they hang on the line until it eases.
 *
 * <h2>The tether</h2>
 * The line runs {@link #LINE_MAX} at most from a reel at the airlock's door, and is a rope of
 * {@link #ROPE_SEGMENTS} segments (Verlet, stretch constraints only, pushed out of the hull) that
 * floats slack, wraps round the hull and goes taut. The reel takes up slack ({@link #TAKE_UP_SPEED})
 * and pays out as it is pulled, and locks like a seatbelt when the line is drawn out faster than {@link #REEL_LOCK_SPEED};
 * locked or run out, the line stretches and pulls back ({@link #LINE_K}, {@link #LINE_C}) on the crew
 * member and, equally, on the ship at the reel. Held past {@link #LINE_RATED} long enough ({@link
 * #LINE_GIVE}) it parts at their clip, and the reel winds the loose line in. A crew member who
 * can't climb in (out cold, or dead) is winched in at {@link #REEL_SPEED}, and pulled into the
 * airlock at the door.
 *
 * <h2>Adrift</h2>
 * Off the hull with no line, a crew member jets back to it, faster the further out they are (up to
 * {@link #CRUISE_MAX}), so long as their pack has the delta-v to take up the difference in speed and
 * stop again, and their ship isn't under way harder than the pack could follow. Otherwise they hold
 * off and wait for the ship to come back for them: they are {@link EvaComponent#stranded} (an
 * {@link EvaEvent.Kind#ADRIFT} event), and stay in the world, drifting on the orbit their speed gives
 * them (the bubble's tidal gravity pulls on them as on the ship), breathing their suit's air until
 * it runs out ({@link EvaEvent.Kind#AIR_OUT}). The ship coming back near enough and slow enough,
 * they jet back themselves; one who can't (out cold, an empty pack) is fetched by a shipmate on a line. One
 * further than {@link #LOST_RANGE} from their ship has drifted out of the bubble and is left behind
 * for good, as is anyone adrift through a jump: an {@link EvaEvent.Kind#LEFT_BEHIND} event, and taken
 * out of the world.
 *
 * <h2>The main engines' exhaust</h2>
 * The exhaust of a ship's main engines burning ({@link EngineExhaust}) pushes whoever is in it away
 * from the nozzle, on the hull (their hold has to bear it too) or off it, and burns them, the nearer
 * the nozzle the harder: right behind one it throws them off and kills them at once. A line through
 * it near enough the nozzle burns through ({@link EvaEvent.Kind#TETHER_BURNED}). Crawling along the
 * hull, a crew member holds back from crawling on into the exhaust of an engine burning, and goes on
 * once it stops.
 *
 * <p>Ships without a physics body (no exterior world, as in the crew module's own tests) stand still
 * at the origin, unturned: their crew still crawl, hop, swing and fall, relative to them. With the
 * exterior world attached ({@link #attach}) the floating origin's shifts carry the crew along.
 */
public class EvaSystem extends FixedLogicSystem {

    /** A crew member in an EVA suit with its jetpack, kg. */
    public static final float MASS = 180f;
    /** The force a crew member's hold on the hull bears, newtons: about a third of a g. */
    public static final float GRIP = 600f;
    /**
     * A hold breaks once the pull past {@link #GRIP} adds up to this many seconds of their full grip
     * (newton-seconds over {@link #GRIP}): twice their grip for this long, or a jolt of {@code GRIP *
     * GRIP_SLIP / MASS} m/s in an instant (a collision).
     */
    public static final float GRIP_SLIP = 0.15f;
    /** Hand over hand along the hull, m/s. */
    public static final float CRAWL_SPEED = 0.4f;
    /** Jetpack thrust, newtons. */
    public static final float JET_THRUST = 90f;
    /** A jetpack hop's cruising speed relative to the hull, m/s. */
    public static final float HOP_SPEED = 2f;
    /** Delta-v a crew member keeps in the pack and won't spend on hops, m/s: enough to get back to the hull after a fall. */
    public static final float JET_RESERVE = 40f;
    /** A hop is worth it when it saves this many tiles of crawling. */
    public static final float HOP_SAVES = 4f;
    /** Speed relative to the hull at which a crew member touching it catches hold, m/s. */
    public static final float GRAB_SPEED = 0.8f;
    /** Seconds after a fall before a crew member can catch hold again. */
    public static final float REGRAB_SECONDS = 0.5f;
    /** Hitting the hull faster than this wounds, m/s. */
    public static final float HURT_SPEED = 3f;
    /** Health lost per m/s past {@link #HURT_SPEED}. */
    public static final float HURT_PER_MPS = 12f;
    /** A crew member's reach against the hull, meters. */
    public static final float RADIUS = 0.3f;
    /** Line on the reel, meters. */
    public static final float LINE_MAX = 40f;
    /** The line's stiffness once stretched, N/m. */
    public static final float LINE_K = 4000f;
    /** The line's damping as it stretches, N s/m: a crew member on its end swings to rest without bouncing much. */
    public static final float LINE_C = 1200f;
    /** The pull the line holds for as long as it is asked to, newtons (on its stretch): a little under a g on a crew member. */
    public static final float LINE_RATED = 1750f;
    /**
     * Past {@link #LINE_RATED} the line gives, and once the pull past it adds up to this many
     * newton-seconds it parts, as a hold does past {@link #GRIP}: the corvette's full burn parts it
     * in a second and a half, while the hardest swing a burn astern starts them on peaks too briefly.
     */
    public static final float LINE_GIVE = 100f;
    /** The reel locks when the line is drawn out faster than this, m/s. */
    public static final float REEL_LOCK_SPEED = 1.5f;
    /** The reel winches in a crew member who can't climb at this speed, m/s. */
    public static final float REEL_SPEED = 0.5f;
    /** The reel's spring takes up slack at this speed, m/s: quick, so a loose line doesn't float about in loops. */
    public static final float TAKE_UP_SPEED = 2f;
    /** The most the reel's motor (or a crew member hand over hand) pulls on the line winding it in, newtons. */
    public static final float WINCH_PULL = 400f;
    /** Slack the reel leaves in the line, meters. */
    public static final float SLACK = 0.5f;
    /** Hand over hand along the line, m/s. */
    public static final float CLIMB_SPEED = 0.3f;
    /** Adrift with no line this far from their ship, meters, a crew member is out of the physics bubble and left behind. */
    public static final float LOST_RANGE = BubbleLimits.BUBBLE_EXIT_RADIUS;
    /** Adrift, the jetpack brings them back at up to this, m/s: from far out a hop's speed would take longer than their air. */
    public static final float CRUISE_MAX = 10f;
    /** Adrift, they make for the hull at a speed that would bring them back in about this many seconds, within a hop's and {@link #CRUISE_MAX}. */
    static final float CRUISE_SECONDS = 60f;
    /** Delta-v kept in hand reckoning whether the pack can bring them back, m/s. */
    static final float JET_SPARE = 2f;
    /** Adrift, they don't chase a ship accelerating harder than this share of what their pack gives. */
    static final float CHASE_SHARE = 0.8f;
    /**
     * Line it may take at most to reach a shipmate adrift for them to be fetched ({@link
     * RetrieveTask}), meters: round the hull from the port to the ring tile nearest them, then straight
     * out, with a little to spare.
     */
    public static final float FETCH_REACH = LINE_MAX - 2f;
    /** A shipmate adrift drifting off the ship slower than this, m/s, is fetched: any faster and they would be out of reach before anyone was out to them. */
    public static final float FETCH_SPEED = 0.5f;
    /** Within this of a shipmate adrift, meters, and slower than {@link #GRAB_SPEED} relative to them, one out to fetch them takes hold. */
    public static final float TAKE_HOLD = 0.6f;
    /** How far beside the one bringing them in a shipmate in tow is held, meters. */
    static final float TOW_GAP = 0.5f;
    /** Seconds between fresh reckonings of how much line it takes to reach one adrift. */
    static final float REACH_LOOK = 0.5f;
    /** Segments in a tether's rope. */
    public static final int ROPE_SEGMENTS = 20;
    static final int ROPE_ITERATIONS = 16;
    /** Seconds over which the line's measured stretch is smoothed: the rope's shape jitters a little as it slides over the hull. */
    static final float STRETCH_TAU = 0.05f;
    /** Share of the rope's swing, relative to the hull beside it, kept each tick. */
    static final float ROPE_KEEP = 0.97f;
    /** Seconds over which the ship's acceleration is smoothed, for whether a crew member off the hull can catch hold of it. */
    static final float DRAG_TAU = 0.1f;
    /** Seconds between looks for airlocks a load caught part way through a cycle. */
    static final float SCAN_SECONDS = 1f;
    /** Within this of the reel, meters, a crew member winched in is pulled into the airlock. */
    static final float AT_REEL = 1.2f;

    private static final Family EVA = Family.all(EvaComponent.class, CrewMemberComponent.class, GridPositionComponent.class).get();
    private static final Family SHIPS = Family.all(ShipGridComponent.class).get();

    /** What happens to crew on EVA, for notices. */
    public final Signal<EvaEvent> events = new Signal<>();

    private final CrewService service;
    private final ComponentMapper<EvaComponent> evaM = ComponentMapper.getFor(EvaComponent.class);
    private ImmutableArray<Entity> crew, ships;
    private final Map<ShipGridComponent, HullSurface> hulls = new WeakHashMap<>();
    private final ShipFrame frame = new ShipFrame();
    /** For queries from outside the tick, so they never disturb {@link #frame}. */
    private final ShipFrame query = new ShipFrame();
    private final EvaEvent event = new EvaEvent();
    private final Array<Entity> ending = new Array<>(), leaving = new Array<>();
    /** Each ship's pull from its tethers this tick: fx, fy, and the sums of fx*x, fy*y... kept as force and point. */
    private final ObjectMap<Body, float[]> pulls = new ObjectMap<>();
    private final Vector2 v2 = new Vector2();
    private final float[] a = new float[4], b = new float[4], q = new float[4];
    /** The main engines' exhaust of the ship of the crew member being stepped, and one for queries. */
    private final EngineExhaust exhaust = new EngineExhaust(), probe = new EngineExhaust();
    /** An engine of that ship burning this step; the exhaust's push on them, newtons in the world frame. */
    private boolean burning;
    private float pushX, pushY;
    private final int[] tile = new int[2];
    private float scan;
    private PhysicsManager physics;
    private final Listener<WorldShiftEvent> shifts = (signal, e) -> shift(e);

    public EvaSystem(CrewService service) {
        super(11, (float) SimClock.TICK_SECONDS);
        this.service = service;
    }

    @Override
    public void addedToEngine(Engine engine) {
        crew = engine.getEntitiesFor(EVA);
        ships = engine.getEntitiesFor(SHIPS);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        detach();
        crew = null;
        ships = null;
    }

    /** Follows {@code physics}'s floating origin, so crew off the hull keep their place as the world shifts. */
    public void attach(PhysicsManager physics) {
        detach();
        this.physics = physics;
        physics.worldShifts.add(shifts);
    }

    public void detach() {
        if (physics != null) physics.worldShifts.remove(shifts);
        physics = null;
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** {@code e}'s EVA, or null if they are not on one. */
    public static EvaComponent of(Entity e) {
        return e == null ? null : e.getComponent(EvaComponent.class);
    }

    /** True if {@code e} is out of the airlock, on the hull or off it. */
    public static boolean outside(Entity e) {
        EvaComponent eva = of(e);
        return eva != null && eva.outside();
    }

    /** {@code grid}'s hull surface, kept until its layout changes. */
    public HullSurface hull(ShipGridComponent grid) {
        HullSurface h = HullSurface.of(grid, hulls.get(grid));
        hulls.put(grid, h);
        return h;
    }

    /** The ring tile just outside {@code port}'s door, where crew step out and wait to come in: {@code out = {x, y}}. */
    public static int[] doorRing(MountFootprint port, int[] out) {
        DockingPortLayout.chamberTile(port, out);
        Side side = DockingPortLayout.facing(port);
        out[0] += side.dx;
        out[1] += side.dy;
        return out;
    }

    /** Where {@code port}'s reel is, tiles: by its door, on the hull's outer edge. {@code out = {x, y}}. */
    public static float[] reel(MountFootprint port, float[] out) {
        float[] edge = new float[4];
        DockingPortLayout.outerEdge(port, edge);
        out[0] = edge[0];
        out[1] = edge[1];
        return out;
    }

    /**
     * The rope of {@code e}'s tether in their ship's frame, tiles, reel first: {@code out} gets x, y
     * pairs. Once it has parted, the line their reel is winding back in; empty once it is in.
     */
    public void ropeTiles(Entity e, FloatArray out) {
        out.clear();
        EvaComponent eva = of(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (eva == null || eva.rope == null || grid == null) return;
        frame.read(p.ship, grid);
        for (int i = 0; i < eva.rope.length; i += 2) {
            frame.toTiles(eva.rope[i], eva.rope[i + 1], a);
            out.add(a[0]);
            out.add(a[1]);
        }
    }

    /** The world-frame rope of {@code e}'s tether, meters, reel first, or null. Read only. */
    public static float[] ropeWorld(Entity e) {
        EvaComponent eva = of(e);
        return eva == null || !eva.tethered ? null : eva.rope;
    }

    // -----------------------------------------------------------------------
    // Going out and coming in
    // -----------------------------------------------------------------------

    /**
     * Starts {@code e}'s EVA through {@code port}: they stand in its chamber, and the airlock is asked
     * to cycle out for them. False if they can't go out there.
     */
    public boolean begin(Entity e, MountFootprint port) {
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (grid == null || port == null || of(e) != null || !Airlock.works(grid, port)) return false;
        int index = grid.mounts.indexOf(port, true);
        if (index < 0) return false;
        Airlock lock = Airlock.of(grid, port);
        if (!lock.claim(e, true)) return false;
        e.add(new EvaComponent(index, port));
        return true;
    }

    /** Ends {@code e}'s EVA where they stand: back aboard, or never out. */
    private void finish(Entity e) {
        EvaComponent eva = of(e);
        if (eva == null) return;
        if (eva.portMount != null && eva.portMount.airlock != null) eva.portMount.airlock.release(e);
        if (eva.towing != null) letGoOf(e, eva);
        EvaComponent holder = of(eva.heldBy);
        if (holder != null && holder.towing == e) holder.towing = null;
        e.remove(EvaComponent.class);
    }

    private void stepOut(Entity e, EvaComponent eva, GridPositionComponent p, ShipGridComponent grid, Airlock lock) {
        doorRing(eva.portMount, tile);
        HullSurface hull = hull(grid);
        if (!hull.isRing(tile[0], tile[1])) {
            // A door onto nothing a crew member could hold: back in
            eva.returning = true;
            return;
        }
        eva.phase = EvaComponent.Phase.ON_HULL;
        eva.ringX = tile[0];
        eva.ringY = tile[1];
        eva.path.clear();
        eva.stepDone = 0f;
        hull.hug(tile[0], tile[1], v2);
        eva.rx = v2.x;
        eva.ry = v2.y;
        eva.rvx = eva.rvy = 0f;
        eva.worldValid = false;
        eva.hullVValid = false;
        eva.rope = null;
        eva.tethered = true;
        eva.reelLocked = false;
        eva.strain = 0f;
        eva.tension = 0f;
        service.physics.release(e);
        p.stop();
        p.x = eva.rx;
        p.y = eva.ry;
        lock.release(e);
        emit(EvaEvent.Kind.OUT, e, p.ship, 0f);
    }

    private void stepIn(Entity e, EvaComponent eva, GridPositionComponent p) {
        DockingPortLayout.chamberTile(eva.portMount, tile);
        eva.phase = EvaComponent.Phase.RETURNING;
        eva.rope = null;
        eva.worldValid = false;
        eva.path.clear();
        p.stop();
        p.setTile(tile[0], tile[1]);
    }

    // -----------------------------------------------------------------------
    // The tick
    // -----------------------------------------------------------------------

    @Override
    protected void updateFixed(float dt) {
        if (crew == null) return;
        if ((scan -= dt) <= 0f) {
            scan = SCAN_SECONDS;
            scanPorts();
        }
        updateAirlocks(dt);
        pulls.clear();
        for (int i = 0; i < crew.size(); i++) step(crew.get(i), dt);
        for (ObjectMap.Entry<Body, float[]> pull : pulls) {
            float[] f = pull.value;
            PhysicsManager.applyForce(pull.key, f[0], f[1], f[2], f[3]);
        }
        for (Entity e : ending) finish(e);
        ending.clear();
        for (Entity e : leaving) leftBehind(e);
        leaving.clear();
    }

    @Override
    protected void catchUp(long ticks, double seconds) {
        // Rails warp waits while the player's crew are out (the game's guard). Anyone else's are back
        // in by the end of the skipped time, their job put down, but for those adrift with no line: a
        // ship jumping along its orbit leaves them where they were
        if (crew != null) {
            for (int i = 0; i < crew.size(); i++) bringIn(crew.get(i));
            for (Entity e : ending) finish(e);
            ending.clear();
            for (Entity e : leaving) leftBehind(e);
            leaving.clear();
        }
        for (int i = 0; i < 64 && updateAirlocks((float) Math.min(seconds, Airlock.DOOR_SECONDS * 4f)); i++) {
            seconds -= Airlock.DOOR_SECONDS * 4f;
            if (seconds <= 0) break;
        }
    }

    /**
     * Puts {@code e}, on an EVA, back in their airlock's chamber with it over, for a time skip; one
     * adrift with no line, or with no port left to come in by, is left behind.
     */
    private void bringIn(Entity e) {
        EvaComponent eva = evaM.get(e);
        if (eva == null) return;
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid != null && eva.portMount == null && eva.port >= 0 && eva.port < grid.mounts.size) eva.portMount = grid.mounts.get(eva.port);
        if (grid == null || eva.portMount == null || eva.phase == EvaComponent.Phase.FREE && !eva.tethered && eva.heldBy == null) {
            (eva.outside() ? leaving : ending).add(e);
            return;
        }
        if (eva.outside()) {
            DockingPortLayout.chamberTile(eva.portMount, tile);
            eva.rope = null;
            p.stop();
            p.setTile(tile[0], tile[1]);
        }
        service.releaseTask(e);
        ending.add(e);
    }

    /** Finds ports a load left part way through a cycle, so their airlocks bring them back to rest. */
    private void scanPorts() {
        for (int i = 0; i < ships.size(); i++) {
            ShipGridComponent grid = ships.get(i).getComponent(ShipGridComponent.class);
            for (int m = 0; m < grid.mounts.size; m++) {
                MountFootprint port = grid.mounts.get(m);
                if (port.kind != MountFootprint.MountKind.PORT || port.airlock != null || Airlock.docked(grid, port)) continue;
                if (unrested(grid, port)) Airlock.of(grid, port);
            }
        }
    }

    /** True if {@code port}'s hatch is shut or its door open (and not blown away). */
    private static boolean unrested(ShipGridComponent grid, MountFootprint port) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                if (h != null && !h.isDestroyed() && h.bulkheadSealed) return true;
                EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                if (d != null && !d.isDestroyed() && !d.bulkheadSealed) return true;
            }
        }
        return false;
    }

    /** Works every airlock in use for {@code dt}; true if any is still cycling. */
    private boolean updateAirlocks(float dt) {
        boolean busy = false;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
            for (int m = 0; m < grid.mounts.size; m++) {
                Airlock lock = grid.mounts.get(m).airlock;
                if (lock == null) continue;
                lock.crowded = crowded(ship, lock);
                lock.tethered = tethered(lock.port);
                lock.update(grid, dt);
                busy |= !lock.isIn() && !lock.isOut();
            }
        }
        return busy;
    }

    /** True if anyone but the airlock's user stands in its chamber. */
    private boolean crowded(Entity ship, Airlock lock) {
        ImmutableArray<Entity> all = service.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e == lock.user()) continue;
            GridPositionComponent p = service.position(e);
            if (p.ship != ship || p.walkway != null || outside(e)) continue;
            // A shipmate brought in by the user comes through the cycle with them
            EvaComponent eva = of(e);
            if (eva != null && eva.heldBy != null && eva.heldBy == lock.user()) continue;
            if (DockingPortLayout.isOpening(lock.port, p.tileX(), p.tileY())) return true;
        }
        return false;
    }

    /** How many are out on {@code port}'s reel. */
    private int tethered(MountFootprint port) {
        int n = 0;
        for (int i = 0; i < crew.size(); i++) {
            EvaComponent eva = evaM.get(crew.get(i));
            if (eva != null && eva.portMount == port && eva.tethered && eva.outside()) n++;
        }
        return n;
    }

    private void step(Entity e, float dt) {
        // Its EVA just over (the family catches up once this system's update is done)
        EvaComponent eva = evaM.get(e);
        if (eva == null) return;
        CrewMemberComponent c = service.crew(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = service.grid(p.ship);
        if (grid == null) {
            ending.add(e);
            return;
        }
        if (eva.portMount == null && eva.port >= 0 && eva.port < grid.mounts.size) eva.portMount = grid.mounts.get(eva.port);
        MountFootprint port = eva.portMount;
        Airlock lock = port == null ? null : Airlock.of(grid, port);
        switch (eva.phase) {
            case LEAVING:
                if (lock == null) {
                    ending.add(e);
                } else if (eva.returning || !c.isConscious() || !Airlock.works(grid, port)) {
                    // Back in without going out
                    if (lock.claim(e, false) && lock.isIn()) ending.add(e);
                } else if (lock.claim(e, true) && lock.isOut()) {
                    stepOut(e, eva, p, grid, lock);
                }
                return;
            case RETURNING:
                if (eva.heldBy != null) {
                    // Brought into the chamber by a shipmate: in with them once the hatch opens
                    if (lock == null || of(eva.heldBy) == null || lock.isIn() && lock.user() != eva.heldBy) {
                        ending.add(e);
                        emit(EvaEvent.Kind.BACK, e, p.ship, 0f);
                    }
                    return;
                }
                if (lock == null || lock.claim(e, false) && lock.isIn()) {
                    ending.add(e);
                    emit(EvaEvent.Kind.BACK, e, p.ship, 0f);
                }
                return;
            default:
                break;
        }
        frame.read(p.ship, grid);
        // Their ship's body made again (the bubble rebuilt) or new to it (come into the bubble): where
        // they are in the world is worked out again from where they are relative to the ship
        if (frame.body != eva.frameBody) eva.worldValid = eva.shipVValid = false;
        eva.frameBody = frame.body;
        // The ship's acceleration, smoothed: what a hand on its hull has to hold against
        if (eva.shipVValid) {
            float k = Math.min(1f, dt / DRAG_TAU);
            eva.shipAx += ((frame.vx - eva.shipVx) / dt - eva.shipAx) * k;
            eva.shipAy += ((frame.vy - eva.shipVy) / dt - eva.shipAy) * k;
            eva.shipAlpha += ((frame.omega - eva.shipOmega) / dt - eva.shipAlpha) * k;
        }
        eva.shipVx = frame.vx;
        eva.shipVy = frame.vy;
        eva.shipOmega = frame.omega;
        eva.shipVValid = true;
        HullSurface hull = hull(grid);
        if (!eva.worldValid) initWorld(eva);
        if (eva.regrab > 0f) eva.regrab -= dt;
        boolean conscious = c.isConscious();
        if (eva.airSeen > 0f && c.suitAir <= 0f && c.state != CrewState.DEAD) emit(EvaEvent.Kind.AIR_OUT, e, p.ship, 0f);
        eva.airSeen = c.suitAir;
        burning = exhaust.read(p.ship, grid);
        scorch(e, eva, c, p, dt);
        if (eva.heldBy != null) {
            EvaComponent holder = of(eva.heldBy);
            if (holder != null && holder.towing == e) {
                // In tow: the shipmate who has hold of them moves them
                toShipFrame(eva, p);
                return;
            }
            eva.heldBy = null;
        }
        if (eva.phase == EvaComponent.Phase.ON_HULL) {
            if (conscious) hold(e, eva, c, p, grid, hull, dt);
            else letGo(e, eva, p, 0f);
        }
        if (eva.phase == EvaComponent.Phase.FREE) fly(e, eva, c, grid, hull, dt);
        if (eva.phase == EvaComponent.Phase.FREE && !eva.tethered) adrift(e, eva, c, p, grid, hull, dt);
        if (eva.tethered && port != null) rope(e, eva, c, p, grid, hull, port, dt);
        else if (eva.rope != null && port != null) reelIn(eva, hull, port, dt);
        else eva.rope = null;
        toShipFrame(eva, p);
        if (eva.towing != null) carry(e, eva, conscious);
        if (port == null || lock == null) {
            if (!eva.tethered) driftCheck(e, eva, p);
            return;
        }
        // At the door on the way in: in once it opens, and anyone in tow with them
        doorRing(port, tile);
        boolean atDoor = eva.phase == EvaComponent.Phase.ON_HULL && eva.ringX == tile[0] && eva.ringY == tile[1] && eva.path.size == 0;
        boolean reeledIn = !conscious && eva.tethered && eva.lineOut <= AT_REEL;
        if (atDoor && eva.returning || reeledIn) {
            if (lock.claim(e, true) && lock.isOut()) {
                stepIn(e, eva, p);
                if (eva.towing != null) inWith(eva);
            }
        }
        if (!eva.tethered) driftCheck(e, eva, p);
    }

    /**
     * The exhaust of their ship's main engines on {@code eva} this step, if they are in it ({@link
     * EngineExhaust}): its push, for {@link #hold} and {@link #fly}; its burn; and said so when it
     * first catches them.
     */
    private void scorch(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, float dt) {
        float push = burning ? exhaust.push(eva.rx, eva.ry, c4) : 0f;
        eva.exhaust = push;
        pushX = pushY = 0f;
        if (push <= 0f) {
            eva.scorched = false;
            return;
        }
        // Along the ship's axes, into the world's
        pushX = (frame.cos * c4[0] - frame.sin * c4[1]) * push;
        pushY = (frame.sin * c4[0] + frame.cos * c4[1]) * push;
        if (push > EngineExhaust.BURN_FROM) {
            if (!eva.scorched && c.state != CrewState.DEAD) {
                eva.scorched = true;
                emit(EvaEvent.Kind.EXHAUST, e, p.ship, push);
            }
            service.applyDamage(e, EngineExhaust.burn(push) * dt, CrewDamageSink.DamageCause.FIRE);
        } else if (push < EngineExhaust.BURN_FROM * 0.5f) {
            eva.scorched = false;
        }
    }

    /**
     * True if {@code e}, outside, is where a full burn of their ship's main engines would burn them,
     * or their line runs where it would burn through ({@link EngineExhaust}): for a warning before
     * anyone burns.
     */
    public boolean behindEngines(Entity e) {
        EvaComponent eva = of(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (eva == null || !eva.outside() || grid == null || !probe.read(p.ship, grid, 1f)) return false;
        if (probe.burns(eva.rx, eva.ry)) return true;
        if (!eva.tethered || eva.rope == null) return false;
        query.read(p.ship, grid);
        return probe.burnsThrough(ropeInTiles(eva.rope, query));
    }

    private final float[] qa = new float[2];
    private float[] ropeScratch = new float[2 * ROPE_SEGMENTS + 2];

    /** {@code rope}, world meters, in {@code f}'s tiles, into a scratch array. */
    private float[] ropeInTiles(float[] rope, ShipFrame f) {
        if (ropeScratch.length != rope.length) ropeScratch = new float[rope.length];
        for (int i = 0; i < rope.length; i += 2) {
            f.toTiles(rope[i], rope[i + 1], qa);
            ropeScratch[i] = qa[0];
            ropeScratch[i + 1] = qa[1];
        }
        return ropeScratch;
    }

    /**
     * A tick adrift with no line: how long, and once they can't get back on their own, said so; and
     * near enough the ship for a shipmate on a line to reach, a job to fetch them ({@link RetrieveTask}).
     */
    private void adrift(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid,
                        HullSurface hull, float dt) {
        eva.adrift += dt;
        if (!eva.stranded && eva.shortOfFuel && c.state != CrewState.DEAD) {
            eva.stranded = true;
            emit(EvaEvent.Kind.ADRIFT, e, p.ship, range(eva));
        }
        if ((eva.reachLook -= dt) <= 0f) {
            eva.reachLook = REACH_LOOK;
            eva.reach = eva.shortOfFuel && c.state != CrewState.DEAD ? lineToReach(eva, grid, hull) : Float.MAX_VALUE;
        }
        ShipTaskBoardComponent board = service.board(p.ship);
        if (board != null && !board.battleStations && retrievable(e)) {
            int[] n = hull.nearest(eva.rx, eva.ry);
            board.post(new RetrieveTask(e, n == null ? 0 : n[0], n == null ? 0 : n[1]));
        }
    }

    /**
     * Meters of line from the nearest port that can cycle to {@code eva} adrift: round the hull to the
     * ring tile nearest them, then straight out to them. {@code Float.MAX_VALUE} if no port reaches.
     */
    private float lineToReach(EvaComponent eva, ShipGridComponent grid, HullSurface hull) {
        int[] n = hull.nearest(eva.rx, eva.ry);
        if (n == null) return Float.MAX_VALUE;
        hull.hug(n[0], n[1], v2);
        float out = v2.dst(eva.rx, eva.ry) * grid.tileSize;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint port = grid.mounts.get(i);
            if (port.kind != MountFootprint.MountKind.PORT || !Airlock.works(grid, port)) continue;
            doorRing(port, tile);
            if (!hull.path(tile[0], tile[1], n[0], n[1], ringPath)) continue;
            best = Math.min(best, ringPath.size * grid.tileSize + out);
        }
        return best;
    }

    private final Array<int[]> ringPath = new Array<>();

    /**
     * True if {@code e}, adrift and unable to come back on their own (out cold, or their pack can't
     * bring them back), is within {@link #FETCH_REACH} of line of a port of their ship that can cycle,
     * drifting off it slower than {@link #FETCH_SPEED}, and not yet in anyone's tow: someone could go
     * out on a line and fetch them.
     */
    public boolean retrievable(Entity e) {
        EvaComponent eva = of(e);
        CrewMemberComponent c = service.crew(e);
        if (eva == null || c == null || c.state == CrewState.DEAD || eva.phase != EvaComponent.Phase.FREE || eva.tethered
                || eva.heldBy != null || !eva.shortOfFuel || eva.reach > FETCH_REACH) return false;
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (grid == null) return false;
        float speed = (float) Math.sqrt(eva.rvx * eva.rvx + eva.rvy * eva.rvy) * grid.tileSize;
        return speed <= FETCH_SPEED;
    }

    /** How far {@code eva} is from the middle of the ship whose {@link #frame} is read, meters. */
    private float range(EvaComponent eva) {
        float dx = eva.wx - frame.cx, dy = eva.wy - frame.cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Left behind once adrift beyond {@link #LOST_RANGE} of their ship. */
    private void driftCheck(Entity e, EvaComponent eva, GridPositionComponent p) {
        if (eva.phase != EvaComponent.Phase.FREE) return;
        frame.read(p.ship, service.grid(p.ship));
        if (range(eva) > LOST_RANGE) leaving.add(e);
    }

    /**
     * How far {@code e}, outside, is from the middle of their ship, meters, and how fast that is
     * closing (negative: opening), m/s: {@code out = {range, closing}}. False if they are not outside.
     */
    public boolean rangeOf(Entity e, float[] out) {
        EvaComponent eva = of(e);
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (eva == null || !eva.outside() || !eva.worldValid || grid == null) return false;
        query.read(p.ship, grid);
        float dx = eva.wx - query.cx, dy = eva.wy - query.cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        out[0] = d;
        out[1] = d < 1e-3f ? 0f : -((eva.vx - query.vx) * dx + (eva.vy - query.vy) * dy) / d;
        return true;
    }

    // -----------------------------------------------------------------------
    // Fetching a shipmate adrift
    // -----------------------------------------------------------------------

    /** The mass {@code eva} moves: theirs, and a shipmate's in tow. */
    static float mass(EvaComponent eva) {
        return eva.towing != null ? 2f * MASS : MASS;
    }

    /** {@code e} takes hold of {@code other}, adrift, to bring them in. */
    private void takeInTow(Entity e, EvaComponent eva, Entity other, EvaComponent o) {
        eva.chase = null;
        eva.towing = other;
        o.heldBy = e;
        o.stranded = false;
        // Their two speeds made one
        eva.vx = (eva.vx + o.vx) * 0.5f;
        eva.vy = (eva.vy + o.vy) * 0.5f;
        GridPositionComponent op = service.position(other);
        event.by = e;
        emit(EvaEvent.Kind.TAKEN_IN_TOW, other, op == null ? null : op.ship, o.adrift);
        event.by = null;
        o.adrift = 0f;
    }

    /** Keeps the shipmate {@code eva} has in tow beside them, or lets go of them if they can't hold on. */
    private void carry(Entity e, EvaComponent eva, boolean conscious) {
        Entity other = eva.towing;
        EvaComponent o = of(other);
        GridPositionComponent op = service.position(other);
        if (!conscious || o == null || op == null || o.heldBy != e || !o.outside()) {
            letGoOf(e, eva);
            return;
        }
        // Beside them, on the side away from the ship's middle
        float dx = eva.wx - frame.cx, dy = eva.wy - frame.cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = d > 1e-3f ? dx / d : 1f, uy = d > 1e-3f ? dy / d : 0f;
        o.phase = EvaComponent.Phase.FREE;
        o.wx = eva.wx + ux * TOW_GAP;
        o.wy = eva.wy + uy * TOW_GAP;
        o.vx = eva.vx;
        o.vy = eva.vy;
        o.worldValid = true;
        o.frameBody = frame.body;
        o.facing = eva.facing;
        toShipFrame(o, op);
    }

    /** Lets go of whoever {@code eva} has in tow: adrift again where they are. */
    private void letGoOf(Entity e, EvaComponent eva) {
        EvaComponent o = of(eva.towing);
        if (o != null && o.heldBy == e) o.heldBy = null;
        eva.towing = null;
    }

    /** Takes the shipmate {@code eva} has in tow into the chamber with them, stepping in. */
    private void inWith(EvaComponent eva) {
        Entity other = eva.towing;
        EvaComponent o = of(other);
        GridPositionComponent op = service.position(other);
        if (o == null || op == null) {
            eva.towing = null;
            return;
        }
        o.port = eva.port;
        o.portMount = eva.portMount;
        o.phase = EvaComponent.Phase.RETURNING;
        o.rope = null;
        o.worldValid = false;
        o.path.clear();
        op.stop();
        DockingPortLayout.chamberTile(eva.portMount, tile);
        op.setTile(tile[0], tile[1]);
    }

    private void leftBehind(Entity e) {
        GridPositionComponent p = service.position(e);
        Entity ship = p == null ? null : p.ship;
        finish(e);
        service.releaseTask(e);
        emit(EvaEvent.Kind.LEFT_BEHIND, e, ship, 0f);
        service.engine().removeEntity(e);
    }

    // -----------------------------------------------------------------------
    // On the hull
    // -----------------------------------------------------------------------

    /** A tick on the hull: crawls on toward the goal (or hops), and holds on if the hull's motion allows. */
    private void hold(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid,
                      HullSurface hull, float dt) {
        float fromX = eva.rx, fromY = eva.ry;
        // Off on a hop, or out to a shipmate adrift: the jetpack takes over from the hull point's motion
        if (eva.chase != null || crawl(eva, c, hull, dt)) {
            eva.slideX = eva.slideY = 0f;
            eva.path.clear();
            frame.toWorld(eva.rx, eva.ry, a);
            frame.velocityAt(a[0], a[1], b);
            eva.wx = a[0];
            eva.wy = a[1];
            eva.vx = b[0];
            eva.vy = b[1];
            eva.phase = EvaComponent.Phase.FREE;
            eva.hullVValid = false;
            return;
        }
        // Just caught hold short of the hug point: they pull themselves over to it
        if (eva.slideX != 0f || eva.slideY != 0f) {
            float len = (float) Math.sqrt(eva.slideX * eva.slideX + eva.slideY * eva.slideY);
            float ease = CRAWL_SPEED / hull.tileSize() * dt;
            float k = len > ease ? (len - ease) / len : 0f;
            eva.slideX *= k;
            eva.slideY *= k;
            eva.rx += eva.slideX;
            eva.ry += eva.slideY;
        }
        eva.rvx = (eva.rx - fromX) / dt;
        eva.rvy = (eva.ry - fromY) / dt;
        frame.toWorld(eva.rx, eva.ry, a);
        frame.velocityAt(a[0], a[1], b);
        // What keeps them with the hull point: its acceleration times their mass, less what their line
        // pulls. The hull pushing them along its outward normal does that part itself; the rest, a pull
        // away from it or a drag along it, their hold has to give.
        float fx = 0f, fy = 0f, mass = mass(eva);
        if (eva.hullVValid) {
            fx = mass * (b[0] - eva.hullVx) / dt;
            fy = mass * (b[1] - eva.hullVy) / dt;
        }
        if (eva.tethered && eva.tension > 0f && eva.rope != null) {
            int n = eva.rope.length;
            float dx = eva.rope[n - 4] - a[0], dy = eva.rope[n - 3] - a[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                fx -= eva.tension * dx / d;
                fy -= eva.tension * dy / d;
            }
        }
        // And what an engine's exhaust pushes them with
        fx -= pushX;
        fy -= pushY;
        float need = (float) Math.sqrt(fx * fx + fy * fy);
        if (hull.surface(eva.rx, eva.ry, q)) {
            frame.vecToWorld(q[2], q[3], c4);
            float len = (float) Math.sqrt(c4[0] * c4[0] + c4[1] * c4[1]);
            float nx = c4[0] / len, ny = c4[1] / len;
            float fn = fx * nx + fy * ny;
            float tx = fx - fn * nx, ty = fy - fn * ny, pull = Math.min(0f, fn);
            need = (float) Math.sqrt(tx * tx + ty * ty + pull * pull);
        }
        eva.hullVx = b[0];
        eva.hullVy = b[1];
        eva.hullVValid = true;
        frame.vecToWorld(eva.rvx, eva.rvy, c4);
        eva.wx = a[0];
        eva.wy = a[1];
        eva.vx = b[0] + c4[0];
        eva.vy = b[1] + c4[1];
        // Past their grip the strain builds, and their spare grip eases it: a jolt that would take more
        // than GRIP_SLIP seconds of their full grip, or a pull well past it for a moment, tears them off
        eva.strain = Math.max(0f, eva.strain + (need - GRIP) * dt);
        if (eva.strain > GRIP * GRIP_SLIP) letGo(e, eva, p, need / mass);
    }

    private final float[] c4 = new float[2];

    private void letGo(Entity e, EvaComponent eva, GridPositionComponent p, float accel) {
        eva.phase = EvaComponent.Phase.FREE;
        eva.slideX = eva.slideY = 0f;
        eva.hop = false;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.strain = 0f;
        eva.hullVValid = false;
        eva.fell = true;
        eva.regrab = REGRAB_SECONDS;
        emit(EvaEvent.Kind.LOST_GRIP, e, p.ship, accel);
    }

    /**
     * Crawls {@code eva} along the ring toward their goal for {@code dt}. True if they push off on a
     * jetpack hop instead.
     */
    private boolean crawl(EvaComponent eva, CrewMemberComponent c, HullSurface hull, float dt) {
        eva.burnWait = false;
        if (!eva.hasGoal() || !hull.isRing(eva.goalX, eva.goalY) || !hull.isRing(eva.ringX, eva.ringY)) {
            eva.path.clear();
            if (eva.hasGoal() && !hull.isRing(eva.goalX, eva.goalY)) eva.outOfReach = true;
            settle(eva, hull);
            return false;
        }
        int[] last = eva.path.size > 0 ? eva.path.peek() : null;
        boolean fresh = last == null || last[0] != eva.goalX || last[1] != eva.goalY || eva.pathHull != hull;
        if (fresh && (eva.ringX != eva.goalX || eva.ringY != eva.goalY)) {
            if (!hull.path(eva.ringX, eva.ringY, eva.goalX, eva.goalY, eva.path)) {
                eva.outOfReach = true;
                settle(eva, hull);
                return false;
            }
            eva.pathHull = hull;
            eva.stepDone = 0f;
            if (wantsHop(eva, c, hull)) {
                eva.hop = true;
                eva.path.clear();
                return true;
            }
        }
        if (eva.path.size == 0) {
            settle(eva, hull);
            return false;
        }
        // The line run out and stretched: no further this way
        if (eva.lineOut >= LINE_MAX - 0.01f && eva.stretch > 0.05f) {
            eva.outOfReach = true;
            return false;
        }
        float budget = CRAWL_SPEED / hull.tileSize() * dt * c.woundPace();
        while (budget > 0f && eva.path.size > 0) {
            int[] next = eva.path.first();
            hull.hug(eva.ringX, eva.ringY, v2);
            float ax = v2.x, ay = v2.y;
            hull.hug(next[0], next[1], v2);
            // Not on into the exhaust of an engine burning: they hold where they are till it stops
            if (burning && exhaust.burns(v2.x, v2.y) && !exhaust.burns(eva.rx, eva.ry)) {
                eva.burnWait = true;
                return false;
            }
            float len = Math.max(1e-3f, v2.dst(ax, ay));
            float left = len * (1f - eva.stepDone);
            eva.facing = (float) Math.atan2(v2.y - ay, v2.x - ax);
            if (budget >= left) {
                budget -= left;
                eva.ringX = next[0];
                eva.ringY = next[1];
                eva.path.removeIndex(0);
                eva.stepDone = 0f;
                eva.crawled += left;
            } else {
                eva.stepDone += budget / len;
                eva.crawled += budget;
                budget = 0f;
            }
        }
        if (eva.path.size == 0) {
            settle(eva, hull);
        } else {
            int[] next = eva.path.first();
            hull.hug(eva.ringX, eva.ringY, v2);
            float ax = v2.x, ay = v2.y;
            hull.hug(next[0], next[1], v2);
            eva.rx = ax + (v2.x - ax) * eva.stepDone;
            eva.ry = ay + (v2.y - ay) * eva.stepDone;
        }
        return false;
    }

    /** Puts {@code eva} at their ring tile's hug point. */
    private void settle(EvaComponent eva, HullSurface hull) {
        if (!hull.isRing(eva.ringX, eva.ringY)) return;
        hull.hug(eva.ringX, eva.ringY, v2);
        eva.rx = v2.x;
        eva.ry = v2.y;
        eva.stepDone = 0f;
    }

    /** True if the way to the goal is better flown: clear, much shorter, and the pack can spare it. */
    private boolean wantsHop(EvaComponent eva, CrewMemberComponent c, HullSurface hull) {
        if (c.suitKind != Suit.Kind.EVA || c.suitJet < JET_RESERVE + 2f * HOP_SPEED + 1f || eva.noHops) return false;
        hull.hug(eva.ringX, eva.ringY, v2);
        float ax = v2.x, ay = v2.y;
        hull.hug(eva.goalX, eva.goalY, v2);
        float straight = v2.dst(ax, ay);
        return eva.path.size - straight >= HOP_SAVES && hull.clear(ax, ay, v2.x, v2.y);
    }

    // -----------------------------------------------------------------------
    // Off the hull
    // -----------------------------------------------------------------------

    /** A tick off the hull: the line's pull and the jetpack, then the hull in the way. */
    private void fly(Entity e, EvaComponent eva, CrewMemberComponent c, ShipGridComponent grid, HullSurface hull, float dt) {
        float fx = 0f, fy = 0f;
        // The line pulls along its last segment, toward the reel
        if (eva.tethered && eva.tension > 0f && eva.rope != null) {
            int n = eva.rope.length;
            float dx = eva.rope[n - 4] - eva.wx, dy = eva.rope[n - 3] - eva.wy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                fx += eva.tension * dx / d;
                fy += eva.tension * dy / d;
            }
        }
        float mass = mass(eva);
        // The bubble's tidal gravity: the ship falls the same way, so it tells only on one adrift far off
        if (physics != null) {
            physics.getTidalField().accelAt(eva.wx, eva.wy, v2);
            fx += v2.x * mass;
            fy += v2.y * mass;
        }
        // An engine's exhaust
        fx += pushX;
        fy += pushY;
        boolean conscious = c.isConscious();
        int[] target = null;
        eva.jetting = false;
        eva.climbing = false;
        eva.waiting = false;
        eva.shortOfFuel = !eva.tethered && (!conscious || !c.suited || c.suitKind != Suit.Kind.EVA || c.suitJet <= 0f);
        boolean canJet = conscious && c.suited && c.suitKind == Suit.Kind.EVA && c.suitJet > 0f;
        // Out to a shipmate adrift: while they are still there to take hold of
        EvaComponent chased = of(eva.chase);
        if (chased != null && (!canJet || !chased.outside() || chased.heldBy != null || !chased.worldValid)) chased = null;
        if (chased == null) eva.chase = null;
        if (canJet && chased == null) {
            target = eva.hop && eva.hasGoal() && hull.isRing(eva.goalX, eva.goalY) ? new int[]{eva.goalX, eva.goalY}
                    : hull.nearest(eva.rx, eva.ry);
        } else if (conscious && eva.tethered && chased == null) {
            eva.climbing = true;
        }
        if (target != null || chased != null) {
            if (chased != null) {
                a[0] = chased.wx;
                a[1] = chased.wy;
                b[0] = chased.vx;
                b[1] = chased.vy;
            } else {
                hull.hug(target[0], target[1], v2);
                frame.toWorld(v2.x, v2.y, a);
                frame.velocityAt(a[0], a[1], b);
            }
            float rx = a[0] - eva.wx, ry = a[1] - eva.wy;
            float rvx = eva.vx - b[0], rvy = eva.vy - b[1];
            float dist = (float) Math.sqrt(rx * rx + ry * ry);
            float maxA = JET_THRUST / mass;
            // Far out with no line, faster than a hop, or the way back would outlast their air; out from
            // the hull on a line, no faster than the reel pays out without locking
            float cruise = eva.tethered ? HOP_SPEED : Math.max(HOP_SPEED, Math.min(CRUISE_MAX, dist / CRUISE_SECONDS));
            if (chased != null) cruise = Math.min(cruise, REEL_LOCK_SPEED * 0.8f);
            float speed = Math.min(cruise, (float) Math.sqrt(2f * maxA * 0.6f * dist));
            float wantX = dist > 0.05f ? rx / dist * speed : 0f, wantY = dist > 0.05f ? ry / dist * speed : 0f;
            float ax = (wantX - rvx) * 1.5f, ay = (wantY - rvy) * 1.5f;
            if (!eva.tethered) {
                // Only if the pack can bring them back from here, and the ship isn't drawing away under
                // thrust the pack can't match: chasing it would only empty the pack
                float need = (float) Math.sqrt((wantX - rvx) * (wantX - rvx) + (wantY - rvy) * (wantY - rvy)) + speed + JET_SPARE;
                float shipA = (float) Math.sqrt(eva.shipAx * eva.shipAx + eva.shipAy * eva.shipAy);
                eva.shortOfFuel = need > c.suitJet;
                eva.waiting = eva.shortOfFuel || shipA > maxA * CHASE_SHARE;
                if (eva.waiting) ax = ay = 0f;
            }
            float am = (float) Math.sqrt(ax * ax + ay * ay);
            if (am > maxA) {
                ax *= maxA / am;
                ay *= maxA / am;
                am = maxA;
            }
            // The pack's delta-v is reckoned on its wearer alone: a shipmate in tow doubles what it takes
            float spend = am * dt * mass / MASS;
            if (spend > c.suitJet) {
                float k = c.suitJet / spend;
                ax *= k;
                ay *= k;
                spend = c.suitJet;
            }
            c.suitJet = Math.max(0f, c.suitJet - spend);
            fx += ax * mass;
            fy += ay * mass;
            eva.jetting = spend > 0f;
            if (eva.jetting) {
                frame.vecToTiles(ax, ay, c4);
                float jet = (float) Math.sqrt(c4[0] * c4[0] + c4[1] * c4[1]);
                eva.jetX = c4[0] / jet;
                eva.jetY = c4[1] / jet;
            }
            if (!eva.waiting) eva.facing = (float) Math.atan2(ry, rx) - frame.angle;
            float rel = (float) Math.sqrt(rvx * rvx + rvy * rvy);
            if (chased != null) {
                // There and slow enough: they take hold of them
                if (dist < TAKE_HOLD && rel < GRAB_SPEED) takeInTow(e, eva, eva.chase, chased);
                target = null;
            }
            // There, slow enough to take hold, and the hull not dragging past their grip
            if (target != null && dist < 0.3f && rel < GRAB_SPEED && eva.regrab <= 0f && hull.surface(v2.x, v2.y, q)) {
                frame.vecToWorld(q[2], q[3], c4);
                float len = (float) Math.sqrt(c4[0] * c4[0] + c4[1] * c4[1]);
                if (canHold(eva, a[0], a[1], c4[0] / len, c4[1] / len)) {
                    grab(e, eva, target[0], target[1], hull);
                    return;
                }
            }
        }
        eva.vx += fx / mass * dt;
        eva.vy += fy / mass * dt;
        eva.wx += eva.vx * dt;
        eva.wy += eva.vy * dt;
        contact(e, eva, c, grid, hull);
    }

    /** The hull in the way: stops the speed into it and some along it, hurts past {@link #HURT_SPEED}, and is caught hold of if slow. */
    private void contact(Entity e, EvaComponent eva, CrewMemberComponent c, ShipGridComponent grid, HullSurface hull) {
        frame.toTiles(eva.wx, eva.wy, a);
        float qx = a[0], qy = a[1];
        float r = RADIUS / grid.tileSize;
        float px, py, nx, ny;
        if (hull.solid((int) Math.floor(qx), (int) Math.floor(qy))) {
            if (!hull.pushOut(qx, qy, b)) return;
            px = b[0];
            py = b[1];
            nx = px - qx;
            ny = py - qy;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 1e-5f) return;
            nx /= len;
            ny /= len;
            px += nx * r;
            py += ny * r;
        } else {
            if (!hull.surface(qx, qy, b)) return;
            float dx = qx - b[0], dy = qy - b[1];
            if (dx * dx + dy * dy >= r * r) return;
            nx = b[2];
            ny = b[3];
            px = b[0] + nx * r;
            py = b[1] + ny * r;
        }
        frame.toWorld(px, py, a);
        eva.wx = a[0];
        eva.wy = a[1];
        frame.vecToWorld(nx, ny, c4);
        float len = (float) Math.sqrt(c4[0] * c4[0] + c4[1] * c4[1]);
        float nwx = c4[0] / len, nwy = c4[1] / len;
        frame.velocityAt(eva.wx, eva.wy, b);
        float rvx = eva.vx - b[0], rvy = eva.vy - b[1];
        float vn = rvx * nwx + rvy * nwy;
        if (vn < 0f) {
            float impact = -vn;
            rvx -= vn * nwx;
            rvy -= vn * nwy;
            // Friction along the hull, up to half the blow
            float vt = (float) Math.sqrt(rvx * rvx + rvy * rvy);
            if (vt > 1e-5f) {
                float k = Math.max(0f, vt - 0.5f * impact) / vt;
                rvx *= k;
                rvy *= k;
            }
            eva.vx = b[0] + rvx;
            eva.vy = b[1] + rvy;
            if (impact > HURT_SPEED) service.applyDamage(e, (impact - HURT_SPEED) * HURT_PER_MPS, CrewDamageSink.DamageCause.IMPACT);
        }
        float rel = (float) Math.sqrt(rvx * rvx + rvy * rvy);
        if (c.isConscious() && eva.regrab <= 0f && eva.chase == null && rel < GRAB_SPEED && canHold(eva, eva.wx, eva.wy, nwx, nwy)) {
            int[] ring = hull.nearest(px, py);
            if (ring != null) grab(e, eva, ring[0], ring[1], hull);
        }
    }

    /**
     * True if a crew member could keep hold of the hull at world point ({@code wx}, {@code wy}), its
     * outward normal ({@code nx}, {@code ny}): the force that would keep them moving with it, but for
     * the hull's push, within their grip. Their line doesn't count: it pulls one way and then another
     * as they swing, so nobody can lean on it to take hold. A hull dragging harder than that past them
     * (a hard burn still going) can't be caught; they hang on the line until it eases.
     */
    private boolean canHold(EvaComponent eva, float wx, float wy, float nx, float ny) {
        float rx = wx - frame.cx, ry = wy - frame.cy, w2 = frame.omega * frame.omega, mass = mass(eva);
        float fx = mass * (eva.shipAx - eva.shipAlpha * ry - w2 * rx);
        float fy = mass * (eva.shipAy + eva.shipAlpha * rx - w2 * ry);
        float fn = fx * nx + fy * ny;
        float tx = fx - fn * nx, ty = fy - fn * ny, pull = Math.min(0f, fn);
        return tx * tx + ty * ty + pull * pull <= GRIP * GRIP;
    }

    private void grab(Entity e, EvaComponent eva, int x, int y, HullSurface hull) {
        eva.phase = EvaComponent.Phase.ON_HULL;
        eva.ringX = x;
        eva.ringY = y;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.hop = false;
        eva.strain = 0f;
        eva.hullVValid = false;
        eva.jetting = false;
        eva.climbing = false;
        // Where they took hold, short of the ring tile's hug point
        frame.toTiles(eva.wx, eva.wy, a);
        settle(eva, hull);
        eva.slideX = a[0] - eva.rx;
        eva.slideY = a[1] - eva.ry;
        eva.rx = a[0];
        eva.ry = a[1];
        if (eva.fell) {
            eva.fell = false;
            emit(EvaEvent.Kind.CAUGHT_HULL, e, service.position(e).ship, eva.adrift);
        }
        eva.adrift = 0f;
        eva.stranded = false;
    }

    // -----------------------------------------------------------------------
    // The tether
    // -----------------------------------------------------------------------

    /** A tick of the line: the rope's shape, the reel, the pull, and a snap. */
    private void rope(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid,
                      HullSurface hull, MountFootprint port, float dt) {
        int n = ROPE_SEGMENTS;
        reel(port, a);
        frame.toWorld(a[0], a[1], b);
        float ax = b[0], ay = b[1];
        if (eva.rope == null) layRope(eva, ax, ay);
        float[] r = eva.rope;
        swing(r, eva.ropePrev, ax, ay, true, eva.wx, eva.wy, Math.max(0.05f, eva.lineOut) / n, hull, dt);
        float length = 0f;
        for (int i = 0; i < n; i++) {
            float dx = r[2 * i + 2] - r[2 * i], dy = r[2 * i + 3] - r[2 * i + 1];
            length += (float) Math.sqrt(dx * dx + dy * dy);
        }
        // Through the exhaust of an engine burning, it burns through
        if (burning && exhaust.burnsThrough(ropeInTiles(eva.rope, frame))) {
            eva.tethered = false;
            eva.tension = 0f;
            eva.reelLocked = false;
            emit(EvaEvent.Kind.TETHER_BURNED, e, p.ship, 0f);
            return;
        }
        float stretch = length - eva.lineOut;
        float straight = (float) Math.sqrt((eva.wx - ax) * (eva.wx - ax) + (eva.wy - ay) * (eva.wy - ay));
        // The reel: pays out as the line is drawn, unless drawn too fast (it locks), and takes up slack;
        // or winds in one who can't climb, while their weight on the line is no more than its motor pulls
        boolean winched = eva.phase == EvaComponent.Phase.FREE && (eva.climbing || !c.isConscious());
        if (stretch > 0f && !eva.reelLocked && !winched && eva.lineOut < LINE_MAX) {
            // Locks on a line snatched out by one going faster than it pays out: not on one pushed out
            // slowly (a hop, a push off to a shipmate), whose rope only shivers as it lifts off the hull
            frame.velocityAt(eva.wx, eva.wy, c4);
            float rel = (float) Math.sqrt((eva.vx - c4[0]) * (eva.vx - c4[0]) + (eva.vy - c4[1]) * (eva.vy - c4[1]));
            if (eva.phase == EvaComponent.Phase.FREE && stretch / dt > REEL_LOCK_SPEED && rel > REEL_LOCK_SPEED && eva.lineOut > 1f) {
                eva.reelLocked = true;
            } else {
                float out = Math.min(stretch, LINE_MAX - eva.lineOut);
                eva.lineOut += out;
                stretch -= out;
            }
        }
        if (winched) {
            if (eva.tension < WINCH_PULL) eva.lineOut = Math.max(0.3f, eva.lineOut - (c.isConscious() ? CLIMB_SPEED : REEL_SPEED) * dt);
        } else if (!eva.reelLocked && stretch < 0f && eva.lineOut > straight + SLACK) {
            eva.lineOut = Math.max(straight + SLACK, eva.lineOut - TAKE_UP_SPEED * dt);
        }
        if (eva.reelLocked) {
            eva.slackTime = stretch < 0f ? eva.slackTime + dt : 0f;
            if (eva.slackTime > 0.5f || eva.phase == EvaComponent.Phase.ON_HULL) {
                eva.reelLocked = false;
                eva.slackTime = 0f;
            }
        }
        // Its pull: the stretch, and the line's own damping as it stretches; it parts once the stretch
        // alone has been past what it is rated for long enough
        float before = eva.stretchF;
        eva.stretchF += (stretch - eva.stretchF) * Math.min(1f, dt / STRETCH_TAU);
        float tension = 0f, elastic = 0f;
        if (eva.stretchF > 0f) {
            float rate = (eva.stretchF - Math.max(0f, before)) / dt;
            elastic = LINE_K * eva.stretchF;
            tension = Math.max(0f, elastic + LINE_C * rate);
        }
        eva.stretch = stretch;
        eva.tension = tension;
        eva.lineStrain = Math.max(0f, eva.lineStrain + (elastic - LINE_RATED) * dt);
        if (eva.lineStrain > LINE_GIVE) {
            eva.tethered = false;
            eva.tension = 0f;
            eva.reelLocked = false;
            emit(EvaEvent.Kind.TETHER_SNAPPED, e, p.ship, tension);
            return;
        }
        // The same pull on the ship, at the reel, along the line's first segment
        if (tension > 0f && frame.body != null) {
            float dx = r[2] - r[0], dy = r[3] - r[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                float[] f = pulls.get(frame.body);
                if (f == null) pulls.put(frame.body, f = new float[4]);
                f[0] += tension * dx / d;
                f[1] += tension * dy / d;
                f[2] = ax;
                f[3] = ay;
            }
        }
    }

    /**
     * Moves rope {@code r} on by {@code dt} ({@code prev}: its points a step ago): the free points
     * swing on, losing some of their motion relative to the hull beside them; the first goes with the
     * reel at ({@code ax}, {@code ay}) and, if {@code held}, the last with the crew member at ({@code
     * ex}, {@code ey}). Then no segment is let stay longer than {@code rest}, and the rope is pushed
     * out of the hull.
     */
    private void swing(float[] r, float[] prev, float ax, float ay, boolean held, float ex, float ey, float rest,
                       HullSurface hull, float dt) {
        int n = r.length / 2 - 1, free = held ? 2 * n : 2 * n + 2;
        for (int i = 2; i < free; i += 2) {
            float x = r[i], y = r[i + 1];
            frame.velocityAt(x, y, c4);
            float vx = x - prev[i], vy = y - prev[i + 1];
            float sx = c4[0] * dt, sy = c4[1] * dt;
            vx = sx + (vx - sx) * ROPE_KEEP;
            vy = sy + (vy - sy) * ROPE_KEEP;
            prev[i] = x;
            prev[i + 1] = y;
            r[i] = x + vx;
            r[i + 1] = y + vy;
        }
        prev[0] = r[0];
        prev[1] = r[1];
        r[0] = ax;
        r[1] = ay;
        if (held) {
            prev[2 * n] = r[2 * n];
            prev[2 * n + 1] = r[2 * n + 1];
            r[2 * n] = ex;
            r[2 * n + 1] = ey;
        }
        for (int it = 0; it < ROPE_ITERATIONS; it++) {
            // Sweeping from each end in turn, so a pull at either end travels the length of the rope
            for (int step = 0; step < n; step++) {
                int i = it % 2 == 0 ? step : n - 1 - step;
                int j = 2 * i, k = j + 2;
                float dx = r[k] - r[j], dy = r[k + 1] - r[j + 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d <= rest || d < 1e-6f) continue;
                float wa = i == 0 ? 0f : 1f, wb = held && i == n - 1 ? 0f : 1f;
                if (wa + wb == 0f) continue;
                float move = (d - rest) / d / (wa + wb);
                r[j] += dx * move * wa;
                r[j + 1] += dy * move * wa;
                r[k] -= dx * move * wb;
                r[k + 1] -= dy * move * wb;
            }
            // Out of the hull
            if (it >= ROPE_ITERATIONS - 3) {
                for (int i = 2; i < free; i += 2) {
                    frame.toTiles(r[i], r[i + 1], q);
                    if (!hull.pushOut(q[0], q[1], b)) continue;
                    frame.toWorld(b[0], b[1], q);
                    r[i] = q[0];
                    r[i + 1] = q[1];
                }
            }
        }
    }

    /**
     * A tick of a parted line: it parted at the crew member's clip, so the whole of it is left on the
     * reel, its loose end free, and the reel's spring winds it in; gone once it is.
     */
    private void reelIn(EvaComponent eva, HullSurface hull, MountFootprint port, float dt) {
        eva.lineOut -= TAKE_UP_SPEED * dt;
        if (eva.lineOut <= 0.1f) {
            eva.rope = null;
            return;
        }
        reel(port, a);
        frame.toWorld(a[0], a[1], b);
        swing(eva.rope, eva.ropePrev, b[0], b[1], false, 0f, 0f, eva.lineOut / ROPE_SEGMENTS, hull, dt);
    }

    /** Lays the rope straight from the reel to the crew member, at rest relative to the hull. */
    private void layRope(EvaComponent eva, float ax, float ay) {
        int n = ROPE_SEGMENTS;
        eva.rope = new float[2 * n + 2];
        eva.ropePrev = new float[2 * n + 2];
        float straight = (float) Math.sqrt((eva.wx - ax) * (eva.wx - ax) + (eva.wy - ay) * (eva.wy - ay));
        if (eva.lineOut < straight) eva.lineOut = Math.min(LINE_MAX, straight + SLACK);
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = ax + (eva.wx - ax) * t, y = ay + (eva.wy - ay) * t;
            eva.rope[2 * i] = x;
            eva.rope[2 * i + 1] = y;
            frame.velocityAt(x, y, c4);
            eva.ropePrev[2 * i] = x - c4[0] * STEP;
            eva.ropePrev[2 * i + 1] = y - c4[1] * STEP;
        }
        eva.stretch = eva.stretchF = 0f;
    }

    static final float STEP = (float) SimClock.TICK_SECONDS;

    // -----------------------------------------------------------------------
    // Frames
    // -----------------------------------------------------------------------

    /** World-frame state from the ship-frame state saved (or set when they stepped out). */
    private void initWorld(EvaComponent eva) {
        frame.toWorld(eva.rx, eva.ry, a);
        frame.velocityAt(a[0], a[1], b);
        frame.vecToWorld(eva.rvx, eva.rvy, c4);
        eva.wx = a[0];
        eva.wy = a[1];
        eva.vx = b[0] + c4[0];
        eva.vy = b[1] + c4[1];
        eva.worldValid = true;
        eva.hullVValid = false;
        eva.rope = null;
    }

    /** The ship-frame state from the world-frame one, and the crew member's place on deck. */
    private void toShipFrame(EvaComponent eva, GridPositionComponent p) {
        if (eva.phase == EvaComponent.Phase.FREE) {
            frame.toTiles(eva.wx, eva.wy, a);
            eva.rx = a[0];
            eva.ry = a[1];
            frame.velocityAt(eva.wx, eva.wy, b);
            frame.vecToTiles(eva.vx - b[0], eva.vy - b[1], c4);
            eva.rvx = c4[0];
            eva.rvy = c4[1];
        }
        p.x = eva.rx;
        p.y = eva.ry;
    }

    private void shift(WorldShiftEvent s) {
        if (crew == null) return;
        float dx = (float) s.deltaX, dy = (float) s.deltaY, dvx = (float) s.deltaVx, dvy = (float) s.deltaVy;
        for (int i = 0; i < crew.size(); i++) {
            EvaComponent eva = evaM.get(crew.get(i));
            if (eva == null) continue;
            eva.shipVx -= dvx;
            eva.shipVy -= dvy;
            if (!eva.worldValid) continue;
            eva.wx -= dx;
            eva.wy -= dy;
            eva.vx -= dvx;
            eva.vy -= dvy;
            eva.hullVx -= dvx;
            eva.hullVy -= dvy;
            if (eva.rope == null) continue;
            for (int k = 0; k < eva.rope.length; k += 2) {
                eva.rope[k] -= dx;
                eva.rope[k + 1] -= dy;
                eva.ropePrev[k] += dvx * STEP - dx;
                eva.ropePrev[k + 1] += dvy * STEP - dy;
            }
        }
    }

    private void emit(EvaEvent.Kind kind, Entity e, Entity ship, float amount) {
        event.kind = kind;
        event.crew = e;
        event.ship = ship;
        event.amount = amount;
        events.dispatch(event);
        event.crew = null;
        event.ship = null;
    }

    /**
     * A ship's place and motion in the physics world's frame, meters and radians; the identity at
     * rest for a ship with no body.
     */
    static final class ShipFrame {
        float x, y, angle, cos = 1f, sin, cx, cy, vx, vy, omega, ox, oy, tile = 1f;
        Body body;

        void read(Entity ship, ShipGridComponent grid) {
            ox = grid.gridOriginLocal.x;
            oy = grid.gridOriginLocal.y;
            tile = grid.tileSize;
            PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
            body = pb == null ? null : pb.body;
            if (body == null) {
                x = y = angle = sin = cx = cy = vx = vy = omega = 0f;
                cos = 1f;
                return;
            }
            Vector2 p = body.getPosition();
            x = p.x * PhysicsManager.METERS_PER_UNIT;
            y = p.y * PhysicsManager.METERS_PER_UNIT;
            angle = body.getAngle();
            cos = (float) Math.cos(angle);
            sin = (float) Math.sin(angle);
            Vector2 c = body.getWorldCenter();
            cx = c.x * PhysicsManager.METERS_PER_UNIT;
            cy = c.y * PhysicsManager.METERS_PER_UNIT;
            Vector2 v = body.getLinearVelocity();
            vx = v.x * PhysicsManager.METERS_PER_UNIT;
            vy = v.y * PhysicsManager.METERS_PER_UNIT;
            omega = body.getAngularVelocity();
        }

        /** Tile point (tx, ty) in the world, meters: {@code out = {x, y}}. */
        void toWorld(float tx, float ty, float[] out) {
            float lx = ox + tx * tile, ly = oy + ty * tile;
            out[0] = x + cos * lx - sin * ly;
            out[1] = y + sin * lx + cos * ly;
        }

        /** World point (wx, wy), meters, in tiles: {@code out = {x, y}}. */
        void toTiles(float wx, float wy, float[] out) {
            float dx = wx - x, dy = wy - y;
            float lx = cos * dx + sin * dy, ly = -sin * dx + cos * dy;
            out[0] = (lx - ox) / tile;
            out[1] = (ly - oy) / tile;
        }

        /** The ship's velocity at world point (wx, wy), m/s: {@code out = {vx, vy}}. */
        void velocityAt(float wx, float wy, float[] out) {
            out[0] = vx - omega * (wy - cy);
            out[1] = vy + omega * (wx - cx);
        }

        /** A ship-frame vector in tiles as a world one in meters. */
        void vecToWorld(float tx, float ty, float[] out) {
            out[0] = (cos * tx - sin * ty) * tile;
            out[1] = (sin * tx + cos * ty) * tile;
        }

        /** A world vector in meters as a ship-frame one in tiles. */
        void vecToTiles(float wx, float wy, float[] out) {
            out[0] = (cos * wx + sin * wy) / tile;
            out[1] = (-sin * wx + cos * wy) / tile;
        }
    }
}
