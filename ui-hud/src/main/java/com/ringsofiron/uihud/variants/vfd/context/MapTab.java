package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.widgets.ManeuverMap;
import com.ringsofiron.uihud.widgets.TrafficLayer;

import java.util.Locale;

/**
 * M1's panels round the game's orbit canvas (functional-context 10.1; mockup cx-map), which keeps
 * everything the map does: zoom, pan, the burns' handles, transfers, the traffic and the patched
 * conics. The Plan pane on the left, the Target and traffic pane on the right, the view's keys in the
 * bezel over the plot (centre, fit, all traffic) and the timeline under it. Only the panels take the
 * pointer: a click, a drag or the wheel over the plot is the map's. Every order is the game's: a click
 * gives it as its key would, and the keys themselves stay the game's, the number keys among them
 * (the map has no tabs).
 */
final class MapTab extends ModeTab {
    /** The view's keys over the plot, and the gap between the panes and the plot; their labels take the plot's top 22. */
    static final float GAP = 6f, LABELS = 22f;
    static final int VIEW_KEYS = 3;
    static final String[] VIEW_WORD = {"CENTRE: ", "FIT", "ALL TRAFFIC"};
    /** Click ids, from the tab's: the view's keys, and the timeline's events and their warps. */
    static final int VIEW = Modes.TAB_IDS + 210, EVENT = Modes.TAB_IDS + 150, WARP = Modes.TAB_IDS + 180;

    final MapPlan plan = new MapPlan();
    final MapTraffic traffic = new MapTraffic();
    final MapTimeline timeline = new MapTimeline();
    /** The body the map shows, for the title, and what the view centres on. */
    String body = "", centre = "";
    /** The plot: the window through the frame to the game's canvas. */
    float px, py, pw, ph;
    private final String[] viewWords = new String[VIEW_KEYS];
    private final float[] keyAt = new float[2];

    MapTab() {
        super("MAP", ClassicWindow.MAP, "M");
    }

    // ------------------------------------------------------------------ reading

    @Override
    void update(Boards b, HudData d, float delta) {
        ManeuverMap m = view();
        OrbitPath p = m != null && m.getMap().hasPath() ? m.getMap().getPath() : null;
        TrafficFrame t = m != null && m.getTraffic().hasProvider() ? m.getTraffic().getFrame() : null;
        read(d, p, t, m);
    }

    /** This frame's map: the path the canvas drew, the traffic over it, and the canvas itself (null in a test). */
    void read(HudData d, OrbitPath p, TrafficFrame t, ManeuverMap m) {
        body = p == null ? "" : p.bodyName.toUpperCase(Locale.ROOT);
        if (m != null) layers(m.getTraffic());
        String focus = m == null ? body : m.focusName().toUpperCase(Locale.ROOT);
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

    private ManeuverMap view() {
        UiHud h = modes == null ? null : modes.gameHud();
        return h == null ? null : h.getManeuverMap();
    }

    // ------------------------------------------------------------------ laying out

    /** The content area: the panes down each side, the plot between them under the view's keys, the timeline under the plot. */
    @Override
    void place(float x, float y, float w, float h) {
        super.place(x, y, w, h);
        plan.place(x, y, MapPlan.W, h);
        traffic.place(x + w - MapTraffic.W, y, MapTraffic.W, h);
        px = x + MapPlan.W + GAP;
        pw = x + w - MapTraffic.W - GAP - px;
        py = y + Glass.KEY_H + GAP;
        ph = y + h - MapTimeline.H - GAP - py;
        timeline.place(px, y + h - MapTimeline.H, pw);
    }

    // ------------------------------------------------------------------ painting

    @Override
    void paint(Filament f, float time) {
        plan.paint(f);
        traffic.paint(f);
        viewWords[0] = Glass.cut(VIEW_WORD[0] + centre, Glass.cells((pw - 16f) / VIEW_KEYS - 8f));
        viewWords[1] = VIEW_WORD[1];
        viewWords[2] = VIEW_WORD[2];
        Glass.keyStrip(f, px + 8f, y, pw - 16f, VIEW_KEYS, VIEW_KEYS, py + 6f, null, viewWords, null, null, null);
        timeline.paint(f);
    }

    // ------------------------------------------------------------------ the pointer

    @Override
    void boxes(FilamentActor a) {
        plan.boxes(a);
        traffic.boxes(a);
        for (int i = 0; i < VIEW_KEYS; i++) {
            Glass.keyBox(px + 8f, pw - 16f, VIEW_KEYS, i, keyAt);
            a.box(VIEW + i, keyAt[0], y, keyAt[1], Glass.KEY_H);
        }
        timeline.boxes(a, EVENT, WARP);
    }

    /** The wheel turns the list under the pointer: the burns, or the contacts. */
    @Override
    void scrolled(int amount) {
        if (modes == null) return;
        if (plan.overBurns(modes.pointerX, modes.pointerY)) plan.scroll(amount);
        else if (traffic.overContacts(modes.pointerX, modes.pointerY)) traffic.scroll(amount);
    }

    @Override
    void clicked(int id) {
        act(id, modes == null ? null : modes.data(), view());
        if (modes != null) modes.refresh();
    }

    /** A click on box {@code id}: the order it gives the game's planner on {@code d}, or what it does to the canvas {@code m} (null in a test). */
    void act(int id, HudData d, ManeuverMap m) {
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
            if (m != null) layers(m.getTraffic());
        } else if (id >= MapTraffic.CONTACT && id < MapTraffic.CONTACT + MapTraffic.MAX_CONTACTS) {
            String pick = traffic.contactId[id - MapTraffic.CONTACT];
            if (m != null && m.picks()) m.pick(pick);
        } else if (id >= VIEW && id < VIEW + VIEW_KEYS) {
            if (m != null) view(m, id - VIEW);
        } else if (id >= EVENT && id < EVENT + MapTimeline.MAX) {
            MapTimeline.Event e = event(id - EVENT);
            if (e != null && e.placed && m != null) m.centreOn(e.x, e.y);
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

    private static void view(ManeuverMap m, int i) {
        switch (i) {
            case 0:
                m.nextFocus();
                break;
            case 1:
                m.fit();
                break;
            default:
                m.fitTraffic();
        }
    }
}
