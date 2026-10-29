package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.systems.HudRenderSystem;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picture-in-picture bubbles over the outside view: a round window onto a craft the main view
 * draws too small to make out, or does not show at all, magnified so its hull fills the bubble.
 * The game asks for the player's ship, the locked target and the navigation target; each gets a
 * bubble only while it needs one. A bubble for something on screen sits over it, or beside it with
 * a line to it; one for something off screen takes the place of the HUD's edge marker for it: at
 * the edge of the view on the line from the middle of the screen towards it, with a pointer that
 * way and how far off it is.
 *
 * <p>Each bubble is drawn by an {@link ExteriorRenderer} of its own (with its own lighting, so the
 * main view's light map and muzzle flashes are not drawn twice) into a frame buffer, which is then
 * laid on the screen as a disc; the window needs no stencil buffer.
 *
 * <p>The HUD's names for the craft under a bubble are hidden ({@link HudRenderSystem.NameCover});
 * the bubble says how many under its own name, "[+] 3 others", and a click there opens the list of
 * them (and "[-] 3 others" closes it again). A click on a name in the list selects that craft.
 */
public final class PictureInPicture implements Disposable, HudRenderSystem.NameCover {
    /** A bubble's radius, UI units. */
    public static final float RADIUS = 56f;
    /** A craft drawn smaller than this, radius in screen pixels, gets a bubble. */
    public static final float MIN_APPARENT_PIXELS = 10f;
    /** How much of the bubble's radius the craft's own reach fills. */
    private static final float FILL = 0.7f;
    /** A bubble at least magnifies this much over the main view. */
    private static final float MIN_MAGNIFICATION = 2f;
    /** From a bubble's edge to the edge of the screen, UI units. */
    private static final float MARGIN = 14f;
    /** Bubbles for things off screen sit this much further in, room for the pointer on the rim. */
    private static final float EDGE_INSET = 12f;
    private static final float GAP = 10f;
    private static final int SEGMENTS = 96;
    /** Room under a bubble for its name, UI units. */
    private static final float LABEL_ROOM = 22f;
    /** How far the pointer on an off-screen craft's bubble stands out from the rim, UI units. */
    private static final float POINTER = 12f;
    /** How far round a blocked place a bubble looks for a clear one: rings half a radius apart, so many places a ring. */
    private static final int SEARCH_RINGS = 14, SEARCH_ANGLES = 16;
    private static final Color BACKGROUND = new Color(0.02f, 0.02f, 0.05f, 1f);

    /** One craft to show: what it is called and the color it is outlined in. */
    private static final class Bubble {
        Entity entity;
        String label;
        final Color color = new Color();
        /** Where the craft is (world meters) and how far it reaches. */
        final Vector2 at = new Vector2();
        float reach;
        /** Where it is on the UI, and the bubble's middle there. */
        final Vector2 anchor = new Vector2();
        final Vector2 center = new Vector2();
        boolean onScreen;
        /** Drawn right over the craft, so with no line to it. */
        boolean centered;
        /** Meters per screen pixel in the bubble. */
        float metersPerPixel;
        float distance;
        /** The bubble's square in window pixels (logical, y up), as drawn. */
        int glX, glY, glSize;
        /** Under the bubble, UI units, as drawn: all its text, and the "[+] 3 others" line. */
        final Rectangle text = new Rectangle(), toggle = new Rectangle();
        /** Where the top of the text under it goes, UI units: below the pointer when that points down. */
        float textTop;
        /** Half the width the bubble and its name take, UI units. */
        float halfWidth;
        /** The names listed under it while the list is open, and where each was drawn. */
        final Array<Entity> rows = new Array<>();
        final Array<Rectangle> rowBoxes = new Array<>();
    }

    private final Supplier<GameWorld> world;
    private final ExteriorRenderer view;
    private final ExteriorLighting lighting;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Array<Bubble> pool = new Array<>();
    private final Array<Bubble> shown = new Array<>();
    private final GlyphLayout layout = new GlyphLayout();
    private final Array<Rectangle> keepClear = new Array<>(), rectPool = new Array<>();
    private final Rectangle box = new Rectangle(), around = new Rectangle();
    private final PolygonSpriteBatch disc = new PolygonSpriteBatch();
    private FrameBuffer buffer;
    private PolygonRegion circle;
    private final Vector2 tmp = new Vector2();
    private final Vector2 tmp2 = new Vector2();
    private boolean enabled = true;
    /** The craft whose HUD names are under each bubble (by the bubble's craft), this frame. */
    private final ObjectMap<Entity, Array<Entity>> covered = new ObjectMap<>();
    private final Array<Array<Entity>> coveredPool = new Array<>();
    /** The bubbles (by their craft) whose list of hidden names is open. */
    private final ObjectSet<Entity> expanded = new ObjectSet<>();
    private Entity player;

    /**
     * Bubbles drawn with {@code view}, an outside view of {@code world}'s craft of their own whose
     * lighting is {@code lighting} (may be null), and with {@code shapes}, {@code batch} and
     * {@code font} for the rims and names.
     */
    public PictureInPicture(Supplier<GameWorld> world, ExteriorRenderer view, ExteriorLighting lighting,
                            ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        this.world = world;
        this.view = view;
        this.lighting = lighting;
        this.shapes = shapes;
        this.batch = batch;
        this.font = font;
        view.setLighting(lighting);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) shown.clear();
    }

    /** Takes down the bubbles drawn last, when the outside view is not showing. */
    public void clear() {
        shown.clear();
    }

    /** Whether the hulls in the bubbles are lit, as the main view's are. */
    public void setLightsOn(boolean on) {
        view.setLightsOn(on);
    }

    /** A flash in the bubbles too, where a warhead went off or a missile was shot down. */
    public void explode(float x, float y) {
        view.explode(x, y);
    }

    /** At most this many bubbles show at once: the player's ship and two targets. */
    public static final int MAX_BUBBLES = 3;

    /**
     * The {@code i}th bubble drawn last frame with its name under it, UI units, into {@code out}; far
     * off screen and empty when there is none. For the HUD to keep its own text clear of.
     */
    public Rectangle area(int i, Rectangle out) {
        if (i >= shown.size) return out.set(-1e6f, -1e6f, 0f, 0f);
        Bubble b = shown.get(i);
        Vector2 c = b.center;
        out.set(c.x - RADIUS, c.y - RADIUS - LABEL_ROOM, 2f * RADIUS, 2f * RADIUS + LABEL_ROOM);
        return b.text.width > 0f ? out.merge(b.text) : out;
    }

    @Override
    public void begin() {
        for (Array<Entity> list : covered.values()) {
            list.clear();
            coveredPool.add(list);
        }
        covered.clear();
    }

    /**
     * True when {@code marked}'s HUD name at {@code text} is under a bubble drawn last frame, or
     * under the text below it; it is then counted in that bubble's "[+] 3 others", unless it has a
     * bubble of its own (and is named there already).
     */
    @Override
    public boolean covers(Entity marked, Rectangle text) {
        for (Bubble b : shown) {
            if (!under(b, text)) continue;
            // A craft with a bubble of its own is named there already
            if (!hasBubble(marked)) {
                Array<Entity> list = covered.get(b.entity);
                if (list == null) covered.put(b.entity, list = coveredPool.size > 0 ? coveredPool.pop() : new Array<>());
                list.add(marked);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean replacesEdgeMarker(Entity marked) {
        for (Bubble b : shown) if (b.entity == marked && !b.onScreen) return true;
        return false;
    }

    private boolean hasBubble(Entity e) {
        for (Bubble b : shown) if (b.entity == e) return true;
        return false;
    }

    /** True when {@code r} reaches into bubble {@code b} or the text under it. */
    private static boolean under(Bubble b, Rectangle r) {
        if (b.text.width > 0f && b.text.overlaps(r)) return true;
        float nx = MathUtils.clamp(b.center.x, r.x, r.x + r.width) - b.center.x;
        float ny = MathUtils.clamp(b.center.y, r.y, r.y + r.height) - b.center.y;
        return nx * nx + ny * ny < RADIUS * RADIUS;
    }

    /**
     * A left click at ({@code x}, {@code y}), UI units, on the text under a bubble drawn last frame:
     * on "[+] 3 others" (or "[-] 3 others") it opens (or closes) the list of names, on a name in the list
     * it gives that craft to {@code select}. False when it is on none of that.
     */
    public boolean click(float x, float y, Consumer<Entity> select) {
        for (Bubble b : shown) {
            if (b.toggle.contains(x, y)) {
                if (!expanded.add(b.entity)) expanded.remove(b.entity);
                return true;
            }
            for (int i = 0; i < b.rows.size; i++) {
                if (b.rowBoxes.get(i).contains(x, y)) {
                    select.accept(b.rows.get(i));
                    return true;
                }
            }
        }
        return false;
    }

    /** How many bubbles were drawn last frame. For tests. */
    public int shownCount() {
        return shown.size;
    }

    /**
     * Draws a bubble for each of {@code player}, {@code target} and {@code navTarget} (any may be
     * null, and one already shown is not shown twice) that the main view, under {@code viewports},
     * draws too small or not at all. {@code delta} moves the flames and flashes on.
     */
    public void draw(ViewportManager viewports, Entity player, Entity target, Entity navTarget, float delta) {
        shown.clear();
        this.player = player;
        if (!enabled) {
            keepClear.clear();
            return;
        }
        float uiW = viewports.getUiWidth(), uiH = viewports.getUiHeight();
        float mpp = viewports.metersPerPixel();
        float scale = viewports.getUiScale();
        float radiusPx = RADIUS * scale;
        collect(player, "", Color.CYAN, viewports, mpp, radiusPx, uiW, uiH, player);
        collect(target, null, null, viewports, mpp, radiusPx, uiW, uiH, player);
        collect(navTarget, null, null, viewports, mpp, radiusPx, uiW, uiH, player);
        if (shown.size == 0) {
            keepClear.clear();
            return;
        }
        place(uiW, uiH, player);

        float originX = viewports.getUiViewport().getScreenX(), originY = viewports.getUiViewport().getScreenY();
        int size = Math.round(2f * radiusPx);
        prepare(HdpiUtils.toBackBufferX(size));
        for (int i = 0; i < shown.size; i++) {
            Bubble b = shown.get(i);
            b.glSize = size;
            b.glX = Math.round(originX + b.center.x * scale - radiusPx);
            b.glY = Math.round(originY + b.center.y * scale - radiusPx);
            // Flames and flashes move on once a frame, not once a bubble
            drawInside(b, i == 0 ? delta : 0f);
            viewports.applyUi();
            disc.setProjectionMatrix(viewports.getUiCamera().combined);
            disc.disableBlending();
            disc.begin();
            disc.draw(circle, b.center.x - RADIUS, b.center.y - RADIUS, 2f * RADIUS, 2f * RADIUS);
            disc.end();
        }
        drawRims(viewports);
    }

    /** The frame buffer each bubble is drawn into, {@code size} back-buffer pixels square, and the disc it is laid on screen as. */
    private void prepare(int size) {
        if (buffer != null && buffer.getWidth() == size) return;
        if (buffer != null) buffer.dispose();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, false);
        if (lighting != null) {
            lighting.setTarget(buffer);
            lighting.setScreenArea(0, 0, size, size);
        }
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        TextureRegion region = new TextureRegion(buffer.getColorBufferTexture());
        // A frame buffer's texture is upside down
        region.flip(false, true);
        float half = size * 0.5f;
        float[] vertices = new float[2 * (SEGMENTS + 1)];
        short[] triangles = new short[3 * SEGMENTS];
        vertices[0] = half;
        vertices[1] = half;
        for (int i = 0; i < SEGMENTS; i++) {
            float a = MathUtils.PI2 * i / SEGMENTS;
            vertices[2 + 2 * i] = half + half * MathUtils.cos(a);
            vertices[3 + 2 * i] = half + half * MathUtils.sin(a);
            triangles[3 * i] = 0;
            triangles[3 * i + 1] = (short) (1 + i);
            triangles[3 * i + 2] = (short) (1 + (i + 1) % SEGMENTS);
        }
        circle = new PolygonRegion(region, vertices, triangles);
    }

    @Override
    public void dispose() {
        if (buffer != null) buffer.dispose();
        buffer = null;
        disc.dispose();
        view.dispose();
    }

    /**
     * The world point under window pixel ({@code glX}, {@code glY}) (logical pixels, y up) when it
     * falls in a bubble drawn last frame, into {@code out}, and that bubble's meters per pixel;
     * -1 when it falls in none.
     */
    public float unproject(float glX, float glY, Vector2 out) {
        for (int i = shown.size - 1; i >= 0; i--) {
            Bubble b = shown.get(i);
            float half = b.glSize * 0.5f;
            float dx = glX - (b.glX + half), dy = glY - (b.glY + half);
            if (dx * dx + dy * dy > half * half) continue;
            out.set(b.at.x + dx * b.metersPerPixel, b.at.y + dy * b.metersPerPixel);
            return b.metersPerPixel;
        }
        return -1f;
    }

    /** {@code e} as a bubble, when it is in the bubble of physics and the main view needs one for it. */
    private void collect(Entity e, String label, Color color, ViewportManager viewports, float mpp, float radiusPx,
                         float uiW, float uiH, Entity player) {
        if (e == null) return;
        for (Bubble b : shown) if (b.entity == e) return;
        GameWorld w = world.get();
        Body body = w.docking.bodyOf(e);
        if (body == null) return;
        Bubble b = shown.size < pool.size ? pool.get(shown.size) : null;
        if (b == null) pool.add(b = new Bubble());
        b.entity = e;
        // Where the HUD marks it: a craft latched to a station shares the station's body
        TransformComponent t = e.getComponent(TransformComponent.class);
        if (t != null) b.at.set(t.x, t.y);
        else PhysicsManager.getPosition(body, b.at);
        b.reach = reach(e, body);
        viewports.worldToUi(b.at.x, b.at.y, b.anchor);
        b.onScreen = b.anchor.x >= 0f && b.anchor.x <= uiW && b.anchor.y >= 0f && b.anchor.y <= uiH;
        if (b.onScreen && b.reach / mpp >= MIN_APPARENT_PIXELS) return;
        b.metersPerPixel = Math.min(b.reach / (FILL * radiusPx), mpp / MIN_MAGNIFICATION);
        HudMarkerComponent marker = e.getComponent(HudMarkerComponent.class);
        b.label = label != null ? label : marker != null && marker.label != null && !marker.label.isEmpty()
                ? marker.label : "Contact";
        b.color.set(color != null ? color : marker != null ? marker.color : Color.WHITE);
        TransformComponent own = player == null || e == player ? null : player.getComponent(TransformComponent.class);
        b.distance = own == null ? -1f : b.at.dst(own.x, own.y);
        b.halfWidth = RADIUS;
        if (!b.label.isEmpty()) {
            layout.setText(font, b.distance < 0f ? b.label : b.label + "  " + distance(b.distance));
            b.halfWidth = Math.max(RADIUS, layout.width * 0.5f + 4f);
        }
        shown.add(b);
    }

    /**
     * How far a craft reaches from its middle, meters: half its deck plan's diagonal, or without one
     * as far as its body's shapes reach.
     */
    private float reach(Entity e, Body body) {
        ShipGridComponent g = e.getComponent(ShipGridComponent.class);
        if (g != null) return 0.5f * (float) Math.hypot(g.width * g.tileSize, g.height * g.tileSize);
        float far = 0f;
        for (Fixture f : body.getFixtureList()) {
            Shape shape = f.getShape();
            if (shape instanceof CircleShape) {
                far = Math.max(far, ((CircleShape) shape).getPosition().len() + shape.getRadius());
            } else if (shape instanceof PolygonShape) {
                PolygonShape poly = (PolygonShape) shape;
                for (int i = 0; i < poly.getVertexCount(); i++) {
                    poly.getVertex(i, tmp);
                    far = Math.max(far, tmp.len());
                }
            }
        }
        if (far > 0f) return Math.max(far * PhysicsManager.METERS_PER_UNIT, 2f);
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        return m != null ? Math.max(m.radius, 5f) : 20f;
    }

    /**
     * Each bubble's middle: right over its craft when that is clear of the HUD's panels and the
     * bubbles before it, and always for the player's ship ({@code player}); else beside it, away from the middle of the screen, or at the edge towards
     * a craft off screen; when that is over a HUD panel or another bubble too, the nearest place
     * round it that is clear.
     */
    private void place(float uiW, float uiH, Entity player) {
        float cx = uiW * 0.5f, cy = uiH * 0.5f;
        for (int i = 0; i < shown.size; i++) {
            Bubble b = shown.get(i);
            Vector2 dir = tmp.set(b.anchor).sub(cx, cy);
            // The player's own bubble always sits over its ship (in the middle); the others make way for it
            b.centered = b.onScreen && (b.entity == player || centeredClear(i, b.anchor, uiW, uiH));
            if (b.centered) {
                b.center.set(b.anchor);
                continue;
            }
            if (b.onScreen) {
                // Beside it, on the side away from the middle; the player's ship (in the middle) below right
                if (dir.len() < 2f * RADIUS) dir.set(1f, -1f);
                dir.nor();
                b.center.set(b.anchor).mulAdd(dir, 1.8f * RADIUS);
            } else {
                // By the edge, on the line from the middle of the screen towards it, so the bubble
                // points the way the craft is; where a panel is in the way, further in along that line
                float inX = cx - RADIUS - MARGIN - EDGE_INSET, inY = cy - RADIUS - MARGIN - EDGE_INSET - LABEL_ROOM - POINTER;
                dir.nor();
                float t = Math.max(0f, Math.min(dir.x == 0f ? Float.MAX_VALUE : inX / Math.abs(dir.x),
                        dir.y == 0f ? Float.MAX_VALUE : inY / Math.abs(dir.y)));
                boolean found = false;
                for (float in = t; in >= 2f * RADIUS && !found; in -= 0.5f * RADIUS) {
                    b.center.set(cx, cy).mulAdd(dir, in);
                    found = clear(b, i, b.center);
                }
                if (found) continue;
                b.center.set(cx, cy).mulAdd(dir, t);
            }
            clamp(b.center, uiW, uiH);
            if (clear(b, i, b.center)) continue;
            // Rings round where it would go, nearest first
            Vector2 at = tmp2;
            search:
            for (int ring = 1; ring <= SEARCH_RINGS; ring++) {
                float step = 0.5f * RADIUS * ring;
                for (int k = 0; k < SEARCH_ANGLES; k++) {
                    float a = MathUtils.PI2 * k / SEARCH_ANGLES;
                    at.set(b.center).add(step * MathUtils.cos(a), step * MathUtils.sin(a));
                    clamp(at, uiW, uiH);
                    if (clear(b, i, at)) {
                        b.center.set(at);
                        break search;
                    }
                }
            }
        }
        keepClear.clear();
    }

    /**
     * True when the {@code i}th bubble can sit right over its craft at {@code at}: whole on the
     * screen, clear of the HUD's panels and of the bubbles before it.
     */
    private boolean centeredClear(int i, Vector2 at, float uiW, float uiH) {
        float edge = RADIUS + MARGIN;
        if (at.x < edge || at.x > uiW - edge || at.y < edge + LABEL_ROOM || at.y > uiH - edge) return false;
        box.set(at.x - RADIUS, at.y - RADIUS - LABEL_ROOM, 2f * RADIUS, 2f * RADIUS + LABEL_ROOM);
        for (Rectangle r : keepClear) if (r.overlaps(box)) return false;
        float apart = 2f * RADIUS + GAP;
        for (int j = 0; j < i; j++) if (shown.get(j).center.dst2(at) < apart * apart) return false;
        return true;
    }

    /** True when bubble {@code b} (the {@code i}th) at {@code at} is clear of the HUD's panels, the bubbles before it and its own craft. */
    private boolean clear(Bubble b, int i, Vector2 at) {
        // The bubble and its name under it
        box.set(at.x - b.halfWidth, at.y - RADIUS - LABEL_ROOM - POINTER, 2f * b.halfWidth, 2f * RADIUS + LABEL_ROOM + POINTER);
        for (Rectangle r : keepClear) if (r.overlaps(box)) return false;
        float apart = 2f * RADIUS + GAP;
        for (int j = 0; j < i; j++) if (shown.get(j).center.dst2(at) < apart * apart) return false;
        // Not over the craft itself, nor the HUD's marker and name under it
        return !b.onScreen || !box.overlaps(around.set(b.anchor.x - 45f, b.anchor.y - 45f, 90f, 60f));
    }

    /**
     * Keeps this frame's bubbles off {@code r}, UI units (a HUD panel); call before
     * {@link #draw} each frame.
     */
    public void keepClear(Rectangle r) {
        Rectangle copy = keepClear.size < rectPool.size ? rectPool.get(keepClear.size) : null;
        if (copy == null) rectPool.add(copy = new Rectangle());
        keepClear.add(copy.set(r));
    }

    private static void clamp(Vector2 c, float uiW, float uiH) {
        float edge = RADIUS + MARGIN;
        c.x = MathUtils.clamp(c.x, edge, Math.max(edge, uiW - edge));
        c.y = MathUtils.clamp(c.y, edge + LABEL_ROOM, Math.max(edge + LABEL_ROOM, uiH - edge));
    }

    /** The craft and what is around it, magnified, into the frame buffer. */
    private void drawInside(Bubble b, float delta) {
        buffer.begin();
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.viewportWidth = b.glSize * b.metersPerPixel;
        camera.viewportHeight = b.glSize * b.metersPerPixel;
        camera.zoom = 1f;
        camera.position.set(b.at.x, b.at.y, 0f);
        camera.update();
        view.drawBodies(camera, delta);
        view.drawFlight(camera, 2.5f * b.metersPerPixel, delta);
        buffer.end();
    }

    /** Each bubble's rim, its line or pointer to its craft, and its name (and how far off it is). */
    private void drawRims(ViewportManager viewports) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float mpu = viewports.metersPerUiUnit();
        shapes.setProjectionMatrix(viewports.getUiCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Bubble b : shown) {
            shapes.setColor(b.color.r, b.color.g, b.color.b, 0.35f);
            ring(b.center.x, b.center.y, RADIUS, 1.25f);
            Vector2 dir = tmp.set(b.anchor).sub(b.center);
            float len = dir.len();
            // Over its craft: nothing to point at
            if (b.centered || len < 1f) continue;
            dir.scl(1f / len);
            if (b.onScreen) {
                // A line from the rim to just short of the craft as the main view draws it
                float near = Math.max(4f, b.reach / mpu + 3f);
                if (len > RADIUS + near) {
                    shapes.setColor(b.color.r, b.color.g, b.color.b, 0.5f);
                    shapes.rectLine(b.center.x + dir.x * RADIUS, b.center.y + dir.y * RADIUS,
                            b.anchor.x - dir.x * near, b.anchor.y - dir.y * near, 1.5f);
                }
            } else {
                // A pointer on the rim, towards the craft
                float tipX = b.center.x + dir.x * (RADIUS + POINTER), tipY = b.center.y + dir.y * (RADIUS + POINTER);
                float baseX = b.center.x + dir.x * (RADIUS + 2f), baseY = b.center.y + dir.y * (RADIUS + 2f);
                shapes.triangle(tipX, tipY, baseX - dir.y * 7f, baseY + dir.x * 7f, baseX + dir.y * 7f, baseY - dir.x * 7f);
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        for (Bubble b : shown) layOut(b);
        // A dark backing for "[+] 3 others" and the open list, which may lie over anything
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 0.75f);
        for (Bubble b : shown) {
            if (b.toggle.width == 0f) continue;
            float left = b.toggle.x, right = b.toggle.x + b.toggle.width, bottom = b.toggle.y;
            for (int i = 0; i < b.rows.size; i++) {
                Rectangle r = b.rowBoxes.get(i);
                left = Math.min(left, r.x - 4f);
                right = Math.max(right, r.x + r.width + 4f);
                bottom = r.y;
            }
            shapes.rect(left, bottom - 2f, right - left, b.toggle.y + b.toggle.height - bottom + 2f);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        for (Bubble b : shown) {
            float y = b.textTop;
            // The player's own ship goes unnamed
            if (!b.label.isEmpty()) {
                y = line(b.distance < 0f ? b.label : b.label + "  " + distance(b.distance), b.color, b.center.x, y);
            }
            Array<Entity> hidden = covered.get(b.entity);
            if (hidden == null || hidden.size == 0) continue;
            y = line(others(hidden.size, expanded.contains(b.entity)), b.color, b.center.x, y);
            for (Entity e : b.rows) {
                HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
                y = line(rowText(e), m != null ? m.color : Color.WHITE, b.center.x, y);
            }
        }
        batch.end();
        font.setColor(Color.WHITE);
    }

    /**
     * Where the text under bubble {@code b} goes, UI units, for the HUD to hide its names under and
     * clicks to find: its name, "[+] 3 others" when it hides any, and those names when the list is open.
     */
    private void layOut(Bubble b) {
        float lineHeight = font.getLineHeight();
        float top = b.center.y - RADIUS, y = top - 6f;
        if (!b.onScreen) {
            float dy = b.anchor.y - b.center.y, len = b.anchor.dst(b.center);
            if (len > 1f && dy / len < -0.5f) y -= POINTER;
        }
        b.textTop = y;
        float left = b.center.x - RADIUS, right = b.center.x + RADIUS;
        if (!b.label.isEmpty()) {
            layout.setText(font, b.distance < 0f ? b.label : b.label + "  " + distance(b.distance));
            left = Math.min(left, b.center.x - layout.width * 0.5f);
            right = Math.max(right, b.center.x + layout.width * 0.5f);
            y -= lineHeight;
        }
        b.toggle.set(-1e6f, -1e6f, 0f, 0f);
        b.rows.clear();
        Array<Entity> hidden = covered.get(b.entity);
        if (hidden != null && hidden.size > 0) {
            boolean open = expanded.contains(b.entity);
            layout.setText(font, others(hidden.size, open));
            b.toggle.set(b.center.x - layout.width * 0.5f - 6f, y - lineHeight, layout.width + 12f, lineHeight);
            left = Math.min(left, b.toggle.x);
            right = Math.max(right, b.toggle.x + b.toggle.width);
            y -= lineHeight;
            if (open) b.rows.addAll(hidden);
        }
        while (b.rowBoxes.size < b.rows.size) b.rowBoxes.add(new Rectangle());
        for (int i = 0; i < b.rows.size; i++) {
            layout.setText(font, rowText(b.rows.get(i)));
            Rectangle r = b.rowBoxes.get(i).set(b.center.x - layout.width * 0.5f, y - lineHeight, layout.width, lineHeight);
            left = Math.min(left, r.x);
            right = Math.max(right, r.x + r.width);
            y -= lineHeight;
        }
        b.text.set(left, y - 2f, right - left, top - y + 2f);
    }

    /** Draws {@code text} centred on {@code x}, its top at {@code y}, and gives where the next line's top goes. */
    private float line(String text, Color color, float x, float y) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, x - layout.width * 0.5f, y);
        return y - font.getLineHeight();
    }

    /** "[+] 3 others", or "[-] 3 others" with the list open. */
    static String others(int count, boolean open) {
        return com.ringsofiron.uihud.widgets.MarkerWidget.othersText(count, open);
    }

    /** A hidden craft's name and how far off it is, as the HUD would have shown it. */
    private String rowText(Entity e) {
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        String name = m != null && m.label != null && !m.label.isEmpty() ? m.label : "Contact";
        TransformComponent t = e.getComponent(TransformComponent.class);
        TransformComponent own = player == null ? null : player.getComponent(TransformComponent.class);
        return t == null || own == null ? name : name + "  " + distance(Vector2.dst(t.x, t.y, own.x, own.y));
    }

    /** "850 m", "12.4 km", "3,100 km". */
    static String distance(float meters) {
        if (meters < 1000f) return Math.round(meters) + " m";
        if (meters < 100_000f) return String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000f);
        return String.format(java.util.Locale.ROOT, "%,d km", Math.round(meters / 1000f));
    }

    /** A smooth ring {@code width} thick, its outer edge {@code radius} from ({@code x}, {@code y}). */
    private void ring(float x, float y, float radius, float width) {
        float inner = radius - width;
        float c0 = 1f, s0 = 0f;
        for (int i = 1; i <= SEGMENTS; i++) {
            float a = MathUtils.PI2 * i / SEGMENTS;
            float c1 = MathUtils.cos(a), s1 = MathUtils.sin(a);
            shapes.triangle(x + c0 * inner, y + s0 * inner, x + c0 * radius, y + s0 * radius, x + c1 * radius, y + s1 * radius);
            shapes.triangle(x + c0 * inner, y + s0 * inner, x + c1 * radius, y + s1 * radius, x + c1 * inner, y + s1 * inner);
            c0 = c1;
            s0 = s1;
        }
    }
}
