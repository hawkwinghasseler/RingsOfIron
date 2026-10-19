package com.hawkwing.RingsOfIron.art;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Draws a deck with the art pack: floors, coverings and conduits, walls joined to their
 * neighbors, wedges, the systems in their mounts (dark when they have no power), docking ports
 * open or shut, thin walls and bulkhead doors, damage, breaches, seals, fire and cargo.
 *
 * Everything is in the grid's local meters, so the batch's transform carries the craft's pose.
 * What has no art is left out, and the caller keeps its flat shapes for what this cannot draw
 * (see {@link #available()}).
 */
public final class DeckArt {
    private final ArtLibrary art;
    private final Color tint = new Color();
    /** Where each crew member was last drawn and which way they faced: {x, y, degrees}. */
    private final Map<Object, float[]> facing = new IdentityHashMap<>();
    /** The crate of each carrying crew region, for tinting it alone. */
    private final Map<TextureRegion, TextureRegion> crates = new IdentityHashMap<>();

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
        effects(batch, grid);
        cargo(batch, grid, hold);
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
                // A covering stops at a conduit, so the conduit stays in sight
                String covering = ArtNames.covering(t.covering);
                if (covering != null && !t.isPowerConduit()) tile(batch, covering, x0, y0, s);
                if (!t.isPowerConduit()) continue;
                TextureRegion arm = art.region(ArtNames.conduitArm(t.isPowered));
                if (arm == null) continue;
                // 0 up, 1 left, 2 down, 3 right
                if (conduit(grid, x, y + 1)) quarter(batch, arm, x0, y0, s, 0);
                if (conduit(grid, x - 1, y)) quarter(batch, arm, x0, y0, s, 1);
                if (conduit(grid, x, y - 1)) quarter(batch, arm, x0, y0, s, 2);
                if (conduit(grid, x + 1, y)) quarter(batch, arm, x0, y0, s, 3);
            }
        }
    }

    private static boolean conduit(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        return t != null && t.isPowerConduit();
    }

    private void walls(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || !t.isWall() || t.isOpen()) continue;
                if (t.piece != null && t.piece.shape == HullPiece.Shape.WEDGE) continue;
                String id = ArtNames.wallId(t);
                int mask = 0;
                if (id.equals(ArtNames.wallId(grid.getTile(x, y + 1)))) mask |= ArtNames.N;
                if (id.equals(ArtNames.wallId(grid.getTile(x + 1, y)))) mask |= ArtNames.E;
                if (id.equals(ArtNames.wallId(grid.getTile(x, y - 1)))) mask |= ArtNames.S;
                if (id.equals(ArtNames.wallId(grid.getTile(x - 1, y)))) mask |= ArtNames.W;
                tile(batch, ArtNames.wall(id, mask), grid.gridOriginLocal.x + x * s, grid.gridOriginLocal.y + y * s, s);
            }
        }
        for (HullPiece p : grid.hullPieces) {
            if (p.shape != HullPiece.Shape.WEDGE) continue;
            TextureRegion r = art.region(ArtNames.wedge(p));
            if (r == null) continue;
            footprint(batch, r, grid, p.originX, p.originY, p.width(), p.height(), p.baseWidth, p.baseHeight, p.rotation);
        }
    }

    private void systems(Batch batch, ShipGridComponent grid, boolean power) {
        float s = grid.tileSize;
        for (MountFootprint m : grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub == null) continue;
            boolean port = m.kind == MountFootprint.MountKind.PORT;
            boolean powered = port ? power && !sub.isDisabled() : sub.isOperational;
            String state = port && portOpen(grid, m) ? "open" : null;
            TextureRegion r = art.first(ArtNames.component(sub.component, sub.type, m.size, state, powered));
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
                String name = e.hasBulkhead ? ArtNames.door(e.bulkheadSealed, power, true) : ArtNames.edgeWall(e.weightClass, true);
                draw(batch, art.region(name), ox + x * s, oy + y * s - half, s, 2f * half);
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall e = grid.edgeV[y][x];
                if (!standing(e)) continue;
                String name = e.hasBulkhead ? ArtNames.door(e.bulkheadSealed, power, false) : ArtNames.edgeWall(e.weightClass, false);
                draw(batch, art.region(name), ox + x * s - half, oy + y * s, 2f * half, s);
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
                WeightClass weight = heaviest(heaviest(heaviest(heaviest(null, n), e), so), w);
                draw(batch, art.region(ArtNames.edgeJoint(weight, mask)), ox + x * s - half, oy + y * s - half,
                        2f * half, 2f * half);
            }
        }
    }

    private void effects(Batch batch, ShipGridComponent grid) {
        float s = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                float x0 = grid.gridOriginLocal.x + x * s, y0 = grid.gridOriginLocal.y + y * s;
                if (t.isWall() && !t.isOpen()) {
                    if (t.isHullBreach()) tile(batch, "effects/breach", x0, y0, s);
                    else {
                        String damage = ArtNames.damage(t.getIntegrity());
                        if (damage != null) tile(batch, damage, x0, y0, s);
                    }
                }
                if (t.mount != null && t.mount.installed != null) {
                    SubsystemComponent sub = t.mount.installed.getComponent(SubsystemComponent.class);
                    String damage = sub == null ? null : ArtNames.damage(sub.health);
                    if (damage != null) tile(batch, damage, x0, y0, s);
                }
                if (t.bulkheadSealed) tile(batch, "effects/bulkhead_seal", x0, y0, s);
                String fire = ArtNames.fire(t.fireSize());
                if (fire != null) tile(batch, fire, x0, y0, s);
            }
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
        }
    }

    /** Whether there is crew art; without it, draw crew as dots. */
    public boolean hasCrew() {
        return art.region(ArtNames.crew(ArtNames.Crew.SHIP, false)) != null;
    }

    /**
     * A crew member at ({@code x}, {@code y}) in the grid's local meters, turned the way they last
     * walked, with the crate they carry tinted to its commodity ({@code commodity}, or null).
     * {@code key} is the crew member, to remember their facing.
     */
    public void crew(Batch batch, Object key, ArtNames.Crew who, String commodity, float x, float y, float tileSize) {
        TextureRegion r = art.first(ArtNames.crew(who, commodity != null), ArtNames.crew(who, false));
        if (r == null) return;
        float[] f = facing.get(key);
        if (f == null) facing.put(key, f = new float[]{x, y, 0f});
        float dx = x - f[0], dy = y - f[1];
        if (dx * dx + dy * dy > 0.0004f * tileSize * tileSize) {
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

    private static WeightClass heaviest(WeightClass a, EdgeWall e) {
        if (!standing(e)) return a;
        return a == null || e.weightClass.ordinal() > a.ordinal() ? e.weightClass : a;
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

    private static void draw(Batch batch, TextureRegion r, float x, float y, float w, float h) {
        if (r != null) batch.draw(r, x, y, w, h);
    }

    /** A one-tile region turned {@code quarters} counter-clockwise about the tile's middle. */
    private static void quarter(Batch batch, TextureRegion r, float x0, float y0, float s, int quarters) {
        batch.draw(r, x0, y0, s / 2f, s / 2f, s, s, 1f, 1f, quarters * 90f);
    }

    /** Art drawn at its unrotated size {@code bw} x {@code bh} tiles, turned onto a footprint. */
    private static void footprint(Batch batch, TextureRegion r, ShipGridComponent grid, int originX, int originY,
                                  int width, int height, int bw, int bh, int rotation) {
        float s = grid.tileSize;
        float cx = grid.gridOriginLocal.x + (originX + width / 2f) * s;
        float cy = grid.gridOriginLocal.y + (originY + height / 2f) * s;
        centered(batch, r, cx, cy, bw * s, bh * s, rotation);
    }

    private static void centered(Batch batch, TextureRegion r, float cx, float cy, float w, float h, int rotation) {
        batch.draw(r, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, rotation * 90f);
    }
}
