package com.hawkwing.RingsOfIron;

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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.demo.CrewScenario;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Interactive demo for the ai-crew module: the {@link CrewScenario} ship with its crew
 * of five, drawn from above. The scripted trouble (fire, reactor hit, hull breach,
 * boarders) plays out on its own; the mouse adds more.
 *
 * Controls:
 *   Left Click   - Start a fire on a carpeted tile (the mess)
 *   Right Click  - Breach a hull tile, or wound the crew member under the cursor
 *   Middle Click - Spawn a boarder
 *   D            - Open / seal the doors
 *   Space        - Pause
 *   1 / 2 / 3    - Speed x1 / x2 / x4
 *   R            - Restart
 *   ESC          - Exit
 *
 * Crew are circles: blue our crew, red boarders, dimmed when unconscious, grey crosses
 * for the dead. A line shows where each one is walking.
 */
public class CrewDemo extends ApplicationAdapter {

    private static final float TILE_PX = 36f;
    private static final int WORLD_W = 36, WORLD_H = 20;
    /** Screen position of the grid's bottom-left corner. */
    private static final float OFF_X = 2 * TILE_PX, OFF_Y = 9 * TILE_PX;

    private OrthographicCamera camera;
    private FitViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private CrewScenario scenario;
    private final Array<String> log = new Array<>();
    private boolean paused;
    private float speed = 1f;
    private int boarders;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W * TILE_PX, WORLD_H * TILE_PX, camera);
        viewport.apply(true);
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = com.ringsofiron.uihud.HudFonts.body();
        restart();
    }

    private void restart() {
        scenario = new CrewScenario();
        scenario.log = line -> addLog(String.format("%5.1fs %s", scenario.time, line));
        log.clear();
        boarders = 0;
    }

    private void addLog(String line) {
        log.add(line);
        if (log.size > 8) log.removeIndex(0);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        handleInput();
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 20f);
        if (!paused) {
            float step = dt * speed, sub = 1f / 60f;
            while (step > 0f) {
                float d = Math.min(sub, step);
                scenario.update(d);
                step -= d;
            }
        }
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        drawShip();
        drawCrew();
        drawText();
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) restart();
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) speed = 1f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) speed = 2f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) speed = 4f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) toggleDoors();

        Vector3 w = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        int tx = (int) Math.floor((w.x - OFF_X) / TILE_PX), ty = (int) Math.floor((w.y - OFF_Y) / TILE_PX);
        ShipGridComponent g = scenario.grid;
        if (!g.inBounds(tx, ty)) return;
        Tile t = g.getTile(tx, ty);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && t.ignite()) {
            addLog(String.format("%5.1fs Fire at %d,%d", scenario.time, tx, ty));
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            Entity hit = crewAt(tx, ty);
            if (hit != null) {
                scenario.crew.applyDamage(hit, 40f, CrewDamageSink.DamageCause.SPALL);
                addLog(String.format("%5.1fs %s wounded", scenario.time, scenario.crew.crew(hit).name));
            } else if (t.wallType == Tile.WallType.HULL) {
                t.setIntegrity(0f);
                t.bulkheadSealed = false;
                g.gridDirty = true;
                addLog(String.format("%5.1fs Hull breach at %d,%d", scenario.time, tx, ty));
            }
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.MIDDLE) && t.isOpen() && t.mount == null) {
            boarders++;
            scenario.crewMembers.add(CrewFactory.spawn(scenario.crew, scenario.ship, "Raider-" + boarders, CrewRole.MARINE, 1, tx, ty));
            addLog(String.format("%5.1fs Boarder at %d,%d", scenario.time, tx, ty));
        }
    }

    private Entity crewAt(int x, int y) {
        for (Entity e : scenario.crewMembers) {
            GridPositionComponent p = scenario.crew.position(e);
            if (p.tileX() == x && p.tileY() == y && scenario.crew.crew(e).isAlive()) return e;
        }
        return null;
    }

    private void toggleDoors() {
        ShipGridComponent g = scenario.grid;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x <= g.width; x++) {
                EdgeWall wall = g.edgeV[y][x];
                if (wall != null && wall.hasBulkhead) wall.bulkheadSealed = !wall.bulkheadSealed;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    private void drawShip() {
        ShipGridComponent g = scenario.grid;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                float px = OFF_X + x * TILE_PX, py = OFF_Y + y * TILE_PX;
                shapes.setColor(tileColor(t, g.getTileConduit(x, y) != null));
                shapes.rect(px + 1, py + 1, TILE_PX - 2, TILE_PX - 2);
                if (t.holdsAir()) {
                    // Thin air reads as a blue-black wash
                    float air = Math.min(1f, g.oxygenRatioAt(x, y));
                    if (air < 0.9f) {
                        shapes.setColor(0.1f, 0.15f, 0.5f, 0.6f * (1f - air));
                        shapes.rect(px + 1, py + 1, TILE_PX - 2, TILE_PX - 2);
                    }
                }
                if (t.isBurning()) {
                    float s = (TILE_PX - 6) * (0.4f + 0.6f * t.fireIntensity);
                    shapes.setColor(1f, 0.45f + 0.3f * t.fireIntensity, 0.05f, 0.9f);
                    shapes.rect(px + (TILE_PX - s) / 2, py + (TILE_PX - s) / 2, s, s);
                }
            }
        }
        // Edge walls: grey solid, green open door, amber sealed door
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x <= g.width; x++) {
                EdgeWall wall = g.edgeV[y][x];
                if (wall == null || wall.isDestroyed()) continue;
                shapes.setColor(edgeColor(wall));
                shapes.rect(OFF_X + x * TILE_PX - 3, OFF_Y + y * TILE_PX, 6, TILE_PX);
            }
        }
        for (int y = 0; y <= g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                EdgeWall wall = g.edgeH[y][x];
                if (wall == null || wall.isDestroyed()) continue;
                shapes.setColor(edgeColor(wall));
                shapes.rect(OFF_X + x * TILE_PX, OFF_Y + y * TILE_PX - 3, TILE_PX, 6);
            }
        }
        shapes.end();
    }

    private Color tileColor(Tile t, boolean conduit) {
        if (t.isVoid()) return Color.CLEAR;
        if (t.isHullBreach()) return new Color(0.02f, 0.02f, 0.05f, 1f);
        if (t.bulkheadSealed) return new Color(0.75f, 0.6f, 0.15f, 1f);
        if (t.isWall()) return new Color(0.35f, 0.37f, 0.42f, 1f);
        if (t.mount != null) {
            SubsystemComponent s = t.mount.installed.getComponent(SubsystemComponent.class);
            float h = s.health;
            return new Color(0.25f + 0.5f * (1f - h), 0.3f + 0.3f * h, 0.55f * h + 0.1f, 1f);
        }
        if (t.covering != com.ringsofiron.interiorgrid.FloorCovering.NONE) return new Color(0.33f, 0.2f, 0.2f, 1f);
        if (conduit) return new Color(0.22f, 0.24f, 0.18f, 1f);
        return new Color(0.2f, 0.21f, 0.24f, 1f);
    }

    private Color edgeColor(EdgeWall w) {
        if (!w.hasBulkhead) return new Color(0.6f, 0.62f, 0.68f, 1f);
        return w.bulkheadSealed ? new Color(0.9f, 0.6f, 0.1f, 1f) : new Color(0.3f, 0.8f, 0.4f, 1f);
    }

    private void drawCrew() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : scenario.crewMembers) {
            GridPositionComponent p = scenario.crew.position(e);
            if (!p.isMoving()) continue;
            shapes.setColor(1f, 1f, 1f, 0.35f);
            float lx = sx(p.x), ly = sy(p.y);
            for (int i = p.pathIndex; i < p.path.size; i++) {
                float nx = sx(p.path.get(i).x + 0.5f), ny = sy(p.path.get(i).y + 0.5f);
                shapes.line(lx, ly, nx, ny);
                lx = nx;
                ly = ny;
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : scenario.crewMembers) {
            CrewMemberComponent c = scenario.crew.crew(e);
            GridPositionComponent p = scenario.crew.position(e);
            float x = sx(p.x), y = sy(p.y), r = TILE_PX * 0.3f;
            if (c.carriedBy != null) {
                x += r * 0.6f;
                y += r * 0.6f;
                r *= 0.7f;
            }
            if (c.state == CrewState.DEAD) {
                shapes.setColor(0.5f, 0.5f, 0.5f, 1f);
                shapes.rectLine(x - r, y - r, x + r, y + r, 3f);
                shapes.rectLine(x - r, y + r, x + r, y - r, 3f);
                continue;
            }
            Color base = c.faction == 0 ? new Color(0.35f, 0.65f, 1f, 1f) : new Color(1f, 0.3f, 0.3f, 1f);
            if (c.state == CrewState.UNCONSCIOUS) base.mul(0.5f, 0.5f, 0.5f, 1f);
            shapes.setColor(base);
            shapes.circle(x, y, r, 20);
            // Health bar
            shapes.setColor(0f, 0f, 0f, 0.8f);
            shapes.rect(x - r, y + r + 2, 2 * r, 4);
            shapes.setColor(c.healthFraction() > 0.5f ? Color.GREEN : (c.healthFraction() > 0.25f ? Color.YELLOW : Color.RED));
            shapes.rect(x - r, y + r + 2, 2 * r * c.healthFraction(), 4);
        }
        shapes.end();
    }

    private void drawText() {
        batch.begin();
        for (Entity e : scenario.crewMembers) {
            CrewMemberComponent c = scenario.crew.crew(e);
            GridPositionComponent p = scenario.crew.position(e);
            if (c.carriedBy != null) continue;
            font.setColor(Color.WHITE);
            font.draw(batch, c.name.substring(0, 1), sx(p.x) - 4, sy(p.y) + 6);
        }
        float top = WORLD_H * TILE_PX - 10;
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("AI crew demo   t=%.1fs   speed x%.0f%s", scenario.time, speed, paused ? "   PAUSED" : ""), 10, top);
        font.draw(batch, "LMB fire   RMB breach hull / wound crew   MMB boarder   D doors   Space pause   1-3 speed   R restart", 10, top - 18);
        float y = OFF_Y - 16;
        for (Entity e : scenario.crewMembers) {
            CrewMemberComponent c = scenario.crew.crew(e);
            font.setColor(c.faction == 0 ? Color.SKY : Color.SALMON);
            font.draw(batch, String.format("%-10s %-8s %3.0f hp  %-11s %s", c.name, c.role, c.health, c.state, c.activity), 10, y);
            y -= 16;
            if (y < 10) break;
        }
        float lx = OFF_X + scenario.grid.width * TILE_PX + 16;
        float ly = OFF_Y + scenario.grid.height * TILE_PX;
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Task board", lx, ly);
        ly -= 18;
        for (CrewTask t : scenario.board.tasks) {
            if (!t.isActive()) continue;
            font.draw(batch, t.toString(), lx, ly);
            ly -= 16;
        }
        ly -= 10;
        font.draw(batch, "Events", lx, ly);
        ly -= 18;
        for (String line : log) {
            font.draw(batch, line, lx, ly);
            ly -= 16;
        }
        batch.end();
    }

    private float sx(float gridX) {
        return OFF_X + gridX * TILE_PX;
    }

    private float sy(float gridY) {
        return OFF_Y + gridY * TILE_PX;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
