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
 *   <li>The <b>UI camera</b> sits in a {@link ScreenViewport}: one stage unit is one screen pixel,
 *       origin bottom-left, so HUD text and lines stay crisp at any window size.</li>
 * </ul>
 */
public class ViewportManager {
    public static final float DEFAULT_VIEW_METERS = 300f;
    public static final float MIN_VIEW_METERS = 20f;
    public static final float MAX_VIEW_METERS = 8000f;

    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final ExtendViewport worldViewport;
    private final OrthographicCamera uiCamera = new OrthographicCamera();
    private final ScreenViewport uiViewport = new ScreenViewport(uiCamera);
    private final Vector3 tmp = new Vector3();
    private float viewMeters;
    private int screenWidth = 1, screenHeight = 1;

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
        uiViewport.update(screenWidth, screenHeight, true);
    }

    /** Centers the world camera on a point in bubble meters. */
    public void follow(float x, float y) {
        worldCamera.position.set(x, y, 0f);
        worldCamera.update();
    }

    /** Zooms so the shorter screen axis shows this many meters. */
    public void setViewMeters(float meters) {
        viewMeters = MathUtils.clamp(meters, MIN_VIEW_METERS, MAX_VIEW_METERS);
        worldViewport.setMinWorldWidth(viewMeters);
        worldViewport.setMinWorldHeight(viewMeters);
        worldViewport.update(screenWidth, screenHeight, false);
    }

    /** Multiplies the zoom, e.g. {@code zoom(1.15f)} per mouse-wheel notch. */
    public void zoom(float factor) {
        setViewMeters(viewMeters * factor);
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
        // tmp is now in GL window pixels (y up). The UI viewport covers the whole window 1:1.
        return out.set(tmp.x - uiViewport.getScreenX(), tmp.y - uiViewport.getScreenY());
    }

    /** Converts a UI stage point back to world meters, for clicks on the HUD. */
    public Vector2 uiToWorld(float uiX, float uiY, Vector2 out) {
        float glX = uiX + uiViewport.getScreenX();
        float glY = uiY + uiViewport.getScreenY();
        float ndcX = 2f * (glX - worldViewport.getScreenX()) / worldViewport.getScreenWidth() - 1f;
        float ndcY = 2f * (glY - worldViewport.getScreenY()) / worldViewport.getScreenHeight() - 1f;
        tmp.set(ndcX, ndcY, -1f).prj(worldCamera.invProjectionView);
        return out.set(tmp.x, tmp.y);
    }

    /** Meters represented by one UI pixel at the current zoom. */
    public float metersPerPixel() {
        return worldViewport.getWorldWidth() / Math.max(1, worldViewport.getScreenWidth());
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
