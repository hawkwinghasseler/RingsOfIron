package com.hawkwing.RingsOfIron.debug;

/**
 * A scenario's own use of the mouse on the game (a tool that paints walls, drops fires, drags a wall): given world
 * meters, which {@code world.docking.bodyOf(ship).getLocalPoint} turns into a ship's own. Return true to keep the
 * click from the game. A drag and the release come only after a press this returned true for.
 */
public interface ScenarioPointer {
    /** @param button {@link com.badlogic.gdx.Input.Buttons}; {@code deckView} true in the deck view */
    boolean down(float worldX, float worldY, int button, boolean deckView);

    default void dragged(float worldX, float worldY) {
    }

    default void up(float worldX, float worldY, int button) {
    }
}
