package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

/** Hand-built grids for tests. */
final class TestShips {
    private TestShips() {
    }

    /**
     * A w x h box: a ring of hull tiles round an open, carpeted, pressurized floor. Grid origin
     * is set so the box is centred on the body origin.
     */
    static ShipGridComponent box(int w, int h, float tileSize, WeightClass hullClass) {
        ShipGridComponent grid = new ShipGridComponent(w, h, tileSize, -w * tileSize / 2f, -h * tileSize / 2f);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = grid.getTile(x, y);
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    t.wallType = Tile.WallType.HULL;
                    t.weightClass = hullClass;
                } else {
                    t.floorType = Tile.FloorType.FLOOR;
                    t.setCovering(FloorCovering.CARPET, tileSize);
                }
            }
        }
        CompartmentBuilder.rebuild(grid);
        return grid;
    }

    /** Installs a primary system of the given type in an S (1x1) or M (2x2) mount. */
    static Entity install(ShipGridComponent grid, SubsystemComponent.SubsystemType type,
                          MountFootprint.MountSize size, int x, int y) {
        MountFootprint m = new MountFootprint(type.mountKind, size, x, y, 0);
        grid.placeMount(m);
        Entity e = new Entity();
        e.add(new SubsystemComponent(m, type));
        m.installed = e;
        return e;
    }
}
