package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.damage.Shields;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * The shield bubble round each craft whose shields are up: an ellipse fitted round its hull, a
 * faint rim at rest, that ripples where a hit lands on it, brighter the more the shield took, and
 * bursts outward when the shields drop. The ripple rides the bubble as the craft turns, and each
 * one flashes the hull with shield light.
 *
 * <p>Each frame: {@link #begin}, {@link #collect} every craft in view, {@link #end}, then
 * {@link #draw} after the light map, additively, as the flames are. {@link #hit} takes each
 * {@link HullHitEvent} a shield softened.
 */
public final class ShieldBubbles implements Disposable {
    /** Shield cyan, the emitter's color in the art. */
    public static final Color SHIELD = new Color(0x4fd1ffff);
    static final float ROOT_2 = (float) Math.sqrt(2);
    /** How far the bubble stands off the hull's corners, meters. */
    static final float MARGIN = 1.5f;
    /** Seconds the bubble takes to come up from nothing, and to go out. */
    static final float RISE = 0.6f, FALL = 0.08f;
    /** How bright the rim is at rest, with the shields up. */
    static final float REST = 0.12f;
    /** Seconds a ripple lasts, and a collapse's burst. */
    static final float RIPPLE_LIFE = 0.45f, COLLAPSE_LIFE = 0.5f;
    /** A ripple's length along the rim at its start and its end, meters, at full strength. */
    static final float RIPPLE_START = 1f, RIPPLE_END = 12f;
    /** Meters between the soft beads a ripple is drawn with along the rim, and most beads each way. */
    static final float BEAD_SPACING = 0.4f;
    static final int MAX_BEADS = 40;
    /** How far a collapse's burst grows past the bubble, as a share of its size. */
    static final float COLLAPSE_GROWTH = 0.3f;
    /** Most ripples a bubble keeps at once; the oldest goes. */
    static final int MAX_RIPPLES = 12;

    /** One craft's bubble, in its body frame. */
    static final class Bubble {
        /** The center and half-axes, body-local meters. */
        float cx, cy, a, b;
        /** How strongly it shows, 0 to 1, and seconds since it collapsed (past its life: none). */
        float level, collapse = COLLAPSE_LIFE;
        ShipGridComponent fittedTo;
        final Array<float[]> ripples = new Array<>();
    }

    private final ObjectMap<Entity, Bubble> bubbles = new ObjectMap<>();
    private final ObjectSet<Entity> seen = new ObjectSet<>();
    private final ObjectMap<Entity, Body> drawn = new ObjectMap<>();
    private final Vector2 local = new Vector2();
    private TextureRegion rim, flare;
    private float delta;

    /** Starts a frame {@code delta} seconds after the last (0 while paused). */
    public void begin(float delta) {
        this.delta = Math.max(0f, delta);
        seen.clear();
        drawn.clear();
    }

    /** {@code craft}, flying as {@code body}: its bubble follows whether its shields are up. */
    public void collect(Entity craft, Body body) {
        ShipGridComponent grid = craft == null ? null : craft.getComponent(ShipGridComponent.class);
        if (grid == null || body == null) return;
        seen.add(craft);
        boolean up = Shields.up(craft);
        Bubble bubble = bubbles.get(craft);
        if (bubble == null) {
            if (!up) return;
            bubbles.put(craft, bubble = new Bubble());
        }
        if (bubble.fittedTo != grid) fit(grid, bubble);
        step(bubble, up, delta);
        drawn.put(craft, body);
    }

    /** Forgets the craft not collected since {@link #begin}, and bubbles with nothing left to show. */
    public void end() {
        for (ObjectMap.Entries<Entity, Bubble> it = bubbles.entries(); it.hasNext; ) {
            ObjectMap.Entry<Entity, Bubble> e = it.next();
            Bubble b = e.value;
            if (!seen.contains(e.key) || (b.level <= 0f && b.collapse >= COLLAPSE_LIFE && b.ripples.size == 0)) {
                it.remove();
                drawn.remove(e.key);
            }
        }
    }

    /**
     * A hit {@code body}'s shields took part of: a ripple on the bubble facing where it struck, and
     * a flash of shield light there into {@code light} (may be null).
     */
    public void hit(HullHitEvent hit, Body body, ExteriorLighting light) {
        if (hit == null || hit.ship == null || body == null || !(hit.shielded > 0f)) return;
        ShipGridComponent grid = hit.ship.getComponent(ShipGridComponent.class);
        if (grid == null) return;
        Bubble bubble = bubbles.get(hit.ship);
        if (bubble == null) bubbles.put(hit.ship, bubble = new Bubble());
        if (bubble.fittedTo != grid) fit(grid, bubble);
        // Shields are up if they took part of it, whether or not the craft was in view yet
        bubble.level = Math.max(bubble.level, 0.5f);
        CombatPhysics.toBodyLocal(body, hit.worldPoint.x, hit.worldPoint.y, local);
        float phi = MathUtils.atan2((local.y - bubble.cy) / bubble.b, (local.x - bubble.cx) / bubble.a);
        float strength = strength(hit.shielded);
        if (bubble.ripples.size >= MAX_RIPPLES) bubble.ripples.removeIndex(0);
        bubble.ripples.add(new float[]{phi, 0f, strength});
        if (light != null) {
            float lx = bubble.cx + bubble.a * MathUtils.cos(phi), ly = bubble.cy + bubble.b * MathUtils.sin(phi);
            Vector2 at = toWorld(body, lx, ly, local);
            light.flash(at.x, at.y, SHIELD, 6f + 14f * strength, 0.3f + 0.6f * strength, 0.25f + 0.25f * strength);
        }
    }

    /**
     * How bright a ripple is for {@code joules} the shield took, 0.2 to 1: by the order of the hit,
     * so an autocannon round shows (about 0.35) and a railgun slug flares fully.
     */
    static float strength(float joules) {
        float order = (float) Math.log10(Math.max(1f, joules));
        return MathUtils.clamp((order - 4f) / 4f, 0.2f, 1f);
    }

    /**
     * Fits {@code bubble} round {@code grid}'s tiles that are not void: the ellipse through the
     * corners of their box (half-axes {@code sqrt 2} times the box's), stood off by {@link #MARGIN}.
     */
    static void fit(ShipGridComponent grid, Bubble bubble) {
        int x0 = grid.width, y0 = grid.height, x1 = -1, y1 = -1;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
            }
        }
        if (x1 < 0) {
            x0 = y0 = 0;
            x1 = grid.width - 1;
            y1 = grid.height - 1;
        }
        float s = grid.tileSize;
        float halfW = 0.5f * (x1 - x0 + 1) * s, halfH = 0.5f * (y1 - y0 + 1) * s;
        bubble.cx = grid.gridOriginLocal.x + x0 * s + halfW;
        bubble.cy = grid.gridOriginLocal.y + y0 * s + halfH;
        bubble.a = halfW * ROOT_2 + MARGIN;
        bubble.b = halfH * ROOT_2 + MARGIN;
        bubble.fittedTo = grid;
    }

    /** {@code bubble} {@code dt} seconds on, with its shields {@code up} or not: a drop starts the burst. */
    static void step(Bubble bubble, boolean up, float dt) {
        boolean wasUp = bubble.level > 0f;
        bubble.level = ThrusterFlames.step(bubble.level, up ? 1f : 0f, RISE, FALL, dt);
        if (wasUp && !up && bubble.collapse >= COLLAPSE_LIFE) bubble.collapse = 0f;
        else bubble.collapse = Math.min(COLLAPSE_LIFE, bubble.collapse + dt);
        for (int i = bubble.ripples.size - 1; i >= 0; i--) {
            float[] r = bubble.ripples.get(i);
            r[1] += dt;
            if (r[1] >= RIPPLE_LIFE) bubble.ripples.removeIndex(i);
        }
    }

    /**
     * Every bubble collected this frame, in {@code batch} (not begun, its projection set),
     * additively: its rim, its ripples and any collapse burst.
     */
    public void draw(Batch batch) {
        if (drawn.size == 0) return;
        if (rim == null) {
            rim = new TextureRegion(rimTexture(128));
            flare = new TextureRegion(flareTexture(64));
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            Bubble bubble = bubbles.get(e.key);
            if (bubble == null) continue;
            Body body = e.value;
            float deg = body.getAngle() * MathUtils.radiansToDegrees;
            Vector2 c = toWorld(body, bubble.cx, bubble.cy, local);
            float cx = c.x, cy = c.y;
            // The whole bubble brightens a little with each hit it is taking
            float glow = 0f;
            for (float[] r : bubble.ripples) glow += r[2] * (1f - r[1] / RIPPLE_LIFE);
            float alpha = bubble.level * (REST + 0.5f * Math.min(1f, glow));
            if (alpha > 0.005f) {
                batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, alpha);
                ellipse(batch, rim, cx, cy, bubble.a, bubble.b, 1f, deg);
            }
            if (bubble.collapse < COLLAPSE_LIFE) {
                float t = bubble.collapse / COLLAPSE_LIFE;
                batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, 0.6f * (1f - t) * (1f - t));
                ellipse(batch, rim, cx, cy, bubble.a, bubble.b, 1f + COLLAPSE_GROWTH * t, deg);
            }
            for (float[] r : bubble.ripples) ripple(batch, body, bubble, r[0], r[1] / RIPPLE_LIFE, r[2], deg);
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * A ripple {@code t} of the way through its life, struck at {@code phi} on the bubble: two fronts
     * running apart along the rim from where it struck, over a glow that fades there, drawn as soft
     * beads along the ellipse so it bends with it; and a white-hot core in its first third.
     */
    private void ripple(Batch batch, Body body, Bubble bubble, float phi, float t, float strength, float deg) {
        float fade = 1f - t;
        float half = (RIPPLE_START + (RIPPLE_END - RIPPLE_START) * t) * (0.5f + 0.5f * strength);
        float across = 0.5f + 1.5f * strength * fade;
        int beads = Math.min(MAX_BEADS, MathUtils.ceil(half / BEAD_SPACING));
        float step = half / Math.max(1, beads), bead = Math.max(4f * step, 1.5f * across);
        for (int i = -beads; i <= beads; i++) {
            float d = i * step;
            float front = (Math.abs(d) - half) / (0.3f * half + 0.5f), middle = d / (0.4f * half + 0.3f);
            float a = 0.35f * (0.7f + 0.3f * strength) * fade
                    * ((float) Math.exp(-front * front) + 0.5f * (float) Math.exp(-middle * middle));
            if (a < 0.01f) continue;
            float at = phi + d / speed(bubble, phi);
            float cos = MathUtils.cos(at), sin = MathUtils.sin(at);
            Vector2 p = toWorld(body, bubble.cx + bubble.a * cos, bubble.cy + bubble.b * sin, local);
            // Along the rim: the ellipse's tangent there, turned with the body
            float along = MathUtils.atan2(bubble.b * cos, -bubble.a * sin) * MathUtils.radiansToDegrees + deg;
            batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, Math.min(1f, a));
            batch.draw(flare, p.x - 0.5f * bead, p.y - 0.5f * across, 0.5f * bead, 0.5f * across, bead, across, 1f, 1f, along);
        }
        if (t < 0.33f) {
            float core = 1f - t / 0.33f, size = 0.8f + 2.2f * strength;
            Vector2 p = toWorld(body, bubble.cx + bubble.a * MathUtils.cos(phi), bubble.cy + bubble.b * MathUtils.sin(phi), local);
            batch.setColor(1f, 1f, 1f, 0.8f * strength * core);
            batch.draw(flare, p.x - 0.5f * size, p.y - 0.5f * size, 0.5f * size, 0.5f * size, size, size, 1f, 1f, 0f);
        }
    }

    /** Meters along {@code bubble}'s rim per radian of its angle, at {@code phi}. */
    static float speed(Bubble bubble, float phi) {
        float sin = MathUtils.sin(phi), cos = MathUtils.cos(phi);
        return Math.max(0.1f, (float) Math.sqrt(bubble.a * bubble.a * sin * sin + bubble.b * bubble.b * cos * cos));
    }

    private static void ellipse(Batch batch, TextureRegion r, float cx, float cy, float a, float b, float scale, float deg) {
        batch.draw(r, cx - a, cy - b, a, b, 2f * a, 2f * b, scale, scale, deg);
    }

    private static Vector2 toWorld(Body body, float lx, float ly, Vector2 out) {
        return CombatPhysics.toWorld(body, lx, ly, out);
    }

    /** A soft ring at the texture's edge with a faint fill inside, white, for tinting. */
    static Texture rimTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, rimAlpha((float) Math.sqrt(dx * dx + dy * dy))));
            }
        }
        return texture(pm);
    }

    /** The rim's alpha {@code r} out from the center (1 at the edge): a bright band just inside the edge over a faint fill. */
    static float rimAlpha(float r) {
        if (r >= 1f) return 0f;
        float band = (r - 0.93f) / 0.06f;
        return Math.min(1f, (float) Math.exp(-band * band) + 0.12f * r * r * r);
    }

    /** A soft round blob, white, for tinting and stretching. */
    static Texture flareTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                float f = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy));
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, f * f));
            }
        }
        return texture(pm);
    }

    private static Texture texture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    /** The bubble of {@code craft}, or null. For tests. */
    Bubble bubbleOf(Entity craft) {
        return bubbles.get(craft);
    }

    @Override
    public void dispose() {
        if (rim != null) rim.getTexture().dispose();
        if (flare != null) flare.getTexture().dispose();
        rim = flare = null;
    }
}
