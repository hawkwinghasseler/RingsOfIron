package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Camera;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * C1, the orders menu (section 6): a mode control panel opened over the world, every order the
 * captain can give with its key and whether it runs, read across and down in the spec's order:
 * HELM, then AUTOPILOT and CREW, then SHIELDS and SHIP. The helm orders are interlocked lit keys
 * under their number caps; the rest carry their cap at their left, and abandon ship and the tow sit
 * under guards in the far corner. An order that can't be given now is drawn, not hidden, with a
 * barber-pole strip, and the foot gives each reason once with the keys it blocks. The keys give the
 * orders as they always have: the panel shows them, so the player learns what exists.
 */
final class OrdersPanel extends NpInstrument {
    static final float W = 620f, H = 250f, PITCH = 16f;
    /** The foot holds three reasons in the panel's 250 units; more grow it upwards, to six. */
    static final int REASONS = 3, MAX_REASONS = 6;
    private static final float L = 16f, C0 = 112f;
    private static final String[] HELM_1 = {"FLY", "MATCH", "HOLD", "APPROACH", "DOCK", "CANCEL"};

    /** The helm orders in key order (fly the burns, match, hold, approach, dock, cancel): keys and states. */
    final String[] helmKeys = {"1", "2", "3", "4", "5", "0"};
    final CtxDraw.Btn[] helm = new CtxDraw.Btn[6];
    /** The approach order's distance, its lower line. */
    String approach = "500 M";
    String autopilotKey = "6", autopilotState = "OFF";
    CtxDraw.Btn autopilot = CtxDraw.Btn.IDLE;
    /** The crew's toggles: suits at battle stations, everyone held in from EVA. */
    CtxDraw.Btn suits = CtxDraw.Btn.IDLE, eva = CtxDraw.Btn.IDLE;
    String shieldKey = "9", trimKey = "8", shieldState = "--", trim = "even";
    CtxDraw.Btn shields = CtxDraw.Btn.NA;
    boolean trimNa = true;
    String abandonKey = "P", towKey = "B", towCost = "";
    CtxDraw.Btn abandon = CtxDraw.Btn.IDLE, tow = CtxDraw.Btn.IDLE;
    /** A guard stands open while its order is armed. */
    boolean abandonOpen, towOpen;
    /** Each reason once, and the keys it blocks. */
    final List<String> reasons = new ArrayList<>(), blocked = new ArrayList<>();

    OrdersPanel(NpKit kit) {
        super(kit);
        Arrays.fill(helm, CtxDraw.Btn.IDLE);
        setSize(W, H);
    }

    /** Lists {@code why} once with {@code key} among the keys it blocks; nothing for an empty reason. */
    void reason(String why, String key) {
        if (why == null || why.isEmpty()) return;
        String k = key == null ? "" : key;
        int i = reasons.indexOf(why);
        if (i < 0) {
            reasons.add(why);
            blocked.add(k);
        } else if (!k.isEmpty()) {
            String b = blocked.get(i);
            blocked.set(i, b.isEmpty() ? k : b + " " + k);
        }
    }

    float heightFor() {
        int n = Math.max(REASONS, Math.min(MAX_REASONS, reasons.size()));
        return H + (n - REASONS) * PITCH;
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        Camera cam = getStage() == null ? null : getStage().getCamera();
        NpHardware.bezel(cv, 0f, 0f, w, h, 4f);
        NpHardware.fastener(cv, 7f, 7f);
        NpHardware.fastener(cv, w - 7f, 7f);
        NpHardware.fastener(cv, 7f, h - 7f);
        NpHardware.fastener(cv, w - 7f, h - 7f);
        // HELM: one order at a time (giving one releases the one running); CANCEL ends it
        String[] l2 = {"BURNS", "VELOCITY", "POSITION", approach, "AT NAV", ""};
        for (int i = 0; i < helm.length; i++) {
            float bx = C0 + i * 82f;
            String k = helmKeys[i];
            if (!k.isEmpty()) CtxDraw.capKey(cv, bx + 38f - CtxDraw.capW(cv, k) / 2f, 11f, k, 16f);
            CtxDraw.korry(cv, cam, bx, 31f, 76f, 40f, HELM_1[i], l2[i], helm[i], true);
        }
        legend(cv, L, 56f, "HELM");
        // AUTOPILOT and the CREW's two toggles
        float yy = 84f;
        legend(cv, L, yy + 24f, "AUTOPILOT");
        capped(cv, C0, yy, autopilotKey);
        CtxDraw.korry(cv, cam, C0 + 22f, yy, 76f, 40f, "TO NAV", autopilotState, autopilot, false);
        legend(cv, C0 + 116f, yy + 24f, "CREW");
        CtxDraw.korry(cv, cam, C0 + 168f, yy, 118f, 40f, "SUITS AT BATTLE", suits == CtxDraw.Btn.ON ? "ON" : "OFF", suits, false);
        CtxDraw.korry(cv, cam, C0 + 292f, yy, 126f, 40f, "HOLD IN FROM EVA", eva == CtxDraw.Btn.ON ? "ON" : "OFF", eva, false);
        // SHIELDS: raise or lower, the trim as a five-way selector; then the SHIP's guarded orders,
        // last and furthest from the helm keys the hand uses most
        yy = 136f;
        legend(cv, L, yy + 24f, "SHIELDS");
        capped(cv, C0, yy, shieldKey);
        CtxDraw.korry(cv, cam, C0 + 22f, yy, 56f, 40f, "RAISE", shieldState, shields, false);
        capped(cv, C0 + 86f, yy, trimKey);
        CtxDraw.trimSelector(cv, cam, C0 + 108f, yy, trim, trimNa);
        legend(cv, C0 + 222f, yy + 24f, "SHIP");
        capped(cv, C0 + 262f, yy, abandonKey);
        CtxDraw.guarded(cv, cam, C0 + 289f, yy, 76f, 40f, "ABANDON", "SHIP", abandon, true, abandonOpen);
        capped(cv, C0 + 378f, yy, towKey);
        CtxDraw.guarded(cv, cam, C0 + 405f, yy, 84f, 40f, "CALL TOW", towCost, tow, false, towOpen);
        // the foot: each reason once, with the keys it blocks
        float fy = 186f;
        cv.fill(12f, fy, w - 24f, h - fy - 10f, NpColors.GLASS);
        int n = Math.min(MAX_REASONS, reasons.size());
        for (int i = 0; i < n; i++) {
            float ly = fy + 16f + i * PITCH;
            CtxDraw.barber(cv, cam, 22f, ly - 7f, 14f);
            String keys = blocked.get(i);
            float kw = keys.isEmpty() ? 0f : cv.width(keys, CtxDraw.B, true, 0.6f) + 8f;
            String why = shorten(cv, reasons.get(i), w - 22f - 44f - kw);
            float tw = cv.text(44f, ly, why, CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            if (!keys.isEmpty()) cv.text(44f + tw + 8f, ly, keys, CtxDraw.B, true, NpColors.WHITE, 0.6f, NpCanvas.START);
        }
    }

    /**
     * {@code why} as the foot's line {@code room} wide: whole if it fits, else without the game's
     * bracketed hint at its end (the key that fixes it, which C1 shows anyway), else cut.
     */
    static String shorten(NpCanvas cv, String why, float room) {
        if (cv.width(why, CtxDraw.B, false, 0.3f) <= room) return why;
        int open = why.lastIndexOf(" (");
        String bare = open > 0 && why.endsWith(")") ? why.substring(0, open) : why;
        return CtxDraw.fit(cv, bare, room, CtxDraw.B, false, 0.3f);
    }

    private static void legend(NpCanvas cv, float x, float y, String t) {
        cv.text(x, y, t, CtxDraw.LB, true, NpColors.STENCIL, 1f, NpCanvas.START);
    }

    /** A key cap beside a control 40 tall; none for an order with no key. */
    private static void capped(NpCanvas cv, float x, float y, String k) {
        if (k != null && !k.isEmpty()) CtxDraw.capKey(cv, x, y + 12f, k, 17f);
    }
}
