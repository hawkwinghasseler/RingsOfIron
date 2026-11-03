package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

import java.util.Locale;

/**
 * Z5, the schematic (SCH): the deck plan on a green tube, drawn from the ship's own grid, nose to the
 * right. Hull and walls are phosphor lines; what is wrong is drawn in its room: fire hatched red, thin
 * air amber, vacuum red with a dashed edge, smoke a pale hatch, breaches a red tear, shorts and stuns a
 * spark, wrecked systems a cross, a hull hit a red burst where it lands. The captain is the amber dot
 * in a ring. No text on the tube: the pointer over a room names it and what is wrong there.
 */
final class Schematic extends YardPart {
    static final float W = 300f, H = 152f;
    private static final float TX = 14f, TY = 624f, TW = 272f, TH = 124f;
    /** How long a hull hit shows. */
    static final float HIT_SECONDS = 1.5f;

    private final TaskFrame f;
    Yard.Finish finish = Yard.Finish.YARD;
    private final Vector2 avatar = new Vector2();
    private final Faults faults = new Faults();
    /** The plan's placement this frame, in the mockup's coordinates, for the hover. */
    private float ox, oy, px;
    private int gw, gh;

    Schematic(TaskFrame f) {
        super(0f, 610f);
        this.f = f;
    }

    static ShipGridComponent grid(HudData d) {
        Entity ship = d.ctx.ship;
        return ship == null ? null : ship.getComponent(ShipGridComponent.class);
    }

    @Override
    protected void draw(YardPaint p) {
        p.unit(0f, 610f, W, H, finish, "lb");
        p.tube(TX, TY, TW, TH, 5f, 5f, false);
        ShipGridComponent g = grid(f.data);
        if (g != null && g.width > 0 && g.height > 0) plan(p, g);
        else gw = gh = 0;
        p.tubeOver(TX, TY, TW, TH);
    }

    private float tx(float x) {
        return ox + x * px;
    }

    /** The top of tile row {@code y} (grid y up). */
    private float ty(float y) {
        return oy + (gh - 1 - y) * px;
    }

    private void plan(YardPaint p, ShipGridComponent g) {
        HudData d = f.data;
        gw = g.width;
        gh = g.height;
        px = Math.min(Math.min((TW - 16f) / gw, (TH - 14f) / gh), 16f);
        ox = TX + (TW - gw * px) / 2f;
        oy = TY + (TH - gh * px) / 2f;
        float lw = Math.max(1.2f, px * 0.16f);

        // Air, under everything: whole rooms
        for (int i = 0; i < g.compartments.size; i++) {
            Compartment c = g.compartments.get(i);
            float pr = c.pressureRatio();
            boolean vac = pr < ShipAlerts.VACUUM_PRESSURE, thin = !vac && pr < ShipAlerts.LOW_PRESSURE;
            boolean smoke = c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f;
            if (!vac && !thin && !smoke) continue;
            // An airlock pumped down for a spacewalk is empty on purpose
            if ((vac || thin) && pumpedDown(d, g, c)) {
                vac = thin = false;
                if (!smoke) continue;
            }
            for (int k = 0; k < c.tiles.size; k++) {
                int idx = c.tiles.get(k), x = idx % gw, y = idx / gw;
                float rx = tx(x), ry = ty(y);
                if (vac) p.rect(rx, ry, px, px, Yard.P_RED, 0.16f);
                else if (thin) p.rect(rx, ry, px, px, Yard.P_AMBER, 0.14f);
                if (smoke) p.hatch(rx, ry, px, px, Yard.P_WHITE, 0.2f);
                if (vac) {
                    if (other(g, c, x - 1, y)) p.dashed(rx, ry, rx, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, x + 1, y)) p.dashed(rx + px, ry, rx + px, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, x, y + 1)) p.dashed(rx, ry, rx + px, ry, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, x, y - 1)) p.dashed(rx, ry + px, rx + px, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                }
            }
        }

        // Fire, hatched in its tiles and breathing
        float pulse = 0.65f + 0.25f * MathUtils.sin(f.time * 6f);
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                Tile t = g.getTile(x, y);
                if (t != null && t.isBurning()) p.hatch(tx(x), ty(y), px, px, Yard.P_RED, pulse);
            }
        }

        // Systems: an inset outline in each mount, coloured by its fault
        faults.rows = d.powerFrame.faults;
        for (int i = 0; i < g.mounts.size; i++) {
            MountFootprint m = g.mounts.get(i);
            if (m.installed == null) continue;
            SystemRow row = faults.on(m);
            Color c = row == null ? Yard.P_GDIM : row.fault == Fault.NO_POWER || row.fault == Fault.OFF ? Yard.P_DIM
                    : row.fault == Fault.STUNNED ? Yard.P_AMBER : Yard.P_RED;
            float in = Math.max(1f, px * 0.22f);
            float mx0 = tx(m.originX) + in, my0 = ty(m.originY + m.height() - 1) + in;
            float mw = m.width() * px - 2 * in, mh = m.height() * px - 2 * in;
            if (mw <= 2f || mh <= 2f) continue;
            p.frame(mx0, my0, mw, mh, 1f, c, 0.85f);
            if (row == null) continue;
            float cx = mx0 + mw / 2f, cy = my0 + mh / 2f, s = Math.min(Math.min(mw, mh) * 0.4f, 6f);
            if (row.fault == Fault.DESTROYED || row.fault == Fault.DISABLED) {
                p.line(cx - s, cy - s, cx + s, cy + s, 1.4f, Yard.P_RED, 0.95f);
                p.line(cx - s, cy + s, cx + s, cy - s, 1.4f, Yard.P_RED, 0.95f);
            } else if (row.fault == Fault.SHORTED || row.fault == Fault.STUNNED) {
                spark(p, cx, cy, s * 1.3f, row.fault == Fault.SHORTED ? Yard.P_RED : Yard.P_AMBER);
            }
        }

        // Walls: the hull bright, the inner walls dim, joined centre to centre like a line drawing
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (!wall(g, x, y)) continue;
                boolean hull = hull(g, x, y);
                float cx = tx(x) + px / 2f, cy = ty(y) + px / 2f;
                int joins = 0;
                joins += join(p, g, x, y, x + 1, y, hull, cx, cy, lw);
                joins += join(p, g, x, y, x, y + 1, hull, cx, cy, lw);
                if (!wall(g, x + 1, y) && !wall(g, x, y + 1)) joins += join(p, g, x, y, x + 1, y + 1, hull, cx, cy, lw);
                if (!wall(g, x - 1, y) && !wall(g, x, y + 1)) joins += join(p, g, x, y, x - 1, y + 1, hull, cx, cy, lw);
                if (joins == 0 && !wall(g, x - 1, y) && !wall(g, x, y - 1) && !wall(g, x - 1, y - 1) && !wall(g, x + 1, y - 1)) {
                    p.rect(cx - lw, cy - lw, 2 * lw, 2 * lw, hull ? Yard.P_GREEN : Yard.P_GDIM, 0.9f);
                }
            }
        }
        // Breaches are hull tiles no longer standing: a tear where the line breaks
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                Tile t = g.getTile(x, y);
                if (t != null && t.isHullBreach()) tear(p, tx(x) + px / 2f, ty(y) + px / 2f, px);
            }
        }
        // Thin walls on the tile edges
        float ew = Math.max(1f, px * 0.12f);
        for (int y = 0; y <= gh && y < g.edgeH.length; y++) {
            for (int x = 0; x < gw && x < g.edgeH[y].length; x++) {
                EdgeWall w = g.edgeH[y][x];
                if (w == null || w.isDestroyed()) continue;
                float yy = oy + (gh - y) * px;
                p.line(tx(x), yy, tx(x) + px, yy, ew, Yard.P_GDIM, w.opensForCrew ? 0.45f : 0.9f);
            }
        }
        for (int y = 0; y < gh && y < g.edgeV.length; y++) {
            for (int x = 0; x <= gw && x < g.edgeV[y].length; x++) {
                EdgeWall w = g.edgeV[y][x];
                if (w == null || w.isDestroyed()) continue;
                p.line(tx(x), ty(y), tx(x), ty(y) + px, ew, Yard.P_GDIM, w.opensForCrew ? 0.45f : 0.9f);
            }
        }

        // A hit on the hull flashes where it landed, a red burst fading over a second and a half
        HudData.HullHit hit = d.lastHullHit;
        if (!Float.isNaN(hit.age) && hit.age < HIT_SECONDS && g.inBounds(hit.tileX, hit.tileY)) {
            float a = 1f - hit.age / HIT_SECONDS, cx = tx(hit.tileX) + px / 2f, cy = ty(hit.tileY) + px / 2f;
            float k = Math.max(0.7f, px / 13f);
            p.glow(cx, cy, 12f * k, Yard.P_RED, 0.5f * a);
            for (int i = 0; i < 8; i++) {
                float an = i * 45f, c = MathUtils.cosDeg(an), sn = MathUtils.sinDeg(an);
                p.line(cx + 4f * k * c, cy + 4f * k * sn, cx + 9f * k * c, cy + 9f * k * sn, 1.5f, Yard.P_RED, a);
            }
        }

        // The captain, when aboard
        if (d.interactor != null && d.interactor.getAvatarShip() == d.ctx.ship && d.interactor.getAvatarTile(avatar)) {
            float cx = ox + avatar.x * px, cy = oy + (gh - avatar.y) * px;
            float r = Math.max(2.2f, Math.min(3.6f, px * 0.24f));
            p.glow(cx, cy, r * 3f, Yard.P_AMBER, 0.35f);
            p.circle(cx, cy, r, Yard.P_AMBER);
            ring(p, cx, cy, r * 2.3f);
        }
    }

    /** Draws the line from wall tile ({@code x}, {@code y}) to its neighbour when that is a wall too. */
    private int join(YardPaint p, ShipGridComponent g, int x, int y, int nx, int ny, boolean hull, float cx, float cy, float lw) {
        if (!wall(g, nx, ny)) return 0;
        boolean both = hull && hull(g, nx, ny);
        Color c = both ? Yard.P_GREEN : Yard.P_GDIM;
        float ex = tx(nx) + px / 2f, ey = ty(ny) + px / 2f;
        p.line(cx, cy, ex, ey, lw * 3f, c, 0.12f);
        p.line(cx, cy, ex, ey, lw, c, 0.95f);
        return 1;
    }

    private static boolean wall(ShipGridComponent g, int x, int y) {
        if (!g.inBounds(x, y)) return false;
        Tile t = g.getTile(x, y);
        return t != null && t.isIntactWall();
    }

    private static boolean hull(ShipGridComponent g, int x, int y) {
        Tile t = g.getTile(x, y);
        return t != null && t.wallType == Tile.WallType.HULL;
    }

    /** Whether ({@code x}, {@code y}) is outside room {@code c}: the edge of its vacuum is drawn there. */
    private static boolean other(ShipGridComponent g, Compartment c, int x, int y) {
        return !g.inBounds(x, y) || g.compartmentAt(x, y) != c;
    }

    private static boolean pumpedDown(HudData d, ShipGridComponent g, Compartment c) {
        if (c.tiles.size == 0) return false;
        int idx = c.tiles.get(0);
        String name = d.roomAt(idx % g.width, idx / g.width);
        return name.startsWith("AIRLOCK");
    }

    /** A breach: a red tear across the tile. */
    private static void tear(YardPaint p, float cx, float cy, float px) {
        float k = Math.max(0.6f, px / 13f);
        p.polyline(new float[]{cx - 5 * k, cy, cx - 2 * k, cy - 3 * k, cx, cy + 1 * k, cx + 3 * k, cy - 3 * k, cx + 5 * k, cy}, false, 1.8f, Yard.P_RED, 1f);
        p.glow(cx, cy, 7f * k, Yard.P_RED, 0.3f);
    }

    /** A short or a stun: a spark. */
    private static void spark(YardPaint p, float cx, float cy, float s, Color c) {
        p.polyline(new float[]{cx - s * 0.3f, cy - s, cx + s * 0.25f, cy - s * 0.1f, cx - s * 0.25f, cy + s * 0.1f, cx + s * 0.3f, cy + s}, false, 1.6f, c, 1f);
    }

    private static void ring(YardPaint p, float cx, float cy, float r) {
        int n = 20;
        float[] pts = new float[2 * n];
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            pts[2 * i] = cx + r * MathUtils.cos(a);
            pts[2 * i + 1] = cy + r * MathUtils.sin(a);
        }
        p.polyline(pts, true, 1.1f, Yard.P_AMBER, 0.9f);
    }

    /** The faults this frame, found by the mount they sit in. */
    private static final class Faults {
        com.badlogic.gdx.utils.Array<SystemRow> rows;

        SystemRow on(MountFootprint m) {
            SystemRow worst = null;
            for (int i = 0; i < rows.size; i++) {
                SystemRow r = rows.get(i);
                if (r.tileX < 0 || !m.covers(r.tileX, r.tileY)) continue;
                if (worst == null || r.fault.ordinal() < worst.fault.ordinal()) worst = r;
            }
            return worst;
        }
    }

    @Override
    public String hover(float pxs, float pys) {
        if (gw == 0 || px <= 0f) return null;
        float lx = localX(pxs), ly = localY(pys);
        int x = (int) Math.floor((lx - ox) / px), y = gh - 1 - (int) Math.floor((ly - oy) / px);
        ShipGridComponent g = grid(f.data);
        if (g == null || !g.inBounds(x, y)) return null;
        Compartment c = g.compartmentAt(x, y);
        if (c == null) return null;
        String room = f.data.roomAt(x, y);
        if (room.isEmpty()) return null;
        StringBuilder s = new StringBuilder(room.charAt(0) + room.substring(1).toLowerCase(Locale.ROOT));
        int n = 0;
        float pr = c.pressureRatio();
        boolean pumped = pumpedDown(f.data, g, c);
        if (pr < ShipAlerts.VACUUM_PRESSURE && !pumped) n = add(s, n, "vacuum");
        else if (pr < ShipAlerts.LOW_PRESSURE && !pumped) n = add(s, n, String.format(Locale.ROOT, "thin air, %.0f%%", pr * 100f));
        if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) n = add(s, n, "smoke");
        int fire = 0, breach = 0;
        for (int i = 0; i < c.tiles.size; i++) {
            int idx = c.tiles.get(i);
            Tile t = g.getTile(idx % gw, idx / gw);
            if (t != null && t.isBurning()) fire++;
        }
        for (int yy = 0; yy < gh; yy++) {
            for (int xx = 0; xx < gw; xx++) {
                Tile t = g.getTile(xx, yy);
                if (t != null && t.isHullBreach() && near(g, c, xx, yy)) breach++;
            }
        }
        if (fire > 0) n = add(s, n, "fire");
        if (breach > 0) n = add(s, n, breach == 1 ? "a breach" : breach + " breaches");
        for (int i = 0; i < f.data.powerFrame.faults.size; i++) {
            SystemRow r = f.data.powerFrame.faults.get(i);
            if (!room.equals(r.room) || r.fault == Fault.OFF) continue;
            n = add(s, n, r.system.toLowerCase(Locale.ROOT) + " " + r.fault.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return s.toString();
    }

    private static int add(StringBuilder s, int n, String what) {
        s.append(n == 0 ? ": " : ", ").append(what);
        return n + 1;
    }

    /** Whether the breach at ({@code x}, {@code y}) opens into room {@code c}. */
    private static boolean near(ShipGridComponent g, Compartment c, int x, int y) {
        return (g.inBounds(x - 1, y) && g.compartmentAt(x - 1, y) == c) || (g.inBounds(x + 1, y) && g.compartmentAt(x + 1, y) == c)
                || (g.inBounds(x, y - 1) && g.compartmentAt(x, y - 1) == c) || (g.inBounds(x, y + 1) && g.compartmentAt(x, y + 1) == c);
    }
}
