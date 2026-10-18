package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventInstance;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Keeps the sound engine in step with the world each frame: places the listener, starts
 * emitters, moves their instances to their entities, passes on occlusion and pressure, and
 * advances the backend. Stops and frees an emitter's sound when its entity or component goes.
 *
 * Positions are world (bubble) meters. In {@link AudioListenerComponent.Mode#EXTERIOR} the
 * listener is at its own entity's transform (the ship's center); in INTERIOR it is at the
 * player character's ship-local spot carried through the ship's transform.
 *
 * Hook {@link #worldShiftListener()} to physics-exterior's {@code worldShifts} signal so a
 * floating-origin rebase does not register as a burst of speed.
 */
public class AudioUpdateSystem extends EntitySystem implements EntityListener {

    public static final int DEFAULT_PRIORITY = 1000;

    private final AudioManager audio;
    private final ComponentMapper<AudioEmitterComponent> emitters = ComponentMapper.getFor(AudioEmitterComponent.class);
    private final ComponentMapper<AudioListenerComponent> listeners = ComponentMapper.getFor(AudioListenerComponent.class);
    private final ComponentMapper<TransformComponent> transforms = ComponentMapper.getFor(TransformComponent.class);
    private final Family emitterFamily = Family.all(AudioEmitterComponent.class).get();
    private ImmutableArray<Entity> emitterEntities;
    private ImmutableArray<Entity> listenerEntities;
    private Engine engine;
    private final Array<Entity> finished = new Array<>();
    private final Vector2 tmp = new Vector2();

    private boolean listenerHasPrevious;
    private float listenerPrevX, listenerPrevY;

    public AudioUpdateSystem(AudioManager audio) {
        super(DEFAULT_PRIORITY);
        this.audio = audio;
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        emitterEntities = engine.getEntitiesFor(emitterFamily);
        listenerEntities = engine.getEntitiesFor(Family.all(AudioListenerComponent.class).get());
        engine.addEntityListener(emitterFamily, this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(this);
        for (Entity e : emitterEntities) releaseInstance(emitters.get(e));
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        updateListener(deltaTime);
        for (Entity e : emitterEntities) updateEmitter(e, emitters.get(e), deltaTime);
        for (Entity e : finished) engine.removeEntity(e);
        finished.clear();
        audio.update(deltaTime);
    }

    private void updateListener(float dt) {
        if (listenerEntities.size() == 0) return;
        Entity entity = listenerEntities.first();
        AudioListenerComponent l = listeners.get(entity);
        if (l.mode == AudioListenerComponent.Mode.INTERIOR && l.ship != null) {
            toWorld(transforms.get(l.ship), l.localX, l.localY, tmp);
        } else {
            TransformComponent t = transforms.get(entity);
            if (t == null && l.ship != null) t = transforms.get(l.ship);
            if (t != null) tmp.set(t.x, t.y);
            else tmp.set(0f, 0f);
        }
        l.worldX = tmp.x;
        l.worldY = tmp.y;
        float vx = 0f, vy = 0f;
        if (listenerHasPrevious && dt > 0f) {
            vx = (tmp.x - listenerPrevX) / dt;
            vy = (tmp.y - listenerPrevY) / dt;
        }
        listenerPrevX = tmp.x;
        listenerPrevY = tmp.y;
        listenerHasPrevious = true;
        audio.getBackend().setListener(tmp.x, tmp.y, vx, vy);
    }

    private void updateEmitter(Entity entity, AudioEmitterComponent em, float dt) {
        if (em.placement == AudioEmitterComponent.Placement.INTERIOR) {
            toWorld(em.ship != null ? transforms.get(em.ship) : null, em.localX, em.localY, tmp);
        } else {
            TransformComponent t = transforms.get(entity);
            if (t != null) tmp.set(t.x, t.y);
            else tmp.set(em.worldX, em.worldY); // no transform: keep wherever it was put
        }
        em.worldX = tmp.x;
        em.worldY = tmp.y;
        float vx = 0f, vy = 0f;
        if (em.hasPrevious && dt > 0f) {
            vx = (tmp.x - em.prevX) / dt;
            vy = (tmp.y - em.prevY) / dt;
        }
        em.prevX = tmp.x;
        em.prevY = tmp.y;
        em.hasPrevious = true;

        if (em.instance == null) {
            if (!em.autoStart || em.eventPath == null) return;
            em.instance = audio.createInstance(em.eventPath);
            em.started = false;
        }
        AudioEventInstance inst = em.instance;
        inst.setPosition(tmp.x, tmp.y);
        inst.setVelocity(vx, vy);
        inst.setParameter(AudioParameters.OCCLUSION, em.occlusion);
        inst.setParameter(AudioParameters.PRESSURE, em.pressure);
        for (ObjectFloatMap.Entry<String> p : em.parameters) inst.setParameter(p.key, p.value);

        if (!em.started && em.autoStart) {
            inst.start();
            em.started = true;
        } else if (em.started && !inst.isPlaying() && em.removeEntityWhenDone) {
            finished.add(entity);
        }
    }

    private static void toWorld(TransformComponent ship, float localX, float localY, Vector2 out) {
        if (ship == null) {
            out.set(localX, localY);
            return;
        }
        float c = MathUtils.cosDeg(ship.rotation);
        float s = MathUtils.sinDeg(ship.rotation);
        out.set(ship.x + localX * c - localY * s, ship.y + localX * s + localY * c);
    }

    /** Moves remembered positions with a floating-origin rebase of (deltaX, deltaY). */
    public void onWorldShift(float deltaX, float deltaY) {
        listenerPrevX -= deltaX;
        listenerPrevY -= deltaY;
        if (emitterEntities == null) return;
        for (Entity e : emitterEntities) {
            AudioEmitterComponent em = emitters.get(e);
            em.prevX -= deltaX;
            em.prevY -= deltaY;
            if (em.placement == AudioEmitterComponent.Placement.EXTERIOR && transforms.get(e) == null) {
                em.worldX -= deltaX;
                em.worldY -= deltaY;
            }
        }
    }

    /** A listener for {@code PhysicsManager.worldShifts}. */
    public Listener<WorldShiftEvent> worldShiftListener() {
        return new Listener<WorldShiftEvent>() {
            @Override
            public void receive(Signal<WorldShiftEvent> signal, WorldShiftEvent event) {
                onWorldShift(event.deltaX, event.deltaY);
            }
        };
    }

    @Override
    public void entityAdded(Entity entity) {}

    @Override
    public void entityRemoved(Entity entity) {
        AudioEmitterComponent em = emitters.get(entity);
        if (em != null) releaseInstance(em);
    }

    private static void releaseInstance(AudioEmitterComponent em) {
        if (em.instance == null) return;
        em.instance.stop();
        em.instance.release();
        em.instance = null;
        em.started = false;
    }
}
