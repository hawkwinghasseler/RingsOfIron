package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;

/**
 * The player's orbit drawn as a map: the conic it is on now, the one its planned burns lead to, and
 * where those burns happen. Implemented by orbital-mechanics; the HUD's orbit map reads it each frame.
 */
public interface OrbitPathProvider {
    /** Fills {@code out} for this frame and returns true, or returns false when there is no orbit to draw. */
    boolean getPlayerPath(OrbitPath out);

    /** Another body in the map's frame: a moon of the parent, or the body the parent orbits. */
    final class MapBody {
        public String name = "";
        public double x, y, radius;
        /** Its orbit around the parent as x,y pairs, or empty when it is not the parent's moon. */
        public final FloatArray orbit = new FloatArray();
    }

    /** A craft drawn on the map: a station, or the navigation target. */
    final class MapMarker {
        public String name = "";
        public double x, y;
        /** The navigation target: drawn larger and labelled. */
        public boolean target;
    }

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
        /** Other bodies, positioned relative to the parent body. */
        public final Array<MapBody> bodies = new Array<>();
        private final Array<MapBody> bodyPool = new Array<>();
        /** Stations around the parent body, and the navigation target wherever it is. */
        public final Array<MapMarker> markers = new Array<>();
        private final Array<MapMarker> markerPool = new Array<>();
        /** The navigation target's orbit, when it shares the ship's parent body; else empty. */
        public final FloatArray targetOrbit = new FloatArray();
        /**
         * Closest approach to the target along the planned path: the ship's point then the
         * target's point, x,y,x,y; empty with no target.
         */
        public final FloatArray approach = new FloatArray();
        /** A transfer proposed to the target but not yet accepted: its path and its burns. */
        public final FloatArray proposed = new FloatArray();
        public final FloatArray proposedNodes = new FloatArray();

        public MapBody addBody() {
            MapBody b = bodyPool.size > 0 ? bodyPool.pop() : new MapBody();
            b.orbit.clear();
            bodies.add(b);
            return b;
        }

        public MapMarker addMarker() {
            MapMarker m = markerPool.size > 0 ? markerPool.pop() : new MapMarker();
            m.name = "";
            m.target = false;
            markers.add(m);
            return m;
        }

        public void clear() {
            bodyName = "";
            bodyRadius = 0;
            shipX = shipY = 0;
            current.clear();
            planned.clear();
            nodes.clear();
            selectedNode = -1;
            caption.setLength(0);
            bodyPool.addAll(bodies);
            bodies.clear();
            markerPool.addAll(markers);
            markers.clear();
            targetOrbit.clear();
            approach.clear();
            proposed.clear();
            proposedNodes.clear();
        }
    }
}
