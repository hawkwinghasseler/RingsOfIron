package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Round 10 part 4's trace of round 9's real-window case (9f41c90): "First ace fight" lost with the
 * guns held, the tow to Highport with the cabin still in vacuum, then physics warp at the berth. Logs
 * each second the captain's suit air, the air where they stand, each check of
 * {@code CaptainSafety.steerAshore}, the crew and the warp. Run with
 * {@code ./gradlew :core:test --tests '*CaptainAshoreProbe' -PcaptainProbe} (build/captain-ashore.txt).
 */
class CaptainAshoreProbe extends CareerScript {
    @Override
    String journalName() {
        return "captain-ashore";
    }

    @Test
    void theCaptainAtAVentingBerthUnderWarp() {
        assumeTrue(System.getProperty("captain.probe") != null, "-PcaptainProbe");
        start(seed);
        game.beginCareer();
        run(120);
        hireAVeteran();
        fightTheRaiderAndTowItHome();
        fightASecondRaider();
        requireRank(1, "a second fight's worth");
        takeAnOperation();

        // fightAnAce up to its save, then the guns held
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
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, world.macroSim.hostiles().all().values(), Difficulty.of(world), true));
        warpFor(86_400);
        world.docking.undock(game.player);
        run(60);
        h.targetUuid = HostileComponent.PLAYER;
        bringIn(id, 3_000, 0);
        boolean lost = runUntil(600f, () -> ShipDisablement.isDisabled(game.player) && world.disablement.judge(game.player) != null);
        log("Ace fight with the guns held: %s at %.0f s, hull %.2f, disabled %s.", lost ? "lost" : "not lost",
                world.engine.getSimSeconds(), hull(game.player), ShipDisablement.reason(game.player));
        log("  %s", crew());
        log("  %s", world.beacon.call());
        assertTrue(runUntil(30f, () -> world.docking.dockedTo(game.player) != null), "towed home");
        run(60);
        log("At the berth: %s", crew());
        log("  venting: %s; rails warp: %s", world.berthVenting.station() != null, world.engine.getRailsWarpBlockedReason());

        // Warp at the berth as part 6 did: the period key four times (x10 physics warp)
        int steps = Integer.parseInt(System.getenv().getOrDefault("CAPTAIN_PROBE_STEPS", "4"));
        for (int i = 0; i < steps; i++) world.timeWarp.step(1);
        log("Warp %d (rails %s).", world.timeWarp.getWarp(), world.timeWarp.isRailsWarp());
        double next = world.engine.getSimSeconds();
        double until = next + 900;
        while (world.engine.getSimSeconds() < until && world.getCareerOver() == null) {
            world.update(FRAME);
            if (world.engine.getSimSeconds() >= next) {
                next += 5;
                log("%s", second());
                if (!dumped && world.crew.crew(world.captain.avatar()).suitAir < CaptainSafety.ASHORE_AT) {
                    dumped = true;
                    log("%s", way());
                }
            }
        }
        log("Career over: %s", world.getCareerOver());
        log("  %s", crew());
    }

    private boolean dumped;

    /** The way from the captain to each link, step by step, with the edge between each step. */
    private String way() {
        Entity e = world.captain.avatar();
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        com.ringsofiron.aicrew.nav.GridPathfinder pf = world.crew.pathfinder(grid);
        StringBuilder s = new StringBuilder();
        for (GridLink l : grid.links) {
            com.badlogic.gdx.utils.Array<com.ringsofiron.aicrew.nav.GridNode> path = new com.badlogic.gdx.utils.Array<>();
            boolean found = pf.findPath(p.tileX(), p.tileY(), l.x, l.y, path);
            s.append(String.format(Locale.ROOT, "link at %d,%d side %s found %s:", l.x, l.y, l.side, found));
            int x = p.tileX(), y = p.tileY();
            for (com.ringsofiron.aicrew.nav.GridNode n : path) {
                com.ringsofiron.interiorgrid.EdgeWall w = (n.x == x && n.y == y) ? null : grid.getEdgeBetween(x, y, n.x, n.y);
                s.append(String.format(Locale.ROOT, " %d,%d%s", n.x, n.y, w == null ? "" : (w.hasBulkhead ? (w.blocks() ? "[shut door]" : "[open door]") : (w.blocks() ? "[wall]" : "[edge]"))));
                x = n.x;
                y = n.y;
            }
            s.append("; ");
        }
        return s.toString();
    }

    private String second() {
        Entity e = world.captain.avatar();
        CrewMemberComponent c = world.crew.crew(e);
        GridPositionComponent p = world.crew.position(e);
        ShipGridComponent grid = world.crew.grid(p.ship);
        PlayerAvatarComponent walk = e.getComponent(PlayerAvatarComponent.class);
        StringBuilder links = new StringBuilder();
        for (GridLink l : grid.links) {
            links.append(String.format(Locale.ROOT, "[%s cross %s friendly %s safe %s]", PlayerDocking.name(l.otherShip), l.canCross(),
                    world.crew.friendlyTo(c.faction, l.otherShip), CrewTasks.anywhereSafe(l.other)));
        }
        return String.format(Locale.ROOT, "t %.0f warp %d: you %s %s, suit %s %.0f s, on %s (%d,%d %.2f,%.2f walkway %s) breathable %s, "
                        + "safe aboard %s, move %.2f,%.2f, links %s | %s",
                world.engine.getSimSeconds(), world.timeWarp.getWarp(), c.state, c.isConscious() ? "up" : "down",
                c.suited ? "on" : "off", c.suitAir, p.ship == game.player ? "the ship" : PlayerDocking.name(p.ship), p.tileX(), p.tileY(),
                p.x, p.y, p.walkway == null ? "no" : String.format(Locale.ROOT, "past %.2f of %.2f", p.walkway.past(p.x, p.y), p.walkway.gap), TaskRules.isBreathable(grid, p.tileX(), p.tileY()), CrewTasks.anywhereSafe(world.crew.grid(game.player)),
                walk.moveX, walk.moveY, links, crew()) + avatars();
    }

    private String avatars() {
        StringBuilder s = new StringBuilder(" avatars:");
        for (Entity a : world.crew.crewEntities()) {
            if (a.getComponent(PlayerAvatarComponent.class) == null) continue;
            GridPositionComponent q = world.crew.position(a);
            s.append(String.format(Locale.ROOT, " [%s %s %.2f,%.2f on %s, game.avatar %s, in engine %s]", world.crew.crew(a).name,
                    Integer.toHexString(System.identityHashCode(a)), q.x, q.y, q.ship == game.player ? "the ship" : PlayerDocking.name(q.ship),
                    a == game.avatar, world.engine.getEcsEngine().getEntities().contains(a, true)));
        }
        return s.toString();
    }

    private String crew() {
        StringBuilder s = new StringBuilder();
        for (Entity e : world.crewCareer.ownCrew(game.player)) {
            CrewMemberComponent c = world.crew.crew(e);
            GridPositionComponent p = world.crew.position(e);
            s.append(String.format(Locale.ROOT, "%s %s %s %s %s on %s; ", c.name, c.state, c.wound, c.activity,
                    c.suited ? String.format(Locale.ROOT, "suited %.0f s", c.suitAir) : "unsuited",
                    p.ship == game.player ? "the ship" : PlayerDocking.name(p.ship)));
        }
        return s.toString();
    }
}
