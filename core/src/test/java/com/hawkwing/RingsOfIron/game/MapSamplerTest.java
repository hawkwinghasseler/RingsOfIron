package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The orbit map's sampling: fine where the map looks, coarse elsewhere, and precise far from the body. */
class MapSamplerTest {
    /** About the save's orbit: 193,629 km up around Earth. */
    static final double R = 2.0e8;

    private static MapSampler.Curve circle(double phase) {
        return (u, xy) -> {
            xy[0] = R * Math.cos(u + phase);
            xy[1] = R * Math.sin(u + phase);
        };
    }

    @Test
    void withNoViewItIsTheEvenSamplesAboutTheBody() {
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        MapSampler s = new MapSampler();
        s.setView(path);
        FloatArray out = new FloatArray();
        s.sample(circle(0), 0, 2 * Math.PI, PlayerOrbitPath.SEGMENTS, out);
        assertEquals(2 * (PlayerOrbitPath.SEGMENTS + 1), out.size);
        assertEquals(0, path.originX);
        assertEquals(R, out.get(0), 1);
    }

    @Test
    void zoomedInTheLineStaysOnTheOrbitHoweverTheSamplesFall() {
        for (double mpp : new double[]{450, 20, 1}) {
            // The ship's point on the orbit, at the middle of the view
            double angle = 0.7;
            OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
            path.viewScale = mpp;
            path.viewX = R * Math.cos(angle);
            path.viewY = R * Math.sin(angle);
            path.viewReach = 1100;
            MapSampler s = new MapSampler();
            s.setView(path);
            // A near-circular orbit's periapsis wanders from frame to frame: the samples start anywhere
            for (double phase : new double[]{0, 0.013, 1.9, -2.4}) {
                FloatArray out = new FloatArray();
                s.sample(circle(phase), 0, 2 * Math.PI, PlayerOrbitPath.SEGMENTS, out);
                int near = 0;
                for (int i = 2; i + 1 < out.size; i += 2) {
                    double x0 = path.originX + out.get(i - 2), y0 = path.originY + out.get(i - 1);
                    double x1 = path.originX + out.get(i), y1 = path.originY + out.get(i + 1);
                    double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                    // Only what the map shows matters
                    if (distance(path.viewX, path.viewY, x0, y0, x1, y1) > path.viewReach * mpp) continue;
                    near++;
                    // The chord's middle is where it strays farthest inside the orbit; floats round to a
                    // few centimetres this close to the origin
                    double inside = R - Math.hypot(mx, my);
                    assertTrue(Math.abs(inside) <= MapSampler.TOLERANCE * mpp + 0.05,
                            "at " + mpp + " m/px a chord strays " + inside + " m, phase " + phase);
                }
                assertTrue(near > 0, "the orbit crosses the view");
                assertTrue(out.size < 2 * (PlayerOrbitPath.SEGMENTS + 1) + 2 * 200, "refined only near the view: " + out.size / 2);
            }
        }
    }

    /** From (px, py) to the nearest point of the segment (x0, y0)-(x1, y1). */
    private static double distance(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double t = Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - x0 - t * dx, py - y0 - t * dy);
    }
}
