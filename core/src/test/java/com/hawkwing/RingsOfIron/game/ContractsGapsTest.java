package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.economy.TraderComponent;
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

/** Round 3's contract gaps: a convoy's grace either side, a recovery into a full hold, and the Lifeline operation. */
class ContractsGapsTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;
    ContractBoard board;
    TraderComponent trader;
    String kestrel, hauler;

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
        kestrel = freighterNamed("Hauler Kestrel");
        hauler = freighterNamed("Hauler 1");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private String freighterNamed(String name) {
        for (Map.Entry<String, FreighterComponent> e : world.macroSim.freighters().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return e.getKey();
        }
        fail("no freighter " + name);
        return null;
    }

    private double now() {
        return world.engine.getSimSeconds();
    }

    /** Puts water orders out of Highport for the depot on the board, one hauled by each freighter given. */
    private void haulsToTheDepot(String... freighters) {
        List<TradeOrder> orders = new ArrayList<>(world.macroSim.economy().orders());
        long id = 9_200;
        for (String f : freighters) {
            TradeOrder o = new TradeOrder(id++, "water", game.highport, game.depot, 50, 1, 2);
            o.claimedBy = f;
            orders.add(o);
        }
        world.macroSim.economy().restoreOrders(orders, id);
    }

    // ------------------------------------------------------------------ convoy grace

    /** Warps the clock on by {@code seconds}, so a time a grace back from now is not before the game began. */
    private void passTime(double seconds) {
        double until = now() + seconds;
        assertTrue(world.timeWarp.setWarp(1_000), String.valueOf(world.engine.getRailsWarpBlockedReason()));
        while (now() < until) world.update(FRAME);
        assertTrue(world.timeWarp.setWarp(1));
        world.update(FRAME);
    }

    /** A taken convoy of Kestrel and Hauler 1 to the depot, with the player still at Highport, far off. */
    private Contract takenConvoy() {
        passTime(2 * ContractBoard.ESCORT_GRACE);
        assertTrue(board.distanceTo(game.player, game.highport) <= ContractBoard.ESCORT_RANGE, "still beside Highport");
        haulsToTheDepot(kestrel, hauler);
        Contract c = null;
        for (Contract o : board.offers(game.highport)) if (o.type == Contract.Type.CONVOY) c = o;
        assertNotNull(c);
        world.standing.record().rank = 1;
        assertNull(board.accept(c.id));
        assertTrue(board.distanceTo(game.player, game.depot) > ContractBoard.ESCORT_RANGE, "the player is not at the depot");
        return c;
    }

    private int index(Contract c, String freighter) {
        for (int i = 0; i < c.targets.length; i++) if (c.targets[i].equals(freighter)) return i;
        fail(freighter + " is not in the convoy");
        return -1;
    }

    @Test
    void aConvoyFreighterDockedWithoutThePlayerCountsIfTheyTurnUpWithinTheGrace() {
        Contract c = takenConvoy();
        int k = index(c, kestrel), h = index(c, hauler);
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.macroSim.freighters().get(hauler).tripsCompleted++;
        world.update(FRAME);
        assertEquals(Contract.MARK_OPEN, c.marks[k], "still waiting on the player");
        assertEquals(Contract.MARK_OPEN, c.marks[h]);
        assertEquals(Contract.Status.ACTIVE, c.status);

        // Most of the grace gone, then the player turns up (here the buyer is moved to them)
        c.arrivals[k] = c.arrivals[h] = now() - (ContractBoard.ESCORT_GRACE - 60);
        world.update(FRAME);
        assertEquals(Contract.MARK_OPEN, c.marks[k], "within the grace");
        double credits = trader.credits, full = c.reward;
        float standing = world.standing.standing(c.giver), offered = c.standing;
        c.destinationUuid = game.highport;
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(2, c.count(Contract.MARK_GOOD));
        assertEquals(credits + full, trader.credits, 1e-6, "paid in full");
        assertEquals(standing + offered, world.standing.standing(c.giver), 1e-4);
    }

    @Test
    void aConvoyFreighterDockedPastTheGraceWithoutThePlayerIsMarkedBad() {
        Contract c = takenConvoy();
        int k = index(c, kestrel), h = index(c, hauler);
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.update(FRAME);
        c.arrivals[k] = now() - ContractBoard.ESCORT_GRACE - 1;
        world.update(FRAME);
        assertEquals(Contract.MARK_BAD, c.marks[k]);
        assertTrue(board.notices.contains("Convoy: Hauler Kestrel docked without you"), board.notices.toString());
        assertEquals(Contract.Status.ACTIVE, c.status, "Hauler 1 is still to come");

        // Turning up afterwards does not bring Kestrel back; Hauler 1 then comes in with the player
        c.destinationUuid = game.highport;
        world.update(FRAME);
        assertEquals(Contract.MARK_BAD, c.marks[k]);
        double full = c.reward;
        world.macroSim.freighters().get(hauler).tripsCompleted++;
        world.update(FRAME);
        assertEquals(Contract.MARK_GOOD, c.marks[h]);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals("convoy in, 1 of 2", c.outcome);
        assertEquals(full / 2, c.reward, 1e-6);
    }

    @Test
    void aConvoyAllPastTheGraceFailsBeforeItsDeadline() {
        Contract c = takenConvoy();
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.macroSim.freighters().get(hauler).tripsCompleted++;
        world.update(FRAME);
        java.util.Arrays.fill(c.arrivals, now() - ContractBoard.ESCORT_GRACE - 1);
        assertTrue(now() < c.deadline);
        float standing = world.standing.standing(c.giver);
        world.update(FRAME);
        assertEquals(Contract.Status.FAILED, c.status);
        assertEquals("none of the convoy came in with you", c.outcome);
        assertEquals(standing - c.standing * ContractBoard.FAIL_SHARE, world.standing.standing(c.giver), 1e-4);
    }

    @Test
    void theGraceDoesNotOutrunTheDeadline() {
        Contract c = takenConvoy();
        int k = index(c, kestrel);
        world.macroSim.freighters().get(kestrel).tripsCompleted++;
        world.macroSim.destroy(hauler);
        world.update(FRAME);
        assertEquals(Contract.MARK_OPEN, c.marks[k], "Kestrel is in its grace");
        c.deadline = now() - 1;
        world.update(FRAME);
        assertEquals(Contract.MARK_BAD, c.marks[k], "what is still open at the deadline went badly");
        assertEquals(Contract.Status.FAILED, c.status);
    }

    // ------------------------------------------------------------------ recovery into a full hold

    private CommodityType water() {
        return world.macroSim.economy().catalog().commodity("water");
    }

    /** A taken recovery of {@code units} water from a wreck drifting beside the player. */
    private Contract takenRecovery(double units) {
        Derelicts.Derelict wreck = world.derelicts.castOff(game.player, Derelicts.Kind.WRECK, "Test wreck", 0, "water", units,
                new Random(1));
        assertNotNull(wreck);
        Contract c = board.offerFor(wreck, game.highport, "");
        assertTrue(board.postEvent(c));
        assertNull(board.accept(c.id));
        return c;
    }

    /** Holds alongside until the crew's carrying time is up. */
    private void carryAcross(Contract c) {
        world.update(FRAME);
        assertTrue(c.arrivedAt >= 0, "holding alongside");
        double start = c.arrivedAt;
        while (c.progress == 0 && c.arrivedAt == start && now() < start + Derelicts.SALVAGE_SECONDS + 2) world.update(0.25f);
    }

    @Test
    void aRecoveryIntoAFullHoldIsRefusedAndNotDone() {
        trader.stow(water(), trader.room(water()));
        assertEquals(0, trader.room(water()), 0.5, "the hold is full");
        double inHold = trader.amount("water"), credits = trader.credits;
        Contract c = takenRecovery(5);
        carryAcross(c);
        assertEquals(0, c.progress, "nothing carried over");
        assertTrue(board.notices.contains("No room in the hold for the " + c.commodityName), board.notices.toString());
        assertNotNull(world.derelicts.get(c.targetUuid), "the wreck is left to try again");
        assertEquals(inHold, trader.amount("water"), 1e-6);
        assertTrue(c.arrivedAt >= now() - 1, "the crew start over");

        // Docking at the station with nothing aboard from the wreck does not finish it
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.ACTIVE, c.status, c.outcome);
        assertEquals(credits, trader.credits, 1e-6);
        assertEquals(inHold, trader.amount("water"), 1e-6, "the player's own water is not handed over");
    }

    @Test
    void aRecoveryIntoANearlyFullHoldTakesWhatFitsAndPaysForThat() {
        trader.stow(water(), trader.room(water()));
        trader.handOver(water(), 2);
        assertEquals(2, trader.room(water()), 1e-6);
        double inHold = trader.amount("water");
        Contract c = takenRecovery(5);
        double full = c.reward;
        carryAcross(c);
        assertEquals(1, c.progress, board.notices.toString());
        assertEquals(2, c.quantity, 1e-6, "only what fitted");
        assertEquals(Math.round(full * 2 / 5), c.reward, 1e-6);
        assertNull(world.derelicts.get(c.targetUuid), "the rest is left with the wreck, which is gone");

        double credits = trader.credits;
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, c.status, c.outcome);
        assertEquals(inHold, trader.amount("water"), 1e-6, "the two recovered handed over");
        assertEquals(credits + c.reward, trader.credits, 1e-6);
    }

    // ------------------------------------------------------------------ the Lifeline operation

    private int rank(String id) {
        RankLadder ranks = world.standing.ranks();
        for (int i = 0; i < ranks.size(); i++) if (id.equals(ranks.get(i).id)) return i;
        fail("no rank " + id);
        return -1;
    }

    private Contract operationOffer(String station, String id) {
        for (Contract c : board.offers(station)) if (c.type == Contract.Type.OPERATION && id.equals(c.operationId)) return c;
        return null;
    }

    @Test
    void lifelineIsOfferedFromLieutenantCommanderAndRunsEscortReconBountyToItsReward() {
        int ltCdr = rank("lieutenant_commander");
        world.standing.record().rank = ltCdr - 1;
        assertNull(operationOffer(game.highport, "lifeline"), "not below lieutenant commander");
        world.standing.record().rank = ltCdr;
        Contract op = operationOffer(game.highport, "lifeline");
        assertNotNull(op, "offered on promotion");
        assertEquals("Lifeline", op.operationName);
        assertEquals(15_000, op.reward, 1e-6);
        assertEquals(8, op.standing, 1e-6);
        assertEquals(3, op.steps);

        haulsToTheDepot(kestrel);
        assertNull(board.accept(op.id));
        ContractBoard.Operation running = board.operation();
        assertNotNull(running);
        assertEquals("lifeline", running.id);
        assertEquals(1, board.active().size());
        Contract escort = board.active().get(0);
        assertEquals(Contract.Type.ESCORT, escort.type);
        assertEquals(1, escort.step);
        assertTrue(escort.heading().startsWith("Lifeline 1/3: "), escort.heading());
        Contract other = operationOffer(game.highport, "clear_the_lanes");
        if (other != null) assertEquals("You are already on Lifeline", board.accept(other.id), "one operation at a time");

        // Escort: its freighter docks with the player close (here, at Highport)
        escort.destinationUuid = game.highport;
        world.macroSim.freighters().get(escort.targetUuid).tripsCompleted++;
        double credits = trader.credits;
        float standing = world.standing.standing("patrol");
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, escort.status, escort.outcome);
        Contract recon = board.active().get(0);
        assertEquals(Contract.Type.RECON, recon.type);
        assertEquals(2, recon.step);
        assertTrue(recon.heading().startsWith("Lifeline 2/3: "), recon.heading());

        // Recon: the raider outside Highport stands in for the pirate
        String raider = world.universe.uuidOf(game.raider);
        recon.targetUuid = raider;
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, recon.status, recon.outcome);
        assertEquals(raider, running.quarry);
        Contract bounty = board.active().get(0);
        assertEquals(Contract.Type.BOUNTY, bounty.type);
        assertEquals(3, bounty.step);
        assertEquals(raider, bounty.targetUuid, "the bounty is on the pirate the recon found");

        world.engine.removeEntity(game.raider);
        world.update(FRAME);
        assertEquals(Contract.Status.DONE, bounty.status, bounty.outcome);
        assertNull(board.operation());
        assertTrue(board.operationDone("lifeline"));
        assertEquals(credits + escort.reward + recon.reward + bounty.reward + 15_000, trader.credits, 1e-6);
        assertEquals(standing + escort.standing + recon.standing + bounty.standing + 8, world.standing.standing("patrol"), 1e-3);
        assertNull(operationOffer(game.shipyard, "lifeline"), "never offered again");
    }
}
