package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;

/**
 * Marks the crew member the player walks around as. The avatar skips the behavior
 * tree; {@link CrewMovementSystem} moves it from {@link #moveX}/{@link #moveY}, which the
 * input layer sets each frame, as a body in the deck's Box2D world: walls and shut doors stop
 * it, and it pushes past other crew softly ({@link CrewBody}).
 *
 * Other modules (HUD interact prompts, the audio listener) read the avatar's position
 * from its {@link GridPositionComponent}; see {@link CrewQueries#playerAvatar}.
 */
public class PlayerAvatarComponent implements Component {
    /** Desired movement in [-1, 1] along grid x and y. Input of the moment, so not saved. */
    public transient float moveX, moveY;
}
