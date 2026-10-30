package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import java.util.function.Supplier;

/**
 * The player's engines as a sound: a thruster loop on the player's ship whose intensity follows
 * the stick. The main engine drives it fully; RCS (strafing, turning) is quieter. Rotation assist
 * firing the RCS on its own to stop a spin is heard too, as hard as it fires. It ramps up and
 * down rather than switching, and falls silent when the tanks are dry. Runs every frame just
 * before the audio systems.
 */
public class EngineAudio extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 890;
    /** How loud strafing and turning are next to the main engine at the same stick deflection. */
    public static final float RCS_SHARE = 0.5f;
    /** Intensity change per second when throttling up, and when throttling down. */
    static final float ATTACK_PER_SECOND = 8f;
    static final float RELEASE_PER_SECOND = 3f;

    private final ComponentMapper<FlightControlComponent> controls = ComponentMapper.getFor(FlightControlComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusters = ComponentMapper.getFor(ThrusterComponent.class);
    private final ComponentMapper<AudioEmitterComponent> emitters = ComponentMapper.getFor(AudioEmitterComponent.class);
    private final Supplier<Entity> player;
    private Entity ship;
    private AudioEmitterComponent emitter;
    private float intensity;

    public EngineAudio(Supplier<Entity> player) {
        super(Math.min(DEFAULT_PRIORITY, AudioUpdateSystem.DEFAULT_PRIORITY - 1));
        this.player = player;
    }

    /** The loop's current 0 to 1 intensity. */
    public float getIntensity() {
        return intensity;
    }

    @Override
    public void update(float deltaTime) {
        Entity current = player.get();
        if (current != ship) {
            // A new ship, or a loaded game: the old ship keeps no engine sound of ours.
            if (ship != null && emitter != null && emitters.get(ship) == emitter) ship.remove(AudioEmitterComponent.class);
            ship = current;
            emitter = null;
            intensity = 0f;
        }
        if (ship == null) return;
        FlightControlComponent control = controls.get(ship);
        ThrusterComponent thruster = thrusters.get(ship);
        if (control == null || thruster == null) return;
        if (emitter == null || emitters.get(ship) != emitter) {
            AudioEmitterComponent existing = emitters.get(ship);
            if (existing != null && AudioEventCatalog.THRUSTER.equals(existing.eventPath)) {
                // Ours, back from a save.
                emitter = existing;
            } else if (existing != null) {
                // One emitter per entity: leave a ship that already plays something else alone.
                return;
            } else {
                emitter = new AudioEmitterComponent(AudioEventCatalog.THRUSTER);
                ship.add(emitter);
            }
        }

        float target = thruster.isOutOfFuel() ? 0f : MathUtils.clamp(Math.max(Math.abs(control.forward),
                RCS_SHARE * Math.max(Math.abs(control.strafe), Math.max(Math.abs(control.turn), control.assistTurn))), 0f, 1f);
        float rate = target > intensity ? ATTACK_PER_SECOND : RELEASE_PER_SECOND;
        intensity = MathUtils.clamp(target, intensity - rate * deltaTime, intensity + rate * deltaTime);
        emitter.setParameter(AudioParameters.INTENSITY, intensity);
    }
}
