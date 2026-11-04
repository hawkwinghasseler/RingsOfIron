package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * H5, the contact scope (section 7): 200 x 200, north up, contacts by allegiance, the Lock's
 * brackets and the Nav's ring, missiles as chevrons, off-scale contacts as ticks on the rim at their
 * bearing. − and + (24 each) set the range by hand until the situation changes. In cruise with
 * nothing inside 50 km it folds to a 48 x 48 key with the count of contacts beyond; a click on the
 * key opens it.
 */
final class ContactScope extends NpDu {
    static final float S = 200f, KEY = 48f;
    /** The steps, km: the game's scope ranges with 5 km added, for fights out to the coilgun's reach. */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};

    final List<NpInstruments.Blip> blips = new ArrayList<>();
    float heading;
    int auto = 4;
    /** The step picked by hand, or -1 for automatic. */
    int override = -1;
    boolean folded;
    /** Opened from its folded key by hand, until the situation changes. */
    boolean opened;
    int beyond;

    ContactScope(NpKit kit) {
        super(kit);
        bezel = 8f;
        setSize(S, S);
    }

    void fold(boolean f) {
        folded = f;
        setSize(f ? KEY : S, f ? KEY : S);
    }

    int step() {
        return override >= 0 ? override : auto;
    }

    float rangeMetres() {
        return STEPS[step()] * 1000f;
    }

    @Override
    protected void paint(NpCanvas cv) {
        if (!folded) {
            super.paint(cv);
            return;
        }
        NpHardware.bezel(cv, 0f, 0f, KEY, KEY, 4f);
        cv.fill(5f, 5f, 38f, 38f, NpColors.GLASS);
        cv.ring(24f, 24f, 15f, 1.2f, NpColors.GRATICULE);
        cv.line(9f, 24f, 39f, 24f, 1f, NpColors.GRATICULE);
        cv.line(24f, 9f, 24f, 39f, 1f, NpColors.GRATICULE);
        cv.text(24f, 29.5f, Integer.toString(beyond), CtxDraw.V, true, NpColors.WHITE, 0f, NpCanvas.MIDDLE);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float cx = gx + gw / 2f, cy = gy + gh / 2f - 4f, r = 78f;
        NpInstruments.scope(cv, cx, cy, r, heading, blips, null);
        float by = gy + gh - 28f;
        CtxDraw.button(cv, gx + 4f, by, 24f, 24f, "−", CtxDraw.V);
        CtxDraw.button(cv, gx + 32f, by, 24f, 24f, "+", CtxDraw.V);
        float km = STEPS[step()];
        String v = km < 1f ? "0.5" : NpFormat.thousands((long) km);
        CtxDraw.valEnd(cv, gx + gw - 6f, gy + gh - 10f, v, "KM", CtxDraw.B, override >= 0 ? NpColors.CYAN : NpColors.WHITE);
    }

    @Override
    protected int[] keyAt(float x, float y) {
        if (folded) return x >= 0f && x <= KEY && y >= 0f && y <= KEY ? new int[] {TOP, 0} : null;
        float gx = glassX(), gy = glassY(), gh = glassH(), by = gy + gh - 28f;
        if (y < by || y > by + 24f) return null;
        if (x >= gx + 4f && x <= gx + 28f) return new int[] {BOTTOM, 0};
        if (x >= gx + 32f && x <= gx + 56f) return new int[] {BOTTOM, 1};
        return null;
    }

    @Override
    protected void onKey(int edge, int index) {
        if (folded) {
            opened = true;
            return;
        }
        int s = step();
        // − steps out to a longer range, + in to a shorter one
        override = Math.max(0, Math.min(STEPS.length - 1, index == 0 ? s + 1 : s - 1));
    }
}
