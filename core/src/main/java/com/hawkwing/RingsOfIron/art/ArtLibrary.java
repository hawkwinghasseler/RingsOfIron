package com.hawkwing.RingsOfIron.art;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.moddingdata.AssetRegistry;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The art from {@code assets/art}, rasterized into one texture atlas by the art pipeline
 * ({@code gradlew :art-pipeline:packArt}; the game's run tasks build it). A region is named by its
 * folder and file name, {@code tiles/floor} or {@code components/reactor_xl}, so code finds the art
 * for a data id without a table (see documentation/art/Design.md). The emissive layer of a file
 * that has one is the region {@code glow/<name>}.
 *
 * With no atlas (the asset key is missing or the file was never built) every lookup is null, and
 * callers draw their flat shapes instead.
 */
public final class ArtLibrary implements Disposable {
    /** The {@code assets.toml} key of the atlas. */
    public static final String ATLAS_KEY = "atlas_art";
    /** Where the base game's atlas is, as an internal file. */
    public static final String DEFAULT_PATH = "art/art.atlas";
    /** SVG units in one tile. */
    public static final float UNITS_PER_TILE = 64f;

    /** Where a region's art sits, in SVG units: its viewBox, and its meta shapes. */
    public static final class Info {
        /** viewBox origin and size: the SVG point (x, y) is the region's top-left corner. */
        public final float x, y, w, h;
        /** Collider as {x, y, w, h} (a rect) or {cx, cy, r} (a circle), or null. */
        public final float[] collider;
        /** Where crew stand or reach to use the object, {cx, cy}, or null. */
        public final float[] interact;

        Info(float x, float y, float w, float h, float[] collider, float[] interact) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.collider = collider;
            this.interact = interact;
        }
    }

    private static final ArtLibrary NONE = new ArtLibrary(null, new HashMap<>());

    private final TextureAtlas atlas;
    private final Map<String, Info> infos;
    /** Lookups so far, misses included (as null). */
    private final Map<String, AtlasRegion> regions = new HashMap<>();

    private ArtLibrary(TextureAtlas atlas, Map<String, Info> infos) {
        this.atlas = atlas;
        this.infos = infos;
    }

    /** A library with no art: every lookup is null. */
    public static ArtLibrary none() {
        return NONE;
    }

    /**
     * Loads the atlas that {@code assets} names under {@link #ATLAS_KEY}, or returns {@link #none()}
     * (and logs why) when there is none. Needs a running LibGDX application.
     */
    public static ArtLibrary load(AssetRegistry assets) {
        if (assets == null || !assets.has(ATLAS_KEY)) return NONE;
        return load(assets.path(ATLAS_KEY));
    }

    /** Loads the atlas at {@code path}: absolute, or an internal file such as {@link #DEFAULT_PATH}. */
    public static ArtLibrary load(String path) {
        FileHandle file = new File(path).isAbsolute() ? Gdx.files.absolute(path) : Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("Art", "No art atlas at " + path + " (gradlew :art-pipeline:packArt builds it); drawing shapes");
            return NONE;
        }
        try {
            TextureAtlas atlas = new TextureAtlas(file);
            FileHandle index = file.sibling(file.nameWithoutExtension() + ".json");
            return new ArtLibrary(atlas, index.exists() ? readIndex(index) : new HashMap<>());
        } catch (GdxRuntimeException e) {
            Gdx.app.error("Art", "Could not load the art atlas " + path + "; drawing shapes", e);
            return NONE;
        }
    }

    private static Map<String, Info> readIndex(FileHandle file) {
        Map<String, Info> out = new HashMap<>();
        JsonValue regions = new JsonReader().parse(file).get("regions");
        for (JsonValue r = regions == null ? null : regions.child; r != null; r = r.next) {
            JsonValue c = r.get("collider");
            float[] collider = null;
            if (c != null) collider = (c.has("rect") ? c.get("rect") : c.get("circle")).asFloatArray();
            JsonValue i = r.get("interact");
            out.put(r.name, new Info(r.getFloat("x"), r.getFloat("y"), r.getFloat("w"), r.getFloat("h"),
                    collider, i == null ? null : i.asFloatArray()));
        }
        return out;
    }

    public boolean isEmpty() {
        return atlas == null;
    }

    /** The region named {@code name} ({@code walls/hull_heavy_ns}), or null. */
    public AtlasRegion region(String name) {
        if (atlas == null) return null;
        AtlasRegion r = regions.get(name);
        if (r == null && !regions.containsKey(name)) {
            r = atlas.findRegion(name);
            regions.put(name, r);
        }
        return r;
    }

    /** The first of {@code names} that has art, or null. */
    public AtlasRegion first(String... names) {
        for (String n : names) {
            AtlasRegion r = region(n);
            if (r != null) return r;
        }
        return null;
    }

    /** The viewBox and meta shapes of a region, or null. */
    public Info info(String name) {
        return infos.get(name);
    }

    /** {@link #info} for a region found by {@link #region}. */
    public Info info(AtlasRegion region) {
        return region == null ? null : infos.get(region.name);
    }

    @Override
    public void dispose() {
        if (atlas != null) atlas.dispose();
    }
}
