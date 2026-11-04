package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.TargetStatusProvider;

/**
 * ui-hud's {@link TargetStatusProvider}: the player's locked target from the ship's sensor track,
 * against the ship's own motion: range, closing, closest approach and whether it is on the player.
 */
public class PlayerTargetStatus implements TargetStatusProvider {
    private final GameContext ctx;
    private final DockingSystem docking;
    private final SensorTrackingSystem sensors;
    private final PirateBase pirateBase;
    private final Vector2 pos = new Vector2(), vel = new Vector2(), shipPos = new Vector2(), shipVel = new Vector2();
    private final ShipAlerts hull = new ShipAlerts();

    public PlayerTargetStatus(GameContext ctx, DockingSystem docking, SensorTrackingSystem sensors, PirateBase pirateBase) {
        this.ctx = ctx;
        this.docking = docking;
        this.sensors = sensors;
        this.pirateBase = pirateBase;
    }

    @Override
    public void read(TargetStatus out) {
        Entity ship = ctx.player();
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        Entity target = sensor == null ? null : sensor.lockedTarget;
        if (target == null) return;
        out.present = true;
        HudMarkerComponent marker = target.getComponent(HudMarkerComponent.class);
        out.label = marker != null ? marker.label : "contact";
        out.relation = relation(ship, target, marker);
        if (target.getComponent(MissileComponent.class) == null) out.state = HostileLabel.of(pirateBase, target);
        SensorComponent theirs = target.getComponent(SensorComponent.class);
        out.firingOnUs = out.relation == Relation.HOSTILE && theirs != null && theirs.lockedTarget == ship;
        ShipGridComponent grid = target.getComponent(ShipGridComponent.class);
        if (grid != null) out.hull = hull.scan(grid).hullIntegrity;

        Track track = sensor.trackFor(target);
        Body body = docking.bodyOf(ship);
        if (track == null || body == null) return;
        out.trackAge = sensors.ageOf(track);
        HudContacts.sensed(sensor, sensors, target, pos, vel);
        PhysicsManager.getPosition(body, shipPos);
        PhysicsManager.getVelocity(body, shipVel);
        float rx = pos.x - shipPos.x, ry = pos.y - shipPos.y;
        float vx = vel.x - shipVel.x, vy = vel.y - shipVel.y;
        out.range = (float) Math.sqrt(rx * rx + ry * ry);
        out.relativeSpeed = (float) Math.sqrt(vx * vx + vy * vy);
        out.closing = out.range < 1e-3f ? 0f : -(rx * vx + ry * vy) / out.range;
        float v2 = vx * vx + vy * vy;
        float t = v2 < 1e-6f ? Float.NaN : -(rx * vx + ry * vy) / v2;
        if (t > 0f) {
            out.cpaTime = t;
            out.cpaDistance = (float) Math.hypot(rx + vx * t, ry + vy * t);
        } else {
            out.cpaDistance = out.range;
        }
        out.bearingDeg = wrapDeg((MathUtils.atan2(ry, rx) - body.getAngle()) * MathUtils.radiansToDegrees);
        if (vel.len2() > 1e-4f && shipVel.len2() > 1e-4f) {
            out.aspectDeg = wrapDeg(vel.angleDeg() - shipVel.angleDeg());
        }
    }

    private Relation relation(Entity ship, Entity target, HudMarkerComponent marker) {
        if (target.getComponent(MissileComponent.class) != null) return Relation.MISSILE;
        if (GunCrews.hostile(ship, pirateBase, target) || target.getComponent(HostileComponent.class) != null) return Relation.HOSTILE;
        if (marker == null) return Relation.UNKNOWN;
        switch (marker.kind) {
            case HOSTILE: return Relation.HOSTILE;
            case FRIENDLY:
            case STATION: return Relation.FRIENDLY;
            default: return Relation.NEUTRAL;
        }
    }

    private static float wrapDeg(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
