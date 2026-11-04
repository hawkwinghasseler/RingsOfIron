package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * X3, the emergency card (section 8): a hazard-striped card, the cause at 22, then the way out.
 * Disabled, the ways out are the same guarded switches as C1's, abandon ship (P twice) and the tow
 * (B twice) with what it costs; armed, a switch's guard stands open, the switch lights amber and
 * the foot says what the second press does while a bar runs out its 3 s. Abandoning, how many are
 * in the pods and P to launch now. The captain's suit air short, its time and the air here. With
 * two of these, the second takes one line under the first.
 */
final class EmergencyCard extends NpInstrument {
    enum Mode { DISABLED, ABANDONING, SUIT_AIR }

    static final float W = 500f;
    private static final Color RIM = NpColors.BEZEL_SHADOW;
    static final float ARM_WINDOW = 3f;

    Mode mode = Mode.DISABLED;
    String title = "";
    /** Disabled: the switches' states, the tow's cost and why each can't be given now. */
    CtxDraw.Btn abandon = CtxDraw.Btn.IDLE, tow = CtxDraw.Btn.IDLE;
    boolean towOffered = true;
    String towCost = "", abandonKey = "P", towKey = "B";
    /** Seconds left on an armed order's window, 0 with none armed. */
    float abandonArmed, towArmed;
    String foot = "";
    Color footColor = NpColors.DIM;
    /** Abandoning and the suit air: their body line, and its key ("" for none). */
    String line = "", lineKey = "";
    /** A second state's one line ("" for none). */
    String second = "";

    EmergencyCard(NpKit kit) {
        super(kit);
        setSize(W, 112f);
    }

    float heightFor() {
        float h;
        switch (mode) {
            case DISABLED: h = 112f; break;
            case ABANDONING: h = 96f; break;
            default: h = 72f; break;
        }
        return h + (second.isEmpty() ? 0f : 22f);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        cv.fillRound(-2f, -2f, w + 4f, h + 4f, 3f, RIM, 1f);
        CtxDraw.stripes(cv, getStage() == null ? null : getStage().getCamera(), 0f, 0f, w, h, 8f, NpColors.ORANGE, CtxDraw.GUARD_DARK);
        cv.fill(6f, 6f, w - 12f, h - 12f, NpColors.GLASS);
        cv.text(20f, 30f, CtxDraw.fit(cv, title, w - 40f, 22f, true, 0.6f), 22f, true, NpColors.RED, 0.6f, NpCanvas.START);
        switch (mode) {
            case DISABLED: disabled(cv); break;
            default: body(cv); break;
        }
        if (!second.isEmpty()) {
            float yy = h - 14f;
            cv.line(14f, yy - 15f, w - 14f, yy - 15f, 1f, NpColors.GRATICULE);
            cv.text(20f, yy, CtxDraw.fit(cv, second, w - 40f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, NpColors.RED, 0.4f, NpCanvas.START);
        }
    }

    private void disabled(NpCanvas cv) {
        com.badlogic.gdx.graphics.Camera cam = getStage() == null ? null : getStage().getCamera();
        float ky = 44f;
        CtxDraw.capKey(cv, 20f, ky + 12f, abandonKey, 17f);
        CtxDraw.guarded(cv, cam, 48f, ky, 96f, 40f, "ABANDON", "SHIP", abandon, true, abandonArmed > 0f);
        if (towOffered) {
            CtxDraw.capKey(cv, 172f, ky + 12f, towKey, 17f);
            CtxDraw.guarded(cv, cam, 200f, ky, 96f, 40f, "CALL", "TOW", tow, true, towArmed > 0f);
            if (!towCost.isEmpty()) {
                CtxDraw.lab(cv, 314f, ky + 17f, "TOW COSTS");
                cv.text(314f, ky + 35f, CtxDraw.fit(cv, towCost, 166f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            }
        }
        float fy = 102f;
        if (!foot.isEmpty()) cv.text(20f, fy, CtxDraw.fit(cv, foot, 286f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, footColor, 0.3f, NpCanvas.START);
        float armed = Math.max(abandonArmed, towArmed);
        if (armed > 0f) {
            float share = Math.max(0f, Math.min(1f, armed / ARM_WINDOW));
            cv.fill(314f, fy - 8f, 166f, 6f, NpColors.TRACK);
            cv.fill(314f, fy - 8f, 166f * share, 6f, NpColors.AMBER);
        }
    }

    private void body(NpCanvas cv) {
        float yy = 56f, x = 20f;
        if (mode == Mode.ABANDONING && !lineKey.isEmpty()) {
            cv.text(x, yy, CtxDraw.fit(cv, line, W - 40f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            float kx = x + CtxDraw.capKey(cv, x, yy + 9f, lineKey, 17f) + 8f;
            cv.text(kx, yy + 22f, "LAUNCHES THE PODS NOW", CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            return;
        }
        cv.text(x, yy, CtxDraw.fit(cv, line, W - 40f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
    }
}
