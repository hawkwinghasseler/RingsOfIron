package com.ringsofiron.combatsys.modding;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.SubsystemComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks and fits {@link LoadoutDef}s: a spawned NPC's guns drawn from the loadouts for its hull
 * and the difficulty, weighted, and an ace's refit. A loadout naming a weapon the data doesn't
 * have, or one too big for the hardpoint's turret mount, leaves that hardpoint's stock weapon.
 */
public final class Loadouts {
    private Loadouts() {
    }

    /**
     * Draws a loadout for {@code shipId} at difficulty {@code difficulty}: one of its ace refits
     * when {@code ace}, else one of its ordinary ones, by weight. Null when none qualifies (the ship
     * keeps its stock weapons).
     */
    public static LoadoutDef pick(CombatDefs defs, String shipId, double difficulty, boolean ace, Random rng) {
        List<LoadoutDef> candidates = candidates(defs, shipId, difficulty, ace);
        double total = 0;
        for (LoadoutDef l : candidates) total += l.weight;
        if (total <= 0) return null;
        double roll = rng.nextDouble() * total;
        for (LoadoutDef l : candidates) {
            roll -= l.weight;
            if (roll < 0) return l;
        }
        return candidates.get(candidates.size() - 1);
    }

    /** The loadouts that {@link #pick} would draw from, in id order. */
    public static List<LoadoutDef> candidates(CombatDefs defs, String shipId, double difficulty, boolean ace) {
        List<LoadoutDef> out = new ArrayList<>();
        for (LoadoutDef l : defs.loadouts.values()) {
            if (l.ship == null || !l.ship.equals(shipId) || l.ace != ace || l.weight <= 0) continue;
            if (difficulty < l.minDifficulty || difficulty >= l.maxDifficulty) continue;
            out.add(l);
        }
        out.sort((a, b) -> a.id.compareTo(b.id));
        return out;
    }

    /** Fits {@code loadout} to {@code ship}'s hardpoints. Returns how many hardpoints changed weapon. */
    public static int apply(Entity ship, LoadoutDef loadout, CombatDefs defs) {
        WeaponsComponent weapons = ship == null || loadout == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return 0;
        int changed = 0;
        for (int i = 0; i < weapons.mounts.size && i < loadout.weapons.size(); i++) {
            String id = loadout.weapons.get(i);
            WeaponDef def = id == null || id.isEmpty() ? null : defs.weapons.get(id);
            WeaponMountComponent mount = weapons.mounts.get(i);
            if (def == null || def == mount.def || !def.fitsMount(mountSize(mount))) continue;
            mount.def = def;
            mount.cooldown = def.cooldown;
            mount.beamTime = 0f;
            mount.biasSet = false;
            changed++;
        }
        return changed;
    }

    /** The size of the turret mount a hardpoint is wired to, e.g. {@code "M"}; null when it has none. */
    public static String mountSize(WeaponMountComponent mount) {
        SubsystemComponent s = mount.subsystem == null ? null : mount.subsystem.getComponent(SubsystemComponent.class);
        return s == null ? null : s.footprint.size.name();
    }
}
