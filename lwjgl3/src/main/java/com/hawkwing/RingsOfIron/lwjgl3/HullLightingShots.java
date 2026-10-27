package com.hawkwing.RingsOfIron.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.render.HullShading;

import java.io.File;

/**
 * Still pictures of the hull lighting prototype ({@link HullShading}): for each hull named, the
 * hull three times side by side, lit as each {@link HullShading.Mode} lights it (the art's baked
 * light, normal maps, normal maps and roof shadows), turned {@code -Dshots.angle} degrees (30 by
 * default) under the fixed key light, saved as {@code <out>/<hull>.png}. No light map over it:
 * the art as the shader leaves it. {@code -Dshots.lights} puts point lights by each hull (body
 * meters, {@code x:y:reach:strength;...}), which shade it by its normal map against
 * {@code -Dshots.ambient}.
 *
 * <pre>gradlew :lwjgl3:hullLightingShots -Pout=build/hull-lighting -Phulls=corvette,frigate -Plights=-9:0:8:1</pre>
 */
public final class HullLightingShots extends ApplicationAdapter {
    private static final int WIDTH = 2400, HEIGHT = 900;

    private final String[] hulls;
    private final File out;
    private final float degrees = Float.parseFloat(System.getProperty("shots.angle", "30"));
    private ArtLibrary art;
    private SpriteBatch batch;
    private final HullShading shading = new HullShading();
    private final float[] lights = lights(System.getProperty("shots.lights", ""));
    private final float ambient = Float.parseFloat(System.getProperty("shots.ambient", "0.48"));
    private final float[] world = new float[lights.length];
    private int next;

    private HullLightingShots(String[] hulls, File out) {
        this.hulls = hulls;
        this.out = out;
    }

    public static void main(String[] args) {
        File out = new File(args.length > 0 ? args[0] : "hull-lighting");
        String[] hulls = (args.length > 1 ? args[1] : "corvette,frigate,gunship,picket,freighter").split(",");
        out.mkdirs();
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("Hull lighting shots");
        c.setWindowedMode(WIDTH, HEIGHT);
        c.setResizable(false);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 8, 0);
        c.disableAudio(true);
        new Lwjgl3Application(new HullLightingShots(hulls, out), c);
    }

    @Override
    public void create() {
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        batch = new SpriteBatch();
    }

    @Override
    public void render() {
        if (next >= hulls.length) {
            Gdx.app.exit();
            return;
        }
        String id = hulls[next++].trim();
        AtlasRegion hull = art.region("hulls/" + id);
        if (hull == null) {
            Gdx.app.error("Shots", "No hull art for " + id);
            return;
        }
        ArtLibrary.Info info = art.info(hull);
        float wMeters = info.w / ArtLibrary.UNITS_PER_TILE, hMeters = info.h / ArtLibrary.UNITS_PER_TILE;
        // Each of three panels fits the hull at any turn
        float span = (float) Math.hypot(wMeters, hMeters);
        float panel = span * 1.05f;
        OrthographicCamera cam = new OrthographicCamera(3f * panel, 3f * panel * HEIGHT / WIDTH);
        cam.position.set(1.5f * panel, 0f, 0f);
        cam.update();
        ScreenUtils.clear(0.03f, 0.035f, 0.05f, 1f, true);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float angle = degrees * MathUtils.degreesToRadians;
        HullShading.Mode was = HullShading.mode;
        HullShading.Mode[] modes = HullShading.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            HullShading.mode = modes[i];
            float px = (i + 0.5f) * panel;
            lightsAt(px, angle);
            if (!shading.draw(batch, art, hull, px, 0f, angle, r -> place(r, info, px, angle))) place(hull, info, px, angle);
        }
        HullShading.mode = was;
        batch.end();
        Pixmap shot = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        // The frame buffer reads bottom row first
        Pixmap flipped = new Pixmap(shot.getWidth(), shot.getHeight(), Pixmap.Format.RGBA8888);
        for (int y = 0; y < shot.getHeight(); y++) flipped.drawPixmap(shot, 0, y, shot.getWidth(), 1, 0, shot.getHeight() - 1 - y, shot.getWidth(), 1);
        PixmapIO.writePNG(Gdx.files.absolute(new File(out, id + ".png").getAbsolutePath()), flipped);
        shot.dispose();
        flipped.dispose();
    }

    /** The point lights by a hull at ({@code x}, 0) turned {@code angle}, into the world. */
    private void lightsAt(float x, float angle) {
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        for (int i = 0; i + 3 < lights.length; i += 4) {
            world[i] = x + lights[i] * cos - lights[i + 1] * sin;
            world[i + 1] = lights[i] * sin + lights[i + 1] * cos;
            world[i + 2] = lights[i + 2];
            world[i + 3] = lights[i + 3];
        }
        shading.setPointLights(world, lights.length / 4, ambient);
    }

    private static float[] lights(String spec) {
        if (spec.isBlank()) return new float[0];
        String[] each = spec.split(";");
        float[] out = new float[4 * each.length];
        for (int i = 0; i < each.length; i++) {
            String[] v = each[i].split(":");
            for (int k = 0; k < 4; k++) out[4 * i + k] = Float.parseFloat(v[k].trim());
        }
        return out;
    }

    /** A region of the hull's art with its body origin at ({@code x}, 0), turned {@code angle}, as ExteriorArt places it. */
    private void place(AtlasRegion r, ArtLibrary.Info info, float x, float angle) {
        float u = 1f / ArtLibrary.UNITS_PER_TILE;
        float left = info.x * u, top = -info.y * u, w = info.w * u, h = info.h * u, bottom = top - h;
        batch.draw(r, x + left, bottom, -left, -bottom, w, h, 1f, 1f, angle * MathUtils.radiansToDegrees);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shading.dispose();
        art.dispose();
    }
}
