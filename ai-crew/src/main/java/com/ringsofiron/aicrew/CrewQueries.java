package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Read-only lookups other modules use without touching crew internals: the player
 * avatar and its ship-local position (for HUD interact prompts and the audio listener),
 * and the skill the crew bring to a ship's guns and sensors.
 */
public final class CrewQueries {

    private static final Family AVATAR = Family.all(PlayerAvatarComponent.class, GridPositionComponent.class).get();

    /** A ship station whose skill the crew aboard set. */
    public enum Station {
        GUNNERY,
        TRACKING
    }

    /** Returned by {@link #bestSkill} for a ship with no crew aboard. */
    public static final float NO_CREW = -1f;

    private CrewQueries() {}

    /**
     * The best skill at {@code station} among the conscious crew of {@code ship}'s own faction
     * (boarders don't man its guns). The player's avatar counts as crew.
     *
     * @return the skill, 0 .. 1; 0 when every crew member aboard is down; {@link #NO_CREW} when the
     *     ship has no crew aboard at all, e.g. an NPC ship whose crew is not simulated
     */
    public static float bestSkill(Engine engine, Entity ship, Station station) {
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        int faction = board == null ? 0 : board.faction;
        ImmutableArray<Entity> crew = engine.getEntitiesFor(CrewService.CREW);
        float best = NO_CREW;
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (p.ship != ship || c.faction != faction) continue;
            float skill = !c.isConscious() ? 0f
                    : station == Station.GUNNERY ? c.def.gunnerySkill : c.def.trackingSkill;
            best = Math.max(best, skill);
        }
        return best;
    }

    /** The player's avatar entity, or null if the player is not aboard as crew. */
    public static Entity playerAvatar(Engine engine) {
        ImmutableArray<Entity> found = engine.getEntitiesFor(AVATAR);
        return found.size() == 0 ? null : found.first();
    }

    /**
     * Writes the avatar's ship-local position (meters, ship body frame) into {@code out}.
     *
     * @return false if there is no avatar or its ship has no grid
     */
    public static boolean playerShipLocal(Engine engine, Vector2 out) {
        Entity e = playerAvatar(engine);
        if (e == null) return false;
        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
        ShipGridComponent grid = p.ship == null ? null : p.ship.getComponent(ShipGridComponent.class);
        if (grid == null) return false;
        p.toShipLocal(grid, out);
        return true;
    }
}
