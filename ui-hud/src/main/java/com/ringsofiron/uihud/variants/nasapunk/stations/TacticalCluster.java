package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * The tactical cluster (section 8.3), 480 x 160 at the weapons console: six rows of fixed slots.
 * The target; a chip per gun in hand, by where it sits, with the crew member seated at it, its
 * state and its own chance to hit; point defense and the decoy; a chip per launcher with its
 * missiles, readiness and salvo; the shields on a hull with an emitter. At this seat guns and
 * launchers are YOURS and point defense AUTO, said once in the slot labels. The cluster never grows:
 * past two guns the chips take half a row each, the gunner as initials, and past what fits the last
 * row reads "+n ON WEAPONS".
 */
final class TacticalCluster extends NpDu {
    static final float W = StationsLayout.CLUSTER_W, H = StationsLayout.CLUSTER_H;
    private static final int ROWS = 6;
    private static final float PITCH = 23.7f;

    static final class Gun {
        String place = "", crew = "", state = "", hit = "";
        BoxState box = BoxState.NORMAL;
    }

    static final class Launcher {
        String place = "", left = "", ready = "", salvo = "";
    }

    boolean locked;
    /** The lock: its name, its relation ("HOSTILE"), its hull ("HULL 62%") where the sensors say, its state where it isn't fit. */
    String target = "", targetClass = "", targetHull = "", rng = "", closing = "", targetState = "";
    NpSymbols.Kind targetKind = NpSymbols.Kind.HOSTILE;
    final List<Gun> guns = new ArrayList<>();
    final List<Launcher> launchers = new ArrayList<>();
    boolean pd;
    String pdState = "", decoy = "";
    BoxState pdBox = BoxState.NORMAL;
    boolean emitter;
    String shieldMode = "", cloud = "", trim = "";
    BoxState shieldBox = BoxState.NORMAL;
    /** The weapons console has no power, or the ship none at all: the NO POWER face (section 5.1). */
    boolean noPower;

    TacticalCluster(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "TAC";
        setSize(W, H);
    }

    private static float row(float gy, int i) {
        return gy + 1f + i * PITCH;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        if (noPower) {
            StDraw.dead(cv, gx, gy, gw, gh);
            return;
        }
        for (int i = 1; i < ROWS; i++) cv.line(gx + 4f, row(gy, i), gx + gw - 4f, row(gy, i), 1f, NpColors.GRATICULE);
        float L = gx + 8f, R = gx + gw - 8f, C0 = L + 70f;
        int r = 0;
        target(cv, L, R, row(gy, r++) + 16.5f);

        // what the rows hold: the target, the point defense and decoy row and the shields are fixed;
        // the rest is one gun or launcher a row while they fit, else two
        int fixed = 2 + (emitter ? 1 : 0);
        int room = ROWS - fixed;
        boolean halfGuns = guns.size() > 2, halfLaunchers = false;
        int gunRows = halfGuns ? (guns.size() + 1) / 2 : guns.size();
        int launcherRows = launchers.size();
        if (gunRows + launcherRows > room) {
            halfLaunchers = true;
            launcherRows = (launchers.size() + 1) / 2;
        }
        int gunsShown = guns.size();
        if (gunRows + launcherRows > room) {
            gunRows = Math.max(0, room - launcherRows);
            gunsShown = Math.min(guns.size(), halfGuns ? gunRows * 2 - 1 : gunRows);
        }
        for (int i = 0; i < gunRows; i++) {
            float base = row(gy, r++) + 16.5f;
            if (i == 0) label(cv, L, base, "YOURS", NpColors.CYAN);
            if (!halfGuns) {
                if (i < gunsShown) gun(cv, C0, R, base, guns.get(i), false);
            } else {
                float mid = C0 + (R - C0) / 2f;
                int a = i * 2, b = a + 1;
                if (a < gunsShown) gun(cv, C0, mid - 8f, base, guns.get(a), true);
                if (b < gunsShown) gun(cv, mid + 4f, R, base, guns.get(b), true);
                else if (gunsShown < guns.size() && i == gunRows - 1) StDraw.labEnd(cv, R, base, "+" + (guns.size() - gunsShown) + " ON WEAPONS");
            }
        }

        float base = row(gy, r++) + 16.5f;
        if (pd) {
            label(cv, L, base, "AUTO", NpColors.GREEN);
            StDraw.body(cv, C0, base, "PD", NpColors.WHITE);
            StDraw.box(cv, C0 + 130f, base, pdState, pdBox);
        }
        StDraw.bodyEnd(cv, R, base, decoy, NpColors.WHITE);
        StDraw.labEnd(cv, R - StDraw.bodyW(cv, decoy) - 6f, base, "DECOY");

        for (int i = 0; i < launcherRows; i++) {
            base = row(gy, r++) + 16.5f;
            if (i == 0) label(cv, L, base, "YOURS", NpColors.CYAN);
            if (!halfLaunchers) {
                launcher(cv, C0, R, base, launchers.get(i), false);
            } else {
                float mid = C0 + (R - C0) / 2f;
                int a = i * 2, b = a + 1;
                if (a < launchers.size()) launcher(cv, C0, mid - 8f, base, launchers.get(a), true);
                if (b < launchers.size()) launcher(cv, mid + 4f, R, base, launchers.get(b), true);
            }
        }

        if (emitter && r < ROWS) {
            base = row(gy, r) + 16.5f;
            StDraw.body(cv, C0, base, "SHIELDS", NpColors.WHITE);
            float x = C0 + 130f;
            x += StDraw.box(cv, x, base, shieldMode, shieldBox) + 12f;
            x += StDraw.pair(cv, x, base, "CLOUD", cloud, NpColors.WHITE) + 12f;
            StDraw.pair(cv, x, base, "TRIM", trim, NpColors.CYAN);
        }
    }

    private static void label(NpCanvas cv, float x, float base, String t, Color c) {
        cv.text(x, base, t, 12f, true, c, 0.5f, NpCanvas.START);
    }

    /** The target: its symbol is its label; name and class, then range and closing at the right. */
    private void target(NpCanvas cv, float L, float R, float base) {
        if (!locked) {
            StDraw.lab(cv, L, base, "NO TARGET LOCKED");
            return;
        }
        Color c = NpSymbols.colorOf(targetKind);
        NpSymbols.shape(cv, targetKind, L + 5f, base - 4.5f, 5f, c, 1.5f, true, 0f);
        float rightW = StDraw.pairW(cv, "CLOSING", closing) + 12f + StDraw.pairW(cv, "RNG", rng);
        float x0 = R - rightW;
        float x = L + 16f;
        float room = x0 - x - 12f;
        String name = StDraw.fitName(cv, target, room, StDraw.B, true, 0.3f);
        x += StDraw.body(cv, x, base, name, c) + 10f;
        // after the name: its state where it isn't fit, else its relation, with the hull when both fit whole
        String sub = targetState;
        if (sub.isEmpty()) {
            String both = targetClass.isEmpty() ? targetHull : targetHull.isEmpty() ? targetClass : targetClass + " · " + targetHull;
            sub = cv.width(both, 12f, true, 0.3f) <= x0 - x - 10f ? both : targetClass;
        }
        if (!sub.isEmpty() && x < x0 - 30f) {
            Color sc = targetState.isEmpty() ? NpColors.DIM : NpColors.AMBER;
            cv.text(x, base, StDraw.fitHead(cv, sub, x0 - x - 10f, 12f, true, 0.3f), 12f, true, sc, 0.3f, NpCanvas.START);
        }
        float rx = x0 + StDraw.pair(cv, x0, base, "RNG", rng, NpColors.WHITE) + 12f;
        StDraw.pair(cv, rx, base, "CLOSING", closing, NpColors.WHITE);
    }

    /** A gun's chip between {@code x} and {@code end}: place, gunner, state, hit. */
    private static void gun(NpCanvas cv, float x, float end, float base, Gun g, boolean half) {
        float hitW = g.hit.isEmpty() ? 0f : cv.width(g.hit, 15f, true, 0f) + (half ? 4f : StDraw.labW(cv, "HIT") + 9f);
        if (!g.hit.isEmpty()) {
            cv.text(end, base + 0.5f, g.hit, 15f, true, NpColors.CYAN, 0f, NpCanvas.END);
            if (!half) StDraw.labEnd(cv, end - cv.width(g.hit, 15f, true, 0f) - 5f, base, "HIT");
        }
        if (half) {
            float pw = StDraw.body(cv, x, base, StDraw.fitName(cv, g.place, 64f, StDraw.B, true, 0.3f), NpColors.WHITE);
            float bx = x + Math.max(pw, 40f) + 6f;
            if (!g.crew.isEmpty()) bx += cv.text(bx, base, StDraw.initials(g.crew), 12f, true, NpColors.WHITE, 0.3f, NpCanvas.START) + 6f;
            String st = StDraw.fitHead(cv, g.state, end - hitW - bx - 12f, 12f, true, 0.5f);
            if (!st.isEmpty()) StDraw.box(cv, bx, base, st, g.box);
            return;
        }
        StDraw.body(cv, x, base, StDraw.fitName(cv, g.place, 74f, StDraw.B, true, 0.3f), NpColors.WHITE);
        if (!g.crew.isEmpty()) cv.text(x + 78f, base, StDraw.fitName(cv, g.crew, 48f, StDraw.B, false, 0f), StDraw.B, false, NpColors.WHITE, 0f, NpCanvas.START);
        String st = StDraw.fitHead(cv, g.state, end - hitW - (x + 130f) - 12f, 12f, true, 0.5f);
        if (!st.isEmpty()) StDraw.box(cv, x + 130f, base, st, g.box);
    }

    /** A launcher's chip: place, MSL and what is left of its magazine, ready, and SALVO at the end. */
    private static void launcher(NpCanvas cv, float x, float end, float base, Launcher l, boolean half) {
        StDraw.body(cv, x, base, StDraw.fitName(cv, l.place, half ? 56f : 74f, StDraw.B, true, 0.3f), NpColors.WHITE);
        float mx = x + (half ? 60f : 130f);
        mx += StDraw.lab(cv, mx, base, "MSL") + 6f;
        mx += cv.text(mx, base + 0.5f, l.left, half ? 13f : 15f, true, NpColors.WHITE, 0f, NpCanvas.START) + 8f;
        float sw = l.salvo.isEmpty() || half ? 0f : StDraw.boxW(cv, l.salvo);
        if (mx + StDraw.bodyW(cv, l.ready) < end - sw - 6f) StDraw.body(cv, mx, base, l.ready, NpColors.WHITE);
        if (sw > 0f) StDraw.box(cv, end - sw, base, l.salvo, BoxState.CAUTION);
    }
}
