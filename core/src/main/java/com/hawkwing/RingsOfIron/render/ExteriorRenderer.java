package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectSet;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.ExteriorArt;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.lighting.ExteriorLighting;
import com.hawkwing.RingsOfIron.lighting.HullArt;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.function.Supplier;

/**
 * The view from outside, for the game and every demo that shows craft or rounds in flight: each
 * craft with its hull art ({@link ExteriorArt}) or, without it, every body as its outline; and
 * everything in flight, which {@link #drawFlight} draws on its own for a view (a deck) that shows
 * no hulls: fast passes as streaks, rounds, missiles, beams, decoys and explosions. A
 * {@link Style} says what color each outline is, so a demo can pick out what it shows without
 * drawing the hulls itself.
 *
 * <p>With {@link #setLighting}, the hulls are lit ({@link ExteriorLighting}): a dim starlight,
 * brightened by the hulls' own lights, their engines while they burn, muzzle flashes, missile
 * plumes, beams, decoys and explosions. The light map goes over the hull art only; outlines and
 * everything in flight are drawn after it, since they give off their own light (or are markers).
 *
 * <p>Everything is drawn in world meters; {@link #inFrameOf} draws it instead in one body's own
 * frame, for a view that turns with a craft.
 */
public final class ExteriorRenderer implements Disposable {
    /** How outlines are drawn. */
    public interface Style {
        /** The color of {@code fixture}'s outline on {@code body}, owned by {@code owner} (may be null); null skips it. */
        Color outline(Entity owner, Body body, Fixture fixture);
    }

    /** How far behind a fast pass its streak reaches: where it was this many seconds ago. */
    private static final float STREAK_SECONDS = 0.05f;
    private static final Color STREAK = new Color(0.8f, 0.8f, 0.85f, 1f);
    private static final Color STREAK_TAIL = new Color(0.8f, 0.8f, 0.85f, 0f);
    /** A round younger than this when first seen was just fired: a muzzle flash where it left the gun. */
    private static final float JUST_FIRED = 0.1f;
    private static final Color BEAM_LIGHT = new Color(0.6f, 0.8f, 1f, 1f);
    private static final Color DECOY_LIGHT = new Color(1f, 0.95f, 0.85f, 1f);

    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJECTILES = ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<MissileComponent> MISSILES = ComponentMapper.getFor(MissileComponent.class);
    private static final ComponentMapper<KinematicTransientComponent> TRANSIENTS = ComponentMapper.getFor(KinematicTransientComponent.class);
    private static final Family COMBAT_ROUNDS = Family.all(ProjectileComponent.class).get();
    private static final Family DECOYS = Family.all(DecoyComponent.class).get();
    private static final Family ARMED = Family.all(WeaponsComponent.class).get();
    private static final Family FAST_PASSES = Family.all(KinematicTransientComponent.class).get();

    private final Supplier<GameWorld> world;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final ArtLibrary art;
    private final ExteriorArt exteriorArt;
    private final SceneLighting lighting;
    private final HullArt hullArt;
    private final Supplier<Entity> player;
    private ExteriorLighting exteriorLighting;
    private final float[] pointLightBuffer = new float[4 * ExteriorLighting.MAX_LIGHTS];
    private boolean lightsOn = true;
    /**
     * Rounds seen last frame and this one, so each muzzle flashes once. By their projectile, not
     * their entity: a round's entity comes back from the engine's pool as the next one fired.
     */
    private ObjectSet<ProjectileComponent> roundsSeen = new ObjectSet<>(), roundsNow = new ObjectSet<>();
    private Style style;
    private boolean hullArtOn = true;
    private Supplier<Body> frame;

    /** Flashes for warheads and missiles shot down: {x, y, age}. */
    private final Array<float[]> explosions = new Array<>();
    /** Combat entities drawn with art this frame, which the shapes then skip. */
    private final Array<Entity> drawnWithArt = new Array<>();
    private final Array<Body> bodies = new Array<>();
    private final Matrix4 projection = new Matrix4();
    private final Matrix4 toFrame = new Matrix4();
    private final Vector2 tmp = new Vector2();
    private final Vector2 tmp2 = new Vector2();
    private final float[] polygon = new float[16];
    private final ThrusterFlames flames;
    private final ShieldParticles shields = new ShieldParticles();
    /** Craft drawn with their hull art this frame, whose flames are drawn with art too. */
    private final ObjectSet<Entity> withArt = new ObjectSet<>();
    /** This frame's craft drawn as their decks instead (the deck view), with their hulls' bodies. */
    private final com.badlogic.gdx.utils.ObjectMap<Entity, Body> decks = new com.badlogic.gdx.utils.ObjectMap<>();
    /** Every hull this frame whose shield is drawn, for keeping the light under it off it. */
    private final Array<Body> hulls = new Array<>();

    /**
     * Draws the world {@code world} gives (a demo may load a new one) with {@code shapes} and
     * {@code batch} and the art pack {@code art}. {@code lighting} and {@code hullArt} may be null,
     * for no hull lights or glow. {@code player}'s rounds, missiles and beams are drawn in its own
     * colors, and outlines as in the game ({@link #gameStyle}) until set otherwise.
     */
    public ExteriorRenderer(Supplier<GameWorld> world, ShapeRenderer shapes, SpriteBatch batch, ArtLibrary art,
                            SceneLighting lighting, HullArt hullArt, Supplier<Entity> player) {
        this.world = world;
        this.shapes = shapes;
        this.batch = batch;
        this.art = art;
        this.exteriorArt = new ExteriorArt(art);
        this.flames = new ThrusterFlames(exteriorArt);
        this.lighting = lighting;
        this.hullArt = hullArt;
        this.player = player;
        this.style = gameStyle(player);
    }

    public ExteriorRenderer setStyle(Style style) {
        this.style = style;
        return this;
    }

    /** Whether craft with hull art are drawn with it (else every body is drawn as its outline). */
    public ExteriorRenderer setHullArt(boolean on) {
        hullArtOn = on;
        return this;
    }

    /** Lights the hulls with {@code lighting}; null for none (hulls drawn as the art is). */
    public ExteriorRenderer setLighting(ExteriorLighting lighting) {
        exteriorLighting = lighting;
        return this;
    }

    /** Whether the hulls are lit (when there is lighting): off, they are drawn as the art is. */
    public ExteriorRenderer setLightsOn(boolean on) {
        lightsOn = on;
        return this;
    }

    /** The lighting over the hulls, or null. */
    public ExteriorLighting lighting() {
        return exteriorLighting;
    }

    /** Everything drawn in {@code body}'s own frame (the body a demo's view turns with); null for world meters. */
    public ExteriorRenderer inFrameOf(Supplier<Body> body) {
        frame = body;
        return this;
    }

    /** A flash where a warhead went off or a missile was shot down, at ({@code x}, {@code y}) world meters. */
    public void explode(float x, float y) {
        explosions.add(new float[]{x, y, 0f});
        if (exteriorLighting != null) exteriorLighting.flash(x, y, ExteriorLighting.BLAST, 45f, 1f, 0.9f);
    }

    /**
     * The game's outlines: the player cyan, rounds orange, anything with a HUD marker in the
     * marker's color, and everything else grey.
     */
    public static Style gameStyle(Supplier<Entity> player) {
        return (e, body, fixture) -> {
            HudMarkerComponent marker = e == null ? null : MARKERS.get(e);
            if (e != null && e == player.get()) return Color.CYAN;
            if (e != null && PROJECTILES.has(e)) return Color.ORANGE;
            if (marker != null) return marker.color;
            return Color.LIGHT_GRAY;
        };
    }

    /**
     * Every craft and body, then everything in flight, under {@code camera}. {@code px} is the size
     * of a few pixels' worth of meters over four hundred (the game's view width / 400), so what is
     * small stays visible zoomed out; {@code delta} ages the explosions.
     */
    public void draw(OrthographicCamera camera, float px, float delta) {
        drawBodies(camera, delta);
        drawFlight(camera, px, delta);
        // Navigation lights and the glow of running systems, a few pixels across at least
        if (lighting != null) lighting.renderGlow(batch, camera, 2.5f * px);
    }

    /** {@link #drawBodies(OrthographicCamera, float)} a frame's time after the last. */
    public void drawBodies(OrthographicCamera camera) {
        drawBodies(camera, Gdx.graphics.getDeltaTime());
    }

    /**
     * Craft with hull art drawn with it, lit when there is lighting, then the flames of their
     * engines and RCS ({@link ThrusterFlames}) over the light map; every other body as its
     * outline. {@code delta} (seconds since the last frame, 0 while paused) spools the flames.
     */
    public void drawBodies(OrthographicCamera camera, float delta) {
        drawBodies(camera, delta, null);
    }

    /**
     * {@link #drawBodies(OrthographicCamera, float)}, leaving out every body owned by one of
     * {@code without} (null for none): the deck view's craft, drawn as decks instead.
     */
    public void drawBodies(OrthographicCamera camera, float delta, Array<Entity> without) {
        GameWorld w = world.get();
        Matrix4 view = view(camera);
        w.physics.getWorld().getBodies(bodies);
        if (without != null) {
            for (int i = bodies.size - 1; i >= 0; i--) {
                Entity e = owner(bodies.get(i));
                if (e == null || !without.contains(e, true)) continue;
                // Drawn as a deck, but its shield shows round it all the same
                if (w.docking.bodyOf(e) == bodies.get(i)) decks.put(e, bodies.get(i));
                bodies.removeIndex(i);
            }
        }
        boolean lit = exteriorLighting != null && lightsOn;
        if (lit) exteriorLighting.begin();
        flames.begin(delta);
        shields.begin(delta);
        // Every hull's own lights, drawn with art or not: only the hull's own body, since a port's
        // collar carriage and head carry the craft as owner too
        for (Body body : bodies) {
            Entity e = owner(body);
            ShipGridComponent hull = e == null ? null : e.getComponent(ShipGridComponent.class);
            if (hull == null || w.docking.bodyOf(e) != body) continue;
            Vector2 at = PhysicsManager.getPosition(body, tmp2);
            String chassis = hullArt == null ? null : hullArt.chassisOf(e);
            if (lighting != null && chassis != null) lighting.addHull(chassis, hull, at.x, at.y, body.getAngle());
            if (lit && chassis != null) exteriorLighting.addHull(chassis, hull, at.x, at.y, body.getAngle());
            flames.collect(e, body);
            shields.collect(e, body, lit ? exteriorLighting : null);
            hulls.add(body);
        }
        for (com.badlogic.gdx.utils.ObjectMap.Entry<Entity, Body> d : decks) {
            shields.collect(d.key, d.value, null, true);
            hulls.add(d.value);
        }
        flames.end();
        shields.end();
        // Every flame lights the hulls and glows, as strong as it is long
        flames.light(lit ? exteriorLighting : null, lighting);
        if (lit) lightsInFlight();
        // The same lights shade the lit hulls by their normal maps
        int pointLights = lit ? exteriorLighting.pointLights(pointLightBuffer) : 0;
        exteriorArt.setPointLights(pointLightBuffer, pointLights, lit ? exteriorLighting.ambientBrightness() : 1f);
        // The shield flecks under the light map, so it lights them: those beyond the hull's plane under its art
        float mpp = metersPerPixel(camera);
        if (hullArtOn || shields.any()) {
            batch.setProjectionMatrix(view);
            batch.begin();
            shields.drawFlecks(batch, true, mpp);
        }
        if (hullArtOn) {
            for (int i = bodies.size - 1; i >= 0; i--) {
                Body body = bodies.get(i);
                Entity e = owner(body);
                if (e != null && w.docking.bodyOf(e) == body && exteriorArt.draw(batch, e, body)) {
                    withArt.add(e);
                    bodies.removeIndex(i);
                }
            }
        }
        if (hullArtOn || shields.any()) {
            shields.drawFlecks(batch, false, mpp);
            batch.end();
        }
        if (lit) renderLightMap(camera, view);
        // The flames give off their own light: over the light map, not darkened by it
        batch.setProjectionMatrix(view);
        flames.draw(batch, withArt);
        // So does the shield where it packs and where it is struck, over hull art and outline alike
        shields.drawGlow(batch, mpp, () -> maskHulls(view));
        shapes.setProjectionMatrix(view);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Entity e = owner(body);
            for (Fixture fixture : body.getFixtureList()) {
                Color c = style.outline(e, body, fixture);
                if (c == null) continue;
                shapes.setColor(c);
                drawFixture(body, fixture.getShape());
            }
        }
        // A craft without hull art shows its firing RCS as short lines
        flames.drawLines(shapes, withArt);
        shapes.end();
        bodies.clear();
        withArt.clear();
        decks.clear();
        hulls.clear();
    }

    /** Meters one screen pixel spans under {@code camera}. */
    private static float metersPerPixel(OrthographicCamera camera) {
        int width = Gdx.graphics == null ? 0 : Gdx.graphics.getWidth();
        return width > 0 ? camera.viewportWidth * camera.zoom / width : 0f;
    }

    /**
     * The lights of what is in flight this frame: a flash where each new round left its gun,
     * each burning missile's plume, both ends of every beam, and every decoy flare.
     */
    private void lightsInFlight() {
        GameWorld w = world.get();
        ExteriorLighting l = exteriorLighting;
        roundsNow.clear();
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            roundsNow.add(p);
            if (m != null) {
                float cos = MathUtils.cos(m.heading), sin = MathUtils.sin(m.heading), back = m.def.hitRadius * 3f;
                l.nozzle(p.pos.x - cos * back, p.pos.y - sin * back, m.heading + MathUtils.PI, m.throttle,
                        ExteriorLighting.ENGINE, 8f);
            } else if (!roundsSeen.contains(p) && p.age < JUST_FIRED) {
                // Where it left the gun: back along its flight by its age
                float x = p.pos.x - p.vel.x * p.age, y = p.pos.y - p.vel.y * p.age;
                if (p.pointDefense) l.flash(x, y, ExteriorLighting.MUZZLE, 4f, 0.6f, 0.08f);
                else l.flash(x, y, ExteriorLighting.MUZZLE, 9f, 0.9f, 0.15f);
            }
        }
        ObjectSet<ProjectileComponent> swap = roundsSeen;
        roundsSeen = roundsNow;
        roundsNow = swap;
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(ARMED)) {
            for (WeaponMountComponent m : e.getComponent(WeaponsComponent.class).mounts) {
                if (!m.beamOn) continue;
                l.add(m.muzzle.x, m.muzzle.y, BEAM_LIGHT, 5f, 0.6f);
                l.add(m.beamEnd.x, m.beamEnd.y, BEAM_LIGHT, 8f, 0.9f);
            }
        }
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            l.add(d.pos.x, d.pos.y, DECOY_LIGHT, 10f, 0.8f);
        }
    }

    /** The light map over the hull art, in the frame's body when there is one. */
    private void renderLightMap(OrthographicCamera camera, Matrix4 view) {
        Body body = frame == null ? null : frame.get();
        if (body == null) {
            exteriorLighting.render(camera);
            return;
        }
        // Turned with the body: keep every light within the view's corners of it
        Vector2 at = PhysicsManager.getPosition(body, tmp2);
        float half = 0.5f * camera.zoom * (float) Math.hypot(camera.viewportWidth, camera.viewportHeight);
        float cos = MathUtils.cos(body.getAngle()), sin = MathUtils.sin(body.getAngle());
        float cx = camera.position.x, cy = camera.position.y;
        exteriorLighting.render(view, at.x + cos * cx - sin * cy, at.y + sin * cx + cos * cy, half);
    }

    /**
     * Fast passes as streaks along their flight, rounds as streaks along their last tick's flight,
     * missiles as a nose line with a plume while they burn, beams as lines from the gun to what
     * they touch, decoys as small flares, and a fading ring where a warhead went off or a missile
     * was shot down; with art where the art pack has it. Sizes scale with {@code px} so they stay
     * visible.
     */
    public void drawFlight(OrthographicCamera camera, float px, float delta) {
        GameWorld w = world.get();
        if (exteriorLighting != null) exteriorLighting.age(delta);
        Matrix4 view = view(camera);
        Entity me = player.get();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawFastPasses(view, px);
        boolean beamArt = drawCombatArt(view, px);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(view);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            if (drawnWithArt.contains(e, true)) continue;
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            if (m == null) {
                shapes.setColor(me != null && p.owner == me ? Color.YELLOW : Color.ORANGE);
                // A streak at least a few pixels long, behind the round.
                tmp.set(p.pos).sub(p.prevPos);
                float len = tmp.len();
                if (len < 3f * px) tmp.setLength(3f * px);
                shapes.line(p.pos.x - tmp.x, p.pos.y - tmp.y, p.pos.x, p.pos.y);
                continue;
            }
            float size = Math.max(m.def.hitRadius * 3f, 6f * px);
            float cos = (float) Math.cos(m.heading), sin = (float) Math.sin(m.heading);
            shapes.setColor(me != null && m.launcher == me ? Color.CYAN : Color.RED);
            shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x + cos * size, p.pos.y + sin * size);
            if (m.throttle > 0f) {
                shapes.setColor(Color.ORANGE);
                float plume = size * (1f + 2f * m.throttle);
                shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x - cos * plume, p.pos.y - sin * plume);
            }
        }
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(ARMED)) {
            WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
            for (WeaponMountComponent m : weapons.mounts) {
                if (!m.beamOn || beamArt) continue;
                shapes.setColor(me != null && e == me ? Color.SKY : Color.MAGENTA);
                shapes.line(m.muzzle.x, m.muzzle.y, m.beamEnd.x, m.beamEnd.y);
            }
        }
        shapes.setColor(Color.WHITE);
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            if (drawnWithArt.contains(e, true)) continue;
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            shapes.circle(d.pos.x, d.pos.y, Math.max(1f, 3f * px), 8);
        }
        for (int i = explosions.size - 1; i >= 0; i--) {
            float[] x = explosions.get(i);
            x[2] += delta;
            if (x[2] > 1f) {
                explosions.removeIndex(i);
                continue;
            }
            if (art.region(ArtNames.explosion(0f)) != null) continue;
            shapes.setColor(1f, 0.6f, 0.2f, 1f - x[2]);
            shapes.circle(x[0], x[1], Math.max(5f, 20f * px) * (0.3f + x[2]), 20);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Each fast pass (a body too fast for Box2D, swept along its trajectory) as a streak fading
     * back along its flight, as wide as the body and at least a few pixels long, with its head
     * on where it is now. A transient with a kinematic body (a station on rails) is drawn as a
     * body instead.
     */
    private void drawFastPasses(Matrix4 view, float px) {
        shapes.setProjectionMatrix(view);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(FAST_PASSES)) {
            KinematicTransientComponent k = TRANSIENTS.get(e);
            if (k.body != null || !k.hasPrevious) continue;
            float r = Math.max(k.radius, px);
            tmp.set(k.vx, k.vy).scl(STREAK_SECONDS);
            if (tmp.len() < 8f * px) tmp.setLength(8f * px);
            shapes.rectLine(k.x - tmp.x, k.y - tmp.y, k.x, k.y, 2f * r, STREAK_TAIL, STREAK);
            shapes.setColor(STREAK);
            shapes.circle(k.x, k.y, Math.max(r, 1.5f * px), 12);
        }
        shapes.end();
    }

    /**
     * Rounds, missiles, decoys, beams and explosions that have art, in one batch; the rest are
     * left to the shapes. Each is drawn at least a few pixels long, as the shapes are. True when
     * the beams were drawn.
     */
    private boolean drawCombatArt(Matrix4 view, float px) {
        GameWorld w = world.get();
        drawnWithArt.clear();
        batch.setProjectionMatrix(view);
        batch.begin();
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            boolean drawn;
            if (m != null) {
                drawn = exteriorArt.projectile(batch, ArtNames.projectile(
                        m.def.art, m.def.id, true), p.pos.x, p.pos.y, m.heading, Math.max(m.def.hitRadius * 6f, 12f * px));
            } else {
                drawn = p.def != null && exteriorArt.projectile(batch, ArtNames.projectile(
                        p.def.art, p.def.id, false), p.pos.x, p.pos.y, MathUtils.atan2(p.vel.y, p.vel.x), 10f * px);
            }
            if (drawn) drawnWithArt.add(e);
        }
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            if (exteriorArt.decoy(batch, d.pos.x, d.pos.y, d.vel.x, d.vel.y, 8f * px)) drawnWithArt.add(e);
        }
        boolean beams = art.region(ArtNames.BEAM) != null;
        if (beams) {
            // Beams glow: add their light to what is under them
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (Entity e : w.engine.getEcsEngine().getEntitiesFor(ARMED)) {
                for (WeaponMountComponent m : e.getComponent(WeaponsComponent.class).mounts) {
                    if (m.beamOn) exteriorArt.beam(batch, m.muzzle.x, m.muzzle.y, m.beamEnd.x, m.beamEnd.y, Math.max(0.25f, 3f * px));
                }
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        for (float[] x : explosions) {
            exteriorArt.explosion(batch, x[0], x[1], 2f * Math.max(5f, 20f * px) * (0.3f + x[2]), x[2]);
        }
        batch.end();
        return beams;
    }

    @Override
    public void dispose() {
        shields.dispose();
    }

    /** Marks every hull drawn this frame with 1 in the stencil buffer, the rest 0, drawing nothing. */
    private void maskHulls(Matrix4 view) {
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glClearStencil(0);
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glColorMask(false, false, false, false);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        shapes.setProjectionMatrix(view);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Body body : hulls) {
            Entity craft = owner(body);
            if (craft != null && decks.containsKey(craft)) {
                // Drawn as its deck plan, whose outline is its tiles, not its hull art's
                maskDeck(body, craft.getComponent(ShipGridComponent.class));
                continue;
            }
            for (Fixture fixture : body.getFixtureList()) {
                if (!(fixture.getShape() instanceof PolygonShape)) continue;
                int n = PolygonSkin.outline(body, (PolygonShape) fixture.getShape(), polygon);
                // Box2D's polygons are convex: a fan from the first corner fills one
                for (int k = 1; k + 1 < n; k++) {
                    shapes.triangle(polygon[0], polygon[1], polygon[2 * k], polygon[2 * k + 1], polygon[2 * k + 2], polygon[2 * k + 3]);
                }
            }
        }
        shapes.end();
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
    }

    /** {@code grid}'s tiles that are deck (not void, nor an engine's casing), filled in world meters at {@code body}'s pose. */
    private void maskDeck(Body body, ShipGridComponent grid) {
        if (grid == null) return;
        Vector2 at = PhysicsManager.getPosition(body, tmp2);
        float c = MathUtils.cos(body.getAngle()), sn = MathUtils.sin(body.getAngle()), t = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                // An engine's casing is drawn as its nozzle alone, with space round it
                if (grid.getTile(x, y).isVoid() || com.ringsofiron.interiorgrid.EngineLayout.isCasing(grid, x, y)) continue;
                float lx = grid.gridOriginLocal.x + x * t, ly = grid.gridOriginLocal.y + y * t;
                float x0 = at.x + c * lx - sn * ly, y0 = at.y + sn * lx + c * ly;
                float x1 = x0 + c * t, y1 = y0 + sn * t;
                float x3 = x0 - sn * t, y3 = y0 + c * t;
                float x2 = x1 - sn * t, y2 = y1 + c * t;
                shapes.triangle(x0, y0, x1, y1, x2, y2);
                shapes.triangle(x0, y0, x2, y2, x3, y3);
            }
        }
    }

    private void drawFixture(Body body, Shape shape) {
        if (shape instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            shapes.circle(c.x, c.y, Math.max(0.5f, shape.getRadius() * PhysicsManager.METERS_PER_UNIT), 16);
        } else if (shape instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) shape;
            int n = PolygonSkin.outline(body, poly, polygon);
            shapes.polygon(polygon, 0, n * 2);
        }
    }

    /** The camera's projection, moved into the frame's body when there is one. */
    private Matrix4 view(OrthographicCamera camera) {
        Body body = frame == null ? null : frame.get();
        if (body == null) return projection.set(camera.combined);
        Vector2 at = PhysicsManager.getPosition(body, tmp2);
        toFrame.idt().rotate(0f, 0f, 1f, -body.getAngle() * MathUtils.radiansToDegrees).translate(-at.x, -at.y, 0f);
        return projection.set(camera.combined).mul(toFrame);
    }

    private static Entity owner(Body body) {
        Object o = body.getUserData();
        return o instanceof Entity ? (Entity) o : null;
    }
}
