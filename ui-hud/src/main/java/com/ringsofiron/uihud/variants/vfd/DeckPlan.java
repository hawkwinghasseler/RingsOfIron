package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;

/**
 * The player's ship's deck plan on a graphic dot panel, for the HUDs that draw one (Context's H4 face,
 * Task's schematic): its rooms' walls, lit round a room with something wrong in it; burning tiles as a
 * dense checker, smoke as a sparse dither, a breach as an X across its tile, a room without air in
 * red, and the last hit on the hull flashing. With {@link #systems} on it also rings each system
 * that is down, in red while it is damaged and in amber while it comes back by itself (a short, a
 * stun), and lights its room. The walls are rebuilt only when the layout changes.
 */
public final class DeckPlan {
    static final int FIRE = 1, SMOKE = 2, VACUUM = 4, BREACH = 8, LOW = 16, SYSTEM = 32;
    /** The dot pitch the plan is drawn at. */
    static final float P = 2f;

    /** The walls: x0, y0, x1, y1 in tiles from the plan's top left, y down; each face's room and whether it is a door. */
    private final FloatArray faces = new FloatArray();
    private final IntArray faceRoom = new IntArray(), faceDoor = new IntArray();
    private ShipGridComponent grid;
    private int revision = -1;
    int width, height;
    private int minX, maxY, gridWidth;
    /** This frame: each room's trouble, and the tiles burning, smoky and breached (packed y * width + x). */
    private final IntIntMap trouble = new IntIntMap();
    private final IntArray burning = new IntArray(), smoky = new IntArray(), breached = new IntArray();
    private int hitX = -1, hitY = -1;
    /** Whether to mark the systems that are down (Task's schematic); off, the plan is hazards alone. */
    public boolean systems;
    /** This frame: the tile of each system that is down, and whether it comes back by itself. */
    private final IntArray downTiles = new IntArray();
    private final IntArray downByItself = new IntArray();

    /** Reads this frame's ship: its layout when that changed, and each room's trouble. */
    public void update(HudData d) {
        ShipGridComponent g = d.ctx.ship == null ? null : d.ctx.ship.getComponent(ShipGridComponent.class);
        if (g != grid || g != null && g.layoutRevision != revision) build(g);
        trouble.clear();
        burning.clear();
        smoky.clear();
        breached.clear();
        downTiles.clear();
        downByItself.clear();
        hitX = hitY = -1;
        if (g == null) return;
        gridWidth = g.width;
        Array<Compartment> rooms = g.compartments;
        for (int i = 0; i < rooms.size; i++) {
            Compartment c = rooms.get(i);
            int bits = 0;
            float p = c.pressureRatio();
            if (p < ShipAlerts.VACUUM_PRESSURE) bits |= VACUUM;
            else if (p < ShipAlerts.LOW_PRESSURE) bits |= LOW;
            boolean smoke = c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f;
            if (smoke) bits |= SMOKE;
            for (int k = 0; k < c.tiles.size; k++) {
                int packed = c.tiles.get(k);
                Tile t = g.getTile(packed % g.width, packed / g.width);
                if (t == null) continue;
                if (t.isBurning()) {
                    bits |= FIRE;
                    burning.add(packed);
                }
                if (t.isHullBreach()) {
                    bits |= BREACH;
                    breached.add(packed);
                }
                if (smoke && t.isFloor()) smoky.add(packed);
            }
            if (bits != 0) trouble.put(c.id, bits);
        }
        if (systems) systemsDown(d, g);
        if (!Float.isNaN(d.lastHullHit.age) && d.lastHullHit.age < 1.2f) {
            hitX = d.lastHullHit.tileX;
            hitY = d.lastHullHit.tileY;
        }
    }

    /** The systems that are down: damaged, destroyed, shorted or stunned; not those off or unpowered, which the room does not show. */
    private void systemsDown(HudData d, ShipGridComponent g) {
        Array<SystemRow> faults = d.powerFrame.faults;
        for (int i = 0; i < faults.size; i++) {
            SystemRow r = faults.get(i);
            if (r.tileX < 0 || r.tileY < 0 || r.tileX >= g.width || r.tileY >= g.height) continue;
            boolean byItself = r.fault == Fault.SHORTED || r.fault == Fault.STUNNED;
            if (!byItself && r.fault != Fault.DESTROYED && r.fault != Fault.DISABLED) continue;
            downTiles.add(r.tileY * g.width + r.tileX);
            downByItself.add(byItself ? 1 : 0);
            Tile t = g.getTile(r.tileX, r.tileY);
            if (t != null && t.compartment != null) trouble.put(t.compartment.id, trouble.get(t.compartment.id, 0) | SYSTEM);
        }
    }

    private void build(ShipGridComponent g) {
        grid = g;
        faces.clear();
        faceRoom.clear();
        faceDoor.clear();
        width = height = 0;
        if (g == null) return;
        revision = g.layoutRevision;
        int x0 = Integer.MAX_VALUE, x1 = -1, y0 = Integer.MAX_VALUE, y1 = -1;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
            }
        }
        if (x1 < 0) return;
        minX = x0;
        maxY = y1;
        width = x1 - x0 + 1;
        height = y1 - y0 + 1;
        // Each floor tile's sides that face a wall, the outside, or a thin wall: the room's outline
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Tile t = g.getTile(x, y);
                if (t == null || !t.isFloor()) continue;
                int room = t.compartment == null ? -1 : t.compartment.id;
                float u = x - x0, v = y1 - y;
                face(g, x, y, x - 1, y, u, v, u, v + 1f, room);
                face(g, x, y, x + 1, y, u + 1f, v, u + 1f, v + 1f, room);
                face(g, x, y, x, y + 1, u, v, u + 1f, v, room);
                face(g, x, y, x, y - 1, u, v + 1f, u + 1f, v + 1f, room);
            }
        }
    }

    private void face(ShipGridComponent g, int x, int y, int nx, int ny, float x0, float y0, float x1, float y1, int room) {
        Tile n = g.inBounds(nx, ny) ? g.getTile(nx, ny) : null;
        boolean floor = n != null && n.isFloor();
        EdgeWall e = g.getEdgeBetween(x, y, nx, ny);
        boolean door = false;
        if (floor) {
            if (e == null || e.isDestroyed()) return;
            door = e.hasBulkhead;
            // Between two floor tiles a thin wall is shared: drawn once, from the left or lower tile
            if (nx < x || ny < y) return;
        }
        faces.add(x0);
        faces.add(y0);
        faces.add(x1);
        faces.add(y1);
        faceRoom.add(room);
        faceDoor.add(door ? 1 : 0);
    }

    /** Whether there is a plan to draw. */
    public boolean present() {
        return width > 0;
    }

    /** The plan fitted and centred in the box, whole tiles on the dot pitch. */
    public void paint(Filament f, float x, float y, float w, float h) {
        if (width == 0) return;
        float t = Math.max(P, (float) Math.floor(Math.min(w / width, h / height) / P) * P);
        float ox = x + (float) Math.floor((w - width * t) / 2f / P) * P;
        float oy = y + (float) Math.floor((h - height * t) / 2f / P) * P;
        float size = P * 0.78f;
        for (int i = 0; i < burning.size; i++) fill(f, burning.get(i), ox, oy, t, size, true);
        for (int i = 0; i < smoky.size; i++) fill(f, smoky.get(i), ox, oy, t, size, false);
        int n = Math.max(1, Math.round(t / P));
        for (int i = 0; i < faceRoom.size; i++) {
            int bits = trouble.get(faceRoom.get(i), 0);
            boolean airless = (bits & (VACUUM | BREACH)) != 0;
            float lv = bits != 0 ? Drive.NORM : Drive.DIM;
            Phosphor c = airless ? Phosphor.RED : (bits & (FIRE | LOW)) != 0 ? Phosphor.AMBER : Phosphor.ZINC;
            float x0 = ox + faces.get(4 * i) * t, y0 = oy + faces.get(4 * i + 1) * t;
            float x1 = ox + faces.get(4 * i + 2) * t, y1 = oy + faces.get(4 * i + 3) * t;
            boolean door = faceDoor.get(i) == 1;
            for (int k = 0; k <= n; k++) {
                // A door is its frame: the ends of its wall
                if (door && k > 0 && k < n) continue;
                f.dot(x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, f.o().size(size).level(lv).color(c));
            }
        }
        for (int i = 0; i < breached.size; i++) {
            float bx = tileX(breached.get(i), ox, t), by = tileY(breached.get(i), oy, t);
            for (int k = 0; k <= n; k++) {
                f.dot(bx + t * k / n, by + t * k / n, f.o().size(size).red().full());
                f.dot(bx + t - t * k / n, by + t * k / n, f.o().size(size).red().full());
            }
        }
        for (int i = 0; i < downTiles.size; i++) {
            float bx = tileX(downTiles.get(i), ox, t), by = tileY(downTiles.get(i), oy, t);
            ring(f, bx - P, by - P, t + 2f * P, size, downByItself.get(i) == 1 ? Phosphor.AMBER : Phosphor.RED);
        }
        if (hitX >= 0 && gridWidth > 0) {
            int packed = hitY * gridWidth + hitX;
            float bx = tileX(packed, ox, t), by = tileY(packed, oy, t);
            for (float yy = by + P / 2f; yy < by + t; yy += P) {
                for (float xx = bx + P / 2f; xx < bx + t; xx += P) f.dot(xx, yy, f.o().size(size).full());
            }
        }
    }

    /** A square of dots {@code s} on a side from {@code x, y}, one every pitch: a ring round a tile. */
    private static void ring(Filament f, float x, float y, float s, float size, Phosphor c) {
        int n = Math.max(1, Math.round(s / P));
        for (int k = 0; k <= n; k++) {
            float a = s * k / n;
            f.dot(x + a, y, f.o().size(size).color(c).full());
            f.dot(x + a, y + s, f.o().size(size).color(c).full());
            if (k == 0 || k == n) continue;
            f.dot(x, y + a, f.o().size(size).color(c).full());
            f.dot(x + s, y + a, f.o().size(size).color(c).full());
        }
    }

    private float tileX(int packed, float ox, float t) {
        return ox + (packed % gridWidth - minX) * t;
    }

    private float tileY(int packed, float oy, float t) {
        return oy + (maxY - packed / gridWidth) * t;
    }

    private void fill(Filament f, int packed, float ox, float oy, float t, float size, boolean fire) {
        float bx = tileX(packed, ox, t), by = tileY(packed, oy, t);
        int i = 0;
        for (float yy = by + P / 2f; yy < by + t; yy += P) {
            int j = 0;
            for (float xx = bx + P / 2f; xx < bx + t; xx += P) {
                boolean on = fire ? (i + j) % 2 == 0 : i % 2 == 0 && (j + i / 2) % 2 == 0;
                if (on) f.dot(xx, yy, fire ? f.o().size(size).amber().full() : f.o().size(size * 0.9f).dim());
                j++;
            }
            i++;
        }
    }
}
