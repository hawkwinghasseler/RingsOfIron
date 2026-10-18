package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A crew member: stats, health, consciousness state, role and what they are doing.
 * Their place on the ship is in {@link GridPositionComponent}.
 */
public class CrewMemberComponent implements Component {

    public String name = "Crew";
    public CrewRole role = CrewRole.CREWMAN;
    public CrewDef def = new CrewDef();
    /** Crew of different factions on the same ship are hostile to each other. */
    public int faction = 0;

    public float health;
    public CrewState state = CrewState.CONSCIOUS;
    public CrewDamageSink.DamageCause lastDamageCause;

    /** The board task this crew member has claimed, or null. */
    public CrewTask task;
    /** The unconscious crew member this one is carrying, or null. */
    public Entity carrying;
    /** The crew member carrying this one, or null. */
    public Entity carriedBy;
    /** The medic treating this crew member, or null. */
    public Entity treatedBy;
    /** Item in hand, for display. */
    public String equippedItem = "";

    /** Seconds until this crew member can fire again. */
    public float fireCooldown = 0f;
    /** Short description of the current activity, for debug display and the HUD. */
    public String activity = "idle";

    public CrewMemberComponent() {
        this.health = def.maxHealth;
    }

    public CrewMemberComponent(String name, CrewRole role, int faction) {
        this.name = name;
        this.role = role;
        this.faction = faction;
        this.def = CrewDef.forRole(role);
        this.health = def.maxHealth;
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
