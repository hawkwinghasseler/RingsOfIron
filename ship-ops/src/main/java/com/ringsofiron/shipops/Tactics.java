package com.ringsofiron.shipops;

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
 *       ({@link #RUN_ODDS}), or when it is under fire from several sides and not winning; but not
 *       while it is still closing to its guns' reach (round 6: it came to fight, and used to turn
 *       for home before it had fired a round). Patrols and aces never run; they break off at their
 *       hull line like before.</li>
 *   <li>{@link Plan#STANDOFF}: a ship with launchers and long guns that isn't clearly winning
 *       ({@link #STANDOFF_ODDS}), or with nothing but launchers, keeps out at missile range. One whose
 *       guns are all short closes to use them instead (round 5: the pirate gunship stood off at 5 km
 *       with a chaingun that reaches 3).</li>
 *   <li>{@link Plan#FLANK}: a wingman fighting beside its leader swings round behind the enemy, so the
 *       wing has it in a crossfire (round 5).</li>
 *   <li>{@link Plan#KITE}: a ship whose guns all reach far keeps out past the enemy's short guns
 *       ({@link #KITE_RANGE}) and backs off when it closes, unless it clearly outguns it; an ace with
 *       long guns always does (round 5).</li>
 *   <li>{@link Plan#RUSH}: a ship whose only guns are short-ranged ({@link #SHORT_GUN}) closes fast.</li>
 *   <li>{@link Plan#FLANK}: a ship that clearly outguns its enemy ({@link #FLANK_ODDS}) swings round
 *       behind it to hit its engines.</li>
 *   <li>{@link Plan#HOLD}: else, hold the range band and circle, as before.</li>
 * </ol>
 * The odds are the ship's firepower times what is left of its hull (with its wing's, when it
 * fights beside it), over the enemy's.
 */
public final class Tactics {
    public enum Plan { HOLD, STANDOFF, RUSH, FLANK, KITE, RUN }

    /** Below these odds a pirate that has been hit runs. */
    public static final double RUN_ODDS = 0.5;
    /**
     * A pirate only runs once its hull is below this: it has been hit more than once. Round 4's 0.9
     * sent a raider off at the first hit, before it was ever in its own guns' range.
     */
    public static final double RUN_HULL = 0.8;
    /** Below these odds a ship with launchers stays out at missile range. */
    public static final double STANDOFF_ODDS = 1.2;
    /** From these odds a ship goes round for the enemy's engines. */
    public static final double FLANK_ODDS = 1.5;
    /** A gun that reaches no further than this, m, is a short gun; a gun with no limit reaches far. */
    public static final float SHORT_GUN = 3_500f;
    /** Ranges and closing speeds of the stances, m and m/s. */
    public static final float HOLD_RANGE = 1_500f, STANDOFF_RANGE = 5_000f, RUSH_RANGE = 600f, FLANK_RANGE = 1_000f;
    public static final float HOLD_CLOSING = 80f, STANDOFF_CLOSING = 100f, RUSH_CLOSING = 240f, FLANK_CLOSING = 120f;
    /**
     * How close an inbound missile comes before a ship breaks across its line, m. A rushing ship holds
     * its run in until {@link #RUSH_EVADE}: round 6 found lone raiders spending a fight weaving round
     * the corvette's harpoons from 3 km out, beaten before their chainguns ever reached (3 of 10
     * fired nothing). Was 3,000 m and 160 m/s for a rush.
     */
    public static final float EVADE_RANGE = 3_000f, RUSH_EVADE = 1_000f;
    /**
     * A kiting ship's range, m: past the short guns, and no further than {@link #KITE_REACH} of its own
     * shortest-reaching gun. It opens the range again at {@link #KITE_CLOSING}.
     */
    public static final float KITE_RANGE = 4_500f, KITE_CLOSING = 140f;
    public static final float KITE_REACH = 0.85f;

    private Tactics() {
    }

    /** What a ship has to fight with, point defense aside. */
    public static final class Arms {
        public boolean launchers;
        public boolean guns;
        /** It has guns, and every one of them is a short gun (launchers aside). */
        public boolean shortGunsOnly;
        /** It has guns and none of them is a short gun. */
        public boolean longGunsOnly;
        /** How far its shortest-reaching gun reaches, m; infinite when none has a limit. */
        public float reach = Float.POSITIVE_INFINITY;
    }

    public static Arms arms(Entity ship) {
        Arms a = new Arms();
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return a;
        boolean longGun = false, shortGun = false;
        for (WeaponMountComponent m : weapons.mounts) {
            WeaponDef d = m.def;
            if (d == null || d.pointDefense) continue;
            if (d.mount == WeaponDef.MountType.LAUNCHER) {
                // An empty launcher is no reason to stand off
                if (d.magazine > 0 && m.ammo == 0) continue;
                a.launchers = true;
            } else {
                a.guns = true;
                if (d.maxRange <= 0f || d.maxRange > SHORT_GUN) longGun = true;
                else shortGun = true;
                if (d.maxRange > 0f) a.reach = Math.min(a.reach, d.maxRange);
            }
        }
        a.shortGunsOnly = a.guns && !longGun;
        a.longGunsOnly = longGun && !shortGun;
        return a;
    }

    public static Plan choose(Arms arms, double odds, double hull, boolean pirate, boolean crossfire) {
        return choose(arms, odds, hull, pirate, crossfire, false, false);
    }

    /**
     * As {@link #choose(Arms, double, double, boolean, boolean)}, for an {@code ace} (it never runs:
     * the caller passes {@code pirate} false for it) and a {@code wingman} fighting beside its leader.
     */
    public static Plan choose(Arms arms, double odds, double hull, boolean pirate, boolean crossfire, boolean ace,
            boolean wingman) {
        return choose(arms, odds, hull, pirate, crossfire, ace, wingman, false);
    }

    /**
     * As {@link #choose(Arms, double, double, boolean, boolean, boolean, boolean)}, for a ship that is
     * {@code beyondReach}: further from its enemy than its shortest-reaching gun reaches, so it does
     * not run yet.
     */
    public static Plan choose(Arms arms, double odds, double hull, boolean pirate, boolean crossfire, boolean ace,
            boolean wingman, boolean beyondReach) {
        if (pirate && !beyondReach && hull < RUN_HULL && (odds < RUN_ODDS || (crossfire && odds < 1))) return Plan.RUN;
        if (arms.launchers && (!arms.guns || (!arms.shortGunsOnly && odds < STANDOFF_ODDS))) return Plan.STANDOFF;
        if (wingman && arms.guns && !arms.longGunsOnly) return Plan.FLANK;
        if (arms.longGunsOnly && (ace || odds < FLANK_ODDS)) return Plan.KITE;
        if (arms.shortGunsOnly) return Plan.RUSH;
        if (odds >= FLANK_ODDS) return Plan.FLANK;
        return Plan.HOLD;
    }

    /** The range a ship armed with {@code arms} kites at. */
    public static float kiteRange(Arms arms) {
        return Math.min(KITE_RANGE, KITE_REACH * arms.reach);
    }

    /** Sets {@code combat} up to fight to {@code plan} (anything but {@link Plan#RUN}). */
    public static void apply(NpcCombatComponent combat, Plan plan) {
        apply(combat, plan, null);
    }

    /** As {@link #apply(NpcCombatComponent, Plan)}; a kite keeps inside the reach of {@code arms}, when given. */
    public static void apply(NpcCombatComponent combat, Plan plan, Arms arms) {
        switch (plan) {
            case KITE:
                set(combat, NpcCombatComponent.Stance.KITE, arms == null ? KITE_RANGE : kiteRange(arms), KITE_CLOSING);
                break;
            case STANDOFF:
                set(combat, NpcCombatComponent.Stance.STANDOFF, STANDOFF_RANGE, STANDOFF_CLOSING);
                break;
            case RUSH:
                set(combat, NpcCombatComponent.Stance.RUSH, RUSH_RANGE, RUSH_CLOSING);
                combat.evadeRange = RUSH_EVADE;
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
        combat.evadeRange = EVADE_RANGE;
        combat.stance = stance;
        combat.preferredRange = range;
        combat.maxClosingSpeed = closing;
    }
}
