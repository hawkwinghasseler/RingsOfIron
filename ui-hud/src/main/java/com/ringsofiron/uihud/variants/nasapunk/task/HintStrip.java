package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * HNT, the key strip (task spec section 6, Z9): full width along the bottom edge, key caps with
 * their verbs, centred; faded once the context has held for 10 s.
 */
final class HintStrip extends NpInstrument {
    private static final Color STRIP = new Color(5 / 255f, 7 / 255f, 10 / 255f, 0.88f);
    private static final Color RULE = NpColors.rgb(0x1E262E);
    private static final Color FADED = NpColors.rgb(0x4C5864);
    /** Keys and their verbs, as pairs. */
    final List<String[]> keys = new ArrayList<>();
    boolean faded;

    /** Its height, units: it runs the window's full width. */
    static final float H = 24f;

    HintStrip(NpKit kit) {
        super(kit);
        setSize(1280f, H);
    }

    /** A key's cap and the gap after it, or nothing for a line with no key. */
    private static float capW(NpCanvas cv, String key) {
        return key.isEmpty() ? 0f : cv.width(key, 12f, true, 0f) + 14f + 7f;
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        cv.fill(0f, 0f, w, h, STRIP);
        cv.fill(0f, 0f, w, 1f, RULE);
        if (keys.isEmpty()) return;
        // a key with no cap ("" for its key) is a line of its own, as the placing hint is
        float total = -26f;
        for (String[] k : keys) total += capW(cv, k[0]) + cv.width(k[1], 12f, true, 0.4f) + 26f;
        float kx = (w - total) / 2f;
        for (String[] k : keys) {
            float kw = capW(cv, k[0]);
            if (!k[0].isEmpty()) {
                NpHardware.softKey(cv, kx, 3f, kw - 7f, 18f, false);
                cv.text(kx + (kw - 7f) / 2f, 16.5f, k[0], 12f, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
            }
            float vw = cv.text(kx + kw, 16.5f, k[1], 12f, true, faded ? FADED : NpColors.DIM, 0.4f, NpCanvas.START);
            kx += kw + vw + 26f;
        }
    }
}
