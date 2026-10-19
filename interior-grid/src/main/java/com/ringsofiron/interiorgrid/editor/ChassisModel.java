package com.ringsofiron.interiorgrid.editor;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.interiorgrid.WallLightLayout;
import com.ringsofiron.moddingdata.defs.ChassisDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A chassis layout as the ship editor edits it: a grid of tile-type symbols with every mounted
 * component, edge wall, edge conduit, hull piece and covering listed on top. It converts to and from the
 * {@link ChassisDef} the game loads; {@link #toDef} decides which mounts can be drawn as letters
 * in the rows and which must be listed.
 *
 * <p>Grid coordinates have (0, 0) at the bottom-left tile and +y up, as in the game.
 */
public class ChassisModel {

    /** A component installed in the layout. */
    public static class Mount {
        public String component;
        public MountFootprint.MountSize size;
        public int x, y, rotation;
        /** Written to {@code mounts} even when it could be drawn in the rows (it was listed there). */
        public boolean listed;
        /** Docking ports: extends a latch collar when armed. */
        public boolean active;

        public Mount() {
        }

        public Mount(String component, MountFootprint.MountSize size, int x, int y, int rotation) {
            this.component = component;
            this.size = size;
            this.x = x;
            this.y = y;
            this.rotation = Math.floorMod(rotation, 4);
        }

        /** Its footprint, or null if the component is unknown or has no mount of this size. */
        public MountFootprint footprint(ShipPartCatalog catalog) {
            ShipPartCatalog.ComponentType type = catalog.component(component);
            if (type == null || !size.exists(type.mountKind())) return null;
            return new MountFootprint(type.mountKind(), size, x, y, rotation);
        }

        Mount copy() {
            Mount m = new Mount(component, size, x, y, rotation);
            m.listed = listed;
            m.active = active;
            return m;
        }
    }

    public String id;
    public String name;
    public boolean station;
    public float tileSize = 1f;
    public float originX, originY;
    /** Tile-type symbols, {@code [y][x]}. Mounts are not drawn here. */
    public char[][] tiles;
    public final List<Mount> mounts = new ArrayList<>();
    public final List<ChassisDef.EdgeWall> edgeWalls = new ArrayList<>();
    /** Power conduits along tile edges, each listed once (on either tile it lies between). */
    public final List<ChassisDef.EdgeConduit> edgeConduits = new ArrayList<>();
    public final List<ChassisDef.HullPiece> hullPieces = new ArrayList<>();
    public final List<ChassisDef.Covering> coverings = new ArrayList<>();
    /** Lights hung on walls; null until the layout places any, and the game works them out. */
    public List<ChassisDef.Light> lights;
    /** Listed mounts and port settings that name nothing the editor knows; written back as they were. */
    public final List<ChassisDef.Mount> unresolvedMounts = new ArrayList<>();
    public final List<ChassisDef.Port> unresolvedPorts = new ArrayList<>();

    public int width() {
        return tiles.length == 0 ? 0 : tiles[0].length;
    }

    public int height() {
        return tiles.length;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width() && y < height();
    }

    /** A new layout: a hull border around steel floor. */
    public static ChassisModel blank(String id, int width, int height, ShipPartCatalog catalog) {
        ChassisModel m = new ChassisModel();
        m.id = id;
        m.name = id;
        m.tiles = new char[height][width];
        char hull = symbolFor(catalog, ShipPartCatalog.TileBase.HULL, '#');
        char floor = symbolFor(catalog, ShipPartCatalog.TileBase.FLOOR, '.');
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                m.tiles[y][x] = edge ? hull : floor;
            }
        }
        m.centerOrigin();
        return m;
    }

    /** Reads a chassis definition. Letter rectangles that are legal mounts become mounts. */
    public static ChassisModel fromDef(ChassisDef def, ShipPartCatalog catalog) {
        ChassisModel m = new ChassisModel();
        m.id = def.id;
        m.name = def.name;
        m.station = def.station;
        m.tileSize = def.tileSize;
        m.originX = def.originX;
        m.originY = def.originY;
        m.tiles = ChassisBlueprintLoader.toGrid(def.rows);
        for (ChassisBlueprintLoader.SubsystemPlacement p : ChassisBlueprintLoader.drawnMounts(m.tiles, catalog, false)) {
            Mount mount = new Mount(p.component.id, p.footprint.size, p.footprint.originX, p.footprint.originY,
                    p.footprint.rotation);
            m.mounts.add(mount);
            m.fillUnder(p.footprint, catalog);
        }
        for (ChassisDef.Mount d : def.mounts) {
            ShipPartCatalog.ComponentType type = d.component != null ? catalog.component(d.component)
                    : standardOrNull(catalog, d.type);
            MountFootprint.MountSize size = sizeOrNull(d.size);
            if (type == null || size == null || !size.exists(type.mountKind())) {
                m.unresolvedMounts.add(d);
                continue;
            }
            Mount mount = new Mount(type.id, size, d.x, d.y, d.rotation);
            mount.listed = true;
            m.mounts.add(mount);
        }
        for (ChassisDef.Port p : def.ports) {
            Mount port = m.portAt(p.x, p.y, catalog);
            if (port != null) port.active = p.active;
            else m.unresolvedPorts.add(p);
        }
        m.edgeWalls.addAll(def.edgeWalls);
        m.edgeConduits.addAll(def.edgeConduits);
        m.hullPieces.addAll(def.hullPieces);
        m.coverings.addAll(def.coverings);
        m.lights = copyLights(def.lights);
        return m;
    }

    /**
     * The definition the game loads. Each mount not marked {@link Mount#listed} is drawn as its
     * component's letter when the loader would read exactly that mount back; the rest are listed.
     */
    public ChassisDef toDef(ShipPartCatalog catalog) {
        ChassisDef def = new ChassisDef();
        def.id = id;
        def.name = name;
        def.station = station;
        def.tileSize = tileSize;
        def.originX = originX;
        def.originY = originY;

        List<Mount> drawn = new ArrayList<>();
        for (Mount m : mounts) {
            if (!m.listed && catalog.component(m.component) != null && m.footprint(catalog) != null) drawn.add(m);
        }
        char[][] chars;
        while (true) {
            chars = copyTiles();
            for (Mount m : drawn) draw(chars, m, catalog);
            Array<ChassisBlueprintLoader.SubsystemPlacement> read = ChassisBlueprintLoader.drawnMounts(chars, catalog, false);
            // List one unmatched mount at a time: two touching mounts read back once either is listed
            Mount unmatched = null;
            for (Mount m : drawn) if (!readsBack(m, read)) unmatched = m;
            if (unmatched == null) break;
            drawn.remove(unmatched);
        }

        int h = height();
        def.rows = new String[h];
        for (int r = 0; r < h; r++) def.rows[r] = new String(chars[h - 1 - r]);

        Set<Mount> isDrawn = new HashSet<>(drawn);
        for (Mount m : mounts) {
            if (isDrawn.contains(m) || catalog.component(m.component) == null) continue;
            ChassisDef.Mount d = new ChassisDef.Mount();
            ShipPartCatalog.ComponentType type = catalog.component(m.component);
            if (catalog.standardComponent(type.behavior) == type && type.id.equals(type.behavior.name().toLowerCase())) {
                d.type = type.behavior.name();
            } else {
                d.component = type.id;
            }
            d.size = m.size.name();
            d.x = m.x;
            d.y = m.y;
            d.rotation = m.rotation;
            def.mounts.add(d);
        }
        def.mounts.addAll(unresolvedMounts);
        for (Mount m : mounts) {
            if (!m.active || !isPort(m, catalog)) continue;
            ChassisDef.Port p = new ChassisDef.Port();
            p.x = m.x;
            p.y = m.y;
            p.active = true;
            def.ports.add(p);
        }
        def.ports.addAll(unresolvedPorts);
        def.edgeWalls.addAll(edgeWalls);
        def.edgeConduits.addAll(edgeConduits);
        def.hullPieces.addAll(hullPieces);
        def.coverings.addAll(coverings);
        def.lights = copyLights(lights);
        return def;
    }

    private static List<ChassisDef.Light> copyLights(List<ChassisDef.Light> from) {
        if (from == null) return null;
        List<ChassisDef.Light> out = new ArrayList<>();
        for (ChassisDef.Light l : from) out.add(Defs.copy(l, ChassisDef.Light.class));
        return out;
    }

    private static boolean readsBack(Mount m, Array<ChassisBlueprintLoader.SubsystemPlacement> read) {
        for (ChassisBlueprintLoader.SubsystemPlacement p : read) {
            MountFootprint f = p.footprint;
            if (p.component.id.equals(m.component) && f.originX == m.x && f.originY == m.y && f.size == m.size
                    && f.rotation == m.rotation) {
                return true;
            }
        }
        return false;
    }

    private static void draw(char[][] chars, Mount m, ShipPartCatalog catalog) {
        MountFootprint f = m.footprint(catalog);
        char symbol = catalog.component(m.component).symbol;
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (y >= 0 && y < chars.length && x >= 0 && x < chars[y].length) chars[y][x] = symbol;
            }
        }
    }

    // ---- Queries ----

    /** The light hung on that side of tile (x, y), or null. */
    public ChassisDef.Light lightAt(int x, int y, Side side) {
        if (lights == null) return null;
        for (ChassisDef.Light l : lights) {
            if (l.x == x && l.y == y && side.name().equals(l.side)) return l;
        }
        return null;
    }

    /** The mount covering tile (x, y), or null. */
    public Mount mountAt(int x, int y, ShipPartCatalog catalog) {
        for (int i = mounts.size() - 1; i >= 0; i--) {
            Mount m = mounts.get(i);
            MountFootprint f = m.footprint(catalog);
            if (f != null && f.covers(x, y)) return m;
        }
        return null;
    }

    private Mount portAt(int x, int y, ShipPartCatalog catalog) {
        Mount m = mountAt(x, y, catalog);
        return m != null && isPort(m, catalog) ? m : null;
    }

    public static boolean isPort(Mount m, ShipPartCatalog catalog) {
        ShipPartCatalog.ComponentType t = catalog.component(m.component);
        return t != null && t.mountKind() == MountFootprint.MountKind.PORT;
    }

    public ChassisDef.HullPiece hullPieceAt(int x, int y) {
        for (int i = hullPieces.size() - 1; i >= 0; i--) {
            ChassisDef.HullPiece p = hullPieces.get(i);
            int w = p.rotation % 2 == 0 ? p.width : p.height, h = p.rotation % 2 == 0 ? p.height : p.width;
            if (x >= p.x && x < p.x + w && y >= p.y && y < p.y + h) return p;
        }
        return null;
    }

    public ChassisDef.Covering coveringAt(int x, int y) {
        for (int i = coverings.size() - 1; i >= 0; i--) {
            ChassisDef.Covering c = coverings.get(i);
            if (x >= c.x && x < c.x + c.width && y >= c.y && y < c.y + c.height) return c;
        }
        return null;
    }

    /** The edge wall on that side of tile (x, y), however it was listed, or null. */
    public ChassisDef.EdgeWall edgeWallAt(int x, int y, Side side) {
        for (ChassisDef.EdgeWall w : edgeWalls) {
            Side s = Side.valueOf(w.side);
            if (w.x == x && w.y == y && s == side) return w;
            if (w.x + s.dx == x && w.y + s.dy == y && s.opposite() == side) return w;
        }
        return null;
    }

    /** The edge conduit on that side of tile (x, y), however it was listed, or null. */
    public ChassisDef.EdgeConduit edgeConduitAt(int x, int y, Side side) {
        for (ChassisDef.EdgeConduit c : edgeConduits) {
            Side s = Side.valueOf(c.side);
            if (c.x == x && c.y == y && s == side) return c;
            if (c.x + s.dx == x && c.y + s.dy == y && s.opposite() == side) return c;
        }
        return null;
    }

    /** True if any tile is drawn with {@code symbol}. */
    public boolean usesSymbol(char symbol) {
        for (char[] row : tiles) for (char c : row) if (c == symbol) return true;
        return false;
    }

    public boolean usesComponent(String componentId) {
        for (Mount m : mounts) if (m.component.equals(componentId)) return true;
        for (ChassisDef.Mount m : unresolvedMounts) if (componentId.equals(m.component)) return true;
        return false;
    }

    // ---- Edits ----

    /** Paints a tile type; a mount over that tile is removed. */
    public void paint(int x, int y, char symbol, ShipPartCatalog catalog) {
        if (!inBounds(x, y)) return;
        tiles[y][x] = symbol;
        Mount m = mountAt(x, y, catalog);
        if (m != null) mounts.remove(m);
    }

    /**
     * Installs a component with its bottom-left tile at (x, y), replacing any mount it overlaps.
     * The tiles under it become steel floor (hull, for a docking port, which is drawn over the hull).
     */
    public Mount place(String componentId, MountFootprint.MountSize size, int x, int y, int rotation,
                       ShipPartCatalog catalog) {
        Mount mount = new Mount(componentId, size, x, y, rotation);
        MountFootprint f = mount.footprint(catalog);
        if (f == null) return null;
        for (int i = mounts.size() - 1; i >= 0; i--) {
            MountFootprint o = mounts.get(i).footprint(catalog);
            if (o != null && overlaps(f, o)) mounts.remove(i);
        }
        fillUnder(f, catalog);
        mounts.add(mount);
        return mount;
    }

    /**
     * Turns an installed component a quarter turn counter-clockwise about its middle. Returns null
     * when it turned, else why it cannot: a docking port faces out of the hull it sits in, and a
     * turned slot must stay on the grid and clear of other mounts.
     */
    public String rotate(Mount mount, ShipPartCatalog catalog) {
        MountFootprint f = mount.footprint(catalog);
        if (f == null) return "Unknown component";
        if (f.kind == MountFootprint.MountKind.PORT) return "A docking port faces out of the hull it sits in";
        // Truncating division keeps the middle in place both ways, so four turns end where they began
        int x = f.originX + (f.width() - f.height()) / 2, y = f.originY + (f.height() - f.width()) / 2;
        MountFootprint turned = new MountFootprint(f.kind, f.size, x, y, f.rotation + 1);
        if (!inBounds(x, y) || !inBounds(x + turned.width() - 1, y + turned.height() - 1)) {
            return "Turned, it would not fit on the grid";
        }
        for (Mount o : mounts) {
            MountFootprint of = o == mount ? null : o.footprint(catalog);
            if (of != null && overlaps(turned, of)) return "Turned, it would overlap another mount";
        }
        mount.x = x;
        mount.y = y;
        mount.rotation = turned.rotation;
        fillUnder(turned, catalog);
        return null;
    }

    private static boolean overlaps(MountFootprint a, MountFootprint b) {
        return a.originX < b.originX + b.width() && b.originX < a.originX + a.width()
                && a.originY < b.originY + b.height() && b.originY < a.originY + a.height();
    }

    private void fillUnder(MountFootprint f, ShipPartCatalog catalog) {
        char base = f.kind == MountFootprint.MountKind.PORT
                ? symbolFor(catalog, ShipPartCatalog.TileBase.HULL, '#')
                : symbolFor(catalog, ShipPartCatalog.TileBase.FLOOR, '.');
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (inBounds(x, y)) tiles[y][x] = base;
            }
        }
    }

    /**
     * Removes the topmost thing at tile (x, y): a mount, else a hull piece, else a covering, else
     * paints the tile void. Returns what was removed, for the status line.
     */
    public String erase(int x, int y, ShipPartCatalog catalog) {
        Mount m = mountAt(x, y, catalog);
        if (m != null) {
            mounts.remove(m);
            return m.component;
        }
        ChassisDef.HullPiece p = hullPieceAt(x, y);
        if (p != null) {
            hullPieces.remove(p);
            return "hull piece";
        }
        ChassisDef.Covering c = coveringAt(x, y);
        if (c != null) {
            coverings.remove(c);
            return "covering";
        }
        if (inBounds(x, y)) tiles[y][x] = symbolFor(catalog, ShipPartCatalog.TileBase.VOID, ' ');
        return "tile";
    }

    /** Adds an edge wall on that side of tile (x, y), or removes the one already there. */
    public void toggleEdgeWall(int x, int y, Side side, String weightClass, boolean bulkhead) {
        ChassisDef.EdgeWall existing = edgeWallAt(x, y, side);
        if (existing != null) {
            edgeWalls.remove(existing);
            return;
        }
        ChassisDef.EdgeWall w = new ChassisDef.EdgeWall();
        w.x = x;
        w.y = y;
        w.side = side.name();
        w.weightClass = weightClass;
        w.bulkhead = bulkhead;
        edgeWalls.add(w);
    }

    /**
     * Runs a power conduit along that side of tile (x, y), or takes up the one already there. It
     * may run inside a thin wall or along a full-tile wall. False if the tile is off the grid.
     */
    public boolean toggleEdgeConduit(int x, int y, Side side) {
        if (!inBounds(x, y)) return false;
        ChassisDef.EdgeConduit existing = edgeConduitAt(x, y, side);
        if (existing != null) {
            edgeConduits.remove(existing);
            return true;
        }
        ChassisDef.EdgeConduit c = new ChassisDef.EdgeConduit();
        c.x = x;
        c.y = y;
        c.side = side.name();
        edgeConduits.add(c);
        return true;
    }

    /**
     * Hangs a light on that side of tile (x, y), or takes down the one already there. A light takes
     * no room on its tile. False if the tile is off the grid.
     */
    public boolean toggleLight(int x, int y, Side side) {
        if (!inBounds(x, y)) return false;
        ChassisDef.Light existing = lightAt(x, y, side);
        if (lights == null) lights = new ArrayList<>();
        if (existing != null) {
            lights.remove(existing);
            return true;
        }
        ChassisDef.Light l = new ChassisDef.Light();
        l.x = x;
        l.y = y;
        l.side = side.name();
        lights.add(l);
        return true;
    }

    /**
     * Replaces the lights with ones placed along the walls of every room ({@link WallLightLayout}),
     * worked out from the layout as the game would load it. Null, or why the layout can't be loaded.
     */
    public String autoPlaceLights(ShipPartCatalog catalog) {
        ShipGridComponent grid;
        try {
            grid = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(toDef(catalog), catalog)).grid;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        lights = new ArrayList<>();
        for (WallLight w : WallLightLayout.of(grid)) {
            ChassisDef.Light l = new ChassisDef.Light();
            l.x = w.x;
            l.y = w.y;
            l.side = w.side.name();
            lights.add(l);
        }
        // Bottom row first, left to right, so the saved list reads in order
        lights.sort((p, q) -> p.y != q.y ? Integer.compare(p.y, q.y) : p.x != q.x ? Integer.compare(p.x, q.x) : p.side.compareTo(q.side));
        return null;
    }

    /**
     * Places a hull piece with its bottom-left tile at (x, y), replacing any piece it overlaps.
     * Returns null, placing nothing, when it would not fit on the grid.
     */
    public ChassisDef.HullPiece addHullPiece(String shape, int x, int y, int width, int height, int rotation,
                                             String weightClass) {
        int r = Math.floorMod(rotation, 4);
        int w = r % 2 == 0 ? width : height, h = r % 2 == 0 ? height : width;
        if (!inBounds(x, y) || !inBounds(x + w - 1, y + h - 1)) return null;
        // A new piece replaces the ones it overlaps, as a component replaces mounts
        for (int i = hullPieces.size() - 1; i >= 0; i--) {
            ChassisDef.HullPiece o = hullPieces.get(i);
            int ow = o.rotation % 2 == 0 ? o.width : o.height, oh = o.rotation % 2 == 0 ? o.height : o.width;
            if (x < o.x + ow && o.x < x + w && y < o.y + oh && o.y < y + h) hullPieces.remove(i);
        }
        ChassisDef.HullPiece p = new ChassisDef.HullPiece();
        p.shape = shape;
        p.x = x;
        p.y = y;
        p.width = width;
        p.height = height;
        p.rotation = Math.floorMod(rotation, 4);
        p.weightClass = weightClass;
        hullPieces.add(p);
        return p;
    }

    /** Lays a covering over the rectangle between two corner tiles, clipped to the grid. */
    public ChassisDef.Covering addCovering(String covering, int x0, int y0, int x1, int y1) {
        int ax = Math.max(0, Math.min(x0, x1)), ay = Math.max(0, Math.min(y0, y1));
        int bx = Math.min(width() - 1, Math.max(x0, x1)), by = Math.min(height() - 1, Math.max(y0, y1));
        if (bx < ax || by < ay) return null;
        ChassisDef.Covering c = new ChassisDef.Covering();
        c.covering = covering;
        c.x = ax;
        c.y = ay;
        c.width = bx - ax + 1;
        c.height = by - ay + 1;
        coverings.add(c);
        return c;
    }

    /** Paints every tile in the rectangle between two corner tiles. */
    public void fill(int x0, int y0, int x1, int y1, char symbol, ShipPartCatalog catalog) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) paint(x, y, symbol, catalog);
        }
    }

    /**
     * Grows ({@code delta} 1) or shrinks ({@code delta} -1) the grid by one row or column on a side.
     * Everything listed shifts with the tiles; whatever falls off the grid is removed. The origin
     * moves too, so the tiles that stay keep their place on the body.
     */
    public void resize(Side side, int delta, ShipPartCatalog catalog) {
        int w = width(), h = height();
        int nw = w + (side == Side.LEFT || side == Side.RIGHT ? delta : 0);
        int nh = h + (side == Side.TOP || side == Side.BOTTOM ? delta : 0);
        if (nw < 1 || nh < 1) return;
        int dx = side == Side.LEFT ? delta : 0, dy = side == Side.BOTTOM ? delta : 0;
        char voidSymbol = symbolFor(catalog, ShipPartCatalog.TileBase.VOID, ' ');
        char[][] next = new char[nh][nw];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int ox = x - dx, oy = y - dy;
                next[y][x] = ox >= 0 && oy >= 0 && ox < w && oy < h ? tiles[oy][ox] : voidSymbol;
            }
        }
        tiles = next;
        originX -= dx * tileSize;
        originY -= dy * tileSize;
        shift(dx, dy, catalog);
    }

    private void shift(int dx, int dy, ShipPartCatalog catalog) {
        for (int i = mounts.size() - 1; i >= 0; i--) {
            Mount m = mounts.get(i);
            m.x += dx;
            m.y += dy;
            MountFootprint f = m.footprint(catalog);
            if (f != null && !(inBounds(f.originX, f.originY) && inBounds(f.originX + f.width() - 1, f.originY + f.height() - 1))) {
                mounts.remove(i);
            }
        }
        for (int i = edgeWalls.size() - 1; i >= 0; i--) {
            ChassisDef.EdgeWall e = edgeWalls.get(i);
            e.x += dx;
            e.y += dy;
            if (!inBounds(e.x, e.y)) edgeWalls.remove(i);
        }
        for (int i = edgeConduits.size() - 1; i >= 0; i--) {
            ChassisDef.EdgeConduit c = edgeConduits.get(i);
            c.x += dx;
            c.y += dy;
            if (!inBounds(c.x, c.y)) edgeConduits.remove(i);
        }
        for (int i = hullPieces.size() - 1; i >= 0; i--) {
            ChassisDef.HullPiece p = hullPieces.get(i);
            p.x += dx;
            p.y += dy;
            int pw = p.rotation % 2 == 0 ? p.width : p.height, ph = p.rotation % 2 == 0 ? p.height : p.width;
            if (!inBounds(p.x, p.y) || !inBounds(p.x + pw - 1, p.y + ph - 1)) hullPieces.remove(i);
        }
        if (lights != null) {
            for (int i = lights.size() - 1; i >= 0; i--) {
                ChassisDef.Light l = lights.get(i);
                l.x += dx;
                l.y += dy;
                if (!inBounds(l.x, l.y)) lights.remove(i);
            }
        }
        for (int i = coverings.size() - 1; i >= 0; i--) {
            ChassisDef.Covering c = coverings.get(i);
            c.x += dx;
            c.y += dy;
            int x0 = Math.max(0, c.x), y0 = Math.max(0, c.y);
            int x1 = Math.min(width(), c.x + c.width), y1 = Math.min(height(), c.y + c.height);
            if (x1 <= x0 || y1 <= y0) {
                coverings.remove(i);
                continue;
            }
            c.x = x0;
            c.y = y0;
            c.width = x1 - x0;
            c.height = y1 - y0;
        }
    }

    /** Puts the body origin at the middle of the grid. */
    public void centerOrigin() {
        originX = -width() * tileSize / 2f;
        originY = -height() * tileSize / 2f;
    }

    /** Redraws every tile drawn with one symbol with another, as when a tile type's symbol changes. */
    public void replaceSymbol(char from, char to) {
        for (char[] row : tiles) for (int x = 0; x < row.length; x++) if (row[x] == from) row[x] = to;
    }

    /** A deep copy, for undo. */
    public ChassisModel copy() {
        ChassisModel m = new ChassisModel();
        m.id = id;
        m.name = name;
        m.station = station;
        m.tileSize = tileSize;
        m.originX = originX;
        m.originY = originY;
        m.tiles = copyTiles();
        for (Mount mount : mounts) m.mounts.add(mount.copy());
        for (ChassisDef.EdgeWall e : edgeWalls) m.edgeWalls.add(Defs.copy(e, ChassisDef.EdgeWall.class));
        for (ChassisDef.EdgeConduit c : edgeConduits) m.edgeConduits.add(Defs.copy(c, ChassisDef.EdgeConduit.class));
        for (ChassisDef.HullPiece p : hullPieces) m.hullPieces.add(Defs.copy(p, ChassisDef.HullPiece.class));
        for (ChassisDef.Covering c : coverings) m.coverings.add(Defs.copy(c, ChassisDef.Covering.class));
        for (ChassisDef.Mount u : unresolvedMounts) m.unresolvedMounts.add(Defs.copy(u, ChassisDef.Mount.class));
        for (ChassisDef.Port u : unresolvedPorts) m.unresolvedPorts.add(Defs.copy(u, ChassisDef.Port.class));
        m.lights = copyLights(lights);
        return m;
    }

    private char[][] copyTiles() {
        char[][] out = new char[tiles.length][];
        for (int y = 0; y < tiles.length; y++) out[y] = tiles[y].clone();
        return out;
    }

    /** The symbol of the first tile type with that base, or {@code fallback}. */
    public static char symbolFor(ShipPartCatalog catalog, ShipPartCatalog.TileBase base, char fallback) {
        ShipPartCatalog.TileType preferred = catalog.tileFor(fallback);
        if (preferred != null && preferred.base == base) return fallback;
        for (ShipPartCatalog.TileType t : catalog.tiles()) if (t.base == base) return t.symbol;
        return fallback;
    }

    private static ShipPartCatalog.ComponentType standardOrNull(ShipPartCatalog catalog, String type) {
        try {
            return catalog.standardComponent(SubsystemComponent.SubsystemType.valueOf(type));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static MountFootprint.MountSize sizeOrNull(String size) {
        try {
            return MountFootprint.MountSize.valueOf(size);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
