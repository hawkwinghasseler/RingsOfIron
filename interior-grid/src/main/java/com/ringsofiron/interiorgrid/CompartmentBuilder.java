package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;

import java.util.Arrays;

/**
 * Divides a grid's air-holding tiles into {@link Compartment}s and carries the existing
 * atmosphere over when the layout changes.
 *
 * <h2>What counts as one room</h2>
 * An opening only one tile wide (a door, a 1-wide corridor, a single missing hull tile)
 * is treated as closed for the purpose of deciding what a room is. Concretely:
 * <ul>
 *   <li>A tile is <b>wide</b> if it lies in some 2×2 block of tiles that all hold air
 *       and are all connected to each other (no wall, closed door or seal inside the
 *       block). Every other air-holding tile is <b>narrow</b>.</li>
 *   <li>Two adjacent wide tiles are in the same room only if the edge between them is
 *       inside such a 2×2 block, so an opening two tiles across joins two areas but a
 *       single open door in a wall does not.</li>
 *   <li>Adjacent narrow tiles join each other, so a 1-wide corridor is its own
 *       compartment. A narrow tile never joins a wide one.</li>
 * </ul>
 * Neighbouring compartments still exchange air through the openings between them;
 * {@link GridHazardSystem} handles that flow.
 *
 * <h2>Conserving air across a rebuild</h2>
 * Each old compartment's gas and smoke are spread evenly over its old tiles, and every
 * new compartment takes the share of each tile it now contains. A tile that has left
 * every compartment (it was sealed off) hands its share to the new compartments that
 * inherited the rest of its old room. A tile that newly holds air (a wall was blown
 * out, a seal was lifted) brings no air of its own, so the room it joins thins out.
 * The first build fills every compartment with standard air.
 */
public final class CompartmentBuilder {

    private CompartmentBuilder() {}

    /** Rebuilds the compartments if walls, doors or seals changed since the last build. */
    public static boolean rebuildIfChanged(ShipGridComponent grid) {
        boolean[] layout = layout(grid);
        if (grid.compartmentLayout != null && Arrays.equals(layout, grid.compartmentLayout)) return false;
        rebuild(grid);
        return true;
    }

    /** Rebuilds the compartments now. */
    public static void rebuild(ShipGridComponent grid) {
        int w = grid.width, h = grid.height;
        boolean firstBuild = grid.compartmentLayout == null;
        grid.compartmentLayout = layout(grid);
        grid.layoutRevision++;

        // Remember where the old air was
        Compartment[] oldOwner = new Compartment[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                oldOwner[y * w + x] = grid.tiles[y][x].compartment;
            }
        }
        Array<Compartment> old = new Array<>(grid.compartments);

        // Which 2x2 blocks (by bottom-left corner) are fully connected air
        boolean[][] block = new boolean[h][w];
        for (int y = 0; y + 1 < h; y++) {
            for (int x = 0; x + 1 < w; x++) {
                block[y][x] = grid.canFlow(x, y, x + 1, y) && grid.canFlow(x, y + 1, x + 1, y + 1)
                        && grid.canFlow(x, y, x, y + 1) && grid.canFlow(x + 1, y, x + 1, y + 1);
            }
        }
        boolean[][] wide = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                wide[y][x] = blockAt(block, x, y) || blockAt(block, x - 1, y)
                        || blockAt(block, x, y - 1) || blockAt(block, x - 1, y - 1);
            }
        }

        // Union-find over tiles
        int[] parent = new int[w * h];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.tiles[y][x].holdsAir()) continue;
                // Right neighbour: the edge is inside the block below or above it
                if (x + 1 < w && grid.canFlow(x, y, x + 1, y)) {
                    boolean join = wide[y][x] && wide[y][x + 1]
                            ? blockAt(block, x, y) || blockAt(block, x, y - 1)
                            : !wide[y][x] && !wide[y][x + 1];
                    if (join) union(parent, y * w + x, y * w + x + 1);
                }
                // Top neighbour: the edge is inside the block left or right of it
                if (y + 1 < h && grid.canFlow(x, y, x, y + 1)) {
                    boolean join = wide[y][x] && wide[y + 1][x]
                            ? blockAt(block, x, y) || blockAt(block, x - 1, y)
                            : !wide[y][x] && !wide[y + 1][x];
                    if (join) union(parent, y * w + x, (y + 1) * w + x);
                }
            }
        }

        // Create the new compartments
        grid.compartments.clear();
        Compartment[] byRoot = new Compartment[w * h];
        float tileVolume = grid.tileSize * grid.tileSize * Compartment.DECK_HEIGHT;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = grid.tiles[y][x];
                if (!t.holdsAir()) {
                    t.compartment = null;
                    continue;
                }
                int root = find(parent, y * w + x);
                Compartment c = byRoot[root];
                if (c == null) {
                    c = new Compartment(grid.compartments.size);
                    byRoot[root] = c;
                    grid.compartments.add(c);
                }
                c.tiles.add(y * w + x);
                c.volume += tileVolume;
                t.compartment = c;
            }
        }
        for (Compartment c : grid.compartments) c.temperature = meanTemperature(grid, c);

        if (firstBuild) {
            for (Compartment c : grid.compartments) c.fillStandardAir();
            return;
        }
        redistribute(grid, old, oldOwner);
    }

    /** Moves the old compartments' contents into the new ones, conserving every gas. */
    private static void redistribute(ShipGridComponent grid, Array<Compartment> old, Compartment[] oldOwner) {
        int n = old.size;
        // Per old compartment: how many of its tiles went to each new one, and how many were orphaned
        int[][] inherited = new int[n][];
        int[] orphaned = new int[n];
        for (int i = 0; i < n; i++) inherited[i] = new int[grid.compartments.size];
        for (int i = 0; i < oldOwner.length; i++) {
            Compartment from = oldOwner[i];
            if (from == null) continue;
            Compartment to = grid.tiles[i / grid.width][i % grid.width].compartment;
            if (to == null) orphaned[from.id]++;
            else inherited[from.id][to.id]++;
        }
        for (int i = 0; i < n; i++) {
            Compartment from = old.get(i);
            int kept = from.tiles.size - orphaned[i];
            if (kept <= 0) continue; // the whole room was sealed off; its air has nowhere to go
            for (Compartment to : grid.compartments) {
                int share = inherited[i][to.id];
                if (share == 0) continue;
                double f = (double) share / kept;
                to.oxygen += from.oxygen * f;
                to.nitrogen += from.nitrogen * f;
                to.carbonDioxide += from.carbonDioxide * f;
                to.smoke += from.smoke * f;
            }
        }
    }

    static float meanTemperature(ShipGridComponent grid, Compartment c) {
        if (c.tiles.size == 0) return Compartment.AMBIENT_TEMPERATURE;
        double sum = 0;
        for (int i = 0; i < c.tiles.size; i++) {
            int idx = c.tiles.get(i);
            sum += grid.tiles[idx / grid.width][idx % grid.width].temperature;
        }
        return (float) (sum / c.tiles.size);
    }

    /**
     * Everything that decides the compartments: which tiles hold air and which edges are
     * blocked. Damage that does not open or close anything leaves it unchanged.
     */
    static boolean[] layout(ShipGridComponent grid) {
        int w = grid.width, h = grid.height;
        boolean[] out = new boolean[w * h + (h + 1) * w + h * (w + 1)];
        int i = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) out[i++] = grid.tiles[y][x].holdsAir();
        for (int y = 0; y <= h; y++)
            for (int x = 0; x < w; x++) out[i++] = grid.edgeH[y][x] != null && grid.edgeH[y][x].blocks();
        for (int y = 0; y < h; y++)
            for (int x = 0; x <= w; x++) out[i++] = grid.edgeV[y][x] != null && grid.edgeV[y][x].blocks();
        return out;
    }

    private static boolean blockAt(boolean[][] block, int x, int y) {
        return y >= 0 && y < block.length && x >= 0 && x < block[0].length && block[y][x];
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
