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
            "origin_x", "origin_y", "rows", "mounts", "edge_walls", "edge_conduits", "hull_pieces", "coverings", "ports", "lights"));
    public static final Set<String> TILE_KEYS = new HashSet<>(Arrays.asList("name", "symbol", "base",
            "weight_class", "material", "covering", "art", "color"));
    public static final Set<String> COMPONENT_KEYS = new HashSet<>(Arrays.asList("name", "symbol", "behavior",
            "sizes", "color", "power_output", "throughput"));

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
                String what = m.component != null ? "component" : "type";
                String id = m.component != null ? m.component : m.type;
                List<String> kv = new ArrayList<>(Arrays.asList(what, str(id), "size", str(m.size),
                        "x", Integer.toString(m.x), "y", Integer.toString(m.y)));
                if (m.rotation != 0) kv.addAll(Arrays.asList("rotation", Integer.toString(m.rotation)));
                mounts.add(table(kv.toArray(new String[0])));
            }
            out.add(new Entry("mounts", multiLineArray(mounts)));
        }
        if (!d.edgeWalls.isEmpty()) {
            List<String> walls = new ArrayList<>();
            for (ChassisDef.EdgeWall w : d.edgeWalls) {
                List<String> kv = new ArrayList<>(Arrays.asList("x", Integer.toString(w.x), "y", Integer.toString(w.y),
                        "side", str(w.side), "weight_class", str(w.weightClass)));
                if (w.material != null && !w.material.equals(MaterialTable.DEFAULT_MATERIAL)) {
                    kv.addAll(Arrays.asList("material", str(w.material)));
                }
                if (w.bulkhead) kv.addAll(Arrays.asList("bulkhead", "true"));
                walls.add(table(kv.toArray(new String[0])));
            }
            out.add(new Entry("edge_walls", multiLineArray(walls)));
        }
        if (!d.edgeConduits.isEmpty()) {
            List<String> conduits = new ArrayList<>();
            for (ChassisDef.EdgeConduit c : d.edgeConduits) {
                conduits.add(table("x", Integer.toString(c.x), "y", Integer.toString(c.y), "side", str(c.side)));
            }
            out.add(new Entry("edge_conduits", multiLineArray(conduits)));
        }
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
        if (!d.ports.isEmpty()) {
            List<String> ports = new ArrayList<>();
            for (ChassisDef.Port p : d.ports) {
                ports.add(table("x", Integer.toString(p.x), "y", Integer.toString(p.y), "active", Boolean.toString(p.active)));
            }
            out.add(new Entry("ports", multiLineArray(ports)));
        }
        if (d.lights != null) {
            List<String> lights = new ArrayList<>();
            for (ChassisDef.Light l : d.lights) {
                lights.add(table("x", Integer.toString(l.x), "y", Integer.toString(l.y), "side", str(l.side)));
            }
            out.add(new Entry("lights", lights.isEmpty() ? "[]" : multiLineArray(lights)));
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
        boolean floor = "FLOOR".equals(d.base) || "CONDUIT".equals(d.base);
        if (floor && d.covering != null && !"NONE".equals(d.covering)) out.add(new Entry("covering", str(d.covering)));
        if (d.art != null) out.add(new Entry("art", str(d.art)));
        if (d.color != null) out.add(new Entry("color", str(d.color)));
        return out;
    }

    public static List<Entry> component(ShipComponentDef d) {
        List<Entry> out = new ArrayList<>();
        if (d.name != null) out.add(new Entry("name", str(d.name)));
        out.add(new Entry("symbol", str(d.symbol)));
        out.add(new Entry("behavior", str(d.behavior)));
        if (!d.sizes.isEmpty()) {
            List<String> sizes = new ArrayList<>();
            for (String s : d.sizes) sizes.add(str(s));
            out.add(new Entry("sizes", inlineArray(sizes)));
        }
        if (d.color != null) out.add(new Entry("color", str(d.color)));
        if ("REACTOR".equals(d.behavior) && !d.powerOutput.isEmpty()) {
            List<String> kv = new ArrayList<>();
            for (String size : new String[]{"S", "M", "L", "XL"}) {
                Double w = d.powerOutput.get(size);
                if (w != null) {
                    kv.add(size);
                    kv.add(num(w));
                }
            }
            for (Map.Entry<String, Double> e : d.powerOutput.entrySet()) {
                if (!kv.contains(e.getKey())) {
                    kv.add(e.getKey());
                    kv.add(num(e.getValue()));
                }
            }
            out.add(new Entry("power_output", table(kv.toArray(new String[0]))));
        }
        if ("LIFE_SUPPORT".equals(d.behavior) && d.throughput != 1f) out.add(new Entry("throughput", num(d.throughput)));
        return out;
    }
}
