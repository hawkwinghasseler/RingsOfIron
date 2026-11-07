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
 * <h2>Across to another craft</h2>
 * A crew member can go out to cross to another craft standing off nearby ({@link #beginCrossing}):
 * out of the airlock with no line clipped on, over the hull to the side of it facing the other
 * craft, and off it on the jetpack ({@link #cross}) at up to {@link #CROSS_SPEED}. From the moment
 * they push off they count as aboard the craft they make for, in its frame: they catch hold of its
 * hull as of their own, crawl to the port they mean to go in by and go in through its airlock, cycled
 * for them if it is their own craft's (or has been taken over), straight into its chamber if its door
 * has been forced open ({@link EvaComponent#forcedIn}). A hostile craft's door is forced from
 * outside first ({@link EvaComponent#breaking}): their behavior does that work, and says how it went.
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
    /**
     * The pull the line holds for as long as it is asked to, newtons (on its stretch): about 1.2 g on
     * a crew member. Nov 2026: 1750 N until the mass rebalance left the corvette about a fifth
     * lighter, so its burns pull a fifth harder on the line; raised with them, so what held still holds.
     */
    public static final float LINE_RATED = 2150f;
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
    /** Cruising speed relative to the craft being crossed to, m/s: brisk, with no line to fall back on. */
    public static final float CROSS_SPEED = 3f;
    /** The furthest off, meters (hull to hull, near enough), another craft is crossed to on the jetpack. */
    public static final float CROSS_RANGE = 300f;
    /** The most the two craft may be drifting apart or together for a crossing to set out, m/s. */
    public static final float CROSS_DRIFT = 2f;
    /**
     * The furthest apart, meters, the two craft of a boarding across on EVA may drift before the
     * boarders are out of reach to cross back, and it is called off.
     */
    public static final float CROSS_REACH = 500f;

    private static final Family EVA = Family.all(EvaComponent.class, CrewMemberComponent.class, GridPositionComponent.class).get();
    private static final Family SHIPS = Family.all(ShipGridComponent.class).get();

    /** What happens to crew on EVA, for notices. */
    public final Signal<EvaEvent> events = new Signal<>();

    final CrewService service;
    private final ComponentMapper<EvaComponent> evaM = ComponentMapper.getFor(EvaComponent.class);
    private ImmutableArray<Entity> crew, ships;
    private final Map<ShipGridComponent, HullSurface> hulls = new WeakHashMap<>();
    final ShipFrame frame = new ShipFrame();
    /** For queries from outside the tick, so they never disturb {@link #frame}. */
    private final ShipFrame query = new ShipFrame();
    final EvaEvent event = new EvaEvent();
    private final Array<Entity> ending = new Array<>(), leaving = new Array<>();
    /** Each ship's pull from its tethers this tick: fx, fy, and the sums of fx*x, fy*y... kept as force and point. */
    final ObjectMap<Body, float[]> pulls = new ObjectMap<>();
    final Vector2 v2 = new Vector2();
    final float[] a = new float[4], b = new float[4], q = new float[4];
    /** The main engines' exhaust of the ship of the crew member being stepped, and one for queries. */
    final EngineExhaust exhaust = new EngineExhaust(), probe = new EngineExhaust();
    /** An engine of that ship burning this step; the exhaust's push on them, newtons in the world frame. */
    boolean burning;
    float pushX, pushY;
    final int[] tile = new int[2];
    private float scan;
    PhysicsManager physics;
    private final Listener<WorldShiftEvent> shifts = (signal, e) -> shift(e);
    final EvaCrossing crossings = new EvaCrossing(this);
    final HullHold hullHold = new HullHold(this);
    final HullCrawl crawling = new HullCrawl(this);
    final EvaFlight flight = new EvaFlight(this);
    final EvaTether tether = new EvaTether(this);
    final RopeSwing ropeSwing = new RopeSwing(this);
    final EvaTow tow = new EvaTow(this);
    final ExhaustScorch scorching = new ExhaustScorch(this);

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

    /**
     * Starts {@code e}'s EVA through {@code port} to cross to {@code target} and go in by its port
     * {@code targetPort}, its door forced from outside first if {@code breaking}: out with no line,
     * over the hull to the side facing {@code target}, and off it ({@link #cross}). False if they
     * can't go out there.
     */
    public boolean beginCrossing(Entity e, MountFootprint port, Entity target, MountFootprint targetPort, boolean breaking) {
        if (target == null || targetPort == null || !begin(e, port)) return false;
        EvaComponent eva = of(e);
        eva.crossTo = target;
        eva.crossPort = targetPort;
        eva.crossBreaking = breaking;
        return true;
    }

    /**
     * Pushes {@code e} off the hull (or wherever they are outside) for {@code target}, to go in by its
     * port {@code port}, forced from outside first if {@code breaking}. From now on they count as
     * aboard {@code target}, untethered; their place and speed are worked out in its frame. False if
     * they are not outside or {@code port} is not one of {@code target}'s.
     */
    public boolean cross(Entity e, Entity target, MountFootprint port, boolean breaking) {
        return crossings.cross(e, target, port, breaking);
    }

    /**
     * {@code other}'s middle in {@code ship}'s frame, tiles: {@code out = {x, y}}. False if either has
     * no grid.
     */
    public boolean middleIn(Entity other, Entity ship, float[] out) {
        ShipGridComponent grid = service.grid(ship), og = service.grid(other);
        if (grid == null || og == null) return false;
        ShipFrame there = new ShipFrame();
        there.read(other, og);
        there.toWorld(og.width * 0.5f, og.height * 0.5f, q);
        frame.read(ship, grid);
        frame.toTiles(q[0], q[1], out);
        return true;
    }

    /**
     * The ring tile of {@code e}'s hull (the craft they count as aboard) nearest {@code other}'s
     * middle, from which they push off for it: {@code out = {x, y}}. False if either has no grid or
     * no hull.
     */
    public boolean launchTile(Entity e, Entity other, int[] out) {
        GridPositionComponent p = service.position(e);
        ShipGridComponent grid = p == null ? null : service.grid(p.ship);
        if (grid == null || !middleIn(other, p.ship, a)) return false;
        int[] ring = hull(grid).nearest(a[0], a[1]);
        if (ring == null) return false;
        out[0] = ring[0];
        out[1] = ring[1];
        return true;
    }

    /**
     * The distance between {@code a}'s and {@code b}'s middles, meters, and how fast they are drawing
     * apart (positive) or together, m/s: {@code out = {distance, rate}}. Craft without a physics body
     * stand at the origin. False if either has no grid.
     */
    public boolean separation(Entity a, Entity b, float[] out) {
        ShipGridComponent ga = service.grid(a), gb = service.grid(b);
        if (ga == null || gb == null) return false;
        ShipFrame fa = new ShipFrame(), fb = new ShipFrame();
        fa.read(a, ga);
        fb.read(b, gb);
        float[] pa = new float[2], pb = new float[2];
        fa.toWorld(ga.width * 0.5f, ga.height * 0.5f, pa);
        fb.toWorld(gb.width * 0.5f, gb.height * 0.5f, pb);
        float dx = pb[0] - pa[0], dy = pb[1] - pa[1];
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        out[0] = d;
        out[1] = d < 1e-4f ? 0f : ((fb.vx - fa.vx) * dx + (fb.vy - fa.vy) * dy) / d;
        return true;
    }

    /** Ends {@code e}'s EVA where they stand: back aboard, or never out. */
    private void finish(Entity e) {
        EvaComponent eva = of(e);
        if (eva == null) return;
        if (eva.portMount != null && eva.portMount.airlock != null) eva.portMount.airlock.release(e);
        if (eva.towing != null) tow.letGoOf(e, eva);
        EvaComponent holder = of(eva.heldBy);
        if (holder != null && holder.towing == e) holder.towing = null;
        e.remove(EvaComponent.class);
        // Sent out, the order is carried out once they are back in
        CrewMemberComponent c = service.crew(e);
        if (c != null && c.evaOrder == CrewMemberComponent.EvaOrder.OUT) c.evaOrder = CrewMemberComponent.EvaOrder.NONE;
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
        // Out to cross to another craft, no line is clipped on: it wouldn't reach
        eva.tethered = eva.crossTo == null;
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
        ImmutableArray<Entity> aboard = service.aboard(ship);
        for (int i = 0; i < aboard.size(); i++) {
            Entity e = aboard.get(i);
            if (e == lock.user()) continue;
            GridPositionComponent p = service.position(e);
            if (p.walkway != null || outside(e)) continue;
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
                        emit(p.ship == service.homeOf(e) ? EvaEvent.Kind.BACK : EvaEvent.Kind.ABOARD, e, p.ship, 0f);
                    }
                    return;
                }
                // In by a door forced open there is no cycle: through the hatch as it stands
                if (lock == null || eva.forcedIn || lock.claim(e, false) && lock.isIn()) {
                    ending.add(e);
                    emit(p.ship == service.homeOf(e) ? EvaEvent.Kind.BACK : EvaEvent.Kind.ABOARD, e, p.ship, 0f);
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
        eva.cargo = c.crate == null ? 0f : c.crate.mass;
        burning = exhaust.read(p.ship, grid);
        scorching.scorch(e, eva, c, p, dt);
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
            if (conscious) hullHold.hold(e, eva, c, p, grid, hull, dt);
            else hullHold.letGo(e, eva, p, 0f);
        }
        if (eva.phase == EvaComponent.Phase.FREE) flight.fly(e, eva, c, grid, hull, dt);
        if (eva.phase == EvaComponent.Phase.FREE && !eva.tethered) adrift(e, eva, c, p, grid, hull, dt);
        if (eva.tethered && port != null) tether.rope(e, eva, c, p, grid, hull, port, dt);
        else if (eva.rope != null && port != null) tether.reelIn(eva, hull, port, dt);
        else eva.rope = null;
        toShipFrame(eva, p);
        if (eva.towing != null) tow.carry(e, eva, conscious);
        if (port == null || lock == null) {
            if (!eva.tethered) driftCheck(e, eva, p);
            return;
        }
        // At the door on the way in: in once it opens, and anyone in tow with them. A hostile craft's
        // door is forced first (their behavior's work), then they go in through it as it was left
        doorRing(port, tile);
        boolean atDoor = eva.phase == EvaComponent.Phase.ON_HULL && eva.ringX == tile[0] && eva.ringY == tile[1] && eva.path.size == 0;
        boolean reeledIn = !conscious && eva.tethered && eva.lineOut <= AT_REEL;
        if (eva.forcedIn && atDoor && eva.returning) {
            if (doorOpen(grid, port)) {
                stepIn(e, eva, p);
                if (eva.towing != null) tow.inWith(eva);
            } else {
                // Shut again on them from inside: forced again
                eva.forcedIn = false;
                eva.breaking = true;
            }
        } else if (!eva.breaking && (atDoor && eva.returning || reeledIn)) {
            if (lock.claim(e, true) && lock.isOut()) {
                stepIn(e, eva, p);
                if (eva.towing != null) tow.inWith(eva);
            }
        }
        if (!eva.tethered) driftCheck(e, eva, p);
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
        return probe.burnsThrough(tether.ropeInTiles(eva.rope, query));
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
        if (board != null && retrievable(e)) {
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

    /** True if any of {@code port}'s outer doors stands open or is gone. */
    public static boolean doorOpen(ShipGridComponent grid, MountFootprint port) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                if (!DockingPortLayout.isDoorTile(port, x, y)) continue;
                EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                if (d == null || !d.blocks()) return true;
            }
        }
        return false;
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

    /** The mass {@code eva} moves: theirs and the crate they carry, and a shipmate's in tow with theirs. */
    static float mass(EvaComponent eva) {
        float m = MASS + eva.cargo;
        if (eva.towing == null) return m;
        EvaComponent o = of(eva.towing);
        return m + MASS + (o == null ? 0f : o.cargo);
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

    final float[] c4 = new float[2];

    // -----------------------------------------------------------------------
    // Off the hull
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // The tether
    // -----------------------------------------------------------------------

    static final float STEP = (float) SimClock.TICK_SECONDS;

    // -----------------------------------------------------------------------
    // Frames
    // -----------------------------------------------------------------------

    /** World-frame state from the ship-frame state saved (or set when they stepped out). */
    void initWorld(EvaComponent eva) {
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
    void toShipFrame(EvaComponent eva, GridPositionComponent p) {
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
        float dx = s.deltaX, dy = s.deltaY, dvx = s.deltaVx, dvy = s.deltaVy;
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

    void emit(EvaEvent.Kind kind, Entity e, Entity ship, float amount) {
        event.kind = kind;
        event.crew = e;
        event.ship = ship;
        event.amount = amount;
        events.dispatch(event);
        event.crew = null;
        event.ship = null;
    }

}
