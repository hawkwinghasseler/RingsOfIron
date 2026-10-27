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
    void whenAnEmitterDropsItsShareDriftsLooseAndComesBack() {
        run(0.5f);
        cloud.holding = 0.5f;
        run(0.1f);
        assertEquals(100, swarm.count(ShieldParticles.LOOSE), 1);
        cloud.holding = 1f;
        run(0.1f);
        assertEquals(0, swarm.count(ShieldParticles.LOOSE));
    }

    @Test
    void theBubbleIsAThinLineOverASoftGlow() {
        assertTrue(ShieldParticles.bandAlpha(0f) > 0.9f, "bright at its middle");
        assertTrue(ShieldParticles.bandAlpha(0.3f) < 0.3f, "faint off it");
        assertTrue(ShieldParticles.bandAlpha(1f) < 0.01f, "nothing at its edge");
    }

    @Test
    void bigHitsShowBrighterThanSmallOnes() {
        assertTrue(ShieldParticles.strength(1.8e5f) < 0.5f);
        assertEquals(1f, ShieldParticles.strength(1.8e8f), 1e-6f);
        assertEquals(0.2f, ShieldParticles.strength(10f), 1e-6f);
    }
}
