package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.shipops.CargoLoading;
import com.ringsofiron.shipops.DoorForcing;
import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.SalvageSettlement;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.StationYard;
import com.ringsofiron.macrosim.economy.SwitchableCommodityCatalog;
import com.hawkwing.RingsOfIron.wiring.AudioListenerSync;
import com.hawkwing.RingsOfIron.wiring.AvatarInteractor;
import com.hawkwing.RingsOfIron.wiring.CombatAudio;
import com.hawkwing.RingsOfIron.wiring.CombatThreatSensor;
import com.hawkwing.RingsOfIron.wiring.CrewAudio;
import com.hawkwing.RingsOfIron.wiring.CrewDamageAdapter;
import com.hawkwing.RingsOfIron.wiring.CrewStationSync;
import com.hawkwing.RingsOfIron.wiring.NoticeAudio;
import com.hawkwing.RingsOfIron.wiring.NpcBubblePilots;
import com.ringsofiron.aicrew.pilot.TrueStatePilotReference;
import com.ringsofiron.audiospatial.systems.EngineAudio;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.Factions;
import com.ringsofiron.career.GuideProgress;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.career.ServiceBudget;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.input.Action;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.pilot.PilotReference;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.SoundEventDef;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.modding.ShipWeaponsAssembler;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.BeamSystem;
import com.ringsofiron.combatsys.systems.CollisionDamageSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.combatsys.systems.MissileGuidanceSystem;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.TickProfiler;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.bubble.GameBubbleGateway;
import com.ringsofiron.macrosim.economy.DefRegistryCommodityCatalog;
import com.ringsofiron.macrosim.economy.DefaultCommodityCatalog;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.ModTrustPolicy;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.moddingdata.defs.SoundEventData;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.OrbitalAnchorBridge;
import com.ringsofiron.physicsexterior.RailsBubbleHandoff;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingSystem;
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
    /** Hulls meeting faster than 25 m/s damage each other (combat-sys/Design.md 5.12). */
    public final CollisionDamageSystem collisionDamage;
    /** Finds what each burning beam touches and burns it. */
    public final BeamSystem beams;
    public final AudioManager audio;
    public final AvatarInteractor avatar;
    /** The player's seat at the helm or weapons console, and the helm flying the ship meanwhile. */
    public final Bridge bridge;
    /** Who works the player's guns, and whether those guns fire by themselves. */
    public final GunCrews gunCrews;
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
    /** Furniture and decor yards sell, carried aboard and set down where the player puts it. */
    public final FurnishingStore furnishings;
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
    /** The engine, the player's ship, the notices and the key hints: what most managers read. */
    public final GameContext context;
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
    /** The player's crew out on EVA: warp waits for them, and notices say what befalls them. */
    public final Spacewalks spacewalks;
    /** Docking collars jammed by hits, and the crew's work outside to free them. */
    public final JammedCollars jammedCollars;
    /** Salvaged weapons the crew mount on the ship's hardpoints from outside, away from a yard. */
    public final FieldRefits fieldRefits;
    /** The player's figure suiting up at battle stations and in thin air. */
    public final CaptainSafety captain;
    /** Warnings before the player's people run out of air. */
    public final AirWatch air;
    /** A station's infirmary, treating the player's wounded at its berth (round 8). */
    public final StationInfirmary infirmary;
    /** The distress beacon: a stranded ship towed home. */
    public final DistressBeacon beacon;
    /** First Play's and the opening walkthrough's progress, saved with the game. */
    public final GuideProgress guides = new GuideProgress();

    private final ImmutableArray<Entity> players;
    private final OrbitalAnchorBridge anchorBridge;
    private final Modules modules;
    final SwitchableCommodityCatalog commodities = new SwitchableCommodityCatalog(new DefaultCommodityCatalog());
    private ModContext content;
    /** Ships' bodies and mass from their layouts and the loaded data. */
    private final ShipFactory ships;
    private Crash crash;
    /** Frames after a load that rails warp still waits for the player's latch to weld again. */
    static final int LOAD_SETTLE_FRAMES = 120;
    /** Frames left of that wait: from a load until a frame has run and the player's latch is welded again. */
    private int loadSettling;
    private String careerOver;
    /** After every other fixed system of the tick (they all run below 100): see {@link #tickManagers}. */
    static final int MANAGERS_PRIORITY = 500;
    private final List<Entity> wrecks = new ArrayList<>();
    /** What a load starts again from scratch before the save is read, in the order registered. */
    private final List<Resettable> resettables = new ArrayList<>();
    private final double[] trueState = new double[4];

    public GameWorld() {
        this(new Modules());
    }

    public GameWorld(Modules modules) {
        this.modules = modules;
        engine = new GameEngine();
        SaveTypes.register(engine.getSerializationService());
        players = engine.getEcsEngine().getEntitiesFor(PLAYER);
        notices = new NoticeLog(engine::getSimSeconds);
        // The old game's notices are not this one's (the log is not saved)
        addResettable(notices::clear);
        context = new GameContext(engine, this::getPlayer, this::getContent, notices, hints);
        warnings = new FlightWarnings(context);
        physics = new PhysicsManager();
        // Each area in run order: the order systems, step listeners and save participants are added
        // is the order they run (SystemOrderTest pins it). Each area reads the fields set before it.
        FlightInstaller.install(this, modules);
        DockingInstaller dock = new DockingInstaller(this);
        docking = dock.docking;
        berthing = dock.berthing;
        OrbitInstaller orbit = new OrbitInstaller(this, modules);
        universe = orbit.universe;
        orbitalEvents = orbit.orbitalEvents;
        anchorBridge = orbit.anchorBridge;
        timeWarp = orbit.timeWarp;
        burns = orbit.burns;
        macroSim = MacroSimInstaller.install(this);
        InteriorInstaller.install(this);
        CrewInstaller crews = new CrewInstaller(this);
        crew = crews.crew;
        standing = crews.standing;
        budget = crews.budget;
        forcing = crews.forcing;
        cargoSettlement = crews.cargoSettlement;
        cargo = crews.cargo;
        avatar = crews.avatar;
        bridge = crews.bridge;
        ships = new ShipFactory(physics, this::getContent, standing, crew, macroSim);
        CombatInstaller combat = new CombatInstaller(this, modules);
        combatEvents = combat.combatEvents;
        combatDefs = combat.combatDefs;
        ballistics = combat.ballistics;
        yard = combat.yard;
        contracts = combat.contracts;
        derelicts = combat.derelicts;
        sensors = combat.sensors;
        missiles = combat.missiles;
        weaponControl = combat.weaponControl;
        gunCrews = combat.gunCrews;
        impacts = combat.impacts;
        collisionDamage = combat.collisionDamage;
        beams = combat.beams;
        disablement = combat.disablement;
        abandon = combat.abandon;
        AftermathInstaller aftermath = new AftermathInstaller(this, orbit.bubble);
        rescue = aftermath.rescue;
        beacon = aftermath.beacon;
        boarding = aftermath.boarding;
        salvage = aftermath.salvage;
        shipyard = aftermath.shipyard;
        furnishings = aftermath.furnishings;
        AudioInstaller sound = new AudioInstaller(this, modules);
        audio = sound.audio;
        audioListener = sound.audioListener;
        engineAudio = sound.engineAudio;
        crewAudio = sound.crewAudio;
        PirateInstaller pirates = new PirateInstaller(this);
        hostiles = pirates.hostiles;
        pirateBoarding = pirates.pirateBoarding;
        pirateBase = pirates.pirateBase;
        tension = pirates.tension;
        nav = Navigation.install(this);
        CareerInstaller career = new CareerInstaller(this);
        loop = career.loop;
        events = career.events;
        crewCareer = career.crewCareer;
        crewHiring = career.crewHiring;
        prisoners = career.prisoners;
        CrewManagersInstaller managers = new CrewManagersInstaller(this);
        damageControl = managers.damageControl;
        medical = managers.medical;
        berthVenting = managers.berthVenting;
        spacewalks = managers.spacewalks;
        jammedCollars = managers.jammedCollars;
        fieldRefits = managers.fieldRefits;
        captain = managers.captain;
        air = managers.air;
        infirmary = managers.infirmary;
        // The managers that keep time or set what the crew act on run every sim tick, after the
        // tick's other systems, not once a frame after however many ticks the frame ran
        engine.addSystem(new TickManagers());
    }

    /**
     * Registers state a load starts again from scratch ({@link #resetForLoad()}). Systems built here
     * register next to their {@code addParticipant}; one built elsewhere registers itself the same way.
     */
    public void addResettable(Resettable r) {
        resettables.add(r);
    }

    /** Starts every registered {@link Resettable} again from scratch, before a save is read over them. */
    public void resetForLoad() {
        for (Resettable r : resettables) r.reset();
    }

    /** What {@link #resetForLoad()} resets, for tests. */
    List<Resettable> resettablesForTest() {
        return java.util.Collections.unmodifiableList(resettables);
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
        bootstrap.beforeScripts(ctx -> ships.registerAssembler(ctx, assetsDir));
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
    String tensionRefusal(Entity station, Entity vessel) {
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
        // What follows changes the world between updates, with the engine idle: ships spawned and
        // removed (wrecks, raids, the day's events), the bubble collapsed and rebuilt for a rescue,
        // time warp set. The managers that keep time run on the tick instead (tickManagers)
        for (Entity wreck : wrecks) engine.removeEntity(wreck);
        wrecks.clear();
        rescue.update();
        hostiles.afterUpdate();
        pirateBase.afterUpdate();
        derelicts.update();
        spacewalks.update();
        jammedCollars.update();
        fieldRefits.update();
        events.update();
        checkBubbleCrash();
        checkAvatar();
        if ((crash != null || careerOver != null) && timeWarp.getWarp() != 1) timeWarp.setWarp(1);
        // Notices on how things stand now: once a frame is soon enough
        warnings.update();
        damageControl.update();
        air.update();
        // Warp changes only between updates: the autopilot raises it again after a burn
        nav.autopilot.afterUpdate();
    }

    /**
     * Once a sim tick, after every other fixed system (the crew have moved, the air has spread):
     * the managers whose timers count sim time or whose state the crew act on the next tick. Run
     * once a frame they saw up to 150 ticks at once at 10x warp, and a frame's worth of battle
     * stations or suit time depended on the frame rate.
     */
    private void tickManagers() {
        if (crash != null || careerOver != null) return;
        pirateBoarding.update();
        medical.update();
        berthVenting.update();
        captain.update();
        infirmary.update();
    }

    /** Runs {@link #tickManagers}; a named class so the tick profiler names it. */
    private final class TickManagers extends com.ringsofiron.coreengine.systems.FixedLogicSystem {
        TickManagers() {
            super(MANAGERS_PRIORITY, (float) com.ringsofiron.coreengine.SimClock.TICK_SECONDS);
        }

        @Override
        protected void updateFixed(float deltaTime) {
            tickManagers();
        }
    }

    /** Why rails warp waits after a load: until a frame has run and the player's ship is welded to its dock again. */
    String loadingRefusal() {
        return loadSettling > 0 ? "the game is still loading" : null;
    }

    /** A save was just loaded: rails warp waits until its bodies and the player's latch are back. */
    void loaded() {
        loadSettling = LOAD_SETTLE_FRAMES;
        captain.loaded();
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
    void onImpact(OrbitalEvent event) {
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
        return ships.shipMass(ship);
    }

    /** {@link ShipFactory#rebuildHull}: a ship's body shaped as its current layout, after a yard rebuilds its decks. */
    public boolean rebuildHull(Entity ship) {
        return ships.rebuildHull(ship);
    }

    /** {@link ShipFactory#buildShipBody}: a Box2D body for a ship with a layout, at the bubble origin. */
    public Body buildShipBody(Entity ship) {
        return ships.buildShipBody(ship);
    }

    /** What a station pays for a salvaged part. */
    double partPrice(String part) {
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

