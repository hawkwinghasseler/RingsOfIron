package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

/** Beams for {@link PenetrationResolver}: a tick's energy burning into the first intact thing along the line, and on. */
final class BurnModel {
    private final PenetrationResolver sys;

    BurnModel(PenetrationResolver sys) {
        this.sys = sys;
    }

    /** As {@link PenetrationResolver#burn}. */
    PenetrationResolver.BurnResult burn(ShipGridComponent grid, Entity ship, Vector2 localPoint, Vector2 localDir, float energy,
                                        RandomXS128 rng, DamageReport report, final PenetrationResolver.BurnResult out) {
        sys.begin(grid, ship, rng, report);
        out.reset();
        if (energy <= 0f) {
            sys.end();
            return out;
        }
        final float dirX = localDir.x, dirY = localDir.y;
        float ex = (localPoint.x - grid.gridOriginLocal.x) / grid.tileSize;
        float ey = (localPoint.y - grid.gridOriginLocal.y) / grid.tileSize;
        final float[] left = {energy};
        final float[] heat = {0f};
        final boolean[] entered = {false};
        float maxT = grid.width + grid.height + 2f * PenetrationResolver.START_OUTSIDE_TILES + 2f;
        GridRayTraversal.traverse(ex - dirX * PenetrationResolver.START_OUTSIDE_TILES, ey - dirY * PenetrationResolver.START_OUTSIDE_TILES, dirX, dirY, maxT,
                new GridRayTraversal.Visitor() {
                    @Override
                    public boolean tile(int x, int y, float tEnter, float tExit) {
                        Tile tile = sys.grid.getTile(x, y);
                        if (tile == null || tile.isVoid()) return !entered[0];
                        entered[0] = true;
                        if (tile.isWall()) {
                            if (tile.getIntegrity() <= 0f) return true;
                            float took = burnWall(tile, x, y, left[0], out);
                            left[0] -= took;
                            heat[0] = took;
                            return true;
                        }
                        if (heat[0] > 0f) {
                            sys.tryIgnite(tile, x, y, DamageType.ENERGY, heat[0] * PenetrationResolver.BEAM_HEAT_SOAK / PenetrationResolver.FLOOR_RESISTANCE);
                            heat[0] = 0f;
                        }
                        if (left[0] <= 0f) return false;
                        if (burnContents(tile, x, y, left[0], out)) {
                            left[0] = 0f;
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public boolean edge(int fromX, int fromY, int toX, int toY, float t) {
                        if (!entered[0] || left[0] <= 0f) return left[0] > 0f || heat[0] > 0f;
                        EdgeWall wall = sys.grid.getEdgeBetween(fromX, fromY, toX, toY);
                        if (wall == null || !wall.blocks()) return true;
                        MaterialTable.ClassStats stats = sys.materials.get(wall.material, wall.weightClass);
                        float hardness = (stats.hitPoints + stats.armor) * PenetrationResolver.EDGE_WALL_FRACTION;
                        float ablation = sys.ablationFactor(wall.material);
                        float took = Math.min(left[0], hardness * wall.integrity / ablation);
                        wall.integrity = Math.max(0f, wall.integrity - took * ablation / hardness);
                        left[0] -= took;
                        heat[0] = took;
                        touch(out, fromX, fromY, took);
                        if (wall.isDestroyed()) {
                            out.burnedThrough = true;
                            if (report != null) report.structureChanged = true;
                        }
                        return true;
                    }
                });
        sys.end();
        return out;
    }

    /** Burns into an intact wall tile. Returns the energy it took. */
    float burnWall(Tile tile, int x, int y, float energy, PenetrationResolver.BurnResult out) {
        if (energy <= 0f) return 0f;
        MaterialTable.ClassStats stats = sys.materials.get(tile.material, tile.weightClass);
        float hardness = stats.hitPoints + stats.armor;
        float ablation = sys.ablationFactor(tile.material);
        float integrity = tile.getIntegrity();
        float took = Math.min(energy, hardness * integrity / ablation);
        boolean wasBreach = tile.isHullBreach();
        sys.damagePiece(tile, took * ablation / hardness);
        touch(out, x, y, took);
        if (tile.getIntegrity() <= 0f) {
            tile.setIntegrity(0f);
            out.burnedThrough = true;
            if (!wasBreach && tile.isHullBreach()) sys.addBreach(tile, x, y);
            if (sys.report != null) sys.report.structureChanged = true;
        }
        return took;
    }

    /** A beam crossing an open tile. Returns true if machinery or a person there stopped it. */
    boolean burnContents(Tile tile, int x, int y, float energy, PenetrationResolver.BurnResult out) {
        Entity system = sys.grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : PenetrationResolver.subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            sys.damageSubsystem(system, sub, energy / PenetrationResolver.subsystemHardness(sub.type), x, y);
            touch(out, x, y, energy);
            sys.tryIgnite(tile, x, y, DamageType.ENERGY, energy / PenetrationResolver.FLOOR_RESISTANCE);
            return true;
        }
        if (sys.crew != null) {
            sys.occupants.clear();
            sys.crew.occupantsAt(sys.ship, x, y, sys.occupants);
            if (sys.occupants.size > 0) {
                sys.injure(sys.occupants.first(), energy / PenetrationResolver.CREW_JOULES_PER_HP, HitCause.FIRE, x, y);
                touch(out, x, y, energy);
                sys.tryIgnite(tile, x, y, DamageType.ENERGY, energy / PenetrationResolver.FLOOR_RESISTANCE);
                return true;
            }
        }
        // The beam crosses the deck, and burns into the conduit laid on it if it strikes it
        sys.strikeConduit(x, y, energy, false);
        return false;
    }

    static void touch(PenetrationResolver.BurnResult out, int x, int y, float energy) {
        if (energy <= 0f) return;
        if (!out.touched) {
            out.touched = true;
            out.tileX = x;
            out.tileY = y;
        }
        out.absorbed += energy;
    }
}
