package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The terminal's Contracts (functional-task 7, TRM; mockup tk-terminal): the day's offers, operations
 * first, as a table counted in cells: the kind, the job, what it pays, the standing it brings (marked
 * when the giver isn't the sponsor, since only the sponsor's counts toward rank) and the time it
 * allows; a locked offer dim, its reason beside it. A delivery's pay is its fee and what the buyer pays
 * for the cargo, less what the cargo costs here when the market sells it, so it compares with the other
 * kinds. Sort orders it by the board, the pay or the time. Beside the list, the focused offer's full
 * terms: why it was posted, its giver, the pay as fee and cargo, the standing and with whom, the time it
 * allows and the day that makes it, why it is locked, and the pay big. Take takes it, and Held opens
 * the contracts held in the ship's book.
 *
 * <p>The game gives no reach for an offer (travel time and delta-v from here), so the table has no
 * REACH column and the detail no route.
 */
final class OffersTab extends ListTab {
    static final int TAKE = 0, SORT = 1, HELD = 2;
    static final String[] SORTS = {"BOARD", "PAY", "TIME"};
    /** The list's parts under its top: the column heads' baseline, the first row. */
    static final float HEADS = 34f, LIST = 42f;
    /** The columns' cells: the kind, the pay, the standing, the time; the job has the rest. */
    static final int TYPE = 8, PAY = 7, STDG = 5, TIME = 8;
    /** A value's column in the detail, in cells after its label's. */
    static final int VALUE = 11;

    /** "1,500 cr" but not "12.0 cr/unit"; "+4 standing"; what follows the standing, the time allowed. */
    private static final Pattern CREDITS = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?) cr(?!/)");
    private static final Pattern UNIT = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?) cr/unit");
    private static final Pattern STANDING = Pattern.compile("([+-][0-9]+) standing(?:, (.+))?$");
    private static final Pattern STEPS = Pattern.compile("^(\\d+) steps");
    private static final Pattern DELIVER = Pattern.compile("^Deliver (\\d+) (.+) to (.+)$");
    private static final Pattern TIME_LEFT = Pattern.compile("^([0-9.]+) (min|h|d)$");

    /** An offer as the table has it, parsed from the board's words. */
    static final class Offer {
        long id;
        int order;
        String title = "", kind = "", type = "", job = "", giver = "", terms = "", locked = "", note = "";
        /** The pay as the table shows it ("1,980", "1,500+"), as a number to sort by, and in words for the detail. */
        String pay = "", payWords = "";
        double payValue;
        /** The standing it brings ("+4"), and the time it allows ("2.0 D", "--") with seconds to sort by. */
        String standing = "", time = "";
        double timeLeft = Double.MAX_VALUE;
        boolean sponsor, operation;
        int steps;
    }

    final Array<Offer> offers = new Array<>();
    private final Array<Offer> pool = new Array<>();
    private final Glass.Cols cols = new Glass.Cols();
    private final String[] lines = new String[6];
    int sort;
    private MissionBoard board;
    private String tension = "";
    private int held, jobCells = 30, operations;
    private boolean otherGiver;
    private double now;

    OffersTab() {
        super("CONTRACTS", ClassicWindow.MISSIONS, "J");
        keys.define(3, 3);
        // Walked with ← and →, Enter pressing the one walked to: the game's letters stay the game's
        keys.set(TAKE, "TAKE", false).set(SORT, "SORT", true).set(HELD, "HELD", false);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && b.contracts && !b.missions.stationName.isEmpty();
    }

    @Override
    void refresh(Boards b, HudData d) {
        board = b.missions;
        now = d.ctx.simSeconds;
        long focusId = focused() == null ? Long.MIN_VALUE : focused().id;
        pool.addAll(offers);
        offers.clear();
        operations = 0;
        otherGiver = false;
        String sponsor = b.career.present ? b.career.sponsor : "";
        for (int i = 0; i < board.offers.size; i++) {
            Offer o = pool.size > 0 ? pool.pop() : new Offer();
            read(o, board.offers.get(i), i, sponsor, b);
            if (o.operation) operations++;
            if (!o.sponsor && !o.giver.isEmpty()) otherGiver = true;
            offers.add(o);
        }
        order();
        tension = board.tension == null ? "" : board.tension.toUpperCase(Locale.ROOT);
        held = board.held.size;
        pages.set(offers.size, shown);
        for (int i = 0; i < offers.size; i++) {
            if (offers.get(i).id == focusId) {
                pages.focus(i);
                break;
            }
        }
        focusMoved();
    }

    /** Offer {@code j}, the {@code i}th on the board, as the table has it. */
    static void read(Offer o, Job j, int i, String sponsor, Boards b) {
        o.id = j.id;
        o.order = i;
        o.title = j.title == null ? "" : j.title;
        o.giver = j.giver == null ? "" : j.giver;
        o.terms = j.terms == null ? "" : j.terms;
        o.locked = j.locked == null ? "" : j.locked;
        o.note = j.note == null ? "" : j.note;
        o.sponsor = !sponsor.isEmpty() && sponsor.equalsIgnoreCase(o.giver);
        kind(o);
        pay(o, b);
        Matcher s = STANDING.matcher(o.terms);
        boolean found = s.find();
        o.standing = found ? s.group(1) : "";
        String allowed = found ? s.group(2) : null;
        o.timeLeft = Double.MAX_VALUE;
        Matcher st = STEPS.matcher(o.terms);
        o.steps = st.find() ? Integer.parseInt(st.group(1)) : 0;
        if (o.operation || allowed == null) {
            o.time = o.steps > 0 ? o.steps + " STEPS" : "--";
        } else {
            String t = allowed.trim();
            o.time = t.toUpperCase(Locale.ROOT);
            o.timeLeft = seconds(t);
        }
    }

    /** The kind from the title's first word: "Deliver 120 Water to Highport" a DELIVERY of "120 WATER TO HIGHPORT". */
    static void kind(Offer o) {
        String t = o.title;
        int sp = t.indexOf(' ');
        String first = (sp < 0 ? t : t.substring(0, sp)).replace(":", "");
        String rest = sp < 0 ? "" : t.substring(sp + 1).trim();
        o.operation = false;
        switch (first.toLowerCase(Locale.ROOT)) {
            case "deliver":
                o.type = "DELIV.";
                o.kind = "DELIVERY";
                break;
            case "escort":
                o.type = o.kind = "ESCORT";
                break;
            case "bounty":
                o.type = o.kind = "BOUNTY";
                break;
            case "patrol":
                o.type = o.kind = "PATROL";
                break;
            case "rescue":
                o.type = o.kind = "RESCUE";
                break;
            case "recover":
                o.type = "RECOV.";
                o.kind = "RECOVERY";
                break;
            case "convoy":
                o.type = o.kind = "CONVOY";
                break;
            case "defend":
                o.type = o.kind = "DEFEND";
                break;
            case "find":
                o.type = o.kind = "RECON";
                break;
            case "raid":
                o.type = o.kind = "RAID";
                break;
            case "operation":
                o.type = "OPER.";
                o.kind = "OPERATION";
                o.operation = true;
                break;
            default:
                o.type = first.toUpperCase(Locale.ROOT);
                o.kind = o.type;
                rest = t;
        }
        o.job = rest.toUpperCase(Locale.ROOT);
    }

    /**
     * The pay: the credits the terms name; a delivery's with what the buyer pays for the cargo, less
     * what it costs here when the market sells it ("1,980"), else marked as more on top ("1,500+").
     */
    static void pay(Offer o, Boards b) {
        Matcher m = CREDITS.matcher(o.terms);
        double fee = m.find() ? number(m.group(1)) : 0;
        o.payValue = fee;
        o.pay = Glass.thousands(fee);
        o.payWords = Glass.thousands(fee) + " CR";
        if (o.terms.startsWith("up to")) o.payWords = "UP TO " + o.payWords + ", SHARED OUT BY HOW IT GOES";
        if (o.operation) o.payWords = o.payWords + " AT THE END";
        Matcher u = UNIT.matcher(o.terms);
        Matcher dv = DELIVER.matcher(o.title);
        if (!u.find() || !dv.find()) return;
        double unit = number(u.group(1)), qty = number(dv.group(1));
        String goods = dv.group(2);
        TradeLine here = b.trade.isDocked() ? line(b, goods) : null;
        double sale = unit * qty;
        if (here != null && here.available > 1e-6 && here.buyPrice > 0) {
            double cost = here.buyPrice * qty;
            o.payValue = fee + sale - cost;
            o.pay = Glass.thousands(o.payValue);
            o.payWords = String.format(Locale.ROOT, "%s FEE + %s FOR THE CARGO, LESS %s TO BUY IT HERE: %s CR", Glass.thousands(fee),
                    Glass.thousands(sale), Glass.thousands(cost), Glass.thousands(o.payValue));
        } else {
            o.payValue = fee + sale;
            o.pay = Glass.thousands(fee) + "+";
            o.payWords = String.format(Locale.ROOT, "%s FEE + %s FOR THE CARGO (%s CR A UNIT), NOT SOLD HERE", Glass.thousands(fee),
                    Glass.thousands(sale), MarketTab.price(unit));
        }
    }

    /** The market's line for {@code goods} by its name, or null. */
    private static TradeLine line(Boards b, String goods) {
        for (int i = 0; i < b.trade.lines.size; i++) {
            TradeLine l = b.trade.lines.get(i);
            if (l.name != null && l.name.equalsIgnoreCase(goods)) return l;
        }
        return null;
    }

    static double number(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "2.0 d", "5.5 h", "30 min" in seconds; "arriving" none. */
    static double seconds(String t) {
        Matcher m = TIME_LEFT.matcher(t.trim().toLowerCase(Locale.ROOT));
        if (!m.find()) return t.trim().equalsIgnoreCase("arriving") ? 0 : Double.MAX_VALUE;
        double v = number(m.group(1));
        switch (m.group(2)) {
            case "min":
                return v * 60;
            case "h":
                return v * 3600;
            default:
                return v * 86400;
        }
    }

    private static final Comparator<Offer> BY_BOARD = (a, b) -> a.operation != b.operation ? (a.operation ? -1 : 1) : Integer.compare(a.order, b.order);
    private static final Comparator<Offer> BY_PAY = (a, b) -> a.payValue != b.payValue ? Double.compare(b.payValue, a.payValue) : BY_BOARD.compare(a, b);
    private static final Comparator<Offer> BY_TIME = (a, b) -> a.timeLeft != b.timeLeft ? Double.compare(a.timeLeft, b.timeLeft) : BY_BOARD.compare(a, b);

    /** Operations first, then the board's order; or by pay, most first; or by the time allowed, least first. */
    void order() {
        offers.sort(sort == 1 ? BY_PAY : sort == 2 ? BY_TIME : BY_BOARD);
    }

    /** The offer in focus, or null. */
    Offer focused() {
        int f = pages.focus();
        return f >= 0 && f < offers.size ? offers.get(f) : null;
    }

    @Override
    void place(Tab.Place p) {
        super.place(p);
        listX = p.lx;
        listCells = Glass.cells(p.lw());
        jobCells = Math.max(10, listCells - 1 - TYPE - PAY - 1 - STDG - 1 - TIME - 1);
        listY = p.ly + LIST;
        // Under the list: the pager, the tension and the contracts held
        shown = fit(p.lb - 3f * Glass.ROW);
        pages.set(offers.size, shown);
    }

    @Override
    void keysFor(int focus) {
        Offer o = focused();
        keys.set(TAKE, "TAKE", o != null && o.locked.isEmpty() && held < StationTerminal.MAX_HELD);
        keys.set(SORT, "SORT", offers.size > 1);
        keys.set(HELD, "HELD", held > 0);
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        if (k == SORT) {
            Offer o = focused();
            sort = (sort + 1) % SORTS.length;
            order();
            if (o != null) pages.focus(offers.indexOf(o, true));
            focusMoved();
            return;
        }
        if (k == HELD) {
            windows.show(windows.book, windows.book.held);
            return;
        }
        Offer o = focused();
        if (d == null || d.missions == null || o == null) return;
        windows.say(d.missions.accept(o.id));
        windows.refresh();
    }

    // ------------------------------------------------------------------ painting

    @Override
    void paint(Filament f, float time) {
        float x0 = at.lx, x1 = at.lr, top = at.ly;
        String head = offers.size == 0 ? "OFFERS" : "OFFERS  " + offers.size + " TODAY" + (operations == 0 ? ""
                : operations == 1 ? ", 1 OPERATION" : ", " + operations + " OPERATIONS");
        Glass.text(f, x0 + Glass.CW, top, head, Drive.NORM);
        String sorted = "SORT " + SORTS[sort];
        Glass.right(f, x1, top, sorted, Drive.DIM);
        float hy = top + HEADS - 8f;
        int c = 1;
        Glass.label(f, x0 + c * Glass.CW, hy, "TYPE");
        c += TYPE;
        Glass.label(f, x0 + c * Glass.CW, hy, "JOB");
        c += jobCells + PAY;
        Glass.labelRight(f, x0 + c * Glass.CW - Glass.P, hy, "PAY");
        c += 1 + STDG;
        Glass.labelRight(f, x0 + c * Glass.CW - Glass.P, hy, "STDG");
        c += 1;
        Glass.label(f, x0 + c * Glass.CW, hy, "TIME");
        if (offers.size == 0) Glass.text(f, x0 + Glass.CW, listY, "NO OFFERS HERE TODAY", Drive.DIM);
        for (int i = pages.first(); i < pages.end(); i++) {
            Offer o = offers.get(i);
            String stdg = o.standing + (o.sponsor || o.standing.isEmpty() ? "" : "*");
            cols.clear().left(o.type, TYPE).left(o.job, jobCells).right(o.locked.isEmpty() ? o.pay : "LOCKED", PAY)
                    .gap(1).right(stdg, STDG).gap(1).left(o.time, TIME);
            Glass.row(f, x0, rowY(i), cols.toString(), listCells, i == pages.focus(), o.locked.isEmpty() ? Drive.NORM : Drive.DIM);
        }
        float y = listY + shown * rowStep + 3f;
        if (pages.pages() > 1) Glass.pager(f, x0 + Glass.CW, y, pages.page(), pages.pages());
        y += Glass.ROW;
        if (otherGiver && y + Glass.RH <= at.lb) {
            Glass.dim(f, x0 + Glass.CW, y, Glass.cut("* STANDING WITH ANOTHER FACTION: IT DOESN'T COUNT TOWARD RANK", listCells - 1));
            y += Glass.ROW;
        }
        if (!tension.isEmpty() && y + Glass.RH <= at.lb) {
            Glass.dim(f, x0 + Glass.CW, y, Glass.cut(tension, listCells - 1));
            y += Glass.ROW;
        }
        if (y + Glass.RH <= at.lb + Glass.ROW) {
            String h = held == 0 ? "NO CONTRACTS HELD" : "HOLDING " + held + " OF " + StationTerminal.MAX_HELD + ", IN THE SHIP'S BOOK";
            Glass.text(f, x0 + Glass.CW, y, Glass.cut(h, listCells - 1), held >= StationTerminal.MAX_HELD ? Drive.NORM : Drive.DIM);
        }
        paintDetail(f);
    }

    /** Beside the list: the focused offer's full terms, and its pay big at the foot. */
    private void paintDetail(Filament f) {
        Offer o = focused();
        float x0 = at.dx, x1 = at.dr, y = at.dy;
        int cells = Glass.cells(x1 - x0);
        if (o == null) {
            Glass.dim(f, x0, y, offers.size == 0 ? "NOTHING ON THE BOARD" : "↑ ↓ PICK AN OFFER");
            return;
        }
        // The kind and job in large type, two lines at most
        int big = Math.max(4, (int) Math.floor((x1 - x0 + Glass.P) / (Glass.CW * 2f / Glass.P)));
        int n = Glass.wrap(o.kind + ": " + o.job, big, lines, 2);
        for (int i = 0; i < n; i++) {
            f.dm(x0, y, lines[i], f.o().pitch(2f).full().noGhost());
            y += 24f;
        }
        y += 10f;
        y = field(f, x0, y, cells, "GIVER", o.giver.isEmpty() ? "" : o.giver.toUpperCase(Locale.ROOT) + (o.sponsor ? " (SPONSOR)" : ""), Drive.NORM);
        y = field(f, x0, y, cells, "PAY", o.payWords, Drive.NORM);
        String st = o.standing.isEmpty() ? "" : o.standing + (o.giver.isEmpty() ? "" : " WITH " + o.giver.toUpperCase(Locale.ROOT))
                + (o.sponsor || o.giver.isEmpty() ? "" : ": NOT YOUR SPONSOR, NO RANK");
        y = field(f, x0, y, cells, "STANDING", st, Drive.NORM);
        y = field(f, x0, y, cells, o.operation ? "STEPS" : "TIME", time(o), Drive.NORM);
        if (!o.note.isEmpty()) y = field(f, x0, y, cells, "WHY", o.note, Drive.DIM);
        if (!o.locked.isEmpty()) {
            Glass.label(f, x0, y + 9.5f, "LOCKED");
            int m = Glass.wrap(o.locked, cells - VALUE, lines, 3);
            for (int i = 0; i < m; i++) {
                Glass.text(f, x0 + VALUE * Glass.CW, y, lines[i], Drive.NORM, Phosphor.AMBER);
                y += SectionTab.LINE;
            }
            y += 6f;
        }
        // The decision's number, big: what it pays
        float by = Math.max(y + 10f, at.db - 34f);
        if (by + 30f > at.db + 8f) return;
        Glass.label(f, x0, by + 26f, "PAY");
        String digits = String.format(Locale.ROOT, "%d", Math.round(o.payValue));
        float fx = x0 + 40f;
        fx += f.seg7(fx, by, 26f, digits, f.o().cells(Math.max(5, digits.length())).norm()) + 6f;
        f.legend(fx, by + 26f, o.locked.isEmpty() ? "CR" : "CR, LOCKED", f.o().size(13f).dim().noGhost());
    }

    /** "2.0 D LEFT, BY DAY 6", or an operation's steps. */
    private String time(Offer o) {
        if (o.operation || o.timeLeft == Double.MAX_VALUE) return o.steps > 0 ? o.steps + " STEPS, THEN THE PAY" : "";
        if (o.timeLeft <= 0) return "DUE NOW";
        return o.time + " LEFT, BY DAY " + Fmt.day(now + o.timeLeft);
    }

    /** A label and its value wrapped in the cells after it; returns the y after them. */
    private float field(Filament f, float x0, float y, int cells, String label, String value, float level) {
        if (value == null || value.isEmpty() || y + Glass.RH > at.db) return y;
        Glass.label(f, x0, y + 9.5f, label);
        int m = Glass.wrap(value, Math.max(4, cells - VALUE), lines, 3);
        for (int i = 0; i < m && y + Glass.RH <= at.db; i++) {
            Glass.text(f, x0 + VALUE * Glass.CW, y, lines[i], level);
            y += SectionTab.LINE;
        }
        return y + 6f;
    }
}
