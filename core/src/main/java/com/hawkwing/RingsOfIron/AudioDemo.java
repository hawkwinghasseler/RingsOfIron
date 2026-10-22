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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.game.AudioDemoScene;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.ringsofiron.audiospatial.backend.AudioBackend;
import com.ringsofiron.audiospatial.backend.OpenAlAudioBackend;
import com.ringsofiron.audiospatial.backend.SimulatedInstance;
import com.ringsofiron.audiospatial.backend.SynthAudioBackend;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Interactive demo for the audio-spatial module: walk the game's corvette as your crew avatar and
 * hear sounds muffle as walls and doors come between you and them.
 *
 * <p>The world is the game's ({@link AudioDemoScene}): its hull, crew and audio systems, so the
 * crew's footsteps and voices and the doors' sounds are the game's own, and the deck and crew are
 * drawn as the game draws them ({@link DeckRenderer}). On top, four sounds placed by hand (reactor
 * hum, alarm, fire, breach hiss) can be switched on and off, each marked green when heard clear
 * through amber to red when muffled, with its wall count, cutoff and volume beside it. Use
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
    /** Meters of margin round the deck. */
    private static final float MARGIN = 3f;

    private final Path assetsDir;
    private AudioDemoScene demo;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private ArtLibrary art;
    private DeckArt deckArt;
    private DeckRenderer deckRenderer;
    private final Array<Entity> decks = new Array<>();
    private String message = "";
    private final Vector2 tmp = new Vector2();
    private final Vector3 tmp3 = new Vector3();

    public AudioDemo() {
        this(Paths.get("").toAbsolutePath());
    }

    public AudioDemo(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        deckArt = new DeckArt(art);
        demo = new AudioDemoScene(assetsDir);
        demo.load();
        deckRenderer = new DeckRenderer(() -> demo.world, shapes, batch, deckArt, null, () -> demo.avatar).inShipFrame();

        ShipGridComponent grid = demo.grid();
        camera = new OrthographicCamera();
        // The deck a little low in the window, the help above it
        viewport = new ExtendViewport(grid.width * grid.tileSize + 2 * MARGIN, grid.height * grid.tileSize + 2 * MARGIN + 2f, camera);
        hudCamera = new OrthographicCamera();
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        handleInput();
        demo.update(dt);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        ShipGridComponent grid = demo.grid();
        float cx = grid.gridOriginLocal.x + grid.width * grid.tileSize / 2f;
        float cy = grid.gridOriginLocal.y + grid.height * grid.tileSize / 2f;
        camera.position.set(cx, cy + 1f, 0f);
        camera.update();
        decks.clear();
        decks.add(demo.ship);
        deckRenderer.draw(decks, camera, dt);
        drawSounds();
        drawLabels();
        drawHud();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) demo.toggleListening();
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) demo.laser();
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) demo.hullHit();
        for (int i = 0; i < demo.sources.size(); i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) demo.toggleSource(i);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) message = demo.toggleNearestDoor();

        float sx = 0f, sy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) sy += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) sy -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) sx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) sx += 1f;
        demo.walk(sx, sy);
    }

    /** Each sound's disc, and in interior listening a line from it to the avatar, colored by how muffled it is. */
    private void drawSounds() {
        boolean inside = demo.listeningInside();
        Vector2 you = demo.avatarLocal(tmp);
        float r = demo.grid().tileSize;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (AudioDemoScene.Source s : demo.sources) {
            if (!s.playing() || !inside || you == null) continue;
            soundColor(s.emitter, 0.8f);
            shapes.line(s.emitter.localX, s.emitter.localY, you.x, you.y);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (AudioDemoScene.Source s : demo.sources) {
            if (s.playing()) soundColor(s.emitter, 0.55f);
            else shapes.setColor(0.4f, 0.4f, 0.4f, 0.55f);
            float pulse = s.playing() ? 0.15f * MathUtils.sin(Gdx.graphics.getFrameId() * 0.2f) : 0f;
            shapes.circle(s.emitter.localX, s.emitter.localY, (0.45f + pulse) * r, 20);
        }
        // Piloting, the listener is the ship's middle
        if (!inside) {
            shapes.setColor(Color.WHITE);
            ShipGridComponent g = demo.grid();
            shapes.circle(g.gridOriginLocal.x + g.width * g.tileSize / 2f, g.gridOriginLocal.y + g.height * g.tileSize / 2f, 0.6f * r, 4);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Green when clear, through amber, to red when fully muffled. */
    private void soundColor(AudioEmitterComponent em, float alpha) {
        float o = em.occlusion;
        shapes.setColor(Math.min(1f, 0.2f + 1.6f * o), Math.min(1f, 1.6f * (1f - o)), 0.2f, alpha);
    }

    private void drawLabels() {
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        for (int i = 0; i < demo.sources.size(); i++) {
            AudioDemoScene.Source s = demo.sources.get(i);
            tmp3.set(s.emitter.localX, s.emitter.localY + 0.3f * demo.grid().tileSize, 0f);
            viewport.project(tmp3);
            font.setColor(Color.WHITE);
            font.draw(batch, (i + 1) + " " + s.name, tmp3.x - 30f, tmp3.y + 46f);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, describe(s), tmp3.x - 30f, tmp3.y + 30f);
        }
        batch.end();
    }

    private String describe(AudioDemoScene.Source s) {
        if (!s.playing()) return "off";
        AudioEmitterComponent em = s.emitter;
        String walls = em.wallCount == 1 ? "1 wall" : em.wallCount + " walls";
        if (!demo.listeningInside()) walls = "outside";
        else if (em.roomLossDb > 0f && !Float.isInfinite(em.roomLossDb)) walls += String.format(Locale.ROOT, ", -%.0f dB", em.roomLossDb);
        if (!(em.instance instanceof SimulatedInstance)) return walls;
        SimulatedInstance sim = (SimulatedInstance) em.instance;
        return String.format(Locale.ROOT, "%s\n%.0f Hz  vol %.1f%%", walls, sim.mix.cutoffHz, 100f * sim.mix.gain);
    }

    private void drawHud() {
        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        float x = 10f, y = hudCamera.viewportHeight - 10f, line = 18f;
        font.setColor(Color.WHITE);
        font.draw(batch, "=== Spatial Audio Demo ===", x, y);
        y -= line * 1.5f;
        boolean inside = demo.listeningInside();
        font.setColor(inside ? Color.LIME : Color.ORANGE);
        font.draw(batch, inside ? "Listening: INTERIOR (on the deck)" : "Listening: EXTERIOR (piloting, interior sounds clamped)", x, y);
        y -= line;
        font.setColor(Color.LIGHT_GRAY);
        AudioBackend backend = demo.world.audio.getBackend();
        font.draw(batch, backend instanceof OpenAlAudioBackend ? "Backend: OpenAL Soft"
                : backend instanceof SynthAudioBackend ? "Backend: procedural synth"
                : "Backend: headless (no sound device found)", x, y);
        y -= line * 1.5f;
        font.draw(batch, "WASD walk   E open/seal door   TAB interior/exterior", x, y);
        y -= line;
        font.draw(batch, "SPACE laser   H hull impact   1-4 sounds on/off   ESC exit", x, y);
        if (!message.isEmpty()) {
            y -= line * 1.5f;
            font.setColor(Color.WHITE);
            font.draw(batch, message, x, y);
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        hudCamera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        demo.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (art != null) art.dispose();
    }
}
