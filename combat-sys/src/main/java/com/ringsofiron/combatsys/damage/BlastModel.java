package com.ringsofiron.combatsys.damage;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.WarheadDef;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

/** Overpressure for {@link PenetrationResolver}: a flood fill out to the blast radius, stopped by intact walls and closed doors. */
final class BlastModel {
    private final PenetrationResolver sys;
    private final IntArray frontier = new IntArray();
    private final IntArray nextFrontier = new IntArray();

    BlastModel(PenetrationResolver sys) {
        this.sys = sys;
    }

    /**
     * Overpressure flood fill out to the blast radius, stopped by intact walls and closed doors.
     * Air carries the blast, so damage scales with pressure at the detonation tile.
     */
    float blast(int cx, int cy, WarheadDef warhead) {
        return blast(cx, cy, warhead.blastRadiusTiles, warhead.blastDamage);
    }

    /** {@link #blast(int, int, WarheadDef)} with {@code blastDamage} J at the middle, out to {@code radius} tiles. */
    float blast(int cx, int cy, int radius, float blastDamage) {
        float pressure = sys.grid.pressureRatioAt(cx, cy);
        if (pressure <= 0f || radius <= 0 || blastDamage <= 0f || !sys.grid.inBounds(cx, cy)) return 0f;

        boolean[] seen = new boolean[sys.grid.width * sys.grid.height];
        frontier.clear();
        frontier.add(sys.pack(cx, cy));
        seen[sys.pack(cx, cy)] = true;
        float total = 0f;
        for (int d = 0; d <= radius && frontier.size > 0; d++) {
            float energy = blastDamage * pressure * (1f - d / (radius + 1f));
            nextFrontier.clear();
            for (int i = 0; i < frontier.size; i++) {
                int idx = frontier.get(i);
                int x = idx % sys.grid.width, y = idx / sys.grid.width;
                Tile tile = sys.grid.getTile(x, y);
                total += blastTile(tile, x, y, energy);
                // Only open space carries the blast onward.
                if (!tile.isOpen() || d == radius) continue;
                for (Side s : Side.values()) {
                    int nx = x + s.dx, ny = y + s.dy;
                    if (!sys.grid.inBounds(nx, ny)) continue;
                    int n = sys.pack(nx, ny);
                    if (seen[n] || sys.grid.isEdgeBlocked(x, y, nx, ny)) continue;
                    Tile nt = sys.grid.getTile(nx, ny);
                    if (nt.isVoid()) continue;
                    seen[n] = true;
                    nextFrontier.add(n);
                }
            }
            frontier.clear();
            frontier.addAll(nextFrontier);
        }
        return total;
    }

    float blastTile(Tile tile, int x, int y, float energy) {
        if (energy <= 0f) return 0f;
        if (tile.isWall()) {
            if (tile.getIntegrity() <= 0f) return 0f;
            MaterialTable.ClassStats stats = sys.materials.get(tile.material, tile.weightClass);
            boolean wasBreach = tile.isHullBreach();
            float take = Math.min(energy, stats.hitPoints * tile.getIntegrity());
            sys.damagePiece(tile, take / stats.hitPoints);
            if (tile.getIntegrity() <= 0f) {
                if (!wasBreach && tile.isHullBreach()) sys.addBreach(tile, x, y);
                if (sys.report != null) sys.report.structureChanged = true;
            }
            return take;
        }
        float spent = 0f;
        Entity system = sys.grid.subsystemAt(x, y);
        SubsystemComponent sub = system == null ? null : PenetrationResolver.subsystemMapper.get(system);
        if (sub != null && !sub.isDestroyed()) {
            float hardness = PenetrationResolver.subsystemHardness(sub.type);
            float take = Math.min(energy, hardness * sub.health);
            sys.damageSubsystem(system, sub, take / hardness, x, y);
            spent += take;
        }
        if (sys.crew != null) {
            sys.occupants.clear();
            sys.crew.occupantsAt(sys.ship, x, y, sys.occupants);
            for (int i = 0; i < sys.occupants.size; i++) {
                // Overpressure hits everyone on the tile without being used up by them.
                sys.injure(sys.occupants.get(i), 100f * energy / PenetrationResolver.CREW_BLAST_LETHAL, HitCause.BLAST, x, y);
            }
        }
        sys.tryIgnite(tile, x, y, DamageType.EXPLOSIVE, energy / PenetrationResolver.FLOOR_RESISTANCE);
        return spent;
    }
}
