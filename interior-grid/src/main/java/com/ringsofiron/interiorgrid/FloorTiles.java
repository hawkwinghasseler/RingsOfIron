package com.ringsofiron.interiorgrid;

import java.util.ArrayList;
import java.util.List;

/** Where crew can stand on a deck: the open floor tiles, in the order the game places people. */
public final class FloorTiles {
    private FloorTiles() {
    }

    /** Open deck tiles clear of mounted systems, scanning from the middle row outwards. */
    public static List<int[]> of(ShipGridComponent grid) {
        List<int[]> out = new ArrayList<>();
        if (grid == null) return out;
        for (int dy = 0; dy < grid.height; dy++) {
            int y = grid.height / 2 + ((dy % 2 == 0) ? dy / 2 : -(dy / 2 + 1));
            if (y < 0 || y >= grid.height) continue;
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor() && t.isOpen() && t.mount == null) out.add(new int[]{x, y});
            }
        }
        return out;
    }
}
