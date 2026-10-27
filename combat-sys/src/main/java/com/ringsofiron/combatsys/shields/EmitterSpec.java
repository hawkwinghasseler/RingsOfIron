package com.ringsofiron.combatsys.shields;

import com.ringsofiron.interiorgrid.MountFootprint;

/**
 * What a shield emitter of each mount size can do (documentation/combat-sys/Design.md, 5.11). The
 * prototype's figures; they move to components.toml once they settle.
 */
public final class EmitterSpec {
    /** Particles it adds to the ship's pool, kg. */
    public final float capacity;
    /** How far from the emitter it answers for impacts, meters. */
    public final float radius;
    /** Regions it can concentrate mass in at once. */
    public final int regions;
    /** How wide each region is: mass thins to nothing one width either side of its center, meters. */
    public final float width;
    /** Densest it packs a region, kg per square meter of the cloud's face. */
    public final float maxDensity;
    /** Particles thrown faster than this leave the bubble for good, m/s. */
    public final float retention;
    /** How fast its particles travel along the perimeter to a region, m/s. */
    public final float clusterSpeed;
    /** Heat it holds before it trips, J; and the heat its radiators shed, W. */
    public final float heatTrip, cooling;

    EmitterSpec(float capacity, float radius, int regions, float width, float maxDensity, float retention,
                float clusterSpeed, float heatTrip, float cooling) {
        this.capacity = capacity;
        this.radius = radius;
        this.regions = regions;
        this.width = width;
        this.maxDensity = maxDensity;
        this.retention = retention;
        this.clusterSpeed = clusterSpeed;
        this.heatTrip = heatTrip;
        this.cooling = cooling;
    }

    public static final EmitterSpec S = new EmitterSpec(300f, 20f, 1, 4f, 25f, 400f, 40f, 60e6f, 0.4e6f);
    public static final EmitterSpec M = new EmitterSpec(900f, 30f, 2, 4f, 60f, 400f, 35f, 180e6f, 1.2e6f);
    public static final EmitterSpec L = new EmitterSpec(2_700f, 45f, 3, 4f, 200f, 400f, 30f, 540e6f, 3.6e6f);
    public static final EmitterSpec XL = new EmitterSpec(8_000f, 70f, 4, 5f, 400f, 400f, 25f, 1_600e6f, 10.7e6f);

    public static EmitterSpec of(MountFootprint.MountSize size) {
        if (size == null) return S;
        switch (size) {
            case M: return M;
            case L: return L;
            case XL: return XL;
            default: return S;
        }
    }

    /** Most mass one region holds: its peak density over its width, through the cloud's depth. */
    public float regionCap() {
        return maxDensity * width * ShieldCloudComponent.DEPTH;
    }
}
