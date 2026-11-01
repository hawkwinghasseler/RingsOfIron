package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Tanks in the ship grid, the pipes that reach them, and life support drawing on them. */
class PipingTest {

    private static final float DT = GridHazardSystem.HAZARD_TICK;

    private final Engine engine = new Engine();
    private final GridHazardSystem hazards = new GridHazardSystem(1);
    private final LifeSupportSystem lifeSupport = new LifeSupportSystem();
    private ShipGridComponent grid;
    private ShipSubsystemRegistryComponent registry;

    /**
     * One 9 x 4 room: life support at (1, 1)-(1, 2), an oxygen tank at (7, 1)-(7, 2) and a
     * nitrogen tank at (8, 1)-(8, 2), with a reactor so the unit has power.
     */
    private static ChassisBlueprint room() {
        return new ChassisBlueprint(
                "###########",
                "#.........#",
                "#.........#",
                "#.........#",
                "###########")
                .mount("life_support", MountFootprint.MountSize.S, 1, 1)
                .mount("oxygen_tank", MountFootprint.MountSize.S, 7, 1)
                .mount("nitrogen_tank", MountFootprint.MountSize.S, 8, 1)
                .mount("reactor", MountFootprint.MountSize.S, 1, 3)
                .conduitRun(1, 2, 1, 3);
    }

    /** Pipe across the floor from the life support unit to both tanks, along y = 1. */
    private static ChassisBlueprint piped(ChassisBlueprint bp) {
        for (int x = 1; x <= 8; x++) bp.pipe(x, 1);
        return bp;
    }

    private void build(ChassisBlueprint bp) {
        engine.addSystem(new PowerRoutingSystem());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Entity ship = new Entity();
        registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        grid = r.grid;
        hazards.step(grid, DT);
        reroute();
        engine.addSystem(lifeSupport);
    }

    private void reroute() {
        grid.gridDirty = true;
        engine.update(0f);
    }

    private SubsystemComponent system(SubsystemComponent.SubsystemType type, Fluid fluid) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == type && s.fluid == fluid) return s;
        }
        return null;
    }

    private SubsystemComponent tank(Fluid fluid) {
        return system(SubsystemComponent.SubsystemType.TANK, fluid);
    }

    private Compartment room(ShipGridComponent g) {
        return g.compartmentAt(4, 2);
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            hazards.step(grid, DT);
            engine.update(DT);
        }
    }

    /** Empties the room to a fifth of its air, as after a breach that has been sealed. */
    private void vent() {
        room(grid).scaleContents(0.2);
    }

    @Test
    void aTankStartsFullAtItsComponentsCapacity() {
        build(piped(room()));
        SubsystemComponent o2 = tank(Fluid.OXYGEN);
        assertEquals(2 * Fluid.OXYGEN.capacityPerTile, o2.capacity, 1e-6, "an S tank is two tiles");
        assertEquals(o2.capacity, o2.contents, 1e-6);
    }

    @Test
    void pipesJoinTheTanksToLifeSupport() {
        build(piped(room()));
        SubsystemComponent unit = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null);
        assertEquals(2, unit.pipedTanks.size);
        assertEquals(1, grid.pipeNetworks.size);
        assertTrue(grid.getPipe(4, 1).isFed());
        assertEquals(2, grid.pipeNetworks.first().tanks.size);
    }

    @Test
    void refillingARoomDrawsDownBothTanksAtTheAirMix() {
        build(piped(room()));
        double o2 = tank(Fluid.OXYGEN).contents, n2 = tank(Fluid.NITROGEN).contents;
        double before = room(grid).totalMoles();
        vent();
        run(60);
        double added = room(grid).totalMoles() - before * 0.2;
        double usedO2 = (o2 - tank(Fluid.OXYGEN).contents) / Fluid.OXYGEN.molarMass;
        double usedN2 = (n2 - tank(Fluid.NITROGEN).contents) / Fluid.NITROGEN.molarMass;
        assertTrue(added > 100, "the room is refilling: " + added);
        assertEquals(added, usedO2 + usedN2, added * 0.01, "every mole released came out of a tank");
        assertEquals(Compartment.STANDARD_O2_FRACTION, usedO2 / (usedO2 + usedN2), 0.02);
    }

    @Test
    void anEmptyTankStopsTheRefill() {
        build(piped(room()));
        tank(Fluid.NITROGEN).contents = 0;
        vent();
        double vented = room(grid).totalMoles();
        run(60);
        double o2Share = room(grid).oxygenFraction();
        assertEquals(vented, room(grid).totalMoles(), vented * 0.05, "no fresh air without nitrogen");
        assertTrue(o2Share >= Compartment.STANDARD_O2_FRACTION - 0.01, "but oxygen still tops up: " + o2Share);
    }

    @Test
    void withTheOxygenTankDryLifeSupportRefillsFromItsOwnOxygen() {
        // Round 8: a burst oxygen tank left a patched gunship unbreathable for good
        build(piped(room()));
        tank(Fluid.OXYGEN).contents = 0;
        SubsystemComponent unit = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null);
        assertEquals(LifeSupportSystem.EMERGENCY_OXYGEN_KG, TankSupply.available(unit, Fluid.OXYGEN), 1e-9);
        vent();
        run(600);
        assertEquals(Compartment.STANDARD_PRESSURE, room(grid).pressure(), 2.0, "back to full pressure");
        assertEquals(Compartment.STANDARD_O2_FRACTION, room(grid).oxygenFraction(), 0.01, "and breathable");
        assertTrue(unit.reserveUsed > 0 && unit.reserveUsed < LifeSupportSystem.EMERGENCY_OXYGEN_KG, "used " + unit.reserveUsed);
        assertEquals(0, tank(Fluid.OXYGEN).contents, 1e-9);
    }

    @Test
    void lifeSupportKeepsItsOwnOxygenWhileTheTanksHaveSome() {
        build(piped(room()));
        vent();
        run(60);
        assertEquals(0, system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null).reserveUsed, 1e-12);
    }

    @Test
    void anUnpipedUnitReleasesNothingOnceTheShipHasTanks() {
        build(room());
        vent();
        double vented = room(grid).totalMoles();
        run(60);
        assertEquals(vented, room(grid).totalMoles(), vented * 0.01);
        assertEquals(tank(Fluid.OXYGEN).capacity, tank(Fluid.OXYGEN).contents, 1e-9);
    }

    @Test
    void aShipWithoutTanksIsNotLimited() {
        build(new ChassisBlueprint(
                "###########",
                "#.........#",
                "#.........#",
                "#.........#",
                "###########")
                .mount("life_support", MountFootprint.MountSize.S, 1, 1)
                .mount("reactor", MountFootprint.MountSize.S, 1, 3)
                .conduitRun(1, 2, 1, 3));
        vent();
        double vented = room(grid).totalMoles();
        run(60);
        assertTrue(room(grid).totalMoles() > vented + 100, "a layout from before tanks refills as it always did");
    }

    @Test
    void closingATanksValveCutsItOff() {
        build(piped(room()));
        tank(Fluid.OXYGEN).switchedOn = false;
        SubsystemComponent unit = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null);
        assertEquals(0, TankSupply.available(unit.pipedTanks, Fluid.OXYGEN), 1e-9);
        assertEquals(TankSupply.reserve(unit), TankSupply.available(unit, Fluid.OXYGEN), 1e-9, "only life support's own oxygen");
        assertTrue(TankSupply.available(unit, Fluid.NITROGEN) > 0);
    }

    @Test
    void edgePipesRunThroughWallsAndAreCutWithThem() {
        // The unit and the tanks in rooms either side of an internal wall at x = 5
        ChassisBlueprint bp = new ChassisBlueprint(
                "###########",
                "#....I....#",
                "#....I....#",
                "#....I....#",
                "###########")
                .mount("life_support", MountFootprint.MountSize.S, 1, 1)
                .mount("oxygen_tank", MountFootprint.MountSize.S, 7, 1)
                .mount("nitrogen_tank", MountFootprint.MountSize.S, 8, 1)
                .mount("reactor", MountFootprint.MountSize.S, 1, 3)
                .conduitRun(1, 2, 1, 3);
        bp.pipe(1, 1).pipe(2, 1).pipe(3, 1).pipe(4, 1);
        // Up the wall's left edge, along its bottom over the hull and down its right edge
        bp.pipe(5, 1, Side.LEFT).pipe(5, 1, Side.BOTTOM).pipe(5, 1, Side.RIGHT);
        bp.pipe(6, 1).pipe(7, 1).pipe(8, 1);
        build(bp);
        SubsystemComponent unit = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null);
        assertEquals(2, unit.pipedTanks.size, "the edge pipe joins the pipes across the tiles on both sides of its ends");

        grid.getTile(5, 1).setIntegrity(0f);
        reroute();
        assertEquals(0, unit.pipedTanks.size, "destroying the wall it runs in cuts it");
        assertNull(grid.getEdgePipe(5, 1, Side.BOTTOM).network);
    }

    @Test
    void aDestroyedFloorTileCutsThePipeAcrossIt() {
        build(piped(room()));
        grid.getTile(4, 1).setIntegrity(0f);
        reroute();
        assertEquals(0, system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null).pipedTanks.size);
        assertEquals(2, grid.pipeNetworks.size, "the run splits in two");
    }

    @Test
    void aDisabledTankSuppliesNothing() {
        build(piped(room()));
        tank(Fluid.OXYGEN).health = 0.1f;
        assertFalse(tank(Fluid.OXYGEN).canSupply());
        double before = tank(Fluid.OXYGEN).contents;
        SubsystemComponent unit = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT, null);
        TankSupply.draw(unit, Fluid.OXYGEN, 10);
        assertEquals(before, tank(Fluid.OXYGEN).contents, 1e-9);
        assertEquals(10, unit.reserveUsed, 1e-9, "it came out of life support's own oxygen");
    }

    @Test
    void drawsShareOutByWhatEachTankHolds() {
        build(piped(room()));
        SubsystemComponent a = tank(Fluid.OXYGEN);
        SubsystemComponent b = new SubsystemComponent(new MountFootprint(MountFootprint.MountKind.AUXILIARY,
                MountFootprint.MountSize.S, 0, 0, 0), SubsystemComponent.SubsystemType.TANK);
        b.fluid = Fluid.OXYGEN;
        b.capacity = b.contents = 100;
        a.contents = 300;
        com.badlogic.gdx.utils.Array<SubsystemComponent> both = new com.badlogic.gdx.utils.Array<>();
        both.add(a);
        both.add(b);
        assertEquals(40, TankSupply.draw(both, Fluid.OXYGEN, 40), 1e-9);
        assertEquals(270, a.contents, 1e-9);
        assertEquals(90, b.contents, 1e-9);
        assertEquals(360, TankSupply.draw(both, Fluid.OXYGEN, 1000), 1e-9, "no more than they hold");
    }

    @Test
    void pipesAndTankContentsAreSaved() {
        build(piped(room()).pipe(9, 2, Side.TOP));
        ShipGridComponent back = WallLightSaveTest.roundTrip(grid);
        assertNotNull(back.getPipe(4, 1));
        assertNull(back.getPipe(4, 2));
        assertNotNull(back.getEdgePipe(9, 3, Side.BOTTOM), "an edge pipe is the same from either tile");
    }

    @Test
    void aBadlyDamagedTankLeaksItsGasIntoTheRoom() {
        build(piped(room()));
        SubsystemComponent o2 = tank(Fluid.OXYGEN);
        o2.health = 0f;
        double before = o2.contents, roomO2 = room(grid).oxygen;
        run(10);
        double lost = before * (1 - Math.exp(-TankSupply.LEAK_RATE * 10));
        assertEquals(before - lost, o2.contents, before * 0.01);
        assertEquals(lost / Fluid.OXYGEN.molarMass, room(grid).oxygen - roomO2, lost / Fluid.OXYGEN.molarMass * 0.05,
                "the oxygen ends up in the room's air");
    }

    @Test
    void aLightlyDamagedTankHolds() {
        build(piped(room()));
        tank(Fluid.NITROGEN).health = TankSupply.LEAK_THRESHOLD + 0.05f;
        run(10);
        assertEquals(tank(Fluid.NITROGEN).capacity, tank(Fluid.NITROGEN).contents, 1e-9);
        assertEquals(TankSupply.LEAK_RATE / 2, TankSupply.leakRate(TankSupply.LEAK_THRESHOLD / 2), 1e-12);
    }

    @Test
    void whatTheTanksHoldWeighsOnTheShip() {
        build(piped(room()));
        ShipGridComponent.MassProperties dry = grid.computeMassProperties(MaterialTable.createDefault());
        ShipGridComponent.MassProperties wet = grid.computeMassProperties(MaterialTable.createDefault());
        TankSupply.addMassTo(grid, registry, wet);
        assertEquals(dry.mass + TankSupply.contents(registry), wet.mass, 1e-2);
        assertTrue(wet.center.x > dry.center.x, "the tanks sit toward +x, so the center moves that way");
        tank(Fluid.OXYGEN).contents = 0;
        tank(Fluid.NITROGEN).contents = 0;
        ShipGridComponent.MassProperties empty = grid.computeMassProperties(MaterialTable.createDefault());
        TankSupply.addMassTo(grid, registry, empty);
        assertEquals(dry.mass, empty.mass, 1e-2, "empty tanks weigh nothing extra");
    }

    @Test
    void theSystemsWeighOnTheShip() {
        build(piped(room()));
        ShipGridComponent.MassProperties dry = grid.computeMassProperties(MaterialTable.createDefault());
        ShipGridComponent.MassProperties fitted = grid.computeMassProperties(MaterialTable.createDefault());
        ComponentMass.addMassTo(grid, registry, fitted);
        double systems = 0;
        for (Entity e : registry.subsystems) {
            systems += ComponentMass.of(e.getComponent(SubsystemComponent.class));
        }
        assertTrue(systems > 0);
        assertEquals(dry.mass + systems, fitted.mass, 1e-1);
    }
}
