package com.ringsofiron.moddingdata.defs;

/**
 * Construction material stats per weight class, from combat-sys design section 6.
 *
 * <pre>
 * [material.hull_steel]
 * fire_chance = 0.0
 * ablation_factor = 1.0
 * light  = { mass = 800.0,  hit_points = 2.0e6, armor = 1.0e6 }
 * medium = { mass = 2000.0, hit_points = 5.0e6, armor = 3.0e6 }
 * heavy  = { mass = 5000.0, hit_points = 1.2e7, armor = 8.0e6 }
 * </pre>
 */
public class MaterialDef {
    public String id;
    public float fireChance;
    public float ablationFactor = 1f;
    public ClassStats light = new ClassStats();
    public ClassStats medium = new ClassStats();
    public ClassStats heavy = new ClassStats();

    public static class ClassStats {
        /** kg per full tile. */
        public float mass;
        /** J to take a full tile from integrity 1 to 0. */
        public float hitPoints;
        /** Penetration (J) stripped from a round before the tile absorbs. */
        public float armor;
    }
}
