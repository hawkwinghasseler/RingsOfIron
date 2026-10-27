package com.ringsofiron.uihud;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Owns the two cameras the game draws with.
 *
 * <ul>
 *   <li>The <b>world camera</b> looks at the physics bubble in meters (the same local coordinates
 *       as {@code TransformComponent}). It sits in an {@link ExtendViewport} so the shorter screen
 *       axis always shows at least {@link #getViewMeters()} meters.</li>
 *   <li>The <b>UI camera</b> sits in a {@link ScreenViewport}: one stage unit is {@link #getUiScale()}
 *       screen pixels ({@link UiScale}), origin bottom-left, so the HUD keeps its layout and grows
 *       with the screen.</li>
 * </ul>
 */
public class ViewportManager {
    public static final float DEFAULT_VIEW_METERS = 300f;
    public static final float MIN_VIEW_METERS = 20f;
    /** Far enough out to take in the whole physics bubble (25 km to its edge). */
    public static final float MAX_VIEW_METERS = 60_000f;

    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final ExtendViewport worldViewport;
    private final OrthographicCamera uiCamera = new OrthographicCamera();
    private final ScreenViewport uiViewport = new ScreenViewport(uiCamera);
    private final Vector3 tmp = new Vector3();
    private float viewMeters;
    private final ZoomEase ease = new ZoomEase();
    private int screenWidth = 1, screenHeight = 1;
    private float uiScale = 1f;

    public ViewportManager() {
        this(DEFAULT_VIEW_METERS);
    }

    public ViewportManager(float viewMeters) {
        this.viewMeters = viewMeters;
        worldViewport = new ExtendViewport(viewMeters, viewMeters, worldCamera);
    }

    /** Call from {@code ApplicationListener.resize}. Updates both viewports. */
    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        worldViewport.update(screenWidth, screenHeight, false);
        uiScale = UiScale.effective(screenWidth, screenHeight);
        uiViewport.setUnitsPerPixel(1f / uiScale);
        uiViewport.update(screenWidth, screenHeight, true);
    }

    /** Screen pixels per UI unit, from {@link UiScale} at the last resize. */
    public float getUiScale() {
        return uiScale;
    }

    /** Centers the world camera on a point in bubble meters. */
    public void follow(float x, float y) {
        worldCamera.position.set(x, y, 0f);
        worldCamera.update();
    }

    /** Zooms so the shorter screen axis shows this many meters, at once (ending any eased zoom). */
    public void setViewMeters(float meters) {
        ease.stop();
        apply(meters);
    }

    private void apply(float meters) {
        viewMeters = MathUtils.clamp(meters, MIN_VIEW_METERS, MAX_VIEW_METERS);
        worldViewport.setMinWorldWidth(viewMeters);
        worldViewport.setMinWorldHeight(viewMeters);
        worldViewport.update(screenWidth, screenHeight, false);
    }

    /** Zooms to {@code meters} over {@link ZoomEase#SECONDS}, easing in and out; {@link #update} moves it on. */
    public void easeViewMeters(float meters) {
        ease.start(viewMeters, MathUtils.clamp(meters, MIN_VIEW_METERS, MAX_VIEW_METERS));
    }

    /** Moves an eased zoom on by {@code delta} seconds of real time. */
    public void update(float delta) {
        if (!ease.isEasing()) return;
        ease.update(delta);
        apply(ease.meters());
    }

    /** How far along the eased zoom is, eased in and out: 0 at its start, 1 once settled. */
    public float easeProgress() {
        return ease.progress();
    }

    /** Multiplies the zoom, e.g. {@code zoom(1.15f)} per mouse-wheel notch; mid-ease, where it is easing to as well. */
    public void zoom(float factor) {
        if (ease.isEasing()) ease.scale(factor, MIN_VIEW_METERS, MAX_VIEW_METERS);
        apply(viewMeters * factor);
    }

    /** The view an eased zoom is heading for, or the view itself once it has settled. */
    public float getTargetViewMeters() {
        return ease.isEasing() ? ease.target() : viewMeters;
    }

    public float getViewMeters() {
        return viewMeters;
    }

    /** Applies the world viewport's GL viewport; call before drawing the world. */
    public void applyWorld() {
        worldViewport.apply();
    }

    /** Applies the UI viewport's GL viewport; call before drawing the stage. */
    public void applyUi() {
        uiViewport.apply();
    }

    /**
     * Projects a world point (bubble meters) into UI stage coordinates (pixels, origin bottom-left).
     * Points off screen still project; the result is just outside {@code [0, uiWidth] x [0, uiHeight]}.
     */
    public Vector2 worldToUi(float worldX, float worldY, Vector2 out) {
        tmp.set(worldX, worldY, 0f);
        worldCamera.project(tmp, worldViewport.getScreenX(), worldViewport.getScreenY(),
                worldViewport.getScreenWidth(), worldViewport.getScreenHeight());
        // tmp is now in GL window pixels (y up). The UI viewport covers the whole window, uiScale pixels a unit.
        return out.set((tmp.x - uiViewport.getScreenX()) / uiScale, (tmp.y - uiViewport.getScreenY()) / uiScale);
    }

    /** Converts a UI stage point back to world meters, for clicks on the HUD. */
    public Vector2 uiToWorld(float uiX, float uiY, Vector2 out) {
        float glX = uiX * uiScale + uiViewport.getScreenX();
        float glY = uiY * uiScale + uiViewport.getScreenY();
        float ndcX = 2f * (glX - worldViewport.getScreenX()) / worldViewport.getScreenWidth() - 1f;
        float ndcY = 2f * (glY - worldViewport.getScreenY()) / worldViewport.getScreenHeight() - 1f;
        tmp.set(ndcX, ndcY, -1f).prj(worldCamera.invProjectionView);
        return out.set(tmp.x, tmp.y);
    }

    /** Meters represented by one screen pixel at the current zoom. */
    public float metersPerPixel() {
        return worldViewport.getWorldWidth() / Math.max(1, worldViewport.getScreenWidth());
    }

    /** Meters represented by one UI unit at the current zoom. */
    public float metersPerUiUnit() {
        return metersPerPixel() * uiScale;
    }

    public OrthographicCamera getWorldCamera() {
        return worldCamera;
    }

    public ExtendViewport getWorldViewport() {
        return worldViewport;
    }

    public OrthographicCamera getUiCamera() {
        return uiCamera;
    }

    public ScreenViewport getUiViewport() {
        return uiViewport;
    }

    public float getUiWidth() {
        return uiViewport.getWorldWidth();
    }

    public float getUiHeight() {
        return uiViewport.getWorldHeight();
    }
}
