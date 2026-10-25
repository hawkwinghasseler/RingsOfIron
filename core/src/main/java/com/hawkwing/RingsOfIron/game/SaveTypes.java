package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.coreengine.SerializationService;

/**
 * The stable type id each saved component is written under, in place of its Java class name, so a
 * class can be renamed or moved to another package without breaking saves (core-engine registers
 * its own). Saves made before ids (format 1, keyed by class name) still load.
 *
 * <p>Every component that ends up in a save needs a line here: SaveTypesTest fails on one that has
 * none. Once a save has been made with an id it must never change or be reused. A component class
 * renamed before it had an id needs {@link SerializationService#registerAlias} with its old name.
 * Components the game never saves (skipped, rebuilt after a load) have none.
 */
public final class SaveTypes {
    private SaveTypes() {
    }

    public static void register(SerializationService s) {
        s.registerType("crew.CrewMember", com.ringsofiron.aicrew.CrewMemberComponent.class);
        s.registerType("crew.GridPosition", com.ringsofiron.aicrew.GridPositionComponent.class);
        s.registerType("crew.PlayerAvatar", com.ringsofiron.aicrew.PlayerAvatarComponent.class);
        s.registerType("crew.ShipTaskBoard", com.ringsofiron.aicrew.ShipTaskBoardComponent.class);
        s.registerType("crew.NpcCombat", com.ringsofiron.aicrew.pilot.NpcCombatComponent.class);
        s.registerType("crew.NpcPilot", com.ringsofiron.aicrew.pilot.NpcPilotComponent.class);

        s.registerType("audio.AudioEmitter", com.ringsofiron.audiospatial.components.AudioEmitterComponent.class);
        s.registerType("audio.AudioListener", com.ringsofiron.audiospatial.components.AudioListenerComponent.class);

        s.registerType("combat.Decoy", com.ringsofiron.combatsys.components.DecoyComponent.class);
        s.registerType("combat.Missile", com.ringsofiron.combatsys.components.MissileComponent.class);
        s.registerType("combat.Projectile", com.ringsofiron.combatsys.components.ProjectileComponent.class);
        s.registerType("combat.Sensor", com.ringsofiron.combatsys.components.SensorComponent.class);
        s.registerType("combat.Signature", com.ringsofiron.combatsys.components.SignatureComponent.class);
        s.registerType("combat.WeaponMount", com.ringsofiron.combatsys.components.WeaponMountComponent.class);
        s.registerType("combat.Weapons", com.ringsofiron.combatsys.components.WeaponsComponent.class);

        s.registerType("game.AbandonOrder", AbandonOrderComponent.class);
        s.registerType("game.Autopilot", AutopilotComponent.class);
        s.registerType("game.Boarded", BoardedComponent.class);
        s.registerType("game.CargoOrders", CargoOrdersComponent.class);
        s.registerType("game.Disabled", DisabledComponent.class);
        s.registerType("game.FactionStanding", FactionStandingComponent.class);
        s.registerType("game.Helm", HelmComponent.class);
        s.registerType("game.HelmOrder", HelmOrderComponent.class);
        s.registerType("game.Morale", MoraleComponent.class);
        s.registerType("game.NavTarget", NavTargetComponent.class);
        s.registerType("game.SalvageOrders", SalvageOrdersComponent.class);
        s.registerType("game.Salvaged", SalvagedComponent.class);
        s.registerType("game.Seat", SeatComponent.class);
        s.registerType("game.ShipLayout", ShipLayoutComponent.class);
        s.registerType("game.Survivor", SurvivorComponent.class);
        s.registerType("game.TransferLeg", TransferLegComponent.class);
        s.registerType("game.YardOrders", YardOrdersComponent.class);

        s.registerType("grid.CargoHold", com.ringsofiron.interiorgrid.CargoHoldComponent.class);
        s.registerType("grid.ShipGrid", com.ringsofiron.interiorgrid.ShipGridComponent.class);
        s.registerType("grid.ShipSubsystemRegistry", com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class);
        s.registerType("grid.Subsystem", com.ringsofiron.interiorgrid.SubsystemComponent.class);

        s.registerType("macro.Trader", com.ringsofiron.macrosim.economy.TraderComponent.class);
        s.registerType("macro.Hostile", com.ringsofiron.macrosim.hostile.HostileComponent.class);

        s.registerType("mod.DefRef", com.ringsofiron.moddingdata.components.DefRefComponent.class);

        s.registerType("orbital.CelestialBody", com.ringsofiron.orbital.components.CelestialBodyComponent.class);
        s.registerType("orbital.ManeuverNode", com.ringsofiron.orbital.components.ManeuverNodeComponent.class);
        s.registerType("orbital.Orbit", com.ringsofiron.orbital.components.OrbitComponent.class);

        s.registerType("physics.BodyShape", com.ringsofiron.physicsexterior.components.BodyShapeComponent.class);
        s.registerType("physics.FlightControl", com.ringsofiron.physicsexterior.components.FlightControlComponent.class);
        s.registerType("physics.Thruster", com.ringsofiron.physicsexterior.components.ThrusterComponent.class);

        s.registerType("docking.Berthing", com.ringsofiron.stationsdocking.BerthingComponent.class);
        s.registerType("docking.DockedRide", com.ringsofiron.stationsdocking.DockedRideComponent.class);
        s.registerType("docking.DockingPorts", com.ringsofiron.stationsdocking.DockingPortsComponent.class);
        s.registerType("docking.Station", com.ringsofiron.stationsdocking.StationComponent.class);

        s.registerType("hud.HudMarker", com.ringsofiron.uihud.components.HudMarkerComponent.class);
        s.registerType("hud.InteractableConsole", com.ringsofiron.uihud.components.InteractableConsoleComponent.class);
    }
}
