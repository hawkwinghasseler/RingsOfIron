package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * The player's standing with each faction, on the player's ship and saved with it: 0 to begin
 * with, down to {@link FactionStanding#MIN} as hostile acts add up. Indexed by faction.
 */
public class FactionStandingComponent implements Component {
    public float[] standing = new float[8];

    public float get(int faction) {
        return faction >= 0 && faction < standing.length ? standing[faction] : 0f;
    }

    public void add(int faction, float change) {
        if (faction < 0) return;
        if (faction >= standing.length) standing = java.util.Arrays.copyOf(standing, faction + 1);
        standing[faction] = Math.max(FactionStanding.MIN, Math.min(FactionStanding.MAX, standing[faction] + change));
    }
}
