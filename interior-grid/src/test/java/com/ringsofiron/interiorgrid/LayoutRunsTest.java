package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** How a layout writes conduits, pipes and thin walls: runs of tiles and of edges. */
class LayoutRunsTest {

    private static Set<String> tiles(List<int[][]> runs) {
        Set<String> out = new HashSet<>();
        for (int[][] run : runs) for (int[] t : LayoutRuns.tiles(run)) out.add(t[0] + "," + t[1]);
        return out;
    }

    private static String name(LayoutRuns.Edge e) {
        return e.x + "," + e.y + " " + e.side;
    }

    @Test
    void aTileRunLiesOnEveryTileItPassesOnce() {
        List<int[]> t = LayoutRuns.tiles(new int[][]{{1, 1}, {3, 1}, {3, 3}});
        List<String> names = new ArrayList<>();
        for (int[] p : t) names.add(p[0] + "," + p[1]);
        assertEquals(List.of("1,1", "2,1", "3,1", "3,2", "3,3"), names, "the corner tile once");
        assertEquals(1, LayoutRuns.tiles(new int[][]{{4, 2}}).size(), "a run of one point is one tile");
        assertEquals(3, LayoutRuns.tiles(new int[][]{{2, 0}, {0, 0}}).size(), "either way along");
    }

    @Test
    void aRunGoesStraightAcrossOrUpAndDown() {
        IllegalArgumentException diagonal = assertThrows(IllegalArgumentException.class,
                () -> LayoutRuns.tiles(new int[][]{{0, 0}, {2, 2}}));
        assertTrue(diagonal.getMessage().contains("not straight"), diagonal.getMessage());
        assertThrows(IllegalArgumentException.class, () -> LayoutRuns.tiles(new int[0][]));
        assertThrows(IllegalArgumentException.class, () -> LayoutRuns.tiles(null), "a wall written the old way, with no path");
        assertThrows(IllegalArgumentException.class, () -> LayoutRuns.tiles(new int[][]{{1}}));
        IllegalArgumentException one = assertThrows(IllegalArgumentException.class,
                () -> LayoutRuns.edges(new int[][]{{1, 1}}, 4, 4));
        assertTrue(one.getMessage().contains("at least 2"), one.getMessage());
    }

    @Test
    void anEdgeRunGoesCornerToCornerAndNamesEachEdgeOneWay() {
        // Along the bottom of row 2, then up the left of column 3
        List<String> names = new ArrayList<>();
        for (LayoutRuns.Edge e : LayoutRuns.edges(new int[][]{{1, 2}, {3, 2}, {3, 4}}, 5, 5)) names.add(name(e));
        assertEquals(List.of("1,2 BOTTOM", "2,2 BOTTOM", "3,2 LEFT", "3,3 LEFT"), names);
        // On the grid's top and right borders the edge is named from the tile inside
        assertEquals("0,4 TOP", name(LayoutRuns.edges(new int[][]{{0, 5}, {1, 5}}, 5, 5).get(0)));
        assertEquals("4,0 RIGHT", name(LayoutRuns.edges(new int[][]{{5, 0}, {5, 1}}, 5, 5).get(0)));
        // Backwards along the same edges names them the same
        assertEquals("1,2 BOTTOM", name(LayoutRuns.edges(new int[][]{{2, 2}, {1, 2}}, 5, 5).get(0)));
        assertThrows(IllegalArgumentException.class, () -> LayoutRuns.edges(new int[][]{{5, 1}, {6, 1}}, 5, 5), "off the grid");
    }

    @Test
    void tileRunsCoverTheSetAndNothingElse() {
        boolean[][] set = new boolean[5][6];
        String[] lay = {
                "......",
                ".##...",
                "..#...",
                "..####",
                "#....."};
        for (int r = 0; r < lay.length; r++)
            for (int x = 0; x < 6; x++) set[lay.length - 1 - r][x] = lay[r].charAt(x) == '#';
        List<int[][]> runs = LayoutRuns.tileRuns(set);
        Set<String> want = new HashSet<>();
        for (int y = 0; y < 5; y++) for (int x = 0; x < 6; x++) if (set[y][x]) want.add(x + "," + y);
        assertEquals(want, tiles(runs));
        // A row of two or more, a column joining it, a lone tile: four runs, not one per tile
        assertEquals(4, runs.size(), "two rows, the column joining them and the lone tile");
        assertEquals(0, LayoutRuns.tileRuns(new boolean[3][3]).size());
    }

    @Test
    void edgeRunsCoverTheMarkedEdgesAndNothingElse() {
        int w = 4, h = 3;
        boolean[][] eh = new boolean[h + 1][w], ev = new boolean[h][w + 1];
        LayoutRuns.Edge[] marked = {
                new LayoutRuns.Edge(0, 0, Side.BOTTOM), new LayoutRuns.Edge(1, 0, Side.BOTTOM),
                new LayoutRuns.Edge(2, 1, Side.LEFT), new LayoutRuns.Edge(1, 1, Side.RIGHT),   // one edge, named twice
                new LayoutRuns.Edge(2, 2, Side.LEFT), new LayoutRuns.Edge(3, 2, Side.TOP),
                new LayoutRuns.Edge(3, 0, Side.RIGHT)};
        Set<String> want = new HashSet<>();
        for (LayoutRuns.Edge e : marked) {
            LayoutRuns.mark(eh, ev, e.x, e.y, e.side);
            want.add(name(LayoutRuns.edges(edgeOf(e), w, h).get(0)));
        }
        List<int[][]> runs = LayoutRuns.edgeRuns(eh, ev);
        Set<String> got = new HashSet<>();
        for (int[][] run : runs) for (LayoutRuns.Edge e : LayoutRuns.edges(run, w, h)) got.add(name(e));
        assertEquals(want, got);
        assertEquals(4, runs.size(), "the bottom pair, the column x = 2 up two tiles, the top edge, the right edge");
    }

    /** The corner run along one edge. */
    private static int[][] edgeOf(LayoutRuns.Edge e) {
        switch (e.side) {
            case BOTTOM: return new int[][]{{e.x, e.y}, {e.x + 1, e.y}};
            case TOP:    return new int[][]{{e.x, e.y + 1}, {e.x + 1, e.y + 1}};
            case LEFT:   return new int[][]{{e.x, e.y}, {e.x, e.y + 1}};
            default:     return new int[][]{{e.x + 1, e.y}, {e.x + 1, e.y + 1}};
        }
    }
}
