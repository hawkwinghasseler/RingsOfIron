package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What tiles, edges and systems weigh (the Nov 2026 mass rebalance). */
class MassBalanceTest {
    private static final MaterialTable STEEL = MaterialTable.createDefault();

    private static ShipGridComponent strip(Tile.FloorType... floors) {
        ShipGridComponent grid = new ShipGridComponent(floors.length, 1, 1f);
        for (int x = 0; x < floors.length; x++) grid.getTile(x, 0).floorType = floors[x];
        return grid;
    }

    @Test
    void deckWeighsFarLessThanHull() {
        ShipGridComponent grid = strip(Tile.FloorType.FLOOR, Tile.FloorType.FLOOR);
        assertEquals(2 * ShipGridComponent.DECK_MASS, grid.computeMassProperties(STEEL).mass, 1e-3);
        Tile hull = grid.getTile(1, 0);
        hull.floorType = Tile.FloorType.NONE;
        hull.wallType = Tile.WallType.HULL;
        assertEquals(ShipGridComponent.DECK_MASS + 2000f, grid.computeMassProperties(STEEL).mass, 1e-3,
                "a medium hull tile weighs its steel");
    }

    @Test
    void conduitAddsOnlyItsCable() {
        ShipGridComponent grid = strip(Tile.FloorType.POWER_CONDUIT, Tile.FloorType.FLOOR);
        float tiles = 2 * ShipGridComponent.DECK_MASS + ShipGridComponent.CONDUIT_CABLE_MASS;
        assertEquals(tiles, grid.computeMassProperties(STEEL).mass, 1e-3);
        grid.setConduit(1, 0, Side.TOP, new EdgeConduit());
        assertEquals(tiles + ShipGridComponent.CONDUIT_CABLE_MASS, grid.computeMassProperties(STEEL).mass, 1e-3,
                "an edge conduit is cable only");
    }

    @Test
    void standardSystemsWeighLikeRealHardware() {
        assertEquals(6000, ComponentMass.standard(SubsystemComponent.SubsystemType.ENGINE, MountFootprint.MountSize.S, null));
        assertEquals(25000, ComponentMass.standard(SubsystemComponent.SubsystemType.REACTOR, MountFootprint.MountSize.M, null));
        assertEquals(150, ComponentMass.standard(SubsystemComponent.SubsystemType.TANK, MountFootprint.MountSize.S, Fluid.PROPELLANT));
        assertEquals(400, ComponentMass.standard(SubsystemComponent.SubsystemType.TANK, MountFootprint.MountSize.S, Fluid.OXYGEN));
    }
}
