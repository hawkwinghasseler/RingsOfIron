package com.ringsofiron.shipsystems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * A craft's RCS thruster blocks set how hard it can turn, strafe and back up. Each working nozzle
 * pushes the ship away from the way it fires, from where it sits on the hull, so it adds to the
 * retro thrust if it fires forward, to the strafe one way or the other if it fires to a side, and
 * to the turn one way or the other by its lever arm about the centre of mass. The ship strafes and
 * turns each way as hard as the nozzles pushing that way allow ({@link ThrusterComponent#lateral},
 * {@link ThrusterComponent#torque}), so every nozzle drawn firing ({@link #firing}) pushes; planners
 * go by the weaker way ({@link ThrusterComponent#lateralThrust}, {@link ThrusterComponent#maxTorque}).
 * A block stops working when the hull tile under it is destroyed, so a craft that loses its bow
 * thrusters turns slower, and one that loses a side's strafes only the other way.
 *
 * <p>A craft with no RCS blocks keeps its ship's figures ({@code ships.toml}, and the engines'
 * share in {@link EngineThrust}). The main engines stay the forward thrust either way.
 *
 * <p>Runs every frame after {@link EngineThrust}, before flight control reads the thrusters.
 */
public class RcsThrust extends EntitySystem {
    private static final Family SHIPS = Family.all(ThrusterComponent.class, ShipGridComponent.class, PhysicsBodyComponent.class).get();
    private static final ComponentMapper<ThrusterComponent> THRUSTERS = ComponentMapper.getFor(ThrusterComponent.class);
    private static final ComponentMapper<ShipGridComponent> GRIDS = ComponentMapper.getFor(ShipGridComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<FlightControlComponent> CONTROLS = ComponentMapper.getFor(FlightControlComponent.class);

    /** Below this spin, rad/s, rotation assist has nothing to stop and fires nothing. */
    static final float ASSIST_SPIN = 0.01f;

    /** What a craft's RCS can do: newtons and newton-meters, each way. */
    public static final class Authority {
        public double retro, port, starboard, ccw, cw;

        public double lateral() {
            return Math.min(port, starboard);
        }

        public double torque() {
            return Math.min(ccw, cw);
        }
    }

    private ImmutableArray<Entity> ships;
    private final Authority authority = new Authority();
    private final Vector2 center = new Vector2();

    public RcsThrust() {
        super(PropellantFeed.DEFAULT_PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) apply(ships.get(i));
    }

    /** Sets one craft's turning, strafing and retro thrust from its RCS. False, changing nothing, for one with none. */
    public boolean apply(Entity ship) {
        ShipGridComponent grid = GRIDS.get(ship);
        Body body = BODIES.get(ship).body;
        if (grid == null || grid.rcsThrusters.size == 0 || body == null) return false;
        authority(grid, centerOfMass(body, center), authority);
        ThrusterComponent t = THRUSTERS.get(ship);
        t.reverseThrust = (float) authority.retro;
        t.lateralThrust = (float) authority.lateral();
        t.maxTorque = (float) authority.torque();
        t.portThrust = (float) authority.port;
        t.starboardThrust = (float) authority.starboard;
        t.ccwTorque = (float) authority.ccw;
        t.cwTorque = (float) authority.cw;
        return true;
    }

    /** The body's centre of mass in its own frame, meters. */
    public static Vector2 centerOfMass(Body body, Vector2 out) {
        return out.set(body.getLocalCenter()).scl(PhysicsManager.METERS_PER_UNIT);
    }

    /** What the working RCS of {@code grid} can do about {@code center} (ship-local meters), into {@code out}. */
    public static Authority authority(ShipGridComponent grid, Vector2 center, Authority out) {
        out.retro = out.port = out.starboard = out.ccw = out.cw = 0;
        for (RcsThruster r : grid.rcsThrusters) {
            if (!r.works(grid)) continue;
            double f = r.nozzleThrust();
            for (Side exhaust : r.nozzles()) {
                // It pushes the opposite way to its exhaust
                double fx = -exhaust.dx * f, fy = -exhaust.dy * f;
                double lx = grid.gridOriginLocal.x + r.nozzleX(exhaust) * grid.tileSize - center.x;
                double ly = grid.gridOriginLocal.y + r.nozzleY(exhaust) * grid.tileSize - center.y;
                double torque = lx * fy - ly * fx;
                out.retro += Math.max(0, -fx);
                out.port += Math.max(0, fy);
                out.starboard += Math.max(0, -fy);
                out.ccw += Math.max(0, torque);
                out.cw += Math.max(0, -torque);
            }
        }
        return out;
    }

    /**
     * How hard nozzle {@code exhaust} of block {@code r} fires, 0 to 1, for what the pilot asks:
     * any nozzle whose push helps the retro burn, the strafe or the turn fires, at the strongest
     * of the three it helps. With rotation assist on and no turn asked for, the turn is the one
     * that stops {@code spin} (rad/s), as hard as flight control's trim last was
     * ({@link FlightControlComponent#assistTurn}). Nothing fires out of fuel, or from a block that no longer works.
     */
    public static float firing(ShipGridComponent grid, RcsThruster r, Side exhaust, Vector2 center,
                               FlightControlComponent c, ThrusterComponent t, float spin) {
        if (c == null || (t != null && t.isOutOfFuel()) || !r.works(grid)) return 0f;
        float turn = c.turn;
        if (turn == 0f && c.rotationAssist && !c.assistHeld && Math.abs(spin) > ASSIST_SPIN) turn = -Math.signum(spin) * c.assistTurn;
        float fx = -exhaust.dx, fy = -exhaust.dy;
        float lx = grid.gridOriginLocal.x + r.nozzleX(exhaust) * grid.tileSize - center.x;
        float ly = grid.gridOriginLocal.y + r.nozzleY(exhaust) * grid.tileSize - center.y;
        float torque = lx * fy - ly * fx;
        float out = 0f;
        if (c.forward < 0f && fx < 0f) out = Math.max(out, Math.min(1f, -c.forward));
        if (c.strafe * fy > 0f) out = Math.max(out, Math.min(1f, Math.abs(c.strafe)));
        if (turn * torque > 1e-4f) out = Math.max(out, Math.min(1f, Math.abs(turn)));
        return out;
    }

    /** The thrust of a nozzle whose jet is drawn at full size ({@link #jetSize}): a standard linear block's, newtons. */
    static final double FULL_JET = RcsThruster.Kind.LINEAR.standardThrust;
    /** The most a jet is drawn, of a {@link #FULL_JET} nozzle's, however hard its nozzle pushes. */
    public static final float MAX_JET = 2f;

    /**
     * How big block {@code r}'s jets are drawn at full throttle, of a {@link #FULL_JET} nozzle's: as
     * the square root of the thrust it gives (a nozzle's mouth grows with its thrust), up to
     * {@link #MAX_JET}. A standard quad's 100 kN jets are drawn a little over half as big as a
     * linear block's 300 kN one, so each jet shows how hard it pushes.
     */
    public static float jetSize(RcsThruster r) {
        return Math.min(MAX_JET, (float) Math.sqrt(r.nozzleThrust() / FULL_JET));
    }

    /** {@link #firing} for a craft's own controls and body. */
    public static float firing(Entity ship, RcsThruster r, Side exhaust, Vector2 center) {
        ShipGridComponent grid = GRIDS.get(ship);
        PhysicsBodyComponent pb = BODIES.get(ship);
        float spin = pb == null || pb.body == null ? 0f : pb.body.getAngularVelocity();
        return firing(grid, r, exhaust, center, CONTROLS.get(ship), THRUSTERS.get(ship), spin);
    }
}
