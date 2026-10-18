package com.ringsofiron.audiospatial;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.physicsexterior.events.CollisionEvent;

/**
 * Plays a hull impact for each physics collision. Add it to physics-exterior's
 * {@code PhysicsManager.collisions} signal. Impacts are exterior sounds, so they reach the
 * listener through the frame even from inside the ship ("Spacer Ears").
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

    @Override
    public void receive(Signal<CollisionEvent> signal, CollisionEvent event) {
        if (event.approachSpeed < minSpeed) return;
        float intensity = MathUtils.clamp(event.approachSpeed / fullSpeed, 0.1f, 1f);
        audio.playOneShot(eventPath, event.pointX, event.pointY, intensity);
    }
}
