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
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.ExteriorArt;
import com.hawkwing.RingsOfIron.game.GameWorld;
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
 * <p>Everything is drawn in world meters; {@link #inFrameOf} draws it instead in one body's own
 * frame, for a view that turns with a craft.
 */
public final class ExteriorRenderer {
    /** How outlines are drawn. */
    public interface Style {
        /** The color of {@code fixture}'s outline on {@code body}, owned by {@code owner} (may be null); null skips it. */
        Color outline(Entity owner, Body body, Fixture fixture);
    }

    /** How far behind a fast pass its streak reaches: where it was this many seconds ago. */
    private static final float STREAK_SECONDS = 0.05f;
    private static final Color STREAK = new Color(0.8f, 0.8f, 0.85f, 1f);
    private static final Color STREAK_TAIL = new Color(0.8f, 0.8f, 0.85f, 0f);

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

    /** Everything drawn in {@code body}'s own frame (the body a demo's view turns with); null for world meters. */
    public ExteriorRenderer inFrameOf(Supplier<Body> body) {
        frame = body;
        return this;
    }

    /** A flash where a warhead went off or a missile was shot down, at ({@code x}, {@code y}) world meters. */
    public void explode(float x, float y) {
        explosions.add(new float[]{x, y, 0f});
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
        drawBodies(camera);
        drawFlight(camera, px, delta);
        // Navigation lights and the glow of running systems, a few pixels across at least
        if (lighting != null) lighting.renderGlow(batch, camera, 2.5f * px);
    }

    /** Craft with hull art drawn with it; every other body as its outline. */
    public void drawBodies(OrthographicCamera camera) {
        GameWorld w = world.get();
        Matrix4 view = view(camera);
        w.physics.getWorld().getBodies(bodies);
        if (hullArtOn) {
            batch.setProjectionMatrix(view);
            batch.begin();
            for (int i = bodies.size - 1; i >= 0; i--) {
                Body body = bodies.get(i);
                Entity e = owner(body);
                if (e != null && w.docking.bodyOf(e) == body && exteriorArt.draw(batch, e, body)) bodies.removeIndex(i);
            }
            batch.end();
        }
        shapes.setProjectionMatrix(view);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Entity e = owner(body);
            ShipGridComponent hull = e == null ? null : e.getComponent(ShipGridComponent.class);
            // Only the hull's own body: a port's collar carriage and head carry the craft as owner too
            if (lighting != null && hullArt != null && hull != null && w.docking.bodyOf(e) == body) {
                Vector2 at = PhysicsManager.getPosition(body, tmp2);
                lighting.addHull(hullArt.chassisOf(e), hull, at.x, at.y, body.getAngle());
            }
            for (Fixture fixture : body.getFixtureList()) {
                Color c = style.outline(e, body, fixture);
                if (c == null) continue;
                shapes.setColor(c);
                drawFixture(body, fixture.getShape());
            }
        }
        shapes.end();
        bodies.clear();
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
