package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
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
        // The raid is flown, the base fights back, and it falls
        assertTrue(raidsFought > 0, "the raid was flown");
        assertTrue(scrambled > 0, "the base scrambled its wing");
        // Round 6: the Captain in the frigate wins the raid
        assertFalse(world.pirateBase.standing(), "the base falls to the raid");
        assertEquals("frigate_patrol", game.player.getComponent(DefRefComponent.class).defId);
        assertTrue(rank() >= 3);
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
            log("Operation %s (%s) at %s: %s", best.operationName, why, PlayerDocking.name(world.engine.getEntity(station)),
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
        for (String station : new String[]{game.highport, game.shipyard}) {
            dockAtStation(station);
            Contract best = null;
            for (Contract c : world.contracts.offers(station)) {
                if (c.type != Contract.Type.BOUNTY || world.contracts.locked(c) != null) continue;
                HostileComponent h = world.macroSim.hostiles().get(c.targetUuid);
                if (h == null || !h.isPirate()) continue;
                if (best == null || c.standing > best.standing) best = c;
            }
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
        if (target.getComponent(PhysicsBodyComponent.class) == null) bringIn(c.targetUuid, 3_000, 0);
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
        int weeks = 0;
        while (credits() < cost && weeks < 12) {
            // A Commander's pay, with the contracts a week brings
            if (!takeABounty()) weeksPass(1);
            else if (credits() < cost) weeksPass(1);
            weeks++;
            dockAtStation(game.shipyard);
            cost = world.shipyard.cost(frigate, game.player);
        }
        log("Frigate at Anvil Yards: price %.0f, trade-in %.0f, cost %.0f; we have %.0f after %d week(s). Refusal: %s.",
                frigate.price, Shipyard.tradeIn(game.player), cost, credits(), weeks, world.shipyard.refusal(frigate));
        if (credits() < cost) {
            log("  short by %.0f cr: the script makes up the difference.", cost - credits());
            grantedCredits += cost + 1 - credits();
            account().credits = cost + 1;
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

    /** Flies to the base: its wing scrambles, and each defender is fought in turn until it strikes. */
    private void raidTheBase(Contract c) {
        raidsFought++;
        Entity base = world.engine.getEntity(world.pirateBase.uuid());
        undockForAFight();
        world.rescue.jumpBeside(game.player, base, 8_000);
        if (raidsFought == 1) saveForPlaytest("Before the raid");
        double t0 = world.engine.getSimSeconds();
        runUntil(10f, () -> !world.pirateBase.defenders().isEmpty());
        List<String> wing = new ArrayList<>(world.pirateBase.defenders());
        scrambled += wing.size();
        log("  at %s: it scrambles %s.", world.pirateBase.name(), wing);
        float hull0 = hull(game.player);
        int crew0 = ownCrew().size();
        for (String name : wing) {
            Entity ship = hostile(name);
            if (ship == null || FirstPlay.beaten(world, ship)) continue;
            HostileComponent h = ship.getComponent(HostileComponent.class);
            log("  defender %s: %s, loadout %s.", name, h.shipId, h.loadout);
            if (ship.getComponent(PhysicsBodyComponent.class) == null) bringIn(world.universe.uuidOf(ship), 3_000, 0);
            if (!fight(ship, h, "Defender " + name)) break;
        }
        runUntil(2 * PirateBase.CHECK_SECONDS + 1, () -> !world.pirateBase.standing());
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

    /** Repairs at Highport if the last job left any damage. */
    private void repairIfNeeded() {
        if (world.yard.repairCost(game.player) > 0.5) {
            dockAtStation(game.highport);
            yardRepair(world.engine.getEntity(game.highport));
        }
        // Hands lost in the fight are replaced at Highport
        if (ownCrew().size() < crewWanted()) {
            dockAtStation(game.highport);
            hireUpTo(game.highport, crewWanted());
        }
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
                        : PlayerDocking.name(world.docking.dockedTo(game.player)), hull(game.player));
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
                PlayerDocking.name(station), cost, paid - credits(), orders, (world.engine.getSimSeconds() - r0) / 3_600,
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

    private Entity hostile(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return world.engine.getEntity(e.getKey());
        }
        return null;
    }

    private int rank() {
        return world.standing.record().rank;
    }

    private double day() {
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
