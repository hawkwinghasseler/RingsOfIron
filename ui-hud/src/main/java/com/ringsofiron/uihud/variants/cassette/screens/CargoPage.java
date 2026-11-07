package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The hold (Context's M2 Cargo): what the bays carry, each commodity with its units, its volume, its
 * mass and the bays it fills, the salvaged parts aboard under their own heading, and the hold used of
 * its capacity on a level gauge. Read from the ship's hold itself, so it shows in flight as well as
 * docked; the market's names for what it trades are used where there is one.
 */
final class CargoPage extends ListPage {
    /** What the hold's ids for salvaged parts start with (the game's {@code Salvage.PART}). */
    static final String PART = "part:";
    private static final int HEAD = 0, GOODS = 1;
    private static final String[] HEADS = {"GOODS", "SALVAGED PARTS"};
    private Boards b;
    private CargoHoldComponent hold;
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    /** The list: each row a heading (its index in {@link #HEADS}) or a line. */
    private final Array<Object> rowsOf = new Array<>();
    /** Whether it drew in a side panel last frame (the slate): the units, volume and mass from the tube's right. */
    private boolean narrow;

    /** One commodity aboard. */
    static final class Line {
        String id = "", name = "";
        double units, volume, mass;
        int bays;
        boolean part;
    }

    CargoPage() {
        this(ClassicWindow.CREW);
    }

    /** The hold on a tab standing for {@code window}: the slate's (I), or the helm console's panel's. */
    CargoPage(ClassicWindow window) {
        super("CARGO", "HOLD", window);
    }

    @Override
    boolean offered() {
        return hold != null && hold.bayTileCount() > 0;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        Entity ship = b.data == null ? null : b.data.ctx.ship;
        hold = ship == null ? null : ship.getComponent(CargoHoldComponent.class);
        read(hold, b.trade, lines, pool);
        rowsOf.clear();
        boolean goods = false, parts = false;
        for (int i = 0; i < lines.size; i++) {
            if (lines.get(i).part) parts = true;
            else goods = true;
        }
        if (goods) {
            rowsOf.add(HEADS[0]);
            for (int i = 0; i < lines.size; i++) if (!lines.get(i).part) rowsOf.add(lines.get(i));
        }
        if (parts) {
            rowsOf.add(HEADS[1]);
            for (int i = 0; i < lines.size; i++) if (lines.get(i).part) rowsOf.add(lines.get(i));
        }
        keepSelection();
    }

    /** The hold's stacks summed by commodity, goods by name and then the parts. */
    static void read(CargoHoldComponent hold, TradeBoard trade, Array<Line> out, Array<Line> pool) {
        pool.addAll(out);
        out.clear();
        if (hold == null || hold.stacks == null) return;
        for (int i = 0; i < hold.stacks.length; i++) {
            CargoHoldComponent.CargoStack s = hold.stacks[i];
            if (s == null || s.commodity == null || s.units <= 1e-9) continue;
            Line l = null;
            for (int k = 0; k < out.size; k++) if (out.get(k).id.equals(s.commodity)) l = out.get(k);
            if (l == null) {
                l = pool.size > 0 ? pool.pop() : new Line();
                l.id = s.commodity;
                l.part = s.commodity.startsWith(PART);
                l.name = name(s.commodity, trade);
                l.units = l.volume = l.mass = 0;
                l.bays = 0;
                out.add(l);
            }
            l.units += s.units;
            l.volume += s.volume();
            l.mass += s.mass();
            l.bays++;
        }
        out.sort((a, c) -> a.part != c.part ? (a.part ? 1 : -1) : a.name.compareTo(c.name));
    }

    /** What to call a commodity: the market's name for it where it trades it, else its id in words. */
    static String name(String id, TradeBoard trade) {
        if (trade != null) {
            for (int i = 0; i < trade.lines.size; i++) {
                if (trade.lines.get(i).commodityId.equals(id) && !trade.lines.get(i).name.isEmpty()) return trade.lines.get(i).name;
            }
        }
        boolean part = id.startsWith(PART);
        String s = (part ? id.substring(PART.length()) : id).replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) return id;
        s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return part ? "Salvaged " + s.toLowerCase(Locale.ROOT) : s;
    }

    @Override
    int count() {
        return rowsOf.size;
    }

    @Override
    boolean selectable(int i) {
        return rowsOf.get(i) instanceof Line;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        // In a side panel the figures end at the tube's right, the name taking what is left
        int cMass = narrow ? L.cols - 1 : 84, cVol = narrow ? L.cols - 10 : 68, cUnits = narrow ? L.cols - 20 : 52;
        Object o = rowsOf.get(i);
        if (o instanceof String) {
            L.at(p, 1, r, (String) o, DIM);
            if (HEADS[0].equals(o) || i == 0) {
                L.right(p, cUnits, r, "UNITS", DIM);
                L.right(p, cVol, r, "VOLUME", DIM);
                L.right(p, cMass, r, "MASS", DIM);
                if (!narrow && L.cols >= 100) L.at(p, 88, r, "BAYS", DIM);
            }
            return;
        }
        Line l = (Line) o;
        put(p, s, 2, r, Ledger.cut(narrow ? Fmt.upper(l.name) : l.name, narrow ? cUnits - 9 : 40), false);
        put(p, s, cUnits, r, Fmt.units(l.units), true);
        put(p, s, cVol, r, volume(l.volume), true);
        put(p, s, cMass, r, mass(l.mass), true);
        if (!narrow && L.cols >= 100) put(p, s, 88, r, l.bays == 1 ? "1 BAY" : l.bays + " BAYS", false);
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, boolean right) {
        if (right) {
            if (s) L.revRight(p, c, r, t);
            else L.right(p, c, r, t, AMBER);
        } else if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, AMBER);
    }

    /** "12.0 M³". */
    static String volume(double m3) {
        return String.format(Locale.ROOT, "%.1f M³", m3);
    }

    /** "850 KG" under a tonne, "1.6 T" above. */
    static String mass(double kg) {
        if (kg < 1000) return String.format(Locale.ROOT, "%.0f KG", kg);
        return String.format(Locale.ROOT, "%.1f T", kg / 1000.0);
    }

    @Override
    void keys() {
    }

    @Override
    void press(int k) {
    }

    @Override
    String empty() {
        return "THE HOLD IS EMPTY";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        narrow = w < 700f;
        float bx = x + 8f, by = y + (narrow ? 4f : 6f), bw = w - 16f, bh = h - (narrow ? 8f : 12f);
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = narrow ? Ledger.colsIn(bw - 36f) : Math.max(90, Ledger.colsIn(bw - 42f));
        int rows = Math.max(narrow ? 6 : 10, Ledger.rowsIn(bh - (narrow ? 70f : 100f)));
        L.set(bx + (narrow ? 12f : 16f), by + (narrow ? 10f : 12f), cols, rows);
        L.under(p, false);
        double used = hold == null ? 0 : hold.usedVolume(), cap = hold == null ? 0 : hold.capacity();
        double kg = hold == null ? 0 : hold.mass();
        int bays = 0;
        for (int i = 0; i < rowsOf.size; i++) if (rowsOf.get(i) instanceof Line) bays += ((Line) rowsOf.get(i)).bays;
        String of = volume(used).replace(" M³", "") + " OF " + volume(cap);
        title(p, "HOLD", narrow ? of + " · " + bays + "/" + (hold == null ? 0 : hold.bayTileCount()) + " BAYS"
                : of + " · " + mass(kg) + " · " + bays + " OF " + (hold == null ? 0 : hold.bayTileCount()) + " BAYS");
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        // The hold used of its capacity, on a level gauge under the tube
        float gy = L.y + L.h + 28f, gw = Math.min(narrow ? L.w - 60f : 520f, L.w);
        p.legend("HOLD USED", L.x, gy - 8f, Yard.INK);
        float frac = cap <= 1e-9 ? 0f : (float) Math.min(1.0, used / cap);
        p.level(L.x, gy, gw, 16f, frac, null, null);
        p.textR(YardFonts.Face.LEGEND, 12f, Math.round(frac * 100f) + "%", L.x + gw + 46f, gy + 12f, Yard.INK);
    }
}
