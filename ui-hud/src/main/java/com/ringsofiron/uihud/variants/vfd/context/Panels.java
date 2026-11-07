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
 * its port, its owner and the captain's standing with it as the game's signed number (+0 on day 0),
 * and its services each behind its key cap, the ones it does not offer left out, the infirmary only
 * while it treats one of the crew or has billed for one since docking; then the undock key. X2,
 * boarding: the other ship with LATCHED or EVA, its state on a row of its own ("TAKEN: CREW
 * SURRENDERED", "TAKEN: UNDEFENDED"), the party, the salvage (the parts worth taking and what they
 * sell for, until one is marked), the cargo when it has any, and the prisoners held against the
 * berths free, or else why no party can go, in six rows of glass with the two keys printed with their
 * actions under it. X2 is for a beaten ship or one of ours: a fit one in reach is still a fight.
 */
final class Panels {
    static final int X1_ROWS = 10;
    static final float X2_GLASS = 6 * Geo.ROW + 6f, X2_H = X2_GLASS + 5f + 24f;
    static final String[] BY = {"LATCHED", "EVA"};

    // X1
    boolean station;
    String name = "", port = "", owner = "", standing = "";
    final String[] keys = new String[X1_ROWS], words = new String[X1_ROWS];
    int lines;
    /** The undock key printed with its action. */
    String undockKey = "U  UNDOCK";

    // X2
    boolean boarding, latched;
    String craft = "", state = "", party = "", partyState = "", boardKey = "K", castOffKey = "U";
    /** Its rows under the state, each only when it has something to say: PARTY (none for a craft nobody defended). */
    boolean partyRow;
    /** SALV: until a part is marked, how many are worth taking and what a station pays; then how many are marked and off. */
    boolean salvRow, anyMarked;
    String worth = "", worthCr = "", marked = "", off = "";
    /** CARGO, when it has any aboard: the lots, and how many are coming across. */
    boolean cargoRow;
    String lots = "", coming = "";
    /** HELD: the prisoners held aboard it, and the berths free for them, since only as many come across. */
    boolean prisoners;
    String held = "", berths = "";
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
        standing = standing(st.standing);
        lines = 0;
        if (st.market) line(key(d, "TRADE", "T"), "MARKET");
        if (st.yard) line(key(d, "YARD", "Y"), "YARD");
        if (st.contracts) line(key(d, "MISSIONS", "J"), "MISSIONS");
        // Shipyard and hire have no keys of their own: they are tabs in the terminal
        if (st.shipyard) line("", "SHIPYARD: SHIPS FOR SALE");
        if (st.hiring) line("", "HIRE: CREW LOOKING FOR A BERTH");
        String sick = infirmary(st);
        if (!sick.isEmpty()) line("", sick);
        if (!st.refusal.isEmpty()) line("", Rows.words(st.refusal, 33));
        undockKey = key(d, "UNDOCK", "U") + "  UNDOCK";
    }

    /** The game's number, signed as the game signs it ("+0", "-12"): it names no levels. */
    static String standing(float value) {
        return Float.isNaN(value) ? "" : String.format(Locale.ROOT, "%+d", Math.round(value));
    }

    /**
     * The infirmary's line, only while it has one of the crew to treat ("INFIRMARY: TREATING ADA") or
     * has billed for one since docking ("INFIRMARY: 1 TREATED · 150 CR"); else none.
     */
    static String infirmary(StationInfo st) {
        if (!st.infirmary) return "";
        if (st.treating.size == 1) return Fmt.cut("INFIRMARY: TREATING " + st.treating.first().toUpperCase(Locale.ROOT), 33);
        if (st.treating.size > 1) return "INFIRMARY: TREATING " + st.treating.size;
        if (st.treated > 0) return Fmt.cut("INFIRMARY: " + st.treated + " TREATED · " + Cautions.group(st.treatedCost) + " CR", 33);
        return "";
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
        state = Fmt.cut(stateWords(b), 25);
        partyRow = !b.undefended;
        party = Integer.toString(Math.min(9, b.aboard));
        partyState = b.aboard <= 0 ? "" : b.taken ? "HOLDING" : b.defenders > 0 ? "FIGHTING" : "SEARCHING";
        // Why no party can go, in whole words on the last row, while it has no prisoners to name
        String why = b.aboard > 0 || b.taken ? "" : b.boardRefusal;
        if (!why.equals(refusalOf)) {
            refusalOf = why;
            refusal = Rows.words(why, 33);
        }
        int m = 0, o = 0, n = 0;
        double cr = 0;
        for (Part p : b.parts) {
            if (p.marked) m++;
            if (p.off) o++;
            if (!p.off) {
                n++;
                cr += p.value;
            }
        }
        salvRow = b.parts.size > 0;
        anyMarked = m > 0;
        worth = Integer.toString(Math.min(99, n));
        worthCr = Cautions.group(cr) + " CR";
        marked = Integer.toString(Math.min(9, m));
        off = Integer.toString(Math.min(9, o));
        int across = 0;
        for (Cargo c : b.cargo) if (c.onOrder > 0) across++;
        cargoRow = b.cargo.size > 0;
        lots = Integer.toString(Math.min(9, b.cargo.size));
        coming = Integer.toString(Math.min(9, across));
        prisoners = b.prisoners > 0;
        held = Integer.toString(Math.min(9, b.prisoners));
        berths = Integer.toString(Math.min(9, b.berthsFree));
        boardKey = key(d, "BOARD", "K") + "  BOARDING";
        castOffKey = key(d, "UNDOCK", "U") + "  CAST OFF";
    }

    /** Its state on its own row: how it was taken, else the game's words ("Disabled: reactor down", "being boarded"). */
    static String stateWords(BoardingBoard b) {
        if (b.taken) return b.undefended ? "TAKEN: UNDEFENDED" : b.prisoners > 0 ? "TAKEN: CREW SURRENDERED" : "TAKEN";
        return b.state.toUpperCase(Locale.ROOT);
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
        // Dot matrix, not digits: the seven-segment ROM has no plus sign
        float cx = Rows.lab(f, x + 6f, b, "STANDING");
        Kit.fits(Rows.txt(f, cx, b, standing, 5) - 6f, x + Geo.CW - 4f, "X1 standing");
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
        float last = y + 3f + 5 * Geo.ROW, cx;
        // The rows that have something to say, in order, closing up
        if (partyRow) {
            ry += Geo.ROW;
            b = Geo.base(ry);
            f.legend(x + 6f, b, "PARTY", f.o().dim());
            cx = Rows.num(f, vx, b, party, 1);
            cx = Rows.lab(f, cx, b, "ABOARD") + 4f;
            f.dm(cx, Geo.dmTop(b), partyState, f.o().pitch(Geo.P).cells(8).norm());
        }
        if (salvRow) {
            ry += Geo.ROW;
            b = Geo.base(ry);
            f.legend(x + 6f, b, "SALV", f.o().dim());
            if (anyMarked) {
                cx = Rows.num(f, vx, b, marked, 1);
                cx = Rows.lab(f, cx, b, "PARTS MARKED") + 6f;
                cx = Rows.num(f, cx, b, off, 1);
                Rows.lab(f, cx, b, "OFF");
            } else {
                cx = Rows.num(f, vx, b, worth, 2);
                cx = Rows.lab(f, cx, b, "PARTS") + 6f;
                Rows.txt(f, cx, b, worthCr, 12);
            }
        }
        if (cargoRow && ry < last) {
            ry += Geo.ROW;
            b = Geo.base(ry);
            f.legend(x + 6f, b, "CARGO", f.o().dim());
            cx = Rows.num(f, vx, b, lots, 1);
            cx = Rows.lab(f, cx, b, "LOTS") + 6f;
            cx = Rows.num(f, cx, b, coming, 1);
            Rows.lab(f, cx, b, "COMING ACROSS");
        }
        if (prisoners && ry < last) {
            ry += Geo.ROW;
            b = Geo.base(ry);
            f.legend(x + 6f, b, "HELD", f.o().dim());
            cx = Rows.num(f, vx, b, held, 1);
            cx = Rows.lab(f, cx, b, "·") + 2f;
            cx = Rows.num(f, cx, b, berths, 1);
            Rows.lab(f, cx, b, "BERTHS FREE");
        } else if (!refusal.isEmpty()) {
            f.dm(x + 6f, Geo.dmTop(Geo.base(last)), refusal, f.o().pitch(Geo.P).cells(33).dim());
        }
        float ky = y + gh + 5f, kw = (Geo.CW - 6f) / 2f;
        f.key(x, ky, kw, 24f, boardKey, f.o());
        if (latched) f.key(x + kw + 6f, ky, kw, 24f, castOffKey, f.o());
    }
}
