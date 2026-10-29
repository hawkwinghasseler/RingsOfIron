package com.hawkwing.RingsOfIron.lighting;

import box2dLight.ConeLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Light outside, over the view of craft in flight: a dim starlight ambient, brightened where
 * something gives off light. Each frame takes the hulls' own lights (navigation lights, bridge
 * glazing, reactor glow, from {@link HullLightRig}), whatever burns or fires this frame (engines,
 * thrusters, missile plumes, beams, decoys) and the flashes still fading (muzzle flashes,
 * warheads), and lays a box2dlights light map over the hull art drawn under it.
 *
 * <p>No light casts shadows outside: flat hull art has no height for light to fall across, and a
 * box2dlights occluder darkens everything past its edge, its own art included. So every light is
 * x-ray, and the light map only colors and brightens what is near it.
 *
 * <p>Each frame: {@link #begin}, then {@link #addHull}, {@link #add} and {@link #flash} for what
 * is in view, then {@link #render} over the hulls, before anything that gives off its own light
 * (rounds, beams, explosions, the glow pass) is drawn on top; {@link #age} fades the flashes.
 */
public final class ExteriorLighting implements Disposable {
    /** Starlight: how lit a hull is with nothing near it to light it. */
    public static final Color AMBIENT = new Color(0.46f, 0.47f, 0.52f, 1f);
    /** At most this many lights in one frame, the first added first. */
    public static final int MAX_LIGHTS = 96;
    /** At most this many flashes fade at once; the oldest go first. */
    static final int MAX_FLASHES = 48;
    /** X-ray lights cast no shadows, so a few rays draw them round enough. */
    static final int RAYS = 32;

    /** A missile's rocket exhaust: hot orange-white. */
    public static final Color ENGINE = new Color(1f, 0.62f, 0.3f, 1f);
    /** A craft's main drive: the blue-white of its plume art ({@code engines/exhaust_<size>}). */
    public static final Color DRIVE = new Color(0.62f, 0.8f, 1f, 1f);
    /** Thruster jets (RCS): the cold blue-white of their art ({@code engines/rcs_puff}). */
    public static final Color THRUSTER = new Color(0.78f, 0.9f, 1f, 1f);
    /** A gun's muzzle flash. */
    public static final Color MUZZLE = new Color(1f, 0.8f, 0.5f, 1f);
    /** A warhead going off, or a missile shot down. */
    public static final Color BLAST = new Color(1f, 0.55f, 0.2f, 1f);

    private final HullLightRig rig;
    private final LightSpots local = new LightSpots();
    private final LightSpots spots = new LightSpots();
    /** Flashes fading: x, y, reach, strength, age, life, r, g, b. */
    private final Array<float[]> flashes = new Array<>();
    private final Color ambient = new Color(AMBIENT);
    private final Color tmp = new Color();

    private World world;
    private RayHandler handler;
    private final Array<PointLight> points = new Array<>();
    private final Array<ConeLight> cones = new Array<>();
    private int[] screen;
    private FrameBuffer target;

    public ExteriorLighting(EmissiveLights art) {
        rig = new HullLightRig(art);
    }

    /** As {@link SceneLighting#setScreenArea}: the light map covers only this part of the window. */
    public void setScreenArea(int x, int y, int width, int height) {
        screen = new int[]{x, y, width, height};
        if (handler != null) handler.useCustomViewport(x, y, width, height);
    }

    /** How lit a hull is with nothing near it; {@link #AMBIENT} until set. */
    public void setAmbient(Color c) {
        ambient.set(c);
    }

    /** Starts a frame: drops last frame's lights. */
    public void begin() {
        spots.clear();
    }

    /** Ages the flashes by {@code delta} seconds; nothing fades while it is zero (paused). */
    public void age(float delta) {
        for (int i = flashes.size - 1; i >= 0; i--) {
            float[] f = flashes.get(i);
            f[4] += delta;
            if (f[4] >= f[5]) flashes.removeIndex(i);
        }
    }

    /** The lights of a craft whose hull art is {@code chassis}, its body at ({@code x}, {@code y}) meters, turned {@code angle} radians. */
    public void addHull(String chassis, ShipGridComponent grid, float x, float y, float angle) {
        local.clear();
        rig.collect(chassis, grid, local);
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        for (int i = 0; i < local.size(); i++) {
            LightSpot a = local.get(i);
            if (!a.on || !a.casts()) continue;
            LightSpot b = spots.add();
            b.x = x + cos * a.x - sin * a.y;
            b.y = y + sin * a.x + cos * a.y;
            b.reach = a.reach;
            b.glow = a.glow;
            b.color.set(a.color);
            b.strength = a.strength;
            b.isCone = a.isCone;
            b.direction = a.direction + angle;
            b.cone = a.cone;
        }
    }

    /** A light all round ({@code x}, {@code y}) meters this frame, reaching {@code reach} meters at {@code strength} (0-1). */
    public LightSpot add(float x, float y, Color color, float reach, float strength) {
        LightSpot s = spots.add();
        s.x = x;
        s.y = y;
        s.reach = reach;
        s.color.set(color);
        s.strength = strength;
        return s;
    }

    /**
     * A thruster or engine nozzle at ({@code x}, {@code y}) meters firing toward {@code direction}
     * (radians, world) at {@code firing} (0-1): a soft light around the nozzle, pushed a little
     * way down its exhaust, reaching {@code reach} meters at full burn. Nothing when not firing.
     */
    public void nozzle(float x, float y, float direction, float firing, Color color, float reach) {
        if (firing <= 0f) return;
        float f = Math.min(1f, firing);
        float r = reach * (0.5f + 0.5f * f);
        float out = 0.25f * r;
        add(x + MathUtils.cos(direction) * out, y + MathUtils.sin(direction) * out, color, r, 0.35f + 0.55f * f);
    }

    /** A flash at ({@code x}, {@code y}) meters, reaching {@code reach} meters at {@code strength}, fading out over {@code life} seconds. */
    public void flash(float x, float y, Color color, float reach, float strength, float life) {
        if (flashes.size >= MAX_FLASHES) flashes.removeIndex(0);
        flashes.add(new float[]{x, y, reach, strength, 0f, Math.max(1e-3f, life), color.r, color.g, color.b});
    }

    /** The lights added this frame, world meters, and the flashes as they stand. For tests. */
    public LightSpots spots() {
        return spots;
    }

    /**
     * Every light this frame that lights its surroundings, steady and fading, into {@code out}
     * (world meters) as x, y, reach, strength each, as the light map draws them; returns how
     * many, at most {@code out.length / 4}. A cone is taken as a light all round its tip.
     */
    public int pointLights(float[] out) {
        int n = 0, max = out.length / 4;
        for (int i = 0; i < spots.size() && n < max; i++) {
            LightSpot s = spots.get(i);
            n = put(out, n, s.x, s.y, s.reach, s.strength);
        }
        for (int i = 0; i < flashes.size && n < max; i++) {
            float[] f = flashes.get(i);
            float left = 1f - f[4] / f[5];
            n = put(out, n, f[0], f[1], f[2] * (0.6f + 0.4f * left), f[3] * left * left);
        }
        return n;
    }

    private static int put(float[] out, int n, float x, float y, float reach, float strength) {
        out[4 * n] = x;
        out[4 * n + 1] = y;
        out[4 * n + 2] = reach;
        out[4 * n + 3] = MathUtils.clamp(strength, 0f, 1f);
        return n + 1;
    }

    /** How bright the ambient is, 0-1: its color's mean. */
    public float ambientBrightness() {
        return (ambient.r + ambient.g + ambient.b) / 3f;
    }

    /** How many flashes are still fading. For tests. */
    public int flashes() {
        return flashes.size;
    }

    /** Draws the light map over what is drawn under {@code camera}, which must not be rotated. */
    public void render(OrthographicCamera camera) {
        prepare();
        handler.setCombinedMatrix(camera);
        draw();
    }

    /**
     * Draws the light map under {@code view}, a projection that may turn (a view in one body's
     * frame); everything within {@code radius} meters of ({@code x}, {@code y}) is kept.
     */
    public void render(Matrix4 view, float x, float y, float radius) {
        prepare();
        handler.setCombinedMatrix(view, x, y, 2f * radius, 2f * radius);
        draw();
    }

    /**
     * Draws into {@code buffer} rather than the screen, for a view drawn off screen (a
     * picture-in-picture bubble); null for the screen. Set {@link #setScreenArea} to its whole area.
     */
    public void setTarget(FrameBuffer buffer) {
        target = buffer;
    }

    private void draw() {
        if (target == null) {
            handler.updateAndRender();
            return;
        }
        handler.update();
        handler.prepareRender();
        // box2dlights leaves the screen bound once its light map is ready: back to the buffer to lay it over
        target.begin();
        handler.renderOnly();
    }

    private void prepare() {
        ensureHandler();
        handler.setAmbientLight(ambient);
        int p = 0, c = 0;
        for (int i = 0; i < spots.size() && p + c < MAX_LIGHTS; i++) {
            LightSpot s = spots.get(i);
            Light l;
            if (s.isCone) {
                ConeLight cone = cone(c++);
                cone.setDirection(s.direction * MathUtils.radiansToDegrees);
                cone.setConeDegree(s.cone * MathUtils.radiansToDegrees);
                l = cone;
            } else {
                l = point(p++);
            }
            light(l, s.x, s.y, s.reach, s.color, s.strength);
        }
        // Flashes after the steady lights: the newest last, so the oldest stay when there are too many
        for (int i = 0; i < flashes.size && p + c < MAX_LIGHTS; i++) {
            float[] f = flashes.get(i);
            float left = 1f - f[4] / f[5];
            tmp.set(f[6], f[7], f[8], 1f);
            // Bright at once, then fading fast: a flash, not a lamp
            light(point(p++), f[0], f[1], f[2] * (0.6f + 0.4f * left), tmp, f[3] * left * left);
        }
        for (int i = p; i < points.size; i++) points.get(i).setActive(false);
        for (int i = c; i < cones.size; i++) cones.get(i).setActive(false);
    }

    private void light(Light l, float x, float y, float reach, Color color, float strength) {
        l.setActive(true);
        l.setPosition(x, y);
        l.setDistance(Math.max(0.1f, reach));
        tmp.set(color);
        tmp.a = MathUtils.clamp(strength, 0f, 1f);
        l.setColor(tmp);
    }

    private void ensureHandler() {
        if (handler != null) return;
        world = new World(new Vector2(0f, 0f), false);
        RayHandler.useDiffuseLight(true);
        handler = new RayHandler(world);
        handler.setBlurNum(1);
        // The light map with its ambient needs "shadows" on; the lights themselves are x-ray
        handler.setShadows(true);
        if (screen != null) handler.useCustomViewport(screen[0], screen[1], screen[2], screen[3]);
    }

    private PointLight point(int i) {
        while (points.size <= i) {
            PointLight l = new PointLight(handler, RAYS, Color.WHITE, 1f, 0f, 0f);
            l.setXray(true);
            l.setStaticLight(false);
            points.add(l);
        }
        return points.get(i);
    }

    private ConeLight cone(int i) {
        while (cones.size <= i) {
            ConeLight l = new ConeLight(handler, RAYS, Color.WHITE, 1f, 0f, 0f, 0f, 45f);
            l.setXray(true);
            cones.add(l);
        }
        return cones.get(i);
    }

    @Override
    public void dispose() {
        if (handler != null) handler.dispose();
        if (world != null) world.dispose();
        handler = null;
        world = null;
        points.clear();
        cones.clear();
    }
}
