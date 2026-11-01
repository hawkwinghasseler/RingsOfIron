package com.ringsofiron.interiorgrid.editor;

import com.google.gson.Gson;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ringsofiron.interiorgrid.editor.TomlSections.Entry;
import static com.ringsofiron.interiorgrid.editor.TomlSections.inlineArray;
import static com.ringsofiron.interiorgrid.editor.TomlSections.multiLineArray;
import static com.ringsofiron.interiorgrid.editor.TomlSections.num;
import static com.ringsofiron.interiorgrid.editor.TomlSections.str;
import static com.ringsofiron.interiorgrid.editor.TomlSections.table;

/** Writes the editor's definitions as TOML section entries, and copies them. */
public final class Defs {

    private static final Gson GSON = new Gson();

    private Defs() {
    }

    /** A deep copy of a plain definition object. */
    public static <T> T copy(T value, Class<T> cls) {
        return GSON.fromJson(GSON.toJson(value), cls);
    }

    /** Every key a chassis section can have, so a rewrite drops the ones no longer used. */
    public static final Set<String> CHASSIS_KEYS = new HashSet<>(Arrays.asList("name", "station", "tile_size",
            "origin_x", "origin_y", "rows", "mounts", "conduits", "edge_conduits", "pipes", "edge_pipes", "edge_walls",
            "doors", "hull_pieces", "coverings", "lights", "rcs"));
    public static final Set<String> TILE_KEYS = new HashSet<>(Arrays.asList("name", "symbol", "base",
            "weight_class", "material", "covering", "art", "color"));
    public static final Set<String> COMPONENT_KEYS = new HashSet<>(Arrays.asList("name", "behavior",
            "sizes", "color", "power_output", "throughput", "fluid", "capacity", "thrust", "short_chance",
            "burst_threshold", "burst_chance", "efficiency", "casing", "maker", "description", "price"));

    public static List<Entry> chassis(ChassisDef d) {
        List<Entry> out = new ArrayList<>();
        if (d.name != null) out.add(new Entry("name", str(d.name)));
        if (d.station) out.add(new Entry("station", "true"));
        out.add(new Entry("tile_size", num(d.tileSize)));
        out.add(new Entry("origin_x", num(d.originX)));
        out.add(new Entry("origin_y", num(d.originY)));
        List<String> rows = new ArrayList<>();
        for (String r : d.rows) rows.add(str(r));
        out.add(new Entry("rows", multiLineArray(rows)));
        if (!d.mounts.isEmpty()) {
            List<String> mounts = new ArrayList<>();
            for (ChassisDef.Mount m : d.mounts) {
                List<String> kv = new ArrayList<>(Arrays.asList("component", str(m.component), "size", str(m.size),
                        "x", Integer.toString(m.x), "y", Integer.toString(m.y)));
                if (m.rotation != 0) kv.addAll(Arrays.asList("rotation", Integer.toString(m.rotation)));
                if (m.active) kv.addAll(Arrays.asList("active", "true"));
                mounts.add(table(kv.toArray(new String[0])));
            }
            out.add(new Entry("mounts", multiLineArray(mounts)));
        }
        runs(out, "conduits", d.conduits);
        runs(out, "edge_conduits", d.edgeConduits);
        runs(out, "pipes", d.pipes);
        runs(out, "edge_pipes", d.edgePipes);
        List<String> walls = new ArrayList<>();
        for (ChassisDef.EdgeWall w : d.edgeWalls) {
            List<String> kv = new ArrayList<>(Arrays.asList("path", run(w.path), "weight_class", str(w.weightClass)));
            if (w.material != null && !w.material.equals(MaterialTable.DEFAULT_MATERIAL)) {
                kv.addAll(Arrays.asList("material", str(w.material)));
            }
            walls.add(table(kv.toArray(new String[0])));
        }
        list(out, "edge_walls", walls);
        List<String> doors = new ArrayList<>();
        for (ChassisDef.Door door : d.doors) {
            doors.add(table("x", Integer.toString(door.x), "y", Integer.toString(door.y), "side", str(door.side)));
        }
        list(out, "doors", doors);
        if (!d.hullPieces.isEmpty()) {
            List<String> pieces = new ArrayList<>();
            for (ChassisDef.HullPiece p : d.hullPieces) {
                List<String> kv = new ArrayList<>(Arrays.asList("shape", str(p.shape), "x", Integer.toString(p.x),
                        "y", Integer.toString(p.y), "width", Integer.toString(p.width),
                        "height", Integer.toString(p.height), "rotation", Integer.toString(p.rotation),
                        "weight_class", str(p.weightClass)));
                if (p.material != null && !p.material.equals(MaterialTable.DEFAULT_MATERIAL)) {
                    kv.addAll(Arrays.asList("material", str(p.material)));
                }
                pieces.add(table(kv.toArray(new String[0])));
            }
            out.add(new Entry("hull_pieces", multiLineArray(pieces)));
        }
        if (!d.coverings.isEmpty()) {
            List<String> coverings = new ArrayList<>();
            for (ChassisDef.Covering c : d.coverings) {
                coverings.add(table("covering", str(c.covering), "x", Integer.toString(c.x), "y", Integer.toString(c.y),
                        "width", Integer.toString(c.width), "height", Integer.toString(c.height)));
            }
            // A single covering stays on one line, as the data files write it
            out.add(new Entry("coverings", coverings.size() == 1 ? "[ " + coverings.get(0) + " ]" : multiLineArray(coverings)));
        }
        if (d.lights != null) {
            List<String> lights = new ArrayList<>();
            for (ChassisDef.Light l : d.lights) {
                lights.add(table("x", Integer.toString(l.x), "y", Integer.toString(l.y), "side", str(l.side)));
            }
            out.add(new Entry("lights", lights.isEmpty() ? "[]" : multiLineArray(lights)));
        }
        if (!d.rcs.isEmpty()) {
            List<String> rcs = new ArrayList<>();
            for (ChassisDef.Rcs r : d.rcs) {
                List<String> kv = new ArrayList<>(Arrays.asList("x", Integer.toString(r.x), "y", Integer.toString(r.y),
                        "side", str(r.side), "kind", str(r.kind)));
                if (r.thrust > 0) kv.addAll(Arrays.asList("thrust", num(r.thrust)));
                rcs.add(table(kv.toArray(new String[0])));
            }
            out.add(new Entry("rcs", multiLineArray(rcs)));
        }
        return out;
    }

    public static List<Entry> tile(TileTypeDef d) {
        List<Entry> out = new ArrayList<>();
        if (d.name != null) out.add(new Entry("name", str(d.name)));
        out.add(new Entry("symbol", str(d.symbol)));
        out.add(new Entry("base", str(d.base)));
        boolean wall = "HULL".equals(d.base) || "INTERNAL_WALL".equals(d.base);
        if (wall) {
            out.add(new Entry("weight_class", str(d.weightClass)));
            out.add(new Entry("material", str(d.material)));
        }
        boolean floor = "FLOOR".equals(d.base);
        if (floor && d.covering != null && !"NONE".equals(d.covering)) out.add(new Entry("covering", str(d.covering)));
        if (d.art != null) out.add(new Entry("art", str(d.art)));
        if (d.color != null) out.add(new Entry("color", str(d.color)));
        return out;
    }

    public static List<Entry> component(ShipComponentDef d) {
        List<Entry> out = new ArrayList<>();
        if (d.name != null) out.add(new Entry("name", str(d.name)));
        if (d.maker != null) out.add(new Entry("maker", str(d.maker)));
        if (d.description != null) out.add(new Entry("description", str(d.description)));
        out.add(new Entry("behavior", str(d.behavior)));
        if (!d.sizes.isEmpty()) {
            List<String> sizes = new ArrayList<>();
            for (String s : d.sizes) sizes.add(str(s));
            out.add(new Entry("sizes", inlineArray(sizes)));
        }
        if (d.color != null) out.add(new Entry("color", str(d.color)));
        if ("REACTOR".equals(d.behavior) && !d.powerOutput.isEmpty()) {
            out.add(new Entry("power_output", sizeTable(d.powerOutput)));
        }
        if ("LIFE_SUPPORT".equals(d.behavior) && d.throughput != 1f) out.add(new Entry("throughput", num(d.throughput)));
        if ("TANK".equals(d.behavior)) {
            out.add(new Entry("fluid", str(d.fluid == null ? "PROPELLANT" : d.fluid)));
            if (!d.capacity.isEmpty()) out.add(new Entry("capacity", sizeTable(d.capacity)));
        }
        if ("ENGINE".equals(d.behavior)) {
            if (!d.thrust.isEmpty()) out.add(new Entry("thrust", sizeTable(d.thrust)));
            if (d.efficiency != null) out.add(new Entry("efficiency", num(d.efficiency)));
            if (d.casing != null) out.add(new Entry("casing", str(d.casing)));
        }
        if (d.price != null && !d.price.isEmpty()) out.add(new Entry("price", sizeTable(d.price)));
        if (d.shortChance != null) out.add(new Entry("short_chance", num(d.shortChance)));
        if ("TANK".equals(d.behavior)) {
            if (d.burstThreshold != null) out.add(new Entry("burst_threshold", num(d.burstThreshold)));
            if (d.burstChance != null) out.add(new Entry("burst_chance", num(d.burstChance)));
        }
        return out;
    }

    /** Writes a list of runs, on one line when there is just one. */
    private static void runs(List<Entry> out, String key, List<int[][]> runs) {
        List<String> items = new ArrayList<>();
        for (int[][] r : runs) items.add(run(r));
        list(out, key, items);
    }

    /** Writes a list unless it is empty, on one line when it has just one item. */
    private static void list(List<Entry> out, String key, List<String> items) {
        if (items.isEmpty()) return;
        out.add(new Entry(key, items.size() == 1 ? "[ " + items.get(0) + " ]" : multiLineArray(items)));
    }

    /** A run as {@code [[x, y], [x, y]]}. */
    public static String run(int[][] points) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < points.length; i++) {
            if (i > 0) b.append(", ");
            b.append('[').append(points[i][0]).append(", ").append(points[i][1]).append(']');
        }
        return b.append(']').toString();
    }

    /** A by-size table such as {@code { S = 1.0, M = 2.0 }}, sizes in order and any others after. */
    private static String sizeTable(Map<String, Double> bySize) {
        List<String> kv = new ArrayList<>();
        for (String size : new String[]{"S", "M", "L", "XL"}) {
            Double w = bySize.get(size);
            if (w != null) {
                kv.add(size);
                kv.add(num(w));
            }
        }
        for (Map.Entry<String, Double> e : bySize.entrySet()) {
            if (!kv.contains(e.getKey())) {
                kv.add(e.getKey());
                kv.add(num(e.getValue()));
            }
        }
        return table(kv.toArray(new String[0]));
    }
}
