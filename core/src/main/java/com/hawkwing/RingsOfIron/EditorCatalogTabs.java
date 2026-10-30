package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.Defs;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link ShipEditor}'s Tiles and Components tabs: the part catalog's tile types and components,
 * edited and saved like the layouts.
 */
abstract class EditorCatalogTabs extends EditorPanel {

    EditorCatalogTabs(Path assetsDir, Path modsDir, ShipEditor.Refit refit) {
        super(assetsDir, modsDir, refit);
    }

    // ---- Tiles tab ----

    float tilesPanel(float x, float y) {
        text(x, y, "Tile types: the characters layouts are painted with", Color.CYAN);
        y -= LINE;
        for (EditorProject.Item<TileTypeDef> i : project.tiles.values()) {
            TileTypeDef d = i.value;
            final String id = i.id;
            int rgb = colorOf(d.color);
            char sym = d.symbol != null && d.symbol.length() == 1 ? d.symbol.charAt(0) : '?';
            y = swatchRow(x, y, rgb, sym, id + "  " + (d.name != null ? d.name : "") + (i.dirty ? " *" : "")
                    + (i.readOnly ? " (mod)" : ""), id.equals(editTileId), () -> editTileId = id);
        }
        y -= LINE * 0.4f;
        float bx = x;
        bx += button(bx, y, "New from selected", false, () -> startEdit("new-tile",
                project.freeId(EditorProject.Kind.TILE, editTileId + "_2"), this::newTile));
        button(bx, y, "Delete", false, () -> {
            EditorProject.Item<TileTypeDef> i = project.tiles.get(editTileId);
            if (i == null) return;
            beginEdit();
            String why = project.delete(i);
            if (why != null) {
                undo.pop();
                say("Cannot delete " + i.id + ": " + why);
                return;
            }
            partsChanged();
            say("Deleted tile type " + i.id);
            editTileId = project.tiles.isEmpty() ? null : project.tiles.keySet().iterator().next();
        });
        if (edit != null && edit.key.equals("new-tile")) {
            y -= LINE * 1.2f;
            field(x, y, "new-tile", "New id", edit.text.toString(), true, s -> { });
        }
        y -= LINE * 1.5f;
        EditorProject.Item<TileTypeDef> item = editTileId == null ? null : project.tiles.get(editTileId);
        if (item == null) return y;
        TileTypeDef d = item.value;
        boolean ed = !item.readOnly;
        text(x, y, "tile." + item.id + "  (" + origin(item, "tiles.toml") + ")", Color.CYAN);
        y -= LINE;
        field(x, y, "tile-name", "Name", d.name == null ? "" : d.name, ed, s -> {
            beginEdit();
            d.name = s;
            tileChanged(item);
        });
        y -= LINE;
        field(x, y, "tile-symbol", "Symbol", d.symbol == null ? "" : d.symbol, ed, s -> {
            if (s.length() != 1) {
                say("A symbol is one character");
                return;
            }
            beginEdit();
            String why = project.setTileSymbol(item, s.charAt(0));
            if (why != null) {
                undo.pop();
                say("Cannot use '" + s + "': " + why);
                return;
            }
            revision++;
            say("Symbol changed; layouts using it were redrawn");
        });
        y -= LINE;
        List<String> bases = new ArrayList<>();
        for (ShipPartCatalog.TileBase b : ShipPartCatalog.TileBase.values()) bases.add(b.name());
        choice(x, y, "Base", d.base, bases.subList(0, 3), ed, v -> {
            beginEdit();
            d.base = v;
            tileChanged(item);
        });
        y -= LINE;
        choice(x, y, "", d.base, bases.subList(3, bases.size()), ed, v -> {
            beginEdit();
            d.base = v;
            tileChanged(item);
        });
        y -= LINE;
        boolean wall = "HULL".equals(d.base) || "INTERNAL_WALL".equals(d.base);
        if (wall) {
            List<String> weights = new ArrayList<>();
            for (WeightClass w : WeightClass.values()) weights.add(w.name());
            choice(x, y, "Weight class", d.weightClass, weights, ed, v -> {
                beginEdit();
                d.weightClass = v;
                tileChanged(item);
            });
            y -= LINE;
            choice(x, y, "Material", d.material, project.materials, ed, v -> {
                beginEdit();
                d.material = v;
                tileChanged(item);
            });
            y -= LINE;
        } else if (!"VOID".equals(d.base)) {
            List<String> coverings = new ArrayList<>();
            for (FloorCovering f : FloorCovering.values()) coverings.add(f.name());
            choice(x, y, "Covering", d.covering == null ? "NONE" : d.covering, coverings, ed, v -> {
                beginEdit();
                d.covering = v;
                tileChanged(item);
            });
            y -= LINE;
        }
        field(x, y, "tile-color", "Color", d.color == null ? "" : d.color, ed, s -> {
            if (!validColor(s)) {
                say("Colors are #rrggbb");
                return;
            }
            beginEdit();
            d.color = s.startsWith("#") ? s : "#" + s;
            tileChanged(item);
        });
        y -= LINE;
        return y;
    }

    void tileChanged(EditorProject.Item<TileTypeDef> item) {
        item.dirty = true;
        partsChanged();
        // Layouts painted with it build differently now
        String s = item.value.symbol;
        if (s != null && s.length() == 1) {
            for (EditorProject.Item<ChassisModel> c : project.chassis.values()) {
                if (!c.readOnly && c.value.usesSymbol(s.charAt(0))) c.dirty = true;
            }
        }
    }

    void newTile(String id) {
        if (!EditorProject.validId(id) || project.tiles.containsKey(id)) {
            say("'" + id + "' is not a free id (lowercase letters, digits and _)");
            return;
        }
        EditorProject.Item<TileTypeDef> from = project.tiles.get(editTileId);
        TileTypeDef d = from != null ? Defs.copy(from.value, TileTypeDef.class) : new TileTypeDef();
        d.id = id;
        d.name = (d.name != null ? d.name : id) + " (new)";
        String preferred = d.symbol != null && !d.symbol.isEmpty() ? d.symbol.toLowerCase(Locale.ROOT) : "";
        d.symbol = String.valueOf(project.freeSymbol(preferred));
        beginEdit();
        project.addTile(d);
        editTileId = id;
        tileId = id;
        partsChanged();
        say("Made tile type " + id + " drawn with '" + d.symbol + "'");
    }

    // ---- Components tab ----

    float componentsPanel(float x, float y) {
        text(x, y, "Ship components: the systems layouts mount", Color.CYAN);
        y -= LINE;
        for (EditorProject.Item<ShipComponentDef> i : project.components.values()) {
            ShipComponentDef d = i.value;
            final String id = i.id;
            char sym = d.symbol != null && d.symbol.length() == 1 ? d.symbol.charAt(0) : '?';
            y = swatchRow(x, y, colorOf(d.color), sym, id + "  " + (d.name != null ? d.name : "") + (i.dirty ? " *" : "")
                    + (i.readOnly ? " (mod)" : ""), id.equals(editComponentId), () -> editComponentId = id);
        }
        y -= LINE * 0.4f;
        float bx = x;
        bx += button(bx, y, "New from selected", false, () -> startEdit("new-component",
                project.freeId(EditorProject.Kind.COMPONENT, editComponentId + "_2"), this::newComponent));
        button(bx, y, "Delete", false, () -> {
            EditorProject.Item<ShipComponentDef> i = project.components.get(editComponentId);
            if (i == null) return;
            beginEdit();
            String why = project.delete(i);
            if (why != null) {
                undo.pop();
                say("Cannot delete " + i.id + ": " + why);
                return;
            }
            partsChanged();
            say("Deleted component " + i.id);
            editComponentId = project.components.isEmpty() ? null : project.components.keySet().iterator().next();
        });
        if (edit != null && edit.key.equals("new-component")) {
            y -= LINE * 1.2f;
            field(x, y, "new-component", "New id", edit.text.toString(), true, s -> { });
        }
        y -= LINE * 1.5f;
        EditorProject.Item<ShipComponentDef> item = editComponentId == null ? null : project.components.get(editComponentId);
        if (item == null) return y;
        ShipComponentDef d = item.value;
        boolean ed = !item.readOnly;
        text(x, y, "component." + item.id + "  (" + origin(item, "components.toml") + ")", Color.CYAN);
        y -= LINE;
        field(x, y, "comp-name", "Name", d.name == null ? "" : d.name, ed, s -> {
            beginEdit();
            d.name = s;
            componentChanged(item);
        });
        y -= LINE;
        field(x, y, "comp-symbol", "Symbol", d.symbol == null ? "" : d.symbol, ed, s -> {
            if (s.length() != 1) {
                say("A symbol is one letter");
                return;
            }
            beginEdit();
            String why = project.setComponentSymbol(item, s.charAt(0));
            if (why != null) {
                undo.pop();
                say("Cannot use '" + s + "': " + why);
                return;
            }
            revision++;
        });
        y -= LINE;
        text(x, y, "Behavior", Color.LIGHT_GRAY);
        y -= LINE;
        float bx2 = x + 10f;
        for (SubsystemComponent.SubsystemType type : SubsystemComponent.SubsystemType.values()) {
            final String name = type.name();
            String label = name.toLowerCase(Locale.ROOT);
            layout.setText(font, label);
            if (bx2 + layout.width + 16 > panelX() + PANEL_WIDTH - PAD) {
                bx2 = x + 10f;
                y -= LINE;
            }
            bx2 += button(bx2, y, label, name.equals(d.behavior), ed ? () -> setBehavior(item, name) : () -> { });
        }
        y -= LINE;
        MountFootprint.MountKind kind;
        try {
            kind = SubsystemComponent.SubsystemType.valueOf(d.behavior).mountKind;
        } catch (IllegalArgumentException | NullPointerException e) {
            kind = MountFootprint.MountKind.PRIMARY;
        }
        text(x, y, "Mount: " + kind.name().toLowerCase(Locale.ROOT) + sizeHint(kind), Color.GRAY);
        y -= LINE;
        text(x, y, "Sizes", Color.LIGHT_GRAY);
        bx2 = x + 120f;
        for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) {
            if (!s.exists(kind)) continue;
            final String sn = s.name();
            boolean on = d.sizes.isEmpty() || d.sizes.contains(sn);
            final MountFootprint.MountKind k = kind;
            bx2 += button(bx2, y, sn, on, ed ? () -> toggleSize(item, sn, k) : () -> { });
        }
        y -= LINE;
        field(x, y, "comp-color", "Color", d.color == null ? "" : d.color, ed, s -> {
            if (!validColor(s)) {
                say("Colors are #rrggbb");
                return;
            }
            beginEdit();
            d.color = s.startsWith("#") ? s : "#" + s;
            componentChanged(item);
        });
        y -= LINE;
        if ("REACTOR".equals(d.behavior)) {
            text(x, y, "Power output, MW (blank for standard)", Color.LIGHT_GRAY);
            y -= LINE;
            for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) {
                if (!s.exists(kind)) continue;
                final String sn = s.name();
                Double w = d.powerOutput.get(sn);
                field(x + 10f, y, "comp-power-" + sn, sn, w == null ? "" : TomlNum.of((float) (w / 1e6)), ed, v -> {
                    beginEdit();
                    if (v.trim().isEmpty()) {
                        d.powerOutput.remove(sn);
                    } else {
                        Float mw = parseFloat(v);
                        if (mw == null || mw <= 0) {
                            undo.pop();
                            say("Power output is a positive number of megawatts");
                            return;
                        }
                        d.powerOutput.put(sn, mw * 1e6);
                    }
                    componentChanged(item);
                });
                y -= LINE;
            }
        }
        if ("LIFE_SUPPORT".equals(d.behavior)) {
            field(x, y, "comp-throughput", "Throughput (x)", TomlNum.of(d.throughput), ed, v -> {
                Float t = parseFloat(v);
                if (t == null || t <= 0) {
                    say("Throughput is a positive multiplier");
                    return;
                }
                beginEdit();
                d.throughput = t;
                componentChanged(item);
            });
            y -= LINE;
        }
        if ("TANK".equals(d.behavior)) {
            text(x, y, "Holds", Color.LIGHT_GRAY);
            float fx = x + 120f;
            String current = d.fluid == null ? Fluid.PROPELLANT.name() : d.fluid;
            for (Fluid f : Fluid.values()) {
                final String fn = f.name();
                fx += button(fx, y, fn.toLowerCase(Locale.ROOT), fn.equals(current), ed ? () -> {
                    if (fn.equals(d.fluid)) return;
                    beginEdit();
                    d.fluid = fn;
                    componentChanged(item);
                } : () -> { });
            }
            y -= LINE;
            Fluid fluid = Fluid.valueOf(current);
            text(x, y, "Capacity, kg (blank for " + TomlNum.of(fluid.capacityPerTile) + " a tile)", Color.LIGHT_GRAY);
            y -= LINE;
            for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) {
                if (!s.exists(kind)) continue;
                final String sn = s.name();
                Double kg = d.capacity.get(sn);
                field(x + 10f, y, "comp-capacity-" + sn, sn, kg == null ? "" : TomlNum.of(kg.floatValue()), ed, v -> {
                    beginEdit();
                    if (v.trim().isEmpty()) {
                        d.capacity.remove(sn);
                    } else {
                        Float c = parseFloat(v);
                        if (c == null || c <= 0) {
                            undo.pop();
                            say("Capacity is a positive number of kilograms");
                            return;
                        }
                        d.capacity.put(sn, (double) c);
                    }
                    componentChanged(item);
                });
                y -= LINE;
            }
        }
        return y;
    }

    /** Where a definition comes from, for the panel. */
    static String origin(EditorProject.Item<?> item, String defaultFile) {
        if (item.source != null) return item.source;
        return (item.dirty ? "new" : "built in") + "; saves to " + defaultFile;
    }

    static String sizeHint(MountFootprint.MountKind kind) {
        switch (kind) {
            case PRIMARY: return " (square: S 1x1, M 2x2, L 3x3, XL 5x5)";
            case AUXILIARY: return " (slot: S 1x2, M 2x3, L 3x7, XL 5x12)";
            default: return " (hull edge: S 3x1, M 5x2, L 7x3)";
        }
    }

    void setBehavior(EditorProject.Item<ShipComponentDef> item, String behavior) {
        ShipComponentDef d = item.value;
        if (behavior.equals(d.behavior)) return;
        MountFootprint.MountKind from = SubsystemComponent.SubsystemType.valueOf(d.behavior).mountKind;
        MountFootprint.MountKind to = SubsystemComponent.SubsystemType.valueOf(behavior).mountKind;
        if (from != to) {
            for (EditorProject.Item<ChassisModel> c : project.chassis.values()) {
                if (c.value.usesComponent(item.id)) {
                    say("Chassis " + c.id + " mounts it; a " + to.name().toLowerCase(Locale.ROOT) + " behavior needs another mount shape");
                    return;
                }
            }
        }
        beginEdit();
        d.behavior = behavior;
        if (from != to) d.sizes.clear();
        componentChanged(item);
    }

    void toggleSize(EditorProject.Item<ShipComponentDef> item, String size, MountFootprint.MountKind kind) {
        ShipComponentDef d = item.value;
        beginEdit();
        if (d.sizes.isEmpty()) {
            for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) if (s.exists(kind)) d.sizes.add(s.name());
        }
        if (d.sizes.contains(size)) {
            if (d.sizes.size() == 1) {
                undo.pop();
                say("A component needs at least one size");
                return;
            }
            d.sizes.remove(size);
        } else {
            d.sizes.add(size);
        }
        int all = 0;
        for (MountFootprint.MountSize s : MountFootprint.MountSize.values()) if (s.exists(kind)) all++;
        if (d.sizes.size() == all) d.sizes.clear();
        componentChanged(item);
    }

    void componentChanged(EditorProject.Item<ShipComponentDef> item) {
        item.dirty = true;
        partsChanged();
        for (EditorProject.Item<ChassisModel> c : project.chassis.values()) {
            if (!c.readOnly && c.value.usesComponent(item.id)) c.dirty = true;
        }
    }

    void newComponent(String id) {
        if (!EditorProject.validId(id) || project.components.containsKey(id)) {
            say("'" + id + "' is not a free id (lowercase letters, digits and _)");
            return;
        }
        EditorProject.Item<ShipComponentDef> from = project.components.get(editComponentId);
        ShipComponentDef d = from != null ? Defs.copy(from.value, ShipComponentDef.class) : new ShipComponentDef();
        if (d.behavior == null) d.behavior = "CARGO";
        d.id = id;
        d.name = (d.name != null ? d.name : id) + " (new)";
        // Components are drawn with capitals, as the built-in ones are; the name's initial if it is free
        String initial = id.substring(0, 1).toUpperCase(Locale.ROOT);
        char symbol = project.freeSymbol(initial + "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        if (!Character.isLetter(symbol)) symbol = project.freeSymbol("abcdefghijklmnopqrstuvwxyz");
        d.symbol = String.valueOf(symbol);
        beginEdit();
        project.addComponent(d);
        editComponentId = id;
        componentId = id;
        partsChanged();
        say("Made component " + id + " drawn with '" + d.symbol + "'");
    }
}
