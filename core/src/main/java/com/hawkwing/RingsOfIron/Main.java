package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.Engine;
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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.ringsofiron.interiorgrid.*;

/**
 * Interactive demo for the interior-grid module.
 *
 * Controls:
 *   Left Click         - Toggle fire on a floor tile
 *   Right Click        - Toggle bulkhead seal on a tile
 *   Middle Click       - Destroy a hull tile (create breach)
 *   F                  - Extinguish all fires
 *   V                  - Restore all vacuum tiles to full oxygen
 *   P                  - Toggle power (destroy/restore reactor)
 *   R                  - Reset to initial state
 *   ESC                - Exit
 */
public class Main extends ApplicationAdapter {

    // --- Layout -------------------------------------------------
    private static final float TILE_PX = 40f;     // pixel size of each tile on screen
    private static final int WORLD_W = 24;         // tiles wide for the virtual viewport
    private static final int WORLD_H = 18;         // tiles tall

    // --- Ship blueprint (inner dimensions determined by '#' border) ---
    private static final String[] BLUEPRINT = {
        "####################",
        "#..................#",
        "#..R+++++++++++H..#",
        "#..................#",
        "#..L...........#..#",  // internal bulkhead wall in middle of row
        "#..................#",
        "#..................#",
        "####################"
    };

    // --- ECS / Module objects ---
    private Engine engine;
    private Entity shipEntity;
    private ShipGridComponent grid;
    private ShipSubsystemRegistryComponent subsysRegistry;

    // --- Rendering ---
    private OrthographicCamera camera;
    private FitViewport viewport;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;

    // --- State ---
    private boolean reactorDestroyed = false;
    private float elapsedTime = 0f;

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

        // -- ECS Engine --
        engine = new Engine();
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());

        buildShip();
    }

    private void buildShip() {
        // Parse blueprint
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(BLUEPRINT, 1f);
        grid = result.grid;

        // Create ship entity
        shipEntity = new Entity();
        shipEntity.add(grid);
        subsysRegistry = new ShipSubsystemRegistryComponent(shipEntity);
        shipEntity.add(subsysRegistry);
        engine.addEntity(shipEntity);

        // Spawn subsystem entities from blueprint
        for (ChassisBlueprintLoader.SubsystemPlacement placement : result.subsystems) {
            Entity subsysEntity = new Entity();
            SubsystemComponent sub = new SubsystemComponent(placement.x, placement.y, placement.type);
            subsysEntity.add(sub);
            engine.addEntity(subsysEntity);
            subsysRegistry.registerSubsystem(subsysEntity);
        }
    }

    // -----------------------------------------------------------------------
    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        elapsedTime += delta;

        handleInput();
        engine.update(delta);

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
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) restoreAllOxygen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) toggleReactor();

        // Convert mouse → tile coordinates
        Vector3 worldPos = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        // Grid starts at (2 * TILE_PX, 2 * TILE_PX) to leave some margin
        float gridOffsetX = 2 * TILE_PX;
        float gridOffsetY = 2 * TILE_PX;
        int tx = (int) ((worldPos.x - gridOffsetX) / TILE_PX);
        int ty = (int) ((worldPos.y - gridOffsetY) / TILE_PX);

        if (grid.inBounds(tx, ty)) {
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                toggleFire(tx, ty);
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
                toggleBulkhead(tx, ty);
            }
            if (Gdx.input.isButtonJustPressed(Input.Buttons.MIDDLE)) {
                breachHull(tx, ty);
            }
        }
    }

    private void toggleFire(int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.isWall() || t.isVoid()) return;
        t.hasFire = !t.hasFire;
    }

    private void toggleBulkhead(int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.isVoid()) return;
        t.bulkheadSealed = !t.bulkheadSealed;
    }

    private void breachHull(int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.isWall()) return;
        t.integrity = 0f;
    }

    private void extinguishAllFires() {
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null) t.hasFire = false;
            }
    }

    private void restoreAllOxygen() {
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor()) {
                    t.oxygenLevel = 1.0f;
                    t.hasVacuum = false;
                }
            }
    }

    private void toggleReactor() {
        // Find reactor subsystem and toggle its health
        com.badlogic.ashley.core.ComponentMapper<SubsystemComponent> sm =
                com.badlogic.ashley.core.ComponentMapper.getFor(SubsystemComponent.class);
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = sm.get(e);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR) {
                sub.health = (sub.health > 0) ? 0f : 1f;
                reactorDestroyed = (sub.health <= 0);
            }
        }
    }

    private void resetShip() {
        // Remove old ship from engine
        engine.removeAllEntities();
        buildShip();
        reactorDestroyed = false;
        elapsedTime = 0f;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private void drawGrid() {
        float offX = 2 * TILE_PX;
        float offY = 2 * TILE_PX;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile tile = grid.getTile(x, y);
                float px = offX + x * TILE_PX;
                float py = offY + y * TILE_PX;

                Color c = tileColor(tile);
                shapeRenderer.setColor(c);
                shapeRenderer.rect(px + 1, py + 1, TILE_PX - 2, TILE_PX - 2);
            }
        }

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

                // Draw sealed bulkhead indicator (bright yellow X)
                if (tile.bulkheadSealed) {
                    shapeRenderer.setColor(Color.YELLOW);
                    shapeRenderer.line(px + 4, py + 4, px + TILE_PX - 4, py + TILE_PX - 4);
                    shapeRenderer.line(px + TILE_PX - 4, py + 4, px + 4, py + TILE_PX - 4);
                    shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);
                }
            }
        }
        shapeRenderer.end();

        // Draw subsystem icons as small filled circles
        drawSubsystems(offX, offY);
    }

    private Color tileColor(Tile tile) {
        if (tile == null || tile.isVoid()) return new Color(0.05f, 0.05f, 0.1f, 1f);

        if (tile.isWall()) {
            if (tile.integrity <= 0f) return new Color(0.6f, 0.1f, 0.0f, 1f); // Breached = dark red
            return new Color(0.35f, 0.35f, 0.4f, 1f); // Hull = grey
        }

        if (tile.hasFire) {
            // Flicker orange/yellow
            float t = (float)(Math.sin(elapsedTime * 8 + tile.hashCode()) * 0.5 + 0.5);
            return new Color(1f, 0.4f + t * 0.3f, 0f, 1f);
        }

        if (tile.hasVacuum || tile.oxygenLevel < 0.05f) {
            return new Color(0.05f, 0.05f, 0.25f, 1f); // Near-black blue for vacuum
        }

        // Oxygen tint: full O2 = light blue-green, partial = darker
        float o2 = tile.oxygenLevel;
        if (tile.isPowered) {
            // Powered: add subtle yellow highlight
            return new Color(0.2f * o2 + 0.1f, 0.25f * o2 + 0.15f, 0.15f, 1f);
        }

        if (tile.isPowerConduit()) {
            return new Color(0.1f, 0.1f + 0.3f * o2, 0.3f, 1f);
        }

        return new Color(0.15f * o2, 0.22f * o2, 0.22f * o2, 1f);
    }

    private void drawSubsystems(float offX, float offY) {
        com.badlogic.ashley.core.ComponentMapper<SubsystemComponent> sm =
                com.badlogic.ashley.core.ComponentMapper.getFor(SubsystemComponent.class);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = sm.get(e);
            if (sub == null) continue;
            float cx = offX + sub.gridX * TILE_PX + TILE_PX * 0.5f;
            float cy = offY + sub.gridY * TILE_PX + TILE_PX * 0.5f;
            float r = TILE_PX * 0.25f;

            if (!sub.isOperational) {
                shapeRenderer.setColor(Color.DARK_GRAY);
            } else if (sub.powered) {
                shapeRenderer.setColor(Color.YELLOW);
            } else {
                shapeRenderer.setColor(Color.RED);
            }
            shapeRenderer.circle(cx, cy, r, 16);
        }
        shapeRenderer.end();

        // Draw subsystem labels
        batch.begin();
        for (Entity e : subsysRegistry.subsystems) {
            SubsystemComponent sub = sm.get(e);
            if (sub == null) continue;
            float tx = offX + sub.gridX * TILE_PX + 4;
            float ty = offY + sub.gridY * TILE_PX + TILE_PX - 4;
            font.setColor(Color.WHITE);
            font.getData().setScale(0.7f);
            String label = sub.type.name().substring(0, Math.min(3, sub.type.name().length()));
            font.draw(batch, label, tx, ty);
        }
        batch.end();
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
        font.draw(batch, "[L-Click]  Toggle fire on tile", lx, ly); ly -= lineH;
        font.draw(batch, "[R-Click]  Toggle bulkhead seal (yellow X)", lx, ly); ly -= lineH;
        font.draw(batch, "[M-Click]  Breach hull tile", lx, ly); ly -= lineH;
        font.draw(batch, "[P]        Toggle reactor power", lx, ly); ly -= lineH;
        font.draw(batch, "[F]        Extinguish all fires", lx, ly); ly -= lineH;
        font.draw(batch, "[V]        Restore oxygen to all tiles", lx, ly); ly -= lineH;
        font.draw(batch, "[R]        Reset ship", lx, ly); ly -= lineH;
        font.draw(batch, "[ESC]      Exit", lx, ly); ly -= lineH * 1.5f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "Subsystem circles:", lx, ly); ly -= lineH;
        font.setColor(Color.YELLOW);
        font.draw(batch, "  Yellow = powered & operational", lx, ly); ly -= lineH;
        font.setColor(Color.RED);
        font.draw(batch, "  Red    = no power", lx, ly); ly -= lineH;
        font.setColor(Color.DARK_GRAY);
        font.draw(batch, "  Gray   = destroyed", lx, ly); ly -= lineH * 1.5f;

        font.setColor(Color.ORANGE);
        font.draw(batch, "Fire spreads slowly (1 tile/sec).", lx, ly); ly -= lineH;
        font.setColor(new Color(0.4f, 0.4f, 1f, 1f));
        font.draw(batch, "Vacuum bleeds from hull breaches.", lx, ly); ly -= lineH;
        font.setColor(Color.WHITE);
        font.draw(batch, "Bulkhead seals stop both.", lx, ly);

        batch.end();
    }

    // -----------------------------------------------------------------------
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }
}