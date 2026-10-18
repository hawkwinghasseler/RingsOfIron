package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.pilot.NpcPilotSystem;
import com.ringsofiron.aicrew.pilot.PilotReference;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.ImpactAudio;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.ModTrustPolicy;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.EncounterPredictor;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.OrbitalAnchorBridge;
import com.ringsofiron.physicsexterior.PhysicsSaveParticipant;
import com.ringsofiron.physicsexterior.RailsBubbleHandoff;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Composition root: builds every game module into one {@link GameEngine} and owns the frame loop.
 * No rendering here, so it runs headless in tests; screens hold a GameWorld and draw it.
 *
 * <p>Wired today: core-engine, physics-exterior, orbital-mechanics (rails, events, time warp),
 * interior-grid (hazards, power, life support, rails-warp guard), ai-crew (crew AI and the NPC
 * pilot), combat-sys (ballistics and impact damage, hurting crew through ai-crew), audio-spatial
 * (sound, occlusion, listener on the player), modding-data (data and mod loading through
 * {@link #loadContent}, ships get Box2D bodies from their defs). ui-hud needs a GL context, so
 * screens attach it with {@link GameHud}.
 * macro-sim plugs in here when it lands.
 *
 * <p>Frame order: time warp first (a rails jump must land before the frame's systems see the new
 * tick), then the engine's fixed ticks and every-frame systems.
 */
public class GameWorld implements Disposable {
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class).get();

    /** Swappable pieces, for tests and for modules that are not on main yet. */
    public static class Modules {
        /** Null means physics-exterior's {@link RailsBubbleHandoff}. */
        public BubbleHandoff bubble;
        /**
         * Steps run after data loads and before mod scripts: where each module registers its def
         * types and component assemblers with the {@link ModContext}.
         */
        public final List<Consumer<ModContext>> contentRegistrations = new ArrayList<>();
        public ModTrustPolicy modTrust = ModTrustPolicy.WARN_AND_ALLOW;
        /** Frame the NPC pilot flies in; null means the bubble stands in for the parent body. */
        public PilotReference pilotReference;
        /** Null picks audio-spatial's default: synth when libGDX audio runs, else headless. */
        public AudioManager audio;
        public long combatSeed = 1L;
    }

    public final GameEngine engine;
    public final PhysicsManager physics;
    public final RailsUniverse universe;
    public final OrbitalEventSystem orbitalEvents;
    public final TimeWarpController timeWarp;
    public final CrewService crew;
    public final CombatEvents combatEvents;
    /** Weapon, projectile, missile and warhead tables; data from {@link #loadContent} is merged in. */
    public final CombatDefs combatDefs;
    public final BallisticsSystem ballistics;
    public final AudioManager audio;
    public final AvatarInteractor avatar;
    public final AudioListenerSync audioListener;

    private final ImmutableArray<Entity> players;
    private final OrbitalAnchorBridge anchorBridge;
    private final Modules modules;
    private ModContext content;

    public GameWorld() {
        this(new Modules());
    }

    public GameWorld(Modules modules) {
        this.modules = modules;
        engine = new GameEngine();
        players = engine.getEcsEngine().getEntitiesFor(PLAYER);

        // ai-crew's NPC pilot sets flight controls before physics reads them.
        PilotReference pilotFrame = modules.pilotReference != null
                ? modules.pilotReference : PilotReference.bubbleOnly(engine::getSimSeconds);
        engine.addSystem(new NpcPilotSystem(pilotFrame));

        // physics-exterior
        physics = new PhysicsManager();
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new FloatingOriginSystem(physics));

        // orbital-mechanics
        universe = new RailsUniverse(engine);
        orbitalEvents = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(orbitalEvents);
        engine.getSerializationService().addParticipant(orbitalEvents);
        // Keeps orbital's copy of the bubble anchor in step with physics, both ways.
        anchorBridge = new OrbitalAnchorBridge(physics, orbitalEvents);
        EncounterPredictor encounters = new EncounterPredictor(engine.getEcsEngine(), universe);
        BubbleHandoff bubble = modules.bubble != null
                ? modules.bubble : new RailsBubbleHandoff(physics, engine.getEcsEngine());
        timeWarp = new TimeWarpController(engine, bubble, orbitalEvents,
                universe, encounters, this::getPlayer);

        // interior-grid
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        engine.addRailsWarpGuard(new PlayerShipHazardGuard(this::getPlayer));

        // ai-crew
        crew = CrewFactory.install(engine.getEcsEngine());
        avatar = new AvatarInteractor(engine.getEcsEngine());

        // combat-sys
        combatEvents = new CombatEvents();
        combatDefs = CombatDefs.createDefault();
        ballistics = new BallisticsSystem(physics, combatEvents);
        engine.addSystem(ballistics);
        ImpactResolutionSystem impacts = new ImpactResolutionSystem(physics, combatEvents, combatDefs, ballistics,
                MaterialTable.createDefault(), modules.combatSeed);
        impacts.setCrew(new CrewDamageAdapter(crew, crew));
        engine.addSystem(impacts);

        // audio-spatial
        audio = modules.audio != null ? modules.audio : AudioManager.createDefault();
        audioListener = new AudioListenerSync(this::getPlayer, avatar);
        engine.addSystem(audioListener);
        engine.addSystem(new AcousticOcclusionSystem());
        AudioUpdateSystem audioUpdate = new AudioUpdateSystem(audio);
        engine.addSystem(audioUpdate);
        physics.collisions.add(new ImpactAudio(audio));
        physics.worldShifts.add(audioUpdate.worldShiftListener());

        // Bubble bodies are saved as true states, never as live Box2D bodies.
        PhysicsSaveParticipant.install(physics, engine);
    }

    /**
     * Loads base data from {@code assetsDir/data} and every mod in {@code modsDir}, runs mod scripts
     * (subject to the trust policy) and returns the result: defs, assets, entity factory, report.
     * Call once, before spawning anything.
     */
    public ModContext loadContent(Path assetsDir, Path modsDir) {
        if (content != null) throw new IllegalStateException("Content is already loaded");
        ModdingBootstrap bootstrap = new ModdingBootstrap(assetsDir, modsDir).trustPolicy(modules.modTrust);
        bootstrap.beforeScripts(ctx -> CombatDefs.registerTypes(ctx.defs));
        bootstrap.beforeScripts(ctx -> ShipPhysicsAssembler.register(ctx, physics));
        for (Consumer<ModContext> step : modules.contentRegistrations) bootstrap.beforeScripts(step);
        content = bootstrap.load(engine);
        mergeCombatDefs(CombatDefs.fromRegistry(content.defs));
        return content;
    }

    /**
     * Puts the bubble in orbit at game start: the floating origin falls along {@code orbit} around
     * the body {@code parentUuid}. After this the bubble handoff keeps the anchor up to date.
     */
    public void setAnchor(String parentUuid, KeplerOrbit orbit) {
        physics.setAnchorModel(new KeplerAnchor(orbit), parentUuid);
    }

    /** What {@link #loadContent} produced, or null before it ran. */
    public ModContext getContent() {
        return content;
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
        anchorBridge.dispose();
        engine.clear();
        physics.dispose();
        audio.dispose();
    }

    private void mergeCombatDefs(CombatDefs loaded) {
        combatDefs.weapons.putAll(loaded.weapons);
        combatDefs.projectiles.putAll(loaded.projectiles);
        combatDefs.missiles.putAll(loaded.missiles);
        combatDefs.warheads.putAll(loaded.warheads);
    }
}
