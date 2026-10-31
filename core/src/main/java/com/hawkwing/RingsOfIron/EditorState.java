package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.EditorProject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@link ShipEditor}'s state: the project, what is open and selected, the view, undo and redo,
 * and the small helpers every part of the editor uses. Each layer above it adds one part of the
 * editor: {@link EditorCanvas} draws the grid, {@link EditorTools} uses the tools on it,
 * {@link EditorPanel} and {@link EditorCatalogTabs} lay out the panel.
 */
abstract class EditorState extends ApplicationAdapter {
    static final float PANEL_WIDTH = 440f;
    static final float LINE = 20f;
    static final float PAD = 10f;
    static final int UNDO_LIMIT = 100;

    enum Tab { LAYOUT, TILES, COMPONENTS }

    enum Tool {
        PAINT("Paint"), COMPONENT("Component"), EDGE("Edge wall"), HULL("Hull piece"), COVERING("Covering"),
        ERASE("Erase"), PORT("Port"), LIGHT("Light"), CONDUIT("Conduit"), PIPE("Pipe"), RCS("RCS");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    /** A clickable rectangle on the panel, from the last frame drawn. */
    static final class Hit {
        final float x, y, w, h;
        final Runnable action;

        Hit(float x, float y, float w, float h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** A text field being typed into. */
    static final class Edit {
        final String key;
        final StringBuilder text;
        final Consumer<String> commit;

        Edit(String key, String value, Consumer<String> commit) {
            this.key = key;
            this.text = new StringBuilder(value);
            this.commit = commit;
        }
    }

    /** An undo step: everything, and which chassis was open. */
    static final class Step {
        final EditorProject.Snapshot snapshot;
        final String chassis;

        Step(EditorProject.Snapshot snapshot, String chassis) {
            this.snapshot = snapshot;
            this.chassis = chassis;
        }
    }

    final Path assetsDir;
    final Path modsDir;
    /** The yard refit this editor is open for, or null for the editor on its own. */
    final ShipEditor.Refit refit;
    EditorProject project;

    ShapeRenderer shapes;
    SpriteBatch batch;
    BitmapFont font;
    /** The art pack; with it (and {@link #artView}) the layout is drawn as the deck will look. */
    ArtLibrary art;
    boolean artView = true;
    OrthographicCamera ui;
    final GlyphLayout layout = new GlyphLayout();
    final Color color = new Color();

    // Panel drawing: shapes and text are queued, then drawn in two batches
    final List<float[]> rects = new ArrayList<>();
    final List<Object[]> texts = new ArrayList<>();
    List<Hit> hits = new ArrayList<>();
    List<Hit> building = new ArrayList<>();
    float panelScroll;
    float panelContentHeight;

    Tab tab = Tab.LAYOUT;
    Tool tool = Tool.PAINT;
    /** The kind of RCS block the RCS tool bolts on. */
    String rcsKind = "QUAD";
    String chassisId;
    String tileId = "hull";
    String componentId = "reactor";
    MountFootprint.MountSize size = MountFootprint.MountSize.S;
    int rotation;
    WeightClass weight = WeightClass.MEDIUM;
    boolean bulkhead;
    String pieceShape = "WEDGE";
    int pieceWidth = 1, pieceHeight = 2;
    FloorCovering covering = FloorCovering.CARPET;
    String editTileId = "floor";
    String editComponentId = "reactor";

    // View: pixels per tile, and where tile (0, 0)'s bottom-left corner is on screen
    float scale = 32f, panX = 40f, panY = 40f;
    boolean panning;
    float lastMouseX, lastMouseY;
    int hoverX = Integer.MIN_VALUE, hoverY = Integer.MIN_VALUE;
    float hoverFx, hoverFy;
    boolean stroke;
    int strokeLastX = Integer.MIN_VALUE, strokeLastY = Integer.MIN_VALUE;
    int rectStartX = Integer.MIN_VALUE, rectStartY;

    Edit edit;
    final Deque<Step> undo = new ArrayDeque<>();
    final Deque<Step> redo = new ArrayDeque<>();
    String status = "";
    float statusAge;
    boolean quitArmed;

    int revision;
    /** The yard's price for the refit as drawn, and the revision it was worked out at. */
    String quote;
    int quotedRevision = -1;
    int checkedRevision = -1;
    EditorProject.Check check;
    String ghostKey;
    boolean ghostValid;
    String ghostReason;

    EditorState(Path assetsDir, Path modsDir, ShipEditor.Refit refit) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
        this.refit = refit;
    }

    abstract void applyRefit();

    abstract float tilesPanel(float x, float y);

    abstract float componentsPanel(float x, float y);

    // ---- State ----

    EditorProject.Item<ChassisModel> current() {
        return chassisId == null ? null : project.chassis.get(chassisId);
    }

    ChassisModel model() {
        EditorProject.Item<ChassisModel> c = current();
        return c == null ? null : c.value;
    }

    ShipPartCatalog catalog() {
        return project.catalog();
    }

    boolean editable() {
        EditorProject.Item<ChassisModel> c = current();
        return c != null && !c.readOnly;
    }

    /** Call before any change: records an undo step. */
    void beginEdit() {
        undo.push(new Step(project.snapshot(), chassisId));
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
        quitArmed = false;
    }

    /** Call after a change to the open chassis. */
    void changed() {
        EditorProject.Item<ChassisModel> c = current();
        if (c != null) c.dirty = true;
        revision++;
    }

    void partsChanged() {
        project.partsChanged();
        revision++;
    }

    void undoStep() {
        if (undo.isEmpty()) {
            say("Nothing to undo");
            return;
        }
        redo.push(new Step(project.snapshot(), chassisId));
        Step s = undo.pop();
        project.restore(s.snapshot);
        if (s.chassis != null && project.chassis.containsKey(s.chassis)) chassisId = s.chassis;
        revision++;
        say("Undone");
    }

    void redoStep() {
        if (redo.isEmpty()) {
            say("Nothing to redo");
            return;
        }
        undo.push(new Step(project.snapshot(), chassisId));
        Step s = redo.pop();
        project.restore(s.snapshot);
        if (s.chassis != null && project.chassis.containsKey(s.chassis)) chassisId = s.chassis;
        revision++;
        say("Redone");
    }

    void save() {
        try {
            List<Path> files = project.save();
            say(files.isEmpty() ? "Nothing to save" : "Saved " + files.size() + " file(s): " + names(files));
            quitArmed = false;
        } catch (IOException e) {
            say("Save failed: " + e.getMessage());
        }
    }

    static String names(List<Path> files) {
        StringBuilder sb = new StringBuilder();
        for (Path p : files) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.getFileName());
        }
        return sb.toString();
    }

    void say(String message) {
        status = message;
        statusAge = 0f;
        Gdx.app.log("ShipEditor", message);
    }

    EditorProject.Check check() {
        if (checkedRevision != revision || check == null) {
            ChassisModel m = model();
            check = m == null ? null : project.check(m);
            checkedRevision = revision;
            ghostKey = null;
        }
        return check;
    }

    void openChassis(String id) {
        // A refit edits the one ship's layout
        if (refit != null && !id.equals(chassisId)) return;
        chassisId = id;
        revision++;
        fit();
    }

    /** Zooms and pans so the whole grid fits the view. */
    void fit() {
        ChassisModel m = model();
        if (m == null) return;
        float viewW = Gdx.graphics.getWidth() - PANEL_WIDTH, viewH = Gdx.graphics.getHeight();
        scale = Math.max(4f, Math.min(64f, Math.min((viewW - 80f) / m.width(), (viewH - 80f) / m.height())));
        panX = (viewW - m.width() * scale) / 2f;
        panY = (viewH - m.height() * scale) / 2f;
    }

    // ---- Helpers ----

    @SafeVarargs
    static <T> List<T> list(T... values) {
        List<T> out = new ArrayList<>();
        for (T v : values) out.add(v);
        return out;
    }

    static Float parseFloat(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean validColor(String s) {
        return s.matches("#?[0-9a-fA-F]{6}");
    }

    static int colorOf(String s) {
        try {
            return ShipPartCatalog.parseColor(s, 0x808080);
        } catch (IllegalArgumentException e) {
            return 0x808080;
        }
    }

    List<String> wrap(String s, float width) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            layout.setText(font, next);
            if (layout.width > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** Short number text for fields: 1 not 1.0, but 2.5 as is. */
    static final class TomlNum {
        static String of(float v) {
            if (v == Math.rint(v)) return Integer.toString((int) v);
            return Float.toString(v);
        }
    }

    static String rcsLabel(String kind) {
        return "QUAD".equals(kind) ? "Quad (3 nozzles)" : "Linear (1 nozzle)";
    }
}
