package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.Defs;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The ship editor: makes and changes chassis layouts, tile types and ship components, and saves
 * them into the same data files the game loads ({@code assets/data/chassis.toml}, {@code tiles.toml},
 * {@code components.toml}). Run with {@code gradlew :lwjgl3:runShipEditor}, or
 * {@code demos/Ship editor.bat} on Windows. See {@code documentation/ship-editor/Design.md}.
 *
 * <p>The panel on the right has three tabs: Layout, Tiles and Components. On the Layout tab, keys
 * 1-7 pick a tool (paint, component, edge wall, hull piece, covering, erase, port), R rotates,
 * and the left mouse button uses the tool on the grid; Shift-drag with the paint tool fills a
 * rectangle. Right or middle drag pans, the wheel zooms (over the panel it scrolls), F fits the
 * ship to the view. Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, Esc quits (twice with unsaved
 * changes). Every edit is built through the game's own loader, so the panel shows what the game
 * will make of the layout, or why it refuses it. A toggles between the art pack and flat colors.
 */
public class ShipEditor extends ApplicationAdapter {

    private static final float PANEL_WIDTH = 440f;
    private static final float LINE = 20f;
    private static final float PAD = 10f;
    private static final int UNDO_LIMIT = 100;

    private enum Tab { LAYOUT, TILES, COMPONENTS }

    private enum Tool {
        PAINT("Paint"), COMPONENT("Component"), EDGE("Edge wall"), HULL("Hull piece"), COVERING("Covering"),
        ERASE("Erase"), PORT("Port");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    /** A clickable rectangle on the panel, from the last frame drawn. */
    private static final class Hit {
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
    private static final class Edit {
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
    private static final class Step {
        final EditorProject.Snapshot snapshot;
        final String chassis;

        Step(EditorProject.Snapshot snapshot, String chassis) {
            this.snapshot = snapshot;
            this.chassis = chassis;
        }
    }

    private final Path assetsDir;
    private final Path modsDir;
    private EditorProject project;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    /** The art pack; with it (and {@link #artView}) the layout is drawn as the deck will look. */
    private ArtLibrary art;
    private boolean artView = true;
    private OrthographicCamera ui;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color color = new Color();

    // Panel drawing: shapes and text are queued, then drawn in two batches
    private final List<float[]> rects = new ArrayList<>();
    private final List<Object[]> texts = new ArrayList<>();
    private List<Hit> hits = new ArrayList<>();
    private List<Hit> building = new ArrayList<>();
    private float panelScroll;
    private float panelContentHeight;

    private Tab tab = Tab.LAYOUT;
    private Tool tool = Tool.PAINT;
    private String chassisId;
    private String tileId = "hull";
    private String componentId = "reactor";
    private MountFootprint.MountSize size = MountFootprint.MountSize.S;
    private int rotation;
    private WeightClass weight = WeightClass.MEDIUM;
    private boolean bulkhead;
    private String pieceShape = "WEDGE";
    private int pieceWidth = 1, pieceHeight = 2;
    private FloorCovering covering = FloorCovering.CARPET;
    private String editTileId = "floor";
    private String editComponentId = "reactor";

    // View: pixels per tile, and where tile (0, 0)'s bottom-left corner is on screen
    private float scale = 32f, panX = 40f, panY = 40f;
    private boolean panning;
    private float lastMouseX, lastMouseY;
    private int hoverX = Integer.MIN_VALUE, hoverY = Integer.MIN_VALUE;
    private float hoverFx, hoverFy;
    private boolean stroke;
    private int strokeLastX = Integer.MIN_VALUE, strokeLastY = Integer.MIN_VALUE;
    private int rectStartX = Integer.MIN_VALUE, rectStartY;

    private Edit edit;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private String status = "";
    private float statusAge;
    private boolean quitArmed;

    private int revision;
    private int checkedRevision = -1;
    private EditorProject.Check check;
    private String ghostKey;
    private boolean ghostValid;
    private String ghostReason;

    public ShipEditor() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath());
    }

    public ShipEditor(Path assetsDir, Path modsDir) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        ui = new OrthographicCamera();
        project = EditorProject.load(assetsDir, modsDir);
        if (!project.chassis.isEmpty()) chassisId = project.chassis.keySet().iterator().next();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        fit();
        if (!project.problems.isEmpty()) say(project.problems.size() + " problem(s) loading data: " + project.problems.get(0));
        else say("Loaded " + project.chassis.size() + " chassis, " + project.tiles.size() + " tile types and "
                + project.components.size() + " components from " + project.dataDir);
        Gdx.input.setInputProcessor(new EditorInput());
    }

    @Override
    public void resize(int width, int height) {
        ui.setToOrtho(false, width, height);
        ui.update();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        art.dispose();
    }

    /**
     * Asks to close the window. False the first time while there are unsaved changes, so a second
     * close request (or Esc) is needed to lose them.
     */
    public boolean requestClose() {
        if (!project.hasUnsavedChanges() || quitArmed) return true;
        quitArmed = true;
        say("Unsaved changes: close again to quit without saving, or Ctrl+S to save");
        return false;
    }

    // ---- State ----

    private EditorProject.Item<ChassisModel> current() {
        return chassisId == null ? null : project.chassis.get(chassisId);
    }

    private ChassisModel model() {
        EditorProject.Item<ChassisModel> c = current();
        return c == null ? null : c.value;
    }

    private ShipPartCatalog catalog() {
        return project.catalog();
    }

    private boolean editable() {
        EditorProject.Item<ChassisModel> c = current();
        return c != null && !c.readOnly;
    }

    /** Call before any change: records an undo step. */
    private void beginEdit() {
        undo.push(new Step(project.snapshot(), chassisId));
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
        quitArmed = false;
    }

    /** Call after a change to the open chassis. */
    private void changed() {
        EditorProject.Item<ChassisModel> c = current();
        if (c != null) c.dirty = true;
        revision++;
    }

    private void partsChanged() {
        project.partsChanged();
        revision++;
    }

    private void undoStep() {
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

    private void redoStep() {
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

    private void save() {
        try {
            List<Path> files = project.save();
            say(files.isEmpty() ? "Nothing to save" : "Saved " + files.size() + " file(s): " + names(files));
            quitArmed = false;
        } catch (IOException e) {
            say("Save failed: " + e.getMessage());
        }
    }

    private static String names(List<Path> files) {
        StringBuilder sb = new StringBuilder();
        for (Path p : files) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.getFileName());
        }
        return sb.toString();
    }

    private void say(String message) {
        status = message;
        statusAge = 0f;
        Gdx.app.log("ShipEditor", message);
    }

    private EditorProject.Check check() {
        if (checkedRevision != revision || check == null) {
            ChassisModel m = model();
            check = m == null ? null : project.check(m);
            checkedRevision = revision;
            ghostKey = null;
        }
        return check;
    }

    private void openChassis(String id) {
        chassisId = id;
        revision++;
        fit();
    }

    /** Zooms and pans so the whole grid fits the view. */
    private void fit() {
        ChassisModel m = model();
        if (m == null) return;
        float viewW = Gdx.graphics.getWidth() - PANEL_WIDTH, viewH = Gdx.graphics.getHeight();
        scale = Math.max(4f, Math.min(64f, Math.min((viewW - 80f) / m.width(), (viewH - 80f) / m.height())));
        panX = (viewW - m.width() * scale) / 2f;
        panY = (viewH - m.height() * scale) / 2f;
    }

    // ---- Frame ----

    @Override
    public void render() {
        statusAge += Gdx.graphics.getDeltaTime();
        Gdx.gl.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(ui.combined);
        batch.setProjectionMatrix(ui.combined);
        if (model() != null) drawGrid();
        drawPanel();
    }

    private float sx(float tileX) {
        return panX + tileX * scale;
    }

    private float sy(float tileY) {
        return panY + tileY * scale;
    }

    private void setColor(int rgb, float alpha) {
        color.set(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
    }

    private void drawGrid() {
        ChassisModel m = model();
        ShipPartCatalog cat = catalog();
        EditorProject.Check c = check();
        int w = m.width(), h = m.height();

        boolean withArt = drawingArt();
        if (withArt) drawArt(m, cat);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Tiles
        for (int y = 0; y < h && !withArt; y++) {
            for (int x = 0; x < w; x++) {
                char ch = m.tiles[y][x];
                ShipPartCatalog.TileType t = cat.tileFor(ch);
                if (t != null && t.base == ShipPartCatalog.TileBase.VOID) continue;
                if (t == null) setColor(0xff00ff, 1f);
                else setColor(t.color, 1f);
                shapes.setColor(color);
                shapes.rect(sx(x), sy(y), scale, scale);
            }
        }
        // Coverings
        for (ChassisDef.Covering cov : withArt ? java.util.Collections.<ChassisDef.Covering>emptyList() : m.coverings) {
            shapes.setColor(0.75f, 0.25f, 0.2f, 0.35f);
            shapes.rect(sx(cov.x), sy(cov.y), cov.width * scale, cov.height * scale);
        }
        // Hull pieces (the art has wedges; blocks stay flat)
        for (ChassisDef.HullPiece p : m.hullPieces) {
            if (withArt && "WEDGE".equals(p.shape)) continue;
            shapes.setColor(0.55f, 0.6f, 0.68f, 1f);
            drawPiece(p.shape, p.x, p.y, p.width, p.height, p.rotation);
        }
        // Mounts
        for (ChassisModel.Mount mount : m.mounts) {
            MountFootprint f = mount.footprint(cat);
            ShipPartCatalog.ComponentType type = cat.component(mount.component);
            if (f == null || type == null || withArt) continue;
            setColor(type.color, 0.85f);
            shapes.setColor(color);
            shapes.rect(sx(f.originX) + 2, sy(f.originY) + 2, f.width() * scale - 4, f.height() * scale - 4);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        // Grid lines
        shapes.setColor(1f, 1f, 1f, 0.07f);
        for (int x = 0; x <= w; x++) shapes.line(sx(x), sy(0), sx(x), sy(h));
        for (int y = 0; y <= h; y++) shapes.line(sx(0), sy(y), sx(w), sy(y));
        shapes.setColor(1f, 1f, 1f, 0.3f);
        shapes.rect(sx(0), sy(0), w * scale, h * scale);
        // Mount outlines, port facing and active collars
        for (ChassisModel.Mount mount : m.mounts) {
            MountFootprint f = mount.footprint(cat);
            if (f == null) continue;
            if (withArt && !mount.active) continue;
            shapes.setColor(mount.active ? Color.ORANGE : Color.BLACK);
            shapes.rect(sx(f.originX) + 2, sy(f.originY) + 2, f.width() * scale - 4, f.height() * scale - 4);
            if (f.kind == MountFootprint.MountKind.PORT) {
                // A short arrow out of the middle of its outer edge
                Side out = DockingPortLayout.facing(f);
                float cx = sx(f.originX + f.width() / 2f + out.dx * f.width() / 2f);
                float cy = sy(f.originY + f.height() / 2f + out.dy * f.height() / 2f);
                shapes.setColor(Color.GREEN);
                shapes.line(cx, cy, cx + out.dx * scale * 0.7f, cy + out.dy * scale * 0.7f);
            }
        }
        // Body origin
        float ox = sx(-m.originX / m.tileSize), oy = sy(-m.originY / m.tileSize);
        shapes.setColor(0.3f, 0.8f, 1f, 0.8f);
        shapes.line(ox - 8, oy, ox + 8, oy);
        shapes.line(ox, oy - 8, ox, oy + 8);
        // Error tile
        if (c != null && c.error != null && m.inBounds(c.errorX, c.errorY)) {
            shapes.setColor(Color.RED);
            shapes.rect(sx(c.errorX) - 2, sy(c.errorY) - 2, scale + 4, scale + 4);
            shapes.rect(sx(c.errorX) - 3, sy(c.errorY) - 3, scale + 6, scale + 6);
        }
        drawGhostOutline(m, cat);
        shapes.end();

        // Edge walls, thick
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (ChassisDef.EdgeWall e : withArt ? java.util.Collections.<ChassisDef.EdgeWall>emptyList() : m.edgeWalls) {
            Side s = Side.valueOf(e.side);
            if (e.bulkhead) shapes.setColor(0.3f, 0.85f, 0.95f, 1f);
            else shapes.setColor(0.85f, 0.85f, 0.85f, 1f);
            float t = "HEAVY".equals(e.weightClass) ? 6f : "LIGHT".equals(e.weightClass) ? 2f : 4f;
            edgeRect(e.x, e.y, s, t);
        }
        if (tab == Tab.LAYOUT && tool == Tool.EDGE && m.inBounds(hoverX, hoverY)) {
            shapes.setColor(0.4f, 1f, 0.4f, 0.6f);
            edgeRect(hoverX, hoverY, nearestSide(), 4f);
        }
        drawGhostFill(m, cat);
        shapes.end();

        // Letters
        batch.begin();
        if (scale >= 12f) {
            for (ChassisModel.Mount mount : withArt ? java.util.Collections.<ChassisModel.Mount>emptyList() : m.mounts) {
                MountFootprint f = mount.footprint(cat);
                ShipPartCatalog.ComponentType type = cat.component(mount.component);
                if (f == null || type == null) continue;
                font.setColor(Color.BLACK);
                String label = type.symbol + " " + f.size;
                layout.setText(font, label);
                font.draw(batch, label, sx(f.originX + f.width() / 2f) - layout.width / 2f,
                        sy(f.originY + f.height() / 2f) + layout.height / 2f);
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (cat.tileFor(m.tiles[y][x]) != null) continue;
                    font.setColor(Color.WHITE);
                    font.draw(batch, String.valueOf(m.tiles[y][x]), sx(x) + scale * 0.3f, sy(y) + scale * 0.7f);
                }
            }
        }
        if (m.inBounds(hoverX, hoverY)) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, hoverX + "," + hoverY + "  " + describeTile(m, cat, hoverX, hoverY), 10, 20);
        }
        batch.end();
    }

    // ---- Art view ----

    private boolean drawingArt() {
        return artView && art.region("tiles/floor") != null;
    }

    /** The layout as the deck will look: floors, joined walls, wedges, systems, ports and thin walls. */
    private void drawArt(ChassisModel m, ShipPartCatalog cat) {
        int w = m.width(), h = m.height();
        batch.begin();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char ch = m.tiles[y][x];
                ShipPartCatalog.TileType t = cat.tileFor(ch);
                if (t == null || t.base == ShipPartCatalog.TileBase.VOID) continue;
                float x0 = sx(x), y0 = sy(y);
                switch (t.base) {
                    case FLOOR:
                        artTile(art.region("tiles/floor"), x0, y0, 0);
                        ChassisDef.Covering cov = m.coveringAt(x, y);
                        String covering = cov == null ? null : ArtNames.covering(FloorCovering.valueOf(cov.covering));
                        if (covering != null) artTile(art.region(covering), x0, y0, 0);
                        break;
                    case CONDUIT:
                        artTile(art.region("tiles/conduit"), x0, y0, 0);
                        TextureRegion arm = art.region(ArtNames.conduitArm(true));
                        if (conduitAt(m, cat, x, y + 1)) artTile(arm, x0, y0, 0);
                        if (conduitAt(m, cat, x - 1, y)) artTile(arm, x0, y0, 1);
                        if (conduitAt(m, cat, x, y - 1)) artTile(arm, x0, y0, 2);
                        if (conduitAt(m, cat, x + 1, y)) artTile(arm, x0, y0, 3);
                        break;
                    default:
                        ChassisDef.HullPiece piece = m.hullPieceAt(x, y);
                        if (piece != null && "WEDGE".equals(piece.shape)) break;
                        int mask = 0;
                        if (sameTile(m, x, y + 1, ch)) mask |= ArtNames.N;
                        if (sameTile(m, x + 1, y, ch)) mask |= ArtNames.E;
                        if (sameTile(m, x, y - 1, ch)) mask |= ArtNames.S;
                        if (sameTile(m, x - 1, y, ch)) mask |= ArtNames.W;
                        artTile(art.region(ArtNames.wall(wallArtId(t), mask)), x0, y0, 0);
                        break;
                }
            }
        }
        for (ChassisDef.HullPiece p : m.hullPieces) {
            if (!"WEDGE".equals(p.shape)) continue;
            TextureRegion r = art.region("walls/hull_" + p.weightClass.toLowerCase(Locale.ROOT) + "_wedge_" + p.width + "x" + p.height);
            int pw = p.rotation % 2 == 0 ? p.width : p.height, ph = p.rotation % 2 == 0 ? p.height : p.width;
            artCentered(r, sx(p.x + pw / 2f), sy(p.y + ph / 2f), p.width * scale, p.height * scale, p.rotation);
        }
        for (ChassisModel.Mount mount : m.mounts) {
            MountFootprint f = mount.footprint(cat);
            ShipPartCatalog.ComponentType type = cat.component(mount.component);
            if (f == null || type == null) continue;
            int bw = f.size.baseWidth(f.kind), bh = f.size.baseHeight(f.kind);
            float cx = sx(f.originX + f.width() / 2f), cy = sy(f.originY + f.height() / 2f);
            artCentered(art.first(ArtNames.component(type.id, type.behavior, f.size, null, true)), cx, cy,
                    bw * scale, bh * scale, f.rotation);
            if (f.kind != MountFootprint.MountKind.PORT) continue;
            // The latch collar, half a tile out past the outer edge
            Side out = DockingPortLayout.facing(f);
            float off = (bh / 2f + 0.25f) * scale;
            artCentered(art.region(ArtNames.collar(f.size, true)), cx + out.dx * off, cy + out.dy * off,
                    bw * scale, 0.5f * scale, f.rotation);
        }
        // Thin walls: a segment on each edge, a door in place of one, and a joint where they meet
        WeightClass[][] hEdges = new WeightClass[h + 1][w], vEdges = new WeightClass[h][w + 1];
        float t = scale / 4f;
        for (ChassisDef.EdgeWall e : m.edgeWalls) {
            Side side = Side.valueOf(e.side);
            WeightClass weight = WeightClass.valueOf(e.weightClass);
            boolean horizontal = side == Side.TOP || side == Side.BOTTOM;
            int ex = e.x + (side == Side.RIGHT ? 1 : 0), ey = e.y + (side == Side.TOP ? 1 : 0);
            if (horizontal && ey >= 0 && ey <= h && ex >= 0 && ex < w) hEdges[ey][ex] = weight;
            if (!horizontal && ey >= 0 && ey < h && ex >= 0 && ex <= w) vEdges[ey][ex] = weight;
            String name = e.bulkhead ? ArtNames.door(true, true, horizontal) : ArtNames.edgeWall(weight, horizontal);
            TextureRegion r = art.region(name);
            if (r == null) continue;
            if (horizontal) batch.draw(r, sx(ex), sy(ey) - t / 2f, scale, t);
            else batch.draw(r, sx(ex) - t / 2f, sy(ey), t, scale);
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x <= w; x++) {
                WeightClass n = y < h ? vEdges[y][x] : null, so = y > 0 ? vEdges[y - 1][x] : null;
                WeightClass e = x < w ? hEdges[y][x] : null, we = x > 0 ? hEdges[y][x - 1] : null;
                int mask = (n != null ? ArtNames.N : 0) | (e != null ? ArtNames.E : 0) | (so != null ? ArtNames.S : 0)
                        | (we != null ? ArtNames.W : 0);
                if (mask == 0) continue;
                WeightClass weight = WeightClass.LIGHT;
                for (WeightClass c : new WeightClass[]{n, e, so, we}) if (c != null && c.ordinal() > weight.ordinal()) weight = c;
                TextureRegion r = art.region(ArtNames.edgeJoint(weight, mask));
                if (r != null) batch.draw(r, sx(x) - t / 2f, sy(y) - t / 2f, t, t);
            }
        }
        batch.end();
    }

    /** A wall type's art: its own when the pack has it (hull, hull_heavy...), else its base's. */
    private String wallArtId(ShipPartCatalog.TileType t) {
        if (art.region(ArtNames.wall(t.id, 0)) != null) return t.id;
        if (t.base == ShipPartCatalog.TileBase.INTERNAL_WALL) return "internal_wall";
        return "hull_" + ArtNames.weight(t.weightClass);
    }

    private static boolean sameTile(ChassisModel m, int x, int y, char ch) {
        return m.inBounds(x, y) && m.tiles[y][x] == ch;
    }

    private static boolean conduitAt(ChassisModel m, ShipPartCatalog cat, int x, int y) {
        if (!m.inBounds(x, y)) return false;
        ShipPartCatalog.TileType t = cat.tileFor(m.tiles[y][x]);
        return t != null && t.base == ShipPartCatalog.TileBase.CONDUIT;
    }

    /** A one-tile region at a tile's bottom-left corner, turned {@code quarters} about its middle. */
    private void artTile(TextureRegion r, float x0, float y0, int quarters) {
        if (r != null) batch.draw(r, x0, y0, scale / 2f, scale / 2f, scale, scale, 1f, 1f, quarters * 90f);
    }

    private void artCentered(TextureRegion r, float cx, float cy, float w, float h, int quarters) {
        if (r != null) batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, quarters * 90f);
    }

    private String describeTile(ChassisModel m, ShipPartCatalog cat, int x, int y) {
        StringBuilder sb = new StringBuilder();
        ShipPartCatalog.TileType t = cat.tileFor(m.tiles[y][x]);
        sb.append(t != null ? t.name : "unknown '" + m.tiles[y][x] + "'");
        ChassisModel.Mount mount = m.mountAt(x, y, cat);
        if (mount != null) {
            ShipPartCatalog.ComponentType type = cat.component(mount.component);
            sb.append(" | ").append(type != null ? type.name : mount.component).append(' ').append(mount.size);
            if (ChassisModel.isPort(mount, cat)) sb.append(mount.active ? " (active)" : " (passive)");
        }
        if (m.hullPieceAt(x, y) != null) sb.append(" | hull piece");
        if (m.coveringAt(x, y) != null) sb.append(" | ").append(m.coveringAt(x, y).covering.toLowerCase(Locale.ROOT));
        float bx = m.originX + (x + 0.5f) * m.tileSize, by = m.originY + (y + 0.5f) * m.tileSize;
        sb.append(String.format(Locale.ROOT, " | body %.1f, %.1f m", bx, by));
        return sb.toString();
    }

    private void edgeRect(int x, int y, Side s, float t) {
        switch (s) {
            case TOP: shapes.rect(sx(x), sy(y + 1) - t / 2, scale, t); break;
            case BOTTOM: shapes.rect(sx(x), sy(y) - t / 2, scale, t); break;
            case LEFT: shapes.rect(sx(x) - t / 2, sy(y), t, scale); break;
            default: shapes.rect(sx(x + 1) - t / 2, sy(y), t, scale); break;
        }
    }

    private void drawPiece(String shape, int x, int y, int baseW, int baseH, int rot) {
        int w = rot % 2 == 0 ? baseW : baseH, h = rot % 2 == 0 ? baseH : baseW;
        float x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
        if (!"WEDGE".equals(shape)) {
            shapes.rect(x0, y0, x1 - x0, y1 - y0);
            return;
        }
        // The right angle sits at the bottom-left corner at rotation 0 and turns counter-clockwise
        switch (Math.floorMod(rot, 4)) {
            case 0: shapes.triangle(x0, y0, x1, y0, x0, y1); break;
            case 1: shapes.triangle(x1, y0, x1, y1, x0, y0); break;
            case 2: shapes.triangle(x1, y1, x0, y1, x1, y0); break;
            default: shapes.triangle(x0, y1, x0, y0, x1, y1); break;
        }
    }

    // ---- Ghosts: what the tool would do at the cursor ----

    private int[] ghostOrigin(MountFootprint probe) {
        return new int[]{hoverX - (probe.width() - 1) / 2, hoverY - (probe.height() - 1) / 2};
    }

    private MountFootprint ghostFootprint(ShipPartCatalog cat) {
        ShipPartCatalog.ComponentType type = cat.component(componentId);
        if (type == null || !size.exists(type.mountKind())) return null;
        MountFootprint probe = new MountFootprint(type.mountKind(), size, 0, 0, rotation);
        int[] o = ghostOrigin(probe);
        return new MountFootprint(type.mountKind(), size, o[0], o[1], rotation);
    }

    /** Tries the component tool at the cursor on a copy, and remembers whether the game would take it. */
    private void updateGhost(ChassisModel m, MountFootprint f) {
        String key = revision + ":" + componentId + ":" + size + ":" + rotation + ":" + f.originX + "," + f.originY;
        if (key.equals(ghostKey)) return;
        ghostKey = key;
        ChassisModel copy = m.copy();
        copy.place(componentId, size, f.originX, f.originY, rotation, catalog());
        EditorProject.Check before = check();
        EditorProject.Check after = project.check(copy);
        ShipPartCatalog.ComponentType type = catalog().component(componentId);
        if (type != null && !type.sizes.contains(size)) {
            ghostValid = false;
            ghostReason = type.name + " does not come in size " + size;
        } else if (after.error == null || (before.error != null && before.error.equals(after.error))) {
            ghostValid = true;
            ghostReason = null;
        } else {
            ghostValid = false;
            ghostReason = after.error;
        }
    }

    private void drawGhostOutline(ChassisModel m, ShipPartCatalog cat) {
        if (tab != Tab.LAYOUT || !m.inBounds(hoverX, hoverY) || panning) return;
        if (rectStartX != Integer.MIN_VALUE) {
            shapes.setColor(Color.YELLOW);
            int x0 = Math.min(rectStartX, hoverX), y0 = Math.min(rectStartY, hoverY);
            shapes.rect(sx(x0), sy(y0), (Math.abs(hoverX - rectStartX) + 1) * scale, (Math.abs(hoverY - rectStartY) + 1) * scale);
            return;
        }
        if (tool == Tool.COMPONENT) {
            MountFootprint f = ghostFootprint(cat);
            if (f == null) return;
            updateGhost(m, f);
            shapes.setColor(ghostValid ? Color.GREEN : Color.RED);
            shapes.rect(sx(f.originX), sy(f.originY), f.width() * scale, f.height() * scale);
        } else if (tool == Tool.HULL) {
            int w = rotation % 2 == 0 ? pieceWidth : pieceHeight, h = rotation % 2 == 0 ? pieceHeight : pieceWidth;
            shapes.setColor(Color.YELLOW);
            shapes.rect(sx(hoverX), sy(hoverY), w * scale, h * scale);
        } else if (tool != Tool.EDGE) {
            shapes.setColor(Color.YELLOW);
            shapes.rect(sx(hoverX), sy(hoverY), scale, scale);
        }
    }

    private void drawGhostFill(ChassisModel m, ShipPartCatalog cat) {
        if (tab != Tab.LAYOUT || !m.inBounds(hoverX, hoverY) || panning) return;
        if (tool == Tool.COMPONENT) {
            MountFootprint f = ghostFootprint(cat);
            ShipPartCatalog.ComponentType type = cat.component(componentId);
            if (f == null || type == null) return;
            setColor(type.color, 0.4f);
            shapes.setColor(color);
            shapes.rect(sx(f.originX), sy(f.originY), f.width() * scale, f.height() * scale);
        } else if (tool == Tool.HULL) {
            shapes.setColor(0.7f, 0.75f, 0.8f, 0.5f);
            drawPiece(pieceShape, hoverX, hoverY, pieceWidth, pieceHeight, rotation);
        } else if (tool == Tool.PAINT) {
            ShipPartCatalog.TileType t = cat.tile(tileId);
            if (t == null) return;
            setColor(t.color, 0.6f);
            shapes.setColor(color);
            shapes.rect(sx(hoverX), sy(hoverY), scale, scale);
        }
    }

    private Side nearestSide() {
        float fx = hoverFx - hoverX, fy = hoverFy - hoverY;
        float dl = fx, dr = 1 - fx, db = fy, dt = 1 - fy;
        float min = Math.min(Math.min(dl, dr), Math.min(db, dt));
        if (min == dl) return Side.LEFT;
        if (min == dr) return Side.RIGHT;
        if (min == db) return Side.BOTTOM;
        return Side.TOP;
    }

    // ---- Grid tools ----

    private void toolDown(boolean shift) {
        ChassisModel m = model();
        if (m == null || !m.inBounds(hoverX, hoverY)) return;
        if (!editable()) {
            say("This chassis comes from a mod and cannot be edited here");
            return;
        }
        ShipPartCatalog cat = catalog();
        switch (tool) {
            case PAINT:
                if (shift) {
                    rectStartX = hoverX;
                    rectStartY = hoverY;
                    return;
                }
                if (cat.tile(tileId) == null) return;
                beginEdit();
                stroke = true;
                strokeTo(m, cat);
                break;
            case ERASE:
                beginEdit();
                stroke = true;
                strokeTo(m, cat);
                break;
            case COVERING:
                rectStartX = hoverX;
                rectStartY = hoverY;
                break;
            case COMPONENT: {
                MountFootprint f = ghostFootprint(cat);
                if (f == null) return;
                beginEdit();
                m.place(componentId, size, f.originX, f.originY, rotation, cat);
                changed();
                ShipPartCatalog.ComponentType type = cat.component(componentId);
                say("Placed " + (type != null ? type.name : componentId) + " " + size + " at " + f.originX + "," + f.originY);
                break;
            }
            case EDGE: {
                Side side = nearestSide();
                beginEdit();
                m.toggleEdgeWall(hoverX, hoverY, side, weight.name(), bulkhead);
                changed();
                break;
            }
            case HULL:
                beginEdit();
                m.addHullPiece(pieceShape, hoverX, hoverY, pieceWidth, pieceHeight, rotation, weight.name());
                changed();
                break;
            case PORT: {
                ChassisModel.Mount mount = m.mountAt(hoverX, hoverY, cat);
                if (mount == null || !ChassisModel.isPort(mount, cat)) {
                    say("No docking port here");
                    return;
                }
                beginEdit();
                mount.active = !mount.active;
                changed();
                say("Port " + (mount.active ? "active: it extends a latch collar" : "passive: magnets only"));
                break;
            }
            default:
                break;
        }
    }

    /**
     * Paints or erases every tile from the last one the stroke reached to the hovered one, so a
     * fast drag leaves no gaps. Each tile is done once per visit.
     */
    private void strokeTo(ChassisModel m, ShipPartCatalog cat) {
        if (hoverX == strokeLastX && hoverY == strokeLastY) return;
        int x0 = strokeLastX == Integer.MIN_VALUE ? hoverX : strokeLastX;
        int y0 = strokeLastX == Integer.MIN_VALUE ? hoverY : strokeLastY;
        int steps = Math.max(Math.abs(hoverX - x0), Math.abs(hoverY - y0));
        boolean first = strokeLastX == Integer.MIN_VALUE;
        for (int i = first ? 0 : 1; i <= steps; i++) {
            int x = steps == 0 ? hoverX : Math.round(x0 + (hoverX - x0) * (float) i / steps);
            int y = steps == 0 ? hoverY : Math.round(y0 + (hoverY - y0) * (float) i / steps);
            if (!m.inBounds(x, y)) continue;
            if (tool == Tool.PAINT) {
                ShipPartCatalog.TileType t = cat.tile(tileId);
                if (t != null) m.paint(x, y, t.symbol, cat);
            } else if (tool == Tool.ERASE) {
                say("Erased " + m.erase(x, y, cat));
            }
        }
        strokeLastX = hoverX;
        strokeLastY = hoverY;
        changed();
    }

    private void toolUp() {
        ChassisModel m = model();
        stroke = false;
        strokeLastX = strokeLastY = Integer.MIN_VALUE;
        if (rectStartX == Integer.MIN_VALUE || m == null) return;
        int x0 = rectStartX, y0 = rectStartY;
        rectStartX = Integer.MIN_VALUE;
        int x1 = Math.max(0, Math.min(m.width() - 1, hoverX)), y1 = Math.max(0, Math.min(m.height() - 1, hoverY));
        ShipPartCatalog cat = catalog();
        beginEdit();
        if (tool == Tool.PAINT) {
            ShipPartCatalog.TileType t = cat.tile(tileId);
            if (t != null) m.fill(x0, y0, x1, y1, t.symbol, cat);
        } else if (tool == Tool.COVERING) {
            m.addCovering(covering.name(), x0, y0, x1, y1);
            say("Laid " + covering.name().toLowerCase(Locale.ROOT));
        }
        changed();
    }

    // ---- Panel ----

    private float panelX() {
        return Gdx.graphics.getWidth() - PANEL_WIDTH;
    }

    private void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        rects.add(new float[]{x, y, w, h, r, g, b, a});
    }

    private void text(float x, float y, String s, Color c) {
        texts.add(new Object[]{x, y, s, new Color(c)});
    }

    /** A button; returns its width. */
    private float button(float x, float y, String label, boolean selected, Runnable action) {
        layout.setText(font, label);
        float w = layout.width + 12f, h = LINE - 2f;
        if (selected) rect(x, y - h + 4, w, h, 0.25f, 0.45f, 0.7f, 1f);
        else rect(x, y - h + 4, w, h, 0.18f, 0.2f, 0.25f, 1f);
        text(x + 6, y, label, Color.WHITE);
        building.add(new Hit(x, y - h + 4, w, h, action));
        return w + 4f;
    }

    /** "label: value", which turns into a text field when clicked. */
    private void field(float x, float y, String key, String label, String value, boolean enabled, Consumer<String> commit) {
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
    private <T> void choice(float x, float y, String label, T value, List<T> options, boolean enabled, Consumer<T> pick) {
        text(x, y, label, Color.LIGHT_GRAY);
        float vx = x + 120f;
        float bx = vx;
        for (T option : options) {
            final T o = option;
            bx += button(bx, y, String.valueOf(option), option.equals(value), enabled ? () -> pick.accept(o) : () -> { });
        }
    }

    private void startEdit(String key, String value, Consumer<String> commit) {
        commitEdit();
        edit = new Edit(key, value, commit);
    }

    private void commitEdit() {
        if (edit == null) return;
        Edit e = edit;
        edit = null;
        e.commit.accept(e.text.toString());
    }

    private void drawPanel() {
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
        bx += button(bx, y, "Tiles", tab == Tab.TILES, () -> tab = Tab.TILES);
        bx += button(bx, y, "Components", tab == Tab.COMPONENTS, () -> tab = Tab.COMPONENTS);
        String unsaved = project.hasUnsavedChanges() ? "unsaved *" : "saved";
        button(bx + 10, y, "Save (" + unsaved + ")", false, this::save);
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
        text(x, LINE * 1.0f, "Ctrl+S save  Ctrl+Z/Y undo/redo  F fit  Esc quit", Color.GRAY);

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

    private float layoutPanel(float x, float y) {
        ShipPartCatalog cat = catalog();
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
        ChassisModel m = model();
        if (m == null) return y - LINE;
        boolean ed = editable();
        y -= LINE * 1.4f;
        EditorProject.Item<ChassisModel> item = current();
        text(x, y, item.id + "  " + m.width() + " x " + m.height() + " tiles"
                + (item.source != null ? "  (" + item.source + ")" : "  (new)"), Color.CYAN);
        y -= LINE;
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
            String label = (i + 1) + " " + t.label;
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
            text(x, y, c.systems + " systems, " + c.poweredSystems + " reached by power", Color.WHITE);
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

    private float toolOptions(float x, float y, ChassisModel m, ShipPartCatalog cat) {
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
                text(x, y, "Click to install; R rotates (" + rotation * 90 + " deg)", Color.GRAY);
                y -= LINE;
                for (ShipPartCatalog.ComponentType t : cat.components()) {
                    final String id = t.id;
                    y = swatchRow(x, y, t.color, t.symbol, t.name + "  (" + t.behavior.name().toLowerCase(Locale.ROOT) + ")",
                            id.equals(componentId), () -> componentId = id);
                }
                ShipPartCatalog.ComponentType type = cat.component(componentId);
                // Keep the size one the selected component comes in
                if (type != null && !type.sizes.contains(size) && !type.sizes.isEmpty()) size = type.sizes.iterator().next();
                if (type != null) {
                    List<MountFootprint.MountSize> sizes = new ArrayList<>(type.sizes);
                    choice(x, y, "Size", size, sizes, true, s -> size = s);
                    y -= LINE;
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
                text(x, y, "Click to place over hull or void; R rotates", Color.GRAY);
                y -= LINE;
                choice(x, y, "Shape", pieceShape, list("WEDGE", "BLOCK"), true, v -> pieceShape = v);
                y -= LINE;
                choice(x, y, "Width", pieceWidth, list(1, 2, 3, 4), true, v -> pieceWidth = v);
                y -= LINE;
                choice(x, y, "Height", pieceHeight, list(1, 2, 3, 4), true, v -> pieceHeight = v);
                y -= LINE;
                choice(x, y, "Weight", weight, list(WeightClass.values()), true, v -> weight = v);
                y -= LINE;
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
                text(x, y, "Removes a component, hull piece or covering, else the tile", Color.GRAY);
                y -= LINE;
                break;
            case PORT:
                text(x, y, "Click a docking port: active ports extend a collar", Color.GRAY);
                y -= LINE;
                break;
            default:
                break;
        }
        return y;
    }

    private float swatchRow(float x, float y, int rgb, char symbol, String label, boolean selected, Runnable action) {
        float w = PANEL_WIDTH - 2 * PAD;
        if (selected) rect(x - 2, y - LINE + 5, w + 4, LINE - 1, 0.25f, 0.45f, 0.7f, 1f);
        setColor(rgb, 1f);
        rect(x, y - LINE + 7, 14, 14, color.r, color.g, color.b, 1f);
        text(x + 22, y, symbol == ' ' ? "sp" : String.valueOf(symbol), Color.WHITE);
        text(x + 46, y, label, Color.WHITE);
        building.add(new Hit(x, y - LINE + 5, w, LINE - 1, action));
        return y - LINE;
    }

    private void resize(Side side, int delta) {
        ChassisModel m = model();
        if (m == null || !editable()) return;
        if (delta < 0 && (side == Side.LEFT || side == Side.RIGHT ? m.width() : m.height()) <= 1) return;
        beginEdit();
        m.resize(side, delta, catalog());
        changed();
        say((delta > 0 ? "Added" : "Removed") + " a " + (side == Side.LEFT || side == Side.RIGHT ? "column" : "row")
                + " on the " + side.name().toLowerCase(Locale.ROOT));
    }

    private void newChassis(String id, ChassisModel from) {
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

    // ---- Tiles tab ----

    private float tilesPanel(float x, float y) {
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

    private void tileChanged(EditorProject.Item<TileTypeDef> item) {
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

    private void newTile(String id) {
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

    private float componentsPanel(float x, float y) {
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
        return y;
    }

    /** Where a definition comes from, for the panel. */
    private static String origin(EditorProject.Item<?> item, String defaultFile) {
        if (item.source != null) return item.source;
        return (item.dirty ? "new" : "built in") + "; saves to " + defaultFile;
    }

    private static String sizeHint(MountFootprint.MountKind kind) {
        switch (kind) {
            case PRIMARY: return " (square: S 1x1, M 2x2, L 3x3, XL 5x5)";
            case AUXILIARY: return " (slot: S 1x2, M 2x3, L 3x7, XL 5x12)";
            default: return " (hull edge: S 3x1, M 5x2, L 7x3)";
        }
    }

    private void setBehavior(EditorProject.Item<ShipComponentDef> item, String behavior) {
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

    private void toggleSize(EditorProject.Item<ShipComponentDef> item, String size, MountFootprint.MountKind kind) {
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

    private void componentChanged(EditorProject.Item<ShipComponentDef> item) {
        item.dirty = true;
        partsChanged();
        for (EditorProject.Item<ChassisModel> c : project.chassis.values()) {
            if (!c.readOnly && c.value.usesComponent(item.id)) c.dirty = true;
        }
    }

    private void newComponent(String id) {
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

    // ---- Helpers ----

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        List<T> out = new ArrayList<>();
        for (T v : values) out.add(v);
        return out;
    }

    private static Float parseFloat(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean validColor(String s) {
        return s.matches("#?[0-9a-fA-F]{6}");
    }

    private static int colorOf(String s) {
        try {
            return ShipPartCatalog.parseColor(s, 0x808080);
        } catch (IllegalArgumentException e) {
            return 0x808080;
        }
    }

    private List<String> wrap(String s, float width) {
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
    private static final class TomlNum {
        static String of(float v) {
            if (v == Math.rint(v)) return Integer.toString((int) v);
            return Float.toString(v);
        }
    }

    // ---- Input ----

    private final class EditorInput extends InputAdapter {
        /** Ctrl or Cmd, tracked from key events (held-key polling misses it under some window systems). */
        private boolean ctrlDown;

        private float uiY(int screenY) {
            return Gdx.graphics.getHeight() - screenY;
        }

        private void hover(int screenX, int screenY) {
            float fx = (screenX - panX) / scale, fy = (uiY(screenY) - panY) / scale;
            hoverFx = fx;
            hoverFy = fy;
            if (screenX >= panelX()) {
                hoverX = hoverY = Integer.MIN_VALUE;
                return;
            }
            hoverX = (int) Math.floor(fx);
            hoverY = (int) Math.floor(fy);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            hover(screenX, screenY);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            hover(screenX, screenY);
            lastMouseX = screenX;
            lastMouseY = screenY;
            if (screenX >= panelX()) {
                float y = uiY(screenY);
                for (Hit h : hits) {
                    if (h.contains(screenX, y)) {
                        // Clicking a field that is being typed in keeps it open
                        h.action.run();
                        return true;
                    }
                }
                commitEdit();
                return true;
            }
            commitEdit();
            if (button == com.badlogic.gdx.Input.Buttons.RIGHT || button == com.badlogic.gdx.Input.Buttons.MIDDLE) {
                panning = true;
                return true;
            }
            if (button == com.badlogic.gdx.Input.Buttons.LEFT && tab == Tab.LAYOUT) {
                toolDown(Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT));
            }
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (panning) {
                panX += screenX - lastMouseX;
                panY -= screenY - lastMouseY;
            }
            lastMouseX = screenX;
            lastMouseY = screenY;
            hover(screenX, screenY);
            ChassisModel m = model();
            if (stroke && m != null) strokeTo(m, catalog());
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            hover(screenX, screenY);
            if (panning) {
                panning = false;
                return true;
            }
            if (button == com.badlogic.gdx.Input.Buttons.LEFT) toolUp();
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            int mx = Gdx.input.getX(), my = Gdx.input.getY();
            if (mx >= panelX()) {
                float max = Math.max(0f, panelContentHeight - Gdx.graphics.getHeight() + LINE * 5);
                // Scrolling down lifts the content: the offset goes negative
                panelScroll = Math.max(-max, Math.min(0f, panelScroll - amountY * LINE * 2));
                return true;
            }
            float before = scale;
            scale = Math.max(4f, Math.min(96f, scale * (float) Math.pow(1.15f, -amountY)));
            float cx = mx, cy = uiY(my);
            panX = cx - (cx - panX) * scale / before;
            panY = cy - (cy - panY) * scale / before;
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.CONTROL_LEFT || keycode == com.badlogic.gdx.Input.Keys.CONTROL_RIGHT
                    || keycode == com.badlogic.gdx.Input.Keys.SYM) {
                ctrlDown = true;
                return true;
            }
            boolean ctrl = ctrlDown;
            if (edit != null) {
                if (keycode == com.badlogic.gdx.Input.Keys.ENTER || keycode == com.badlogic.gdx.Input.Keys.NUMPAD_ENTER) {
                    commitEdit();
                } else if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                    edit = null;
                } else if (keycode == com.badlogic.gdx.Input.Keys.BACKSPACE && edit.text.length() > 0) {
                    edit.text.setLength(edit.text.length() - 1);
                } else if (ctrl && keycode == com.badlogic.gdx.Input.Keys.V) {
                    String clip = Gdx.app.getClipboard().getContents();
                    if (clip != null) edit.text.append(clip.replace("\n", " "));
                }
                return true;
            }
            if (ctrl) {
                if (keycode == com.badlogic.gdx.Input.Keys.S) save();
                else if (keycode == com.badlogic.gdx.Input.Keys.Z) undoStep();
                else if (keycode == com.badlogic.gdx.Input.Keys.Y) redoStep();
                return true;
            }
            switch (keycode) {
                case com.badlogic.gdx.Input.Keys.ESCAPE:
                    if (!project.hasUnsavedChanges() || quitArmed) Gdx.app.exit();
                    else requestClose();
                    return true;
                case com.badlogic.gdx.Input.Keys.F:
                    fit();
                    return true;
                case com.badlogic.gdx.Input.Keys.A:
                    artView = !artView;
                    say(drawingArt() ? "Showing the art pack" : art.isEmpty() ? "No art atlas built; showing flat colors"
                            : "Showing flat colors");
                    return true;
                case com.badlogic.gdx.Input.Keys.R:
                    rotation = (rotation + 1) % 4;
                    say("Rotation " + rotation * 90 + " deg");
                    return true;
                case com.badlogic.gdx.Input.Keys.TAB:
                    tab = Tab.values()[(tab.ordinal() + 1) % Tab.values().length];
                    return true;
                default:
                    break;
            }
            int n = keycode - com.badlogic.gdx.Input.Keys.NUM_1;
            if (n >= 0 && n < Tool.values().length) {
                tool = Tool.values()[n];
                tab = Tab.LAYOUT;
                return true;
            }
            return false;
        }

        @Override
        public boolean keyUp(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.CONTROL_LEFT || keycode == com.badlogic.gdx.Input.Keys.CONTROL_RIGHT
                    || keycode == com.badlogic.gdx.Input.Keys.SYM) {
                ctrlDown = false;
            }
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (edit == null) return false;
            if (character >= 32 && character != 127) edit.text.append(character);
            return true;
        }
    }
}
