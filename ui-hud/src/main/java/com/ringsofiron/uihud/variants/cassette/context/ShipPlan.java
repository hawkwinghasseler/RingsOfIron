package com.ringsofiron.uihud.variants.cassette.context;

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

/**
 * The ship's plan in the condition unit (H4), drawn from the ship's own grid on the green tube, nose to
 * the right: hull and walls as phosphor lines, and what is wrong in its room: fire hatched red, thin air
 * amber, vacuum red with a dashed edge, smoke a pale hatch, breaches a red tear, a wrecked system a cross,
 * a short or a stun a spark, the last hull hit a red burst where it landed, and the captain an amber dot.
 * The Stations HUD's DAMAGE page draws the same plan; this is its copy, until the kit has one for both.
 */
final class ShipPlan {
    /** How long a hull hit shows. */
    static final float HIT_SECONDS = 1.5f;

    private final Vector2 avatar = new Vector2();
    private float ox, oy, px;
    private int gw, gh;

    static ShipGridComponent grid(HudData d) {
        Entity ship = d.ctx.ship;
        return ship == null ? null : ship.getComponent(ShipGridComponent.class);
    }

    /** Draws the plan to fit ({@code x}, {@code y}, {@code w}, {@code h}); {@code time} pulses the fires. */
    void draw(YardPaint p, HudData d, float x, float y, float w, float h, float time) {
        ShipGridComponent g = grid(d);
        if (g == null || g.width <= 0 || g.height <= 0) return;
        gw = g.width;
        gh = g.height;
        px = Math.min(Math.min(w / gw, h / gh), 14f);
        ox = x + (w - gw * px) / 2f;
        oy = y + (h - gh * px) / 2f;
        float lw = Math.max(1.1f, px * 0.15f);

        for (int i = 0; i < g.compartments.size; i++) {
            Compartment c = g.compartments.get(i);
            float pr = c.pressureRatio();
            boolean vac = pr < ShipAlerts.VACUUM_PRESSURE, thin = !vac && pr < ShipAlerts.LOW_PRESSURE;
            boolean smoke = c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f;
            if (!vac && !thin && !smoke) continue;
            if ((vac || thin) && pumpedDown(d, g, c)) {
                vac = thin = false;
                if (!smoke) continue;
            }
            for (int k = 0; k < c.tiles.size; k++) {
                int idx = c.tiles.get(k), tx = idx % gw, ty = idx / gw;
                float rx = tx(tx), ry = ty(ty);
                if (vac) p.rect(rx, ry, px, px, Yard.P_RED, 0.16f);
                else if (thin) p.rect(rx, ry, px, px, Yard.P_AMBER, 0.14f);
                if (smoke) p.hatch(rx, ry, px, px, Yard.P_WHITE, 0.2f);
                if (vac) {
                    if (other(g, c, tx - 1, ty)) p.dashed(rx, ry, rx, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, tx + 1, ty)) p.dashed(rx + px, ry, rx + px, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, tx, ty + 1)) p.dashed(rx, ry, rx + px, ry, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                    if (other(g, c, tx, ty - 1)) p.dashed(rx, ry + px, rx + px, ry + px, 1.2f, 3f, 2f, Yard.P_RED, 0.9f);
                }
            }
        }

        float pulse = 0.65f + 0.25f * MathUtils.sin(time * 6f);
        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                Tile t = g.getTile(tx, ty);
                if (t != null && t.isBurning()) p.hatch(tx(tx), ty(ty), px, px, Yard.P_RED, pulse);
            }
        }

        for (int i = 0; i < g.mounts.size; i++) {
            MountFootprint m = g.mounts.get(i);
            if (m.installed == null) continue;
            SystemRow row = fault(d, m);
            Color c = row == null ? Yard.P_GDIM : row.fault == Fault.NO_POWER || row.fault == Fault.OFF ? Yard.P_DIM
                    : row.fault == Fault.STUNNED ? Yard.P_AMBER : Yard.P_RED;
            float in = Math.max(1f, px * 0.22f);
            float mx0 = tx(m.originX) + in, my0 = ty(m.originY + m.height() - 1) + in;
            float mw = m.width() * px - 2 * in, mh = m.height() * px - 2 * in;
            if (mw <= 2f || mh <= 2f) continue;
            p.frame(mx0, my0, mw, mh, 1f, c, 0.85f);
            if (row == null) continue;
            float cx = mx0 + mw / 2f, cy = my0 + mh / 2f, s = Math.min(Math.min(mw, mh) * 0.4f, 5f);
            if (row.fault == Fault.DESTROYED || row.fault == Fault.DISABLED) {
                p.line(cx - s, cy - s, cx + s, cy + s, 1.4f, Yard.P_RED, 0.95f);
                p.line(cx - s, cy + s, cx + s, cy - s, 1.4f, Yard.P_RED, 0.95f);
            } else if (row.fault == Fault.SHORTED || row.fault == Fault.STUNNED) {
                float k = s * 1.3f;
                p.polyline(new float[]{cx - k * 0.3f, cy - k, cx + k * 0.25f, cy - k * 0.1f, cx - k * 0.25f, cy + k * 0.1f, cx + k * 0.3f, cy + k},
                        false, 1.5f, row.fault == Fault.SHORTED ? Yard.P_RED : Yard.P_AMBER, 1f);
            }
        }

        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                if (!wall(g, tx, ty)) continue;
                boolean hull = hull(g, tx, ty);
                float cx = tx(tx) + px / 2f, cy = ty(ty) + px / 2f;
                int joins = join(p, g, tx, ty, tx + 1, ty, hull, cx, cy, lw) + join(p, g, tx, ty, tx, ty + 1, hull, cx, cy, lw);
                if (!wall(g, tx + 1, ty) && !wall(g, tx, ty + 1)) joins += join(p, g, tx, ty, tx + 1, ty + 1, hull, cx, cy, lw);
                if (!wall(g, tx - 1, ty) && !wall(g, tx, ty + 1)) joins += join(p, g, tx, ty, tx - 1, ty + 1, hull, cx, cy, lw);
                if (joins == 0 && !wall(g, tx - 1, ty) && !wall(g, tx, ty - 1) && !wall(g, tx - 1, ty - 1) && !wall(g, tx + 1, ty - 1)) {
                    p.rect(cx - lw, cy - lw, 2 * lw, 2 * lw, hull ? Yard.P_GREEN : Yard.P_GDIM, 0.9f);
                }
            }
        }
        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                Tile t = g.getTile(tx, ty);
                if (t != null && t.isHullBreach()) tear(p, tx(tx) + px / 2f, ty(ty) + px / 2f, px);
            }
        }
        float ew = Math.max(1f, px * 0.12f);
        for (int ty = 0; ty <= gh && ty < g.edgeH.length; ty++) {
            for (int tx = 0; tx < gw && tx < g.edgeH[ty].length; tx++) {
                EdgeWall e = g.edgeH[ty][tx];
                if (e == null || e.isDestroyed()) continue;
                float yy = oy + (gh - ty) * px;
                p.line(tx(tx), yy, tx(tx) + px, yy, ew, Yard.P_GDIM, e.opensForCrew ? 0.45f : 0.9f);
            }
        }
        for (int ty = 0; ty < gh && ty < g.edgeV.length; ty++) {
            for (int tx = 0; tx <= gw && tx < g.edgeV[ty].length; tx++) {
                EdgeWall e = g.edgeV[ty][tx];
                if (e == null || e.isDestroyed()) continue;
                p.line(tx(tx), ty(ty), tx(tx), ty(ty) + px, ew, Yard.P_GDIM, e.opensForCrew ? 0.45f : 0.9f);
            }
        }

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

        if (d.interactor != null && d.interactor.getAvatarShip() == d.ctx.ship && d.interactor.getAvatarTile(avatar)) {
            float cx = ox + avatar.x * px, cy = oy + (gh - avatar.y) * px;
            float r = Math.max(2f, Math.min(3.2f, px * 0.24f));
            p.glow(cx, cy, r * 3f, Yard.P_AMBER, 0.35f);
            p.circle(cx, cy, r, Yard.P_AMBER);
        }
    }

    private float tx(float x) {
        return ox + x * px;
    }

    /** The top of tile row {@code y} (grid y up). */
    private float ty(float y) {
        return oy + (gh - 1 - y) * px;
    }

    private static SystemRow fault(HudData d, MountFootprint m) {
        SystemRow worst = null;
        for (int i = 0; i < d.powerFrame.faults.size; i++) {
            SystemRow r = d.powerFrame.faults.get(i);
            if (r.tileX < 0 || !m.covers(r.tileX, r.tileY)) continue;
            if (worst == null || r.fault.ordinal() < worst.fault.ordinal()) worst = r;
        }
        return worst;
    }

    private int join(YardPaint p, ShipGridComponent g, int x, int y, int nx, int ny, boolean hull, float cx, float cy, float lw) {
        if (!wall(g, nx, ny)) return 0;
        Color c = hull && hull(g, nx, ny) ? Yard.P_GREEN : Yard.P_GDIM;
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

    private static boolean other(ShipGridComponent g, Compartment c, int x, int y) {
        return !g.inBounds(x, y) || g.compartmentAt(x, y) != c;
    }

    /** An airlock pumped down for a spacewalk is empty on purpose. */
    static boolean pumpedDown(HudData d, ShipGridComponent g, Compartment c) {
        if (c.tiles.size == 0) return false;
        int idx = c.tiles.get(0);
        return d.roomAt(idx % g.width, idx / g.width).startsWith("AIRLOCK");
    }

    private static void tear(YardPaint p, float cx, float cy, float px) {
        float k = Math.max(0.6f, px / 13f);
        p.polyline(new float[]{cx - 5 * k, cy, cx - 2 * k, cy - 3 * k, cx, cy + 1 * k, cx + 3 * k, cy - 3 * k, cx + 5 * k, cy}, false, 1.8f, Yard.P_RED, 1f);
        p.glow(cx, cy, 7f * k, Yard.P_RED, 0.3f);
    }
}
