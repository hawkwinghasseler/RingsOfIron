package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardMapPainter;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.widgets.ManeuverMap;
import com.ringsofiron.uihud.widgets.MapPainter;
import com.ringsofiron.uihud.widgets.OrbitMap;
import com.ringsofiron.uihud.widgets.TrafficLayer;

import java.util.Locale;

/**
 * The map as the Context spec lays it out (M1, section 10.1) in the Yard Standard (style guide 9.9):
 * the game's own map is the plotting tube in the middle, drawn by the look's map painter, and round it
 * stand the console's bays. The head has the view row (what the map centres on, on flaps; fit; all
 * the traffic; the zoom rocker). On the left the plan: the orbit now and the burns on ledger tubes, the
 * selected burn's result, the delta-v needed and held and the arrival on drums, and the orders that fly
 * it. On the right the target and the traffic: the nav card, the next target and the transfer's push
 * buttons, the contacts in order with a sort rotary, and the layers as bat toggles. Under the tube the
 * timeline: the burns, the apsides, the changes of sphere, an impact and the closest approach on one
 * axis from now. The foot has the feed and the keys that close it.
 *
 * <p>It reads what the game writes for the classic map panel, which it hides: the planner's lines,
 * the path's burn labels and marks, the nav readout and the traffic; and gives the planner's orders.
 * The map keeps the zoom, the panning, the burn handles and the clicks in the window, which it leaves
 * to the map ({@link #contains}).
 */
final class MapMode extends YardPart implements Hits.Target {
    static final float HEAD_H = BigConsole.HEAD_H, FOOT_H = BigConsole.FOOT_H, GAP = 8f, PAD = 12f;
    /** The timeline under the tube (Context 10.1: 90 tall). */
    static final float TIMELINE_H = 90f;
    /** The bays' width: Context's 320 at 1280 wide, between these. */
    static final float RAIL_MIN = 300f, RAIL_MAX = 380f;
    static final float PUSH_H = 34f;
    /** How often the planner's lines are read again, seconds. */
    static final float REFRESH = 0.25f;
    /** Seconds a lifted guard stays up. */
    static final float GUARD_UP = ListPage.GUARD_UP;

    // What a press tells
    static final int CENTRE = 0, FIT = 1, ALL = 2, ZOOM_OUT = 3, ZOOM_IN = 4;
    static final int BURN_ROW = 100, DELETE = 200;
    static final int HELM = 300, AUTO = 301, WARP = 302, CLEAR = 303;
    static final int NEXT = 400, PLAN = 401, ACCEPT = 402, DISCARD = 403;
    static final int SORT = 500, LAYER = 600, EVENT = 700, CONTACT = 1000;

    /** The layer toggles, as they stand, and the contacts each shows (null: not a kind). */
    static final String[] LAYERS = {"STATIONS", "HAULERS", "PIRATES", "DERELICTS", "OBJECTIVES", "TENSION"};
    private static final ContactKind[] LAYER_KINDS = {ContactKind.STATION, ContactKind.HAULER, ContactKind.HOSTILE, ContactKind.OTHER, null, null};

    private static final String[] HELM_LINES = {"HELM FLIES", "BURNS"}, AUTO_LINES = {"AUTOPILOT", "TO NAV"};
    private static final String[] WARP_LINES = {"WARP TO", "NEXT BURN"}, CLEAR_LINES = {"CLEAR ALL", "BURNS"};
    private static final String[] AGAIN_LINES = {"PRESS AGAIN", "TO CLEAR"};
    private static final String[] NEXT_LINES = {"NEXT", "TARGET"}, PLAN_LINES = {"PLAN"};
    private static final String[] ACCEPT_LINES = {"ACCEPT"}, DISCARD_LINES = {"DISCARD"};
    private static final Color KEY_FACE = Yard.hex("3D3732"), FOOT_SEAM = Yard.hex("0B0908");
    private static final Color AMBER = Ledger.AMBER, DIM = Ledger.DIM;

    private HudVariantHost host;
    private final Hits hits = new Hits();
    private final FeedTube feed = new FeedTube();
    private final Ledger orbitTube = new Ledger(), burnTube = new Ledger(), flyTube = new Ledger();
    private final Ledger navTube = new Ledger(), contactTube = new Ledger();
    private float top, railW;
    /** The window the game's map shows through this frame, design units, y down. */
    float winX, winY, winW, winH;
    private float tlY;
    // What the planner's lines say, read a few times a second
    private final Array<MapText.Row> orbitRows = new Array<>(), burnRows = new Array<>();
    private int orbitCount, burnCount;
    private final String[] body = {""};
    private final int[] which = {0};
    private final MapText.Plan plan = new MapText.Plan();
    private final Array<MapText.Event> events = new Array<>();
    private int eventCount;
    private final double[] selectedBurn = new double[2];
    private boolean hasSelected;
    private float sinceRefresh = REFRESH;
    // The traffic in order, each frame: the contacts move
    private final IntArray order = new IntArray();
    private final FloatArray dist = new FloatArray();
    private boolean byKind;
    private int contactTop, contactShown = 1;
    /** Whether the tension readout shows on the map (its layer). */
    private boolean tension = true;
    private float now, clearAt = -100f;
    // The insets last given the map, so they are given once
    private float insL = -1f, insT, insR, insB;
    private float[] want = new float[16], widths = new float[16], placed = new float[16];
    private final Vector2 mid = new Vector2();
    private final Color bandA = new Color(), bandB = new Color();

    MapMode() {
        super(0f, 0f);
    }

    void show(HudVariantHost host) {
        this.host = host;
        if (!visible) {
            feed.clear();
            sinceRefresh = REFRESH;
            contactTop = 0;
            insL = -1f;
        }
        visible = true;
    }

    void hide() {
        visible = false;
    }

    /** Lays the bays and the window out on a screen {@code sw} by {@code sh}, {@code top} kept for the HUD's strip. */
    void layout(float sw, float sh, float top) {
        this.top = top;
        place(0f, 0f, sw, sh);
        railW = MathUtils.clamp(sw * 0.235f, RAIL_MIN, RAIL_MAX);
        winX = GAP + railW + GAP;
        winW = Math.max(160f, sw - 2f * winX);
        winY = top + HEAD_H + GAP;
        tlY = sh - FOOT_H - GAP - TIMELINE_H;
        winH = Math.max(120f, tlY - GAP - winY);
    }

    /**
     * Keeps the game's map in the window: its canvas there (the orbits fit and centre in it, the scale
     * bar sits in it), the painter's tube there, and the map's own title and instructions off, the head
     * and the foot saying them.
     */
    void fitMap() {
        if (host == null) return;
        ManeuverMap m = host.hud().getManeuverMap();
        float l = winX, t = winY, r = w - winX - winW, b = h - winY - winH;
        if (l != insL || t != insT || r != insR || b != insB) {
            m.setCanvasInsets(l, t, r, b);
            insL = l;
            insT = t;
            insR = r;
            insB = b;
        }
        MapPainter mp = m.getPainter();
        if (mp instanceof YardMapPainter) {
            YardMapPainter yp = (YardMapPainter) mp;
            yp.setInsets(l, t, r, b);
            yp.setDraws(false, false, tension);
        }
    }

    /** Once a frame while it shows. */
    void update(float delta) {
        feed.update(delta);
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH) refreshNow();
    }

    /** Reads the planner's lines now: as it comes up, a few times a second, and after each order. */
    void refreshNow() {
        sinceRefresh = 0f;
        if (host == null) return;
        ManeuverPlannerProvider pl = planner();
        OrbitPath path = path();
        double first = Double.NaN;
        for (int i = 0; i < path.nodeLabels.size; i++) {
            String l = path.nodeLabels.get(i);
            double s = MapText.seconds(l.substring(l.indexOf(": ") + 1));
            if (!Double.isNaN(s) && (Double.isNaN(first) || s < first)) first = s;
        }
        orbitCount = MapText.orbit(pl == null ? "" : pl.orbitText(), first, orbitRows, body);
        burnCount = MapText.burn(pl == null ? "" : pl.burnText(), burnRows, which);
        MapText.plan(pl == null ? "" : pl.planText(), plan);
        hasSelected = pl != null && pl.selectedBurn(selectedBurn);
        NavReadout n = data().navReadout;
        boolean closest = n.present && !Float.isNaN(n.cpaIn);
        eventCount = MapText.events(path.nodeLabels, orbitRows, orbitCount, path.marks, closest ? n.cpaIn : Double.NaN,
                closest ? MapText.distance(n.cpaDistance) : "", events);
    }

    // --- Where the pointer goes --------------------------------------------------------------

    /** The bays, the head and the foot are the console's; the window is the map's. */
    @Override
    public boolean contains(float px, float py) {
        if (!visible || px < 0f || px > w || py < top || py > h) return false;
        return !(px >= winX && px <= winX + winW && py >= winY && py <= winY + winH);
    }

    @Override
    public boolean pressable(float px, float py) {
        return hits.any(px, py);
    }

    @Override
    public boolean press(float px, float py) {
        return hits.press(px, py);
    }

    /** The wheel over the contacts scrolls them; true when it did. Elsewhere it zooms the map. */
    boolean scroll(float px, float py, float amount) {
        Ledger L = contactTube;
        if (!visible || px < L.x || px > L.x + L.w || py < L.y || py > L.y + L.h) return false;
        contactTop = Math.max(0, contactTop + Math.round(amount * 3f));
        return true;
    }

    @Override
    public void hit(int code) {
        if (host == null) return;
        ManeuverMap m = host.hud().getManeuverMap();
        ManeuverPlannerProvider pl = planner();
        boolean burns = path().nodeLabels.size > 0;
        if (code == CENTRE) m.nextFocus();
        else if (code == FIT) m.fit();
        else if (code == ALL) m.fitTraffic();
        else if (code == ZOOM_OUT || code == ZOOM_IN) zoom(code == ZOOM_IN ? 1.25f : 0.8f);
        else if (code >= BURN_ROW && code < DELETE) {
            if (pl != null) pl.selectBurn(code - BURN_ROW);
        } else if (code >= EVENT && code < CONTACT) {
            MapText.Event e = code - EVENT < eventCount ? events.get(code - EVENT) : null;
            if (e != null && e.burn >= 0 && pl != null) pl.selectBurn(e.burn);
        } else if (code >= CONTACT) {
            TrafficFrame f = m.getTraffic().getFrame();
            int i = code - CONTACT;
            if (i < f.contacts.size) m.pick(f.contacts.get(i).id);
        } else if (code >= LAYER && code < LAYER + LAYERS.length) {
            layer(m.getTraffic(), code - LAYER);
        } else if (code == SORT) {
            byKind = !byKind;
            contactTop = 0;
        } else if (pl == null) {
            feed.say("MAP", "NO PLANNER ON THIS SHIP");
        } else {
            order(pl, code, burns);
        }
        refreshNow();
        m.refresh();
    }

    /** One of the planner's orders, unless it can't act now: then why, in the feed. */
    private void order(ManeuverPlannerProvider pl, int code, boolean burns) {
        String no = refusal(code, burns, pl.hasTarget(), pl.hasProposal());
        if (no != null) {
            feed.say("MAP", no);
            return;
        }
        switch (code) {
            case DELETE:
                pl.deleteSelected();
                break;
            case HELM:
                pl.helmFlyBurns();
                break;
            case AUTO:
                pl.autopilot();
                break;
            case WARP:
                pl.warpToBurn();
                break;
            case CLEAR:
                // Under a guard: the first press lifts it
                if (now - clearAt > GUARD_UP) {
                    clearAt = now;
                    return;
                }
                clearAt = -100f;
                pl.clearBurns();
                break;
            case NEXT:
                pl.nextTarget();
                break;
            case PLAN:
                pl.planToTarget();
                break;
            case ACCEPT:
                pl.acceptPlan();
                break;
            case DISCARD:
                pl.discardPlan();
                break;
            default:
        }
    }

    /** Why the order on {@code code} can't be given now, or null when it can. */
    String refusal(int code, boolean burns, boolean target, boolean proposal) {
        switch (code) {
            case DELETE:
                return hasSelected ? null : "SELECT A BURN FIRST";
            case HELM:
            case WARP:
            case CLEAR:
                return burns || code == HELM && plan.helmFlying ? null : "NO BURNS PLANNED: CLICK THE PATH TO PLAN ONE";
            case AUTO:
                return target || plan.autopilot ? null : "NO NAV TARGET: PICK A CONTACT";
            case PLAN:
                return target ? null : "NO NAV TARGET: PICK A CONTACT";
            case ACCEPT:
            case DISCARD:
                return proposal ? null : "NO TRANSFER PROPOSED: PLAN ONE FIRST";
            default:
                return null;
        }
    }

    /** Turns layer {@code k} over: a kind of contact, the waypoints' rings, or the tension readout. */
    private void layer(TrafficLayer t, int k) {
        if (LAYER_KINDS[k] != null) t.setShown(LAYER_KINDS[k], !t.isShown(LAYER_KINDS[k]));
        else if (k == 4) t.setObjectivesShown(!t.objectivesShown());
        else tension = !tension;
    }

    private boolean layerOn(TrafficLayer t, int k) {
        if (LAYER_KINDS[k] != null) return t.isShown(LAYER_KINDS[k]);
        return k == 4 ? t.objectivesShown() : tension;
    }

    /** Zooms about the window's middle, as the wheel does about the pointer. */
    private void zoom(float factor) {
        ManeuverMap m = host.hud().getManeuverMap();
        OrbitMap om = m.getMap();
        mid.set(om.getWidth() / 2f, om.getHeight() / 2f);
        om.localToStageCoordinates(mid);
        m.zoomAt(mid.x, mid.y, factor);
    }

    private HudData data() {
        return host.data();
    }

    private ManeuverPlannerProvider planner() {
        return host.data().planner;
    }

    private OrbitPath path() {
        return host.hud().getManeuverMap().getMap().getPath();
    }

    /** What the view is centred on, for its flaps. */
    private String focusName() {
        switch (host.hud().getManeuverMap().getFocus()) {
            case SHIP:
                return "SHIP";
            case TARGET:
                return "TARGET";
            case ENCOUNTER:
                return "ENCOUNTER";
            default:
                String b = path().bodyName;
                return b.isEmpty() ? "BODY" : b.toUpperCase(Locale.ROOT);
        }
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void draw(YardPaint p) {
        hits.clear();
        if (host == null) return;
        now = p.time();
        chassis(p);
        head(p);
        planBay(p);
        targetBay(p);
        timeline(p);
        foot(p);
    }

    /** The console's ground round the window, one gradient down it all, and the seam the tube sits in. */
    private void chassis(YardPaint p) {
        band(p, 0f, top, w, winY - top);
        band(p, 0f, winY + winH, w, h - winY - winH);
        band(p, 0f, winY, winX, winH);
        band(p, winX + winW, winY, w - winX - winW, winH);
        p.frame(winX - 2f, winY - 2f, winW + 4f, winH + 4f, 2f, BigConsole.SEAM, 1f);
    }

    private void band(YardPaint p, float x, float y, float bw, float bh) {
        if (bw <= 0f || bh <= 0f) return;
        float span = Math.max(1f, h - top);
        bandA.set(Yard.CHASSIS_TOP).lerp(Yard.CHASSIS_BOTTOM, (y - top) / span);
        bandB.set(Yard.CHASSIS_TOP).lerp(Yard.CHASSIS_BOTTOM, (y + bh - top) / span);
        p.vgrad(x, y, bw, bh, bandA, bandB);
    }

    /** The head: the nameplate, then the view row over the window, the zoom rocker at its end. */
    private void head(YardPaint p) {
        float y = top;
        p.vgrad(0f, y, w, HEAD_H, Yard.ENAMEL_TOP, Yard.ENAMEL_BOTTOM);
        p.rect(0f, y, w, 1f, Color.WHITE, 0.18f);
        p.rect(0f, y + HEAD_H - 3f, w, 3f, BigConsole.SHIP_STRIPE);
        p.rect(0f, y + HEAD_H, w, 1.5f, BigConsole.SEAM);
        p.text(YardFonts.Face.LEGEND, 20f, "MAP", 14f, y + 22f, Yard.INK);
        float x = winX;
        x += plastic(p, x, y + 3f, 80f, "CENTRE", CENTRE) + 8f;
        String f = focusName();
        int cells = Math.max(8, Math.min(14, f.length()));
        p.flaps(x, y + 5f, f.length() > cells ? f.substring(0, cells) : f, cells, 10f, 20f, 14f, Yard.FLAP_TEXT);
        x += YardPaint.flapsWidth(cells, 10f) + 16f;
        x += plastic(p, x, y + 3f, 52f, "FIT", FIT) + 8f;
        plastic(p, x, y + 3f, 104f, "ALL TRAFFIC", ALL);
        float zx = winX + winW - 60f;
        p.keyButton(zx, y + 4f, 28f, 22f, "-");
        hits.add(p, zx, y + 4f, 28f, 22f, this, ZOOM_OUT);
        p.keyButton(zx + 32f, y + 4f, 28f, 22f, "+");
        hits.add(p, zx + 32f, y + 4f, 28f, 22f, this, ZOOM_IN);
        p.textR(YardFonts.Face.LEGEND, 12f, "ZOOM", zx - 10f, y + 19.5f, Yard.INK);
    }

    /** A grey plastic key with its legend, as the console's tabs are. Returns its width. */
    private float plastic(YardPaint p, float x, float y, float kw, String label, int code) {
        float kh = 24f;
        p.rrect(x - 3f, y - 3f, kw + 6f, kh + 4f, 2.5f, Yard.BAKELITE);
        p.rrect(x + 0.5f, y + 1.5f, kw, kh - 2f, 2f, Color.BLACK, 0.4f);
        p.rrect(x, y, kw, kh - 2f, 2f, BigConsole.KEY_UP);
        p.rect(x + 2f, y + 0.8f, kw - 4f, 1f, Color.WHITE, 0.5f);
        p.textC(YardFonts.Face.LEGEND, 12f, label, x + kw / 2f, y + kh / 2f + 3.3f, Yard.INK);
        hits.add(p, x, y, kw, kh, this, code);
        return kw;
    }

    /** A push button for an order: lit while it is in force, its barber strip while it can't be given. */
    private void push(YardPaint p, float x, float y, float pw, String[] lines, boolean lit, boolean unavailable, int code) {
        push(p, x, y, pw, lines, Yard.AMBER_LAMP, lit, unavailable, code);
    }

    private void push(YardPaint p, float x, float y, float pw, String[] lines, Yard.Lamp lamp, boolean lit, boolean unavailable, int code) {
        p.push(x, y, pw, PUSH_H, lines, lamp, lit, YardPaint.Kind.ORDER, unavailable, false);
        hits.add(p, x - 3f, y - 3f, pw + 6f, PUSH_H + 6f, this, code);
    }

    // --- The plan ----------------------------------------------------------------------------

    private void planBay(YardPaint p) {
        float bx = GAP, by = top + HEAD_H + GAP, bw = railW, bh = h - FOOT_H - GAP - by;
        BigConsole.bay(p, bx, by, bw, bh, "PLAN", false);
        float x = bx + PAD, tw = bw - 2f * PAD;
        int cols = Ledger.colsIn(tw);
        float y = by + 26f;
        orbit(p, x, y, cols);
        y += orbitTube.h + 10f;
        // From the foot up: FLY IT and its tube, then the totals; the burns take what is left
        float foot = by + bh - PAD;
        float flyTop = foot - 108f - Ledger.height(2);
        float totalsTop = flyTop - 12f - 104f;
        int rows = Math.max(5, Ledger.rowsIn(totalsTop - 38f - y));
        burns(p, x, y, cols, rows);
        totals(p, x, totalsTop, tw);
        fly(p, x, flyTop, tw);
    }

    /** The orbit now: the body on the title row, then the height, the apsides with their times, the period. */
    private void orbit(YardPaint p, float x, float y, int cols) {
        Ledger L = orbitTube.set(x, y, cols, 6);
        L.under(p, false);
        L.bar(p, 0);
        L.rev(p, 1, 0, "ORBIT NOW");
        L.revRight(p, cols - 1, 0, Ledger.cut(body[0], cols - 12));
        if (orbitCount == 0) L.at(p, 1, 1, "NO ORBIT", DIM);
        for (int i = 0; i < Math.min(orbitCount, 5); i++) {
            MapText.Row r = orbitRows.get(i);
            Color c = r.red ? Yard.P_RED : r.dim ? DIM : AMBER;
            L.at(p, 1, i + 1, Ledger.cut(r.left, Math.max(4, cols - 3 - r.right.length())), c);
            L.right(p, cols - 1, i + 1, r.right, c);
        }
        L.over(p);
    }

    /**
     * The burns: a row each (its number, when, its delta-v; the selected one's prograde and radial),
     * the selected one's result under them, and its DELETE key.
     */
    private void burns(YardPaint p, float x, float y, int cols, int rows) {
        Ledger L = burnTube.set(x, y, cols, rows);
        L.under(p, false);
        OrbitPath path = path();
        int n = path.nodeLabels.size, sel = path.selectedNode;
        L.bar(p, 0);
        L.rev(p, 1, 0, "BURNS");
        L.revRight(p, cols - 1, 0, n == 0 ? "NONE PLANNED" : n + " PLANNED");
        float cT = 3f, cDv = cols - 12f, cPro = cols - 6f, cRad = cols - 1f;
        L.at(p, 1, 1, "#", DIM);
        L.at(p, cT, 1, "T-", DIM);
        L.right(p, cDv, 1, "DV", DIM);
        L.right(p, cPro, 1, "PRO", DIM);
        L.right(p, cRad, 1, "RAD", DIM);
        boolean result = hasSelected && burnCount > 0 && sel >= 0;
        int after = result ? Math.min(burnCount, Math.max(1, rows - 7)) + 1 : 0;
        int listRows = Math.max(1, rows - 3 - after - (after > 0 ? 1 : 0));
        int first = sel >= listRows ? sel - listRows + 1 : 0;
        if (n == 0) {
            L.at(p, 1, 2, "CLICK THE PATH TO PLAN ONE", DIM);
            L.at(p, 1, 3, "ON THE MAP", DIM);
        }
        for (int r = 0; r < listRows && first + r < n; r++) {
            int i = first + r, row = 2 + r;
            String l = path.nodeLabels.get(i);
            int c = l.indexOf(": ");
            String num = c < 0 ? String.valueOf(i + 1) : l.substring(0, c);
            String t = c < 0 ? l : l.substring(c + 2).toUpperCase(Locale.ROOT);
            boolean s = i == sel;
            float dv = i < plan.burnDv.size ? plan.burnDv.get(i) : Float.NaN;
            String pro = "", rad = "";
            if (s && hasSelected) {
                dv = (float) Math.hypot(selectedBurn[0], selectedBurn[1]);
                pro = whole(selectedBurn[0]);
                rad = whole(selectedBurn[1]);
            }
            String dvs = Float.isNaN(dv) ? "" : whole(dv);
            if (s) {
                L.bar(p, row);
                L.rev(p, 1, row, num);
                L.rev(p, cT, row, t);
                L.revRight(p, cDv, row, dvs);
                L.revRight(p, cPro, row, pro);
                L.revRight(p, cRad, row, rad);
            } else {
                L.at(p, 1, row, num, AMBER);
                L.at(p, cT, row, t, AMBER);
                L.right(p, cDv, row, dvs, AMBER);
            }
            hits.add(p, L.x, L.rowTop(row), L.w, Ledger.ROW, this, BURN_ROW + i);
        }
        L.meter(p, 2, 1 + listRows, first, listRows, n);
        if (after > 0) {
            int r0 = rows - 1 - after;
            L.rule(p, r0 - 1);
            L.at(p, 1, r0, "AFTER BURN " + which[0], AMBER);
            for (int k = 0; k < after - 1; k++) {
                MapText.Row r = burnRows.get(k);
                Color c = r.red ? Yard.P_RED : AMBER;
                L.at(p, 1, r0 + 1 + k, Ledger.cut(r.left, cols - 3 - r.right.length()), c);
                L.right(p, cols - 1, r0 + 1 + k, r.right, c);
            }
        }
        // DELETE on the last row, struck with no burn selected
        String no = hasSelected ? null : n == 0 ? "" : "SELECT ONE";
        float kw = 96f, kx = L.cx(cols - 1) - kw;
        softLabel(p, L, kx, kw, "DELETE", no, DELETE);
        L.over(p);
        softBody(p, L, kx, kw, DELETE);
    }

    /** "3,110", "-846": a burn's figures, whole metres a second. */
    static String whole(double v) {
        long r = Math.round(v);
        return String.format(Locale.ROOT, "%,d", r == 0 ? 0 : r);
    }

    /** A soft key's label on the tube's last row, over the rule; struck, with its reason at the left, when it can't act. */
    private void softLabel(YardPaint p, Ledger L, float kx, float kw, String label, String refusal, int code) {
        int r = L.rows - 1;
        L.rule(p, r - 1);
        float yy = L.base(r);
        boolean struck = refusal != null;
        if (struck && !refusal.isEmpty()) L.at(p, 0, r, Ledger.cut(" " + refusal, (int) ((kx - L.cx(0)) / Ledger.COL) - 1), DIM);
        p.frame(kx, yy - 13.5f, kw, 18f, 1f, AMBER, struck ? 0.45f : 0.9f);
        p.textC(YardFonts.Face.TUBE, 13f, label, kx + kw / 2f, yy, struck ? DIM : AMBER);
        if (struck) p.rect(kx + 6f, yy - 5.2f, kw - 12f, 1.4f, AMBER);
        hits.add(p, kx, yy - 13.5f, kw, 18f, this, code);
    }

    /** The soft key itself, under the tube. */
    private void softBody(YardPaint p, Ledger L, float kx, float kw, int code) {
        float ky = L.y + L.h + 10f;
        p.rrect(kx + 1f, ky + 2f, kw, 11f, 2f, Yard.SHADOW, 0.7f);
        p.rrect(kx, ky, kw, 11f, 2f, Yard.BAKELITE);
        p.rrect(kx + 1.5f, ky + 1f, kw - 3f, 6f, 1.5f, KEY_FACE);
        hits.add(p, kx, ky - 2f, kw, 17f, this, code);
    }

    /** The totals on drums: the delta-v the plan needs and what the tanks hold; the arrival. */
    private void totals(YardPaint p, float x, float y, float tw) {
        FlightStatus f = data().flightStatus;
        double need = f.deltaVPlanned, have = f.deltaVLeft;
        boolean short_ = need > 0 && !Double.isNaN(have) && !Double.isInfinite(have) && have < need;
        float half = tw / 2f, dw = half - 44f;
        p.legend("ΔV NEED", x, y + 12f, Yard.INK);
        p.legend("HAVE", x + half, y + 12f, Yard.INK);
        if (short_) {
            p.lamp(x + half - 20f, y + 7f, 3.2f, Yard.RED_LAMP, true);
            p.textR(YardFonts.Face.LEGEND, 10f, "SHORT", x + half - 27f, y + 11.5f, Yard.RED);
        }
        p.drumsR(x + dw, y + 18f, figures(need), 17f, 28f, 22f, dw, true);
        p.legend("M/S", x + dw + 7f, y + 40f, Yard.INK);
        if (Double.isInfinite(have)) {
            p.flaps(x + half, y + 20f, "FREE", 4, 14f, 24f, 16f, Yard.FLAP_TEXT);
        } else {
            p.drumsR(x + half + dw, y + 18f, Double.isNaN(have) ? "     " : figures(have), 17f, 28f, 22f, dw, true);
            p.legend("M/S", x + half + dw + 7f, y + 40f, Yard.INK);
        }
        // The arrival: the closest approach to the nav target along the plan
        float ay = y + 58f;
        NavReadout n = data().navReadout;
        p.legend("ARRIVAL", x, ay + 12f, Yard.INK);
        if (!n.present || Float.isNaN(n.cpaIn)) {
            p.text(YardFonts.Face.LEGEND, 12f, n.present ? "NO CLOSE APPROACH ON THIS PATH" : "NO NAV TARGET", x, ay + 38f, Yard.INK, 0.45f);
            return;
        }
        p.legend("T-", x, ay + 38f, Yard.INK);
        String t = MapText.time(n.cpaIn);
        boolean days = t.endsWith(" d");
        String figs = days ? t.substring(0, t.length() - 2) : t;
        float aw = p.drums(x + 22f, ay + 18f, figs, 17f, 28f, 22f, false);
        if (days) p.flaps(x + 22f + aw + 8f, ay + 20f, "D", 1, 14f, 24f, 16f, Yard.FLAP_TEXT);
        if (!Double.isNaN(n.cpaDistance)) {
            p.textR(YardFonts.Face.LEGEND, 12f, MapText.distance(n.cpaDistance), x + tw, ay + 12f, Yard.INK);
            p.textR(YardFonts.Face.LEGEND, 10f, "CLOSEST", x + tw, ay + 26f, Yard.INK);
        }
    }

    /** Whole metres a second on five drums or more, the leading ones blank. */
    static String figures(double v) {
        long r = Math.max(0L, Math.round(v));
        return String.format(Locale.ROOT, "%05d", r);
    }

    /** FLY IT: the helm, the autopilot, warp to the next burn, and clearing every burn under a guard; then what the helm and autopilot say. */
    private void fly(YardPaint p, float x, float y, float tw) {
        p.legend("FLY IT", x, y + 12f, Yard.INK);
        float pw = (tw - 14f) / 2f, py = y + 22f;
        boolean burns = path().nodeLabels.size > 0;
        ManeuverPlannerProvider pl = planner();
        boolean target = pl != null && pl.hasTarget();
        push(p, x + 3f, py, pw, HELM_LINES, plan.helmFlying, refusal(HELM, burns, target, false) != null, HELM);
        push(p, x + pw + 11f, py, pw, AUTO_LINES, plan.autopilot, refusal(AUTO, burns, target, false) != null, AUTO);
        push(p, x + 3f, py + PUSH_H + 12f, pw, WARP_LINES, false, !burns, WARP);
        boolean lifted = now - clearAt <= GUARD_UP;
        float cx = x + pw + 11f, cy = py + PUSH_H + 12f;
        push(p, cx, cy, pw, lifted ? AGAIN_LINES : CLEAR_LINES, Yard.RED_LAMP, lifted, !burns, CLEAR);
        if (!lifted) {
            // The red guard over it: the first press lifts it
            p.rrect(cx - 4f, cy - 4f, pw + 8f, PUSH_H + 8f, 3f, Yard.RED, 0.55f);
            p.rect(cx - 4f, cy - 4f, pw + 8f, 3f, Yard.hex("7A221B"));
            p.glass(cx - 4f, cy - 4f, pw + 8f, PUSH_H + 8f);
        } else {
            p.lamp(cx + pw - 8f, cy - 9f, 2.4f, Yard.RED_LAMP, true);
        }
        // What the helm and the autopilot say they are doing
        Ledger L = flyTube.set(x, py + 2f * PUSH_H + 26f, Ledger.colsIn(tw), 2);
        L.under(p, false);
        int cols = L.cols;
        if (plan.helm.isEmpty() && plan.auto.isEmpty()) {
            L.at(p, 1, 0, "NO FLIGHT ORDERS", DIM);
        } else {
            int r = 0;
            if (!plan.helm.isEmpty()) L.at(p, 1, r++, Ledger.cut(plan.helm.toUpperCase(Locale.ROOT), cols - 2), plan.helmWaits ? DIM : AMBER);
            if (!plan.auto.isEmpty() && r < 2) L.at(p, 1, r, Ledger.cut(plan.auto.toUpperCase(Locale.ROOT), cols - 2), AMBER);
        }
        L.over(p);
    }

    // --- The target and the traffic ----------------------------------------------------------

    private void targetBay(YardPaint p) {
        float bw = railW, bx = w - GAP - bw, by = top + HEAD_H + GAP, bh = h - FOOT_H - GAP - by;
        BigConsole.bay(p, bx, by, bw, bh, "TARGET AND TRAFFIC", false);
        float x = bx + PAD, tw = bw - 2f * PAD;
        int cols = Ledger.colsIn(tw);
        float y = by + 26f;
        nav(p, x, y, cols);
        y += navTube.h + 14f;
        transfer(p, x, y, tw);
        y += 92f;
        float layersTop = by + bh - PAD - 118f;
        contacts(p, x, y, tw, cols, layersTop - 14f);
        layers(p, x, layersTop, tw);
    }

    /** The nav card: the name, what it is, a contract it serves, the distance, closing, the closest approach. */
    private void nav(YardPaint p, float x, float y, int cols) {
        Ledger L = navTube.set(x, y, cols, 6);
        L.under(p, false);
        NavReadout n = data().navReadout;
        L.bar(p, 0);
        if (!n.present) {
            L.rev(p, 1, 0, "NAV");
            L.at(p, 1, 1, "NONE SET", DIM);
            L.at(p, 1, 3, Ledger.cut("CLICK A CONTACT ON THE MAP", cols - 2), DIM);
            L.at(p, 1, 4, Ledger.cut("OR IN THE LIST BELOW", cols - 2), DIM);
            L.over(p);
            return;
        }
        L.rev(p, 1, 0, Ledger.cut("NAV  " + n.name.toUpperCase(Locale.ROOT), cols - 2));
        int r = 1;
        L.at(p, 1, r++, Ledger.cut(detail(n), cols - 2), DIM);
        if (!n.contract.isEmpty()) L.at(p, 1, r++, Ledger.cut("CONTRACT " + n.contract.toUpperCase(Locale.ROOT), cols - 2), AMBER);
        L.at(p, 1, r, "DIST", AMBER);
        L.right(p, cols - 1, r++, MapText.distance(n.distance), AMBER);
        if (!Float.isNaN(n.closing)) {
            L.at(p, 1, r, n.closing >= 0 ? "CLOSING" : "OPENING", AMBER);
            L.right(p, cols - 1, r++, String.format(Locale.ROOT, "%,.1f M/S", Math.abs(n.closing)), AMBER);
        }
        if (r < 6) {
            L.at(p, 1, r, "CLOSEST", AMBER);
            String c = Double.isNaN(n.cpaDistance) || Float.isNaN(n.cpaIn) ? "NONE ON THIS PATH"
                    : MapText.distance(n.cpaDistance) + "  " + MapText.tMinus(MapText.time(n.cpaIn));
            L.right(p, cols - 1, r, c, Double.isNaN(n.cpaDistance) ? DIM : AMBER);
        }
        L.over(p);
    }

    /** "STATION · FREE TRADERS", "SHIP · HOSTILE": what the nav target is, and whose or how it stands. */
    static String detail(NavReadout n) {
        String kind = n.kind.name();
        String more = "";
        if (n.station.present && !n.station.owner.isEmpty()) more = n.station.owner.toUpperCase(Locale.ROOT);
        else if (n.relation != Relation.UNKNOWN) more = n.relation.name();
        return more.isEmpty() ? kind : kind + " · " + more;
    }

    /** The next target, and the transfer to the nav target: plan it, accept it, discard it; whether one is up. */
    private void transfer(YardPaint p, float x, float y, float tw) {
        ManeuverPlannerProvider pl = planner();
        boolean target = pl != null && pl.hasTarget(), proposal = pl != null && pl.hasProposal();
        float pw = (tw - 3f * 10f - 6f) / 4f, py = y + 22f;
        p.legend("TARGET", x, y + 12f, Yard.INK);
        p.legend("TRANSFER", x + 3f + pw + 10f, y + 12f, Yard.INK);
        push(p, x + 3f, py, pw, NEXT_LINES, false, pl == null, NEXT);
        push(p, x + 3f + (pw + 10f), py, pw, PLAN_LINES, false, !target, PLAN);
        push(p, x + 3f + 2f * (pw + 10f), py, pw, ACCEPT_LINES, false, !proposal, ACCEPT);
        push(p, x + 3f + 3f * (pw + 10f), py, pw, DISCARD_LINES, false, !proposal, DISCARD);
        NavReadout n = data().navReadout;
        String s = proposal ? "PROPOSED " + Fmt.thousands(n.planDeltaV) + " M/S" : "NONE PROPOSED";
        int cells = Math.min(s.length(), (int) ((tw - pw - 16f) / 10f));
        p.flaps(x + tw - YardPaint.flapsWidth(cells, 10f) + 2f, py + PUSH_H + 12f, s.length() > cells ? s.substring(0, cells) : s,
                cells, 10f, 18f, 13f, proposal ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
    }

    /** The contacts nearest the ship first, or by kind; a click makes one the nav target. */
    private void contacts(YardPaint p, float x, float y, float tw, int cols, float bottom) {
        ManeuverMap m = host.hud().getManeuverMap();
        TrafficLayer layer = m.getTraffic();
        TrafficFrame f = layer.getFrame();
        MapText.order(f.contacts, byKind, order, dist);
        // Hidden layers leave the list as they leave the map; the nav target stays
        for (int i = order.size - 1; i >= 0; i--) {
            Contact c = f.contacts.get(order.get(i));
            if (!c.target && !layer.isShown(c.kind)) order.removeIndex(i);
        }
        p.legend("CONTACTS", x, y + 16f, Yard.INK);
        String counts = layer.counts().toUpperCase(Locale.ROOT).replace("   ", " · ");
        float knobX = x + tw - 30f, knobY = y + 15f;
        p.legendFit(counts, x + 78f, y + 16f, Math.max(0f, knobX - 64f - x - 78f), Yard.INK);
        sortKnob(p, knobX, knobY);
        float ty = y + 32f;
        int rows = Math.max(4, Ledger.rowsIn(bottom - ty));
        Ledger L = contactTube.set(x, ty, cols, rows);
        L.under(p, false);
        L.at(p, 2, 0, byKind ? "KIND, NAME" : "NAME", DIM);
        L.right(p, cols - 1, 0, "DIST", DIM);
        int shown = rows - 1;
        contactShown = shown;
        contactTop = Math.max(0, Math.min(contactTop, order.size - shown));
        if (order.size == 0) L.at(p, 2, 1, f.frameName.isEmpty() ? "NO TRAFFIC PICTURE" : "NONE IN SIGHT", DIM);
        boolean picks = m.takesPicks();
        for (int r = 0; r < shown && contactTop + r < order.size; r++) {
            int i = order.get(contactTop + r), row = 1 + r;
            Contact c = f.contacts.get(i);
            Color ink = c.stale ? DIM : c.kind == ContactKind.HOSTILE ? Yard.P_RED : AMBER;
            if (c.target) L.bar(p, row);
            symbol(p, L, row, c, c.target ? Yard.CRT_BLACK : ink);
            String name = Ledger.cut(c.name.toUpperCase(Locale.ROOT), cols - 14);
            String d = MapText.distance(dist.get(i));
            if (c.target) {
                L.rev(p, 2, row, name);
                L.revRight(p, cols - 1, row, d);
            } else {
                L.at(p, 2, row, name, ink);
                L.right(p, cols - 1, row, d, ink);
            }
            if (picks && !c.id.isEmpty()) hits.add(p, L.x, L.rowTop(row), L.w, Ledger.ROW, this, CONTACT + i);
        }
        L.meter(p, 1, rows - 1, contactTop, shown, order.size);
        L.over(p);
    }

    /** A contact's mark at its row's left: a station's square, a hauler's chevron, a pirate's diamond; ringed when a contract points there. */
    private static void symbol(YardPaint p, Ledger L, int row, Contact c, Color ink) {
        float cx = L.cx(0) + 5f, cy = L.base(row) - 5f;
        if (c.objective) {
            p.circle(cx, cy, 7f, Yard.P_AMBER, 0.9f);
            p.circle(cx, cy, 5.6f, c.target ? Yard.P_AMBER : Yard.CRT_BLACK);
        }
        switch (c.kind) {
            case STATION:
                p.frame(cx - 3.5f, cy - 3.5f, 7f, 7f, 1.4f, ink, 1f);
                break;
            case HAULER:
                p.line(cx - 3f, cy - 4f, cx + 3f, cy, 1.5f, ink);
                p.line(cx + 3f, cy, cx - 3f, cy + 4f, 1.5f, ink);
                break;
            case HOSTILE:
                p.diamond(cx, cy, 4.5f, ink);
                break;
            default:
                p.rect(cx - 2f, cy - 2f, 4f, 4f, ink);
        }
    }

    /** The sort rotary: its pointer at DIST or KIND; a click turns it. */
    private void sortKnob(YardPaint p, float cx, float cy) {
        p.text(YardFonts.Face.LEGEND, 9.5f, "DIST", cx - 34f, cy - 6f, Yard.INK);
        p.text(YardFonts.Face.LEGEND, 9.5f, "KIND", cx + 6f, cy - 9.5f, Yard.INK);
        p.textR(YardFonts.Face.LEGEND, 10f, "SORT", cx - 14f, cy + 9f, Yard.INK);
        p.circle(cx + 0.8f, cy + 1.5f, 9.5f, Yard.SHADOW, 0.6f);
        p.circle(cx, cy, 9.5f, Yard.BAKELITE);
        p.circle(cx, cy, 7.5f, Yard.hex("2E2925"));
        float a = byKind ? 0.35f : 2.6f;
        p.line(cx, cy, cx + MathUtils.cos(a) * 8f, cy - MathUtils.sin(a) * 8f, 2f, Yard.INK_LIGHT);
        hits.add(p, cx - 40f, cy - 18f, 52f, 30f, this, SORT);
    }

    /** The layers as bat toggles, lever up while the layer shows. */
    private void layers(YardPaint p, float x, float y, float tw) {
        p.legend("LAYERS", x, y + 12f, Yard.INK);
        TrafficLayer t = host.hud().getManeuverMap().getTraffic();
        float cw = tw / 3f;
        for (int k = 0; k < LAYERS.length; k++) {
            float cx = x + cw * (k % 3) + cw / 2f, ly = y + 22f + (k / 3) * 50f;
            boolean on = layerOn(t, k);
            p.textC(YardFonts.Face.LEGEND, 10f, LAYERS[k], cx, ly + 8f, Yard.INK);
            p.toggle(cx - 8f, ly + 29f, on, false, false);
            // Engraved in the enamel: the way the lever stands in full ink, the other faint
            p.text(YardFonts.Face.LEGEND, 9.5f, "ON", cx + 6f, ly + 21f, Yard.INK, on ? 1f : 0.35f);
            p.text(YardFonts.Face.LEGEND, 9.5f, "OFF", cx + 6f, ly + 43f, Yard.INK, on ? 0.35f : 1f);
            hits.add(p, cx - cw / 2f, ly, cw, 50f, this, LAYER + k);
        }
    }

    // --- The timeline ------------------------------------------------------------------------

    /**
     * The timeline: an axis from now to past the last event, each event a drawn mark on it with its name
     * and time above and below by turns. A burn's mark selects it.
     */
    private void timeline(YardPaint p) {
        float x = winX, y = tlY, tw = winW, th = TIMELINE_H;
        p.tube(x, y, tw, th, 4f, 5f, false);
        float ax0 = x + 34f, ax1 = x + tw - 30f, ay = y + th / 2f + 2f;
        p.line(ax0, ay, ax1, ay, 5f, Yard.P_GREEN, 0.1f);
        p.line(ax0, ay, ax1, ay, 1.5f, Yard.P_GREEN);
        double span = 600;
        for (int i = 0; i < eventCount; i++) span = Math.max(span, events.get(i).t * 1.1);
        for (int i = 1; i < 10; i++) {
            float tx = ax0 + (ax1 - ax0) * i / 10f;
            p.line(tx, ay - 3f, tx, ay + 3f, 1f, Yard.P_GDIM);
        }
        p.line(ax0, ay - 9f, ax0, ay + 9f, 1.6f, Yard.P_GREEN);
        String end = MapText.tMinus(MapText.time(span));
        float endX = ax1 - p.width(YardFonts.Face.TUBE, 12f, end), below = -1f;
        if (eventCount == 0) {
            p.tube("NOW", ax0 - 14f, ay + 24f, 13f, DIM, false);
            p.tube("NOTHING PLANNED: CLICK THE PATH ON THE MAP TO PLAN A BURN", ax0 + 60f, ay - 12f, 13f, DIM, false);
            p.tubeOver(x, y, tw, th);
            return;
        }
        grow(eventCount + 1);
        // Above and below by turns; NOW is the first below
        for (int side = 0; side < 2; side++) {
            int n = 0;
            if (side == 1) {
                want[n] = ax0 - 14f;
                widths[n] = p.width(YardFonts.Face.TUBE, 13f, "NOW");
                n++;
            }
            for (int i = side; i < eventCount; i += 2) {
                MapText.Event e = events.get(i);
                float ex = ax0 + (float) ((ax1 - ax0) * e.t / span);
                want[n] = ex - 4f;
                widths[n] = Math.max(p.width(YardFonts.Face.TUBE, 13f, e.name), p.width(YardFonts.Face.TUBE, 12f, e.when));
                n++;
            }
            MapText.spread(want, widths, n, x + 8f, x + tw - 8f, 12f, placed);
            if (side == 1) for (int i = 0; i < n; i++) if (!Float.isNaN(placed[i])) below = Math.max(below, placed[i] + widths[i]);
            int k = 0;
            if (side == 1) {
                p.tube("NOW", placed[0], ay + 24f, 13f, DIM, false);
                k = 1;
            }
            for (int i = side; i < eventCount; i += 2, k++) {
                MapText.Event e = events.get(i);
                float ex = ax0 + (float) ((ax1 - ax0) * e.t / span);
                boolean sel = e.burn >= 0 && e.burn == path().selectedNode;
                Color ink = e.red ? Yard.P_RED : e.burn >= 0 ? AMBER : Yard.P_GREEN;
                mark(p, e, ex, ay, ink);
                float lx = placed[k];
                if (Float.isNaN(lx)) continue;
                float nameBase = side == 0 ? ay - 26f : ay + 24f, whenBase = side == 0 ? ay - 12f : ay + 38f;
                if (sel) {
                    float bw = p.width(YardFonts.Face.TUBE, 13f, e.name) + 6f;
                    p.rect(lx - 3f, nameBase - 13f, bw, 17f, AMBER);
                    p.text(YardFonts.Face.TUBE, 13f, e.name, lx, nameBase, Yard.CRT_BLACK);
                } else {
                    p.tube(e.name, lx, nameBase, 13f, ink, false);
                }
                p.tube(e.when, lx, whenBase, 12f, e.red ? Yard.P_RED : DIM, false);
                // A leader from a label pushed along to its mark
                if (Math.abs(lx + 4f - ex) > 6f) p.line(ex, side == 0 ? ay - 8f : ay + 8f, Math.min(Math.max(ex, lx), lx + widths[k]), side == 0 ? ay - 9f : ay + 9f, 1f, Yard.P_GDIM);
                if (e.burn >= 0) hits.add(p, Math.min(lx, ex - 6f), side == 0 ? nameBase - 14f : ay - 6f, Math.max(widths[k], 12f) + Math.abs(lx - ex) + 6f, 36f, this, EVENT + i);
            }
        }
        // How far the axis runs, at its end under it, where no label is
        if (below < endX - 10f) p.tube(end, endX, y + th - 9f, 12f, Yard.P_GDIM, false);
        p.tubeOver(x, y, tw, th);
    }

    /** An event's mark on the axis: a burn's chevron, a change of sphere's ring, an impact's diamond, an apsis' tick. */
    private static void mark(YardPaint p, MapText.Event e, float ex, float ay, Color ink) {
        if (e.burn >= 0) {
            p.line(ex - 4f, ay - 5f, ex, ay, 1.6f, ink);
            p.line(ex, ay, ex - 4f, ay + 5f, 1.6f, ink);
            p.line(ex + 1f, ay - 5f, ex + 5f, ay, 1.6f, ink);
            p.line(ex + 5f, ay, ex + 1f, ay + 5f, 1.6f, ink);
        } else if (e.kind == MarkKind.IMPACT) {
            p.diamond(ex, ay, 5f, ink);
        } else if (e.kind == MarkKind.SOI_ENTRY || e.kind == MarkKind.SOI_EXIT) {
            p.circle(ex, ay, 5f, ink);
            p.circle(ex, ay, 3.4f, Yard.CRT_BLACK);
        } else if (e.closest) {
            p.circle(ex, ay, 4.5f, ink);
        } else {
            p.line(ex, ay - 6f, ex, ay + 6f, 1.4f, ink);
            p.circle(ex, ay, 2.2f, ink);
        }
    }

    private void grow(int n) {
        if (want.length >= n) return;
        want = new float[n * 2];
        widths = new float[n * 2];
        placed = new float[n * 2];
    }

    // --- The foot ----------------------------------------------------------------------------

    /** The feed, the mouse's ways with the map, and the keys that close it. */
    private void foot(YardPaint p) {
        float y = h - FOOT_H;
        p.rect(0f, y, w, FOOT_H, Yard.CHASSIS_DEEP);
        p.rect(0f, y, w, 1.5f, FOOT_SEAM);
        float tw = Math.min(560f, w * 0.44f);
        feed.draw(p, data(), 14f, y + 5f, tw);
        float x = w - 12f;
        x -= p.width(YardFonts.Face.LEGEND, 12f, "CLOSE");
        p.legend("CLOSE", x, y + 18.5f, Yard.INK_LIGHT);
        x -= 7f + p.keyWidth("ESC");
        p.key(x, y + 6.5f, "ESC");
        x -= 3f + p.keyWidth("M");
        p.key(x, y + 6.5f, "M");
        float hx = 14f + tw + 18f, room = x - 18f - hx;
        String hint = hint(false);
        if (p.width(YardFonts.Face.LEGEND, 9.5f, hint) > room) hint = hint(true);
        if (p.width(YardFonts.Face.LEGEND, 9.5f, hint) <= room) p.legendFit(hint, hx, y + 18.5f, room, Yard.LEGEND_FAINT);
    }

    /** How the mouse plans on the map, for the foot; {@code brief} for a narrow screen. */
    private String hint(boolean brief) {
        if (hasSelected) return brief ? "DRAG A HANDLE: ΔV  ·  DRAG THE BURN: MOVE IT"
                : "DRAG A HANDLE: ΔV, SHIFT FINER  ·  DRAG THE BURN: MOVE IT  ·  CLICK THE PATH: ANOTHER";
        return brief ? "CLICK THE PATH: PLAN A BURN  ·  WHEEL: ZOOM"
                : "CLICK THE PATH: PLAN A BURN  ·  CLICK A CONTACT: NAV TARGET  ·  DRAG: PAN  ·  WHEEL: ZOOM";
    }
}
