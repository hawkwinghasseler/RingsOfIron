package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Saves the bubble without depending on where the floating origin happened to be: the anchor's
 * true state, and each bubble body's true state (doubles, relative to the parent body), angle and
 * spin, keyed by entity UUID.
 *
 * Box2D bodies themselves are not saved: {@link PhysicsBodyComponent} and
 * {@link KinematicTransientComponent} are skipped, and whoever builds a ship or rock (its
 * assembler) recreates the body after load. When a body appears on an entity with a saved state,
 * the state is applied to it, whether the body exists before this participant is read or later.
 *
 * The anchor's motion model is not saved: the game sets the model for the current parent body
 * (e.g. a Kepler orbit) and this participant restarts it from the saved state at the saved tick.
 */
public class PhysicsSaveParticipant implements SaveParticipant {
    public static final String SAVE_KEY = "physics-exterior";

    private static final Family BODIES = Family.all(PhysicsBodyComponent.class, UuidComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final PhysicsManager physics;
    private final GameEngine game;
    /** Saved states for bodies not built yet: uuid -> {x, y, vx, vy, angle, spin}. */
    private final ObjectMap<String, double[]> pending = new ObjectMap<>();
    private final double[] state = new double[4];

    /** Creates the participant, registers it with the game's serializer and skips the live physics components. */
    public static PhysicsSaveParticipant install(PhysicsManager physics, GameEngine game) {
        PhysicsSaveParticipant participant = new PhysicsSaveParticipant(physics, game);
        SerializationService serializer = game.getSerializationService();
        serializer.skipComponent(PhysicsBodyComponent.class);
        serializer.skipComponent(KinematicTransientComponent.class);
        serializer.addParticipant(participant);
        game.getEcsEngine().addEntityListener(BODIES, participant.new BodyAppeared());
        return participant;
    }

    private PhysicsSaveParticipant(PhysicsManager physics, GameEngine game) {
        this.physics = physics;
        this.game = game;
    }

    @Override
    public String saveKey() {
        return SAVE_KEY;
    }

    @Override
    public void write(Json json) {
        json.writeArrayStart("anchor");
        json.writeValue(physics.getOriginX());
        json.writeValue(physics.getOriginY());
        json.writeValue(physics.getOriginVx());
        json.writeValue(physics.getOriginVy());
        json.writeArrayEnd();

        json.writeObjectStart("bodies");
        for (Entity entity : game.getEcsEngine().getEntitiesFor(BODIES)) {
            Body body = bodyMapper.get(entity).body;
            if (body == null) continue;
            physics.getTrueState(body, state);
            json.writeArrayStart(uuidMapper.get(entity).id);
            for (double v : state) json.writeValue(v);
            json.writeValue((double) body.getAngle());
            json.writeValue((double) body.getAngularVelocity());
            json.writeArrayEnd();
        }
        json.writeObjectEnd();
    }

    @Override
    public void read(Json json, JsonValue data) {
        double[] anchor = data.get("anchor").asDoubleArray();
        physics.getAnchorModel().reset(anchor[0], anchor[1], anchor[2], anchor[3], physics.getSimSeconds());
        physics.syncToClock();

        pending.clear();
        JsonValue bodies = data.get("bodies");
        if (bodies == null) return;
        for (JsonValue entry = bodies.child; entry != null; entry = entry.next) {
            double[] saved = entry.asDoubleArray();
            Entity entity = game.getEntity(entry.name);
            Body body = entity == null ? null : bodyMapper.get(entity) == null ? null : bodyMapper.get(entity).body;
            if (body != null) apply(body, saved);
            else pending.put(entry.name, saved);
        }
    }

    /** Saved states still waiting for their entity's body to be built. */
    public int getPendingCount() {
        return pending.size;
    }

    private void apply(Body body, double[] saved) {
        physics.setTrueState(body, saved[0], saved[1], saved[2], saved[3]);
        Vector2 p = body.getPosition();
        body.setTransform(p.x, p.y, (float) saved[4]);
        body.setAngularVelocity((float) saved[5]);
    }

    private class BodyAppeared implements EntityListener {
        @Override
        public void entityAdded(Entity entity) {
            if (pending.size == 0) return;
            Body body = bodyMapper.get(entity).body;
            if (body == null) return;
            double[] saved = pending.remove(uuidMapper.get(entity).id);
            if (saved != null) apply(body, saved);
        }

        @Override
        public void entityRemoved(Entity entity) {
        }
    }
}
