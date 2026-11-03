package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * X2, the boarding panel (section 8): the other ship and how it is held (latched, or by EVA), its
 * state on a row of its own, then the two ways on as buttons 26 tall: K opens the boarding window,
 * where the orders are, and while latched U casts off. The party, salvage and cargo rows wait on
 * the game saying how a boarding stands.
 */
final class BoardingPanel extends NpDu {
    static final float W = 308f, P = 17f;

    String ship = "", held = "", stateHead = "", state = "";
    Color stateHeadColor = NpColors.WHITE;
    /** The keys: boarding, and cast off ("" while not latched). */
    String boardKey = "K", castOffKey = "";

    BoardingPanel(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 98f);
    }

    float heightFor() {
        // the name's row, the state's, the buttons 8 below its baseline, and 6 under them
        return 12f + 16f + P + 8f + 26f + 6f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, M = L + 58f, R = gx + gw - 8f, yy = gy + 16f;
        CtxDraw.lab(cv, L, yy, "BOARD");
        float x = M + cv.text(M, yy, CtxDraw.fit(cv, ship, 150f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START) + 4f;
        if (!held.isEmpty()) {
            x += cv.text(x, yy, "·", CtxDraw.B, true, NpColors.DIM, 0f, NpCanvas.START) + 4f;
            cv.text(x, yy, held, CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        yy += P;
        if (!state.isEmpty() || !stateHead.isEmpty()) {
            float sx = M;
            if (!stateHead.isEmpty()) sx += cv.text(M, yy, stateHead, CtxDraw.B, true, stateHeadColor, 0.3f, NpCanvas.START) + 4f;
            cv.text(sx, yy, CtxDraw.fit(cv, state, R - sx, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        float by = yy + 8f;
        CtxDraw.keyButton(cv, L - 2f, by, 136f, boardKey, "BOARDING");
        if (!castOffKey.isEmpty()) CtxDraw.keyButton(cv, gx + gw - 6f - 136f, by, 136f, castOffKey, "CAST OFF");
    }
}
