package com.ringsofiron.aicrew;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Arrays;

/**
 * The Box2D world inside one ship or station, in the grid's tile units with no gravity: the
 * deck's walls as static edges, and a body for each crew member walking it ({@link CrewBody}).
 *
 * The walls are the tile edges crew cannot cross: between a walkable tile and one that is not
 * (closed, the hull's outside, or holding an obstacle such as a reactor or stacked cargo, by
 * {@link GridNavGraph#isWalkable}), and wherever a wall or shut door stands between two walkable
 * tiles. So a system or a stack of goods is a solid block crew bump into and slide along. A docked port
 * that can be crossed leaves its edge open. They are rebuilt whenever that set changes, so a door
 * opening or a wall blown away takes effect on the next step.
 */
final class InteriorWorld implements Disposable {

    static final short WALL = 0x1, BODY = 0x2, CORE = 0x4;

    final ShipGridComponent grid;
    private final GridNavGraph nav;
    final World world;
    /** The crew walking this deck, in the order they boarded (forces are summed in this order). */
    final Array<CrewBody> crew = new Array<>();
    private Body walls;
    /** Which tile edges are walls: vertical edges (x from 0 to width, then y), then horizontal ones. */
    private boolean[] barriers = new boolean[0];
    private boolean[] scratch = new boolean[0];
    /** Which tiles crew can walk, row by row, as of the last {@link #syncWalls}. */
    private boolean[] walkable = new boolean[0];

    InteriorWorld(GridNavGraph nav) {
        // Both are no-ops once loaded; the game's backend has usually loaded them already
        GdxNativesLoader.load();
        Box2D.init();
        this.nav = nav;
        this.grid = nav.grid;
        world = new World(new Vector2(0f, 0f), true);
        syncWalls();
    }

    /** Rebuilds the walls if any edge changed since the last build. */
    void syncWalls() {
        int w = grid.width, h = grid.height;
        int n = (w + 1) * h + w * (h + 1);
        if (scratch.length != n) scratch = new boolean[n];
        // Each tile borders four edges: whether it can be walked is worked out once, not per edge
        if (walkable.length != w * h) walkable = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) walkable[y * w + x] = nav.isWalkable(x, y);
        }
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x <= w; x++) scratch[i++] = blocks(x - 1, y, x, y, Side.RIGHT);
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x < w; x++) scratch[i++] = blocks(x, y - 1, x, y, Side.TOP);
        }
        if (walls != null && Arrays.equals(scratch, barriers)) return;
        boolean[] t = barriers;
        barriers = scratch;
        scratch = t;
        build();
    }

    /** True if the edge from tile (ax, ay) across its {@code side} to tile (bx, by) is a wall. */
    private boolean blocks(int ax, int ay, int bx, int by, Side side) {
        Tile a = grid.getTile(ax, ay), b = grid.getTile(bx, by);
        boolean aOpen = a != null && walkable[ay * grid.width + ax], bOpen = b != null && walkable[by * grid.width + bx];
        if (aOpen && bOpen) return grid.isEdgeBlocked(ax, ay, bx, by);
        if (aOpen) return !crossable(ax, ay, side, b);
        if (bOpen) return !crossable(bx, by, side.opposite(), a);
        return false;
    }

    /** True if a docked port out of tile (x, y) across {@code side} is open to walk through. */
    private boolean crossable(int x, int y, Side side, Tile beyond) {
        if (beyond != null || grid.links.size == 0) return false;
        GridLink link = grid.linkAt(x, y, side);
        return link != null && link.canCross();
    }

    private void build() {
        if (walls != null) world.destroyBody(walls);
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        walls = world.createBody(def);
        EdgeShape edge = new EdgeShape();
        FixtureDef fd = new FixtureDef();
        fd.shape = edge;
        fd.friction = 0f;
        fd.filter.categoryBits = WALL;
        fd.filter.maskBits = BODY;
        int w = grid.width, h = grid.height;
        // Runs of wall along each grid line become one edge, so bodies slide along without catching on seams
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x <= w; x++) {
                if (barriers[i + x] && (y == 0 || !barriers[i + x - (w + 1)])) {
                    int end = y;
                    while (end + 1 < h && barriers[i + x + (end + 1 - y) * (w + 1)]) end++;
                    edge.set(x, y, x, end + 1);
                    walls.createFixture(fd);
                }
            }
            i += w + 1;
        }
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x < w; x++) {
                if (barriers[i + x] && (x == 0 || !barriers[i + x - 1])) {
                    int end = x;
                    while (end + 1 < w && barriers[i + end + 1]) end++;
                    edge.set(x, y, end + 1, y);
                    walls.createFixture(fd);
                }
            }
            i += w;
        }
        edge.dispose();
    }

    /** Pushes every two crew closer than their soft radii apart, then steps the world. */
    void step(float dt) {
        syncWalls();
        for (int i = 0; i < crew.size; i++) {
            for (int j = i + 1; j < crew.size; j++) crew.get(i).pushApart(crew.get(j));
        }
        world.step(dt, 6, 2);
    }

    @Override
    public void dispose() {
        world.dispose();
    }
}
