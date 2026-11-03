package com.ringsofiron.uihud.providers;

/**
 * The orders the captain can give, which stand, and why any can't be given now: the helm's orders,
 * the autopilot, the crew's standing orders, and the guarded ship orders (abandon ship, the tow),
 * for a menu of orders that shows every one. Filled once a frame by the game wiring layer from the
 * helm, the autopilot, the crew's task board, the escape pods, the distress beacon and the keys.
 * Shields are in {@link ShieldStatusProvider}.
 */
public interface OrdersProvider {
    /** Fills {@code out}, already cleared. */
    void read(Orders out);

    /** The helm's orders, keys 1 to 5; {@link #NONE} is Cancel (0). */
    enum HelmOrder {
        NONE, FLY_BURNS, MATCH_VELOCITY, HOLD_POSITION, APPROACH, DOCK
    }

    enum Autopilot {
        OFF, ON,
        /** On, but holding off: warp held, a hostile near; why is {@link Orders#autopilotHeld}. */
        HELD
    }

    final class Orders {
        /** At a seat, where the helm and autopilot orders are taken; on deck they are not. */
        public boolean seated;

        /** The helm's standing order, and who it flies relative to ("Highport"), or "". */
        public HelmOrder helm = HelmOrder.NONE;
        public String helmTarget = "";
        /** The approach order's distance off the target's hull, m; NaN with no approach order. */
        public float approachDistance = Float.NaN;
        /** What the helm is doing, as the game words it ("Helm: approaching Highport to 500 m"), or "". */
        public String helmLine = "";
        /**
         * Why each helm order can't be given now, by {@link HelmOrder} ordinal, as the game would
         * answer it; "" when it can. Cancel always can, from a seat.
         */
        public final String[] helmRefusal = new String[HelmOrder.values().length];

        public Autopilot autopilot = Autopilot.OFF;
        /** Where it flies, and why it holds; "" otherwise. */
        public String autopilotTo = "", autopilotHeld = "";
        /** Why it can't be engaged now; "" when it can, or when it is on (the key then stops it). */
        public String autopilotRefusal = "";

        /** The crew: at battle stations now; suiting up for them; held in from EVA. */
        public boolean battleStations, suitsAtBattleStations, evaHeld;

        /** The crew are under orders to abandon ship, and seconds until the pods go with whoever is in them; NaN when not. */
        public boolean abandoning;
        public float podsLaunchIn = Float.NaN;
        /** Conscious crew of the ship's own aboard, the captain among them, and how many of them are in a pod now. */
        public int crewAboard, inPods;
        /** Escape pods that can still launch, and the seats in them nobody is in yet. */
        public int pods, freeSeats;
        /**
         * The captain's straight-line distance to the nearest pod with a seat free, m (0 once in one),
         * and the tile in it the crew make for; NaN and -1 with none, or with the captain not aboard.
         */
        public float podDistance = Float.NaN;
        public int podTileX = -1, podTileY = -1;
        public boolean captainInPod;
        /** Why the crew can't be ordered to the pods now; "" when they can, and while abandoning (the key then launches the pods). */
        public String abandonRefusal = "";
        /** Seconds left on a first press of abandon ship, and of the tow: a second press within them gives it; 0 when not pending. */
        public float abandonArmed, towArmed;
        /** The tow: its fee, credits, and the standing it costs; why it can't be called now, "" when it can. */
        public double towFee;
        public float towStanding;
        public String towRefusal = "";

        /** The game is paused. */
        public boolean paused;

        public Orders() {
            clear();
        }

        public void clear() {
            seated = false;
            helm = HelmOrder.NONE;
            helmTarget = "";
            approachDistance = Float.NaN;
            helmLine = "";
            java.util.Arrays.fill(helmRefusal, "");
            autopilot = Autopilot.OFF;
            autopilotTo = "";
            autopilotHeld = "";
            autopilotRefusal = "";
            battleStations = suitsAtBattleStations = evaHeld = false;
            abandoning = false;
            podsLaunchIn = Float.NaN;
            crewAboard = inPods = pods = freeSeats = 0;
            podDistance = Float.NaN;
            podTileX = podTileY = -1;
            captainInPod = false;
            abandonRefusal = "";
            abandonArmed = towArmed = 0f;
            towFee = 0;
            towStanding = 0f;
            towRefusal = "";
            paused = false;
        }
    }
}
