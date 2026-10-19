package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.ScreenEdge;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.widgets.LeadReticle;
import com.ringsofiron.uihud.widgets.MarkerWidget;

import java.util.Locale;

/**
 * Projects every {@link HudMarkerComponent} entity from world meters to the UI stage each frame
 * and moves its {@link MarkerWidget} there. Also places the lead reticle from the
 * {@link TargetingProvider}, if one is set.
 *
 * Run it after physics has synced transforms and after the world camera has been positioned
 * for the frame, so markers do not lag a frame behind.
 */
public class HudRenderSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 1000;
    /** Distance from the screen border at which off-screen arrows sit, pixels. */
    public static final float EDGE_MARGIN = 28f;
    private static final float MIN_BRACKET = 10f;

    private static final Family MARKED = Family.all(HudMarkerComponent.class, TransformComponent.class).get();
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class, TransformComponent.class).get();
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);

    private final ViewportManager viewports;
    private final Group layer;
    private final Skin skin;
    private final LeadReticle reticle;
    private final ObjectMap<Entity, MarkerWidget> widgets = new ObjectMap<>();
    private final ObjectMap<Entity, MarkerWidget> seen = new ObjectMap<>();
    /** Where the off-screen markers' text went this frame, so the next one can keep clear. */
    private final com.badlogic.gdx.utils.Array<Rectangle> edgeText = new com.badlogic.gdx.utils.Array<>();
    private final Vector2 screen = new Vector2();
    private final Vector2 lead = new Vector2();
    private ImmutableArray<Entity> marked;
    private ImmutableArray<Entity> players;
    private TargetingProvider targeting;

    /** @param layer the stage group markers are added to (normally full-screen, behind panels). */
    public HudRenderSystem(ViewportManager viewports, Group layer, Skin skin) {
        super(DEFAULT_PRIORITY);
        this.viewports = viewports;
        this.layer = layer;
        this.skin = skin;
        reticle = new LeadReticle(skin.getRegion("white"));
        reticle.setVisible(false);
        layer.addActor(reticle);
    }

    public void setTargeting(TargetingProvider targeting) {
        this.targeting = targeting;
    }

    public TargetingProvider getTargeting() {
        return targeting;
    }

    @Override
    public void addedToEngine(Engine engine) {
        marked = engine.getEntitiesFor(MARKED);
        players = engine.getEntitiesFor(PLAYER);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        for (MarkerWidget w : widgets.values()) w.remove();
        widgets.clear();
        marked = null;
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        float w = viewports.getUiWidth();
        float h = viewports.getUiHeight();
        float pixelsPerMeter = 1f / Math.max(1e-6f, viewports.metersPerPixel());
        TransformComponent player = players.size() > 0 ? TRANSFORMS.get(players.first()) : null;
        Entity locked = targeting != null ? targeting.getLockedTarget() : null;

        seen.clear();
        edgeText.clear();
        for (Entity e : marked) {
            HudMarkerComponent m = MARKERS.get(e);
            TransformComponent t = TRANSFORMS.get(e);
            MarkerWidget widget = widgets.get(e);
            if (widget == null) {
                widget = new MarkerWidget(skin);
                widgets.put(e, widget);
                layer.addActor(widget);
            }
            seen.put(e, widget);

            viewports.worldToUi(t.x, t.y, screen);
            boolean onScreen = ScreenEdge.isOnScreen(screen.x, screen.y, w, h);
            if (!onScreen && !m.showOffscreen) {
                widget.setVisible(false);
                continue;
            }
            float angle = 0f;
            if (!onScreen) angle = ScreenEdge.clampToEdge(screen, w, h, EDGE_MARGIN);
            widget.setVisible(true);
            widget.setPosition(screen.x, screen.y);

            String range = player != null ? formatRange(Vector2.dst(player.x, player.y, t.x, t.y)) : "";
            float half = Math.max(MIN_BRACKET, m.radius * pixelsPerMeter * 1.2f);
            widget.set(m.label, range, m.color, half, !onScreen, angle, e == locked);
            if (!onScreen) unstack(widget, screen.y < h / 2f ? 1f : -1f);
        }

        // Drop widgets whose entity lost its marker or left the engine.
        for (ObjectMap.Entries<Entity, MarkerWidget> it = widgets.entries(); it.hasNext(); ) {
            ObjectMap.Entry<Entity, MarkerWidget> entry = it.next();
            if (!seen.containsKey(entry.key)) {
                entry.value.remove();
                it.remove();
            }
        }

        updateReticle(locked);
    }

    /**
     * Two contacts the same way off screen put their names on top of each other at the edge: moves
     * this one's text inward ({@code dir} up or down) until it clears the ones already placed.
     */
    private void unstack(MarkerWidget widget, float dir) {
        Rectangle box = widget.textBounds(new Rectangle());
        for (int tries = 0; tries < 8; tries++) {
            Rectangle hit = null;
            for (Rectangle r : edgeText) if (r.overlaps(box)) hit = r;
            if (hit == null) break;
            float dy = dir > 0 ? hit.y + hit.height + 2f - box.y : hit.y - 2f - (box.y + box.height);
            widget.nudgeText(0f, dy);
            box.y += dy;
        }
        edgeText.add(box);
    }

    private void updateReticle(Entity locked) {
        TransformComponent t = locked != null ? TRANSFORMS.get(locked) : null;
        if (t == null || !targeting.getLeadPoint(lead)) {
            reticle.setVisible(false);
            return;
        }
        viewports.worldToUi(lead.x, lead.y, screen);
        reticle.setPosition(screen.x, screen.y);
        viewports.worldToUi(t.x, t.y, screen);
        reticle.setTargetPoint(screen.x, screen.y);
        reticle.setVisible(true);
        reticle.toFront();
    }

    /** Number of live marker widgets (for tests and debugging). */
    public int markerCount() {
        return widgets.size;
    }

    public static String formatRange(float meters) {
        if (meters < 1000f) return String.format(Locale.ROOT, "%d m", MathUtils.round(meters));
        if (meters < 100_000f) return String.format(Locale.ROOT, "%.1f km", meters / 1000f);
        return String.format(Locale.ROOT, "%,d km", MathUtils.round(meters / 1000f));
    }
}
