package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.career.Contract;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.orbital.math.StateVector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * Which pirates on the rails the player knows of, and where they were last seen. A pirate is seen
 * while it is within {@link #SENSOR_RANGE} of the player or of one of the sponsor's patrols; one the
 * player holds a bounty on is known from the moment the contract is taken, at the position the
 * bounty gave. The traffic map draws known pirates, at their last known position when out of range.
 *
 * <p>Not saved: after a load, pirates are known again as soon as they are seen, and bounties at once.
 */
public class PirateSightings extends EntitySystem {
    /** How far the player's or a patrol's sensors find a pirate on the rails, m. */
    public static final double SENSOR_RANGE = 300_000;

    /** Where a pirate was last seen, relative to the body it orbited then. */
    public static final class Sighting {
        public String parentUuid;
        public final StateVector state = new StateVector();
        public double time;
        /** Seen this update; false for a last known position. */
        public boolean live;
    }

    private final GameContext ctx;
    private final PhysicsManager physics;
    private final RailsUniverse universe;
    private final MacroSimulation macroSim;
    private final ContractBoard contracts;
    private final Map<String, Sighting> seen = new HashMap<>();
    private final List<Entity> eyes = new ArrayList<>();
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    public PirateSightings(GameContext ctx, PhysicsManager physics, RailsUniverse universe, MacroSimulation macroSim, ContractBoard contracts) {
        this.ctx = ctx;
        this.physics = physics;
        this.universe = universe;
        this.macroSim = macroSim;
        this.contracts = contracts;
    }

    @Override
    public void update(float deltaTime) {
        refresh();
    }

    /** Looks again now. */
    public void refresh() {
        Map<String, HostileComponent> hostiles = macroSim.hostiles().all();
        seen.keySet().removeIf(uuid -> !hostiles.containsKey(uuid) || ctx.engine.getEntity(uuid) == null);
        eyes.clear();
        Entity player = ctx.player();
        if (player != null) eyes.add(player);
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            HostileComponent h = e.getValue();
            if (h.role == HostileComponent.Role.PATROL && HostileTraffic.SPONSOR.equals(h.faction)) {
                Entity patrol = ctx.engine.getEntity(e.getKey());
                if (patrol != null && universe.orbit(patrol) != null) eyes.add(patrol);
            }
        }
        double now = universe.now();
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            // The base's moored guns are found with the base, not sighted on the lanes
            if (!e.getValue().isPirate() || e.getValue().moored()) continue;
            Entity pirate = ctx.engine.getEntity(e.getKey());
            if (pirate == null || universe.orbit(pirate) == null) {
                // In the bubble the world markers show it; it is no longer where the map last had it
                Sighting s = seen.get(e.getKey());
                if (s != null) s.live = false;
                continue;
            }
            boolean inRange = false;
            for (Entity eye : eyes) {
                if (withinRange(pirate, eye, now)) {
                    inRange = true;
                    break;
                }
            }
            Sighting s = seen.get(e.getKey());
            if (inRange || (s == null && bountyHeld(e.getKey()))) {
                if (s == null) seen.put(e.getKey(), s = new Sighting());
                Entity parent = universe.parentOf(pirate);
                s.parentUuid = parent == null ? null : universe.uuidOf(parent);
                universe.stateInFrame(pirate, parent, now, s.state);
                s.time = now;
                s.live = inRange;
            } else if (s != null) {
                s.live = false;
            }
        }
    }

    /** Pirates the player knows of. */
    public Collection<String> known() {
        return new ArrayList<>(seen.keySet());
    }

    /** Where {@code uuid} was last seen, or null if it is not known. */
    public Sighting sighting(String uuid) {
        return seen.get(uuid);
    }

    /** True while the player holds a bounty on {@code uuid}. */
    public boolean bountyHeld(String uuid) {
        for (Contract c : contracts.active()) {
            if (c.type == Contract.Type.BOUNTY && uuid.equals(c.targetUuid)) return true;
        }
        return false;
    }

    private boolean withinRange(Entity pirate, Entity eye, double now) {
        if (!absolute(eye, now, b)) return false;
        universe.absoluteState(pirate, now, a);
        return Math.hypot(a.x - b.x, a.y - b.y) <= SENSOR_RANGE;
    }

    private final double[] trueState = new double[4];
    private final StateVector parentState = new StateVector();

    /** A craft's state relative to the root body, on the rails or in the bubble; false if it can't be placed. */
    private boolean absolute(Entity e, double now, StateVector out) {
        if (universe.orbit(e) != null) {
            universe.absoluteState(e, now, out);
            return true;
        }
        com.ringsofiron.physicsexterior.components.PhysicsBodyComponent pb =
                e.getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class);
        String anchor = physics.getAnchorParentUuid();
        Entity parent = anchor == null ? null : universe.entity(anchor);
        if (pb == null || pb.body == null || parent == null) return false;
        physics.getTrueState(pb.body, trueState);
        out.set(trueState[0], trueState[1], trueState[2], trueState[3]);
        out.add(universe.absoluteState(parent, now, parentState));
        return true;
    }
}
