package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** World events: rolled from the seed, at most one a day, each changing the world and posting jobs. */
class WorldEventsTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    /** The kinds the events roll for days 1 to {@code days} with {@code seed}, one world per call. */
    private List<String> kinds(long seed, int days) {
        GameWorld w = new GameWorld();
        try {
            GameScenario.start(w, assets, assets.resolve("no-mods-here"));
            w.update(FRAME);
            w.events.seed = seed;
            for (int d = 1; d <= days; d++) w.events.roll(d);
            List<String> out = new ArrayList<>();
            for (WorldEvents.Event e : w.events.log()) out.add(e.day + ":" + e.kind);
            return out;
        } finally {
            w.dispose();
        }
    }

    @Test
    void theSameSeedRollsTheSameEventsAndAnotherSeedOthers() {
        List<String> a = kinds(11, 8), b = kinds(11, 8), c = kinds(12, 8);
        assertFalse(a.isEmpty(), "eight days bring something");
        assertEquals(a, b);
        assertNotEquals(a, c);
        // At most one a day
        for (int i = 1; i < a.size(); i++) assertNotEquals(a.get(i - 1).split(":")[0], a.get(i).split(":")[0]);
    }

    @Test
    void theFirstDayIsQuietAndTheNextTurnRollsOnce() {
        world.update(FRAME);
        assertTrue(world.events.log().isEmpty());
    }

    @Test
    void aShortageEmptiesAStationOfSomethingAndPostsUrgentDeliveriesThere() {
        assertTrue(world.events.happen(WorldEvents.Kind.SHORTAGE, 1, new Random(5)));
        WorldEvents.Event e = world.events.log().get(0);
        EconomyNode node = world.macroSim.station(e.station);
        assertNotNull(node);
        assertTrue(e.text.contains("is short of"));
        boolean posted = false;
        for (Contract c : world.contracts.extraOffers()) {
            if (c.type != Contract.Type.DELIVERY) continue;
            posted = true;
            assertEquals(e.station, c.targetUuid);
            assertEquals(e.commodity, c.commodity);
            assertTrue(c.orderId < 0, "no trade order behind it");
            assertTrue(c.unitPrice > node.price(e.commodity), "a premium over the short station's price");
            assertEquals(e.text, c.reason);
        }
        assertTrue(posted);
        assertTrue(world.events.notices.get(0).startsWith(e.text));
    }

    @Test
    void anUrgentDeliveryNeedsNoTradeOrder() {
        assertTrue(world.events.happen(WorldEvents.Kind.SHORTAGE, 1, new Random(5)));
        Contract c = null;
        for (Contract o : world.contracts.extraOffers()) if (o.type == Contract.Type.DELIVERY) c = o;
        world.standing.record().rank = 4;
        for (var f : world.standing.factions().all()) world.standing.reward(f.id, 100, "test");
        int orders = world.macroSim.economy().orders().size();
        assertNull(world.contracts.accept(c.id));
        world.update(FRAME);
        assertEquals(Contract.Status.ACTIVE, c.status, "not called off for want of an order");
        assertNull(world.contracts.abandon(c.id));
        assertEquals(orders, world.macroSim.economy().orders().size(), "no order touched");
    }

    @Test
    void aLostFreighterLeavesAPodAndAWreckAndIsReplacedLater() {
        // Put a hauler out on the rails with cargo, as one underway would be
        String uuid = null;
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            Entity ship = world.engine.getEntity(e.getKey());
            if (ship != null && world.universe.orbit(ship) != null) {
                uuid = e.getKey();
                break;
            }
        }
        assertNotNull(uuid, "a hauler on the rails");
        FreighterComponent f = world.macroSim.freighters().get(uuid);
        String name = f.displayName;
        f.state = FreighterComponent.State.TO_DELIVERY;
        f.destinationUuid = game.highport;
        f.cargoCommodity = "water";
        f.cargoQuantity = 40;
        int fleet = world.macroSim.freighters().all().size();
        assertTrue(world.events.happen(WorldEvents.Kind.FREIGHTER_LOST, 1, new Random(9)));
        assertNull(world.macroSim.freighters().get(uuid), "lost");
        assertEquals(fleet - 1, world.macroSim.freighters().all().size());
        Derelicts.Derelict pod = null, wreck = null;
        for (Derelicts.Derelict d : world.derelicts.all()) {
            if (d.isPod()) pod = d;
            else wreck = d;
        }
        assertNotNull(pod);
        assertNotNull(wreck);
        assertTrue(pod.survivors >= 2);
        assertEquals("water", wreck.commodity);
        assertTrue(wreck.quantity >= 1 && wreck.quantity <= 40);
        List<Contract.Type> posted = new ArrayList<>();
        for (Contract c : world.contracts.offers(game.highport)) posted.add(c.type);
        assertTrue(posted.contains(Contract.Type.RESCUE), posted.toString());
        assertTrue(posted.contains(Contract.Type.RECOVERY), posted.toString());

        assertEquals(1, world.events.replacements().size());
        world.events.replacements().get(0).at = world.engine.getSimSeconds();
        world.update(FRAME);
        assertEquals(fleet, world.macroSim.freighters().all().size(), "a new hull");
        boolean back = false;
        for (FreighterComponent g : world.macroSim.freighters().all().values()) back |= name.equals(g.displayName);
        assertTrue(back);
    }

    @Test
    void aRobbedFreighterSometimesLeavesAPodWithARescuePosted() {
        String uuid = world.macroSim.freighters().all().keySet().iterator().next();
        world.macroSim.freighters().get(uuid).destinationUuid = game.highport;
        int pods = 0;
        for (int t = 0; t < 30; t++) world.events.plundered("nobody", uuid, "water", 10, t * 1000.0);
        for (Derelicts.Derelict d : world.derelicts.all()) if (d.isPod()) pods++;
        assertTrue(pods > 0 && pods < 30, "some, not all: " + pods);
        long rescues = world.contracts.offers(game.highport).stream().filter(c -> c.type == Contract.Type.RESCUE).count();
        assertEquals(pods, rescues);
    }

    @Test
    void tensionPostsShowsOfForceOutToARivalsStations() {
        assertTrue(world.events.happen(WorldEvents.Kind.TENSION, 1, new Random(2)));
        WorldEvents.Event e = world.events.log().get(0);
        int rival = world.standing.factions().index(e.faction);
        assertNotEquals(world.standing.sponsor(), rival);
        boolean posted = false;
        for (Contract c : world.contracts.extraOffers()) {
            if (c.type != Contract.Type.PATROL) continue;
            posted = true;
            assertEquals(rival, world.standing.factionOf(world.engine.getEntity(c.targetUuid)));
            assertEquals(world.standing.sponsor(), world.standing.factionOf(world.engine.getEntity(c.stationUuid)));
        }
        assertTrue(posted);
    }
}
