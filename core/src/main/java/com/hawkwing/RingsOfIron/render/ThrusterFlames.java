package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.art.ExteriorArt;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;

/**
 * The flames of craft in flight: each main engine's plume and each RCS nozzle's jet, and the light
 * they give off, from one figure per nozzle so the flame and its light always agree.
 *
 * <p>A nozzle's figure, its level, follows how hard it fires ({@link ExteriorArt#engineNozzles},
 * {@link RcsNozzles#nozzles}) but not at once: a main drive spools up over a few tenths of a
 * second and dies down a little faster, an RCS jet opens and shuts in a few hundredths, and a jet
 * opening from cold kicks a little past full before it settles, as a valve popping does. On top it
 * flickers, the same flicker for the flame's length and its light. Nothing moves while the game
 * is paused (no time passes).
 *
 * <p>Each frame: {@link #collect} every craft in view, {@link #light} the light maps from them,
 * then {@link #draw} the flames after the light map, additively, so it does not darken them and
 * they glow over what is behind them.
 */
public final class ThrusterFlames {
    /** Seconds a main drive takes to go from cold to full, and from full to out. */
    static final float DRIVE_RISE = 0.35f, DRIVE_FALL = 0.2f;
    /** Seconds an RCS jet takes to open fully, and to shut. */
    static final float RCS_RISE = 0.04f, RCS_FALL = 0.07f;
    /** How far past full a jet opening from cold kicks, and over how many seconds the kick fades. */
    static final float RCS_KICK = 0.35f, RCS_KICK_TIME = 0.09f;
    /** How much a flame's length wavers either way: a drive's steady burn, an RCS jet's sputter. */
    static final float DRIVE_FLICKER = 0.06f, RCS_FLICKER = 0.12f;
    /** Below this a flame is out: not drawn and gives no light. */
    static final float OUT = 0.01f;
    /** How far a main drive's light reaches at full burn, times its nozzle's width, and at least, meters. */
    static final float DRIVE_REACH = 8f, DRIVE_MIN_REACH = 6f;
    /** How far an RCS jet's light reaches at full, meters. */
    static final float RCS_REACH = 4f;
    /** How long a firing RCS nozzle's line is on a craft drawn as its outline, meters at full. */
    static final float JET_LINE = 1.2f;

    /** One nozzle's flame this frame. */
    static final class Flame {
        Entity craft;
        /** The engine's size for a main drive's plume; null for an RCS jet. */
        String size;
        /** The engine's component, whose plume it is; null for an RCS jet or a hull's own engine. */
        String component;
        float x, y, exhaust, width, level, flicker;

        boolean isRcs() {
            return size == null;
        }
    }

    private final ExteriorArt art;
    private final RcsNozzles nozzles = new RcsNozzles();
    /** Each craft's nozzles from frame to frame, in the order they are reported: level, kick left, each. */
    private final ObjectMap<Entity, FloatArray> state = new ObjectMap<>();
    private final ObjectSet<Entity> seen = new ObjectSet<>();
    private final Array<Flame> flames = new Array<>();
    private final Array<Flame> pool = new Array<>();
    private final Color tint = new Color();
    private float clock;
    private float delta;

    public ThrusterFlames(ExteriorArt art) {
        this.art = art;
    }

    /** Starts a frame {@code delta} seconds after the last (0 while paused): drops last frame's flames. */
    public void begin(float delta) {
        this.delta = Math.max(0f, delta);
        clock += this.delta;
        pool.addAll(flames);
        flames.clear();
        seen.clear();
    }

    /** Every main engine and RCS nozzle of {@code craft}, flying as {@code body}. */
    public void collect(Entity craft, Body body) {
        if (craft == null || body == null) return;
        seen.add(craft);
        FloatArray s = state.get(craft);
        if (s == null) state.put(craft, s = new FloatArray());
        FloatArray nozzleState = s;
        int[] slot = {0};
        float seed = (System.identityHashCode(craft) & 0xffff) * 0.0137f;
        art.engineNozzles(craft, body, (size, component, x, y, exhaust, width, throttle) ->
                add(craft, nozzleState, slot[0]++, seed, size, component, x, y, exhaust, width, throttle));
        nozzles.nozzles(craft, body, (x, y, degrees, throttle) ->
                add(craft, nozzleState, slot[0]++, seed, null, null, x, y, degrees * MathUtils.degreesToRadians, 0f, throttle));
        s.size = 2 * slot[0];
    }

    /** Forgets the craft not collected since {@link #begin}: gone, or out of view. */
    public void end() {
        for (ObjectMap.Entries<Entity, FloatArray> it = state.entries(); it.hasNext; ) {
            if (!seen.contains(it.next().key)) it.remove();
        }
    }

    private void add(Entity craft, FloatArray s, int slot, float seed, String size, String component,
                     float x, float y, float exhaust, float width, float throttle) {
        int i = 2 * slot;
        while (s.size < i + 2) s.add(0f);
        boolean rcs = size == null;
        float was = s.get(i), kick = s.get(i + 1);
        float level = rcs ? step(was, throttle, RCS_RISE, RCS_FALL, delta) : step(was, throttle, DRIVE_RISE, DRIVE_FALL, delta);
        if (rcs) {
            // A jet opening from cold pops past full, then settles
            if (was < OUT && throttle > 0f) kick = RCS_KICK_TIME;
            else kick = Math.max(0f, kick - delta);
        }
        s.set(i, level);
        s.set(i + 1, kick);
        if (level < OUT) return;
        Flame f = pool.size > 0 ? pool.pop() : new Flame();
        f.craft = craft;
        f.size = size;
        f.component = component;
        f.x = x;
        f.y = y;
        f.exhaust = exhaust;
        f.width = width;
        f.level = rcs ? level * (1f + RCS_KICK * kick / RCS_KICK_TIME) : level;
        f.flicker = flicker(clock, seed + slot * 1.618f, rcs ? RCS_FLICKER : DRIVE_FLICKER);
        flames.add(f);
    }

    /**
     * {@code level} moved toward {@code target} over {@code dt} seconds: up at full scale in
     * {@code rise} seconds, down in {@code fall}.
     */
    static float step(float level, float target, float rise, float fall, float dt) {
        target = MathUtils.clamp(target, 0f, 1f);
        if (target > level) return Math.min(target, level + dt / rise);
        return Math.max(target, level - dt / fall);
    }

    /** A wavering about 1, by {@code amount} at most either way, at time {@code t}, different for each {@code seed}. */
    static float flicker(float t, float seed, float amount) {
        float n = 0.5f * MathUtils.sin(t * 31f + seed)
                + 0.3f * MathUtils.sin(t * 53.7f + seed * 1.7f)
                + 0.2f * MathUtils.sin(t * 97.3f + seed * 2.3f);
        return 1f + amount * n;
    }

    /**
     * The light of every flame: into {@code outside}'s light map (may be null) and, for the RCS
     * jets, the glow pass of {@code glow} (may be null), each as strong as its flame is long.
     */
    public void light(ExteriorLighting outside, SceneLighting glow) {
        for (Flame f : flames) {
            float level = f.level * f.flicker;
            if (f.isRcs()) {
                if (outside != null) outside.nozzle(f.x, f.y, f.exhaust, level, ExteriorLighting.THRUSTER, RCS_REACH);
                if (glow != null) glow.addThruster(f.x, f.y, f.exhaust * MathUtils.radiansToDegrees, Math.min(1f, level), false);
            } else if (outside != null) {
                outside.nozzle(f.x, f.y, f.exhaust, level, ExteriorLighting.DRIVE, Math.max(DRIVE_MIN_REACH, DRIVE_REACH * f.width));
            }
        }
    }

    /**
     * Every flame of a craft drawn with its hull art, in {@code batch} (not begun, its projection
     * set), additively: a plume or jet stretched with its level, a little narrower and fainter
     * when it barely fires.
     */
    public void draw(Batch batch, ObjectSet<Entity> withArt) {
        if (flames.size == 0) return;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (Flame f : flames) {
            if (!withArt.contains(f.craft)) continue;
            AtlasRegion r = art.flameArt(f.size, f.component);
            if (r == null) continue;
            float level = f.level * f.flicker;
            float across = 0.7f + 0.3f * Math.min(1f, f.level);
            tint.set(1f, 1f, 1f, Math.min(1f, 0.55f + 0.45f * level));
            batch.setColor(tint);
            art.flame(batch, r, f.x, f.y, f.exhaust, level, across);
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** The RCS jets of craft drawn as their outline, as short lines, in {@code shapes} already begun with lines. */
    public void drawLines(ShapeRenderer shapes, ObjectSet<Entity> withArt) {
        shapes.setColor(Color.ORANGE);
        for (Flame f : flames) {
            if (!f.isRcs() || withArt.contains(f.craft)) continue;
            float len = JET_LINE * f.level * f.flicker;
            shapes.line(f.x, f.y, f.x + MathUtils.cos(f.exhaust) * len, f.y + MathUtils.sin(f.exhaust) * len);
        }
    }

    /** The flames this frame. For tests. */
    Array<Flame> flames() {
        return flames;
    }
}
