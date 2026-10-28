package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

/**
 * A fixed-size mount on the grid that one ship system installs into.
 *
 * Primary systems (generators, shield emitters, turret mounts) use square mounts;
 * auxiliary and utility systems use rectangular slots, which may be placed in either
 * orientation. Docking ports use port mounts built into the hull edge (see
 * {@link DockingPortLayout}). Every tile inside the footprint belongs to the installed
 * system, so a hit on any of them damages it.
 */
public class MountFootprint {

    public enum MountKind {
        PRIMARY,
        AUXILIARY,
        /** A docking port in the hull edge: width along the hull, depth into the ship. */
        PORT
    }

    public enum MountSize {
        //   primary   auxiliary (unrotated w x h)   port (along the hull x depth; none at XL)
        S (1, 1,     1, 2,                          3, 1),
        M (2, 2,     2, 3,                          5, 2),
        L (3, 3,     3, 7,                          7, 3),
        XL(5, 5,     5, 12,                         0, 0);

        final int primaryW, primaryH, auxW, auxH, portW, portD;

        MountSize(int primaryW, int primaryH, int auxW, int auxH, int portW, int portD) {
            this.primaryW = primaryW;
            this.primaryH = primaryH;
            this.auxW = auxW;
            this.auxH = auxH;
            this.portW = portW;
            this.portD = portD;
        }

        public int baseWidth(MountKind kind) {
            switch (kind) {
                case PRIMARY: return primaryW;
                case PORT:    return portW;
                default:      return auxW;
            }
        }

        public int baseHeight(MountKind kind) {
            switch (kind) {
                case PRIMARY: return primaryH;
                case PORT:    return portD;
                default:      return auxH;
            }
        }

        /** True if this size exists for that kind of mount (there is no XL docking port). */
        public boolean exists(MountKind kind) {
            return baseWidth(kind) > 0;
        }
    }

    public final MountKind kind;
    public final MountSize size;
    /** Bottom-left tile of the footprint. */
    public final int originX, originY;
    /**
     * Counter-clockwise quarter turns (0-3). Odd values swap a rectangular slot's
     * width and height; on a square mount it only sets the system's facing. A docking
     * port faces +y at 0 and turns with it: 1 faces -x, 2 faces -y, 3 faces +x.
     */
    public final int rotation;
    /**
     * The system entity installed here, or null for an empty mount. Not saved: after a
     * load, {@link ShipGridComponent#relinkMounts} restores it from the ship's systems.
     */
    public transient Entity installed;
    /**
     * A docking port's airlock while it is cycling or has cycled ({@link Airlock#of}), or null. Not
     * saved: the doors are, and after a load the airlock is made again from them.
     */
    public transient Airlock airlock;

    /** For deserialization only; the loader fills in the fields. */
    @SuppressWarnings("unused")
    private MountFootprint() {
        this(MountKind.PRIMARY, MountSize.S, 0, 0, 0);
    }

    public MountFootprint(MountKind kind, MountSize size, int originX, int originY, int rotation) {
        if (!size.exists(kind)) throw new IllegalArgumentException("There is no " + size + " " + kind + " mount");
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
            if (!size.exists(kind)) continue;
            int bw = size.baseWidth(kind), bh = size.baseHeight(kind);
            if (bw == w && bh == h) return new MountFootprint(kind, size, originX, originY, 0);
            if (bw == h && bh == w) return new MountFootprint(kind, size, originX, originY, 1);
        }
        return null;
    }
}
