package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.variants.nasapunk.task.TaskLayout.Part;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Task HUD's zones hold the spec's rules (functional-task.md section 5) at the design size and
 * at the largest UI scale, for each post with everything it can show at once: nothing overlaps,
 * everything is on screen and above the hint strip, and the centre ellipse stays clear.
 */
class TaskLayoutTest {
    /**
     * UI units: the design size (also any 16:10 window at the largest scale that fits it), 16:9 at
     * its largest scale (1920x1080 at 1.35x), 4K at 2x, and a 5:4 window at 1x.
     */
    private static final float[][] WINDOWS = {{1280f, 800f}, {1422f, 800f}, {1920f, 1080f}, {1280f, 1024f}};

    /** Four contract rows (the navigation target's with its second line) and three news slips. */
    private static final float FULL_OBJECTIVES = 3 * (26f + 6f) + (42f + 6f) + 3 * 24f;

    @Test
    void atTheHelmInAFight() {
        TaskLayout.Sizes s = sizes();
        s.flight = true;
        s.compactWeapons = true;
        check(EnumSet.of(Part.CLOCK, Part.LAMPS, Part.BAND, Part.OBJECTIVES, Part.TARGET, Part.SCOPE, Part.WEAPONS,
                Part.FLIGHT, Part.HELM, Part.HINTS), s);
    }

    @Test
    void atAGunneryPost() {
        TaskLayout.Sizes s = sizes();
        check(EnumSet.of(Part.CLOCK, Part.LAMPS, Part.BAND, Part.OBJECTIVES, Part.TARGET, Part.SCOPE, Part.WEAPONS,
                Part.HELM, Part.HINTS), s);
    }

    @Test
    void onDeck() {
        TaskLayout.Sizes s = sizes();
        check(EnumSet.of(Part.CLOCK, Part.LAMPS, Part.SUIT, Part.BAND, Part.OBJECTIVES, Part.HELM, Part.HINTS), s);
    }

    @Test
    void theTargetCardKeepsItsPlaceUnderOneOrTwoObjectives() {
        TaskLayout.Sizes s = sizes();
        s.objectives = 26f + 6f;
        float one = TaskLayout.box(Part.TARGET, 1280f, 800f, s)[1];
        s.objectives = 2 * (26f + 6f);
        assertTrue(one == TaskLayout.box(Part.TARGET, 1280f, 800f, s)[1]);
    }

    @Test
    void theCommonWidgetsStayPutAcrossPosts() {
        // CHR, ALR, OBJ, ANN and HNT keep their places through a change of post (section 5)
        TaskLayout.Sizes helm = sizes(), gun = sizes();
        helm.flight = true;
        helm.compactWeapons = true;
        for (Part p : EnumSet.of(Part.CLOCK, Part.LAMPS, Part.BAND, Part.OBJECTIVES, Part.HINTS)) {
            float[] a = TaskLayout.box(p, 1280f, 800f, helm), b = TaskLayout.box(p, 1280f, 800f, gun);
            for (int i = 0; i < 4; i++) assertTrue(a[i] == b[i], p.name());
        }
    }

    private static TaskLayout.Sizes sizes() {
        TaskLayout.Sizes s = new TaskLayout.Sizes();
        s.objectives = FULL_OBJECTIVES;
        return s;
    }

    private static void check(Set<Part> shown, TaskLayout.Sizes s) {
        for (float[] win : WINDOWS) {
            float w = win[0], h = win[1];
            String where = (int) w + "x" + (int) h + ": ";
            for (Part p : shown) {
                float[] b = TaskLayout.box(p, w, h, s);
                assertTrue(b[0] >= 0f && b[1] >= 0f && b[0] + b[2] <= w && b[1] + b[3] <= h, where + p + " is off screen");
                if (p != Part.HINTS) assertTrue(b[1] + b[3] <= h - HintStrip.H, where + p + " runs under the hint strip");
                assertFalse(inClearZone(b, w, h), where + p + " enters the clear zone");
                for (Part q : shown) {
                    if (q.ordinal() <= p.ordinal()) continue;
                    assertFalse(overlap(b, TaskLayout.box(q, w, h, s)), where + p + " overlaps " + q);
                }
            }
        }
    }

    private static boolean overlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    /** Whether box {@code b} reaches into the ellipse on the window's centre. */
    private static boolean inClearZone(float[] b, float w, float h) {
        float cx = w / 2f, cy = h / 2f, rx = TaskLayout.CLEAR_W / 2f, ry = TaskLayout.CLEAR_H / 2f;
        // scaled by the ellipse's axes it is the unit circle, and the box is still a box: take its
        // point nearest the centre
        float x0 = (b[0] - cx) / rx, x1 = (b[0] + b[2] - cx) / rx, y0 = (b[1] - cy) / ry, y1 = (b[1] + b[3] - cy) / ry;
        float dx = Math.max(x0, Math.min(0f, x1)), dy = Math.max(y0, Math.min(0f, y1));
        return dx * dx + dy * dy < 1f;
    }
}
