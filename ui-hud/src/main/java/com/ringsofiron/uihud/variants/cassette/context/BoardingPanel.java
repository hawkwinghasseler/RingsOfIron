package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * X2, the boarding panel on the right rail: how the boarding stands, and the way into it (v19). The
 * other ship on flaps with a LATCHED or BY EVA lens; its state in a row of its own, in the game's words
 * ("Disabled: reactor down", "Being boarded", "Taken: crew held"), and for a craft nobody defends how it
 * is taken; then the party (how many and what they are doing, or why none can go), the salvage marked,
 * the cargo coming across and the prisoners held with the berths free for them, each only while it
 * applies, and the K BOARDING and U CAST OFF keys. Rows past its 150 give way from the last.
 */
final class BoardingPanel extends YardPart {
    static final float W = 308f, MAX_H = 150f, ROW = 20f, HEAD = 24f, KEYS = 22f;
    /** The rows under the state, in the order they stand. */
    enum Row { PARTY, SALVAGE, CARGO, HELD }
    /** Seconds between looks at the crew's berths, while prisoners are held. */
    private static final float BERTHS_EVERY = 1f;
    private static final Pattern BERTHS = Pattern.compile("(\\d+) / (\\d+) berths.*");

    private final ContextFrame f;
    private List<String> state = List.of();
    private final Array<Row> rows = new Array<>();
    private final CrewSheet crew = new CrewSheet();
    private float berthsAt = -100f, clock;
    /** Berths free for prisoners, or -1 when the game doesn't say. */
    private int berthsFree = -1;

    BoardingPanel(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Lays out this frame's rows; returns the height. */
    float measure(YardPaint p, float delta) {
        BoardingBoard b = f.board;
        clock += delta;
        if (b.craftName.isEmpty()) return 0f;
        state = Kit.wrap(state(b), Kit.chars(p, W - 40f, 13f), 2);
        rows.clear();
        if (party(b) != null) rows.add(Row.PARTY);
        if (b.salvage && b.parts.size > 0) rows.add(Row.SALVAGE);
        if (b.cargo.size > 0) rows.add(Row.CARGO);
        if (b.taken && b.prisoners > 0) {
            rows.add(Row.HELD);
            berths(f.data);
        }
        // Too many for its 150: the state's second line goes first, then the last rows
        while (height() > MAX_H && state.size() > 1) state = state.subList(0, 1);
        while (height() > MAX_H && rows.size > 1) rows.pop();
        return height();
    }

    private float height() {
        return 6f + HEAD + 4f + Math.max(1, state.size()) * 16f + 4f + 4f + rows.size * ROW + 4f + KEYS + 4f;
    }

    /** The berths free for the prisoners: the ship's berths less everyone aboard (the crew sheet's count). */
    private void berths(HudData d) {
        if (d == null || d.crew == null || clock - berthsAt < BERTHS_EVERY) return;
        berthsAt = clock;
        crew.clear();
        d.crew.collect(crew);
        berthsFree = berthsFree(crew.summary);
    }

    /** "5 / 8 berths, ..." has 3 free; -1 when the summary gives no count. */
    static int berthsFree(String summary) {
        Matcher m = BERTHS.matcher(summary == null ? "" : summary);
        return m.matches() ? Math.max(0, Integer.parseInt(m.group(2)) - Integer.parseInt(m.group(1))) : -1;
    }

    /**
     * The craft's state for its row: the game's words while it is disabled or surrendered; "Being
     * boarded" while the party is at it (the party's row has the counts); for a craft nobody defends,
     * how it is taken; once taken, how: its crew held, or nobody left to hold.
     */
    static String state(BoardingBoard b) {
        if (b.taken) return b.prisoners > 0 ? "Taken: crew held" : "Taken: undefended";
        if (b.boarding) return "Being boarded";
        if (undefended(b)) return "Nobody aboard: a part marked takes it";
        return b.state;
    }

    /** A disabled craft with no one aboard to fight for it: taken as soon as the salvage begins. */
    static boolean undefended(BoardingBoard b) {
        return b.salvage && !b.taken && !b.boarding && b.defenders == 0 && b.disabled;
    }

    /**
     * The party's state in a word, or null when it has no row: CROSSING, FIGHTING, ABOARD, COMING HOME
     * once the craft is taken, CAN'T GO, READY. A craft nobody defends needs no party, and once the party
     * is home from a taken one it has nothing to say.
     */
    static String party(BoardingBoard b) {
        if (b.taken) return b.aboard > 0 ? "COMING HOME" : null;
        if (b.boarding) return b.aboard == 0 ? "CROSSING" : b.defenders > 0 ? "FIGHTING" : "ABOARD";
        if (undefended(b)) return null;
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
        p.face(6f, 6f, w - 12f, HEAD, Yard.Finish.YARD, false);
        p.legend("BOARD", 14f, 23f, Yard.INK);
        p.flaps(60f, 9f, YardText.fit(b.craftName, 18), 18, 8.4f, 17f, 12.5f, Yard.FLAP_TEXT);
        if (b.byEva) Kit.lens(p, 240f, 10f, 56f, 15f, "BY EVA", Yard.WHITE_LAMP, true);
        else Kit.lens(p, 240f, 10f, 56f, 15f, "LATCHED", Yard.GREEN_LAMP, true);
        // The state, as the game words it
        float ty = 6f + HEAD + 4f, th = Math.max(1, state.size()) * 16f + 4f;
        p.tube(8f, ty, w - 16f, th, 3f, 2f, false);
        for (int i = 0; i < state.size(); i++) p.tube(state.get(i), 16f, ty + 14f + i * 16f, 13f, Yard.P_AMBER, false);
        p.tubeOver(8f, ty, w - 16f, th);
        float y = ty + th + 6f;
        for (int i = 0; i < rows.size; i++, y += ROW) row(p, b, rows.get(i), y);
        // The keys at the foot
        float ky = h - KEYS - 3f;
        p.key(14f, ky, Kit.key(d, "BOARD", "K"));
        p.legend("BOARDING", 40f, ky + 12f, Yard.INK_LIGHT);
        if (!b.byEva) {
            p.key(170f, ky, Kit.key(d, "UNDOCK", "U"));
            p.legend("CAST OFF", 196f, ky + 12f, Yard.INK_LIGHT);
        }
    }

    private void row(YardPaint p, BoardingBoard b, Row r, float y) {
        p.face(6f, y - 1f, w - 12f, ROW - 1f, Yard.Finish.YARD, false);
        float base = y + 13f;
        switch (r) {
            case PARTY: {
                String pt = party(b);
                boolean cant = "CAN'T GO".equals(pt);
                p.lamp(16f, y + 9f, 3f, cant ? Yard.AMBER_LAMP : Yard.GREEN_LAMP, !"READY".equals(pt));
                p.legend("PARTY", 26f, base, Yard.INK);
                p.flaps(80f, y + 1f, pt, 11, 8.2f, 16f, 12.5f, cant ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
                if (b.boarding || b.taken) {
                    // Ours aboard, and while it fights theirs standing
                    float dx = 178f;
                    float dw = p.drums(dx, y, String.valueOf(Math.min(99, b.aboard)), 10f, 17f, 14f, false);
                    p.legend("OURS", dx + dw + 4f, base, Yard.INK);
                    if (!b.taken && b.defenders > 0) {
                        float tx = dx + dw + 38f;
                        float tw = p.drums(tx, y, String.valueOf(Math.min(99, b.defenders)), 10f, 17f, 14f, false);
                        p.legend("THEIRS", tx + tw + 4f, base, Yard.INK);
                    }
                }
                break;
            }
            case SALVAGE: {
                p.legend("SALVAGE", 26f, base, Yard.INK);
                float dw = p.drums(92f, y, String.valueOf(Math.min(99, marked(b))), 10f, 17f, 14f, false);
                float lw = p.legend("MARKED", 92f + dw + 5f, base, Yard.INK);
                float ox = 92f + dw + 5f + lw + 14f;
                float ow = p.drums(ox, y, String.valueOf(Math.min(99, off(b))), 10f, 17f, 14f, false);
                p.legend("OFF", ox + ow + 5f, base, Yard.INK);
                break;
            }
            case CARGO: {
                p.legend("CARGO", 26f, base, Yard.INK);
                float dw = p.drums(92f, y, String.valueOf(Math.min(99, b.cargo.size)), 10f, 17f, 14f, false);
                int n = coming(b);
                p.legend(n > 0 ? "LOTS · " + n + " COMING ACROSS" : "LOTS ABOARD IT", 92f + dw + 6f, base, Yard.INK);
                break;
            }
            default: {
                // The prisoners held aboard it, and the berths free for them: only as many come across
                p.legend("HELD", 26f, base, Yard.INK);
                float dw = p.drums(92f, y, String.valueOf(Math.min(99, b.prisoners)), 10f, 17f, 14f, false);
                if (berthsFree >= 0) p.legend("· " + berthsFree + (berthsFree == 1 ? " BERTH FREE" : " BERTHS FREE"), 92f + dw + 6f, base, Yard.INK);
                break;
            }
        }
    }

    @Override
    public String hover(float px, float py) {
        BoardingBoard b = f.board;
        float ly = localY(py);
        if (ly >= h - KEYS - 4f) return null;
        float ty = 6f + HEAD + 4f, rowsTop = ty + Math.max(1, state.size()) * 16f + 4f + 6f;
        // The state's row: the game's own sentence
        if (ly >= ty && ly < rowsTop - 6f) return b.state.isEmpty() ? null : b.state;
        int i = (int) Math.floor((ly - rowsTop) / ROW);
        if (i < 0 || i >= rows.size) return null;
        switch (rows.get(i)) {
            case PARTY:
                return !b.boardRefusal.isEmpty() && !b.boarding && !b.taken ? b.boardRefusal : null;
            case SALVAGE:
                if (!b.salvageRefusal.isEmpty()) return b.salvageRefusal;
                for (int k = 0; k < b.parts.size; k++) {
                    Part part = b.parts.get(k);
                    if (part.marked && !part.status.isEmpty()) return part.name + ": " + part.status;
                }
                return null;
            case HELD:
                return berthsFree < 0 ? null : "Only as many prisoners come across as there are berths: " + berthsFree + " free";
            default:
                return null;
        }
    }
}
