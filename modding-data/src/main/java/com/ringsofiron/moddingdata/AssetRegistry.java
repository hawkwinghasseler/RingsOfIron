package com.ringsofiron.moddingdata;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps string keys to asset files so code never names a path: {@code getTexture("tex_laser_red")}.
 *
 * Keys come from {@code assets.toml} files, one table per asset kind:
 * <pre>
 * [texture]
 * tex_laser_red = "textures/laser_red.png"
 * [sound]
 * sfx_railgun = "audio/railgun.ogg"
 * [audio_event]                       # FMOD/Wwise event paths, not files
 * evt_laser = "event:/weapons/laser"
 * </pre>
 * The base game's paths are relative to the assets folder (LibGDX internal files). A mod's
 * paths are relative to its own folder and are stored as absolute paths, so a mod
 * overrides a base asset just by reusing its key.
 *
 * Kinds in {@link #LOADABLE} are loaded through a LibGDX {@link AssetManager}; any other
 * kind (like {@code audio_event}) is a plain string lookup via {@link #getString}.
 */
public class AssetRegistry {

    /** Asset kinds the AssetManager can load, by table name. */
    public static final Map<String, Class<?>> LOADABLE;

    static {
        Map<String, Class<?>> m = new LinkedHashMap<>();
        m.put("texture", Texture.class);
        m.put("atlas", TextureAtlas.class);
        m.put("sound", Sound.class);
        m.put("music", Music.class);
        m.put("font", BitmapFont.class);
        LOADABLE = Collections.unmodifiableMap(m);
    }

    public static class Entry {
        public final String key;
        public final String kind;
        /** Internal path for base assets, absolute path for mod assets, or a plain value. */
        public final String path;
        public final String source;

        Entry(String key, String kind, String path, String source) {
            this.key = key;
            this.kind = kind;
            this.path = path;
            this.source = source;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final LoadReport report;
    private AssetManager manager;

    public AssetRegistry(LoadReport report) {
        this.report = report;
    }

    /**
     * Reads an {@code assets.toml}. Missing files are skipped quietly.
     *
     * @param resolveAgainst null to keep paths as written (base game, internal files);
     *                       a mod folder to turn its relative paths into absolute ones
     */
    public void loadManifest(Path file, Path resolveAgainst, String source) {
        if (!Files.isRegularFile(file)) return;
        Map<String, Object> tree;
        try {
            tree = DataFileParser.parse(file);
        } catch (IOException | IllegalArgumentException e) {
            report.error(source, "Bad " + file.getFileName() + ": " + e.getMessage());
            return;
        }
        for (Map.Entry<String, Object> kindEntry : tree.entrySet()) {
            String kind = kindEntry.getKey();
            if (!(kindEntry.getValue() instanceof Map)) {
                report.warn(source, "assets.toml key '" + kind + "' is not a table; ignored");
                continue;
            }
            for (Map.Entry<?, ?> e : ((Map<?, ?>) kindEntry.getValue()).entrySet()) {
                String path = String.valueOf(e.getValue());
                if (resolveAgainst != null && LOADABLE.containsKey(kind)) {
                    path = resolveAgainst.resolve(path).toAbsolutePath().normalize().toString();
                }
                register(String.valueOf(e.getKey()), kind, path, source);
            }
        }
    }

    /** Adds or overrides one key. Also callable from mod scripts. */
    public void register(String key, String kind, String path, String source) {
        Entry previous = entries.get(key);
        if (previous != null && !previous.kind.equals(kind)) {
            report.warn(source, "Asset key '" + key + "' was a " + previous.kind + " from " + previous.source
                    + "; now a " + kind);
        }
        entries.put(key, new Entry(key, kind, path, source));
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public Entry entry(String key) {
        return entries.get(key);
    }

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /** The file path (or plain value) for a key. */
    public String path(String key) {
        return require(key).path;
    }

    /** A non-file value such as an audio middleware event path. */
    public String getString(String key) {
        return require(key).path;
    }

    private Entry require(String key) {
        Entry e = entries.get(key);
        if (e == null) throw new IllegalArgumentException("Unknown asset key '" + key + "'");
        return e;
    }

    /**
     * A LibGDX AssetManager whose resolver reads absolute paths (mod assets) from disk
     * and everything else as internal files. Needs a running LibGDX application.
     */
    public static AssetManager createAssetManager() {
        FileHandleResolver resolver = fileName -> new File(fileName).isAbsolute()
                ? Gdx.files.absolute(fileName)
                : Gdx.files.internal(fileName);
        return new AssetManager(resolver);
    }

    /** Queues every loadable asset on the manager; call {@code manager.update()} or {@code finishLoading()} after. */
    public void queueAll(AssetManager manager) {
        this.manager = manager;
        for (Entry e : entries.values()) {
            Class<?> type = LOADABLE.get(e.kind);
            if (type != null) manager.load(e.path, type);
        }
    }

    public void setAssetManager(AssetManager manager) {
        this.manager = manager;
    }

    public <T> T get(String key, Class<T> type) {
        if (manager == null) throw new IllegalStateException("AssetRegistry has no AssetManager; call queueAll first");
        Entry e = require(key);
        if (!manager.isLoaded(e.path, type)) {
            manager.load(e.path, type);
            manager.finishLoadingAsset(e.path);
        }
        return manager.get(e.path, type);
    }

    public Texture getTexture(String key) {
        return get(key, Texture.class);
    }

    public Sound getSound(String key) {
        return get(key, Sound.class);
    }

    public Music getMusic(String key) {
        return get(key, Music.class);
    }

    public TextureAtlas getAtlas(String key) {
        return get(key, TextureAtlas.class);
    }

    /** Keys per kind, for tools and debug overlays. */
    public Map<String, Integer> countsByKind() {
        Map<String, Integer> counts = new HashMap<>();
        for (Entry e : entries.values()) counts.merge(e.kind, 1, Integer::sum);
        return counts;
    }
}
