package com.ringsofiron.audiospatial.backend;

/**
 * One playing (or ready to play) copy of a sound event. Positions are 2D world (bubble) meters; the backend maps them into
 * its own 3D space.
 *
 * Instances are driven from the game thread only.
 */
public interface AudioEventInstance {

    /** The event path this instance plays, e.g. {@code "event:/weapons/laser"}. */
    String path();

    void start();

    void stop();

    /**
     * True from {@link #start()} until the sound ends. A looping sound plays until stopped; a
     * one-shot ends on its own.
     */
    boolean isPlaying();

    /** Frees the instance once it stops. A released instance must not be used again. */
    void release();

    void setPosition(float x, float y);

    void setVelocity(float vx, float vy);

    /** Sets a named event parameter, such as {@link AudioParameters#OCCLUSION}. */
    void setParameter(String name, float value);

    /** The last value set for the parameter, or {@code defaultValue} if it was never set. */
    float getParameter(String name, float defaultValue);
}
