package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * How the deck view's light inside the decks gives way to the light outside (starlight, the
 * shield's own light, engines) at the hull. A weight map, 1 deep in the decks and 0 off them,
 * lays the decks' light map on with its weight and the outside one with the rest, so at the hull's
 * edge the one fades into the other instead of meeting it at a hard line.
 *
 * <p>The weight is set at the corners of a grid of half tiles by how far each lies from the edge
 * of the footprint (tiles that are neither void, an engine's casing nor sloped armor), in tiles,
 * and runs smoothly across each half tile between them. {@link Mode#HARD} keeps the old hard edge
 * at the hull, drawn with the stencil buffer instead.
 *
 * <p>Each frame in the deck view: {@link #begin} with the weights ({@code DeckRenderer}), then
 * {@link #apply} the decks' light map inside, then the outside's ({@code ExteriorRenderer}).
 */
public final class DeckEdgeBlend implements Disposable {
    /** Where the decks' light gives way to the light outside, in tiles from the hull's edge. */
    public enum Mode {
        /** Hard at the hull's edge (the stencil buffer), as before. */
        HARD(0f, 0f),
        /** Across the outer half of the outermost tile. */
        HALF(0f, 0.5f),
        /** Across the outermost tile: its outer edge lit from outside, its inner edge from inside. */
        TILE(0f, 1f),
        /** Across the two outermost tiles. */
        WIDE(0f, 2f),
        /** The outermost tile lit from outside, the gradient across the tile within it. */
        INSET(1f, 1f);

        /** How far in from the edge the gradient starts, and how wide it is, in tiles. */
        public final float inset, width;

        Mode(float inset, float width) {
            this.inset = inset;
            this.width = width;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String label() {
            switch (this) {
                case HARD: return "Deck edge: hard at the hull";
                case HALF: return "Deck edge: blended across the outer half tile";
                case TILE: return "Deck edge: blended across the outer tile";
                case WIDE: return "Deck edge: blended across the outer two tiles";
                default: return "Deck edge: outer tile lit outside, blended across the next";
            }
        }

        /** The mode named {@code name} (any case), or null. */
        public static Mode named(String name) {
            try {
                return name == null ? null : valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** How each tile is split for the weights, per side. */
    static final int SUBDIVISIONS = 2;

    /**
     * How the deck edge is drawn, for every renderer; {@code -Ddeck.edgeBlend=hard|half|tile|wide|inset}
     * sets it at start, and the dev console's {@code deckEdge} changes it.
     */
    public static Mode mode = fromProperty();

    private static Mode fromProperty() {
        Mode m = Mode.named(System.getProperty("deck.edgeBlend"));
        return m == null ? Mode.HARD : m;
    }

    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  v_uv = a_texCoord0;\n"
            + "  gl_Position = a_position;\n"
            + "}\n";
    /** A light map with its ambient (as box2dlights' diffuse pass), laid on at the weight: none of it where the weight is 0. */
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "uniform sampler2D u_light;\n"
            + "uniform sampler2D u_weight;\n"
            + "uniform vec3 u_ambient;\n"
            + "uniform float u_inside;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  vec3 light = u_ambient + texture2D(u_light, v_uv).rgb;\n"
            + "  float w = texture2D(u_weight, v_uv).r;\n"
            + "  if (u_inside < 0.5) w = 1.0 - w;\n"
            + "  gl_FragColor = vec4(mix(vec3(1.0), light, w), 1.0);\n"
            + "}\n";

    private FrameBuffer weights;
    private ShaderProgram shader;
    private Mesh quad;
    private boolean ready;
    private final IntBuffer viewport = BufferUtils.newIntBuffer(16);
    private final int[] saved = new int[4];

    /** True when this frame's weights are drawn and the light maps should be laid on with them. */
    public boolean ready() {
        return ready;
    }

    /**
     * Starts the weights for this frame: binds a buffer the size of the current viewport, cleared
     * to 0, for the caller to fill (red: the weight) under its camera; {@link #end} then puts the
     * viewport back. False, with nothing bound, in {@link Mode#HARD} or where the shader won't compile.
     */
    public boolean begin() {
        ready = false;
        if (mode == Mode.HARD || !compiled()) return false;
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        for (int i = 0; i < 4; i++) saved[i] = viewport.get(i);
        int w = Math.max(1, saved[2]), h = Math.max(1, saved[3]);
        if (weights == null || weights.getWidth() != w || weights.getHeight() != h) {
            if (weights != null) weights.dispose();
            weights = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            weights.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        weights.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        return true;
    }

    /** Ends the weights {@link #begin} started; the light maps are laid on with them until the frame's outside pass. */
    public void end() {
        weights.end(saved[0], saved[1], saved[2], saved[3]);
        ready = true;
    }

    /**
     * Lays {@code light} (a box2dlights light map) with {@code ambient} over what is drawn, in the
     * current viewport: at the weight {@code inside} (the decks' map), else at the rest of it (the
     * outside's). The outside's pass ends the frame's weights.
     */
    public void apply(Texture light, Color ambient, boolean inside) {
        if (!ready) return;
        GL20 gl = Gdx.gl;
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        weights.getColorBufferTexture().bind(1);
        light.bind(0);
        shader.bind();
        shader.setUniformi("u_light", 0);
        shader.setUniformi("u_weight", 1);
        shader.setUniformf("u_ambient", ambient.r, ambient.g, ambient.b);
        shader.setUniformf("u_inside", inside ? 1f : 0f);
        quad.render(shader, GL20.GL_TRIANGLE_FAN);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (!inside) ready = false;
    }

    private boolean compiled() {
        if (shader != null) return shader.isCompiled();
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERTEX, FRAGMENT);
        if (!shader.isCompiled()) {
            Gdx.app.error("DeckEdgeBlend", "Shader would not compile, keeping the hard deck edge: " + shader.getLog());
            return false;
        }
        quad = new Mesh(true, 4, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        quad.setVertices(new float[]{-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f});
        return true;
    }

    /**
     * The weights at the corners of the {@link #SUBDIVISIONS}-split cells of a grid whose tiles
     * {@code on} ([y][x]) are in the footprint: {@code (w * n + 1) * (h * n + 1)} of them, row by
     * row from the bottom. A corner touching any cell off the footprint (or past the grid) is at
     * the edge, 0 tiles in; the rest are as far in as the fewest steps to an edge corner, a step
     * to any of the eight round it being 1/n tile. The weight rises from 0 at {@code inset} tiles
     * in to 1 at {@code inset + width}.
     */
    static float[] cornerWeights(boolean[][] on, float inset, float width) {
        int n = SUBDIVISIONS, h = on.length, w = h == 0 ? 0 : on[0].length;
        int cw = w * n + 1, ch = h * n + 1;
        int[] steps = new int[cw * ch];
        java.util.Arrays.fill(steps, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int vy = 0; vy < ch; vy++) {
            for (int vx = 0; vx < cw; vx++) {
                if (cell(on, vx - 1, vy - 1) && cell(on, vx, vy - 1) && cell(on, vx - 1, vy) && cell(on, vx, vy)) continue;
                steps[vy * cw + vx] = 0;
                queue.add(vy * cw + vx);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll(), vx = i % cw, vy = i / cw;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = vx + dx, y = vy + dy;
                    if (x < 0 || y < 0 || x >= cw || y >= ch || steps[y * cw + x] <= steps[i] + 1) continue;
                    steps[y * cw + x] = steps[i] + 1;
                    queue.add(y * cw + x);
                }
            }
        }
        float[] out = new float[cw * ch];
        for (int i = 0; i < out.length; i++) {
            float d = steps[i] / (float) n;
            out[i] = width <= 0f ? (d > inset ? 1f : 0f) : MathUtils.clamp((d - inset) / width, 0f, 1f);
        }
        return out;
    }

    /** Whether cell ({@code cx}, {@code cy}) of the split grid lies on a footprint tile. */
    private static boolean cell(boolean[][] on, int cx, int cy) {
        int tx = Math.floorDiv(cx, SUBDIVISIONS), ty = Math.floorDiv(cy, SUBDIVISIONS);
        return ty >= 0 && ty < on.length && tx >= 0 && tx < on[ty].length && on[ty][tx];
    }

    @Override
    public void dispose() {
        if (weights != null) weights.dispose();
        if (shader != null) shader.dispose();
        if (quad != null) quad.dispose();
        weights = null;
        shader = null;
        quad = null;
    }
}
