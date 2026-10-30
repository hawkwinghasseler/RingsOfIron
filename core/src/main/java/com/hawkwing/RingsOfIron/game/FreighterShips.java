package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.moddingdata.DefEntry;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * macro-sim's freighters as real ships: each is built into a {@link GameScenario#FREIGHTER_SHIP}
 * (the Hauler: its interior, systems, ports and cargo bays) with a crew of Free Traders, the first
 * time it comes into the bubble. macro-sim makes them as bare entities, and on the rails that is
 * all they need, so one that never comes near the player costs nothing.
 *
 * <p>Its cargo bays hold what macro-sim says it carries ({@link FreighterComponent#cargoCommodity}).
 * Docked at a station in the bubble, the goods it buys or sells are carried across by hand
 * ({@link FreighterTraffic}); anywhere else the bays are simply kept matching ({@link #stow}).
 */
public final class FreighterShips {
    /** The crew a Hauler flies with: two to carry cargo and an engineer. */
    static final CrewRole[] CREW_ROLES = {CrewRole.CREWMAN, CrewRole.ENGINEER, CrewRole.CREWMAN};
    /** The crew faction freighters fly for: the Free Traders, as the factions work names them. */
    static final String TRADERS = HostileTraffic.TRADERS;

    private FreighterShips() {
    }

    /**
     * Builds {@code freighter} into a Hauler if it is a freighter with no interior yet: what the
     * entity factory gives a Hauler spawned from data, less its body (the bubble builds that), and
     * its crew. Its bays are stocked with the cargo it carries. True if it was built.
     */
    public static boolean outfit(GameWorld world, Entity freighter) {
        ModContext content = world.getContent();
        FreighterComponent f = freighter.getComponent(FreighterComponent.class);
        if (f == null || content == null || freighter.getComponent(ShipGridComponent.class) != null) return false;
        DefEntry def = content.defs.entry("ship", GameScenario.FREIGHTER_SHIP);
        if (def == null) return false;
        if (freighter.getComponent(TransformComponent.class) == null) freighter.add(new TransformComponent(0f, 0f, 0f));
        freighter.add(new DefRefComponent("ship", GameScenario.FREIGHTER_SHIP));
        for (var assembler : content.factory.assemblers("ship")) {
            if (!(assembler instanceof ShipPhysicsAssembler)) {
                assembler.assemble(freighter, def, content.factory);
                continue;
            }
            // Its thrusters and controls, but not the body the physics assembler makes with them: the
            // bubble builds the one it comes in on. Made on a stand-in the engine never sees, sharing
            // its layout, so no system ever takes the body for the freighter's and it goes at once
            Entity standIn = new Entity();
            standIn.add(freighter.getComponent(ShipGridComponent.class));
            standIn.add(freighter.getComponent(DefRefComponent.class));
            assembler.assemble(standIn, def, content.factory);
            PhysicsBodyComponent pb = standIn.getComponent(PhysicsBodyComponent.class);
            if (pb != null && pb.body != null) world.physics.destroyBody(pb.body);
            ThrusterComponent thrusters = standIn.getComponent(ThrusterComponent.class);
            FlightControlComponent controls = standIn.getComponent(FlightControlComponent.class);
            if (thrusters != null) freighter.add(thrusters);
            if (controls != null) freighter.add(controls);
        }
        ShipConsoles.install(freighter);
        int faction = world.standing.factions().index(TRADERS);
        if (faction < 0) faction = Factions.FIRST;
        CrewFactory.ensureBoard(freighter).faction = faction;
        world.standing.factions().crewHabits(CrewFactory.ensureBoard(freighter));
        ShipDef ship = content.defs.get("ship", GameScenario.FREIGHTER_SHIP, ShipDef.class);
        List<int[]> floor = GameScenario.floorTiles(freighter.getComponent(ShipGridComponent.class));
        int crew = ship == null ? 0 : Math.min(ship.crew, floor.size());
        for (int i = 0; i < crew; i++) {
            int[] t = floor.get(i * floor.size() / crew);
            CrewFactory.spawn(world.crew, freighter, "Crew " + (i + 1), CREW_ROLES[i % CREW_ROLES.length], faction,
                    t[0], t[1]);
        }
        stow(world, freighter);
        return true;
    }

    /**
     * Makes {@code freighter}'s bays hold just what macro-sim says it carries, at once: anything
     * else unloaded, its cargo loaded or unloaded to the amount. Nothing for a ship without bays.
     */
    public static void stow(GameWorld world, Entity freighter) {
        stow(world.macroSim, freighter);
    }

    /** {@link #stow(GameWorld, Entity)}, from {@code macroSim}'s catalog. */
    public static void stow(MacroSimulation macroSim, Entity freighter) {
        FreighterComponent f = freighter.getComponent(FreighterComponent.class);
        CargoHoldComponent hold = freighter.getComponent(CargoHoldComponent.class);
        if (f == null || hold == null) return;
        for (String k : commodities(hold)) {
            if (!k.equals(f.cargoCommodity)) hold.unload(k, hold.amount(k));
        }
        double want = f.cargoCommodity == null ? 0 : f.cargoQuantity, have = f.cargoCommodity == null ? 0 : hold.amount(f.cargoCommodity);
        if (have > want) {
            hold.unload(f.cargoCommodity, have - want);
        } else if (have < want) {
            CommodityType c = macroSim.economy().catalog().commodity(f.cargoCommodity);
            if (c != null) hold.load(f.cargoCommodity, want - have, c.unitVolume, c.unitMass);
        }
    }

    /** The commodities in {@code hold}'s stacks. */
    static Set<String> commodities(CargoHoldComponent hold) {
        Set<String> out = new HashSet<>();
        for (CargoHoldComponent.CargoStack s : hold.stacks) {
            if (s != null && s.commodity != null && s.units > 0) out.add(s.commodity);
        }
        return out;
    }
}
