package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.shipops.CollarWear;
import com.hawkwing.RingsOfIron.wiring.CrewDamageAdapter;
import com.hawkwing.RingsOfIron.wiring.CrewStationSync;
import com.ringsofiron.shipops.FreighterRefueling;
import com.ringsofiron.shipsystems.ShipWeaponPower;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.StationYard;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.save.CombatSaveParticipant;
import com.ringsofiron.combatsys.shields.ShieldSystem;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.BeamSystem;
import com.ringsofiron.combatsys.systems.CollisionDamageSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.combatsys.systems.MissileGuidanceSystem;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * combat-sys in {@link GameWorld}, with the yards, boards and derelicts that sit between its ballistics
 * and its fire control in the run order: sensors, fire control, missiles, ballistics and impact damage,
 * then the ships that are beaten and their crews taking to the pods.
 */
final class CombatInstaller {
    final CombatEvents combatEvents;
    final CombatDefs combatDefs;
    final BallisticsSystem ballistics;
    final StationYard yard;
    final ContractBoard contracts;
    final Derelicts derelicts;
    final SensorTrackingSystem sensors;
    final MissileGuidanceSystem missiles;
    final WeaponControlSystem weaponControl;
    final GunCrews gunCrews;
    final ImpactResolutionSystem impacts;
    final CollisionDamageSystem collisionDamage;
    final BeamSystem beams;
    final ShipDisablement disablement;
    final AbandonShip abandon;

    CombatInstaller(GameWorld world, GameWorld.Modules modules) {
        GameEngine engine = world.engine;
        PhysicsManager physics = world.physics;
        DockingSystem docking = world.docking;
        CrewService crew = world.crew;
        GameContext context = world.context;
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
        contracts = new ContractBoard(context, () -> world.getCrash() != null, crew, physics, world.universe,
                world.macroSim, docking, world.standing, world::stationDice, () -> world.crewHiring,
                () -> world.derelicts, () -> world.pirateBase, () -> world.tension);
        engine.addSystem(contracts);
        engine.getSerializationService().addParticipant(contracts);
        world.addResettable(contracts);
        engine.getSerializationService().addParticipant(world.guides);
        // ...outside First Play and the opening walkthrough
        world.addResettable(world.guides);
        // Pods and wrecks on the rails, for rescues and recoveries; saved with the game
        derelicts = new Derelicts(context, physics, world.universe, world.macroSim, contracts);
        world.addResettable(derelicts);
        // NPC freighters fill their tanks wherever they dock
        engine.addSystem(new FreighterRefueling(docking::dockedTo));
        // Each physics tick: sensors fix, gunners aim and fire, missiles steer, then rounds fly
        // and hit, then hits resolve and beams burn. Their step listeners run in the order added here.
        sensors = new SensorTrackingSystem(physics, modules.combatSeed + 1);
        missiles = new MissileGuidanceSystem(physics, combatEvents, combatDefs, ballistics, sensors);
        weaponControl = new WeaponControlSystem(physics, combatEvents, combatDefs, ballistics, missiles, sensors,
                modules.combatSeed + 2);
        // The crew aboard set each ship's gunnery and tracking skill; on the player's ship, gun by gun,
        // with whoever mans each gun or the weapons console, and those guns fire by themselves
        CrewStationSync stations = new CrewStationSync();
        stations.gunsByGun = ship -> ship == world.getPlayer();
        engine.addSystem(stations);
        gunCrews = new GunCrews(context, world.avatar, world.bridge, docking, sensors, weaponControl,
                () -> world.pirateBase);
        engine.addSystem(gunCrews);
        engine.addSystem(sensors);
        // Shots and beams draw on the ship's power circuits.
        weaponControl.setPower(new ShipWeaponPower());
        // Round 7: other ships' gunners hold fire with a ship in the way (a pirate emptied its
        // magazine into a surrendered wingmate); the player's crew, firing at will, hold for any
        // but a hostile one
        weaponControl.setHoldFire((shooter, blocker) -> shooter != world.getPlayer()
                || !GunCrews.hostile(world.getPlayer(), world.pirateBase, blocker));
        // Round 9: the player's guns firing at will (point defense, crewed guns) never take a struck ship;
        // round 10: nor a fallen base's silenced battery (they went on hitting Den batteries 23 and 24)
        weaponControl.setSpared((shooter, target) -> shooter == world.getPlayer() && Struck.struck(world.pirateBase, target));
        // A struck ship gives way to whoever docks: no clearance to ask, and no door to force before boarding
        docking.yields = e -> e != world.getPlayer() && Struck.struck(world.pirateBase, e);
        // Round 10: nor do its missiles' seekers lock a fallen base's battery they weren't fired at
        // (part 6's window: a heavy missile fired at a raider burst on Den battery 23). Only the
        // batteries: sparing surrendered ships too moved fights before the frigate (seed 2026's ace)
        missiles.setSpared((shooter, target) -> shooter == world.getPlayer()
                && Struck.silencedBattery(world.pirateBase, target));
        engine.addSystem(weaponControl);
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        // Shield clouds gather where rounds in flight will cross them, before this tick's hits resolve
        engine.addSystem(new ShieldSystem(physics));
        // Shields go down for docking, and back up after
        engine.addSystem(new ShieldDocking(context, docking));
        impacts = new ImpactResolutionSystem(physics, combatEvents, combatDefs, ballistics,
                MaterialTable.createDefault(), modules.combatSeed);
        impacts.setCrew(new CrewDamageAdapter(crew, crew));
        impacts.setEntityIds(engine.getEntityIds());
        engine.addSystem(impacts);
        // Hulls that meet too fast damage each other
        collisionDamage = new CollisionDamageSystem(physics, combatEvents, impacts);
        engine.addSystem(collisionDamage);
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
    }
}
