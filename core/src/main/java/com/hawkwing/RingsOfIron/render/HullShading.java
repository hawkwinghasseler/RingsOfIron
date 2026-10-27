package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ShortArray;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A prototype of hull lighting worked out by the game instead of painted into the art. One key
 * light, a far star at a fixed direction in the world, falls on every hull, so a hull's lit side
 * swings round as it turns and every craft in view agrees on where the light comes from.
 *
 * <ul>
 *   <li>{@link Mode#NORMALS}: the hull's flat art ({@code flat/hulls/<id>}, its painted-on sheen
 *   and lit edges left out) is drawn with a shader that lights it by its normal map
 *   ({@code normal/hulls/<id>}), so the edge of a plate, a block or a turret ring catches the key
 *   light on the side that faces it.</li>
 *   <li>{@link Mode#SHADOWS}: as {@link Mode#NORMALS}, and each raised part (the art's relief
 *   layer: superstructure, turret rings, vents and boxes) casts its shadow across the roof, away
 *   from the key light, as long as its height over the tangent of the light's elevation. Shadows
 *   are cut to the hull's own art, so none falls on space, and the raised parts
 *   ({@code raised/hulls/<id>}) are drawn again over them, so a part's shadow falls past it and
 *   not on it; then each raised part takes the shadows of the taller parts beside it, as long as
 *   the height between them.</li>
 * </ul>
 *
 * The lights near a hull ({@link #setPointLights}: its engines and thrusters, muzzle flashes,
 * blasts) light it by its normal map too: the shading at each point is the key light's and each
 * point light's, weighed by how strongly each reaches it against the starlight ambient. All of it
 * brightens and darkens about what the flat art is at its plain roof, so the box2dlights light
 * map ({@code ExteriorLighting}) still lays the brightness and color of the ambient and every
 * light over it as before; the shader only gives that light its direction.
 */
public final class HullShading implements Disposable {
    /** How the hulls are lit. */
    public enum Mode {
        /** The light painted into the art, as before. */
        BAKED,
        /** Flat art lit by its normal map. */
        NORMALS,
        /** Flat art lit by its normal map, with the shadows raised parts cast on the roof. */
        SHADOWS;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String label() {
            switch (this) {
                case BAKED: return "Hull lighting: baked into the art";
                case NORMALS: return "Hull lighting: normal maps";
                default: return "Hull lighting: normal maps and roof shadows";
            }
        }
    }

    /**
     * How the hulls are lit, for every renderer; {@code -Dhull.lighting=baked|normals|shadows}
     * sets it at start, and the dev console or F7 changes it.
     */
    public static Mode mode = fromProperty();

    /** Where the key light comes from in the world: its bearing (radians, from +x) and its height over the plane. */
    public static float azimuth = 135f * MathUtils.degreesToRadians;
    public static float elevation = 32f * MathUtils.degreesToRadians;
    /** How dark a shadow is: the share of the light the roof loses in it. */
    public static float shadowStrength = 0.45f;
    /** How much of the shading is the key light (the rest is light from all round, which shows no relief). */
    public static float diffuse = 0.75f;
    /** How high over the roof a point light is taken to be, in meters: lower rakes its light across the hull's relief. */
    public static float pointHeight = 1.2f;
    /** At most this many point lights shade one hull: the strongest that reach it. */
    public static final int MAX_POINTS = 8;
    /** Raised parts this close in height (meters) are taken as one level, which shades as one. */
    static final float LEVEL_STEP = 0.05f;

    private static final String VERTEX = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
            + "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
            + "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "  v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
            + "  v_color.a = v_color.a * (255.0/254.0);\n"
            + "  v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
            + "  gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
            + "}\n";

    // The normal map's region in its own texture, found from where this fragment is in the albedo's
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform sampler2D u_normals;\n"
            + "uniform vec4 u_albedoRect;\n"
            + "uniform vec4 u_normalRect;\n"
            + "uniform vec3 u_light;\n"
            + "uniform float u_diffuse;\n"
            // The art's place in the body frame (left, top, width, height, meters), so each
            // fragment knows where it is; and the point lights there: x, y, reach, strength
            + "uniform vec4 u_box;\n"
            + "uniform vec4 u_points[" + MAX_POINTS + "];\n"
            + "uniform int u_pointCount;\n"
            + "uniform float u_ambientWeight;\n"
            + "uniform float u_pointHeight;\n"
            + "void main() {\n"
            + "  vec4 c = v_color * texture2D(u_texture, v_texCoords);\n"
            + "  vec2 t = (v_texCoords - u_albedoRect.xy) / (u_albedoRect.zw - u_albedoRect.xy);\n"
            + "  vec3 n = normalize(texture2D(u_normals, mix(u_normalRect.xy, u_normalRect.zw, t)).rgb * 2.0 - 1.0);\n"
            + "  float ambient = 1.0 - u_diffuse;\n"
            + "  float plain = ambient + u_diffuse * u_light.z;\n"
            + "  float key = (ambient + u_diffuse * max(dot(n, u_light), 0.0)) / plain;\n"
            // Each point light's shading, as bright as the key light's at plain roof, weighed by
            // how strongly it reaches here (box2dlights' falloff) against the ambient
            + "  vec2 p = vec2(u_box.x + t.x * u_box.z, u_box.y - t.y * u_box.w);\n"
            + "  float sum = u_ambientWeight * key, weight = u_ambientWeight;\n"
            + "  for (int i = 0; i < " + MAX_POINTS + "; i++) {\n"
            + "    if (i >= u_pointCount) break;\n"
            + "    vec4 pl = u_points[i];\n"
            + "    vec3 d = vec3(pl.xy - p, u_pointHeight);\n"
            + "    float w = pl.w * clamp(1.0 - length(d.xy) / pl.z, 0.0, 1.0);\n"
            + "    if (w <= 0.0) continue;\n"
            + "    vec3 l = normalize(d);\n"
            + "    float s = ambient + u_diffuse * max(dot(n, l), 0.0);\n"
            + "    sum += w * min(s / (ambient + u_diffuse * l.z), 3.0);\n"
            + "    weight += w;\n"
            + "  }\n"
            + "  gl_FragColor = vec4(c.rgb * (sum / weight), c.a);\n"
            + "}\n";

    private ShaderProgram shader;
    private Texture white;
    private boolean failed;
    private final Map<float[][], float[][]> triangles = new IdentityHashMap<>();
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private final float[] quad = new float[20];
    private float lx, ly, lz;
    private int stencilBits = -1;
    /** The point lights this frame, world meters: x, y, reach, strength for each. */
    private float[] worldPoints = new float[0];
    private int worldPointCount;
    private float ambientWeight = 1f;
    /** The strongest of them near the hull being drawn, in its body frame, as the shader takes them. */
    private final float[] points = new float[MAX_POINTS * 4];
    private final float[] pointPower = new float[MAX_POINTS];
    private int pointCount;
    private final float[] box = new float[4];
    private final com.badlogic.gdx.utils.FloatArray levels = new com.badlogic.gdx.utils.FloatArray();

    private static Mode fromProperty() {
        String p = System.getProperty("hull.lighting");
        if (p == null) return Mode.SHADOWS;
        try {
            return Mode.valueOf(p.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.SHADOWS;
        }
    }

    /** False when the shader would not compile here, so the hulls are drawn as the art is. */
    public boolean available() {
        if (shader == null && !failed) {
            ShaderProgram s = new ShaderProgram(VERTEX, FRAGMENT);
            if (!s.isCompiled()) {
                Gdx.app.error("HullShading", "Lit hull shader did not compile; drawing the baked art\n" + s.getLog());
                s.dispose();
                failed = true;
            } else {
                shader = s;
                Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                p.setColor(Color.WHITE);
                p.fill();
                white = new Texture(p);
                p.dispose();
            }
        }
        return shader != null;
    }

    /**
     * The point lights for this frame, world meters, {@code count} of them in {@code xyrs} as
     * x, y, reach, strength (0-1); {@code ambient} is how bright the scene is with none of them
     * (the light map's ambient), which they are weighed against. None (count 0) for a view with
     * no lighting. Kept, not copied: fill it again each frame.
     */
    public void setPointLights(float[] xyrs, int count, float ambient) {
        worldPoints = xyrs;
        worldPointCount = Math.min(count, xyrs.length / 4);
        ambientWeight = Math.max(0.05f, ambient);
    }

    /** Draws a region of a hull's art where the hull is (its baked art's place, its dents). */
    public interface Placer {
        void draw(AtlasRegion region);
    }

    /**
     * Draws the hull whose baked art is {@code hull} lit as {@link #mode} says, its body at
     * ({@code px}, {@code py}) world meters turned {@code angle}, in a batch already begun: its
     * flat art lit by its normal map, then (with shadows) the shadows of its raised parts and
     * those parts again over them. {@code place} draws each region where the hull is. False
     * when it was not drawn (the mode is {@link Mode#BAKED}, the art pack has no lit twins for
     * it, or the shader would not compile), and the caller draws the baked art.
     */
    public boolean draw(Batch batch, ArtLibrary art, AtlasRegion hull, float px, float py, float angle, Placer place) {
        if (mode == Mode.BAKED) return false;
        AtlasRegion flat = art.region(ArtNames.litHull(hull.name, ArtNames.FLAT));
        AtlasRegion normals = art.region(ArtNames.litHull(hull.name, ArtNames.NORMALS));
        ArtLibrary.Info info = art.info(hull);
        if (flat == null || normals == null || info == null || flat.rotate || normals.rotate || !available()) return false;
        face(angle);
        near(info, px, py, angle);
        begin(batch, flat, normals);
        place.draw(flat);
        end(batch);
        AtlasRegion raised = art.region(ArtNames.litHull(hull.name, ArtNames.RAISED));
        if (mode != Mode.SHADOWS || raised == null || info.relief == null) return true;
        shadows(batch, info, px, py, angle, () -> place.draw(flat));
        begin(batch, raised, normals);
        place.draw(raised);
        end(batch);
        raisedShadows(batch, info, px, py, angle, () -> place.draw(raised));
        return true;
    }

    /**
     * Sets the key light for a hull turned {@code angle} radians: the world direction turned
     * into the hull's own frame, which its normal map and relief are in.
     */
    public void face(float angle) {
        float a = azimuth - angle, c = MathUtils.cos(elevation);
        lx = MathUtils.cos(a) * c;
        ly = MathUtils.sin(a) * c;
        lz = MathUtils.sin(elevation);
    }

    /**
     * Picks the point lights that shade a hull whose art is placed as {@code info}, its body at
     * ({@code px}, {@code py}) turned {@code angle}: the {@link #MAX_POINTS} strongest that reach
     * its art, turned into its body frame; and sets where its art is in that frame.
     */
    void near(ArtLibrary.Info info, float px, float py, float angle) {
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        near(info.x * u, -info.y * u, info.w * u, info.h * u, px, py, angle);
    }

    /** As {@link #near(ArtLibrary.Info, float, float, float)}, the art's box in body meters: its top-left corner (y up) and size. */
    void near(float left, float top, float width, float height, float px, float py, float angle) {
        box[0] = left;
        box[1] = top;
        box[2] = width;
        box[3] = height;
        float cx = box[0] + box[2] / 2f, cy = box[1] - box[3] / 2f, radius = 0.5f * (float) Math.hypot(box[2], box[3]);
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        pointCount = 0;
        for (int i = 0; i < worldPointCount; i++) {
            float wx = worldPoints[4 * i] - px, wy = worldPoints[4 * i + 1] - py;
            float reach = worldPoints[4 * i + 2], strength = worldPoints[4 * i + 3];
            if (reach <= 0f || strength <= 0f) continue;
            // Into the body frame: turned back by the hull's angle
            float bx = wx * cos + wy * sin, by = -wx * sin + wy * cos;
            float gap = (float) Math.hypot(bx - cx, by - cy) - radius;
            if (gap >= reach) continue;
            // As strong as it is where it first reaches the art, to keep the strongest
            float power = strength * (1f - Math.max(0f, gap) / reach);
            int at = pointCount;
            if (pointCount == MAX_POINTS) {
                at = 0;
                for (int k = 1; k < MAX_POINTS; k++) if (pointPower[k] < pointPower[at]) at = k;
                if (pointPower[at] >= power) continue;
            } else {
                pointCount++;
            }
            pointPower[at] = power;
            points[4 * at] = bx;
            points[4 * at + 1] = by;
            points[4 * at + 2] = reach;
            points[4 * at + 3] = strength;
        }
    }

    /** How many point lights shade the hull drawn last. For tests. */
    int pointCount() {
        return pointCount;
    }

    /** The {@code i}th of them in the hull's body frame: x, y, reach, strength. For tests. */
    float point(int i, int k) {
        return points[4 * i + k];
    }

    /**
     * Starts drawing {@code albedo} lit by {@code normals}, in a batch already begun: the shader
     * on, the normal map's texture bound beside the albedo's. Everything the batch draws until
     * {@link #end} is lit as that region, so draw only it (or its {@code raised/} twin, packed
     * with the same layout) in between.
     */
    public void begin(Batch batch, AtlasRegion albedo, AtlasRegion normals) {
        batch.setShader(shader);
        normals.getTexture().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_normals", 1);
        shader.setUniformf("u_albedoRect", albedo.getU(), albedo.getV(), albedo.getU2(), albedo.getV2());
        shader.setUniformf("u_normalRect", normals.getU(), normals.getV(), normals.getU2(), normals.getV2());
        shader.setUniformf("u_light", lx, ly, lz);
        shader.setUniformf("u_diffuse", diffuse);
        shader.setUniformf("u_box", box[0], box[1], box[2], box[3]);
        shader.setUniform4fv("u_points", points, 0, MAX_POINTS * 4);
        shader.setUniformi("u_pointCount", pointCount);
        shader.setUniformf("u_ambientWeight", ambientWeight);
        shader.setUniformf("u_pointHeight", pointHeight);
    }

    /** Back to the batch's own shader. */
    public void end(Batch batch) {
        batch.setShader(null);
    }

    /**
     * The shadows a hull's raised parts ({@code info.relief}) cast on its roof, the hull's body
     * at ({@code px}, {@code py}) world meters turned {@code angle}: each part's outline swept
     * away from the key light by its height, marked in the stencil buffer, then the hull's flat
     * art ({@code drawRoof}) drawn darkened where it is marked. Call {@link #face} first; the
     * caller then draws the raised parts over it.
     */
    public void shadows(Batch batch, ArtLibrary.Info info, float px, float py, float angle, Drawer drawRoof) {
        if (info.relief == null || info.relief.length == 0 || !stencil()) return;
        batch.flush();
        GL20 gl = Gdx.gl;
        gl.glClearStencil(0);
        gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL20.GL_STENCIL_TEST);
        gl.glColorMask(false, false, false, false);
        gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        float[][] tris = trianglesOf(info.relief);
        for (int k = 0; k < info.relief.length; k++) sweep(batch, info.relief[k], tris[k], info.relief[k][0], px, py, cos, sin);
        darken(batch, 1, drawRoof);
    }

    /**
     * The shadows the taller raised parts cast on the lower ones' tops, after the raised parts
     * are drawn ({@code drawRaised} draws them again): for each level of height a part stands
     * at, the parts taller than it, swept away from the key light by the height between, darken
     * the parts at that level where they fall on them, but not where a taller part stands over
     * one (as a bridge on a spine does). Call {@link #face} first.
     */
    public void raisedShadows(Batch batch, ArtLibrary.Info info, float px, float py, float angle, Drawer drawRaised) {
        float[][] relief = info.relief;
        if (relief == null || relief.length < 2 || !stencil()) return;
        levels.clear();
        for (float[] part : relief) {
            float level = level(part[0]);
            if (!levels.contains(level)) levels.add(level);
        }
        levels.sort();
        float[][] tris = trianglesOf(relief);
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        GL20 gl = Gdx.gl;
        // The tallest level has nothing over it to cast on it
        for (int l = 0; l < levels.size - 1; l++) {
            float at = levels.get(l);
            batch.flush();
            gl.glClearStencil(0);
            gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
            gl.glEnable(GL20.GL_STENCIL_TEST);
            gl.glColorMask(false, false, false, false);
            // 1 where a taller part's shadow falls at this height
            gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
            gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
            for (int k = 0; k < relief.length; k++) {
                if (level(relief[k][0]) > at) sweep(batch, relief[k], tris[k], relief[k][0] - at, px, py, cos, sin);
            }
            batch.flush();
            // 2 where that falls on a part at this height
            gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xff);
            gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_INCR);
            for (int k = 0; k < relief.length; k++) {
                if (level(relief[k][0]) == at) foot(batch, tris[k], 0f, 0f, px, py, cos, sin);
            }
            batch.flush();
            // and not where a taller part stands over it: its own top is lit
            gl.glStencilFunc(GL20.GL_ALWAYS, 0, 0xff);
            gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
            for (int k = 0; k < relief.length; k++) {
                if (level(relief[k][0]) > at) foot(batch, tris[k], 0f, 0f, px, py, cos, sin);
            }
            darken(batch, 2, drawRaised);
        }
    }

    /** The level a part of {@code h} meters is taken to stand at, so parts of near the same height shade as one. */
    static float level(float h) {
        return Math.round(h / LEVEL_STEP) * LEVEL_STEP;
    }

    /**
     * A part's outline swept away from the key light by {@code height} meters' worth of shadow
     * (its foot, its shadow's far end and each side between), into the stencil buffer as the
     * caller set it up.
     */
    private void sweep(Batch batch, float[] part, float[] tris, float height, float px, float py, float cos, float sin) {
        // The shadow runs away from the light, along the light's bearing turned into the body frame
        float run = height / (float) Math.tan(elevation);
        float flat = (float) Math.hypot(lx, ly);
        float ox = flat < 1e-4f ? 0f : -lx / flat * run, oy = flat < 1e-4f ? 0f : -ly / flat * run;
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        foot(batch, tris, 0f, 0f, px, py, cos, sin);
        foot(batch, tris, ox, oy, px, py, cos, sin);
        int n = (part.length - 1) / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = part[1 + 2 * i] * u, ay = -part[2 + 2 * i] * u;
            float bx = part[1 + 2 * j] * u, by = -part[2 + 2 * j] * u;
            quad(batch, px, py, cos, sin, ax, ay, bx, by, bx + ox, by + oy, ax + ox, ay + oy);
        }
    }

    /** A part's outline (its triangles) moved ({@code ox}, {@code oy}) body meters. */
    private void foot(Batch batch, float[] t, float ox, float oy, float px, float py, float cos, float sin) {
        for (int i = 0; i + 5 < t.length; i += 6) {
            triangle(batch, px, py, cos, sin, t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5], ox, oy);
        }
    }

    /** Ends a stencil pass: draws {@code art} in black at {@link #shadowStrength} where the stencil is {@code mark}. */
    private void darken(Batch batch, int mark, Drawer art) {
        GL20 gl = Gdx.gl;
        batch.flush();
        gl.glColorMask(true, true, true, true);
        gl.glStencilFunc(GL20.GL_EQUAL, mark, 0xff);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        Color was = batch.getColor().cpy();
        batch.setColor(0f, 0f, 0f, shadowStrength);
        art.draw();
        batch.flush();
        batch.setColor(was);
        gl.glDisable(GL20.GL_STENCIL_TEST);
    }

    /** Whether what is drawn to has a stencil buffer, which the shadows are cut out with (the window's back buffer must ask for one). */
    private boolean stencil() {
        if (stencilBits < 0) {
            java.nio.IntBuffer b = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
            Gdx.gl.glGetIntegerv(GL20.GL_STENCIL_BITS, b);
            stencilBits = b.get(0);
            if (stencilBits == 0) Gdx.app.error("HullShading", "No stencil buffer: drawing the hulls without roof shadows");
        }
        return stencilBits > 0;
    }

    /** Draws a hull's roof or its raised parts, for {@link #shadows} or {@link #raisedShadows} to darken. */
    public interface Drawer {
        void draw();
    }

    /** Each relief part's outline cut into triangles, in body meters (x, y-up), worked out once per art. */
    private float[][] trianglesOf(float[][] relief) {
        float[][] t = triangles.get(relief);
        if (t != null) return t;
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        t = new float[relief.length][];
        for (int k = 0; k < relief.length; k++) {
            float[] part = relief[k];
            int n = (part.length - 1) / 2;
            float[] pts = new float[2 * n];
            for (int i = 0; i < n; i++) {
                pts[2 * i] = part[1 + 2 * i] * u;
                pts[2 * i + 1] = -part[2 + 2 * i] * u;
            }
            ShortArray idx = triangulator.computeTriangles(pts);
            float[] out = new float[idx.size * 2];
            for (int i = 0; i < idx.size; i++) {
                out[2 * i] = pts[2 * idx.get(i)];
                out[2 * i + 1] = pts[2 * idx.get(i) + 1];
            }
            t[k] = out;
        }
        triangles.put(relief, t);
        return t;
    }

    private void triangle(Batch batch, float px, float py, float cos, float sin,
                          float ax, float ay, float bx, float by, float cx, float cy, float ox, float oy) {
        quad(batch, px, py, cos, sin, ax + ox, ay + oy, bx + ox, by + oy, cx + ox, cy + oy, cx + ox, cy + oy);
    }

    /** A quad (a triangle when two corners meet) in body meters, drawn in the world with the white texture. */
    private void quad(Batch batch, float px, float py, float cos, float sin,
                      float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float c = Color.WHITE_FLOAT_BITS;
        put(0, px + ax * cos - ay * sin, py + ax * sin + ay * cos, c);
        put(1, px + bx * cos - by * sin, py + bx * sin + by * cos, c);
        put(2, px + cx * cos - cy * sin, py + cx * sin + cy * cos, c);
        put(3, px + dx * cos - dy * sin, py + dx * sin + dy * cos, c);
        batch.draw(white, quad, 0, 20);
    }

    private void put(int i, float x, float y, float color) {
        quad[i * 5] = x;
        quad[i * 5 + 1] = y;
        quad[i * 5 + 2] = color;
        quad[i * 5 + 3] = 0.5f;
        quad[i * 5 + 4] = 0.5f;
    }

    @Override
    public void dispose() {
        if (shader != null) shader.dispose();
        if (white != null) white.dispose();
        shader = null;
        white = null;
    }
}
