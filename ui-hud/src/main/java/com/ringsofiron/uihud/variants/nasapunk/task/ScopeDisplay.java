package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * SNS, the scope in Z7's corner (task spec section 6): contacts around the ship, north up, at
 * their offsets as fractions of the range, past it as ticks on the rim. The range is automatic;
 * the − and + caps either side of its figure override it until the post changes (− steps out, +
 * in, as the game's scope has its buttons today). The count of hostiles sits under the scope.
 */
final class ScopeDisplay extends NpDu {
    /** The steps, km: 5 is the spec's new one, for fights out to the coilgun's reach. */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    private static final Color CAP = NpColors.rgb(0x0E141A);

    final List<NpInstruments.Blip> blips = new ArrayList<>();
    float heading;
    int auto = 2;
    /** The step picked by hand, or -1 for automatic. */
    int override = -1;
    String hostiles;

    /** Its size, units (square). */
    static final float S = 176f;

    ScopeDisplay(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "SCOPE";
        setSize(S, S);
    }

    int step() {
        return override >= 0 ? override : auto;
    }

    float rangeMetres() {
        return STEPS[step()] * 1000f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        // a contact past the range is a tick reaching 6 past the rim: due south it still ends well
        // clear of the count of hostiles under the scope, so it can't read as part of it
        float cx = gx + gw / 2f, cy = gy + 57f, r = 50f, ky = gy + gh - 24f;
        NpInstruments.scope(cv, cx, cy, r, heading, blips, null);
        if (hostiles != null) cv.text(gx + 6f, gy + 129f, hostiles, 12f, true, NpColors.RED, 0.3f, NpCanvas.START);
        cap(cv, gx + 4f, ky, "−");
        cap(cv, gx + gw - 26f, ky, "+");
        float km = STEPS[step()];
        String label = (km < 1f ? "0.5" : NpFormat.thousands((long) km)) + " KM";
        cv.text(cx, ky + 16f, label, 12f, false, override >= 0 ? NpColors.CYAN : NpColors.DIM, 0.3f, NpCanvas.MIDDLE);
    }

    private void cap(NpCanvas cv, float x, float y, String k) {
        cv.fill(x, y, 22f, 22f, CAP);
        cv.stroke(x, y, 22f, 22f, 1f, NpColors.KEY_EDGE);
        cv.text(x + 11f, y + 16.5f, k, 15f, true, NpColors.WHITE, 0f, NpCanvas.MIDDLE);
    }

    @Override
    protected int[] keyAt(float x, float y) {
        float gx = glassX(), gy = glassY(), gw = glassW(), gh = glassH(), ky = gy + gh - 24f;
        if (y < ky || y > ky + 22f) return null;
        if (x >= gx + 4f && x <= gx + 26f) return new int[] {BOTTOM, 0};
        if (x >= gx + gw - 26f && x <= gx + gw - 4f) return new int[] {BOTTOM, 1};
        return null;
    }

    @Override
    protected void onKey(int edge, int index) {
        int s = step();
        // − steps out to a longer range, + in to a shorter one
        override = Math.max(0, Math.min(STEPS.length - 1, index == 0 ? s + 1 : s - 1));
    }
}
