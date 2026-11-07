package com.ringsofiron.uihud.variants.cassette.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Task-driven Cassette HUD's layout (framework rule 3, Variants.md): at 1280 x 800, at the largest
 * scale a 1080p window allows (1422 x 800), at 1080p's own 1920 x 1080 and at a 5:4 window, on every
 * post and in every situation the visibility rules allow, at the units' largest sizes, no two units
 * overlap, all stay on screen and above the key sill, and nothing but the crisis placard reaches into
 * the clear zone.
 */
class TaskLayoutTest {
    private static final float[][] SIZES = {{1280f, 800f}, {1422f, 800f}, {1920f, 1080f}, {1280f, 1024f}, {2048f, 1152f}};

    @Test
    void nothingOverlapsOnAnyPost() {
        int checked = 0;
        for (float[] size : SIZES) {
            for (int post = 0; post < 3; post++) {
                for (int mask = 0; mask < 1 << 9; mask++) {
                    TaskLayout l = layout(size, post, mask);
                    if (l == null) continue;
                    l.solve();
                    l.placeRack(262f);
                    check(l, "post " + post + " mask " + Integer.toBinaryString(mask) + " at " + (int) size[0] + "x" + (int) size[1]);
                    checked++;
                }
            }
        }
        assertTrue(checked > 1000, "checked " + checked);
    }

    /**
     * One situation: {@code post} 0 deck, 1 helm, 2 gunnery; each bit of {@code mask} one condition.
     * Returns null for a situation the visibility rules never make.
     */
    private static TaskLayout layout(float[] size, int post, int mask) {
        boolean docked = (mask & 1) != 0, rails = (mask & 2) != 0, combat = (mask & 4) != 0, crisis = (mask & 8) != 0;
        boolean abandon = (mask & 16) != 0, damage = (mask & 32) != 0, targeted = (mask & 64) != 0, order = (mask & 128) != 0;
        boolean band = (mask & 256) != 0;
        if (abandon && !crisis) return null;
        if (docked && rails) return null;
        TaskLayout l = new TaskLayout();
        l.width = size[0];
        l.height = size[1];
        l.visor = band || crisis;
        l.crs = crisis;
        l.crsH = abandon ? CrisisPlacard.H_ABANDON : CrisisPlacard.H_FULL;
        l.rack = true;
        boolean outside = post != 0;
        if (post == 0) {
            l.per = damage;
            l.hlm = !docked;
            l.prompt = order;
            l.promptW = 420f;
        }
        if (outside) {
            l.sch = damage;
            l.scope = !rails && (!docked || combat);
            l.tgt = targeted;
            l.tgtH = (rails ? TargetPlate.H_RAILS : TargetPlate.H_FULL) + TargetPlate.NAV_LINE;
        }
        if (post == 1) {
            l.flt = !rails;
            l.fltH = order ? FlightPod.H + FlightPod.HLM_H : FlightPod.H;
            l.wpn = combat && !docked && !rails;
            l.wpnCompact = true;
        }
        if (post == 2) {
            l.hlm = !docked;
            l.hlmAtGunnery = true;
            l.wpn = !docked && !rails;
            l.wpnCompact = false;
            // Eight mounts: the panel's tallest; the decoy and the shields in their pod beside it
            l.wpnBaseH = 42f + 8f;
            l.wpnMounts = 8;
            l.defence = l.wpn;
            l.defenceH = DefencePod.H_SHIELDS;
        }
        return l;
    }

    private static void check(TaskLayout l, String where) {
        TaskLayout.Rect[] all = l.all;
        String[] names = {"master", "visor", "rack", "per", "sch", "flt", "hlm", "scope", "tgt", "wpn", "sill", "crs", "prompt", "defence"};
        for (int i = 0; i < all.length; i++) {
            TaskLayout.Rect r = all[i];
            if (!r.on) continue;
            if (r.x < -0.01f || r.y < -0.01f || r.right() > l.width + 0.01f || r.bottom() > l.height + 0.01f) {
                fail(names[i] + " " + r + " leaves the " + l.width + "x" + l.height + " screen, " + where);
            }
            if (r != l.sillR && r.bottom() > l.height - TaskLayout.SILL + 0.01f) fail(names[i] + " " + r + " runs under the sill, " + where);
            if (r != l.crsR && l.inClearZone(r)) fail(names[i] + " " + r + " reaches into the clear zone, " + where);
            if (r.h <= 0f || r.w <= 0f) fail(names[i] + " " + r + " has no size, " + where);
            for (int j = i + 1; j < all.length; j++) {
                if (r.overlaps(all[j])) fail(names[i] + " " + r + " overlaps " + names[j] + " " + all[j] + ", " + where);
            }
        }
    }

    @Test
    void strandedAndAbandoningStayAboveTheClearZone() {
        // Spec CRS: those two are a strip that keeps out of the clear zone, since the ship can still
        // move; only the full placard of a disabled ship may hang into it
        for (float[] size : SIZES) {
            for (int post = 0; post < 3; post++) {
                for (float h : new float[]{CrisisPlacard.H_STRANDED, CrisisPlacard.H_ABANDON}) {
                    TaskLayout l = layout(size, post, 8);
                    l.crsH = h;
                    l.solve();
                    l.placeRack(262f);
                    assertFalse(l.inClearZone(l.crsR), "a " + h + " strip " + l.crsR + " in the clear zone at " + (int) size[0] + "x" + (int) size[1]);
                    check(l, "post " + post + ", a " + h + " strip, at " + (int) size[0] + "x" + (int) size[1]);
                }
            }
        }
    }

    @Test
    void theConsolePanelStandsBesideItsConsoleClearOfEveryUnit() {
        // Spec CON: on deck, by the console the captain stands at, which the deck view keeps about the
        // middle of the screen; as tall as life support's with five rooms and both tanks
        int checked = 0;
        for (float[] size : SIZES) {
            for (int mask = 0; mask < 1 << 9; mask++) {
                TaskLayout l = layout(size, 0, mask);
                if (l == null) continue;
                // The prompt goes while a console is open
                l.prompt = false;
                l.solve();
                l.placeRack(262f);
                for (float dx = -120f; dx <= 120f; dx += 40f) {
                    for (float dy = -120f; dy <= 120f; dy += 40f) {
                        float cx = size[0] / 2f + dx, cy = size[1] / 2f + dy;
                        for (float h : new float[]{140f, 200f, 260f}) {
                            l.placeConsole(true, cx, cy, ConsolePanel.W, h, null);
                            TaskLayout.Rect r = l.conR;
                            String where = h + " tall at (" + cx + ", " + cy + "), mask " + Integer.toBinaryString(mask) + " at " + (int) size[0] + "x" + (int) size[1];
                            assertTrue(r.on, where);
                            assertTrue(r.x >= 0f && r.right() <= l.width && r.y >= 0f, r + " leaves the screen, " + where);
                            assertTrue(r.bottom() <= l.height - TaskLayout.SILL, r + " runs under the sill, " + where);
                            assertTrue(r.x >= cx + TaskLayout.CON_LEAD - 0.01f || r.right() <= cx - TaskLayout.CON_LEAD + 0.01f,
                                    r + " stands over its console, " + where);
                            for (TaskLayout.Rect o : l.all) assertFalse(r.overlaps(o), r + " overlaps " + o + ", " + where);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10000, "checked " + checked);
        TaskLayout l = layout(SIZES[0], 0, 0);
        l.placeConsole(false, 0f, 0f, 0f, 0f, null);
        assertFalse(l.conR.on);
    }

    @Test
    void theConsolePanelKeepsOffItsShipWhereThereIsRoom() {
        // Spec CON: by its console, but not over the rooms round it. On deck the starter corvette is
        // about 360 x 120 units, a bigger ship 640 x 220, with the captain's console anywhere on it
        int checked = 0, offDeck = 0;
        TaskLayout.Rect hull = new TaskLayout.Rect();
        for (float[] size : SIZES) {
            for (int mask = 0; mask < 1 << 9; mask++) {
                TaskLayout l = layout(size, 0, mask);
                if (l == null) continue;
                l.prompt = false;
                l.solve();
                l.placeRack(262f);
                float cx = size[0] / 2f, cy = size[1] / 2f;
                for (float[] deck : new float[][]{{360f, 120f}, {640f, 220f}}) {
                    for (float fx = 0.1f; fx < 1f; fx += 0.2f) {
                        for (float fy : new float[]{0.2f, 0.8f}) {
                            hull.set(cx - fx * deck[0], cy - fy * deck[1], deck[0], deck[1]);
                            for (float h : new float[]{140f, 260f}) {
                                l.placeConsole(true, cx, cy, ConsolePanel.W, h, hull);
                                TaskLayout.Rect r = l.conR;
                                String where = h + " tall on a deck " + hull + ", mask " + Integer.toBinaryString(mask) + " at " + (int) size[0] + "x" + (int) size[1];
                                assertTrue(r.on, where);
                                assertTrue(r.x >= 0f && r.right() <= l.width && r.y >= 0f, r + " leaves the screen, " + where);
                                assertTrue(r.bottom() <= l.height - TaskLayout.SILL, r + " runs under the sill, " + where);
                                for (TaskLayout.Rect o : l.all) assertFalse(r.overlaps(o), r + " overlaps " + o + ", " + where);
                                if (r.overlaps(hull)) {
                                    // No room off the deck: beside the console, never over it
                                    assertTrue(r.x >= cx + TaskLayout.CON_LEAD - 0.01f || r.right() <= cx - TaskLayout.CON_LEAD + 0.01f,
                                            r + " stands over its console, " + where);
                                } else {
                                    offDeck++;
                                }
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(offDeck > checked * 3 / 4, offDeck + " of " + checked + " off the deck");
        // Docked, with nothing else about the middle, the corvette always leaves it room under the deck
        for (float[] size : SIZES) {
            TaskLayout l = layout(size, 0, 1);
            l.solve();
            l.placeRack(262f);
            float cx = size[0] / 2f, cy = size[1] / 2f;
            hull.set(cx - 180f, cy - 60f, 360f, 120f);
            l.placeConsole(true, cx, cy, ConsolePanel.W, 260f, hull);
            assertEquals(hull.bottom() + TaskLayout.HULL_GAP, l.conR.y, 0.01f, l.conR + " at " + (int) size[0] + "x" + (int) size[1]);
            assertEquals(cx, l.conR.x + l.conR.w / 2f, 0.01f, l.conR + " centred under its console");
        }
    }

    @Test
    void theGunneryPanelKeepsTheStarterShipsRows() {
        // The corvette's five mounts (coilgun, two autocannons, harpoons, laser), at 1280 x 800
        TaskLayout l = layout(SIZES[0], 2, 4 | 64);
        l.wpnMounts = 5;
        l.solve();
        assertEquals(5, l.wpnRows);
        assertFalse(l.inClearZone(l.wpnR));
        assertFalse(l.inClearZone(l.defR));
    }

    @Test
    void theTargetPlateRisesClearOfTheScope() {
        TaskLayout l = layout(SIZES[0], 1, 64 | 128);
        l.solve();
        assertTrue(l.tgtR.bottom() <= l.scopeR.y - TaskLayout.GAP + 0.01f, l.tgtR + " over " + l.scopeR);
        // and on a tall window it hangs at its own place
        l = layout(SIZES[2], 1, 64 | 128);
        l.solve();
        assertEquals(TaskLayout.TGT_Y, l.tgtR.y, 0.01f);
    }

    @Test
    void zonesKeepTheirEdgesOnAWideWindow() {
        TaskLayout l = layout(SIZES[2], 1, 4 | 32 | 64);
        l.solve();
        l.placeRack(100f);
        assertEquals(0f, l.master.x, 0.01f);
        assertEquals(0f, l.schR.x, 0.01f);
        assertEquals(1920f, l.scopeR.right(), 0.01f);
        assertEquals(1920f, l.rackR.right(), 0.01f);
        assertEquals(1920f, l.tgtR.right(), 0.01f);
        assertEquals(960f, l.fltR.x + l.fltR.w / 2f, 0.01f);
        assertEquals(1080f - TaskLayout.SILL, l.fltR.bottom(), 0.01f);
        // Nothing is stretched
        assertEquals(FlightPod.W, l.fltR.w, 0.01f);
    }
}
