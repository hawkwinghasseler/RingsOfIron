package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * F2, the weapons rack (section 7), under the master cue's hood at fire control: one row per mount,
 * its place, weapon, state, the hit chance on the Lock as cyan pips with the figure and its word,
 * rounds left for a launcher, and who fires it (YOU in cyan, CREW, PD). Mounts in hand carry a cyan
 * mark. Point defense engaging reads green, as an engaged mode does. At a gun seat it shrinks to
 * that gun's row and any launcher's.
 */
final class WeaponsRack extends NpDu {
    static final float W = 620f, ROW = 24f;

    static final class Row {
        String mount = "", weapon = "", state = "", value, ammo, who = "", hitPct, hitWord;
        Color stateColor = NpColors.WHITE, whoColor = NpColors.WHITE;
        int pips = -1;
        boolean inHand;
    }

    final List<Row> rows = new ArrayList<>();

    WeaponsRack(NpKit kit) {
        super(kit);
        bezel = 8f;
        setSize(W, 150f);
    }

    float heightFor() {
        return 16f + 7f + Math.max(1, rows.size()) * ROW + 4f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float R = gx + gw - 10f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float top = gy + 7f + i * ROW, yy = top + 16.7f;
            if (i > 0) cv.line(gx + 4f, top, gx + gw - 4f, top, 1f, NpColors.GRATICULE);
            if (r.inHand) cv.fillPoly(new float[] {gx + 3f, yy - 10f, gx + 9f, yy - 5f, gx + 3f, yy}, NpColors.CYAN);
            cv.text(gx + 12f, yy, CtxDraw.fit(cv, r.mount, 96f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, NpColors.WHITE, 0.4f, NpCanvas.START);
            cv.text(gx + 112f, yy, CtxDraw.fitHead(cv, r.weapon, 122f, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            float sw = cv.text(gx + 240f, yy, CtxDraw.fit(cv, r.state, 130f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, r.stateColor, 0.4f, NpCanvas.START);
            if (r.value != null) CtxDraw.val(cv, gx + 240f + sw + 8f, yy, r.value, null, CtxDraw.B, NpColors.WHITE);
            if (r.pips >= 0) {
                for (int k = 0; k < 3; k++) {
                    float px = gx + 376f + k * 9f;
                    if (k < r.pips) cv.disc(px, yy - 4.5f, 6f, NpColors.CYAN);
                    cv.ring(px, yy - 4.5f, 3f, 1.2f, NpColors.CYAN);
                }
                cv.text(gx + 402f, yy, r.hitPct, CtxDraw.B, true, NpColors.CYAN, 0f, NpCanvas.START);
                cv.text(gx + 440f, yy, r.hitWord, CtxDraw.B, true, NpColors.CYAN, 0.4f, NpCanvas.START);
            }
            if (r.ammo != null) cv.text(gx + 490f, yy, r.ammo, CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            cv.text(R, yy, r.who, CtxDraw.B, true, r.whoColor, 0.6f, NpCanvas.END);
        }
    }

    /**
     * A mount's place without the kind word the game's name ends on ("FWD PORT PD" is FWD PORT),
     * since the weapon column says what it is; a name that is only the kind keeps it.
     */
    static String place(String name) {
        String[] w = name.toUpperCase(Locale.ROOT).split(" ");
        StringBuilder b = new StringBuilder();
        for (String s : w) {
            if (s.equals("GUN") || s.equals("PD") || s.equals("MSL") || s.equals("BEAM")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(s);
        }
        String out = b.toString();
        return out.isEmpty() || out.matches("[0-9]+") ? name.toUpperCase(Locale.ROOT) : out;
    }

    private static final Pattern MARK = Pattern.compile("\\s+MK\\s*[0-9IVX]+$");

    /** A weapon's name in capitals without its mark ("Coilgun Mk1" is COILGUN). */
    static String weaponName(String weapon) {
        return MARK.matcher(weapon == null ? "" : weapon.toUpperCase(Locale.ROOT)).replaceAll("").trim();
    }
}
