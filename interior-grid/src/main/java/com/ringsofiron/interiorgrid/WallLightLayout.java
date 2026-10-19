package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where wall lights could hang on a deck, worked out from its layout: what the ship editor's
 * auto-place puts in a chassis, and what the game uses for a layout that does not say. Each room (floor tiles joined without a wall or door between them) gets lights on its walls
 * until every floor tile is within {@link #COVER} tiles in front of one, picking each time the
 * wall that lights the most tiles still dark, and never two within {@link #MIN_SPACING} tiles.
 *
 * <p>It picks the wall side of a floor tile that no system stands on, so the light is not hidden
 * behind machinery; placed by hand, a light can go on any tile (see {@link WallLight}).
 */
public final class WallLightLayout {
    /** Tiles a wall light is counted as lighting: within this distance, in front of its wall. */
    public static final float COVER = 3.5f;
    /** No two wall lights hang closer than this, tiles. */
    public static final float MIN_SPACING = 3f;

    /** Worked-out lights of grids whose layout does not say, by the layout revision they fit. */
    private static final Map<ShipGridComponent, Worked> WORKED = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Worked {
        final int revision;
        final Array<WallLight> lights;

        Worked(int revision, Array<WallLight> lights) {
            this.revision = revision;
            this.lights = lights;
        }
    }

    private WallLightLayout() {
    }

    /**
     * The wall lights hung on {@code grid}: where its layout put them, or, for a layout that does
     * not say, worked out with {@link #of} (again whenever the layout changes).
     */
    public static Array<WallLight> lightsOf(ShipGridComponent grid) {
        if (grid.wallLights != null) return grid.wallLights;
        Worked w = WORKED.get(grid);
        if (w == null || w.revision != grid.layoutRevision) WORKED.put(grid, w = new Worked(grid.layoutRevision, of(grid)));
        return w.lights;
    }

    /** The wall lights for {@code grid}'s current layout. */
    public static Array<WallLight> of(ShipGridComponent grid) {
        int w = grid.width, h = grid.height;
        int[] room = rooms(grid);
        Array<WallLight> out = new Array<>();
        // Candidate spots: every wall side of a free floor tile
        Array<WallLight> walls = new Array<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (room[y * w + x] < 0 || grid.getTile(x, y).mount != null) continue;
                for (Side s : Side.values()) if (isWall(grid, x, y, s)) walls.add(new WallLight(x, y, s));
            }
        }
        boolean[] lit = new boolean[w * h];
        for (int i = 0; i < lit.length; i++) lit[i] = room[i] < 0;
        boolean[] used = new boolean[walls.size];
        IntArray covered = new IntArray();
        while (true) {
            int best = -1, bestCount = 0;
            for (int i = 0; i < walls.size; i++) {
                if (used[i] || tooClose(walls.get(i), out)) continue;
                int n = cover(grid, room, walls.get(i), lit, null);
                if (n > bestCount) {
                    best = i;
                    bestCount = n;
                }
            }
            if (best < 0) break;
            used[best] = true;
            WallLight p = walls.get(best);
            covered.clear();
            cover(grid, room, p, lit, covered);
            for (int i = 0; i < covered.size; i++) lit[covered.get(i)] = true;
            out.add(p);
        }
        return out;
    }

    /**
     * How many tiles still dark (in {@code lit}) the light at {@code p} covers: tiles of its room
     * within {@link #COVER} of its edge and in front of its wall. Adds them to {@code into} if given.
     */
    private static int cover(ShipGridComponent grid, int[] room, WallLight p, boolean[] lit, IntArray into) {
        int w = grid.width, r = room[p.y * w + p.x];
        float ex = p.edgeX(), ey = p.edgeY();
        int reach = (int) Math.ceil(COVER);
        int n = 0;
        for (int y = Math.max(0, p.y - reach); y <= Math.min(grid.height - 1, p.y + reach); y++) {
            for (int x = Math.max(0, p.x - reach); x <= Math.min(w - 1, p.x + reach); x++) {
                int i = y * w + x;
                if (lit[i] || room[i] != r) continue;
                float dx = x + 0.5f - ex, dy = y + 0.5f - ey;
                // In front: away from the wall, which lies toward the side's direction
                if (dx * p.side.dx + dy * p.side.dy >= 0f) continue;
                if (dx * dx + dy * dy > COVER * COVER) continue;
                n++;
                if (into != null) into.add(i);
            }
        }
        return n;
    }

    private static boolean tooClose(WallLight p, Array<WallLight> placed) {
        for (WallLight q : placed) {
            float dx = p.edgeX() - q.edgeX(), dy = p.edgeY() - q.edgeY();
            if (dx * dx + dy * dy < MIN_SPACING * MIN_SPACING) return true;
        }
        return false;
    }

    /** True if the {@code side} edge of floor tile (x, y) is a wall a light can hang on: no door, no way through. */
    private static boolean isWall(ShipGridComponent grid, int x, int y, Side side) {
        EdgeWall edge = grid.getEdge(x, y, side);
        if (edge != null) return !edge.hasBulkhead;
        Tile beyond = grid.getTile(x + side.dx, y + side.dy);
        return beyond != null && beyond.isIntactWall();
    }

    /** Floor tiles numbered by room, -1 for everything else; edge walls and doors part rooms. */
    public static int[] rooms(ShipGridComponent grid) {
        int w = grid.width, h = grid.height;
        int[] room = new int[w * h];
        Arrays.fill(room, -2);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || !t.isFloor() || t.isIntactWall()) room[y * w + x] = -1;
            }
        }
        IntArray stack = new IntArray();
        int next = 0;
        for (int start = 0; start < room.length; start++) {
            if (room[start] != -2) continue;
            room[start] = next;
            stack.add(start);
            while (stack.size > 0) {
                int i = stack.pop(), x = i % w, y = i / w;
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (room[j] != -2 || grid.getEdge(x, y, s) != null) continue;
                    room[j] = next;
                    stack.add(j);
                }
            }
            next++;
        }
        return room;
    }
}
