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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import com.ringsofiron.interiorgrid.HullPiece;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * How the deck view's light inside the decks gives way to the light outside (starlight, the
 * shield's own light, engines) at the hull. A weight map, 1 deep in the decks and 0 off them,
 * mixes the two light maps: the decks' where it is 1, the outside's where it is 0, and between
 * them in proportion, so at the hull's edge the one fades into the other instead of meeting it at
 * a hard line.
 *
 * <p>The weight rises from 0 at the hull's edge to 1 a tile in ({@link #WIDTH}), so the
 * outermost tile (a hull wall block, say) is lit from outside on its outer side and from inside
 * on its inner side. It is set by how far each point lies from the edge, in tiles, at the corners
 * of a grid of half tiles and on the slopes of sloped armor, and runs smoothly between them. The
 * edge is the outline of the footprint (tiles that are neither void nor an engine's casing), with
 * sloped armor as its triangle, so a slope is lit from outside and fades in behind it as a flat
 * side does. How far is measured along the grid, across plus up ({@link #toSegment}): square to
 * a side that is a tile, but square to a slope only half a tile's diagonal, so the fade behind a
 * slope ends on the line through the inner corners of the outermost tiles, and a wall block that
 * touches the edge at one corner alone is lit from inside from its middle on. {@link #fade} sets
 * how the fade runs between (for comparing). Where the shader won't compile, the decks keep a
 * hard edge at the hull, drawn with the stencil buffer instead.
 *
 * <p>Each frame in the deck view: {@link #begin} with the weights ({@code DeckRenderer}), then
 * {@link #apply} the decks' light map inside, then the outside's ({@code ExteriorRenderer}).
 */
public final class DeckEdgeBlend implements Disposable {
    /** The developer overlay of the weights, in the developer console. */
    public static final String NAME = "lightMask";
    /** What the overlay shows, for the console's overlay list and the debug menu. */
    public static final String DESCRIPTION = "the deck view's light mask alone: red where the decks' light is used, blue the light"
            + " outside, yellow lines at all inside, half and half, and all outside";

    /** How far in from the hull's edge the decks' light has wholly taken over from the light outside, in tiles. */
    static final float WIDTH = 1f;

    /** How each tile is split for the weights, per side. */
    static final int SUBDIVISIONS = 4;

    /** How the fade runs across the outer tile: straight or eased, its corners sharp or soft. */
    public enum Fade {
        /** Straight from the edge to a tile in, creased where two faces' fades meet. */
        LINEAR(false, false, false),
        /** Eased in and out (smoothstep), so it starts and ends gently. */
        EASED(true, false, false),
        /**
         * Straight, but where two faces' fades meet the crease is rounded ({@link #SOFT_CORNER})
         * through the middle of the fade: where it starts and ends stay as {@link #LINEAR} has them.
         */
        SOFT(false, true, false),
        /** Both. */
        SOFT_EASED(true, true, false),
        /**
         * Rounded as {@link #SOFT}, where it starts staying as {@link #LINEAR} has it, but rounded
         * right through to where it ends, which comes out toward the edge by {@link #SOFT_CORNER}
         * so the rounding never takes it further in than {@link #LINEAR}'s.
         */
        HYBRID(false, true, true);

        /** Whether the fade is eased, its creases rounded, and where it ends free to come out. */
        public final boolean eased, soft, outward;

        Fade(boolean eased, boolean soft, boolean outward) {
            this.eased = eased;
            this.soft = soft;
            this.outward = outward;
        }

        public String label() {
            return "Deck fade: " + (outward ? "soft corners, the inner line free to come out, "
                    : soft ? "soft corners, " : "sharp corners, ") + (eased ? "eased" : "straight");
        }

        /** The fade named {@code name} (any case), or null. */
        public static Fade named(String name) {
            try {
                return name == null ? null : valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** How the fade runs, for every deck; the developer console's {@code deckFade} changes it. */
    public static Fade fade = Fade.SOFT;

    /**
     * How far round a crease the fade is rounded, {@link Fade#soft}, in tiles: the distances to
     * the edge are smoothed over about this far (a binomial filter across the split grid), which
     * leaves them as they were along a straight face and rounds them where faces meet.
     */
    static final float SOFT_CORNER = 0.25f;

    /** The weight {@code d} tiles in from the edge: 0 there, rising to 1 {@link #WIDTH} tiles in. */
    static float weight(float d) {
        return MathUtils.clamp(d / WIDTH, 0f, 1f);
    }

    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  v_uv = a_texCoord0;\n"
            + "  gl_Position = a_position;\n"
            + "}\n";
    /**
     * A light map with its ambient (as box2dlights' diffuse pass) laid on at the weight. Inside,
     * the decks' at the weight and none where it is 0. Outside, what turns that into the two
     * mixed by the weight: the outside's alone where it is 0, and nothing where it is 1.
     */
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "uniform sampler2D u_light;\n"
            + "uniform sampler2D u_weight;\n"
            + "uniform sampler2D u_inner;\n"
            + "uniform vec3 u_ambient;\n"
            + "uniform vec3 u_innerAmbient;\n"
            + "uniform float u_inside;\n"
            + "uniform float u_ease;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  vec4 at = texture2D(u_weight, v_uv);\n"
            + "  float w = min(at.r + at.g, 1.0);\n"
            + "  w = mix(w, w * w * (3.0 - 2.0 * w), u_ease);\n"
            + "  vec3 light = min(u_ambient + texture2D(u_light, v_uv).rgb, vec3(1.0));\n"
            + "  if (u_inside > 0.5) {\n"
            + "    gl_FragColor = vec4(mix(vec3(1.0), light, w), 1.0);\n"
            + "  } else {\n"
            + "    vec3 inner = min(u_innerAmbient + texture2D(u_inner, v_uv).rgb, vec3(1.0));\n"
            + "    vec3 laid = mix(vec3(1.0), inner, w);\n"
            + "    gl_FragColor = vec4(mix(light, inner, w) / max(laid, vec3(0.004)), 1.0);\n"
            + "  }\n"
            + "}\n";
    /**
     * The weights alone, for the overlay: red for the decks' light, blue for the light outside,
     * mixed between, and yellow lines where the decks' light stops being all there is, where the
     * two are half and half, and where the light outside stops being all there is (each the first
     * pixel short of the weight, beside one at it).
     */
    private static final String MASK = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "uniform sampler2D u_weight;\n"
            + "uniform vec2 u_texel;\n"
            + "uniform float u_ease;\n"
            + "varying vec2 v_uv;\n"
            + "float weight(vec2 uv) { vec4 at = texture2D(u_weight, uv); float w = min(at.r + at.g, 1.0); return mix(w, w * w * (3.0 - 2.0 * w), u_ease); }\n"
            + "float at(vec2 uv, float t) { return step(t, weight(uv)); }\n"
            + "float line(float t) {\n"
            + "  float around = max(max(at(v_uv + vec2(u_texel.x, 0.0), t), at(v_uv - vec2(u_texel.x, 0.0), t)),\n"
            + "      max(at(v_uv + vec2(0.0, u_texel.y), t), at(v_uv - vec2(0.0, u_texel.y), t)));\n"
            + "  return around * (1.0 - at(v_uv, t));\n"
            + "}\n"
            + "void main() {\n"
            + "  float w = weight(v_uv);\n"
            + "  float lines = max(max(line(0.998), line(0.5)), line(0.002));\n"
            + "  gl_FragColor = vec4(mix(vec3(w, 0.0, 1.0 - w), vec3(1.0, 0.9, 0.0), lines), 1.0);\n"
            + "}\n";

    private FrameBuffer weights;
    private ShaderProgram maskShader;
    /** Whether the overlay of the weights is drawn ({@link #drawMask}), and whether this frame has weights to draw. */
    private boolean maskShown, drawn;
    private ShaderProgram shader;
    private Mesh quad;
    private boolean ready;
    /** The decks' light map and ambient this frame, for the outside's pass to mix with. */
    private Texture inner;
    private final Color innerAmbient = new Color();
    private final IntBuffer viewport = BufferUtils.newIntBuffer(16);
    private final int[] saved = new int[4];

    /** True when this frame's weights are drawn and the light maps should be laid on with them. */
    public boolean ready() {
        return ready;
    }

    /**
     * Starts the weights for this frame: binds a buffer the size of the current viewport, cleared
     * to 0, for the caller to fill (the weight: red, and green past 1) under its camera; {@link #end} then puts the
     * viewport back. False, with nothing bound, where the shader won't compile.
     */
    public boolean begin() {
        ready = false;
        drawn = false;
        inner = null;
        if (!compiled()) return false;
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
        drawn = true;
    }

    /** Whether the developer overlay of the weights is shown. */
    public boolean maskShown() {
        return maskShown;
    }

    public void setMaskShown(boolean shown) {
        maskShown = shown;
    }

    /**
     * The developer overlay, when it is shown: this frame's weights alone over the whole view,
     * red where the decks' light is used and blue where the light outside is, mixed between.
     * Drawn over the deck view's world, in its viewport; nothing where there were no weights this
     * frame (another view, or the shader would not compile).
     */
    public void drawMask() {
        boolean fresh = drawn;
        drawn = false;
        if (!maskShown || !fresh) return;
        if (maskShader == null) maskShader = new ShaderProgram(VERTEX, MASK);
        if (!maskShader.isCompiled()) return;
        Gdx.gl.glDisable(GL20.GL_BLEND);
        weights.getColorBufferTexture().bind(0);
        maskShader.bind();
        maskShader.setUniformi("u_weight", 0);
        maskShader.setUniformf("u_ease", fade.eased ? 1f : 0f);
        maskShader.setUniformf("u_texel", 1f / weights.getWidth(), 1f / weights.getHeight());
        quad.render(maskShader, GL20.GL_TRIANGLE_FAN);
    }

    /**
     * Lays {@code light} (a box2dlights light map) with {@code ambient} over what is drawn, in the
     * current viewport: the decks' map ({@code inside}) at the weight, then the outside's so that
     * together the two are mixed by the weight. The outside's pass ends the frame's weights; the
     * decks' map must stand until then.
     */
    public void apply(Texture light, Color ambient, boolean inside) {
        if (!ready || (!inside && inner == null)) return;
        if (inside) {
            inner = light;
            innerAmbient.set(ambient);
        }
        GL20 gl = Gdx.gl;
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        inner.bind(2);
        weights.getColorBufferTexture().bind(1);
        light.bind(0);
        shader.bind();
        shader.setUniformi("u_light", 0);
        shader.setUniformi("u_weight", 1);
        shader.setUniformi("u_inner", 2);
        shader.setUniformf("u_ambient", ambient.r, ambient.g, ambient.b);
        shader.setUniformf("u_innerAmbient", innerAmbient.r, innerAmbient.g, innerAmbient.b);
        shader.setUniformf("u_inside", inside ? 1f : 0f);
        shader.setUniformf("u_ease", fade.eased ? 1f : 0f);
        quad.render(shader, GL20.GL_TRIANGLE_FAN);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (!inside) {
            ready = false;
            inner = null;
        }
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
     * Sloped armor {@code p} as a triangle in tiles from its grid's bottom-left corner: the corner
     * of its footprint where its legs meet, then the ends of its slope, counter-clockwise. At
     * rotation 0 the legs meet at the bottom left; each quarter turn moves them a corner on,
     * counter-clockwise (as {@code HullShape} collides on it).
     */
    static int[] wedge(HullPiece p) {
        int x0 = p.originX, y0 = p.originY, x1 = x0 + p.width(), y1 = y0 + p.height();
        int[] corners = {x0, y0, x1, y0, x1, y1, x0, y1};
        int a = p.rotation, b = (a + 1) % 4, c = (a + 3) % 4;
        return new int[]{corners[2 * a], corners[2 * a + 1], corners[2 * b], corners[2 * b + 1], corners[2 * c], corners[2 * c + 1]};
    }

    /**
     * A deck's footprint as the weights see it, in tiles from its grid's bottom-left corner: its
     * whole tiles {@code on} ([y][x]) and its sloped armor, each a {@link #wedge} triangle. Its
     * edge is every side of them that does not lie against another of them, and every slope.
     */
    static final class Outline {
        private final boolean[][] on;
        private final int width, height;
        /** The wedges' corners, six to each. */
        private final IntArray wedges;
        /** x0, y0, x1, y1 of each stretch of the edge. */
        private final IntArray edge = new IntArray();

        Outline(boolean[][] on, IntArray wedges) {
            this.on = on;
            this.height = on.length;
            this.width = height == 0 ? 0 : on[0].length;
            this.wedges = wedges;
            // Every side of every piece, counter-clockwise round it: a side that two pieces share
            // runs one way for each, and is not the edge
            LongArray sides = new LongArray();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!on[y][x]) continue;
                    sides.add(side(x, y, x + 1, y));
                    sides.add(side(x + 1, y, x + 1, y + 1));
                    sides.add(side(x + 1, y + 1, x, y + 1));
                    sides.add(side(x, y + 1, x, y));
                }
            }
            for (int i = 0; i < wedges.size; i += 6) {
                int ax = wedges.get(i), ay = wedges.get(i + 1), bx = wedges.get(i + 2), by = wedges.get(i + 3);
                int cx = wedges.get(i + 4), cy = wedges.get(i + 5);
                // The legs a tile at a time, to meet the tiles beside them; the slope whole
                leg(sides, ax, ay, bx, by);
                sides.add(side(bx, by, cx, cy));
                leg(sides, cx, cy, ax, ay);
            }
            java.util.HashSet<Long> all = new java.util.HashSet<>();
            for (int i = 0; i < sides.size; i++) all.add(sides.get(i));
            // What is left is the edge; each run of it along one line is one face, so faces meet
            // only at the hull's corners
            java.util.HashMap<Long, Long> from = new java.util.HashMap<>(), to = new java.util.HashMap<>();
            for (int i = 0; i < sides.size; i++) {
                long s = sides.get(i);
                if (all.contains(side(x1(s), y1(s), x0(s), y0(s)))) continue;
                from.put(point(x0(s), y0(s)) << 3 | heading(s), s);
                to.put(point(x1(s), y1(s)) << 3 | heading(s), s);
            }
            for (long s : from.values()) {
                // A face starts where no stretch along its line ends
                int h = heading(s);
                Long before = to.get(point(x0(s), y0(s)) << 3 | h);
                if (before != null && parallel(before, s)) continue;
                long end = s;
                for (Long next = from.get(point(x1(end), y1(end)) << 3 | h); next != null; next = from.get(point(x1(end), y1(end)) << 3 | h)) {
                    if (!parallel(next, s)) break;
                    end = next;
                }
                edge.add(x0(s), y0(s), x1(end), y1(end));
            }
        }

        private static int x0(long s) { return (int) (s >>> 48 & 0xffff); }
        private static int y0(long s) { return (int) (s >>> 32 & 0xffff); }
        private static int x1(long s) { return (int) (s >>> 16 & 0xffff); }
        private static int y1(long s) { return (int) (s & 0xffff); }

        private static long point(int x, int y) {
            return (long) x << 16 | y;
        }

        /** Which of the eight ways round {@code s} heads, 0 to 7. */
        private static int heading(long s) {
            int dx = Integer.signum(x1(s) - x0(s)), dy = Integer.signum(y1(s) - y0(s));
            return (dx + 1) * 3 + dy + 1 - ((dx + 1) * 3 + dy + 1 > 4 ? 1 : 0);
        }

        /** Whether {@code a} and {@code b} run along one line the same way. */
        private static boolean parallel(long a, long b) {
            return (long) (x1(a) - x0(a)) * (y1(b) - y0(b)) == (long) (y1(a) - y0(a)) * (x1(b) - x0(b));
        }

        private static long side(int x0, int y0, int x1, int y1) {
            return (long) x0 << 48 | (long) y0 << 32 | (long) x1 << 16 | y1;
        }

        /** The side from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), along a row or column, a tile at a time. */
        private static void leg(LongArray sides, int x0, int y0, int x1, int y1) {
            int dx = Integer.signum(x1 - x0), dy = Integer.signum(y1 - y0);
            for (int x = x0, y = y0; x != x1 || y != y1; x += dx, y += dy) sides.add(side(x, y, x + dx, y + dy));
        }

        /** How far ({@code x}, {@code y}) lies from the edge, in tiles across plus up ({@link #toSegment}). */
        float distance(float x, float y) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < edge.size; i += 4) {
                best = Math.min(best, toSegment(x, y, edge.get(i), edge.get(i + 1), edge.get(i + 2), edge.get(i + 3)));
            }
            return best;
        }

        /**
         * The weights at the corners of the grid's tiles split {@link #SUBDIVISIONS}
         * ways each side: {@code (width * n + 1) * (height * n + 1)} of them, row by row from the
         * bottom. Each is as far in as the nearest point of the edge ({@link #distance}); past 1
         * (as far as 2) deep in, for {@link Fade#outward}.
         */
        float[] cornerWeights() {
            int n = SUBDIVISIONS, cw = width * n + 1, ch = height * n + 1;
            float[] d = new float[cw * ch];
            Arrays.fill(d, Float.MAX_VALUE);
            // Past this far from the edge the weight is 1 whatever the distance: each stretch
            // reaches only the corners within it
            float reach = WIDTH + (fade.outward ? 4f : fade.soft ? 2f : 0f) * SOFT_CORNER;
            for (int i = 0; i < edge.size; i += 4) {
                int x0 = edge.get(i), y0 = edge.get(i + 1), x1 = edge.get(i + 2), y1 = edge.get(i + 3);
                int vx0 = Math.max(0, MathUtils.floor((Math.min(x0, x1) - reach) * n));
                int vx1 = Math.min(cw - 1, MathUtils.ceil((Math.max(x0, x1) + reach) * n));
                int vy0 = Math.max(0, MathUtils.floor((Math.min(y0, y1) - reach) * n));
                int vy1 = Math.min(ch - 1, MathUtils.ceil((Math.max(y0, y1) + reach) * n));
                for (int vy = vy0; vy <= vy1; vy++) {
                    for (int vx = vx0; vx <= vx1; vx++) {
                        int at = vy * cw + vx;
                        d[at] = Math.min(d[at], toSegment(vx / (float) n, vy / (float) n, x0, y0, x1, y1));
                    }
                }
            }
            if (!fade.soft) {
                for (int i = 0; i < d.length; i++) d[i] = weight(d[i]);
                return d;
            }
            float[] soft = d.clone();
            soften(soft, cw, ch, reach);
            for (int i = 0; i < d.length; i++) {
                float sharp = weight(d[i]);
                if (fade.outward) {
                    // Rounded right through to where it ends, but a fade SOFT_CORNER shorter, so
                    // where the rounding cuts a corner off the end it still reaches as far out as
                    // the sharp fade's; and less so toward where it starts, which stays put. Past
                    // 1 as far as 2, so where it ends is drawn between the corners too (see shade)
                    float to = MathUtils.clamp(soft[i] / (WIDTH - SOFT_CORNER), 0f, 2f);
                    float half = MathUtils.clamp(2f * sharp, 0f, 1f);
                    d[i] = sharp + (to - sharp) * half * half * (3f - 2f * half);
                } else {
                    // Rounded through the middle of the fade, and less so toward its ends, so
                    // where it starts and where it ends stay where the sharp fade has them
                    d[i] = sharp + (weight(soft[i]) - sharp) * 4f * sharp * (1f - sharp);
                }
            }
            return d;
        }

        /**
         * Smooths the distances {@code d} at the split grid's corners ({@code cw} by {@code ch})
         * over about {@link #SOFT_CORNER}: negative off the footprint first, so they run straight
         * through the edge, and no further than {@code reach}, past which they are all alike to the
         * weight. Along a straight face they stay as they were; where faces meet they round.
         */
        private void soften(float[] d, int cw, int ch, float reach) {
            int n = SUBDIVISIONS;
            boolean[] in = new boolean[cw * ch];
            for (int vy = 0; vy < ch; vy++) {
                for (int vx = 0; vx < cw; vx++) {
                    in[vy * cw + vx] = cell(vx - 1, vy - 1) || cell(vx, vy - 1) || cell(vx - 1, vy) || cell(vx, vy);
                }
            }
            for (int i = 0; i < wedges.size; i += 6) {
                int ax = wedges.get(i), ay = wedges.get(i + 1), bx = wedges.get(i + 2), by = wedges.get(i + 3);
                int cx = wedges.get(i + 4), cy = wedges.get(i + 5);
                for (int vy = Math.min(ay, Math.min(by, cy)) * n; vy <= Math.max(ay, Math.max(by, cy)) * n; vy++) {
                    for (int vx = Math.min(ax, Math.min(bx, cx)) * n; vx <= Math.max(ax, Math.max(bx, cx)) * n; vx++) {
                        float px = vx / (float) n, py = vy / (float) n;
                        if ((cx - bx) * (py - by) - (cy - by) * (px - bx) >= 0f) in[vy * cw + vx] = true;
                    }
                }
            }
            for (int i = 0; i < d.length; i++) d[i] = in[i] ? Math.min(d[i], reach) : -Math.min(d[i], reach);
            // A binomial filter (1 4 6 4 1 across a quarter-tile grid) each way: spread about SOFT_CORNER
            int r = Math.max(1, Math.round(SOFT_CORNER * n));
            float[] kernel = binomial(4 * r);
            float[] t = new float[d.length];
            for (int pass = 0; pass < 2; pass++) {
                for (int vy = 0; vy < ch; vy++) {
                    for (int vx = 0; vx < cw; vx++) {
                        float sum = 0f;
                        for (int k = 0; k < kernel.length; k++) {
                            int o = k - kernel.length / 2;
                            int x = pass == 0 ? MathUtils.clamp(vx + o, 0, cw - 1) : vx, y = pass == 1 ? MathUtils.clamp(vy + o, 0, ch - 1) : vy;
                            sum += kernel[k] * d[y * cw + x];
                        }
                        t[vy * cw + vx] = sum;
                    }
                }
                System.arraycopy(t, 0, d, 0, d.length);
            }
        }

        /** Whether cell ({@code cx}, {@code cy}) of the split grid lies on a whole tile of the footprint. */
        private boolean cell(int cx, int cy) {
            int tx = Math.floorDiv(cx, SUBDIVISIONS), ty = Math.floorDiv(cy, SUBDIVISIONS);
            return ty >= 0 && ty < height && tx >= 0 && tx < width && on[ty][tx];
        }

        /** The binomial weights of {@code order} + 1 taps, summing to 1. */
        private static float[] binomial(int order) {
            float[] k = new float[order + 1];
            k[0] = 1f;
            for (int i = 1; i <= order; i++) for (int j = i; j > 0; j--) k[j] += k[j - 1];
            float sum = 0f;
            for (float v : k) sum += v;
            for (int i = 0; i < k.length; i++) k[i] /= sum;
            return k;
        }

        /**
         * Fills the footprint with its weights, in red, with {@code shapes} (begun
         * to fill, in the deck's frame), the grid's bottom-left corner at ({@code x}, {@code y})
         * and its tiles {@code size} across: each half tile shaded between the weights at its
         * corners, those under sloped armor cut along the slope.
         */
        void fill(ShapeRenderer shapes, float x, float y, float size) {
            int n = SUBDIVISIONS, cw = width * n + 1;
            float[] w = cornerWeights();
            float step = size / n;
            boolean[][] whole = new boolean[height][width];
            for (int ty = 0; ty < height; ty++) for (int tx = 0; tx < width; tx++) whole[ty][tx] = on[ty][tx] && whole(w, cw, tx, ty);
            for (int cy = 0; cy < height * n; cy++) {
                for (int cx = 0; cx < width * n; cx++) {
                    int tx = cx / n, ty = cy / n;
                    if (!on[ty][tx]) continue;
                    // A tile lit wholly from inside, in one piece
                    if (cx % n == 0 && cy % n == 0 && whole[ty][tx]) {
                        float tx0 = x + tx * size, ty0 = y + ty * size;
                        shapes.triangle(tx0, ty0, tx0 + size, ty0, tx0 + size, ty0 + size, Color.WHITE, Color.WHITE, Color.WHITE);
                        shapes.triangle(tx0, ty0, tx0 + size, ty0 + size, tx0, ty0 + size, Color.WHITE, Color.WHITE, Color.WHITE);
                    }
                    if (whole[ty][tx]) continue;
                    float x0 = x + cx * step, y0 = y + cy * step, x1 = x0 + step, y1 = y0 + step;
                    float w00 = w[cy * cw + cx], w10 = w[cy * cw + cx + 1], w01 = w[(cy + 1) * cw + cx], w11 = w[(cy + 1) * cw + cx + 1];
                    shade(c00, w00);
                    shade(c10, w10);
                    shade(c01, w01);
                    shade(c11, w11);
                    // Split along the diagonal the weight changes least along, so a fade square to
                    // a slope either way runs straight across the half tiles instead of in steps
                    if (Math.abs(w00 - w11) <= Math.abs(w10 - w01)) {
                        shapes.triangle(x0, y0, x1, y0, x1, y1, c00, c10, c11);
                        shapes.triangle(x0, y0, x1, y1, x0, y1, c00, c11, c01);
                    } else {
                        shapes.triangle(x0, y0, x1, y0, x0, y1, c00, c10, c01);
                        shapes.triangle(x1, y0, x1, y1, x0, y1, c10, c11, c01);
                    }
                }
            }
            for (int i = 0; i < wedges.size; i += 6) {
                int ax = wedges.get(i), ay = wedges.get(i + 1), bx = wedges.get(i + 2), by = wedges.get(i + 3);
                int cx = wedges.get(i + 4), cy = wedges.get(i + 5);
                int left = Math.min(ax, Math.min(bx, cx)), right = Math.max(ax, Math.max(bx, cx));
                int bottom = Math.min(ay, Math.min(by, cy)), top = Math.max(ay, Math.max(by, cy));
                for (int sy = bottom * n; sy < top * n; sy++) {
                    for (int sx = left * n; sx < right * n; sx++) {
                        int k = cut(sx, sy, bx, by, cx, cy);
                        for (int j = 0; j < k; j++) {
                            float px = cut[2 * j], py = cut[2 * j + 1];
                            float gx = px * n, gy = py * n;
                            // A corner of the half tiles has its weight; a point on the slope is measured
                            cutWeight[j] = gx == (int) gx && gy == (int) gy ? w[(int) gy * cw + (int) gx] : weight(distance(px, py));
                        }
                        for (int j = 1; j + 1 < k; j++) {
                            shade(c00, cutWeight[0]);
                            shade(c10, cutWeight[j]);
                            shade(c11, cutWeight[j + 1]);
                            shapes.triangle(x + cut[0] * size, y + cut[1] * size, x + cut[2 * j] * size, y + cut[2 * j + 1] * size,
                                    x + cut[2 * j + 2] * size, y + cut[2 * j + 3] * size, c00, c10, c11);
                        }
                    }
                }
            }
        }

        /** Whether every corner in tile ({@code tx}, {@code ty}) of the split grid has weight 1. */
        private boolean whole(float[] w, int cw, int tx, int ty) {
            int n = SUBDIVISIONS;
            for (int vy = ty * n; vy <= ty * n + n; vy++) {
                for (int vx = tx * n; vx <= tx * n + n; vx++) if (w[vy * cw + vx] < 1f) return false;
            }
            return true;
        }

        /** The corners of what is drawn, in tiles, and their weights, for {@link #cut}. */
        private final float[] cut = new float[10], cutWeight = new float[5];
        private final Color c00 = new Color(), c10 = new Color(), c01 = new Color(), c11 = new Color();

        /**
         * Half tile ({@code sx}, {@code sy}) cut to the side of the slope from ({@code bx},
         * {@code by}) to ({@code cx}, {@code cy}) that its armor lies on (the left, going along
         * it): its corners, counter-clockwise, into {@link #cut}. How many there are, 0 to 5.
         */
        private int cut(int sx, int sy, int bx, int by, int cx, int cy) {
            float h = 1f / SUBDIVISIONS;
            float[] square = {sx * h, sy * h, (sx + 1) * h, sy * h, (sx + 1) * h, (sy + 1) * h, sx * h, (sy + 1) * h};
            int k = 0;
            for (int i = 0; i < 4; i++) {
                float px = square[2 * i], py = square[2 * i + 1], qx = square[(2 * i + 2) % 8], qy = square[(2 * i + 3) % 8];
                float fp = (cx - bx) * (py - by) - (cy - by) * (px - bx);
                float fq = (cx - bx) * (qy - by) - (cy - by) * (qx - bx);
                if (fp >= 0f) {
                    cut[2 * k] = px;
                    cut[2 * k + 1] = py;
                    k++;
                }
                if (fp > 0f && fq < 0f || fp < 0f && fq > 0f) {
                    float t = fp / (fp - fq);
                    cut[2 * k] = px + (qx - px) * t;
                    cut[2 * k + 1] = py + (qy - py) * t;
                    k++;
                }
            }
            return k;
        }

        /**
         * The weight {@code w} as a colour: red as far as 1, green past it as far as 2. The shaders
         * take their sum, as far as 1, so where the weights reach 1 is drawn between the corners as
         * the weights run on, not where the first corner is clamped to it.
         */
        private static void shade(Color c, float w) {
            c.set(Math.min(w, 1f), MathUtils.clamp(w - 1f, 0f, 1f), 0f, 1f);
        }
    }

    /**
     * How far ({@code x}, {@code y}) lies from the segment ({@code x0}, {@code y0}) to
     * ({@code x1}, {@code y1}), across plus up (the taxicab distance): along the grid's rows and
     * columns, as the tiles lie, so a slope at 45 degrees is half its diagonal away from a tile's
     * far corner. Least where the point's row or column crosses the segment, or at an end.
     */
    static float toSegment(float x, float y, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float best = Math.min(taxicab(x, y, x0, y0), taxicab(x, y, x1, y1));
        if (dx != 0f) best = Math.min(best, along(x, y, x0, y0, dx, dy, (x - x0) / dx));
        if (dy != 0f) best = Math.min(best, along(x, y, x0, y0, dx, dy, (y - y0) / dy));
        return best;
    }

    private static float along(float x, float y, float x0, float y0, float dx, float dy, float t) {
        if (t <= 0f || t >= 1f) return Float.MAX_VALUE;
        return taxicab(x, y, x0 + dx * t, y0 + dy * t);
    }

    private static float taxicab(float x, float y, float px, float py) {
        return Math.abs(x - px) + Math.abs(y - py);
    }

    @Override
    public void dispose() {
        if (weights != null) weights.dispose();
        if (shader != null) shader.dispose();
        if (maskShader != null) maskShader.dispose();
        if (quad != null) quad.dispose();
        weights = null;
        shader = null;
        maskShader = null;
        quad = null;
    }
}
