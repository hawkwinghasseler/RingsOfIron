package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Per-material, per-weight-class construction stats: mass, hit points and armor.
 *
 * The real values are data owned by {@code modding-data} (see the {@code [material.*]}
 * entries in the combat-sys design). This class is the in-memory form that the grid
 * reads for mass properties and that combat reads for damage resolution.
 */
public class MaterialTable {

    public static final String DEFAULT_MATERIAL = "hull_steel";

    /** Stats for one weight class of one material. */
    public static class ClassStats {
        /** Mass of one full tile of this material, in kilograms. */
        public final float mass;
        /** Energy (J) needed to take one full tile from integrity 1 to 0. */
        public final float hitPoints;
        /** Penetration (J) a full-integrity tile strips from a round before absorbing. */
        public final float armor;

        public ClassStats(float mass, float hitPoints, float armor) {
            this.mass = mass;
            this.hitPoints = hitPoints;
            this.armor = armor;
        }
    }

    private final ObjectMap<String, ClassStats[]> materials = new ObjectMap<>();

    /** A table holding only the illustrative {@code hull_steel} entry from the combat design. */
    public static MaterialTable createDefault() {
        MaterialTable table = new MaterialTable();
        table.define(DEFAULT_MATERIAL,
                new ClassStats(800f, 2.0e6f, 1.0e6f),
                new ClassStats(2000f, 5.0e6f, 3.0e6f),
                new ClassStats(5000f, 1.2e7f, 8.0e6f));
        return table;
    }

    public void define(String material, ClassStats light, ClassStats medium, ClassStats heavy) {
        materials.put(material, new ClassStats[]{light, medium, heavy});
    }

    /** Returns the stats for a material and class, falling back to the default material. */
    public ClassStats get(String material, WeightClass weightClass) {
        ClassStats[] stats = materials.get(material);
        if (stats == null) stats = materials.get(DEFAULT_MATERIAL);
        if (stats == null) throw new IllegalStateException("Unknown material: " + material);
        return stats[weightClass.ordinal()];
    }
}
