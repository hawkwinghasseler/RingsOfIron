package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
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
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.Furnishing;
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
 * off light. Crew show their state as the game does whatever the style: standing, down or dead
 * by their art ({@link ArtNames.CrewPose}) with their wound drawn over the figure, a bleeding one
 * blinking; or without that art the down greyed, the dead a cross where they fell and a disc in
 * the wound's color under the hurt. Hits on a hull show where they struck ({@link DeckImpacts}). A {@link Style} says how crew are drawn and lets a screen add to the deck and to each crew member, so a demo shows what it needs on
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
    /** The lamps on an EVA suit: their light, how strong, how far it reaches and the glow of the lamps, meters. */
    private static final Color SUIT_LAMP = new Color(1f, 0.95f, 0.84f, 1f);
    private static final float SUIT_LAMP_STRENGTH = 0.7f, SUIT_LAMP_REACH = 2.5f, SUIT_LAMP_GLOW = 0.25f;
    /** A body's cross without the art for one. */
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
    private boolean heatShown;

    /** Furnishings without art. */
    private static final Color FURNISHING = new Color(0.55f, 0.42f, 0.3f, 1f);
    /** A piece being placed where it fits, and where it doesn't. */
    private static final Color GHOST_FITS = new Color(0.55f, 1f, 0.6f, 0.75f), GHOST_BLOCKED = new Color(1f, 0.4f, 0.35f, 0.75f);
    /** A furnishing being placed, shown on {@link #ghostShip}'s deck; null for none. */
    private Entity ghostShip;
    private Furnishing ghost;
    private boolean ghostFits;
    private final float[] corners = new float[8];

    /** Smoke, wind and flames of each deck on show; a craft's go when it is no longer drawn. */
    private final Map<ShipGridComponent, HazardParticles> hazards = new IdentityHashMap<>();
    private final Map<ShipGridComponent, Boolean> hazardsDrawn = new IdentityHashMap<>();
    private final Matrix4 transform = new Matrix4();
    private final Vector2 at = new Vector2();
    private final Vector2 local = new Vector2();
    private final float[] angle = new float[1];
    private final Color color = new Color();
    private final RcsNozzles nozzles = new RcsNozzles();
    /** Tethers and jetpack puffs of the crew out on EVA. */
    private final Spacewalkers spacewalkers = new Spacewalkers();
    /** Craft with crew out on EVA, drawn from outside this frame. */
    private final Array<Entity> outside = new Array<>();
    /** Every hull hit in the world on show, drawn on its craft's deck. */
    private final DeckImpacts impacts = new DeckImpacts();
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

    /**
     * Shows {@code ghost}, a furnishing being placed, on {@code ship}'s deck: green where it
     * {@code fits}, red where it doesn't. A null ghost shows none.
     */
    public void setGhost(Entity ship, Furnishing ghost, boolean fits) {
        this.ghostShip = ship;
        this.ghost = ghost;
        this.ghostFits = fits;
    }

    /** A furnishing as a filled block at its spot and angle, in the deck's local meters. */
    private void furnishingBlock(ShipGridComponent g, Furnishing f, Color c) {
        float s = g.tileSize, w = f.type.width * s, h = f.type.depth * s;
        float cx = g.gridOriginLocal.x + f.x() * s, cy = g.gridOriginLocal.y + f.y() * s;
        shapes.setColor(c);
        shapes.rect(cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, f.rotation);
    }

    /** The edge of a furnishing's footprint. */
    private void outline(ShipGridComponent g, Furnishing f, Color c) {
        f.corners(0f, corners);
        float s = g.tileSize, ox = g.gridOriginLocal.x, oy = g.gridOriginLocal.y;
        shapes.setColor(c);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            shapes.rectLine(ox + corners[2 * i] * s, oy + corners[2 * i + 1] * s, ox + corners[2 * j] * s, oy + corners[2 * j + 1] * s, 0.03f * s);
        }
    }

    /** Whether the light map and glow are drawn (when there is lighting). */
    public void setLightsOn(boolean on) {
        lightsOn = on;
    }

    public boolean lightsOn() {
        return lightsOn;
    }

    /** Whether the {@link HeatOverlay} overlay is drawn over the decks: off unless a developer asks. */
    public void setHeatShown(boolean shown) {
        heatShown = shown;
    }

    public boolean heatShown() {
        return heatShown;
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
        GameWorld current = world.get();
        impacts.listen(current == null ? null : current.combatEvents.hullHits);
        impacts.update(delta);
        spacewalkers.update(delta);
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
                if (ship == ghostShip && ghost != null) deckArt.furnishing(batch, g, ghost, ghostFits ? GHOST_FITS : GHOST_BLOCKED);
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
                // The firing RCS light the hull outside and glow; the walls keep it out of the rooms
                nozzles.jets(ship, world.get().docking.bodyOf(ship), x, y, radians, lighting::addThruster);
                float cos = MathUtils.cos(radians), sin = MathUtils.sin(radians);
                for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship) continue;
                    Vector2 l = pos.toShipLocal(g, local);
                    float wx = x + cos * l.x - sin * l.y, wy = y + sin * l.x + cos * l.y;
                    lighting.addCrew(wx, wy, CrewBody.RADIUS * g.tileSize);
                    // Out on EVA, their suit's lamps light the hull round them
                    if (EvaSystem.outside(e)) {
                        lighting.addFlash(wx, wy, SUIT_LAMP, SUIT_LAMP_STRENGTH, SUIT_LAMP_REACH * g.tileSize, SUIT_LAMP_GLOW * g.tileSize);
                    }
                }
                // Hits on the hull light the deck for a moment
                impacts.flashes(g, (fx, fy, c, strength, reach, glow) -> {
                    float lx = g.gridOriginLocal.x + fx * g.tileSize, ly = g.gridOriginLocal.y + fy * g.tileSize;
                    lighting.addFlash(x + cos * lx - sin * ly, y + sin * lx + cos * ly, c, strength,
                            reach * g.tileSize, glow * g.tileSize);
                });
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
            // Furnishings with no art to draw them: a plain block at their spot and angle
            for (int ty = 0; ty < g.height; ty++) {
                for (int tx = 0; tx < g.width; tx++) {
                    for (Furnishing f : g.tiles[ty][tx].furnishings) {
                        if (withArt && deckArt.hasArt(f)) continue;
                        float k = 0.4f + 0.6f * f.condition();
                        furnishingBlock(g, f, color.set(FURNISHING.r * k, FURNISHING.g * k, FURNISHING.b * k, 1f));
                    }
                }
            }
            // Smoke and flames over the deck, under everyone aboard; they open their own batches
            shapes.end();
            HazardParticles particles = hazards.get(g);
            if (particles == null) hazards.put(g, particles = new HazardParticles());
            hazardsDrawn.put(g, Boolean.TRUE);
            particles.update(g, delta);
            particles.draw(g, shapes, g.gridOriginLocal.x, g.gridOriginLocal.y, s);
            impacts.drawDebris(g, shapes, g.gridOriginLocal.x, g.gridOriginLocal.y, s);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (heatShown) HeatOverlay.draw(shapes, g, color);
            style.overDeck(shapes, ship, g);
            if (ship == ghostShip && ghost != null) {
                if (!withArt || !deckArt.hasArt(ghost)) furnishingBlock(g, ghost, color.set(ghostFits ? GHOST_FITS : GHOST_BLOCKED));
                outline(g, ghost, ghostFits ? GHOST_FITS : GHOST_BLOCKED);
            }
            shapes.end();
        }
        // Everyone aboard over every deck, so one craft's deck does not cover crew out on the walkway
        // from another, nor what of them reaches past their own craft's collar
        boolean figures = withArt && deckArt.hasCrew();
        for (Entity ship : decks) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            float s = g.tileSize, degrees = angle[0] * MathUtils.radiansToDegrees;
            transform.idt().translate(at.x, at.y, 0f).rotate(0f, 0f, 1f, degrees);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, degrees);
            // Under everyone, the tethers of those out on EVA and the puffs of their jetpacks
            spacewalkers.draw(shapes, world.get(), ship, g, delta);
            for (Entity e : world.get().engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                if (pos.ship != ship) continue;
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                Vector2 l = pos.toShipLocal(g, local);
                boolean posed = figures && deckArt.hasCrew(ArtNames.CrewPose.of(c));
                if (c.state == CrewState.DEAD) {
                    if (!posed) body(shapes, l.x, l.y, s);
                    continue;
                }
                style.underCrew(shapes, e, c, l.x, l.y, s, figures);
                // A part's crate in the arms of one aboard whose figure doesn't hold it: in a suit (the
                // carrying art has none), at work in a pose with no carrying art, or with no art at all
                if (c.crate != null && !EvaSystem.outside(e)
                        && (c.suited || !figures || !deckArt.carries(style.figure(e, c), ArtNames.CrewPose.of(c)))) {
                    spacewalkers.crate(shapes, l.x, l.y, c.crate.commodity, s);
                }
                if (!posed && (c.wound != Wound.NONE || c.state != CrewState.CONSCIOUS)) {
                    // Without the art, the disc shows the wound and that they are down
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
                    ArtNames.CrewPose pose = ArtNames.CrewPose.of(c);
                    boolean posed = deckArt.hasCrew(pose);
                    if (!posed && c.state == CrewState.DEAD) continue;
                    Vector2 l = pos.toShipLocal(g, local);
                    batch.setColor(!posed && c.state == CrewState.UNCONSCIOUS ? GREYED : Color.WHITE);
                    figure(e, c, pose, posed, l.x, l.y, s);
                }
                batch.setColor(Color.WHITE);
                batch.end();
                batch.setTransformMatrix(IDENTITY);
            }
        }
        hazards.keySet().retainAll(hazardsDrawn.keySet());
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (!lit) {
            glowingImpacts(decks, camera);
            progressBars(decks, camera, false);
            return;
        }
        // The light map over the decks, dark when the craft aboard has no power; then the glow on top
        ShipGridComponent aboard = decks.first().getComponent(ShipGridComponent.class);
        lighting.renderLights(camera, aboard != null && DeckLightRig.hasPower(aboard));
        glowingImpacts(decks, camera);
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
        progressBars(decks, camera, false);
    }

    /**
     * Fills where {@code decks} are drawn under {@code camera}: every tile that is not void, a
     * little past its edges so the hull walls on them are inside. For marking the decks in the
     * stencil buffer, so the light map outside ({@link ExteriorRenderer}) keeps off them.
     */
    public void footprints(Array<Entity> decks, OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity ship : decks) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, angle[0] * MathUtils.radiansToDegrees);
            float s = g.tileSize, half = s * (0.5f + FOOTPRINT_MARGIN);
            for (int ty = 0; ty < g.height; ty++) {
                for (int tx = 0; tx < g.width; tx++) {
                    if (g.getTile(tx, ty) == null || g.getTile(tx, ty).isVoid()) continue;
                    Vector2 p = g.tileToLocal(tx, ty);
                    shapes.rect(p.x - half, p.y - half, 2f * half, 2f * half);
                }
            }
        }
        shapes.end();
        shapes.identity();
    }

    /** How far past a tile's edge its footprint reaches, in tiles. */
    private static final float FOOTPRINT_MARGIN = 0.15f;

    /**
     * {@code e}'s figure at ({@code x}, {@code y}), the deck's local meters, in a begun batch: in
     * their suit if they wear one, and out on EVA crawling or floating, turned the way they go.
     */
    private void figure(Entity e, CrewMemberComponent c, ArtNames.CrewPose pose, boolean posed, float x, float y, float s) {
        String wound = posed ? ArtNames.crewWound(c, pose) : null;
        EvaComponent eva = EvaSystem.of(e);
        ArtNames.EvaPose out = ArtNames.EvaPose.of(eva);
        float turn = out == null ? Float.NaN : eva.facing * MathUtils.radiansToDegrees - 90f;
        deckArt.crew(batch, e, style.figure(e, c), pose, c.suited ? c.suitKind : null, out, turn, wound,
                bleeding(c) ? 0.55f : 1f, c.load != null ? c.load.task.job.commodity() : c.crate != null ? c.crate.commodity : null,
                x, y, s);
    }

    /**
     * The crew out on EVA from every craft but {@code decks} (drawn with their decks, crew and all),
     * over the craft as they look from outside: their tethers, the puffs of their jetpacks and
     * their figures. Those still in an airlock stay hidden under the hull.
     */
    public void drawOutside(Array<Entity> decks, OrthographicCamera camera, float delta) {
        GameWorld current = world.get();
        if (current == null) return;
        spacewalkers.update(delta);
        ImmutableArray<Entity> crew = current.engine.getEcsEngine().getEntitiesFor(CrewService.CREW);
        outside.clear();
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            Entity ship = EvaSystem.outside(e) ? e.getComponent(GridPositionComponent.class).ship : null;
            if (ship != null && !decks.contains(ship, true) && !outside.contains(ship, true)) outside.add(ship);
        }
        if (outside.size == 0) return;
        boolean figures = deckArt.available() && deckArt.hasCrew();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        for (Entity ship : outside) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            float s = g.tileSize, degrees = angle[0] * MathUtils.radiansToDegrees;
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, degrees);
            spacewalkers.draw(shapes, current, ship, g, delta);
            if (!figures) {
                for (int i = 0; i < crew.size(); i++) {
                    Entity e = crew.get(i);
                    GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                    if (pos.ship != ship || !EvaSystem.outside(e)) continue;
                    CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                    Vector2 l = pos.toShipLocal(g, local);
                    Color dot = crewColor.set(style.dot(e, c));
                    if (c.state == CrewState.UNCONSCIOUS) dot.mul(GREYED);
                    DeckView.crew(shapes, c, l.x, l.y, s, dot, color);
                }
            }
            shapes.end();
            if (!figures) continue;
            batch.setTransformMatrix(transform.idt().translate(at.x, at.y, 0f).rotate(0f, 0f, 1f, degrees));
            batch.begin();
            for (int i = 0; i < crew.size(); i++) {
                Entity e = crew.get(i);
                GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                if (pos.ship != ship || !EvaSystem.outside(e)) continue;
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                ArtNames.CrewPose pose = ArtNames.CrewPose.of(c);
                boolean posed = deckArt.hasCrew(pose);
                Vector2 l = pos.toShipLocal(g, local);
                batch.setColor(!posed && c.state == CrewState.UNCONSCIOUS ? GREYED : Color.WHITE);
                figure(e, c, pose, posed, l.x, l.y, s);
            }
            batch.setColor(Color.WHITE);
            batch.end();
            batch.setTransformMatrix(IDENTITY);
        }
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        progressBars(outside, camera, true);
    }

    /**
     * A faint bar over each crew member aboard {@code ships} at a slow job nothing else on the deck
     * shows ({@link CrewMemberComponent#progress}): repairing, forcing a door, getting into an EVA
     * suit. Over the light map so it reads in the dark, and level on the screen however the craft turns. {@code outsideOnly} keeps to those out on EVA.
     */
    private void progressBars(Array<Entity> ships, OrthographicCamera camera, boolean outsideOnly) {
        GameWorld current = world.get();
        if (current == null || ships.size == 0) return;
        // Level on the screen: the bar's long side along the camera's right, raised along its up
        float upX = camera.up.x, upY = camera.up.y;
        float degrees = MathUtils.atan2(-upX, upY) * MathUtils.radiansToDegrees;
        // World units to a screen pixel, so the bar keeps a readable size zoomed out
        float pixel = Gdx.graphics.getWidth() > 0 ? camera.zoom * camera.viewportWidth / Gdx.graphics.getWidth() : 0f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity ship : ships) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            float cos = MathUtils.cos(angle[0]), sin = MathUtils.sin(angle[0]), s = g.tileSize;
            for (Entity e : current.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
                if (pos.ship != ship || (outsideOnly && !EvaSystem.outside(e))) continue;
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                if (c.progress < 0f || c.state == CrewState.DEAD) continue;
                Vector2 l = pos.toShipLocal(g, local);
                float grow = Math.max(1f, BAR_MIN_PIXELS * pixel / (BAR_WIDTH * s));
                float w = BAR_WIDTH * s * grow, h = BAR_HEIGHT * s * grow;
                float raise = BAR_RAISE * s + (h - BAR_HEIGHT * s) / 2f;
                float wx = at.x + cos * l.x - sin * l.y + upX * raise;
                float wy = at.y + sin * l.x + cos * l.y + upY * raise;
                shapes.identity();
                shapes.translate(wx, wy, 0f);
                shapes.rotate(0f, 0f, 1f, degrees);
                shapes.setColor(BAR_BACK);
                shapes.rect(-w / 2f, -h / 2f, w, h);
                shapes.setColor(BAR_FILL);
                shapes.rect(-w / 2f, -h / 2f, w * Math.min(1f, c.progress), h);
            }
        }
        shapes.end();
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** The sparks, flashes and fire of hits on {@code decks}' hulls, over the light map so it does not dim them. */
    private void glowingImpacts(Array<Entity> decks, OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        for (Entity ship : decks) {
            ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
            if (g == null || !pose.of(ship, at, angle)) continue;
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, angle[0] * MathUtils.radiansToDegrees);
            impacts.drawGlowing(g, shapes, g.gridOriginLocal.x, g.gridOriginLocal.y, g.tileSize);
        }
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** A job's progress bar over a crew member, in tiles: its length, thickness and how far above them. */
    private static final float BAR_WIDTH = 0.6f, BAR_HEIGHT = 0.05f, BAR_RAISE = 0.55f;
    /** The shortest a progress bar is drawn on the screen, pixels: it grows to this zoomed out. */
    private static final float BAR_MIN_PIXELS = 14f;
    /** Faint: a hint over the figure, not a HUD element. */
    private static final Color BAR_BACK = new Color(0.8f, 0.85f, 0.9f, 0.12f), BAR_FILL = new Color(0.8f, 0.88f, 0.95f, 0.45f);

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
                else out.set(1f, 0.15f, 0.15f, bleeding(c, clock) ? 0.45f : 1f);
                break;
            default: out.set(0.85f, 0.9f, 1f, 1f);
        }
        if (c.state == CrewState.UNCONSCIOUS) out.mul(0.75f, 0.75f, 0.75f, 1f);
        return out;
    }

    /** Whether {@code c}'s wound is in the dim half of its blink: a critical one, bleeding out. */
    private boolean bleeding(CrewMemberComponent c) {
        return bleeding(c, clock);
    }

    private static boolean bleeding(CrewMemberComponent c, float clock) {
        return c.wound == Wound.CRITICAL && !c.stabilised && clock % BLINK >= BLINK / 2f;
    }

    /** A dead crew member's body at ({@code x}, {@code y}) without the art for one: a grey cross. */
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
