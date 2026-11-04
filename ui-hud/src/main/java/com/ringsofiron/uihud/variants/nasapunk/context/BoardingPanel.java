package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * X2, the boarding panel (section 8): the other ship and how it is held (latched, or by EVA), its
 * state on a row of its own, the party, the parts marked to strip, the cargo coming across and the
 * prisoners to bring aboard, each row only while it has something to say, then the two ways on as
 * buttons 26 tall: K opens the boarding window, where the orders are, and while latched U casts
 * off. Seven rows at most, 308 x up to 150: why no party can go takes a second line only when
 * there are no prisoners, which come only once a party has taken the ship.
 */
final class BoardingPanel extends NpDu {
    static final float W = 308f, P = 17f;
    /** Bezels, margins and the label column: the width a row's words have. */
    private static final float VALUE_W = W - 12f - 16f - 58f;

    final BoardingRows rows = new BoardingRows();
    /** The keys: boarding, and cast off ("" while not latched). */
    String boardKey = "K", castOffKey = "";
    private final String[] partyLines = {"", ""};
    private int partyCount;

    BoardingPanel(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 98f);
    }

    /** The rows read, the party's words split where they need two lines. */
    float heightFor() {
        wrapParty(kit.canvas());
        int n = 1 + (state() ? 1 : 0) + partyCount + (rows.salvage.isEmpty() ? 0 : 1) + (rows.cargo.isEmpty() ? 0 : 1)
                + (rows.prisoners ? 1 : 0);
        // the rows 17 apart from the first's baseline, the buttons 7 under the last, and 4 under them
        return 12f + 16f + P * (n - 1) + 7f + 26f + 4f;
    }

    private boolean state() {
        return !rows.stateHead.isEmpty() || !rows.state.isEmpty();
    }

    /** Why no party can go, at a word onto a second line when one won't hold it. */
    private void wrapParty(NpCanvas cv) {
        partyCount = 0;
        String s = rows.party;
        if (s.isEmpty()) return;
        if (!rows.partyWhy || cv.width(s, CtxDraw.B, true, 0.3f) <= VALUE_W) {
            partyLines[partyCount++] = s;
            return;
        }
        String first = s;
        for (int sp = first.lastIndexOf(' '); sp > 0 && cv.width(first, CtxDraw.B, true, 0.3f) > VALUE_W; sp = first.lastIndexOf(' ')) {
            first = first.substring(0, sp).trim();
        }
        partyLines[partyCount++] = first;
        String rest = s.substring(first.length()).trim();
        if (!rest.isEmpty()) partyLines[partyCount++] = rest;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, M = L + 58f, R = gx + gw - 8f, yy = gy + 16f;
        CtxDraw.lab(cv, L, yy, "BOARD");
        float x = M + cv.text(M, yy, CtxDraw.fit(cv, rows.ship, 150f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START) + 4f;
        if (!rows.held.isEmpty()) {
            x += cv.text(x, yy, "·", CtxDraw.B, true, NpColors.DIM, 0f, NpCanvas.START) + 4f;
            cv.text(x, yy, rows.held, CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        if (state()) {
            yy += P;
            float sx = M;
            if (!rows.stateHead.isEmpty()) sx += cv.text(M, yy, rows.stateHead, CtxDraw.B, true, rows.stateColor, 0.3f, NpCanvas.START) + 4f;
            cv.text(sx, yy, CtxDraw.fit(cv, rows.state, R - sx, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        for (int i = 0; i < partyCount; i++) {
            yy += P;
            if (i == 0) CtxDraw.lab(cv, L, yy, "PARTY");
            words(cv, M, yy, CtxDraw.fit(cv, partyLines[i], R - M, CtxDraw.B, true, 0.3f), rows.partyWhy ? NpColors.AMBER : NpColors.WHITE);
        }
        if (!rows.salvage.isEmpty()) {
            yy += P;
            CtxDraw.lab(cv, L, yy, "SALV");
            words(cv, M, yy, rows.salvage, NpColors.WHITE);
        }
        if (!rows.cargo.isEmpty()) {
            yy += P;
            CtxDraw.lab(cv, L, yy, "CARGO");
            words(cv, M, yy, rows.cargo, NpColors.WHITE);
        }
        if (rows.prisoners) {
            yy += P;
            words(cv, M, yy, "PRISONERS TO BRING ABOARD", NpColors.WHITE);
        }
        float by = yy + 7f;
        CtxDraw.keyButton(cv, L - 2f, by, 136f, boardKey, "BOARDING");
        if (!castOffKey.isEmpty()) CtxDraw.keyButton(cv, gx + gw - 6f - 136f, by, 136f, castOffKey, "CAST OFF");
    }

    /** {@code s} from {@code x}, its " · " dots dim between the words. */
    private static void words(NpCanvas cv, float x, float y, String s, Color c) {
        String[] parts = s.split(" · ", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) x += cv.text(x, y, "·", CtxDraw.B, true, NpColors.DIM, 0f, NpCanvas.START) + 4f;
            x += cv.text(x, y, parts[i], CtxDraw.B, true, c, 0.3f, NpCanvas.START) + 4f;
        }
    }
}
