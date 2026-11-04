package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * The status strip (section 7.7), 720 x 24 at the top of the column: the condition's lens at the
 * left, YELLOW or RED, with BATTLE STATIONS beside it when the crew are at them; DOCKED or LATCHED
 * at the right; and in an emergency the state and the player's ways out across the middle, keys
 * first ("DISABLED [P][P] ABANDON [B][B] TOW"). Dark in Green with nothing else to say.
 */
final class StatusStrip extends NpInstrument {
    static final float W = 720f, H = 24f;

    /** RED, YELLOW, or null for Green. */
    String condition;
    boolean battle;
    /** DOCKED, LATCHED or "". */
    String state = "";
    /** The emergency's words, each bracketed key a cap; "" with none. */
    String emergency = "";

    StatusStrip(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    boolean dark() {
        return condition == null && state.isEmpty() && emergency.isEmpty();
    }

    @Override
    protected void paint(NpCanvas cv) {
        cv.fillRound(-1.5f, -1.5f, W + 3f, H + 3f, 2.5f, StDraw.RIM, 1f);
        cv.fillRound(0f, 0f, W, H, 1.5f, NpColors.GLASS, 1f);
        if (condition != null) {
            Color c = "RED".equals(condition) ? NpColors.RED : NpColors.AMBER;
            cv.fillRound(3f, 3f, 92f, H - 6f, 1.5f, c, 1f);
            cv.fillRound(3f, 3f, 92f, 7f, 1.5f, NpColors.WHITE, 0.14f);
            cv.text(49f, 16.5f, condition, 12f, true, NpColors.INK, 1.2f, NpCanvas.MIDDLE);
            if (battle) cv.text(106f, 16.5f, "BATTLE STATIONS", 12f, true, c, 1f, NpCanvas.START);
        }
        if (!emergency.isEmpty()) emergency(cv, W / 2f + 40f, 16.5f);
        if (!state.isEmpty()) {
            cv.stroke(W - 95f, 3f, 92f, H - 6f, 1.2f, NpColors.WHITE);
            cv.text(W - 49f, 16.5f, state, 12f, true, NpColors.WHITE, 1f, NpCanvas.MIDDLE);
        }
    }

    /** The emergency's words in red and its keys as caps, centred on {@code cx}. */
    private void emergency(NpCanvas cv, float cx, float base) {
        float w = 0f;
        String[] parts = emergency.split("(?=\\[)|(?<=\\])");
        for (String p : parts) w += partW(cv, p);
        float x = cx - w / 2f;
        for (String p : parts) {
            if (key(p)) {
                StDraw.capKey(cv, x, 4f, p.substring(1, p.length() - 1), 16f);
            } else {
                cv.text(x, base, p, 12f, true, NpColors.RED, 0.4f, NpCanvas.START);
            }
            x += partW(cv, p);
        }
    }

    private static boolean key(String p) {
        return p.length() > 2 && p.startsWith("[") && p.endsWith("]");
    }

    private static float partW(NpCanvas cv, String p) {
        return key(p) ? StDraw.capW(cv, p.substring(1, p.length() - 1)) + 2f : cv.width(p, 12f, true, 0.4f);
    }
}
