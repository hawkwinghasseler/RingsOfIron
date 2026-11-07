package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.widgets.ManeuverMap;
import com.ringsofiron.uihud.widgets.TrafficLayer;

import java.util.Locale;

/**
 * The map (functional-task 7, MAP; mockup tk-map): the game's orbit canvas through a window in a
 * plate across the screen under the band, which keeps everything the map does (zoom, pan, the burns'
 * handles, transfers, the traffic, the patched conics), the body it shows in fourteen segments over it
 * with the view's keys and the close keys; beside it the Destination pane (MAP.1: the navigation
 * target, the transfer's keys, the contacts round the body, and the layers, MAP.6) and the Plan pane
 * (MAP.2 to MAP.4: the orbit now, the burns with the selected one, the plan's delta-v against what is
 * left and the arrival, and the orders that fly it, each with its key); and the plan's timeline across
 * the foot (MAP.5). Only the panes take the pointer: a click, a drag or the wheel over the plot is the
 * map's. Every order is the game's: a click gives it as its key would, and the keys stay the game's.
 */
final class MapScreen extends Screen {
    /** The gaps between the plot and the panes, the plates' pad, the header row over the plot. */
    static final float GAP = 12f, PAD = 6f, HEAD = 30f;
    static final int VIEW_KEYS = 3;
    static final String[] VIEW_WORD = {"CENTRE: ", "FIT", "ALL TRAFFIC"};
    /**
     * The header's parts: the room the close keys take at its right with a gap before them, a view key's
     * pad round its word and the gap between two, and the body's name in fourteen segments (a cell's
     * advance at its height, as {@code Filament.seg14Width} sets it).
     */
    static final float CLOSE_ROOM = 172f, KEY_PAD = 16f, KEY_GAP = 6f, TITLE_H = 13f, TITLE_CELL = TITLE_H * 0.84f;
    /** Click ids: the timeline's events and their warps, and the view's keys. */
    static final int EVENT = Windows.TAB_IDS + 150, WARP = Windows.TAB_IDS + 180, VIEW = Windows.TAB_IDS + 210;

    final MapPlan plan = new MapPlan();
    final MapTraffic traffic = new MapTraffic();
    final MapTimeline timeline = new MapTimeline();
    final MapTab view = new MapTab();
    /** The body the map shows, for the title, and what the view centres on. */
    String body = "", centre = "";
    /** The plot: the window through the plate to the game's canvas. */
    float px, py, pw, ph;
    /** The plot's plate: the box round the header and the plot. */
    private float bx, by, bw, bh;
    private final String[] viewWords = new String[VIEW_KEYS];
    /** The view's keys in the header, each round its word, and the body's name as it fits before them. */
    private final float[] keyX = new float[VIEW_KEYS], keyW = new float[VIEW_KEYS];
    private String heading = "";
    private final String[] esc = {"ESC"}, m = {"M"};

    @Override
    Tab[] tabs() {
        return new Tab[] {view};
    }

    @Override
    void title(Boards b, HudData d) {
    }

    // ------------------------------------------------------------------ reading

    @Override
    void update(Boards b, HudData d, float delta) {
        ManeuverMap mm = canvas();
        OrbitPath p = mm != null && mm.getMap().hasPath() ? mm.getMap().getPath() : null;
        TrafficFrame t = mm != null && mm.getTraffic().hasProvider() ? mm.getTraffic().getFrame() : null;
        read(d, p, t, mm);
    }

    /** This frame's map: the path the canvas drew, the traffic over it, and the canvas itself (null in a test). */
    void read(HudData d, OrbitPath p, TrafficFrame t, ManeuverMap mm) {
        body = p == null ? "" : p.bodyName.toUpperCase(Locale.ROOT);
        if (mm != null) layers(mm.getTraffic());
        String focus = mm == null ? body : mm.focusName().toUpperCase(Locale.ROOT);
        centre = focus.isEmpty() ? "BODY" : focus;
        timeline.read(d, p, t);
        plan.read(d, p, timeline);
        traffic.read(d, p, t);
    }

    /** The layers' keys on the canvas's traffic: what shows is what the lamps say. */
    void layers(TrafficLayer l) {
        boolean[] on = traffic.layer;
        for (int i = 0; i < MapTraffic.LAYERS; i++) {
            ContactKind k = MapTraffic.LAYER_KIND[i];
            if (k != null && l.shown(k) != on[i]) l.setShown(k, on[i]);
        }
        if (l.objectivesShown() != on[MapTraffic.OBJECTIVES]) l.setObjectivesShown(on[MapTraffic.OBJECTIVES]);
        if (l.regionsShown() != on[MapTraffic.TENSION]) l.setRegionsShown(on[MapTraffic.TENSION]);
    }

    private ManeuverMap canvas() {
        UiHud h = windows == null ? null : windows.gameHud();
        return h == null ? null : h.getManeuverMap();
    }

    // ------------------------------------------------------------------ laying out

    /** Across the stage under the band: the plot left, the Destination and Plan panes right, the timeline under them all. */
    @Override
    void layout(float stageW, float stageH) {
        x = Geo.SCREEN_X;
        y = Geo.SCREEN_Y;
        w = stageW - 2f * Geo.SCREEN_X;
        h = stageH - Geo.SCREEN_FOOT - y;
        float ty = y + h - MapTimeline.H, top = ty - GAP - y;
        float planX = x + w - MapPlan.W, trafficX = planX - GAP - MapTraffic.W;
        plan.place(planX, y, MapPlan.W, top);
        traffic.place(trafficX, y, MapTraffic.W, top);
        timeline.place(x, ty, w);
        bx = x;
        by = y;
        bw = trafficX - GAP - x;
        bh = top;
        px = bx;
        py = by + HEAD;
        pw = bw;
        ph = bh - HEAD;
        header();
    }

    /**
     * The header from its right: the close keys, then the view's keys, each round its word, then the
     * body's name in what is left. Where there is not room for all of it, the name gives up " SYSTEM"
     * first, then the centre's word is cut, keeping a few letters of what it centres on, and last the
     * name is cut (fourteen segments have no "…").
     */
    private void header() {
        float left = bx + 8f, right = bx + bw - CLOSE_ROOM;
        float room = right - left - keyWidth(VIEW_WORD[1]) - keyWidth(VIEW_WORD[2]) - 2f * KEY_GAP;
        String full = body.isEmpty() ? "NO MAP" : body + " SYSTEM", centred = VIEW_WORD[0] + centre;
        heading = body.isEmpty() || named(full) + keyWidth(centred) <= room ? full : body;
        int centreCells = Math.max(VIEW_WORD[0].length() + 3, Glass.cells(room - named(heading) - KEY_PAD));
        viewWords[0] = Glass.cut(centred, centreCells);
        viewWords[1] = VIEW_WORD[1];
        viewWords[2] = VIEW_WORD[2];
        float kx = right;
        for (int i = VIEW_KEYS - 1; i >= 0; i--) {
            keyW[i] = keyWidth(viewWords[i]);
            kx -= keyW[i];
            keyX[i] = kx;
            kx -= KEY_GAP;
        }
        int cells = Math.max(4, (int) Math.floor((keyX[0] - 16f - left + TITLE_H * 0.24f) / TITLE_CELL));
        if (heading.length() > cells) heading = heading.substring(0, cells).stripTrailing();
    }

    /** How wide the body's name {@code s} sets in the header, with its gap before the keys. */
    private static float named(String s) {
        return TITLE_CELL * s.length() + 16f;
    }

    private static float keyWidth(String word) {
        return Glass.width(word.length()) + KEY_PAD;
    }

    /** The word on view key {@code i}, and where that key is on the stage: its left and its width. */
    String viewWord(int i) {
        return viewWords[i];
    }

    float viewKeyX(int i) {
        return keyX[i];
    }

    float viewKeyW(int i) {
        return keyW[i];
    }

    /** The body's name as the header sets it. */
    String headerTitle() {
        return heading;
    }

    /** The plot's edges on the stage, for the game's canvas's insets. */
    float plotLeft() {
        return px;
    }

    float plotTop() {
        return py;
    }

    float plotRight() {
        return px + pw;
    }

    float plotBottom() {
        return py + ph;
    }

    // ------------------------------------------------------------------ painting

    @Override
    void paint(Filament f, float time) {
        // The plate round the plot, its window cut through to the canvas; the header row on the plate
        f.plateAround(bx, by, bw, bh, PAD, 8f, px, py, pw, ph);
        f.rectLit(px, py - 1f, pw, 0.8f, f.o().ghostOnly().k(2.2f));
        f.seg14(bx + 8f, by + 6f, TITLE_H, heading, f.o().norm());
        for (int i = 0; i < VIEW_KEYS; i++) {
            f.key(keyX[i], by + 3f, keyW[i], Glass.KEY_H - 2f, "", f.o().size(11f));
            Glass.text(f, keyX[i] + KEY_PAD / 2f, by + 9f, viewWords[i], Drive.NORM);
        }
        float cx = bx + bw - 8f - Glass.capWordWidth(f, esc, "CLOSE") - Glass.capWordWidth(f, m, "OR");
        cx = Glass.capWord(f, cx, by + 5f, m, "OR", Drive.DIM) - 8f;
        Glass.capWord(f, cx, by + 5f, esc, "CLOSE", Drive.DIM);
        traffic.paint(f);
        plan.paint(f);
        timeline.paint(f);
    }

    // ------------------------------------------------------------------ the pointer

    /** Only the panes, the header and the timeline take the pointer; the plot is the canvas's. */
    @Override
    void boxes(FilamentActor a) {
        a.box(Windows.PLATE, bx - PAD, by - PAD, bw + 2f * PAD, HEAD + PAD);
        a.box(Windows.PLATE, traffic.x - PAD, traffic.y - PAD, traffic.w + 2f * PAD, traffic.h + 2f * PAD);
        a.box(Windows.PLATE, plan.x - PAD, plan.y - PAD, plan.w + 2f * PAD, plan.h + 2f * PAD);
        a.box(Windows.PLATE, x - PAD, y + h - MapTimeline.H - PAD, w + 2f * PAD, MapTimeline.H + 2f * PAD);
        plan.boxes(a);
        traffic.boxes(a);
        for (int i = 0; i < VIEW_KEYS; i++) a.box(VIEW + i, keyX[i], by + 3f, keyW[i], Glass.KEY_H - 2f);
        timeline.boxes(a, EVENT, WARP);
    }

    @Override
    void clicked(int id) {
        act(id, windows == null ? null : windows.data(), canvas());
        if (windows != null) windows.refresh();
    }

    /** A click on box {@code id}: the order it gives the game's planner on {@code d}, or what it does to the canvas {@code mm} (null in a test). */
    void act(int id, HudData d, ManeuverMap mm) {
        ManeuverPlannerProvider pl = d == null ? null : d.planner;
        if (id >= MapPlan.BURN && id < MapPlan.BURN + MapPlan.MAX_BURNS) {
            if (pl != null) pl.selectBurn(id - MapPlan.BURN);
        } else if (id >= MapPlan.ORDER && id < MapPlan.ORDER + MapPlan.ORDERS) {
            order(pl, id - MapPlan.ORDER);
        } else if (id == MapTraffic.NEXT_TARGET) {
            if (pl != null) pl.nextTarget();
        } else if (id == MapTraffic.PLAN) {
            if (pl != null && traffic.canPlan) pl.planToTarget();
        } else if (id == MapTraffic.ACCEPT) {
            if (pl != null && traffic.proposed) pl.acceptPlan();
        } else if (id == MapTraffic.DISCARD) {
            if (pl != null && traffic.proposed) pl.discardPlan();
        } else if (id == MapTraffic.SORT) {
            traffic.byKind = !traffic.byKind;
            traffic.first = 0;
        } else if (id >= MapTraffic.LAYER && id < MapTraffic.LAYER + MapTraffic.LAYERS) {
            int i = id - MapTraffic.LAYER;
            traffic.layer[i] = !traffic.layer[i];
            if (mm != null) layers(mm.getTraffic());
        } else if (id >= MapTraffic.CONTACT && id < MapTraffic.CONTACT + MapTraffic.MAX_CONTACTS) {
            String pick = traffic.contactId[id - MapTraffic.CONTACT];
            if (mm != null && mm.picks()) mm.pick(pick);
        } else if (id >= VIEW && id < VIEW + VIEW_KEYS) {
            if (mm != null) view(mm, id - VIEW);
        } else if (id >= EVENT && id < EVENT + MapTimeline.MAX) {
            MapTimeline.Event e = event(id - EVENT);
            if (e != null && e.placed && mm != null) mm.centreOn(e.x, e.y);
        } else if (id >= WARP && id < WARP + MapTimeline.MAX) {
            MapTimeline.Event e = event(id - WARP);
            if (e != null && pl != null) {
                // The next burn's own warp stops for the helm to turn onto it; anything else stops a little before
                if (e.burn == 0) pl.warpToBurn();
                else pl.warpTo(e.in, e.name.toLowerCase(Locale.ROOT));
            }
        }
    }

    private MapTimeline.Event event(int i) {
        return i >= 0 && i < timeline.events.size ? timeline.events.get(i) : null;
    }

    private static void order(ManeuverPlannerProvider pl, int i) {
        if (pl == null) return;
        switch (i) {
            case MapPlan.HELM:
                pl.helmFlyBurns();
                break;
            case MapPlan.AUTOPILOT:
                pl.autopilot();
                break;
            case MapPlan.WARP:
                pl.warpToBurn();
                break;
            case MapPlan.DELETE:
                pl.deleteSelected();
                break;
            case MapPlan.CLEAR:
                pl.clearBurns();
                break;
            default:
        }
    }

    private static void view(ManeuverMap mm, int i) {
        switch (i) {
            case 0:
                mm.nextFocus();
                break;
            case 1:
                mm.fit();
                break;
            default:
                mm.fitTraffic();
        }
    }

    /** The wheel turns the list under the pointer: the burns, or the contacts. */
    void scrolled(int amount) {
        if (windows == null) return;
        if (plan.overBurns(windows.pointerX, windows.pointerY)) plan.scroll(amount);
        else if (traffic.overContacts(windows.pointerX, windows.pointerY)) traffic.scroll(amount);
    }

    /** The map's one tab: it stands for the game's map, and M closes it. */
    final class MapTab extends Tab {
        MapTab() {
            super("MAP", ClassicWindow.MAP, "M");
        }

        @Override
        void paint(Filament f, float time) {
        }

        @Override
        void scrolled(int amount) {
            MapScreen.this.scrolled(amount);
        }
    }
}
