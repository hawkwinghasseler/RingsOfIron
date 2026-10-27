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
 *   not on it.</li>
 * </ul>
 *
 * The lighting brightens and darkens about what the flat art is under the key light at its
 * plain roof, so the box2dlights light map ({@code ExteriorLighting}) still lays the starlight
 * ambient and the hulls' own lights over it as before.
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
            + "void main() {\n"
            + "  vec4 c = v_color * texture2D(u_texture, v_texCoords);\n"
            + "  vec2 t = (v_texCoords - u_albedoRect.xy) / (u_albedoRect.zw - u_albedoRect.xy);\n"
            + "  vec3 n = normalize(texture2D(u_normals, mix(u_normalRect.xy, u_normalRect.zw, t)).rgb * 2.0 - 1.0);\n"
            + "  float ambient = 1.0 - u_diffuse;\n"
            + "  float plain = ambient + u_diffuse * u_light.z;\n"
            + "  float lit = (ambient + u_diffuse * max(dot(n, u_light), 0.0)) / plain;\n"
            + "  gl_FragColor = vec4(c.rgb * lit, c.a);\n"
            + "}\n";

    private ShaderProgram shader;
    private Texture white;
    private boolean failed;
    private final Map<float[][], float[][]> triangles = new IdentityHashMap<>();
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private final float[] quad = new float[20];
    private float lx, ly, lz;
    private int stencilBits = -1;

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
        begin(batch, flat, normals);
        place.draw(flat);
        end(batch);
        AtlasRegion raised = art.region(ArtNames.litHull(hull.name, ArtNames.RAISED));
        if (mode != Mode.SHADOWS || raised == null || info.relief == null) return true;
        shadows(batch, info, px, py, angle, () -> place.draw(flat));
        begin(batch, raised, normals);
        place.draw(raised);
        end(batch);
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
        // The shadow runs away from the light, along the light's bearing turned into the body frame
        float run = 1f / (float) Math.tan(elevation);
        float flat = (float) Math.hypot(lx, ly);
        float dx = flat < 1e-4f ? 0f : -lx / flat * run, dy = flat < 1e-4f ? 0f : -ly / flat * run;
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        float[][] tris = trianglesOf(info.relief);
        for (int k = 0; k < info.relief.length; k++) {
            float[] part = info.relief[k];
            float h = part[0], ox = dx * h, oy = dy * h;
            int n = (part.length - 1) / 2;
            float[] t = tris[k];
            // The part's foot and its shadow's far end, and each side swept between them
            for (int i = 0; i + 5 < t.length; i += 6) {
                triangle(batch, px, py, cos, sin, t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5], 0f, 0f);
                triangle(batch, px, py, cos, sin, t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5], ox, oy);
            }
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ax = part[1 + 2 * i] * u, ay = -part[2 + 2 * i] * u;
                float bx = part[1 + 2 * j] * u, by = -part[2 + 2 * j] * u;
                quad(batch, px, py, cos, sin, ax, ay, bx, by, bx + ox, by + oy, ax + ox, ay + oy);
            }
        }
        batch.flush();
        gl.glColorMask(true, true, true, true);
        gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xff);
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        // The roof itself in black: shadow only where there is hull under it
        Color was = batch.getColor().cpy();
        batch.setColor(0f, 0f, 0f, shadowStrength);
        drawRoof.draw();
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

    /** Draws a hull's roof, for {@link #shadows} to darken. */
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
