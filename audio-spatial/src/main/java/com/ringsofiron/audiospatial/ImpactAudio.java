package com.ringsofiron.audiospatial;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.events.CollisionEvent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;

/**
 * Plays a hull impact for each physics collision and each hypervelocity pass. Hook it up with
 * {@link #attach(PhysicsManager)}. Impacts are exterior sounds, so they reach the listener
 * through the frame even from inside the ship ("Spacer Ears").
 */
public class ImpactAudio implements Listener<CollisionEvent> {
    private final AudioManager audio;
    /** Collisions slower than this (m/s) make no sound. */
    public float minSpeed = 0.5f;
    /** Collision speed (m/s) that plays at full intensity. */
    public float fullSpeed = 20f;
    public String eventPath = AudioEventCatalog.HULL_IMPACT;

    public ImpactAudio(AudioManager audio) {
        this.audio = audio;
    }

    /** Listens to {@code physics.collisions} and {@code physics.fastPassImpacts}. */
    public ImpactAudio attach(PhysicsManager physics) {
        physics.collisions.add(this);
        physics.fastPassImpacts.add((signal, event) -> onFastPass(event));
        return this;
    }

    @Override
    public void receive(Signal<CollisionEvent> signal, CollisionEvent event) {
        play(event.approachSpeed, event.pointX, event.pointY);
    }

    private void play(float speed, float x, float y) {
        if (speed < minSpeed) return;
        float intensity = MathUtils.clamp(speed / fullSpeed, 0.1f, 1f);
        audio.playOneShot(eventPath, x, y, intensity);
    }

    /** A hypervelocity round passing through a hull. */
    void onFastPass(FastPassImpactEvent event) {
        play(event.relativeSpeed, event.pointX, event.pointY);
    }
}
