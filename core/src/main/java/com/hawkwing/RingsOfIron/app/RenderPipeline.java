package com.hawkwing.RingsOfIron.app;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.render.FireArcOverlay;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.render.NavOverlay;
import com.hawkwing.RingsOfIron.lighting.EmissiveLights;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.hawkwing.RingsOfIron.lighting.HullArt;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.hawkwing.RingsOfIron.render.DeckEdgeBlend;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.hawkwing.RingsOfIron.render.ExteriorRenderer;
import com.hawkwing.RingsOfIron.render.PictureInPicture;
import com.hawkwing.RingsOfIron.render.SpaceSnow;
import com.hawkwing.RingsOfIron.render.StarField;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.text.HudText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws a frame of the game as an ordered list of passes ({@link #passNames}): the outside or the
 * decks, the developer overlays over the world, the selection ring, the picture-in-picture bubbles,
 * the status column, the HUD, the overlays over it, and last a panel (crash, notice log, controls).
 * Owns the renderers, the lighting and the camera's follow.
 */
public final class RenderPipeline {
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    /** No decks: in the exterior view every craft is seen from outside. */
    private static final Array<Entity> NO_DECKS = new Array<>();

    /** One step of drawing a frame, {@code delta} seconds after the last (0 while paused). */
    interface Pass {
        void draw(float delta);
    }

    private final GameSession session;
    private final GameWorld world;
    private final ViewportManager viewports;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final BitmapFont font;
    /** The art pack's atlas; empty when it was not built, and then everything is drawn in shapes. */
    private final ArtLibrary art;
    private final DeckRenderer deckRenderer;
    private final ExteriorRenderer exterior;
    /** Speed shown against a circular orbit at the player's altitude. */
    private final SpaceSnow snow;
    /** The distant stars behind it all. */
    private final StarField stars = new StarField();
    /** Deck lights and shadows, and the glow of every lamp and screen, from the art's emissive layers. */
    private final SceneLighting lighting;
    /** Light over the hulls outside: starlight, the hulls' own lights, engines, guns and blasts. */
    private final ExteriorLighting exteriorLighting;
    /** How the decks' light gives way to the light outside at the hull in the deck view. */
    private final DeckEdgeBlend deckEdge = new DeckEdgeBlend();
    /** Bubbles magnifying the player's ship and its targets when the outside view shows them too small, and their own light. */
    private final PictureInPicture pip;
    private final ExteriorLighting pipLighting;
    private NavOverlay navOverlay;
    /** The guns' firing arcs, tinted by their chance to hit. */
    private FireArcOverlay fireArcs;
    private boolean lightsOn = true;
    /** The craft whose decks the interior view draws. */
    private final Array<Entity> decks = new Array<>();
    private final List<Entity> deckList = new ArrayList<>();
    private final Vector2 followAt = new Vector2();
    private final Vector2 tmp = new Vector2();
    private final Rectangle panelRect = new Rectangle();

    /** The developer overlays and selection, the status column and the panels: set before the first frame. */
    DevTools dev;
    GameHudText text;
    private final List<String> names = new ArrayList<>();
    private final List<Pass> passes = new ArrayList<>();

    RenderPipeline(GameSession session, ShapeRenderer shapes, SpriteBatch batch, ArtLibrary art, Path assetsDir) {
        this.session = session;
        this.world = session.world;
        this.viewports = session.viewports;
        this.shapes = shapes;
        this.batch = batch;
        this.font = session.hud.getSkin().getFont("default-font");
        this.art = art;
        DeckArt deckArt = new DeckArt(art);
        EmissiveLights emissive = EmissiveLights.load(assetsDir);
        lighting = new SceneLighting(emissive);
        exteriorLighting = new ExteriorLighting(emissive);
        deckRenderer = new DeckRenderer(() -> world, shapes, batch, deckArt, lighting, () -> session.game.avatar);
        HullArt hullArt = new HullArt(session.game.content.defs);
        exterior = new ExteriorRenderer(() -> world, shapes, batch, art, lighting, hullArt, () -> session.game.player)
                .setLighting(exteriorLighting).setEdgeBlend(deckEdge);
        deckRenderer.setEdgeBlend(deckEdge);
        pipLighting = new ExteriorLighting(emissive);
        pip = new PictureInPicture(() -> world,
                new ExteriorRenderer(() -> world, shapes, batch, art, null, hullArt, () -> session.game.player),
                pipLighting, shapes, batch, font);
        snow = new SpaceSnow(() -> world, () -> session.game.player);

        pass("clear", delta -> {
            Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            viewports.applyWorld();
            shapes.setProjectionMatrix(viewports.getWorldCamera().combined);
            lighting.clear(delta);
        });
        pass("world", delta -> {
            if (session.deckView()) drawInterior(delta);
            else drawBodies();
        });
        pass("world overlays", delta -> dev.drawOverlays(delta, true));
        pass("selection", delta -> dev.drawSelection());
        pass("picture in picture", delta -> {
            if (session.hud.getViewMode().getMode() == ViewMode.EXTERIOR) drawPictureInPicture(delta);
            else pip.clear();
        });
        // The status column before the HUD, so an open HUD window (the crew roster, the yard) covers it
        // rather than having the helm's order written across its rows (round 9, real window)
        pass("status", delta -> text.drawStatus());
        pass("hud", delta -> session.gameHud.draw(delta));
        pass("screen overlays", delta -> dev.drawOverlays(delta, false));
        pass("panels", delta -> text.drawPanels());
    }

    private void pass(String name, Pass pass) {
        names.add(name);
        passes.add(pass);
    }

    /** The passes a frame is drawn in, in order. */
    public List<String> passNames() {
        return Collections.unmodifiableList(names);
    }

    /** The planned burns and the docking corridor, and the guns' arcs, over the outside view. */
    void setNavigation(NavOverlay navOverlay, FireArcOverlay fireArcs) {
        this.navOverlay = navOverlay;
        this.fireArcs = fireArcs;
    }

    NavOverlay navOverlay() {
        return navOverlay;
    }

    DeckRenderer deckRenderer() {
        return deckRenderer;
    }

    DeckEdgeBlend deckEdge() {
        return deckEdge;
    }

    SpaceSnow snow() {
        return snow;
    }

    StarField stars() {
        return stars;
    }

    PictureInPicture pip() {
        return pip;
    }

    ShapeRenderer shapes() {
        return shapes;
    }

    SpriteBatch batch() {
        return batch;
    }

    BitmapFont font() {
        return font;
    }

    /** The craft whose decks the deck view drew last frame. */
    List<Entity> decks() {
        deckList.clear();
        for (Entity e : decks) deckList.add(e);
        return deckList;
    }

    /** A missile shot down or a warhead gone off at ({@code x}, {@code y}): its blast in the view and the bubbles. */
    void explode(float x, float y) {
        exterior.explode(x, y);
        pip.explode(x, y);
    }

    /** O: the lighting off or back on, inside and out; true when it is now on. */
    boolean toggleLights() {
        lightsOn = !lightsOn;
        deckRenderer.setLightsOn(lightsOn);
        exterior.setLightsOn(lightsOn);
        pip.setLightsOn(lightsOn);
        return lightsOn;
    }

    /** One frame: the view eased and the camera on the player, then every pass in order. */
    void render(float delta) {
        // Real time, so a change of view eases while paused too
        viewports.update(Math.min(Gdx.graphics.getDeltaTime(), 0.25f));
        follow();
        for (Pass pass : passes) pass.draw(delta);
    }

    /**
     * Centers the camera on the avatar on deck and on the ship outside; while the zoom eases from
     * one view to the other, as far between the two as the zoom has come.
     */
    void follow() {
        TransformComponent pt = TRANSFORMS.get(session.game.player);
        Vector2 walker = avatarWorld(followAt);
        boolean inside = session.deckView();
        float toWalker = inside ? viewports.easeProgress() : 1f - viewports.easeProgress();
        if (walker == null || (pt != null && toWalker <= 0f)) {
            if (pt != null) viewports.follow(pt.x, pt.y);
        } else if (pt == null || toWalker >= 1f) {
            viewports.follow(walker.x, walker.y);
        } else {
            viewports.follow(pt.x + (walker.x - pt.x) * toWalker, pt.y + (walker.y - pt.y) * toWalker);
        }
    }

    /** Where the avatar stands, world meters, or null when it is aboard nothing in the bubble. */
    private Vector2 avatarWorld(Vector2 out) {
        Entity aboard = world.avatar.getAvatarShip();
        GridPositionComponent pos = session.game.avatar == null ? null : session.game.avatar.getComponent(GridPositionComponent.class);
        ShipGridComponent g = aboard == null ? null : aboard.getComponent(ShipGridComponent.class);
        Body body = aboard == null ? null : world.docking.bodyOf(aboard);
        if (pos == null || g == null || body == null) return null;
        Vector2 local = pos.toShipLocal(g, out).scl(PhysicsManager.UNITS_PER_METER);
        Vector2 w = body.getWorldPoint(local);
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    private void drawBodies() {
        float px = viewports.getViewMeters() / 400f;
        drawBackdrop(viewports.getWorldCamera());
        exterior.drawBodies(viewports.getWorldCamera());
        exterior.drawFlight(viewports.getWorldCamera(), px, Gdx.graphics.getDeltaTime());
        // Crew out on EVA, on the hulls and off them
        deckRenderer.drawOutside(NO_DECKS, viewports.getWorldCamera(), Gdx.graphics.getDeltaTime());
        // Every planned burn's direction, and the corridor into a docking port
        navOverlay.draw(shapes, px);
        // The arcs of the guns in hand, or of every gun flying with a target locked
        fireArcs.draw(shapes, px);
        // Navigation lights and the glow of running systems, a few pixels across at least
        lighting.renderGlow(batch, viewports.getWorldCamera(), 2.5f * px);
    }

    /** The distant starfield, then the space snow over it, in either view. */
    private void drawBackdrop(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        stars.draw(shapes, camera, viewports.metersPerPixel());
        snow.draw(shapes, camera, viewports.metersPerPixel());
    }

    /**
     * The deck plan of the craft the avatar is aboard, and of any craft docked to it, each at its
     * own pose, with its smoke and flames, the cargo and everyone aboard; every other craft in view
     * as it looks outside. {@code delta} moves the smoke and flames on; nothing moves while paused.
     */
    private void drawInterior(float delta) {
        Entity aboard = world.avatar.getAvatarShip();
        OrthographicCamera camera = viewports.getWorldCamera();
        // The stars and the snow behind everything; the decks' light map keeps to the decks
        drawBackdrop(camera);
        // Every other craft over the decks' light map, which would darken them to the dim deck
        // ambient: lit as outside, the outside light map kept off the decks, which it would darken
        // in turn. The decks themselves go down over their shields' undersides, under the rest
        Array<Entity> around = DeckRenderer.decksAround(aboard == null ? session.game.player : aboard, decks);
        int[] glowFrom = {0};
        exteriorLighting.age(delta);
        exterior.drawBodies(camera, delta, decks, () -> deckRenderer.footprints(decks, camera), () -> {
            deckRenderer.draw(around, camera, delta);
            glowFrom[0] = lighting.glowSpots().size();
        }, () -> deckRenderer.drawGlow(around, camera, glowFrom[0]));
        // ...and the crew out on EVA from those other craft
        deckRenderer.drawOutside(decks, camera, delta);
        // Their navigation lights and running systems glow as outside; the decks' glowed already
        lighting.renderGlow(batch, camera, 2.5f * viewports.getViewMeters() / 400f, glowFrom[0]);
        deckEdge.drawMask();
    }

    /**
     * The bubbles for the player's ship, the locked target and the navigation target, as they need
     * them, clear of the HUD's panels and the status column.
     */
    private void drawPictureInPicture(float delta) {
        if (world.getCrash() != null) return;
        float uiW = viewports.getUiViewport().getWorldWidth(), uiH = viewports.getUiViewport().getWorldHeight();
        float statusBottom = text.hudText().statusBottom();
        pip.keepClear(panelRect.set(uiW - HudText.STATUS_WIDTH - 10f, statusBottom, HudText.STATUS_WIDTH + 10f, uiH - statusBottom));
        keepClear(session.hud.getFlightPanel().getParent());
        keepClear(session.hud.getMinimapPanel());
        keepClear(session.hud.getOrbitMap().getParent());
        pip.draw(viewports, session.game.player, session.gun.getTarget(), world.nav.target.entity(), delta);
    }

    /** Keeps the bubbles off {@code panel} while it shows. */
    private void keepClear(Actor panel) {
        if (panel == null || !panel.isVisible()) return;
        Vector2 at = panel.localToStageCoordinates(tmp.set(0f, 0f));
        pip.keepClear(panelRect.set(at.x, at.y, panel.getWidth(), panel.getHeight()));
    }

    /** The renderers and their lighting; before the world goes. */
    void disposeRenderers() {
        lighting.dispose();
        exteriorLighting.dispose();
        deckEdge.dispose();
        exterior.dispose();
        pipLighting.dispose();
        pip.dispose();
    }

    /** The shape renderer, batch and art; last. */
    void disposeGraphics() {
        shapes.dispose();
        batch.dispose();
        art.dispose();
    }
}
