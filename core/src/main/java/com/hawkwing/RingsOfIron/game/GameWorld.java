package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewSaveParticipant;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.pilot.NpcCombatSystem;
import com.ringsofiron.aicrew.pilot.NpcPilotSystem;
import com.ringsofiron.aicrew.pilot.PilotReference;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.ImpactAudio;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.SoundEventDef;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.audiospatial.systems.InteriorHazardAudioSystem;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.save.CombatSaveParticipant;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.modding.ShipWeaponsAssembler;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.BeamSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.combatsys.systems.MissileGuidanceSystem;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.InteriorGridSaveParticipant;
import com.ringsofiron.interiorgrid.InteriorHazardWarpGuard;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.MacroSaveParticipant;
import com.ringsofiron.macrosim.MacroSimSystem;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.economy.DefRegistryCommodityCatalog;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.ModTrustPolicy;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.moddingdata.defs.SoundEventData;
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
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Composition root: builds every game module into one {@link GameEngine} and owns the frame loop.
 * No rendering here, so it runs headless in tests; screens hold a GameWorld and draw it.
 *
 * <p>Wired today: core-engine, physics-exterior, orbital-mechanics (rails, events, time warp),
 * interior-grid (hazards, power, life support, rails-warp guard, saves, ships' interiors, cargo bays
 * and mass from data), ai-crew (crew AI and the NPC
 * pilot), combat-sys (sensor tracks, turret fire control, missiles and
 * point defense, ballistics and impact damage, hurting crew through ai-crew; ships get their
 * weapons from their hardpoints), audio-spatial
 * (sound, occlusion, listener on the player), modding-data (data and mod loading through
 * {@link #loadContent}, ships get Box2D bodies from their defs), macro-sim (economy, freighter
 * traffic and bubble transitions on GameWorld's rails, NPCs entering the bubble flown by ai-crew's
 * pilot). ui-hud needs a GL context, so screens attach it with {@link GameHud}.
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
        /** Frame the NPC pilot flies in; null means the true parent-relative state from physics. */
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
    /** The universe outside the bubble: economy, freighters, bubble entries and exits. */
    public final MacroSimulation macroSim;
    public final CrewService crew;
    public final CombatEvents combatEvents;
    /** Weapon, projectile, missile, warhead and material tables; data from {@link #loadContent} is merged in. */
    public final CombatDefs combatDefs;
    public final BallisticsSystem ballistics;
    public final SensorTrackingSystem sensors;
    public final WeaponControlSystem weaponControl;
    /** Flies missiles and decoys; launch and drop them through it. */
    public final MissileGuidanceSystem missiles;
    /** Resolves hits; damage uses the loaded material data once {@link #loadContent} has run. */
    public final ImpactResolutionSystem impacts;
    /** Finds what each burning beam touches and burns it. */
    public final BeamSystem beams;
    public final AudioManager audio;
    public final AvatarInteractor avatar;
    public final AudioListenerSync audioListener;
    /** The player's planned burns. */
    public final BurnPlanner burns;

    /** How the player's ship was lost. */
    public static final class Crash {
        public final String bodyName;
        public final double time;

        Crash(String bodyName, double time) {
            this.bodyName = bodyName;
            this.time = time;
        }
    }
    public final EngineAudio engineAudio;
    public final CrewAudio crewAudio;

    private final ImmutableArray<Entity> players;
    private final OrbitalAnchorBridge anchorBridge;
    private final Modules modules;
    private final SwitchableCommodityCatalog commodities = new SwitchableCommodityCatalog(new DefaultCommodityCatalog());
    private ModContext content;
    private Crash crash;
    private final List<Entity> wrecks = new ArrayList<>();
    private final double[] trueState = new double[4];
    /** Materials from the loaded data, for ships' mass. */
    private MaterialTable materials;

    public GameWorld() {
        this(new Modules());
    }

    public GameWorld(Modules modules) {
        this.modules = modules;
        engine = new GameEngine();
        players = engine.getEcsEngine().getEntitiesFor(PLAYER);

        physics = new PhysicsManager();

        // ai-crew's NPC pilot sets flight controls before physics reads them.
        PilotReference pilotFrame = modules.pilotReference != null
                ? modules.pilotReference : new TrueStatePilotReference(physics, engine::getSimSeconds);
        engine.addSystem(new NpcPilotSystem(pilotFrame));
        // ... and its combat pilot flies NPC ships in a fight, dodging combat-sys missiles.
        NpcCombatSystem npcCombat = new NpcCombatSystem();
        npcCombat.setThreatSensor(new CombatThreatSensor(engine.getEcsEngine()));
        engine.addSystem(npcCombat);

        // physics-exterior
        engine.addSystem(new FlightControlSystem(physics));
        // The anchor carries the planet's main pull; bubble bodies feel only the tidal difference.
        engine.addSystem(new TidalGravitySystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        // Fast passes and stations: drawn and swept for collisions while they stay on rails.
        engine.addSystem(new KinematicTransientSystem(physics));
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
        orbitalEvents.impacts.add((signal, event) -> onImpact(event));
        // Planned burns: the event system fires them on rails, this system in the bubble.
        engine.addSystem(new BubbleBurnSystem(physics, universe));
        burns = new BurnPlanner(this);

        // macro-sim, on the same rails and event system. Prediction runs inline so warp drop-out
        // always sees a current forecast; the bubble pauses while rails warp has it collapsed.
        macroSim = new MacroSimulation(engine, new OrbitalRailsModel(engine, universe, orbitalEvents),
                new GameBubbleGateway(physics, engine.getUuidRegistry()), commodities, null, engine.getSimSeconds());
        macroSim.bubble().addListener(new NpcBubblePilots(engine.getUuidRegistry()));
        // Economy, fleet and bubble state; restores its own components on load.
        MacroSaveParticipant.register(macroSim, engine.getSerializationService());
        engine.addSystem(new MacroSimSystem(macroSim, engine));
        timeWarp.addDropOutSource(macroSim::nextDropOutTime);
        timeWarp.changes.add((signal, change) -> macroSim.setBubbleSuspended(change.rails));

        // interior-grid
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        engine.getSerializationService().addParticipant(new InteriorGridSaveParticipant(engine));
        // Traded goods sit in the ships' cargo bays and weigh them down
        engine.addSystem(new ShipCargo(this::shipMass));
        // Watches whichever ship the player is flying now.
        InteriorHazardWarpGuard hazards = new InteriorHazardWarpGuard(null);
        engine.addRailsWarpGuard(() -> {
            Entity player = getPlayer();
            hazards.setGrid(player == null ? null : player.getComponent(ShipGridComponent.class));
            return hazards.railsWarpBlockedReason();
        });

        // ai-crew
        crew = CrewFactory.install(engine.getEcsEngine());
        CrewSaveParticipant.install(crew, engine);
        avatar = new AvatarInteractor(engine.getEcsEngine());

        // combat-sys
        combatEvents = new CombatEvents();
        combatDefs = CombatDefs.createDefault();
        ballistics = new BallisticsSystem(physics, combatEvents);
        // Rounds and missiles fall through the same tidal field as the Box2D bodies.
        ballistics.setTidalField(physics.getTidalField()::accelAt);
        engine.getSerializationService().addParticipant(new CombatSaveParticipant(engine));
        // Each physics tick: sensors fix, gunners aim and fire, missiles steer, then rounds fly
        // and hit, then hits resolve and beams burn. Their step listeners run in the order added here.
        sensors = new SensorTrackingSystem(physics, modules.combatSeed + 1);
        missiles = new MissileGuidanceSystem(physics, combatEvents, combatDefs, ballistics, sensors);
        weaponControl = new WeaponControlSystem(physics, combatEvents, combatDefs, ballistics, missiles, sensors,
                modules.combatSeed + 2);
        // The crew aboard set each ship's gunnery and tracking skill
        engine.addSystem(new CrewStationSync());
        engine.addSystem(sensors);
        // Shots and beams draw on the ship's power circuits.
        weaponControl.setPower(new ShipWeaponPower());
        engine.addSystem(weaponControl);
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        impacts = new ImpactResolutionSystem(physics, combatEvents, combatDefs, ballistics,
                MaterialTable.createDefault(), modules.combatSeed);
        impacts.setCrew(new CrewDamageAdapter(crew, crew));
        engine.addSystem(impacts);
        beams = new BeamSystem(physics, impacts);
        engine.addSystem(beams);

        // audio-spatial
        audio = modules.audio != null ? modules.audio : AudioManager.createDefault();
        audioListener = new AudioListenerSync(this::getPlayer, avatar);
        engine.addSystem(audioListener);
        engineAudio = new EngineAudio(this::getPlayer);
        engine.addSystem(engineAudio);
        crewAudio = new CrewAudio();
        engine.addSystem(crewAudio);
        // Fires, breaches, alarms and doors aboard every ship
        engine.addSystem(new InteriorHazardAudioSystem());
        engine.addSystem(new AcousticOcclusionSystem(audio.getCatalog()));
        AudioUpdateSystem audioUpdate = new AudioUpdateSystem(audio);
        engine.addSystem(audioUpdate);
        new ImpactAudio(audio).attach(physics);
        CombatAudio.install(engine.getEcsEngine(), combatEvents, combatDefs, audio, this::getContent);
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
        // The interior first: the physics assembler takes the ship's mass from its grid.
        bootstrap.beforeScripts(InteriorGridData::install);
        bootstrap.beforeScripts(ctx -> ShipPhysicsAssembler.register(ctx.factory, physics, gridMass(ctx)));
        // Weapons after the interior: each hardpoint is wired to the nearest weapon system.
        bootstrap.beforeScripts(ctx -> ShipWeaponsAssembler.register(ctx.factory, combatDefs));
        for (Consumer<ModContext> step : modules.contentRegistrations) bootstrap.beforeScripts(step);
        content = bootstrap.load(engine);
        mergeCombatDefs(CombatDefs.fromRegistry(content.defs));
        // Armor and hull stats from data, the same table ship mass comes from.
        if (content.defs.has("material", MaterialTable.DEFAULT_MATERIAL)) {
            impacts.setMaterials(InteriorGridData.toMaterialTable(content.defs));
        }
        DefRegistryCommodityCatalog economy = new DefRegistryCommodityCatalog(content.defs);
        if (!economy.commodities().isEmpty()) commodities.set(economy);
        registerSounds(content, audio.getCatalog());
        return content;
    }

    /** Gives the audio backends playback rules for every {@code [sound_event.*]} in base data and mods. */
    static void registerSounds(ModContext content, AudioEventCatalog catalog) {
        for (SoundEventData s : content.defs.all(SoundEventData.class)) {
            if (s.path == null || s.path.isEmpty()) continue; // reported by the def validator
            catalog.register(new SoundEventDef(s.path, s.looping, s.duration, s.minDistance, s.maxDistance, s.volume));
        }
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

    /** One rendered frame. Once the player has crashed the world stops. */
    public void update(float frameDelta) {
        if (crash != null) return;
        timeWarp.update(frameDelta);
        engine.update(frameDelta);
        for (Entity wreck : wrecks) engine.removeEntity(wreck);
        wrecks.clear();
        checkBubbleCrash();
        if (crash != null && timeWarp.getWarp() != 1) timeWarp.setWarp(1);
    }

    /** How the player's ship was lost, or null while it still flies. */
    public Crash getCrash() {
        return crash;
    }

    /** Lets the world run again, after loading a save from before the crash. */
    public void clearCrash() {
        crash = null;
    }

    /**
     * A ship on rails reached a body's surface. The player's ends the game; any other is removed
     * after the frame, unless macro-sim owns it: macro-sim handles the loss of its own stations and freighters.
     */
    private void onImpact(OrbitalEvent event) {
        Entity ship = universe.entity(event.entityUuid);
        if (ship == null) return;
        if (ship == getPlayer()) {
            crash(universe.parentOf(ship), event.time);
            return;
        }
        if (macroSim.freighters().get(event.entityUuid) != null || macroSim.station(event.entityUuid) != null) return;
        if (!wrecks.contains(ship)) wrecks.add(ship);
    }

    /** In the bubble the player flies under real physics; crashed once below the surface. */
    private void checkBubbleCrash() {
        Entity player = getPlayer();
        PhysicsBodyComponent pb = player == null ? null : player.getComponent(PhysicsBodyComponent.class);
        String parentUuid = physics.getAnchorParentUuid();
        if (crash != null || pb == null || pb.body == null || parentUuid == null) return;
        Entity parent = universe.entity(parentUuid);
        CelestialBodyComponent body = parent == null ? null : universe.celestial(parent);
        if (body == null) return;
        physics.getTrueState(pb.body, trueState);
        if (Math.hypot(trueState[0], trueState[1]) < body.radius) crash(parent, engine.getSimSeconds());
    }

    private void crash(Entity body, double time) {
        CelestialBodyComponent c = body == null ? null : universe.celestial(body);
        crash = new Crash(c == null || c.name == null ? "the surface" : c.name, time);
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

    /**
     * Mass, center and inertia of a ship from its interior grid and the loaded materials, with the
     * cargo in its bays. Null for a ship without a grid, or before content has loaded.
     */
    public ShipGridComponent.MassProperties shipMass(Entity ship) {
        return content == null ? null : shipMass(ship, content.defs);
    }

    private ShipGridComponent.MassProperties shipMass(Entity ship, DefRegistry defs) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return null;
        if (materials == null) materials = InteriorGridData.toMaterialTable(defs);
        ShipGridComponent.MassProperties m = grid.computeMassProperties(materials);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        if (hold != null) hold.addMassTo(grid, m);
        return m;
    }

    /** A ship's mass for the physics assembler, from {@link #shipMass}. */
    private ShipPhysicsAssembler.MassSource gridMass(ModContext ctx) {
        return (entity, def, out) -> {
            ShipGridComponent.MassProperties m = shipMass(entity, ctx.defs);
            if (m == null) return false;
            out[0] = m.mass;
            out[1] = m.center.x;
            out[2] = m.center.y;
            out[3] = m.inertia;
            return true;
        };
    }

    private void mergeCombatDefs(CombatDefs loaded) {
        combatDefs.weapons.putAll(loaded.weapons);
        combatDefs.projectiles.putAll(loaded.projectiles);
        combatDefs.missiles.putAll(loaded.missiles);
        combatDefs.warheads.putAll(loaded.warheads);
        combatDefs.materials.putAll(loaded.materials);
    }
}
