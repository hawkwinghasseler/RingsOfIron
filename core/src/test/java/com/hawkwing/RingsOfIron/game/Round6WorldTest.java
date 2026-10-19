package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
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
 * Round 6 part 6, a world that reacts: tension raising a station's yard prices and adding combat jobs
 * to its board, a rival's board offering fewer jobs to a player it distrusts, the tension readout on
 * the traffic map, survivors coming aboard hurt by their time in the pod, and what follows the
 * pirates' base falling (a Captain's operation, and a new base rising after long strain).
 */
class Round6WorldTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;
    ContractBoard board;
    TraderComponent trader;
    int patrol, selene, traders;

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
        patrol = world.standing.factions().index("patrol");
        selene = world.standing.factions().index("selene");
        traders = world.standing.factions().index("traders");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    // ------------------------------------------------------------------ prices

    @Test
    void theDataTunesTheWorldsReactions() {
        var t = world.tension.tuning();
        assertEquals(0.25f, t.priceRise, 1e-6);
        assertEquals(50f, t.combatJobsAt, 1e-6);
        assertEquals(1, t.combatJobs);
        assertEquals(20, t.baseRisesAfterDays, 1e-9);
        assertFalse(game.content.report.toString().contains("tension."), game.content.report.toString());
    }

    @Test
    void tensionRaisesAStationsYardPricesUpToAQuarter() {
        Entity highport = world.engine.getEntity(game.highport), shackleton = world.engine.getEntity(game.lunarMine);
        assertEquals(1.0, world.tension.priceFactor(highport), 1e-9, "calm: base prices");
        world.tension.raise(patrol, selene, 40f, null);
        assertEquals(1.10, world.tension.priceFactor(highport), 1e-6, "a tenth at 40");
        assertEquals(1.10, world.tension.priceFactor(shackleton), 1e-6, "both sides of the strain");
        assertEquals(1.0, world.tension.priceFactor(world.engine.getEntity(game.freehold)), 1e-9, "not the Free Traders, who are calm");
        world.tension.raise(patrol, traders, 100f, null);
        assertEquals(1.25, world.tension.priceFactor(highport), 1e-6, "a quarter at full: the highest strain counts");

        // The yard charges it for the tanks...
        assertTrue(game.dockAtHighport());
        world.update(FRAME);
        ShipSubsystemRegistryComponent registry = game.player.getComponent(ShipSubsystemRegistryComponent.class);
        for (SubsystemComponent tank : TankSupply.tanks(registry, null)) tank.contents = tank.capacity / 2;
        double base = world.yard.refillCost(game.player);
        assertTrue(base > 1);
        assertEquals(base * 1.25, world.yard.refillCost(game.player, highport), 1e-6);
        trader.credits = 1e9;
        double credits = trader.credits;
        world.yard.orderRefill(game.player, highport);
        assertEquals(base * 1.25, credits - trader.credits, 1e-3, "paid at the strained price");

        // ...and for the repairs, as the work is done
        SubsystemComponent worn = null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent sub = e.getComponent(SubsystemComponent.class);
            if (worn == null && sub.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) worn = sub;
        }
        worn.health = 0.5f;
        double repair = world.yard.repairCost(game.player);
        assertTrue(repair > 1, "something to repair");
        assertEquals(repair * 1.25, world.yard.repairCost(game.player, highport), 1e-6);
        // The yard's work, not the crew's own: they stand by
        for (Entity e : world.crewCareer.ownCrew(game.player)) world.crew.crew(e).held = true;
        credits = trader.credits;
        String said = world.yard.orderRepairs(game.player, highport);
        assertTrue(said.startsWith("Repairs ordered"), said);
        for (int i = 0; i < 36_000 && world.yard.repairing(game.player); i++) world.update(FRAME);
        assertFalse(world.yard.repairing(game.player), "repaired");
        assertEquals(repair * 1.25, credits - trader.credits, repair * 0.02, "paid at the strained price");
    }

    // ------------------------------------------------------------------ boards

    private static long count(List<Contract> offers, Contract.Type... types) {
        return offers.stream().filter(c -> List.of(types).contains(c.type)).count();
    }

    @Test
    void highTensionAddsACombatJobToTheBoard() {
        List<Contract> calm = new ArrayList<>(board.offers(game.highport));
        assertTrue(calm.stream().noneMatch(c -> ContractBoard.TENSION_JOB.equals(c.reason)));
        // The next day's board, with the factions strained
        world.tension.raise(patrol, selene, FactionTension.HIGH, null);
        world.contracts.reset();
        List<Contract> strained = board.offers(game.highport);
        List<Contract> extra = strained.stream().filter(c -> ContractBoard.TENSION_JOB.equals(c.reason)).toList();
        assertEquals(1, extra.size(), "one more combat job: " + strained);
        assertTrue(extra.get(0).type == Contract.Type.BOUNTY || extra.get(0).type == Contract.Type.ESCORT, extra.toString());
        assertEquals(ContractBoard.OFFERS_PER_STATION + 1, strained.size() - board.extraOffers().size(),
                "on top of the usual five: " + strained);
    }

    @Test
    void aRivalThatDistrustsThePlayerOffersFewerJobs() {
        int n = ContractBoard.OFFERS_PER_STATION;
        assertEquals(n, world.tension.offersFor(selene, n), "no strain, no distrust");
        world.tension.raise(patrol, selene, 60f, null);
        assertEquals(n - 2, world.tension.offersFor(selene, n), "one fewer for each 25 of tension with the sponsor");
        assertEquals(n, world.tension.offersFor(patrol, n), "the sponsor's own boards are full");
        world.tension.raise(patrol, selene, 40f, null);
        assertEquals(2, world.tension.offersFor(selene, n), "never fewer than two");
        world.standing.record().set("selene", 15f);
        assertEquals(n, world.tension.offersFor(selene, n), "a player they trust gets the full board");
        world.standing.record().set("selene", 0f);

        // Shackleton's board, posted fresh with the strain
        world.contracts.reset();
        long shown = board.offers(game.lunarMine).stream().filter(c -> c.type != Contract.Type.OPERATION
                && !ContractBoard.TENSION_JOB.equals(c.reason)).count() - board.extraOffers().stream()
                .filter(c -> game.lunarMine.equals(c.stationUuid)).count();
        assertTrue(shown <= 2, "a distrustful board: " + shown);
    }

    // ------------------------------------------------------------------ the traffic map

    @Test
    void theTrafficMapShowsEachRegionsTension() {
        world.tension.raise(patrol, selene, 60f, null);
        TrafficMapProvider.TrafficFrame out = new TrafficMapProvider.TrafficFrame();
        new RailsTrafficMap(world).collect(out);
        List<String> lines = new ArrayList<>();
        for (TrafficMapProvider.Region r : out.regions) lines.add(r.line + (r.high ? " !" : ""));
        assertEquals(3, lines.size(), "the Patrol, Selene and the Free Traders, not the pirates: " + lines);
        assertTrue(lines.contains("Terran Patrol Authority: 60 HIGH, yards +15% !"), lines.toString());
        assertTrue(lines.contains("Selene Mining Combine: 60 HIGH, yards +15% !"), lines.toString());
        assertTrue(lines.contains("Free Traders: 0"), lines.toString());
        out.clear();
        assertEquals(0, out.regions.size);
    }

    // ------------------------------------------------------------------ survivors

    private Contract rescueBeside(int survivors, double agoDays) {
        Derelicts.Derelict pod = world.derelicts.castOff(game.player, Derelicts.Kind.POD, "Hauler 9's pod", survivors, null, 0,
                new Random(1));
        pod.expires -= agoDays * Derelicts.DAY;
        Contract c = board.offerFor(pod, game.highport, "a test");
        assertTrue(board.postEvent(c));
        assertNull(board.accept(c.id));
        return c;
    }

    private List<CrewMemberComponent> survivors() {
        List<CrewMemberComponent> out = new ArrayList<>();
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(SurvivorComponent.class) != null) out.add(world.crew.crew(e));
        return out;
    }

    @Test
    void survivorsLongInThePodComeAboardHurtAndAreTended() {
        int room = CrewHiring.berths(game.player) - world.crewHiring.aboard(game.player);
        Contract c = rescueBeside(room, 3.5);
        world.update(FRAME);
        assertEquals(room, c.carried);
        List<CrewMemberComponent> aboard = survivors();
        assertTrue(aboard.stream().allMatch(s -> s.health < s.def.maxHealth), "the pod's stale air told on all of them");
        long wounded = aboard.stream().filter(s -> s.wound != Wound.NONE).count();
        assertTrue(wounded > 0, "some are wounded after days adrift");
        assertTrue(board.notices.stream().anyMatch(n -> n.contains(wounded + " hurt")), board.notices.toString());
        // Held aboard, they are cared for like the crew: a serious or critical wound is treated
        boolean tended = false;
        for (int i = 0; i < 60 / FRAME && !tended; i++) {
            world.update(FRAME);
            for (CrewMemberComponent s : survivors()) tended |= s.treatedBy != null;
        }
        if (aboard.stream().anyMatch(s -> s.wound.disabling())) assertTrue(tended, "a crew member tends them");
    }

    @Test
    void survivorsPickedUpAtOnceAreMostlyWell() {
        int hurt = 0, critical = 0, picked = 0;
        for (int i = 0; i < 6; i++) {
            Contract c = rescueBeside(2, 0);
            world.update(FRAME);
            for (CrewMemberComponent s : survivors()) {
                picked++;
                if (s.wound != Wound.NONE) hurt++;
                if (s.wound == Wound.CRITICAL) critical++;
            }
            assertTrue(game.dockAtHighport());
            world.update(FRAME);
            assertEquals(Contract.Status.DONE, c.status, c.outcome);
            world.docking.undock(game.player);
            run(1);
        }
        assertEquals(12, picked);
        assertEquals(0, critical, "nobody critical straight out of the pod");
        assertTrue(hurt <= 6, "a few hurt: " + hurt);
    }

    // ------------------------------------------------------------------ after the base falls

    @Test
    void afterTheBaseFallsTheSponsorOffersACaptainsOperation() {
        world.standing.record().rank = 4;
        assertFalse(operations(game.highport).contains("scatter_the_remnants"), "not while the base stands");
        world.pirateBase.fall("a test");
        world.contracts.reset();
        List<String> ops = operations(game.highport);
        assertEquals("scatter_the_remnants", ops.get(0), "heads the board once it falls: " + ops);
        world.standing.record().rank = 3;
        world.contracts.reset();
        assertFalse(operations(game.highport).contains("scatter_the_remnants"), "a Captain's: " + operations(game.highport));
    }

    private List<String> operations(String station) {
        List<String> out = new ArrayList<>();
        for (Contract c : board.offers(station)) if (c.type == Contract.Type.OPERATION) out.add(c.operationId);
        return out;
    }

    @Test
    void aNewBaseRisesAfterLongStrain(@TempDir Path dir) {
        world.pirateBase.fall("a test");
        assertFalse(world.pirateBase.standing());
        assertTrue(game.dockAtHighport());
        // Calm: the base stays down however long
        warp(5 * Derelicts.DAY);
        assertFalse(world.pirateBase.standing());
        assertEquals(0, world.pirateBase.strainedDays(), 1e-6, "no strain counted while calm");

        world.tension.raise(patrol, selene, FactionTension.MAX, null);
        warp(10 * Derelicts.DAY);
        assertFalse(world.pirateBase.standing());
        assertEquals(10, world.pirateBase.strainedDays(), 0.5);

        // A save keeps the count
        FileHandle file = new FileHandle(dir.resolve("strained.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(world.pirateBase.strainedDays(), fresh.pirateBase.strainedDays(), 1e-6);
            assertFalse(fresh.pirateBase.standing());
        } finally {
            fresh.dispose();
        }

        warp(11 * Derelicts.DAY);
        assertTrue(world.pirateBase.standing(), "the raiders move back in");
        assertTrue(world.pirateBase.found(), "where everyone knows it is");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("moved back into")));
        assertEquals(0, world.pirateBase.extraRaiders(), "it grows again from now");
        world.standing.record().rank = ContractBoard.RAID_RANK;
        world.contracts.reset();
        assertTrue(board.offers(game.highport).stream().anyMatch(c -> c.type == Contract.Type.RAID), "and can be raided again");
    }

    private void warp(double seconds) {
        double until = world.engine.getSimSeconds() + seconds;
        for (int i = 0; i < 200_000 && world.engine.getSimSeconds() < until; i++) {
            if (world.timeWarp.getWarp() == 1) {
                double left = until - world.engine.getSimSeconds();
                for (int level : new int[]{100_000, 10_000, 1_000, 100, 10}) {
                    if (level * FRAME <= left && world.timeWarp.setWarp(level)) break;
                }
            }
            world.update(FRAME);
        }
        world.timeWarp.setWarp(1);
        run(PirateBase.CHECK_SECONDS + 1);
    }
}
