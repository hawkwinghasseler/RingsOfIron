package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;

/**
 * How an NPC captain fights (round 3, enemies): a stance picked from its ship's weapons and the
 * odds, re-picked every look {@link HostileTraffic} takes at the bubble. In order:
 * <ol>
 *   <li>{@link Plan#RUN}: a pirate that has been hit ({@link #RUN_HULL}) runs when outgunned
 *       ({@link #RUN_ODDS}), or when it is under fire from several sides and not winning.
 *       Patrols never run; they break off at their hull line like before.</li>
 *   <li>{@link Plan#STANDOFF}: a ship with launchers that isn't clearly winning ({@link #STANDOFF_ODDS}),
 *       or with nothing but launchers, keeps out at missile range.</li>
 *   <li>{@link Plan#RUSH}: a ship whose only guns are short-ranged ({@link #SHORT_GUN}) closes fast.</li>
 *   <li>{@link Plan#FLANK}: a ship that clearly outguns its enemy ({@link #FLANK_ODDS}) swings round
 *       behind it to hit its engines.</li>
 *   <li>{@link Plan#HOLD}: else, hold the range band and circle, as before.</li>
 * </ol>
 * The odds are the ship's firepower times what is left of its hull (with its wing's, when it
 * fights beside it), over the enemy's.
 */
public final class Tactics {
    public enum Plan { HOLD, STANDOFF, RUSH, FLANK, RUN }

    /** Below these odds a pirate that has been hit runs. */
    public static final double RUN_ODDS = 0.5;
    /** A pirate only runs once its hull is below this: it has been hit. */
    public static final double RUN_HULL = 0.9;
    /** Below these odds a ship with launchers stays out at missile range. */
    public static final double STANDOFF_ODDS = 1.2;
    /** From these odds a ship goes round for the enemy's engines. */
    public static final double FLANK_ODDS = 1.5;
    /** A gun that reaches no further than this, m, is a short gun; a gun with no limit reaches far. */
    public static final float SHORT_GUN = 3_500f;
    /** Ranges and closing speeds of the stances, m and m/s. */
    public static final float HOLD_RANGE = 1_500f, STANDOFF_RANGE = 5_000f, RUSH_RANGE = 600f, FLANK_RANGE = 1_000f;
    public static final float HOLD_CLOSING = 80f, STANDOFF_CLOSING = 100f, RUSH_CLOSING = 160f, FLANK_CLOSING = 120f;

    private Tactics() {
    }

    /** What a ship has to fight with, point defense aside. */
    public static final class Arms {
        public boolean launchers;
        public boolean guns;
        /** Every gun it has is a short gun. */
        public boolean shortGunsOnly;
    }

    public static Arms arms(Entity ship) {
        Arms a = new Arms();
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return a;
        boolean longGun = false;
        for (WeaponMountComponent m : weapons.mounts) {
            WeaponDef d = m.def;
            if (d == null || d.pointDefense) continue;
            if (d.mount == WeaponDef.MountType.LAUNCHER) {
                a.launchers = true;
            } else {
                a.guns = true;
                if (d.maxRange <= 0f || d.maxRange > SHORT_GUN) longGun = true;
            }
        }
        a.shortGunsOnly = a.guns && !longGun && !a.launchers;
        return a;
    }

    public static Plan choose(Arms arms, double odds, double hull, boolean pirate, boolean crossfire) {
        if (pirate && hull < RUN_HULL && (odds < RUN_ODDS || (crossfire && odds < 1))) return Plan.RUN;
        if (arms.launchers && (!arms.guns || odds < STANDOFF_ODDS)) return Plan.STANDOFF;
        if (arms.shortGunsOnly) return Plan.RUSH;
        if (odds >= FLANK_ODDS) return Plan.FLANK;
        return Plan.HOLD;
    }

    /** Sets {@code combat} up to fight to {@code plan} (anything but {@link Plan#RUN}). */
    public static void apply(NpcCombatComponent combat, Plan plan) {
        switch (plan) {
            case STANDOFF:
                set(combat, NpcCombatComponent.Stance.STANDOFF, STANDOFF_RANGE, STANDOFF_CLOSING);
                break;
            case RUSH:
                set(combat, NpcCombatComponent.Stance.RUSH, RUSH_RANGE, RUSH_CLOSING);
                break;
            case FLANK:
                set(combat, NpcCombatComponent.Stance.FLANK, FLANK_RANGE, FLANK_CLOSING);
                break;
            default:
                set(combat, NpcCombatComponent.Stance.HOLD, HOLD_RANGE, HOLD_CLOSING);
                break;
        }
    }

    private static void set(NpcCombatComponent combat, NpcCombatComponent.Stance stance, float range, float closing) {
        combat.stance = stance;
        combat.preferredRange = range;
        combat.maxClosingSpeed = closing;
    }
}
