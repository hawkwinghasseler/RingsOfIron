package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * The situation panels in the right column's middle (functional-context 8). X1, docked: the station,
 * its port, its owner and the captain's standing with it, and its services each behind its key cap,
 * the ones it does not offer left out; then the undock key. X2, boarding: the other ship with LATCHED
 * or EVA, its state on a row of its own, the party, the salvage, the cargo coming across, and the
 * prisoners or else why no party can go, in six rows of glass with the two keys printed with their
 * actions under it. X2 is for a beaten ship or one of ours: a fit one in reach is still a fight.
 */
final class Panels {
    static final int X1_ROWS = 10;
    static final float X2_GLASS = 6 * Geo.ROW + 6f, X2_H = X2_GLASS + 5f + 24f;
    static final String[] BY = {"LATCHED", "EVA"};

    // X1
    boolean station;
    String name = "", port = "", owner = "", standing = "", standingValue = "";
    final String[] keys = new String[X1_ROWS], words = new String[X1_ROWS];
    int lines;
    /** The undock key printed with its action. */
    String undockKey = "U  UNDOCK";

    // X2
    boolean boarding, latched;
    String craft = "", state = "", party = "", partyState = "", marked = "", off = "", lots = "", boardKey = "K", castOffKey = "U";
    boolean prisoners;
    /** Why no boarding party can go now, and the refusal it was cut from. */
    String refusal = "";
    private String refusalOf = "";
    private final float[] lit2 = new float[2];

    void update(HudData d, Situations s, BoardingBoard board) {
        station = s.docked && !s.fullScreen && d.navReadout.docked.present;
        if (station) station(d);
        boarding = s.boarding && !s.fullScreen && board != null && board.isOpen() && s.seat != Situations.Seat.NONE;
        if (boarding) boarding(d, s, board);
    }

    private void station(HudData d) {
        StationInfo st = d.navReadout.docked;
        name = Fmt.name(st.name.isEmpty() ? d.ctx.stationName : st.name, 18);
        port = d.docking.present && d.docking.ports > 1 ? "PORT " + (d.docking.port + 1) : "";
        owner = Fmt.cut(st.owner, 33);
        standing = st.standingName.isEmpty() ? "" : Fmt.cut(st.standingName, 12);
        standingValue = Float.isNaN(st.standing) ? "" : (st.standing > 0 ? "+" : "") + Math.round(st.standing);
        lines = 0;
        if (st.market) line(key(d, "TRADE", "T"), "MARKET");
        if (st.yard) line(key(d, "YARD", "Y"), "YARD");
        if (st.contracts) line(key(d, "MISSIONS", "J"), "MISSIONS");
        // Shipyard and hire have no keys of their own: they are tabs in the terminal
        if (st.shipyard) line("", "SHIPYARD: SHIPS FOR SALE");
        if (st.hiring) line("", "HIRE: CREW LOOKING FOR A BERTH");
        if (st.infirmary) line("", "INFIRMARY: TREATS THE WOUNDED");
        if (!st.refusal.isEmpty()) line("", Rows.words(st.refusal, 33));
        undockKey = key(d, "UNDOCK", "U") + "  UNDOCK";
    }

    private void line(String k, String w) {
        if (lines == X1_ROWS) return;
        keys[lines] = k;
        words[lines] = w;
        lines++;
    }

    private void boarding(HudData d, Situations s, BoardingBoard b) {
        latched = s.latched;
        craft = Fmt.name(b.craftName, 16);
        state = Fmt.cut(b.state.isEmpty() ? (b.taken ? "TAKEN" : "") : b.state, 25);
        party = Integer.toString(Math.min(9, b.aboard));
        partyState = b.aboard <= 0 ? "" : b.taken ? "HOLDING" : b.defenders > 0 ? "FIGHTING" : "SEARCHING";
        // Why no party can go, in whole words on the last row, while it has no prisoners to name
        String why = b.aboard > 0 || b.taken ? "" : b.boardRefusal;
        if (!why.equals(refusalOf)) {
            refusalOf = why;
            refusal = Rows.words(why, 33);
        }
        int m = 0, o = 0;
        for (Part p : b.parts) {
            if (p.marked) m++;
            if (p.off) o++;
        }
        marked = b.parts.size > 0 ? Integer.toString(Math.min(9, m)) : "";
        off = b.parts.size > 0 ? Integer.toString(Math.min(9, o)) : "";
        int coming = 0;
        for (Cargo c : b.cargo) if (c.onOrder > 0) coming++;
        lots = Integer.toString(Math.min(9, coming));
        prisoners = b.prisoners > 0;
        boardKey = key(d, "BOARD", "K") + "  BOARDING";
        castOffKey = key(d, "UNDOCK", "U") + "  CAST OFF";
    }

    private static String key(HudData d, String action, String fallback) {
        String k = d.key(action);
        return (k.isEmpty() ? fallback : k).toUpperCase(Locale.ROOT);
    }

    float stationHeight() {
        return station ? (4 + lines) * Geo.ROW + 6f + 30f : 0f;
    }

    float boardingHeight() {
        return boarding ? X2_H : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid from the right

    void paintStation(Filament f, float y) {
        if (!station) return;
        float x = Geo.RX, h = stationHeight();
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        float ry = y + 3f, b = ry + 17f;
        float nw = f.dm(x + 6f, b - 14f, name, f.o().pitch(2f).norm().noGhost());
        if (!port.isEmpty()) f.legend(x + 6f + nw + 10f, b, port, f.o().dim());
        ry += 24f;
        f.dm(x + 6f, Geo.dmTop(Geo.base(ry)), owner, f.o().pitch(Geo.P).cells(33).dim());
        ry += Geo.ROW;
        b = Geo.base(ry);
        float cx = Rows.lab(f, x + 6f, b, "STANDING");
        cx = Rows.txt(f, cx, b, standing, 12);
        Kit.fits(f.seg7(cx, b - 12f, 12f, standingValue, f.o().cells(4).right().norm()) + cx, x + Geo.CW - 4f, "X1 standing");
        ry += Geo.ROW + 4f;
        for (int i = 0; i < lines; i++) {
            b = Geo.base(ry);
            cx = x + 6f;
            if (!keys[i].isEmpty()) cx += f.keycap(cx, ry + 1f, keys[i], 17f, 11f, false) + 6f;
            f.dm(cx, Geo.dmTop(b), words[i], f.o().pitch(Geo.P).level(keys[i].isEmpty() ? Drive.DIM : Drive.NORM).noGhost());
            ry += Geo.ROW;
        }
        float ky = y + h - 30f;
        f.key(x + 4f, ky, Geo.CW - 8f, 24f, undockKey, f.o());
    }

    /** X2 with its top at {@code y}: the glass, then its two keys under it. */
    void paintBoarding(Filament f, float y) {
        if (!boarding) return;
        float x = Geo.RX, gh = X2_GLASS;
        f.plate(x, y, Geo.CW, X2_H, 5f, 6f);
        f.module(x, y, Geo.CW, gh, f.o().radius(2.5f));
        float ry = y + 3f, vx = x + 56f, b = Geo.base(ry);
        f.legend(x + 6f, b, "BOARD", f.o().dim());
        f.dm(vx, Geo.dmTop(b), craft, f.o().pitch(Geo.P).cells(16).norm());
        lit2[0] = latched ? Drive.NORM : Float.NaN;
        lit2[1] = !latched ? Drive.NORM : Float.NaN;
        Kit.fits(Kit.states(f, vx + 16 * Geo.CELL + 6f, b, BY, lit2, 13f, 6f) - 6f, x + Geo.CW - 4f, "X2 board");
        ry += Geo.ROW;
        f.dm(vx, Geo.dmTop(Geo.base(ry)), state, f.o().pitch(Geo.P).cells(25).norm());
        ry += Geo.ROW;
        b = Geo.base(ry);
        f.legend(x + 6f, b, "PARTY", f.o().dim());
        float cx = Rows.num(f, vx, b, party, 1);
        cx = Rows.lab(f, cx, b, "ABOARD") + 4f;
        f.dm(cx, Geo.dmTop(b), partyState, f.o().pitch(Geo.P).cells(8).norm());
        ry += Geo.ROW;
        b = Geo.base(ry);
        f.legend(x + 6f, b, "SALV", f.o().dim());
        cx = Rows.num(f, vx, b, marked, 1);
        cx = Rows.lab(f, cx, b, "PARTS MARKED") + 6f;
        cx = Rows.num(f, cx, b, off, 1);
        Rows.lab(f, cx, b, "OFF");
        ry += Geo.ROW;
        b = Geo.base(ry);
        f.legend(x + 6f, b, "CARGO", f.o().dim());
        cx = Rows.num(f, vx, b, lots, 1);
        Rows.lab(f, cx, b, "LOTS COMING ACROSS");
        ry += Geo.ROW;
        if (prisoners) f.dm(vx, Geo.dmTop(Geo.base(ry)), "PRISONERS TO BRING ABOARD", f.o().pitch(Geo.P).cells(25).norm());
        else f.dm(x + 6f, Geo.dmTop(Geo.base(ry)), refusal, f.o().pitch(Geo.P).cells(33).dim());
        float ky = y + gh + 5f, kw = (Geo.CW - 6f) / 2f;
        f.key(x, ky, kw, 24f, boardKey, f.o());
        if (latched) f.key(x + kw + 6f, ky, kw, 24f, castOffKey, f.o());
    }
}
