package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewModeController;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.data.HudData;

/** What a {@link HudVariant} is given: where to draw, the data, and the classic HUD it sits on. */
public final class HudVariantHost {
    private final UiHud hud;
    private final Group layer;

    public HudVariantHost(UiHud hud, Group layer) {
        this.hud = hud;
        this.layer = layer;
    }

    /**
     * The variant's own layer on the HUD stage, filling it, above the classic panels and the world
     * markers and below the windows and the map. Touchable for its children only.
     */
    public Group layer() {
        return layer;
    }

    /** The common data layer, refreshed each frame before {@link HudVariant#update}. */
    public HudData data() {
        return hud.getData();
    }

    public Stage stage() {
        return hud.getStage();
    }

    /** The classic skin (fonts at the current UI scale, the panel drawables). A variant may build its own. */
    public Skin skin() {
        return hud.getSkin();
    }

    /** The world and UI cameras: {@code worldToUi} places a marker over something in the world. */
    public ViewportManager viewports() {
        return hud.getViewports();
    }

    /** The outside or deck view, and its listeners. */
    public ViewModeController viewMode() {
        return hud.getViewMode();
    }

    /** UI units across and up: the stage's size at the current UI scale. */
    public float width() {
        return hud.getViewports().getUiWidth();
    }

    public float height() {
        return hud.getViewports().getUiHeight();
    }

    /** The UI scale: UI units are this many pixels. */
    public float uiScale() {
        return hud.getViewports().getUiScale();
    }

    /** The classic HUD, for its windows and the map (open them, ask if one is open). */
    public UiHud hud() {
        return hud;
    }
}
