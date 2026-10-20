package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * The player's ship's bridge: who sits at which console, and who has the helm.
 *
 * <p>Seats (GDD §6): the player's avatar sits at the helm or weapons console it stands beside
 * ({@link #sit}), which is what lets the screen show the ship from outside. It stays seated until it
 * stands ({@link #stand}), or until it can't stay: it is knocked out or no longer beside the console,
 * the console is destroyed, or the ship is no longer the player's. Then {@link #unseated} says why.
 *
 * <p>The helm: only the player sitting at the helm flies the ship. Whenever they are not, the
 * ship's {@link FlightControlComponent} is taken over every frame. A crew member manning the helm
 * (ai-crew's {@code ManConsoleTask}) keeps the main engine and strafing off, keeps rotation assist
 * on and turns the ship back to the heading it had when the player left the helm. With nobody at
 * the helm, or a helm that is unpowered or disabled, nothing fires at all and the ship drifts and
 * spins as it was. A helm that is not working also gives the seated player no control. Only ships
 * with a {@link HelmComponent} are run this way; docked ones are left to the dock. A helmsman
 * with an order from the player flies it instead of holding the heading ({@link HelmOrders}).
 *
 * <p>Runs every physics tick, before physics and after the screen has set the frame's input.
 */
public class Bridge extends FixedLogicSystem {
    /** How near the console's footprint the avatar must stand to sit at it, tiles. */
    public static final float SIT_REACH = 1f;
    /** How far a seated avatar may be pushed off before it counts as having left, tiles. */
    public static final float STAY_REACH = 1.5f;
    /** A helmsman turns back towards the held heading at this many rad/s per radian off it. */
    public static final float HOLD_GAIN = 1.5f;
    /** ... and no faster than this, rad/s. */
    public static final float HOLD_RATE = 0.5f;
    /** Within this of the held heading, and turning slower than {@link #HOLD_SETTLED_RATE}, rotation assist just holds it. */
    public static final float HOLD_TOLERANCE = 0.003f;
    public static final float HOLD_SETTLED_RATE = 0.005f;
    /** Runs before every tick system at the default priority, physics included. */
    public static final int PRIORITY = -1;

    private static final ComponentMapper<SeatComponent> SEATS = ComponentMapper.getFor(SeatComponent.class);
    private static final ComponentMapper<HelmComponent> HELMS = ComponentMapper.getFor(HelmComponent.class);
    private static final ComponentMapper<GridPositionComponent> POSITIONS = ComponentMapper.getFor(GridPositionComponent.class);
    private static final ComponentMapper<CrewMemberComponent> CREW = ComponentMapper.getFor(CrewMemberComponent.class);
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    /** Why the avatar stood up without being asked to: "You left the helm", ... */
    public final Signal<String> unseated = new Signal<>();

    /** For testing ({@code -Dview.anywhere=true}): the player has the helm wherever they are, as before seats. */
    public boolean playerAlwaysAtHelm;

    private final GameWorld world;
    private ImmutableArray<Entity> crew;

    public Bridge(GameWorld world) {
        super(PRIORITY, PhysicsManager.TIME_STEP);
        this.world = world;
    }

    @Override
    public void addedToEngine(Engine engine) {
        crew = engine.getEntitiesFor(CrewService.CREW);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        crew = null;
    }

    /** Gives a ship the player commands a helm; does nothing if it has one. */
    public static HelmComponent install(Entity ship) {
        HelmComponent helm = HELMS.get(ship);
        if (helm == null) {
            helm = new HelmComponent();
            ship.add(helm);
        }
        return helm;
    }

    /**
     * Sits the avatar at the helm or weapons console of the player's ship it stands beside, the
     * nearer if both. Returns null once seated (or already seated there), else why it can't.
     */
    public String sit() {
        return sit(null);
    }

    /** Sits the avatar at the console of {@code type} (HELM or WEAPONS; null for the nearer) beside it. */
    public String sit(SubsystemType type) {
        Entity avatar = world.avatar.getAvatar();
        Entity ship = world.getPlayer();
        if (avatar == null || ship == null) return "You're not aboard";
        GridPositionComponent pos = POSITIONS.get(avatar);
        if (pos.ship != ship) return "Your ship's consoles are aboard your ship";
        CrewMemberComponent c = CREW.get(avatar);
        if (c != null && !c.isConscious()) return "You're in no state to";
        SubsystemComponent best = null;
        float nearest = SIT_REACH;
        for (SubsystemType t : new SubsystemType[]{SubsystemType.HELM, SubsystemType.WEAPONS}) {
            if (type != null && t != type) continue;
            SubsystemComponent s = console(ship, t);
            if (s == null) continue;
            float d = reach(pos, s.footprint);
            if (d <= nearest + 1e-4f && (best == null || d < nearest - 1e-4f)) {
                best = s;
                nearest = d;
            }
        }
        if (best == null) return "Stand at the helm or the weapons console to sit at it";
        SeatComponent seat = SEATS.get(avatar);
        if (seat == null) avatar.add(new SeatComponent(best.type));
        else seat.console = best.type;
        PlayerAvatarComponent figure = avatar.getComponent(PlayerAvatarComponent.class);
        if (figure != null) figure.seated = true;
        pos.path.clear();
        return null;
    }

    /** Stands the avatar up from its console, if it is seated. */
    public void stand() {
        Entity avatar = world.avatar.getAvatar();
        if (avatar == null) return;
        avatar.remove(SeatComponent.class);
        PlayerAvatarComponent figure = avatar.getComponent(PlayerAvatarComponent.class);
        if (figure != null) figure.seated = false;
    }

    /** The console the avatar sits at, or null. */
    public SubsystemType seat() {
        Entity avatar = world.avatar.getAvatar();
        SeatComponent seat = avatar == null ? null : SEATS.get(avatar);
        return seat == null ? null : seat.console;
    }

    /** True while the player sits at the helm of their ship. */
    public boolean playerAtHelm() {
        return playerAlwaysAtHelm || seat() == SubsystemType.HELM;
    }

    /** True while the player sits at the weapons console of their ship. */
    public boolean playerAtWeapons() {
        return seat() == SubsystemType.WEAPONS;
    }

    /** Why the player at the helm can't fly the ship ("the helm has no power"), or null when they can. */
    public String helmFault(Entity ship) {
        SubsystemComponent helm = ship == null ? null : console(ship, SubsystemType.HELM);
        if (helm == null) return "no helm";
        if (helm.isDisabled()) return "the helm is wrecked";
        if (!helm.switchedOn) return "the helm is switched off";
        if (!helm.isOperational) return "the helm has no power";
        return null;
    }

    /** The crew member (not the player) manning {@code ship}'s helm, or null. */
    public Entity helmsman(Entity ship) {
        if (crew == null || ship == null) return null;
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            if (POSITIONS.get(e).ship != ship || e.getComponent(PlayerAvatarComponent.class) != null) continue;
            if (CrewQueries.isManning(e, SubsystemType.HELM)) return e;
        }
        return null;
    }

    /** The first console of {@code type} aboard {@code ship} that is not destroyed, or null. */
    public static SubsystemComponent console(Entity ship, SubsystemType type) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = SUBSYSTEMS.get(e);
            if (s != null && s.type == type && !s.isDestroyed()) return s;
        }
        return null;
    }

    /** How far the crew member is from the footprint's tiles, in tiles; 0 on it. */
    static float reach(GridPositionComponent pos, MountFootprint f) {
        float dx = Math.max(Math.max(f.originX - pos.x, pos.x - (f.originX + f.width())), 0f);
        float dy = Math.max(Math.max(f.originY - pos.y, pos.y - (f.originY + f.height())), 0f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    protected void updateFixed(float deltaTime) {
        checkSeat();
        Entity ship = world.getPlayer();
        // Flying with the player away from the helm, a crewman takes it ahead of a patient
        ShipTaskBoardComponent board = ship == null ? null : world.crew.board(ship);
        if (board != null) board.helmWanted = !playerAtHelm() && world.docking.dockedTo(ship) == null;
        HelmComponent helm = ship == null ? null : HELMS.get(ship);
        if (helm != null) steer(ship, helm, deltaTime);
    }

    /** Stands the avatar up if it can no longer stay at its console. */
    private void checkSeat() {
        Entity avatar = world.avatar.getAvatar();
        SeatComponent seat = avatar == null ? null : SEATS.get(avatar);
        PlayerAvatarComponent figure = avatar == null ? null : avatar.getComponent(PlayerAvatarComponent.class);
        if (figure != null) figure.seated = seat != null;
        if (seat == null) return;
        String why = null;
        String name = seat.console == SubsystemType.HELM ? "the helm" : "the weapons console";
        GridPositionComponent pos = POSITIONS.get(avatar);
        CrewMemberComponent c = CREW.get(avatar);
        Entity ship = world.getPlayer();
        SubsystemComponent console = ship == null ? null : console(ship, seat.console);
        if (ship == null || pos.ship != ship) why = "You left your ship";
        else if (c != null && !c.isConscious()) why = "You were knocked out at " + name;
        else if (console == null) why = "The " + name.substring(4) + " is destroyed";
        else if (pos.walkway != null || reach(pos, console.footprint) > STAY_REACH) why = "You left " + name;
        if (why == null) return;
        avatar.remove(SeatComponent.class);
        if (figure != null) figure.seated = false;
        unseated.dispatch(why);
    }

    private void steer(Entity ship, HelmComponent helm, float deltaTime) {
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        Body body = pb == null ? null : pb.body;
        if (controls == null) return;
        boolean player = playerAtHelm();
        if (player) {
            if (!helm.playerHad) {
                // Taking the helm from the helmsman: nothing the autopilot had set carries on
                controls.clear();
                controls.rotationAssist = helm.playerAssist;
            }
            helm.playerHad = true;
            if (helmFault(ship) != null) controls.clear();
            if (body != null) helm.heldAngle = body.getAngle();
            helm.holding = true;
            return;
        }
        if (helm.playerHad) {
            helm.playerAssist = controls.rotationAssist;
            helm.playerHad = false;
            if (body != null) {
                helm.heldAngle = body.getAngle();
                helm.holding = true;
            }
        }
        controls.clear();
        if (world.docking.dockedTo(ship) != null) return;
        boolean manned = helmFault(ship) == null && helmsman(ship) != null;
        controls.rotationAssist = manned;
        if (!manned || body == null) return;
        if (!helm.holding) {
            helm.heldAngle = body.getAngle();
            helm.holding = true;
        }
        // A standing order from the player: the autopilot
        if (world.nav != null && world.nav.helm.steer(ship, helm, controls, body, deltaTime)) return;
        controls.turn = holdTurn(body, ship.getComponent(ThrusterComponent.class), helm.heldAngle);
    }

    /** Stick input that turns {@code body} back to {@code heading} without overshooting, -1 to 1. */
    static float holdTurn(Body body, ThrusterComponent thruster, float heading) {
        float error = heading - body.getAngle();
        error = MathUtils.atan2(MathUtils.sin(error), MathUtils.cos(error));
        float spin = body.getAngularVelocity();
        if (Math.abs(error) < HOLD_TOLERANCE && Math.abs(spin) < HOLD_SETTLED_RATE) return 0f;
        float inertia = PhysicsManager.getInertia(body);
        float accel = thruster == null || inertia <= 0f ? 0f : thruster.maxTorque / inertia;
        if (accel <= 0f) return 0f;
        // Turn back at a rate it can still brake from in the angle left, at half its torque
        float want = Math.min(Math.min(Math.abs(error) * HOLD_GAIN, HOLD_RATE),
                (float) Math.sqrt(accel * Math.abs(error)));
        want = Math.copySign(want, error);
        return MathUtils.clamp((want - spin) / (accel * 0.25f), -1f, 1f);
    }
}
