package com.ringsofiron.uihud.data;

/**
 * What carries a warning notice in place of the news ({@link MessageFeed.Message#alert}), so a HUD that
 * sorts the game's warnings into alerts and news (the Task spec's 9.2, "Where the game's warning notices
 * go") can tell the one from the other. Each is the alert the warning becomes, by 9.2's names, or one
 * of the two lines that ask or tell in its place: the crisis panel's question and the autopilot's hold.
 * A warning with none is news. A HUD shows a warning as news unless it shows what the warning names, so
 * one whose alert a HUD can't raise yet still reaches the player.
 *
 * <p>The game's own alerts take the same id where it raises them ({@link #MISSILE}, {@link #PROPELLANT},
 * {@link #DISABLED}).
 */
public final class NoticeAlerts {
    /** A missile salvo fired at the ship. */
    public static final String MISSILE = "missile";
    /** Too little propellant left for the trip home. */
    public static final String PROPELLANT = "propellant";
    /** The ship disabled (not wrecked or abandoned, which are news). */
    public static final String DISABLED = "disabled";
    /** Air venting to space: a leak, or the ship venting at its berth. */
    public static final String VENTING = "venting";
    /** The ship's air: its oxygen tanks dry. */
    public static final String AIR = "air";
    /** The captain's own air, wound or suit: what PER and the captain's alerts show. */
    public static final String CAPTAIN = "captain";
    /** Someone aboard other than the captain with little air left in their suit. */
    public static final String SUIT_AIR = "suit air";
    /** Someone outside in trouble: thrown off the hull, a tether parted, a suit out of air. */
    public static final String EVA = "eva";
    /** EVA's adrift form: someone outside with no way back alone. */
    public static final String ADRIFT = "adrift";
    /** Someone outside in the main engines' exhaust. */
    public static final String EVA_BURN = "eva burn";
    /** Someone aboard critically wounded, their clock running. */
    public static final String CRITICAL = "critical";
    /** Someone aboard seriously wounded and down. */
    public static final String WOUNDED = "wounded";
    /** A pirate moving to intercept the ship. */
    public static final String INTERCEPT = "intercept";
    /** A pirate coming alongside to board the ship, or latched on. */
    public static final String BOARDERS = "boarders";
    /** A docking collar jammed. */
    public static final String COLLAR = "collar";
    /** A planned burn missed or cut short. */
    public static final String BURN_MISSED = "burn missed";
    /** A helm order dropped, or the autopilot stopped short. */
    public static final String HELM = "helm";
    /** The first press of a guarded order (the tow, abandon ship), asking for the second. */
    public static final String ASKED = "asked";
    /** The autopilot holding off, which the helm line says. */
    public static final String AUTOPILOT = "autopilot";

    private NoticeAlerts() {
    }
}
