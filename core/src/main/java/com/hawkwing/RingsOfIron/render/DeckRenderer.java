package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
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
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.HazardParticles;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.lighting.DeckLightRig;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.ringsofiron.aicrew.CrewBody;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.minimap.TileShade;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The deck view, for the game and every demo that shows a deck: each craft's deck plan at its
 * pose (the art pack's when it is built, else the minimap's shades), its smoke and flames, its
 * cargo, everyone aboard, and with {@link SceneLighting} the light map and the glow of what gives
 * off light. Crew show their state as the game does whatever the style: the down greyed, the dead
 * as a body where they fell, and a wound disc under the hurt. A {@link Style} says how crew are
 * drawn and lets a screen add to the deck and to each crew member, so a demo shows what it needs on
 * top without drawing a deck of its own.
 */
public final class DeckRenderer {
    /** How crew are drawn, and what a screen adds over the deck. */
    public interface Style {
        /** Which crew figure to draw for {@code crew}. */
        ArtNames.Crew figure(Entity crew, CrewMemberComponent c);

        /** The dot's color for {@code crew} when there is no crew art, before it is greyed for the down. */
        Color dot(Entity crew, CrewMemberComponent c);

        /**
         * Drawn over the deck, its smoke and flames, under everyone aboard: in the deck's local
         * meters inside a filled batch already moved to the craft's pose.
         */
        default void overDeck(ShapeRenderer shapes, Entity ship, ShipGridComponent grid) {
        }

        /**
         * Drawn under living {@code crew} at ({@code x}, {@code y}), the deck's local meters, inside
         * the same filled batch, beneath the wound disc. {@code figures} is whether the crew art is
         * drawn over it.
         */
        default void underCrew(ShapeRenderer shapes, Entity crew, CrewMemberComponent c, float x, float y,
                               float tileSize, boolean figures) {
        }
    }

    /** Where a deck is drawn: false when it is not drawn. */
    public interface Pose {
        boolean of(Entity ship, Vector2 at, float[] angle);
    }

    private static final Matrix4 IDENTITY = new Matrix4();
    /** The tint of a down crew member's figure, and what their dot is scaled by. */
    private static final Color GREYED = new Color(0.55f, 0.55f, 0.6f, 1f);
    /** A body's cross until there is art for one. */
    private static final Color BODY = new Color(0.55f, 0.55f, 0.55f, 1f);
    /** How long a bleeding wound's disc takes to blink. */
    private static final float BLINK = 0.6f;

    private final Supplier<GameWorld> world;
    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final DeckArt deckArt;
    private final SceneLighting lighting;
    private Style style;
    private Pose pose;
    private boolean lightsOn = true;

    /** Smoke, wind and flames of each deck on show; a craft's go when it is no longer drawn. */
    private final Map<ShipGridComponent, HazardParticles> hazards = new IdentityHashMap<>();
    private final Map<ShipGridComponent, Boolean> hazardsDrawn = new IdentityHashMap<>();
    private final Matrix4 transform = new Matrix4();
    private final Vector2 at = new Vector2();
    private final Vector2 local = new Vector2();
    private final float[] angle = new float[1];
    private final Color color = new Color();
    private final Color crewColor = new Color();
    /** Time the decks have been drawn for, which blinks the bleeding. */
    private float clock;

    /**
     * Draws the decks of the world {@code world} gives (a demo may load a new one) with {@code shapes}
     * and {@code batch}; {@code lighting} may be null, for no light map or glow. Decks stand at their
     * bodies' poses and crew are drawn as in the game ({@link #gameStyle}) until set otherwise.
     */
    public DeckRenderer(Supplier<GameWorld> world, ShapeRenderer shapes, SpriteBatch batch, DeckArt deckArt,
                        SceneLighting lighting, Supplier<Entity> avatar) {
        this.world = world;
        this.shapes = shapes;
        this.batch = batch;
        this.deckArt = deckArt;
        this.lighting = lighting;
        this.style = gameStyle(world, avatar);
        this.pose = this::bodyPose;
    }

    public DeckRenderer setStyle(Style style) {
        this.style = style;
        return this;
    }

    /** Where each deck is drawn; by default at its body's pose in the bubble. */
    public DeckRenderer setPose(Pose pose) {
        this.pose = pose;
        return this;
    }

    /** Every deck drawn at the origin, unturned: for a view in one craft's own frame. */
    public DeckRenderer inShipFrame() {
        return setPose((ship, out, a) -> {
            out.setZero();
            a[0] = 0f;
            return true;
        });
    }

    /** Whether the light map and glow are drawn (when there is lighting). */
    public void setLightsOn(boolean on) {
        lightsOn = on;
    }

    public boolean lightsOn() {
        return lightsOn;
    }

    /**
     * The craft aboard and every craft docked to it, into {@code out}: what the deck view shows
     * when standing aboard {@code aboard}.
     */
    public static Array<Entity> decksAround(Entity aboard, Array<Entity> out) {
        out.clear();
        ShipGridComponent grid = aboard == null ? null : aboard.getComponent(ShipGridComponent.class);
        if (grid == null) return out;
        out.add(aboard);
        for (GridLink link : grid.links) {
            if (!out.contains(link.otherShip, true)) out.add(link.otherShip);
        }
        return out;
    }

    /**
     * The game's crew: art for the player, the station's people and a ship's; without the art, the
     * avatar white, a known faction's crew in the station color and everyone else gold.
     */
    public static Style gameStyle(Supplier<GameWorld> world, Supplier<Entity> avatar) {
        return new Style() {
            @Override
            public ArtNames.Crew figure(Entity crew, CrewMemberComponent c) {
                return crew == avatar.get() ? ArtNames.Crew.PLAYER
                        : world.get().standing.factions().known(c.faction) ? ArtNames.Crew.STATION : ArtNames.Crew.SHIP;
            }

            @Override
            public Color dot(Entity crew, CrewMemberComponent c) {
                return crew == avatar.get() ? Color.WHITE
                        : world.get().standing.factions().known(c.faction) ? DeckView.STATION_CREW : Color.GOLD;
            }
        };
    }

    /**
     * Draws {@code decks} under {@code camera}, the first being the craft aboard (its power lights
     * the light map). {@code delta} moves the smoke and flames on; 0 holds them.
     */
    public void draw(Array<Entity> decks, OrthographicCamera camera, float delta) {
        clock = (clock + delta) % BLINK;
        if (decks.size == 0) return;
        boolean withArt = deckArt.available();
        boolean lit = lighting != null && lightsOn;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        hazardsDrawn.clear();
        for (Entity ship : decks) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            float x = at.x, y = at.y, radians = angle[0];
            float degrees = radians * MathUtils.radiansToDegrees;
            CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
            if (withArt) {
                batch.setProjectionMatrix(camera.combined);
                batch.setTransformMatrix(transform.idt().translate(x, y, 0f).rotate(0f, 0f, 1f, degrees));
                batch.begin();
                deckArt.draw(batch, g, hold);
                batch.end();
                batch.setTransformMatrix(IDENTITY);
            }
            // The batch's end turned blending off; the walkways drawn below are translucent
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.setProjectionMatrix(camera.combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.identity();
            shapes.translate(x, y, 0f);
            shapes.rotate(0f, 0f, 1f, degrees);
            if (lit) {
                lighting.addDeck(g, x, y, radians);
                float cos = MathUtils.cos(radians), sin = MathUtils.sin(radians);
                for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship) continue;
                    Vector2 l = pos.toShipLocal(g, local);
                    lighting.addCrew(x + cos * l.x - sin * l.y, y + sin * l.x + cos * l.y, CrewBody.RADIUS * g.tileSize);
                }
            }
            float s = g.tileSize;
            if (withArt) {
                if (!deckArt.hasWalkways()) DeckView.walkways(shapes, g, color, 0.6f);
            } else {
                for (int ty = 0; ty < g.height; ty++) {
                    for (int tx = 0; tx < g.width; tx++) {
                        TileShade.deck(g.getTile(tx, ty), color);
                        if (color.a <= 0f) continue;
                        shapes.setColor(color);
                        Vector2 p = g.tileToLocal(tx, ty);
                        shapes.rect(p.x - s / 2f + 0.03f, p.y - s / 2f + 0.03f, s - 0.06f, s - 0.06f);
                    }
                }
                DeckView.walkways(shapes, g, color, 1f);
                DeckView.walls(shapes, g, color);
                DeckView.cargo(shapes, g, hold, color);
            }
            // Smoke and flames over the deck, under everyone aboard; they open their own batches
            shapes.end();
            HazardParticles particles = hazards.get(g);
            if (particles == null) hazards.put(g, particles = new HazardParticles());
            hazardsDrawn.put(g, Boolean.TRUE);
            particles.update(g, delta);
            particles.draw(g, shapes, g.gridOriginLocal.x, g.gridOriginLocal.y, s);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            style.overDeck(shapes, ship, g);
            boolean figures = withArt && deckArt.hasCrew();
            for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                if (pos.ship != ship) continue;
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                Vector2 l = pos.toShipLocal(g, local);
                if (c.state == CrewState.DEAD) {
                    body(shapes, l.x, l.y, s);
                    continue;
                }
                style.underCrew(shapes, e, c, l.x, l.y, s, figures);
                if (c.wound != Wound.NONE || c.state != CrewState.CONSCIOUS) {
                    // The figure has no art of its own for being hurt or down
                    Color w = woundColor(c, clock, crewColor);
                    shapes.setColor(w.r, w.g, w.b, w.a * 0.7f);
                    shapes.circle(l.x, l.y, WOUND_DISC * s, 20);
                }
                if (figures) continue;
                Color dot = crewColor.set(style.dot(e, c));
                if (c.state == CrewState.UNCONSCIOUS) dot.mul(GREYED);
                DeckView.crew(shapes, c, l.x, l.y, s, dot, color);
            }
            shapes.end();
            if (figures) {
                batch.setTransformMatrix(transform);
                batch.begin();
                for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship) continue;
                    CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                    if (c.state == CrewState.DEAD) continue;
                    Vector2 l = pos.toShipLocal(g, local);
                    batch.setColor(c.state == CrewState.UNCONSCIOUS ? GREYED : Color.WHITE);
                    deckArt.crew(batch, e, style.figure(e, c), c.load == null ? null : c.load.task.job.commodity(), l.x, l.y, s);
                }
                batch.setColor(Color.WHITE);
                batch.end();
                batch.setTransformMatrix(IDENTITY);
            }
        }
        hazards.keySet().retainAll(hazardsDrawn.keySet());
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (!lit) return;
        // The light map over the decks, dark when the craft aboard has no power; then the glow on top
        ShipGridComponent aboard = decks.first().getComponent(ShipGridComponent.class);
        lighting.renderLights(camera, aboard != null && DeckLightRig.hasPower(aboard));
        if (withArt) {
            // What gives off light shows lit whatever light falls on it
            batch.setProjectionMatrix(camera.combined);
            for (Entity ship : decks) {
                ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
                if (g == null || !pose.of(ship, at, angle)) continue;
                batch.setTransformMatrix(transform.idt().translate(at.x, at.y, 0f).rotate(0f, 0f, 1f, angle[0] * MathUtils.radiansToDegrees));
                batch.begin();
                deckArt.glow(batch, g);
                batch.end();
            }
            batch.setTransformMatrix(IDENTITY);
        }
        lighting.renderGlow(batch, camera, 0f);
    }

    /** The radius of the wound disc under a crew member, in tiles: a ring round the dot without the art. */
    private static final float WOUND_DISC = 0.336f;

    /**
     * A wound's color, into {@code out}: near white unhurt, yellow light, orange serious, red critical
     * (blinking on {@code clock} while it bleeds), cyan stabilised; dimmer while down.
     */
    private static Color woundColor(CrewMemberComponent c, float clock, Color out) {
        switch (c.wound) {
            case LIGHT: out.set(1f, 0.9f, 0.3f, 1f); break;
            case SERIOUS: out.set(1f, 0.55f, 0.15f, 1f); break;
            case CRITICAL:
                if (c.stabilised) out.set(0.3f, 0.9f, 1f, 1f);
                else out.set(1f, 0.15f, 0.15f, (clock % BLINK) < BLINK / 2f ? 1f : 0.45f);
                break;
            default: out.set(0.85f, 0.9f, 1f, 1f);
        }
        if (c.state == CrewState.UNCONSCIOUS) out.mul(0.75f, 0.75f, 0.75f, 1f);
        return out;
    }

    /** A dead crew member's body at ({@code x}, {@code y}): a grey cross until there is art for one. */
    private static void body(ShapeRenderer shapes, float x, float y, float tileSize) {
        float r = 0.32f * tileSize, width = 0.08f * tileSize;
        shapes.setColor(BODY);
        shapes.rectLine(x - r, y - r, x + r, y + r, width);
        shapes.rectLine(x - r, y + r, x + r, y - r, width);
    }

    private boolean bodyPose(Entity ship, Vector2 out, float[] a) {
        Body body = world.get().docking.bodyOf(ship);
        if (body == null) return false;
        PhysicsManager.getPosition(body, out);
        a[0] = body.getAngle();
        return true;
    }
}
