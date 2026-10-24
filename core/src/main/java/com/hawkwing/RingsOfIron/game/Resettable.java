package com.hawkwing.RingsOfIron.game;

/**
 * State of the game's own that a load starts again from scratch before the save is read over it,
 * so a save that lacks it (older, or with nothing to say) does not keep the last game's. Register
 * with {@link GameWorld#addResettable(Resettable)} where the system is built, as a
 * {@link com.ringsofiron.coreengine.SaveParticipant} registers with the serialization service.
 */
@FunctionalInterface
public interface Resettable {
    /** Forgets everything back to how a new game starts. */
    void reset();
}
