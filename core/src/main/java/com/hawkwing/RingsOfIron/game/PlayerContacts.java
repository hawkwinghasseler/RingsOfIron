package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;

import java.util.Comparator;

/**
 * ui-hud's {@link ContactProvider}: every craft with a HUD marker in the bubble (ships, stations,
 * derelicts, crew adrift) and every missile in flight, as values against the player's ship: what
 * it is, whose side it is on, its state, where it is and how it closes, nearest first.
 */
public class PlayerContacts implements ContactProvider {
    private static final Family MARKED = Family.all(HudMarkerComponent.class).get();
    private static final Family MISSILES = Family.all(MissileComponent.class, ProjectileComponent.class).get();
    private static final Comparator<Contact> NEAREST = (a, b) -> Float.compare(rangeOf(a), rangeOf(b));

    private final GameContext ctx;
    private final DockingSystem docking;
    private final NavTarget navTarget;
    private final PirateBase pirateBase;
    private final Spacewalks spacewalks;
    private final ImmutableArray<Entity> marked, missiles;
    private final Vector2 pos = new Vector2(), vel = new Vector2(), shipPos = new Vector2(), shipVel = new Vector2();

    public PlayerContacts(GameContext ctx, DockingSystem docking, NavTarget navTarget, PirateBase pirateBase, Spacewalks spacewalks) {
        this.ctx = ctx;
        this.docking = docking;
        this.navTarget = navTarget;
        this.pirateBase = pirateBase;
        this.spacewalks = spacewalks;
        this.marked = ctx.engine.getEcsEngine().getEntitiesFor(MARKED);
        this.missiles = ctx.engine.getEcsEngine().getEntitiesFor(MISSILES);
    }

    @Override
    public void collect(ContactFrame out) {
        Entity ship = ctx.player();
        if (ship == null) return;
        Body body = docking.bodyOf(ship);
        if (body == null && !HudContacts.read(ship, shipPos, shipVel)) return;
        if (body != null) {
            PhysicsManager.getPosition(body, shipPos);
            PhysicsManager.getVelocity(body, shipVel);
        }
        float nose = body == null ? Float.NaN : body.getAngle();
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        Entity locked = sensor == null ? null : sensor.lockedTarget;
        Entity nav = navTarget.entity();
        for (int i = 0; i < marked.size(); i++) {
            Entity e = marked.get(i);
            if (e == ship || e.isScheduledForRemoval() || !HudContacts.read(e, pos, vel)) continue;
            HudMarkerComponent marker = e.getComponent(HudMarkerComponent.class);
            Contact c = out.add();
            c.entity = e;
            c.label = marker.label == null ? "" : marker.label;
            c.radius = marker.radius;
            c.kind = kind(e, marker);
            c.relation = HudContacts.relation(ship, pirateBase, e);
            if (c.kind == Kind.SHIP || c.kind == Kind.STATION || c.kind == Kind.DERELICT) {
                c.state = HudContacts.state(pirateBase, e);
                c.disabledReason = HudContacts.disabledReason(e);
            }
            SensorComponent theirs = e.getComponent(SensorComponent.class);
            c.threat = c.relation == Relation.HOSTILE && theirs != null && theirs.lockedTarget == ship;
            place(c, e, nose, locked, nav);
        }
        for (int i = 0; i < missiles.size(); i++) {
            Entity e = missiles.get(i);
            if (e.getComponent(HudMarkerComponent.class) != null || !HudContacts.read(e, pos, vel)) continue;
            MissileComponent m = e.getComponent(MissileComponent.class);
            Contact c = out.add();
            c.entity = e;
            c.label = m.launcher == ship ? "OUR MISSILE" : "MISSILE";
            c.kind = Kind.MISSILE;
            c.relation = Relation.MISSILE;
            c.radius = 1f;
            c.threat = m.launcher != ship && (m.target == ship || m.seekerLock == ship);
            place(c, e, nose, locked, nav);
        }
        out.contacts.sort(NEAREST);
    }

    /** Position, range, bearing from the nose (port +) and closing, from {@link #pos} and {@link #vel}. */
    private void place(Contact c, Entity e, float nose, Entity locked, Entity nav) {
        c.x = pos.x;
        c.y = pos.y;
        float rx = pos.x - shipPos.x, ry = pos.y - shipPos.y;
        float range = (float) Math.sqrt(rx * rx + ry * ry);
        c.range = range;
        if (!Float.isNaN(nose)) c.bearingDeg = HudContacts.wrapDeg((MathUtils.atan2(ry, rx) - nose) * MathUtils.radiansToDegrees);
        c.closing = range < 1e-3f ? 0f : -(rx * (vel.x - shipVel.x) + ry * (vel.y - shipVel.y)) / range;
        c.locked = e == locked;
        c.nav = e == nav;
    }

    private Kind kind(Entity e, HudMarkerComponent marker) {
        if (e.getComponent(MissileComponent.class) != null) return Kind.MISSILE;
        if (e.getComponent(StationComponent.class) != null) return Kind.STATION;
        if (spacewalks.isAdriftMarker(e)) return Kind.EVA;
        DisabledComponent d = e.getComponent(DisabledComponent.class);
        if (d != null && (d.reason == DisabledComponent.Reason.WRECKED || d.reason == DisabledComponent.Reason.ABANDONED)) {
            return Kind.DERELICT;
        }
        if (e.getComponent(ShipGridComponent.class) != null || docking.bodyOf(e) != null) return Kind.SHIP;
        // A marker with nothing behind it in the bubble: a derelict's (a wreck or pod on the rails), or a place
        return marker.kind == HudMarkerComponent.Kind.WAYPOINT || e.getComponent(TransformComponent.class) == null
                ? Kind.WAYPOINT : Kind.DERELICT;
    }

    private static float rangeOf(Contact c) {
        return Float.isNaN(c.range) ? Float.MAX_VALUE : c.range;
    }
}
