package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.GameOverProvider.Choice;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpCdu;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;

/**
 * The ship lost or the career over (documentation/ui-overhaul/variations/nasapunk-shell, SH8): every
 * display on the deck shows its red X, the style's mark for a failed display, and CDU-1 carries the
 * one line that says what happened, then the ways on as prompts, each under its key: the latest
 * autosave (where the cursor sits), the quick save and the menu. The keys stay the game's; a click on
 * a prompt or its line-select key does what the key does, where the game lets a click do it.
 */
public final class LostCard {
    public static final float W = 468f;
    private static final float PITCH = 46f;
    private static final Color X_RED = NpColors.RED;
    private static final String[] LINES = new String[3];

    /** The unit as last laid out, local units, y down. */
    public float x, y, w, h;

    /**
     * Draws the card in a screen {@code sw} by {@code sh}, over the HUD's {@code panels} (UI units, y
     * up, as {@code HudVariant#panels} gives them), each struck through as a failed display.
     */
    public void paint(NpCanvas cv, float sw, float sh, GameOver g, Array<Rectangle> panels, Hits hits) {
        cv.fill(0f, 0f, sw, sh, NpColors.HALO, 0.55f);
        for (int i = 0; panels != null && i < panels.size; i++) {
            Rectangle r = panels.get(i);
            float px = r.x, py = sh - r.y - r.height;
            cv.fill(px, py, r.width, r.height, NpColors.GLASS, 0.92f);
            cv.line(px + 4f, py + 4f, px + r.width - 4f, py + r.height - 4f, 1.6f, X_RED);
            cv.line(px + r.width - 4f, py + 4f, px + 4f, py + r.height - 4f, 1.6f, X_RED);
        }
        hits.plate(0f, 0f, sw, sh);
        String title = g.title.isEmpty() ? "SHIP LOST" : Fmt.caps(g.title);
        int lines = Ui.wrap(cv, Fmt.caps(g.line), W - 2f * NpCdu.PAD_SIDE - 10f, NpCdu.DATA, true, NpCdu.DATA_TRACK, LINES.length, LINES);
        w = W;
        h = NpCdu.BEZEL + NpCdu.PAD_TOP + 34f + lines * 20f + 24f + g.choices.size * PITCH + NpCdu.PAD_BOTTOM + NpCdu.FOOT;
        x = Math.round((sw - w) / 2f);
        y = Math.round(Math.max(12f, (sh - h) / 2f));
        NpCdu.unit(cv, x, y, w, h, "CDU-1");
        float gx = x + NpCdu.GLASS_X, gw = w - 2f * NpCdu.GLASS_X, top = y + NpCdu.BEZEL;
        float tw = cv.width(title, NpCdu.TITLE, true, NpCdu.TITLE_TRACK) + 14f;
        cv.stroke(gx + gw / 2f - tw / 2f, top + 5f, tw, 22f, 1.5f, NpColors.RED);
        cv.text(gx + gw / 2f, top + 21f, title, NpCdu.TITLE, true, NpColors.RED, NpCdu.TITLE_TRACK, NpCanvas.MIDDLE);
        float lx = x + NpCdu.PAD_SIDE + 5f, ly = top + 34f + 22f;
        for (int i = 0; i < lines; i++) cv.text(lx, ly + i * 20f, LINES[i], NpCdu.DATA, true, NpColors.WHITE, NpCdu.DATA_TRACK, NpCanvas.START);
        float cy = ly + lines * 20f + 24f;
        int cursor = cursor(g);
        for (int i = 0; i < g.choices.size; i++) {
            Choice c = g.choices.get(i);
            float row = cy + i * PITCH;
            cv.text(lx, row, Fmt.caps(c.key), NpCdu.LABEL, true, NpColors.DIM, NpCdu.LABEL_TRACK, NpCanvas.START);
            String prompt = "<" + Fmt.caps(c.label);
            float pw = cv.width(prompt, NpCdu.DATA, true, NpCdu.DATA_TRACK);
            float base = row + 22f;
            cv.text(lx, base, prompt, NpCdu.DATA, true, c.run == null ? NpColors.STENCIL : NpColors.WHITE, NpCdu.DATA_TRACK, NpCanvas.START);
            float boxY = base - 16f;
            if (i == cursor) NpCdu.cursor(cv, lx - 6f, boxY - 2f, pw + 12f, 24f);
            float kx = x + NpCdu.LSK_X, ky = boxY + 2f;
            NpCdu.lsk(cv, kx, ky, false);
            if (c.run != null) {
                hits.add(lx - 6f, boxY - 2f, pw + 12f, 24f, c, i);
                hits.add(kx - 2f, ky - 2f, NpCdu.LSK_W + 4f, NpCdu.LSK_H + 4f, c, i);
            }
        }
    }

    /** The choice the cursor sits on: Enter's, the latest autosave, else the first a click can give. */
    static int cursor(GameOver g) {
        for (int i = 0; i < g.choices.size; i++) if ("Enter".equalsIgnoreCase(g.choices.get(i).key)) return i;
        for (int i = 0; i < g.choices.size; i++) if (g.choices.get(i).run != null) return i;
        return -1;
    }

    /** A click on a choice: what its key does. */
    public static boolean clicked(Object target) {
        if (!(target instanceof Choice)) return false;
        Choice c = (Choice) target;
        if (c.run != null) c.run.run();
        return true;
    }
}
