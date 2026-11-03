package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.ContactState;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;

/**
 * What the HUD variants' providers say of another craft the same way: whose side it is on, what
 * state it is in, and where it is in bubble metres. One rule for the target card, the contact list
 * and the navigation readout, so they never disagree.
 */
final class HudContacts {
    private HudContacts() {
    }

    /**
     * {@code e}'s side against the player's {@code ship}: a missile, hostile (it is fighting the
     * player, or a raider), else as its marker says (a station counts as friendly), else unknown.
     */
    static Relation relation(Entity ship, PirateBase pirateBase, Entity e) {
        if (e == null) return Relation.UNKNOWN;
        if (e.getComponent(MissileComponent.class) != null) return Relation.MISSILE;
        if (GunCrews.hostile(ship, pirateBase, e) || e.getComponent(HostileComponent.class) != null) return Relation.HOSTILE;
        HudMarkerComponent marker = e.getComponent(HudMarkerComponent.class);
        if (marker == null) return e.getComponent(StationComponent.class) != null ? Relation.FRIENDLY : Relation.UNKNOWN;
        switch (marker.kind) {
            case HOSTILE: return Relation.HOSTILE;
            case FRIENDLY:
            case STATION: return Relation.FRIENDLY;
            default: return Relation.NEUTRAL;
        }
    }

    /** {@code e}'s state as {@link HostileLabel} words it, as a value: fit, running, struck, silenced, disabled or wrecked. */
    static ContactProvider.State state(PirateBase pirateBase, Entity e) {
        DisabledComponent d = e.getComponent(DisabledComponent.class);
        if (d != null) {
            switch (d.reason) {
                case SURRENDERED: return ContactProvider.State.STRUCK;
                case WRECKED: return ContactProvider.State.WRECKED;
                default: return ContactProvider.State.DISABLED;
            }
        }
        String label = HostileLabel.of(pirateBase, e);
        if (HostileLabel.SILENCED.equals(label)) return ContactProvider.State.SILENCED;
        if (HostileLabel.RUNNING.equals(label)) return ContactProvider.State.RUNNING;
        return ContactProvider.State.FIT;
    }

    /** Why {@code e} is disabled ("NO_POWER", "WRECKED"), or "". */
    static String disabledReason(Entity e) {
        DisabledComponent d = e.getComponent(DisabledComponent.class);
        return d == null ? "" : d.reason.name();
    }

    /**
     * Where {@code e} is and how it moves, bubble metres and m/s: a body, transient, missile, round
     * or decoy as combat reads it, else a marker's transform (which is in physics units) standing
     * still. False when it has neither.
     */
    static boolean read(Entity e, Vector2 pos, Vector2 vel) {
        if (ContactState.read(e, pos, vel)) return true;
        TransformComponent t = e == null ? null : e.getComponent(TransformComponent.class);
        if (t == null) return false;
        pos.set(t.x * PhysicsManager.METERS_PER_UNIT, t.y * PhysicsManager.METERS_PER_UNIT);
        vel.setZero();
        return true;
    }

    /** {@code deg} in (-180, 180]. */
    static float wrapDeg(float deg) {
        deg %= 360f;
        if (deg > 180f) deg -= 360f;
        if (deg <= -180f) deg += 360f;
        return deg;
    }

    /** {@code deg} in [0, 360). */
    static float heading(float deg) {
        deg %= 360f;
        if (deg < 0f) deg += 360f;
        // A hair under 0 comes back as 360 in floats
        return deg >= 360f ? 0f : deg;
    }
}
