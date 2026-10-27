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
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.ringsofiron.combatsys.CombatPhysics;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;

/**
 * A ship's shield as the cloud it is (documentation/combat-sys/Design.md, 5.11): dark metal flecks
 * drifting in lazy orbits round the {@link ShieldPerimeter}, sparse at rest, that stream into the
 * regions the emitters gather and glow there as they pack. A hit flashes, blasts the flecks it
 * plowed through along the shot, throws the lost ones clear to fade, and lets the rest curve back
 * in. A faint bubble traces the curve, brighter where regions pack it; an emitter that stops holding lets its share drift loose. Each emitter's projector glows on
 * the hull, toward orange as it heats.
 *
 * <p>Each frame: {@link #begin}, {@link #collect} every craft in view, {@link #end}; then
 * {@link #drawFlecks} with the hull art, so the light map lights them (those behind the hull's
 * plane first, {@code behind} true, and those in front after), and {@link #drawGlow} after the
 * light map, additively. The flecks follow the simulation's {@link ShieldCloudComponent}; they
 * show it and never feed back into it.
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
    static final float FLECK_SIZE = 0.08f, MIN_PIXELS = 1f;
    /** The bubble's glow at rest, and how much a packed region adds to it. */
    static final float BUBBLE_REST = 0.05f, BUBBLE_PACKED = 0.22f;
    /** Areal density at which a region lights the bubble fully, kg/m^2. */
    static final float BUBBLE_FULL = 25f;
    /** How far a fleck's lazy orbit swings it in and out of the curve, meters. */
    static final float ORBIT = 0.9f;
    /** Pull toward where a fleck should be (per second squared per meter), its damping, and its top speed, m/s. */
    static final float PULL = 18f, DAMP = 8f, TOP_SPEED = 30f;
    /** Fastest a thrown fleck is shown flying, m/s: real throws would leave the screen in a frame. */
    static final float THROW_SHOWN = 45f;
    /** Seconds a lost fleck takes to fade. */
    static final float LOST_FADE = 1.4f;
    /** Seconds a hit's flash lasts. */
    static final float FLASH_LIFE = 0.35f;

    static final byte DEAD = 0, FREE = 1, PACKED = 2, THROWN = 3, LOST = 4, LOOSE = 5;

    /** One ship's flecks, body-local. */
    static final class Swarm {
        int n;
        float kgPer;
        float[] u, drift, off, ph, w, key, x, y, vx, vy, timer, flash, rs, rn, z;
        byte[] state;
        ShieldCloudComponent.Region[] region;
        /** The last hit read from the cloud's log. */
        int hitsSeen = -1;
        /** Hits to flash, body-local: x, y, dirX, dirY, strength, age. */
        final Array<float[]> flashes = new Array<>();
        /** Hits read this frame, for the light: x, y, strength (body-local). */
        final Array<float[]> fresh = new Array<>();
        private final IdentityMap<ShieldCloudComponent.Region, int[]> members = new IdentityMap<>();
        private final Vector2 p = new Vector2(), nrm = new Vector2();

        Swarm(int n, float kgPer) {
            this.n = n;
            this.kgPer = kgPer;
            u = new float[n];
            drift = new float[n];
            off = new float[n];
            ph = new float[n];
            w = new float[n];
            key = new float[n];
            x = new float[n];
            y = new float[n];
            vx = new float[n];
            vy = new float[n];
            timer = new float[n];
            flash = new float[n];
            rs = new float[n];
            rn = new float[n];
            z = new float[n];
            state = new byte[n];
            region = new ShieldCloudComponent.Region[n];
            for (int i = 0; i < n; i++) {
                u[i] = MathUtils.random();
                // Lazy: a few meters a second along the curve, either way, and a slow swing through it
                drift[i] = MathUtils.random(0.6f, 2.2f) * (MathUtils.randomBoolean() ? 1f : -1f);
                off[i] = MathUtils.random(-0.5f, 0.5f) * ShieldCloudComponent.DEPTH;
                ph[i] = MathUtils.random(MathUtils.PI2);
                w[i] = MathUtils.random(0.25f, 0.7f);
                key[i] = (i + 0.5f) / n;
            }
        }

        /** How many flecks are in the cloud's stock: the rest are lost and not drawn. */
        int alive(ShieldCloudComponent cloud) {
            return MathUtils.clamp(Math.round(Math.max(0f, cloud.stock) / kgPer), 0, n);
        }

        /** The swarm {@code dt} seconds on, following {@code cloud}. */
        void update(ShieldCloudComponent cloud, float dt) {
            ShieldPerimeter per = cloud.perimeter;
            if (per == null || per.length <= 0f) return;
            fresh.clear();
            boolean first = hitsSeen < 0;
            if (first) {
                hitsSeen = cloud.hitCount;
                for (int i = 0; i < n; i++) {
                    place(per, i);
                    x[i] = p.x;
                    y[i] = p.y;
                }
            }
            readHits(cloud, per);
            reviveAndRetire(cloud);
            looseness(cloud);
            recruit(cloud, per);
            if (dt > 0f) move(per, Math.min(dt, 0.05f));
            for (int i = flashes.size - 1; i >= 0; i--) {
                float[] f = flashes.get(i);
                if ((f[5] += dt) >= FLASH_LIFE) flashes.removeIndex(i);
            }
        }

        /** Where fleck {@code i}'s orbit has it now, into {@link #p}; its depth into {@link #z}. */
        private void place(ShieldPerimeter per, int i) {
            per.pointAt(u[i] * per.length, p);
            per.normalAt(u[i] * per.length, nrm);
            float swing = ORBIT * MathUtils.sin(ph[i]);
            z[i] = MathUtils.cos(ph[i]);
            p.mulAdd(nrm, off[i] + swing);
        }

        /** Each new hit: a flash, and the flecks nearest it thrown along the shot, the lost ones for good. */
        private void readHits(ShieldCloudComponent cloud, ShieldPerimeter per) {
            for (ShieldCloudComponent.Hit h : cloud.hits) {
                if (h.index < hitsSeen) continue;
                hitsSeen = h.index + 1;
                per.pointAt(h.s, p);
                float hx = p.x, hy = p.y;
                float strength = strength(h.absorbed);
                flashes.add(new float[]{hx, hy, h.dirX, h.dirY, strength, 0f});
                fresh.add(new float[]{hx, hy, strength});
                int lost = round(h.lost / kgPer), thrown = round(h.scattered / kgPer);
                if (lost + thrown == 0) thrown = 1;
                Array<Integer> near = nearest(hx, hy, lost + thrown);
                float shown = Math.min(THROW_SHOWN, Math.max(8f, 0.15f * h.speed));
                for (int k = 0; k < near.size; k++) {
                    int i = near.get(k);
                    boolean gone = k < lost;
                    // A cone along the shot, wider for what is kept
                    float spread = (gone ? 0.35f : 0.8f) * MathUtils.random(-1f, 1f);
                    float c = MathUtils.cos(spread), s = MathUtils.sin(spread);
                    float dx = h.dirX * c - h.dirY * s, dy = h.dirX * s + h.dirY * c;
                    float speed = shown * MathUtils.random(gone ? 0.9f : 0.3f, gone ? 1.4f : 1f) * (h.beam ? 0.2f : 1f);
                    vx[i] = dx * speed;
                    vy[i] = dy * speed;
                    state[i] = gone ? LOST : THROWN;
                    region[i] = null;
                    flash[i] = 1f;
                    // Kept flecks rejoin as the cloud's scattered mass does: e-folding over its rejoin time
                    timer[i] = gone ? LOST_FADE : -ShieldModel.REJOIN * (float) Math.log(1f - MathUtils.random(0.95f));
                }
            }
        }

        private static int round(float v) {
            int whole = (int) v;
            return whole + (MathUtils.random() < v - whole ? 1 : 0);
        }

        /** Up to {@code count} live flecks nearest ({@code px}, {@code py}), nearest first. */
        private Array<Integer> nearest(float px, float py, int count) {
            Array<Integer> out = new Array<>();
            if (count <= 0) return out;
            Array<float[]> all = new Array<>();
            for (int i = 0; i < n; i++) {
                if (state[i] != FREE && state[i] != PACKED) continue;
                float dx = x[i] - px, dy = y[i] - py;
                all.add(new float[]{dx * dx + dy * dy, i});
            }
            all.sort((a, b) -> Float.compare(a[0], b[0]));
            for (int k = 0; k < Math.min(count, all.size); k++) out.add((int) all.get(k)[1]);
            return out;
        }

        /** Refills bring dead flecks back; a stock that fell (a beam's burn) retires the farthest-gone. */
        private void reviveAndRetire(ShieldCloudComponent cloud) {
            int want = alive(cloud), have = 0;
            for (int i = 0; i < n; i++) if (state[i] != DEAD && state[i] != LOST) have++;
            for (int i = 0; i < n && have < want; i++) {
                if (state[i] != DEAD) continue;
                state[i] = FREE;
                place(cloud.perimeter, i);
                x[i] = p.x;
                y[i] = p.y;
                vx[i] = vy[i] = 0f;
                have++;
            }
            for (int i = n - 1; i >= 0 && have > want; i--) {
                if (state[i] == DEAD || state[i] == LOST) continue;
                state[i] = LOST;
                region[i] = null;
                timer[i] = LOST_FADE;
                have--;
            }
        }

        /** Flecks past the working emitters' share drift loose, and come back when it recovers. */
        private void looseness(ShieldCloudComponent cloud) {
            for (int i = 0; i < n; i++) {
                if (state[i] == FREE || state[i] == PACKED) {
                    if (key[i] > cloud.holding) {
                        state[i] = LOOSE;
                        region[i] = null;
                        float a = MathUtils.random(MathUtils.PI2), sp = MathUtils.random(0.5f, 2.5f);
                        vx[i] += MathUtils.cos(a) * sp;
                        vy[i] += MathUtils.sin(a) * sp;
                    }
                } else if (state[i] == LOOSE && key[i] <= cloud.holding) {
                    state[i] = FREE;
                }
            }
        }

        /** Each region pulls in as many flecks as it holds mass, nearest free ones first; extras go free. */
        private void recruit(ShieldCloudComponent cloud, ShieldPerimeter per) {
            members.clear();
            for (ShieldCloudComponent.Region r : cloud.regions) members.put(r, new int[]{0, round(r.mass / kgPer)});
            for (int i = 0; i < n; i++) {
                if (state[i] != PACKED) continue;
                int[] m = members.get(region[i]);
                if (m == null || m[0] >= m[1]) {
                    state[i] = FREE;
                    region[i] = null;
                    // Back into the orbit it is passing through
                    u[i] = nearestU(per, x[i], y[i]);
                    off[i] = MathUtils.clamp(off[i], -1f, 1f);
                } else {
                    m[0]++;
                }
            }
            for (ShieldCloudComponent.Region r : cloud.regions) {
                int[] m = members.get(r);
                int need = m[1] - m[0];
                if (need <= 0) continue;
                per.pointAt(r.s, p);
                float rx = p.x, ry = p.y;
                Array<float[]> free = new Array<>();
                for (int i = 0; i < n; i++) {
                    if (state[i] != FREE) continue;
                    float dx = x[i] - rx, dy = y[i] - ry;
                    free.add(new float[]{dx * dx + dy * dy, i});
                }
                free.sort((a, b) -> Float.compare(a[0], b[0]));
                for (int k = 0; k < Math.min(need, free.size); k++) {
                    int i = (int) free.get(k)[1];
                    state[i] = PACKED;
                    region[i] = r;
                    rs[i] = raisedCosine();
                    rn[i] = MathUtils.random(-0.5f, 0.5f) * ShieldCloudComponent.DEPTH * 0.8f;
                }
            }
        }

        /** The arc-length fraction of the curve's sample nearest ({@code px}, {@code py}). */
        private static float nearestU(ShieldPerimeter per, float px, float py) {
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
                float dx = per.x[k] - px, dy = per.y[k] - py, d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
            return per.along[best] / per.length;
        }

        private void move(ShieldPerimeter per, float dt) {
            for (int i = 0; i < n; i++) {
                byte st = state[i];
                if (st == DEAD) continue;
                flash[i] = Math.max(0f, flash[i] - dt / FLASH_LIFE);
                ph[i] += w[i] * dt;
                if (st == LOST) {
                    x[i] += vx[i] * dt;
                    y[i] += vy[i] * dt;
                    if ((timer[i] -= dt) <= 0f) state[i] = DEAD;
                    continue;
                }
                if (st == THROWN) {
                    // Thrown clear, slowed by the emitters' field, then pulled back in
                    float drag = (float) Math.exp(-1.5f * dt);
                    vx[i] *= drag;
                    vy[i] *= drag;
                    x[i] += vx[i] * dt;
                    y[i] += vy[i] * dt;
                    if ((timer[i] -= dt) <= 0f) {
                        state[i] = FREE;
                        u[i] = nearestU(per, x[i], y[i]);
                    }
                    continue;
                }
                if (st == LOOSE) {
                    // No field holding it: it drifts, slowly losing what it had
                    float drag = (float) Math.exp(-0.3f * dt);
                    vx[i] *= drag;
                    vy[i] *= drag;
                    x[i] += vx[i] * dt;
                    y[i] += vy[i] * dt;
                    z[i] = MathUtils.cos(ph[i]);
                    continue;
                }
                float tx, ty;
                if (st == PACKED && region[i] != null) {
                    ShieldCloudComponent.Region r = region[i];
                    float s = r.s + rs[i] * r.spec.width;
                    per.pointAt(s, p);
                    per.normalAt(s, nrm);
                    // Packed tight: the orbit's swing all but stilled
                    p.mulAdd(nrm, rn[i] + 0.15f * ORBIT * MathUtils.sin(ph[i]));
                    z[i] = 0.3f * MathUtils.cos(ph[i]);
                    tx = p.x;
                    ty = p.y;
                    u[i] = per.wrap(s) / per.length;
                } else {
                    u[i] += drift[i] * dt / per.length;
                    u[i] -= MathUtils.floor(u[i]);
                    place(per, i);
                    tx = p.x;
                    ty = p.y;
                }
                vx[i] += ((tx - x[i]) * PULL - vx[i] * DAMP) * dt;
                vy[i] += ((ty - y[i]) * PULL - vy[i] * DAMP) * dt;
                float sp = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                if (sp > TOP_SPEED) {
                    vx[i] *= TOP_SPEED / sp;
                    vy[i] *= TOP_SPEED / sp;
                }
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
            }
        }

        /** A draw from the raised cosine over -1 to 1, as the region's mass lies. */
        static float raisedCosine() {
            // Rejection: cheap, and only on recruiting
            while (true) {
                float t = MathUtils.random(-1f, 1f);
                if (MathUtils.random() < 0.5f * (1f + MathUtils.cos(MathUtils.PI * t))) return t;
            }
        }

        int count(byte st) {
            int c = 0;
            for (int i = 0; i < n; i++) if (state[i] == st) c++;
            return c;
        }
    }

    private final ObjectMap<Entity, Swarm> swarms = new ObjectMap<>();
    private final ObjectMap<Entity, Body> drawn = new ObjectMap<>();
    private final ObjectSet<Entity> seen = new ObjectSet<>();
    private final Vector2 at = new Vector2();
    private TextureRegion dot, glow, band;
    private float delta;

    /** How bright a hit shows for {@code joules} the cloud took, 0.2 to 1, by its order. */
    static float strength(float joules) {
        float order = (float) Math.log10(Math.max(1f, joules));
        return MathUtils.clamp((order - 4f) / 4f, 0.2f, 1f);
    }

    /** Starts a frame {@code delta} seconds after the last (0 while paused). */
    public void begin(float delta) {
        this.delta = Math.max(0f, delta);
        seen.clear();
        drawn.clear();
    }

    /**
     * {@code craft}, flying as {@code body}: its flecks follow its cloud, and each new hit flashes
     * shield light into {@code light} (may be null).
     */
    public void collect(Entity craft, Body body, ExteriorLighting light) {
        ShieldCloudComponent cloud = craft == null ? null : craft.getComponent(ShieldCloudComponent.class);
        if (cloud == null || cloud.perimeter == null || body == null || cloud.capacity <= 0f) return;
        seen.add(craft);
        Swarm swarm = swarms.get(craft);
        int n = MathUtils.clamp(Math.round(cloud.capacity / KG_PER_FLECK), MIN_FLECKS, MAX_FLECKS);
        if (swarm == null || swarm.n != n) swarms.put(craft, swarm = new Swarm(n, cloud.capacity / n));
        swarm.update(cloud, delta);
        if (light != null) {
            for (float[] f : swarm.fresh) {
                Vector2 w = CombatPhysics.toWorld(body, f[0], f[1], at);
                light.flash(w.x, w.y, SHIELD, 6f + 14f * f[2], 0.3f + 0.6f * f[2], 0.25f + 0.25f * f[2]);
            }
        }
        drawn.put(craft, body);
    }

    /** Forgets the craft not collected since {@link #begin}. */
    public void end() {
        for (ObjectMap.Entries<Entity, Swarm> it = swarms.entries(); it.hasNext; ) {
            if (!seen.contains(it.next().key)) it.remove();
        }
    }

    /**
     * The flecks of every craft collected this frame into {@code batch} (begun, normal blending):
     * those behind the hull's plane with {@code behind}, else those in front. {@code metersPerPixel}
     * keeps them a pixel or two across however far out the view is.
     */
    public void drawFlecks(Batch batch, boolean behind, float metersPerPixel) {
        if (drawn.size == 0) return;
        textures();
        float size = Math.max(FLECK_SIZE, MIN_PIXELS * metersPerPixel);
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            Swarm sw = swarms.get(e.key);
            if (sw == null) continue;
            Body body = e.value;
            for (int i = 0; i < sw.n; i++) {
                byte st = sw.state[i];
                if (st == DEAD || (sw.z[i] < 0f) != behind) continue;
                float a = st == LOST ? Math.max(0f, sw.timer[i] / LOST_FADE) : 1f;
                // Turned toward the light above the plane, shadowed below it
                float shade = 0.6f + 0.4f * sw.z[i];
                if (st == LOOSE) shade *= 0.7f;
                float s = size * (1f + 0.25f * sw.z[i]);
                Vector2 w = CombatPhysics.toWorld(body, sw.x[i], sw.y[i], at);
                batch.setColor(FLECK.r * shade, FLECK.g * shade, FLECK.b * shade, a);
                batch.draw(dot, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * The shield's own light, into {@code batch} (not begun, its projection set), additively:
     * packed flecks glowing by how dense their region is, hits' flashes, and the emitters' projectors.
     */
    public void drawGlow(Batch batch, float metersPerPixel) {
        if (drawn.size == 0) return;
        textures();
        float size = Math.max(FLECK_SIZE, MIN_PIXELS * metersPerPixel);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (ObjectMap.Entry<Entity, Body> e : drawn) {
            Swarm sw = swarms.get(e.key);
            ShieldCloudComponent cloud = e.key.getComponent(ShieldCloudComponent.class);
            if (sw == null || cloud == null) continue;
            Body body = e.value;
            bubble(batch, body, cloud);
            for (int i = 0; i < sw.n; i++) {
                byte st = sw.state[i];
                if (st == DEAD) continue;
                float charge = 0.025f;
                if (st == PACKED && sw.region[i] != null) {
                    ShieldCloudComponent.Region r = sw.region[i];
                    charge = 0.15f + 0.75f * Math.min(1f, r.peak() / r.spec.maxDensity);
                } else if (st == LOOSE) {
                    charge = 0f;
                } else if (st == LOST) {
                    charge = 0.5f * Math.max(0f, sw.timer[i] / LOST_FADE);
                }
                float a = Math.min(1f, charge + sw.flash[i]);
                if (a < 0.02f) continue;
                float s = size * (1.8f + 2f * sw.flash[i]);
                Vector2 w = CombatPhysics.toWorld(body, sw.x[i], sw.y[i], at);
                // White-hot as it is struck, cooling to cyan
                float hot = sw.flash[i];
                batch.setColor(SHIELD.r + (1f - SHIELD.r) * hot, SHIELD.g + (1f - SHIELD.g) * hot, 1f, a * 0.8f);
                batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
            }
            for (float[] f : sw.flashes) {
                float t = f[5] / FLASH_LIFE, fade = (1f - t) * (1f - t);
                float s = (1.5f + 4f * f[4]) * (0.6f + 0.8f * t);
                Vector2 w = CombatPhysics.toWorld(body, f[0], f[1], at);
                batch.setColor(1f, 1f, 1f, 0.9f * f[4] * fade);
                batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
                batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, 0.5f * fade);
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
                float s = Math.max(1.6f, 4f * MIN_PIXELS * metersPerPixel);
                Vector2 w = CombatPhysics.toWorld(body, em.x, em.y, at);
                batch.setColor(r, g, b, a);
                batch.draw(glow, w.x - 0.5f * s, w.y - 0.5f * s, s, s);
            }
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * The faint bubble on {@code cloud}'s curve: a soft band through the cloud's depth, as bright as
     * the share of the cloud held, and brighter where regions pack it.
     */
    private void bubble(Batch batch, Body body, ShieldCloudComponent cloud) {
        ShieldPerimeter per = cloud.perimeter;
        float held = cloud.stock > 0f && cloud.capacity > 0f ? cloud.holding * Math.min(1f, (cloud.stock - cloud.scattered) / cloud.capacity) : 0f;
        if (held <= 0.01f) return;
        float base = cloud.baseLineDensity() / ShieldCloudComponent.DEPTH;
        float deg = body.getAngle() * MathUtils.radiansToDegrees;
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
            int k2 = (k + 1) % ShieldPerimeter.SAMPLES;
            float x0 = per.x[k], y0 = per.y[k], x1 = per.x[k2], y1 = per.y[k2];
            float len = Vector2.dst(x0, y0, x1, y1);
            if (len < 1e-4f) continue;
            float mid = per.along[k] + 0.5f * len;
            float packed = Math.max(0f, cloud.densityAt(mid) - base) / BUBBLE_FULL;
            float a = held * (BUBBLE_REST + BUBBLE_PACKED * Math.min(1f, packed));
            Vector2 w = CombatPhysics.toWorld(body, 0.5f * (x0 + x1), 0.5f * (y0 + y1), at);
            float along = MathUtils.atan2(y1 - y0, x1 - x0) * MathUtils.radiansToDegrees + deg;
            float thick = ShieldCloudComponent.DEPTH;
            batch.setColor(SHIELD.r, SHIELD.g, SHIELD.b, a);
            // A little longer than the segment, so neighbours meet without a seam
            float l = len + 0.05f;
            batch.draw(band, w.x - 0.5f * l, w.y - 0.5f * thick, 0.5f * l, 0.5f * thick, l, thick, 1f, 1f, along);
        }
    }

    /** True if any craft collected this frame has flecks to draw. */
    public boolean any() {
        return drawn.size > 0;
    }

    private void textures() {
        if (dot != null) return;
        dot = new TextureRegion(dotTexture(16));
        glow = new TextureRegion(glowTexture(64));
        band = new TextureRegion(bandTexture(32));
    }

    /** A hard-edged round fleck, white, for tinting. */
    static Texture dotTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, MathUtils.clamp((1f - r) * 4f, 0f, 1f)));
            }
        }
        return texture(pm);
    }

    /** A soft round glow, white, for tinting. */
    static Texture glowTexture(int n) {
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

    /** A band, soft across and even along, white, for tinting and stretching along a curve. */
    static Texture bandTexture(int n) {
        Pixmap pm = new Pixmap(4, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            float v = (y + 0.5f) / n * 2f - 1f;
            float a = bandAlpha(v);
            for (int x = 0; x < 4; x++) pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
        }
        return texture(pm);
    }

    /** The band's alpha {@code v} across it (-1 to 1): a bright thin line at the middle over a soft glow. */
    static float bandAlpha(float v) {
        float line = v / 0.12f, soft = v / 0.5f;
        return Math.min(1f, 0.8f * (float) Math.exp(-line * line) + 0.35f * (float) Math.exp(-soft * soft));
    }

    private static Texture texture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    /** The flecks of {@code craft}, or null. For tests. */
    Swarm swarmOf(Entity craft) {
        return swarms.get(craft);
    }

    @Override
    public void dispose() {
        if (dot != null) dot.getTexture().dispose();
        if (glow != null) glow.getTexture().dispose();
        if (band != null) band.getTexture().dispose();
        dot = glow = band = null;
    }
}
