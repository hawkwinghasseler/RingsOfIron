package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.Contract;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.MissionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mission boards in the starting scenario: what they offer, taking jobs, and each kind ending well or badly. */
class ContractBoardTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;
    ContractBoard board;
    TraderComponent trader;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        board = world.contracts;
        trader = game.player.getComponent(TraderComponent.class);
        world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    /** The first offer of that kind the new ensign may take, else the first of that kind. */
    private Contract offer(String station, Contract.Type type) {
        Contract any = null;
        for (Contract c : board.offers(station)) {
            if (c.type != type) continue;
            if (board.locked(c) == null) return c;
            if (any == null) any = c;
        }
        return any;
    }

    /** A commodity the depot has in stock and Highport buys, with a trade order for it from the depot to Highport. */
    private TradeOrder orderFromDepotToHighport() {
        EconomyNode depot = world.macroSim.station(game.depot), highport = world.macroSim.station(game.highport);
        for (CommodityType c : world.macroSim.economy().catalog().commodities()) {
            if (c.unitVolume <= 0 || depot.available(c.id) < 10 || !world.macroSim.market().stationBuys(highport, c.id)) continue;
            List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
            TradeOrder o = new TradeOrder(9_001, c.id, game.depot, game.highport, 5, depot.price(c.id), highport.price(c.id) * 1.2);
            orders.add(o);
            world.macroSim.economy().restoreOrders(orders, 9_002);
            return o;
        }
        fail("no commodity the depot sells and Highport buys");
        return null;
    }

    @Test
    void aBoardOffersUpToFiveJobsFromTheWorldGivenByTheStationsOwner() {
        List<Contract> offers = board.offers(game.highport);
        assertFalse(offers.isEmpty());
        assertTrue(offers.size() <= ContractBoard.OFFERS_PER_STATION);
        boolean open = false;
        for (Contract c : offers) {
            assertEquals("patrol", c.giver);
            assertEquals("Highport", c.stationName);
            assertTrue(c.reward > 0 && c.standing > 0 && c.deadline > world.engine.getSimSeconds());
            open |= board.locked(c) == null;
        }
        assertTrue(open, "a new ensign can take something");
        Contract bounty = offer(game.highport, Contract.Type.BOUNTY);
        assertNotNull(bounty, "the pirate raider has a price on it");
        assertEquals(world.universe.uuidOf(game.raider), bounty.targetUuid);
        assertNotNull(offer(game.highport, Contract.Type.PATROL));
        assertSame(offers.get(0), board.offers(game.highport).get(0), "the same board all day");
        for (Contract c : board.offers(game.lunarMine)) assertEquals("selene", c.giver);
    }

    @Test
    void higherTiersNeedRankWithTheSponsorOrStandingWithOthers() {
        Contract c = new Contract();
        c.giver = "patrol";
        c.tier = 1;
        assertEquals("Needs rank Lieutenant", board.locked(c));
        world.standing.record().rank = 1;
        assertNull(board.locked(c));
        c.giver = "selene";
        assertEquals("Needs standing 20 with Selene Mining Combine", board.locked(c));
        world.standing.reward("selene", 25f, "test");
        assertNull(board.locked(c));
    }

    @Test
    void aDeliveryTakesItsOrderFromTheFreightersAndPaysTheAgreedPriceOnDocking() {
        TradeOrder order = orderFromDepotToHighport();
        Contract c = offer(game.depot, Contract.Type.DELIVERY);
        assertNotNull(c);
        assertEquals(order.id, c.orderId);
        assertEquals(5, c.quantity, 1e-9);
        assertNull(board.accept(c.id));
        assertEquals(ContractBoard.PLAYER_CLAIM, order.claimedBy, "no freighter takes it now");
        assertTrue(board.active().contains(c));

        // Buy the goods at the depot, then dock at Highport with them
        EconomyNode depot = world.macroSim.station(game.depot);
        assertEquals(5, world.macroSim.market().buy(depot, trader, c.commodity, 5), 1e-6);
        double credits = trader.credits;
        float standing = world.standing.standing("patrol");
        assertTrue(FirstPlay.dockAtHighport(world, game));
        world.update(FRAME);

        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(0, trader.amount(c.commodity), 1e-6);
        assertTrue(trader.credits >= credits + 5 * c.unitPrice + c.reward - 1e-6, "paid the agreed price and the fee");
        assertEquals(standing + c.standing, world.standing.standing("patrol"), 1e-4);
        assertFalse(world.macroSim.economy().orders().contains(order), "the order is done with");
        assertTrue(board.history().contains(c));
    }

    @Test
    void abandoningCostsHalfTheStandingAndReleasesTheOrder() {
        TradeOrder order = orderFromDepotToHighport();
        Contract c = offer(game.depot, Contract.Type.DELIVERY);
        assertNull(board.accept(c.id));
        float standing = world.standing.standing("patrol");
        assertNull(board.abandon(c.id));
        assertEquals(Contract.Status.ABANDONED, c.status);
        assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing("patrol"), 1e-4);
        assertFalse(world.macroSim.economy().orders().contains(order));
        assertNotNull(board.abandon(c.id), "nothing left to abandon");
        assertNotNull(board.accept(c.id), "a taken job is off the board");
    }

    @Test
    void aBountyIsPaidOnceItsShipIsGoneAndItsMarkerShowsMeanwhile() {
        Contract c = offer(game.highport, Contract.Type.BOUNTY);
        assertNull(board.accept(c.id));
        world.update(FRAME);
        HudMarkerComponent marker = game.raider.getComponent(HudMarkerComponent.class);
        assertTrue(marker.label.endsWith(ContractBoard.MARKER_SUFFIX));
        assertTrue(board.isWaypoint(c.targetUuid));
        assertEquals(Contract.Status.ACTIVE, c.status, "a fighting raider is not beaten");

        double credits = trader.credits;
        world.engine.removeEntity(game.raider);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status);
        assertEquals(credits + c.reward, trader.credits, 1e-6);
        // Pirates out on the rails may still have prices on them; the raider has none
        assertTrue(board.offers(game.highport).stream().noneMatch(o -> c.targetUuid.equals(o.targetUuid)),
                "no price on a ship that is gone");
    }

    @Test
    void anAbandonedContractsMarkerGoesBackToHowItWas() {
        Contract c = offer(game.highport, Contract.Type.BOUNTY);
        assertNull(board.accept(c.id));
        world.update(FRAME);
        assertNull(board.abandon(c.id));
        world.update(FRAME);
        HudMarkerComponent marker = game.raider.getComponent(HudMarkerComponent.class);
        assertEquals("Raider", marker.label);
        assertEquals(HudMarkerComponent.Kind.HOSTILE.defaultColor, marker.color);
    }

    @Test
    void aPatrolPassesTheFarEndThenComesBack() {
        Contract c = offer(game.highport, Contract.Type.PATROL);
        assertNull(board.accept(c.id));
        world.update(FRAME);
        assertEquals(0, c.progress, "the far end is far");
        // The player is a few km from Highport: make it the far end too
        c.targetUuid = game.highport;
        world.update(FRAME);
        assertEquals(1, c.progress);
        assertEquals(game.highport, c.waypointUuid());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status);
    }

    @Test
    void aMissedDeadlineFailsTheJob() {
        Contract c = offer(game.highport, Contract.Type.PATROL);
        assertNull(board.accept(c.id));
        float standing = world.standing.standing("patrol");
        c.deadline = world.engine.getSimSeconds() - 1;
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertEquals("out of time", c.outcome);
        assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing("patrol"), 1e-4);
    }

    @Test
    void anEscortEndsWhenItsFreighterDocksWithThePlayerClose() {
        String kestrel = null;
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if ("Hauler Kestrel".equals(e.getValue().displayName)) kestrel = e.getKey();
        }
        assertNotNull(kestrel);
        List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
        TradeOrder run = new TradeOrder(9_100, "water", game.highport, game.depot, 50, 1, 2);
        run.claimedBy = kestrel;
        orders.add(run);
        world.macroSim.economy().restoreOrders(orders, 9_102);
        Contract c = offer(game.highport, Contract.Type.ESCORT);
        assertNotNull(c);
        assertEquals(kestrel, c.targetUuid);
        assertEquals(game.depot, c.destinationUuid);
        assertNull(board.accept(c.id));

        // It docks, here at Highport so the player is close by
        c.destinationUuid = game.highport;
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);

    }

    @Test
    void anEscortsDeadlineLeavesTimeForAFreighterStillFlyingInToLoad() {
        String kestrel = null;
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if ("Hauler Kestrel".equals(e.getValue().displayName)) kestrel = e.getKey();
        }
        List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
        TradeOrder run = new TradeOrder(9_100, "water", game.highport, game.depot, 50, 1, 2);
        run.claimedBy = kestrel;
        orders.add(run);
        world.macroSim.economy().restoreOrders(orders, 9_101);
        // Round 9, seed 12: on its way in from another body's sphere, a week from loading at Highport,
        // its arrival time only the crossing a few hours off
        FreighterComponent f = world.macroSim.freighters().get(kestrel);
        double now = world.engine.getSimSeconds();
        f.state = FreighterComponent.State.TO_PICKUP;
        f.destinationUuid = game.highport;
        f.transitBodyUuid = "the moon";
        f.arrivalTime = now + 3 * 3_600;
        Contract c = offer(game.highport, Contract.Type.ESCORT);
        assertNotNull(c);
        assertEquals(kestrel, c.targetUuid);
        double days = (c.deadline - f.arrivalTime) / ContractBoard.DAY;
        assertTrue(days >= ContractBoard.FAR_DAYS + 2 * ContractBoard.NEAR_DAYS,
                "the crossing, then loading, then the haul: " + days + " days");
    }

    @Test
    void anEscortWhoseFreighterIsLostFails() {
        String kestrel = null;
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if ("Hauler Kestrel".equals(e.getValue().displayName)) kestrel = e.getKey();
        }
        List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
        TradeOrder run = new TradeOrder(9_100, "water", game.highport, game.depot, 50, 1, 2);
        run.claimedBy = kestrel;
        orders.add(run);
        world.macroSim.economy().restoreOrders(orders, 9_101);
        Contract c = offer(game.highport, Contract.Type.ESCORT);
        assertNull(board.accept(c.id));
        world.macroSim.destroy(kestrel);
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertTrue(c.outcome.contains("lost"));
    }

    @Test
    void noMoreThanThreeContractsAtOnce() {
        int taken = 0;
        for (String station : List.of(game.highport, game.depot, game.lunarMine)) {
            for (Contract c : board.offers(station)) {
                if (board.locked(c) == null && board.accept(c.id) == null) taken++;
            }
        }
        assertTrue(taken <= ContractBoard.MAX_ACTIVE);
        assertEquals(taken, board.active().size());
    }

    @Test
    void theMissionWindowShowsTheDockedBoardAndTheContractsHeld() {
        ContractMissions missions = new ContractMissions(world);
        MissionProvider.MissionBoard out = new MissionProvider.MissionBoard();
        missions.collect(out);
        assertFalse(out.isOpen(), "not docked and holding nothing");
        assertTrue(FirstPlay.dockAtHighport(world, game));
        out.clear();
        missions.collect(out);
        assertEquals("Highport", out.stationName);
        assertTrue(out.career.startsWith("Ensign"));
        assertTrue(out.offers.size > 0);
        long id = -1;
        for (MissionProvider.Job j : out.offers) if (j.locked.isEmpty()) id = j.id;
        assertEquals("Contract taken", missions.accept(id));
        out.clear();
        missions.collect(out);
        assertEquals(1, out.held.size);
        assertTrue(out.held.first().terms.contains("left"));
    }

    @Test
    void contractsAndBoardsSaveWithTheGame(@TempDir Path dir) {
        Contract c = offer(game.highport, Contract.Type.BOUNTY);
        assertNull(board.accept(c.id));
        int offers = board.offers(game.highport).size();
        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(1, fresh.contracts.active().size());
            Contract back = fresh.contracts.active().get(0);
            assertEquals(c.id, back.id);
            assertEquals(c.title(), back.title());
            assertEquals(c.reward, back.reward, 1e-9);
            assertEquals(c.deadline, back.deadline, 1e-6);
            assertEquals(offers, fresh.contracts.offers(game.highport).size(), "the day's board is kept");
            assertEquals(Contract.Status.ACTIVE, back.status);
        } finally {
            fresh.dispose();
        }
    }
}
