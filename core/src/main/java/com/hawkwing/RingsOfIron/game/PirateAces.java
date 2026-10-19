package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Named pirate aces (round 3, enemies). A pirate becomes one when it gets away from a fight with
 * the player, or once it has robbed {@link #PLUNDERS_TO_ACE} freighters; no more than
 * {@link #MAX_ACES} are about at once. An ace takes a callsign, a better crew ({@link #ACE_SKILL})
 * and a refit from its ship's {@code ace = true} loadouts, which carry a weapon no yard sells. One
 * that got away from the player keeps a grudge and hunts them whatever the odds. Beaten, it is worth
 * {@link #BOUNTY_SCALE} times a pirate's bounty, {@link #BOUNTY_STANDING} more standing, and a spare
 * rare weapon is left crated in its hold, for whoever boards it.
 */
public final class PirateAces {
    public static final int MAX_ACES = 3;
    public static final int PLUNDERS_TO_ACE = 3;
    /** An ace's crew are at least this good at the guns and the sensors. */
    public static final float ACE_SKILL = 0.85f;
    public static final double BOUNTY_SCALE = 4;
    public static final int BOUNTY_STANDING = 6;

    static final String[] CALLSIGNS = {"Red Kestrel", "Black Sal", "Iron Dagny", "Mad Ilse", "Cold Teodor",
            "Silent Oyelaran", "Lucky Mbeki", "Grey Vasquez", "Knife Haruto", "Old Brannigan"};

    private PirateAces() {
    }

    /** Aces about now: promoted and not yet beaten. */
    public static int alive(Collection<HostileComponent> hostiles) {
        int n = 0;
        for (HostileComponent h : hostiles) if (h.ace && !h.partDropped) n++;
        return n;
    }

    /**
     * Makes {@code h} an ace, if it is a pirate, not one already, and there is room. Returns false
     * when it was not promoted.
     */
    public static boolean promote(GameWorld world, Entity ship, HostileComponent h, Collection<HostileComponent> all,
            double difficulty, boolean grudge) {
        if (!h.isPirate() || h.ace || alive(all) >= MAX_ACES) return false;
        h.ace = true;
        h.grudge = grudge;
        h.displayName = callsign(all, h);
        Random rng = new Random(h.displayName.hashCode() * 31L + h.plunders + h.escapes);
        LoadoutDef refit = Loadouts.pick(world.combatDefs, h.shipId, difficulty, true, rng);
        if (refit != null && Loadouts.apply(ship, refit, world.combatDefs) > 0) h.loadout = refit.id;
        h.firepower = HostileTraffic.firepower(ship);
        for (CrewMemberComponent c : crewOf(world, ship)) {
            c.def.gunnerySkill = Math.max(c.def.gunnerySkill, ACE_SKILL);
            c.def.trackingSkill = Math.max(c.def.trackingSkill, ACE_SKILL);
        }
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons != null) for (WeaponMountComponent m : weapons.mounts) m.gunnerySkill = Math.max(m.gunnerySkill, ACE_SKILL);
        return true;
    }

    /** The first callsign no other ace has. */
    static String callsign(Collection<HostileComponent> all, HostileComponent self) {
        Set<String> taken = new HashSet<>();
        for (HostileComponent o : all) if (o != self && o.ace) taken.add(o.displayName);
        for (String c : CALLSIGNS) if (!taken.contains(c)) return c;
        return CALLSIGNS[0] + " " + (taken.size() + 1);
    }

    /**
     * A beaten ace: leaves a crate of its rare weapon in its hold (the first rare weapon in the data,
     * if its refit carried none), once, and forgets its grudge. Returns the weapon's id, or null.
     */
    public static String dropPart(Entity ship, HostileComponent h, CombatDefs defs) {
        if (!h.ace || h.partDropped) return null;
        h.partDropped = true;
        h.grudge = false;
        String rare = rareWeapon(ship);
        if (rare == null) rare = firstRare(defs);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (rare == null || hold == null) return rare;
        hold.load(Salvage.PART + rare, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
        return rare;
    }

    /** The first weapon aboard no yard sells, or null. */
    static String rareWeapon(Entity ship) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return null;
        for (WeaponMountComponent m : weapons.mounts) if (m.def != null && m.def.rare) return m.def.id;
        return null;
    }

    static String firstRare(CombatDefs defs) {
        String best = null;
        for (WeaponDef d : defs.weapons.values()) if (d.rare && (best == null || d.id.compareTo(best) < 0)) best = d.id;
        return best;
    }

    private static Iterable<CrewMemberComponent> crewOf(GameWorld world, Entity ship) {
        ImmutableArray<Entity> crew = world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW);
        List<CrewMemberComponent> out = new ArrayList<>();
        for (Entity e : crew) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) out.add(e.getComponent(CrewMemberComponent.class));
        }
        return out;
    }
}
