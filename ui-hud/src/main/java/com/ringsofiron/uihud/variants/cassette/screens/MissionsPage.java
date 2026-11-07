package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The mission board (the classic {@code MissionWindow}). At a station's board, Context's M3 Missions:
 * the offers, each with Take, and the contracts already held, one line each with Give up, so a new
 * job is weighed against them. On the record (M5 Contracts), the contracts held alone. The selected
 * job's terms are on a fanfold sheet beside the tube; giving one up can't be undone, so its key is
 * guarded.
 */
final class MissionsPage extends ListPage {
    /** Row kinds; in a side panel each job's terms take a dim row of their own under it ({@code TERMS}). */
    private static final int HEAD = 0, OFFER = 1, HELD = 2, TERMS = 3;
    /** True on the record (the contracts held, anywhere), false on a station's board. */
    private final boolean record;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();
    private final Array<String> wrapped = new Array<>();
    private String wrappedFor;
    private float wrappedW;
    /** Whether it drew in a side panel last frame (the Station link, the slate): a job's terms under it. */
    private boolean narrow;

    MissionsPage(boolean record) {
        super(record ? "CONTRACTS" : "MISSIONS", "JOBS", ClassicWindow.MISSIONS);
        this.record = record;
    }

    private MissionBoard board() {
        return b.missions;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    boolean offered() {
        // The record has the contracts held wherever the ship is (and the board's own while docked, as
        // the game opens it then); the station's console only at a board
        return b != null && (record ? board().isOpen() : !board().stationName.isEmpty());
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        MissionBoard m = board();
        kinds.clear();
        refs.clear();
        if (!record) {
            add(HEAD, 0);
            for (int i = 0; i < m.offers.size; i++) {
                add(OFFER, i);
                if (narrow) add(TERMS, i);
            }
        }
        add(HEAD, 1);
        for (int i = 0; i < m.held.size; i++) {
            add(HELD, i);
            if (narrow) add(TERMS, -1 - i);
        }
        keepSelection();
    }

    private void add(int kind, int ref) {
        kinds.add(kind);
        refs.add(ref);
    }

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) != HEAD && kinds.get(i) != TERMS;
    }

    @Override
    boolean belongsAbove(int i) {
        return kinds.get(i) == TERMS;
    }

    @Override
    String windowKey() {
        // J opens the board while docked, the contracts held only away from a station
        return record && b != null && !board().stationName.isEmpty() ? null : super.windowKey();
    }

    private Job job(int i) {
        if (i < 0 || i >= kinds.size) return null;
        int k = kinds.get(i);
        return k == OFFER ? board().offers.get(refs.get(i)) : k == HELD ? board().held.get(refs.get(i)) : null;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        MissionBoard m = board();
        int kind = kinds.get(i);
        int termsAt = Math.max(36, Math.min(L.cols - 40, 64));
        if (kind == HEAD) {
            boolean offers = refs.get(i) == 0;
            L.at(p, 1, r, offers ? "OFFERS" : "HELD", DIM);
            if (offers && m.offers.size == 0) L.at(p, 9, r, "No work posted here", DIM);
            else if (!offers && m.held.size == 0) L.at(p, 9, r, "No contracts held", DIM);
            else if (narrow && !offers) L.right(p, L.cols - 1, r, "HOLDING " + m.held.size + " OF 3", DIM);
            else if (!narrow) L.at(p, termsAt, r, offers ? "TERMS" : "TERMS, TIME LEFT", DIM);
            return;
        }
        if (kind == TERMS) {
            // The job's terms under it, lit with it while it is selected
            int ref = refs.get(i);
            Job j = ref >= 0 ? m.offers.get(ref) : m.held.get(-1 - ref);
            boolean locked = ref >= 0 && !j.locked.isEmpty();
            String t = Ledger.cut(locked ? Fmt.upper(j.locked) : j.terms, L.cols - 5);
            if (s) L.rev(p, 4, r, t);
            else L.at(p, 4, r, t, DIM);
            return;
        }
        if (narrow) {
            Job j = job(i);
            boolean locked = kind == OFFER && !j.locked.isEmpty();
            String title = Ledger.cut(j.title, L.cols - 3);
            if (s) L.rev(p, 2, r, title);
            else L.at(p, 2, r, title, locked ? DIM : AMBER);
            return;
        }
        Job j = job(i);
        boolean locked = kind == OFFER && !j.locked.isEmpty();
        Color ink = locked ? DIM : AMBER;
        String title = Ledger.cut(j.title, termsAt - 4), terms = Ledger.cut(j.terms, L.cols - termsAt - 1);
        if (s) {
            L.rev(p, 2, r, title);
            L.rev(p, termsAt, r, terms);
        } else {
            L.at(p, 2, r, title, ink);
            L.at(p, termsAt, r, terms, ink);
        }
    }

    @Override
    void keys() {
        Job j = job(sel);
        if (j == null) return;
        if (kinds.get(sel) == OFFER) softKey("TAKE", j.locked.isEmpty() ? null : Fmt.upper(j.locked));
        else guardedKey("GIVE UP", null, "Give up " + j.title + "? Press again: it costs standing"
                + (j.giver.isEmpty() ? "" : " with " + j.giver));
    }

    @Override
    void press(int k) {
        MissionProvider provider = b.data == null ? null : b.data.missions;
        Job j = job(sel);
        if (provider == null || j == null) return;
        say(kinds.get(sel) == OFFER ? provider.accept(j.id) : provider.abandon(j.id));
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean was = narrow;
        narrow = w < 700f;
        if (narrow != was && b != null) refresh(b);
        if (narrow) {
            drawNarrow(p, hits, x, y, w, h);
            return;
        }
        boolean liv = !record;
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        Color ink = liv ? BigConsole.LIV_INK : Yard.INK;
        // The tube across the page, as the market's; under it the career and the tension at the left
        // and the selected job's sheet at the right, where the market has its printer
        int cols = Math.max(70, Ledger.colsIn(bw - 42f)), rows = Math.max(10, Ledger.rowsIn(bh - 300f));
        L.set(bx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, record ? "CONTRACTS HELD" : "MISSION BOARD", record ? null : Ledger.cut(Fmt.upper(b.station), 24));
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        float uy = L.y + L.h + 40f, sheetW = Math.min(460f, L.w * 0.5f), sheetX = L.x + L.w - sheetW;
        float lw = sheetX - 30f - L.x, ty = uy + 12f;
        MissionBoard m = board();
        if (!m.career.isEmpty()) {
            p.legend("CAREER", L.x, ty, ink);
            p.legendFit(Fmt.upper(b.rank), L.x, ty + 18f, lw, ink);
            ty += 44f;
        }
        String tension = m.tension.regionMatches(true, 0, "Tension:", 0, 8) ? m.tension.substring(8).trim() : m.tension;
        if (!tension.isEmpty()) {
            p.legend("TENSION", L.x, ty, ink);
            p.legendFit(Fmt.upper(tension), L.x, ty + 18f, lw, ink);
        }
        sheet(p, sheetX, uy, sheetW, by + bh - 16f - uy, liv);
    }

    /**
     * In a side panel: the jobs on the tube, each with its terms on a dim row under it, the contracts
     * held under them with how many of three; the soft keys under the tube; and the selected job typed
     * on the sheet under them, for whom and why it was posted.
     */
    private void drawNarrow(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean liv = !record;
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        float sheetH = Math.min(150f, Math.max(0f, bh - 330f));
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(8, Ledger.rowsIn(bh - 42f - sheetH));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, record ? "CONTRACTS HELD" : "CONTRACT BOARD", record ? null : Ledger.cut(Fmt.upper(b.station), 18));
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        float sy = L.y + L.h + 30f;
        if (sheetH >= 80f) sheet(p, L.x, sy, L.w, by + bh - 8f - sy, liv);
    }

    /** The selected job typed on a fanfold sheet: what, for whom, the terms and the note; a stamp when it is locked. */
    private void sheet(YardPaint p, float x, float y, float w, float h, boolean liv) {
        Job j = job(sel);
        if (j == null || h < 80f) return;
        Paper.fanfold(p, x, y, w, h, liv ? BigConsole.LIV_FOOT : Yard.ENAMEL_BOTTOM);
        float tx = x + 26f, tw = w - 52f;
        String text = j.title + "\n" + j.giver + "\n" + j.terms + "\n" + j.note;
        if (!text.equals(wrappedFor) || tw != wrappedW) {
            wrappedFor = text;
            wrappedW = tw;
            wrapped.clear();
            Array<String> part = new Array<>();
            String[] parts = {j.title, j.giver.isEmpty() ? "" : "For " + j.giver, j.terms, j.note};
            for (int i = 0; i < parts.length; i++) {
                Paper.wrap(p, parts[i], tw, Paper.SIZE, part);
                if (part.size == 0) continue;
                wrapped.addAll(part);
                // A line between the parts where there is room for it; in a side panel the sheet is short
                if (!narrow) wrapped.add("");
            }
        }
        float ly = y + 30f + 13f;
        p.text(YardFonts.Face.PAPER, 12f, kinds.get(sel) == OFFER ? "OFFER" : "CONTRACT", tx, y + 22f, Paper.FADED);
        for (int i = 0; i < wrapped.size && ly < y + h - 8f; i++, ly += Paper.LINE) {
            p.typed(wrapped.get(i), tx, ly, Paper.SIZE, Paper.INK);
        }
        if (kinds.get(sel) == OFFER && !j.locked.isEmpty() && h > 120f) Paper.stamp(p, Fmt.upper(Ledger.cut(j.locked, 30)), tx, y + h - 40f);
    }
}
