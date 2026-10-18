package com.ringsofiron.audiospatial.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.ringsofiron.audiospatial.backend.AudioEventInstance;

/**
 * A sound attached to an entity: an engine, a weapon, a fire, a broken pipe.
 *
 * An {@link Placement#EXTERIOR} emitter sits at its entity's TransformComponent and is never
 * muffled. An {@link Placement#INTERIOR} emitter sits at ship-local ({@link #localX},
 * {@link #localY}) aboard {@link #ship} and is muffled by the walls between it and the
 * listener.
 *
 * Removing the component or its entity stops the sound.
 */
public class AudioEmitterComponent implements Component {

    public enum Placement { EXTERIOR, INTERIOR }

    public String eventPath;
    public Placement placement = Placement.EXTERIOR;
    /** The ship an interior emitter is aboard. */
    public transient Entity ship;
    /** Ship-local position in meters, for interior emitters. */
    public float localX, localY;
    /** Start playing as soon as the audio system sees the emitter. */
    public boolean autoStart = true;
    /** Remove the whole entity once a one-shot finishes. Used for spawned one-shot entities. */
    public boolean removeEntityWhenDone;
    /** Extra event parameters, applied every frame. Set them with {@link #setParameter}. */
    public final ObjectFloatMap<String> parameters = new ObjectFloatMap<>();

    // Runtime state, written by the audio systems
    public transient AudioEventInstance instance;
    /** Walls and closed doors between this emitter and the listener, last occlusion pass. */
    public int wallCount;
    /** 0 (clear) to 1 (fully muffled), last occlusion pass. */
    public float occlusion;
    /** Air pressure the sound travels through, 0 to 1, last occlusion pass. */
    public float pressure = 1f;
    /** World position this frame. */
    public float worldX, worldY;
    public transient boolean started;
    /** Last frame's world position, for velocity. Cleared to skip a jump (e.g. a teleport). */
    public transient boolean hasPrevious;
    public transient float prevX, prevY;

    public AudioEmitterComponent() {}

    public AudioEmitterComponent(String eventPath) {
        this.eventPath = eventPath;
    }

    /** An emitter inside {@code ship} at ship-local ({@code localX}, {@code localY}). */
    public static AudioEmitterComponent interior(String eventPath, Entity ship, float localX, float localY) {
        AudioEmitterComponent e = new AudioEmitterComponent(eventPath);
        e.placement = Placement.INTERIOR;
        e.ship = ship;
        e.localX = localX;
        e.localY = localY;
        return e;
    }

    public void setParameter(String name, float value) {
        parameters.put(name, value);
    }

    /** Starts (or restarts) the sound on the next audio update. */
    public void play() {
        autoStart = true;
        started = false;
    }
}
