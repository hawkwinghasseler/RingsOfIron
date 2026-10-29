package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.shields.EmitterSpec;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The flecks orbit the cloud's curve, pack into its regions, fly from hits, and drift loose when it isn't held. */
class ShieldParticlesTest {
    private static final float DT = 1f / 60f;
    private ShieldCloudComponent cloud;
    private ShieldParticles.Swarm swarm;

    @BeforeEach
    void setUp() {
        MathUtils.random.setSeed(7L);
        // A 20 m by 8 m hull
        ShipGridComponent grid = new ShipGridComponent(10, 4, 2f, -10f, -4f);
        cloud = new ShieldCloudComponent();
        cloud.perimeter = ShieldPerimeter.fit(grid);
        cloud.capacity = 300f;
        cloud.stock = 300f;
        cloud.holding = 1f;
        swarm = new ShieldParticles.Swarm(200, 1.5f);
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) swarm.update(cloud, DT);
    }

    /** How far fleck {@code i} is from the curve, meters (to its nearest sample). */
    private float offCurve(int i) {
        float best = Float.MAX_VALUE;
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
            best = Math.min(best, Vector2.dst(swarm.x[i], swarm.y[i], cloud.perimeter.x[k], cloud.perimeter.y[k]));
        }
        return best;
    }

    @Test
    void atRestTheFlecksDriftInOrbitsAboutTheCurve() {
        run(2f);
        assertEquals(200, swarm.count(ShieldParticles.FREE));
        // The curve's samples are a couple of meters apart; allow for that, the depth and the swing
        float slack = 0.5f * ShieldCloudComponent.DEPTH + ShieldParticles.ORBIT + 1.5f;
        for (int i = 0; i < swarm.n; i++) assertTrue(offCurve(i) < slack, "fleck " + i + " is " + offCurve(i) + " m off");
        float before = swarm.u[0];
        run(1f);
        assertNotEquals(before, swarm.u[0], "and they move along it");
    }

    @Test
    void aRegionPullsInFlecksByItsMassAndPacksThemWhereItIs() {
        run(0.5f);
        ShieldCloudComponent.Region r = new ShieldCloudComponent.Region();
        r.spec = EmitterSpec.S;
        r.s = 0f;
        r.mass = 90f;
        cloud.regions.add(r);
        run(3f);
        int packed = swarm.count(ShieldParticles.PACKED);
        assertEquals(60, packed, 3, "about one fleck per 1.5 kg");
        Vector2 at = cloud.perimeter.pointAt(0f, new Vector2());
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] != ShieldParticles.PACKED) continue;
            float d = Vector2.dst(swarm.x[i], swarm.y[i], at.x, at.y);
            assertTrue(d < EmitterSpec.S.width + ShieldCloudComponent.DEPTH, "packed fleck " + i + " is " + d + " m off the region");
        }
        cloud.regions.clear();
        run(0.1f);
        assertEquals(0, swarm.count(ShieldParticles.PACKED), "they go back to orbit when it lets go");
    }

    @Test
    void aHitThrowsFlecksAlongTheShotAndTheLostOnesFadeForGood() {
        run(0.5f);
        ShieldCloudComponent.Hit h = new ShieldCloudComponent.Hit();
        h.index = cloud.hitCount++;
        h.s = 0f;
        h.dirX = -1f;
        h.absorbed = 3e5f;
        h.scattered = 15f;
        h.lost = 6f;
        h.speed = 300f;
        cloud.hits.add(h);
        cloud.stock -= 6f;
        swarm.update(cloud, DT);
        assertEquals(4, swarm.count(ShieldParticles.LOST), "6 kg lost is four flecks");
        assertEquals(10, swarm.count(ShieldParticles.THROWN), "15 kg knocked loose is ten");
        assertEquals(1, swarm.fresh.size, "and a flash for the light");
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] == ShieldParticles.LOST) assertTrue(swarm.vx[i] < 0f, "thrown along the shot");
        }
        run(ShieldParticles.LOST_FADE + 0.1f);
        assertEquals(0, swarm.count(ShieldParticles.LOST));
        assertEquals(swarm.alive(cloud), swarm.n - swarm.count(ShieldParticles.DEAD), "the lost are gone, the rest kept");
        run(6f * ShieldModel.REJOIN);
        assertEquals(0, swarm.count(ShieldParticles.THROWN), "the kept ones rejoin");
        swarm.update(cloud, DT);
        assertEquals(0, swarm.fresh.size, "a hit flashes once");
    }

    @Test
    void massAdriftKeepsItsVelocityWhileTheShipFliesOn() {
        ShieldParticles.Frame f = new ShieldParticles.Frame();
        for (int i = 0; i < 30; i++) swarm.update(cloud, DT, f);
        cloud.adrift = 150f;
        swarm.update(cloud, DT, f);
        assertEquals(100, swarm.count(ShieldParticles.LOOSE), "150 kg adrift is a hundred flecks");
        float before = meanLooseX();
        // The ship speeds up 10 m/s and flies on a second: what is adrift falls 10 m behind it
        f.vx = 10f;
        for (int i = 0; i < 60; i++) swarm.update(cloud, DT, f);
        assertEquals(-10f, meanLooseX() - before, 1.5f, "left behind");
        // The floating origin moving shifts every velocity at once: that leaves nothing behind
        before = meanLooseX();
        f.vx = 210f;
        swarm.update(cloud, DT, f);
        assertEquals(0f, meanLooseX() - before, 0.5f);
        // Pulled back in when the emitters recover
        cloud.adrift = 0f;
        swarm.update(cloud, DT, f);
        assertEquals(0, swarm.count(ShieldParticles.LOOSE));
    }

    private float meanLooseX() {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] != ShieldParticles.LOOSE) continue;
            sum += swarm.x[i];
            n++;
        }
        return sum / Math.max(1, n);
    }

    @Test
    void loweredShieldsStreamIntoTheEmittersAndComeBackOutOfThem() {
        ShieldCloudComponent.Emitter em = new ShieldCloudComponent.Emitter();
        em.spec = EmitterSpec.S;
        em.x = 4f;
        em.y = 1f;
        cloud.emitters.put(new com.badlogic.ashley.core.Entity(), em);
        run(0.5f);
        cloud.stowed = 300f;
        run(0.1f);
        assertEquals(0, swarm.count(ShieldParticles.FREE), "all on their way in");
        run(3f);
        assertEquals(200, swarm.count(ShieldParticles.STOWED), "and in");
        cloud.stowed = 0f;
        swarm.update(cloud, DT);
        assertEquals(0, swarm.count(ShieldParticles.STOWED));
        for (int i = 0; i < swarm.n; i++) {
            assertTrue(Vector2.dst(swarm.x[i], swarm.y[i], em.x, em.y) < 0.5f, "deployed from the emitter");
        }
        run(2f);
        for (int i = 0; i < swarm.n; i++) assertTrue(offCurve(i) < 4f, "back out round the curve: " + offCurve(i));
    }

    /** Puts {@code share} of the resting layer on the bow half of the curve (x > 0), the rest astern. */
    private void layBow(float share) {
        int bow = 0;
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) if (cloud.perimeter.x[k] > 0f) bow++;
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) {
            boolean ahead = cloud.perimeter.x[k] > 0f;
            cloud.line[k] = ahead ? share / bow : (1f - share) / (ShieldPerimeter.SAMPLES - bow);
        }
    }

    private int ahead(byte state) {
        int c = 0;
        for (int i = 0; i < swarm.n; i++) if (swarm.state[i] == state && swarm.x[i] > 0f) c++;
        return c;
    }

    @Test
    void theFlecksLieAsTheRestingLayerDoes() {
        layBow(0.8f);
        run(2f);
        int free = swarm.count(ShieldParticles.FREE);
        assertEquals(0.8f, ahead(ShieldParticles.FREE) / (float) free, 0.08f, "four in five ahead, where the layer is");
        layBow(0.5f);
        run(3f);
        assertEquals(0.5f, ahead(ShieldParticles.FREE) / (float) swarm.count(ShieldParticles.FREE), 0.08f, "and they follow it as it moves");
    }

    @Test
    void whatGoesAdriftLeavesFromWhereNoEmitterHoldsTheCurve() {
        run(0.5f);
        // The stern's emitter has died: nothing holds the curve astern
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) cloud.anchored[k] = cloud.perimeter.x[k] > 0f ? 1f : 0f;
        cloud.adrift = 60f;
        run(DT);
        int loose = swarm.count(ShieldParticles.LOOSE), astern = 0;
        for (int i = 0; i < swarm.n; i++) {
            // In space, along the world's axes: the ship lies at heading 0, so its own
            if (swarm.state[i] == ShieldParticles.LOOSE && swarm.x[i] < 0f) astern++;
        }
        assertEquals(40, loose, 2, "one fleck per 1.5 kg adrift");
        assertTrue(astern > 0.9f * loose, "from astern: " + astern + " of " + loose);
    }

    @Test
    void theBubbleIsAThinLineOverASoftGlow() {
        assertTrue(ShieldParticles.bandAlpha(0f) > 0.9f, "bright at its middle");
        assertTrue(ShieldParticles.bandAlpha(0.3f) < 0.3f, "faint off it");
        assertTrue(ShieldParticles.bandAlpha(1f) < 0.01f, "nothing at its edge");
    }

    /** How much a closed curve's radius from (cx, cy) jumps from one sample to the next, on average, meters. */
    private static float roughness(float[] x, float[] y, float cx, float cy) {
        int n = x.length;
        float sum = 0f;
        for (int k = 0; k < n; k++) {
            int a = (k - 1 + n) % n, b = (k + 1) % n;
            float r = Vector2.dst(x[k], y[k], cx, cy);
            // Second difference: a straight run or an even curve scores nothing, a step scores its height
            sum += Math.abs(Vector2.dst(x[a], y[a], cx, cy) - 2f * r + Vector2.dst(x[b], y[b], cx, cy));
        }
        return sum / n;
    }

    @Test
    void theBubblesOutlineIsSmoothedButStaysOnTheShieldsCurve() {
                ShipGridComponent grid = new ShipGridComponent(12, 5, 2f, -12f, -5f);
        // Every other tile along the top and bottom rows left out: a crenellated hull
        for (int x = 0; x < 12; x++) for (int y = 0; y < 5; y++) {
            boolean notch = (y == 0 || y == 4) && x % 2 == 1;
            if (!notch) grid.getTile(x, y).floorType = com.ringsofiron.interiorgrid.Tile.FloorType.FLOOR;
        }
        ShieldPerimeter per = ShieldPerimeter.fit(grid);
        int n = ShieldPerimeter.SAMPLES;
        float[] x = new float[n], y = new float[n], nx = new float[n], ny = new float[n];
        ShieldParticles.smoothCurve(per, x, y, nx, ny);
        assertTrue(roughness(x, y, per.cx, per.cy) < 0.6f * roughness(per.x, per.y, per.cx, per.cy),
                "smoother: " + roughness(x, y, per.cx, per.cy) + " vs " + roughness(per.x, per.y, per.cx, per.cy));
        for (int k = 0; k < n; k++) {
            float r = Vector2.dst(per.x[k], per.y[k], per.cx, per.cy), r2 = Vector2.dst(x[k], y[k], per.cx, per.cy);
            assertTrue(r2 >= r - ShieldParticles.BUBBLE_INSET - 1e-3f, "never far inside the shield's curve at " + k);
            assertEquals(1f, Vector2.len(nx[k], ny[k]), 1e-3f);
            // Outward: away from the center
            assertTrue((x[k] - per.cx) * nx[k] + (y[k] - per.cy) * ny[k] > 0f, "normal out at " + k);
        }
    }

    @Test
    void theFlecksAreSoftAndGoneZoomedFarOut() {
        assertEquals(1f, ShieldParticles.dotAlpha(0f), 1e-6f);
        assertTrue(ShieldParticles.dotAlpha(0.5f) > 0.2f && ShieldParticles.dotAlpha(0.5f) < 0.8f, "a gradient, not a solid disc");
        assertEquals(0f, ShieldParticles.dotAlpha(1f), 1e-6f);
        assertEquals(1f, ShieldParticles.zoomFade(0.05f), 1e-6f, "close in: all there");
        assertEquals(0f, ShieldParticles.zoomFade(1f), 1e-6f, "far out: gone");
        float mid = ShieldParticles.zoomFade(0.5f * (ShieldParticles.FADE_NEAR + ShieldParticles.FADE_FAR));
        assertEquals(0.5f, mid, 1e-3f, "fading between");
    }

    @Test
    void bigHitsShowBrighterThanSmallOnes() {
        assertTrue(ShieldParticles.strength(1.8e5f) < 0.5f);
        assertEquals(1f, ShieldParticles.strength(1.8e8f), 1e-6f);
        assertEquals(0.2f, ShieldParticles.strength(10f), 1e-6f);
    }
}
