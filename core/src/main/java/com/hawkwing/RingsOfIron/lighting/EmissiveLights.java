package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The lights each SVG's emissive layer gives off, from {@code assets/art/lights.json}, which
 * {@code tools/art/extract_lights.py} builds from the art. Keyed by the art's folder and name,
 * as {@code components/reactor_m}; art with no emissive layer (or an {@code _unpowered} variant)
 * has no entry.
 */
public final class EmissiveLights {
    /** SVG units in a tile, and in a meter outside (see the art guide). */
    public static final float UNITS_PER_TILE = 64f;

    /** What the art says a glowing part is, with its {@code data-light} attribute. */
    public enum Kind {
        /** Glows, but lights nothing around it. */
        GLOW,
        /** Lights all around it, whatever its size. */
        POINT,
        /** Lights a cone, whatever its size. */
        CONE
    }

    /** One glowing part of the art: its center and size in SVG units with y up. */
    public static final class Source {
        public final float x, y, w, h;
        public final Color color;
        /** The most opaque shape's opacity, 0-1. */
        public final float alpha;
        /** What the art says it is, or null to judge by its size. */
        public final Kind kind;
        /** How far its light reaches, tiles, or 0 to judge by its size. */
        public final float reach;
        /** How strong its light is, 0-1, or -1 to judge by its opacity. */
        public final float intensity;
        /** A cone shining toward {@link #direction} (degrees, 0 = +x, y up) over {@link #cone}° each side. */
        public final boolean isCone;
        public final float direction, cone;

        Source(float x, float y, float w, float h, Color color, float alpha, Kind kind, float reach, float intensity,
               float direction, float cone) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.color = color;
            this.alpha = alpha;
            this.kind = kind;
            this.reach = reach;
            this.intensity = intensity;
            this.isCone = kind == Kind.CONE;
            this.direction = direction;
            this.cone = cone;
        }

        /** The longer side, SVG units. */
        public float size() {
            return Math.max(w, h);
        }
    }

    /** The lights of one SVG, and its canvas: bottom-left corner and size, in the lights' frame. */
    public static final class Art {
        public final float left, bottom, width, height;
        public final Source[] lights;

        Art(float left, float bottom, float width, float height, Source[] lights) {
            this.left = left;
            this.bottom = bottom;
            this.width = width;
            this.height = height;
            this.lights = lights;
        }
    }

    private final Map<String, Art> art = new HashMap<>();

    private EmissiveLights() {
    }

    /** No art lights at all: what the game uses when {@code lights.json} is missing. */
    public static EmissiveLights empty() {
        return new EmissiveLights();
    }

    /** Reads {@code art/lights.json} under {@code assetsDir}; empty if there is none. */
    public static EmissiveLights load(Path assetsDir) {
        Path file = assetsDir.resolve("art").resolve("lights.json");
        if (!Files.isRegularFile(file)) return empty();
        try {
            return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Can't read " + file, e);
        }
    }

    public static EmissiveLights parse(String json) {
        EmissiveLights out = new EmissiveLights();
        for (JsonValue entry = new JsonReader().parse(json).child; entry != null; entry = entry.next) {
            float[] canvas = entry.get("canvas").asFloatArray();
            JsonValue list = entry.get("lights");
            Source[] lights = new Source[list.size];
            int i = 0;
            for (JsonValue l = list.child; l != null; l = l.next) {
                String kind = l.getString("light", null);
                lights[i++] = new Source(l.getFloat("x"), l.getFloat("y"), l.getFloat("w"), l.getFloat("h"),
                        Color.valueOf(l.getString("color")), l.getFloat("alpha"),
                        kind == null ? null : Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)),
                        l.getFloat("reach", 0f), l.getFloat("intensity", -1f),
                        l.getFloat("direction", 0f), l.getFloat("cone", 0f));
            }
            out.art.put(entry.name, new Art(canvas[0], canvas[1], canvas[2], canvas[3], lights));
        }
        return out;
    }

    /** The lights of {@code folder/name}, or null when that art gives off none. */
    public Art get(String key) {
        return art.get(key);
    }

    public int size() {
        return art.size();
    }
}
