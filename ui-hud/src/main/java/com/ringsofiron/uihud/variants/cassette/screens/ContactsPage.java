package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.ContactProvider.State;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The tactical board's Contacts tab (the Stations spec's 10.2): every contact the sensors hold, a missile
 * coming at the ship first, then any other threat, the hostiles, what is locked or set as the course, the
 * rest; each by name, what it is or the state it is in, its range, and a missile's time to reach the ship or
 * the rate it closes, with LOCK and NAV where they hold. The selected contact's bearing, and why it is
 * disabled, are on the line under the list.
 */
final class ContactsPage extends ListPage {
    /** The contacts as of the last refresh, copied: the frame's rows are reused. */
    final Array<Contact> list = new Array<>();
    private final Array<Contact> pool = new Array<>();
    private boolean own;

    ContactsPage() {
        super("CONTACTS", "TAC", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        HudData d = b.data;
        own = d != null && ConsolePanels.ship(d) == d.ctx.ship;
        pool.addAll(list);
        list.clear();
        if (d == null || !own) return;
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            Contact c = d.contactFrame.contacts.get(i);
            if (Float.isNaN(c.range) || c.kind == Kind.WAYPOINT) continue;
            list.add(copy(c, pool.size > 0 ? pool.pop() : new Contact()));
        }
        list.sort((a, c) -> rank(a) != rank(c) ? rank(a) - rank(c) : Float.compare(a.range, c.range));
        keepSelection();
    }

    private static Contact copy(Contact c, Contact to) {
        to.entity = c.entity;
        to.label = c.label;
        to.kind = c.kind;
        to.relation = c.relation;
        to.state = c.state;
        to.disabledReason = c.disabledReason;
        to.range = c.range;
        to.bearingDeg = c.bearingDeg;
        to.closing = c.closing;
        to.locked = c.locked;
        to.nav = c.nav;
        to.threat = c.threat;
        return to;
    }

    // --- The words -----------------------------------------------------------------------------

    /** Its place in the list: a missile coming in, a threat, a hostile, locked or the course, the rest, other missiles. */
    static int rank(Contact c) {
        if (c.kind == Kind.MISSILE && c.threat) return 0;
        if (c.threat) return 1;
        if (c.relation == Relation.HOSTILE && c.kind != Kind.DERELICT) return 2;
        if (c.locked || c.nav) return 3;
        return c.kind == Kind.MISSILE ? 5 : 4;
    }

    /** What it is, or the state it is in when that matters more: "HOSTILE", "STRUCK", "STATION", "MISSILE". */
    static String what(Contact c) {
        switch (c.kind) {
            case MISSILE:
                return c.threat ? "INBOUND" : "MISSILE";
            case STATION:
                return "STATION";
            case DERELICT:
                return "DERELICT";
            case EVA:
                return "EVA";
            default:
                break;
        }
        if (c.state != State.FIT) return c.state.name();
        switch (c.relation) {
            case HOSTILE:
                return "HOSTILE";
            case FRIENDLY:
                return "FRIENDLY";
            case NEUTRAL:
                return "NEUTRAL";
            default:
                return "UNKNOWN";
        }
    }

    /** "850 M", "2.8 KM", "1,204 KM". */
    static String range(float m) {
        if (Float.isNaN(m) || Float.isInfinite(m)) return "--";
        float a = Math.abs(m);
        if (a < 1000f) return Math.round(a) + " M";
        if (a < 100_000f) return String.format(Locale.ROOT, "%.1f KM", a / 1000f);
        return YardText.thousands(a / 1000f) + " KM";
    }

    /** Seconds until a missile coming in reaches the ship at the rate it closes; NaN for anything else, or one ten minutes off. */
    static float hitsIn(Contact c) {
        if (c.kind != Kind.MISSILE || !c.threat || Float.isNaN(c.closing) || c.closing <= 0f) return Float.NaN;
        float t = c.range / c.closing;
        return t < 600f ? t : Float.NaN;
    }

    /**
     * Its CLOSE column, six characters at most: a missile coming in, its time to reach the ship ("0:13");
     * else the metres a second it closes ("+104", "-1.2K" opening), nothing for a station or a contact that
     * holds its range.
     */
    static String tail(Contact c) {
        float t = hitsIn(c);
        if (!Float.isNaN(t)) return YardText.clock(t);
        if (Float.isNaN(c.closing) || Math.abs(c.closing) < 0.5f || c.kind == Kind.STATION) return "";
        int v = Math.round(c.closing);
        return Math.abs(v) < 1000 ? String.format(Locale.ROOT, "%+d", v) : String.format(Locale.ROOT, "%+.1fK", c.closing / 1000f);
    }

    /** LOCK, NAV, both (LK+NV) or neither. */
    static String tags(Contact c) {
        return c.locked && c.nav ? "LK+NV" : c.locked ? "LOCK" : c.nav ? "NAV" : "";
    }

    /** The selected contact's line: its bearing off north, and why it is disabled when it is. */
    static String detail(Contact c) {
        StringBuilder b = new StringBuilder();
        b.append(c.label.isEmpty() ? what(c) : Fmt.upper(c.label));
        float t = hitsIn(c);
        if (!Float.isNaN(t)) b.append(", HITS IN ").append(YardText.clock(t));
        else if (!Float.isNaN(c.closing) && Math.abs(c.closing) >= 0.5f && c.kind != Kind.STATION) {
            b.append(c.closing > 0f ? ", CLOSING " : ", OPENING ").append(Math.round(Math.abs(c.closing))).append(" M/S");
        }
        if (!Float.isNaN(c.bearingDeg)) b.append(String.format(Locale.ROOT, ", BEARING %03d°", Math.floorMod(Math.round(c.bearingDeg), 360)));
        if (!c.disabledReason.isEmpty()) b.append(", ").append(Fmt.upper(c.disabledReason));
        return b.toString();
    }

    /** How a contact's row is inked: red for a missile coming in, amber for a hostile or what is locked or the course. */
    static Color ink(Contact c) {
        if (c.kind == Kind.MISSILE && c.threat) return Yard.P_RED;
        if (c.threat || (c.relation == Relation.HOSTILE && c.kind != Kind.DERELICT) || c.locked || c.nav) return AMBER;
        return DIM;
    }

    /** Missiles coming at the ship. */
    int inbound() {
        int n = 0;
        for (int i = 0; i < list.size; i++) if (list.get(i).kind == Kind.MISSILE && list.get(i).threat) n++;
        return n;
    }

    // --- The list ------------------------------------------------------------------------------

    @Override
    int count() {
        return list.size;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Contact c = list.get(i);
        Color ink = ink(c);
        String name = c.label.isEmpty() ? what(c) : Fmt.upper(c.label);
        put(p, s, 1, r, Ledger.cut(name, 12), ink);
        put(p, s, 14, r, Ledger.cut(what(c), 8), ink);
        if (s) L.revRight(p, 31, r, range(c.range));
        else L.right(p, 31, r, range(c.range), ink);
        put(p, s, 33, r, Ledger.cut(tail(c), 6), ink);
        String t = tags(c);
        if (!t.isEmpty()) {
            if (s) L.revRight(p, L.cols - 1, r, t);
            else L.right(p, L.cols - 1, r, t, AMBER);
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
    }

    @Override
    void press(int k) {
    }

    @Override
    String empty() {
        return "NO CONTACTS";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 24f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        int in = inbound();
        title(p, "CONTACTS", in > 0 ? in + " INBOUND" : list.size == 1 ? "1 CONTACT" : list.size + " CONTACTS");
        L.at(p, 1, 1, "CONTACT", DIM);
        L.at(p, 14, 1, "WHAT", DIM);
        L.right(p, 31, 1, "RANGE", DIM);
        L.at(p, 33, 1, "CLOSE", DIM);
        drawList(p, hits, 2, rows - 3);
        L.rule(p, rows - 2);
        if (sel >= 0 && sel < list.size) L.at(p, 1, rows - 1, Ledger.cut(detail(list.get(sel)), cols - 2), ink(list.get(sel)) == DIM ? DIM : AMBER);
        L.over(p);
    }
}
