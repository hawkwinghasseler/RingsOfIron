package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.FiringSolution;

/**
 * One weapon on a ship (combat-sys/Design.md 3.3). A ship with several weapons holds them in a
 * {@link WeaponsComponent}. Angles are in the ship's body frame, radians, counter-clockwise from
 * the nose (+x). The entity links are transient; {@code CombatSaveParticipant} saves them by
 * UUID and relinks them after a load.
 */
public class WeaponMountComponent implements Component {
    public WeaponDef def;
    /** Hardpoint position, body-local meters. */
    public final Vector2 hardpoint = new Vector2();
    /** Centre of the firing arc. */
    public float facing;
    /** Where the barrel points now. */
    public float aim;
    /** Seconds until the weapon can fire again. */
    public float cooldown;
    /** What to shoot at; null holds fire. */
    public transient Entity target;
    /** Whether the gunner may fire; false only aims. */
    public boolean weaponsFree = true;
    /**
     * Gunner skill 0 (green) .. 1 (veteran): aim jitter, reaction time and burst walking.
     * ai-crew writes this from the gunner's stats.
     */
    public float gunnerySkill = 0.5f;
    /**
     * The ship's WEAPONS (or TURRET_MOUNT) subsystem entity. When set, the mount only works
     * while that system is operational and powered. Null means always available.
     */
    public transient Entity subsystem;

    /** Latest solution on the target, for the HUD lead reticle. Valid when {@code solution.valid}. */
    public final FiringSolution solution = new FiringSolution();
    /** Seconds since the target was assigned; the gunner reacts after a skill-based delay. */
    public float engagedFor;
    /** Last target, to notice a switch. */
    public transient Entity lastTarget;
    /** Rounds fired. */
    public int shotsFired;

    public WeaponMountComponent() {
    }

    public WeaponMountComponent(WeaponDef def, float hardpointX, float hardpointY, float facing) {
        this.def = def;
        this.hardpoint.set(hardpointX, hardpointY);
        this.facing = facing;
        this.aim = facing;
    }
}
