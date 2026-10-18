package com.ringsofiron.audiospatial;

import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.audiospatial.backend.AcousticModel;
import com.ringsofiron.audiospatial.backend.SoundEventDef;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Combines the two ways a sound inside a ship reaches the listener.
 *
 * <ul>
 *   <li><b>Direct path</b>: the straight line, losing {@link #DIRECT_WALL_DB} for every wall
 *       it passes ({@link GridOcclusion}). Clear and bright with nothing in the way, but it
 *       falls off fast once walls intervene. It is traced as a beam of {@link #BEAM_RAYS}
 *       parallel rays about two tiles wide and averaged, so a single ray grazing a wall or
 *       threading a doorway does not swing the volume from one tile to the next.</li>
 *   <li><b>Room path</b>: around through the rooms ({@link SoundField}), losing a little at
 *       each open doorway and more at each wall or sealed door, with distance rolloff over the
 *       route's length.</li>
 * </ul>
 *
 * The two add as power. The result is a {@link Result#transmission} volume factor, relative to
 * an unobstructed sound at the same straight-line distance, and an {@link Result#occlusion}
 * that sets the low-pass cutoff: a power-weighted blend of each path's muffling.
 */
public final class SoundPropagation {
    private SoundPropagation() {}

    /** Loss on the direct path for every wall it crosses, dB. */
    public static final float DIRECT_WALL_DB = 20f;
    /**
     * Loss on the direct path for every open doorway it threads, dB. A doorway is a narrow
     * opening: a straight line through two aligned doors should not suddenly sound like the
     * same room.
     */
    public static final float DIRECT_OPENING_DB = 6f;
    /** Room-path loss (walls and sealed doors, dB) that counts as fully muffled. */
    public static final float FULL_MUFFLE_DB = 80f;
    /** Walls on the direct path that count as fully muffled. */
    public static final int FULL_MUFFLE_WALLS = 4;
    /** Weight of the room path against the direct one, so a sound in the same room is not doubled. */
    public static final float ROOM_PATH_SCALE = 0.7f;

    /** Parallel rays in the direct-path beam, and their spread either side of center in tiles. */
    public static final int BEAM_RAYS = 5;
    public static final float BEAM_HALF_WIDTH = 1f;

    public static class Result {
        /** Volume relative to an unobstructed sound at the straight-line distance, 0 and up. */
        public float transmission;
        /** 0 (bright) to 1 (fully muffled). */
        public float occlusion;
        /** Volume of each path, for display and tuning. */
        public float directGain, roomGain;
        /** Walls on the center ray of the direct beam. */
        public int walls;
    }

    public static float dbToGain(float db) {
        return (float) Math.pow(10.0, -db / 20.0);
    }

    /**
     * Traces the direct beam between ship-local points (ex, ey) and (lx, ly).
     *
     * @param out receives {@code beam[0]}: the mean power factor (1 = nothing in the way) and
     *            {@code beam[1]}: the power-weighted occlusion of the beam
     * @return walls on the center ray
     */
    public static int traceBeam(ShipGridComponent grid, float ex, float ey, float lx, float ly, float[] out) {
        float dx = lx - ex, dy = ly - ey;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        // Unit perpendicular in meters per tile of offset
        float px = len > 0f ? -dy / len * grid.tileSize : 0f, py = len > 0f ? dx / len * grid.tileSize : 0f;
        float power = 0f, occ = 0f;
        int rays = 0, centerWalls = 0;
        int[] openings = new int[1];
        for (int i = 0; i < BEAM_RAYS; i++) {
            float o = BEAM_RAYS == 1 ? 0f : -BEAM_HALF_WIDTH + 2f * BEAM_HALF_WIDTH * i / (BEAM_RAYS - 1);
            int[] a = grid.localToTile(ex + px * o, ey + py * o);
            int[] b = grid.localToTile(lx + px * o, ly + py * o);
            boolean center = i == BEAM_RAYS / 2;
            if (a[0] < 0 || b[0] < 0) {
                if (!center) continue; // ray leaves the grid: drop it from the beam
                a = grid.localToTile(ex, ey);
                b = grid.localToTile(lx, ly);
            }
            int w = GridOcclusion.trace(grid, a[0], a[1], b[0], b[1], openings);
            if (center) centerWalls = w;
            float g = dbToGain(w * DIRECT_WALL_DB + openings[0] * DIRECT_OPENING_DB);
            power += g * g;
            occ += g * g * Math.min(w, FULL_MUFFLE_WALLS) / (float) FULL_MUFFLE_WALLS;
            rays++;
        }
        out[0] = rays > 0 ? power / rays : 0f;
        out[1] = power > 0f ? occ / power : 1f;
        return centerWalls;
    }

    /**
     * @param distance  straight-line distance from emitter to listener, meters
     * @param beam      from {@link #traceBeam}: mean power factor and occlusion of the direct beam
     * @param walls     walls on the center ray, for display
     * @param field     the emitter's room field
     * @param lx        listener tile
     * @param ly        listener tile
     */
    public static Result combine(SoundEventDef def, float distance, float[] beam, int walls, SoundField field, int lx, int ly, Result out) {
        float openGain = AcousticModel.distanceGain(distance, def.minDistance, def.maxDistance);
        float direct = openGain * (float) Math.sqrt(beam[0]);
        float directOcc = beam[1];

        float room = 0f, roomOcc = 1f;
        if (field != null && field.reaches(lx, ly)) {
            room = ROOM_PATH_SCALE * AcousticModel.distanceGain(field.length(lx, ly), def.minDistance, def.maxDistance)
                    * dbToGain(field.lossDb(lx, ly));
            roomOcc = MathUtils.clamp(field.muffleDb(lx, ly) / FULL_MUFFLE_DB, 0f, 1f);
        }
        out.walls = walls;
        out.directGain = direct;
        out.roomGain = room;
        float wd = direct * direct, wr = room * room, total = wd + wr;
        if (total <= 0f || openGain <= 0f) {
            out.transmission = 0f;
            out.occlusion = 1f;
            return out;
        }
        out.transmission = (float) Math.sqrt(total) / openGain;
        out.occlusion = (wd * directOcc + wr * roomOcc) / total;
        return out;
    }
}
