package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;

/**
 * The full-screen map (M): the game's {@link OrbitMap} at any zoom, with the apsides, changes of sphere
 * of influence and impacts labelled, the traffic around the body over it (a {@link TrafficLayer}:
 * stations, haulers, pirates, derelicts, and the tension readout), and a side panel to plan burns and
 * fly them.
 *
 * <p>Mouse: a click on the path plans a burn there; a click on a burn selects it, and dragging it slides
 * it along its orbit. The selected burn has four handles, prograde and retrograde, radial out and in:
 * dragging one out adds delta-v that way, more the farther it goes (Shift for a tenth as much), and the
 * planned path follows as it changes. Dragging empty space, or with the right button, pans; the wheel
 * zooms about the pointer. A click on a contact makes it the navigation target ({@link #setPickListener}).
 * The panel's buttons warp to the next burn, give the helm the order to fly the
 * burns, start the transfer autopilot, and plan, accept or discard a transfer to the navigation target.
 *
 * <p>A HUD variant can give it its own look ({@link #setPainter}: everything on the canvas, the
 * backdrop, the burn handles, the scale bar and the title), show the side panel's contents in its own
 * panels instead ({@link #setOwnPanel}), and keep the canvas clear of them ({@link #setCanvasInsets}).
 * The zoom, the panning, the burn handles and the picks stay as they are.
 */
public class ManeuverMap extends Table {
    /** How far from a burn its handles sit, pixels, and how near a press must land to take one. */
    static final float HANDLE_DISTANCE = 46f, HANDLE_RADIUS = 10f;
    /** How near a press must land to a burn to take it, and to the path to plan one there, pixels. */
    static final float NODE_PICK = 9f, PATH_PICK = 12f;
    /** A press that moves less than this before it is let go is a click, pixels. */
    static final float DRAG_START = 4f;
    /** A handle dragged {@link #DRAG_UNIT} pixels out adds {@link #DRAG_DV} m/s; twice as far, four times as much. */
    static final double DRAG_UNIT = 50, DRAG_DV = 20;
    static final float PANEL_WIDTH = 300f;
    static final float REFRESH_INTERVAL = 0.25f;
    /** Zoom limits, meters per pixel. */
    static final double MIN_SCALE = 1, MAX_SCALE = 5e7;

    /** What the view is centred on, with any panning added. */
    public enum Focus { BODY, SHIP, TARGET, ENCOUNTER }

    private static final String INSTRUCTIONS = "[M] close   [wheel] zoom   [drag] pan   [click the path] plan a burn";

    private final OrbitMap map;
    private MapPainter painter;
    private final TrafficLayer traffic;
    private java.util.function.Consumer<String> pickListener;
    private final Label orbitInfo, burnInfo, planInfo, hint;
    private final Table panel, burnButtons, proposalButtons;
    private final TextButton focusButton, planButton;
    private ManeuverPlannerProvider planner;
    private float sinceRefresh = REFRESH_INTERVAL;

    /** Meters per pixel, 0 to fit the orbit around the body; the view's middle, meters from the focus. */
    private double metersPerPixel, panX, panY;
    private Focus focus = Focus.BODY;

    /** What the press now down is doing. */
    private enum Drag { NONE, HANDLE, NODE, PAN, PRESS }
    private Drag drag = Drag.NONE;
    private int dragHandle = -1, hoverHandle = -1;
    private float downX, downY, dragX, dragY;
    private double startPro, startRad, startCenterX, startCenterY;
    private final double[] burn = new double[2];

    public ManeuverMap(Skin skin) {
        TextureRegion white = skin.getRegion("white");
        BitmapFont font = skin.getFont("default-font");
        map = new OrbitMap(white);
        map.setFont(font);
        map.setShowMarks(true);
        // Clear of the title at the top, with room for the apsides' labels
        map.setFitMargin(48f);
        traffic = new TrafficLayer(white, font);
        painter = map.getPainter();
        traffic.setPainter(painter);

        orbitInfo = info(skin);
        burnInfo = info(skin);
        planInfo = info(skin);
        hint = new Label("", skin, "dim");
        hint.setWrap(true);

        panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        panel.pad(10f);
        panel.top().left();
        panel.defaults().left().width(PANEL_WIDTH - 20f);
        panel.add(new Label("ORBIT", skin, "title")).row();
        panel.add(orbitInfo).padBottom(8f).row();
        panel.add(new Label("BURN", skin, "title")).row();
        panel.add(burnInfo).row();
        burnButtons = new Table();
        burnButtons.left();
        burnButtons.add(order(skin, "Delete burn", ManeuverPlannerProvider::deleteSelected)).padRight(6f);
        burnButtons.add(order(skin, "Clear all", ManeuverPlannerProvider::clearBurns));
        panel.add(burnButtons).padTop(4f).padBottom(8f).row();
        panel.add(new Label("FLY IT", skin, "title")).row();
        Table fly = new Table();
        fly.left();
        fly.defaults().left().padBottom(4f);
        fly.add(order(skin, "Warp to burn", ManeuverPlannerProvider::warpToBurn)).padRight(6f);
        fly.add(order(skin, "Helm: fly burns", ManeuverPlannerProvider::helmFlyBurns)).row();
        fly.add(order(skin, "Autopilot", ManeuverPlannerProvider::autopilot)).colspan(2);
        panel.add(fly).row();
        panel.add(planInfo).padBottom(8f).row();
        panel.add(new Label("TARGET", skin, "title")).row();
        Table target = new Table();
        target.left();
        target.add(order(skin, "Next target", ManeuverPlannerProvider::nextTarget)).padRight(6f);
        planButton = order(skin, "Plan transfer", ManeuverPlannerProvider::planToTarget);
        target.add(planButton);
        panel.add(target).padTop(2f).row();
        proposalButtons = new Table();
        proposalButtons.left();
        proposalButtons.add(order(skin, "Accept plan", ManeuverPlannerProvider::acceptPlan)).padRight(6f);
        proposalButtons.add(order(skin, "Discard", ManeuverPlannerProvider::discardPlan));
        panel.add(proposalButtons).padTop(4f).row();
        panel.add(new Label("VIEW", skin, "title")).padTop(8f).row();
        Table view = new Table();
        view.left();
        focusButton = button(skin, "Centre: body", this::nextFocus);
        view.add(focusButton).padRight(6f);
        view.add(button(skin, "Fit", this::fit)).padRight(6f);
        view.add(button(skin, "All traffic", this::fitTraffic));
        panel.add(view).padTop(2f).row();
        panel.add(hint).padTop(10f).row();

        add(map).expand().fill();
        add(panel).top().right().width(PANEL_WIDTH).pad(44f, 0f, 10f, 10f);

        map.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return press(event.getStageX(), event.getStageY(), button);
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                dragTo(event.getStageX(), event.getStageY());
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                release(event.getStageX(), event.getStageY());
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                hoverHandle = handleAt(event.getStageX(), event.getStageY());
                return false;
            }
        });
        setVisible(false);
    }

    private static Label info(Skin skin) {
        Label l = new Label("", skin);
        l.setWrap(true);
        return l;
    }

    private TextButton button(Skin skin, String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.pad(2f, 8f, 2f, 8f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!b.isDisabled()) action.run();
                refresh();
            }
        });
        return b;
    }

    /** A button that gives the planner an order. */
    private TextButton order(Skin skin, String text, java.util.function.Consumer<ManeuverPlannerProvider> order) {
        return button(skin, text, () -> {
            if (planner != null) order.accept(planner);
        });
    }

    public void setProvider(OrbitPathProvider provider) {
        map.setProvider(provider);
    }

    /** Off-bubble traffic to draw over the orbits; null for none. */
    public void setTraffic(TrafficMapProvider provider) {
        traffic.setProvider(provider);
    }

    public TrafficLayer getTraffic() {
        return traffic;
    }

    /** Called with a contact's id when the player clicks it; null turns picking contacts off. */
    public void setPickListener(java.util.function.Consumer<String> listener) {
        pickListener = listener;
    }

    /** Whether the traffic is drawn: there is some, around the body the orbits are drawn around. */
    boolean showsTraffic() {
        return traffic.matches(map.getPath().bodyName);
    }

    public void setPlanner(ManeuverPlannerProvider planner) {
        this.planner = planner;
        refresh();
    }

    public OrbitMap getMap() {
        return map;
    }

    /**
     * Shows or hides the side panel (orbit, burn, fly it, target, view): a HUD variant that shows the
     * map's panels itself ({@code ClassicWindow.MAP}) hides it, and the orbit canvas takes the whole
     * screen under the variant's panels.
     */
    public void setOwnPanel(boolean shown) {
        if (panel.isVisible() == shown) return;
        panel.setVisible(shown);
        com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cell = getCell(panel);
        if (shown) cell.width(PANEL_WIDTH).pad(44f, 0f, 10f, 10f);
        else cell.width(0f).pad(0f);
        invalidate();
    }

    /** Whether the side panel shows ({@link #setOwnPanel}). */
    public boolean hasOwnPanel() {
        return panel.isVisible();
    }

    /**
     * Draws the map with {@code painter}, a HUD variant's look, from the orbits to the title; null goes
     * back to the classic look. Where everything goes, and the mouse, stay the map's.
     */
    public void setPainter(MapPainter painter) {
        map.setPainter(painter);
        this.painter = map.getPainter();
        traffic.setPainter(this.painter);
    }

    public MapPainter getPainter() {
        return painter;
    }

    /**
     * Keeps the canvas clear of a HUD variant's panels over it, UI units in from each edge of the
     * screen: the orbits fit, centre and zoom in what is left, and the scale bar and title sit there.
     * All 0 by default; the HUD sets them back when the variant changes.
     */
    public void setCanvasInsets(float left, float top, float right, float bottom) {
        getCell(map).pad(top, left, bottom, right);
        invalidate();
    }

    public Focus getFocus() {
        return focus;
    }

    /** Meters per pixel the map was drawn at last frame. */
    public double metersPerPixel() {
        double s = map.getDrawScale();
        return s > 0 ? 1.0 / s : metersPerPixel;
    }

    /** Back to the whole orbit around the body. */
    public void fit() {
        metersPerPixel = 0;
        panX = panY = 0;
        focus = Focus.BODY;
    }

    /** Out far enough to take in all the traffic around the body, centred on it. */
    public void fitTraffic() {
        if (!showsTraffic()) {
            fit();
            return;
        }
        float half = Math.min(map.getWidth(), map.getHeight()) / 2f - 40f;
        if (half <= 0) return;
        metersPerPixel = MathUtils.clamp(traffic.fitRadius() / half, MIN_SCALE, MAX_SCALE);
        panX = panY = 0;
        focus = Focus.BODY;
    }

    /** Centres on the body, the ship, the navigation target and where the path meets a moon in turn. */
    public void nextFocus() {
        Focus[] all = Focus.values();
        for (int i = 1; i <= all.length; i++) {
            Focus f = all[(focus.ordinal() + i) % all.length];
            if (focusPoint(f, null)) {
                setFocus(f);
                return;
            }
        }
    }

    public void setFocus(Focus f) {
        if (metersPerPixel == 0) metersPerPixel = metersPerPixel();
        focus = f;
        panX = panY = 0;
    }

    /**
     * Centres the view on ({@code x}, {@code y}), meters from the map's body as the path's marks are,
     * at the zoom it has: a variant's timeline shows where an event happens this way.
     */
    public void centreOn(double x, double y) {
        if (metersPerPixel == 0) metersPerPixel = metersPerPixel();
        focus = Focus.BODY;
        setCenter(x, y);
    }

    /** What the view centres on now, as its Centre button says it: the body's name, "ship", "target" or "encounter". */
    public String focusName() {
        switch (focus) {
            case SHIP: return "ship";
            case TARGET: return "target";
            case ENCOUNTER: return "encounter";
            default: return map.getPath().bodyName.isEmpty() ? "body" : map.getPath().bodyName;
        }
    }

    /** Picks the contact the provider knows by {@code id}, as a click on it does: it becomes the navigation target. */
    public void pick(String id) {
        if (pickListener != null && id != null && !id.isEmpty()) pickListener.accept(id);
    }

    /** Whether a click on a contact picks it, as {@link #pick} does. */
    public boolean picks() {
        return pickListener != null;
    }

    /**
     * The point {@code f} centres on, meters in the map's frame, into {@code out} (may be null to only
     * ask); false when there is none this frame: no target, or no moon on the path.
     */
    boolean focusPoint(Focus f, double[] out) {
        OrbitPathProvider.OrbitPath p = map.getPath();
        double x = 0, y = 0;
        boolean found;
        switch (f) {
            case SHIP:
                found = map.hasPath();
                x = p.shipX;
                y = p.shipY;
                break;
            case TARGET:
                found = false;
                for (OrbitPathProvider.MapMarker m : p.markers) {
                    if (!m.target) continue;
                    found = true;
                    x = m.x;
                    y = m.y;
                }
                break;
            case ENCOUNTER:
                found = false;
                for (OrbitPathProvider.PathMark m : p.marks) {
                    if (m.kind != OrbitPathProvider.MarkKind.GHOST || found) continue;
                    found = true;
                    x = m.x;
                    y = m.y;
                }
                break;
            default:
                found = true;
        }
        if (found && out != null) {
            out[0] = x;
            out[1] = y;
        }
        return found;
    }

    /** Zooms by {@code factor} (above 1 zooms in) keeping the point under (stageX, stageY) still. */
    public void zoomAt(float stageX, float stageY, float factor) {
        double mpp = metersPerPixel();
        if (!(mpp > 0)) return;
        double next = MathUtils.clamp(mpp / factor, MIN_SCALE, MAX_SCALE);
        double wx = worldX(stageX), wy = worldY(stageY);
        double[] c = viewCenter();
        // The point under the pointer stays there: the middle moves toward it as the scale shrinks
        double cx = wx - (wx - c[0]) * next / mpp, cy = wy - (wy - c[1]) * next / mpp;
        metersPerPixel = next;
        setCenter(cx, cy);
    }

    /** The view's middle now, meters in the map's frame. */
    private double[] viewCenter() {
        double[] f = new double[2];
        if (!focusPoint(focus, f)) f[0] = f[1] = 0;
        if (metersPerPixel == 0) return new double[]{worldX(mapMidX()), worldY(mapMidY())};
        return new double[]{f[0] + panX, f[1] + panY};
    }

    private void setCenter(double x, double y) {
        double[] f = new double[2];
        if (!focusPoint(focus, f)) f[0] = f[1] = 0;
        panX = x - f[0];
        panY = y - f[1];
    }

    private float mapMidX() {
        return map.getX() + getX() + map.getWidth() / 2f;
    }

    private float mapMidY() {
        return map.getY() + getY() + map.getHeight() / 2f;
    }

    /** The map-frame point drawn at stage x, last frame (the map draws in stage coordinates). */
    double worldX(float stageX) {
        return (stageX - map.screenX(0)) / map.getDrawScale();
    }

    double worldY(float stageY) {
        return (stageY - map.screenY(0)) / map.getDrawScale();
    }

    // ------------------------------------------------------------------ mouse

    private boolean press(float x, float y, int button) {
        if (!map.hasPath()) return false;
        downX = dragX = x;
        downY = dragY = y;
        double[] c = viewCenter();
        startCenterX = c[0];
        startCenterY = c[1];
        if (button != Input.Buttons.LEFT) {
            drag = Drag.PAN;
            return true;
        }
        int h = handleAt(x, y);
        if (h >= 0 && planner != null && planner.selectedBurn(burn)) {
            drag = Drag.HANDLE;
            dragHandle = h;
            startPro = burn[0];
            startRad = burn[1];
            return true;
        }
        int node = nodeAt(x, y);
        if (node >= 0 && planner != null) {
            planner.selectBurn(node);
            drag = Drag.NODE;
            refresh();
            return true;
        }
        drag = Drag.PRESS;
        return true;
    }

    @SuppressWarnings("fallthrough") // a press that moves far enough becomes a pan
    private void dragTo(float x, float y) {
        dragX = x;
        dragY = y;
        boolean moved = Math.hypot(x - downX, y - downY) >= DRAG_START;
        switch (drag) {
            case PRESS:
                if (moved) drag = Drag.PAN;
                else break;
                // falls through to pan
            case PAN:
                if (metersPerPixel == 0) metersPerPixel = metersPerPixel();
                double mpp = metersPerPixel();
                setCenter(startCenterX - (x - downX) * mpp, startCenterY - (y - downY) * mpp);
                break;
            case HANDLE: {
                float[] at = nodeScreen(map.getPath().selectedNode);
                float[] dir = handleDir(dragHandle);
                if (at == null || dir == null) break;
                double out = (x - at[0]) * dir[0] + (y - at[1]) * dir[1] - HANDLE_DISTANCE;
                double dv = dragDeltaV(out, fine());
                double sign = dragHandle % 2 == 0 ? 1 : -1;
                if (dragHandle < 2) planner.setSelectedBurn(round(startPro + sign * dv), startRad);
                else planner.setSelectedBurn(startPro, round(startRad + sign * dv));
                refresh();
                break;
            }
            case NODE:
                if (moved) planner.moveSelectedNear(worldX(x), worldY(y));
                refresh();
                break;
            default:
        }
    }

    private void release(float x, float y) {
        // A contact under the click is picked rather than a burn planned on the path beneath it
        String picked = drag == Drag.PRESS && pickListener != null && showsTraffic() ? traffic.pick(x, y) : null;
        if (picked != null) {
            pickListener.accept(picked);
            refresh();
        } else if (drag == Drag.PRESS && planner != null) {
            planner.addBurnNear(worldX(x), worldY(y), PATH_PICK * metersPerPixel());
            refresh();
        }
        drag = Drag.NONE;
        dragHandle = -1;
    }

    /**
     * Delta-v for a handle dragged {@code pixels} out past where it rests (negative: back toward the
     * burn), m/s: {@link #DRAG_DV} at {@link #DRAG_UNIT} pixels, growing with the square, a tenth of
     * that when {@code fine}.
     */
    static double dragDeltaV(double pixels, boolean fine) {
        double dv = Math.signum(pixels) * DRAG_DV * (pixels / DRAG_UNIT) * (pixels / DRAG_UNIT);
        return fine ? dv / 10 : dv;
    }

    private static double round(double dv) {
        return Math.round(dv * 10) / 10.0;
    }

    private static boolean fine() {
        return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    }

    /** The burn drawn within {@link #NODE_PICK} of (x, y), or -1. */
    int nodeAt(float x, float y) {
        FloatArray nodes = map.getPath().nodes;
        int best = -1;
        double nearest = NODE_PICK;
        for (int i = 0; i * 2 + 1 < nodes.size; i++) {
            float[] at = nodeScreen(i);
            double d = Math.hypot(at[0] - x, at[1] - y);
            if (d < nearest) {
                nearest = d;
                best = i;
            }
        }
        return best;
    }

    /** The selected burn's handle within {@link #HANDLE_RADIUS} of (x, y): 0 prograde, 1 retrograde, 2 radial out, 3 radial in; or -1. */
    int handleAt(float x, float y) {
        float[] at = nodeScreen(map.getPath().selectedNode);
        if (at == null) return -1;
        for (int h = 0; h < 4; h++) {
            float[] d = handleDir(h);
            if (d != null && Math.hypot(at[0] + d[0] * HANDLE_DISTANCE - x, at[1] + d[1] * HANDLE_DISTANCE - y) < HANDLE_RADIUS) return h;
        }
        return -1;
    }

    /** Where burn {@code i} was drawn, stage coordinates, or null. */
    private float[] nodeScreen(int i) {
        FloatArray nodes = map.getPath().nodes;
        if (i < 0 || i * 2 + 1 >= nodes.size) return null;
        return new float[]{map.pathScreenX(nodes.get(i * 2)), map.pathScreenY(nodes.get(i * 2 + 1))};
    }

    /** Handle {@code h}'s direction from the selected burn, a unit vector on screen, or null. */
    private float[] handleDir(int h) {
        FloatArray axes = map.getPath().nodeAxes;
        int i = map.getPath().selectedNode;
        if (i < 0 || i * 4 + 3 >= axes.size) return null;
        float sign = h % 2 == 0 ? 1f : -1f;
        int at = i * 4 + (h < 2 ? 0 : 2);
        return new float[]{sign * axes.get(at), sign * axes.get(at + 1)};
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!isVisible()) return;
        traffic.act(delta);
        // The traffic layer marks the stations and the target; the orbit map only one off the edge
        map.setShowStations(!showsTraffic());
        // Follow the focus as it moves (the ship along its orbit), keeping any panning
        if (metersPerPixel > 0) {
            double[] c = viewCenter();
            map.setView(metersPerPixel, c[0], c[1]);
        } else {
            map.setView(0, 0, 0);
        }
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_INTERVAL) refresh();
    }

    /** Reads the planner's text into the panel now. */
    public void refresh() {
        sinceRefresh = 0;
        boolean selected = planner != null && planner.selectedBurn(burn);
        orbitInfo.setText(planner == null ? "No orbit data" : planner.orbitText());
        String b = planner == null ? "" : planner.burnText();
        burnInfo.setText(b.isEmpty() ? "No burn selected" : b);
        planInfo.setText(planner == null ? "" : planner.planText());
        burnButtons.setVisible(selected);
        proposalButtons.setVisible(planner != null && planner.hasProposal());
        planButton.setDisabled(planner == null || !planner.hasTarget());
        focusButton.setText("Centre: " + focusName());
        hint.setText(selected
                ? "Drag a handle to add delta-v (Shift: finer); drag the burn along its orbit to move it. Click the path to plan another."
                : "Click the path to plan a burn there, or a contact to make it the navigation target. Drag or right-drag to pan; the wheel zooms.");
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor().cpy();
        painter.backdrop(batch, getX(), getY(), getWidth(), getHeight(), parentAlpha);
        batch.setColor(old);
        super.draw(batch, parentAlpha);
        // The canvas: the screen less the side panel, or less the variant's panels' insets
        float left = getX() + map.getX(), bottom = getY() + map.getY(), top = bottom + map.getHeight();
        if (!map.hasPath()) {
            painter.noOrbit(batch, left + map.getWidth() / 2f, bottom + map.getHeight() / 2f, parentAlpha);
            return;
        }
        boolean showTraffic = showsTraffic();
        if (showTraffic) {
            // Over the orbits, inside the map's part of the screen
            batch.flush();
            if (map.clipBegin()) {
                traffic.draw(batch, parentAlpha, map, left, bottom, map.getWidth(), map.getHeight());
                batch.flush();
                map.clipEnd();
            }
        }
        handles(batch, parentAlpha);
        float barY = scaleBar(batch, parentAlpha);
        if (showTraffic) traffic.drawRegions(batch, parentAlpha, left + 20f, barY + painter.lineHeight() * traffic.regionLines());
        painter.title(batch, map.getPath().bodyName, showTraffic ? traffic.counts() : "", left, top, parentAlpha);
        painter.instructions(batch, INSTRUCTIONS + (pickListener == null ? "" : "   [click a contact] navigation target"),
                left, top, parentAlpha);
        batch.setColor(old);
    }

    /** The selected burn's four handles, the one being dragged drawn out to the pointer. */
    private void handles(Batch batch, float alpha) {
        float[] at = nodeScreen(map.getPath().selectedNode);
        if (at == null) return;
        for (int h = 0; h < 4; h++) {
            float[] d = handleDir(h);
            if (d == null) continue;
            float reach = HANDLE_DISTANCE;
            if (h == dragHandle) {
                float out = (dragX - at[0]) * d[0] + (dragY - at[1]) * d[1];
                reach = Math.max(12f, out);
            }
            painter.handle(batch, h, at[0], at[1], d[0], d[1], reach, h == dragHandle || h == hoverHandle, alpha);
        }
    }

    /** A bar of a round length in the canvas's bottom left, labelled; returns the top of what it drew, stage y. */
    private float scaleBar(Batch batch, float alpha) {
        double mpp = metersPerPixel();
        float x = getX() + map.getX() + 20f, y = getY() + map.getY() + 20f;
        if (!(mpp > 0)) return y;
        double length = TrafficLayer.niceStep(160 * mpp);
        return painter.scaleBar(batch, x, y, (float) (length / mpp), TrafficLayer.formatDistance(length), alpha);
    }
}
