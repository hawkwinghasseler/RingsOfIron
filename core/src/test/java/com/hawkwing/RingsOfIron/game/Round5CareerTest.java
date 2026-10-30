package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.career.Contract;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Round 5's career past the gunship, played by script on from round 3's ({@link #playToTheGunship}):
 * a Lieutenant Commander's operation, bounties to Commander, weeks of a Commander's pay, the frigate
 * bought at Anvil Yards, Hunt the Hunters flown in it to the raid on the pirates' base (which
 * scrambles a wing), and on towards Captain as far as {@link #LAST_DAY} allows. Each operation step
 * is flown as a player would, with the usual hand-moves to get near: {@link Rescue#jumpBeside} puts
 * the ship beside a far station, and a pirate to find or fight is brought in beside the ship.
 *
 * <p>Writes {@code build/round5-career.txt}, the source of {@code planning/round5-career.md}.
 */
class Round5CareerTest extends CareerScript {
    /** The script stops climbing on this day, wherever it has got to. */
    static final double LAST_DAY = 140;
    /** Most operation steps or bounties the script flies, so a stuck world cannot spin forever. */
    static final int MOST_JOBS = 40;
    /** Most tries at the raid, each after the yard (round 7: 2). */
    static final int RAID_TRIES = 3;
    /**
     * Round 10: while the money is what holds the frigate back, {@link #buyTheFrigate} takes the bounty
     * that pays most, as a player saving for a ship would; otherwise the one with the most standing,
     * as before. Seed 9 took two plain raiders for 14,322 and 22,185 cr with a week docked after each.
     */
    static final boolean PAY_FIRST_WHEN_SHORT = true;
    /**
     * Round 10: bounties {@link #buyTheFrigate} takes in a day, from either board, before a week docked
     * while the money is short (round 9: 1). Seed 9's Highport board offered one bounty a week.
     */
    static final int FRIGATE_BOUNTIES_A_DAY = 2;

    final List<String> rows = new ArrayList<>();
    int jobs, operationsDone, bountiesDone, raidsFought;
    double repairsLeft;
    int scrambled;
    boolean savedAFight;

    @Override
    String journalName() {
        return "round5-career";
    }

    @Tag("slow")
    @Test
    void theCareerPastTheGunship() {
        playPastTheGunship();
        // The raid is flown and the base fights back. Whether it falls is a balance target, the
        // playtest benchmark CareerPacingBenchmark
        assertTrue(raidsFought > 0, "the raid was flown");
        assertTrue(scrambled > 0, "the base scrambled its wing");
        assertEquals("frigate_patrol", game.player.getComponent(DefRefComponent.class).defId);
        assertTrue(rank() >= 3);
    }

    /** The career from the start to the raid and on towards Captain, with its journal and milestone table. */
    void playPastTheGunship() {
        playToTheGunship();
        // A gunship or frigate holed by a wing vents hard enough to shake loose of the latch more often
        // (part 6's docking work lets the yard ride through that)
        yardTries = 12;
        crewUp();
        row("Gunship");
        saveForPlaytest("Gunship");
        logBoard(game.highport);

        // 1. A Lieutenant Commander's operation, then bounties, to Commander
        flyAnOperation("Lieutenant Commander");
        while (rank() < 3 && day() < LAST_DAY && jobs < MOST_JOBS && takeABounty()) { }
        requireRank(3, "a commander's standing");
        double commander = world.standing.record().promotedAt;
        row("Commander");
        saveForPlaytest("Commander");

        // 2. A Commander's pay and contracts buy the frigate at Anvil Yards
        buyTheFrigate();

        // 3. Hunt the Hunters, in the frigate: its last step is the raid on the pirates' base
        flyTheHunt();

        // 3b. Round 6: with the base fallen, the sponsor's Captain's operation to scatter what is left
        if (!world.pirateBase.standing() && rank() >= 4 && flyAnOperation("after the fall")) row("After the fall");

        // 4. On towards Captain, as far as the days allow, if the raid left the ship fit to fly
        while (!world.pirateBase.standing() && rank() < 4 && day() < LAST_DAY && jobs < MOST_JOBS) {
            if (!flyAnOperation("Captain") && !takeABounty()) weeksPass(1);
        }
        noteCaptain();
        if (rank() >= 4) {
            // Round 7: at least 5 days as Commander before Captain (seed 7 made both in one day)
            double captain = world.standing.record().promotedAt;
            log("Promoted to Commander on day %.1f and Captain on day %.1f.", commander / 86_400, captain / 86_400);
            assertTrue(captain - commander >= 5 * 86_400 - 1e-6, "five days as Commander first");
        }

        snapshot("end");
        log("Operations done: %d, bounties: %d, raid fights: %d, jobs flown: %d. Fights lost: %d. Repairs the yard left undone: %.0f cr. Standing granted: %.0f, credits granted: %.0f.",
                operationsDone, bountiesDone, raidsFought, jobs, fightsLost, repairsLeft, granted, grantedCredits);
        log("Tension at the end: %s", world.tension.describe(world.standing.sponsor()));
        log("Free Trader patrols flying: %d.", world.hostiles.patrols(HostileTraffic.TRADERS));
        log("World events seen: %d.", world.events.log().size());
        for (WorldEvents.Event e : world.events.log()) log("  day %d %s: %s", e.day, e.kind, e.text);
        log("");
        log("| Milestone | Day | Rank | Standing | Credits | Ship | Difficulty |");
        log("|---|---|---|---|---|---|---|");
        for (String r : rows) log("%s", r);
    }

    // ------------------------------------------------------------------ the climb

    /**
     * Takes the operation a board offers that the rank opened most recently, from Highport or Anvil
     * Yards, and flies each step. False if none was on offer or none could be started.
     */
    boolean flyAnOperation(String why) {
        for (String station : new String[]{game.highport, game.shipyard}) {
            dockAtStation(station);
            Contract best = null;
            for (Contract c : world.contracts.offers(station)) {
                if (c.type == Contract.Type.OPERATION && world.contracts.locked(c) == null && best == null) best = c;
            }
            if (best == null) continue;
            String said = world.contracts.accept(best.id);
            log("Operation %s (%s) at %s: %s", best.operationName, why, ShipNames.name(world.engine.getEntity(station)),
                    said == null ? "taken" : said);
            if (said != null) continue;
            String id = best.operationId;
            while (world.contracts.operation() != null && id.equals(world.contracts.operation().id) && jobs < MOST_JOBS) {
                Contract step = currentStep(id);
                if (step == null) break;
                flyStep(step);
            }
            boolean done = world.contracts.operationDone(id);
            log("  operation %s: %s. %s", best.operationName, done ? "done" : "not done",
                    world.contracts.history().isEmpty() ? "" : last().outcome);
            if (done) operationsDone++;
            repairIfNeeded();
            snapshot("after " + best.operationName);
            return true;
        }
        return false;
    }

    private Contract currentStep(String operationId) {
        for (Contract c : world.contracts.active()) if (operationId.equals(c.operationId)) return c;
        return null;
    }

    private Contract last() {
        return world.contracts.history().get(world.contracts.history().size() - 1);
    }

    /** Flies one contract to its end, whatever its type; logs how it went. */
    void flyStep(Contract c) {
        jobs++;
        double t0 = world.engine.getSimSeconds();
        log("  step %s", c.heading());
        // Round 7: a recon or bounty never names an ace already beaten (round 6's Scatter the Remnants did)
        assertFalse(c.targetUuid != null && acesBeaten.contains(c.targetUuid), c.heading() + " names an ace already beaten");
        switch (c.type) {
            case PATROL:
                jumpTo(c.targetUuid);
                runUntil(30f, () -> c.progress > 0 || !c.isActive());
                dockAtStation(c.destinationUuid);
                runUntil(30f, () -> !c.isActive());
                break;
            case ESCORT:
            case CONVOY:
                // Wait at the buyer: each freighter docking there with the player close counts
                dockAtStation(c.destinationUuid);
                warpUntil(c, c.deadline);
                break;
            case DELIVERY:
                deliver(c);
                break;
            case RECON: {
                Entity pirate = world.engine.getEntity(c.targetUuid);
                if (pirate == null) break;
                undockForAFight();
                if (pirate.getComponent(PhysicsBodyComponent.class) == null) bringIn(c.targetUuid, 15_000, 0);
                runUntil(10f, () -> !c.isActive());
                break;
            }
            case BOUNTY:
                fightFor(c);
                break;
            case RAID:
                raidTheBase(c);
                // Round 7: the frigate wins about 7 raids in 10, so a raid lost is flown again after
                // the yard, as a player would (seed 2026 lost its reactor to the batteries' darts).
                // Round 8: up to RAID_TRIES in all, since the career now flies the survey's raid
                for (int tries = 1; tries < RAID_TRIES && c.isActive() && world.pirateBase.standing(); tries++) {
                    log("  the raid is lost: home for repairs, and once more.");
                    repairIfNeeded();
                    raidTheBase(c);
                }
                break;
            default:
                log("  (no script for %s)", c.type);
                world.contracts.abandon(c.id);
                break;
        }
        if (c.isActive()) {
            log("  still open after %.1f d: abandoned.", days(t0));
            world.contracts.abandon(c.id);
        }
        log("  %s: %s (%.1f d).", c.status, c.outcome, days(t0));
    }

    /** Buys the goods at the giver's station and hands them over docked at the buyer. */
    private void deliver(Contract c) {
        dockAtStation(c.stationUuid);
        EconomyNode from = world.macroSim.station(c.stationUuid);
        double bought = world.macroSim.market().buy(from, account(), c.commodity, c.quantity - c.delivered);
        log("  bought %.0f %s at %s.", bought, c.commodityName, from.displayName);
        dockAtStation(c.targetUuid);
        run(10);
        if (c.isActive()) warpUntil(c, Math.min(c.deadline, world.engine.getSimSeconds() + 3_600));
    }

    /** A bounty the boards at Highport or Anvil Yards offer, fought for. False if none could be taken. */
    boolean takeABounty() {
        return takeABounty(false);
    }

    /**
     * A bounty the boards at Highport or Anvil Yards offer, fought for: the one with the most standing,
     * or with {@code payFirst} the one that pays most (round 10). The offers it chose from go in the
     * journal. False if none could be taken.
     */
    boolean takeABounty(boolean payFirst) {
        for (String station : new String[]{game.highport, game.shipyard}) {
            dockAtStation(station);
            Contract best = null;
            List<String> offers = new ArrayList<>();
            for (Contract c : world.contracts.offers(station)) {
                if (c.type != Contract.Type.BOUNTY || world.contracts.locked(c) != null) continue;
                HostileComponent h = world.macroSim.hostiles().get(c.targetUuid);
                if (h == null || !h.isPirate()) continue;
                offers.add(String.format(java.util.Locale.ROOT, "%s %.0f cr %+.0f", c.targetName, c.reward, c.standing));
                if (best == null || (payFirst ? c.reward > best.reward : c.standing > best.standing)) best = c;
            }
            if (!offers.isEmpty()) log("  bounties at %s (%s): %s", ShipNames.name(world.engine.getEntity(station)),
                    payFirst ? "pay first" : "standing first", String.join("; ", offers));
            if (best == null || world.contracts.accept(best.id) != null) continue;
            log("Took %s: %.0f cr, %+.0f standing.", best.heading(), best.reward, best.standing);
            flyStep(best);
            if (best.status == Contract.Status.DONE) bountiesDone++;
            noteCaptain();
            repairIfNeeded();
            return true;
        }
        return false;
    }

    /** Brings the bounty's ship in beside the player and fights it. */
    private void fightFor(Contract c) {
        Entity target = world.engine.getEntity(c.targetUuid);
        if (target == null) {
            run(2);
            return;
        }
        HostileComponent h = target.getComponent(HostileComponent.class);
        undockForAFight();
        boolean onRails = target.getComponent(PhysicsBodyComponent.class) == null;
        if (h != null) log("  found it %s.", onRails ? "on the rails (" + h.state + "), brought in at 3 km"
                : String.format(java.util.Locale.ROOT, "in the bubble (%s), %.0f m off", h.state, range(game.player, target)));
        if (onRails) bringIn(c.targetUuid, 3_000, 0);
        else flyTo(target, 3_000f);
        if (h != null) h.targetUuid = HostileComponent.PLAYER;
        if (!savedAFight && "gunship_heavy".equals(game.player.getComponent(DefRefComponent.class).defId)) {
            savedAFight = true;
            saveForPlaytest("Gunship bounty fight");
        }
        fight(target, h, "Bounty on " + c.targetName);
        run(60 * 10);
    }

    // ------------------------------------------------------------------ the frigate and the raid

    void buyTheFrigate() {
        dockAtStation(game.shipyard);
        Shipyard.Listing frigate = world.shipyard.listing("patrol_frigate");
        double cost = world.shipyard.cost(frigate, game.player);
        int weeks = 0, docked = 0;
        // Round 7: a new Commander waits their days in rank for the first frigate too
        while ((credits() < cost || world.standing.newChassisWait(frigate.shipId) > 0) && weeks < 12) {
            // A Commander's pay, with the contracts a week brings
            boolean took = takeABounty(PAY_FIRST_WHEN_SHORT && credits() < cost);
            // Round 10: still short, another bounty offered today before the week docked
            for (int more = 1; took && more < FRIGATE_BOUNTIES_A_DAY && credits() < cost
                    && world.standing.newChassisWait(frigate.shipId) <= 0; more++) {
                dockAtStation(game.shipyard);
                cost = world.shipyard.cost(frigate, game.player);
                if (credits() >= cost || !takeABounty(PAY_FIRST_WHEN_SHORT)) break;
            }
            if (!took || credits() < cost) {
                weeksPass(1);
                docked++;
            }
            weeks++;
            dockAtStation(game.shipyard);
            cost = world.shipyard.cost(frigate, game.player);
        }
        // Round 9: the loop's rounds, and the weeks of them spent docked, which are what move the day
        log("Frigate at Anvil Yards: price %.0f, trade-in %.0f, cost %.0f; we have %.0f after %d round(s), %d of them a week docked. Refusal: %s.",
                frigate.price, Shipyard.tradeIn(game.player), cost, credits(), weeks, docked, world.shipyard.refusal(frigate));
        if (credits() < cost) {
            log("  short by %.0f cr: the script makes up the difference.", cost - credits());
            makeUp(cost + 1, "the frigate");
        }
        log("  %s", world.shipyard.buy("patrol_frigate"));
        assertEquals("frigate_patrol", game.player.getComponent(DefRefComponent.class).defId);
        run(120);
        crewUp();
        row("Frigate");
        snapshot("in the frigate");
        saveForPlaytest("Frigate");
    }

    /** Hires the best hands the docked station offers until the ship carries the crew its design calls for. */
    void crewUp() {
        String defId = game.player.getComponent(DefRefComponent.class).defId;
        ShipDef def = world.getContent().defs.get("ship", defId, ShipDef.class);
        int want = crewWanted();
        for (String uuid : new String[]{game.shipyard, game.highport}) {
            if (ownCrew().size() >= want) break;
            dockAtStation(uuid);
            hireUpTo(uuid, want);
        }
        log("Crewed the %s: %d aboard of the %d it calls for.", defId, ownCrew().size(), def.crew);
    }

    private void hireUpTo(String uuid, int want) {
        while (ownCrew().size() < want) {
            CrewHiring.Candidate best = null;
            for (CrewHiring.Candidate c : world.crewHiring.pool(uuid)) if (best == null || top(c) > top(best)) best = c;
            if (best == null) break;
            String why = world.crewHiring.hire(best.key);
            if (why != null) { log("  could not hire %s: %s", best.name, why); break; }
            log("  hired %s (%s, %s)", best.name, best.role, best.record.describeSkills());
        }
    }

    /** Hunt the Hunters to its raid: recon, bounty, intel (the base found), then the raid. */
    void flyTheHunt() {
        dockAtStation(game.highport);
        Contract hunt = null;
        for (String station : new String[]{game.highport, game.shipyard}) {
            dockAtStation(station);
            for (Contract c : world.contracts.offers(station)) if ("hunt_the_hunters".equals(c.operationId)) hunt = c;
            if (hunt != null) break;
        }
        assertNotNull(hunt, "a Commander is offered the hunt");
        String said = world.contracts.accept(hunt.id);
        log("Operation Hunt the Hunters: %s", said == null ? "taken" : said);
        assertNull(said);
        while (world.contracts.operation() != null && "hunt_the_hunters".equals(world.contracts.operation().id) && jobs < MOST_JOBS) {
            Contract step = currentStep("hunt_the_hunters");
            if (step == null) break;
            // Hurt by the hunt's bounty, the ship is patched up and its wounded seen to before the raid,
            // as a player would (seed 12 flew in with its new hands bleeding out: "Nobody aboard to take the helm")
            if (step.type == Contract.Type.RAID && (fitCrew() < ownCrew().size() || world.yard.repairCost(game.player) > 0.5)) {
                repairIfNeeded();
            }
            flyStep(step);
        }
        if (!world.pirateBase.standing()) operationsDone++;
        // The hunt fell through before its raid: the sponsor's own raid contract, once the base is known.
        // A raid beaten off is where the script stops: a frigate holed by a wing vents too hard for
        // the yard to keep it latched, and the player, revived aboard, goes down again (parts 2 and 6)
        for (int attempt = 0; attempt < 1 && raidsFought == 0 && world.pirateBase.standing(); attempt++) {
            log("  the base still stands (%s); it %s.", last().outcome, world.pirateBase.found() ? "is known" : "is still hidden");
            if (!world.pirateBase.found()) world.pirateBase.reveal("Script: a captured log");
            repairIfNeeded();
            // The boards post the raid with the next day's jobs
            Contract raid = null;
            for (int d = 0; d < 3 && raid == null; d++) {
                if (d > 0) warpFor(86_400);
                for (String station : new String[]{game.highport, game.shipyard}) {
                    dockAtStation(station);
                    for (Contract c : world.contracts.offers(station)) if (c.type == Contract.Type.RAID) raid = c;
                    if (raid != null) break;
                }
            }
            assertNotNull(raid, "the sponsor offers a raid");
            assertNull(world.contracts.accept(raid.id));
            flyStep(raid);
        }
        repairIfNeeded();
        row(world.pirateBase.standing() ? "Raid beaten off" : "Base raided");
        snapshot("after the raid");
    }

    /**
     * Flies the raid the survey's way ({@link RaidRoutine}, round 8): in 8 km from the base, the helm
     * holding on it, each defender fought as the base launches it, until it strikes or we are beaten.
     */
    private void raidTheBase(Contract c) {
        raidsFought++;
        undockForAFight();
        double t0 = world.engine.getSimSeconds();
        float hull0 = hull(game.player);
        int crew0 = ownCrew().size();
        EnemySurveyTest.Result r = RaidRoutine.fly(world, game, () -> {
            if (raidsFought == 1) saveForPlaytest("Before the raid");
        });
        scrambled += r.wing.size();
        log("  at %s: it scrambles %s (%s).", world.pirateBase.name(), r.wing, r.loadouts);
        log("  try %d: %s", raidsFought, r);
        if (!"won".equals(r.outcome)) fightsLost++;
        log("Raid on %s: %.0f s, base %s; our hull %.2f -> %.2f, crew %d -> %d.", world.pirateBase.name(),
                world.engine.getSimSeconds() - t0, world.pirateBase.standing() ? "still standing" : "fallen", hull0,
                hull(game.player), crew0, ownCrew().size());
        run(60);
    }

    // ------------------------------------------------------------------ getting about

    /** Undocked, wherever the ship is. */
    private void undockForAFight() {
        if (world.docking.dockedTo(game.player) != null) {
            world.docking.undock(game.player);
            run(60);
        }
    }

    /** Undocks and jumps beside {@code station} on its orbit, a couple of km back. */
    private void jumpTo(String station) {
        undockForAFight();
        world.rescue.jumpBeside(game.player, world.engine.getEntity(station), 2_000);
        run(30);
    }

    /** Docked at {@code station}, from wherever the ship is. */
    void dockAtStation(String station) {
        Entity at = world.docking.dockedTo(game.player);
        if (at != null && world.universe.uuidOf(at).equals(station)) return;
        jumpTo(station);
        // A ship venting hard can shake loose of the latch for a moment (the yard surviving that is
        // round 5's docking part): latch on again
        for (int tries = 0; tries < 3 && world.docking.dockedTo(game.player) == null; tries++) {
            assertTrue(game.dockAt(station), "a port free at " + station);
            run(120);
        }
        assertSame(world.engine.getEntity(station), world.docking.dockedTo(game.player));
    }

    /** Warps docked until {@code c} ends or sim second {@code until}, a few hours at a time. */
    private void warpUntil(Contract c, double until) {
        while (c.isActive() && world.engine.getSimSeconds() < until && world.getCrash() == null) {
            warpFor(Math.min(6 * 3_600, Math.max(60, until - world.engine.getSimSeconds())));
        }
    }

    /** Longest the script waits out a boarding before it goes home anyway, sim seconds: the trip in and the time aboard. */
    static final float BOARDING_WAIT = (float) PirateBoarding.BOARD_SECONDS + 600f;

    /** Repairs at Highport if the last job left any damage. */
    private void repairIfNeeded() {
        // Round 8: beaten with pirates coming aboard, the ship waits out the boarding before it goes
        // home, as a beaten player would (seed 2026 went home mid-boarding and was boarded at the berth)
        if (world.pirateBoarding.boarder() != null) runUntil(BOARDING_WAIT, () -> world.pirateBoarding.boarder() == null);
        // Hands down after the fight get help before the yard's hour, or bleed out during it
        if (ownCrew().size() < crewWanted() || fitCrew() == 0) {
            dockAtStation(game.highport);
            hireReplacements(crewWanted());
        }
        if (world.yard.repairCost(game.player) > 0.5) {
            dockAtStation(game.highport);
            yardRepair(world.engine.getEntity(game.highport));
        }
        // Hands lost in the fight are replaced at Highport
        if (ownCrew().size() < crewWanted()) {
            dockAtStation(game.highport);
            hireUpTo(game.highport, crewWanted());
        }
        // Round 10: crew who sheltered ashore while the ship vented walk back aboard once the yard has
        // mended it, as a player would wait for them (seed 11 flew its second raid with them still at
        // Highport: "Nobody aboard to take the helm")
        double ashore = world.engine.getSimSeconds();
        if (!runUntil(CREW_ABOARD_WAIT, this::crewAboard)) log("  the crew still not all aboard after %.0f s.", CREW_ABOARD_WAIT);
        else if (world.engine.getSimSeconds() - ashore > 1) log("  the crew came back aboard in %.0f s.", world.engine.getSimSeconds() - ashore);
    }

    /** Longest the career waits for its crew to come back aboard after the yard, seconds. */
    static final float CREW_ABOARD_WAIT = 180f;

    /** True once every conscious hand of the ship's own is aboard it. */
    private boolean crewAboard() {
        for (Entity e : ownCrew()) {
            if (world.crew.crew(e).isConscious() && world.crew.position(e).ship != game.player) return false;
        }
        return true;
    }

    /**
     * Repairs at {@code station}'s yard, ordering again if the work stopped, and
     * goes on with what is left if the yard can't finish (round 3's {@link #repairAt} asserts it does).
     */
    private void yardRepair(Entity station) {
        String uuid = world.universe.uuidOf(station);
        double cost = world.yard.repairCost(game.player), paid = credits(), r0 = world.engine.getSimSeconds();
        int orders = 0;
        while (orders < yardTries && world.yard.repairCost(game.player) > 0.5) {
            if (world.docking.dockedTo(game.player) != station) {
                game.dockAt(uuid);
                run(60);
            }
            String said = world.yard.orderRepairs(game.player, station);
            orders++;
            if (orders <= 3 || world.yard.repairCost(game.player) > 0.5 && orders == yardTries) {
                log("  order %d: %s (docked at %s, hull %.2f)", orders, said, world.docking.dockedTo(game.player) == null ? "nothing"
                        : ShipNames.name(world.docking.dockedTo(game.player)), hull(game.player));
            }
            if (!runUntil(3_600f, () -> !world.yard.repairing(game.player))) {
                log("  the yard is still at it after an hour: %s (career over: %s, crash: %s)", said, world.getCareerOver(), world.getCrash());
                break;
            }
        }
        for (String n : world.yard.notices) log("  yard: %s", n);
        world.yard.notices.clear();
        repairsLeft += world.yard.repairCost(game.player);
        log("Yard at %s: quoted %.0f cr, paid %.0f, ordered %d time(s) in %.1f h; %.0f cr left undone; hull %.2f.",
                ShipNames.name(station), cost, paid - credits(), orders, (world.engine.getSimSeconds() - r0) / 3_600,
                world.yard.repairCost(game.player), hull(game.player));
        if (world.docking.dockedTo(game.player) != station) {
            game.dockAt(uuid);
            run(60);
        }
    }

    /** Air leaving the player's ship for space, mol/s, counting only flows past the hazard mark. */
    private double venting() {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        double sum = 0;
        for (int i = 0; i < grid.airflow.size; i++) {
            var e = grid.airflow.get(i);
            if (e.other == null && e.flow > ShipGridComponent.VENTING_HAZARD_FLOW) sum += e.flow;
        }
        return sum;
    }

    private int crewWanted() {
        String defId = game.player.getComponent(DefRefComponent.class).defId;
        return Math.min(world.getContent().defs.get("ship", defId, ShipDef.class).crew, CrewHiring.berths(game.player));
    }

    int rank() {
        return world.standing.record().rank;
    }

    double day() {
        return world.engine.getSimSeconds() / 86_400;
    }

    private void noteCaptain() {
        if (rank() >= 4 && rows.stream().noneMatch(r -> r.startsWith("| Captain"))) row("Captain");
    }

    private void row(String milestone) {
        rows.add(String.format(java.util.Locale.ROOT, "| %s | %.1f | %s | %.1f | %,.0f | %s | %.2f |", milestone, day(),
                world.standing.rankName(rank()), world.standing.standing(world.standing.sponsor()), credits(),
                game.player.getComponent(DefRefComponent.class).defId, Difficulty.of(world)));
    }
}
