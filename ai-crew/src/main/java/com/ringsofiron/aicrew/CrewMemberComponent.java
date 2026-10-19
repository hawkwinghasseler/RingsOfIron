package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A crew member: stats, health, consciousness state, role and what they are doing.
 * Their place on the ship is in {@link GridPositionComponent}.
 *
 * Saves keep who the crew member is and their health, but not the job in hand: the
 * claimed task and the carry and treatment links are transient. After a load
 * {@link CrewSaveParticipant} sets carried casualties down where they are and crew pick
 * their jobs afresh from the hazards and casualties around them.
 */
public class CrewMemberComponent implements Component {

    public String name = "Crew";
    public CrewRole role = CrewRole.CREWMAN;
    public CrewDef def = new CrewDef();
    /**
     * Skills, morale, wage and history ({@link CrewRecord}); read it through {@link #record()}, which
     * makes one from the def for a crew member saved before records existed.
     */
    public CrewRecord record;
    /** Crew of different factions on the same ship are hostile to each other. */
    public int faction = 0;

    public float health;
    public CrewState state = CrewState.CONSCIOUS;
    public CrewDamageSink.DamageCause lastDamageCause;

    /** The board task this crew member has claimed, or null. */
    public transient CrewTask task;
    /** The unconscious crew member this one is carrying, or null. */
    public transient Entity carrying;
    /** The crew member carrying this one, or null. */
    public transient Entity carriedBy;
    /** Goods in hand on a carry task, or null. */
    public transient Load load;
    /**
     * The craft this crew member works for, when it is not always the one they are aboard: a
     * station's loaders walk aboard docked ships and come home. Null means the craft they are on.
     */
    public transient Entity home;
    /** {@link #home} as saved. */
    public EntityRef homeRef;
    /** The medic treating this crew member, or null. */
    public transient Entity treatedBy;
    /** Item in hand, for display. */
    public String equippedItem = "";
    /**
     * Held prisoner: crew of a ship that was taken. They neither fight nor are fought, and take no
     * work; they stay where they are kept.
     */
    public boolean held;
    /** Whether this crew member has their space suit on. */
    public boolean suited;
    /** Seconds of air left in their suit; topped up when it is taken off. */
    public float suitAir;

    /** Seconds until this crew member can fire again. */
    public float fireCooldown = 0f;
    /** Short description of the current activity, for debug display and the HUD. */
    public String activity = "idle";

    public CrewMemberComponent() {
        this.health = def.maxHealth;
        this.suitAir = def.suitAir;
    }

    public CrewMemberComponent(String name, CrewRole role, int faction) {
        this.name = name;
        this.role = role;
        this.faction = faction;
        this.def = CrewDef.forRole(role);
        this.health = def.maxHealth;
        this.suitAir = def.suitAir;
        this.record = CrewRecord.from(def);
    }

    /** This crew member's record, made from their def if they have none yet. */
    public CrewRecord record() {
        if (record == null) record = CrewRecord.from(def);
        return record;
    }

    /** True while this crew member breathes from their suit: wearing it, with air left in it. */
    public boolean breathesFromSuit() {
        return suited && suitAir > 0f;
    }

    /** Puts their suit on, or takes it off (topping its air up). */
    public void setSuited(boolean on) {
        suited = on;
        if (!on) suitAir = def.suitAir;
        equippedItem = on ? "space suit" : "";
    }

    /** Goods carried by hand on a {@link CarryTask}. */
    public static final class Load {
        public final CarryTask task;
        public final double units;

        public Load(CarryTask task, double units) {
            this.task = task;
            this.units = units;
        }
    }

    /** Makes {@code ship} this crew member's home, keeping {@link #homeRef} in step. */
    public void setHome(Entity ship) {
        home = ship;
        homeRef = ship != null && ship.getComponent(UuidComponent.class) != null ? EntityRef.of(ship) : null;
    }

    public boolean isConscious() {
        return state == CrewState.CONSCIOUS;
    }

    public boolean isAlive() {
        return state != CrewState.DEAD;
    }

    public float healthFraction() {
        return def.maxHealth > 0f ? health / def.maxHealth : 0f;
    }
}
