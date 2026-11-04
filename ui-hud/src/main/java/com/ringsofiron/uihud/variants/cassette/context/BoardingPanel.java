package com.ringsofiron.uihud.variants.cassette.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.List;

/**
 * X2, the boarding panel on the right rail: how the boarding stands, and the way into it. The other
 * ship on flaps with LATCHED and BY EVA lenses, its state as the game words it on a two-line tube (who
 * is aboard and who still stands, or taken and how many held), then the party, the salvage marked and
 * the cargo coming across, each only while it applies, and the K BOARDING and U CAST OFF keys.
 */
final class BoardingPanel extends YardPart {
    static final float W = 308f, MAX_H = 150f, ROW = 22f;

    private final ContextFrame f;
    private List<String> state = List.of();
    private boolean salvage, cargo;

    BoardingPanel(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Lays out this frame's rows; returns the height. */
    float measure(YardPaint p) {
        BoardingBoard b = f.board;
        if (b.craftName.isEmpty()) return 0f;
        state = Kit.wrap(b.state, Kit.chars(p, W - 40f, 13f), 2);
        salvage = b.salvage && b.parts.size > 0;
        cargo = b.cargo.size > 0;
        float hh = 8f + 22f + 6f + Math.max(1, state.size()) * 16f + 10f + ROW + (salvage ? ROW : 0f) + (cargo ? ROW : 0f) + 26f + 6f;
        return Math.min(MAX_H, hh);
    }

    /** The party's state in a word: ABOARD, CROSSING, CAN'T GO, READY, or TAKEN once the ship is. */
    static String party(BoardingBoard b) {
        if (b.taken) return "TAKEN";
        if (b.boarding) return b.aboard > 0 ? "ABOARD" : "CROSSING";
        if (!b.boardRefusal.isEmpty()) return "CAN'T GO";
        return "READY";
    }

    /** Parts marked to strip and not yet off. */
    static int marked(BoardingBoard b) {
        int n = 0;
        for (int i = 0; i < b.parts.size; i++) if (b.parts.get(i).marked && !b.parts.get(i).off) n++;
        return n;
    }

    /** Parts already off its hull, on their way home or in the hold. */
    static int off(BoardingBoard b) {
        int n = 0;
        for (int i = 0; i < b.parts.size; i++) if (b.parts.get(i).off) n++;
        return n;
    }

    static int coming(BoardingBoard b) {
        int n = 0;
        for (int i = 0; i < b.cargo.size; i++) {
            Cargo c = b.cargo.get(i);
            if (c.onOrder > 1e-6) n++;
        }
        return n;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        BoardingBoard b = f.board;
        Kit.slideRail(p, f.screenW - x, false, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        p.face(6f, 6f, w - 12f, 26f, Yard.Finish.YARD, false);
        p.legend("BOARD", 14f, 24f, Yard.INK);
        p.flaps(60f, 10f, YardText.fit(b.craftName, 13), 13, 8.4f, 17f, 12.5f, Yard.FLAP_TEXT);
        Kit.lens(p, 178f, 11f, 56f, 15f, "LATCHED", Yard.GREEN_LAMP, !b.byEva);
        Kit.lens(p, 240f, 11f, 54f, 15f, "BY EVA", Yard.WHITE_LAMP, b.byEva);
        // The state, as the game words it
        float ty = 38f, th = Math.max(1, state.size()) * 16f + 6f;
        p.tube(8f, ty, w - 16f, th, 3f, 2f, false);
        for (int i = 0; i < state.size(); i++) p.tube(state.get(i), 16f, ty + 15f + i * 16f, 13f, Yard.P_AMBER, false);
        p.tubeOver(8f, ty, w - 16f, th);
        float y = ty + th + 8f;
        // PARTY
        String pt = party(b);
        p.face(6f, y - 2f, w - 12f, ROW, Yard.Finish.YARD, false);
        p.lamp(16f, y + 9f, 3f, pt.equals("CAN'T GO") ? Yard.AMBER_LAMP : Yard.GREEN_LAMP, !pt.equals("READY"));
        p.legend("PARTY", 26f, y + 13f, Yard.INK);
        p.flaps(80f, y + 1f, pt, 8, 8.4f, 16f, 12.5f, pt.equals("CAN'T GO") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        if (b.boarding || b.taken) {
            // Ours aboard and theirs standing; once taken, the prisoners held
            float dx = 156f;
            int n = b.taken ? b.prisoners : b.aboard;
            float dw = p.drums(dx, y, String.valueOf(Math.min(99, n)), 10f, 17f, 14f, false);
            p.legend(b.taken ? "HELD" : "OURS", dx + dw + 4f, y + 13f, Yard.INK);
            if (!b.taken) {
                float tx = dx + dw + 46f;
                float tw = p.drums(tx, y, String.valueOf(Math.min(99, b.defenders)), 10f, 17f, 14f, false);
                p.legend("THEIRS", tx + tw + 4f, y + 13f, Yard.INK);
            }
        }
        y += ROW;
        if (salvage && y + ROW <= h - 26f) {
            p.face(6f, y - 2f, w - 12f, ROW, Yard.Finish.YARD, false);
            p.legend("SALVAGE", 26f, y + 13f, Yard.INK);
            float dw = p.drums(92f, y, String.valueOf(Math.min(99, marked(b))), 10f, 17f, 14f, false);
            float lw = p.legend("MARKED", 92f + dw + 5f, y + 13f, Yard.INK);
            float ox = 92f + dw + 5f + lw + 14f;
            float ow = p.drums(ox, y, String.valueOf(Math.min(99, off(b))), 10f, 17f, 14f, false);
            p.legend("OFF", ox + ow + 5f, y + 13f, Yard.INK);
            y += ROW;
        }
        if (cargo && y + ROW <= h - 26f) {
            p.face(6f, y - 2f, w - 12f, ROW, Yard.Finish.YARD, false);
            p.legend("CARGO", 26f, y + 13f, Yard.INK);
            float dw = p.drums(92f, y, String.valueOf(Math.min(99, b.cargo.size)), 10f, 17f, 14f, false);
            int n = coming(b);
            p.legend(n > 0 ? "LOTS · " + n + " COMING ACROSS" : "LOTS ABOARD IT", 92f + dw + 6f, y + 13f, Yard.INK);
        }
        // The keys at the foot
        float ky = h - 24f;
        p.key(14f, ky, Kit.key(d, "BOARD", "K"));
        p.legend("BOARDING", 40f, ky + 12f, Yard.INK_LIGHT);
        if (!b.byEva) {
            p.key(170f, ky, Kit.key(d, "UNDOCK", "U"));
            p.legend("CAST OFF", 196f, ky + 12f, Yard.INK_LIGHT);
        }
    }

    @Override
    public String hover(float px, float py) {
        BoardingBoard b = f.board;
        float ly = localY(py);
        if (ly >= h - 28f) return null;
        if (ly > 38f + Math.max(1, state.size()) * 16f + 6f && ly < 38f + Math.max(1, state.size()) * 16f + 14f + ROW) {
            if (!b.boardRefusal.isEmpty() && !b.boarding) return b.boardRefusal;
        }
        if (!b.salvageRefusal.isEmpty()) return b.salvageRefusal;
        for (int i = 0; i < b.parts.size; i++) {
            Part part = b.parts.get(i);
            if (part.marked && !part.status.isEmpty()) return part.name + ": " + part.status;
        }
        return null;
    }
}
