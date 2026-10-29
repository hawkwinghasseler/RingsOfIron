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
    /** Whether the gunner may fire on their own; false only aims, and fires on {@link #trigger}. */
    public boolean weaponsFree = true;
    /**
     * Held by the player: fire whenever loaded, as soon as the barrel is on the solution (or at
     * once, straight down the barrel, with nothing to aim at). Cleared by the caller.
     */
    public boolean trigger;
    /**
     * Aimed by hand: while set, the barrel slews onto {@link #aimPoint} rather than a target, and
     * the trigger fires once it is there. Set by whoever has the gun in hand, each frame.
     */
    public transient boolean manualAim;
    /** Where a hand-aimed barrel points, local meters (the bubble frame). */
    public final transient Vector2 aimPoint = new Vector2();
    /**
     * Launcher: missiles left in it; -1 until weapon control first fills it to its magazine.
     * Unused by a weapon with no magazine.
     */
    public int ammo = -1;
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
    /**
     * Partly unbolted: crew are swapping it for another weapon, and it doesn't fire until the new
     * one is on. Set each frame by whoever does the work; not saved.
     */
    public transient boolean underRefit;

    /** Latest solution on the target, for the HUD lead reticle. Valid when {@code solution.valid}. */
    public final FiringSolution solution = new FiringSolution();
    /** Seconds since the target was assigned; the gunner reacts after a skill-based delay. */
    public float engagedFor;
    /** Last target, to notice a switch. */
    public transient Entity lastTarget;
    /**
     * What the mount is actually engaging this tick: {@link #target}, else the ship's locked
     * target, or for point defense the most urgent incoming missile. Set by weapon control.
     */
    public transient Entity engaged;
    /**
     * How far this mount opens up firing at will on {@link #engaged}: the range where its chance a
     * shot reaches {@code HitChance.OPEN_FIRE} against that target dodging. Kept by weapon control,
     * refreshed now and then.
     */
    public transient float effectiveRange;
    /** The target {@link #effectiveRange} was worked out for, and seconds since. */
    public transient Entity rangedFor;
    public transient float rangedAge;
    /** Whether the barrel was on the solution last tick. */
    public boolean onTarget;
    /** The hardpoint in the bubble frame last tick, local meters. Set by weapon control. */
    public final transient Vector2 muzzle = new Vector2();
    /** Rounds fired, or beam pulses started. */
    public int shotsFired;

    /**
     * Where the gunner's aim is off, radians, on top of each shot's jitter. A new target or a
     * pause starts it fresh; each shot of a burst then walks it in by part of that shot's miss.
     * For a beam it is the wander the gunner fights to hold the spot steady.
     */
    public float aimBias;
    /** Whether {@link #aimBias} belongs to the current engagement. */
    public transient boolean biasSet;
    /** Seconds since the last shot. */
    public float sinceShot = Float.MAX_VALUE;

    /** Seconds left in the beam pulse now burning, 0 when off. */
    public float beamTime;
    /** Whether the beam burned this tick. Set by weapon control. */
    public transient boolean beamOn;
    /** Share of its power the beam got this tick, 0 to 1; it burns that much of its beam power. */
    public transient float beamStrength = 1f;
    /** World direction of the beam this tick, unit. Set by weapon control. */
    public final transient Vector2 beamDir = new Vector2();
    /** Where the beam ended this tick (a hull, or its full reach), local meters. Set by BeamSystem. */
    public final transient Vector2 beamEnd = new Vector2();
    /** What the beam touched last tick, to tell a new contact from a held one. Set by BeamSystem. */
    public transient Entity beamContact;
    /** A beam turned on an incoming missile this tick: lasers are point defense too. */
    public transient boolean defending;

    public WeaponMountComponent() {
    }

    public WeaponMountComponent(WeaponDef def, float hardpointX, float hardpointY, float facing) {
        this.def = def;
        this.hardpoint.set(hardpointX, hardpointY);
        this.facing = facing;
        this.aim = facing;
    }
}
