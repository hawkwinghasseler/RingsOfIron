package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.interiorgrid.InteriorHazardWarpGuard;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round 3 played by script on a fresh seeded career, headless, across several in-game weeks: hire a
 * veteran, fight the raider for its bounty and tow it home as a prize, take an operation, plan and fly
 * a transfer to the Moon, fight an ace, and buy the gunship at the Patrol's yard. Two more tests
 * check a seed replays: the same seed gives the same start and four weeks of the same events
 * (world events, pirates' hunts and robberies, the fleet each week), another seed others, and a
 * career saved and loaded halfway goes on exactly as one that never was.
 *
 * <p>As in round 2's loop test, getting the ship near a fight is done by hand (the ship is put beside
 * the target). Since round 4's balance pass the career earns its ranks and the gunship's price in
 * play: three fights (two raiders, both towed home as prizes, and an ace) and three weeks' pay. Only a fight the player loses is made up for by the script, and the test then says
 * so instead of passing quietly. Everything else is the game's own systems. The career test writes what it saw (time, credits, standing, rank,
 * difficulty, prices) to {@code build/round3-career.txt} for the round 3 playtest notes.
 */
class Round3CareerTest {
    static final float FRAME = 1f / 60f;
    static final long SEED = 2026;

    GameWorld world;
    GameScenario game;
    final List<String> journal = new ArrayList<>();
    double granted;
    /** Credits the script put in, and credits that came from selling prizes. */
    double grantedCredits, prizeCredits;
    int fightsLost;
    final java.util.Set<String> warpBlocks = new java.util.HashSet<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (world != null) world.dispose();
        if (!journal.isEmpty()) {
            Path out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("round3-career.txt");
            Files.createDirectories(out.getParent());
            Files.write(out, journal);
        }
    }

    static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    private void start(long seed) {
        world = new GameWorld();
        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
    }

    // ------------------------------------------------------------------ the career

    @Test
    void aSeededCareerAcrossSeveralWeeks() {
        start(SEED);
        game.beginCareer();
        run(120);
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player), "a Patrol career starts docked at Highport");
        log("Seed %d, Patrol career. %d stations, %d freighters, %d pirates, %d crew aboard.", SEED,
                world.macroSim.stations().size(), world.macroSim.freighters().all().size(), pirates(), ownCrew().size());
        snapshot("start");
        logBoard(game.highport);

        // 1. Hire the most skilled hand Highport offers
        hireAVeteran();

        // 2. The raider outside Highport: its bounty, then tow it home as a prize
        fightTheRaiderAndTowItHome();

        // 3. The second raider's bounty
        fightASecondRaider();

        // 4. Two fights make a lieutenant, who is offered an operation
        requireRank(1, "a second fight's worth");
        takeAnOperation();

        // 5. An ace that got away from someone comes hunting outside Highport: its bounty is the
        // Patrol's, so its standing counts toward the next rank
        fightAnAce();

        // 6. A transfer to the Moon, planned by navigation and flown by the burns it plans
        flyToTheMoon();

        // 7. Weeks pass: wages, budget, events
        String home = world.universe.uuidOf(world.docking.dockedTo(game.player));
        weeksPass(3);

        // 8. A lieutenant commander buys the gunship at Anvil Yards
        requireRank(2, "a lieutenant commander's standing");
        buyTheGunship();

        snapshot("end");
        log("Standing granted by the script in all: %.0f. Credits granted: %.0f. Credits from prizes: %.0f. Fights lost: %d.",
                granted, grantedCredits, prizeCredits, fightsLost);
        log("World events seen: %d.", world.events.log().size());
        for (WorldEvents.Event e : world.events.log()) log("  day %d %s: %s", e.day, e.kind, e.text);
        assertNotNull(home);
        // Round 4's pacing: won in play, the career earns both ranks and the gunship
        if (fightsLost == 0) {
            assertEquals(0, granted, 1e-6, "standing earned in play");
            assertEquals(0, grantedCredits, 1e-6, "the gunship paid for in play");
        }
    }

    private void hireAVeteran() {
        List<CrewHiring.Candidate> pool = world.crewHiring.pool(game.highport);
        assertFalse(pool.isEmpty(), "Highport has hands to hire");
        CrewHiring.Candidate best = pool.get(0);
        for (CrewHiring.Candidate c : pool) {
            log("  for hire: %s, %s, %s, wage %.0f/week, signing %.0f", c.name, c.role, c.record.describeSkills(),
                    c.record.wage, c.signing);
            if (top(c) > top(best)) best = c;
        }
        double before = credits();
        int crew = ownCrew().size();
        assertNull(world.crewHiring.hire(best.key), "hired");
        assertEquals(crew + 1, ownCrew().size(), "the hire walks aboard");
        log("Hired %s (%s, %s) for %.0f up front.", best.name, best.role, best.record.describeSkills(), before - credits());
        snapshot("hired");
    }

    private static float top(CrewHiring.Candidate c) {
        return Math.max(c.record.gunnery, Math.max(c.record.engineering, c.record.marine));
    }

    private void fightTheRaiderAndTowItHome() {
        Entity highport = world.engine.getEntity(game.highport);
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) bounty = c;
        }
        assertNotNull(bounty, "the raider's bounty is on Highport's board");
        // First Play's raider flies with nobody aboard; two pirates crew it here, as in PrizeTowingTest,
        // so the prize comes home with prisoners if they live through the fight
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        List<int[]> floor = GameScenario.floorTiles(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            com.ringsofiron.aicrew.CrewFactory.spawn(world.crew, game.raider, "Pirate " + i,
                    com.ringsofiron.aicrew.CrewRole.CREWMAN, pirates, t[0], t[1]);
        }
        assertNull(world.contracts.accept(bounty.id));
        log("Took %s: %.0f cr, %+.0f standing.", bounty.heading(), bounty.reward, bounty.standing);

        assertTrue(world.docking.undock(game.player));
        run(60);
        flyTo(game.raider, 1_500f);
        run(2);
        game.provokeRaider();
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.raider);
        double t0 = world.engine.getSimSeconds();
        assertTrue(runUntil(300f, () -> {
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            return ShipDisablement.isDisabled(game.raider);
        }), "the raider is beaten");
        log("The raider is beaten in %.0f s; our hull %.2f.", world.engine.getSimSeconds() - t0, hull(game.player));

        // The fight left the bow leaking. Out here, far from a yard, the crew patch it from the ship's
        // stores, and warp is no longer refused for it (round 3 had to dock at Highport's yard first)
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        logVenting("after the fight");
        assertTrue(grid.isVenting(), "the fight left the ship venting");
        assertTrue(world.engine.getRailsWarpBlockedReason().startsWith(InteriorHazardWarpGuard.VENTING),
                world.engine.getRailsWarpBlockedReason());
        int kits = grid.patchKits;
        double p0 = world.engine.getSimSeconds();
        assertTrue(runUntil(600f, () -> !grid.isVenting() && LeakPatches.leaks(grid).size == 0), "the crew patched the leak");
        run(60);
        assertFalse(grid.isVenting());
        assertTrue(DamageControl.hasPatches(grid), "patched, not repaired");
        assertNull(new InteriorHazardWarpGuard(grid).railsWarpBlockedReason(), "the leak no longer holds warp back");
        assertTrue(noticed("Leak patched"), () -> "the player was told: " + world.notices.history().stream().map(n -> n.text).toList());
        log("Damage control: venting stopped in %.0f s with %d patch(es) from the stores (%d left); hull %.2f; "
                        + "warp is clear of hazards: %s.", world.engine.getSimSeconds() - p0, kits - grid.patchKits,
                grid.patchKits, hull(game.player), world.engine.getRailsWarpBlockedReason());

        run(60 * 20);
        assertEquals(Contract.Status.DONE, bounty.status, bounty.outcome);
        snapshot("bounty paid");

        // Latch on, board, take it, and bring the party home
        flyTo(game.raider, 40f);
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        assertTrue(runUntil(10f, () -> world.boarding.target(game.player) == game.raider && world.docking.isRigid(game.player)));
        run(120);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        assertTrue(runUntil(300f, () -> world.boarding.takenBy(game.raider, game.player)), "taken");
        world.boarding.recall(game.player);
        assertTrue(runUntil(180f, () -> partyAboard(game.raider) == 0), "the party came home");
        int prisoners = world.prisoners.aboard(game.raider).size();
        assertSame(game.raider, world.shipyard.prize(), "a prize in tow");

        // Tow it (as one body) to 800 m off Highport and sell it there
        towTo(game.raider, highport, 800f);
        double value = Difficulty.shipValue(game.raider), before = credits();
        assertNull(world.shipyard.prizeRefusal(false));
        said = world.shipyard.sellPrize();
        log("Prize: %s (ship value %.0f, %d prisoners).", said, value, prisoners);
        log("  for comparison: the bounty paid %.0f; the prize paid %.0f.", bounty.reward, credits() - before);
        prizeCredits += credits() - before;
        snapshot("prize sold");
        assertTrue(game.dockAtHighport());
        run(120);
        assertSame(highport, world.docking.dockedTo(game.player));

        // The patched bow still wants a yard: Highport's mends it, and restocks the patches
        repairAt(highport);
        snapshot("repaired");
    }

    /**
     * The other raider bounty on Highport's board, fought outside Highport for its bounty alone: one
     * prize a career is enough (round 4 keeps the gunship within reach without prize windfalls).
     * Its crew are logged, since a light raider's crew rarely live through the player's guns.
     */
    private void fightASecondRaider() {
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.contracts.locked(c) == null && c.targetUuid != null
                    && world.macroSim.hostiles().get(c.targetUuid) != null) bounty = c;
        }
        assertNotNull(bounty, "a second raider's bounty on Highport's board");
        assertNull(world.contracts.accept(bounty.id));
        log("Took %s: %.0f cr, %+.0f standing.", bounty.heading(), bounty.reward, bounty.standing);
        HostileComponent h = world.macroSim.hostiles().get(bounty.targetUuid);
        log("  it flies a %s, loadout %s.", h.shipId, h.loadout);

        assertTrue(world.docking.undock(game.player));
        run(60);
        Entity ship = bringIn(bounty.targetUuid, 3_000, 0);
        if (fight(ship, h, "The second raider")) {
            log("  beaten: %s, %d of its crew alive.", ShipDisablement.reason(ship), alive(ship));
            run(60 * 20);
            assertEquals(Contract.Status.DONE, bounty.status, bounty.outcome);
            snapshot("second bounty paid");
            boardAndSell(ship);
        }
        assertTrue(game.dockAtHighport());
        run(120);
        repairAt(world.engine.getEntity(game.highport));
        snapshot("second fight over");
    }

    /**
     * Boards the beaten {@code ship}, holds whoever lives aboard, and tows it to Highport: the prize
     * pays its share of the ship's value and the hand-over pay for its prisoners. A light raider's
     * crew rarely live through the player's guns, so the prisoners are counted, not required.
     */
    private void boardAndSell(Entity ship) {
        flyTo(ship, 40f);
        world.docking.dockInPlace(game.player, 0, ship, 0);
        assertTrue(runUntil(10f, () -> world.boarding.target(game.player) == ship && world.docking.isRigid(game.player)),
                "latched on");
        run(120);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        assertTrue(runUntil(300f, () -> world.boarding.takenBy(ship, game.player)), "taken");
        world.boarding.recall(game.player);
        assertTrue(runUntil(180f, () -> partyAboard(ship) == 0), "the party came home");
        int prisoners = world.prisoners.aboard(ship).size();
        towTo(ship, world.engine.getEntity(game.highport), 800f);
        double value = Shipyard.prizeValue(ship), before = credits();
        said = world.shipyard.sellPrize();
        double paid = credits() - before;
        log("Prize: %s (prize value %.0f; %d prisoners, handed over for %.0f).", said, value, prisoners, paid - value);
        assertEquals(value + prisoners * Prisoners.HANDOVER_PAY, paid, 1e-6, "prisoners are paid for with the prize");
        prizeCredits += paid;
    }

    /** Fights {@code ship} with every gun and missile until one side is beaten; true if it was. */
    private boolean fight(Entity ship, HostileComponent h, String what) {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(ship);
        int[] fired = new int[1], hits = new int[1];
        world.combatEvents.shots.add((sig, shot) -> { if (shot.shooter == ship) fired[0]++; });
        world.combatEvents.impacts.add((sig, hit) -> { if (hit.owner == ship && hit.target == game.player) hits[0]++; });
        double t0 = world.engine.getSimSeconds();
        float hull0 = hull(game.player);
        int crew0 = ownCrew().size();
        boolean over = runUntil(400f, () -> {
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            return (h != null && h.partDropped) || ShipDisablement.isDisabled(ship) || ShipDisablement.isDisabled(game.player)
                    || world.getCrash() != null;
        });
        boolean won = (h != null && h.partDropped) || ShipDisablement.isDisabled(ship);
        log("%s: %.0f s, %s; it fired %d rounds, %d hit us; our hull %.2f -> %.2f, crew %d -> %d.", what,
                world.engine.getSimSeconds() - t0, won ? "beaten" : over ? "we lost" : "neither side beaten", fired[0],
                hits[0], hull0, hull(game.player), crew0, ownCrew().size());
        assertTrue(over, "the fight ends one way or the other");
        if (!won) fightsLost++;
        return won;
    }

    /** Has {@code station}'s yard mend the ship, which takes off the crew's patches and restocks the kits. */
    private void repairAt(Entity station) {
        logVenting("at the yard");
        double cost = world.yard.repairCost(game.player), paid = credits(), r0 = world.engine.getSimSeconds();
        log("Yard: %s (quoted %.0f cr).", world.yard.orderRepairs(game.player, station), cost);
        assertTrue(runUntil(2 * 3_600f, () -> !world.yard.repairing(game.player)), "the yard finished");
        // A ship venting hard can shake loose of the latch for a moment, which calls the work off: order it again
        for (int again = 0; again < 3 && world.yard.repairCost(game.player) > 0.5; again++) {
            for (String n : world.yard.notices) log("  yard: %s", n);
            world.yard.notices.clear();
            if (world.docking.dockedTo(game.player) != station) assertTrue(game.dockAt(world.universe.uuidOf(station)));
            run(60);
            log("  ordered again: %s", world.yard.orderRepairs(game.player, station));
            assertTrue(runUntil(2 * 3_600f, () -> !world.yard.repairing(game.player)), "the yard finished");
        }
        log("  repaired in %.1f h for %.0f cr; hull %.2f; docked at %s.", (world.engine.getSimSeconds() - r0) / 3_600,
                paid - credits(), hull(game.player), world.docking.dockedTo(game.player) == null ? "nothing"
                        : PlayerDocking.name(world.docking.dockedTo(game.player)));
        for (String n : world.yard.notices) log("  yard: %s", n);
        world.yard.notices.clear();
        assertTrue(world.yard.repairCost(game.player) <= 0.5,
                String.format("left to repair: %.0f cr in %d items", world.yard.repairCost(game.player), world.yard.damage(game.player).size()));
        logVenting("after repairs");
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        assertFalse(DamageControl.hasPatches(grid), "the yard's repairs take the patches off");
        assertEquals(LeakPatches.DEFAULT_KITS, grid.patchKits, "and restock them");
    }

    private void takeAnOperation() {
        Contract op = null;
        for (Contract c : world.contracts.offers(game.highport)) if (c.type == Contract.Type.OPERATION) op = c;
        assertNotNull(op, "a lieutenant is offered an operation at Highport");
        // Not taken: the script flies to the Moon instead, and a dropped operation costs standing
        log("Offered operation %s (%s): reward %.0f cr, %+.0f standing.", op.operationName, op.reason, op.reward, op.standing);
    }

    private void flyToTheMoon() {
        logVenting("before the Moon trip");
        String target = game.oreMine != null ? game.oreMine : game.lunarMine;
        Entity station = world.engine.getEntity(target);
        assertTrue(world.docking.undock(game.player));
        run(120);
        // Clear of Highport, at the helm
        world.nav.target.select(target);
        double fuel0 = propellantKg(), t0 = world.engine.getSimSeconds();
        String said = world.nav.transfer.plan();
        PlayerTransfer.Proposal p = world.nav.transfer.proposal();
        log("Plan to %s: %s", world.nav.target.name(target), said);
        assertNotNull(p, said);
        log("  Δv needed %.0f m/s, Δv left %.0f m/s, return estimate %.0f m/s.", world.nav.deltaVNeeded(),
                world.nav.deltaVLeft(), world.nav.returnDeltaV());
        said = world.nav.transfer.accept(world.burns);
        log("  %s", said);
        // Warp out: burns fire when due, then the capture is proposed once in the Moon's sphere
        boolean captured = false;
        double give = 12 * 86_400;
        while (world.engine.getSimSeconds() - t0 < give && !captured && world.getCrash() == null) {
            warpFor(3_600);
            if (world.nav.transfer.proposal() != null) {
                log("  day %.1f: %s", days(t0), world.nav.transfer.describe(world.nav.transfer.proposal()));
                log("  %s", world.nav.transfer.accept(world.burns));
                captured = true;
            }
        }
        for (String n : world.nav.transfer.notices) log("  navigation: %s", n);
        world.nav.transfer.notices.clear();
        assertTrue(captured, "the capture was proposed in the Moon's sphere");
        // Fly the capture and the rest of the trip, then measure how close it got
        double closest = Double.MAX_VALUE;
        double until = world.engine.getSimSeconds() + 6 * 86_400;
        while (world.engine.getSimSeconds() < until && world.getCrash() == null) {
            warpFor(1_800);
            closest = Math.min(closest, distance(game.player, station));
            if (world.burns.nodes() == null || world.burns.nodes().nodes.size == 0) {
                if (closest < 50_000) break;
            }
        }
        log("Moon trip: %.1f days, %.0f kg of propellant (%.0f kg left), closest pass to %s %.1f km. Crash: %s.",
                days(t0), fuel0 - propellantKg(), propellantKg(), world.nav.target.name(target), closest / 1000,
                world.getCrash());
        assertNull(world.getCrash(), "the trip doesn't end in a crash");
        // Dock there by hand from close in
        if (closest < 50_000 && game.dockAt(target)) {
            run(120);
            log("  docked at %s.", world.nav.target.name(target));
        } else {
            log("  not close enough to dock by hand; flown back to Highport's dock instead.");
            game.dockAtHighport();
            run(120);
        }
        snapshot("after the Moon");
    }

    private void fightAnAce() {
        String id = null;
        HostileComponent h = null;
        for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
            Entity candidate = world.engine.getEntity(e.getKey());
            if (e.getValue().isPirate() && e.getValue().wingOf == null && !e.getValue().ace
                    && HostileTraffic.RAIDER.equals(e.getValue().shipId) && candidate != null
                    && !ShipDisablement.isDisabled(candidate) && alive(candidate) > 0) {
                id = e.getKey();
                h = e.getValue();
            }
        }
        assertNotNull(id, "a raider to become an ace");
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, world.macroSim.hostiles().all().values(), Difficulty.of(world), true));
        log("Ace: %s in a %s, loadout %s, firepower %.0f.", h.displayName, h.shipId, h.loadout, h.firepower);
        for (Entity c : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (c.getComponent(GridPositionComponent.class).ship == ship) {
                assertTrue(c.getComponent(CrewMemberComponent.class).record().gunnery >= PirateAces.ACE_SKILL,
                        "the ace's crew keep their skill in their records");
            }
        }
        // Its bounty goes up on the next day's boards
        warpFor(86_400);
        Entity at = world.docking.dockedTo(game.player);
        String stationUuid = world.universe.uuidOf(at);
        Contract bounty = null;
        for (Contract c : world.contracts.offers(stationUuid)) if (c.type == Contract.Type.BOUNTY && id.equals(c.targetUuid)) bounty = c;
        if (bounty != null && world.contracts.locked(bounty) == null && world.contracts.accept(bounty.id) == null) {
            log("  took its bounty: %s, %.0f cr, %+.0f standing.", bounty.heading(), bounty.reward, bounty.standing);
        } else {
            log("  no bounty on it that we may take at %s.", PlayerDocking.name(at));
            bounty = null;
        }
        world.docking.undock(game.player);
        run(60);
        h.targetUuid = HostileComponent.PLAYER;
        bringIn(id, 3_000, 0);
        fight(ship, h, "Ace fight");
        run(60 * 10);
        if (bounty != null) log("  its bounty: %s.", bounty.status);
        snapshot("after the ace");
        if (ShipDisablement.isDisabled(game.player)) {
            // A stranded ship calls the tug (round 3's beacon) rather than sit in a soft lock
            log("  disabled: tow refusal %s.", world.beacon.refusal());
            log("  %s", world.beacon.call());
            assertTrue(runUntil(30f, () -> world.docking.dockedTo(game.player) != null), "towed home");
        } else {
            assertTrue(game.dockAtHighport());
            run(120);
            // The ace's rounds holed the hull: Highport's yard mends it before the crew run out of air
            repairAt(world.engine.getEntity(game.highport));
        }
        snapshot("home again");
    }

    private void weeksPass(int weeks) {
        double t0 = world.engine.getSimSeconds(), credits0 = credits();
        world.budget.pay();
        double budget = world.standing.rank().budget;
        float morale0 = meanMorale();
        warpFor(weeks * ServiceBudget.WEEK);
        log("%d weeks docked: credits %+.0f (budget %.0f a week before wages), crew morale %.2f -> %.2f.", weeks,
                credits() - credits0, budget, morale0, meanMorale());
        log("  %.1f days passed.", days(t0));
        snapshot("weeks later");
    }

    private void buyTheGunship() {
        assertTrue(world.docking.undock(game.player));
        run(60);
        assertTrue(game.dockAt(game.shipyard));
        run(120);
        Entity yard = world.engine.getEntity(game.shipyard);
        assertSame(yard, world.docking.dockedTo(game.player), "at Anvil Yards");
        Shipyard.Listing gunship = world.shipyard.listing("patrol_gunship");
        double cost = world.shipyard.cost(gunship, game.player);
        log("Gunship at Anvil Yards: price %.0f, trade-in %.0f, cost %.0f; we have %.0f. Refusal: %s.", gunship.price,
                Shipyard.tradeIn(game.player), cost, credits(), world.shipyard.refusal(gunship));
        log("  without the prizes (%.0f cr) we'd have %.0f.", prizeCredits, credits() - prizeCredits);
        if (credits() < cost) {
            log("  short by %.0f cr: the script makes up the difference.", cost - credits());
            grantedCredits += cost + 1 - credits();
            account().credits = cost + 1;
        }
        String said = world.shipyard.buy("patrol_gunship");
        log("  %s", said);
        assertEquals("gunship_heavy", game.player.getComponent(DefRefComponent.class).defId, said);
        run(120);
        snapshot("in the gunship");
    }

    // ------------------------------------------------------------------ replay

    /** Weeks of world each replay plays through. */
    static final int REPLAY_WEEKS = 4;

    @Test
    void theSameSeedReplaysItsEventLogAndAnotherSeedDoesNot() {
        Replay a = replay(7, REPLAY_WEEKS, -1, null, false), again = replay(7, REPLAY_WEEKS, -1, null, false),
                b = replay(8, REPLAY_WEEKS, -1, null, false);
        assertEquals(a.start, again.start, "the same seed, the same start: stations, traffic, boards and hires");
        assertLinesEqual(a.events, again.events, "the same seed, the same events, day by day");
        assertNotEquals(a.start, b.start, "another seed, another start");
        assertNotEquals(a.events, b.events, "and other events");
        assertTrue(a.events.size() > REPLAY_WEEKS && b.events.size() > REPLAY_WEEKS,
                "weeks bring events: " + a.events.size() + " and " + b.events.size());
        assertTrue(a.events.stream().anyMatch(l -> l.contains(" robs ")), "pirates rob freighters in the replay");
    }

    @Test
    void aCareerSavedAndLoadedMidwayGoesOnAsIfNeverSaved(@org.junit.jupiter.api.io.TempDir Path dir) {
        Replay straight = replay(7, REPLAY_WEEKS, REPLAY_WEEKS / 2, dir, false);
        Replay reloaded = replay(7, REPLAY_WEEKS, REPLAY_WEEKS / 2, dir, true);
        assertLinesEqual(straight.events, reloaded.events, "loaded halfway, the career goes on the same");
    }

    /** What a replay saw: its start, and every event (world events, hostile traffic, the fleet each week) in order. */
    static final class Replay {
        String start;
        final List<String> events = new ArrayList<>();
    }

    /**
     * A seeded career's start (station places, traffic, Highport's board and hiring pool) and
     * {@code weeks} of world events, hostile traffic and the fleet's state, as text, docked at
     * Highport under time warp. With {@code saveAfterWeeks} at 0 or more, the game is saved then into
     * {@code dir}, and with {@code reload} loaded into a fresh world, which plays the rest.
     */
    private Replay replay(long seed, int weeks, int saveAfterWeeks, Path dir, boolean reload) {
        Replay r = new Replay();
        start(seed);
        try {
            game.beginCareer();
            run(120);
            StringBuilder out = new StringBuilder();
            StateVector s = new StateVector();
            for (var n : world.macroSim.stations()) {
                world.macroSim.rails().stateAt(n.stationUuid, 0, s);
                out.append(n.displayName).append(String.format(Locale.ROOT, " %.3f; ", Math.atan2(s.y, s.x)));
            }
            out.append("\nfreighters ").append(world.macroSim.freighters().all().size());
            out.append("\nboard");
            for (Contract c : world.contracts.offers(game.highport)) out.append(" | ").append(c.type).append(' ').append(c.reward);
            out.append("\nhires");
            for (CrewHiring.Candidate c : world.crewHiring.pool(game.highport)) out.append(" | ").append(c.name);
            r.start = out.toString();
            listen(r);
            double[] seen = {-1};
            for (int week = 0; week < weeks; week++) {
                if (week == saveAfterWeeks) {
                    com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(dir.resolve("replay.json").toFile());
                    assertNull(game.quickSave(file));
                    if (reload) {
                        world.dispose();
                        world = new GameWorld();
                        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"));
                        game.quickLoad(file);
                        assertTrue(world.engine.getSerializationService().getLastLoadWarnings().isEmpty(),
                                String.valueOf(world.engine.getSerializationService().getLastLoadWarnings()));
                        listen(r);
                    }
                    // A loaded ship is welded back to Highport in its first frames; both runs wait as long
                    run(5);
                    assertNull(world.engine.getRailsWarpBlockedReason());
                }
                for (int day = 0; day < 7; day++) {
                    warpFor(WorldEvents.DAY);
                    for (WorldEvents.Event e : world.events.log()) {
                        if (e.at <= seen[0]) continue;
                        seen[0] = e.at;
                        r.events.add(String.format(Locale.ROOT, "day %d %.0f %s %s", e.day, e.at, e.kind, e.text));
                    }
                }
                r.events.add("week " + (week + 1) + " fleet:" + fleet());
            }
            return r;
        } finally {
            world.dispose();
            world = null;
        }
    }

    /** Writes hostile traffic's doings into {@code r} as they happen, by name. */
    private void listen(Replay r) {
        var hostiles = world.macroSim.hostiles();
        hostiles.addListener(new com.ringsofiron.macrosim.hostile.HostileTrafficController.Listener() {
            @Override
            public void intercepting(String hostile, String target, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s goes after %s", t, name(hostile), name(target)));
            }

            @Override
            public void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s robs %s of %.1f %s", t, name(hostile), name(freighter),
                        quantity, commodity));
            }

            @Override
            public void drivenOff(String hostile, String patrol, double t) {
                r.events.add(String.format(Locale.ROOT, "%.0f %s is driven off by %s", t, name(hostile), name(patrol)));
            }
        });
    }

    /** A ship or station by its name, so lines read the same whatever its uuid. */
    private String name(String uuid) {
        if (HostileComponent.PLAYER.equals(uuid)) return "the player";
        HostileComponent h = world.macroSim.hostiles().get(uuid);
        if (h != null && h.displayName != null) return h.displayName;
        var f = world.macroSim.freighters().get(uuid);
        if (f != null && f.displayName != null) return f.displayName;
        var n = world.macroSim.station(uuid);
        return n != null ? n.displayName : uuid;
    }

    /** Every freighter's name, state, trips, credits and cargo, in name order. */
    private String fleet() {
        java.util.TreeMap<String, String> rows = new java.util.TreeMap<>();
        for (var f : world.macroSim.freighters().all().values()) {
            rows.put(f.displayName, String.format(Locale.ROOT, "%s %d %.0f %s %.1f", f.state, f.tripsCompleted,
                    f.credits, f.cargoCommodity, f.cargoQuantity));
        }
        StringBuilder out = new StringBuilder();
        rows.forEach((k, v) -> out.append(" | ").append(k).append(' ').append(v));
        return out.toString();
    }

    /** Equal line by line, failing on the first line that differs with the lines around it. */
    private static void assertLinesEqual(List<String> expected, List<String> actual, String message) {
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                fail(message + ": line " + i + " differs\n  expected: " + expected.get(i) + "\n  actual:   " + actual.get(i)
                        + "\n  before: " + expected.subList(Math.max(0, i - 3), i));
            }
        }
        assertEquals(expected.size(), actual.size(), message + ": " + expected + "\nvs\n" + actual);
    }

    // ------------------------------------------------------------------ helpers

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private boolean runUntil(float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    /** Lets {@code seconds} of game time pass in the fastest warp the game allows, raising it again after each drop. */
    private void warpFor(double seconds) {
        double until = world.engine.getSimSeconds() + seconds;
        int frames = 0;
        double last = world.engine.getSimSeconds();
        while (world.engine.getSimSeconds() < until && frames++ < 100_000) {
            // Time stood still for ten seconds of frames: something holds the game, so fail rather than spin
            if (frames % 600 == 0) {
                assertNotEquals(last, world.engine.getSimSeconds(), "game time stopped; warp: "
                        + world.engine.getRailsWarpBlockedReason());
                last = world.engine.getSimSeconds();
            }
            if (world.timeWarp.getWarp() == 1 && world.getCrash() == null) {
                double left = until - world.engine.getSimSeconds();
                String blocked = world.engine.getRailsWarpBlockedReason();
                if (blocked != null && warpBlocks.add(blocked)) log("  (rails warp refused: %s)", blocked);
                for (int level : new int[]{100_000, 10_000, 1_000, 100, 10}) {
                    if (level * FRAME <= left * 2 && world.timeWarp.setWarp(level)) break;
                }
            }
            world.update(FRAME);
            if (world.getCrash() != null) break;
        }
        if (world.engine.getSimSeconds() < until) {
            log("  (warp gave up %.0f s short; crash: %s)", until - world.engine.getSimSeconds(), world.getCrash());
        }
        world.timeWarp.setWarp(1);
        run(2);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void flyTo(Entity target, float meters) {
        Vector2 at = PhysicsManager.getPosition(body(target), new Vector2());
        Vector2 v = PhysicsManager.getVelocity(body(target), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, -90f * MathUtils.degRad);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    /** Moves the player and the prize latched to it, as one, to {@code meters} off {@code station}, at rest beside it. */
    private void towTo(Entity towed, Entity station, float meters) {
        Body s = world.docking.bodyOf(station);
        Vector2 at = PhysicsManager.getPosition(s, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(s, new Vector2());
        Vector2 own = PhysicsManager.getPosition(body(game.player), new Vector2());
        Vector2 prize = PhysicsManager.getPosition(body(towed), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x + meters, at.y, body(game.player).getAngle());
        PhysicsManager.setPosition(body(towed), at.x + meters + prize.x - own.x, at.y + prize.y - own.y,
                body(towed).getAngle());
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
        PhysicsManager.setVelocity(body(towed), v.x, v.y);
        run(2);
    }

    /** Puts a hostile on the rails {@code dx, dy} meters from the bubble origin and lets it come in. */
    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(),
                s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private double distance(Entity a, Entity b) {
        double now = world.universe.now();
        Entity frame = world.universe.parentOf(b);
        var sa = new com.ringsofiron.orbital.math.StateVector();
        var sb = new com.ringsofiron.orbital.math.StateVector();
        inFrame(a, frame, now, sa);
        inFrame(b, frame, now, sb);
        return Math.hypot(sa.x - sb.x, sa.y - sb.y);
    }

    /** A ship in the physics bubble has no orbit of its own: it rides the bubble's anchor (meters off it). */
    private void inFrame(Entity e, Entity frame, double now, com.ringsofiron.orbital.math.StateVector out) {
        if (world.universe.orbit(e) != null) {
            world.universe.stateInFrame(e, frame, now, out);
            return;
        }
        var gateway = world.macroSim.gateway();
        Entity parent = world.universe.entity(gateway.anchorParentUuid());
        var p = new com.ringsofiron.orbital.math.StateVector();
        world.universe.stateInFrame(parent, frame, now, p);
        StateVector a = gateway.anchorStateAt(now, new StateVector());
        out.set(a.x + p.x, a.y + p.y, a.vx + p.vx, a.vy + p.vy);
    }

    private int alive(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship && e.getComponent(CrewMemberComponent.class).isAlive()) n++;
        }
        return n;
    }

    private int partyAboard(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.getComponent(GridPositionComponent.class).ship == ship && c.home == game.player && c.isAlive()) n++;
        }
        return n;
    }

    private List<Entity> ownCrew() {
        return world.crewCareer.ownCrew(game.player);
    }

    private float meanMorale() {
        List<Entity> crew = ownCrew();
        float sum = 0f;
        for (Entity e : crew) sum += e.getComponent(CrewMemberComponent.class).record().morale;
        return crew.isEmpty() ? Float.NaN : sum / crew.size();
    }

    private int pirates() {
        int n = 0;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (h.isPirate()) n++;
        return n;
    }

    private TraderComponent account() {
        return game.player.getComponent(TraderComponent.class);
    }

    private double credits() {
        return account().credits;
    }

    private float hull(Entity ship) {
        return ShipDisablement.hullIntegrity(ship.getComponent(ShipGridComponent.class));
    }

    private double propellantKg() {
        ShipSubsystemRegistryComponent r = game.player.getComponent(ShipSubsystemRegistryComponent.class);
        return r == null ? 0 : PropellantFeed.available(r);
    }

    private double days(double since) {
        return (world.engine.getSimSeconds() - since) / 86_400;
    }

    /**
     * Checks the career earned {@code rank} in play; if a lost fight left it short, grants the standing
     * still missing and logs how much that was.
     */
    private void requireRank(int rank, String standsFor) {
        float need = world.standing.ranks().get(rank).standing - world.standing.standing(world.standing.sponsor());
        if (need > 0) {
            world.standing.reward(world.standing.sponsor(), need + 0.5f, "script");
            granted += need + 0.5f;
            log("Granted %.0f standing (%s) for rank %s.", need + 0.5f, standsFor, world.standing.describeRank());
        } else {
            log("Rank %s earned in play.", world.standing.describeRank());
        }
        assertTrue(world.standing.record().rank >= rank);
    }

    /** True if the notice log holds a notice starting with {@code text}. */
    private boolean noticed(String text) {
        for (NoticeLog.Notice n : world.notices.history()) if (n.text.startsWith(text)) return true;
        return false;
    }

    /** Where air leaves the player's ship for space, if anywhere: rails warp waits on it. */
    private void logVenting(String when) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        for (int i = 0; i < grid.airflow.size; i++) {
            var e = grid.airflow.get(i);
            if (e.other == null && e.flow > ShipGridComponent.VENTING_HAZARD_FLOW) {
                var t = grid.getTile(e.x, e.y);
                log("  venting %s at (%d, %d) %s: %.3f mol/s, tile %s", when, e.x, e.y, e.side, e.flow, t);
            }
        }
    }

    private void snapshot(String when) {
        log("[%s] day %.2f, credits %.0f, standing %.1f, rank %s, difficulty %.2f, ship value %.0f, hull %.2f, crew %d",
                when, world.engine.getSimSeconds() / 86_400, credits(), world.standing.standing(world.standing.sponsor()),
                world.standing.describeRank(), Difficulty.of(world), Difficulty.shipValue(game.player), hull(game.player),
                ownCrew().size());
    }

    private void logBoard(String station) {
        for (Contract c : world.contracts.offers(station)) {
            log("  board: %s, %.0f cr, %+.0f standing%s", c.heading(), c.reward, c.standing,
                    world.contracts.locked(c) == null ? "" : " (locked: " + world.contracts.locked(c) + ")");
        }
    }

    private void log(String format, Object... args) {
        String line = String.format(Locale.ROOT, format, args);
        journal.add(line);
        System.out.println(line);
        try {
            Path out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("round3-career.log");
            Files.createDirectories(out.getParent());
            Files.writeString(out, line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the journal is written whole at the end too
        }
    }
}
