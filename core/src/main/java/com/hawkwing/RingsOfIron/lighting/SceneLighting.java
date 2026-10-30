package com.hawkwing.RingsOfIron.lighting;

import box2dLight.ConeLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Lights the scene from the art's emissive layers. Inside, every deck in view gets box2dlights
 * point and cone lights where its systems, conduits and fires glow, with shadows cast by its walls
 * and hull, over an ambient light that drops to emergency darkness when the craft loses power.
 * Inside and out, a glow pass then draws each glowing part additively, so lamps, screens and
 * navigation lights read as light sources whatever the light map does to the art under them.
 *
 * <p>Each frame: {@link #clear}, then {@link #addDeck} or {@link #addHull} for each craft in view
 * at its pose, then {@link #renderLights} over the drawn scene (inside only) and
 * {@link #renderGlow} on top. Art drawn from an emissive layer belongs between the two, after the
 * light map, so the light map does not darken it.
 *
 * <p>The shadows come from a Box2D world of its own, in world meters: one static body per deck,
 * its walls as edges, moved to the craft's pose each frame and rebuilt when a door or wall changes.
 * Crew in view ({@link #addCrew}) are small circles in the same world, so they cast shadows too.
 */
public final class SceneLighting implements Disposable {
    /** Rooms on a craft with power, before the wall lights and systems light them. */
    public static final Color AMBIENT_POWERED = new Color(0.2f, 0.21f, 0.24f, 1f);
    /** Rooms on a craft without: the lights are out. */
    public static final Color AMBIENT_DARK = new Color(0.06f, 0.055f, 0.07f, 1f);
    /** At most this many lights cast rays in one frame, the first added first. */
    public static final int MAX_LIGHTS = 64;
    static final int RAYS = 96;
    /** Shadow edges blur over this many meters. */
    static final float SOFTNESS = 0.25f;
    /**
     * A crew member's shadow is cast by a circle this many times their size. Smaller than the
     * crew member, so the light's soft edge ({@link #SOFTNESS}) still reaches across them and they
     * are not left in their own shadow.
     */
    static final float CREW_SHADOW_SCALE = 0.5f;
    /** A firing RCS nozzle's light: its color, how strong and how far at full throttle, meters, and its spread each side, radians. */
    static final Color THRUSTER = ExteriorLighting.THRUSTER;
    static final float THRUSTER_STRENGTH = 0.9f, THRUSTER_REACH = 3f, THRUSTER_GLOW = 0.35f;
    static final float THRUSTER_CONE = 40f * MathUtils.degreesToRadians;
    /** The glow drawn around a glowing part, as many times its size. */
    static final float GLOW_SCALE = 2.4f;

    private final DeckLightRig deckRig;
    private final HullLightRig hullRig;
    private final LightSpots local = new LightSpots();
    private final LightSpots deckSpots = new LightSpots();
    private final LightSpots glowSpots = new LightSpots();
    /** This frame's flashes, which cast before the decks' own lights so the cap never drops them. */
    private final LightSpots flashes = new LightSpots();

    private World shadows;
    private RayHandler handler;
    private final Array<PointLight> points = new Array<>();
    private final Array<ConeLight> cones = new Array<>();
    private final Map<ShipGridComponent, Deck> decks = new IdentityHashMap<>();
    /** This frame's crew: x, y and radius, world meters. */
    private final FloatArray crew = new FloatArray();
    private final Array<Body> crewBodies = new Array<>();
    private Texture glow;
    /** The screen area the scene is drawn in, {x, y, width, height} pixels, or null for the whole window. */
    private int[] screen;
    private float time;
    private final Color tmp = new Color();

    private static final class Deck {
        final LightBarriers barriers;
        Body body;
        boolean seen;

        Deck(ShipGridComponent grid) {
            barriers = new LightBarriers(grid);
        }
    }

    public SceneLighting(EmissiveLights art) {
        deckRig = new DeckLightRig(art);
        hullRig = new HullLightRig(art);
    }

    /**
     * Draws the light map into this part of the window only, for a scene letterboxed by its
     * viewport ({@code viewport.getScreenX()} and so on). Without it the map covers the window.
     */
    public void setScreenArea(int x, int y, int width, int height) {
        screen = new int[]{x, y, width, height};
        if (handler != null) handler.useCustomViewport(x, y, width, height);
    }

    /** Starts a frame. */
    public void clear(float delta) {
        time += delta;
        deckSpots.clear();
        glowSpots.clear();
        flashes.clear();
        crew.clear();
        for (Deck d : decks.values()) d.seen = false;
    }

    /** The lights of a deck drawn with its grid's local frame at ({@code x}, {@code y}) meters, turned {@code angle} radians. */
    public void addDeck(ShipGridComponent grid, float x, float y, float angle) {
        local.clear();
        deckRig.collect(grid, local);
        place(local, x, y, angle, deckSpots);
        place(local, x, y, angle, glowSpots);
        Deck d = decks.get(grid);
        if (d == null) decks.put(grid, d = new Deck(grid));
        d.seen = true;
        if (shadows == null) return;
        if (d.barriers.update() || d.body == null) build(d);
        d.body.setTransform(x, y, angle);
    }

    /** A crew member at ({@code x}, {@code y}) meters, {@code radius} meters round, who casts a shadow. */
    public void addCrew(float x, float y, float radius) {
        crew.add(x, y, radius * CREW_SHADOW_SCALE);
    }

    /** How many crew were added this frame. For tests. */
    public int crewInView() {
        return crew.size / 3;
    }

    /** The outside lights of a craft whose hull art is {@code chassis}, its body at ({@code x}, {@code y}) meters. */
    public void addHull(String chassis, ShipGridComponent grid, float x, float y, float angle) {
        local.clear();
        hullRig.collect(chassis, grid, local);
        place(local, x, y, angle, glowSpots);
    }

    /**
     * A firing RCS nozzle at ({@code x}, {@code y}) world meters, its jet toward {@code degrees},
     * at {@code throttle} (0 to 1]: a flickering cone of light out along the jet that lights
     * the deck around it (its hull shadows the inside), and its glow, inside and out.
     */
    public void addThruster(float x, float y, float degrees, float throttle) {
        addThruster(x, y, degrees, throttle, true);
    }

    /**
     * {@link #addThruster}, flickering of its own or not: a jet whose {@code throttle} already
     * wavers with its flame ({@code ThrusterFlames}) is kept steady here, so the two agree.
     */
    public void addThruster(float x, float y, float degrees, float throttle, boolean flickers) {
        for (LightSpots to : new LightSpots[]{deckSpots, glowSpots}) {
            LightSpot s = to.add();
            s.x = x;
            s.y = y;
            s.color.set(THRUSTER);
            s.strength = THRUSTER_STRENGTH * throttle;
            s.reach = THRUSTER_REACH * throttle;
            s.glow = THRUSTER_GLOW * throttle;
            s.isCone = true;
            s.direction = degrees * MathUtils.degreesToRadians;
            s.cone = THRUSTER_CONE;
            s.flicker = flickers ? x * 7.31f + y * 3.17f : -1f;
        }
    }

    /**
     * A flash at ({@code x}, {@code y}) world meters, such as a hit on the hull: light of
     * {@code color} at {@code strength} (0 to 1) out to {@code reach} meters, shadowed by the walls,
     * and a glow {@code glow} meters across. Lasts the frame; a fading flash is added each frame.
     */
    public void addFlash(float x, float y, Color color, float strength, float reach, float glow) {
        LightSpot s = flashes.add();
        s.x = x;
        s.y = y;
        s.color.set(color);
        s.strength = strength;
        s.reach = reach;
        LightSpot g = glowSpots.add();
        g.x = x;
        g.y = y;
        g.color.set(color);
        g.color.a = Math.min(1f, strength);
        g.glow = glow;
    }

    /**
     * Draws the light map over what is on screen: ambient for a craft {@code withPower} or not,
     * and the deck lights, shadowed by the walls. {@code camera} must not be rotated. A stencil
     * test the caller has set applies to the map's pass onto the screen, so it can be kept to the
     * decks ({@code DeckRenderer}).
     */
    public void renderLights(OrthographicCamera camera, boolean withPower) {
        setUpLights(withPower);
        handler.setCombinedMatrix(camera);
        handler.updateAndRender();
    }

    /**
     * As {@link #renderLights}, but only into the light map, not over the screen: the caller lays
     * {@link #lightMapTexture} on with {@link #ambient} itself (the deck edge's blend).
     */
    public void prepareLights(OrthographicCamera camera, boolean withPower) {
        setUpLights(withPower);
        handler.setCombinedMatrix(camera);
        handler.update();
        handler.prepareRender();
    }

    /** The light map {@link #prepareLights} drew, the lights only (no ambient). */
    public com.badlogic.gdx.graphics.Texture lightMapTexture() {
        return handler.getLightMapTexture();
    }

    /** The ambient under the light map, for a craft {@code withPower} or not. */
    public static Color ambient(boolean withPower) {
        return withPower ? AMBIENT_POWERED : AMBIENT_DARK;
    }

    private void setUpLights(boolean withPower) {
        ensureHandler();
        for (Iterator<Deck> it = decks.values().iterator(); it.hasNext(); ) {
            Deck d = it.next();
            if (d.seen) continue;
            if (d.body != null) shadows.destroyBody(d.body);
            it.remove();
        }
        placeCrew();
        handler.setAmbientLight(withPower ? AMBIENT_POWERED : AMBIENT_DARK);
        int p = 0, c = 0;
        for (int i = 0; i < flashes.size() + deckSpots.size() && p + c < MAX_LIGHTS; i++) {
            LightSpot s = i < flashes.size() ? flashes.get(i) : deckSpots.get(i - flashes.size());
            if (!s.on || !s.casts()) continue;
            Light l;
            if (s.isCone) {
                ConeLight cone = cone(c++);
                cone.setDirection(s.direction * MathUtils.radiansToDegrees);
                cone.setConeDegree(s.cone * MathUtils.radiansToDegrees);
                l = cone;
            } else {
                l = point(p++);
            }
            l.setActive(true);
            l.setPosition(s.x, s.y);
            l.setDistance(s.reach);
            tmp.set(s.color);
            tmp.a = s.strength * flicker(s);
            l.setColor(tmp);
        }
        for (int i = p; i < points.size; i++) points.get(i).setActive(false);
        for (int i = c; i < cones.size; i++) cones.get(i).setActive(false);
    }

    /**
     * Draws every glowing part that is on additively, at least {@code minSize} meters across so
     * small lamps stay visible zoomed out.
     */
    public void renderGlow(SpriteBatch batch, OrthographicCamera camera, float minSize) {
        renderGlow(batch, camera, minSize, 0);
    }

    /**
     * {@link #renderGlow(SpriteBatch, OrthographicCamera, float)} for the glowing parts added
     * since {@code from} of them were ({@code glowSpots().size()} then): what was added after
     * an earlier glow pass drew the rest.
     */
    public void renderGlow(SpriteBatch batch, OrthographicCamera camera, float minSize, int from) {
        if (glowSpots.size() <= from) return;
        if (glow == null) glow = glowTexture();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (int i = from; i < glowSpots.size(); i++) {
            LightSpot s = glowSpots.get(i);
            if (!s.on) continue;
            float size = Math.max(minSize, s.glow * GLOW_SCALE);
            batch.setColor(s.color.r, s.color.g, s.color.b, 0.85f * s.color.a * flicker(s));
            batch.draw(glow, s.x - size / 2f, s.y - size / 2f, size, size);
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** The spots added this frame that light a deck, world meters. For tests and debugging. */
    public LightSpots deckSpots() {
        return deckSpots;
    }

    /** The spots added this frame that glow, inside and out, world meters. */
    public LightSpots glowSpots() {
        return glowSpots;
    }

    private float flicker(LightSpot s) {
        if (s.flicker < 0f) return 1f;
        float t = time * 9f + s.flicker;
        return 0.78f + 0.12f * MathUtils.sin(t) + 0.1f * MathUtils.sin(t * 2.7f + 1.3f);
    }

    /** Copies {@code from} into {@code to}, moved from the body frame to the world. */
    private static void place(LightSpots from, float x, float y, float angle, LightSpots to) {
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        for (int i = 0; i < from.size(); i++) {
            LightSpot a = from.get(i), b = to.add();
            b.x = x + cos * a.x - sin * a.y;
            b.y = y + sin * a.x + cos * a.y;
            b.reach = a.reach;
            b.glow = a.glow;
            b.color.set(a.color);
            b.strength = a.strength;
            b.isCone = a.isCone;
            b.direction = a.direction + angle;
            b.cone = a.cone;
            b.on = a.on;
            b.flicker = a.flicker;
        }
    }

    private void ensureHandler() {
        if (handler != null) return;
        shadows = new World(new Vector2(0f, 0f), false);
        RayHandler.useDiffuseLight(true);
        handler = new RayHandler(shadows);
        handler.setBlurNum(1);
        handler.setShadows(true);
        if (screen != null) handler.useCustomViewport(screen[0], screen[1], screen[2], screen[3]);
        // Decks added before the handler existed get their walls now; their pose comes next frame
        for (Deck d : decks.values()) {
            d.barriers.update();
            build(d);
        }
    }

    /** Moves a circle onto each crew member added this frame, and puts the spare ones away. */
    private void placeCrew() {
        int n = crew.size / 3;
        for (int i = 0; i < n; i++) {
            float x = crew.get(i * 3), y = crew.get(i * 3 + 1), r = crew.get(i * 3 + 2);
            Body b = i < crewBodies.size ? crewBodies.get(i) : null;
            if (b != null && Math.abs(b.getFixtureList().first().getShape().getRadius() - r) > 1e-4f) {
                shadows.destroyBody(b);
                b = null;
            }
            if (b == null) {
                BodyDef def = new BodyDef();
                def.type = BodyDef.BodyType.StaticBody;
                b = shadows.createBody(def);
                CircleShape circle = new CircleShape();
                circle.setRadius(r);
                b.createFixture(circle, 0f);
                circle.dispose();
                if (i < crewBodies.size) crewBodies.set(i, b);
                else crewBodies.add(b);
            }
            b.setActive(true);
            b.setTransform(x, y, 0f);
        }
        for (int i = n; i < crewBodies.size; i++) crewBodies.get(i).setActive(false);
    }

    private void build(Deck d) {
        Vector2 at = d.body == null ? new Vector2() : new Vector2(d.body.getPosition());
        float angle = d.body == null ? 0f : d.body.getAngle();
        if (d.body != null) shadows.destroyBody(d.body);
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        def.position.set(at);
        def.angle = angle;
        d.body = shadows.createBody(def);
        EdgeShape edge = new EdgeShape();
        FixtureDef fd = new FixtureDef();
        fd.shape = edge;
        FloatArray seg = d.barriers.segments();
        for (int i = 0; i + 3 < seg.size; i += 4) {
            edge.set(seg.get(i), seg.get(i + 1), seg.get(i + 2), seg.get(i + 3));
            d.body.createFixture(fd);
        }
        edge.dispose();
    }

    private PointLight point(int i) {
        while (points.size <= i) {
            PointLight l = new PointLight(handler, RAYS, Color.WHITE, 1f, 0f, 0f);
            l.setSoftnessLength(SOFTNESS);
            points.add(l);
        }
        return points.get(i);
    }

    private ConeLight cone(int i) {
        while (cones.size <= i) {
            ConeLight l = new ConeLight(handler, RAYS, Color.WHITE, 1f, 0f, 0f, 0f, 45f);
            l.setSoftnessLength(SOFTNESS);
            cones.add(l);
        }
        return cones.get(i);
    }

    /** A soft white disc fading to nothing at its edge. */
    private static Texture glowTexture() {
        int n = 64;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                float f = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy));
                pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, f * f));
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    @Override
    public void dispose() {
        if (handler != null) handler.dispose();
        if (shadows != null) shadows.dispose();
        if (glow != null) glow.dispose();
        handler = null;
        shadows = null;
        glow = null;
        crewBodies.clear();
    }
}
