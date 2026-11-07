package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.Kind;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The Plot's TARGETS page (functional-stations 10.2), as FILAMENT × Context's map has its Target and
 * traffic pane: the Nav card (its
 * name, what it is and how far, where it is and when the trip arrives) with the key that turns to the
 * next target; the transfer's keys, Plan, Accept and Discard, with what is proposed; the contacts round
 * the body the map shows, sorted by distance or by kind, a click making one the navigation target;
 * and at the pane's foot the map's layers, each a key with its lamp lit while the layer shows: the
 * stations, the haulers, the pirates, the derelicts, the contracts' places, and the factions' tension,
 * which leaves the map's face to be a layer here.
 */
final class PlotTraffic {
    static final float W = 320f;
    static final int CELLS = 50;
    /** The contacts the list can hold. */
    static final int MAX_CONTACTS = 60;
    /** The layers, in their keys' order. */
    static final int STATIONS = 0, HAULERS = 1, PIRATES = 2, DERELICTS = 3, OBJECTIVES = 4, TENSION = 5, LAYERS = 6;
    static final String[] LAYER_WORD = {"STATIONS", "HAULERS", "PIRATES", "DERELICTS", "OBJECTIVES", "TENSION"};
    static final ContactKind[] LAYER_KIND = {ContactKind.STATION, ContactKind.HAULER, ContactKind.HOSTILE, ContactKind.OTHER, null, null};
    /** Click ids, from the tab's. */
    static final int NEXT_TARGET = Panels.PAGE_IDS + 60, PLAN = NEXT_TARGET + 1, ACCEPT = NEXT_TARGET + 2, DISCARD = NEXT_TARGET + 3,
            SORT = NEXT_TARGET + 4, LAYER = NEXT_TARGET + 10, CONTACT = NEXT_TARGET + 20;

    float x, y, w, h;
    // The Nav card
    String navName = "", navLine = "", navWhere = "";
    boolean hasNav;
    String kNext = "Q";
    // The transfer
    String kPlan = ";", kAccept = "ENTER", kDiscard = "BKSP", transferNote = "";
    boolean canPlan, proposed;
    // The contacts
    boolean byKind;
    int contacts, first, rows = 1;
    final String[] contactRow = new String[MAX_CONTACTS];
    final String[] contactId = new String[MAX_CONTACTS];
    final float[] contactLevel = new float[MAX_CONTACTS];
    private float rowsY;
    private final Array<Contact> listed = new Array<>();
    private final double[] distance = new double[512];
    // The layers: what shows, as the keys' lamps say
    final boolean[] layer = {true, true, true, true, true, false};
    private final Glass.Cols cols = new Glass.Cols();
    private final String[] capsBuf = new String[1];
    /** Where each transfer key's words end, as last painted, for their click boxes. */
    private final float[] keyEnd = new float[3];

    // ------------------------------------------------------------------ reading

    void read(HudData d, OrbitPath p, TrafficFrame t) {
        readNav(d);
        readTransfer(d);
        readContacts(p, t);
    }

    private void readNav(HudData d) {
        NavReadout n = d.navReadout;
        kNext = PlotPlan.cap(d.key("NAV_TARGET"));
        hasNav = n.present && n.name != null && !n.name.isEmpty();
        if (!hasNav) {
            navName = "NONE";
            navLine = "PICK ONE: " + kNext + ", OR A CONTACT";
            navWhere = "";
            return;
        }
        navName = Fmt.name(n.name, 21);
        String what = what(n);
        String dist = Double.isNaN(n.distance) ? "" : Fmt.rangeText(n.distance);
        navLine = Glass.cut(what.isEmpty() ? dist : dist.isEmpty() ? what : what + " · " + dist, CELLS);
        navWhere = where(n);
    }

    /**
     * The Nav card's last line, as H2's NAV lines have it: for a target round another body, that body and
     * the plan's arrival; for one round this body, closing and the closest approach, as much as fits.
     */
    static String where(NavReadout n) {
        if (n.around != null && !n.around.isEmpty()) {
            String at = "AT " + PlotPlan.the(n.around);
            return Float.isNaN(n.arrivalIn) ? at : Glass.cut(at + " · ARRIVAL T-" + PlotEvents.timeText(n.arrivalIn), CELLS);
        }
        if (!Float.isNaN(n.arrivalIn)) return "ARRIVAL T-" + PlotEvents.timeText(n.arrivalIn);
        String rate = Float.isNaN(n.closing) ? "" : (n.closing < 0f ? "OPENING " : "CLOSING ") + Math.round(Math.abs(n.closing)) + " M/S";
        String cpa = Double.isNaN(n.cpaDistance) ? "" : "CLOSEST " + Fmt.rangeText(n.cpaDistance);
        if (rate.isEmpty() || cpa.isEmpty()) return Glass.cut(rate + cpa, CELLS);
        String both = rate + " · " + cpa;
        if (both.length() <= CELLS) return both;
        String timed = Float.isNaN(n.cpaIn) || n.cpaIn <= 0f ? cpa : cpa + " T-" + PlotEvents.timeText(n.cpaIn);
        return Glass.cut(timed, CELLS);
    }

    /** What the target is, in a word or two: the station's kind ("ICE MINE"), a ship as it stands to the player, a wreck. */
    static String what(NavReadout n) {
        switch (n.kind) {
            case STATION:
                return n.station.type != null && !n.station.type.isEmpty() ? n.station.type.toUpperCase(Locale.ROOT) : "STATION";
            case SHIP:
                return n.relation == Relation.HOSTILE ? "HOSTILE SHIP" : n.relation == Relation.FRIENDLY ? "FRIENDLY SHIP" : "SHIP";
            case WRECK:
                return "WRECK";
            case BODY:
                return "BODY";
            default:
                return n.kind == Kind.POINT ? "" : n.kind.name();
        }
    }

    private void readTransfer(HudData d) {
        NavReadout n = d.navReadout;
        kPlan = PlotPlan.cap(d.key("PLAN_TO_TARGET"));
        kAccept = PlotPlan.cap(d.key("ACCEPT_PLAN"));
        kDiscard = PlotPlan.cap(d.key("DISCARD_PLAN"));
        canPlan = hasNav;
        proposed = n.proposal;
        if (proposed) {
            // The arrival when it fits: the Nav card gives it too
            transferNote = "PROPOSED: " + Glass.thousands(n.planDeltaV) + " M/S";
            String arrives = Float.isNaN(n.arrivalIn) ? "" : ", ARRIVES T-" + PlotEvents.timeText(n.arrivalIn);
            if (transferNote.length() + arrives.length() <= CELLS) transferNote += arrives;
        } else if (!hasNav) {
            transferNote = "PLAN NEEDS A NAV TARGET";
        } else {
            transferNote = "ACCEPT, DISCARD: NOTHING PROPOSED";
        }
        transferNote = Glass.cut(transferNote, CELLS);
    }

    private void readContacts(OrbitPath p, TrafficFrame t) {
        listed.clear();
        contacts = 0;
        if (t == null || t.frameName.isEmpty()) return;
        // Distances from the ship: the traffic's own mark for it, else the path's
        double sx = Double.NaN, sy = Double.NaN;
        for (Contact c : t.contacts) {
            if (c.kind == ContactKind.PLAYER) {
                sx = c.x;
                sy = c.y;
                break;
            }
        }
        if (Double.isNaN(sx) && p != null && t.frameName.equalsIgnoreCase(p.bodyName)) {
            sx = p.shipX;
            sy = p.shipY;
        }
        for (Contact c : t.contacts) {
            if (c.kind == ContactKind.PLAYER || !shows(c) || listed.size >= distance.length) continue;
            listed.add(c);
        }
        for (int i = 0; i < listed.size; i++) {
            Contact c = listed.get(i);
            distance[i] = Double.isNaN(sx) ? Double.NaN : Math.hypot(c.x - sx, c.y - sy);
        }
        // Sorted in place: by kind first when asked, then by distance
        int n = listed.size;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            if (byKind) {
                int k = Integer.compare(listed.get(a).kind.ordinal(), listed.get(b).kind.ordinal());
                if (k != 0) return k;
            }
            return Double.compare(nanLast(distance[a]), nanLast(distance[b]));
        });
        for (int k = 0; k < n && contacts < MAX_CONTACTS; k++) {
            Contact c = listed.get(order[k]);
            double dm = distance[order[k]];
            String dist = Double.isNaN(dm) ? "" : dm < 1000 ? Long.toString(Math.round(dm)) : km(dm);
            String unit = Double.isNaN(dm) ? "" : dm < 1000 ? "M" : "KM";
            contactRow[contacts] = cols.clear().left(Fmt.name(c.name, 18), 18).right(dist, 10).gap(1).left(unit, 2).toString();
            contactId[contacts] = c.id;
            contactLevel[contacts] = c.target ? Drive.FULL : c.stale ? Drive.DIM : Drive.NORM;
            contacts++;
        }
    }

    private static double nanLast(double v) {
        return Double.isNaN(v) ? Double.MAX_VALUE : v;
    }

    /** Kilometres as the list puts them: a tenth under 100 km, whole with commas above. */
    static String km(double meters) {
        double k = meters / 1000.0;
        return k < 100 ? String.format(Locale.ROOT, "%.1f", k) : Glass.thousands(k);
    }

    /** Whether contact {@code c} shows with the layers as they are: as the map draws it. */
    boolean shows(Contact c) {
        for (int i = 0; i < LAYERS; i++) {
            if (LAYER_KIND[i] == c.kind) return layer[i] || c.target || c.objective && layer[OBJECTIVES];
        }
        return true;
    }

    // ------------------------------------------------------------------ laying out

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rowsY = y + 30f + 94f + 22f + 58f + 22f;
        rows = Math.max(1, (int) Math.floor((layersTop() - 24f - 10f - rowsY) / Glass.ROW));
        first = Math.max(0, Math.min(first, Math.max(0, contacts - rows)));
    }

    /** The layers' keys: the first row's labels; the head over them, 24 above. */
    float layersTop() {
        return y + h - 10f - 52f - 38f;
    }

    /** Turns the list by {@code amount} rows. */
    void scroll(int amount) {
        first = Math.max(0, Math.min(first + amount, Math.max(0, contacts - rows)));
    }

    boolean overContacts(float px, float py) {
        return px >= x && px < x + w && py >= rowsY - 22f && py < rowsY + rows * Glass.ROW;
    }

    // ------------------------------------------------------------------ painting

    void paint(Filament f) {
        float x0 = x + 10f, x1 = x + w - 10f, yy = y + 30f;
        f.legend(x0, yy + 12f, "NAV", f.o().size(13f).dim().noGhost());
        Filament.Opt big = f.o().pitch(2f).noGhost();
        if (hasNav) big.full();
        else big.dim();
        f.dm(x0 + 40f, yy, navName, big);
        if (!navLine.isEmpty()) {
            if (hasNav) Glass.text(f, x0, yy + 24f, navLine);
            else Glass.dim(f, x0, yy + 24f, navLine);
        }
        if (!navWhere.isEmpty()) Glass.dim(f, x0, yy + 38f, navWhere);
        Glass.capWord(f, x0, yy + 58f, caps(kNext), "NEXT TARGET", Drive.NORM);
        yy += 94f;
        Glass.head(f, x0, yy, x1, "TRANSFER");
        yy += 22f;
        keyEnd[0] = Glass.capWord(f, x0, yy, caps(kPlan), "PLAN", canPlan ? Drive.NORM : Drive.DIM);
        keyEnd[1] = Glass.capWord(f, keyEnd[0], yy, caps(kAccept), "ACCEPT", proposed ? Drive.NORM : Drive.DIM);
        keyEnd[2] = Glass.capWord(f, keyEnd[1], yy, caps(kDiscard), "DISCARD", proposed ? Drive.NORM : Drive.DIM);
        if (proposed) Glass.text(f, x0, yy + 26f, transferNote);
        else Glass.dim(f, x0, yy + 26f, transferNote);
        yy += 58f;
        Glass.head(f, x0, yy, x1, "CONTACTS");
        String sort = sortWord();
        Glass.right(f, x1, yy, sort, Drive.DIM);
        float ry = rowsY;
        if (contacts == 0) Glass.dim(f, x0, ry, "NONE ROUND THIS BODY");
        for (int i = first; i < Math.min(contacts, first + rows); i++) {
            Glass.text(f, x0, ry, contactRow[i], contactLevel[i]);
            ry += Glass.ROW;
        }
        if (first + rows < contacts) Glass.right(f, x1, ry - Glass.ROW, "▼", Drive.DIM);
        if (first > 0) Glass.right(f, x1, rowsY, "▲", Drive.DIM);
        // The layers, at the pane's foot: each a key with its lamp, its name in the glass over it
        float ly = layersTop();
        Glass.head(f, x0, ly - 24f, x1, "LAYERS");
        float sw = (x1 - x0) / 3f;
        for (int i = 0; i < LAYERS; i++) {
            float sx = x0 + (i % 3) * sw, sy = ly + (i / 3) * 52f;
            String t = LAYER_WORD[i];
            Glass.text(f, sx + (sw - Glass.width(t.length())) / 2f, sy, t, layer[i] ? Drive.NORM : Drive.DIM);
            f.key(sx + 8f, sy + 16f, sw - 16f, 22f, "", f.o().size(12f));
            f.led(sx + sw - 20f, sy + 27f, Filament.Led.GREEN, layer[i], 2f);
        }
    }

    String sortWord() {
        return byKind ? "SORT ◀ KIND ▶" : "SORT ◀ DISTANCE ▶";
    }

    private String[] caps(String k) {
        capsBuf[0] = k;
        return capsBuf;
    }

    // ------------------------------------------------------------------ clicks

    void boxes(FilamentActor a) {
        float x0 = x + 10f, yy = y + 30f;
        a.box(NEXT_TARGET, x0 - 2f, yy + 55f, w - 16f, 24f);
        yy += 94f + 22f;
        if (keyEnd[2] > x0) {
            a.box(PLAN, x0 - 2f, yy - 3f, keyEnd[0] - x0 - 8f, 24f);
            a.box(ACCEPT, keyEnd[0] - 2f, yy - 3f, keyEnd[1] - keyEnd[0] - 8f, 24f);
            a.box(DISCARD, keyEnd[1] - 2f, yy - 3f, keyEnd[2] - keyEnd[1] - 8f, 24f);
        }
        yy += 58f;
        float sortW = Glass.width(sortWord().length());
        a.box(SORT, x + w - 10f - sortW - 4f, yy - 4f, sortW + 8f, 20f);
        float ry = rowsY;
        for (int i = first; i < Math.min(contacts, first + rows); i++) {
            if (contactId[i] != null && !contactId[i].isEmpty()) a.box(CONTACT + i, x0 - 2f, ry - 3f, w - 16f, Glass.ROW);
            ry += Glass.ROW;
        }
        float ly = layersTop(), sw = (w - 20f) / 3f;
        for (int i = 0; i < LAYERS; i++) {
            float sx = x0 + (i % 3) * sw, sy = ly + (i / 3) * 52f;
            a.box(LAYER + i, sx + 2f, sy - 3f, sw - 4f, 44f);
        }
    }
}
