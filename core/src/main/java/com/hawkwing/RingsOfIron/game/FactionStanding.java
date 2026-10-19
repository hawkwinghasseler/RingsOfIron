package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.stationsdocking.DockingEvent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.function.Supplier;

/**
 * How the factions take what the player does to them. Forcing a door is a hostile act that costs
 * standing with the craft's faction, cutting more than prying or hacking; docking without clearance
 * costs nothing by itself. Below {@link #REFUSE_BELOW} stations of the faction refuse the player
 * docking clearance; at {@link #HOSTILE_AT} or below its crew fight the player's.
 */
public class FactionStanding {
    public static final float MIN = -100f, MAX = 100f;
    /** Standing lost for forcing a door each way. */
    public static final float PRY_COST = 10f, HACK_COST = 10f, CUT_COST = 25f;
    /** Below this, the faction's stations refuse the player clearance. */
    public static final float REFUSE_BELOW = -15f;
    /** At or below this, the faction's crew fight the player's. */
    public static final float HOSTILE_AT = -50f;
    /** The player's own faction. */
    public static final int PLAYER = 0;

    /** A hostile act the player did, for the screen. */
    public static final class Act {
        public final Entity victim;
        public final int faction;
        public final ForceMethod method;
        public final float standing;

        Act(Entity victim, int faction, ForceMethod method, float standing) {
            this.victim = victim;
            this.faction = faction;
            this.method = method;
            this.standing = standing;
        }
    }

    private final Supplier<Entity> player;
    /** Hostile acts not yet shown; the screen drains them. */
    public final Array<Act> acts = new Array<>();

    public FactionStanding(Supplier<Entity> player) {
        this.player = player;
    }

    /** The faction a craft belongs to: a station's, or its crew's; -1 for a craft of no one's. */
    public static int factionOf(Entity craft) {
        if (craft == null) return -1;
        if (craft.getComponent(StationComponent.class) != null) return StationCrew.FACTION;
        ShipTaskBoardComponent board = craft.getComponent(ShipTaskBoardComponent.class);
        return board == null ? -1 : board.faction;
    }

    /** The player's standing with {@code faction} (0 if they have never crossed it). */
    public float standing(int faction) {
        Entity ship = player.get();
        FactionStandingComponent c = ship == null ? null : ship.getComponent(FactionStandingComponent.class);
        return c == null ? 0f : c.get(faction);
    }

    /** Changes the player's standing with {@code faction}. */
    public void change(int faction, float by) {
        Entity ship = player.get();
        if (ship == null || faction < 0 || faction == PLAYER) return;
        FactionStandingComponent c = ship.getComponent(FactionStandingComponent.class);
        if (c == null) {
            c = new FactionStandingComponent();
            ship.add(c);
        }
        c.add(faction, by);
    }

    /** Takes note of a docking event: a door the player's crew forced costs standing with its owners. */
    public void onDockingEvent(DockingEvent e) {
        if (e.kind != DockingEvent.Kind.DOOR_FORCED || e.vessel == null || e.vessel != player.get()) return;
        int faction = factionOf(e.other);
        change(faction, -cost(e.method));
        acts.add(new Act(e.other, faction, e.method, standing(faction)));
    }

    /** Standing a hostile act costs. */
    public static float cost(ForceMethod how) {
        switch (how) {
            case CUT:
                return CUT_COST;
            case HACK:
                return HACK_COST;
            default:
                return PRY_COST;
        }
    }

    /**
     * Whether crew of factions {@code a} and {@code b} fight: two factions of ships always do;
     * station crew fight only the player's, and only once the player's standing with them is down
     * to {@link #HOSTILE_AT}.
     */
    public boolean hostile(int a, int b) {
        if (a == b) return false;
        if (a == StationCrew.FACTION || b == StationCrew.FACTION) {
            int other = a == StationCrew.FACTION ? b : a;
            return other == PLAYER && standing(StationCrew.FACTION) <= HOSTILE_AT;
        }
        return true;
    }

    /** Why {@code station} refuses the player clearance on account of standing, or null if it does not. */
    public String refusal(Entity station, Entity vessel) {
        if (vessel != player.get()) return null;
        return standing(factionOf(station)) < REFUSE_BELOW ? "you forced their doors" : null;
    }
}
