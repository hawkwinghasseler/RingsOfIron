package com.ringsofiron.coreengine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.coreengine.systems.RenderSystem;

/**
 * Main wrapper for the Ashley ECS and core services, and owner of authoritative sim time.
 *
 * <p>Each call to {@link #update(float)} is one rendered frame:
 * <ol>
 *   <li>Real time (times the physics warp) is added to an accumulator, and one fixed tick runs per
 *       1/60 s in it. A tick increments {@link #getTick()} and runs every {@link FixedLogicSystem}
 *       that is due, in priority order. Other systems do not run during ticks.</li>
 *   <li>Then one normal Ashley pass runs every other system once ({@link RenderSystem}s get the
 *       interpolation alpha), and fixed systems get {@link FixedLogicSystem#updateInterpolated}.</li>
 * </ol>
 *
 * <p>Time warp: physics warp (1x to 10x) runs more ticks per frame. Rails warp is
 * {@link #advanceRails(long)}: the tick counter jumps and each fixed system gets one coarse
 * catch-up call instead of a run per skipped tick. Orbital-mechanics' TimeWarpController decides
 * when to use which; this class only provides the clock and the guard checks.
 */
public class GameEngine implements SimClock {
    public static final int MAX_PHYSICS_WARP = 10;
    /** Longest frame the accumulator accepts, so a stall (debugger, window drag) does not snowball. */
    public static final float MAX_FRAME_DELTA = 0.25f;
    /** At 1x, at most this many ticks run per frame; scales with physics warp. */
    public static final int MAX_TICKS_PER_FRAME_AT_1X = 15;

    private enum Phase { IDLE, FIXED, FRAME, CATCH_UP }

    private final PooledEngine ecsEngine;
    private final UuidRegistry uuidRegistry;
    private final SerializationService serializationService;
    private final Array<RailsWarpGuard> railsWarpGuards = new Array<>();
    private final Array<EntitySystem> pausedSystems = new Array<>();

    private long tick;
    private double accumulator;
    private float alpha;
    private int physicsWarp = 1;
    private long droppedTicks;
    private Phase phase = Phase.IDLE;
    private long catchUpTicks;
    private long pendingRailsTicks;
    private TickProfiler profiler;
    private final Array<EntitySystem> profiledSystems = new Array<>();

    public GameEngine() {
        this.ecsEngine = new PooledEngine();
        this.uuidRegistry = new UuidRegistry();

        // Listen for UUID components to maintain O(1) lookup
        this.ecsEngine.addEntityListener(Family.all(UuidComponent.class).get(), new UuidEntityListener(uuidRegistry));

        this.serializationService = new SerializationService(this);
    }

    /**
     * Runs one rendered frame: due fixed ticks, then every-frame systems. Call from the LibGDX render loop.
     * @param frameDelta Real seconds since the last frame.
     */
    public void update(float frameDelta) {
        requireIdle("update");
        bindSystems();

        double dt = Math.min(Math.max(frameDelta, 0f), MAX_FRAME_DELTA);
        accumulator += dt * physicsWarp;
        int maxTicks = MAX_TICKS_PER_FRAME_AT_1X * physicsWarp;
        int ran = 0;
        while (accumulator >= TICK_SECONDS && ran < maxTicks) {
            runTick();
            accumulator -= TICK_SECONDS;
            ran++;
        }
        if (accumulator >= TICK_SECONDS) {
            // CPU could not keep up: drop the backlog instead of spiralling.
            long behind = (long) (accumulator / TICK_SECONDS);
            droppedTicks += behind;
            accumulator -= behind * TICK_SECONDS;
        }
        alpha = (float) (accumulator / TICK_SECONDS);

        phase = Phase.FRAME;
        try {
            if (profiler == null) ecsEngine.update(frameDelta);
            else runProfiledFramePass(frameDelta);
        } finally {
            phase = Phase.IDLE;
        }
        applyPendingRails();
    }

    /**
     * The frame pass one system at a time, each in its own Ashley update so the entity changes it
     * makes are applied right after it, as in a normal pass, and its time can be told apart.
     */
    private void runProfiledFramePass(float frameDelta) {
        ImmutableArray<EntitySystem> systems = ecsEngine.getSystems();
        for (int i = 0; i < systems.size(); i++) {
            EntitySystem system = systems.get(i);
            if (system.checkProcessing()) profiledSystems.add(system);
        }
        for (EntitySystem system : profiledSystems) system.setProcessing(false);
        try {
            for (EntitySystem system : profiledSystems) {
                system.setProcessing(true);
                long start = System.nanoTime();
                try {
                    ecsEngine.update(frameDelta);
                } finally {
                    system.setProcessing(false);
                }
                profiler.record("frame/" + nameOf(system), System.nanoTime() - start);
            }
        } finally {
            for (EntitySystem system : profiledSystems) system.setProcessing(true);
            profiledSystems.clear();
        }
    }

    /**
     * Times every fixed system's tick and every system's part of the frame pass from now on, or
     * stops timing with null. For benchmarks: profiling runs the frame pass one system at a time.
     */
    public void setProfiler(TickProfiler profiler) {
        requireIdle("setProfiler");
        this.profiler = profiler;
    }

    public TickProfiler getProfiler() {
        return profiler;
    }

    /** A system's class name for the profiler; anonymous classes go by their full binary name's tail. */
    private static String nameOf(EntitySystem system) {
        Class<?> c = system.getClass();
        String simple = c.getSimpleName();
        return simple.isEmpty() ? c.getName().substring(c.getName().lastIndexOf('.') + 1) : simple;
    }

    /**
     * Runs {@code ticks} fixed ticks immediately with no frame pass. For headless simulation and tests.
     */
    public void stepTicks(int ticks) {
        requireIdle("stepTicks");
        bindSystems();
        for (int i = 0; i < ticks; i++) runTick();
        applyPendingRails();
    }

    /**
     * Rails warp: jumps sim time forward by {@code ticks} without running per-tick updates; each
     * fixed system gets one {@code catchUp} call instead.
     *
     * Called during an update (e.g. by a system), the jump is applied right after that frame.
     *
     * @return false, and nothing happens, if a {@link RailsWarpGuard} refuses rails warp.
     */
    public boolean advanceRails(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Cannot advance time backwards: " + ticks);
        if (getRailsWarpBlockedReason() != null) return false;
        if (ticks == 0) return true;
        if (phase != Phase.IDLE) {
            pendingRailsTicks += ticks;
            return true;
        }
        doRails(ticks);
        return true;
    }

    /** @return null if rails warp is allowed, else the first guard's reason. */
    public String getRailsWarpBlockedReason() {
        for (RailsWarpGuard guard : railsWarpGuards) {
            String reason = guard.railsWarpBlockedReason();
            if (reason != null) return reason;
        }
        return null;
    }

    public void addRailsWarpGuard(RailsWarpGuard guard) {
        railsWarpGuards.add(guard);
    }

    public void removeRailsWarpGuard(RailsWarpGuard guard) {
        railsWarpGuards.removeValue(guard, true);
    }

    /** @param warp 1 (real time) to {@link #MAX_PHYSICS_WARP}. */
    public void setPhysicsWarp(int warp) {
        if (warp < 1 || warp > MAX_PHYSICS_WARP) {
            throw new IllegalArgumentException("Physics warp must be 1.." + MAX_PHYSICS_WARP + ": " + warp);
        }
        physicsWarp = warp;
    }

    public int getPhysicsWarp() {
        return physicsWarp;
    }

    @Override
    public long getTick() {
        return tick;
    }

    /** Sets sim time directly. Used when loading a save. */
    public void setTick(long tick) {
        requireIdle("setTick");
        this.tick = tick;
        this.accumulator = 0;
        this.alpha = 0f;
    }

    /** Interpolation alpha (0..1) between the last fixed tick and the next, for rendering. */
    public float getAlpha() {
        return alpha;
    }

    /** Ticks dropped because frames ran too long to catch up. */
    public long getDroppedTicks() {
        return droppedTicks;
    }

    public void addSystem(EntitySystem system) {
        ecsEngine.addSystem(system);
        bind(system);
    }

    public void removeSystem(EntitySystem system) {
        ecsEngine.removeSystem(system);
        if (system instanceof FixedLogicSystem && ((FixedLogicSystem) system).isBoundTo(this)) {
            ((FixedLogicSystem) system).bindDriver(null);
        } else if (system instanceof RenderSystem && ((RenderSystem) system).isBoundTo(this)) {
            ((RenderSystem) system).bindEngine(null);
        }
    }

    public <T extends EntitySystem> T getSystem(Class<T> type) {
        return ecsEngine.getSystem(type);
    }

    public Entity createEntity() {
        return ecsEngine.createEntity();
    }

    /** Creates an entity that already carries a fresh {@link UuidComponent}, so it can be referenced and saved. */
    public Entity createEntityWithUuid() {
        Entity entity = ecsEngine.createEntity();
        entity.add(new UuidComponent());
        return entity;
    }

    public void addEntity(Entity entity) {
        ecsEngine.addEntity(entity);
    }

    public void removeEntity(Entity entity) {
        ecsEngine.removeEntity(entity);
    }

    /** O(1) lookup by UUID; null if no such entity is in the engine. */
    public Entity getEntity(String uuid) {
        return uuidRegistry.getEntity(uuid);
    }

    public Engine getEcsEngine() {
        return ecsEngine;
    }

    public UuidRegistry getUuidRegistry() {
        return uuidRegistry;
    }

    public SerializationService getSerializationService() {
        return serializationService;
    }

    public void clear() {
        ecsEngine.removeAllEntities();
        uuidRegistry.clear();
    }

    /** Called by a bound {@link FixedLogicSystem} from its update; runs whatever the current phase needs. */
    public void runFixedSystem(FixedLogicSystem system) {
        switch (phase) {
            case FIXED:
                if (tick % system.getTickInterval() != 0) break;
                if (profiler == null) {
                    system.invokeFixed();
                } else {
                    long start = System.nanoTime();
                    system.invokeFixed();
                    profiler.record("tick/" + nameOf(system), System.nanoTime() - start);
                }
                break;
            case FRAME: {
                int interval = system.getTickInterval();
                system.invokeInterpolated((float) (((tick % interval) + alpha) / interval));
                break;
            }
            case CATCH_UP:
                if (profiler == null) {
                    system.invokeCatchUp(catchUpTicks, catchUpTicks * TICK_SECONDS);
                } else {
                    long start = System.nanoTime();
                    system.invokeCatchUp(catchUpTicks, catchUpTicks * TICK_SECONDS);
                    profiler.record("catch-up/" + nameOf(system), System.nanoTime() - start);
                }
                break;
            default:
                // Someone updated the Ashley engine directly; time is only advanced through GameEngine.
                break;
        }
    }

    private void runTick() {
        tick++;
        runFixedPhase(Phase.FIXED, (float) TICK_SECONDS);
    }

    private void doRails(long ticks) {
        bindSystems();
        tick += ticks;
        catchUpTicks = ticks;
        try {
            runFixedPhase(Phase.CATCH_UP, 0f);
        } finally {
            catchUpTicks = 0;
        }
    }

    private void applyPendingRails() {
        if (pendingRailsTicks == 0) return;
        long ticks = pendingRailsTicks;
        pendingRailsTicks = 0;
        doRails(ticks);
    }

    /**
     * Runs only the fixed systems through a normal Ashley update, so entity adds and removes made by
     * a system are applied between systems exactly as Ashley always does.
     */
    private void runFixedPhase(Phase fixedPhase, float delta) {
        for (EntitySystem system : ecsEngine.getSystems()) {
            if (!(system instanceof FixedLogicSystem) && system.checkProcessing()) {
                system.setProcessing(false);
                pausedSystems.add(system);
            }
        }
        phase = fixedPhase;
        try {
            ecsEngine.update(delta);
        } finally {
            phase = Phase.IDLE;
            for (EntitySystem system : pausedSystems) system.setProcessing(true);
            pausedSystems.clear();
        }
    }

    private void bindSystems() {
        // Also catches systems added straight to the Ashley engine.
        for (EntitySystem system : ecsEngine.getSystems()) bind(system);
    }

    private void bind(EntitySystem system) {
        if (system instanceof FixedLogicSystem) {
            FixedLogicSystem fixed = (FixedLogicSystem) system;
            if (!fixed.isBoundTo(this)) fixed.bindDriver(this);
        } else if (system instanceof RenderSystem) {
            RenderSystem render = (RenderSystem) system;
            if (!render.isBoundTo(this)) render.bindEngine(this);
        }
    }

    private void requireIdle(String what) {
        if (phase != Phase.IDLE) throw new IllegalStateException("Cannot call " + what + " while the engine is updating");
    }
}
