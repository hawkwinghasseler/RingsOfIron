package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contracts at their edges: a delivery carried across a save and finished after it, saves from
 * before contracts, ids after a load, refusals, a delivery running out of time, and the history.
 */
class ContractEdgesTest {
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

    private Contract offer(String station, Contract.Type type) {
        for (Contract c : board.offers(station)) if (c.type == type && board.locked(c) == null) return c;
        return null;
    }

    /** A trade order of 5 units of something the depot sells and Highport buys. */
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

    private GameScenario load(GameWorld into, FileHandle file) {
        GameScenario loaded = GameScenario.start(into, assets, assets.resolve("no-mods-here"));
        loaded.quickLoad(file);
        return loaded;
    }

    @Test
    void aDeliveryTakenBeforeASaveIsStillHeldAfterIt(@TempDir Path dir) {
        orderFromDepotToHighport();
        Contract c = offer(game.depot, Contract.Type.DELIVERY);
        assertNotNull(c);
        assertNull(board.accept(c.id));
        assertEquals(5, world.macroSim.market().buy(world.macroSim.station(game.depot), trader, c.commodity, 5), 1e-6);
        FileHandle file = new FileHandle(dir.resolve("delivery.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = load(fresh, file);
            Contract back = fresh.contracts.active().get(0);
            assertEquals(c.orderId, back.orderId);
            assertEquals(c.unitPrice, back.unitPrice, 1e-9);
            TradeOrder order = null;
            for (TradeOrder o : fresh.macroSim.economy().orders()) if (o.id == c.orderId) order = o;
            assertNotNull(order, "the order is still in the economy");
            assertEquals(ContractBoard.PLAYER_CLAIM, order.claimedBy, "and still the player's, not a freighter's");

            TraderComponent t = loaded.player.getComponent(TraderComponent.class);
            assertEquals(5, t.amount(c.commodity), 1e-6, "the goods are still aboard");
            EconomyNode highport = fresh.macroSim.station(game.highport);
            assertEquals(world.macroSim.station(game.highport).incoming.getOrDefault(c.commodity, 0.0),
                    highport.incoming.getOrDefault(c.commodity, 0.0), 1e-6, "Highport still expects the goods");
            fresh.update(FRAME);
            assertEquals(Contract.Status.ACTIVE, back.status, "and the contract runs on");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aSaveFromBeforeContractsLoadsWithNoneHeld(@TempDir Path dir) {
        Contract c = offer(game.highport, Contract.Type.PATROL);
        assertNull(board.accept(c.id));
        JsonValue save = new JsonReader().parse(world.engine.getSerializationService().saveGame());
        save.get("modules").remove("contracts");
        FileHandle file = new FileHandle(dir.resolve("old.json").toFile());
        file.writeString(save.toJson(JsonWriter.OutputType.json), false, "UTF-8");

        game.quickLoad(file);
        assertTrue(board.active().isEmpty(), "nothing held");
        assertTrue(board.history().isEmpty());
        assertFalse(board.offers(game.highport).isEmpty(), "the boards post afresh");
        world.update(FRAME);
    }

    @Test
    void offersPostedAfterALoadNeverReuseAnId(@TempDir Path dir) {
        Contract c = offer(game.highport, Contract.Type.BOUNTY);
        assertNull(board.accept(c.id));
        long highest = 0;
        for (Contract o : board.offers(game.highport)) highest = Math.max(highest, o.id);
        highest = Math.max(highest, c.id);
        FileHandle file = new FileHandle(dir.resolve("ids.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = load(fresh, file);
            for (Contract o : fresh.contracts.offers(loaded.depot)) assertTrue(o.id > highest, "id " + o.id + " is new");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void lateAndLockedJobsAreRefused() {
        Contract c = offer(game.highport, Contract.Type.PATROL);
        c.deadline = world.engine.getSimSeconds() - 1;
        assertEquals("Too late for that one", board.accept(c.id));
        assertTrue(board.active().isEmpty());

        for (Contract o : board.offers(game.lunarMine)) {
            if (board.locked(o) == null) continue;
            assertNotNull(board.accept(o.id), "a locked offer can't be taken");
            return;
        }
    }

    @Test
    void aDeliveryOutOfTimeFailsAndHandsItsOrderBack() {
        TradeOrder order = orderFromDepotToHighport();
        Contract c = offer(game.depot, Contract.Type.DELIVERY);
        assertNull(board.accept(c.id));
        EconomyNode highport = world.macroSim.station(game.highport);
        double incoming = highport.incoming.getOrDefault(c.commodity, 0.0);
        float standing = world.standing.standing("patrol");

        c.deadline = world.engine.getSimSeconds() - 1;
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertFalse(world.macroSim.economy().orders().contains(order), "the order is closed");
        assertEquals(incoming - c.quantity, highport.incoming.getOrDefault(c.commodity, 0.0), 1e-6,
                "Highport no longer expects the goods");
        assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing("patrol"), 1e-4);
    }

    @Test
    void theHistoryKeepsTheLastTwenty() {
        StringBuilder active = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            if (i > 0) active.append(',');
            active.append("{id:").append(10_000 + i).append(",type:PATROL,giver:patrol,status:ACTIVE,deadline:-1}");
        }
        board.read(new com.badlogic.gdx.utils.Json(), new JsonReader().parse("{nextId:20000,active:[" + active + "]}"));
        assertEquals(25, board.active().size());
        world.update(FRAME);
        assertTrue(board.active().isEmpty(), "every one out of time");
        assertEquals(20, board.history().size());
        assertEquals(10_024, board.history().get(19).id, "newest last");
        assertEquals(10_005, board.history().get(0).id, "the oldest five dropped");
    }
}
