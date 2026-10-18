package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoHoldTest {

    /** A 2x3 cargo bay (6 tiles) aft of a corridor, and a 1x2 one forward. */
    private static final String[] LAYOUT = {
        "########",
        "#CC....#",
        "#CC..CC#",
        "#CC....#",
        "########",
    };

    private static ChassisBlueprintLoader.LoadResult ship() {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(LAYOUT).tileSize(1f).origin(-4f, -2.5f));
    }

    private static CargoHoldComponent hold(ChassisBlueprintLoader.LoadResult r) {
        return CargoHoldComponent.forBays(r.grid.tileSize, r.subsystems);
    }

    @Test
    void capacityIsTheVolumeOfTheBayTiles() {
        ChassisBlueprintLoader.LoadResult r = ship();
        CargoHoldComponent hold = hold(r);
        assertEquals(8, hold.bayTileCount());
        double tile = CargoHoldComponent.STACK_HEIGHT;
        assertEquals(8 * tile, hold.capacity(), 1e-9);
        assertEquals(8 * tile, CargoHoldComponent.bayVolume(r), 1e-9);
        assertNull(CargoHoldComponent.forBays(1f, ChassisBlueprintLoader.load(new String[]{"###", "#.#", "###"}, 1f).subsystems),
                "no bays, no hold");
    }

    @Test
    void eachTileHoldsOneCommodity() {
        CargoHoldComponent hold = hold(ship());
        double tile = hold.tileVolume;
        // Three tiles' worth of water (1 m³ a unit), then ore takes what is left
        assertEquals(3 * tile, hold.load("water", 3 * tile, 1.0, 1000), 1e-9);
        assertEquals(5 * tile / 0.5, hold.room("ore", 0.5), 1e-9);
        assertEquals(5 * tile * 2, hold.load("ore", 1e6, 0.5, 1250), 1e-9);
        assertEquals(0, hold.freeVolume(), 1e-9);
        assertEquals(0, hold.load("water", 1, 1.0, 1000), 1e-9);

        // A part-used water tile has room only for more water
        assertEquals(1, hold.unload("water", 1), 1e-9);
        assertEquals(0, hold.room("ore", 0.5), 1e-9);
        assertEquals(1, hold.room("water", 1.0), 1e-9);
        assertEquals(3 * tile - 1, hold.amount("water"), 1e-9);
        assertEquals((3 * tile - 1) * 1000 + 5 * tile * 2 * 1250, hold.mass(), 1e-6);
    }

    @Test
    void unloadingEmptiesTilesForOtherGoods() {
        CargoHoldComponent hold = hold(ship());
        hold.load("food", 2 * hold.tileVolume, 1.0, 500);
        int rev = hold.revision;
        assertEquals(2 * hold.tileVolume, hold.unload("food", 99), 1e-9);
        assertTrue(hold.revision > rev, "unloading changes the revision");
        for (CargoHoldComponent.CargoStack s : hold.stacks) assertNull(s);
        assertEquals(hold.capacity(), hold.freeVolume(), 1e-9);
    }

    @Test
    void cargoShowsWhereItSits() {
        ChassisBlueprintLoader.LoadResult r = ship();
        CargoHoldComponent hold = hold(r);
        // Bays fill in order: the aft bay first, from its bottom-left tile
        hold.load("water", hold.tileVolume * 1.5, 1.0, 1000);
        assertEquals(1f, hold.fillAt(1, 1), 1e-6);
        assertEquals(0.5f, hold.fillAt(2, 1), 1e-6);
        assertEquals(0f, hold.fillAt(5, 2), 1e-6);
        assertEquals("water", hold.stackAt(1, 1).commodity);
        assertNull(hold.stackAt(4, 2), "corridor tiles are not bays");
    }

    @Test
    void cargoMovesTheCenterOfMassTowardTheBay() {
        ChassisBlueprintLoader.LoadResult r = ship();
        CargoHoldComponent hold = hold(r);
        ShipGridComponent.MassProperties structure = r.grid.computeMassProperties(MaterialTable.createDefault());

        ShipGridComponent.MassProperties empty = copy(structure);
        hold.addMassTo(r.grid, empty);
        assertEquals(structure.mass, empty.mass, 1e-3);
        assertEquals(structure.center.x, empty.center.x, 1e-4);
        assertEquals(structure.inertia, empty.inertia, structure.inertia * 1e-5);

        hold.load("ore", 2 * hold.tileVolume * 6, 0.5, 1250); // fills the aft bay's six tiles
        ShipGridComponent.MassProperties loaded = copy(structure);
        hold.addMassTo(r.grid, loaded);
        assertEquals(structure.mass + hold.mass(), loaded.mass, 1.0);
        assertTrue(loaded.center.x < structure.center.x - 0.1f, "aft bay pulls the center aft: " + loaded.center.x);
        assertTrue(loaded.inertia > structure.inertia, "more mass, more inertia");
    }

    @Test
    void cargoSurvivesSaveAndLoad() {
        GameEngine engine = new GameEngine();
        Entity ship = engine.createEntityWithUuid();
        CargoHoldComponent hold = hold(ship());
        hold.load("water", 3, 1.0, 1000);
        hold.load("ore", 2, 0.5, 1250);
        ship.add(hold);
        engine.addEntity(ship);
        String uuid = ship.getComponent(UuidComponent.class).id;

        String save = engine.getSerializationService().saveGame();
        engine.getSerializationService().loadGame(save);

        CargoHoldComponent loaded = engine.getEntity(uuid).getComponent(CargoHoldComponent.class);
        assertNotNull(loaded);
        assertEquals(hold.capacity(), loaded.capacity(), 1e-9);
        assertEquals(3, loaded.amount("water"), 1e-9);
        assertEquals(2, loaded.amount("ore"), 1e-9);
        assertEquals(hold.mass(), loaded.mass(), 1e-9);
        assertEquals("ore", loaded.stackAt(1, 2).commodity, "ore starts a tile of its own");
    }

    private static ShipGridComponent.MassProperties copy(ShipGridComponent.MassProperties p) {
        ShipGridComponent.MassProperties out = new ShipGridComponent.MassProperties();
        out.mass = p.mass;
        out.center.set(p.center);
        out.inertia = p.inertia;
        return out;
    }
}
