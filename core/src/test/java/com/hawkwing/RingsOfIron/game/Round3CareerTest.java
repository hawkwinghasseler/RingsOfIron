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
 * a transfer to the Moon, fight an ace, and buy the gunship at the Patrol's yard. A second test
 * checks a seed replays: the same seed gives the same start, boards and events, another seed others.
 *
 * <p>As in round 2's loop test, getting the ship near a fight is done by hand (the ship is put beside
 * the target), and standing the script can't earn in the time is granted and logged. Everything
 * else is the game's own systems. The career test writes what it saw (time, credits, standing, rank,
 * difficulty, prices) to {@code build/round3-career.txt} for the round 3 playtest notes.
 */
class Round3CareerTest {
    static final float FRAME = 1f / 60f;
    static final long SEED = 2026;

    GameWorld world;
    GameScenario game;
    final List<String> journal = new ArrayList<>();
    double granted;
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

        // 3. A lieutenant is offered an operation
        rankUpTo(1, "a second fight's worth");
        takeAnOperation();

        // 4. A transfer to the Moon, planned by navigation and flown by the burns it plans
        flyToTheMoon();

        // 5. An ace that got away from someone comes hunting
        fightAnAce();

        // 6. Weeks pass: wages, budget, events
        String home = world.universe.uuidOf(world.docking.dockedTo(game.player));
        weeksPass(2);

        // 7. A lieutenant commander buys the gunship at Anvil Yards
        rankUpTo(2, "a lieutenant commander's standing");
        buyTheGunship();

        snapshot("end");
        log("Standing granted by the script in all: %.0f.", granted);
        log("World events seen: %d.", world.events.log().size());
        for (WorldEvents.Event e : world.events.log()) log("  day %d %s: %s", e.day, e.kind, e.text);
        assertNotNull(home);
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
        towTo(highport, 800f);
        double value = Difficulty.shipValue(game.raider), before = credits();
        assertNull(world.shipyard.prizeRefusal(false));
        said = world.shipyard.sellPrize();
        log("Prize: %s (ship value %.0f, %d prisoners).", said, value, prisoners);
        log("  for comparison: the bounty paid %.0f; the prize paid %.0f.", bounty.reward, credits() - before);
        snapshot("prize sold");
        assertTrue(game.dockAtHighport());
        run(120);
        assertSame(highport, world.docking.dockedTo(game.player));

        // The fight left the bow leaking, and warp is refused while air vents: Highport's yard mends it
        logVenting("after the fight");
        double cost = world.yard.repairCost(game.player), paid = credits(), r0 = world.engine.getSimSeconds();
        log("Yard: %s (quoted %.0f cr).", world.yard.orderRepairs(game.player, highport), cost);
        assertTrue(runUntil(2 * 3_600f, () -> !world.yard.repairing(game.player)), "the yard finished");
        log("  repaired in %.1f h for %.0f cr; hull %.2f.", (world.engine.getSimSeconds() - r0) / 3_600, paid - credits(),
                hull(game.player));
        logVenting("after repairs");
        snapshot("repaired");
    }

    private void takeAnOperation() {
        Contract op = null;
        for (Contract c : world.contracts.offers(game.highport)) if (c.type == Contract.Type.OPERATION) op = c;
        assertNotNull(op, "a lieutenant is offered an operation at Highport");
        assertNull(world.contracts.accept(op.id));
        ContractBoard.Operation running = world.contracts.operation();
        assertNotNull(running);
        log("Operation %s (%s): steps %s, reward %.0f cr. First step: %s.", op.operationName, op.reason,
                String.join(", ", running.steps), running.reward, world.contracts.active().get(0).heading());
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
            if (e.getValue().isPirate() && e.getValue().wingOf == null && !e.getValue().ace) {
                id = e.getKey();
                h = e.getValue();
            }
        }
        assertNotNull(id, "a pirate to become an ace");
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, world.macroSim.hostiles().all().values(), Difficulty.of(world), true));
        log("Ace: %s in a %s, loadout %s, firepower %.0f.", h.displayName, h.shipId, h.loadout, h.firepower);
        for (Entity c : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (c.getComponent(GridPositionComponent.class).ship == ship) {
                assertTrue(c.getComponent(CrewMemberComponent.class).record().gunnery >= PirateAces.ACE_SKILL,
                        "the ace's crew keep their skill in their records");
            }
        }
        // Its bounty is first on the board
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
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(ship);
        double t0 = world.engine.getSimSeconds(), hull0 = hull(game.player);
        HostileComponent ace = h;
        boolean beaten = runUntil(400f, () -> {
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            return ace.partDropped || ShipDisablement.isDisabled(ship) || ShipDisablement.isDisabled(game.player)
                    || world.getCrash() != null;
        });
        log("Ace fight: %.0f s; beaten %s; our hull %.2f -> %.2f; ours disabled %s.", world.engine.getSimSeconds() - t0,
                ace.partDropped || ShipDisablement.isDisabled(ship), hull0, hull(game.player),
                ShipDisablement.isDisabled(game.player));
        assertTrue(beaten, "the fight ends one way or the other");
        run(60 * 10);
        if (bounty != null) log("  its bounty: %s.", bounty.status);
        snapshot("after the ace");
        if (ShipDisablement.isDisabled(game.player)) {
            // A stranded ship calls the tug (round 3's beacon) rather than sit in a soft lock
            log("  disabled: tow refusal %s.", world.beacon.refusal());
            log("  %s", world.beacon.call());
            assertTrue(runUntil(30f, () -> world.docking.dockedTo(game.player) != null), "towed home");
        } else {
            String home = game.highport;
            game.dockAt(home);
            run(120);
        }
        snapshot("home again");
    }

    private void weeksPass(int weeks) {
        double t0 = world.engine.getSimSeconds(), credits0 = credits();
        double budget = world.budget.pay();
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
        if (credits() < cost) {
            log("  short by %.0f cr: the script makes up the difference.", cost - credits());
            account().credits = cost + 1;
        }
        String said = world.shipyard.buy("patrol_gunship");
        log("  %s", said);
        assertEquals("gunship_heavy", game.player.getComponent(DefRefComponent.class).defId, said);
        run(120);
        snapshot("in the gunship");
    }

    // ------------------------------------------------------------------ replay

    @Test
    void theSameSeedGivesTheSameStartAndAnotherSeedAnotherStartAndOtherEvents() {
        String[] a = replay(7), again = replay(7), b = replay(8);
        assertEquals(a[0], again[0], "the same seed, the same start: stations, traffic, boards and hires");
        assertNotEquals(a[0], b[0], "another seed, another start");
        assertNotEquals(a[1], b[1], "and other events");
        assertFalse(a[1].isEmpty() || b[1].isEmpty(), "two weeks bring events");
    }

    /**
     * A seeded career's start (station places, traffic, Highport's board and hiring pool) and two weeks
     * of world events, as text. Only the start is compared run to run: what the rails traffic does
     * later (a pirate robbing a hauler, say) depends on encounter prediction running on its own
     * thread and on maps keyed by entity uuids, which are new each run.
     */
    private String[] replay(long seed) {
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
            warpFor(14 * 86_400);
            StringBuilder events = new StringBuilder();
            for (WorldEvents.Event e : world.events.log()) events.append(" | ").append(e.day).append(' ').append(e.kind).append(' ').append(e.text);
            return new String[]{out.toString(), events.toString()};
        } finally {
            world.dispose();
            world = null;
        }
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
        while (world.engine.getSimSeconds() < until && frames++ < 100_000) {
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

    /** Moves the latched pair, as one, to {@code meters} off {@code station}, at rest beside it. */
    private void towTo(Entity station, float meters) {
        Body s = world.docking.bodyOf(station);
        Vector2 at = PhysicsManager.getPosition(s, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(s, new Vector2());
        Vector2 own = PhysicsManager.getPosition(body(game.player), new Vector2());
        Vector2 prize = PhysicsManager.getPosition(body(game.raider), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x + meters, at.y, body(game.player).getAngle());
        PhysicsManager.setPosition(body(game.raider), at.x + meters + prize.x - own.x, at.y + prize.y - own.y,
                body(game.raider).getAngle());
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
        PhysicsManager.setVelocity(body(game.raider), v.x, v.y);
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

    /** Grants the standing still missing for {@code rank}, and logs how much that was. */
    private void rankUpTo(int rank, String standsFor) {
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
