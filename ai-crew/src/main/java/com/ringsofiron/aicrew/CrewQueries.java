package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Read-only lookups other modules use without touching crew internals: the player
 * avatar and its ship-local position (for HUD interact prompts and the audio listener).
 */
public final class CrewQueries {

    private static final Family AVATAR = Family.all(PlayerAvatarComponent.class, GridPositionComponent.class).get();

    private CrewQueries() {}

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
