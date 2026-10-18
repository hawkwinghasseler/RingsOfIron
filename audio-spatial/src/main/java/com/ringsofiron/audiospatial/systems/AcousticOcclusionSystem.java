package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.audiospatial.GridOcclusion;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Works out how muffled each emitter is (the "muffling" pass). Run it before
 * {@link AudioUpdateSystem}, which hands the result to the sound engine.
 *
 * <ul>
 *   <li>Listener inside the emitter's ship: occlusion grows with the walls between them
 *       ({@link GridOcclusion}), full at {@link #maxWalls}.</li>
 *   <li>Listener outside, or aboard another ship: interior sounds are clamped to full occlusion.</li>
 *   <li>Exterior emitters are never occluded: hull impacts and weapon fire carry through the
 *       ship's frame ("Spacer Ears").</li>
 * </ul>
 *
 * Interior sound also needs air: the emitter's {@link AudioEmitterComponent#pressure} is the
 * lower of the air pressure where it is and where the listener is (when listening inside),
 * so a fire in a vented room goes quiet.
 */
public class AcousticOcclusionSystem extends EntitySystem {

    public static final int DEFAULT_PRIORITY = 900;

    private final ComponentMapper<AudioEmitterComponent> emitters = ComponentMapper.getFor(AudioEmitterComponent.class);
    private final ComponentMapper<AudioListenerComponent> listeners = ComponentMapper.getFor(AudioListenerComponent.class);
    private final ComponentMapper<ShipGridComponent> grids = ComponentMapper.getFor(ShipGridComponent.class);
    private ImmutableArray<Entity> emitterEntities;
    private ImmutableArray<Entity> listenerEntities;

    /** Wall count at which a sound is fully muffled. */
    public int maxWalls = 4;

    public AcousticOcclusionSystem() {
        super(DEFAULT_PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        emitterEntities = engine.getEntitiesFor(Family.all(AudioEmitterComponent.class).get());
        listenerEntities = engine.getEntitiesFor(Family.all(AudioListenerComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        AudioListenerComponent listener = listenerEntities.size() > 0 ? listeners.get(listenerEntities.first()) : null;
        for (Entity e : emitterEntities) {
            compute(emitters.get(e), listener);
        }
    }

    private void compute(AudioEmitterComponent emitter, AudioListenerComponent listener) {
        if (emitter.placement == AudioEmitterComponent.Placement.EXTERIOR) {
            emitter.wallCount = 0;
            emitter.occlusion = 0f;
            emitter.pressure = 1f;
            return;
        }
        ShipGridComponent grid = emitter.ship != null ? grids.get(emitter.ship) : null;
        if (grid == null) {
            // No grid to trace: treat it as an open room
            emitter.wallCount = 0;
            emitter.occlusion = 0f;
            emitter.pressure = 1f;
            return;
        }
        int[] et = grid.localToTile(emitter.localX, emitter.localY);
        float pressure = emitterPressure(grid, et[0], et[1]);

        boolean listenerAboard = listener != null
                && listener.mode == AudioListenerComponent.Mode.INTERIOR
                && listener.ship == emitter.ship;
        int[] lt = listenerAboard ? grid.localToTile(listener.localX, listener.localY) : null;
        if (!listenerAboard || et[0] < 0 || lt[0] < 0) {
            emitter.wallCount = maxWalls;
            emitter.occlusion = 1f;
            emitter.pressure = pressure;
            return;
        }
        int walls = GridOcclusion.countWalls(grid, et[0], et[1], lt[0], lt[1]);
        emitter.wallCount = walls;
        emitter.occlusion = Math.min(walls, maxWalls) / (float) maxWalls;
        emitter.pressure = Math.min(pressure, tilePressure(grid, lt[0], lt[1]));
    }

    /** Pressure ratio at a tile, or 1 if compartments have not been built for this grid yet. */
    private static float tilePressure(ShipGridComponent grid, int x, int y) {
        if (grid.compartments.size == 0) return 1f;
        return grid.pressureRatioAt(x, y);
    }

    /**
     * Pressure at the emitter. A sound mounted in a wall tile (a pipe, a conduit) has no air of
     * its own and uses the best-pressurised neighbouring tile.
     */
    private static float emitterPressure(ShipGridComponent grid, int x, int y) {
        if (x < 0 || grid.compartments.size == 0) return 1f;
        if (grid.compartmentAt(x, y) != null) return grid.pressureRatioAt(x, y);
        float best = 0f;
        best = Math.max(best, grid.pressureRatioAt(x + 1, y));
        best = Math.max(best, grid.pressureRatioAt(x - 1, y));
        best = Math.max(best, grid.pressureRatioAt(x, y + 1));
        best = Math.max(best, grid.pressureRatioAt(x, y - 1));
        return best;
    }
}
