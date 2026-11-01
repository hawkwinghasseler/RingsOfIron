package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import java.util.Arrays;

/**
 * Collisions for {@link PenetrationResolver}: energy spread from where the hulls met over the
 * solid tiles joined to it, each taking its share out of its hit points and pushing the rest on
 * into the tile behind.
 */
final class CrushModel {
    /** How far the kernel reaches, in widths ({@code spread}). */
    private static final float CRUSH_REACH = 3f;

    private final PenetrationResolver sys;
    private float[] crushLoss = new float[0];
    /** Tiles this call reached. */
    private boolean[] crushVisited = new boolean[0];
    private final IntArray crushTiles = new IntArray();
    private final IntArray crushQueue = new IntArray();
    /** Each of {@link #crushTiles}' distance from the contact, then its share of the kernel. */
    private final FloatArray crushWeight = new FloatArray();

    CrushModel(PenetrationResolver sys) {
        this.sys = sys;
    }

    /** As {@link PenetrationResolver#crush}. */
    PenetrationResolver.CrushResult crush(ShipGridComponent grid, Entity ship, Vector2 localPoint, Vector2 localDir, float energy,
                                          float spread, RandomXS128 rng, DamageReport report, PenetrationResolver.CrushResult out) {
        out.reset();
        if (!(energy > 0f)) return out;
        sys.begin(grid, ship, rng, report);
        int n = grid.width * grid.height;
        if (crushLoss.length < n) {
            crushLoss = new float[n];
            crushVisited = new boolean[n];
        }
        Arrays.fill(crushLoss, 0, n, 0f);
        Arrays.fill(crushVisited, 0, n, false);
        float dx = localDir.x, dy = localDir.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) {
            sys.end();
            return out;
        }
        dx /= len;
        dy /= len;
        float s = grid.tileSize;
        // From just outside where they met, inward to the first solid tile
        float px = (localPoint.x - grid.gridOriginLocal.x) / s - dx, py = (localPoint.y - grid.gridOriginLocal.y) / s - dy;
        int seed = -1;
        for (float t = 0f; t <= 4f; t += 0.25f) {
            int x = MathUtils.floor(px + dx * t), y = MathUtils.floor(py + dy * t);
            Tile tile = grid.getTile(x, y);
            if (tile != null && !tile.isVoid()) {
                seed = sys.pack(x, y);
                px += dx * t;
                py += dy * t;
                break;
            }
        }
        if (seed < 0) {
            sys.end();
            return out;
        }
        out.entryX = seed % grid.width;
        out.entryY = seed / grid.width;

        // The solid tiles joined to the one they met on, within reach, nearest first
        float reach = CRUSH_REACH * Math.max(spread, 0.5f);
        crushTiles.clear();
        crushWeight.clear();
        crushQueue.clear();
        crushQueue.add(seed);
        crushVisited[seed] = true;
        while (crushQueue.size > 0) {
            int idx = crushQueue.removeIndex(0);
            int x = idx % grid.width, y = idx / grid.width;
            float ddx = x + 0.5f - px, ddy = y + 0.5f - py;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (d > reach) continue;
            crushTiles.add(idx);
            crushWeight.add(d);
            for (Side side : Side.values()) {
                int nx = x + side.dx, ny = y + side.dy;
                Tile nt = grid.getTile(nx, ny);
                if (nt == null || nt.isVoid() || crushVisited[sys.pack(nx, ny)]) continue;
                crushVisited[sys.pack(nx, ny)] = true;
                crushQueue.add(sys.pack(nx, ny));
            }
        }
        Arrays.fill(crushVisited, 0, n, false);
        float sum = 0f;
        float twoSigma2 = 2f * spread * spread;
        for (int i = 0; i < crushWeight.size; i++) {
            float d = crushWeight.get(i);
            float w = (float) Math.exp(-d * d / twoSigma2);
            crushWeight.set(i, w);
            sum += w;
        }
        // Nearest first, so the hull gives way before the decks behind it
        sortByDistance(px, py);
        for (int i = 0; i < crushTiles.size; i++) {
            int idx = crushTiles.get(i);
            float share = energy * crushWeight.get(i) / sum;
            out.absorbed += crushInto(idx % grid.width, idx / grid.width, dx, dy, share);
        }

        for (int idx = 0; idx < n; idx++) {
            if (!crushVisited[idx]) continue;
            Tile tile = grid.getTile(idx % grid.width, idx / grid.width);
            float loss = crushLoss[idx];
            if (loss > 0f) {
                out.tilesDamaged++;
                out.worstLoss = Math.max(out.worstLoss, loss);
                if (tile.getIntegrity() <= 0f && report != null) report.structureChanged = true;
            }
        }
        sys.end();
        return out;
    }

    /** Insertion sort of {@link #crushTiles} (with their weights) by distance from (px, py). */
    void sortByDistance(float px, float py) {
        for (int i = 1; i < crushTiles.size; i++) {
            int idx = crushTiles.get(i);
            float w = crushWeight.get(i);
            float d = distance(idx, px, py);
            int j = i - 1;
            while (j >= 0 && distance(crushTiles.get(j), px, py) > d) {
                crushTiles.set(j + 1, crushTiles.get(j));
                crushWeight.set(j + 1, crushWeight.get(j));
                j--;
            }
            crushTiles.set(j + 1, idx);
            crushWeight.set(j + 1, w);
        }
    }

    float distance(int idx, float px, float py) {
        float ddx = idx % sys.grid.width + 0.5f - px, ddy = idx / sys.grid.width + 0.5f - py;
        return ddx * ddx + ddy * ddy;
    }

    /**
     * {@code energy} J into tile (x, y): it takes what its hit points have left, and the rest
     * pushes on into the tiles behind it along ({@code dx}, {@code dy}) until it is spent or leaves
     * the ship. Returns what the structure took.
     */
    float crushInto(int x, int y, float dx, float dy, float energy) {
        float taken = 0f;
        float fx = x + 0.5f, fy = y + 0.5f;
        int steps = sys.grid.width + sys.grid.height;
        while (energy > 1f && steps-- > 0) {
            Tile tile = sys.grid.getTile(x, y);
            if (tile == null || tile.isVoid()) break;
            int idx = sys.pack(x, y);
            crushVisited[idx] = true;
            float integrity = tile.getIntegrity();
            if (integrity > 0f) {
                float hp = crushHitPoints(tile);
                boolean wasBreach = tile.isHullBreach();
                float take = Math.min(energy, hp * integrity);
                energy -= take;
                taken += take;
                sys.damagePiece(tile, take / hp);
                crushLoss[idx] += take / hp;
                crushContents(tile, x, y, take);
                sys.damageConduit(x, y, take, false);
                if (tile.getIntegrity() <= 0f && !wasBreach && tile.isHullBreach()) sys.addBreach(tile, x, y);
                if (energy <= 1f) break;
            }
            // On into the tile behind
            int ox = x, oy = y;
            while (x == ox && y == oy) {
                fx += dx * 0.5f;
                fy += dy * 0.5f;
                x = MathUtils.floor(fx);
                y = MathUtils.floor(fy);
            }
        }
        return taken;
    }

    /** What it takes to crush a tile from whole to nothing: its hit points. A conduit on it is crushed apart from it. */
    float crushHitPoints(Tile tile) {
        return sys.materials.get(tile.material, tile.weightClass).hitPoints;
    }

    /** A system on the tile takes {@code energy} out of its hardness; whoever stands there is wounded by it. */
    void crushContents(Tile tile, int x, int y, float energy) {
        Entity system = sys.grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : PenetrationResolver.subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            float hardness = PenetrationResolver.subsystemHardness(sub.type);
            sys.damageSubsystem(system, sub, Math.min(energy, hardness * sub.health) / hardness, x, y);
        }
        if (sys.crew != null) {
            sys.occupants.clear();
            sys.crew.occupantsAt(sys.ship, x, y, sys.occupants);
            for (int i = 0; i < sys.occupants.size; i++) {
                sys.injure(sys.occupants.get(i), 100f * energy / PenetrationResolver.CREW_CRUSH_LETHAL, HitCause.IMPACT, x, y);
            }
        }
        sys.tryIgnite(tile, x, y, DamageType.KINETIC, energy / PenetrationResolver.FLOOR_RESISTANCE);
    }
}
