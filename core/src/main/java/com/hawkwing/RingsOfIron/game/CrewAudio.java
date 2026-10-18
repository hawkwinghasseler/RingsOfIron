package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.audiospatial.AudioEmitters;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * The crew as sounds aboard their ship: a footstep every stride they walk, and a voice when one
 * takes a new job or is knocked down. Each is a one-shot at the crew member's spot, so walls muffle
 * it and vacuum silences it like any interior sound. Runs every frame just before the audio systems.
 */
public class CrewAudio extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 890;
    /** Meters walked per footstep. */
    static final float STRIDE = 0.75f;
    /** Seconds a crew member stays quiet after calling out. */
    static final float VOICE_COOLDOWN = 3f;

    private static final Family CREW = Family.all(CrewMemberComponent.class, GridPositionComponent.class).get();

    private final ComponentMapper<CrewMemberComponent> members = ComponentMapper.getFor(CrewMemberComponent.class);
    private final ComponentMapper<GridPositionComponent> positions = ComponentMapper.getFor(GridPositionComponent.class);
    private final ComponentMapper<ShipGridComponent> grids = ComponentMapper.getFor(ShipGridComponent.class);
    private final ObjectMap<Entity, Heard> heard = new ObjectMap<>();
    private final Vector2 local = new Vector2();
    private ImmutableArray<Entity> crew;
    private int footsteps, voices;

    private static final class Heard {
        Entity ship;
        float x, y;
        float walked;
        float voiceCooldown;
        CrewTask task;
        CrewState state;
    }

    private final EntityListener forget = new EntityListener() {
        @Override public void entityAdded(Entity entity) {}
        @Override public void entityRemoved(Entity entity) {
            heard.remove(entity);
        }
    };

    public CrewAudio() {
        super(Math.min(DEFAULT_PRIORITY, AudioUpdateSystem.DEFAULT_PRIORITY - 1));
    }

    /** Footsteps and callouts played so far, for tests. */
    public int footstepsPlayed() {
        return footsteps;
    }

    public int voicesPlayed() {
        return voices;
    }

    @Override
    public void addedToEngine(Engine engine) {
        crew = engine.getEntitiesFor(CREW);
        engine.addEntityListener(CREW, forget);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(forget);
        heard.clear();
    }

    @Override
    public void update(float deltaTime) {
        for (Entity e : crew) {
            CrewMemberComponent m = members.get(e);
            GridPositionComponent p = positions.get(e);
            ShipGridComponent grid = p.ship == null ? null : grids.get(p.ship);
            if (grid == null) continue;
            Heard h = heard.get(e);
            if (h == null || h.ship != p.ship) {
                // Newly aboard (spawned, loaded or moved ships): start counting from here, quietly.
                h = h == null ? new Heard() : h;
                h.ship = p.ship;
                h.x = p.x;
                h.y = p.y;
                h.walked = 0f;
                h.task = m.task;
                h.state = m.state;
                heard.put(e, h);
                continue;
            }

            float moved = Vector2.dst(h.x, h.y, p.x, p.y) * grid.tileSize;
            h.x = p.x;
            h.y = p.y;
            // Carried casualties make no footsteps of their own.
            if (m.isConscious() && m.carriedBy == null) {
                h.walked += moved;
                if (h.walked >= STRIDE) {
                    // One step per frame at most; a fast-forwarded frame doesn't turn into a drum roll.
                    h.walked %= STRIDE;
                    play(AudioEventCatalog.FOOTSTEP, p, grid);
                    footsteps++;
                }
            } else {
                h.walked = 0f;
            }

            h.voiceCooldown = Math.max(0f, h.voiceCooldown - deltaTime);
            boolean knockedDown = h.state == CrewState.CONSCIOUS && m.state == CrewState.UNCONSCIOUS;
            boolean tookJob = m.task != null && m.task != h.task && m.isConscious();
            h.task = m.task;
            h.state = m.state;
            if ((knockedDown || tookJob) && h.voiceCooldown <= 0f) {
                play(AudioEventCatalog.VOICE, p, grid);
                h.voiceCooldown = VOICE_COOLDOWN;
                voices++;
            }
        }
    }

    private void play(String event, GridPositionComponent p, ShipGridComponent grid) {
        p.toShipLocal(grid, local);
        AudioEmitters.spawnInteriorOneShot(getEngine(), event, p.ship, local.x, local.y, 1f);
    }
}
