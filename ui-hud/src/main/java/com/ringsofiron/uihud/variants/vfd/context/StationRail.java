package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The station terminal's rail (functional-context 10.3): what bounds every choice on every tab. The
 * credits in seven segments, the standing with the station's owner as the game's signed number ("+0"),
 * the hold used of its capacity and each tank with a bar, and the infirmary only while it treats one of
 * the crew ("TREATING ADA") or has billed for one since docking ("1 TREATED · 150 CR"), X1's rule.
 */
final class StationRail {
    /** The values' column, from the rail's text edge. */
    static final float VALUE_X = 100f;
    /** A value's cells before its bar. */
    static final int VALUE_CELLS = 15;

    float x, y, w, h;
    private String credits = "0", standing = "", hold = "";
    private float holdFull;
    private boolean hasHold;
    private final Array<String> tanks = new Array<>();
    private final FloatArray tankFull = new FloatArray();
    private String infirmary = "";

    void refresh(Boards b) {
        double cr = b.trade.isDocked() ? b.trade.credits : b.yard.credits;
        credits = Glass.thousands(cr);
        standing = b.standing;
        hasHold = b.trade.hasHold && b.trade.cargoCapacity > 0;
        hold = hasHold ? Glass.units(b.trade.cargoUsed) + " OF " + Glass.units(b.trade.cargoCapacity) + " M3" : "";
        holdFull = hasHold ? (float) (b.trade.cargoUsed / b.trade.cargoCapacity) : 0f;
        tanks.clear();
        tankFull.clear();
        for (int i = 0; i < b.tanks.size; i++) {
            String name = b.tanks.get(i);
            String pct = Math.round(b.tankFull.get(i) * 100f) + "%";
            int pad = Math.max(1, VALUE_CELLS - name.length() - pct.length());
            StringBuilder sb = new StringBuilder(name);
            for (int k = 0; k < pad; k++) sb.append(' ');
            tanks.add(Glass.cut(sb.append(pct).toString(), VALUE_CELLS));
            tankFull.add(b.tankFull.get(i));
        }
        infirmary = infirmary(b);
    }

    /** The infirmary's line, X1's rule: while it treats one of the crew or has billed for one since docking; else none. */
    static String infirmary(Boards b) {
        if (!b.infirmary) return "";
        if (b.treating.size == 1) return "TREATING " + b.treating.first().toUpperCase(Locale.ROOT);
        if (b.treating.size > 1) return "TREATING " + b.treating.size;
        if (b.treated > 0) return b.treated + " TREATED · " + Glass.thousands(b.treatedCost) + " CR";
        return "";
    }

    float wantedHeight() {
        float hh = 8f + 26f + 20f;
        if (hasHold) hh += 20f;
        if (tanks.size > 0) hh += 16f * (tanks.size - 1) + 26f;
        if (!infirmary.isEmpty()) hh += 36f;
        return hh + 8f;
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    void paint(Filament f) {
        Glass.pane(f, x, y, w, h, null);
        float r0 = x + 12f, r1 = x + w - 12f, vx = r0 + VALUE_X, bx = vx + Glass.width(VALUE_CELLS) + 10f;
        float ry = y + 8f;
        f.legend(r0, ry + 14f, "CREDITS", f.o().size(13f).dim().noGhost());
        Glass.figure(f, vx, ry, 16f, credits, "CR", Math.max(5, credits.length()), Drive.NORM);
        ry += 26f;
        if (!standing.isEmpty()) row(f, r0, vx, ry, "STANDING", standing);
        ry += 20f;
        if (hasHold) {
            row(f, r0, vx, ry, "HOLD", hold);
            bar(f, bx, ry, r1, holdFull);
            ry += 20f;
        }
        for (int i = 0; i < tanks.size; i++) {
            if (i == 0) Glass.label(f, r0, ry + 9.5f, "TANKS");
            Glass.text(f, vx, ry, tanks.get(i));
            bar(f, bx, ry, r1, tankFull.get(i));
            ry += i == tanks.size - 1 ? 26f : 16f;
        }
        // No bar on its row: the line runs to the rail's edge
        if (!infirmary.isEmpty()) row(f, r0, vx, ry, "INFIRMARY", Glass.cut(infirmary, Glass.cells(r1 - vx)));
    }

    private static void row(Filament f, float r0, float vx, float ry, String label, String value) {
        Glass.label(f, r0, ry + 9.5f, label);
        Glass.text(f, vx, ry, value);
    }

    private static void bar(Filament f, float bx, float ry, float r1, float full) {
        if (r1 - bx < 24f) return;
        f.bar(bx, ry + 2f, r1 - bx, 6f, 12, Math.max(0f, Math.min(1f, full)), f.o().norm().gap(1f));
    }
}
