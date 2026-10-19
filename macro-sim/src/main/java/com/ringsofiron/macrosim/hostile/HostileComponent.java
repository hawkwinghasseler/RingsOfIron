package com.ringsofiron.macrosim.hostile;

import com.badlogic.ashley.core.Component;

/**
 * An armed NPC ship that lives in macro-sim like a freighter: a pirate lurking by a station's
 * lanes for prey, or a faction patrol flying between its faction's stations. References are UUID
 * strings, as the save format requires; {@link HostileTrafficController} drives it on the rails
 * and the game flies and fights it in the bubble.
 */
public class HostileComponent implements Component {
    /** {@link #targetUuid} when the player is the prey. */
    public static final String PLAYER = "@player";

    public enum Role {
        /** Lurks by a station's lanes and plans intercepts on freighters it outguns, or the player. */
        PIRATE,
        /** Flies between its faction's stations, holding at each for a while, and engages its enemies. */
        PATROL
    }

    public enum State {
        /** Riding an orbit by a station: a pirate's lurk, a patrol's stop. */
        HOLDING,
        /** Flying to meet its prey: by a freighter's destination, or at the player. */
        INTERCEPTING,
        /** Lying in wait by the station a freighter it chose is about to reach. */
        AMBUSHING,
        /** Flying back to its lurk after a hunt. */
        RETURNING,
        /** A patrol flying to its next station. */
        PATROLLING,
        /** Inside the player bubble: rails plan suspended, flown and fought by the game. */
        IN_BUBBLE
    }

    public String displayName;
    /** The ship it flies, a {@code [ship.<id>]} in the data. */
    public String shipId;
    /** Whose it is: {@code pirates}, or a faction's id. */
    public String faction;
    public Role role = Role.PIRATE;
    public State state = State.HOLDING;
    /** State to resume after leaving the bubble. */
    public State resumeState = State.HOLDING;
    /** A pirate's lurk station; a patrol's current stop, or the one it is flying to. */
    public String stationUuid;
    /** A patrol's stations in the order it flies them; empty for a pirate. */
    public String[] route = new String[0];
    public int routeIndex;
    /** What a pirate is after: a freighter's UUID, {@link #PLAYER}, or null. */
    public String targetUuid;
    /** Station by which it waits for {@link #targetUuid}, while ambushing or flying there. */
    public String ambushStationUuid;
    /** How heavily armed it is, in the same units the game rates the player's ship in. */
    public double firepower;
    /** What is left of its hull, 0-1; the game sets it after a fight in the bubble. */
    public double hull = 1;
    /** Cargo it has taken off freighters. */
    public String lootCommodity;
    public double lootQuantity;
    public int plunders;
    /** Sim second of its next arrival, ambush or look around; negative when nothing is due (saves have no NaN). */
    public double eventTime = -1;
    /** It does not go hunting again before this sim second. */
    public double restUntil;
    /** True while it is breaking off a fight in the bubble, until it is back on the rails. */
    public boolean brokeOff;
    /** The {@code [loadout.*]} it is armed with; null for its ship's stock weapons. */
    public String loadout;
    /**
     * A pirate flying as another's wingman: its leader's UUID. It lurks beside the leader, goes
     * where the leader hunts and fights beside it. Null for a leader or a pirate on its own.
     */
    public String wingOf;
    /** A named ace (it got away from the player, or robbed freighters): better crew and a refit. */
    public boolean ace;
    /** An ace that remembers the player: it goes after them whatever the odds. */
    public boolean grudge;
    /** Fights with the player it got away from. */
    public int escapes;
    /** An ace's rare part has been left aboard it for whoever beat it. */
    public boolean partDropped;
    public transient long eventId;

    public HostileComponent() {
    }

    public HostileComponent(String displayName, String shipId, String faction, Role role, double firepower) {
        this.displayName = displayName;
        this.shipId = shipId;
        this.faction = faction;
        this.role = role;
        this.firepower = firepower;
    }

    public boolean isPirate() {
        return role == Role.PIRATE;
    }

    /** True when it is going after the player. */
    public boolean huntingPlayer() {
        return PLAYER.equals(targetUuid);
    }
}
