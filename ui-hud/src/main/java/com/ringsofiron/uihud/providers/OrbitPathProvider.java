package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.FloatArray;

/**
 * The player's orbit drawn as a map: the conic it is on now, the one its planned burns lead to, and
 * where those burns happen. Implemented by orbital-mechanics; the HUD's orbit map reads it each frame.
 */
public interface OrbitPathProvider {
    /** Fills {@code out} for this frame and returns true, or returns false when there is no orbit to draw. */
    boolean getPlayerPath(OrbitPath out);

    /**
     * Plain data for one frame's map. Points are x,y pairs in meters from the parent body's center,
     * sampled evenly in anomaly so the curve stays smooth near periapsis.
     */
    final class OrbitPath {
        public String bodyName = "";
        public double bodyRadius;
        public double shipX, shipY;
        /** The orbit the ship is on now. */
        public final FloatArray current = new FloatArray();
        /** The orbit after every planned burn, or empty with no burns planned. */
        public final FloatArray planned = new FloatArray();
        /** Where each planned burn happens, as x,y pairs. */
        public final FloatArray nodes = new FloatArray();
        /** Index into the burns of the one the planner is editing, or -1. */
        public int selectedNode = -1;
        /** Text under the map: the selected burn and where it leads. Empty for none. */
        public final StringBuilder caption = new StringBuilder();

        public void clear() {
            bodyName = "";
            bodyRadius = 0;
            shipX = shipY = 0;
            current.clear();
            planned.clear();
            nodes.clear();
            selectedNode = -1;
            caption.setLength(0);
        }
    }
}
