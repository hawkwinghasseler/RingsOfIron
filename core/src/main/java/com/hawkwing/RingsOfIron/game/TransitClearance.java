package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;

/**
 * Keeps ships crossing the bubble on their rails plan (ai-crew's {@link NpcPilotComponent}, which
 * flies the plan's burns and nothing else) out of the stations they pass and the berths there. When
 * one would come within {@link #CLEARANCE} beyond both hulls of a station, or of a ship docked to one,
 * in the next {@link #LOOKAHEAD} seconds, it thrusts sideways until its course misses by that much.
 * Craft berthing or leaving a station steer round others themselves ({@link BerthingSystem}).
 * Runs after the pilot, so its controls win while there is something to miss.
 */
public class TransitClearance extends FixedLogicSystem {
    /** Space kept beyond both hulls, meters: as much as a craft berthing keeps. */
    public static final float CLEARANCE = BerthingSystem.CLEARANCE;
    /** How far ahead a course is checked, seconds. */
    static final float LOOKAHEAD = 90f;

    private static final Family TRANSIT = Family.all(NpcPilotComponent.class, PhysicsBodyComponent.class,
            FlightControlComponent.class, ThrusterComponent.class).exclude(NpcCombatComponent.class, BerthingComponent.class).get();
    private static final Family BODIES = Family.all(PhysicsBodyComponent.class).get();
    private final ComponentMapper<FlightControlComponent> controls = ComponentMapper.getFor(FlightControlComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusters = ComponentMapper.getFor(ThrusterComponent.class);
    private final DockingSystem docking;
    private ImmutableArray<Entity> transit, bodies;
    private final Vector2 at = new Vector2(), vel = new Vector2(), rel = new Vector2(), v = new Vector2(),
            ov = new Vector2(), miss = new Vector2(), dv = new Vector2();

    public TransitClearance(DockingSystem docking) {
        // After NpcPilotSystem (-10), before physics reads the controls
        super(-9, (float) SimClock.TICK_SECONDS);
        this.docking = docking;
    }

    @Override
    public void addedToEngine(Engine engine) {
        transit = engine.getEntitiesFor(TRANSIT);
        bodies = engine.getEntitiesFor(BODIES);
    }

    @Override
    protected void updateFixed(float dt) {
        if (transit == null) return;
        for (int i = 0; i < transit.size(); i++) {
            Entity e = transit.get(i);
            Body body = docking.bodyOf(e);
            if (body == null || docking.dockedTo(e) != null) continue;
            if (steerClear(e, body)) push(body, controls.get(e), thrusters.get(e));
        }
    }

    /**
     * Sets {@link #dv} to the change of velocity that takes the ship's course clear of every station
     * and docked ship it would pass too close to; true if there is one.
     */
    private boolean steerClear(Entity e, Body body) {
        PhysicsManager.getPosition(body, at);
        PhysicsManager.getVelocity(body, vel);
        float mine = radius(e);
        dv.setZero();
        for (int i = 0; i < bodies.size(); i++) {
            Entity o = bodies.get(i);
            if (o == e || (o.getComponent(StationComponent.class) == null && docking.dockedTo(o) == null)) continue;
            Body ob = docking.bodyOf(o);
            if (ob == null) continue;
            PhysicsManager.getPosition(ob, rel).sub(at);
            v.set(vel).sub(PhysicsManager.getVelocity(ob, ov));
            float clear = mine + radius(o) + CLEARANCE;
            float dist = rel.len();
            if (dist < clear) {
                // Inside already: away from it
                dv.mulAdd(rel, -1f / Math.max(0.01f, dist));
                continue;
            }
            float closing = v.len2();
            if (closing < 1e-4f || dist > clear + (float) Math.sqrt(closing) * LOOKAHEAD) continue;
            float t = rel.dot(v) / closing;
            if (t <= 0f || t > LOOKAHEAD) continue;
            miss.set(v).scl(t).sub(rel); // where it would be from the other then
            float gap = miss.len();
            if (gap >= clear) continue;
            if (gap < 1e-3f) miss.set(-v.y, v.x); // dead ahead: either side will do
            dv.mulAdd(miss.nor(), (clear - gap) / Math.max(2f, t));
        }
        return !dv.isZero(1e-4f);
    }

    /** Thrusts along {@link #dv}, within the ship's thrusters, leaving its nose to the pilot. */
    private void push(Body body, FlightControlComponent c, ThrusterComponent t) {
        dv.scl(Math.max(1f, PhysicsManager.getMass(body))).rotateRad(-body.getAngle());
        c.forward = MathUtils.clamp(dv.x / Math.max(1f, dv.x >= 0f ? t.forwardThrust : t.reverseThrust), -1f, 1f);
        c.strafe = MathUtils.clamp(dv.y / Math.max(1f, t.lateralThrust), -1f, 1f);
    }

    /** Half a craft's grid's diagonal, meters, or 20 m (a freighter's hull) if it has no grid. */
    private static float radius(Entity e) {
        ShipGridComponent g = e.getComponent(ShipGridComponent.class);
        return g == null ? 20f : (float) Math.hypot(g.width, g.height) * g.tileSize * 0.5f;
    }
}
