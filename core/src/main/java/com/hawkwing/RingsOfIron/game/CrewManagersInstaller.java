package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * The managers in {@link GameWorld} that watch over the player's people and tell them what befalls
 * them: hull leaks, the wounded, venting berths, spacewalks, jammed collars, field refits, the
 * captain's suit, the air, and station infirmaries.
 */
final class CrewManagersInstaller {
    final DamageControl damageControl;
    final CrewMedical medical;
    final BerthVenting berthVenting;
    final Spacewalks spacewalks;
    final JammedCollars jammedCollars;
    final FieldRefits fieldRefits;
    final CaptainSafety captain;
    final AirWatch air;
    final StationInfirmary infirmary;

    CrewManagersInstaller(GameWorld world) {
        GameContext context = world.context;
        CrewService crew = world.crew;
        DockingSystem docking = world.docking;
        damageControl = new DamageControl(context);
        medical = new CrewMedical(context, crew, world.boarding, world.combatEvents, world.prisoners);
        berthVenting = new BerthVenting(context, crew, docking, world.yard);
        spacewalks = new Spacewalks(context, crew, world.crewCareer, world.physics);
        jammedCollars = new JammedCollars(context, crew);
        fieldRefits = new FieldRefits(context, crew, world.combatDefs);
        captain = new CaptainSafety(context, crew);
        air = new AirWatch(context, crew, captain);
        infirmary = new StationInfirmary(context, crew, docking, world.yard);
    }
}
