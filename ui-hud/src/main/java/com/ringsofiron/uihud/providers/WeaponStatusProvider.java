package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Every weapon on the player's ship, one row per mount, with what it is doing and whether it can
 * fire. Filled by the game wiring layer from combat-sys's mounts, the guns' crews and the power grid.
 */
public interface WeaponStatusProvider {
    /** Fills {@code out}, already cleared. Called once a frame. */
    void collect(WeaponFrame out);

    enum Kind {
        /** A gun that throws rounds. */
        GUN,
        BEAM,
        LAUNCHER,
        /** A gun or beam that shoots down missiles by itself. */
        POINT_DEFENSE
    }

    /** Why a mount is or is not ready, worst first. */
    enum State {
        /** Its system is wrecked, or damaged below working order. */
        DESTROYED,
        /** Being swapped for another weapon. */
        REFIT,
        /** Shorted out: back when reset, or when the breaker recloses. */
        SHORTED,
        /** Knocked out by an ion hit for a while. */
        STUNNED,
        /** Switched off, or no circuit reaches it. */
        NO_POWER,
        /** A launcher with nothing left. */
        EMPTY,
        /** Loading or cooling: {@link Mount#cooldown} seconds to go. */
        RELOADING,
        /** Loaded, but the circuit's store cannot pay the next shot yet. */
        CHARGING,
        READY
    }

    /** Who works the mount. */
    enum Controller {
        /** The player has it in hand, at the weapons console or the gun. */
        PLAYER,
        /** A gunner at the gun. */
        CREW,
        /** Crew at the weapons console, from afar. */
        CONSOLE,
        /** Point defense, by itself. */
        AUTO,
        /** Nobody: only the player's trigger fires it. */
        NONE
    }

    final class Mount {
        /** Index in the ship's mounts, stable while the fit does not change. */
        public int index;
        /** Where it sits ("FWD GUN", "PORT PD 2"), by the rule in {@code RoomNames}. */
        public String name = "";
        /** The weapon's own name ("Railgun"). */
        public String weapon = "";
        public Kind kind = Kind.GUN;
        public State state = State.READY;
        public Controller controller = Controller.NONE;
        /** Seconds until it can fire, and its full reload. */
        public float cooldown, reload;
        /** Missiles left and the magazine, for a launcher with one; -1 for none or unlimited. */
        public int ammo = -1, magazine = -1;
        /** Firing on something now, and point defense (or a beam) turned on a missile. */
        public boolean engaging, defending;
        /** Fires at will (crewed and given a target) rather than on the trigger only. */
        public boolean weaponsFree;
        /** The locked target is inside its firing arc; false with no lock. */
        public boolean inArc;
        /** Chance a shot at the locked target hits while it dodges, 0..1; NaN with no lock or no track. */
        public float hitChance = Float.NaN;
        /** Range to the locked target from the muzzle, m; NaN with none. */
        public float range = Float.NaN;
        /** Hardpoint in the ship's body frame, meters, and the arc's centre and width, degrees off the nose. */
        public float hardpointX, hardpointY, facingDeg, arcDeg;
        /** Health of its system, 0..1 (1 with no system). */
        public float health = 1f;
        /** Its system's tile on the ship's grid, or -1. */
        public int tileX = -1, tileY = -1;

        /** Ready, or merely reloading: the mount works. */
        public boolean working() {
            return state == State.READY || state == State.RELOADING || state == State.CHARGING;
        }

        /** 0 just fired .. 1 loaded. */
        public float readiness() {
            return reload <= 0f ? 1f : Math.max(0f, Math.min(1f, 1f - cooldown / reload));
        }
    }

    final class WeaponFrame {
        public final Array<Mount> mounts = new Array<>();
        private final Array<Mount> pool = new Array<>();
        /** Missiles left across the working launchers; -1 when one never runs out. */
        public int missilesLeft;
        /** Seconds until the next decoy can go, 0 when ready. */
        public float decoyCooldown;
        public float decoyReload;
        /** Any point defense engaging a missile now. */
        public boolean pointDefenseActive;
        /** The captain's order to hold fire. */
        public boolean holdFire;
        /** The locked target's label, or "" with no lock. */
        public String target = "";

        public Mount add() {
            Mount m = pool.size > 0 ? pool.pop() : new Mount();
            m.index = mounts.size;
            m.name = "";
            m.weapon = "";
            m.kind = Kind.GUN;
            m.state = State.READY;
            m.controller = Controller.NONE;
            m.cooldown = m.reload = 0f;
            m.ammo = m.magazine = -1;
            m.engaging = m.defending = m.weaponsFree = m.inArc = false;
            m.hitChance = Float.NaN;
            m.range = Float.NaN;
            m.hardpointX = m.hardpointY = m.facingDeg = m.arcDeg = 0f;
            m.health = 1f;
            m.tileX = m.tileY = -1;
            mounts.add(m);
            return m;
        }

        public void clear() {
            pool.addAll(mounts);
            mounts.clear();
            missilesLeft = 0;
            decoyCooldown = 0f;
            decoyReload = 0f;
            pointDefenseActive = false;
            holdFire = false;
            target = "";
        }

        /** Mounts of {@code kind}, counted. */
        public int count(Kind kind) {
            int n = 0;
            for (Mount m : mounts) if (m.kind == kind) n++;
            return n;
        }
    }
}
