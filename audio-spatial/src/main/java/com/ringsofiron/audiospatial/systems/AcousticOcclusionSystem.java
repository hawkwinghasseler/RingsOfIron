package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.audiospatial.SoundField;
import com.ringsofiron.audiospatial.SoundPropagation;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Works out how loud and how muffled each emitter is through the ship around it. Run it before
 * {@link AudioUpdateSystem}, which hands the result to the sound engine.
 *
 * <ul>
 *   <li>Listener inside the emitter's ship: the direct line and the route around through the
 *       rooms are combined ({@link SoundPropagation}). Walls on the direct line cut it hard;
 *       the room route loses a little at each open door and more at each wall or sealed door.</li>
 *   <li>Listener outside, or aboard another ship: interior sounds are clamped to full occlusion
 *       and {@link #exteriorTransmission}.</li>
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

    private final AudioEventCatalog catalog;
    private final ComponentMapper<AudioEmitterComponent> emitters = ComponentMapper.getFor(AudioEmitterComponent.class);
    private final ComponentMapper<AudioListenerComponent> listeners = ComponentMapper.getFor(AudioListenerComponent.class);
    private final ComponentMapper<ShipGridComponent> grids = ComponentMapper.getFor(ShipGridComponent.class);
    private ImmutableArray<Entity> emitterEntities;
    private ImmutableArray<Entity> listenerEntities;
    /** Layout signature of each grid, taken once per update. */
    private final ObjectIntMap<ShipGridComponent> signatures = new ObjectIntMap<>();
    private final SoundPropagation.Result result = new SoundPropagation.Result();
    private final float[] beam = new float[2];

    /** Volume of an interior sound heard from outside the ship, through the hull. */
    public float exteriorTransmission = 0.15f;

    public AcousticOcclusionSystem() {
        this(AudioEventCatalog.withDefaults());
    }

    /** @param catalog distances for each event, so the room route's length can be rolled off */
    public AcousticOcclusionSystem(AudioEventCatalog catalog) {
        super(DEFAULT_PRIORITY);
        this.catalog = catalog;
    }

    @Override
    public void addedToEngine(Engine engine) {
        emitterEntities = engine.getEntitiesFor(Family.all(AudioEmitterComponent.class).get());
        listenerEntities = engine.getEntitiesFor(Family.all(AudioListenerComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        signatures.clear();
        AudioListenerComponent listener = listenerEntities.size() > 0 ? listeners.get(listenerEntities.first()) : null;
        for (Entity e : emitterEntities) {
            compute(emitters.get(e), listener);
        }
    }

    private static void clear(AudioEmitterComponent emitter) {
        emitter.wallCount = 0;
        emitter.occlusion = 0f;
        emitter.transmission = 1f;
        emitter.roomLossDb = 0f;
        emitter.pressure = 1f;
    }

    private void compute(AudioEmitterComponent emitter, AudioListenerComponent listener) {
        if (emitter.placement == AudioEmitterComponent.Placement.EXTERIOR) {
            clear(emitter);
            return;
        }
        ShipGridComponent grid = emitter.ship != null ? grids.get(emitter.ship) : null;
        if (grid == null) {
            clear(emitter); // no grid to trace: treat it as an open room
            return;
        }
        int[] et = grid.localToTile(emitter.localX, emitter.localY);
        float pressure = emitterPressure(grid, et[0], et[1]);

        boolean listenerAboard = listener != null
                && listener.mode == AudioListenerComponent.Mode.INTERIOR
                && listener.ship == emitter.ship;
        int[] lt = listenerAboard ? grid.localToTile(listener.localX, listener.localY) : null;
        if (!listenerAboard || et[0] < 0 || lt[0] < 0) {
            emitter.wallCount = SoundPropagation.FULL_MUFFLE_WALLS;
            emitter.occlusion = 1f;
            emitter.transmission = exteriorTransmission;
            emitter.roomLossDb = 0f;
            emitter.pressure = pressure;
            return;
        }
        int walls = SoundPropagation.traceBeam(grid, emitter.localX, emitter.localY, listener.localX, listener.localY, beam);

        int signature = signatures.get(grid, Integer.MIN_VALUE);
        if (signature == Integer.MIN_VALUE) {
            signature = SoundField.layoutSignature(grid);
            signatures.put(grid, signature);
        }
        SoundField field = emitter.soundField;
        if (field == null || !field.matches(et[0], et[1], signature)) {
            field = SoundField.build(grid, et[0], et[1], signature);
            emitter.soundField = field;
        }

        float dx = listener.localX - emitter.localX, dy = listener.localY - emitter.localY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        SoundPropagation.combine(catalog.get(emitter.eventPath), distance, beam, walls, field, lt[0], lt[1], result);
        emitter.wallCount = walls;
        emitter.occlusion = result.occlusion;
        emitter.transmission = result.transmission;
        emitter.roomLossDb = field.reaches(lt[0], lt[1]) ? field.lossDb(lt[0], lt[1]) : Float.POSITIVE_INFINITY;
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
