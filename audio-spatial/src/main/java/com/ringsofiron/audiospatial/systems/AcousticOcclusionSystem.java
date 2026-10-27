package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.audiospatial.SoundField;
import com.ringsofiron.audiospatial.SoundPropagation;
import com.ringsofiron.audiospatial.backend.AcousticModel;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.SoundEventDef;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Works out how loud and how muffled each emitter is through the ship around it. Run it before
 * {@link AudioUpdateSystem}, which hands the result to the sound engine.
 *
 * <ul>
 *   <li>Listener inside the emitter's ship: the direct line and the route around through the
 *       rooms are combined ({@link SoundPropagation}). Walls on the direct line cut it hard;
 *       the room route loses a little at each open door and more at each wall or sealed door.</li>
 *   <li>Listener aboard a craft docked to the emitter's: the route through the rooms of both,
 *       across the dock: from the emitter to a docked port's opening, through both port doors
 *       (open or sealed, see {@link SoundField#edgeLossDb}) and the collars, and on through the
 *       listener's craft. The best such route is the one heard, as a room path; there is no
 *       direct line between two craft. With no route (hull or vacuum between), it is as from
 *       outside.</li>
 *   <li>Listener outside, or aboard another craft docked to it with no route through: interior
 *       sounds are clamped to full occlusion and {@link #exteriorTransmission}.</li>
 *   <li>Emitter aboard a craft that is neither the listener's nor docked to it (directly or
 *       through other docked craft): silent. Nothing carries through vacuum from another ship's
 *       insides, so its alarms and fires are not heard until the two are docked.</li>
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
    /** Fields from the tiles where sound comes aboard across a dock, per grid. */
    private final ObjectMap<ShipGridComponent, IntMap<SoundField>> farFields = new ObjectMap<>();
    /** The listener's craft and every craft docked to it, directly or through others, taken once per update. */
    private final ObjectSet<ShipGridComponent> joined = new ObjectSet<>();
    private final Array<ShipGridComponent> frontier = new Array<>();

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
        if (farFields.size > 16) farFields.clear(); // grids come and go with docking and loads
        AudioListenerComponent listener = listenerEntities.size() > 0 ? listeners.get(listenerEntities.first()) : null;
        findJoined(listener);
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
        if (listener != null && !joined.contains(grid)) {
            // Inside another ship, not docked to ours: nothing carries across the vacuum
            emitter.wallCount = SoundPropagation.FULL_MUFFLE_WALLS;
            emitter.occlusion = 1f;
            emitter.transmission = 0f;
            emitter.roomLossDb = Float.POSITIVE_INFINITY;
            emitter.pressure = pressure;
            return;
        }

        if (listener != null && listener.mode == AudioListenerComponent.Mode.INTERIOR
                && listener.ship != emitter.ship && et[0] >= 0) {
            ShipGridComponent there = listener.ship != null ? grids.get(listener.ship) : null;
            if (there != null && across(emitter, grid, et, pressure, there, listener)) return;
        }
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

        int signature = signature(grid);
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

    /** Fills {@link #joined} with the listener's craft's grid and every grid docked to it, through any chain of links. */
    private void findJoined(AudioListenerComponent listener) {
        joined.clear();
        ShipGridComponent start = listener != null && listener.ship != null ? grids.get(listener.ship) : null;
        if (start == null) return;
        joined.add(start);
        frontier.clear();
        frontier.add(start);
        while (frontier.size > 0) {
            ShipGridComponent g = frontier.pop();
            for (int i = 0; i < g.links.size; i++) {
                ShipGridComponent other = g.links.get(i).other;
                if (other != null && joined.add(other)) frontier.add(other);
            }
        }
    }

    /**
     * Hears an emitter on {@code grid} from a listener aboard {@code there}, docked to it: the best
     * route through the rooms out of the emitter's craft, across a link, and on to the listener.
     *
     * @return false if the craft are not joined or no route reaches the listener
     */
    private boolean across(AudioEmitterComponent emitter, ShipGridComponent grid, int[] et, float pressure,
                           ShipGridComponent there, AudioListenerComponent listener) {
        int[] lt = there.localToTile(listener.localX, listener.localY);
        if (lt[0] < 0) return false;
        SoundField field = emitter.soundField;
        int signature = signature(grid);
        if (field == null || !field.matches(et[0], et[1], signature)) {
            field = SoundField.build(grid, et[0], et[1], signature);
            emitter.soundField = field;
        }
        float bestKey = Float.POSITIVE_INFINITY, bestLoss = 0f, bestMuffle = 0f, bestLength = 0f;
        float[] muffle = new float[1];
        for (int i = 0; i < grid.links.size; i++) {
            GridLink l = grid.links.get(i);
            if (l.other != there || !field.reaches(l.x, l.y)) continue;
            Tile a = grid.getTile(l.x, l.y), b = there.getTile(l.ox, l.oy);
            if (a == null || b == null || !a.holdsAir() || !b.holdsAir()) continue;
            float doorLoss = SoundField.edgeLossDb(l.edge(), muffle), doorMuffle = muffle[0];
            doorLoss += SoundField.edgeLossDb(l.mate.edge(), muffle);
            doorMuffle += muffle[0];
            SoundField far = farField(there, l.ox, l.oy);
            if (!far.reaches(lt[0], lt[1])) continue;
            float loss = field.lossDb(l.x, l.y) + doorLoss + far.lossDb(lt[0], lt[1]);
            float length = field.length(l.x, l.y) + l.gap * grid.tileSize + grid.tileSize + far.length(lt[0], lt[1]);
            float key = loss + SoundField.DB_PER_METER * length;
            if (key < bestKey) {
                bestKey = key;
                bestLoss = loss;
                bestMuffle = field.muffleDb(l.x, l.y) + doorMuffle + far.muffleDb(lt[0], lt[1]);
                bestLength = length;
            }
        }
        if (bestKey == Float.POSITIVE_INFINITY) return false;
        SoundEventDef def = catalog.get(emitter.eventPath);
        float openGain = AcousticModel.distanceGain(bestLength, def.minDistance, def.maxDistance);
        float room = SoundPropagation.ROOM_PATH_SCALE * openGain * SoundPropagation.dbToGain(bestLoss);
        emitter.wallCount = SoundPropagation.FULL_MUFFLE_WALLS;
        emitter.occlusion = MathUtils.clamp(bestMuffle / SoundPropagation.FULL_MUFFLE_DB, 0f, 1f);
        emitter.transmission = openGain > 0f ? room / openGain : 0f;
        emitter.roomLossDb = bestLoss;
        emitter.pressure = Math.min(pressure, tilePressure(there, lt[0], lt[1]));
        return true;
    }

    /** The field from tile (x, y) of a docked craft, where sound comes in across a link; cached per grid and tile. */
    private SoundField farField(ShipGridComponent grid, int x, int y) {
        int signature = signature(grid);
        IntMap<SoundField> fields = farFields.get(grid);
        if (fields == null) farFields.put(grid, fields = new IntMap<>());
        int key = y * grid.width + x;
        SoundField f = fields.get(key);
        if (f == null || !f.matches(x, y, signature)) {
            f = SoundField.build(grid, x, y, signature);
            fields.put(key, f);
        }
        return f;
    }

    private int signature(ShipGridComponent grid) {
        int signature = signatures.get(grid, Integer.MIN_VALUE);
        if (signature == Integer.MIN_VALUE) {
            signature = SoundField.layoutSignature(grid);
            signatures.put(grid, signature);
        }
        return signature;
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
