package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ShortArray;

/**
 * FILAMENT's icons, laid in phosphor as separate strokes and fills the way a real display's glass
 * carries them (style-vfd.md, section 5.6), on a 16 unit box. Each is a list of parts: filled
 * polygons, stroked polylines, stroked arcs (degrees, 0 up and clockwise) and dots. Copied from the
 * mockups' renderer, so the game and the mockups draw the same icon.
 */
final class Icons {
    enum Kind { POLY, LINE, ARC, DOT }

    static final class Part {
        final Kind kind;
        /** POLY and LINE: x, y pairs. ARC: cx, cy, r, from, to. DOT: cx, cy, r. */
        final float[] data;
        /** POLY: the triangles, as corner indices in threes. */
        final short[] triangles;

        Part(Kind kind, float[] data) {
            this.kind = kind;
            this.data = data;
            if (kind == Kind.POLY) {
                ShortArray t = new EarClippingTriangulator().computeTriangles(data);
                triangles = t.toArray();
            } else {
                triangles = null;
            }
        }
    }

    private static final ObjectMap<String, Part[]> ICONS = new ObjectMap<>();

    static {
        put("missile", poly(3f, 7f, 11f, 7f, 14f, 8f, 11f, 9f, 3f, 9f),
                poly(1f, 3.5f, 3.2f, 3.5f, 5.5f, 6.2f, 2.5f, 6.2f),
                poly(1f, 12.5f, 3.2f, 12.5f, 5.5f, 9.8f, 2.5f, 9.8f),
                line(0.5f, 8f, 1.8f, 8f));
        put("lock", arc(8f, 8f, 5.5f, 20f, 70f),
                arc(8f, 8f, 5.5f, 110f, 160f),
                arc(8f, 8f, 5.5f, 200f, 250f),
                arc(8f, 8f, 5.5f, 290f, 340f),
                line(8f, 0.5f, 8f, 4f),
                line(8f, 12f, 8f, 15.5f),
                line(0.5f, 8f, 4f, 8f),
                line(12f, 8f, 15.5f, 8f),
                dot(8f, 8f, 1.1f));
        put("dock", line(6.5f, 2.5f, 2.5f, 2.5f, 2.5f, 13.5f, 6.5f, 13.5f),
                line(9.5f, 2.5f, 13.5f, 2.5f, 13.5f, 13.5f, 9.5f, 13.5f),
                poly(5f, 6.2f, 7.2f, 8f, 5f, 9.8f),
                poly(11f, 6.2f, 8.8f, 8f, 11f, 9.8f));
        put("prop", line(5f, 2f, 11f, 2f),
                line(4f, 4f, 12f, 4f, 12f, 12f, 4f, 12f, 4f, 4f),
                poly(5.5f, 9f, 10.5f, 9f, 10.5f, 10.6f, 5.5f, 10.6f),
                poly(5.5f, 6.4f, 10.5f, 6.4f, 10.5f, 8f, 5.5f, 8f),
                poly(6.5f, 13.5f, 9.5f, 13.5f, 10.5f, 15.5f, 5.5f, 15.5f));
        put("power", poly(9.5f, 0.8f, 3.5f, 9f, 7.4f, 9f, 6f, 15.2f, 12.5f, 6.6f, 8.6f, 6.6f));
        put("o2", arc(5.2f, 8f, 3.6f, 0f, 360f),
                arc(10.8f, 8f, 3.6f, 200f, 520f));
        put("fire", poly(8f, 0.8f, 12.4f, 7f, 12.6f, 11f, 10.4f, 14.6f, 8f, 15.4f, 5.4f, 14.6f, 3.4f, 11f, 4f, 7.6f, 6f, 9.6f, 6.4f, 5f));
        put("breach", line(1f, 3f, 6.5f, 3f),
                line(9.5f, 3f, 15f, 3f),
                line(1f, 13f, 6f, 13f),
                line(10f, 13f, 15f, 13f),
                line(6.5f, 3f, 9f, 6.5f, 7f, 9.5f, 10f, 13f),
                line(1f, 3f, 1f, 13f),
                line(15f, 3f, 15f, 13f));
        put("crew", arc(8f, 4.2f, 2.8f, 0f, 360f),
                poly(3f, 15.5f, 4f, 10f, 6f, 8.6f, 10f, 8.6f, 12f, 10f, 13f, 15.5f));
        put("cargo", line(2f, 3f, 14f, 3f, 14f, 14f, 2f, 14f, 2f, 3f),
                line(2f, 7f, 14f, 7f),
                poly(6.5f, 3f, 9.5f, 3f, 9.5f, 6f, 6.5f, 6f));
        put("warp", poly(1.5f, 3f, 4.5f, 3f, 9.5f, 8f, 4.5f, 13f, 1.5f, 13f, 6.5f, 8f),
                poly(7.5f, 3f, 10.5f, 3f, 15.5f, 8f, 10.5f, 13f, 7.5f, 13f, 12.5f, 8f));
        put("auto", arc(8f, 8f, 6.5f, 200f, 340f),
                arc(8f, 8f, 6.5f, 20f, 160f),
                line(2f, 8f, 6f, 8f),
                line(10f, 8f, 14f, 8f),
                dot(8f, 8f, 1.6f),
                line(8f, 10f, 8f, 14f));
        put("save", line(2f, 2f, 12f, 2f, 14f, 4f, 14f, 14f, 2f, 14f, 2f, 2f),
                poly(5f, 2.8f, 10.5f, 2.8f, 10.5f, 6.5f, 5f, 6.5f),
                poly(4.5f, 9f, 11.5f, 9f, 11.5f, 13.2f, 4.5f, 13.2f));
        put("comms", line(8f, 7f, 8f, 15.5f),
                dot(8f, 6f, 1.4f),
                arc(8f, 6f, 3.6f, 300f, 420f),
                arc(8f, 6f, 3.6f, 120f, 240f),
                arc(8f, 6f, 6.6f, 305f, 415f),
                arc(8f, 6f, 6.6f, 125f, 235f));
        put("caution", line(8f, 1.5f, 15f, 14.5f, 1f, 14.5f, 8f, 1.5f),
                poly(7.2f, 5.5f, 8.8f, 5.5f, 8.4f, 10.5f, 7.6f, 10.5f),
                dot(8f, 12.4f, 0.9f));
        put("shield", line(8f, 1f, 14f, 3.2f, 13.4f, 9f, 8f, 15f, 2.6f, 9f, 2f, 3.2f, 8f, 1f),
                poly(8f, 4f, 11.5f, 5.2f, 11.1f, 8.6f, 8f, 12f));
        put("heat", line(6.5f, 10f, 6.5f, 2.5f, 9.5f, 2.5f, 9.5f, 10f),
                arc(8f, 12f, 3.2f, -60f, 240f),
                dot(8f, 12f, 1.7f),
                line(11f, 4.5f, 13f, 4.5f),
                line(11f, 7f, 13f, 7f));
        put("ship", poly(1f, 6f, 4f, 4.5f, 11f, 4.5f, 15f, 8f, 11f, 11.5f, 4f, 11.5f, 1f, 10f));
        put("station", arc(8f, 8f, 6.2f, 0f, 360f),
                poly(6.6f, 6.6f, 9.4f, 6.6f, 9.4f, 9.4f, 6.6f, 9.4f),
                line(8f, 1.8f, 8f, 5f),
                line(8f, 11f, 8f, 14.2f),
                line(1.8f, 8f, 5f, 8f),
                line(11f, 8f, 14.2f, 8f));
        put("orbit", arc(8f, 8f, 6.8f, 0f, 360f),
                dot(8f, 8f, 2.4f),
                poly(13f, 2.5f, 15.5f, 2.5f, 15.5f, 5f, 13f, 5f));
        put("clock", arc(8f, 8f, 6.5f, 0f, 360f),
                line(8f, 3.5f, 8f, 8f, 11f, 10f));
        put("tow", line(8f, 1f, 8f, 8f),
                arc(8f, 11f, 3f, -10f, 200f),
                line(5f, 4f, 11f, 4f));
        put("msg", line(1.5f, 3.5f, 14.5f, 3.5f, 14.5f, 12.5f, 1.5f, 12.5f, 1.5f, 3.5f),
                line(1.5f, 3.5f, 8f, 8.5f, 14.5f, 3.5f));
        put("pd", arc(8f, 8f, 6.5f, 0f, 360f),
                line(8f, 4f, 8f, 12f),
                line(4f, 8f, 12f, 8f),
                dot(8f, 8f, 1.6f));
        put("target", poly(8f, 1f, 15f, 8f, 8f, 15f, 1f, 8f));
        put("drive", poly(0.8f, 5.4f, 3.6f, 5.4f, 3.6f, 10.6f, 0.8f, 10.6f),
                poly(4.6f, 6.8f, 6.4f, 6.8f, 10.2f, 2.8f, 10.2f, 13.2f, 6.4f, 9.2f, 4.6f, 9.2f),
                line(11.6f, 5.2f, 15.2f, 4f),
                line(11.6f, 8f, 15.6f, 8f),
                line(11.6f, 10.8f, 15.2f, 12f));
        put("eva", arc(5.4f, 3.8f, 2.4f, 0f, 360f),
                poly(1.2f, 14.6f, 2f, 9.8f, 3.6f, 8.4f, 7.2f, 8.4f, 8.8f, 9.8f, 9.6f, 14.6f),
                line(9.6f, 11.6f, 11.4f, 9.4f, 13f, 11.8f, 15f, 8.6f));
        put("cassette", line(1f, 3f, 15f, 3f, 15f, 13f, 1f, 13f, 1f, 3f),
                arc(5.2f, 7.5f, 1.8f, 0f, 360f),
                arc(10.8f, 7.5f, 1.8f, 0f, 360f),
                line(4f, 13f, 5f, 10.5f, 11f, 10.5f, 12f, 13f));
    }

    private Icons() {
    }

    /** The icon called {@code name}, or null. */
    static Part[] get(String name) {
        return ICONS.get(name);
    }

    static Iterable<String> names() {
        return ICONS.keys();
    }

    private static void put(String name, Part... parts) {
        ICONS.put(name, parts);
    }

    private static Part poly(float... xy) {
        return new Part(Kind.POLY, xy);
    }

    private static Part line(float... xy) {
        return new Part(Kind.LINE, xy);
    }

    private static Part arc(float cx, float cy, float r, float from, float to) {
        return new Part(Kind.ARC, new float[] {cx, cy, r, from, to});
    }

    private static Part dot(float cx, float cy, float r) {
        return new Part(Kind.DOT, new float[] {cx, cy, r});
    }
}
