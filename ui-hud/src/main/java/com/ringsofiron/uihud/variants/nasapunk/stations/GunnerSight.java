package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * The gunner sight (section 8.4), seated at a gun: a deliberately narrow view, the cluster's width
 * and two rows tall, standing where the cluster stands. The gun: its weapon, state, rounds where
 * they are limited, and time to ready. The target: name, range, time of flight and the gun's chance
 * to hit. The sight itself, the arc and the lead, is the game's own in the world.
 */
final class GunnerSight extends NpDu {
    static final float W = StationsLayout.CLUSTER_W, H = 88f;

    String weapon = "", state = "", ammo = "", ready = "";
    BoxState stateBox = BoxState.NORMAL;
    boolean locked;
    String target = "", rng = "", tof = "", hit = "";
    NpSymbols.Kind targetKind = NpSymbols.Kind.HOSTILE;
    /** No reactor running: the sight goes dark with the seat (section 5.1). */
    boolean noPower;

    GunnerSight(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "GUN";
        setSize(W, H);
    }

    void title(String t) {
        title = t;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        if (noPower) {
            StDraw.dead(cv, gx, gy, gw, gh);
            return;
        }
        float L = gx + 10f, R = gx + gw - 10f, C0 = L + 70f;
        float b1 = gy + gh / 2f - 8f, b2 = gy + gh - 9f;
        cv.line(gx + 4f, gy + gh / 2f, gx + gw - 4f, gy + gh / 2f, 1f, NpColors.GRATICULE);
        StDraw.lab(cv, L, b1, "GUN");
        float x = C0 + StDraw.body(cv, C0, b1, StDraw.fitHead(cv, weapon, 120f, StDraw.B, true, 0.3f), NpColors.WHITE) + 12f;
        if (!state.isEmpty()) x += StDraw.box(cv, x, b1, state, stateBox) + 12f;
        if (!ammo.isEmpty()) x += StDraw.pair(cv, x, b1, "ROUNDS", ammo, NpColors.WHITE) + 12f;
        if (!ready.isEmpty()) StDraw.bodyEnd(cv, R, b1, ready, NpColors.WHITE);

        StDraw.lab(cv, L, b2, "TARGET");
        if (!locked) {
            StDraw.lab(cv, C0, b2, "NONE LOCKED");
            return;
        }
        Color c = NpSymbols.colorOf(targetKind);
        NpSymbols.shape(cv, targetKind, C0 + 5f, b2 - 4.5f, 5f, c, 1.5f, true, 0f);
        float right = (hit.isEmpty() ? 0f : cv.width(hit, 15f, true, 0f) + StDraw.labW(cv, "HIT") + 9f)
                + (tof.isEmpty() ? 0f : StDraw.pairW(cv, "TOF", tof) + 12f) + StDraw.pairW(cv, "RNG", rng) + 12f;
        String name = StDraw.fitName(cv, target, R - right - C0 - 24f, StDraw.B, true, 0.3f);
        StDraw.body(cv, C0 + 16f, b2, name, c);
        float rx = R - right;
        rx += StDraw.pair(cv, rx, b2, "RNG", rng, NpColors.WHITE) + 12f;
        if (!tof.isEmpty()) StDraw.pair(cv, rx, b2, "TOF", tof, NpColors.WHITE);
        if (!hit.isEmpty()) {
            cv.text(R, b2 + 0.5f, hit, 15f, true, NpColors.CYAN, 0f, NpCanvas.END);
            StDraw.labEnd(cv, R - cv.width(hit, 15f, true, 0f) - 5f, b2, "HIT");
        }
    }
}
