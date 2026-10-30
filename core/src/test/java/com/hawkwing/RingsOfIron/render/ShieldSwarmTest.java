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
class ShieldSwarmTest {
    private static final float DT = 1f / 60f;
    private ShieldCloudComponent cloud;
    private ShieldSwarm swarm;

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
        swarm = new ShieldSwarm(200, 1.5f);
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
        assertEquals(200, swarm.count(ShieldSwarm.FREE));
        // The curve's samples are a couple of meters apart; allow for that, the depth and the swing
        float slack = 0.5f * ShieldCloudComponent.DEPTH + ShieldSwarm.ORBIT + 1.5f;
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
        int packed = swarm.count(ShieldSwarm.PACKED);
        assertEquals(60, packed, 3, "about one fleck per 1.5 kg");
        Vector2 at = cloud.perimeter.pointAt(0f, new Vector2());
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] != ShieldSwarm.PACKED) continue;
            float d = Vector2.dst(swarm.x[i], swarm.y[i], at.x, at.y);
            assertTrue(d < EmitterSpec.S.width + ShieldCloudComponent.DEPTH, "packed fleck " + i + " is " + d + " m off the region");
        }
        cloud.regions.clear();
        run(0.1f);
        assertEquals(0, swarm.count(ShieldSwarm.PACKED), "they go back to orbit when it lets go");
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
        assertEquals(4, swarm.count(ShieldSwarm.LOST), "6 kg lost is four flecks");
        assertEquals(10, swarm.count(ShieldSwarm.THROWN), "15 kg knocked loose is ten");
        assertEquals(1, swarm.fresh.size, "and a flash for the light");
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] == ShieldSwarm.LOST) assertTrue(swarm.vx[i] < 0f, "thrown along the shot");
        }
        run(ShieldSwarm.LOST_FADE + 0.1f);
        assertEquals(0, swarm.count(ShieldSwarm.LOST));
        assertEquals(swarm.alive(cloud), swarm.n - swarm.count(ShieldSwarm.DEAD), "the lost are gone, the rest kept");
        run(6f * ShieldModel.REJOIN);
        assertEquals(0, swarm.count(ShieldSwarm.THROWN), "the kept ones rejoin");
        swarm.update(cloud, DT);
        assertEquals(0, swarm.fresh.size, "a hit flashes once");
    }

    @Test
    void massAdriftKeepsItsVelocityWhileTheShipFliesOn() {
        ShieldSwarm.Frame f = new ShieldSwarm.Frame();
        for (int i = 0; i < 30; i++) swarm.update(cloud, DT, f);
        cloud.adrift = 150f;
        swarm.update(cloud, DT, f);
        assertEquals(100, swarm.count(ShieldSwarm.LOOSE), "150 kg adrift is a hundred flecks");
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
        assertEquals(0, swarm.count(ShieldSwarm.LOOSE));
    }

    private float meanLooseX() {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < swarm.n; i++) {
            if (swarm.state[i] != ShieldSwarm.LOOSE) continue;
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
        assertEquals(0, swarm.count(ShieldSwarm.FREE), "all on their way in");
        run(3f);
        assertEquals(200, swarm.count(ShieldSwarm.STOWED), "and in");
        cloud.stowed = 0f;
        swarm.update(cloud, DT);
        assertEquals(0, swarm.count(ShieldSwarm.STOWED));
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
        int free = swarm.count(ShieldSwarm.FREE);
        assertEquals(0.8f, ahead(ShieldSwarm.FREE) / (float) free, 0.08f, "four in five ahead, where the layer is");
        layBow(0.5f);
        run(3f);
        assertEquals(0.5f, ahead(ShieldSwarm.FREE) / (float) swarm.count(ShieldSwarm.FREE), 0.08f, "and they follow it as it moves");
    }

    @Test
    void whatGoesAdriftLeavesFromWhereNoEmitterHoldsTheCurve() {
        run(0.5f);
        // The stern's emitter has died: nothing holds the curve astern
        for (int k = 0; k < ShieldPerimeter.SAMPLES; k++) cloud.anchored[k] = cloud.perimeter.x[k] > 0f ? 1f : 0f;
        cloud.adrift = 60f;
        run(DT);
        int loose = swarm.count(ShieldSwarm.LOOSE), astern = 0;
        for (int i = 0; i < swarm.n; i++) {
            // In space, along the world's axes: the ship lies at heading 0, so its own
            if (swarm.state[i] == ShieldSwarm.LOOSE && swarm.x[i] < 0f) astern++;
        }
        assertEquals(40, loose, 2, "one fleck per 1.5 kg adrift");
        assertTrue(astern > 0.9f * loose, "from astern: " + astern + " of " + loose);
    }

    @Test
    void bigHitsShowBrighterThanSmallOnes() {
        assertTrue(ShieldSwarm.strength(1.8e5f) < 0.5f);
        assertEquals(1f, ShieldSwarm.strength(1.8e8f), 1e-6f);
        assertEquals(0.2f, ShieldSwarm.strength(10f), 1e-6f);
    }
}
