package com.ringsofiron.audiospatial.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.audiospatial.AudioEmitters;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Gives every ship's interior hazards a sound, read from its {@link ShipGridComponent}
 * after each hazard tick:
 *
 * <ul>
 *   <li>{@link AudioEventCatalog#FIRE}: one loop per burning compartment, at the middle of
 *       its flames, louder as more of it burns.</li>
 *   <li>{@link AudioEventCatalog#BREACH_HISS}: one loop per opening losing air to space,
 *       louder with the flow through it.</li>
 *   <li>{@link AudioEventCatalog#ALARM}: one loop per compartment that is on fire, venting,
 *       or short of air or oxygen, at the middle of the room.</li>
 *   <li>{@link AudioEventCatalog#DOOR}: a one-shot whenever a door opens or closes.</li>
 * </ul>
 *
 * The emitters are entities of their own, marked {@link TransientComponent} so they are
 * never saved; a loaded game rebuilds them on its first tick.
 */
public class InteriorHazardAudioSystem extends FixedLogicSystem {

    /** After the interior simulation, before the audio systems place and muffle sounds. */
    public static final int DEFAULT_PRIORITY = GridHazardSystem.DEFAULT_PRIORITY + 10;
    /** Burning fire intensity, summed over a room's tiles, that plays at full loudness. */
    public static final float FULL_FIRE = 8f;
    /** Air lost through one opening, mol/s, below which it makes no sound. */
    public static final float MIN_HISS_FLOW = 0.5f;
    /** Air lost through one opening, mol/s, that hisses at full loudness. */
    public static final float FULL_HISS_FLOW = 150f;
    /** Pressure, as a share of standard, below which the alarm sounds. */
    public static final float ALARM_PRESSURE = 0.7f;
    /** Oxygen partial pressure, as a share of standard, below which the alarm sounds. */
    public static final float ALARM_OXYGEN = 0.75f;

    private static final Family FAMILY = Family.all(ShipGridComponent.class).get();

    private final IdentityMap<Entity, ShipSounds> ships = new IdentityMap<>();
    private final Array<Entity> gone = new Array<>();
    private ImmutableArray<Entity> entities;
    private Engine engine;

    public InteriorHazardAudioSystem() {
        super(DEFAULT_PRIORITY, GridHazardSystem.HAZARD_TICK);
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        for (ShipSounds s : ships.values()) s.clear(engine);
        ships.clear();
        entities = null;
        this.engine = null;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        if (entities == null) return;
        for (int i = 0; i < entities.size(); i++) {
            Entity ship = entities.get(i);
            ShipSounds sounds = ships.get(ship);
            if (sounds == null) ships.put(ship, sounds = new ShipSounds());
            sounds.update(engine, ship, ship.getComponent(ShipGridComponent.class));
        }
        // Ships that left the engine take their sounds with them
        gone.clear();
        for (ObjectMap.Entry<Entity, ShipSounds> e : ships) {
            if (!e.key.isScheduledForRemoval() && e.key.getComponent(ShipGridComponent.class) != null && contains(e.key)) continue;
            e.value.clear(engine);
            gone.add(e.key);
        }
        for (Entity e : gone) ships.remove(e);
    }

    private boolean contains(Entity ship) {
        for (int i = 0; i < entities.size(); i++) if (entities.get(i) == ship) return true;
        return false;
    }

    /** The loops currently playing for one ship. Exposed for tests. */
    public int loopCount(Entity ship) {
        ShipSounds s = ships.get(ship);
        return s == null ? 0 : s.fires.size + s.hisses.size + s.alarms.size;
    }

    private static final class ShipSounds {
        final IdentityMap<Compartment, Entity> fires = new IdentityMap<>();
        final IdentityMap<Compartment, Entity> alarms = new IdentityMap<>();
        /** Keyed by tile and side of the opening. */
        final ObjectMap<Integer, Entity> hisses = new ObjectMap<>();
        /** Whether each door blocked last tick. */
        final IdentityMap<EdgeWall, Boolean> doors = new IdentityMap<>();

        final IdentityMap<Compartment, Entity> nextFires = new IdentityMap<>();
        final IdentityMap<Compartment, Entity> nextAlarms = new IdentityMap<>();
        final ObjectMap<Integer, Entity> nextHisses = new ObjectMap<>();

        void update(Engine engine, Entity ship, ShipGridComponent grid) {
            updateFires(engine, ship, grid);
            updateHisses(engine, ship, grid);
            updateAlarms(engine, ship, grid);
            updateDoors(engine, ship, grid);
        }

        void updateFires(Engine engine, Entity ship, ShipGridComponent grid) {
            nextFires.clear();
            for (Compartment c : grid.compartments) {
                float total = 0f, x = 0f, y = 0f;
                for (int i = 0; i < c.tiles.size; i++) {
                    int idx = c.tiles.get(i);
                    int tx = idx % grid.width, ty = idx / grid.width;
                    Tile t = grid.getTile(tx, ty);
                    if (!t.isBurning()) continue;
                    Vector2 p = grid.tileToLocal(tx, ty);
                    total += t.fireIntensity;
                    x += p.x * t.fireIntensity;
                    y += p.y * t.fireIntensity;
                }
                if (total <= 0f) continue;
                float loudness = MathUtils.clamp(total / FULL_FIRE, 0.2f, 1f);
                nextFires.put(c, loop(engine, fires.remove(c), AudioEventCatalog.FIRE, ship, x / total, y / total, loudness));
            }
            swap(engine, fires, nextFires);
        }

        void updateHisses(Engine engine, Entity ship, ShipGridComponent grid) {
            nextHisses.clear();
            for (int i = 0; i < grid.airflow.size; i++) {
                AirflowEdge e = grid.airflow.get(i);
                if (e.other != null || e.flow < MIN_HISS_FLOW) continue;
                Integer key = (e.y * grid.width + e.x) * 5 + (e.side == null ? 4 : e.side.ordinal());
                Vector2 p = grid.tileToLocal(e.x, e.y);
                if (e.side != null) {
                    p.x += e.side.dx * grid.tileSize * 0.5f;
                    p.y += e.side.dy * grid.tileSize * 0.5f;
                }
                float loudness = MathUtils.clamp(e.flow / FULL_HISS_FLOW, 0.1f, 1f);
                nextHisses.put(key, loop(engine, hisses.remove(key), AudioEventCatalog.BREACH_HISS, ship, p.x, p.y, loudness));
            }
            for (Entity e : hisses.values()) engine.removeEntity(e);
            hisses.clear();
            hisses.putAll(nextHisses);
        }

        void updateAlarms(Engine engine, Entity ship, ShipGridComponent grid) {
            nextAlarms.clear();
            for (Compartment c : grid.compartments) {
                if (c.tiles.size == 0 || !inDanger(grid, c)) continue;
                float x = 0f, y = 0f;
                for (int i = 0; i < c.tiles.size; i++) {
                    int idx = c.tiles.get(i);
                    Vector2 p = grid.tileToLocal(idx % grid.width, idx / grid.width);
                    x += p.x;
                    y += p.y;
                }
                nextAlarms.put(c, loop(engine, alarms.remove(c), AudioEventCatalog.ALARM, ship,
                        x / c.tiles.size, y / c.tiles.size, 1f));
            }
            swap(engine, alarms, nextAlarms);
        }

        void updateDoors(Engine engine, Entity ship, ShipGridComponent grid) {
            doorEdges(engine, ship, grid, grid.edgeH, true);
            doorEdges(engine, ship, grid, grid.edgeV, false);
        }

        private void doorEdges(Engine engine, Entity ship, ShipGridComponent grid, EdgeWall[][] edges, boolean horizontal) {
            for (int y = 0; y < edges.length; y++) {
                for (int x = 0; x < edges[y].length; x++) {
                    EdgeWall e = edges[y][x];
                    if (e == null || !e.hasBulkhead) continue;
                    boolean shut = e.blocks();
                    Boolean was = doors.put(e, shut);
                    // A door that was just placed, or one that broke rather than moved, makes no door sound
                    if (was == null || was == shut || e.integrity <= 0f) continue;
                    float lx = grid.gridOriginLocal.x + (horizontal ? (x + 0.5f) : x) * grid.tileSize;
                    float ly = grid.gridOriginLocal.y + (horizontal ? y : (y + 0.5f)) * grid.tileSize;
                    AudioEmitters.spawnInteriorOneShot(engine, AudioEventCatalog.DOOR, ship, lx, ly, 1f);
                }
            }
        }

        static boolean inDanger(ShipGridComponent grid, Compartment c) {
            if (c.pressureRatio() < ALARM_PRESSURE || c.oxygenRatio() < ALARM_OXYGEN) return true;
            for (int i = 0; i < grid.airflow.size; i++) {
                AirflowEdge e = grid.airflow.get(i);
                if (e.room == c && e.other == null && e.flow > ShipGridComponent.VENTING_HAZARD_FLOW) return true;
            }
            for (int i = 0; i < c.tiles.size; i++) {
                int idx = c.tiles.get(i);
                if (grid.getTile(idx % grid.width, idx / grid.width).isBurning()) return true;
            }
            return false;
        }

        /** Moves an existing loop, or starts a new one, and sets its loudness. */
        static Entity loop(Engine engine, Entity existing, String event, Entity ship, float x, float y, float loudness) {
            Entity e = existing;
            AudioEmitterComponent emitter;
            if (e == null) {
                e = engine.createEntity();
                emitter = AudioEmitterComponent.interior(event, ship, x, y);
                e.add(emitter);
                e.add(new TransientComponent());
                engine.addEntity(e);
            } else {
                emitter = e.getComponent(AudioEmitterComponent.class);
                emitter.localX = x;
                emitter.localY = y;
            }
            emitter.setParameter(AudioParameters.INTENSITY, loudness);
            return e;
        }

        /** Stops the loops left in {@code current} and replaces them with {@code next}. */
        static void swap(Engine engine, IdentityMap<Compartment, Entity> current, IdentityMap<Compartment, Entity> next) {
            for (Entity e : current.values()) engine.removeEntity(e);
            current.clear();
            current.putAll(next);
        }

        void clear(Engine engine) {
            for (Entity e : fires.values()) engine.removeEntity(e);
            for (Entity e : alarms.values()) engine.removeEntity(e);
            for (Entity e : hisses.values()) engine.removeEntity(e);
            fires.clear();
            alarms.clear();
            hisses.clear();
            doors.clear();
        }
    }
}
