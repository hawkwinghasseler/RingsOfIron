package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * The walkway through a latched pair of ports: once the weld holds, each opening tile of one
 * port is linked to the opening tile facing it on the other ({@link GridLink}), so people walk
 * across and air flows through while both port doors are open. Each door opens only if its own
 * side agreed to the dock ({@link DockingPort#consented}): a station that gave clearance opens
 * its door, one that refused keeps it shut. Undocking or tearing loose takes the links away and
 * shuts both doors.
 */
final class DockLinks {
    /** Mated door edges sit two half-tile collars apart; anything much further is not a pair. */
    private static final float PAIR_DISTANCE = 1.6f;

    private DockLinks() {
    }

    /** Links the two ports' openings (again, if they were) and opens each door its side agreed to. */
    static void join(DockingSystem.Link link, Entity a, Body ba, ShipGridComponent ga,
                     Entity b, Body bb, ShipGridComponent gb) {
        unjoin(link, ga, gb, false);
        MountFootprint ma = mount(ga, link.pa), mb = mount(gb, link.pb);
        if (ma == null || mb == null) return;
        Array<int[]> doorsA = doorTiles(ma), doorsB = doorTiles(mb);
        Side sa = DockingPortLayout.facing(ma), sb = DockingPortLayout.facing(mb);
        Vector2 pa = new Vector2(), pb = new Vector2();
        for (int[] ta : doorsA) {
            edgeWorld(ba, ga, ta[0], ta[1], sa, pa);
            int[] best = null;
            // A collar locked out holds the edges that much further apart
            float bestDistance = PAIR_DISTANCE * ga.tileSize + link.pa.lockedExtension + link.pb.lockedExtension;
            for (int[] tb : doorsB) {
                float d = edgeWorld(bb, gb, tb[0], tb[1], sb, pb).dst(pa);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = tb;
                }
            }
            if (best == null) continue;
            GridLink joined = GridLink.join(a, ga, ta[0], ta[1], sa, b, gb, best[0], best[1], sb);
            // The walkway between the two edges: the collars, face to face, and any collar tunnel out
            joined.setGap(bestDistance / ga.tileSize);
            link.gridLinks.add(joined);
        }
        setDoors(ga, ma, link.pa.consented || link.pa.forced);
        setDoors(gb, mb, link.pb.consented || link.pb.forced);
    }

    /** Takes the links away; with {@code shut}, both port doors close too. Either grid may be null. */
    static void unjoin(DockingSystem.Link link, ShipGridComponent ga, ShipGridComponent gb, boolean shut) {
        for (GridLink l : link.gridLinks) l.unjoin();
        link.gridLinks.clear();
        if (!shut) return;
        MountFootprint ma = ga == null ? null : mount(ga, link.pa), mb = gb == null ? null : mount(gb, link.pb);
        if (ma != null) setDoors(ga, ma, false);
        if (mb != null) setDoors(gb, mb, false);
    }

    /** Opens or shuts the port's door on every opening tile. A door blown away stays gone. */
    static void setDoors(ShipGridComponent grid, MountFootprint port, boolean open) {
        for (int[] t : doorTiles(port)) {
            EdgeWall door = DockingPortLayout.door(grid, port, t[0], t[1]);
            if (door == null || door.isDestroyed()) continue;
            door.bulkheadSealed = !open;
            door.opensForCrew = false;
        }
    }

    static MountFootprint mount(ShipGridComponent grid, DockingPort port) {
        if (port.gridX < 0 || !grid.inBounds(port.gridX, port.gridY)) return null;
        MountFootprint m = grid.getTile(port.gridX, port.gridY).mount;
        return m != null && m.kind == MountFootprint.MountKind.PORT ? m : null;
    }

    static Array<int[]> doorTiles(MountFootprint port) {
        Array<int[]> out = new Array<>();
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                if (DockingPortLayout.isDoorTile(port, x, y)) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /** World position (meters) of the middle of tile (x, y)'s {@code side} edge. */
    private static Vector2 edgeWorld(Body body, ShipGridComponent grid, int x, int y, Side side, Vector2 out) {
        float lx = grid.gridOriginLocal.x + (x + 0.5f + side.dx * 0.5f) * grid.tileSize;
        float ly = grid.gridOriginLocal.y + (y + 0.5f + side.dy * 0.5f) * grid.tileSize;
        Vector2 w = body.getWorldPoint(out.set(lx * PhysicsManager.UNITS_PER_METER, ly * PhysicsManager.UNITS_PER_METER));
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }
}
