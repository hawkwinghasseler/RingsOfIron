package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.EncounterPredictor;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;

/**
 * Composition root: builds every game module into one {@link GameEngine} and owns the frame loop.
 * No rendering here, so it runs headless in tests; screens hold a GameWorld and draw it.
 *
 * <p>Wired today: core-engine, physics-exterior, orbital-mechanics (rails, events, time warp),
 * interior-grid (hazards, power, life support, rails-warp guard). Other modules plug in here as they
 * land: ai-crew, combat-sys, ui-hud, audio-spatial, macro-sim, modding-data.
 *
 * <p>Frame order: time warp first (a rails jump must land before the frame's systems see the new
 * tick), then the engine's fixed ticks and every-frame systems.
 */
public class GameWorld implements Disposable {
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class).get();

    /** Swappable pieces, for tests and for modules that are not on main yet. */
    public static class Modules {
        public BubbleHandoff bubble = new StubBubbleHandoff();
    }

    public final GameEngine engine;
    public final PhysicsManager physics;
    public final RailsUniverse universe;
    public final OrbitalEventSystem orbitalEvents;
    public final TimeWarpController timeWarp;

    private final ImmutableArray<Entity> players;

    public GameWorld() {
        this(new Modules());
    }

    public GameWorld(Modules modules) {
        engine = new GameEngine();
        players = engine.getEcsEngine().getEntitiesFor(PLAYER);

        // physics-exterior
        physics = new PhysicsManager();
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new FloatingOriginSystem(physics));

        // orbital-mechanics
        universe = new RailsUniverse(engine.getEcsEngine(), engine.getUuidRegistry(), engine::getSimSeconds);
        orbitalEvents = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(orbitalEvents);
        EncounterPredictor encounters = new EncounterPredictor(engine.getEcsEngine(), universe);
        timeWarp = new TimeWarpController(new GameEngineWarpHost(engine), modules.bubble, orbitalEvents,
                universe, encounters, this::getPlayer);

        // interior-grid
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        engine.addRailsWarpGuard(new PlayerShipHazardGuard(this::getPlayer));

        // Live Box2D bodies are rebuilt by the bubble handoff after load, never saved.
        SerializationService saves = engine.getSerializationService();
        saves.skipComponent(PhysicsBodyComponent.class);
    }

    /** One rendered frame. */
    public void update(float frameDelta) {
        timeWarp.update(frameDelta);
        engine.update(frameDelta);
    }

    /** The player's primary actor (their ship), or null. */
    public Entity getPlayer() {
        return players.size() == 0 ? null : players.first();
    }

    @Override
    public void dispose() {
        engine.clear();
        physics.dispose();
    }
}
