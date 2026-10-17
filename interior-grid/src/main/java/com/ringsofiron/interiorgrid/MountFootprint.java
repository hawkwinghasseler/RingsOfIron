package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

/**
 * A fixed-size mount on the grid that one ship system installs into.
 *
 * Primary systems (generators, shield emitters, turret mounts) use square mounts;
 * auxiliary and utility systems use rectangular slots, which may be placed in either
 * orientation. Every tile inside the footprint belongs to the installed system, so a
 * hit on any of them damages it.
 */
public class MountFootprint {

    public enum MountKind {
        PRIMARY,
        AUXILIARY
    }

    public enum MountSize {
        //   primary   auxiliary (unrotated w x h)
        S (1, 1,     1, 2),
        M (2, 2,     2, 3),
        L (3, 3,     3, 7),
        XL(5, 5,     5, 12);

        final int primaryW, primaryH, auxW, auxH;

        MountSize(int primaryW, int primaryH, int auxW, int auxH) {
            this.primaryW = primaryW;
            this.primaryH = primaryH;
            this.auxW = auxW;
            this.auxH = auxH;
        }

        public int baseWidth(MountKind kind) {
            return kind == MountKind.PRIMARY ? primaryW : auxW;
        }

        public int baseHeight(MountKind kind) {
            return kind == MountKind.PRIMARY ? primaryH : auxH;
        }
    }

    public final MountKind kind;
    public final MountSize size;
    /** Bottom-left tile of the footprint. */
    public final int originX, originY;
    /**
     * Counter-clockwise quarter turns (0-3). Odd values swap a rectangular slot's
     * width and height; on a square mount it only sets the system's facing.
     */
    public final int rotation;
    /** The system entity installed here, or null for an empty mount. */
    public Entity installed;

    public MountFootprint(MountKind kind, MountSize size, int originX, int originY, int rotation) {
        this.kind = kind;
        this.size = size;
        this.originX = originX;
        this.originY = originY;
        this.rotation = Math.floorMod(rotation, 4);
    }

    public int width() {
        return rotation % 2 == 0 ? size.baseWidth(kind) : size.baseHeight(kind);
    }

    public int height() {
        return rotation % 2 == 0 ? size.baseHeight(kind) : size.baseWidth(kind);
    }

    public boolean covers(int x, int y) {
        return x >= originX && x < originX + width() && y >= originY && y < originY + height();
    }

    /**
     * Finds the mount of the given kind whose footprint is exactly w x h tiles, in
     * either orientation. Returns null if no legal mount has that size.
     */
    public static MountFootprint fromSize(MountKind kind, int originX, int originY, int w, int h) {
        for (MountSize size : MountSize.values()) {
            int bw = size.baseWidth(kind), bh = size.baseHeight(kind);
            if (bw == w && bh == h) return new MountFootprint(kind, size, originX, originY, 0);
            if (bw == h && bh == w) return new MountFootprint(kind, size, originX, originY, 1);
        }
        return null;
    }
}
