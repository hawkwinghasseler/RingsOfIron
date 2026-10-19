package com.ringsofiron.interiorgrid.editor;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.DefEntry;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.LoadReport;
import com.ringsofiron.moddingdata.ModDiscovery;
import com.ringsofiron.moddingdata.ModManifest;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.moddingdata.TomlDataLoader;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the ship editor edits: every chassis layout, tile type and ship component, where
 * each came from, and what has changed. Loads base data (and mods, read-only) the way the game
 * does, and saves changed definitions back into their files with {@link TomlSections}.
 */
public class EditorProject {

    /** The three kinds of definition the editor edits. */
    public enum Kind {
        CHASSIS("chassis", "chassis.toml"),
        TILE("tile", "tiles.toml"),
        COMPONENT("component", "components.toml");

        /** The definition type, as in {@code [chassis.id]}. */
        public final String type;
        /** Where a new one is written, under the data folder. */
        public final String defaultFile;

        Kind(String type, String defaultFile) {
            this.type = type;
            this.defaultFile = defaultFile;
        }
    }

    /** One definition being edited. */
    public static class Item<T> {
        public final Kind kind;
        public final String id;
        public T value;
        /** The base data file its section is in, or null if it is in none yet. */
        public Path file;
        /** From a mod: shown and usable, but never saved. */
        public boolean readOnly;
        /** Where it came from, for the panel, e.g. "base:chassis.toml". */
        public String source;
        public boolean dirty;

        Item(Kind kind, String id, T value) {
            this.kind = kind;
            this.id = id;
            this.value = value;
        }
    }

    /** What the game would build from a layout. */
    public static class Check {
        /** Null when the layout fails to load; see {@link #error}. */
        public ChassisBlueprintLoader.LoadResult result;
        public String error;
        /** The tile the error names, or -1. */
        public int errorX = -1, errorY = -1;
        public int floorTiles, wallTiles, rooms, systems, poweredSystems;
        public double cargoVolume;
    }

    private static final Pattern TILE_IN_MESSAGE = Pattern.compile("(-?\\d+),(-?\\d+)");

    public final Path dataDir;
    public final Map<String, Item<ChassisModel>> chassis = new LinkedHashMap<>();
    public final Map<String, Item<TileTypeDef>> tiles = new LinkedHashMap<>();
    public final Map<String, Item<ShipComponentDef>> components = new LinkedHashMap<>();
    /** Sections to remove on the next save. */
    private final List<Item<?>> deleted = new ArrayList<>();
    /** Problems loading the data, and symbol clashes in the current catalog. */
    public final List<String> problems = new ArrayList<>();

    private ShipPartCatalog catalog;

    private EditorProject(Path dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Loads {@code assetsDir/data} and, read-only, every enabled mod's data in {@code modsDir}
     * (which may be null or missing).
     */
    public static EditorProject load(Path assetsDir, Path modsDir) {
        EditorProject p = new EditorProject(assetsDir.resolve("data"));
        LoadReport report = new LoadReport();
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        TomlDataLoader loader = new TomlDataLoader(defs, report);
        loader.loadDirectory(p.dataDir, "base", false);
        if (modsDir != null && Files.isDirectory(modsDir)) {
            for (ModManifest mod : new ModDiscovery(report).discover(modsDir)) {
                if (mod.enabled) loader.loadDirectory(mod.directory.resolve("data"), mod.id, false);
            }
        }
        for (LoadReport.Entry e : report.entries()) {
            if (e.severity != LoadReport.Severity.INFO) p.problems.add(e.toString());
        }

        ShipPartCatalog builtIn = ShipPartCatalog.builtIn();
        for (ShipPartCatalog.TileType t : builtIn.tiles()) {
            p.tiles.put(t.id, new Item<>(Kind.TILE, t.id, ShipPartCatalog.toDef(t)));
        }
        for (ShipPartCatalog.ComponentType t : builtIn.components()) {
            p.components.put(t.id, new Item<>(Kind.COMPONENT, t.id, ShipPartCatalog.toDef(t)));
        }
        for (DefEntry e : defs.entries("tile")) {
            p.tiles.put(e.id, p.item(Kind.TILE, e, convert(defs, e, TileTypeDef.class, p)));
        }
        for (DefEntry e : defs.entries("component")) {
            p.components.put(e.id, p.item(Kind.COMPONENT, e, convert(defs, e, ShipComponentDef.class, p)));
        }
        p.tiles.values().removeIf(i -> i.value == null);
        p.components.values().removeIf(i -> i.value == null);
        ShipPartCatalog catalog = p.catalog();
        for (DefEntry e : defs.entries("chassis")) {
            ChassisDef def = convert(defs, e, ChassisDef.class, p);
            if (def == null) continue;
            p.chassis.put(e.id, p.item(Kind.CHASSIS, e, ChassisModel.fromDef(def, catalog)));
        }
        return p;
    }

    private static <T> T convert(DefRegistry defs, DefEntry e, Class<T> cls, EditorProject p) {
        try {
            return defs.convert(e, cls);
        } catch (IllegalArgumentException ex) {
            p.problems.add(ex.getMessage());
            return null;
        }
    }

    private <T> Item<T> item(Kind kind, DefEntry e, T value) {
        Item<T> item = new Item<>(kind, e.id, value);
        item.source = String.join(", ", e.sources());
        for (String s : e.sources()) {
            if (s.startsWith("base:")) {
                if (item.file == null) item.file = dataDir.resolve(s.substring("base:".length()));
            } else {
                item.readOnly = true;
            }
        }
        if (item.file == null) item.readOnly = true;
        return item;
    }

    // ---- Catalog ----

    /** The tile types and components as they stand now. Symbol clashes go to {@link #problems}. */
    public ShipPartCatalog catalog() {
        if (catalog == null) {
            List<ShipPartCatalog.TileType> t = new ArrayList<>();
            List<ShipPartCatalog.ComponentType> c = new ArrayList<>();
            List<String> clashes = new ArrayList<>();
            for (Item<TileTypeDef> i : tiles.values()) {
                try {
                    t.add(ShipPartCatalog.toTileType(i.value));
                } catch (IllegalArgumentException e) {
                    clashes.add(e.getMessage());
                }
            }
            for (Item<ShipComponentDef> i : components.values()) {
                try {
                    c.add(ShipPartCatalog.toComponentType(i.value));
                } catch (IllegalArgumentException e) {
                    clashes.add(e.getMessage());
                }
            }
            catalog = ShipPartCatalog.of(t, c, clashes);
            catalogProblems = clashes;
        }
        return catalog;
    }

    private List<String> catalogProblems = new ArrayList<>();

    /** Problems with the tile types and components as they stand: bad fields and symbols used twice. */
    public List<String> catalogProblems() {
        catalog();
        return catalogProblems;
    }

    /** Call after changing a tile type or component, so the catalog is rebuilt. */
    public void partsChanged() {
        catalog = null;
    }

    // ---- Checking ----

    /** Builds a layout through the game's loader, as the game would. */
    public Check check(ChassisModel model) {
        Check c = new Check();
        ShipPartCatalog cat = catalog();
        try {
            ChassisDef def = model.toDef(cat);
            c.result = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(def, cat));
        } catch (RuntimeException e) {
            c.error = e.getMessage() != null ? e.getMessage() : e.toString();
            Matcher m = TILE_IN_MESSAGE.matcher(c.error);
            if (m.find()) {
                c.errorX = Integer.parseInt(m.group(1));
                c.errorY = Integer.parseInt(m.group(2));
            }
            return c;
        }
        ShipGridComponent grid = c.result.grid;
        CompartmentBuilder.rebuild(grid);
        c.rooms = grid.compartments.size;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).isFloor()) c.floorTiles++;
                else if (grid.getTile(x, y).isWall()) c.wallTiles++;
            }
        }
        c.cargoVolume = CargoHoldComponent.bayVolume(c.result);
        c.systems = c.result.subsystems.size;

        // Route power once on a throwaway ship to see which systems a reactor reaches
        Entity ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(grid);
        ship.add(registry);
        List<SubsystemComponent> subs = new ArrayList<>();
        for (ChassisBlueprintLoader.SubsystemPlacement p : c.result.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            registry.registerSubsystem(e);
            subs.add(sub);
        }
        new PowerRoutingSystem().route(ship);
        for (SubsystemComponent s : subs) if (s.powered) c.poweredSystems++;
        return c;
    }

    // ---- Edits ----

    /** True if {@code id} is a usable definition id: lowercase letters, digits and underscores. */
    public static boolean validId(String id) {
        return id != null && id.matches("[a-z][a-z0-9_]*");
    }

    /** {@code base}, or {@code base_2}, {@code base_3}... whichever no definition of that kind has. */
    public String freeId(Kind kind, String base) {
        Map<String, ? extends Item<?>> items = items(kind);
        if (!items.containsKey(base)) return base;
        for (int n = 2; ; n++) if (!items.containsKey(base + "_" + n)) return base + "_" + n;
    }

    public Map<String, ? extends Item<?>> items(Kind kind) {
        switch (kind) {
            case CHASSIS: return chassis;
            case TILE: return tiles;
            default: return components;
        }
    }

    public Item<ChassisModel> addChassis(ChassisModel model) {
        Item<ChassisModel> item = new Item<>(Kind.CHASSIS, model.id, model);
        item.dirty = true;
        chassis.put(model.id, item);
        return item;
    }

    public Item<TileTypeDef> addTile(TileTypeDef def) {
        Item<TileTypeDef> item = new Item<>(Kind.TILE, def.id, def);
        item.dirty = true;
        tiles.put(def.id, item);
        partsChanged();
        return item;
    }

    public Item<ShipComponentDef> addComponent(ShipComponentDef def) {
        Item<ShipComponentDef> item = new Item<>(Kind.COMPONENT, def.id, def);
        item.dirty = true;
        components.put(def.id, item);
        partsChanged();
        return item;
    }

    /** A character no tile type or component uses yet, preferring {@code preferred}. */
    public char freeSymbol(String preferred) {
        ShipPartCatalog cat = catalog();
        for (char c : (preferred + "abcdefghijklmnopqrstuvwxyzBEFGJKMNOPQUVXYZ0456789*%&$@!?~^=-_:;<>").toCharArray()) {
            if (cat.tileFor(c) == null && cat.componentFor(c) == null && symbolFree(c)) return c;
        }
        return '?';
    }

    private boolean symbolFree(char c) {
        String s = String.valueOf(c);
        for (Item<TileTypeDef> i : tiles.values()) if (s.equals(i.value.symbol)) return false;
        for (Item<ShipComponentDef> i : components.values()) if (s.equals(i.value.symbol)) return false;
        return true;
    }

    /**
     * Changes a tile type's symbol and redraws every editable layout that uses it.
     *
     * @return null if done, otherwise why not
     */
    public String setTileSymbol(Item<TileTypeDef> item, char symbol) {
        if (item.readOnly) return item.id + " comes from a mod";
        if (item.value.symbol != null && item.value.symbol.length() == 1 && item.value.symbol.charAt(0) == symbol) return null;
        if (!symbolFree(symbol)) return "'" + symbol + "' is already used";
        char old = item.value.symbol != null && item.value.symbol.length() == 1 ? item.value.symbol.charAt(0) : 0;
        for (Item<ChassisModel> c : chassis.values()) {
            if (old != 0 && !c.readOnly && c.value.usesSymbol(old)) {
                c.value.replaceSymbol(old, symbol);
                c.dirty = true;
            }
        }
        item.value.symbol = String.valueOf(symbol);
        item.dirty = true;
        partsChanged();
        return null;
    }

    /** Changes a component's symbol. Layouts keep their mounts: they are drawn by id. */
    public String setComponentSymbol(Item<ShipComponentDef> item, char symbol) {
        if (item.readOnly) return item.id + " comes from a mod";
        if (String.valueOf(symbol).equals(item.value.symbol)) return null;
        if (!Character.isLetter(symbol)) return "a component's symbol must be a letter";
        if (!symbolFree(symbol)) return "'" + symbol + "' is already used";
        item.value.symbol = String.valueOf(symbol);
        item.dirty = true;
        // Layouts draw this component's mounts with the new letter on their next save
        for (Item<ChassisModel> c : chassis.values()) if (!c.readOnly && c.value.usesComponent(item.id)) c.dirty = true;
        partsChanged();
        return null;
    }

    /**
     * Deletes a definition. A tile type or component a layout still uses is not deleted.
     *
     * @return null if deleted, otherwise why not
     */
    public String delete(Item<?> item) {
        if (item.readOnly) return item.id + " comes from a mod";
        if (item.kind == Kind.TILE) {
            String s = ((TileTypeDef) item.value).symbol;
            if (s != null && s.length() == 1) {
                for (Item<ChassisModel> c : chassis.values()) {
                    if (c.value.usesSymbol(s.charAt(0))) return "chassis " + c.id + " uses it";
                }
            }
        } else if (item.kind == Kind.COMPONENT) {
            for (Item<ChassisModel> c : chassis.values()) {
                if (c.value.usesComponent(item.id)) return "chassis " + c.id + " uses it";
            }
        }
        items(item.kind).remove(item.id);
        if (item.file != null) deleted.add(item);
        partsChanged();
        return null;
    }

    public boolean hasUnsavedChanges() {
        if (!deleted.isEmpty()) return true;
        for (Kind k : Kind.values()) for (Item<?> i : items(k).values()) if (i.dirty && !i.readOnly) return true;
        return false;
    }

    // ---- Saving ----

    /**
     * Writes every changed definition into its file (a new one into its kind's default file) and
     * removes deleted ones. Read-only items are skipped.
     *
     * @return the files written
     */
    public List<Path> save() throws IOException {
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Item<?> d : deleted) {
            String text = read(texts, d.file);
            texts.put(d.file, TomlSections.remove(text, d.kind.type, d.id));
        }
        deleted.clear();
        ShipPartCatalog cat = catalog();
        for (Kind k : Kind.values()) {
            for (Item<?> i : items(k).values()) {
                if (!i.dirty || i.readOnly) continue;
                if (i.file == null) i.file = dataDir.resolve(k.defaultFile);
                String text = read(texts, i.file);
                texts.put(i.file, TomlSections.put(text, k.type, i.id, entries(i, cat), knownKeys(k)));
            }
        }
        Files.createDirectories(dataDir);
        for (Map.Entry<Path, String> e : texts.entrySet()) {
            Files.write(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        for (Kind k : Kind.values()) for (Item<?> i : items(k).values()) if (!i.readOnly) i.dirty = false;
        return new ArrayList<>(texts.keySet());
    }

    private static String read(Map<Path, String> texts, Path file) throws IOException {
        String text = texts.get(file);
        if (text != null) return text;
        return Files.exists(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : "";
    }

    private static List<TomlSections.Entry> entries(Item<?> i, ShipPartCatalog cat) {
        switch (i.kind) {
            case CHASSIS: return Defs.chassis(((ChassisModel) i.value).toDef(cat));
            case TILE: return Defs.tile((TileTypeDef) i.value);
            default: return Defs.component((ShipComponentDef) i.value);
        }
    }

    private static java.util.Set<String> knownKeys(Kind k) {
        switch (k) {
            case CHASSIS: return Defs.CHASSIS_KEYS;
            case TILE: return Defs.TILE_KEYS;
            default: return Defs.COMPONENT_KEYS;
        }
    }

    // ---- Undo ----

    /** Everything that can change, copied, for undo. */
    public static class Snapshot {
        final Map<String, Object[]> chassis = new LinkedHashMap<>();
        final Map<String, Object[]> tiles = new LinkedHashMap<>();
        final Map<String, Object[]> components = new LinkedHashMap<>();
        final List<Item<?>> deleted = new ArrayList<>();
    }

    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        for (Item<ChassisModel> i : chassis.values()) s.chassis.put(i.id, new Object[]{i, i.value.copy(), i.dirty});
        for (Item<TileTypeDef> i : tiles.values()) s.tiles.put(i.id, new Object[]{i, Defs.copy(i.value, TileTypeDef.class), i.dirty});
        for (Item<ShipComponentDef> i : components.values()) {
            s.components.put(i.id, new Object[]{i, Defs.copy(i.value, ShipComponentDef.class), i.dirty});
        }
        s.deleted.addAll(deleted);
        return s;
    }

    @SuppressWarnings("unchecked")
    public void restore(Snapshot s) {
        restore(chassis, s.chassis, v -> ((ChassisModel) v).copy());
        restore(tiles, s.tiles, v -> Defs.copy((TileTypeDef) v, TileTypeDef.class));
        restore(components, s.components, v -> Defs.copy((ShipComponentDef) v, ShipComponentDef.class));
        deleted.clear();
        deleted.addAll(s.deleted);
        partsChanged();
    }

    @SuppressWarnings("unchecked")
    private static <T> void restore(Map<String, Item<T>> into, Map<String, Object[]> from,
                                    java.util.function.Function<Object, T> copy) {
        into.clear();
        for (Map.Entry<String, Object[]> e : from.entrySet()) {
            Item<T> item = (Item<T>) e.getValue()[0];
            item.value = copy.apply(e.getValue()[1]);
            item.dirty = (Boolean) e.getValue()[2];
            into.put(e.getKey(), item);
        }
    }
}
