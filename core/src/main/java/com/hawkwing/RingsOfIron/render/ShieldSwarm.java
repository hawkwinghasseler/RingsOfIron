package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;

/**
 * One ship's shield flecks, simulated apart from how {@link ShieldParticles} draws them: body-local
 * meters and m/s in the ship's frame; for those in space, meters from the ship's origin along the
 * world's axes and m/s relative to its origin's velocity, so the floating origin moving takes nothing
 * with it. The flecks follow the simulation's {@link ShieldCloudComponent}; they show it and never
 * feed back into it.
 */
final class ShieldSwarm {
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

    /** How bright a hit shows for {@code joules} the cloud took, 0.2 to 1, by its order. */
    static float strength(float joules) {
        float order = (float) Math.log10(Math.max(1f, joules));
        return MathUtils.clamp((order - 4f) / 4f, 0.2f, 1f);
    }

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

    ShieldSwarm(int n, float kgPer) {
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
