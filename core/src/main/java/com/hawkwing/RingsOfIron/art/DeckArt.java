package com.hawkwing.RingsOfIron.art;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.hawkwing.RingsOfIron.render.DeckView;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.EdgeConduit;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.EngineLayout;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.interiorgrid.WallLightLayout;
import com.ringsofiron.interiorgrid.crew.Suit;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Draws a deck with the art pack: floors, coverings and conduits (on tiles and along edges), walls joined to their
 * neighbors, wedges, the systems in their mounts (dark when they have no power), docking ports
 * open or shut, thin walls and bulkhead doors, wall lights, furnishings (each at its own spot and
 * angle, darker as a fire takes it), damage, breaches, seals, fire and cargo. {@link #glow} draws
 * again just what is lit, for after the light map.
 *
 * Everything is in the grid's local meters, so the batch's transform carries the craft's pose.
 * What has no art is left out, and the caller keeps its flat shapes for what this cannot draw
 * (see {@link #available()}).
 */
public final class DeckArt {
    private final ArtLibrary art;
    private final Color tint = new Color();
    /** The batch's color before a furnishing is drawn shaded. */
    private final Color before = new Color();
    /** Where each crew member was last drawn and which way they faced: {x, y, degrees}. */
    private final Map<Object, float[]> facing = new IdentityHashMap<>();
    /** The crate of each carrying crew region, for tinting it alone. */
    private final Map<TextureRegion, TextureRegion> crates = new IdentityHashMap<>();
    /** The emissive layer of each region drawn in the glow pass, or null where it has none. */
    private final Map<TextureRegion, TextureRegion> glows = new IdentityHashMap<>();
    /** Drawing {@link #glow}: each region is swapped for its emissive layer. */
    private boolean glowing;

    public DeckArt(ArtLibrary art) {
        this.art = art;
    }

    public ArtLibrary library() {
        return art;
    }

    /** Whether there is deck art to draw at all; without it, draw the flat deck plan. */
    public boolean available() {
        return art.region("tiles/floor") != null;
    }

    /** The whole deck, in a batch already begun and moved to the craft's pose. */
    public void draw(Batch batch, ShipGridComponent grid, CargoHoldComponent hold) {
        boolean power = shipPowered(grid);
        floors(batch, grid);
        walls(batch, grid);
        systems(batch, grid, power);
        walkways(batch, grid);
        edges(batch, grid, power);
        edgeConduits(batch, grid);
        drawPipes(batch, art, layout(grid), grid.gridOriginLocal.x, grid.gridOriginLocal.y, grid.tileSize);
        wallLights(batch, grid, power);
        furnishings(batch, grid);
        effects(batch, grid);
        cargo(batch, grid, hold);
    }

    /**
     * The emissive layers of what {@link #draw} drew that gives off light (running systems, live
     * conduits, powered doors and wall lights, fire), drawn again over the light map so a lamp
     * shows lit however dark its room. In a batch already begun and moved to the craft's pose.
     */
    public void glow(Batch batch, ShipGridComponent grid) {
        boolean power = shipPowered(grid);
        glowing = true;
        try {
            floors(batch, grid);
            systems(batch, grid, power);
            edges(batch, grid, power);
            edgeConduits(batch, grid);
            wallLights(batch, grid, power);
            if (power) furnishings(batch, grid);
            effects(batch, grid);
        } finally {
            glowing = false;
        }
    }

    // -----------------------------------------------------------------------

    private void floors(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || !t.isOpen()) continue;
                float x0 = grid.gridOriginLocal.x + x * s, y0 = grid.gridOriginLocal.y + y * s;
                tile(batch, ArtNames.floor(t), x0, y0, s);
                String covering = ArtNames.covering(t.covering);
                if (covering != null) tile(batch, covering, x0, y0, s);
                TileConduit conduit = grid.getTileConduit(x, y);
                if (conduit == null) continue;
                // The conduit lies on the floor and its covering; under a system, the system hides
                // it, so it gives no glow there
                if (glowing && t.mount != null) continue;
                tile(batch, ArtNames.conduit(conduit.isPowered), x0, y0, s);
                TextureRegion arm = art.region(ArtNames.conduitArm(conduit.isPowered));
                if (arm == null) continue;
                // 0 up, 1 left, 2 down, 3 right; an arm meets a conduit on the next tile or along the edge
                if (grid.hasConduitToward(x, y, Side.TOP)) quarter(batch, arm, x0, y0, s, 0);
                if (grid.hasConduitToward(x, y, Side.LEFT)) quarter(batch, arm, x0, y0, s, 1);
                if (grid.hasConduitToward(x, y, Side.BOTTOM)) quarter(batch, arm, x0, y0, s, 2);
                if (grid.hasConduitToward(x, y, Side.RIGHT)) quarter(batch, arm, x0, y0, s, 3);
            }
        }
    }

    /**
     * Conduits run along tile edges, over the walls they run in so they stay in sight: a segment on
     * each edge, a joint at each vertex they reach, and a tap where a tile conduit meets one. Without
     * the edge conduit art, each is drawn as two stretched conduit arms.
     */
    private void edgeConduits(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        boolean own = art.region(ArtNames.edgeConduit(true, true)) != null;
        float half = s / 8f;
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeConduit c = grid.conduitH[y][x];
                if (c == null) continue;
                if (own) {
                    draw(batch, art.first(ArtNames.edgeConduit(c.isPowered, true), ArtNames.edgeConduit(true, true)),
                            ox + x * s, oy + y * s - half, s, 2f * half);
                    continue;
                }
                TextureRegion arm = shown(art.region(ArtNames.conduitArm(c.isPowered)));
                if (arm != null) edgeConduit(batch, arm, ox + (x + 0.5f) * s, oy + y * s, s, true);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeConduit c = grid.conduitV[y][x];
                if (c == null) continue;
                if (own) {
                    draw(batch, art.first(ArtNames.edgeConduit(c.isPowered, false), ArtNames.edgeConduit(true, false)),
                            ox + x * s - half, oy + y * s, 2f * half, s);
                    continue;
                }
                TextureRegion arm = shown(art.region(ArtNames.conduitArm(c.isPowered)));
                if (arm != null) edgeConduit(batch, arm, ox + x * s, oy + (y + 0.5f) * s, s, false);
            }
        }
        if (!own) return;
        // Joints over the segments, at every vertex a run reaches
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeConduit n = y < grid.height ? grid.conduitV[y][x] : null;
                EdgeConduit so = y > 0 ? grid.conduitV[y - 1][x] : null;
                EdgeConduit e = x < grid.width ? grid.conduitH[y][x] : null;
                EdgeConduit w = x > 0 ? grid.conduitH[y][x - 1] : null;
                int mask = (n != null ? ArtNames.N : 0) | (e != null ? ArtNames.E : 0)
                        | (so != null ? ArtNames.S : 0) | (w != null ? ArtNames.W : 0);
                if (mask == 0) continue;
                boolean powered = live(n) || live(e) || live(so) || live(w);
                draw(batch, art.first(ArtNames.edgeConduitJoint(powered, mask), ArtNames.edgeConduitJoint(true, mask)),
                        ox + x * s - half, oy + y * s - half, 2f * half, 2f * half);
            }
        }
        // Taps where a tile conduit's arm meets a run
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || grid.getTileConduit(x, y) == null || !t.isOpen()) continue;
                for (Side side : Side.values()) {
                    EdgeConduit c = grid.getConduit(x, y, side);
                    if (c == null) continue;
                    float cx = ox + (x + 0.5f + side.dx * 0.5f) * s, cy = oy + (y + 0.5f + side.dy * 0.5f) * s;
                    draw(batch, art.first(ArtNames.edgeConduitTap(c.isPowered), ArtNames.edgeConduitTap(true)),
                            cx - half, cy - half, 2f * half, 2f * half);
                }
            }
        }
    }

    /** Where a deck's pipes run, for {@link #drawPipes}: the grid's, or a layout's in the editor. */
    public interface PipeLayout {
        int width();

        int height();

        /** A pipe across tile (x, y). */
        boolean across(int x, int y);

        /** A pipe along the bottom edge of tile (x, y); y may be the height (the top row's top edge). */
        boolean alongH(int x, int y);

        /** A pipe along the left edge of tile (x, y); x may be the width (the right column's right edge). */
        boolean alongV(int x, int y);
    }

    /** The grid's pipes as a {@link PipeLayout}. */
    public static PipeLayout layout(ShipGridComponent grid) {
        return new PipeLayout() {
            public int width() { return grid.width; }
            public int height() { return grid.height; }
            public boolean across(int x, int y) { return grid.getPipe(x, y) != null; }
            public boolean alongH(int x, int y) { return grid.pipeH[y][x] != null; }
            public boolean alongV(int x, int y) { return grid.pipeV[y][x] != null; }
        };
    }

    /**
     * Pipes from the tanks, over the edge walls and conduits so they stay in sight: across a tile a
     * valve plate with an arm toward each pipe beyond it (on the next tile or along the edge), and
     * along edges a segment per edge, a joint at each vertex and a tee where a tile's arm meets one.
     * Grid (0, 0) is drawn at ({@code ox}, {@code oy}) with tiles {@code s} across. Pipes have no
     * glow. Nothing is drawn for art the pack lacks.
     */
    public static void drawPipes(Batch batch, ArtLibrary art, PipeLayout p, float ox, float oy, float s) {
        float half = s / 8f;
        TextureRegion hub = art.region(ArtNames.PIPE_HUB), arm = art.region(ArtNames.PIPE_ARM);
        TextureRegion tap = art.region(ArtNames.EDGE_PIPE_TAP);
        TextureRegion segH = art.region(ArtNames.edgePipe(true)), segV = art.region(ArtNames.edgePipe(false));
        int w = p.width(), h = p.height();
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x < w; x++) {
                if (segH != null && p.alongH(x, y)) batch.draw(segH, ox + x * s, oy + y * s - half, s, 2f * half);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x <= w; x++) {
                if (segV != null && p.alongV(x, y)) batch.draw(segV, ox + x * s - half, oy + y * s, 2f * half, s);
            }
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x <= w; x++) {
                int mask = (y < h && p.alongV(x, y) ? ArtNames.N : 0) | (x < w && p.alongH(x, y) ? ArtNames.E : 0)
                        | (y > 0 && p.alongV(x, y - 1) ? ArtNames.S : 0) | (x > 0 && p.alongH(x - 1, y) ? ArtNames.W : 0);
                TextureRegion joint = mask == 0 ? null : art.region(ArtNames.edgePipeJoint(mask));
                if (joint != null) batch.draw(joint, ox + x * s - half, oy + y * s - half, 2f * half, 2f * half);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!p.across(x, y)) continue;
                float x0 = ox + x * s, y0 = oy + y * s;
                // 0 up, 1 left, 2 down, 3 right
                boolean[] toward = {
                        p.alongH(x, y + 1) || (y + 1 < h && p.across(x, y + 1)),
                        p.alongV(x, y) || (x > 0 && p.across(x - 1, y)),
                        p.alongH(x, y) || (y > 0 && p.across(x, y - 1)),
                        p.alongV(x + 1, y) || (x + 1 < w && p.across(x + 1, y))};
                if (arm != null) {
                    for (int q = 0; q < 4; q++) {
                        if (toward[q]) batch.draw(arm, x0, y0, s / 2f, s / 2f, s, s, 1f, 1f, q * 90f);
                    }
                }
                if (hub != null) batch.draw(hub, x0, y0, s, s);
                if (tap == null) continue;
                boolean[] edge = {p.alongH(x, y + 1), p.alongV(x, y), p.alongH(x, y), p.alongV(x + 1, y)};
                float[][] mid = {{0.5f, 1f}, {0f, 0.5f}, {0.5f, 0f}, {1f, 0.5f}};
                for (int q = 0; q < 4; q++) {
                    if (edge[q]) batch.draw(tap, x0 + mid[q][0] * s - half, y0 + mid[q][1] * s - half, 2f * half, 2f * half);
                }
            }
        }
    }

    private static boolean live(EdgeConduit c) {
        return c != null && c.isPowered;
    }

    /**
     * A conduit along a tile edge {@code s} long, whose middle is ({@code cx}, {@code cy}), for an
     * art pack without edge conduit art: two {@link ArtNames#conduitArm} arms out from the middle to
     * the edge's ends. An arm's run fills
     * the top 22 of its 64 units, so it is stretched to reach half a tile.
     */
    public static void edgeConduit(Batch batch, TextureRegion arm, float cx, float cy, float s, boolean horizontal) {
        float h = s * ArtLibrary.UNITS_PER_TILE / (2f * ARM_RUN);
        // Turned about the edge's middle: the arm's inner end
        float originX = s / 2f, originY = h - s / 2f;
        for (int q = horizontal ? 1 : 0; q < 4; q += 2) {
            batch.draw(arm, cx - originX, cy - originY, originX, originY, s, h, 1f, 1f, q * 90f);
        }
    }

    /** SVG units from the top of the conduit arm art to the inner end of its run. */
    private static final float ARM_RUN = 22f;

    private void walls(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || !t.isWall() || t.isOpen()) continue;
                if (t.piece != null && t.piece.shape == HullPiece.Shape.WEDGE) continue;
                // An engine's casing outside the hull is the engine's own art, with space round it
                if (EngineLayout.isCasing(grid, x, y)) continue;
                String id = ArtNames.wallId(t);
                int mask = 0;
                if (joins(grid, id, x, y + 1)) mask |= ArtNames.N;
                if (joins(grid, id, x + 1, y)) mask |= ArtNames.E;
                if (joins(grid, id, x, y - 1)) mask |= ArtNames.S;
                if (joins(grid, id, x - 1, y)) mask |= ArtNames.W;
                draw(batch, art.first(ArtNames.wall(t, mask)), grid.gridOriginLocal.x + x * s, grid.gridOriginLocal.y + y * s, s, s);
            }
        }
        for (HullPiece p : grid.hullPieces) {
            if (p.shape != HullPiece.Shape.WEDGE) continue;
            AtlasRegion r = art.first(ArtNames.wedge(p));
            if (r == null) continue;
            wedge(batch, r, grid.gridOriginLocal.x + (p.originX + p.width() / 2f) * s,
                    grid.gridOriginLocal.y + (p.originY + p.height() / 2f) * s, s, p.baseWidth, p.baseHeight, p.rotation,
                    ArtNames.wedgeMirrored(r.name, p.baseWidth, p.baseHeight));
        }
    }

    /** True if the wall at (x, y) is of kind {@code id} and so joins a wall of that kind beside it. */
    private static boolean joins(ShipGridComponent grid, String id, int x, int y) {
        return id.equals(ArtNames.wallId(grid.getTile(x, y))) && !EngineLayout.isCasing(grid, x, y);
    }

    private void systems(Batch batch, ShipGridComponent grid, boolean power) {
        float s = grid.tileSize;
        for (MountFootprint m : grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub == null) continue;
            boolean port = m.kind == MountFootprint.MountKind.PORT;
            boolean powered = port ? power && !sub.isDisabled() : sub.isOperational;
            // Dark art may fall back to the lit one, whose glow is not this system's
            if (glowing && !powered) continue;
            String state = port && portOpen(grid, m) ? "open" : null;
            // Damaged and destroyed systems have their own art, so the damage is part of the machine
            TextureRegion r = art.first(ArtNames.component(sub.component, sub.type, m.size, state, powered,
                    ArtNames.condition(sub.health)));
            int bw = m.size.baseWidth(m.kind), bh = m.size.baseHeight(m.kind);
            if (r != null) footprint(batch, r, grid, m.originX, m.originY, m.width(), m.height(), bw, bh, m.rotation);
            if (port) {
                // The latch collar: a half-tile strip outside the outer edge, which faces +y unturned
                TextureRegion collar = art.first(ArtNames.collar(m.size, powered), ArtNames.collar(m.size, true));
                if (collar == null) continue;
                float off = (bh / 2f + 0.25f) * s;
                float[] d = turn(0f, off, m.rotation);
                float cx = grid.gridOriginLocal.x + (m.originX + m.width() / 2f) * s + d[0];
                float cy = grid.gridOriginLocal.y + (m.originY + m.height() / 2f) * s + d[1];
                centered(batch, collar, cx, cy, bw * s, 0.5f * s, m.rotation);
            }
        }
    }

    private void edges(Batch batch, ShipGridComponent grid, boolean power) {
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        float half = s / 8f;
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeWall e = grid.edgeH[y][x];
                if (!standing(e)) continue;
                AtlasRegion r = e.hasBulkhead ? art.region(ArtNames.door(e.bulkheadSealed, power, true))
                        : art.first(ArtNames.edgeWall(e.material, e.weightClass, true));
                draw(batch, r, ox + x * s, oy + y * s - half, s, 2f * half);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall e = grid.edgeV[y][x];
                if (!standing(e)) continue;
                AtlasRegion r = e.hasBulkhead ? art.region(ArtNames.door(e.bulkheadSealed, power, false))
                        : art.first(ArtNames.edgeWall(e.material, e.weightClass, false));
                draw(batch, r, ox + x * s - half, oy + y * s, 2f * half, s);
            }
        }
        // Joints over the segments, where they meet at a vertex
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall n = y < grid.height ? grid.edgeV[y][x] : null;
                EdgeWall so = y > 0 ? grid.edgeV[y - 1][x] : null;
                EdgeWall e = x < grid.width ? grid.edgeH[y][x] : null;
                EdgeWall w = x > 0 ? grid.edgeH[y][x - 1] : null;
                int mask = (standing(n) ? ArtNames.N : 0) | (standing(e) ? ArtNames.E : 0)
                        | (standing(so) ? ArtNames.S : 0) | (standing(w) ? ArtNames.W : 0);
                if (mask == 0) continue;
                // The joint takes the heaviest wall's weight and material
                EdgeWall heaviest = heaviest(heaviest(heaviest(heaviest(null, n), e), so), w);
                draw(batch, art.first(ArtNames.edgeJoint(heaviest.material, heaviest.weightClass, mask)),
                        ox + x * s - half, oy + y * s - half, 2f * half, 2f * half);
            }
        }
    }

    /** Each light hung on a wall, its top (the wall) against the edge it hangs on, as the layout places them. */
    private void wallLights(Batch batch, ShipGridComponent grid, boolean power) {
        if (glowing && !power) return;
        AtlasRegion base = art.first(ArtNames.wallLight(power), ArtNames.wallLight(true));
        TextureRegion r = shown(base);
        if (r == null) return;
        ArtLibrary.Info info = art.info(base);
        float s = grid.tileSize, u = s / ArtLibrary.UNITS_PER_TILE;
        float w = (info == null ? base.getRegionWidth() : info.w) * u, h = (info == null ? base.getRegionHeight() : info.h) * u;
        for (WallLight p : WallLightLayout.lightsOf(grid)) {
            float ex = grid.gridOriginLocal.x + p.edgeX() * s, ey = grid.gridOriginLocal.y + p.edgeY() * s;
            batch.draw(r, ex - w / 2f, ey - h, w / 2f, h, w, h, 1f, 1f, p.rotation() * 90f);
        }
    }

    /** Every furnishing aboard, rugs and the like first so the rest stand on them. */
    private void furnishings(Batch batch, ShipGridComponent grid) {
        for (int pass = 0; pass < 2; pass++) {
            boolean flat = pass == 0;
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    for (Furnishing f : grid.tiles[y][x].furnishings) {
                        if (f.type.flat == flat) furnishing(batch, grid, f, null);
                    }
                }
            }
        }
    }

    /** Whether {@code f} has art to draw with. */
    public boolean hasArt(Furnishing f) {
        return art.region(f.type.art) != null;
    }

    /**
     * One furnishing, its middle at its spot and turned by its angle, in a batch already begun and
     * moved to the craft's pose: scorched darker as a fire burns it, or tinted {@code tint} (a piece
     * being placed) when not null.
     */
    public void furnishing(Batch batch, ShipGridComponent grid, Furnishing f, Color tint) {
        TextureRegion r = shown(art.region(f.type.art));
        if (r == null) return;
        float s = grid.tileSize, w = f.type.width * s, h = f.type.depth * s;
        float cx = grid.gridOriginLocal.x + f.x() * s, cy = grid.gridOriginLocal.y + f.y() * s;
        Color was = before.set(batch.getColor());
        if (tint != null) batch.setColor(tint);
        else if (!glowing && f.condition() < 1f) {
            float shade = 0.3f + 0.7f * f.condition();
            batch.setColor(shade, shade * 0.92f, shade * 0.85f, was.a);
        }
        batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, f.rotation);
        if (f.type.holds != null && f.suits.size > 0) lockerSuits(batch, f, cx, cy, w, s);
        batch.setColor(was);
    }

    /**
     * The suits hung in locker {@code f}, drawn over it at its middle ({@code cx}, {@code cy}), its
     * width {@code w}: one in each of its bays from the left, as many as it holds.
     */
    private void lockerSuits(Batch batch, Furnishing f, float cx, float cy, float w, float tileSize) {
        AtlasRegion r = art.region(ArtNames.lockerSuit(f.type.holds));
        if (r == null) return;
        ArtLibrary.Info info = art.info(r);
        float u = tileSize / ArtLibrary.UNITS_PER_TILE;
        float sw = (info == null ? r.getRegionWidth() : info.w) * u, sh = (info == null ? r.getRegionHeight() : info.h) * u;
        float cos = MathUtils.cosDeg(f.rotation), sin = MathUtils.sinDeg(f.rotation);
        int bays = Math.max(1, f.type.capacity);
        for (int i = 0; i < Math.min(f.suits.size, bays); i++) {
            float along = ((i + 0.5f) / bays - 0.5f) * w;
            float x = cx + along * cos, y = cy + along * sin;
            batch.draw(r, x - sw / 2f, y - sh / 2f, sw / 2f, sh / 2f, sw, sh, 1f, 1f, f.rotation);
        }
    }

    private void effects(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                float x0 = grid.gridOriginLocal.x + x * s, y0 = grid.gridOriginLocal.y + y * s;
                if (t.isWall() && !t.isOpen() && !EngineLayout.isCasing(grid, x, y)) {
                    // (an engine's casing shows its damage in the engine's own art)
                    if (t.isHullBreach()) tile(batch, "effects/breach", x0, y0, s);
                    else {
                        String damage = ArtNames.damage(t.getIntegrity());
                        if (damage != null) tile(batch, damage, x0, y0, s);
                    }
                }
                // A leaking wall or deck plate the crew patched in the field, until a yard mends it
                if (t.isPatched()) tile(batch, "effects/hull_patch", x0, y0, s);
                if (t.bulkheadSealed) tile(batch, "effects/bulkhead_seal", x0, y0, s);
                // Propellant a burst tank pooled on bare deck, until it burns off
                if (t.isFloor() && t.covering == FloorCovering.NONE && t.fuel > 0f && !t.isBurning()) {
                    tile(batch, "effects/propellant_spill", x0, y0, s);
                }
                // A shorted conduit, until the crew reset it
                TileConduit conduit = grid.getTileConduit(x, y);
                if (conduit != null && conduit.shorted) tile(batch, "effects/short_circuit", x0, y0, s);
                String fire = ArtNames.fire(t.fireSize());
                if (fire != null) tile(batch, fire, x0, y0, s);
            }
        }
        // A shorted system: the fault sits over the middle of its footprint
        for (MountFootprint m : grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub == null || !sub.shorted) continue;
            tile(batch, "effects/short_circuit", grid.gridOriginLocal.x + (m.originX + (m.width() - 1) / 2f) * s,
                    grid.gridOriginLocal.y + (m.originY + (m.height() - 1) / 2f) * s, s);
        }
    }

    private void cargo(Batch batch, ShipGridComponent grid, CargoHoldComponent hold) {
        if (hold == null) return;
        float s = grid.tileSize;
        Color was = batch.getColor().cpy();
        for (int i = 0; i < hold.bayTileCount(); i++) {
            CargoHoldComponent.CargoStack stack = hold.stacks[i];
            if (stack == null) continue;
            String name = ArtNames.cargoStack(hold.fillAt(hold.bayX[i], hold.bayY[i]));
            if (name == null) continue;
            // The crates keep their own look, warmed toward the commodity's color
            DeckView.commodityColor(stack.commodity, tint);
            batch.setColor(tint.lerp(Color.WHITE, 0.55f));
            tile(batch, name, grid.gridOriginLocal.x + hold.bayX[i] * s, grid.gridOriginLocal.y + hold.bayY[i] * s, s);
        }
        batch.setColor(was);
    }

    /** Whether there is gangway art; without it, draw the walkways as shapes. */
    public boolean hasWalkways() {
        return art.region(ArtNames.WALKWAY) != null;
    }

    /**
     * The gangway out of each open docked port: its half of the way across the collars, as
     * {@link DeckView#walkways} lays it out.
     */
    private void walkways(Batch batch, ShipGridComponent grid) {
        TextureRegion r = art.region(ArtNames.WALKWAY);
        if (r == null) return;
        float s = grid.tileSize, ox = grid.gridOriginLocal.x, oy = grid.gridOriginLocal.y;
        for (GridLink link : grid.links) {
            if (!link.canCross()) continue;
            Side side = link.side;
            float half = link.gap * 0.5f * s;
            if (half <= 0f) continue;
            // Centered on the tile's edge pushed half the walkway out; the art runs along its y
            float cx = ox + (link.x + 0.5f + side.dx * 0.5f) * s + side.dx * half / 2f;
            float cy = oy + (link.y + 0.5f + side.dy * 0.5f) * s + side.dy * half / 2f;
            centered(batch, r, cx, cy, s, half, side.dx != 0 ? 1 : 0);
            tunnelWalls(batch, grid, link, half);
        }
    }

    /**
     * The tunnel's side walls down this grid's half of the gangway, where a collar locked out
     * runs past the two collars on the hulls: half a tile from the edge (the collar) to halfway
     * across, beside the outermost openings of the port. Nothing for collars that met.
     */
    private void tunnelWalls(Batch batch, ShipGridComponent grid, GridLink link, float half) {
        TextureRegion r = art.region(ArtNames.TUNNEL_WALL);
        float s = grid.tileSize, run = half - s * 0.5f;
        if (r == null || run < s * 0.25f) return;
        ArtLibrary.Info info = art.info((com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion) r);
        float thick = (info == null ? r.getRegionWidth() : info.w) * s / ArtLibrary.UNITS_PER_TILE;
        Side side = link.side;
        // Along the gangway (out of the port) and across it
        float ax = side.dx, ay = side.dy, cxAcross = -side.dy, cyAcross = side.dx;
        float ex = grid.gridOriginLocal.x + (link.x + 0.5f + side.dx * 0.5f) * s;
        float ey = grid.gridOriginLocal.y + (link.y + 0.5f + side.dy * 0.5f) * s;
        int pieces = Math.max(1, Math.round(run / (s * 0.5f)));
        float piece = run / pieces;
        for (int k = -1; k <= 1; k += 2) {
            if (linkBeside(grid, link, k)) continue;
            float off = s * 0.5f + thick * 0.5f;
            // The art's walkway side is its x 0: facing in on either side
            int rotation = (side.dx != 0 ? 1 : 0) + ((k > 0) == (side.dx != 0 ? side.dx < 0 : side.dy > 0) ? 2 : 0);
            for (int i = 0; i < pieces; i++) {
                float along = s * 0.5f + piece * (i + 0.5f);
                float cx = ex + ax * along + cxAcross * off * k, cy = ey + ay * along + cyAcross * off * k;
                centered(batch, r, cx, cy, thick, piece, rotation);
            }
        }
    }

    /** True if the same dock has another opening beside {@code link}, on side {@code k} (-1 or 1) across it. */
    private static boolean linkBeside(ShipGridComponent grid, GridLink link, int k) {
        int nx = link.x - link.side.dy * k, ny = link.y + link.side.dx * k;
        for (GridLink l : grid.links) {
            if (l != link && l.otherShip == link.otherShip && l.side == link.side && l.x == nx && l.y == ny) return true;
        }
        return false;
    }

    /** Whether there is crew art; without it, draw crew as dots. */
    public boolean hasCrew() {
        return hasCrew(ArtNames.CrewPose.STANDING);
    }

    /**
     * Whether there is crew art for {@code pose}, wounds over it included; without it, draw the
     * standing figure and mark the pose and wound.
     */
    public boolean hasCrew(ArtNames.CrewPose pose) {
        return art.region(ArtNames.crew(ArtNames.Crew.SHIP, pose, false)) != null
                && art.region(ArtNames.WOUND_ART + (pose.lying() ? "_down" : "")) != null;
    }

    /**
     * Whether {@code who}'s figure in {@code pose}, out of a suit, holds the crate they carry: there
     * is carrying art for the pose. Without it the crate is drawn on its own.
     */
    public boolean carries(ArtNames.Crew who, ArtNames.CrewPose pose) {
        if (pose.lying()) return false;
        TextureRegion r = art.first(ArtNames.crew(who, pose, true), ArtNames.crew(who, pose, false),
                ArtNames.crew(who, true), ArtNames.crew(who, false));
        ArtLibrary.Info info = r == null ? null : art.info((AtlasRegion) r);
        return info != null && info.h > info.w;
    }

    /**
     * A crew member at ({@code x}, {@code y}) in the grid's local meters, turned the way they last
     * walked, in {@code pose} (standing, when there is no art for it), with {@code wound} (a region,
     * or null) over the figure at {@code woundAlpha}, and the crate they carry on their feet tinted
     * to its commodity ({@code commodity}, or null). {@code key} is the crew member, to remember
     * their facing.
     */
    public void crew(Batch batch, Object key, ArtNames.Crew who, ArtNames.CrewPose pose, String wound, float woundAlpha,
                     String commodity, float x, float y, float tileSize) {
        crew(batch, key, who, pose, null, null, Float.NaN, wound, woundAlpha, commodity, x, y, tileSize);
    }

    /**
     * {@link #crew(Batch, Object, ArtNames.Crew, ArtNames.CrewPose, String, float, String, float, float, float)}
     * in a suit of kind {@code suit} (null for none), or out on EVA in {@code outside} (null while
     * aboard: on the hull or off it, seen from behind, the jetpack on their back), and turned to
     * {@code turn} degrees in the deck's frame (the art's +y along it) unless it is NaN, when they
     * face the way they last moved. Suited, they carry no crate; outside, no wound shows.
     */
    public void crew(Batch batch, Object key, ArtNames.Crew who, ArtNames.CrewPose pose, Suit.Kind suit,
                     ArtNames.EvaPose outside, float turn, String wound, float woundAlpha, String commodity,
                     float x, float y, float tileSize) {
        if (pose.lying() || suit != null || outside != null) commodity = null;
        TextureRegion r;
        if (outside != null && !pose.lying()) {
            r = art.first(ArtNames.crew(who, outside), ArtNames.crew(who, pose, Suit.Kind.EVA), ArtNames.crew(who, pose, false),
                    ArtNames.crew(who, false));
            wound = null;
        } else if (suit != null || outside != null) {
            r = art.first(ArtNames.crew(who, pose, suit != null ? suit : Suit.Kind.EVA), ArtNames.crew(who, pose, false),
                    ArtNames.crew(who, false));
        } else {
            r = art.first(ArtNames.crew(who, pose, commodity != null), ArtNames.crew(who, pose, false),
                    ArtNames.crew(who, commodity != null), ArtNames.crew(who, false));
        }
        if (r == null) return;
        float[] f = facing.get(key);
        if (f == null) facing.put(key, f = new float[]{x, y, 0f});
        float dx = x - f[0], dy = y - f[1];
        if (!Float.isNaN(turn)) {
            f[2] = turn;
            f[0] = x;
            f[1] = y;
        } else if (dx * dx + dy * dy > 0.0004f * tileSize * tileSize) {
            // Art faces +y, so a walk along +y is no turn
            f[2] = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 90f;
            f[0] = x;
            f[1] = y;
        }
        ArtLibrary.Info info = art.info((com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion) r);
        float u = tileSize / ArtLibrary.UNITS_PER_TILE;
        // Centered on the SVG origin: the viewBox's top-left is (x, y) in SVG units, y down
        float left = info == null ? -r.getRegionWidth() / 2f * u : info.x * u;
        float w = (info == null ? r.getRegionWidth() : info.w) * u, h = (info == null ? r.getRegionHeight() : info.h) * u;
        float bottom = info == null ? -h / 2f : -(info.y + info.h) * u;
        batch.draw(r, x + left, y + bottom, -left, -bottom, w, h, 1f, 1f, f[2]);
        AtlasRegion over = wound == null ? null : art.region(wound);
        if (over != null) {
            ArtLibrary.Info o = art.info(over);
            float ol = o == null ? -over.getRegionWidth() / 2f * u : o.x * u;
            float ow = (o == null ? over.getRegionWidth() : o.w) * u, oh = (o == null ? over.getRegionHeight() : o.h) * u;
            float ob = o == null ? -oh / 2f : -(o.y + o.h) * u;
            Color was = batch.getColor().cpy();
            batch.setColor(was.r, was.g, was.b, was.a * woundAlpha);
            batch.draw(over, x + ol, y + ob, -ol, -ob, ow, oh, 1f, 1f, f[2]);
            batch.setColor(was);
        }
        if (commodity == null || info == null || info.h <= info.w) return;
        // The crate is held out ahead (+y): the top of the canvas, down to its lower edge at SVG y = -15.5
        TextureRegion crate = crates.get(r);
        if (crate == null) {
            int px = r.getRegionHeight();
            int cratePx = Math.round(px * (-15.5f - info.y) / info.h);
            crate = new TextureRegion(r, 0, 0, r.getRegionWidth(), cratePx);
            crates.put(r, crate);
        }
        float crateH = h * crate.getRegionHeight() / r.getRegionHeight();
        Color was = batch.getColor().cpy();
        batch.setColor(DeckView.commodityColor(commodity, tint).lerp(Color.WHITE, 0.15f));
        batch.draw(crate, x + left, y + bottom + h - crateH, -left, -bottom - (h - crateH), w, crateH, 1f, 1f, f[2]);
        batch.setColor(was);
    }

    // -----------------------------------------------------------------------

    /** Whether the craft has a working reactor; bulkhead doors and ports light up with it. */
    static boolean shipPowered(ShipGridComponent grid) {
        for (MountFootprint m : grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) return true;
        }
        return false;
    }

    /** A port is open while a dock through it can be crossed. */
    private static boolean portOpen(ShipGridComponent grid, MountFootprint m) {
        for (GridLink link : grid.links) {
            if (m.covers(link.x, link.y) && link.canCross()) return true;
        }
        return false;
    }

    private static boolean standing(EdgeWall e) {
        return e != null && !e.isDestroyed();
    }

    private static EdgeWall heaviest(EdgeWall a, EdgeWall e) {
        if (!standing(e)) return a;
        return a == null || e.weightClass.ordinal() > a.weightClass.ordinal() ? e : a;
    }

    /** (x, y) turned {@code quarters} counter-clockwise quarter turns. */
    private static float[] turn(float x, float y, int quarters) {
        switch (Math.floorMod(quarters, 4)) {
            case 1: return new float[]{-y, x};
            case 2: return new float[]{-x, -y};
            case 3: return new float[]{y, -x};
            default: return new float[]{x, y};
        }
    }

    private void tile(Batch batch, String name, float x0, float y0, float s) {
        draw(batch, art.region(name), x0, y0, s, s);
    }

    /** {@code r}, or while drawing the glow its emissive layer (null where it has none). */
    private TextureRegion shown(TextureRegion r) {
        if (!glowing || r == null) return r;
        TextureRegion g = glows.get(r);
        if (g == null && !glows.containsKey(r)) {
            g = r instanceof AtlasRegion ? art.region(ArtNames.glow(((AtlasRegion) r).name)) : null;
            glows.put(r, g);
        }
        return g;
    }

    private void draw(Batch batch, TextureRegion r, float x, float y, float w, float h) {
        r = shown(r);
        if (r != null) batch.draw(r, x, y, w, h);
    }

    /** A one-tile region turned {@code quarters} counter-clockwise about the tile's middle. */
    private void quarter(Batch batch, TextureRegion r, float x0, float y0, float s, int quarters) {
        r = shown(r);
        if (r != null) batch.draw(r, x0, y0, s / 2f, s / 2f, s, s, 1f, 1f, quarters * 90f);
    }

    /** Art drawn at its unrotated size {@code bw} x {@code bh} tiles, turned onto a footprint. */
    private void footprint(Batch batch, TextureRegion r, ShipGridComponent grid, int originX, int originY,
                           int width, int height, int bw, int bh, int rotation) {
        float s = grid.tileSize;
        float cx = grid.gridOriginLocal.x + (originX + width / 2f) * s;
        float cy = grid.gridOriginLocal.y + (originY + height / 2f) * s;
        centered(batch, r, cx, cy, bw * s, bh * s, rotation);
    }

    /**
     * Draws a wedge's art centered on its footprint: {@code tile} is the size of a tile, the base
     * size and rotation are the piece's. {@code mirrored} is for tall art standing in for a wide
     * wedge ({@link ArtNames#wedgeMirrored}), drawn reflected across its diagonal.
     */
    public static void wedge(Batch batch, TextureRegion r, float cx, float cy, float tile, int baseWidth, int baseHeight,
                             int rotation, boolean mirrored) {
        if (!mirrored) {
            drawCentered(batch, r, cx, cy, baseWidth * tile, baseHeight * tile, rotation);
            return;
        }
        float w = baseHeight * tile, h = baseWidth * tile;
        batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, -1f, 1f, (rotation - 1) * 90f);
    }

    private void centered(Batch batch, TextureRegion r, float cx, float cy, float w, float h, int rotation) {
        r = shown(r);
        if (r != null) drawCentered(batch, r, cx, cy, w, h, rotation);
    }

    private static void drawCentered(Batch batch, TextureRegion r, float cx, float cy, float w, float h, int rotation) {
        batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, rotation * 90f);
    }
}
