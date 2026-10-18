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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.ringsofiron.audiospatial.AudioEmitters;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.backend.SynthAudioBackend;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

import java.util.Locale;

/**
 * Interactive demo for the audio-spatial module: walk a small ship and hear sounds muffle as
 * walls and doors come between you and them.
 *
 * Sounds are procedural placeholders (reactor hum, alarm, fire, breach hiss) played with real
 * distance attenuation, stereo panning and a low-pass filter driven by the wall count. Use
 * headphones to hear the panning.
 *
 * Controls:
 *   W A S D      - Walk
 *   E            - Open / seal the nearest door
 *   TAB          - Switch between interior (walking) and exterior (piloting) listening
 *   SPACE        - Fire a laser (exterior sound)
 *   H            - Something hits the hull (exterior sound, heard through the frame)
 *   1 - 4        - Turn each sound on or off
 *   ESC          - Exit
 */
public class AudioDemo extends ApplicationAdapter {

    private static final int GRID_W = 26, GRID_H = 15;
    private static final float WALK_SPEED = 4f; // m/s

    private Engine engine;
    private AudioManager audio;
    private Entity ship;
    private ShipGridComponent grid;
    private AudioListenerComponent listener;
    private final Array<AudioEmitterComponent> sounds = new Array<>();
    private final Array<String> soundNames = new Array<>();
    private final Array<EdgeWall> doors = new Array<>();
    private final Array<Vector2> doorCenters = new Array<>();

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private final Vector2 tmp = new Vector2();
    private final Vector3 tmp3 = new Vector3();

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(GRID_W + 4, GRID_H + 8, camera);
        hudCamera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        audio = AudioManager.createDefault();
        engine = new Engine();
        engine.addSystem(new AcousticOcclusionSystem());
        engine.addSystem(new AudioUpdateSystem(audio));

        buildShip();

        listener = new AudioListenerComponent();
        listener.ship = ship;
        listener.mode = AudioListenerComponent.Mode.INTERIOR;
        tileCenter(12, 4, tmp);
        listener.localX = tmp.x;
        listener.localY = tmp.y;
        ship.add(listener); // in exterior mode the listener is the ship's center

        addSound("Reactor hum", AudioEventCatalog.REACTOR_HUM, 4, 7);
        addSound("Alarm", AudioEventCatalog.ALARM, 21, 11);
        addSound("Fire", AudioEventCatalog.FIRE, 12, 11);
        addSound("Breach hiss", AudioEventCatalog.BREACH_HISS, 21, 3);
    }

    /**
     * A hull around three rooms side by side; the middle one is split in two. Every internal
     * wall has a door, all open to start with.
     */
    private void buildShip() {
        grid = new ShipGridComponent(GRID_W, GRID_H, 1f, -GRID_W / 2f, -GRID_H / 2f);
        for (int y = 0; y < GRID_H; y++) {
            for (int x = 0; x < GRID_W; x++) {
                Tile t = grid.getTile(x, y);
                if (x == 0 || y == 0 || x == GRID_W - 1 || y == GRID_H - 1) {
                    t.wallType = Tile.WallType.HULL;
                } else {
                    t.floorType = Tile.FloorType.FLOOR;
                }
            }
        }
        // Engine room | middle | bridge: walls on the right edge of columns 8 and 16
        for (int col : new int[]{8, 16}) {
            for (int y = 1; y < GRID_H - 1; y++) {
                boolean door = y == GRID_H / 2;
                EdgeWall w = new EdgeWall(WeightClass.MEDIUM, "steel", door);
                grid.setEdge(col, y, Side.RIGHT, w);
                if (door) addDoor(w, col + 1f, y + 0.5f);
            }
        }
        // The middle room split into upper and lower halves
        for (int x = 9; x <= 16; x++) {
            boolean door = x == 12;
            EdgeWall w = new EdgeWall(WeightClass.MEDIUM, "steel", door);
            grid.setEdge(x, 7, Side.TOP, w);
            if (door) addDoor(w, x + 0.5f, 8f);
        }
        ship = new Entity();
        ship.add(new TransformComponent(0f, 0f, 0f));
        ship.add(grid);
        engine.addEntity(ship);
    }

    private void addDoor(EdgeWall wall, float gridX, float gridY) {
        doors.add(wall);
        doorCenters.add(new Vector2(grid.gridOriginLocal.x + gridX, grid.gridOriginLocal.y + gridY));
    }

    private Vector2 tileCenter(int x, int y, Vector2 out) {
        return out.set(grid.tileToLocal(x, y));
    }

    private void addSound(String name, String path, int tx, int ty) {
        tileCenter(tx, ty, tmp);
        Entity e = engine.createEntity();
        AudioEmitterComponent em = AudioEmitterComponent.interior(path, ship, tmp.x, tmp.y);
        e.add(em);
        engine.addEntity(e);
        sounds.add(em);
        soundNames.add(name);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        handleInput(dt);
        engine.update(dt);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        camera.position.set(0f, 2f, 0f); // ship low, HUD text above it
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        drawShip();
        drawSounds();
        drawLabels();
        drawHud();
    }

    private void handleInput(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            listener.mode = listener.mode == AudioListenerComponent.Mode.INTERIOR
                    ? AudioListenerComponent.Mode.EXTERIOR : AudioListenerComponent.Mode.INTERIOR;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            audio.playOneShot(AudioEventCatalog.LASER, GRID_W / 2f + 2f, 0f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            float a = MathUtils.random(MathUtils.PI2);
            audio.playOneShot(AudioEventCatalog.HULL_IMPACT, MathUtils.cos(a) * GRID_W / 2f, MathUtils.sin(a) * GRID_H / 2f,
                    MathUtils.random(0.4f, 1f));
        }
        for (int i = 0; i < sounds.size; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) toggleSound(sounds.get(i));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) toggleNearestDoor();

        if (listener.mode != AudioListenerComponent.Mode.INTERIOR) return;
        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len == 0f) return;
        float step = WALK_SPEED * dt / len;
        tryMove(mx * step, 0f);
        tryMove(0f, my * step);
    }

    /** Moves the player unless it would cross a wall or a sealed door. */
    private void tryMove(float dx, float dy) {
        int[] from = grid.localToTile(listener.localX, listener.localY);
        float nx = listener.localX + dx, ny = listener.localY + dy;
        // Keep a little clearance from the tile edge in the direction of travel
        int[] to = grid.localToTile(nx + Math.signum(dx) * 0.3f, ny + Math.signum(dy) * 0.3f);
        if (to[0] < 0) return;
        if ((to[0] != from[0] || to[1] != from[1]) && !grid.canPass(from[0], from[1], to[0], to[1])) return;
        listener.localX = nx;
        listener.localY = ny;
    }

    private void toggleSound(AudioEmitterComponent em) {
        if (em.instance != null && em.instance.isPlaying()) {
            em.instance.stop();
            em.autoStart = false;
        } else {
            em.play();
        }
    }

    private void toggleNearestDoor() {
        int best = -1;
        float bestDist = 2f;
        for (int i = 0; i < doors.size; i++) {
            float d = doorCenters.get(i).dst(listener.localX, listener.localY);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        if (best < 0) return;
        EdgeWall door = doors.get(best);
        door.bulkheadSealed = !door.bulkheadSealed;
        Vector2 c = doorCenters.get(best);
        AudioEmitters.spawnInteriorOneShot(engine, AudioEventCatalog.DOOR, ship, c.x, c.y, 1f);
    }

    private void drawShip() {
        float ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < GRID_H; y++) {
            for (int x = 0; x < GRID_W; x++) {
                Tile t = grid.getTile(x, y);
                if (t.isWall()) shapes.setColor(0.35f, 0.38f, 0.45f, 1f);
                else if (t.isFloor()) shapes.setColor(0.10f, 0.11f, 0.14f, 1f);
                else continue;
                shapes.rect(ox + x, oy + y, 0.96f, 0.96f);
            }
        }
        // Edge walls: grey wall, green open door, red sealed door
        for (int y = 0; y <= GRID_H; y++) {
            for (int x = 0; x < GRID_W; x++) {
                EdgeWall e = grid.edgeH[y][x];
                if (e != null) {
                    setEdgeColor(e);
                    shapes.rectLine(ox + x, oy + y, ox + x + 1f, oy + y, 0.18f);
                }
            }
        }
        for (int y = 0; y < GRID_H; y++) {
            for (int x = 0; x <= GRID_W; x++) {
                EdgeWall e = grid.edgeV[y][x];
                if (e != null) {
                    setEdgeColor(e);
                    shapes.rectLine(ox + x, oy + y, ox + x, oy + y + 1f, 0.18f);
                }
            }
        }
        shapes.end();
    }

    private void setEdgeColor(EdgeWall e) {
        if (!e.hasBulkhead) shapes.setColor(0.55f, 0.58f, 0.65f, 1f);
        else if (e.bulkheadSealed) shapes.setColor(0.9f, 0.25f, 0.2f, 1f);
        else shapes.setColor(0.3f, 0.85f, 0.4f, 1f);
    }

    private void drawSounds() {
        boolean inside = listener.mode == AudioListenerComponent.Mode.INTERIOR;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (AudioEmitterComponent em : sounds) {
            if (!playing(em) || !inside) continue;
            soundColor(em);
            shapes.line(em.localX, em.localY, listener.localX, listener.localY);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (AudioEmitterComponent em : sounds) {
            if (playing(em)) soundColor(em);
            else shapes.setColor(Color.DARK_GRAY);
            float pulse = playing(em) ? 0.15f * MathUtils.sin(Gdx.graphics.getFrameId() * 0.2f) : 0f;
            shapes.circle(em.localX, em.localY, 0.45f + pulse, 20);
        }
        // The listener: the player on the deck, or the ship's center when piloting
        shapes.setColor(Color.WHITE);
        if (inside) shapes.circle(listener.localX, listener.localY, 0.35f, 20);
        else shapes.circle(0f, 0f, 0.6f, 4);
        shapes.end();
    }

    private static boolean playing(AudioEmitterComponent em) {
        return em.instance != null && em.instance.isPlaying();
    }

    /** Green when clear, through amber, to red when fully muffled. */
    private void soundColor(AudioEmitterComponent em) {
        float o = em.occlusion;
        shapes.setColor(Math.min(1f, 0.2f + 1.6f * o), Math.min(1f, 1.6f * (1f - o)), 0.2f, 1f);
    }

    private void drawLabels() {
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        for (int i = 0; i < sounds.size; i++) {
            AudioEmitterComponent em = sounds.get(i);
            tmp3.set(em.localX, em.localY + 0.9f, 0f);
            viewport.project(tmp3);
            font.setColor(Color.WHITE);
            font.draw(batch, (i + 1) + " " + soundNames.get(i), tmp3.x - 30f, tmp3.y + 30f);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, describe(em), tmp3.x - 30f, tmp3.y + 14f);
        }
        batch.end();
    }

    private String describe(AudioEmitterComponent em) {
        if (!playing(em)) return "off";
        String walls = em.wallCount == 1 ? "1 wall" : em.wallCount + " walls";
        if (!(em.instance instanceof SimulatedInstance)) return walls;
        SimulatedInstance s = (SimulatedInstance) em.instance;
        return String.format(Locale.ROOT, "%s  %5.0f Hz  vol %3.0f%%", walls, s.mix.cutoffHz, 100f * s.mix.gain);
    }

    private void drawHud() {
        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        float x = 10f, y = hudCamera.viewportHeight - 10f, line = 18f;
        font.setColor(Color.WHITE);
        font.draw(batch, "=== Spatial Audio Demo ===", x, y);
        y -= line * 1.5f;
        boolean inside = listener.mode == AudioListenerComponent.Mode.INTERIOR;
        font.setColor(inside ? Color.LIME : Color.ORANGE);
        font.draw(batch, inside ? "Listening: INTERIOR (on the deck)" : "Listening: EXTERIOR (piloting, interior sounds clamped)", x, y);
        y -= line;
        font.setColor(Color.LIGHT_GRAY);
        boolean silent = !(audio.getBackend() instanceof SynthAudioBackend);
        font.draw(batch, silent ? "Backend: headless (no sound device found)" : "Backend: procedural synth", x, y);
        y -= line * 1.5f;
        font.draw(batch, "WASD walk   E open/seal door   TAB interior/exterior", x, y);
        y -= line;
        font.draw(batch, "SPACE laser   H hull impact   1-4 sounds on/off   ESC exit", x, y);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        hudCamera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        engine.removeAllEntities();
        audio.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
