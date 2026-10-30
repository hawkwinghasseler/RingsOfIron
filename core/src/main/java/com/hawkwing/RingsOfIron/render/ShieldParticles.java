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
import com.ringsofiron.physicsexterior.PhysicsManager;

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
 * below the hull's plane is drawn first and what is above it last. The flecks follow the
 * simulation's {@link ShieldCloudComponent}; they show it and never feed back into it.
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

    /** Fastest a ship's velocity changes in a frame, m/s, before it is taken for the floating origin moving. */
    static final float REBASE_JUMP = 30f;

    /**
     * What a fleck is doing. In the ship's frame: orbiting ({@code FREE}), packed in a region,
     * thrown by a hit and coming back, streaming into an emitter ({@code STOWING}) and packed in it
     * ({@code STOWED}, not drawn). In space ({@code LOOSE}: adrift, no emitter holding it; {@code LOST}:
     * gone for good, fading), it keeps the velocity it had and the ship no longer carries it.
     */
    static final byte DEAD = 0, FREE = 1, PACKED = 2, THROWN = 3, LOST = 4, LOOSE = 5, STOWING = 6, STOWED = 7;

    /** A ship's frame this frame: its origin's velocity (m/s, world), its heading and its spin (rad, rad/s). */
    static final class Frame {
        float vx, vy, angle, spin;
    }

    /**
     * One ship's flecks: body-local meters and m/s in the ship's frame; for those in space, meters
     * from the ship's origin along the world's axes and m/s relative to its origin's velocity, so
     * the floating origin moving takes nothing with it.
     */
    static final class Swarm {
        int n;
        float kgPer;
        float[] u, drift, off, ph, w, x, y, vx, vy, timer, flash, rs, rn, z, homeX, homeY;
        /**
         * The resting layer's mass round the curve, cumulative, 0 to 1, at each sample: a free
         * fleck's orbit {@link #u} is a share of that mass, so the flecks lie as the layer does.
         */
        final float[] cdf = new float[ShieldPerimeter.SAMPLES + 1];
        /** The bubble's smoothed curve and its outward normals, body-local, for {@link #curveFor}. */
        final float[] cx = new float[ShieldPerimeter.SAMPLES], cy = new float[ShieldPerimeter.SAMPLES],
                cnx = new float[ShieldPerimeter.SAMPLES], cny = new float[ShieldPerimeter.SAMPLES];
        ShieldPerimeter curveFor;
        /** The bubble's alpha at each sample of its curve this frame. */
        final float[] bubbleAlpha = new float[ShieldPerimeter.SAMPLES];
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
        /** The ship's origin velocity last frame, world m/s; NaN before the first. */
        private float lastVx = Float.NaN, lastVy;
        private float angle, spin;

        Swarm(int n, float kgPer) {
            this.n = n;
            this.kgPer = kgPer;
            u = new float[n];
            drift = new float[n];
            off = new float[n];
            ph = new float[n];
            w = new float[n];
            homeX = new float[n];
            homeY = new float[n];
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
            }
        }

        /** How many flecks are in the cloud's stock: the rest are lost and not drawn. */
        int alive(ShieldCloudComponent cloud) {
            return MathUtils.clamp(Math.round(Math.max(0f, cloud.stock) / kgPer), 0, n);
        }

        /** The swarm {@code dt} seconds on, following {@code cloud}, for a ship at rest. */
        void update(ShieldCloudComponent cloud, float dt) {
            update(cloud, dt, new Frame());
        }

        /** The swarm {@code dt} seconds on, following {@code cloud}, its ship flying as {@code frame}. */
        void update(ShieldCloudComponent cloud, float dt, Frame frame) {
            ShieldPerimeter per = cloud.perimeter;
            if (per == null || per.length <= 0f) return;
            fresh.clear();
            angle = frame.angle;
            spin = frame.spin;
            // What the ship's velocity did since the last frame, which what is in space doesn't share
            float dvx = 0f, dvy = 0f;
            if (!Float.isNaN(lastVx)) {
                dvx = frame.vx - lastVx;
                dvy = frame.vy - lastVy;
                // A jump no engine makes in a frame is the floating origin moving: every velocity shifted alike
                if (dvx * dvx + dvy * dvy > REBASE_JUMP * REBASE_JUMP) dvx = dvy = 0f;
            }
            lastVx = frame.vx;
            lastVy = frame.vy;
            layout(cloud, per);
            boolean first = hitsSeen < 0;
            if (first) {
                hitsSeen = cloud.hitCount;
                int stowedNow = MathUtils.clamp(Math.round(cloud.stowed / kgPer), 0, n);
                for (int i = 0; i < n; i++) {
                    place(per, i);
                    x[i] = p.x;
                    y[i] = p.y;
                    if (i < stowedNow) stowAt(cloud, i, true);
                }
            }
            readHits(cloud, per);
            reviveAndRetire(cloud);
            adrift(cloud, per);
            stow(cloud);
            recruit(cloud, per);
            if (dt > 0f) move(per, Math.min(dt, 0.05f), dvx, dvy);
            for (int i = flashes.size - 1; i >= 0; i--) {
                float[] f = flashes.get(i);
                if ((f[5] += dt) >= FLASH_LIFE) flashes.removeIndex(i);
            }
        }

        /** {@link #cdf} from the cloud's resting layer; even round the curve until it has one. */
        void layout(ShieldCloudComponent cloud, ShieldPerimeter per) {
            float sum = 0f;
            for (float m : cloud.line) sum += m;
            cdf[0] = 0f;
            for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
                cdf[k + 1] = sum > 0f ? cdf[k] + cloud.line[k] / sum : per.along[k + 1] / per.length;
            }
            cdf[ShieldPerimeter.SAMPLES] = 1f;
        }

        /** Meters along the curve at share {@code share} of the resting layer's mass. */
        float sOf(ShieldPerimeter per, float share) {
            int lo = 0, hi = ShieldPerimeter.SAMPLES - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (cdf[mid] <= share) lo = mid;
                else hi = mid - 1;
            }
            float span = cdf[lo + 1] - cdf[lo];
            float f = span > 0f ? MathUtils.clamp((share - cdf[lo]) / span, 0f, 1f) : 0f;
            return per.along[lo] + f * (per.along[lo + 1] - per.along[lo]);
        }

        /** The share of the resting layer's mass that lies before {@code s} meters along the curve. */
        float uOf(ShieldPerimeter per, float s) {
            s = per.wrap(s);
            int k = per.segmentAt(s);
            float seg = per.along[k + 1] - per.along[k];
            float f = seg > 0f ? (s - per.along[k]) / seg : 0f;
            return cdf[k] + f * (cdf[k + 1] - cdf[k]);
        }

        /** Where fleck {@code i}'s orbit has it now, into {@link #p}; its depth into {@link #z}. */
        private void place(ShieldPerimeter per, int i) {
            float s = sOf(per, u[i]);
            per.pointAt(s, p);
            per.normalAt(s, nrm);
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
                    if (gone) toSpace(i);
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
            // Lost: what is adrift first, then whatever is out
            for (int pass = 0; pass < 2 && have > want; pass++) {
                for (int i = n - 1; i >= 0 && have > want; i--) {
                    byte st = state[i];
                    if (st == DEAD || st == LOST || st == STOWED || (pass == 0) != (st == LOOSE)) continue;
                    if (st != LOOSE) toSpace(i);
                    state[i] = LOST;
                    region[i] = null;
                    timer[i] = LOST_FADE;
                    have--;
                }
            }
        }

        /** Fleck {@code i}, in the ship's frame, put into space with the velocity it has there. */
        private void toSpace(int i) {
            float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
            float ox = c * x[i] - s * y[i], oy = s * x[i] + c * y[i];
            float ux = c * vx[i] - s * vy[i] - spin * oy, uy = s * vx[i] + c * vy[i] + spin * ox;
            x[i] = ox;
            y[i] = oy;
            vx[i] = ux;
            vy[i] = uy;
        }

        /** Fleck {@code i}, in space, back into the ship's frame. */
        private void fromSpace(int i) {
            float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
            float ox = x[i], oy = y[i];
            float ux = vx[i] + spin * oy, uy = vy[i] - spin * ox;
            x[i] = c * ox + s * oy;
            y[i] = -s * ox + c * oy;
            vx[i] = c * ux + s * uy;
            vy[i] = -s * ux + c * uy;
        }

        /** True if fleck {@code i} is in space rather than the ship's frame. */
        boolean inSpace(int i) {
            return state[i] == LOOSE || state[i] == LOST;
        }

        /**
         * As many flecks adrift as the cloud has mass adrift, those where no working emitter holds
         * the curve first: they go with what velocity they had; recaptured ones come back.
         */
        private void adrift(ShieldCloudComponent cloud, ShieldPerimeter per) {
            int want = Math.round(cloud.adrift / kgPer), have = count(LOOSE);
            if (have < want) {
                Array<float[]> out = new Array<>();
                for (int i = 0; i < n; i++) {
                    byte st = state[i];
                    if (st != FREE && st != PACKED && st != THROWN && st != STOWING) continue;
                    // Thrown or streaming in: as likely as any
                    float held = st == FREE || st == PACKED ? cloud.anchored[per.segmentAt(sOf(per, u[i]))] : 0.5f;
                    out.add(new float[]{held + 0.05f * MathUtils.random(), i});
                }
                out.sort((a, b) -> Float.compare(a[0], b[0]));
                for (int k = 0; k < out.size && have < want; k++) {
                    int i = (int) out.get(k)[1];
                    state[i] = LOOSE;
                    region[i] = null;
                    toSpace(i);
                    have++;
                }
            }
            for (int i = 0; i < n && have > want; i++) {
                if (state[i] != LOOSE) continue;
                fromSpace(i);
                state[i] = THROWN;
                timer[i] = MathUtils.random(0.3f, 1.2f);
                have--;
            }
        }

        /** As many flecks in the emitters as the cloud has stowed: the nearest stream in; deployed ones come out of them. */
        private void stow(ShieldCloudComponent cloud) {
            int want = MathUtils.clamp(Math.round(cloud.stowed / kgPer), 0, n), have = count(STOWING) + count(STOWED);
            for (int i = 0; i < n && have < want; i++) {
                byte st = state[i];
                if (st != FREE && st != PACKED && st != THROWN) continue;
                stowAt(cloud, i, false);
                have++;
            }
            for (int pass = 0; pass < 2 && have > want; pass++) {
                byte from = pass == 0 ? STOWED : STOWING;
                for (int i = 0; i < n && have > want; i++) {
                    if (state[i] != from) continue;
                    if (from == STOWED) {
                        x[i] = homeX[i];
                        y[i] = homeY[i];
                        vx[i] = vy[i] = 0f;
                    }
                    state[i] = FREE;
                    have--;
                }
            }
        }

        /** Sends fleck {@code i} to the emitter nearest it ({@code already}: it is in it). */
        private void stowAt(ShieldCloudComponent cloud, int i, boolean already) {
            float best = Float.MAX_VALUE;
            homeX[i] = 0f;
            homeY[i] = 0f;
            for (ShieldCloudComponent.Emitter em : cloud.emitters.values()) {
                float d = Vector2.dst2(x[i], y[i], em.x, em.y);
                if (d < best) {
                    best = d;
                    homeX[i] = em.x;
                    homeY[i] = em.y;
                }
            }
            state[i] = already ? STOWED : STOWING;
            region[i] = null;
            if (already) {
                x[i] = homeX[i];
                y[i] = homeY[i];
                vx[i] = vy[i] = 0f;
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

        /** The orbit, as a share of the resting layer, of the curve's sample nearest ({@code px}, {@code py}). */
        private float nearestU(ShieldPerimeter per, float px, float py) {
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
                float dx = per.x[k] - px, dy = per.y[k] - py, d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
            return cdf[best];
        }

        private void move(ShieldPerimeter per, float dt, float dvx, float dvy) {
            for (int i = 0; i < n; i++) {
                byte st = state[i];
                if (st == DEAD || st == STOWED) continue;
                flash[i] = Math.max(0f, flash[i] - dt / FLASH_LIFE);
                ph[i] += w[i] * dt;
                if (st == LOST || st == LOOSE) {
                    // In space: its velocity holds; the ship's changing leaves it behind
                    vx[i] -= dvx;
                    vy[i] -= dvy;
                    x[i] += vx[i] * dt;
                    y[i] += vy[i] * dt;
                    if (st == LOOSE) z[i] = MathUtils.cos(ph[i]);
                    else if ((timer[i] -= dt) <= 0f) state[i] = DEAD;
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
                float tx, ty;
                if (st == STOWING) {
                    tx = homeX[i];
                    ty = homeY[i];
                    z[i] = 0f;
                    if (Vector2.dst2(x[i], y[i], tx, ty) < 0.09f) {
                        state[i] = STOWED;
                        continue;
                    }
                } else if (st == PACKED && region[i] != null) {
                    ShieldCloudComponent.Region r = region[i];
                    float s = r.s + rs[i] * r.spec.width;
                    per.pointAt(s, p);
                    per.normalAt(s, nrm);
                    // Packed tight: the orbit's swing all but stilled
                    p.mulAdd(nrm, rn[i] + 0.15f * ORBIT * MathUtils.sin(ph[i]));
                    z[i] = 0.3f * MathUtils.cos(ph[i]);
                    tx = p.x;
                    ty = p.y;
                    u[i] = uOf(per, s);
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
    /** The craft collected this frame that are drawn as their decks. */
    private final ObjectSet<Entity> decked = new ObjectSet<>();
    /** One quad's vertices for the batch: x, y, color, u, v at each corner. */
    private final float[] quad = new float[20];
    private final Vector2 at = new Vector2();
    private final Frame frame = new Frame();
    private TextureRegion dot, glow, band, white;
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
        Swarm swarm = swarms.get(craft);
        int n = MathUtils.clamp(Math.round(cloud.capacity / KG_PER_FLECK), MIN_FLECKS, MAX_FLECKS);
        if (swarm == null || swarm.n != n) swarms.put(craft, swarm = new Swarm(n, cloud.capacity / n));
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
            Swarm sw = swarms.get(e.key);
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
        for (ObjectMap.Entries<Entity, Swarm> it = swarms.entries(); it.hasNext; ) {
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
            Swarm sw = swarms.get(e.key);
            if (sw == null) continue;
            flecks(batch, e.value, sw, behind, metersPerPixel, fade);
        }
        batch.setColor(Color.WHITE);
    }

    private void flecks(Batch batch, Body body, Swarm sw, boolean behind, float metersPerPixel, float fade) {
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
            Swarm sw = swarms.get(e.key);
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
            Swarm sw = swarms.get(e.key);
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
    private void glowFlecks(Batch batch, Body body, Swarm sw, boolean behind, float metersPerPixel, float fade) {
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
    private static void curveOf(Swarm sw, ShieldPerimeter per) {
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
    private void bubble(Batch batch, Body body, ShieldCloudComponent cloud, Swarm sw) {
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
    private void infill(Batch batch, Body body, ShieldCloudComponent cloud, Swarm sw, float edge) {
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
    private Vector2 where(Body body, Swarm sw, int i) {
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
        dot = new TextureRegion(dotTexture(16));
        glow = new TextureRegion(glowTexture(64));
        band = new TextureRegion(bandTexture(32));
        white = new TextureRegion(whiteTexture());
    }

    /** A round fleck, white, for tinting: solid at its heart and fading to nothing at its rim. */
    static Texture dotTexture(int n) {
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, dotAlpha((float) Math.sqrt(dx * dx + dy * dy))));
            }
        }
        return texture(pm);
    }

    /** A fleck's alpha {@code r} out from its center (0 to 1 at its rim): a smooth fall from 1 to 0. */
    static float dotAlpha(float r) {
        float t = MathUtils.clamp(1f - r, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Plain white, for tinting. */
    static Texture whiteTexture() {
        Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
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
        if (white != null) white.getTexture().dispose();
        dot = glow = band = white = null;
    }
}
