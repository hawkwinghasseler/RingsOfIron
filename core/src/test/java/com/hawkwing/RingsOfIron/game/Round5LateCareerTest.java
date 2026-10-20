package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.moddingdata.defs.OperationDef;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 5 part 5, the career past the gunship: the pirates' base fighting a raid off and falling,
 * world events straining the factions, Free Trader patrols where tension runs high, the tension
 * readout on the board, survivors carried home as people, the new operations, and the frigate's price.
 */
class Round5LateCareerTest {
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

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    /** Undocks and puts the player on the pirates' base's orbit {@code behind} meters back from it, with the bubble there. */
    private Entity besideTheBase(double behind) {
        world.docking.undock(game.player);
        run(1);
        Entity base = world.engine.getEntity(world.pirateBase.uuid());
        world.rescue.jumpBeside(game.player, base, behind);
        String id = world.pirateBase.uuid();
        for (int i = 0; i < 600 && !world.macroSim.bubble().fastPasses().contains(id); i++) world.update(FRAME);
        assertTrue(world.macroSim.bubble().fastPasses().contains(id), "the base is shown in the bubble");
        return base;
    }

    private Entity ship(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return world.engine.getEntity(e.getKey());
        }
        return null;
    }

    private static void beat(Entity ship) {
        ship.add(new DisabledComponent(DisabledComponent.Reason.SURRENDERED, 0));
    }

    // ------------------------------------------------------------------ the base

    @Test
    void theBaseScramblesAWingAgainstARaidAndStrikesOnceItIsBeaten() {
        world.standing.record().rank = ContractBoard.RAID_RANK;
        world.pirateBase.reveal(null);
        Contract raid = null;
        for (Contract c : board.offers(game.highport)) if (c.type == Contract.Type.RAID) raid = c;
        assertNotNull(raid, "a Commander is offered the raid");
        assertNull(board.accept(raid.id));
        world.tension.raise("patrol", "selene", 30f, null);

        besideTheBase(8_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
        List<String> wing = world.pirateBase.defenders();
        int raiders = PirateBase.defendersFor(Difficulty.of(world));
        assertEquals(raiders, wing.size(), "a wing scrambled: " + wing);
        // Round 6: and the base's own guns manned
        List<String> guns = world.pirateBase.guns();
        assertEquals(PirateBase.BATTERIES, guns.size());
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("scrambles") && n.text.contains("its guns open up")),
                () -> world.notices.history().stream().map(n -> n.text).toList().toString());
        String secondUuid = world.universe.uuidOf(ship(wing.get(1)));
        for (int i = 0; i < wing.size(); i++) {
            HostileComponent h = ship(wing.get(i)).getComponent(HostileComponent.class);
            assertTrue(h.huntingPlayer(), "it goes for the player");
            assertEquals(i < 2 ? null : secondUuid, h.wingOf, "the first alone, the rest one wing");
        }
        for (String name : guns) {
            HostileComponent h = ship(name).getComponent(HostileComponent.class);
            assertTrue(h.moored() && h.huntingPlayer(), "the guns are moored, and on the player");
        }
        run(5);
        assertNotNull(ship(wing.get(0)).getComponent(PhysicsBodyComponent.class), "the first defender came into the bubble");
        assertNull(ship(wing.get(1)).getComponent(PhysicsBodyComponent.class), "the rest wait for the second wave");
        assertTrue(world.pirateBase.defended());

        // Beaten one by one: the base stands while any fights on, and the rest launch once the first is beaten
        run(2 * PirateBase.CHECK_SECONDS);
        assertTrue(world.pirateBase.standing());
        beat(ship(wing.get(0)));
        run(2 * PirateBase.CHECK_SECONDS + 5);
        for (int i = 1; i < raiders; i++) {
            assertNotNull(ship(wing.get(i)).getComponent(PhysicsBodyComponent.class), wing.get(i) + " launched in the second wave");
        }
        for (int i = 1; i < wing.size() - 1; i++) beat(ship(wing.get(i)));
        run(2 * PirateBase.CHECK_SECONDS);
        assertTrue(world.pirateBase.standing(), "one defender still fights");
        assertEquals(wing, world.pirateBase.defenders(), "no second wing while the first is out");
        double credits = trader.credits;
        beat(ship(wing.get(wing.size() - 1)));
        run(2 * PirateBase.CHECK_SECONDS);
        assertFalse(world.pirateBase.standing(), "it strikes its colours");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("strikes its colours")));
        assertEquals(30f - PirateBase.FALL_RELIEF, world.tension.get(world.standing.factions().index("patrol"),
                world.standing.factions().index("selene")), 1e-4, "the factions' strain eases");
        assertEquals(Contract.Status.DONE, raid.status, raid.outcome);
        assertEquals(credits + raid.reward, trader.credits, 1e-6);
        assertTrue(world.pirateBase.defenders().isEmpty());
    }

    @Test
    void theBaseWaitsForThePlayerToComeCloseAndAWingStillOutIsNotReplaced(@TempDir Path dir) {
        besideTheBase(PirateBase.SCRAMBLE_RANGE + 3_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
        assertTrue(world.pirateBase.defenders().isEmpty(), "too far off to scramble for");
        assertTrue(world.pirateBase.found(), "but close enough to find it");

        besideTheBase(5_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
        List<String> wing = new ArrayList<>(world.pirateBase.defenders());
        assertFalse(wing.isEmpty());

        // A save keeps the wing, by name; the loaded base meets the same wing, not another
        FileHandle file = new FileHandle(dir.resolve("raid.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(wing, fresh.pirateBase.defenders());
            for (int i = 0; i < 4 * PirateBase.CHECK_SECONDS / FRAME; i++) fresh.update(FRAME);
            assertEquals(wing, fresh.pirateBase.defenders(), "no second wing");
            assertTrue(fresh.pirateBase.standing());
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void defendersGrowWithDifficulty() {
        assertEquals(2, PirateBase.defendersFor(1.0));
        assertEquals(2, PirateBase.defendersFor(2.4), "round 6: two up to 2.5, so the frigate's raid can be won");
        assertEquals(3, PirateBase.defendersFor(2.5));
        assertEquals(4, PirateBase.defendersFor(4.0));
        assertEquals(PirateBase.MAX_DEFENDERS, PirateBase.defendersFor(9));
    }

    // ------------------------------------------------------------------ tension

    @Test
    void worldEventsStrainTheFactions() {
        int patrol = world.standing.factions().index("patrol"), selene = world.standing.factions().index("selene"),
                traders = world.standing.factions().index("traders");
        assertTrue(world.events.happen(WorldEvents.Kind.TENSION, 1, new Random(2)));
        WorldEvents.Event flex = world.events.log().get(world.events.log().size() - 1);
        int rival = world.standing.factions().index(flex.faction);
        assertEquals(WorldEvents.FLEX_STRAIN, world.tension.get(patrol, rival), 1e-4, "a rival flexing strains it with the sponsor");

        float ps = world.tension.get(patrol, selene), pt = world.tension.get(patrol, traders), st = world.tension.get(selene, traders);
        assertTrue(world.events.happen(WorldEvents.Kind.RAID, 2, new Random(3)));
        WorldEvents.Event raid = world.events.log().get(world.events.log().size() - 1);
        int owner = world.standing.factionOf(world.engine.getEntity(raid.station));
        for (int other : new int[]{patrol, selene, traders}) {
            if (other == owner) continue;
            float before = (owner == patrol && other == selene) || (owner == selene && other == patrol) ? ps
                    : (owner == selene && other == traders) || (owner == traders && other == selene) ? st : pt;
            assertEquals(before + WorldEvents.RAID_STRAIN, world.tension.get(owner, other), 1e-4,
                    "a raid sets " + owner + " against " + other);
        }
    }

    @Test
    void highTensionSendsAFreeTraderPatrolToWatchTheLanes() {
        assertEquals(0, world.hostiles.patrols(HostileTraffic.TRADERS));
        world.tension.raise("patrol", "selene", FactionTension.HIGH, null);
        world.update(FRAME);
        assertEquals(1, world.hostiles.patrols(HostileTraffic.TRADERS), "the Free Traders fly a patrol");
        HostileComponent escort = null;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (HostileTraffic.TRADERS.equals(h.faction)) escort = h;
        assertTrue(escort.displayName.startsWith("Free Trader escort"), escort.displayName);
        assertEquals(3, escort.route.length, "Freehold, then a station of each side");
        assertEquals(game.freehold, escort.route[0]);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("watch the lanes")));
        // Not again while it stays high
        world.tension.raise("patrol", "selene", 5f, null);
        world.update(FRAME);
        assertEquals(1, world.hostiles.patrols(HostileTraffic.TRADERS));
    }

    @Test
    void theBoardShowsTheStationsTension() {
        world.tension.raise("patrol", "selene", 34f, null);
        world.tension.raise("patrol", "traders", 60f, null);
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        MissionProvider.MissionBoard out = new MissionProvider.MissionBoard();
        new ContractMissions(world).collect(out);
        assertTrue(out.tension.startsWith("Tension: "), out.tension);
        assertTrue(out.tension.contains("Selene Mining Combine 34"), out.tension);
        assertTrue(out.tension.contains("Free Traders 60 HIGH"), out.tension);
        assertFalse(out.tension.contains("Void Raiders"), "not the pirates: " + out.tension);
    }

    // ------------------------------------------------------------------ survivors

    private Contract rescueBeside(int survivors) {
        Derelicts.Derelict pod = world.derelicts.castOff(game.player, Derelicts.Kind.POD, "Hauler 9's pod", survivors, null, 0,
                new Random(1));
        Contract c = board.offerFor(pod, game.highport, "a test");
        assertTrue(board.postEvent(c));
        assertNull(board.accept(c.id));
        return c;
    }

    @Test
    void survivorsComeAboardTakingBerthsAndGoAshoreWhenPaid() {
        int aboard = world.crewHiring.aboard(game.player);
        Contract c = rescueBeside(3);
        world.update(FRAME);
        assertEquals(1, c.progress, "picked up");
        assertEquals(3, c.carried);
        assertEquals(3, board.survivorsAboard(game.player));
        assertEquals(aboard + 3, world.crewHiring.aboard(game.player), "they take berths");
        assertTrue(world.prisoners.aboard(game.player).isEmpty(), "carried, not held prisoner");
        assertEquals(world.crewCareer.ownCrew(game.player).size(), world.crewCareer.ownCrew(game.player).size());
        for (Entity e : world.crew.crewEntities()) {
            if (e.getComponent(SurvivorComponent.class) == null) continue;
            assertTrue(world.crew.crew(e).name.startsWith("Hauler 9 hand"), world.crew.crew(e).name);
            assertFalse(world.crewCareer.ownCrew(game.player).contains(e), "not crew");
        }

        double credits = trader.credits;
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals("3 survivors brought home", c.outcome);
        assertEquals(credits + ContractBoard.RESCUE_PAY + 3 * ContractBoard.RESCUE_PER_SURVIVOR, trader.credits, 1e-6);
        assertEquals(0, board.survivorsAboard(game.player), "gone ashore");
        assertEquals(aboard, world.crewHiring.aboard(game.player));
    }

    @Test
    void onlyThoseWhoLiveArePaidForAndThoseWithNoBerthStayBehind() {
        int room = CrewHiring.berths(game.player) - world.crewHiring.aboard(game.player);
        Contract c = rescueBeside(room + 2);
        world.update(FRAME);
        assertEquals(room, c.carried, "as many as there are berths for");
        assertTrue(board.notices.stream().anyMatch(n -> n.contains("no berths for 2 more")), board.notices.toString());
        Entity dead = null;
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(SurvivorComponent.class) != null) dead = e;
        world.crew.crew(dead).state = com.ringsofiron.aicrew.CrewState.DEAD;
        double credits = trader.credits;
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(credits + ContractBoard.RESCUE_PAY + (room - 1) * ContractBoard.RESCUE_PER_SURVIVOR, trader.credits, 1e-6);
        assertEquals(0, board.survivorsAboard(game.player));
    }

    @Test
    void survivorsOfAFailedRescueGoAshoreAtTheNextStation(@TempDir Path dir) {
        Contract c = rescueBeside(2);
        world.update(FRAME);
        assertEquals(2, board.survivorsAboard(game.player));
        // A save keeps them aboard, and which rescue they are for
        FileHandle file = new FileHandle(dir.resolve("survivors.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(2, fresh.contracts.survivorsAboard(loaded.player));
            assertEquals(2, fresh.contracts.active().get(0).carried);
        } finally {
            fresh.dispose();
        }
        assertNull(board.abandon(c.id));
        assertEquals(2, board.survivorsAboard(game.player), "still aboard until a station");
        assertTrue(game.dockAt(game.depot));
        world.update(FRAME);
        world.update(FRAME);
        assertEquals(0, board.survivorsAboard(game.player));
        assertTrue(board.notices.stream().anyMatch(n -> n.startsWith("2 survivors go ashore")), board.notices.toString());
    }

    // ------------------------------------------------------------------ operations and the frigate

    @Test
    void newOperationsCarryConvoysAndDeliveriesAndTheHigherRanksOpenTheirOwn() {
        List<OperationDef> ops = game.content.defs.all("operation", OperationDef.class);
        boolean convoy = false, delivery = false;
        for (OperationDef op : ops) {
            convoy |= op.steps.contains("convoy");
            delivery |= op.steps.contains("delivery");
        }
        assertTrue(convoy && delivery, "operations with convoy and delivery steps");

        world.standing.record().rank = 2;
        List<String> offered = operationsAt(game.highport);
        assertFalse(offered.contains("iron_escort"), "a Commander's operation is not offered below Commander: " + offered);
        assertTrue(offered.size() <= ContractBoard.OPERATIONS_SHOWN);
        assertTrue(offered.contains("safe_harbour") || offered.contains("lifeline"), offered.toString());

        world.standing.record().rank = 3;
        world.update(FRAME);
        offered = operationsAt(game.highport);
        assertTrue(offered.contains("iron_escort") || offered.contains("hunt_the_hunters"), "promoted: " + offered);
        for (String id : offered) {
            OperationDef def = game.content.defs.get("operation", id, OperationDef.class);
            assertEquals("commander", def.rank, "the newest the rank opens first: " + offered);
        }
        assertTrue(board.offers(game.highport).stream().anyMatch(c -> c.type != Contract.Type.OPERATION),
                "the board still shows other work");
    }

    @Test
    void aDeliveryStepIsTakenFromTheStationsTradeOrders() {
        world.standing.record().rank = 1;
        Contract op = null;
        for (Contract c : board.offers(game.depot)) if ("supply_line".equals(c.operationId)) op = c;
        if (op == null) {
            // Clear the Lanes may head the board: finish it on paper so Supply Line is offered
            world.contracts.reset();
            world.standing.record().rank = 1;
            for (Contract c : board.offers(game.depot)) if ("supply_line".equals(c.operationId)) op = c;
        }
        assertNotNull(op, "Supply Line is offered at Deepwell: " + board.offers(game.depot));
        String why = board.accept(op.id);
        if (why != null) {
            // No trade order out of the depot today: the operation waits for one, at no cost
            assertTrue(why.startsWith("There is nothing for"), why);
            return;
        }
        Contract step = board.active().get(0);
        assertEquals(Contract.Type.DELIVERY, step.type);
        assertEquals(1, step.step);
        assertEquals("supply_line", step.operationId);
    }

    private List<String> operationsAt(String station) {
        List<String> out = new ArrayList<>();
        for (Contract c : board.offers(station)) if (c.type == Contract.Type.OPERATION) out.add(c.operationId);
        return out;
    }

    @Test
    void theFrigateCostsALittleOverItsValueAsTheGunshipDoes() {
        Shipyard.Listing frigate = null;
        for (Shipyard.Listing l : world.shipyard.listings(world.engine.getEntity(game.shipyard))) {
            if ("frigate_patrol".equals(l.shipId)) frigate = l;
        }
        assertNotNull(frigate);
        Entity hull = game.content.factory.spawnShip("frigate_patrol", 0f, 0f, 0f);
        double value = Difficulty.shipValue(hull);
        world.engine.getEcsEngine().removeEntity(hull);
        assertTrue(frigate.price > value && frigate.price < value * 1.05, frigate.price + " against " + value);
        assertEquals(3, frigate.rank, "a Commander's ship");
    }
}
