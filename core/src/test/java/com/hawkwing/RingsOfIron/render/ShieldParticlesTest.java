package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.shields.ShieldPerimeter;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the shield is drawn: the bubble's smoothed outline, and the flecks fading out as the view zooms out. */
class ShieldParticlesTest {
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
    void theFlecksAreGoneZoomedFarOut() {
        assertEquals(1f, ShieldParticles.zoomFade(0.05f), 1e-6f, "close in: all there");
        assertEquals(0f, ShieldParticles.zoomFade(1f), 1e-6f, "far out: gone");
        float mid = ShieldParticles.zoomFade(0.5f * (ShieldParticles.FADE_NEAR + ShieldParticles.FADE_FAR));
        assertEquals(0.5f, mid, 1e-3f, "fading between");
    }
}
