package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import com.ringsofiron.moddingdata.defs.ChassisDef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link ShipEditor}'s canvas: the layout's grid, in flat colors or the art pack, and the
 * ghost of what the tool would place.
 */
abstract class EditorCanvas extends EditorState {

    EditorCanvas(Path assetsDir, Path modsDir, ShipEditor.Refit refit) {
        super(assetsDir, modsDir, refit);
    }

    float sx(float tileX) {
        return panX + tileX * scale;
    }

    float sy(float tileY) {
        return panY + tileY * scale;
    }

    void setColor(int rgb, float alpha) {
        color.set(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
    }

    void drawGrid() {
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
            if (withArt && wedgeArt(p) != null) continue;
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
            if (f.kind != MountFootprint.MountKind.PORT && scale >= 8f) {
                shapes.setColor(0f, 0f, 0f, 0.45f);
                drawFacing(f);
            }
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
        // Wall lights: a bright strip on the wall, inside the tile it lights (their art when it is drawn)
        boolean lightArt = wallLightArt() != null;
        if (m.lights != null && !lightArt) {
            shapes.setColor(1f, 0.95f, 0.6f, 1f);
            for (ChassisDef.Light l : m.lights) lightRect(l.x, l.y, Side.valueOf(l.side));
        }
        if (tab == Tab.LAYOUT && tool == Tool.LIGHT && m.inBounds(hoverX, hoverY) && !lightArt) {
            shapes.setColor(1f, 0.95f, 0.6f, 0.5f);
            lightRect(hoverX, hoverY, nearestSide());
        }
        // RCS blocks: a block standing out from the hull edge, a tick for each nozzle (their art when it is drawn)
        for (ChassisDef.Rcs r : m.rcs) {
            if (rcsArt(r.kind) != null) continue;
            shapes.setColor(0.62f, 0.85f, 1f, 1f);
            rcsRect(r.x, r.y, Side.valueOf(r.side), r.kind);
        }
        if (tab == Tab.LAYOUT && tool == Tool.RCS && m.inBounds(hoverX, hoverY) && rcsArt(rcsKind) == null) {
            shapes.setColor(0.62f, 0.85f, 1f, 0.5f);
            rcsRect(hoverX, hoverY, nearestSide(), rcsKind);
        }
        // Conduits along edges: a thin amber line, over any wall they run in
        float wire = Math.max(2f, scale * 0.08f);
        for (ChassisDef.EdgeConduit run : withArt ? java.util.Collections.<ChassisDef.EdgeConduit>emptyList() : m.edgeConduits) {
            shapes.setColor(0.95f, 0.7f, 0.2f, 1f);
            edgeRect(run.x, run.y, Side.valueOf(run.side), wire);
        }
        if (tab == Tab.LAYOUT && tool == Tool.CONDUIT && m.inBounds(hoverX, hoverY) && !panning) {
            shapes.setColor(0.95f, 0.7f, 0.2f, 0.6f);
            Side side = conduitSide();
            if (side != null) edgeRect(hoverX, hoverY, side, Math.max(4f, wire * 2f));
            else shapes.rect(sx(hoverX + 0.3f), sy(hoverY + 0.3f), scale * 0.4f, scale * 0.4f);
        }
        // Pipes: a thin teal line along edges, and across tiles from the middle toward each neighbor
        if (!withArt) {
            shapes.setColor(0.45f, 0.75f, 0.85f, 1f);
            float pw = Math.max(2f, scale * 0.08f);
            for (ChassisDef.Pipe p : m.pipes) {
                if (p.side != null) {
                    edgeRect(p.x, p.y, Side.valueOf(p.side), pw);
                    continue;
                }
                shapes.rect(sx(p.x + 0.4f), sy(p.y + 0.4f), scale * 0.2f, scale * 0.2f);
                for (Side side : Side.values()) {
                    if (!pipeToward(m, p.x, p.y, side)) continue;
                    float cx = sx(p.x + 0.5f), cy = sy(p.y + 0.5f);
                    if (side.dx != 0) shapes.rect(side.dx > 0 ? cx : cx - scale / 2f, cy - pw / 2f, scale / 2f, pw);
                    else shapes.rect(cx - pw / 2f, side.dy > 0 ? cy : cy - scale / 2f, pw, scale / 2f);
                }
            }
        }
        if (tab == Tab.LAYOUT && tool == Tool.PIPE && m.inBounds(hoverX, hoverY) && !panning) {
            shapes.setColor(0.45f, 0.75f, 0.85f, 0.6f);
            Side side = conduitSide();
            if (side != null) edgeRect(hoverX, hoverY, side, Math.max(4f, scale * 0.16f));
            else shapes.rect(sx(hoverX + 0.3f), sy(hoverY + 0.3f), scale * 0.4f, scale * 0.4f);
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

    /** A wedge's art, or null for a block or a size the art pack has no wedge for (drawn flat). */
    AtlasRegion wedgeArt(ChassisDef.HullPiece p) {
        if (!"WEDGE".equals(p.shape)) return null;
        return art.first(ArtNames.wedge(p.material, WeightClass.valueOf(p.weightClass), p.width, p.height));
    }

    boolean drawingArt() {
        return artView && art.region("tiles/floor") != null;
    }

    /** The layout as the deck will look: floors, joined walls, wedges, systems, ports and thin walls. */
    void drawArt(ChassisModel m, ShipPartCatalog cat) {
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
                    case CONDUIT:
                        // A conduit's junction sits on the steel floor and any covering laid over it
                        boolean conduit = t.base == ShipPartCatalog.TileBase.CONDUIT;
                        artTile(conduit ? art.region("tiles/floor") : tileArt(t, "tiles/floor"), x0, y0, 0);
                        ChassisDef.Covering cov = m.coveringAt(x, y);
                        String covering = cov == null ? null : ArtNames.covering(FloorCovering.valueOf(cov.covering));
                        if (covering != null) artTile(art.region(covering), x0, y0, 0);
                        if (!conduit) break;
                        artTile(tileArt(t, ArtNames.conduit(true)), x0, y0, 0);
                        TextureRegion arm = art.region(ArtNames.conduitArm(true));
                        if (conduitToward(m, cat, x, y, Side.TOP)) artTile(arm, x0, y0, 0);
                        if (conduitToward(m, cat, x, y, Side.LEFT)) artTile(arm, x0, y0, 1);
                        if (conduitToward(m, cat, x, y, Side.BOTTOM)) artTile(arm, x0, y0, 2);
                        if (conduitToward(m, cat, x, y, Side.RIGHT)) artTile(arm, x0, y0, 3);
                        break;
                    default:
                        ChassisDef.HullPiece piece = m.hullPieceAt(x, y);
                        if (piece != null && "WEDGE".equals(piece.shape)) break;
                        int mask = 0;
                        if (sameTile(m, x, y + 1, ch)) mask |= ArtNames.N;
                        if (sameTile(m, x + 1, y, ch)) mask |= ArtNames.E;
                        if (sameTile(m, x, y - 1, ch)) mask |= ArtNames.S;
                        if (sameTile(m, x - 1, y, ch)) mask |= ArtNames.W;
                        artTile(wallArt(t, mask), x0, y0, 0);
                        break;
                }
            }
        }
        for (ChassisDef.HullPiece p : m.hullPieces) {
            if (!"WEDGE".equals(p.shape)) continue;
            AtlasRegion r = wedgeArt(p);
            if (r == null) continue;
            int pw = p.rotation % 2 == 0 ? p.width : p.height, ph = p.rotation % 2 == 0 ? p.height : p.width;
            DeckArt.wedge(batch, r, sx(p.x + pw / 2f), sy(p.y + ph / 2f), scale, p.width, p.height, p.rotation,
                    ArtNames.wedgeMirrored(r.name, p.width, p.height));
        }
        for (ChassisModel.Mount mount : m.mounts) {
            MountFootprint f = mount.footprint(cat);
            ShipPartCatalog.ComponentType type = cat.component(mount.component);
            if (f == null || type == null) continue;
            int bw = f.size.baseWidth(f.kind), bh = f.size.baseHeight(f.kind);
            float cx = sx(f.originX + f.width() / 2f), cy = sy(f.originY + f.height() / 2f);
            if (f.kind == MountFootprint.MountKind.PORT) {
                // The opening is steel floor in the game, whatever the map has under it, and shows through the port
                for (int py = f.originY; py < f.originY + f.height(); py++) {
                    for (int px = f.originX; px < f.originX + f.width(); px++) {
                        if (DockingPortLayout.isOpening(f, px, py)) artTile(art.region("tiles/floor"), sx(px), sy(py), 0);
                    }
                }
            }
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
        String[][] hMaterial = new String[h + 1][w], vMaterial = new String[h][w + 1];
        float t = scale / 4f;
        for (ChassisDef.EdgeWall e : m.edgeWalls) {
            Side side = Side.valueOf(e.side);
            WeightClass weight = WeightClass.valueOf(e.weightClass);
            boolean horizontal = side == Side.TOP || side == Side.BOTTOM;
            int ex = e.x + (side == Side.RIGHT ? 1 : 0), ey = e.y + (side == Side.TOP ? 1 : 0);
            if (horizontal && ey >= 0 && ey <= h && ex >= 0 && ex < w) {
                hEdges[ey][ex] = weight;
                hMaterial[ey][ex] = e.material;
            }
            if (!horizontal && ey >= 0 && ey < h && ex >= 0 && ex <= w) {
                vEdges[ey][ex] = weight;
                vMaterial[ey][ex] = e.material;
            }
            TextureRegion r = e.bulkhead ? art.region(ArtNames.door(true, true, horizontal))
                    : art.first(ArtNames.edgeWall(e.material, weight, horizontal));
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
                // The heaviest wall meeting here sets the joint's weight and material
                WeightClass weight = null;
                String material = null;
                WeightClass[] around = {n, e, so, we};
                String[] materials = {y < h ? vMaterial[y][x] : null, x < w ? hMaterial[y][x] : null,
                        y > 0 ? vMaterial[y - 1][x] : null, x > 0 ? hMaterial[y][x - 1] : null};
                for (int i = 0; i < 4; i++) {
                    if (around[i] != null && (weight == null || around[i].ordinal() > weight.ordinal())) {
                        weight = around[i];
                        material = materials[i];
                    }
                }
                TextureRegion r = art.first(ArtNames.edgeJoint(material, weight, mask));
                if (r != null) batch.draw(r, sx(x) - t / 2f, sy(y) - t / 2f, t, t);
            }
        }
        drawEdgeConduitArt(m, cat, t);
        DeckArt.drawPipes(batch, art, pipeLayout(m), sx(0), sy(0), scale);
        drawFittingArt(m);
        batch.end();
    }

    /**
     * Wall lights on their walls and RCS blocks on the hull, drawn as the deck and the exterior
     * draw them, and the one the Light or RCS tool would place, faded.
     */
    void drawFittingArt(ChassisModel m) {
        boolean hovering = tab == Tab.LAYOUT && m.inBounds(hoverX, hoverY);
        AtlasRegion light = wallLightArt();
        if (light != null) {
            if (m.lights != null) {
                for (ChassisDef.Light l : m.lights) wallLight(light, new WallLight(l.x, l.y, Side.valueOf(l.side)));
            }
            if (hovering && tool == Tool.LIGHT) {
                batch.setColor(1f, 1f, 1f, 0.5f);
                wallLight(light, new WallLight(hoverX, hoverY, nearestSide()));
                batch.setColor(Color.WHITE);
            }
        }
        for (ChassisDef.Rcs r : m.rcs) {
            AtlasRegion block = rcsArt(r.kind);
            if (block != null) rcsBlock(block, r.x, r.y, Side.valueOf(r.side));
        }
        AtlasRegion ghost = hovering && tool == Tool.RCS ? rcsArt(rcsKind) : null;
        if (ghost != null) {
            batch.setColor(1f, 1f, 1f, 0.5f);
            rcsBlock(ghost, hoverX, hoverY, nearestSide());
            batch.setColor(Color.WHITE);
        }
    }

    /** The wall light's art, or null when the art isn't being drawn or the pack has none. */
    AtlasRegion wallLightArt() {
        return drawingArt() ? art.region(ArtNames.wallLight(true)) : null;
    }

    /** An RCS block's art, or null when the art isn't being drawn or the pack has none for {@code kind}. */
    AtlasRegion rcsArt(String kind) {
        return drawingArt() ? art.region(ArtNames.rcs(kind, true)) : null;
    }

    /** A wall light's art with its wall against the edge it hangs on, as {@code DeckArt} draws it. */
    void wallLight(AtlasRegion r, WallLight p) {
        ArtLibrary.Info info = art.info(r);
        float u = scale / ArtLibrary.UNITS_PER_TILE;
        float w = (info == null ? r.getRegionWidth() : info.w) * u, h = (info == null ? r.getRegionHeight() : info.h) * u;
        float ex = sx(p.edgeX()), ey = sy(p.edgeY());
        batch.draw(r, ex - w / 2f, ey - h, w / 2f, h, w, h, 1f, 1f, p.rotation() * 90f);
    }

    /**
     * An RCS block's art pivoted on the middle of the {@code s} edge of tile (x, y), its main nozzle
     * out toward {@code s}, as the exterior draws it.
     */
    void rcsBlock(AtlasRegion r, int x, int y, Side s) {
        ArtLibrary.Info info = art.info(r);
        float u = scale / ArtLibrary.UNITS_PER_TILE;
        float left = info == null ? -r.originalWidth / 2f * u : info.x * u;
        float top = info == null ? r.originalHeight / 2f * u : -info.y * u;
        float w = (info == null ? r.originalWidth : info.w) * u, h = (info == null ? r.originalHeight : info.h) * u;
        // SVG y runs down, so the region's bottom edge is at -(viewBox y + height)
        float bottom = top - h;
        float degrees = MathUtils.atan2(s.dy, s.dx) * MathUtils.radiansToDegrees - 180f;
        float px = sx(x + 0.5f + s.dx * 0.5f), py = sy(y + 0.5f + s.dy * 0.5f);
        batch.draw(r, px + left, py + bottom, -left, -bottom, w, h, 1f, 1f, degrees);
    }

    /**
     * Conduits along edges, over the walls they run in: segments, a joint at each vertex they reach
     * and a tap where a conduit tile meets one; two stretched conduit arms each without that art.
     */
    void drawEdgeConduitArt(ChassisModel m, ShipPartCatalog cat, float t) {
        int w = m.width(), h = m.height();
        TextureRegion segmentH = art.region(ArtNames.edgeConduit(true, true));
        TextureRegion segmentV = art.region(ArtNames.edgeConduit(true, false));
        if (segmentH == null || segmentV == null) {
            TextureRegion arm = art.region(ArtNames.conduitArm(true));
            if (arm == null) return;
            for (ChassisDef.EdgeConduit c : m.edgeConduits) {
                Side side = Side.valueOf(c.side);
                DeckArt.edgeConduit(batch, arm, sx(c.x + 0.5f + side.dx * 0.5f), sy(c.y + 0.5f + side.dy * 0.5f), scale,
                        side.dy != 0);
            }
            return;
        }
        boolean[][] hRuns = new boolean[h + 1][w], vRuns = new boolean[h][w + 1];
        for (ChassisDef.EdgeConduit c : m.edgeConduits) {
            Side side = Side.valueOf(c.side);
            int ex = c.x + (side == Side.RIGHT ? 1 : 0), ey = c.y + (side == Side.TOP ? 1 : 0);
            if (side.dy != 0 && ey >= 0 && ey <= h && ex >= 0 && ex < w) {
                hRuns[ey][ex] = true;
                batch.draw(segmentH, sx(ex), sy(ey) - t / 2f, scale, t);
            } else if (side.dx != 0 && ey >= 0 && ey < h && ex >= 0 && ex <= w) {
                vRuns[ey][ex] = true;
                batch.draw(segmentV, sx(ex) - t / 2f, sy(ey), t, scale);
            }
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x <= w; x++) {
                int mask = (y < h && vRuns[y][x] ? ArtNames.N : 0) | (x < w && hRuns[y][x] ? ArtNames.E : 0)
                        | (y > 0 && vRuns[y - 1][x] ? ArtNames.S : 0) | (x > 0 && hRuns[y][x - 1] ? ArtNames.W : 0);
                if (mask == 0) continue;
                TextureRegion r = art.region(ArtNames.edgeConduitJoint(true, mask));
                if (r != null) batch.draw(r, sx(x) - t / 2f, sy(y) - t / 2f, t, t);
            }
        }
        TextureRegion tap = art.region(ArtNames.edgeConduitTap(true));
        if (tap == null) return;
        for (ChassisDef.EdgeConduit c : m.edgeConduits) {
            Side side = Side.valueOf(c.side);
            float cx = sx(c.x + 0.5f + side.dx * 0.5f), cy = sy(c.y + 0.5f + side.dy * 0.5f);
            // On whichever tile beside the edge carries a conduit across it
            if (conduitAt(m, cat, c.x, c.y) || conduitAt(m, cat, c.x + side.dx, c.y + side.dy)) {
                batch.draw(tap, cx - t / 2f, cy - t / 2f, t, t);
            }
        }
    }

    /** The layout's pipes, for {@link DeckArt#drawPipes}. */
    static DeckArt.PipeLayout pipeLayout(ChassisModel m) {
        return new DeckArt.PipeLayout() {
            public int width() { return m.width(); }
            public int height() { return m.height(); }
            public boolean across(int x, int y) { return m.pipeAt(x, y, null) != null; }
            public boolean alongH(int x, int y) {
                return y < m.height() ? m.pipeAt(x, y, Side.BOTTOM) != null : m.pipeAt(x, y - 1, Side.TOP) != null;
            }
            public boolean alongV(int x, int y) {
                return x < m.width() ? m.pipeAt(x, y, Side.LEFT) != null : m.pipeAt(x - 1, y, Side.RIGHT) != null;
            }
        };
    }

    /** Whether a pipe across tile (x, y) reaches toward {@code side}: to the next tile's, or one along that edge. */
    static boolean pipeToward(ChassisModel m, int x, int y, Side side) {
        return m.pipeAt(x, y, side) != null || m.pipeAt(x + side.dx, y + side.dy, null) != null;
    }

    /**
     * A wall type's art joined on the sides in {@code mask}, best first: the family its {@code art}
     * key names, art named after its id (hull, hull_heavy...), its material's, then hull steel's.
     */
    AtlasRegion wallArt(ShipPartCatalog.TileType t, int mask) {
        String sides = "_" + ArtNames.sides(mask);
        String weight = ArtNames.weight(t.weightClass);
        java.util.List<String> names = new java.util.ArrayList<>(5);
        if (t.art != null) names.add(t.art + sides);
        names.add(ArtNames.wall(t.id, mask));
        if (t.base == ShipPartCatalog.TileBase.INTERNAL_WALL) names.add(ArtNames.wall("internal_wall", mask));
        names.add(ArtNames.wall(ArtNames.materialPrefix(t.material) + "_" + weight, mask));
        names.add(ArtNames.wall("hull_" + weight, mask));
        return art.first(names.toArray(new String[0]));
    }

    /** A floor type's art: the region its {@code art} key names, else {@code fallback}. */
    AtlasRegion tileArt(ShipPartCatalog.TileType t, String fallback) {
        return t.art == null ? art.region(fallback) : art.first(t.art, fallback);
    }

    static boolean sameTile(ChassisModel m, int x, int y, char ch) {
        return m.inBounds(x, y) && m.tiles[y][x] == ch;
    }

    /** Whether a conduit on tile (x, y) reaches toward {@code side}: to the next tile's, or one along that edge. */
    static boolean conduitToward(ChassisModel m, ShipPartCatalog cat, int x, int y, Side side) {
        return m.edgeConduitAt(x, y, side) != null || conduitAt(m, cat, x + side.dx, y + side.dy);
    }

    static boolean conduitAt(ChassisModel m, ShipPartCatalog cat, int x, int y) {
        if (!m.inBounds(x, y)) return false;
        ShipPartCatalog.TileType t = cat.tileFor(m.tiles[y][x]);
        return t != null && t.base == ShipPartCatalog.TileBase.CONDUIT;
    }

    /** A one-tile region at a tile's bottom-left corner, turned {@code quarters} about its middle. */
    void artTile(TextureRegion r, float x0, float y0, int quarters) {
        if (r != null) batch.draw(r, x0, y0, scale / 2f, scale / 2f, scale, scale, 1f, 1f, quarters * 90f);
    }

    void artCentered(TextureRegion r, float cx, float cy, float w, float h, int quarters) {
        if (r != null) batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, quarters * 90f);
    }

    String describeTile(ChassisModel m, ShipPartCatalog cat, int x, int y) {
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

    void edgeRect(int x, int y, Side s, float t) {
        switch (s) {
            case TOP: shapes.rect(sx(x), sy(y + 1) - t / 2, scale, t); break;
            case BOTTOM: shapes.rect(sx(x), sy(y) - t / 2, scale, t); break;
            case LEFT: shapes.rect(sx(x) - t / 2, sy(y), t, scale); break;
            default: shapes.rect(sx(x + 1) - t / 2, sy(y), t, scale); break;
        }
    }

    /** A wall light on the {@code s} edge of tile (x, y): half the edge long, a little way into the tile. */
    /** An RCS block of {@code kind} on the {@code s} edge of tile (x, y), out past it, with its nozzles. */
    void rcsRect(int x, int y, Side s, String kind) {
        float len = scale * 0.4f, depth = Math.max(3f, scale * 0.15f), tick = Math.max(2f, scale * 0.1f);
        float cx = sx(x + 0.5f + s.dx * 0.5f), cy = sy(y + 0.5f + s.dy * 0.5f);
        boolean quad = "QUAD".equals(kind);
        if (s.dy != 0) {
            float y0 = s.dy > 0 ? cy : cy - depth;
            shapes.rect(cx - len / 2f, y0, len, depth);
            shapes.rect(cx - tick / 2f, s.dy > 0 ? y0 + depth : y0 - tick, tick, tick);
            if (quad) {
                shapes.rect(cx - len / 2f - tick, y0 + depth / 2f - tick / 2f, tick, tick);
                shapes.rect(cx + len / 2f, y0 + depth / 2f - tick / 2f, tick, tick);
            }
        } else {
            float x0 = s.dx > 0 ? cx : cx - depth;
            shapes.rect(x0, cy - len / 2f, depth, len);
            shapes.rect(s.dx > 0 ? x0 + depth : x0 - tick, cy - tick / 2f, tick, tick);
            if (quad) {
                shapes.rect(x0 + depth / 2f - tick / 2f, cy - len / 2f - tick, tick, tick);
                shapes.rect(x0 + depth / 2f - tick / 2f, cy + len / 2f, tick, tick);
            }
        }
    }

    void lightRect(int x, int y, Side s) {
        float len = scale * 0.5f, depth = Math.max(3f, scale * 0.15f);
        float cx = sx(x + 0.5f + s.dx * 0.5f), cy = sy(y + 0.5f + s.dy * 0.5f);
        if (s.dy != 0) shapes.rect(cx - len / 2f, s.dy > 0 ? cy - depth : cy, len, depth);
        else shapes.rect(s.dx > 0 ? cx - depth : cx, cy - len / 2f, depth, len);
    }

    void drawPiece(String shape, int x, int y, int baseW, int baseH, int rot) {
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

    void rotateTool() {
        rotation = (rotation + 1) % 4;
        say("Rotation " + rotation * 90 + " deg");
    }

    /**
     * Mirrors the hull piece tool left to right. A mirrored wedge is the same wedge with its width
     * and height swapped, turned one step the other way, so the data needs no flip of its own.
     */
    void flipPiece() {
        int w = pieceWidth;
        pieceWidth = pieceHeight;
        pieceHeight = w;
        rotation ^= 1;
        say("Flipped");
    }

    /** Turns the component under the cursor, if there is one; false leaves R to turn the tool. */
    boolean rotateHovered() {
        ChassisModel m = model();
        if (tab != Tab.LAYOUT || tool == Tool.HULL || m == null || !m.inBounds(hoverX, hoverY)) return false;
        ShipPartCatalog cat = catalog();
        ChassisModel.Mount mount = m.mountAt(hoverX, hoverY, cat);
        if (mount == null) return false;
        if (!editable()) {
            say("This chassis comes from a mod and cannot be edited here");
            return true;
        }
        ChassisModel trial = m.copy();
        String why = trial.rotate(trial.mountAt(hoverX, hoverY, cat), cat);
        if (why != null) {
            say(why);
            return true;
        }
        beginEdit();
        m.rotate(mount, cat);
        changed();
        // The next one placed faces the same way
        rotation = mount.rotation;
        ShipPartCatalog.ComponentType type = cat.component(mount.component);
        say((type != null ? type.name : mount.component) + " turned to " + mount.rotation * 90 + " deg");
        return true;
    }

    /** A small arrow inside a mount, pointing the way the system faces (+y at rotation 0). */
    void drawFacing(MountFootprint f) {
        Side out = DockingPortLayout.facing(f);
        float cx = sx(f.originX + f.width() / 2f), cy = sy(f.originY + f.height() / 2f);
        float reach = Math.min(f.width(), f.height()) * scale * 0.5f - 4f;
        float tipX = cx + out.dx * reach, tipY = cy + out.dy * reach;
        float back = Math.min(scale * 0.35f, reach), half = back * 0.6f;
        float bx = tipX - out.dx * back, by = tipY - out.dy * back;
        shapes.triangle(tipX, tipY, bx - out.dy * half, by + out.dx * half, bx + out.dy * half, by - out.dx * half);
    }

    // ---- Ghosts: what the tool would do at the cursor ----

    int[] ghostOrigin(MountFootprint probe) {
        return new int[]{hoverX - (probe.width() - 1) / 2, hoverY - (probe.height() - 1) / 2};
    }

    MountFootprint ghostFootprint(ShipPartCatalog cat) {
        ShipPartCatalog.ComponentType type = cat.component(componentId);
        if (type == null || !size.exists(type.mountKind())) return null;
        MountFootprint probe = new MountFootprint(type.mountKind(), size, 0, 0, rotation);
        int[] o = ghostOrigin(probe);
        return new MountFootprint(type.mountKind(), size, o[0], o[1], rotation);
    }

    /** The bottom-left tile of the hull piece tool's footprint, centered on the cursor. */
    int[] pieceOrigin() {
        int w = rotation % 2 == 0 ? pieceWidth : pieceHeight, h = rotation % 2 == 0 ? pieceHeight : pieceWidth;
        return new int[]{hoverX - (w - 1) / 2, hoverY - (h - 1) / 2};
    }

    /** Tries the hull piece tool at the cursor on a copy, and remembers whether the game would take it. */
    void updatePieceGhost(ChassisModel m, int[] o) {
        String key = revision + ":piece:" + pieceShape + ":" + pieceWidth + "x" + pieceHeight + ":" + rotation + ":" + o[0] + "," + o[1];
        if (key.equals(ghostKey)) return;
        ghostKey = key;
        ChassisModel copy = m.copy();
        if (copy.addHullPiece(pieceShape, o[0], o[1], pieceWidth, pieceHeight, rotation, weight.name()) == null) {
            ghostValid = false;
            ghostReason = "it would not fit on the grid";
            return;
        }
        judgeGhost(copy, null);
    }

    /** Tries the component tool at the cursor on a copy, and remembers whether the game would take it. */
    void updateGhost(ChassisModel m, MountFootprint f) {
        String key = revision + ":" + componentId + ":" + size + ":" + rotation + ":" + f.originX + "," + f.originY;
        if (key.equals(ghostKey)) return;
        ghostKey = key;
        ChassisModel copy = m.copy();
        copy.place(componentId, size, f.originX, f.originY, rotation, catalog());
        ShipPartCatalog.ComponentType type = catalog().component(componentId);
        judgeGhost(copy, type != null && !type.sizes.contains(size) ? type.name + " does not come in size " + size : null);
    }

    /** Valid when the edited copy builds, or fails only the way the layout already did. */
    void judgeGhost(ChassisModel copy, String problem) {
        EditorProject.Check before = check();
        EditorProject.Check after = project.check(copy);
        if (problem != null) {
            ghostValid = false;
            ghostReason = problem;
        } else if (after.error == null || (before.error != null && before.error.equals(after.error))) {
            ghostValid = true;
            ghostReason = null;
        } else {
            ghostValid = false;
            ghostReason = after.error;
        }
    }

    void drawGhostOutline(ChassisModel m, ShipPartCatalog cat) {
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
            int[] o = pieceOrigin();
            updatePieceGhost(m, o);
            shapes.setColor(ghostValid ? Color.GREEN : Color.RED);
            shapes.rect(sx(o[0]), sy(o[1]), w * scale, h * scale);
        } else if (tool != Tool.EDGE && tool != Tool.CONDUIT && tool != Tool.PIPE) {
            shapes.setColor(Color.YELLOW);
            shapes.rect(sx(hoverX), sy(hoverY), scale, scale);
        }
    }

    void drawGhostFill(ChassisModel m, ShipPartCatalog cat) {
        if (tab != Tab.LAYOUT || !m.inBounds(hoverX, hoverY) || panning) return;
        if (tool == Tool.COMPONENT) {
            MountFootprint f = ghostFootprint(cat);
            ShipPartCatalog.ComponentType type = cat.component(componentId);
            if (f == null || type == null) return;
            setColor(type.color, 0.4f);
            shapes.setColor(color);
            shapes.rect(sx(f.originX), sy(f.originY), f.width() * scale, f.height() * scale);
            if (f.kind != MountFootprint.MountKind.PORT) {
                shapes.setColor(1f, 1f, 1f, 0.6f);
                drawFacing(f);
            }
        } else if (tool == Tool.HULL) {
            shapes.setColor(0.7f, 0.75f, 0.8f, 0.5f);
            int[] o = pieceOrigin();
            drawPiece(pieceShape, o[0], o[1], pieceWidth, pieceHeight, rotation);
        } else if (tool == Tool.PAINT) {
            ShipPartCatalog.TileType t = cat.tile(tileId);
            if (t == null) return;
            setColor(t.color, 0.6f);
            shapes.setColor(color);
            shapes.rect(sx(hoverX), sy(hoverY), scale, scale);
        }
    }

    Side nearestSide() {
        float fx = hoverFx - hoverX, fy = hoverFy - hoverY;
        float dl = fx, dr = 1 - fx, db = fy, dt = 1 - fy;
        float min = Math.min(Math.min(dl, dr), Math.min(db, dt));
        if (min == dl) return Side.LEFT;
        if (min == dr) return Side.RIGHT;
        if (min == db) return Side.BOTTOM;
        return Side.TOP;
    }

    /**
     * Where the conduit tool works under the cursor: the nearest edge, or null in the middle of
     * the tile, where it lays a conduit across the tile instead.
     */
    Side conduitSide() {
        float fx = hoverFx - hoverX, fy = hoverFy - hoverY;
        float edge = Math.min(Math.min(fx, 1 - fx), Math.min(fy, 1 - fy));
        return edge < 0.3f ? nearestSide() : null;
    }
}
