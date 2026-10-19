package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A station hand carrying goods between two craft joined at an opening, and walking home. */
class CarryAcrossLinkTest {

    final Engine engine = new Engine();
    final CrewService crew = CrewFactory.install(engine);

    /** A craft from {@code rows}, with its cargo bays as a hold. */
    private Entity craft(String... rows) {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(new ChassisBlueprint(rows));
        CompartmentBuilder.rebuildIfChanged(r.grid);
        Entity e = new Entity();
        e.add(r.grid);
        CargoHoldComponent hold = CargoHoldComponent.forBays(r.grid.tileSize, r.subsystems);
        if (hold != null) e.add(hold);
        engine.addEntity(e);
        return e;
    }

    /** The open tile on the {@code side} edge of the grid. */
    private static int[] edgeTile(ShipGridComponent g, Side side) {
        int x = side == Side.RIGHT ? g.width - 1 : 0;
        for (int y = 0; y < g.height; y++) {
            if (g.getTile(x, y).isOpen()) return new int[]{x, y};
        }
        throw new IllegalStateException("no opening");
    }

    /** Moves {@code total} units, settling each load as it lands in {@code to}'s hold. */
    static final class Delivery implements CarryTask.Job {
        final CargoHoldComponent from, to;
        double left, inHand, delivered;
        int trips;

        Delivery(CargoHoldComponent from, CargoHoldComponent to, double total) {
            this.from = from;
            this.to = to;
            this.left = total;
        }

        @Override
        public String commodity() {
            return "ore";
        }

        @Override
        public double take(double volume) {
            double units = Math.min(left - inHand, volume);
            if (units <= 0) return 0;
            inHand += units;
            return units;
        }

        @Override
        public void deliver(double units) {
            inHand -= units;
            left -= units;
            delivered += units;
            from.unload("ore", units);
            to.load("ore", units, 1, 1);
            trips++;
        }

        @Override
        public void abandon(double units) {
            inHand -= units;
        }

        @Override
        public boolean isOpen() {
            return left > 1e-9;
        }
    }

    @Test
    void aLoaderCarriesGoodsAcrossTheDockAndWalksHome() {
        Entity ship = craft(
                "######",
                "#C....",
                "#C...#",
                "######");
        Entity station = craft(
                "#######",
                ".....C#",
                "#....C#",
                "#######");
        ShipGridComponent sg = ship.getComponent(ShipGridComponent.class), tg = station.getComponent(ShipGridComponent.class);
        int[] a = edgeTile(sg, Side.RIGHT), b = edgeTile(tg, Side.LEFT);
        GridLink.join(ship, sg, a[0], a[1], Side.RIGHT, station, tg, b[0], b[1], Side.LEFT).setGap(1f);
        CargoHoldComponent stock = station.getComponent(CargoHoldComponent.class);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        double stocked = stock.load("ore", 20, 1, 1);

        Entity loader = CrewFactory.spawn(crew, station, "Hand", CrewRole.LOADER, 2, 2, b[1]);
        crew.crew(loader).setHome(station);
        ShipTaskBoardComponent board = crew.board(station);
        board.faction = 2;
        Delivery job = new Delivery(stock, hold, 4);
        CarryTask trip = null;
        boolean wentAboard = false;
        for (int i = 0; i < 30 * 120 && (job.isOpen() || crew.position(loader).ship != station
                || crew.position(loader).walkway != null); i++) {
            if (job.isOpen() && (trip == null || !trip.isActive())) {
                trip = new CarryTask(station, b[0], b[1], ship, job);
                board.post(trip);
            }
            engine.update(1f / 30f);
            wentAboard |= crew.position(loader).ship == ship;
        }
        assertEquals(4, hold.amount("ore"), 1e-9, "every load landed in the ship's bays");
        assertEquals(stocked - 4, stock.amount("ore"), 1e-9);
        assertEquals(2, job.trips, "two units a trip at a cubic meter each");
        assertTrue(wentAboard, "walked across into the ship");
        assertSame(station, crew.position(loader).ship, "and went home afterwards");
        assertNull(crew.crew(loader).load);
        assertNull(crew.position(loader).walkway);
    }

    @Test
    void stationHandsAreNotShotAtWhenFactionsAreAtPeace() {
        Entity ship = craft(
                "#######",
                "#.....#",
                "#######");
        Entity mine = CrewFactory.spawn(crew, ship, "Marine", CrewRole.MARINE, 0, 1, 1);
        Entity visitor = CrewFactory.spawn(crew, ship, "Hand", CrewRole.LOADER, 2, 5, 1);
        crew.hostility = (x, y) -> x != y && x != 2 && y != 2;
        for (int i = 0; i < 90; i++) engine.update(1f / 30f);
        assertEquals(crew.crew(visitor).def.maxHealth, crew.crew(visitor).health, 1e-4f);
        crew.hostility = (x, y) -> x != y;
        for (int i = 0; i < 30 * 10; i++) engine.update(1f / 30f);
        assertTrue(crew.crew(visitor).health < crew.crew(visitor).def.maxHealth, "at war, the marine opens fire");
        assertNotNull(mine);
    }
}
