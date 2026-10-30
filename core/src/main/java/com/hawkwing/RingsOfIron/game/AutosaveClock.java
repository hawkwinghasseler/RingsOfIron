package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.ShipDisablement;

/**
 * When to autosave: on docking at a station, and every {@link #FLIGHT_INTERVAL} sim seconds of
 * flight outside time warp. (The game also saves just before undocking, on the key.) Feed it once
 * a frame; it says why to save now, or null.
 */
public final class AutosaveClock {
    /** Ten in-game minutes. */
    public static final double FLIGHT_INTERVAL = 600;

    private boolean wasDocked;
    private double flown;
    private double last = Double.NaN;

    /**
     * @param docked  docked at a station with clearance
     * @param warping time warp above 1x: flight then does not count toward the next save
     * @param sim     sim seconds now
     * @return "docked" or "in flight" when a save is due, else null
     */
    public String update(boolean docked, boolean warping, double sim) {
        if (Double.isNaN(last)) {
            restart(docked, sim);
            return null;
        }
        double dt = Math.max(0, sim - last);
        last = sim;
        String why = null;
        if (docked && !wasDocked) {
            why = "docked";
        } else if (!docked && !warping) {
            flown += dt;
            if (flown >= FLIGHT_INTERVAL) why = "in flight";
        }
        wasDocked = docked;
        if (why != null) flown = 0;
        return why;
    }

    /**
     * The game's check each frame: {@link #update} for {@code world} as it is, but nothing once the
     * ship is lost or the career over (the clock stops then), and no save for {@code player} while it
     * is disabled or being abandoned (the clock runs on): the older slots would be all that is left.
     */
    public String due(GameWorld world, Entity player) {
        if (world.getCrash() != null || world.getCareerOver() != null) return null;
        String why = update(world.playerStation() != null, world.timeWarp.getWarp() > 1, world.engine.getSimSeconds());
        boolean lost = ShipDisablement.reason(player) != null || AbandonShip.abandoning(player);
        return lost ? null : why;
    }

    /** After a save, a load or a rescue: count from now, with no save for the state the game starts in. */
    public void restart(boolean docked, double sim) {
        wasDocked = docked;
        last = sim;
        flown = 0;
    }

    /** Sim seconds of flight counted toward the next save. */
    public double flown() {
        return flown;
    }
}
