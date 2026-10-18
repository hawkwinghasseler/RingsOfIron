package com.ringsofiron.combatsys.defs;

import com.google.gson.annotations.SerializedName;

/**
 * When a round or missile goes off. {@link Type#DELAYED} detonates {@link #depthTiles} solid
 * tiles inside the hull; {@link Type#PROXIMITY} detonates within {@link #radius} meters of a target.
 */
public class Fuse {
    public enum Type {
        @SerializedName(value = "contact", alternate = "CONTACT") CONTACT,
        @SerializedName(value = "delayed", alternate = "DELAYED") DELAYED,
        @SerializedName(value = "proximity", alternate = "PROXIMITY") PROXIMITY
    }

    public Type type = Type.CONTACT;
    /** For {@link Type#DELAYED}: non-void tiles to travel through before detonating. */
    public int depthTiles;
    /** For {@link Type#PROXIMITY}: detonation distance, meters. */
    public float radius;

    public Fuse() {
    }

    public static Fuse contact() {
        return new Fuse();
    }

    public static Fuse delayed(int depthTiles) {
        Fuse f = new Fuse();
        f.type = Type.DELAYED;
        f.depthTiles = depthTiles;
        return f;
    }

    public static Fuse proximity(float radius) {
        Fuse f = new Fuse();
        f.type = Type.PROXIMITY;
        f.radius = radius;
        return f;
    }
}
