package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.ringsofiron.shipsystems.EngineThrust;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The {@link ShipEditor}'s panel: its widgets, the tabs, and the Layout tab with the chassis' details
 * and the tool's options.
 */
abstract class EditorPanel extends EditorTools {

    EditorPanel(Path assetsDir, Path modsDir, ShipEditor.Refit refit) {
        super(assetsDir, modsDir, refit);
    }

    // ---- Panel ----

    float panelX() {
        return Gdx.graphics.getWidth() - PANEL_WIDTH;
    }

    void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        rects.add(new float[]{x, y, w, h, r, g, b, a});
    }

    void text(float x, float y, String s, Color c) {
        texts.add(new Object[]{x, y, s, new Color(c)});
    }

    /** A button; returns its width. */
    float button(float x, float y, String label, boolean selected, Runnable action) {
        layout.setText(font, label);
        float w = layout.width + 12f, h = LINE - 2f;
        if (selected) rect(x, y - h + 4, w, h, 0.25f, 0.45f, 0.7f, 1f);
        else rect(x, y - h + 4, w, h, 0.18f, 0.2f, 0.25f, 1f);
        text(x + 6, y, label, Color.WHITE);
        building.add(new Hit(x, y - h + 4, w, h, action));
        return w + 4f;
    }

    /** "label: value", which turns into a text field when clicked. */
    void field(float x, float y, String key, String label, String value, boolean enabled, Consumer<String> commit) {
        text(x, y, label, Color.LIGHT_GRAY);
        float vx = x + 120f, w = PANEL_WIDTH - (vx - panelX()) - PAD;
        boolean editing = edit != null && edit.key.equals(key);
        rect(vx, y - LINE + 6, w, LINE - 2, editing ? 0.25f : 0.13f, editing ? 0.3f : 0.15f, editing ? 0.4f : 0.18f, 1f);
        String shown = editing ? edit.text + ((int) (statusAge * 2) % 2 == 0 ? "|" : "") : value;
        if (!editing && shown.isEmpty()) shown = "(none)";
        else if (!editing && shown.equals(" ")) shown = "(space)";
        text(vx + 4, y, shown, enabled ? Color.WHITE : Color.GRAY);
        if (enabled) building.add(new Hit(vx, y - LINE + 6, w, LINE - 2, () -> startEdit(key, value, commit)));
    }

    /** "label: value" that steps through choices when clicked. */
    <T> void choice(float x, float y, String label, T value, List<T> options, boolean enabled, Consumer<T> pick) {
        text(x, y, label, Color.LIGHT_GRAY);
        float vx = x + 120f;
        float bx = vx;
        for (T option : options) {
            final T o = option;
            bx += button(bx, y, String.valueOf(option), option.equals(value), enabled ? () -> pick.accept(o) : () -> { });
        }
    }

    void startEdit(String key, String value, Consumer<String> commit) {
        commitEdit();
        edit = new Edit(key, value, commit);
    }

    void commitEdit() {
        if (edit == null) return;
        Edit e = edit;
        edit = null;
        e.commit.accept(e.text.toString());
    }

    void drawPanel() {
        rects.clear();
        texts.clear();
        building = new ArrayList<>();
        float px = panelX(), top = Gdx.graphics.getHeight();
        rect(px, 0, PANEL_WIDTH, top, 0.1f, 0.11f, 0.14f, 1f);
        float y = top - PAD - panelScroll;
        float x = px + PAD;
        // Tabs
        float bx = x;
        bx += button(bx, y, "Layout", tab == Tab.LAYOUT, () -> tab = Tab.LAYOUT);
        if (refit != null) {
            // The yard builds from the parts it has: tile types and components stay as they are
            tab = Tab.LAYOUT;
            bx += button(bx + 10, y, "Rebuild ship", false, this::applyRefit) + 20f;
            button(bx, y, "Leave", false, refit::close);
        } else {
            bx += button(bx, y, "Tiles", tab == Tab.TILES, () -> tab = Tab.TILES);
            bx += button(bx, y, "Components", tab == Tab.COMPONENTS, () -> tab = Tab.COMPONENTS);
            String unsaved = project.hasUnsavedChanges() ? "unsaved *" : "saved";
            button(bx + 10, y, "Save (" + unsaved + ")", false, this::save);
        }
        y -= LINE * 1.6f;
        switch (tab) {
            case LAYOUT: y = layoutPanel(x, y); break;
            case TILES: y = tilesPanel(x, y); break;
            default: y = componentsPanel(x, y); break;
        }
        panelContentHeight = top - PAD - panelScroll - y;

        // Status and help pinned to the bottom
        rect(px, 0, PANEL_WIDTH, LINE * 3.4f, 0.07f, 0.08f, 0.1f, 1f);
        List<String> status = wrap(this.status, PANEL_WIDTH - 2 * PAD);
        float sy = LINE * 3.2f;
        for (int i = 0; i < Math.min(2, status.size()); i++, sy -= LINE) text(x, sy, status.get(i), Color.YELLOW);
        text(x, LINE * 1.0f, refit != null ? "Ctrl+S rebuild ship  Ctrl+Z/Y undo/redo  F fit  Esc leave"
                : "Ctrl+S save  Ctrl+Z/Y undo/redo  F fit  Esc quit", Color.GRAY);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (float[] r : rects) {
            shapes.setColor(r[4], r[5], r[6], r[7]);
            shapes.rect(r[0], r[1], r[2], r[3]);
        }
        shapes.end();
        batch.begin();
        for (Object[] t : texts) {
            font.setColor((Color) t[3]);
            font.draw(batch, (String) t[2], (Float) t[0], (Float) t[1]);
        }
        batch.end();
        hits = building;
    }

    float layoutPanel(float x, float y) {
        ShipPartCatalog cat = catalog();
        if (refit != null) return layoutDetails(x, y + LINE * 1.4f, cat);
        text(x, y, "Chassis", Color.CYAN);
        y -= LINE;
        float bx = x;
        for (EditorProject.Item<ChassisModel> c : project.chassis.values()) {
            String label = c.id + (c.dirty && !c.readOnly ? "*" : "") + (c.readOnly ? " (mod)" : "");
            layout.setText(font, label);
            if (bx + layout.width + 16 > panelX() + PANEL_WIDTH - PAD) {
                bx = x;
                y -= LINE;
            }
            final String id = c.id;
            bx += button(bx, y, label, c.id.equals(chassisId), () -> openChassis(id));
        }
        y -= LINE;
        bx = x;
        bx += button(bx, y, "New", false, () -> startEdit("new-chassis", project.freeId(EditorProject.Kind.CHASSIS, "new_ship"),
                id -> newChassis(id, null)));
        if (model() != null) {
            bx += button(bx, y, "Duplicate", false, () -> startEdit("new-chassis",
                    project.freeId(EditorProject.Kind.CHASSIS, chassisId + "_copy"), id -> newChassis(id, model())));
        }
        if (current() != null && !current().readOnly) {
            button(bx, y, "Delete", false, () -> {
                beginEdit();
                String why = project.delete(current());
                if (why != null) {
                    say("Cannot delete: " + why);
                    return;
                }
                say("Deleted " + chassisId + " (Ctrl+Z to undo, Ctrl+S to remove it from its file)");
                chassisId = project.chassis.isEmpty() ? null : project.chassis.keySet().iterator().next();
                revision++;
                fit();
            });
        }
        if (edit != null && edit.key.equals("new-chassis")) {
            y -= LINE * 1.2f;
            field(x, y, "new-chassis", "New id", edit.text.toString(), true, s -> { });
            y -= LINE;
            text(x, y, "Type an id (lowercase, digits, _) and press Enter", Color.GRAY);
        }
        return layoutDetails(x, y, cat);
    }

    /** The open layout's size, check and tools, below the chassis list. */
    float layoutDetails(float x, float y, ShipPartCatalog cat) {
        float bx;
        ChassisModel m = model();
        if (m == null) return y - LINE;
        boolean ed = editable();
        y -= LINE * 1.4f;
        EditorProject.Item<ChassisModel> item = current();
        text(x, y, item.id + "  " + m.width() + " x " + m.height() + " tiles"
                + (item.source != null ? "  (" + item.source + ")" : "  (new)"), Color.CYAN);
        y -= LINE;
        if (refit != null) {
            if (quotedRevision != revision) {
                quote = refit.quote(m.toDef(cat));
                quotedRevision = revision;
            }
            if (quote != null) {
                text(x, y, quote, Color.GOLD);
                y -= LINE;
            }
        }
        field(x, y, "name", "Name", m.name == null ? "" : m.name, ed, s -> {
            beginEdit();
            m.name = s;
            changed();
        });
        y -= LINE;
        choice(x, y, "Station", m.station ? "yes" : "no", list("no", "yes"), ed, v -> {
            beginEdit();
            m.station = v.equals("yes");
            changed();
        });
        y -= LINE;
        field(x, y, "tile-size", "Tile size (m)", TomlNum.of(m.tileSize), ed, s -> {
            Float v = parseFloat(s);
            if (v == null || v <= 0) {
                say("Tile size must be a positive number");
                return;
            }
            beginEdit();
            m.tileSize = v;
            changed();
        });
        y -= LINE;
        field(x, y, "origin", "Origin (m)", TomlNum.of(m.originX) + ", " + TomlNum.of(m.originY), ed, s -> {
            String[] parts = s.split(",");
            Float ox = parts.length == 2 ? parseFloat(parts[0]) : null, oy = parts.length == 2 ? parseFloat(parts[1]) : null;
            if (ox == null || oy == null) {
                say("Origin is two numbers: x, y");
                return;
            }
            beginEdit();
            m.originX = ox;
            m.originY = oy;
            changed();
        });
        y -= LINE;
        text(x, y, "Grid", Color.LIGHT_GRAY);
        bx = x + 120f;
        for (Side side : new Side[]{Side.TOP, Side.BOTTOM, Side.LEFT, Side.RIGHT}) {
            text(bx, y, side.name().charAt(0) + side.name().substring(1).toLowerCase(Locale.ROOT), Color.LIGHT_GRAY);
            bx += 52f;
        }
        y -= LINE;
        bx = x + 120f;
        for (Side side : new Side[]{Side.TOP, Side.BOTTOM, Side.LEFT, Side.RIGHT}) {
            final Side s = side;
            float b = bx;
            b += button(b, y, "+", false, () -> resize(s, 1));
            button(b, y, "-", false, () -> resize(s, -1));
            bx += 52f;
        }
        y -= LINE;
        button(x + 120f, y, "Center origin", false, () -> {
            if (!ed) return;
            beginEdit();
            m.centerOrigin();
            changed();
        });
        y -= LINE * 1.5f;

        // Tools
        text(x, y, "Tool", Color.CYAN);
        y -= LINE;
        bx = x;
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            final Tool t = tools[i];
            String label = (i + 1) % 10 + " " + t.label;
            layout.setText(font, label);
            if (bx + layout.width + 16 > panelX() + PANEL_WIDTH - PAD) {
                bx = x;
                y -= LINE;
            }
            bx += button(bx, y, label, tool == t, () -> tool = t);
        }
        y -= LINE * 1.4f;
        y = toolOptions(x, y, m, cat);

        // Check
        y -= LINE * 0.6f;
        EditorProject.Check c = check();
        text(x, y, "In game", Color.CYAN);
        y -= LINE;
        if (c.error != null) {
            for (String line : wrap("Refused: " + c.error, PANEL_WIDTH - 2 * PAD)) {
                text(x, y, line, Color.SALMON);
                y -= LINE;
            }
            text(x, y, "It still saves, but the game will not load it.", Color.GRAY);
            y -= LINE;
        } else {
            text(x, y, c.floorTiles + " floor and " + c.wallTiles + " wall tiles, " + c.rooms + " rooms", Color.WHITE);
            y -= LINE;
            text(x, y, c.systems + " systems, " + c.poweredSystems + " of the " + c.powerUsers + " that use power on a conduit",
                    c.poweredSystems < c.powerUsers ? Color.ORANGE : Color.WHITE);
            y -= LINE;
            text(x, y, String.format(Locale.ROOT, "%.1f m3 of cargo bays", c.cargoVolume), Color.WHITE);
            y -= LINE;
        }
        float minX = m.originX, minY = m.originY, maxX = m.originX + m.width() * m.tileSize, maxY = m.originY + m.height() * m.tileSize;
        text(x, y, String.format(Locale.ROOT, "Body extent: x %.1f to %.1f, y %.1f to %.1f m", minX, maxX, minY, maxY), Color.GRAY);
        y -= LINE;
        for (String p : project.catalogProblems()) {
            for (String line : wrap(p, PANEL_WIDTH - 2 * PAD)) {
                text(x, y, line, Color.SALMON);
                y -= LINE;
            }
        }
        return y;
    }

    /** The maker, description, price and engine ratings of a component at the size chosen, as the data gives them. */
    List<String> partDetails(ShipPartCatalog.ComponentType type) {
        List<String> out = new ArrayList<>();
        EditorProject.Item<ShipComponentDef> item = project.components.get(type.id);
        ShipComponentDef d = item == null ? null : item.value;
        if (d == null) return out;
        if (d.maker != null) out.add(d.maker);
        if (d.description != null) out.add(d.description);
        if (type.behavior == SubsystemComponent.SubsystemType.ENGINE) {
            Double n = d.thrust == null ? null : d.thrust.get(size.name());
            double thrust = n != null && n > 0 ? n : EngineThrust.standardThrust(size);
            out.add(String.format(Locale.ROOT, "%.2f MN, efficiency %.2f, %s casing", thrust / 1e6,
                    d.efficiency != null ? d.efficiency : 1f, d.casing != null ? d.casing.toLowerCase(Locale.ROOT) : "light"));
        }
        Double price = d.price == null ? null : d.price.get(size.name());
        if (price != null && price > 0) out.add(String.format(Locale.ROOT, "%,.0f cr at a yard", price));
        return out;
    }

    float toolOptions(float x, float y, ChassisModel m, ShipPartCatalog cat) {
        switch (tool) {
            case PAINT: {
                text(x, y, "Click or drag to paint; Shift-drag fills a rectangle", Color.GRAY);
                y -= LINE;
                for (ShipPartCatalog.TileType t : cat.tiles()) {
                    final String id = t.id;
                    y = swatchRow(x, y, t.color, t.symbol, t.name, id.equals(tileId), () -> tileId = id);
                }
                break;
            }
            case COMPONENT: {
                text(x, y, "Click to install; R turns it (" + rotation * 90 + " deg)", Color.GRAY);
                y -= LINE;
                text(x, y, "R over a placed one turns that one", Color.GRAY);
                y -= LINE;
                for (ShipPartCatalog.ComponentType t : cat.components()) {
                    final String id = t.id;
                    y = swatchRow(x, y, t.color, EditorCanvas.label(t).charAt(0), t.name + "  (" + t.behavior.name().toLowerCase(Locale.ROOT) + ")",
                            id.equals(componentId), () -> componentId = id);
                }
                ShipPartCatalog.ComponentType type = cat.component(componentId);
                // Keep the size one the selected component comes in
                if (type != null && !type.sizes.contains(size) && !type.sizes.isEmpty()) size = type.sizes.iterator().next();
                if (type != null) {
                    List<MountFootprint.MountSize> sizes = new ArrayList<>(type.sizes);
                    choice(x, y, "Size", size, sizes, true, s -> size = s);
                    y -= LINE;
                    for (String line : partDetails(type)) {
                        for (String w : wrap(line, PANEL_WIDTH - 2 * PAD)) {
                            text(x, y, w, Color.LIGHT_GRAY);
                            y -= LINE;
                        }
                    }
                }
                if (ghostReason != null && !ghostValid) {
                    for (String line : wrap("Here: " + ghostReason, PANEL_WIDTH - 2 * PAD)) {
                        text(x, y, line, Color.SALMON);
                        y -= LINE;
                    }
                }
                break;
            }
            case EDGE:
                text(x, y, "Click near a tile edge to add or remove a thin wall", Color.GRAY);
                y -= LINE;
                choice(x, y, "Weight", weight, list(WeightClass.values()), true, v -> weight = v);
                y -= LINE;
                choice(x, y, "Bulkhead door", bulkhead ? "yes" : "no", list("no", "yes"), true, v -> bulkhead = v.equals("yes"));
                y -= LINE;
                break;
            case HULL:
                text(x, y, "Click to place over hull or void; R rotates, X flips", Color.GRAY);
                y -= LINE;
                text(x, y, "A new piece replaces pieces it overlaps", Color.GRAY);
                y -= LINE;
                choice(x, y, "Shape", pieceShape, list("WEDGE", "BLOCK"), true, v -> pieceShape = v);
                y -= LINE;
                choice(x, y, "Width", pieceWidth, list(1, 2, 3, 4), true, v -> pieceWidth = v);
                y -= LINE;
                choice(x, y, "Height", pieceHeight, list(1, 2, 3, 4), true, v -> pieceHeight = v);
                y -= LINE;
                choice(x, y, "Weight", weight, list(WeightClass.values()), true, v -> weight = v);
                y -= LINE;
                text(x, y, "Turn", Color.LIGHT_GRAY);
                button(x + 120f, y, rotation * 90 + " deg", false, this::rotateTool);
                button(x + 200f, y, "Flip", false, this::flipPiece);
                y -= LINE;
                if (ghostReason != null && !ghostValid) {
                    for (String line : wrap("Here: " + ghostReason, PANEL_WIDTH - 2 * PAD)) {
                        text(x, y, line, Color.SALMON);
                        y -= LINE;
                    }
                }
                break;
            case COVERING: {
                text(x, y, "Drag a rectangle to lay a covering on its floors", Color.GRAY);
                y -= LINE;
                List<FloorCovering> options = new ArrayList<>();
                for (FloorCovering f : FloorCovering.values()) if (f != FloorCovering.NONE) options.add(f);
                choice(x, y, "Covering", covering, options, true, v -> covering = v);
                y -= LINE;
                break;
            }
            case ERASE:
                text(x, y, "Removes a light, edge conduit, pipe, component, hull piece or", Color.GRAY);
                y -= LINE;
                text(x, y, "covering, else the tile", Color.GRAY);
                y -= LINE;
                break;
            case CONDUIT:
                text(x, y, "Click near a tile edge to run a conduit along it,", Color.GRAY);
                y -= LINE;
                text(x, y, "even inside a wall; click a floor's middle to lay one across it", Color.GRAY);
                y -= LINE;
                text(x, y, m.edgeConduits.size() + (m.edgeConduits.size() == 1 ? " conduit" : " conduits")
                        + " along edges", Color.WHITE);
                y -= LINE;
                break;
            case PIPE:
                text(x, y, "Pipes join tanks to life support and engines. Click", Color.GRAY);
                y -= LINE;
                text(x, y, "near an edge to run one along it, even in a wall, or", Color.GRAY);
                y -= LINE;
                text(x, y, "a tile's middle to lay one across it, under any component", Color.GRAY);
                y -= LINE;
                text(x, y, m.pipes.size() + (m.pipes.size() == 1 ? " pipe" : " pipes"), Color.WHITE);
                y -= LINE;
                break;
            case PORT:
                text(x, y, "Click a docking port: active ports extend a collar", Color.GRAY);
                y -= LINE;
                break;
            case RCS: {
                text(x, y, "Click near a hull edge that faces space to bolt on an RCS block", Color.GRAY);
                y -= LINE;
                text(x, y, "Its nozzles push the ship the other way: place them in pairs to turn and strafe", Color.GRAY);
                y -= LINE;
                float bx = x;
                for (String kind : new String[]{"LINEAR", "QUAD"}) {
                    bx += button(bx, y, rcsLabel(kind), kind.equals(rcsKind), () -> rcsKind = kind);
                }
                y -= LINE;
                int quads = 0;
                for (ChassisDef.Rcs r : m.rcs) if ("QUAD".equals(r.kind)) quads++;
                text(x, y, m.rcs.isEmpty() ? "No RCS: the ship turns and strafes on its ship's figures"
                        : (m.rcs.size() - quads) + " linear, " + quads + " quad", Color.WHITE);
                y -= LINE;
                break;
            }
            case LIGHT: {
                text(x, y, "Click near a wall to hang or take down a light", Color.GRAY);
                y -= LINE;
                text(x, y, "A light takes no room: anything can still go on its tile", Color.GRAY);
                y -= LINE;
                text(x, y, m.lights == null ? "No lights placed: the game works them out"
                        : m.lights.size() + (m.lights.size() == 1 ? " light" : " lights"), Color.WHITE);
                y -= LINE;
                float bx = x;
                bx += button(bx, y, "Auto-place lights", false, () -> autoPlaceLights());
                button(bx, y, "Clear", false, () -> {
                    if (m.lights == null || m.lights.isEmpty() || !editable()) return;
                    beginEdit();
                    m.lights.clear();
                    changed();
                    say("Took down every light");
                });
                y -= LINE;
                break;
            }
            default:
                break;
        }
        return y;
    }

    /** Replaces the chassis's lights with ones along the walls of every room. */
    void autoPlaceLights() {
        ChassisModel m = model();
        if (m == null) return;
        if (!editable()) {
            say("This chassis comes from a mod and cannot be edited here");
            return;
        }
        beginEdit();
        String error = m.autoPlaceLights(catalog());
        if (error != null) {
            undo.pop();
            say("Can't place lights: " + error);
            return;
        }
        changed();
        say("Placed " + m.lights.size() + " lights along the walls");
    }

    float swatchRow(float x, float y, int rgb, char symbol, String label, boolean selected, Runnable action) {
        float w = PANEL_WIDTH - 2 * PAD;
        if (selected) rect(x - 2, y - LINE + 5, w + 4, LINE - 1, 0.25f, 0.45f, 0.7f, 1f);
        setColor(rgb, 1f);
        rect(x, y - LINE + 7, 14, 14, color.r, color.g, color.b, 1f);
        text(x + 22, y, symbol == ' ' ? "sp" : String.valueOf(symbol), Color.WHITE);
        text(x + 46, y, label, Color.WHITE);
        building.add(new Hit(x, y - LINE + 5, w, LINE - 1, action));
        return y - LINE;
    }

    void resize(Side side, int delta) {
        ChassisModel m = model();
        if (m == null || !editable()) return;
        if (delta < 0 && (side == Side.LEFT || side == Side.RIGHT ? m.width() : m.height()) <= 1) return;
        beginEdit();
        m.resize(side, delta, catalog());
        changed();
        say((delta > 0 ? "Added" : "Removed") + " a " + (side == Side.LEFT || side == Side.RIGHT ? "column" : "row")
                + " on the " + side.name().toLowerCase(Locale.ROOT));
    }

    void newChassis(String id, ChassisModel from) {
        if (!EditorProject.validId(id)) {
            say("'" + id + "' is not a valid id: lowercase letters, digits and _");
            return;
        }
        if (project.chassis.containsKey(id)) {
            say("There is already a chassis " + id);
            return;
        }
        beginEdit();
        ChassisModel m;
        if (from != null) {
            m = from.copy();
            m.id = id;
            m.name = (from.name != null ? from.name : from.id) + " copy";
        } else {
            m = ChassisModel.blank(id, 16, 9, catalog());
        }
        project.addChassis(m);
        openChassis(id);
        say("Made chassis " + id + "; Ctrl+S writes it to chassis.toml");
    }
}
