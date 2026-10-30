package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.PlayerOrbitInfo;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

import java.util.function.Supplier;

/**
 * Faint motes of "space snow" behind the exterior and deck views, so the player can read how fast they are
 * moving. The snow is not fixed to the screen, the bubble or the ship: it rides a circular orbit at
 * the ship's altitude, prograde with the ship, around the body whose sphere of influence the ship is
 * in. A ship in a circular orbit sees it hang still; any eccentricity, burn or intercept shows as
 * drift, and the faster the drift the longer each mote's streak ({@link #drift}).
 *
 * <p>The snow lies in {@link #LAYERS} layers at depths behind the play plane, from 1 to
 * {@link #MAX_DEPTH} (log-spaced), most of it toward the far end: deeper layers are denser, dimmer
 * and finer. It is a backdrop, so how fast it slides does not depend on zoom: a layer at depth
 * {@code d} moves {@code drift / (REFERENCE_MPP * d)} pixels a second ({@link #pixelsPerSecond}),
 * the nearest layer as the true drift looks at the game's starting zoom. Motes are laid out in
 * screen pixels, so zooming never makes them thicken or thin out, and they fade away when they move
 * too far between frames to be followed (at time warp).
 */
public final class SpaceSnow {
    /** As a developer overlay ({@code overlay("snow")} and the debug menu); shown by default. */
    public static final String NAME = "snow";
    public static final String DESCRIPTION = "the space snow that shows speed against a circular orbit at the ship's altitude (on in play)";
    /** How many layers, and the depth of the farthest (the nearest is at depth 1). */
    static final int LAYERS = 8;
    static final float MAX_DEPTH = 40f;
    /**
     * Meters per pixel the nearest layer moves at, whatever the zoom: the game's starting view
     * (160 m across a 1080-pixel screen).
     */
    static final float REFERENCE_MPP = 0.15f;
    /** Each layer's depth, from the farthest (drawn first) to the nearest. */
    private static final float[] DEPTH = new float[LAYERS];
    /** Each layer's spacing, pixels: one cell holds at most one mote. Deeper layers are denser. */
    private static final float[] CELL = new float[LAYERS];
    /** Each layer's brightest mote, and its shortest length, pixels. */
    private static final float[] ALPHA = new float[LAYERS];
    private static final float[] MIN_MOTE = new float[LAYERS];

    static {
        for (int k = 0; k < LAYERS; k++) {
            float d = (float) Math.pow(MAX_DEPTH, 1.0 - k / (double) (LAYERS - 1));
            DEPTH[k] = d;
            CELL[k] = 240f / (float) Math.pow(d, 0.4);
            ALPHA[k] = 0.44f / (float) Math.pow(d, 0.24);
            MIN_MOTE[k] = Math.max(1f, 1.8f / (float) Math.pow(d, 0.15));
        }
    }

    /** Of the cells, the share that hold a mote. */
    private static final float FILL = 0.65f;
    /** A mote's streak shows where it was this long ago, at most {@link #MAX_STREAK} of its cell. */
    private static final float STREAK_SECONDS = 0.08f;
    private static final float MAX_STREAK = 0.45f;
    /** Moving more than FADE_START of its cell in one frame a mote dims, gone by FADE_END. */
    private static final float FADE_START = 0.2f;
    private static final float FADE_END = 0.4f;
    /** The longest frame the snow moves on for; anything longer (a load, a long warp) starts it afresh. */
    private static final double MAX_STEP = 600.0;
    private static final Color MOTE = new Color(0.78f, 0.84f, 0.95f, 1f);

    private final Supplier<GameWorld> world;
    private final Supplier<Entity> player;
    private PlayerOrbitInfo orbits;
    private GameWorld orbitsFor;
    private boolean shown = true;

    /** Each layer's offset within one cell, pixels, and how many whole cells it has slid. */
    private final float[] offsetX = new float[LAYERS];
    private final float[] offsetY = new float[LAYERS];
    private final int[] cellsX = new int[LAYERS];
    private final int[] cellsY = new int[LAYERS];
    private double lastTime = Double.NaN;
    /** The reference orbit's velocity relative to the ship this frame, m/s, when {@link #haveDrift}. */
    private final Vector2 drift = new Vector2();
    private boolean haveDrift;
    private final Color color = new Color();
    private final Color tail = new Color();

    /** Snow for the player ({@code player}) of whatever world {@code world} gives (a load replaces it). */
    public SpaceSnow(Supplier<GameWorld> world, Supplier<Entity> player) {
        this.world = world;
        this.player = player;
    }

    public boolean isShown() {
        return shown;
    }

    public SpaceSnow setShown(boolean shown) {
        this.shown = shown;
        return this;
    }

    /**
     * The velocity, m/s, of the reference frame relative to a ship at {@code state} around a body
     * of gravitational parameter {@code mu}: a circular orbit through the ship's position, turning
     * the way the ship does ({@code +} counterclockwise when it has no angular momentum), less the
     * ship's own velocity. Zero for a ship in a circular orbit.
     */
    public static Vector2 drift(StateVector state, double mu, Vector2 out) {
        double r = Math.hypot(state.x, state.y);
        if (!(r > 0.0) || !(mu > 0.0)) return out.set((float) -state.vx, (float) -state.vy);
        double turn = state.x * state.vy - state.y * state.vx < 0.0 ? -1.0 : 1.0;
        double speed = Math.sqrt(mu / r) * turn;
        // Prograde for counterclockwise is the radial direction turned a quarter left
        double vx = -state.y / r * speed;
        double vy = state.x / r * speed;
        return out.set((float) (vx - state.vx), (float) (vy - state.vy));
    }

    /**
     * How fast a mote of layer {@code k} (0 the farthest) slides across the screen, pixels a
     * second, for a drift of {@code speed} m/s. The same at every zoom.
     */
    static float pixelsPerSecond(float speed, int k) {
        return speed / (REFERENCE_MPP * DEPTH[k]);
    }

    /** The depth of layer {@code k}, 0 the farthest. */
    static float depth(int k) {
        return DEPTH[k];
    }

    /** Of a screen's motes, the share in layer {@code k}, 0 the farthest. */
    static float share(int k) {
        float total = 0f;
        for (float c : CELL) total += 1f / (c * c);
        return 1f / (CELL[k] * CELL[k]) / total;
    }

    /** The drift the last {@link #draw} used, m/s, or null when there was no body to orbit. */
    public Vector2 lastDrift() {
        return haveDrift ? drift : null;
    }

    /**
     * Moves the snow on by the sim time since the last frame, then draws it in the view of
     * {@code camera} (world meters, not turned), {@code metersPerPixel} to a screen pixel.
     */
    public void draw(ShapeRenderer shapes, OrthographicCamera camera, float metersPerPixel) {
        if (!shown || !(metersPerPixel > 0f)) {
            lastTime = Double.NaN;
            return;
        }
        GameWorld w = world.get();
        if (w == null) return;
        if (orbits == null || orbitsFor != w) {
            orbits = new PlayerOrbitInfo(w.universe, w.physics, player);
            orbitsFor = w;
            lastTime = Double.NaN;
        }
        double now = w.universe.now();
        double step = Double.isNaN(lastTime) ? 0.0 : now - lastTime;
        if (step < 0.0 || step > MAX_STEP) step = 0.0;
        lastTime = now;

        KeplerOrbit conic = orbits.currentConic();
        CelestialBodyComponent body = orbits.body();
        haveDrift = conic != null && body != null;
        if (!haveDrift) return;
        drift(orbits.state(), body.mu, drift);
        float speed = drift.len();

        float halfW = camera.viewportWidth * camera.zoom / metersPerPixel / 2f;
        float halfH = camera.viewportHeight * camera.zoom / metersPerPixel / 2f;
        GlPass.alphaBlend();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int k = 0; k < LAYERS; k++) {
            float cell = CELL[k];
            // Pixels this layer slides this frame, and per second for the streak: the same at any zoom
            float scale = REFERENCE_MPP * DEPTH[k];
            float moveX = (float) (drift.x * step / scale);
            float moveY = (float) (drift.y * step / scale);
            slide(k, moveX, moveY);
            float frameMove = (float) Math.hypot(moveX, moveY) / cell;
            float fade = 1f - MathUtils.clamp((frameMove - FADE_START) / (FADE_END - FADE_START), 0f, 1f);
            if (fade <= 0f) continue;
            float pxPerSecond = pixelsPerSecond(speed, k);
            float streak = Math.min(pxPerSecond * STREAK_SECONDS, MAX_STREAK * cell);
            float tailX, tailY;
            if (speed > 0f) {
                float len = Math.max(streak, MIN_MOTE[k]);
                tailX = -drift.x / speed * len;
                tailY = -drift.y / speed * len;
            } else {
                tailX = -MIN_MOTE[k];
                tailY = 0f;
            }
            int i0 = MathUtils.floor((-halfW - offsetX[k]) / cell) - 1;
            int i1 = MathUtils.ceil((halfW - offsetX[k]) / cell) + 1;
            int j0 = MathUtils.floor((-halfH - offsetY[k]) / cell) - 1;
            int j1 = MathUtils.ceil((halfH - offsetY[k]) / cell) + 1;
            for (int i = i0; i <= i1; i++) {
                for (int j = j0; j <= j1; j++) {
                    int h = hash(i - cellsX[k], j - cellsY[k], k);
                    if ((h & 0xffff) / 65536f >= FILL) continue;
                    float x = (i + ((h >>> 16) & 0xff) / 256f) * cell + offsetX[k];
                    float y = (j + ((h >>> 24) & 0xff) / 256f) * cell + offsetY[k];
                    float brightness = 0.5f + 0.5f * (((h >>> 8) & 0xff) / 256f);
                    color.set(MOTE).a = ALPHA[k] * brightness * fade;
                    tail.set(MOTE).a = color.a * 0.15f;
                    float wx = camera.position.x + x * metersPerPixel;
                    float wy = camera.position.y + y * metersPerPixel;
                    shapes.line(wx, wy, wx + tailX * metersPerPixel, wy + tailY * metersPerPixel,
                            color, tail);
                }
            }
        }
        shapes.end();
    }

    /** Slides layer {@code k} by ({@code dx}, {@code dy}) pixels, keeping its offset within one cell. */
    private void slide(int k, float dx, float dy) {
        float cell = CELL[k];
        float x = offsetX[k] + dx;
        float y = offsetY[k] + dy;
        int wholeX = MathUtils.floor(x / cell);
        int wholeY = MathUtils.floor(y / cell);
        offsetX[k] = x - wholeX * cell;
        offsetY[k] = y - wholeY * cell;
        cellsX[k] += wholeX;
        cellsY[k] += wholeY;
    }

    /** A well-mixed hash of a cell and its layer, so the motes look scattered. */
    private static int hash(int i, int j, int k) {
        int h = i * 0x27d4eb2d ^ j * 0x165667b1 ^ (k + 1) * 0x61c88647;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h;
    }
}
