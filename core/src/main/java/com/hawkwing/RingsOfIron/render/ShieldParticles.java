package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;
import com.ringsofiron.physicsexterior.PhysicsManager;

import static com.hawkwing.RingsOfIron.render.ShieldSwarm.*;

/**
 * A ship's shield as the cloud it is (documentation/combat-sys/Design.md, 5.11): dark metal flecks
 * drifting in lazy orbits round the {@link ShieldPerimeter}, sparse at rest, that stream into the
 * regions the emitters gather and glow there as they pack. A hit flashes, blasts the flecks it
 * plowed through along the shot, throws the lost ones clear to fade, and lets the rest curve back
 * in. Free flecks lie as the cloud's resting layer does, thick where emitters overlap or the crew
 * trim it and thin where one has died. A faint bubble traces the curve, dimmer where the layer is
 * thin and brighter where regions pack it. Mass no working emitter holds goes adrift, from where
 * its emitters stopped first: its flecks keep the velocity they had and the ship flies on without them,
 * until they are lost or pulled back. Lowered shields stream into the emitters and come back out
 * of them when raised. Each emitter's projector glows on the hull, toward orange as it heats.
 *
 * <p>Each frame: {@link #begin}, {@link #collect} every craft in view, {@link #end}; then
 * {@link #drawUnder} before the hulls and decks, which cover it where they lie, and
 * {@link #light} into the light map, so the shield's own light keeps what is under it bright;
 * {@link #drawFlecks} (those in front) with the hull art, so the light map lights them; and
 * {@link #drawGlow} after the light map, additively, over the hulls. Nothing is masked: what is
 * below the hull's plane is drawn first and what is above it last. Each craft's flecks are a
 * {@link ShieldSwarm}, which follows the simulation's {@link ShieldCloudComponent}; the textures come
 * from {@link ShieldTextures}.
 */
public final class ShieldParticles implements Disposable {
    /** Shield cyan, the emitter's color in the art. */
    public static final Color SHIELD = new Color(0x4fd1ffff);
    /** Bare flecks: dark gunmetal, lit by the light map. */
    static final Color FLECK = new Color(0.62f, 0.65f, 0.7f, 1f);
    /** Kilograms of the cloud each fleck stands for, and the fewest and most a ship draws. */
    static final float KG_PER_FLECK = 0.4f;
    static final int MIN_FLECKS = 400, MAX_FLECKS = 5_000;
    /** A fleck's size, meters, and the fewest pixels it is drawn across. */
    static final float FLECK_SIZE = 0.05f, MIN_PIXELS = 1.2f;
    /**
     * The flecks show fully zoomed in to this many meters a pixel, and are gone zoomed out to this
     * many: far out they would only be noise; the bubble and the glow of what is struck stay.
     */
    static final float FADE_NEAR = 0.2f, FADE_FAR = 0.4f;
    /** The bubble's glow at rest, and how much a packed region adds to it. */
    static final float BUBBLE_REST = 0.07f, BUBBLE_PACKED = 0.22f;
    /**
     * How far round the curve the bubble's outline is smoothed, samples (a Gaussian's sigma), so it
     * doesn't trace every step of the hull; and the most it may sit inside the shield's own curve, meters.
     */
    static final float BUBBLE_SMOOTH = 3f, BUBBLE_INSET = 0.6f;
    /**
     * The infill across the bubble, brightest at its edge: the bottom one under the hull (seen from
     * the deck too), the top one over it (outside only); their alpha at the edge, and how sharply
     * they rise toward it (alpha goes as the fraction of the way out to this power).
     */
    static final float INFILL_BOTTOM = 0.11f, INFILL_TOP = 0.05f, INFILL_EDGE = 3f;
    /**
     * The shield's light in the light map: this many lights round the bubble, each this far out
     * from its center (a fraction of the way to the curve) and reaching this fraction of the
     * curve's mean radius, at this strength with the whole cloud held. Enough to keep the infill
     * under the hulls, which the light map would dim to starlight, about as bright as it is drawn.
     */
    static final int SHIELD_LIGHTS = 8;
    static final float SHIELD_LIGHT_OUT = 0.8f, SHIELD_LIGHT_REACH = 0.9f, SHIELD_LIGHT = 0.55f;
    /** Rings the infill is drawn in, center to edge. */
    static final int INFILL_RINGS = 10;
    /** Areal density at which a region lights the bubble fully, kg/m^2. */
    static final float BUBBLE_FULL = 25f;

    private final ObjectMap<Entity, ShieldSwarm> swarms = new ObjectMap<>();
    private final ObjectMap<Entity, Body> drawn = new ObjectMap<>();
    private final ObjectSet<Entity> seen = new ObjectSet<>();
    /** The craft collected this frame that are drawn as their decks. */
    private final ObjectSet<Entity> decked = new ObjectSet<>();
    /** One quad's vertices for the batch: x, y, color, u, v at each corner. */
    private final float[] quad = new float[20];
    private final Vector2 at = new Vector2();
    private final ShieldSwarm.Frame frame = new ShieldSwarm.Frame();
    private TextureRegion dot, glow, band, white;
    private float delta;

    /** Starts a frame {@code delta} seconds after the last (0 while paused). */
    public void begin(float delta) {
        this.delta = Math.max(0f, delta);
        seen.clear();
        drawn.clear();
        decked.clear();
    }

    /**
     * {@code craft}, flying as {@code body}: its flecks follow its cloud, and each new hit flashes
     * shield light into {@code light} (may be null).
     */
    public void collect(Entity craft, Body body, ExteriorLighting light) {
        collect(craft, body, light, false);
    }

    /**
     * As {@link #collect(Entity, Body, ExteriorLighting)}; {@code onDeck}: drawn as its deck plan
     * (the deck view), so its top infill is left out.
     */
    public void collect(Entity craft, Body body, ExteriorLighting light, boolean onDeck) {
        ShieldCloudComponent cloud = craft == null ? null : craft.getComponent(ShieldCloudComponent.class);
        if (cloud == null || cloud.perimeter == null || body == null || cloud.capacity <= 0f) return;
        seen.add(craft);
        ShieldSwarm swarm = swarms.get(craft);
        int n = MathUtils.clamp(Math.round(cloud.capacity / KG_PER_FLECK), MIN_FLECKS, MAX_FLECKS);
        if (swarm == null || swarm.n != n) swarms.put(craft, swarm = new ShieldSwarm(n, cloud.capacity / n));
        Vector2 v = body.getLinearVelocityFromLocalPoint(at.set(0f, 0f));
        frame.vx = v.x * PhysicsManager.METERS_PER_UNIT;
        frame.vy = v.y * PhysicsManager.METERS_PER_UNIT;
        frame.angle = body.getAngle();
        frame.spin = body.getAngularVelocity();
        swarm.update(cloud, delta, frame);
        if (light != null) {
            for (float[] f : swarm.fresh) {
                Vector2 w = CombatPhysics.toWorld(body, f[0], f[1], at);
                light.flash(w.x, w.y, SHIELD, 6f + 14f * f[2], 0.3f + 0.6f * f[2], 0.25f + 0.25f * f[2]);
            }
        }
        drawn.put(craft, body);
        if (onDeck) decked.add(craft);
    }

    /**
     * The shield's own light into {@code light}: {@link #SHIELD_LIGHTS} soft cyan lights round each
     * collected craft's bubble, as bright as the share of its cloud held. The light map then lights
     * what {@link #drawUnder} drew in space round the hull, and the hull's edge with it.
     */
    public void light(ExteriorLighting light) {
        if (light == null) return;
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            ShieldSwarm sw = swarms.get(e.key);
            ShieldCloudComponent cloud = e.key.getComponent(ShieldCloudComponent.class);
            if (sw == null || cloud == null) continue;
            float held = cloud.capacity > 0f ? Math.min(1f, cloud.cloud() / cloud.capacity) : 0f;
            if (held <= 0.01f) continue;
            ShieldPerimeter per = cloud.perimeter;
            curveOf(sw, per);
            int n = ShieldPerimeter.SAMPLES;
            float mean = 0f;
            for (int k = 0; k < n; k++) mean += (float) Math.hypot(sw.cx[k] - per.cx, sw.cy[k] - per.cy);
            mean /= n;
            for (int i = 0; i < SHIELD_LIGHTS; i++) {
                int k = i * n / SHIELD_LIGHTS;
                Vector2 w = CombatPhysics.toWorld(e.value, per.cx + (sw.cx[k] - per.cx) * SHIELD_LIGHT_OUT,
                        per.cy + (sw.cy[k] - per.cy) * SHIELD_LIGHT_OUT, at);
                light.add(w.x, w.y, SHIELD, SHIELD_LIGHT_REACH * mean, SHIELD_LIGHT * held);
            }
        }
    }

    /** Forgets the craft not collected since {@link #begin}. */
    public void end() {
        for (ObjectMap.Entries<Entity, ShieldSwarm> it = swarms.entries(); it.hasNext; ) {
            if (!seen.contains(it.next().key)) it.remove();
        }
    }

    /**
     * How much of the flecks shows at {@code metersPerPixel}: all of them zoomed in to
     * {@link #FADE_NEAR}, none zoomed out past {@link #FADE_FAR}.
     */
    static float zoomFade(float metersPerPixel) {
        return MathUtils.clamp((FADE_FAR - metersPerPixel) / (FADE_FAR - FADE_NEAR), 0f, 1f);
    }

    /**
     * The flecks of every craft collected this frame into {@code batch} (begun, normal blending):
     * those behind the hull's plane with {@code behind}, else those in front. They fade out as the
     * view zooms out ({@link #zoomFade}), and stay {@link #MIN_PIXELS} across while they show.
     */
    public void drawFlecks(Batch batch, boolean behind, float metersPerPixel) {
        if (drawn.size == 0) return;
        float fade = zoomFade(metersPerPixel);
        if (fade <= 0f) return;
        textures();
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            ShieldSwarm sw = swarms.get(e.key);
            if (sw == null) continue;
            flecks(batch, e.value, sw, behind, metersPerPixel, fade);
        }
        batch.setColor(Color.WHITE);
    }

    private void flecks(Batch batch, Body body, ShieldSwarm sw, boolean behind, float metersPerPixel, float fade) {
        float min = MIN_PIXELS * metersPerPixel;
        for (int i = 0; i < sw.n; i++) {
            byte st = sw.state[i];
            if (st == DEAD || st == STOWED || (sw.z[i] < 0f) != behind) continue;
            float a = fade * (st == LOST ? Math.max(0f, sw.timer[i] / LOST_FADE) : 1f);
            // Turned toward the light above the plane, shadowed below it
            float shade = 0.6f + 0.4f * sw.z[i];
            if (st == LOOSE) shade *= 0.7f;
            float real = FLECK_SIZE * (1f + 0.25f * sw.z[i]);
            // Smaller than its least size on screen: drawn at that, fainter for it
            float s = Math.max(real, min);
            a *= Math.max(0.45f, real / s);
            Vector2 w = where(body, sw, i);
            batch.setColor(FLECK.r * shade, FLECK.g * shade, FLECK.b * shade, a);
            batch.draw(dot, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
        }
    }

    /**
     * What is below the hull's plane, into {@code batch} (not begun, its projection set), before
     * the hulls and decks are drawn over it: the flecks behind the hull, then additively the bottom
     * infill and the glow of the flecks behind. The light map dims it with the rest of space, and
     * the shield's own {@link #light} brightens it again.
     */
    public void drawUnder(Batch batch, float metersPerPixel) {
        if (drawn.size == 0) return;
        textures();
        float fade = zoomFade(metersPerPixel);
        batch.begin();
        drawFlecks(batch, true, metersPerPixel);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            ShieldSwarm sw = swarms.get(e.key);
            ShieldCloudComponent cloud = e.key.getComponent(ShieldCloudComponent.class);
            if (sw == null || cloud == null) continue;
            infill(batch, e.value, cloud, sw, INFILL_BOTTOM);
            if (fade > 0f) glowFlecks(batch, e.value, sw, true, metersPerPixel, fade);
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * The shield's own light over the hulls, into {@code batch} (not begun, its projection set),
     * additively: the top infill of craft not on their decks; the bubble's outline; packed flecks
     * in front of the hull glowing by how dense their region is; hits' flashes; and the emitters'
     * projectors. What is below the hull's plane was drawn already ({@link #drawUnder}).
     */
    public void drawGlow(Batch batch, float metersPerPixel) {
        if (drawn.size == 0) return;
        textures();
        float fade = zoomFade(metersPerPixel);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            ShieldSwarm sw = swarms.get(e.key);
            ShieldCloudComponent cloud = e.key.getComponent(ShieldCloudComponent.class);
            if (sw == null || cloud == null) continue;
            Body body = e.value;
            if (!decked.contains(e.key)) infill(batch, body, cloud, sw, INFILL_TOP);
            bubble(batch, body, cloud, sw);
            if (fade > 0f) glowFlecks(batch, body, sw, false, metersPerPixel, fade);
            for (float[] f : sw.flashes) {
                float t = f[5] / FLASH_LIFE, fadeOut = (1f - t) * (1f - t);
                float s = (1.5f + 4f * f[4]) * (0.6f + 0.8f * t);
                Vector2 w = CombatPhysics.toWorld(body, f[0], f[1], at);
                batch.setColor(1f, 1f, 1f, 0.9f * f[4] * fadeOut);
                batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
                batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, 0.5f * fadeOut);
                batch.draw(glow, w.x - s, w.y - s, 2f * s, 2f * s);
            }
            for (ShieldCloudComponent.Emitter em : cloud.emitters.values()) {
                float heat = em.spec == null ? 0f : MathUtils.clamp(em.heat / em.spec.heatTrip, 0f, 1f);
                float a;
                if (em.tripped) a = 0.35f + 0.25f * MathUtils.sin(sw.ph[0] * 12f);
                else if (em.working) a = 0.45f + 0.35f * Math.min(1f, em.draw / Math.max(1f, 3f * em.spec.capacity * ShieldModel.HOLD_W_PER_KG));
                else a = 0.08f;
                float r = SHIELD.r + (1f - SHIELD.r) * heat, g = SHIELD.g + (0.45f - SHIELD.g) * heat, b = 1f - 0.85f * heat;
                if (em.tripped) {
                    r = 1f;
                    g = 0.35f;
                    b = 0.15f;
                }
                float s = Math.max(1.6f, 4f * metersPerPixel);
                Vector2 w = CombatPhysics.toWorld(body, em.x, em.y, at);
                batch.setColor(r, g, b, a);
                batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
            }
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** The glow of {@code sw}'s flecks on one side of the hull's plane: packed ones by their region's density, struck ones white-hot. */
    private void glowFlecks(Batch batch, Body body, ShieldSwarm sw, boolean behind, float metersPerPixel, float fade) {
        float size = Math.max(FLECK_SIZE, MIN_PIXELS * metersPerPixel);
        for (int i = 0; i < sw.n; i++) {
            byte st = sw.state[i];
            if (st == DEAD || st == STOWED || (sw.z[i] < 0f) != behind) continue;
            float charge = 0.025f;
            if (st == PACKED && sw.region[i] != null) {
                ShieldCloudComponent.Region r = sw.region[i];
                charge = 0.15f + 0.75f * Math.min(1f, r.peak() / r.spec.maxDensity);
            } else if (st == LOOSE) {
                charge = 0f;
            } else if (st == LOST) {
                charge = 0.5f * Math.max(0f, sw.timer[i] / LOST_FADE);
            }
            float a = fade * Math.min(1f, charge + sw.flash[i]);
            if (a < 0.02f) continue;
            float s = size * (1.8f + 2f * sw.flash[i]);
            Vector2 w = where(body, sw, i);
            // White-hot as it is struck, cooling to cyan
            float hot = sw.flash[i];
            batch.setColor(SHIELD.r + (1f - SHIELD.r) * hot, SHIELD.g + (1f - SHIELD.g) * hot, 1f, a * 0.8f);
            batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
        }
    }

    /**
     * The curve the bubble is drawn on, body-local, into {@code sw}: the shield's own curve smoothed
     * round its center over {@link #BUBBLE_SMOOTH} samples, so it no longer follows every step of the
     * hull, kept at most {@link #BUBBLE_INSET} inside it; with each point's outward normal.
     */
    static void smoothCurve(ShieldPerimeter per, float[] sx, float[] sy, float[] nx, float[] ny) {
        int n = ShieldPerimeter.SAMPLES;
        float[] r = new float[n], dx = new float[n], dy = new float[n];
        for (int k = 0; k < n; k++) {
            float ox = per.x[k] - per.cx, oy = per.y[k] - per.cy;
            r[k] = (float) Math.sqrt(ox * ox + oy * oy);
            dx[k] = r[k] > 0f ? ox / r[k] : 1f;
            dy[k] = r[k] > 0f ? oy / r[k] : 0f;
        }
        int reach = (int) Math.ceil(3f * BUBBLE_SMOOTH);
        for (int k = 0; k < n; k++) {
            float sum = 0f, wsum = 0f;
            for (int j = -reach; j <= reach; j++) {
                float w = (float) Math.exp(-0.5f * j * j / (BUBBLE_SMOOTH * BUBBLE_SMOOTH));
                sum += w * r[((k + j) % n + n) % n];
                wsum += w;
            }
            float smooth = Math.max(sum / wsum, r[k] - BUBBLE_INSET);
            sx[k] = per.cx + dx[k] * smooth;
            sy[k] = per.cy + dy[k] * smooth;
        }
        for (int k = 0; k < n; k++) {
            int a = (k - 1 + n) % n, b = (k + 1) % n;
            float tx = sx[b] - sx[a], ty = sy[b] - sy[a];
            float len = (float) Math.sqrt(tx * tx + ty * ty);
            // Counterclockwise curve: outward is the tangent turned clockwise
            nx[k] = len > 0f ? ty / len : dx[k];
            ny[k] = len > 0f ? -tx / len : dy[k];
        }
    }

    /** {@code sw}'s smoothed curve, made afresh when the shield's curve is refitted. */
    private static void curveOf(ShieldSwarm sw, ShieldPerimeter per) {
        if (sw.curveFor == per) return;
        smoothCurve(per, sw.cx, sw.cy, sw.cnx, sw.cny);
        sw.curveFor = per;
    }

    /**
     * The faint bubble on {@code cloud}'s curve (smoothed): one continuous band through the cloud's
     * depth, a thin line over a soft glow, as bright as the share of the cloud held, dimmer where the
     * resting layer is thin (a dead emitter's notch) and brighter where it is thick, and brighter
     * still where regions pack it. Neighbouring stretches share their edges, so nothing overlaps.
     */
    private void bubble(Batch batch, Body body, ShieldCloudComponent cloud, ShieldSwarm sw) {
        ShieldPerimeter per = cloud.perimeter;
        float held = cloud.capacity > 0f ? Math.min(1f, cloud.cloud() / cloud.capacity) : 0f;
        if (held <= 0.01f) return;
        curveOf(sw, per);
        int n = ShieldPerimeter.SAMPLES;
        float base = cloud.baseLineDensity() / ShieldCloudComponent.DEPTH;
        float half = 0.5f * ShieldCloudComponent.DEPTH;
        float[] alpha = sw.bubbleAlpha;
        for (int k = 0; k < n; k++) {
            float along = per.along[k];
            float rest = cloud.baseLineDensityAt(along) / ShieldCloudComponent.DEPTH;
            float packed = Math.max(0f, cloud.densityAt(along) - rest) / BUBBLE_FULL;
            float lean = base > 0f ? Math.min(2f, rest / base) : 1f;
            alpha[k] = held * (BUBBLE_REST * lean + BUBBLE_PACKED * Math.min(1f, packed));
        }
        float u = 0.5f * (band.getU() + band.getU2());
        for (int k = 0; k < n; k++) {
            int j = (k + 1) % n;
            // Outer edge first, then inner: the band's texture runs across it
            corner(body, sw.cx[k] + sw.cnx[k] * half, sw.cy[k] + sw.cny[k] * half, alpha[k], u, band.getV(), 0);
            corner(body, sw.cx[k] - sw.cnx[k] * half, sw.cy[k] - sw.cny[k] * half, alpha[k], u, band.getV2(), 1);
            corner(body, sw.cx[j] - sw.cnx[j] * half, sw.cy[j] - sw.cny[j] * half, alpha[j], u, band.getV2(), 2);
            corner(body, sw.cx[j] + sw.cnx[j] * half, sw.cy[j] + sw.cny[j] * half, alpha[j], u, band.getV(), 3);
            batch.draw(band.getTexture(), quad, 0, 20);
        }
    }

    /**
     * An infill across {@code cloud}'s bubble, {@code edge} alpha at its rim and fading toward its
     * center as the fraction of the way out to the {@link #INFILL_EDGE} power, as bright as the share
     * of the cloud held: in rings, each sharing its edges with the next.
     */
    private void infill(Batch batch, Body body, ShieldCloudComponent cloud, ShieldSwarm sw, float edge) {
        float held = cloud.capacity > 0f ? Math.min(1f, cloud.cloud() / cloud.capacity) : 0f;
        if (held <= 0.01f) return;
        ShieldPerimeter per = cloud.perimeter;
        curveOf(sw, per);
        int n = ShieldPerimeter.SAMPLES;
        float u = 0.5f * (white.getU() + white.getU2()), v = 0.5f * (white.getV() + white.getV2());
        for (int ring = 0; ring < INFILL_RINGS; ring++) {
            float s0 = ring / (float) INFILL_RINGS, s1 = (ring + 1) / (float) INFILL_RINGS;
            float a0 = edge * held * (float) Math.pow(s0, INFILL_EDGE), a1 = edge * held * (float) Math.pow(s1, INFILL_EDGE);
            if (a1 < 0.002f) continue;
            for (int k = 0; k < n; k++) {
                int j = (k + 1) % n;
                corner(body, per.cx + (sw.cx[k] - per.cx) * s0, per.cy + (sw.cy[k] - per.cy) * s0, a0, u, v, 0);
                corner(body, per.cx + (sw.cx[k] - per.cx) * s1, per.cy + (sw.cy[k] - per.cy) * s1, a1, u, v, 1);
                corner(body, per.cx + (sw.cx[j] - per.cx) * s1, per.cy + (sw.cy[j] - per.cy) * s1, a1, u, v, 2);
                corner(body, per.cx + (sw.cx[j] - per.cx) * s0, per.cy + (sw.cy[j] - per.cy) * s0, a0, u, v, 3);
                batch.draw(white.getTexture(), quad, 0, 20);
            }
        }
    }

    /** Corner {@code c} of {@link #quad}: body-local ({@code lx}, {@code ly}) in world meters, shield cyan at {@code alpha}. */
    private void corner(Body body, float lx, float ly, float alpha, float u, float v, int c) {
        Vector2 w = CombatPhysics.toWorld(body, lx, ly, at);
        int o = c * 5;
        quad[o] = w.x;
        quad[o + 1] = w.y;
        quad[o + 2] = Color.toFloatBits(SHIELD.r, SHIELD.g, SHIELD.b, MathUtils.clamp(alpha, 0f, 1f));
        quad[o + 3] = u;
        quad[o + 4] = v;
    }

    /** Fleck {@code i} of {@code sw} in world meters, in the ship's frame or in space. */
    private Vector2 where(Body body, ShieldSwarm sw, int i) {
        if (!sw.inSpace(i)) return CombatPhysics.toWorld(body, sw.x[i], sw.y[i], at);
        Vector2 o = CombatPhysics.toWorld(body, 0f, 0f, at);
        return o.add(sw.x[i], sw.y[i]);
    }

    /** True if any craft collected this frame has flecks to draw. */
    public boolean any() {
        return drawn.size > 0;
    }

    private void textures() {
        if (dot != null) return;
        dot = new TextureRegion(ShieldTextures.dotTexture(16));
        glow = new TextureRegion(ShieldTextures.glowTexture(64));
        band = new TextureRegion(ShieldTextures.bandTexture(32));
        white = new TextureRegion(ShieldTextures.whiteTexture());
    }


    /** The flecks of {@code craft}, or null. For tests. */
    ShieldSwarm swarmOf(Entity craft) {
        return swarms.get(craft);
    }

    @Override
    public void dispose() {
        if (dot != null) dot.getTexture().dispose();
        if (glow != null) glow.getTexture().dispose();
        if (band != null) band.getTexture().dispose();
        if (white != null) white.getTexture().dispose();
        dot = glow = band = white = null;
    }
}
