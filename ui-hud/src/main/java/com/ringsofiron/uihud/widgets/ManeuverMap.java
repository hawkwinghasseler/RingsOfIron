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
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;

import java.util.Locale;

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

    private static final Color BACKDROP = new Color(0.01f, 0.02f, 0.04f, 1f);
    private static final Color PROGRADE = new Color(0.75f, 1f, 0.3f, 1f);
    private static final Color RADIAL = new Color(0.4f, 0.8f, 1f, 1f);
    private static final Color SCALE_BAR = new Color(0.55f, 0.65f, 0.75f, 0.8f);

    private final TextureRegion white;
    private final BitmapFont font;
    private final OrbitMap map;
    private final TrafficLayer traffic;
    private java.util.function.Consumer<String> pickListener;
    private final Label orbitInfo, burnInfo, planInfo, hint;
    private final Table burnButtons, proposalButtons;
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
        white = skin.getRegion("white");
        font = skin.getFont("default-font");
        map = new OrbitMap(white);
        map.setFont(font);
        map.setShowMarks(true);
        // Clear of the title at the top, with room for the apsides' labels
        map.setFitMargin(48f);
        traffic = new TrafficLayer(white, font);

        orbitInfo = info(skin);
        burnInfo = info(skin);
        planInfo = info(skin);
        hint = new Label("", skin, "dim");
        hint.setWrap(true);

        Table panel = new Table();
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
        return new float[]{map.screenX(nodes.get(i * 2)), map.screenY(nodes.get(i * 2 + 1))};
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

    private String focusName() {
        switch (focus) {
            case SHIP: return "ship";
            case TARGET: return "target";
            case ENCOUNTER: return "encounter";
            default: return map.getPath().bodyName.isEmpty() ? "body" : map.getPath().bodyName;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor().cpy();
        batch.setColor(BACKDROP.r, BACKDROP.g, BACKDROP.b, BACKDROP.a * parentAlpha);
        batch.draw(white, getX(), getY(), getWidth(), getHeight());
        batch.setColor(old);
        super.draw(batch, parentAlpha);
        if (!map.hasPath()) {
            font.setColor(HudSkin.HUD_DIM);
            font.draw(batch, "No orbit to show", getX() + map.getX() + map.getWidth() / 2f - 50f, getY() + getHeight() / 2f);
            return;
        }
        boolean showTraffic = showsTraffic();
        if (showTraffic) {
            // Over the orbits, inside the map's part of the screen
            batch.flush();
            if (map.clipBegin()) {
                traffic.draw(batch, parentAlpha, map, getX() + map.getX(), getY() + map.getY(), map.getWidth(), map.getHeight());
                batch.flush();
                map.clipEnd();
            }
        }
        handles(batch, parentAlpha);
        float barY = scaleBar(batch, parentAlpha);
        if (showTraffic) traffic.drawRegions(batch, parentAlpha, getX() + map.getX() + 20f, barY + TrafficLayer.LINE * traffic.regionLines());
        String counts = showTraffic ? traffic.counts() : "";
        font.setColor(HudSkin.HUD_CYAN.r, HudSkin.HUD_CYAN.g, HudSkin.HUD_CYAN.b, parentAlpha);
        font.draw(batch, "MAP  " + map.getPath().bodyName.toUpperCase(Locale.ROOT) + (counts.isEmpty() ? "" : "   " + counts),
                getX() + 16f, getY() + getHeight() - 14f);
        font.setColor(HudSkin.HUD_DIM.r, HudSkin.HUD_DIM.g, HudSkin.HUD_DIM.b, parentAlpha);
        font.draw(batch, "[M] close   [wheel] zoom   [drag] pan   [click the path] plan a burn"
                        + (pickListener == null ? "" : "   [click a contact] navigation target"),
                getX() + 16f, getY() + getHeight() - 32f);
        batch.setColor(old);
    }

    /** The selected burn's four handles, and a line out to the one being dragged. */
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
            float hx = at[0] + d[0] * reach, hy = at[1] + d[1] * reach;
            Color c = h < 2 ? PROGRADE : RADIAL;
            float a = alpha * (h == dragHandle || h == hoverHandle ? 1f : 0.75f);
            batch.setColor(c.r, c.g, c.b, 0.35f * a);
            line(batch, at[0] + d[0] * 8f, at[1] + d[1] * 8f, hx, hy, 1.5f);
            batch.setColor(c.r, c.g, c.b, a);
            ring(batch, hx, hy, 7f);
            if (h % 2 == 0) {
                // Prograde and radial out: a dot in the ring; retrograde and radial in: a cross
                batch.draw(white, hx - 2f, hy - 2f, 4f, 4f);
            } else {
                batch.draw(white, hx - 5f, hy - 0.75f, 5f, 0.75f, 10f, 1.5f, 1f, 1f, 45f);
                batch.draw(white, hx - 5f, hy - 0.75f, 5f, 0.75f, 10f, 1.5f, 1f, 1f, -45f);
            }
        }
    }

    /** A bar of a round length in the bottom left, labelled; returns the top of what it drew, stage y. */
    private float scaleBar(Batch batch, float alpha) {
        double mpp = metersPerPixel();
        float x = getX() + map.getX() + 20f, y = getY() + map.getY() + 20f;
        if (!(mpp > 0)) return y;
        double length = TrafficLayer.niceStep(160 * mpp);
        float px = (float) (length / mpp);
        batch.setColor(SCALE_BAR.r, SCALE_BAR.g, SCALE_BAR.b, SCALE_BAR.a * alpha);
        batch.draw(white, x, y, px, 1.5f);
        batch.draw(white, x, y, 1.5f, 6f);
        batch.draw(white, x + px - 1.5f, y, 1.5f, 6f);
        font.setColor(SCALE_BAR.r, SCALE_BAR.g, SCALE_BAR.b, alpha);
        font.draw(batch, TrafficLayer.formatDistance(length), x, y + 22f);
        return y + 44f;
    }

    private void ring(Batch batch, float x, float y, float r) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            line(batch, x + MathUtils.cos(a0) * r, y + MathUtils.sin(a0) * r, x + MathUtils.cos(a1) * r, y + MathUtils.sin(a1) * r, 1.5f);
        }
    }

    private void line(Batch batch, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        batch.draw(white, x0, y0 - width / 2f, 0f, width / 2f, len, width, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }
}
