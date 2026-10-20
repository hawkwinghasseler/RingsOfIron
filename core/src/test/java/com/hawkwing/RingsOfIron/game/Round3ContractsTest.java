package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Round 3's new jobs: rescues, recoveries, convoys, defences, operations, bounty records, and what a rescue costs. */
class Round3ContractsTest {
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
        for (Contract c : board.offers(station)) if (c.type == type) return c;
        return null;
    }

    /** A pod or wreck cast off from the player's own ship, so it drifts right beside it. */
    private Derelicts.Derelict besidePlayer(Derelicts.Kind kind, int survivors, String commodity, double quantity) {
        Derelicts.Derelict d = world.derelicts.castOff(game.player, kind, kind == Derelicts.Kind.POD ? "Test pod" : "Test wreck",
                survivors, commodity, quantity, new Random(1));
        assertNotNull(d);
        return d;
    }

    private String freighterNamed(String name) {
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return e.getKey();
        }
        fail("no freighter " + name);
        return null;
    }

    // ------------------------------------------------------------------ rescue and recovery

    @Test
    void aPodIsPickedUpWithASlowPassAndItsSurvivorsBroughtHome() {
        Derelicts.Derelict pod = besidePlayer(Derelicts.Kind.POD, 4, null, 0);
        assertTrue(world.derelicts.closeTo(game.player, pod), "it drifts off at a few m/s");
        Contract c = board.offerFor(pod, game.highport, "a test");
        assertTrue(board.postEvent(c));
        assertTrue(board.offers(game.highport).contains(c), "posted beside the day's offers");
        assertEquals("Rescue 4 from Test pod", c.title());
        assertNull(board.accept(c.id));
        world.update(FRAME);
        assertEquals(1, c.progress, "picked up");
        assertNull(world.derelicts.get(pod.id), "off the rails");
        assertEquals(game.highport, c.waypointUuid());

        double credits = trader.credits;
        float standing = world.standing.standing("patrol");
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(credits + c.reward, trader.credits, 1e-6);
        assertEquals(standing + c.standing, world.standing.standing("patrol"), 1e-4);
    }

    @Test
    void aPodWhoseAirRunsOutFailsItsRescue() {
        Derelicts.Derelict pod = besidePlayer(Derelicts.Kind.POD, 2, null, 0);
        // Out of reach, so it is not picked up first
        pod.x += 50_000;
        pod.orbit = null;
        Contract c = board.offerFor(pod, game.highport, "");
        board.postEvent(c);
        assertNull(board.accept(c.id));
        pod.expires = world.engine.getSimSeconds() - 1;
        world.update(FRAME);
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertTrue(c.outcome.contains("air"), c.outcome);
    }

    @Test
    void aWrecksCargoIsCarriedAcrossWhileHoldingAlongsideThenHandedOverAtTheStation() {
        Derelicts.Derelict wreck = besidePlayer(Derelicts.Kind.WRECK, 0, "water", 5);
        Contract c = board.offerFor(wreck, game.highport, "");
        assertEquals(Contract.Type.RECOVERY, c.type);
        board.postEvent(c);
        assertNull(board.accept(c.id));
        world.update(FRAME);
        assertTrue(c.arrivedAt >= 0, "holding alongside");
        assertEquals(0, c.progress, "the crew take a while");
        assertTrue(board.progress(c).contains("carrying across"));
        double start = world.engine.getSimSeconds();
        while (c.progress == 0 && world.engine.getSimSeconds() < start + Derelicts.SALVAGE_SECONDS + 2) world.update(0.25f);
        assertEquals(1, c.progress, board.notices.toString());
        assertTrue(world.engine.getSimSeconds() >= start + Derelicts.SALVAGE_SECONDS - 1, "after the crew's work");
        assertEquals(5, trader.amount("water"), 1e-6, "in the hold");
        assertNull(world.derelicts.get(wreck.id));

        double credits = trader.credits;
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(0, trader.amount("water"), 1e-6, "handed over");
        assertEquals(credits + c.reward, trader.credits, 1e-6);
    }

    @Test
    void aDerelictNearThePlayerIsMarkedAndShownOnTheTrafficMap() {
        Derelicts.Derelict pod = besidePlayer(Derelicts.Kind.POD, 3, null, 0);
        world.update(FRAME);
        Entity marker = world.derelicts.marker(pod);
        assertNotNull(marker);
        assertEquals("Test pod", marker.getComponent(HudMarkerComponent.class).label);
        Contract c = board.offerFor(pod, game.highport, "");
        board.postEvent(c);
        pod.x += 50_000; // not picked up at once
        pod.orbit = null;
        assertNull(board.accept(c.id));
        world.update(FRAME);
        assertTrue(world.derelicts.marker(pod).getComponent(HudMarkerComponent.class).label.endsWith(ContractBoard.MARKER_SUFFIX));
        var frame = new com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame();
        new RailsTrafficMap(world).collect(frame);
        boolean shown = false;
        for (var contact : frame.contacts) shown |= "Test pod".equals(contact.name) && contact.objective;
        assertTrue(shown);
    }

    // ------------------------------------------------------------------ convoys and defence

    @Test
    void aConvoyIsPaidForTheFreightersThatCameInWithThePlayer() {
        String kestrel = freighterNamed("Hauler Kestrel"), other = freighterNamed("Hauler 1");
        List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
        TradeOrder a = new TradeOrder(9_200, "water", game.highport, game.depot, 50, 1, 2);
        a.claimedBy = kestrel;
        TradeOrder b = new TradeOrder(9_201, "water", game.highport, game.depot, 50, 1, 2);
        b.claimedBy = other;
        orders.add(a);
        orders.add(b);
        world.macroSim.economy().restoreOrders(orders, 9_202);
        Contract c = offer(game.highport, Contract.Type.CONVOY);
        assertNotNull(c, "two freighters out of Highport for the depot");
        assertEquals(2, c.targets.length);
        assertEquals("Convoy of 2 to Deepwell Depot", c.title());
        double full = c.reward;
        world.standing.record().rank = 1;
        assertNull(board.accept(c.id));

        // One docks with the player close (here, at Highport); the other is lost
        c.destinationUuid = game.highport;
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.update(FRAME);
        assertEquals(1, c.count(Contract.MARK_GOOD));
        assertEquals(Contract.Status.ACTIVE, c.status);
        double credits = trader.credits;
        world.macroSim.destroy(other);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(full / 2, c.reward, 1e-6, "half the convoy, half the pay");
        assertEquals(credits + full / 2, trader.credits, 1e-6);
    }

    @Test
    void aRaidPostsADefenceThatIsDoneWhenTheRaidersAreBeaten() {
        assertTrue(world.events.happen(WorldEvents.Kind.RAID, 1, new Random(3)));
        WorldEvents.Event e = world.events.log().get(world.events.log().size() - 1);
        assertEquals(WorldEvents.Kind.RAID, e.kind);
        Contract c = offer(e.station, Contract.Type.DEFEND);
        assertNotNull(c, "the raided station asks for help");
        assertEquals(e.station, c.destinationUuid);
        for (String raider : c.targets) {
            HostileComponent h = world.macroSim.hostiles().get(raider);
            assertNotNull(h);
            assertEquals(e.station, h.stationUuid, "lurking by the station");
        }
        world.standing.record().rank = 4;
        assertNull(board.accept(c.id));
        for (String raider : c.targets) destroyWithCrew(raider);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
    }

    @Test
    void aDefenceRunOutOfTimePaysForTheRaidersBeatenAndFailsIfNone() {
        assertTrue(world.events.happen(WorldEvents.Kind.RAID, 1, new Random(3)));
        WorldEvents.Event e = world.events.log().get(world.events.log().size() - 1);
        Contract c = offer(e.station, Contract.Type.DEFEND);
        world.standing.record().rank = 4;
        assertNull(board.accept(c.id));
        float standing = world.standing.standing(c.giver);
        c.deadline = world.engine.getSimSeconds() - 1;
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing(c.giver), 1e-4);
    }

    /** Takes a pirate out of the game with its crew, as if it had blown up. */
    private void destroyWithCrew(String uuid) {
        Entity ship = world.engine.getEntity(uuid);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(
                com.badlogic.ashley.core.Family.all(com.ringsofiron.aicrew.GridPositionComponent.class).get()).toArray(Entity.class)) {
            if (e.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class).ship == ship) world.engine.removeEntity(e);
        }
        world.macroSim.destroy(uuid);
    }

    // ------------------------------------------------------------------ operations

    @Test
    void anOperationIsOfferedAtItsRankAndRunsItsStepsToItsReward() {
        assertNull(offer(game.depot, Contract.Type.OPERATION), "not for an ensign");
        world.standing.record().rank = 1;
        Contract op = offer(game.highport, Contract.Type.OPERATION);
        assertNotNull(op, "a lieutenant is offered one");
        assertEquals("Clear the Lanes", op.operationName);
        assertTrue(op.reason.startsWith("Raiders"));
        assertNull(board.accept(op.id));
        ContractBoard.Operation running = board.operation();
        assertNotNull(running);
        assertEquals(1, board.active().size());
        Contract patrol = board.active().get(0);
        assertEquals(Contract.Type.PATROL, patrol.type);
        assertEquals(1, patrol.step);
        assertTrue(patrol.heading().startsWith("Clear the Lanes 1/3: "));
        assertNull(offer(game.highport, Contract.Type.OPERATION), "one at a time");

        // Steps don't count against the limit
        int taken = 0;
        for (Contract c : board.offers(game.highport)) if (board.locked(c) == null && board.accept(c.id) == null) taken++;
        assertTrue(board.active().size() > taken, "the step is held as well");
        for (Contract c : new ArrayList<>(board.active())) if (c.step == 0) board.abandon(c.id);

        // Patrol: make the far end Highport, a few km off
        patrol.targetUuid = game.highport;
        world.update(FRAME);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, patrol.status, patrol.outcome);
        Contract recon = board.active().get(0);
        assertEquals(Contract.Type.RECON, recon.type);
        assertEquals(2, recon.step);

        // Recon: the raider outside Highport stands in for the pirate
        String raider = world.universe.uuidOf(game.raider);
        recon.targetUuid = raider;
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, recon.status, recon.outcome);
        assertEquals(raider, running.quarry);
        Contract bounty = board.active().get(0);
        assertEquals(Contract.Type.BOUNTY, bounty.type);
        assertEquals(raider, bounty.targetUuid, "the bounty is on the pirate the recon found");

        double credits = trader.credits;
        world.engine.removeEntity(game.raider);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, bounty.status);
        assertNull(board.operation());
        assertTrue(board.operationDone("clear_the_lanes"));
        assertEquals(credits + bounty.reward + running.reward, trader.credits, 1e-6);
        Contract again = offer(game.shipyard, Contract.Type.OPERATION);
        assertTrue(again == null || !"clear_the_lanes".equals(again.operationId), "never offered again");
    }

    @Test
    void abandoningAStepFailsTheOperation() {
        world.standing.record().rank = 1;
        Contract op = offer(game.highport, Contract.Type.OPERATION);
        assertNull(board.accept(op.id));
        float standing = world.standing.standing("patrol");
        Contract step = board.active().get(0);
        assertNull(board.abandon(step.id));
        assertNull(board.operation());
        assertFalse(board.operationDone("clear_the_lanes"));
        assertEquals(standing - (step.standing + op.standing) * ContractBoard.FAIL_SHARE,
                world.standing.standing("patrol"), 1e-4);
    }

    // ------------------------------------------------------------------ the pirate base

    @Test
    void aRaidOnThePirateBaseIsOfferedOnceItIsFoundAndDoneWhenItFalls() {
        assertNotNull(world.pirateBase.uuid());
        world.standing.record().rank = ContractBoard.RAID_RANK;
        assertNull(offer(game.highport, Contract.Type.RAID), "not while the base is hidden");
        world.pirateBase.reveal(null);
        Contract raid = offer(game.shipyard, Contract.Type.RAID);
        assertNotNull(raid, "the sponsor offers a raid once it is found");
        assertEquals(world.pirateBase.uuid(), raid.targetUuid);
        assertEquals("Raid " + GameScenario.PIRATE_BASE_NAME, raid.title());
        assertNull(offer(game.lunarMine, Contract.Type.RAID), "only the sponsor posts it");
        world.standing.record().rank = 0;
        assertNotNull(board.locked(raid), "it needs rank");
        world.standing.record().rank = ContractBoard.RAID_RANK;
        assertNull(board.accept(raid.id));
        world.update(FRAME);
        assertEquals(Contract.Status.ACTIVE, raid.status);
        double credits = trader.credits;
        world.pirateBase.fall("test");
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, raid.status, raid.outcome);
        assertEquals(credits + raid.reward, trader.credits, 1e-6);
    }

    @Test
    void anIntelStepFollowsAPirateHomeAndTheRaidComesNext() {
        world.standing.record().rank = 3;
        Contract op = null;
        for (Contract c : board.offers(game.highport)) if ("hunt_the_hunters".equals(c.operationId)) op = c;
        assertNotNull(op, "a commander is offered the hunt");
        assertNull(board.accept(op.id));
        ContractBoard.Operation running = board.operation();
        String raider = world.universe.uuidOf(game.raider);
        // Recon and bounty: on the raider outside Highport
        Contract recon = board.active().get(0);
        recon.targetUuid = raider;
        world.update(FRAME);
        Contract bounty = board.active().get(0);
        assertEquals(Contract.Type.BOUNTY, bounty.type);
        bounty.targetUuid = "gone";
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, bounty.status);
        Contract intel = board.active().get(0);
        assertEquals(Contract.Type.RECON, intel.type);
        assertTrue(intel.intel);
        assertFalse(world.pirateBase.found());
        // Another raider stands in for the pirate to follow; it is close
        Entity other = world.engine.getEntity(raider);
        intel.targetUuid = raider;
        assertNotNull(other);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, intel.status, intel.outcome);
        assertTrue(world.pirateBase.found(), "followed home");
        Contract raid = board.active().get(0);
        assertEquals(Contract.Type.RAID, raid.type);
        assertEquals(4, raid.step);
        double credits = trader.credits;
        world.pirateBase.fall("test");
        world.update(FRAME);
        assertNull(board.operation());
        assertTrue(board.operationDone("hunt_the_hunters"));
        assertEquals(credits + raid.reward + running.reward, trader.credits, 1e-6);
    }

    // ------------------------------------------------------------------ records and rescue

    @Test
    void aPirateThatRobbedFreightersCarriesItInItsBountyForMorePay() {
        String pirate = null;
        for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
            if (e.getValue().isPirate()) pirate = e.getKey();
        }
        assertNotNull(pirate);
        var node = world.macroSim.station(game.highport);
        int sponsor = world.standing.sponsor();
        double plain = find(board.bounties(node, sponsor), pirate).reward;
        HostileComponent h = world.macroSim.hostiles().get(pirate);
        h.plunders = 2;
        h.lootCommodity = "water";
        Contract c = find(board.bounties(node, sponsor), pirate);
        assertEquals("robbed 2 freighters, last of Water", c.record);
        assertTrue(c.title().endsWith("(robbed 2 freighters, last of Water)"), c.title());
        assertEquals(Math.round(plain * (1 + 2 * ContractBoard.RECORD_PREMIUM)), c.reward, 1e-6);
    }

    private static Contract find(List<Contract> offers, String target) {
        for (Contract c : offers) if (target.equals(c.targetUuid)) return c;
        fail("no bounty on " + target);
        return null;
    }

    @Test
    void aRescueCostsSponsorStandingAndFailsEveryContractHeld() {
        Contract c = offer(game.highport, Contract.Type.BOUNTY);
        assertNull(board.accept(c.id));
        float standing = world.standing.standing("patrol");
        world.rescue.rescues.dispatch(new Rescue.Result(game.player, game.player, null, "Highport", List.of(), 0, 0, 0));
        assertEquals(Contract.Status.FAILED, c.status);
        assertEquals("you had to be rescued", c.outcome);
        assertEquals(standing - LoopWiring.RESCUE_COST - c.standing * ContractBoard.FAIL_SHARE,
                world.standing.standing("patrol"), 1e-4);
    }

    // ------------------------------------------------------------------ window and saving

    @Test
    void theMissionWindowShowsWhyAJobIsPosted() {
        assertTrue(world.events.happen(WorldEvents.Kind.RAID, 1, new Random(3)));
        WorldEvents.Event e = world.events.log().get(world.events.log().size() - 1);
        // Dock where the raid is, if it is Highport; else look at the offers there directly
        Contract c = offer(e.station, Contract.Type.DEFEND);
        assertEquals(e.text, c.reason);
        assertTrue(ContractMissions.terms(c, "2.0 d").startsWith("up to "));
        if (e.station.equals(game.highport)) {
            assertTrue(game.dockAtHighport());
            MissionProvider.MissionBoard out = new MissionProvider.MissionBoard();
            new ContractMissions(world).collect(out);
            boolean noted = false;
            for (MissionProvider.Job j : out.offers) noted |= e.text.equals(j.note);
            assertTrue(noted);
        }
    }

    @Test
    void podsOperationsAndPostedJobsSaveWithTheGame(@TempDir Path dir) {
        Derelicts.Derelict pod = besidePlayer(Derelicts.Kind.POD, 3, null, 0);
        pod.x += 50_000;
        pod.orbit = null;
        Contract rescue = board.offerFor(pod, game.highport, "lost at sea");
        board.postEvent(rescue);
        world.standing.record().rank = 1;
        Contract op = offer(game.highport, Contract.Type.OPERATION);
        assertNull(board.accept(op.id));
        assertTrue(world.events.happen(WorldEvents.Kind.SHORTAGE, 1, new Random(5)));
        world.update(FRAME);
        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            Derelicts.Derelict back = fresh.derelicts.get(pod.id);
            assertNotNull(back);
            assertEquals(3, back.survivors);
            assertEquals(pod.x, back.x, 1e-6);
            assertTrue(fresh.contracts.offers(game.highport).stream().anyMatch(o -> o.id == rescue.id && "lost at sea".equals(o.reason)));
            assertNotNull(fresh.contracts.operation());
            assertEquals("clear_the_lanes", fresh.contracts.operation().id);
            assertEquals(1, fresh.contracts.active().size());
            assertEquals(1, fresh.contracts.active().get(0).step);
            assertEquals(world.events.log().size(), fresh.events.log().size());
            assertEquals(world.events.seed, fresh.events.seed);
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void operationsInTheDataAreValid() {
        assertFalse(game.content.report.hasErrors(), game.content.report.toString());
        assertFalse(game.content.report.toString().contains("operation."), game.content.report.toString());
        assertEquals(7, game.content.defs.ids("operation").size());
    }
}
