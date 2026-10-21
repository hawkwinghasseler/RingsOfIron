package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.aicrew.CrewDoors;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewSaveParticipant;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
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
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.TickProfiler;
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
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.TankSupply;
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
import com.ringsofiron.physicsexterior.CollisionCategories;
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
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingBubbleHandoff;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationBodies;
import com.ringsofiron.stationsdocking.StationComponent;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
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
 * pilot), stations-docking (stations as walkable grids with real bodies in the bubble, docking
 * ports on ships and stations, latching, undocking, a docked pair riding through rails warp
 * together, and freighters flying themselves in to dock and away again). ui-hud needs a GL context, so screens attach it with {@link GameHud}.
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
    /** The player's seat at the helm or weapons console, and the helm flying the ship meanwhile. */
    public final Bridge bridge;
    public final AudioListenerSync audioListener;
    /** Pirates and faction patrols: their ships, and how they fly and fight in the bubble. */
    public final HostileTraffic hostiles;
    /** Pirates boarding the player once they have beaten them (round 7). */
    public final PirateBoarding pirateBoarding;
    /** The pirates' hidden base, once the scenario has one: found or not, standing or fallen. */
    public final PirateBase pirateBase;
    /** Strain between the factions, and the weekly drifts of tension and of grudges against the player. */
    public final FactionTension tension;
    /** Ties the career (factions and rank) to encounters, rescue, boarding and ships beaten. */
    public final LoopWiring loop;
    /** The player's planned burns. */
    public final BurnPlanner burns;
    /** The navigation target, plan to target, the helm's autopilot and the pirates known on the rails. */
    public final Navigation nav;
    /** Docking ports, collars and latches of every ship and station in the bubble. */
    public final DockingSystem docking;
    /** Craft flying themselves in to dock at stations, and away again. */
    public final BerthingSystem berthing;
    /** Station loaders carrying traded goods across the dock, a load at a time. */
    public final CargoLoading cargo;
    /** The player's standing with each faction, and what hostile acts cost it. */
    public final FactionStanding standing;
    /** The player's rank budget, paid weekly into their credits. */
    public final ServiceBudget budget;
    /** Crew prying, cutting or hacking at a shut port door across a dock. */
    public final DoorForcing forcing;
    /** Station yards repairing and refitting docked ships. */
    public final StationYard yard;
    /** Station mission boards and the player's contracts. */
    public final ContractBoard contracts;
    /** Escape pods and wrecks drifting on the rails, for rescue and recovery contracts. */
    public final Derelicts derelicts;
    /** Shortages, raids, lost freighters and rival tension, rolled each day, with jobs on the boards. */
    public final WorldEvents events;
    /** Which ships are disabled, surrendered or wrecked, and why. */
    public final ShipDisablement disablement;
    /** Crews ordered to the escape pods, and the pods launching. */
    public final AbandonShip abandon;
    /** Where the player wakes after their pod launches, and in what ship. */
    public final Rescue rescue;
    /** Boarding parties sent onto beaten ships, and the ships they take. */
    public final Boarding boarding;
    /** Taken ships stripped for parts, and their cargo carried off. */
    public final Salvage salvage;
    /** Ship markets at faction yards, and prizes towed home (round 3). */
    public final Shipyard shipyard;
    /** How carried cargo is settled: the market's, plus salvage's between ships and parts sold. */
    public final SalvageSettlement cargoSettlement;
    /** The player's crew: experience, morale and wages. */
    public final CrewCareer crewCareer;
    /** Station hiring pools, and hiring and dismissing crew. */
    public final CrewHiring crewHiring;
    /** Prisoners from taken ships: brought aboard, handed over, ransomed, released or recruited. */
    public final Prisoners prisoners;

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
    /** What the game tells the player: post here rather than keeping a list of your own. */
    public final NoticeLog notices;
    /** Key hints by context, for the bottom line and the F1 controls panel. */
    public final HintRegistry hints = HintRegistry.withDefaults();
    /** Low propellant and inbound missiles, as notices. */
    public final FlightWarnings warnings;
    /** Hull leaks and the crew's patches, as notices and hints. */
    public final DamageControl damageControl;
    /** Battle stations, and notices for the wounded (round 5). */
    public final CrewMedical medical;
    /** A ship docked with a hole to space: the station warns, and warp waits. */
    public final BerthVenting berthVenting;
    /** The player's figure suiting up at battle stations and in thin air. */
    public final CaptainSafety captain;
    /** Warnings before the player's people run out of air. */
    public final AirWatch air;
    /** A station's infirmary, treating the player's wounded at its berth (round 8). */
    public final StationInfirmary infirmary;
    /** The distress beacon: a stranded ship towed home. */
    public final DistressBeacon beacon = new DistressBeacon(this);
    /** First Play's and the opening walkthrough's progress, saved with the game. */
    public final GuideProgress guides = new GuideProgress();

    private final ImmutableArray<Entity> players;
    private final OrbitalAnchorBridge anchorBridge;
    private final Modules modules;
    private final SwitchableCommodityCatalog commodities = new SwitchableCommodityCatalog(new DefaultCommodityCatalog());
    private ModContext content;
    /** What ships collide on, once content has loaded. */
    private ShipHulls hulls;
    private Crash crash;
    /** Frames after a load that rails warp still waits for the player's latch to weld again. */
    static final int LOAD_SETTLE_FRAMES = 120;
    /** Frames left of that wait: from a load until a frame has run and the player's latch is welded again. */
    private int loadSettling;
    private String careerOver;
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
        notices = new NoticeLog(engine::getSimSeconds);
        warnings = new FlightWarnings(this);

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
        // Stations with an interior get a kinematic body shaped like it, which ships dock with.
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        engine.addSystem(transients);
        engine.addSystem(new FloatingOriginSystem(physics));

        // stations-docking: magnets, collars and latches, after the bodies exist.
        docking = new DockingSystem(physics, engine.getUuidRegistry());
        engine.addSystem(docking);
        // A collar still drawing a ship in, or a latch still settling, holds rails warp back.
        engine.addRailsWarpGuard(docking::railsWarpBlockedReason);
        // A loaded game's ships get their bodies back in its first frame and a docked ship its weld in
        // the next; warping before then pulled the pair apart and let the air out of both
        engine.addRailsWarpGuard(this::loadingRefusal);
        // Craft that fly themselves in to dock and away again, on their own thrusters
        berthing = new BerthingSystem(docking, engine.getUuidRegistry());
        engine.addSystem(berthing);
        // ... and ships crossing the bubble on their rails plan keep out of the stations and berths
        engine.addSystem(new TransitClearance(docking));

        // orbital-mechanics
        universe = new RailsUniverse(engine);
        orbitalEvents = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(orbitalEvents);
        engine.getSerializationService().addParticipant(orbitalEvents);
        // Keeps orbital's copy of the bubble anchor in step with physics, both ways.
        anchorBridge = new OrbitalAnchorBridge(physics, orbitalEvents);
        EncounterPredictor encounters = new EncounterPredictor(engine.getEcsEngine(), universe);
        // A docked pair goes onto the rails as one body and comes back latched.
        BubbleHandoff bubble = new DockingBubbleHandoff(modules.bubble != null
                ? modules.bubble : new RailsBubbleHandoff(physics, engine.getEcsEngine()),
                docking, physics, engine.getEcsEngine(), engine.getUuidRegistry());
        timeWarp = new TimeWarpController(engine, bubble, orbitalEvents,
                universe, encounters, this::getPlayer);
        orbitalEvents.impacts.add((signal, event) -> onImpact(event));
        // Planned burns: the event system fires them on rails, this system in the bubble.
        engine.addSystem(new BubbleBurnSystem(physics, universe));
        burns = new BurnPlanner(this);

        // macro-sim, on the same rails and event system. Prediction runs inline so warp drop-out
        // always sees a current forecast; the bubble pauses while rails warp has it collapsed.
        GameBubbleGateway bubbleGateway = new GameBubbleGateway(physics, engine.getUuidRegistry());
        // Pirates and patrols come into the bubble on their own layouts
        bubbleGateway.setShipBodies(this::buildShipBody);
        macroSim = new MacroSimulation(engine, new OrbitalRailsModel(engine, universe, orbitalEvents),
                bubbleGateway, commodities, null, engine.getSimSeconds());
        macroSim.bubble().addListener(new NpcBubblePilots(engine.getUuidRegistry()));
        // Freighters in the bubble dock at their stations under their own power, and leave the same way
        FreighterTraffic traffic = new FreighterTraffic(this);
        macroSim.bubble().addListener(traffic);
        engine.addSystem(traffic);
        // Economy, fleet and bubble state; restores its own components on load.
        MacroSaveParticipant.register(macroSim, engine.getSerializationService());
        engine.addSystem(new MacroSimSystem(macroSim, engine));
        timeWarp.addDropOutSource(macroSim::nextDropOutTime);
        timeWarp.changes.add((signal, change) -> macroSim.setBubbleSuspended(change.rails));

        // interior-grid
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        // Thrust and planned burns burn propellant from the tanks piped to the engines
        engine.addSystem(new PropellantFeed());
        // ...and the engines themselves set how hard the ship can push, less as they take damage
        engine.addSystem(new EngineThrust(this::getContent));
        orbitalEvents.burnBudget = (ship, dv) -> {
            ShipGridComponent.MassProperties m = shipMass(ship);
            return m == null ? dv : PropellantFeed.payForBurn(ship, m.mass, dv);
        };
        engine.getSerializationService().addParticipant(new InteriorGridSaveParticipant(engine));
        // Traded goods sit in the ships' cargo bays and weigh them down
        engine.addSystem(new ShipCargo(this::shipMass));
        // ...and so do the tanks, lighter as they run down
        engine.addSystem(new TankMass(this::shipMass));
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
        // A ship taken out of the world (crashed on the rails, lost, wrecked) takes whoever is aboard
        // with it, so no crew go on working on a ship that is gone
        engine.getEcsEngine().addEntityListener(Family.all(ShipGridComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
            }

            @Override
            public void entityRemoved(Entity ship) {
                if (!ship.isRemoving() && !ship.isScheduledForRemoval()) return; // only its layout went (a refit)
                ImmutableArray<Entity> all = crew.crewEntities();
                List<Entity> aboard = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    GridPositionComponent p = crew.position(all.get(i));
                    if (p != null && p.ship == ship) aboard.add(all.get(i));
                }
                for (Entity e : aboard) {
                    crew.releaseTask(e);
                    engine.getEcsEngine().removeEntity(e);
                }
            }
        });
        // Crews fight by their factions' wars and the player's standing; the career saves with the game
        standing = new FactionStanding(this::getPlayer);
        standing.clock = engine::getSimSeconds;
        engine.getSerializationService().addParticipant(standing);
        budget = new ServiceBudget(standing, this::getPlayer, engine::getSimSeconds);
        engine.addSystem(budget);
        crew.hostility = standing::hostile;
        docking.events.add((signal, e) -> standing.onDockingEvent(e));
        // A port door held shut for pressure opens for crew walking through it
        docking.crossing = link -> CrewDoors.crossingPort(crew, link);
        docking.clearance = (station, vessel) -> {
            if (!station.getComponent(StationComponent.class).grantsClearance) return "";
            String refused = standing.refusal(station, vessel);
            return refused != null ? refused : tensionRefusal(station, vessel);
        };
        forcing = new DoorForcing(docking, crew, engine::getSimSeconds);
        engine.addSystem(forcing);
        cargoSettlement = new SalvageSettlement(new MarketCargo(macroSim), commodities,
                this::partPrice);
        cargo = new CargoLoading(docking, crew, engine.getUuidRegistry(), engine::getSimSeconds, cargoSettlement);
        engine.addSystem(cargo);
        avatar = new AvatarInteractor(engine.getEcsEngine());
        // Who sits at the player's consoles, and who flies the ship when the player isn't at the helm
        bridge = new Bridge(this);
        engine.addSystem(bridge);

        // combat-sys
        combatEvents = new CombatEvents();
        combatDefs = CombatDefs.createDefault();
        ballistics = new BallisticsSystem(physics, combatEvents);
        // Rounds and missiles fall through the same tidal field as the Box2D bodies.
        ballistics.setTidalField(physics.getTidalField()::accelAt);
        engine.getSerializationService().addParticipant(new CombatSaveParticipant(engine));
        // stations-docking: the yards fit weapons from combat-sys's defs
        yard = new StationYard(docking, crew, engine.getUuidRegistry(), engine::getSimSeconds, combatDefs);
        engine.addSystem(yard);
        // Mission boards, and the player's contracts checked against the world; saved with the game
        contracts = new ContractBoard(this);
        engine.addSystem(contracts);
        engine.getSerializationService().addParticipant(contracts);
        engine.getSerializationService().addParticipant(guides);
        // Pods and wrecks on the rails, for rescues and recoveries; saved with the game
        derelicts = new Derelicts(this);
        // NPC freighters fill their tanks wherever they dock
        engine.addSystem(new FreighterRefueling(docking::dockedTo));
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
        // Round 7: other ships' gunners hold fire with a ship in the way (a pirate emptied its
        // magazine into a surrendered wingmate); the player's guns fire as the player sets them
        weaponControl.setHoldFire((shooter, blocker) -> shooter != getPlayer());
        // Round 9: the player's guns firing at will (point defense, crewed guns) never take a struck ship;
        // round 10: nor a fallen base's silenced battery (they went on hitting Den batteries 23 and 24)
        weaponControl.setSpared((shooter, target) -> shooter == getPlayer() && Struck.struck(this, target));
        engine.addSystem(weaponControl);
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        impacts = new ImpactResolutionSystem(physics, combatEvents, combatDefs, ballistics,
                MaterialTable.createDefault(), modules.combatSeed);
        impacts.setCrew(new CrewDamageAdapter(crew, crew));
        impacts.setEntityIds(engine.getEntityIds());
        engine.addSystem(impacts);
        // Hits beside a docking port wear its collar
        CollarWear.install(combatEvents);
        beams = new BeamSystem(physics, impacts);
        engine.addSystem(beams);
        // Ships out of power, crew or hull, or beaten, stop fighting
        disablement = new ShipDisablement(engine::getSimSeconds);
        engine.addSystem(disablement);
        // ... and their crews take to the pods; the player's pod ends in a rescue
        abandon = new AbandonShip(crew, disablement, engine::getSimSeconds, docking::dockedTo);
        engine.addSystem(abandon);
        rescue = new Rescue(this, bubble);
        // Beaten ships are boarded, taken and stripped
        boarding = new Boarding(docking, crew, forcing, standing, disablement, engine.getUuidRegistry(), engine::getSimSeconds);
        engine.addSystem(boarding);
        salvage = new Salvage(docking, crew, boarding, cargo, engine.getUuidRegistry(), combatDefs);
        engine.addSystem(salvage);
        // Ships bought at faction yards, and taken ships towed home as prizes
        shipyard = new Shipyard(this);
        engine.addSystem(shipyard);

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
        NoticeAudio.install(notices, audio);
        physics.worldShifts.add(audioUpdate.worldShiftListener());

        // Bubble bodies are saved as true states, never as live Box2D bodies.
        PhysicsSaveParticipant.install(physics, engine);
        // Pirates and faction patrols, on the rails and in the bubble
        hostiles = HostileTraffic.install(this);
        pirateBoarding = new PirateBoarding(this);
        engine.getSerializationService().addParticipant(pirateBoarding);
        pirateBase = new PirateBase(this);
        engine.addSystem(pirateBase);
        engine.getSerializationService().addParticipant(pirateBase);
        hostiles.extraRaiders = pirateBase::extraRaiders;
        tension = new FactionTension(this);
        engine.addSystem(tension);
        engine.getSerializationService().addParticipant(tension);
        yard.priceFactor = tension::priceFactor;
        nav = Navigation.install(this);
        // The loop: the career sets encounters, rescues and what boarding and beating ships are worth
        loop = LoopWiring.install(this);
        // Things that happen on their own, a day at a time, with jobs on the boards
        events = new WorldEvents(this);
        // The player's crew: hired at stations, paid weekly, learning from what they do
        crewCareer = new CrewCareer(this);
        engine.addSystem(crewCareer);
        crewHiring = new CrewHiring(this);
        engine.getSerializationService().addParticipant(crewHiring);
        prisoners = new Prisoners(this);
        hints.register(HintRegistry.Context.LATCHED, "prisoners", 3, KeyBindings.Action.CREW)
                .onlyWhen(() -> prisoners.takenAlongside() != null);
        damageControl = new DamageControl(this);
        medical = new CrewMedical(this);
        berthVenting = new BerthVenting(this);
        captain = new CaptainSafety(this);
        air = new AirWatch(this);
        infirmary = new StationInfirmary(this);
    }

    /**
     * The dice a station's daily board and hiring pool are drawn from: its name and the career's seed
     * ({@link WorldEvents#seed}), so the same seed replays the same boards and two seeds differ. A
     * station's uuid is new each game, so drawing from it alone made every career's boards differ.
     */
    public long stationDice(String stationUuid) {
        var node = macroSim.station(stationUuid);
        String key = node != null && node.displayName != null ? node.displayName : stationUuid;
        return key.hashCode() * 0x9E3779B97F4A7C15L ^ events.seed;
    }

    /**
     * Starts the combat and crew dice over from a new game's seeds, as if the world had been built
     * with them: sensors, fire control and damage from {@code combatSeed} (the same offsets as
     * {@link Modules#combatSeed}), the crew's own draws from {@code crewSeed}.
     */
    public void reseed(long combatSeed, long crewSeed) {
        sensors.reseed(combatSeed + 1);
        weaponControl.reseed(combatSeed + 2);
        impacts.reseed(combatSeed);
        crew.random.setSeed(crewSeed);
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
        // Docking ports come from the chassis the interior was built from.
        bootstrap.beforeScripts(DockingData::install);
        // Ships collide on their hull art's collision layer, else on their layout
        bootstrap.beforeScripts(ctx -> ShipPhysicsAssembler.register(ctx.factory, physics, gridMass(ctx),
                hulls = new ShipHulls(assetsDir, ctx)));
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
        standing.setData(Factions.from(content.defs), RankLadder.from(content.defs));
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

    /** Clearance refused for tension between the factions (built after docking's clearance check is). */
    private String tensionRefusal(Entity station, Entity vessel) {
        return tension.refusal(station, vessel);
    }

    /** What {@link #loadContent} produced, or null before it ran. */
    public ModContext getContent() {
        return content;
    }

    /** One rendered frame. Once the player has crashed the world stops. */
    public void update(float frameDelta) {
        if (crash != null || careerOver != null) return;
        TickProfiler profiler = engine.getProfiler();
        long start = profiler == null ? 0 : System.nanoTime();
        timeWarp.update(frameDelta);
        if (profiler != null) profiler.record("frame/TimeWarpController", System.nanoTime() - start);
        engine.update(frameDelta);
        if (loadSettling > 0) {
            Entity player = getPlayer();
            boolean welding = player != null && docking.dockedTo(player) != null && !docking.isRigid(player);
            loadSettling = welding ? loadSettling - 1 : 0;
        }
        for (Entity wreck : wrecks) engine.removeEntity(wreck);
        wrecks.clear();
        rescue.update();
        hostiles.afterUpdate();
        pirateBoarding.update();
        pirateBase.afterUpdate();
        derelicts.update();
        events.update();
        checkBubbleCrash();
        checkAvatar();
        if ((crash != null || careerOver != null) && timeWarp.getWarp() != 1) timeWarp.setWarp(1);
        warnings.update();
        damageControl.update();
        medical.update();
        berthVenting.update();
        captain.update();
        air.update();
        infirmary.update();
        // Warp changes only between updates: the autopilot raises it again after a burn
        nav.autopilot.afterUpdate();
    }

    /** Why rails warp waits after a load: until a frame has run and the player's ship is welded to its dock again. */
    private String loadingRefusal() {
        return loadSettling > 0 ? "the game is still loading" : null;
    }

    /** A save was just loaded: rails warp waits until its bodies and the player's latch are back. */
    void loaded() {
        loadSettling = LOAD_SETTLE_FRAMES;
    }

    /** How the player's ship was lost, or null while it still flies. */
    public Crash getCrash() {
        return crash;
    }

    /** Lets the world run again, after loading a save from before the crash. */
    public void clearCrash() {
        crash = null;
        careerOver = null;
    }

    /** Why the career ended with the player's death, or null while it goes on. The world stops then. */
    public String getCareerOver() {
        return careerOver;
    }

    /** Fires when the player was killed and the crew brought them home. */
    public final com.badlogic.ashley.signals.Signal<Rescue.Tow> carriedHome = new com.badlogic.ashley.signals.Signal<>();

    /** The player's avatar killed: brought home by the crew if any are left, else the career ends. */
    private void checkAvatar() {
        Entity player = getPlayer();
        if (player == null || crash != null || careerOver != null) return;
        for (Entity e : crew.crewEntities()) {
            if (e.getComponent(com.ringsofiron.aicrew.PlayerAvatarComponent.class) == null) continue;
            if (e.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class).isAlive()) return;
            Rescue.Tow[] tow = new Rescue.Tow[1];
            com.badlogic.ashley.signals.Listener<Rescue.Tow> l = (signal, t) -> tow[0] = t;
            rescue.tows.add(l);
            String why = rescue.carryHome(player);
            rescue.tows.remove(l);
            if (why != null) careerOver = why;
            else carriedHome.dispatch(tow[0]);
            return;
        }
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
        // Crew ordered to the pods before impact get away: whoever reached a pod launches, and if
        // the player was among them it is a rescue, not a lost ship
        Entity player = getPlayer();
        if (player != null && AbandonShip.abandoning(player)) {
            abandon.launchNow(player);
            abandon.update(0f);
            if (rescue.pending()) {
                rescue.update();
                return;
            }
        }
        CelestialBodyComponent c = body == null ? null : universe.celestial(body);
        crash = new Crash(c == null || c.name == null ? "the surface" : c.name, time);
    }

    /** The player's primary actor (their ship), or null. */
    /** The station the player's ship is docked at with clearance, or null. */
    public Entity playerStation() {
        Entity player = getPlayer();
        if (player == null || !docking.dockedWithClearance(player)) return null;
        Entity station = docking.dockedTo(player);
        UuidComponent u = station == null ? null : station.getComponent(UuidComponent.class);
        return u == null || macroSim.station(u.id) == null ? null : station;
    }

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
     * cargo in its bays and what its tanks hold. Null for a ship without a grid, or before content has loaded.
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
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) TankSupply.addMassTo(grid, registry, m);
        return m;
    }

    /**
     * Gives a ship's body the collision shape of its current layout (see {@link ShipHulls}), as after
     * a yard rebuilds its decks. Its mass is left to the caller. Returns false if nothing changed:
     * no body, no grid, or content not loaded.
     */
    public boolean rebuildHull(Entity ship) {
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        if (hulls == null || pb == null || pb.body == null) return false;
        return PhysicsManager.replacePolygonFixtures(pb.body, hulls.hullFor(ship, null));
    }

    /**
     * A Box2D body for a ship with a layout, at the bubble origin: shaped as the physics assembler
     * shapes it ({@link ShipHulls}) and weighing what {@link #shipMass} says. Null for a craft
     * without a grid, or before content has loaded.
     */
    public Body buildShipBody(Entity ship) {
        ShipGridComponent.MassProperties m = shipMass(ship);
        java.util.List<float[]> hull = hulls == null || m == null ? null : hulls.hullFor(ship, null);
        if (hull == null || hull.isEmpty()) return null;
        Body body = physics.createPolygonBody(ship, 0f, 0f, 0f, hull, 1f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        PhysicsManager.setMassData(body, m.mass, m.center.x, m.center.y, m.inertia);
        return body;
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

    /** What a station pays for a salvaged part. */
    private double partPrice(String part) {
        return salvage == null ? 0 : salvage.salePrice(part);
    }

    private void mergeCombatDefs(CombatDefs loaded) {
        combatDefs.weapons.putAll(loaded.weapons);
        combatDefs.projectiles.putAll(loaded.projectiles);
        combatDefs.missiles.putAll(loaded.missiles);
        combatDefs.warheads.putAll(loaded.warheads);
        combatDefs.loadouts.putAll(loaded.loadouts);
        combatDefs.materials.putAll(loaded.materials);
    }
}

