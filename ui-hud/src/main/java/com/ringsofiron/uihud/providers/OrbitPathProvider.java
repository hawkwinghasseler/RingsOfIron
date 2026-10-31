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

    /** What a {@link PathMark} marks on the map. */
    enum MarkKind {
        /** Highest and lowest points of a path. */
        APOAPSIS, PERIAPSIS,
        /** Where a path enters another body's sphere of influence, or leaves the map body's. */
        SOI_ENTRY, SOI_EXIT,
        /** Where a path meets a body's surface. */
        IMPACT,
        /** Where a body will be when a path meets it: drawn as its outline and sphere of influence. */
        GHOST
    }

    /** A labelled point on a path: an apsis, a change of sphere of influence, an impact. */
    final class PathMark {
        public MarkKind kind = MarkKind.PERIAPSIS;
        public double x, y;
        /** A {@link MarkKind#GHOST}'s body radius and sphere of influence, m. */
        public double radius, soiRadius;
        /** On the path after planned burns, rather than the orbit now. */
        public boolean planned;
        public String text = "";
    }

    /**
     * Where a path goes after it leaves the map body's own conic: around a moon it meets, drawn about
     * where the moon will be then, and back out again. Points in the map's frame like the rest.
     */
    final class PathLeg {
        public final FloatArray points = new FloatArray();
        /** Follows the planned path, rather than the orbit now. */
        public boolean planned;
    }

    /**
     * Plain data for one frame's map. Points (the float arrays) are x,y pairs in meters from the
     * {@link #originX origin}, sampled evenly in anomaly so the curve stays smooth near periapsis, and
     * more finely where they cross the map's {@link #viewScale view}. Single positions (the ship, the
     * marks, markers and bodies) are doubles in meters from the parent body's center.
     */
    final class OrbitPath {
        /**
         * What the map shows, set by the map before it asks and kept by {@link #clear}: the point at its
         * middle, meters from the body's center, its meters per pixel (0: not known), and the pixels from
         * its middle to its corners. A provider samples the curves finely where they cross it.
         */
        public double viewX, viewY, viewScale, viewReach;
        /**
         * Where the points are measured from, meters from the body's center: the view's middle when
         * there is one, so floats keep their precision zoomed in far from the body; else the body.
         */
        public double originX, originY;
        public String bodyName = "";
        public double bodyRadius;
        public double shipX, shipY;
        /** The orbit the ship is on now. */
        public final FloatArray current = new FloatArray();
        /** The orbit after every planned burn, or empty with no burns planned. */
        public final FloatArray planned = new FloatArray();
        /** Where each planned burn happens, as x,y pairs. */
        public final FloatArray nodes = new FloatArray();
        /** A label by each planned burn, in order ("1: 0:12:30"), or empty to draw none. */
        public final Array<String> nodeLabels = new Array<>();
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
        /**
         * The navigation target's orbit, in the map's frame: around the ship's parent body, or around
         * another body drawn where that body is now (a moon's station seen from the planet). Else empty.
         */
        public final FloatArray targetOrbit = new FloatArray();
        /**
         * Closest approach to the target along the planned path: the ship's point then the
         * target's point, x,y,x,y; empty with no target.
         */
        public final FloatArray approach = new FloatArray();
        /** A transfer proposed to the target but not yet accepted: its path and its burns. */
        public final FloatArray proposed = new FloatArray();
        public final FloatArray proposedNodes = new FloatArray();
        /**
         * Each planned burn's axes as unit vectors in the map's frame, four floats a burn: prograde x
         * and y, then radial out x and y. For handles that drag a burn's delta-v.
         */
        public final FloatArray nodeAxes = new FloatArray();
        /** Apsides, sphere-of-influence changes and impacts along the path that matters most. */
        public final Array<PathMark> marks = new Array<>();
        private final Array<PathMark> markPool = new Array<>();
        /** The path on past a change of sphere of influence. */
        public final Array<PathLeg> legs = new Array<>();
        private final Array<PathLeg> legPool = new Array<>();

        public PathMark addMark(MarkKind kind, double x, double y, String text) {
            PathMark m = markPool.size > 0 ? markPool.pop() : new PathMark();
            m.kind = kind;
            m.x = x;
            m.y = y;
            m.radius = m.soiRadius = 0;
            m.planned = false;
            m.text = text == null ? "" : text;
            marks.add(m);
            return m;
        }

        public PathLeg addLeg(boolean planned) {
            PathLeg l = legPool.size > 0 ? legPool.pop() : new PathLeg();
            l.points.clear();
            l.planned = planned;
            legs.add(l);
            return l;
        }

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
            originX = originY = 0;
            bodyName = "";
            bodyRadius = 0;
            shipX = shipY = 0;
            current.clear();
            planned.clear();
            nodes.clear();
            nodeLabels.clear();
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
            nodeAxes.clear();
            markPool.addAll(marks);
            marks.clear();
            legPool.addAll(legs);
            legs.clear();
        }
    }
}
