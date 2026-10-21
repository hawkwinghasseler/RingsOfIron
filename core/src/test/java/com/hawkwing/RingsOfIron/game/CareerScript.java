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
import com.ringsofiron.aicrew.Wound;
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
 * A career played by script on a fresh seeded game, headless: what {@link Round3CareerTest} and
 * {@link Round5CareerTest} share. {@link #playToTheGunship} plays round 3's career (hire a veteran,
 * fight two raiders and an ace, tow a prize home, fly to the Moon, wait for pay and buy the gunship);
 * the helpers fly, fight, warp and log for the later careers.
 *
 * <p>As in round 2's loop test, getting the ship near a fight is done by hand (the ship is put beside
 * the target). Only a fight the player loses is made up for by the script, and the test then says so
 * instead of passing quietly. Everything else is the game's own systems. Each career writes what it
 * saw (time, credits, standing, rank, difficulty, prices) to {@code build/<journal>.txt}.
 */
abstract class CareerScript {
    static final float FRAME = 1f / 60f;
    /** 2026 unless {@code -PcareerSeed=n} is given, to play the career on other seeds. */
    static final long SEED = Long.getLong("career.seed", 2026);
    /** The seed this script plays: {@link #SEED}, unless a test plays other seeds side by side. */
    long seed = SEED;

    GameWorld world;
    GameScenario game;
    final List<String> journal = new ArrayList<>();
    double granted;
    /** Credits the script put in, and credits that came from selling prizes. */
    double grantedCredits, prizeCredits;
    int fightsLost;
    /** Range, m, past which a hostile breaking off has got away: since round 5 a wounded crew can still run. */
    static final double GOT_AWAY = 40_000;
    /** A fight looks at the range this often, seconds, and chases a pirate farther off than {@link #CHASE_RANGE}. */
    static final double CHASE_EVERY = 20;
    static final double CHASE_RANGE = 2_500;
    /** The fight whose leak the crew patched from the stores, once one has. */
    String patchedAfter;
    final java.util.Set<String> warpBlocks = new java.util.HashSet<>();
    /** Times the yard work is ordered again after the ship came loose of the latch. */
    int yardTries = 3;

    /**
     * Round 10: {@code -PcareerFightTrace=<text>} traces each fight whose journal name holds the text
     * (say "Iron Dagny"), second by second, to {@code build/fight-trace-<seed>.txt} ({@link FightTrace}).
     */
    static final String FIGHT_TRACE = System.getProperty("career.fightTrace");

    /** Round 10: fastest a prize may spin, rad/s, for the script to latch on and board it. */
    static final float PRIZE_SPIN_LIMIT = 0.5f;

    /** The journal's file name in {@code build/}, without the extension. */
    abstract String journalName();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (world != null) world.dispose();
        if (!journal.isEmpty()) {
            Path out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve(journalName() + ".txt");
            Files.createDirectories(out.getParent());
            Files.write(out, journal);
        }
    }

    static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    void start(long seed) {
        world = new GameWorld();
        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
        world.carriedHome.add((sig, t) -> {
            log("  (the player is down: the crew bring them to %s, %.1f d later)", t.stationName, t.skipped / 86_400);
            // Round 7: down, towed home venting and dry, then down again at the berth, for ever
            double now = world.engine.getSimSeconds();
            while (!carried.isEmpty() && carried.peekFirst() < now - 86_400) carried.removeFirst();
            carried.addLast(now);
            assertTrue(carried.size() < MOST_CARRIED_A_DAY, "the player keeps going down: carried home "
                    + carried.size() + " times in a day, last to " + t.stationName);
        });
        world.pirateBoarding.robbed.add((sig, t) -> log("  (boarded by %s: they took %s)", t.pirateName, t.describe()));
        // The player's crew dying, as CrewCareer tells it (round 8: two died at the berth unlogged)
        world.notices.posted.add((sig, n) -> {
            if (n.text.endsWith(" has died of wounds") || n.text.endsWith(" is dead")) {
                log("  (day %.2f: %s)", world.engine.getSimSeconds() / 86_400, n.text);
            }
        });
    }

    /** When the crew carried the player home, in the last day; a day with this many fails the career. */
    final java.util.ArrayDeque<Double> carried = new java.util.ArrayDeque<>();
    static final int MOST_CARRIED_A_DAY = 3;

    // ------------------------------------------------------------------ round 3's career

    /**
     * Round 3's career from the start to the gunship, by the round 4 balance: three fights, a
     * Moon trip and three weeks' pay. Returns the station the ship was docked at before the weeks passed.
     */
    String playToTheGunship() {
        start(seed);
        game.beginCareer();
        run(120);
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player), "a Patrol career starts docked at Highport");
        log("Seed %d, Patrol career. %d stations, %d freighters, %d pirates, %d crew aboard.", seed,
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
        return home;
    }

    void hireAVeteran() {
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

    static float top(CrewHiring.Candidate c) {
        return Math.max(c.record.gunnery, Math.max(c.record.engineering, c.record.marine));
    }

    /** The player's own crew fit to work: conscious, and no worse than lightly wounded. */
    int fitCrew() {
        int n = 0;
        for (Entity e : ownCrew()) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (c.isConscious() && c.wound.ordinal() <= Wound.LIGHT.ordinal()) n++;
        }
        return n;
    }

    /**
     * After a fight that left hands dead or down, hires at the docked station, as a player would
     * before the next job: the dead are replaced up to {@code want} hands, and while anyone aboard is
     * seriously wounded or worse with nobody fit to treat them, a medic comes aboard. The most
     * skilled hand otherwise; berths allowing.
     */
    void hireReplacements(int want) {
        Entity at = world.docking.dockedTo(game.player);
        if (at == null) return;
        String uuid = world.universe.uuidOf(at);
        while (true) {
            boolean bleeding = false;
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                if (c.isAlive() && c.wound.ordinal() >= Wound.SERIOUS.ordinal()
                        && e.getComponent(GridPositionComponent.class).ship == game.player) bleeding = true;
            }
            boolean medic = bleeding && fitCrew() == 0;
            if (ownCrew().size() >= want && !medic) break;
            CrewHiring.Candidate best = null;
            for (CrewHiring.Candidate c : world.crewHiring.pool(uuid)) {
                if (best == null || (medic ? c.record.medicine > best.record.medicine : top(c) > top(best))) best = c;
            }
            if (best == null) break;
            String why = world.crewHiring.hire(best.key);
            if (why != null) { log("  could not hire %s: %s", best.name, why); break; }
            log("  hired %s (%s, %s) to make up the hands lost", best.name, best.role, best.record.describeSkills());
        }
    }

    void fightTheRaiderAndTowItHome() {
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
        ShipGridComponent ours = game.player.getComponent(ShipGridComponent.class), theirs = game.raider.getComponent(ShipGridComponent.class);
        log("  shorts and tank bursts: ours %d and %d, theirs %d and %d.", ours.shortCount, ours.burstCount,
                theirs.shortCount, theirs.burstCount);

        patchIfVenting("the raider");

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
    void fightASecondRaider() {
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
        boolean won = fight(ship, h, "The second raider");
        patchIfVenting("the second raider");
        if (won) {
            log("  beaten: %s, %d of its crew alive.", ShipDisablement.reason(ship), alive(ship));
            run(60 * 20);
            assertEquals(Contract.Status.DONE, bounty.status, bounty.outcome);
            snapshot("second bounty paid");
            // Beaten with its guns silenced but not yet disabled, there is no boarding it
            if (ShipDisablement.isDisabled(ship)) boardAndSell(ship);
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
    void boardAndSell(Entity ship) {
        if (ship.getComponent(PhysicsBodyComponent.class) == null) {
            // A long fight leaves it drifting: twenty minutes on, it can be out of the bubble
            log("  the prize drifted out of reach before it could be boarded.");
            return;
        }
        // Round 10, seed 19: the beaten raider tumbled at 10.8 rad/s, and latching on tore both docking
        // collars off the frame it was made. A player can't latch onto a ship spinning like that, and
        // leaves the prize where it is
        float spin = Math.abs(body(ship).getAngularVelocity());
        if (spin > PRIZE_SPIN_LIMIT) {
            log("  the prize tumbles at %.1f rad/s: too fast to latch onto, so it isn't boarded.", spin);
            return;
        }
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

    /** Each ace fight, in order: the ace's name, how it ended and how long it took. */
    final List<String> aceFights = new ArrayList<>();

    /** The aces {@link #fight} has beaten, by uuid: no contract may name one again (round 7). */
    final java.util.Set<String> acesBeaten = new java.util.HashSet<>();

    /** Fights {@code ship} with every gun and missile until one side is beaten; true if it was. */
    boolean fight(Entity ship, HostileComponent h, String what) {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(ship);
        int[] fired = new int[1], hits = new int[1], landed = new int[1];
        world.combatEvents.shots.add((sig, shot) -> { if (shot.shooter == ship) fired[0]++; });
        world.combatEvents.impacts.add((sig, hit) -> { if (hit.owner == ship && hit.target == game.player) hits[0]++; });
        world.combatEvents.impacts.add((sig, hit) -> { if (hit.owner == game.player && hit.target == ship) landed[0]++; });
        FightTrace trace = FIGHT_TRACE != null && what.contains(FIGHT_TRACE) ? new FightTrace(this, game.player, ship, what) : null;
        double t0 = world.engine.getSimSeconds();
        float hull0 = hull(game.player);
        int crew0 = ownCrew().size();
        if (h != null) log("  as it opens: %s.", openingState(ship, h));
        ShipGridComponent ours = game.player.getComponent(ShipGridComponent.class), theirs = ship.getComponent(ShipGridComponent.class);
        int[] incidents0 = {ours.shortCount, ours.burstCount, theirs == null ? 0 : theirs.shortCount, theirs == null ? 0 : theirs.burstCount};
        // Shot down at the helm, the player is carried home by the crew: the fight is lost
        boolean[] carried = new boolean[1];
        com.badlogic.ashley.signals.Listener<Rescue.Tow> home = (sig, t) -> carried[0] = true;
        world.carriedHome.add(home);
        // A pirate holding off past the guns' reach is chased in: the script's ship never flies
        // itself (round 7: an ace held off 3 to 6 km for the whole fight). One that broke off runs.
        int[] closed = new int[1];
        double[] nextLook = {t0 + CHASE_EVERY};
        boolean over = runUntil(400f, () -> {
            if (carried[0]) return true;
            if (world.engine.getSimSeconds() >= nextLook[0]) {
                nextLook[0] += CHASE_EVERY;
                if (inBubble(ship) && range(game.player, ship) > CHASE_RANGE && !FirstPlay.beaten(world, ship)
                        && (h == null || !h.brokeOff)) {
                    flyTo(ship, 1_000f);
                    closed[0]++;
                }
            }
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            // Dark for an ion stun's few seconds isn't a lost fight: the guns come back. Judged afresh,
            // since the disabled mark lingers until the next judgement after the stun ends. Won as
            // contracts count it (round 7: a beaten ace's dropped part ended a fight with a fit ship).
            // A broken hull is lost, stunned or not (round 8, as FirstPlay.beaten)
            return FirstPlay.beaten(world, ship)
                    || ShipDisablement.isDisabled(game.player) && (ShipDisablement.reason(game.player) != DisabledComponent.Reason.NO_POWER
                            || !com.ringsofiron.combatsys.damage.IonStun.anyStunned(game.player))
                    && world.disablement.judge(game.player) != null
                    || world.getCrash() != null || range(game.player, ship) > GOT_AWAY;
        });
        world.carriedHome.remove(home);
        if (trace != null) trace.closeAfter(600);
        // One that broke off and was never chased, still running when time is up, got away too
        boolean ran = !over && h != null && h.brokeOff && !FirstPlay.beaten(world, ship);
        boolean won = !carried[0] && FirstPlay.beaten(world, ship);
        if (won && h != null && h.ace) acesBeaten.add(ship.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id);
        if (!won && ShipDisablement.isDisabled(game.player)) log("  we are disabled: %s.", ShipDisablement.reason(game.player));
        boolean away = !won && !carried[0] && (ran || range(game.player, ship) > GOT_AWAY);
        String result = won ? "beaten" : carried[0] ? "we lost (carried home)" : away ? "it got away"
                : over ? "we lost" : "neither side beaten";
        double seconds = world.engine.getSimSeconds() - t0;
        log("%s: %.0f s, %s; it fired %d rounds, %d hit us; our hull %.2f -> %.2f, crew %d -> %d.", what,
                seconds, result, fired[0], hits[0], hull0, hull(game.player), crew0, ownCrew().size());
        // Round 9: each ace fight's result on a line of its own, by the ace's name, for the sweep
        if (h != null && h.ace) {
            aceFights.add(String.format(Locale.ROOT, "%s %s %.0f s", h.displayName, result, seconds));
            log("  ace fight with %s: %s in %.0f s.", h.displayName, result, seconds);
        }
        log("  shorts and tank bursts: ours %d and %d, theirs %d and %d.", ours.shortCount - incidents0[0],
                ours.burstCount - incidents0[1], theirs == null ? 0 : theirs.shortCount - incidents0[2],
                theirs == null ? 0 : theirs.burstCount - incidents0[3]);
        if (closed[0] > 0) log("  chased it in %d time(s) from past %.0f m.", closed[0], CHASE_RANGE);
        if (!over && !ran) log("  unfinished: %d of our hits landed, range %.0f m, its hull %.2f.", landed[0], range(game.player, ship), hull(ship));
        assertTrue(over || ran, "the fight ends one way or the other");
        if (!won) fightsLost++;
        return won;
    }

    /**
     * A hostile's state as a fight with it opens (round 8): its tanks, how many of its own crew are
     * up, the systems a short or damage has out, and why it is already beaten, if it is.
     */
    String openingState(Entity ship, HostileComponent h) {
        com.ringsofiron.physicsexterior.components.ThrusterComponent t =
                ship.getComponent(com.ringsofiron.physicsexterior.components.ThrusterComponent.class);
        // What the propellant tanks hold, and what the thrusters can draw: the feed gives them nothing
        // while the engines are down, so the two differ on a ship whose tanks are full but can't burn
        double held = 0, capacity = 0;
        ShipSubsystemRegistryComponent tanksOf = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (tanksOf != null) {
            for (SubsystemComponent tank : com.ringsofiron.interiorgrid.TankSupply.tanks(tanksOf, com.ringsofiron.interiorgrid.Fluid.PROPELLANT)) {
                held += tank.contents;
                capacity += tank.capacity;
            }
        }
        String tanks = String.format(Locale.ROOT, "tanks %.0f of %.0f kg, thrusters see %.0f", held, capacity, t == null ? 0f : t.fuel);
        int[] crew = world.disablement.crewCount(ship);
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        List<String> shorted = new ArrayList<>(), out = new ArrayList<>();
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                if (s == null) continue;
                if (s.shorted) shorted.add(s.type.name().toLowerCase(Locale.ROOT));
                else if (!s.isOperational || s.isDisabled()) out.add(s.type.name().toLowerCase(Locale.ROOT));
            }
        }
        DisabledComponent.Reason beaten = ShipDisablement.reason(ship);
        return String.format(Locale.ROOT, "%s, crew %d of %d up, shorted %s, out %s, hull %.2f, %s, %d plunders",
                tanks, crew[1], crew[0], shorted.isEmpty() ? "none" : String.join("/", shorted),
                out.isEmpty() ? "none" : String.join("/", out), hull(ship),
                beaten == null ? "not beaten" : "already beaten (" + beaten.label + ")", h.plunders);
    }

    /**
     * If the fight with {@code what} left a leak the crew can patch, and no earlier fight did, checks
     * damage control on it: out here, far from a yard, warp is refused while air vents, and the crew
     * patch the leak from the ship's stores. Once nothing vents, warp is no longer refused for it
     * (round 3 had to dock at a yard first). A breach the patches can't stop is logged and left to
     * the yard. Which fight holes the ship depends on the seed and on the round 4 balance (a plain
     * raider rarely fires), so the career checks the first fight that leaves a patchable leak.
     */
    void patchIfVenting(String what) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        logVenting("after " + what);
        if (patchedAfter != null || LeakPatches.leaks(grid).size == 0) return;
        // A fire aboard refuses warp first: the crew put it out, then the venting is what refuses it
        // (round 8: seed 7's ace fight, flown without two of Highport's technicians aboard)
        if (world.engine.getRailsWarpBlockedReason() != null && world.engine.getRailsWarpBlockedReason().startsWith("Fire")) {
            boolean out = runUntil(600f, () -> world.engine.getRailsWarpBlockedReason() == null
                    || !world.engine.getRailsWarpBlockedReason().startsWith("Fire"));
            log("  a fire aboard after %s: %s", what, out ? "put out" : "still burning after 10 minutes");
            if (LeakPatches.leaks(grid).size == 0) return;
        }
        assertTrue(world.engine.getRailsWarpBlockedReason().startsWith(InteriorHazardWarpGuard.VENTING),
                world.engine.getRailsWarpBlockedReason());
        int kits = grid.patchKits;
        double p0 = world.engine.getSimSeconds();
        if (!runUntil(600f, () -> LeakPatches.leaks(grid).size == 0)) {
            // Seen on seed 2026: a wall leak beside an emptied room that the engineer set out for
            // and gave up on as out of reach. Logged for the crew's part, and left to the yard; a
            // later fight's leak is checked instead
            for (LeakPatches.Leak l : LeakPatches.leaks(grid)) log("  the crew didn't reach the leak at (%d, %d) in 10 minutes", l.x, l.y);
            return;
        }
        run(60);
        if (!DamageControl.hasPatches(grid) && grid.patchKits == kits) {
            // More hits (an ace firing as it breaks off) wrecked the leaking piece outright before the
            // crew reached it: a breach is sealed, not patched, so a later fight checks the patching
            log("  the leak after %s became a breach before the crew could patch it; sealed instead.", what);
            return;
        }
        assertTrue(DamageControl.hasPatches(grid), "patched, not repaired");
        assertTrue(grid.patchKits < kits, "the patches came from the stores");
        if (grid.isVenting()) {
            int breaches = 0;
            for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) if (grid.getTile(x, y).isHullBreach()) breaches++;
            log("  still venting through %d hull breach(es) the patches can't stop; warp: %s", breaches,
                    world.engine.getRailsWarpBlockedReason());
        } else {
            assertNull(new InteriorHazardWarpGuard(grid).railsWarpBlockedReason(), "the leak no longer holds warp back");
            assertTrue(noticed("Leak patched"), () -> "the player was told: " + world.notices.history().stream().map(n -> n.text).toList());
        }
        log("Damage control: leaks patched in %.0f s with %d patch(es) from the stores (%d left); hull %.2f; "
                        + "warp held back by: %s.", world.engine.getSimSeconds() - p0, kits - grid.patchKits,
                grid.patchKits, hull(game.player), world.engine.getRailsWarpBlockedReason());
        patchedAfter = what;
    }

    /** Has {@code station}'s yard mend the ship, which takes off the crew's patches and restocks the kits. */
    void repairAt(Entity station) {
        logVenting("at the yard");
        double cost = world.yard.repairCost(game.player), paid = credits(), r0 = world.engine.getSimSeconds();
        log("Yard: %s (quoted %.0f cr).", world.yard.orderRepairs(game.player, station), cost);
        assertTrue(runUntil(2 * 3_600f, () -> !world.yard.repairing(game.player)), "the yard finished");
        // Should the ship come loose of the latch for a moment, the work is called off: order it again
        // (the yard surviving a blip is round 5's docking part)
        int again = 0;
        for (; again < yardTries && world.yard.repairCost(game.player) > 0.5; again++) {
            for (String n : world.yard.notices) log("  yard: %s", n);
            world.yard.notices.clear();
            if (world.docking.dockedTo(game.player) != station) assertTrue(game.dockAt(world.universe.uuidOf(station)));
            run(60);
            log("  ordered again: %s", world.yard.orderRepairs(game.player, station));
            assertTrue(runUntil(2 * 3_600f, () -> !world.yard.repairing(game.player)), "the yard finished");
        }
        log("  repaired in %.1f h for %.0f cr, ordered %d time(s); hull %.2f; docked at %s.", (world.engine.getSimSeconds() - r0) / 3_600,
                paid - credits(), again + 1, hull(game.player), world.docking.dockedTo(game.player) == null ? "nothing"
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

    void takeAnOperation() {
        Contract op = null;
        for (Contract c : world.contracts.offers(game.highport)) if (c.type == Contract.Type.OPERATION) op = c;
        assertNotNull(op, "a lieutenant is offered an operation at Highport");
        // Not taken: the script flies to the Moon instead, and a dropped operation costs standing
        log("Offered operation %s (%s): reward %.0f cr, %+.0f standing.", op.operationName, op.reason, op.reward, op.standing);
    }

    void flyToTheMoon() {
        logVenting("before the Moon trip");
        saveForPlaytest("Before the Moon trip");
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

    void fightAnAce() {
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
        saveForPlaytest("First ace fight");
        int crew = ownCrew().size();
        fight(ship, h, "Ace fight");
        patchIfVenting("the ace");
        run(60 * 10);
        if (bounty != null) log("  its bounty: %s.", bounty.status);
        snapshot("after the ace");
        if (ShipDisablement.isDisabled(game.player)) {
            // A stranded ship calls the tug (round 3's beacon) rather than sit in a soft lock
            log("  disabled: tow refusal %s.", world.beacon.refusal());
            log("  %s", world.beacon.call());
            assertTrue(runUntil(30f, () -> world.docking.dockedTo(game.player) != null), "towed home");
            hireReplacements(crew);
            // Towed in holed and dry: the yard mends and fills it, or the Moon trip can't start
            run(120);
            Entity home = world.docking.dockedTo(game.player);
            double bill = world.yard.repairCost(game.player, home) + world.yard.airCost(game.player, home) + world.yard.refillCost(game.player, home);
            if (credits() < bill) {
                log("  short by %.0f cr for the yard: the script makes up the difference.", bill - credits());
                makeUp(bill, "the yard after the ace");
            }
            repairAt(home);
            log("  yard: %s.", world.yard.orderRefill(game.player, home));
        } else {
            assertTrue(game.dockAtHighport());
            run(120);
            hireReplacements(crew);
            // The ace's rounds holed the hull: Highport's yard mends it before the crew run out of air
            repairAt(world.engine.getEntity(game.highport));
        }
        // Berths the dead held are free again
        hireReplacements(crew);
        snapshot("home again");
    }

    void weeksPass(int weeks) {
        double t0 = world.engine.getSimSeconds(), credits0 = credits();
        world.budget.pay();
        double budget = world.standing.rank().budget;
        float morale0 = meanMorale();
        Entity berth = world.docking.dockedTo(game.player);
        double until = t0 + weeks * ServiceBudget.WEEK;
        warpFor(weeks * ServiceBudget.WEEK);
        // Round 9, seed 4 on the suits branch: a Free Trader flew through Crisium's berths and tore the
        // ship loose, and warp gave up two weeks short. A player docks again and waits out the rest
        for (int again = 0; again < 3 && berth != null && world.docking.dockedTo(game.player) == null
                && world.engine.getSimSeconds() < until && world.getCrash() == null; again++) {
            log("  came loose of %s's berth on day %.2f (hull %.2f): docked again.", PlayerDocking.name(berth),
                    world.engine.getSimSeconds() / 86_400, hull(game.player));
            assertTrue(game.dockAt(world.universe.uuidOf(berth)), "a port free at " + PlayerDocking.name(berth));
            run(120);
            warpFor(until - world.engine.getSimSeconds());
        }
        log("%d weeks docked: credits %+.0f (budget %.0f a week before wages), crew morale %.2f -> %.2f.", weeks,
                credits() - credits0, budget, morale0, meanMorale());
        log("  %.1f days passed.", days(t0));
        snapshot("weeks later");
    }

    void buyTheGunship() {
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
            makeUp(cost + 1, "the gunship");
        }
        String said = world.shipyard.buy("patrol_gunship");
        log("  %s", said);
        assertEquals("gunship_heavy", game.player.getComponent(DefRefComponent.class).defId, said);
        run(120);
        snapshot("in the gunship");
    }

    // ------------------------------------------------------------------ helpers

    void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    boolean runUntil(float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    /** Lets {@code seconds} of game time pass in the fastest warp the game allows, raising it again after each drop. */
    void warpFor(double seconds) {
        double until = world.engine.getSimSeconds() + seconds;
        int frames = 0;
        double last = world.engine.getSimSeconds();
        while (world.engine.getSimSeconds() < until && frames++ < 100_000) {
            // Time stood still for ten seconds of frames: something holds the game, so fail rather than spin
            if (frames % 600 == 0) {
                assertNotEquals(last, world.engine.getSimSeconds(), "game time stopped; warp: "
                        + world.engine.getRailsWarpBlockedReason() + "; career over: " + world.getCareerOver());
                last = world.engine.getSimSeconds();
            }
            // Round 10: a warp held low (seeds 15 and 18 sat at ×10 for the weeks after a docking at
            // Crisium held rails warp) is raised again once a second, as a player would
            if ((world.timeWarp.getWarp() == 1 || frames % 60 == 0 && world.timeWarp.getWarp() < 100_000) && world.getCrash() == null) {
                double left = until - world.engine.getSimSeconds();
                String blocked = world.engine.getRailsWarpBlockedReason();
                if (blocked != null && warpBlocks.add(blocked)) log("  (rails warp refused: %s)", blocked);
                int now = world.timeWarp.getWarp();
                for (int level : new int[]{100_000, 10_000, 1_000, 100, 10}) {
                    if (level <= now && now > 1) break;
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

    static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    void flyTo(Entity target, float meters) {
        Vector2 at = PhysicsManager.getPosition(body(target), new Vector2());
        Vector2 v = PhysicsManager.getVelocity(body(target), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, -90f * MathUtils.degRad);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    /** Moves the player and the prize latched to it, as one, to {@code meters} off {@code station}, at rest beside it. */
    void towTo(Entity towed, Entity station, float meters) {
        Body s = world.docking.bodyOf(station);
        Vector2 at = new Vector2(), v = new Vector2();
        if (s != null) {
            PhysicsManager.getPosition(s, at);
            PhysicsManager.getVelocity(s, v);
        } else {
            // Round 9, seed 9: a long chase took the fight, and the bubble with it, 25 km past
            // Highport, out of the bubble. Where it is from the bubble's anchor, off the rails
            log("  towing in from %.0f km: %s is out of the bubble.", distance(game.player, station) / 1000, PlayerDocking.name(station));
            double now = world.universe.now();
            var gateway = world.macroSim.gateway();
            Entity frame = world.universe.entity(gateway.anchorParentUuid());
            var st = new com.ringsofiron.orbital.math.StateVector();
            world.universe.stateInFrame(station, frame, now, st);
            StateVector a = gateway.anchorStateAt(now, new StateVector());
            at.set((float) (st.x - a.x), (float) (st.y - a.y));
            v.set((float) (st.vx - a.vx), (float) (st.vy - a.vy));
        }
        Vector2 own = PhysicsManager.getPosition(body(game.player), new Vector2());
        Vector2 prize = PhysicsManager.getPosition(body(towed), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x + meters, at.y, body(game.player).getAngle());
        PhysicsManager.setPosition(body(towed), at.x + meters + prize.x - own.x, at.y + prize.y - own.y,
                body(towed).getAngle());
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
        PhysicsManager.setVelocity(body(towed), v.x, v.y);
        run(2);
        if (s == null && !world.docking.isRigid(game.player)) {
            // Moved that far, the bubble shifts under the pair and the latch lets go: latch on again
            world.docking.dockInPlace(game.player, 0, towed, 0);
            boolean latched = runUntil(10f, () -> world.docking.isRigid(game.player));
            log("  the latch let go on the way in: %s.", latched ? "latched on again" : "could not latch on again");
        }
    }

    /** Puts a hostile on the rails {@code dx, dy} meters from the bubble origin and lets it come in. */
    Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(),
                s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    static boolean inBubble(Entity ship) {
        PhysicsBodyComponent p = ship.getComponent(PhysicsBodyComponent.class);
        return p != null && p.body != null;
    }

    /** The range between two ships in the physics bubble, meters, or {@link #distance} for any other. */
    double range(Entity a, Entity b) {
        if (!inBubble(a) || !inBubble(b)) return distance(a, b);
        return PhysicsManager.getPosition(body(a), new Vector2()).dst(PhysicsManager.getPosition(body(b), new Vector2()));
    }

    double distance(Entity a, Entity b) {
        double now = world.universe.now();
        Entity frame = world.universe.parentOf(b);
        var sa = new com.ringsofiron.orbital.math.StateVector();
        var sb = new com.ringsofiron.orbital.math.StateVector();
        inFrame(a, frame, now, sa);
        inFrame(b, frame, now, sb);
        return Math.hypot(sa.x - sb.x, sa.y - sb.y);
    }

    /** A ship in the physics bubble has no orbit of its own: it rides the bubble's anchor (meters off it). */
    void inFrame(Entity e, Entity frame, double now, com.ringsofiron.orbital.math.StateVector out) {
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

    int alive(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship && e.getComponent(CrewMemberComponent.class).isAlive()) n++;
        }
        return n;
    }

    int partyAboard(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.getComponent(GridPositionComponent.class).ship == ship && c.home == game.player && c.isAlive()) n++;
        }
        return n;
    }

    List<Entity> ownCrew() {
        return world.crewCareer.ownCrew(game.player);
    }

    float meanMorale() {
        List<Entity> crew = ownCrew();
        float sum = 0f;
        for (Entity e : crew) sum += e.getComponent(CrewMemberComponent.class).record().morale;
        return crew.isEmpty() ? Float.NaN : sum / crew.size();
    }

    int pirates() {
        int n = 0;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (h.isPirate()) n++;
        return n;
    }

    TraderComponent account() {
        return game.player.getComponent(TraderComponent.class);
    }

    double credits() {
        return account().credits;
    }

    float hull(Entity ship) {
        return ShipDisablement.hullIntegrity(ship.getComponent(ShipGridComponent.class));
    }

    double propellantKg() {
        ShipSubsystemRegistryComponent r = game.player.getComponent(ShipSubsystemRegistryComponent.class);
        return r == null ? 0 : PropellantFeed.available(r);
    }

    double days(double since) {
        return (world.engine.getSimSeconds() - since) / 86_400;
    }

    /** What the script made up each time a career ran short of credits, in order: "N cr for what". */
    final List<String> madeUp = new ArrayList<>();

    /**
     * Puts the credits a career is short in, bringing it to {@code to}, and says so in the journal
     * (round 9): the sweep counts it, and a career that is made up is not paced in play.
     */
    void makeUp(double to, String what) {
        double n = to - credits();
        grantedCredits += n;
        account().credits = to;
        madeUp.add(String.format(Locale.ROOT, "%.0f cr for %s", n, what));
        log("  the script made up %.0f cr for %s (%.0f cr in all).", n, what, grantedCredits);
    }

    /**
     * Checks the career earned {@code rank} in play; if a lost fight left it short, grants the standing
     * still missing and logs how much that was.
     */
    void requireRank(int rank, String standsFor) {
        float need = world.standing.ranks().get(rank).standing - world.standing.standing(world.standing.sponsor());
        if (need > 0) {
            world.standing.reward(world.standing.sponsor(), need + 0.5f, "script");
            granted += need + 0.5f;
            log("Granted %.0f standing (%s) for rank %s.", need + 0.5f, standsFor, world.standing.describeRank());
        } else {
            log("Rank %s earned in play.", world.standing.describeRank());
        }
        // Standing enough, but not yet long enough in grade: a player waits it out docked
        for (int r = world.standing.record().rank + 1; r <= rank; r = world.standing.record().rank + 1) {
            double left = world.standing.timeInGradeLeft(r);
            if (left <= 0) break;
            log("  %.1f more days in grade before rank %d.", left / 86_400, r);
            warpFor(left + 60);
            world.standing.promoteDue();
        }
        assertTrue(world.standing.record().rank >= rank);
    }

    /** True if the notice log holds a notice starting with {@code text}. */
    boolean noticed(String text) {
        for (NoticeLog.Notice n : world.notices.history()) if (n.text.startsWith(text)) return true;
        return false;
    }

    /** Where air leaves the player's ship for space, if anywhere: rails warp waits on it. */
    void logVenting(String when) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        for (int i = 0; i < grid.airflow.size; i++) {
            var e = grid.airflow.get(i);
            if (e.other == null && e.flow > ShipGridComponent.VENTING_HAZARD_FLOW) {
                var t = grid.getTile(e.x, e.y);
                log("  venting %s at (%d, %d) %s: %.3f mol/s, tile %s", when, e.x, e.y, e.side, e.flow, t);
            }
        }
    }

    void snapshot(String when) {
        log("[%s] day %.2f, credits %.0f, standing %.1f, rank %s, difficulty %.2f, ship value %.0f, hull %.2f, crew %d",
                when, world.engine.getSimSeconds() / 86_400, credits(), world.standing.standing(world.standing.sponsor()),
                world.standing.describeRank(), Difficulty.of(world), Difficulty.shipValue(game.player), hull(game.player),
                ownCrew().size());
    }

    /**
     * Saves the game as {@code name} into {@code build/playtest-saves/seed-<seed>/}, with the summary
     * the load menu lists, for the real-window playtest to load ({@code tools/playtest/mid-late.sh}).
     * A save the game refuses (in warp, say) is logged and skipped.
     */
    void saveForPlaytest(String name) {
        Path dir = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("playtest-saves").resolve("seed-" + seed);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log("  (playtest save %s: %s)", name, e.getMessage());
            return;
        }
        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(
                dir.resolve(name.replaceAll("[^A-Za-z0-9_-]+", "_") + ".json").toFile());
        String refused = game.quickSave(file);
        if (refused == null) com.hawkwing.RingsOfIron.shell.SaveSummary.of(game, name).writeFor(file);
        log("  (playtest save %s: %s)", name, refused == null ? "written" : refused);
    }

    void logBoard(String station) {
        for (Contract c : world.contracts.offers(station)) {
            log("  board: %s, %.0f cr, %+.0f standing%s", c.heading(), c.reward, c.standing,
                    world.contracts.locked(c) == null ? "" : " (locked: " + world.contracts.locked(c) + ")");
        }
    }

    void log(String format, Object... args) {
        String line = String.format(Locale.ROOT, format, args);
        journal.add(line);
        System.out.println(line);
        try {
            Path out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve(journalName() + ".log");
            Files.createDirectories(out.getParent());
            Files.writeString(out, line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the journal is written whole at the end too
        }
    }
}
