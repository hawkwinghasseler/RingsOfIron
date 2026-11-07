package com.ringsofiron.uihud.variants.vfd.stations;

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
 * The Plot (functional-stations 10.2; mockup st-plot): the game's orbit canvas across the screen, which
 * keeps everything the map does (zoom, pan, the burns' handles, transfers, the traffic, the patched
 * conics), and a terminal at its right the screen's height with its page keys along the top: MAP (the
 * view's keys and what happens next), PLAN (the burns and the orders that fly them) and TARGETS (the nav
 * target, the transfer to it and the traffic). It hides the band; the top column stands over the
 * canvas, recentred left of the terminal.
 *
 * <p>M opens and closes it, as Esc does. Every key but Tab stays the game's, the map's own among
 * them (the arrows, [ and ], N, Delete, / and the rest): a click on the terminal gives an order as its
 * key would. The terminal has the map's title, so the canvas draws none of its own; what the canvas's
 * help line said (the wheel, a drag, a click on the path or on a contact) is on the MAP page.
 */
final class PlotPanel extends TerminalPanel {
    final PlotEvents events = new PlotEvents();
    final PlotPlan plan = new PlotPlan();
    final PlotTraffic traffic = new PlotTraffic();
    final MapPage mapPage = new MapPage();
    final PlanPage planPage = new PlanPage();
    final TargetsPage targetsPage = new TargetsPage();
    /** The body the map shows, for the title, and what the view centres on. */
    String body = "", centre = "";
    /** The traffic round the body the map shows ("7 STATIONS  12 HAULERS"), empty with none drawn. */
    String counts = "";
    /** Whether a click on a contact makes it the nav target. */
    boolean picks;

    @Override
    Terminal.Kind kind() {
        return Terminal.Kind.PLOT;
    }

    @Override
    Page[] pages() {
        return new Page[] {mapPage, planPage, targetsPage};
    }

    /** M opens it at the page it last showed, the plan the first time. */
    @Override
    Page pageFor(ClassicWindow w, Boards b, HudData d) {
        if (w != ClassicWindow.MAP) return null;
        return page != null ? page : planPage;
    }

    /** The first time, the plan: before the panel's own opening turns to its first page, the map. */
    @Override
    boolean open(ClassicWindow w, Boards b, HudData d) {
        if (page == null && w == ClassicWindow.MAP) turn(planPage);
        return super.open(w, b, d);
    }

    @Override
    boolean fullScreen() {
        return true;
    }

    /** The top column stands over the canvas, left of the terminal. */
    @Override
    boolean beside() {
        return true;
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = "PLOT";
        frame.owner = body.isEmpty() ? "NAVIGATION" : body + " · NAVIGATION";
        frame.figs = 0;
    }

    @Override
    void layout(float stageW, float stageH, float floor) {
        frame.kind = Terminal.Kind.PLOT;
        x = Geo.panelX(stageW);
        y = Geo.PANEL_MARGIN;
        w = Geo.PANEL_W;
        h = stageH - 2f * Geo.PANEL_MARGIN;
        frame.count = Math.min(Terminal.MAX_PAGES, pages.length);
        for (int i = 0; i < frame.count; i++) {
            frame.names[i] = on[i] ? pages[i].name : null;
            frame.caps[i] = pages[i].cap;
        }
        frame.active = index(page);
        frame.keys = page == null ? null : page.keys;
        frame.response = response;
        frame.place(x, y, w, h);
        if (page != null) page.place(frame.at);
    }

    /** Every key but Tab is the game's: the map has its own. */
    @Override
    boolean key(int keycode) {
        return keycode == com.badlogic.gdx.Input.Keys.TAB && super.key(keycode);
    }

    // ------------------------------------------------------------------ reading

    @Override
    void update(Boards b, HudData d, float delta) {
        ManeuverMap m = view();
        OrbitPath p = m != null && m.getMap().hasPath() ? m.getMap().getPath() : null;
        TrafficFrame t = m != null && m.getTraffic().hasProvider() ? m.getTraffic().getFrame() : null;
        read(d, p, t, m);
        super.update(b, d, delta);
    }

    /** This frame's map: the path the canvas drew, the traffic over it, and the canvas itself (null in a test). */
    void read(HudData d, OrbitPath p, TrafficFrame t, ManeuverMap m) {
        body = p == null ? "" : p.bodyName.toUpperCase(Locale.ROOT);
        if (m != null) layers(m.getTraffic());
        String focus = m == null ? body : m.focusName().toUpperCase(Locale.ROOT);
        centre = focus.isEmpty() ? "BODY" : focus;
        counts = p == null ? "" : counts(t, p.bodyName);
        picks = m != null && m.picks();
        events.read(d, p, t);
        plan.read(d, p, events);
        traffic.read(d, p, t);
    }

    /** "7 STATIONS  12 HAULERS  2 PIRATES": the traffic the canvas draws round {@code body}, empty with none. */
    static String counts(TrafficFrame t, String body) {
        if (t == null || t.frameName.isEmpty() || !t.frameName.equals(body)) return "";
        int pirates = t.count(ContactKind.HOSTILE);
        return t.count(ContactKind.STATION) + " STATIONS  " + t.count(ContactKind.HAULER) + " HAULERS" + (pirates == 0 ? "" : "  " + pirates + " PIRATES");
    }

    /** The layers' keys on the canvas's traffic: what shows is what the lamps say. */
    void layers(TrafficLayer l) {
        boolean[] lit = traffic.layer;
        for (int i = 0; i < PlotTraffic.LAYERS; i++) {
            ContactKind k = PlotTraffic.LAYER_KIND[i];
            if (k != null && l.shown(k) != lit[i]) l.setShown(k, lit[i]);
        }
        if (l.objectivesShown() != lit[PlotTraffic.OBJECTIVES]) l.setObjectivesShown(lit[PlotTraffic.OBJECTIVES]);
        if (l.regionsShown() != lit[PlotTraffic.TENSION]) l.setRegionsShown(lit[PlotTraffic.TENSION]);
    }

    ManeuverMap view() {
        UiHud h = panels == null ? null : panels.gameHud();
        return h == null ? null : h.getManeuverMap();
    }

    ManeuverPlannerProvider planner() {
        HudData d = panels == null ? null : panels.data();
        return d == null ? null : d.planner;
    }

    // ------------------------------------------------------------------ the pages

    /** A page of the Plot: it stands for the map, and leaves its keys to the game. */
    abstract static class PlotPage extends Page {
        PlotPage(String name) {
            super(name, ClassicWindow.MAP, "");
        }

        PlotPanel plot() {
            return (PlotPanel) panel;
        }
    }

    /**
     * MAP: what the view is centred on and the traffic it draws; what happens next, soonest first, each a
     * click that centres the map on it and a » that warps on to it; what the pointer does on the plot;
     * and the view's keys at the foot (clicks only, the map having no keys for them): centre on the next
     * body, fit the orbits, fit all the traffic.
     */
    static final class MapPage extends PlotPage {
        static final int EVENT = Panels.PAGE_IDS, WARP = Panels.PAGE_IDS + 40, KEY = Panels.PAGE_IDS + 80;
        static final int CENTRE = 0, FIT = 1, ALL = 2;
        /** Where the list of what happens next starts under the page's top, and the pointer's help at its foot. */
        static final float LIST_TOP = 70f, HELP_H = 20f + 3 * 16f + 10f;
        private int rows = 1;
        private final Glass.Cols cols = new Glass.Cols();

        MapPage() {
            super("MAP");
            // A blank cap on each: no key presses them, so none is walked to with Enter
            keys.define(3, 3).set(CENTRE, "CENTRE", true).set(FIT, "FIT", true).set(ALL, "ALL TRAFFIC", true)
                    .cap(CENTRE, "").cap(FIT, "").cap(ALL, "");
        }

        @Override
        void place(Page.Place p) {
            super.place(p);
            rows = Math.max(1, (int) Math.floor((p.bottom - HELP_H - (p.top + LIST_TOP)) / Glass.ROW));
        }

        @Override
        void paint(Filament f, float time) {
            PlotPanel pl = plot();
            float x0 = at.x0, x1 = at.x1, y = at.top;
            int valueCells = Glass.cells(x1 - x0 - 110f);
            Glass.label(f, x0, y + 9.5f, "CENTRED ON");
            Glass.text(f, x0 + 110f, y, Glass.cut(pl.centre, valueCells), Drive.NORM);
            Glass.label(f, x0, y + 20f + 9.5f, "TRAFFIC");
            Glass.text(f, x0 + 110f, y + 20f, Glass.cut(pl.counts.isEmpty() ? "NONE DRAWN HERE" : pl.counts, valueCells), pl.counts.isEmpty() ? Drive.DIM : Drive.NORM);
            y += 50f;
            Glass.head(f, x0, y, x1, "NEXT");
            y += 20f;
            if (pl.events.events.size == 0) Glass.dim(f, x0 + Glass.CW, y, "NOTHING PLANNED: CLICK THE PATH FOR A BURN");
            int cells = Glass.cells(x1 - x0);
            for (int i = 0; i < Math.min(rows, pl.events.events.size); i++) {
                PlotEvents.Event e = pl.events.events.get(i);
                String line = cols.clear().left(e.name, cells - 16).right(e.when, 12).gap(2).left("»", 1).toString();
                Glass.text(f, x0, y + i * Glass.ROW, line, e.estimate ? Drive.DIM : Drive.NORM);
            }
            // What the canvas's own help line said, the terminal having its title
            float hy = at.bottom - HELP_H + 6f;
            Glass.head(f, x0, hy, x1, "ON THE PLOT");
            hy += 20f;
            Glass.dim(f, x0, hy, "THE WHEEL ZOOMS, A DRAG PANS");
            Glass.dim(f, x0, hy + 16f, "A CLICK ON THE PATH PLANS A BURN THERE");
            if (pl.picks) Glass.dim(f, x0, hy + 32f, "A CLICK ON A CONTACT MAKES IT THE NAV TARGET");
        }

        @Override
        void boxes(FilamentActor a) {
            PlotPanel pl = plot();
            float x0 = at.x0, x1 = at.x1, y = at.top + LIST_TOP;
            for (int i = 0; i < Math.min(rows, pl.events.events.size); i++) {
                a.box(EVENT + i, x0 - 2f, y + i * Glass.ROW - 3f, x1 - x0 - 30f, Glass.ROW);
                a.box(WARP + i, x1 - 30f, y + i * Glass.ROW - 3f, 32f, Glass.ROW);
            }
            keys.boxes(a, KEY);
        }

        @Override
        void clicked(int id) {
            PlotPanel pl = plot();
            ManeuverMap m = pl.view();
            ManeuverPlannerProvider planner = pl.planner();
            if (id >= KEY && id < KEY + 3) {
                if (m == null) return;
                if (id - KEY == CENTRE) m.nextFocus();
                else if (id - KEY == FIT) m.fit();
                else m.fitTraffic();
            } else if (id >= WARP && id < WARP + 40) {
                PlotEvents.Event e = event(id - WARP);
                if (e == null || planner == null) return;
                // The next burn's own warp stops for the helm to turn onto it; anything else stops a little before
                if (e.burn == 0) planner.warpToBurn();
                else planner.warpTo(e.in, e.name.toLowerCase(Locale.ROOT));
            } else if (id >= EVENT && id < EVENT + 40) {
                PlotEvents.Event e = event(id - EVENT);
                if (e != null && e.placed && m != null) m.centreOn(e.x, e.y);
            }
        }

        private PlotEvents.Event event(int i) {
            PlotEvents ev = plot().events;
            return i >= 0 && i < ev.events.size ? ev.events.get(i) : null;
        }
    }

    /** PLAN: the orbit now, the burns, where the selected one leads, the plan's delta-v, and the orders that fly it. */
    static final class PlanPage extends PlotPage {
        PlanPage() {
            super("PLAN");
            keys.define(0, 0);
        }

        @Override
        void place(Page.Place p) {
            super.place(p);
            // The pane's own margins: 10 in, its first line 30 under its top
            plot().plan.place(p.x0 - 10f, p.top - 30f, p.x1 - p.x0 + 20f, p.bottom + 10f - (p.top - 30f));
        }

        @Override
        void paint(Filament f, float time) {
            plot().plan.paint(f);
        }

        @Override
        void boxes(FilamentActor a) {
            plot().plan.boxes(a);
        }

        @Override
        void scrolled(int amount) {
            plot().plan.scroll(amount);
        }

        @Override
        void clicked(int id) {
            PlotPanel pl = plot();
            ManeuverPlannerProvider p = pl.planner();
            if (p == null) return;
            if (id >= PlotPlan.BURN && id < PlotPlan.BURN + PlotPlan.MAX_BURNS) {
                p.selectBurn(id - PlotPlan.BURN);
            } else if (id >= PlotPlan.ORDER && id < PlotPlan.ORDER + PlotPlan.ORDERS) {
                switch (id - PlotPlan.ORDER) {
                    case PlotPlan.HELM:
                        p.helmFlyBurns();
                        break;
                    case PlotPlan.AUTOPILOT:
                        p.autopilot();
                        break;
                    case PlotPlan.WARP:
                        p.warpToBurn();
                        break;
                    case PlotPlan.DELETE:
                        p.deleteSelected();
                        break;
                    case PlotPlan.CLEAR:
                        p.clearBurns();
                        break;
                    default:
                }
            }
            pl.panels.refresh();
        }
    }

    /** TARGETS: the nav target, the transfer to it, the traffic round the body the map shows, and the map's layers. */
    static final class TargetsPage extends PlotPage {
        TargetsPage() {
            super("TARGETS");
            keys.define(0, 0);
        }

        @Override
        void place(Page.Place p) {
            super.place(p);
            plot().traffic.place(p.x0 - 10f, p.top - 30f, p.x1 - p.x0 + 20f, p.bottom + 10f - (p.top - 30f));
        }

        @Override
        void paint(Filament f, float time) {
            plot().traffic.paint(f);
        }

        @Override
        void boxes(FilamentActor a) {
            plot().traffic.boxes(a);
        }

        @Override
        void scrolled(int amount) {
            plot().traffic.scroll(amount);
        }

        @Override
        void clicked(int id) {
            PlotPanel pl = plot();
            PlotTraffic t = pl.traffic;
            ManeuverPlannerProvider p = pl.planner();
            ManeuverMap m = pl.view();
            if (id == PlotTraffic.NEXT_TARGET) {
                if (p != null) p.nextTarget();
            } else if (id == PlotTraffic.PLAN) {
                if (p != null && t.canPlan) p.planToTarget();
            } else if (id == PlotTraffic.ACCEPT) {
                if (p != null && t.proposed) p.acceptPlan();
            } else if (id == PlotTraffic.DISCARD) {
                if (p != null && t.proposed) p.discardPlan();
            } else if (id == PlotTraffic.SORT) {
                t.byKind = !t.byKind;
                t.first = 0;
            } else if (id >= PlotTraffic.LAYER && id < PlotTraffic.LAYER + PlotTraffic.LAYERS) {
                int i = id - PlotTraffic.LAYER;
                t.layer[i] = !t.layer[i];
                if (m != null) pl.layers(m.getTraffic());
            } else if (id >= PlotTraffic.CONTACT && id < PlotTraffic.CONTACT + PlotTraffic.MAX_CONTACTS) {
                String pick = t.contactId[id - PlotTraffic.CONTACT];
                if (m != null && m.picks()) m.pick(pick);
            }
            pl.panels.refresh();
        }
    }
}
