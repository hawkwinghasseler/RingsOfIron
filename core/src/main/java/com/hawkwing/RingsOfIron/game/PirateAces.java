package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;

import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.ShipDisablement;
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
 * and a refit from its ship's {@code ace = true} loadouts, which carry its hull's best guns. One
 * that got away from the player keeps a grudge and hunts them whatever the odds. Beaten, it is worth
 * {@link #BOUNTY_SCALE} times a pirate's bounty, {@link #BOUNTY_STANDING_PER_TIER} more standing for each
 * step of its bounty's tier (from 1 for an ace no match for the player's ship), and a spare of
 * its most valuable weapon is left crated in its hold, for whoever boards it. An ace never runs on the odds and
 * fights on to {@link #BREAK_OFF_HULL} (round 4: the one in round 3 ran at its first hit).
 *
 * <p>Round 5: once the player flies a gunship, an ace takes one of the pirates' gunships
 * ({@link #gunshipAce}); and an ace's bounty is posted the day it appears ({@link #postBounty}),
 * not on the next day's boards.
 */
public final class PirateAces {
    public static final int MAX_ACES = 3;
    public static final int PLUNDERS_TO_ACE = 3;
    /** An ace's crew are at least this good at the guns and the sensors. */
    public static final float ACE_SKILL = 0.85f;
    public static final double BOUNTY_SCALE = 4;
    /**
     * Standing an ace's bounty adds for each step of its tier, counting from 1: 3 (an ace no match for the
     * player's ship) to 9 (one that far outguns it). Round 7: was a flat 6, which took seed 7 from
     * Lieutenant Commander to Captain in a week on four light aces. Placeholder number.
     */
    public static final int BOUNTY_STANDING_PER_TIER = 3;
    /**
     * An ace breaks off below this share of its hull (a plain pirate at {@link HostileTraffic#BREAK_OFF_HULL}),
     * and never runs on the odds: it fights on until it is nearly beaten.
     */
    public static final double BREAK_OFF_HULL = ShipDisablement.SURRENDER_HULL + 0.05;

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
        if (!h.isPirate() || h.moored() || h.ace || alive(all) >= MAX_ACES) return false;
        h.ace = true;
        h.grudge = grudge;
        String was = h.displayName;
        h.displayName = callsign(all, h);
        // In the bubble its marker goes by the new name too (a contract's suffix kept)
        com.ringsofiron.uihud.components.HudMarkerComponent marker =
                ship.getComponent(com.ringsofiron.uihud.components.HudMarkerComponent.class);
        if (marker != null && was != null && marker.label.startsWith(was)) {
            marker.label = h.displayName + marker.label.substring(was.length());
        }
        // The career's seed too, so two careers' aces take different refits (round 6)
        Random rng = new Random(NewGame.mix(world.events.seed, h.displayName.hashCode() * 31L + h.plunders + h.escapes));
        LoadoutDef refit = Loadouts.pick(world.combatDefs, h.shipId, difficulty, true, rng);
        if (refit != null && Loadouts.apply(ship, refit, world.combatDefs) > 0) h.loadout = refit.id;
        h.firepower = HostileTraffic.firepower(ship);
        // An ace's crew keep everyone's order: resetting first made the aces harder (round 10)
        com.ringsofiron.aicrew.ShipTaskBoardComponent board = ship.getComponent(com.ringsofiron.aicrew.ShipTaskBoardComponent.class);
        if (board != null) board.resetsPowerFirst = false;
        for (CrewMemberComponent c : crewOf(world, ship)) {
            // Into the crew's record too, which the crew career turns back into the def
            CrewRecord r = c.record();
            r.gunnery = Math.max(r.gunnery, ACE_SKILL);
            r.tracking = Math.max(r.tracking, ACE_SKILL);
            c.def.gunnerySkill = Math.max(c.def.gunnerySkill, ACE_SKILL);
            c.def.trackingSkill = Math.max(c.def.trackingSkill, ACE_SKILL);
        }
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons != null) for (WeaponMountComponent m : weapons.mounts) m.gunnerySkill = Math.max(m.gunnerySkill, ACE_SKILL);
        return true;
    }

    /**
     * Makes the first plain pirate gunship, by uuid, an ace, when no ace at large flies a gunship and
     * there is room for another ({@link #MAX_ACES}). It keeps no grudge: it goes after the player when
     * it outguns them enough, as any pirate does. Returns its uuid, or null when none was promoted.
     */
    public static String gunshipAce(GameWorld world, java.util.Map<String, HostileComponent> all, double difficulty) {
        String best = null;
        for (java.util.Map.Entry<String, HostileComponent> e : all.entrySet()) {
            HostileComponent h = e.getValue();
            if (!h.isPirate() || !HostileTraffic.gunshipHull(h.shipId)) continue;
            if (h.ace && !h.partDropped) return null;
            if (!h.ace && (best == null || e.getKey().compareTo(best) < 0)) best = e.getKey();
        }
        Entity ship = best == null ? null : world.engine.getEntity(best);
        if (ship == null || !promote(world, ship, all.get(best), all.values(), difficulty, false)) return null;
        return best;
    }

    /**
     * Posts the bounty on the ace {@code uuid} at once on every board that would list it, unless today's
     * board already does; it stands until the day turns, when the day's boards take it up.
     */
    public static void postBounty(GameWorld world, String uuid) {
        if (world.contracts == null || uuid == null) return;
        double now = world.engine.getSimSeconds();
        double dayEnds = (Math.floor(now / ContractBoard.DAY) + 1) * ContractBoard.DAY;
        for (com.ringsofiron.macrosim.economy.EconomyNode node : world.macroSim.stations()) {
            Entity station = world.engine.getEntity(node.stationUuid);
            if (station == null) continue;
            boolean listed = false;
            for (Contract c : world.contracts.offers(node.stationUuid)) listed |= uuid.equals(c.targetUuid);
            if (listed) continue;
            int giver = world.standing.factionOf(station);
            for (Contract c : world.contracts.bounties(node, giver)) {
                if (!uuid.equals(c.targetUuid)) continue;
                c.stationUuid = node.stationUuid;
                c.deadline = dayEnds;
                world.contracts.postEvent(c);
            }
        }
    }

    /** The first callsign no other ace has. */
    static String callsign(Collection<HostileComponent> all, HostileComponent self) {
        Set<String> taken = new HashSet<>();
        for (HostileComponent o : all) if (o != self && o.ace) taken.add(o.displayName);
        for (String c : CALLSIGNS) if (!taken.contains(c)) return c;
        return CALLSIGNS[0] + " " + (taken.size() + 1);
    }

    /**
     * A beaten ace: leaves a crate of its most valuable weapon in its hold, once, and forgets its
     * grudge. Returns the weapon's id, or null when it carries none.
     */
    public static String dropPart(Entity ship, HostileComponent h, CombatDefs defs) {
        if (!h.ace || h.partDropped) return null;
        h.partDropped = true;
        h.grudge = false;
        String best = bestWeapon(ship);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (best == null || hold == null) return best;
        hold.load(Salvage.PART + best, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS);
        return best;
    }

    /** The weapon aboard with the highest price (the first id on a tie), or null. */
    static String bestWeapon(Entity ship) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return null;
        WeaponDef best = null;
        for (WeaponMountComponent m : weapons.mounts) {
            WeaponDef d = m.def;
            if (d == null) continue;
            if (best == null || d.price > best.price || (d.price == best.price && d.id.compareTo(best.id) < 0)) best = d;
        }
        return best == null ? null : best.id;
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
