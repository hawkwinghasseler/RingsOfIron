package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

/**
 * Data describing a ship chassis, consumed by {@link ChassisBlueprintLoader}.
 *
 * Tiles come from a character map ({@link #rows}, written top-to-bottom as it reads on
 * screen). Everything a character cannot express (edge walls, multi-tile hull pieces,
 * explicit mounts, floor coverings) is listed separately in grid coordinates, where (0, 0) is the
 * bottom-left tile and +y points up, matching Box2D.
 */
public class ChassisBlueprint {

    /** An edge wall on one side of a tile. */
    public static class EdgeWallDef {
        public final int x, y;
        public final Side side;
        public final WeightClass weightClass;
        public final String material;
        public final boolean hasBulkhead;

        public EdgeWallDef(int x, int y, Side side, WeightClass weightClass, String material, boolean hasBulkhead) {
            this.x = x;
            this.y = y;
            this.side = side;
            this.weightClass = weightClass;
            this.material = material;
            this.hasBulkhead = hasBulkhead;
        }
    }

    /** A floor covering laid over every floor tile in a rectangle. */
    public static class CoveringDef {
        public final FloorCovering covering;
        public final int x, y, width, height;

        public CoveringDef(FloorCovering covering, int x, int y, int width, int height) {
            this.covering = covering;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** A system installed in an explicitly placed mount. */
    public static class MountDef {
        public final MountFootprint footprint;
        public final SubsystemComponent.SubsystemType type;
        /** The component installed, or null for the type's standard one in the blueprint's catalog. */
        public final ShipPartCatalog.ComponentType component;

        public MountDef(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
            this.footprint = footprint;
            this.type = type;
            this.component = null;
        }

        public MountDef(MountFootprint footprint, ShipPartCatalog.ComponentType component) {
            this.footprint = footprint;
            this.type = component.behavior;
            this.component = component;
        }
    }

    /** Character map, one string per row, top row first. See {@link ChassisBlueprintLoader}. */
    public final String[] rows;
    /** World-unit size of each tile (e.g. 1.0f for 1 metre per tile). */
    public float tileSize = 1f;
    /** Body-local position (meters) of the bottom-left corner of tile (0, 0). */
    public float originLocalX = 0f, originLocalY = 0f;

    public final Array<EdgeWallDef> edgeWalls = new Array<>();
    public final Array<HullPiece> hullPieces = new Array<>();
    public final Array<MountDef> mounts = new Array<>();
    public final Array<CoveringDef> coverings = new Array<>();
    /** Lights hung on walls; null when the layout does not place any (see {@link ShipGridComponent#wallLights}). */
    public Array<WallLight> wallLights;
    /** The tile types and components the map's characters stand for. */
    public ShipPartCatalog catalog = ShipPartCatalog.builtIn();

    public ChassisBlueprint(String... rows) {
        this.rows = rows;
    }

    public ChassisBlueprint tileSize(float tileSize) {
        this.tileSize = tileSize;
        return this;
    }

    public ChassisBlueprint catalog(ShipPartCatalog catalog) {
        this.catalog = catalog;
        return this;
    }

    public ChassisBlueprint origin(float localX, float localY) {
        this.originLocalX = localX;
        this.originLocalY = localY;
        return this;
    }

    public ChassisBlueprint edgeWall(int x, int y, Side side, WeightClass weightClass, boolean hasBulkhead) {
        edgeWalls.add(new EdgeWallDef(x, y, side, weightClass, MaterialTable.DEFAULT_MATERIAL, hasBulkhead));
        return this;
    }

    public ChassisBlueprint hullPiece(HullPiece piece) {
        hullPieces.add(piece);
        return this;
    }

    /** Lays a covering on every floor tile in the rectangle with bottom-left tile (x, y). */
    public ChassisBlueprint covering(FloorCovering covering, int x, int y, int width, int height) {
        coverings.add(new CoveringDef(covering, x, y, width, height));
        return this;
    }

    public ChassisBlueprint mount(MountFootprint footprint, SubsystemComponent.SubsystemType type) {
        mounts.add(new MountDef(footprint, type));
        return this;
    }

    public ChassisBlueprint mount(MountFootprint footprint, ShipPartCatalog.ComponentType component) {
        mounts.add(new MountDef(footprint, component));
        return this;
    }
}
