package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * X1, the station panel (section 8), docked, where the lock card would be: the station and the
 * port, who holds it and the captain's standing there, then each service it offers with the key
 * that opens it (a service it doesn't offer is left out), and U to undock.
 */
final class StationPanel extends NpDu {
    static final float W = 308f, KEY_ROW = 24f;

    String name = "", owner = "", standing = "", refusal = "";
    /** The service rows: key ("" for a terminal tab with no key of its own) and words. */
    final List<String[]> services = new ArrayList<>();
    String undockKey = "U";

    StationPanel(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 260f);
    }

    float heightFor() {
        float head = 70f + (refusal.isEmpty() ? 0f : 19f);
        return 12f + head + services.size() * KEY_ROW + 10f + KEY_ROW + 6f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f;
        cv.text(L, gy + 21f, CtxDraw.fit(cv, name, R - L, CtxDraw.V, true, 0.5f), CtxDraw.V, true, NpColors.WHITE, 0.5f, NpCanvas.START);
        if (!owner.isEmpty()) cv.text(L, gy + 41f, CtxDraw.fit(cv, owner, R - L, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
        if (!standing.isEmpty()) {
            float x = L + CtxDraw.lab(cv, L, gy + 60f, "STANDING") + 8f;
            cv.text(x, gy + 60f, CtxDraw.fit(cv, standing, R - x, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
        float yy = gy + 79f;
        if (!refusal.isEmpty()) {
            cv.text(L, yy, CtxDraw.fit(cv, refusal, R - L, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.AMBER, 0.3f, NpCanvas.START);
            yy += 19f;
        }
        cv.line(gx + 4f, yy - 9f, gx + gw - 4f, yy - 9f, 1f, NpColors.GRATICULE);
        yy += 10f;
        for (String[] s : services) {
            keyRow(cv, L, R, yy, s[0], s[1], NpColors.WHITE);
            yy += KEY_ROW;
        }
        cv.line(gx + 4f, yy - 16f, gx + gw - 4f, yy - 16f, 1f, NpColors.GRATICULE);
        yy += 6f;
        keyRow(cv, L, R, yy, undockKey, "UNDOCK", NpColors.WHITE);
    }

    private static void keyRow(NpCanvas cv, float L, float R, float yy, String key, String words, Color c) {
        float x = L;
        if (key != null && !key.isEmpty()) x += CtxDraw.capKey(cv, L, yy - 13f, key, 17f) + 8f;
        else x += 30f;
        cv.text(x, yy, CtxDraw.fit(cv, words, R - x, CtxDraw.B, true, 0.4f), CtxDraw.B, true, c, 0.4f, NpCanvas.START);
    }
}
