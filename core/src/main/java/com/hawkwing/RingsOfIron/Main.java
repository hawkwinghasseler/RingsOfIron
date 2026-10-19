package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.ComponentMapper;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SimClock;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.lighting.DeckLightRig;
import com.hawkwing.RingsOfIron.lighting.EmissiveLights;
import com.hawkwing.RingsOfIron.lighting.SceneLighting;
import com.ringsofiron.interiorgrid.*;

import java.nio.file.Paths;

/**
 * Interactive demo for the interior-grid module, drawn with the art pack and lit by the deck's own
 * lights (box2dlights), as the game draws a deck. Without the art atlas it draws the flat plan.
 *
 * Controls:
 *   Left Click         - Start / put out a fire on a carpeted tile
 *   Right Click        - Toggle a bulkhead seal on a tile (seals a breach too)
 *   Middle Click       - Damage a tile by 12.5%, or the edge wall next to the cursor
 *   D                  - Open/seal the doors in the edge wall
 *   F                  - Extinguish all fires
 *   V                  - Refill every room with fresh air and cool the ship down
 *   P                  - Switch the reactor off / on
 *   L                  - Deck lighting on / off
 *   R                  - Reset to initial state
 *   ESC                - Exit
 */
public class Main extends ApplicationAdapter {

    // --- Layout -------------------------------------------------
    private static final float TILE_PX = 40f;     // pixel size of each tile on screen
    private static final int WORLD_W = 24;         // tiles wide for the virtual viewport
    private static final int WORLD_H = 18;         // tiles tall
    /** Damage dealt by one middle click, as a share of full condition. */
    private static final float CLICK_DAMAGE = 0.125f;
    /** How close (share of a tile) the cursor must be to an edge wall to hit it. */
    private static final float EDGE_PICK = 0.2f;

    // --- Ship blueprint (inner dimensions determined by '#' border) ---
    // Text rows read top-to-bottom; the grid stores them bottom-left first, +y up (Box2D).
    // Every floor is steel; the rooms left of the dividing wall are carpeted on top.
    private static final String[] BLUEPRINT = {
        "####################  ",
        "#..................#  ",
        "#..R++++++++++++HH.#  ",  // S reactor (1x1), S helm slot (2x1)
        "#..................#  ",
        "#..LL..........I...#  ",  // S life support slot, full-tile internal wall
        "#..................#  ",
        "#..................#  ",
        "####################  "
    };
    /** Grid column whose left edge carries the thin dividing wall. */
    private static final int EDGE_WALL_X = 10;

    private static ChassisBlueprint createBlueprint() {
        ChassisBlueprint bp = new ChassisBlueprint(BLUEPRINT).tileSize(1f);
        // Thin wall splitting the hold, with a bulkhead door at y = 3
        for (int y = 1; y <= 6; y++) {
            bp.edgeWall(EDGE_WALL_X, y, Side.LEFT, WeightClass.LIGHT, y == 3);
        }
        // Carpet over the steel floor of everything left of the dividing wall
        bp.covering(FloorCovering.CARPET, 1, 1, EDGE_WALL_X - 1, 6);
        // Heavy 1x2 armor wedge on the starboard side, sloped face outward
        bp.hullPiece(new HullPiece(HullPiece.Shape.WEDGE, 20, 3, 1, 2, 0,
                WeightClass.HEAVY, MaterialTable.DEFAULT_MATERIAL));
        return bp;
    }

    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS =
            ComponentMapper.getFor(SubsystemComponent.class);

    // --- ECS / Module objects ---
    private GameEngine engine;
    private InteriorHazardWarpGuard warpGuard;
    /** Result of the last rails warp attempt, and how long it stays on screen. */
    private String warpMessage = null;
    private float warpMessageTime = 0f;
    private Entity shipEntity;
    private ShipGridComponent grid;
    private ShipSubsystemRegistryComponent subsysRegistry;

    // --- Rendering ---
    private OrthographicCamera camera;
    private FitViewport viewport;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private ArtLibrary art;
    private DeckArt deckArt;
    private SceneLighting lighting;
    private boolean lightsOn = true;
    /** The deck in meters, for the light map: the grid's local frame at the world origin. */
    private final OrthographicCamera lightCamera = new OrthographicCamera();
    /** Grid local meters to screen pixels, for drawing the deck art. */
    private final Matrix4 deckPose = new Matrix4();
    private final Matrix4 identity = new Matrix4();
    private float time;

    // --- State ---
    private final HazardParticles particles = new HazardParticles();
    private int hoverX = -1, hoverY = -1;

    // -----------------------------------------------------------------------
    @Override
    public void create() {
        // -- Camera & viewport setup --
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W * TILE_PX, WORLD_H * TILE_PX, camera);
        viewport.apply(true);

        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        deckArt = new DeckArt(art);
        // The demo runs in assets/, where art/lights.json is
        lighting = new SceneLighting(EmissiveLights.load(Paths.get("").toAbsolutePath()));

        // -- ECS Engine --
        engine = new GameEngine();
        warpGuard = new InteriorHazardWarpGuard(null);
        engine.addRailsWarpGuard(warpGuard);
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());

        buildShip();
    }

    private void buildShip() {
        // Parse blueprint
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(createBlueprint());
        grid = result.grid;
        // Divide the interior into rooms and fill them with air before the first frame
        CompartmentBuilder.rebuildIfChanged(grid);

        // Create ship entity
        shipEntity = new Entity();
        shipEntity.add(grid);
        subsysRegistry = new ShipSubsystemRegistryComponent(shipEntity);
        shipEntity.add(subsysRegistry);
        engine.addEntity(shipEntity);
        warpGuard.setGrid(grid);

        // Spawn subsystem entities from blueprint
        for (ChassisBlueprintLoader.SubsystemPlacement placement : result.subsystems) {
            Entity subsysEntity = new Entity();
            SubsystemComponent sub = new SubsystemComponent(placement.footprint, placement.type);
            subsysEntity.add(sub);
            placement.footprint.installed = subsysEntity;
            engine.addEntity(subsysEntity);
            subsysRegistry.registerSubsystem(subsysEntity);
        }
    }

    // -----------------------------------------------------------------------
    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleInput();
        engine.update(delta);
        particles.update(grid, delta);
        warpMessageTime -= delta;
        time += delta;
        lighting.clear(delta);

        // --- Draw ---
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        drawGrid();
        drawHud();
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) resetShip();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) extinguishAllFires();
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) restoreAtmosphere();
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) toggleReactor();
        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) lightsOn = !lightsOn;
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) toggleEdgeDoors();
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) railsWarp();

        // Convert mouse → tile coordinates
        Vector3 worldPos = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        // Grid starts at (2 * TILE_PX, 2 * TILE_PX) to leave some margin
        float gx = (worldPos.x - 2 * TILE_PX) / TILE_PX;
        float gy = (worldPos.y - 2 * TILE_PX) / TILE_PX;
        int tx = (int) Math.floor(gx);
        int ty = (int) Math.floor(gy);
        hoverX = tx;
        hoverY = ty;

        if (grid.inBounds(tx, ty)) {
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                toggleFire(tx, ty);
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
                toggleBulkhead(tx, ty);
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.MIDDLE)) {
                damageAt(tx, ty, gx - tx, gy - ty);
            }
        }
    }

    private void toggleFire(int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null) return;
        if (t.isBurning()) t.extinguish();
        else t.ignite(); // only carpet burns; bare steel will not light
    }

    private void toggleBulkhead(int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.isVoid()) return;
        t.bulkheadSealed = !t.bulkheadSealed;
    }

    /** Damages the edge wall the cursor is next to, or else the tile itself. */
    private void damageAt(int x, int y, float fx, float fy) {
        Side nearest = null;
        float best = EDGE_PICK;
        float[] dist = {fy, 1f - fy, fx, 1f - fx}; // BOTTOM, TOP, LEFT, RIGHT
        Side[] sides = {Side.BOTTOM, Side.TOP, Side.LEFT, Side.RIGHT};
        for (int i = 0; i < 4; i++) {
            if (dist[i] < best && grid.getEdge(x, y, sides[i]) != null) {
                best = dist[i];
                nearest = sides[i];
            }
        }
        if (nearest != null) {
            EdgeWall wall = grid.getEdge(x, y, nearest);
            wall.integrity = Math.max(0f, wall.integrity - CLICK_DAMAGE);
        } else {
            Tile t = grid.getTile(x, y);
            if (t == null || t.isVoid()) return;
            t.setIntegrity(t.getIntegrity() - CLICK_DAMAGE);
        }
        grid.gridDirty = true;
    }

    private void toggleEdgeDoors() {
        for (int y = 0; y < grid.height; y++) {
            EdgeWall wall = grid.getEdge(EDGE_WALL_X, y, Side.LEFT);
            if (wall != null && wall.hasBulkhead) wall.bulkheadSealed = !wall.bulkheadSealed;
        }
    }

    private void extinguishAllFires() {
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) grid.getTile(x, y).extinguish();
    }

    /** Jumps an hour ahead, unless a fire or venting aboard refuses it. */
    private void railsWarp() {
        String blocked = engine.getRailsWarpBlockedReason();
        if (blocked == null) engine.advanceRails(SimClock.secondsToTicks(3600));
        warpMessage = blocked == null ? "Warped 1 hour ahead" : "Rails warp refused: " + blocked;
        warpMessageTime = 3f;
    }

    private void restoreAtmosphere() {
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) grid.getTile(x, y).temperature = Compartment.AMBIENT_TEMPERATURE;
        for (Compartment c : grid.compartments) {
            c.temperature = Compartment.AMBIENT_TEMPERATURE;
            c.fillStandardAir();
        }
    }

    private void toggleReactor() {
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = SUBSYSTEMS.get(e);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR) {
                sub.switchedOn = !sub.switchedOn;
            }
        }
        grid.gridDirty = true;
    }

    private void resetShip() {
        // Remove old ship from engine
        engine.getEcsEngine().removeAllEntities();
        buildShip();
        particles.clear();
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private void drawGrid() {
        if (deckArt.available()) drawArtDeck();
        else drawFlatDeck();
    }

    /**
     * The deck in the art pack, then the light map over it and what glows on top, as the game
     * draws a deck; the demo's readouts (heat, leaks, room lines, system status) go over the light.
     */
    private void drawArtDeck() {
        float offX = 2 * TILE_PX;
        float offY = 2 * TILE_PX;
        // The grid's corner sits at (offX, offY), one meter to TILE_PX pixels
        float ox = offX - grid.gridOriginLocal.x * TILE_PX, oy = offY - grid.gridOriginLocal.y * TILE_PX;
        deckPose.idt().translate(ox, oy, 0f).scale(TILE_PX, TILE_PX, 1f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setTransformMatrix(deckPose);
        batch.begin();
        deckArt.draw(batch, grid, null);
        batch.end();
        batch.setTransformMatrix(identity);

        // Vacuum and haze over the rooms, in the deck's meters
        shapeRenderer.setTransformMatrix(deckPose);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        DeckView.atmosphere(shapeRenderer, grid, time % 1f < 0.5f);
        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(identity);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawHeat(offX, offY);
        shapeRenderer.end();
        particles.draw(grid, shapeRenderer, offX, offY, TILE_PX);

        if (lightsOn) {
            lightCamera.viewportWidth = viewport.getWorldWidth() / TILE_PX;
            lightCamera.viewportHeight = viewport.getWorldHeight() / TILE_PX;
            lightCamera.position.set((camera.position.x - ox) / TILE_PX, (camera.position.y - oy) / TILE_PX, 0f);
            lightCamera.update();
            lighting.addDeck(grid, 0f, 0f, 0f);
            lighting.renderLights(lightCamera, DeckLightRig.hasPower(grid));
            // What gives off light shows lit whatever light falls on it
            batch.setTransformMatrix(deckPose);
            batch.begin();
            deckArt.glow(batch, grid);
            batch.end();
            batch.setTransformMatrix(identity);
            lighting.renderGlow(batch, lightCamera, 0f);
            batch.setProjectionMatrix(camera.combined);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                if (tile == null || tile.isVoid()) continue;
                drawLeak(tile, offX + x * TILE_PX, offY + y * TILE_PX);
            }
        }
        drawRoomBoundaries(offX, offY);
        shapeRenderer.end();

        // A status dot in each system's corner, the art showing the system itself
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = SUBSYSTEMS.get(e);
            if (sub == null) continue;
            MountFootprint f = sub.footprint;
            shapeRenderer.setColor(subsystemColor(sub));
            shapeRenderer.circle(offX + (f.originX + f.width()) * TILE_PX - 7f,
                    offY + (f.originY + f.height()) * TILE_PX - 7f, 4f, 12);
        }
        shapeRenderer.end();
        drawSubsystemLabels(offX, offY);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Heat glow, from dull red at 350 K to bright at the ignition temperature. Call inside a Filled batch. */
    private void drawHeat(float offX, float offY) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                float heat = (tile.temperature - 350f) / (GridHazardSystem.IGNITION_TEMPERATURE - 350f);
                if (heat > 0f && !tile.isVoid()) {
                    shapeRenderer.setColor(1f, 0.25f, 0.05f, Math.min(0.6f, heat * 0.6f));
                    shapeRenderer.rect(offX + x * TILE_PX + 1, offY + y * TILE_PX + 1, TILE_PX - 2, TILE_PX - 2);
                }
            }
        }
    }

    /** Leaking walls and floors: a cyan cross, brighter as the leak grows. Call inside a Line batch. */
    private void drawLeak(Tile tile, float px, float py) {
        float leak = tile.isOpen() && !tile.isFloor() ? 0f : tile.leakFactor();
        if (leak <= 0f || tile.getIntegrity() <= 0f) return;
        shapeRenderer.setColor(0.3f, 0.9f, 1f, 0.4f + leak);
        shapeRenderer.line(px + 8, py + TILE_PX / 2, px + TILE_PX - 8, py + TILE_PX / 2);
        shapeRenderer.line(px + TILE_PX / 2, py + 8, px + TILE_PX / 2, py + TILE_PX - 8);
    }

    /** The deck plan in flat shapes, for when there is no art atlas. */
    private void drawFlatDeck() {
        float offX = 2 * TILE_PX;
        float offY = 2 * TILE_PX;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                float px = offX + x * TILE_PX;
                float py = offY + y * TILE_PX;

                // Wedge tiles are drawn as one triangle per piece below
                Color c = (tile.piece != null && tile.piece.hasSlopedFace())
                        ? tileColor(null) : tileColor(tile);
                shapeRenderer.setColor(c);
                shapeRenderer.rect(px + 1, py + 1, TILE_PX - 2, TILE_PX - 2);
            }
        }
        drawHeat(offX, offY);

        drawHullPieces(offX, offY);

        shapeRenderer.end();

        // Draw wireframe lines
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                if (tile == null || tile.isVoid()) continue;
                float px = offX + x * TILE_PX;
                float py = offY + y * TILE_PX;
                shapeRenderer.rect(px, py, TILE_PX, TILE_PX);
                drawLeak(tile, px, py);

                // Draw sealed bulkhead indicator (bright yellow X)
                if (tile.bulkheadSealed) {
                    shapeRenderer.setColor(Color.YELLOW);
                    shapeRenderer.line(px + 4, py + 4, px + TILE_PX - 4, py + TILE_PX - 4);
                    shapeRenderer.line(px + TILE_PX - 4, py + 4, px + 4, py + TILE_PX - 4);
                }
                shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);
            }
        }
        drawRoomBoundaries(offX, offY);
        shapeRenderer.end();

        drawEdgeWalls(offX, offY);

        // Smoke and flames over the floor, under the system icons
        particles.draw(grid, shapeRenderer, offX, offY, TILE_PX);

        // Draw subsystem icons as small filled circles
        drawSubsystems(offX, offY);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private Color tileColor(Tile tile) {
        if (tile == null || tile.isVoid()) return new Color(0.05f, 0.05f, 0.1f, 1f);

        if (tile.isWall() && !tile.isOpen()) {
            if (tile.getIntegrity() <= 0f) return new Color(0.6f, 0.1f, 0.0f, 1f); // Breached = dark red
            // Hull = grey, darker as it takes damage
            float shade = 0.2f + 0.2f * tile.getIntegrity();
            return new Color(shade, shade, shade + 0.05f, 1f);
        }
        if (tile.isFloor() && tile.getIntegrity() <= 0f) return new Color(0.02f, 0.02f, 0.05f, 1f); // Hole in the deck

        // Air pressure sets the brightness: a vented room goes dark
        float p = tile.compartment == null ? 0f : Math.min(1f, tile.compartment.pressureRatio());
        float light = 0.35f + 0.65f * p;

        if (tile.covering == FloorCovering.CARPET) {
            return new Color(0.42f * light, 0.12f * light, 0.14f * light, 1f);
        }
        if (tile.isPowerConduit()) {
            return tile.isPowered
                    ? new Color(0.22f * light, 0.3f * light, 0.45f * light, 1f)
                    : new Color(0.14f * light, 0.18f * light, 0.3f * light, 1f);
        }
        // Bare steel deck, with a faint yellow cast where it carries power
        if (tile.isPowered) return new Color(0.3f * light, 0.3f * light, 0.25f * light, 1f);
        return new Color(0.26f * light, 0.28f * light, 0.3f * light, 1f);
    }

    /** White lines where two different rooms meet, so the room division is visible. */
    private void drawRoomBoundaries(float offX, float offY) {
        shapeRenderer.setColor(1f, 1f, 1f, 0.8f);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Compartment c = grid.compartmentAt(x, y);
                if (c == null) continue;
                Compartment right = grid.compartmentAt(x + 1, y), up = grid.compartmentAt(x, y + 1);
                if (right != null && right != c && grid.canFlow(x, y, x + 1, y)) {
                    float lx = offX + (x + 1) * TILE_PX;
                    shapeRenderer.line(lx, offY + y * TILE_PX + 3, lx, offY + (y + 1) * TILE_PX - 3);
                }
                if (up != null && up != c && grid.canFlow(x, y, x, y + 1)) {
                    float ly = offY + (y + 1) * TILE_PX;
                    shapeRenderer.line(offX + x * TILE_PX + 3, ly, offX + (x + 1) * TILE_PX - 3, ly);
                }
            }
        }
    }

    /** Fills each sloped hull piece as a triangle. Call inside a Filled batch. */
    private void drawHullPieces(float offX, float offY) {
        for (HullPiece piece : grid.hullPieces) {
            if (!piece.hasSlopedFace()) continue;
            float x0 = offX + piece.originX * TILE_PX, y0 = offY + piece.originY * TILE_PX;
            float x1 = x0 + piece.width() * TILE_PX, y1 = y0 + piece.height() * TILE_PX;
            // Footprint corners counter-clockwise; the wedge leaves out the one opposite its right angle
            float[][] corners = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
            int skip = (2 + piece.rotation) % 4;
            float[] v = new float[6];
            for (int i = 0, k = 0; i < 4; i++) {
                if (i == skip) continue;
                v[k++] = corners[i][0];
                v[k++] = corners[i][1];
            }
            float shade = 0.25f + 0.2f * piece.integrity;
            shapeRenderer.setColor(piece.isDestroyed()
                    ? new Color(0.6f, 0.1f, 0.0f, 1f) : new Color(shade, shade, shade + 0.05f, 1f));
            shapeRenderer.triangle(v[0], v[1], v[2], v[3], v[4], v[5]);
        }
    }

    /**
     * Thin walls on tile edges: grey wall, green open door, yellow sealed door, cyan when leaking.
     * Conduits along edges are gold over them, brown without power.
     */
    private void drawEdgeWalls(float offX, float offY) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float t = 4f;
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeWall w = grid.edgeH[y][x];
                if (w == null || w.isDestroyed()) continue;
                shapeRenderer.setColor(edgeWallColor(w));
                shapeRenderer.rect(offX + x * TILE_PX, offY + y * TILE_PX - t / 2, TILE_PX, t);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall w = grid.edgeV[y][x];
                if (w == null || w.isDestroyed()) continue;
                shapeRenderer.setColor(edgeWallColor(w));
                shapeRenderer.rect(offX + x * TILE_PX - t / 2, offY + y * TILE_PX, t, TILE_PX);
            }
        }
        // Conduits along edges, over the walls they may run in
        float c = 2f;
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeConduit conduit = grid.conduitH[y][x];
                if (conduit == null) continue;
                shapeRenderer.setColor(conduit.isPowered ? Color.GOLD : Color.BROWN);
                shapeRenderer.rect(offX + x * TILE_PX, offY + y * TILE_PX - c / 2, TILE_PX, c);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeConduit conduit = grid.conduitV[y][x];
                if (conduit == null) continue;
                shapeRenderer.setColor(conduit.isPowered ? Color.GOLD : Color.BROWN);
                shapeRenderer.rect(offX + x * TILE_PX - c / 2, offY + y * TILE_PX, c, TILE_PX);
            }
        }
        shapeRenderer.end();
    }

    private Color edgeWallColor(EdgeWall w) {
        if (w.blocks() && w.leakFactor() > 0f) return Color.CYAN;
        if (!w.hasBulkhead) return new Color(0.5f + 0.3f * w.integrity, 0.5f + 0.3f * w.integrity, 0.5f + 0.3f * w.integrity, 1f);
        return w.bulkheadSealed ? Color.YELLOW : Color.GREEN;
    }

    private void drawSubsystems(float offX, float offY) {
        // Mount outlines
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.CYAN);
        for (MountFootprint m : grid.mounts) {
            shapeRenderer.rect(offX + m.originX * TILE_PX + 2, offY + m.originY * TILE_PX + 2,
                    m.width() * TILE_PX - 4, m.height() * TILE_PX - 4);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = SUBSYSTEMS.get(e);
            if (sub == null) continue;
            MountFootprint f = sub.footprint;
            float cx = offX + (f.originX + f.width() * 0.5f) * TILE_PX;
            float cy = offY + (f.originY + f.height() * 0.5f) * TILE_PX;
            float r = TILE_PX * 0.25f;
            shapeRenderer.setColor(subsystemColor(sub));
            shapeRenderer.circle(cx, cy, r, 16);
        }
        shapeRenderer.end();
        drawSubsystemLabels(offX, offY);
    }

    /** Each system's name, shortened, in the top-left corner of its mount. */
    private void drawSubsystemLabels(float offX, float offY) {
        batch.begin();
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = SUBSYSTEMS.get(e);
            if (sub == null) continue;
            float tx = offX + sub.footprint.originX * TILE_PX + 4;
            float ty = offY + (sub.footprint.originY + sub.footprint.height()) * TILE_PX - 4;
            font.setColor(Color.WHITE);
            font.getData().setScale(0.7f);
            String label = sub.type.name().substring(0, Math.min(3, sub.type.name().length()));
            font.draw(batch, label, tx, ty);
        }
        batch.end();
    }

    private Color subsystemColor(SubsystemComponent sub) {
        if (sub.isDisabled()) return Color.DARK_GRAY;
        if (!sub.switchedOn) return Color.BLUE;
        if (sub.isOperational) return Color.YELLOW;
        return Color.RED;
    }

    private void drawHud() {
        batch.begin();
        font.getData().setScale(1.0f);
        font.setColor(Color.WHITE);

        float lx = 5f;
        float ly = viewport.getWorldHeight() - 5f;
        float lineH = 18f;

        font.draw(batch, "=== Interior Grid Demo ===", lx, ly); ly -= lineH * 1.5f;

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "[L-Click]  Start / put out fire (carpet only)", lx, ly); ly -= lineH;
        font.draw(batch, deckArt.available() ? "[R-Click]  Toggle bulkhead seal"
                : "[R-Click]  Toggle bulkhead seal (yellow X)", lx, ly); ly -= lineH;
        font.draw(batch, "[M-Click]  Damage tile, or edge wall by cursor", lx, ly); ly -= lineH;
        font.draw(batch, "[D]        Open/seal edge-wall door", lx, ly); ly -= lineH;
        font.draw(batch, "[P]        Switch reactor off / on", lx, ly); ly -= lineH;
        if (deckArt.available()) {
            font.draw(batch, "[L]        Deck lighting " + (lightsOn ? "on" : "off"), lx, ly); ly -= lineH;
        }
        font.draw(batch, "[F]        Extinguish all fires", lx, ly); ly -= lineH;
        font.draw(batch, "[V]        Fresh air in every room", lx, ly); ly -= lineH;
        font.draw(batch, "[T]        Rails warp 1 hour (refused during hazards)", lx, ly); ly -= lineH;
        font.draw(batch, "[R]        Reset ship", lx, ly); ly -= lineH;
        font.draw(batch, "[ESC]      Exit", lx, ly); ly -= lineH * 1.5f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "Systems: yellow = running", lx, ly); ly -= lineH;
        font.setColor(Color.RED);
        font.draw(batch, "  red = no power", lx, ly);
        font.setColor(new Color(0.4f, 0.5f, 1f, 1f));
        font.draw(batch, "blue = switched off", lx + 120, ly);
        font.setColor(Color.GRAY);
        font.draw(batch, "grey = damaged", lx + 260, ly); ly -= lineH;
        font.setColor(new Color(0.3f, 0.9f, 1f, 1f));
        font.draw(batch, deckArt.available() ? "Cyan cross = leaking (below 50%)"
                : "Cyan cross / wall = leaking (below 50%)", lx, ly); ly -= lineH;
        font.setColor(Color.WHITE);
        font.draw(batch, "White lines divide rooms at 1-wide openings", lx, ly);

        if (warpMessage != null && warpMessageTime > 0f) {
            font.setColor(Color.ORANGE);
            font.draw(batch, warpMessage, 560f, 60f);
        }

        drawTileInfo(560f, viewport.getWorldHeight() - 5f, lineH);
        batch.end();
    }

    /** Readout for the tile under the cursor and the room it is in. Call inside the batch. */
    private void drawTileInfo(float x, float y, float lineH) {
        font.setColor(Color.WHITE);
        font.draw(batch, "=== Under cursor ===", x, y); y -= lineH * 1.5f;
        Tile t = grid.getTile(hoverX, hoverY);
        if (t == null || t.isVoid()) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, t == null ? "(hover over the ship)" : "Space", x, y);
            return;
        }
        font.setColor(Color.LIGHT_GRAY);
        String kind = t.isWall() ? (t.wallType == Tile.WallType.HULL ? "Hull" : "Internal wall")
                : t.covering == FloorCovering.CARPET ? "Carpet on steel floor"
                : t.isPowerConduit() ? "Steel floor, power conduit" : "Steel floor";
        font.draw(batch, kind + "  (" + hoverX + ", " + hoverY + ")", x, y); y -= lineH;
        font.draw(batch, String.format("Condition %.0f%%   leak %.0f%%", t.getIntegrity() * 100f,
                (t.isOpen() && !t.isFloor() ? 0f : t.leakFactor()) * 100f), x, y); y -= lineH;
        font.draw(batch, String.format("Temperature %.0f K", t.temperature), x, y); y -= lineH;
        if (t.fuel > 0f || t.isBurning()) {
            font.setColor(t.isBurning() ? Color.ORANGE : Color.LIGHT_GRAY);
            font.draw(batch, String.format("Fire: %s (%.0f%%)   fuel %.2f kg", t.fireSize(),
                    t.fireIntensity * 100f, t.fuel), x, y); y -= lineH;
        }
        if (t.bulkheadSealed) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "Sealed", x, y); y -= lineH;
        }
        y -= lineH * 0.5f;

        Compartment c = t.compartment;
        if (c == null) return;
        font.setColor(new Color(0.6f, 0.8f, 1f, 1f));
        font.draw(batch, String.format("Room %d: %d tiles, %.0f m3", c.id, c.tiles.size, c.volume), x, y); y -= lineH;
        font.draw(batch, String.format("Pressure %.1f kPa", c.pressure()), x, y); y -= lineH;
        font.draw(batch, String.format("O2 %.1f%%   CO2 %.2f%%", c.oxygenFraction() * 100f,
                c.carbonDioxideFraction() * 100f), x, y); y -= lineH;
        font.draw(batch, String.format("Smoke %.1f g/m3   air %.0f K", c.smokeDensity() * 1000f, c.temperature), x, y);
    }

    // -----------------------------------------------------------------------
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        // The light map covers the letterboxed scene, not the bars around it
        lighting.setScreenArea(viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
        lighting.dispose();
        art.dispose();
    }
}
