package com.ringsofiron.combatsys.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A way to arm one hull, the {@code [loadout.*]} table: which weapon goes on each of the ship's
 * hardpoints, in the order its {@code [ship.*]} lists them. NPC ships draw one at spawn from the
 * loadouts for their hull and the game's difficulty, weighted, so the same hull fights differently.
 * See {@link com.ringsofiron.combatsys.modding.Loadouts}.
 */
public class LoadoutDef {
    public String id;
    /** A few words for the player, e.g. "Missile boat"; the id when null. */
    public String name;
    /** The {@code [ship.*]} this arms. */
    public String ship;
    /** How often it is drawn against the others that qualify. */
    public float weight = 1f;
    /** It is drawn only from this difficulty up, and below {@link #maxDifficulty}. */
    public double minDifficulty;
    public double maxDifficulty = Double.MAX_VALUE;
    /** An ace's refit: drawn only when a pirate becomes an ace, never at spawn. */
    public boolean ace;
    /**
     * Weapon ids by hardpoint; an empty string, or a list shorter than the ship's hardpoints,
     * leaves those hardpoints with their stock weapon.
     */
    public List<String> weapons = new ArrayList<>();

    /** {@link #name}, or the id when it has none. */
    public String displayName() {
        return name != null ? name : id;
    }
}
