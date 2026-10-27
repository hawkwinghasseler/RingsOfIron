package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.components.HudMarkerComponent;

/**
 * Who works the player's guns, and so whether they fire by themselves.
 *
 * <p>A gun fires at will on the hostiles inside its effective range ({@link #hostile}) while somebody works it: a crew member
 * manning its own turret mount, at that gunner's skill, or else one at the weapons console, which
 * works every gun at once but none of them as well ({@link #REMOTE_SKILL}). With nobody at either it
 * only aims at the locked target and waits for the trigger, which fires it with the player's skill
 * at that same share. It takes the locked target when that is
 * hostile and inside its effective range on it, else the nearest such hostile contact. Guns hold while the ship is docked
 * (round 8: they opened fire at the berth and the recoil shook the ship off it).
 *
 * <p>A gun the player has in hand ({@link PlayerGunnery#inHand}) is theirs: it never fires at will,
 * and shoots with the player's skill when they sit at it, or from the weapons console with its own
 * gunner's, or the player's at {@link #REMOTE_SKILL} if it has none. Launchers always wait for the
 * player's word, since their missiles run out; point defense keeps to itself
 * ({@link CrewStationSync}).
 *
 * <p>While the ship is docked at a station, each launcher takes on a missile every {@link #RESTOCK_TIME}.
 */
public class GunCrews extends FixedLogicSystem {
    /** Seconds between updates. */
    public static final float INTERVAL = 0.25f;
    /** Share of the skill a gun keeps worked only from the weapons console, with nobody at the gun. */
    public static final float REMOTE_SKILL = 0.5f;
    /** Seconds per missile a station's yard hands up to each launcher while docked. */
    public static final float RESTOCK_TIME = 2f;

    /** The captain's order to hold fire: no gun fires at will, crewed or not. */
    public boolean holdFire;

    private final GameWorld world;
    private float restockClock;

    public GunCrews(GameWorld world) {
        super(INTERVAL);
        this.world = world;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        Entity ship = world.getPlayer();
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return;
        boolean docked = world.docking.dockedTo(ship) != null;
        restock(weapons, docked && world.docking.dockedWithClearance(ship), deltaTime);
        man(world, ship, weapons, docked || holdFire);
    }

    /** Sets each of the ship's guns' gunner, target and whether it fires at will; {@code hold}: none does. */
    static void man(GameWorld world, Entity ship, WeaponsComponent weapons, boolean hold) {
        Engine engine = world.engine.getEcsEngine();
        float console = CrewQueries.gunneryAt(engine, ship, weaponsConsole(ship));
        Entity seatedGun = world.bridge.seatedGun();
        float player = playerSkill(world);
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        for (WeaponMountComponent m : weapons.mounts) {
            WeaponDef def = m.def;
            if (def == null || def.pointDefense) continue;
            float gunner = isGun(m.subsystem) ? CrewQueries.gunneryAt(engine, ship, m.subsystem) : CrewQueries.NO_CREW;
            if (PlayerGunnery.inHand(world, m)) {
                m.weaponsFree = false;
                m.gunnerySkill = seatedGun != null ? player
                        : gunner != CrewQueries.NO_CREW ? gunner : player * REMOTE_SKILL;
                continue;
            }
            m.manualAim = false;
            float skill = gunner != CrewQueries.NO_CREW ? gunner
                    : console != CrewQueries.NO_CREW ? console * REMOTE_SKILL : CrewQueries.NO_CREW;
            // Nobody works it: only the trigger fires it, from wherever the player is, as from the console
            m.gunnerySkill = skill != CrewQueries.NO_CREW ? skill : player * REMOTE_SKILL;
            if (skill == CrewQueries.NO_CREW || def.mount == WeaponDef.MountType.LAUNCHER) {
                m.weaponsFree = false;
                m.target = null;
                continue;
            }
            Entity target = hold ? null : pick(world, ship, sensor, m);
            m.target = target;
            m.weaponsFree = target != null;
        }
    }

    /**
     * What a gun fires on at will: the locked target if it is hostile and inside the gun's effective
     * range on it ({@link WeaponControlSystem#effectiveRange}), else the nearest hostile the ship
     * tracks inside it. Null with none.
     */
    static Entity pick(GameWorld world, Entity ship, SensorComponent sensor, WeaponMountComponent m) {
        if (sensor == null) return null;
        Vector2 at = new Vector2();
        Track locked = sensor.trackFor(sensor.lockedTarget);
        if (locked != null && hostile(world, locked.target)
                && distance(world, locked, m, at) <= world.weaponControl.openingRange(m, locked.target)) return locked.target;
        Entity best = null;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < sensor.tracks.size; i++) {
            Track t = sensor.tracks.get(i);
            if (t.target == ship || !hostile(world, t.target)) continue;
            float d = distance(world, t, m, at);
            if (d <= nearest && d <= world.weaponControl.openingRange(m, t.target)) {
                nearest = d;
                best = t.target;
            }
        }
        return best;
    }

    private static float distance(GameWorld world, Track t, WeaponMountComponent m, Vector2 at) {
        return t.positionAt(world.sensors.ageOf(t), at).dst(m.muzzle);
    }

    /**
     * True if the player's crew shoot at {@code e} unbidden: a ship fighting the player (its captain
     * is after them, or it is marked hostile and has them locked) that has not struck and is not
     * disabled. The crew don't start a fight: a pirate minding its own business waits for the
     * player's trigger. Never a missile; point defense has those.
     */
    public static boolean hostile(GameWorld world, Entity e) {
        Entity player = world.getPlayer();
        if (e == null || e.isScheduledForRemoval() || e == player) return false;
        if (e.getComponent(MissileComponent.class) != null || e.getComponent(DisabledComponent.class) != null) return false;
        if (Struck.struck(world, e)) return false;
        NpcCombatComponent fight = e.getComponent(NpcCombatComponent.class);
        if (fight != null && fight.target == player) return true;
        HudMarkerComponent marker = e.getComponent(HudMarkerComponent.class);
        SensorComponent sensor = e.getComponent(SensorComponent.class);
        return marker != null && marker.kind == HudMarkerComponent.Kind.HOSTILE && sensor != null && sensor.lockedTarget == player;
    }

    /** Docked at a station: a missile into each launcher that is short, one per {@link #RESTOCK_TIME}. */
    private void restock(WeaponsComponent weapons, boolean atStation, float dt) {
        if (!atStation) {
            restockClock = 0f;
            return;
        }
        restockClock += dt;
        if (restockClock < RESTOCK_TIME) return;
        restockClock -= RESTOCK_TIME;
        for (WeaponMountComponent m : weapons.mounts) WeaponControlSystem.restock(m, 1);
    }

    private static boolean isGun(Entity subsystem) {
        SubsystemComponent s = subsystem == null ? null : subsystem.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemType.TURRET_MOUNT;
    }

    /** The ship's first weapons console that is not destroyed (the one fought from), or null. */
    static Entity weaponsConsole(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemType.WEAPONS && !s.isDestroyed()) return e;
        }
        return null;
    }

    /** The gunnery skill of the player's own character, or a middling one with none aboard. */
    static float playerSkill(GameWorld world) {
        Entity avatar = world.avatar.getAvatar();
        CrewMemberComponent c = avatar == null ? null : avatar.getComponent(CrewMemberComponent.class);
        return c == null || !c.isConscious() ? (c == null ? 0.5f : 0f) : c.def.gunnerySkill;
    }
}
