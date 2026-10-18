package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * Where the player's avatar is inside a ship, so the HUD can offer "Press [E]" prompts for
 * nearby consoles and draw the avatar on the minimap. Implemented by ai-crew (which owns crew
 * and avatar movement on the grid).
 */
public interface InteractorProvider {
    /** The ship entity (holding a {@code ShipGridComponent}) the avatar is aboard, or null. */
    Entity getAvatarShip();

    /**
     * Writes the avatar's position in fractional tile coordinates of that ship's grid
     * (tile (x, y) spans x..x+1, y..y+1). Returns false when the avatar is not aboard a ship.
     */
    boolean getAvatarTile(Vector2 out);
}
