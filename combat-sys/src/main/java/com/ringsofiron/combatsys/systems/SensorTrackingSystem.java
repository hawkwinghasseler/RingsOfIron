package com.ringsofiron.combatsys.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.combatsys.ContactState;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Turns the truth into what each ship's crew believes (combat-sys/Design.md 3.1). Every
 * {@code fixInterval} a sensor takes a position fix on each contact with a
 * {@link SignatureComponent} in range, with noise that grows with range, and folds it into that
 * contact's alpha-beta-gamma {@link Track}. Tracks on contacts that leave range go stale and are
 * dropped after {@code trackTimeout}; tracks on contacts that leave the engine are dropped at once.
 *
 * Runs from {@link PhysicsManager.StepListener#beforeStep}; add it before weapon control and
 * missile guidance so they read this tick's tracks.
 */
public class SensorTrackingSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family SENSORS = Family.all(SensorComponent.class).get();
    private static final Family CONTACTS = Family.all(SignatureComponent.class).get();
    /** Ranges below this get the noise of this range, meters. */
    private static final float MIN_NOISE_RANGE = 100f;

    private final ComponentMapper<SensorComponent> sensorMapper = ComponentMapper.getFor(SensorComponent.class);

    private final PhysicsManager physics;
    private final RandomXS128 rng;
    private ImmutableArray<Entity> sensors;
    private ImmutableArray<Entity> contacts;
    private final ObjectSet<Entity> live = new ObjectSet<>();
    private final Listener<WorldShiftEvent> shiftListener = this::onWorldShift;
    private float clock;

    private final Vector2 ownPos = new Vector2();
    private final Vector2 ownVel = new Vector2();
    private final Vector2 pos = new Vector2();
    private final Vector2 vel = new Vector2();

    public SensorTrackingSystem(PhysicsManager physics, long seed) {
        this.physics = physics;
        this.rng = new RandomXS128(seed);
    }

    /** Seconds of fixed-tick time run; tracks' {@code lastFixTime} is on this clock. */
    public float getClock() {
        return clock;
    }

    /** Seconds since the track's last fix, for {@link Track#positionAt}. */
    public float ageOf(Track track) {
        return clock - track.lastFixTime;
    }

    @Override
    public void addedToEngine(Engine engine) {
        sensors = engine.getEntitiesFor(SENSORS);
        contacts = engine.getEntitiesFor(CONTACTS);
        physics.addStepListener(this);
        physics.worldShifts.add(shiftListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        physics.worldShifts.remove(shiftListener);
        sensors = null;
        contacts = null;
    }

    @Override
    public void update(float deltaTime) {
        // Work happens on the physics tick; see beforeStep.
    }

    @Override
    public void beforeStep(float dt) {
        clock += dt;
        live.clear();
        for (int i = 0; i < contacts.size(); i++) {
            Entity c = contacts.get(i);
            if (!c.isScheduledForRemoval()) live.add(c);
        }
        for (int i = 0; i < sensors.size(); i++) {
            Entity ship = sensors.get(i);
            SensorComponent sensor = sensorMapper.get(ship);
            dropDead(sensor);
            if (ship.isScheduledForRemoval() || !ContactState.read(ship, ownPos, ownVel)) continue;
            sensor.fixTimer -= dt;
            if (sensor.fixTimer > 0f) continue;
            sensor.fixTimer += sensor.fixInterval;
            if (sensor.fixTimer < 0f) sensor.fixTimer = 0f;
            takeFixes(ship, sensor);
        }
    }

    private void dropDead(SensorComponent sensor) {
        if (sensor.lockedTarget != null && !live.contains(sensor.lockedTarget)) sensor.lockedTarget = null;
        for (int t = sensor.tracks.size - 1; t >= 0; t--) {
            Track track = sensor.tracks.get(t);
            if (!live.contains(track.target) || clock - track.lastFixTime > sensor.trackTimeout) {
                sensor.tracks.removeIndex(t);
            }
        }
    }

    private void takeFixes(Entity ship, SensorComponent sensor) {
        float range2 = sensor.range * sensor.range;
        for (int i = 0; i < contacts.size(); i++) {
            Entity c = contacts.get(i);
            if (c == ship || c.isScheduledForRemoval() || !ContactState.read(c, pos, vel)) continue;
            float d2 = pos.dst2(ownPos);
            if (d2 > range2) continue;
            float range = Math.max((float) Math.sqrt(d2), MIN_NOISE_RANGE);
            float sigma = sensor.sigmaPerKm * range / 1000f;
            float zx = pos.x + (float) rng.nextGaussian() * sigma;
            float zy = pos.y + (float) rng.nextGaussian() * sigma;

            Track track = sensor.trackFor(c);
            if (track == null) {
                track = new Track();
                track.target = c;
                track.setSkill(sensor.trackingSkill);
                sensor.tracks.add(track);
            }
            float since = track.fixes == 0 ? sensor.fixInterval : clock - track.lastFixTime;
            track.update(zx, zy, Math.max(since, 1e-3f), clock, sigma);
        }
    }

    /** Tracks are in the bubble frame, so they move with the floating origin. */
    private void onWorldShift(Signal<WorldShiftEvent> signal, WorldShiftEvent shift) {
        if (sensors == null) return;
        for (int i = 0; i < sensors.size(); i++) {
            SensorComponent sensor = sensorMapper.get(sensors.get(i));
            for (int t = 0; t < sensor.tracks.size; t++) {
                Track track = sensor.tracks.get(t);
                track.p.sub(shift.deltaX, shift.deltaY);
                track.v.sub(shift.deltaVx, shift.deltaVy);
            }
        }
    }
}
