package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.events.ShotFiredEvent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.ClosestApproach;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Navigation target, closest approach, plan to target, the helm's autopilot, pirates on the map, warp to burn. */
class NavigationTest {
    static final float FRAME = 1f / 60f;
    static final double MU = GameScenario.MU_EARTH;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    // ------------------------------------------------------------------ closest approach

    @Test
    void closestApproachOfTwoCircularOrbitsIsTheirGap() {
        double r1 = 7.0e6, r2 = 7.2e6;
        ClosestApproach.Path path = new ClosestApproach.Path().add(0, KeplerOrbit.circular(MU, r1, 0.5, 0, 1));
        KeplerOrbit other = KeplerOrbit.circular(MU, r2, 0.5, 0, 1);
        ClosestApproach.Result r = ClosestApproach.find(path, (t, out) -> {
            other.stateAt(t, out);
            return true;
        }, 0, 2 * Math.PI * Math.sqrt(r1 * r1 * r1 / MU), new ClosestApproach.Result());
        assertTrue(r.found());
        assertEquals(r2 - r1, r.distance, 1.0);
        assertEquals(0, r.time, 1.0, "side by side at the start, drawing apart after");
    }

    @Test
    void aHohmannTransferMeetsTheTargetItWasPhasedFor() {
        double r1 = 7.0e6, r2 = 8.0e6;
        double transfer = Math.PI * Math.sqrt(Math.pow((r1 + r2) / 2, 3) / MU);
        double n2 = Math.sqrt(MU / (r2 * r2 * r2));
        // The target leads by what it covers during the transfer, short of half a turn
        KeplerOrbit target = KeplerOrbit.circular(MU, r2, Math.PI - n2 * transfer, 0, 1);
        KeplerOrbit ship = KeplerOrbit.circular(MU, r1, 0, 0, 1);
        double dv = Math.sqrt(MU / r1) * (Math.sqrt(2 * r2 / (r1 + r2)) - 1);
        ClosestApproach.Path path = new ClosestApproach.Path().add(0, ship).add(0, ship.withBurn(0, dv, 0));
        ClosestApproach.Result r = ClosestApproach.find(path, (t, out) -> {
            target.stateAt(t, out);
            return true;
        }, 0, 2 * transfer, new ClosestApproach.Result());
        assertTrue(r.distance < 1_000, "met at apoapsis: " + r.distance);
        assertEquals(transfer, r.time, 30);
    }

    // ------------------------------------------------------------------ targets and plans

    @Test
    void theRaiderOutsideComesBeforeTheStations() {
        java.util.List<String> all = world.nav.target.candidates();
        int raider = all.indexOf(world.universe.uuidOf(game.raider));
        assertTrue(raider >= 0, all.toString());
        assertTrue(raider < all.indexOf(game.highport), "a craft in the bubble before the stations: " + all);
    }

    @Test
    void targetsCycleThroughTheStationsAndBackToNone() {
        Navigation nav = world.nav;
        assertNull(nav.target.uuid());
        java.util.List<String> all = nav.target.candidates();
        assertTrue(all.contains(game.highport) && all.contains(game.depot) && all.contains(game.lunarMine), all.toString());
        assertFalse(all.contains(world.universe.uuidOf(game.player)), "never the player's own ship");
        assertEquals("Navigation target: Highport", nav.target.select(game.highport));
        assertEquals(game.highport, nav.target.uuid());
        for (int i = 0; i < all.size() + 1; i++) nav.target.cycle(1);
        assertEquals(game.highport, nav.target.uuid(), "round the whole list and back");
        nav.target.select(null);
        nav.target.cycle(-1);
        assertEquals(all.get(all.size() - 1), nav.target.uuid());
    }

    @Test
    void planToTheDepotIsAProposalUntilAccepted() {
        Navigation nav = world.nav;
        assertTrue(nav.transfer.plan().startsWith("No navigation target"));
        nav.target.select(game.depot);
        String said = nav.transfer.plan();
        assertTrue(said.startsWith("To Deepwell Depot: 2 burns"), said);
        PlayerTransfer.Proposal p = nav.transfer.proposal();
        assertNotNull(p);
        assertNull(world.burns.nodes(), "nothing planned until it is accepted");
        assertTrue(p.burns.get(0)[0] >= world.universe.now() + PlayerTransfer.LEAD - 1);
        // A Hohmann transfer up 2,000 km from low orbit: about 900 m/s in all
        assertEquals(900, p.totalDeltaV, 150);
        assertTrue(nav.deltaVNeeded() > 0);

        // The proposal leads to the depot: the closest approach along it is close
        ClosestApproach.Result a = nav.approach();
        assertNotNull(a);
        assertTrue(a.distance < 50_000, "closest " + a.distance);

        // The orbit map shows it
        OrbitPathProvider.OrbitPath path = new OrbitPathProvider.OrbitPath();
        assertTrue(new PlayerOrbitPath(world.context, world.physics, world.universe, world.macroSim, world.pirateBase, world.nav,
                world.burns).getPlayerPath(path));
        assertTrue(path.proposed.size > 0);
        assertEquals(4, path.proposedNodes.size);
        assertEquals(4, path.approach.size);
        assertTrue(path.targetOrbit.size > 0, "the depot's orbit");
        boolean station = false, target = false;
        for (OrbitPathProvider.MapMarker m : path.markers) {
            station |= !m.target && m.name.equals("Highport");
            target |= m.target && m.name.equals("Deepwell Depot");
        }
        assertTrue(station && target);

        assertTrue(nav.transfer.accept(world.burns).startsWith("Transfer to Deepwell Depot planned: 2 burns"));
        assertNull(nav.transfer.proposal());
        assertEquals(2, world.burns.nodes().nodes.size);
        assertEquals(p.burns.get(0)[1], world.burns.nodes().nodes.first().prograde, 1e-9);
        assertTrue(nav.approach().distance < 50_000, "the same path, now planned");
    }

    @Test
    void discardingAProposalPlansNothing() {
        world.nav.target.select(game.depot);
        world.nav.transfer.plan();
        assertEquals("Transfer plan discarded", world.nav.transfer.discard());
        assertNull(world.nav.transfer.proposal());
        assertNull(world.burns.nodes());
    }

    @Test
    void planToTheLunarMinePlansTheBurnOutToTheMoon() {
        world.nav.target.select(game.lunarMine);
        String said = world.nav.transfer.plan();
        PlayerTransfer.Proposal p = world.nav.transfer.proposal();
        assertNotNull(p, said);
        assertEquals(GameScenario.MOON, p.nextParent);
        assertEquals(1, p.burns.size());
        // Trans-lunar injection from low Earth orbit is a little over 3 km/s
        assertEquals(3_100, p.burns.get(0)[1], 200);
    }

    @Test
    void aCraftInTheBubbleIsNotPlannedTo() {
        world.nav.target.select(world.universe.uuidOf(game.raider));
        assertTrue(world.nav.transfer.plan().contains("in the bubble with you"));
        assertNull(world.nav.transfer.proposal());
    }

    // ------------------------------------------------------------------ the helm

    @Test
    void ordersNeedAHelmsmanAndATarget() {
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.MATCH_VELOCITY).startsWith("The helm needs a navigation target"));
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.FLY_BURNS).startsWith("No burns planned"));
        world.nav.target.select(game.highport);
        knockOutTheCrew();
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.APPROACH).startsWith("Nobody aboard"));
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());
    }

    @Tag("slow")
    @Test
    void theHelmsmanApproachesTheTargetAndHoldsThere() {
        world.nav.target.select(game.highport);
        String said = world.nav.helm.give(HelmOrderComponent.Mode.APPROACH);
        assertTrue(said.contains("Hand over the helm"), "the player has the helm: " + said);
        assertEquals("Helm: approach Highport to 1,000 m off its hull", world.nav.helm.give(HelmOrderComponent.Mode.APPROACH),
                "again: the next distance");
        handOverTheHelm();
        double hold = 1_000 + HelmOrders.radius(world.engine.getEntity(game.highport));
        world.timeWarp.setWarp(10);
        for (int i = 0; i < 60 * 120 && !(Math.abs(range() - hold) < 5 && relativeSpeed() < 0.1); i++) world.update(FRAME);
        assertEquals(hold, range(), 5, "held off its hull at the distance asked");
        assertTrue(relativeSpeed() < 0.1, "and stopped there: " + relativeSpeed());
        assertEquals(HelmOrderComponent.Mode.APPROACH, world.nav.helm.mode(), "the order stands");
        assertTrue(world.nav.helm.describe().startsWith("Helm: approaching Highport"), world.nav.helm.describe());
    }

    @Tag("slow")
    @Test
    void theHelmsmanMatchesTheTargetsVelocity() {
        world.nav.target.select(game.highport);
        handOverTheHelm();
        Body body = game.player.getComponent(PhysicsBodyComponent.class).body;
        body.setLinearVelocity(body.getLinearVelocity().x + 15f * com.ringsofiron.physicsexterior.PhysicsManager.UNITS_PER_METER,
                body.getLinearVelocity().y);
        assertTrue(relativeSpeed() > 10);
        world.nav.helm.give(HelmOrderComponent.Mode.MATCH_VELOCITY);
        world.timeWarp.setWarp(10);
        for (int i = 0; i < 60 * 30; i++) world.update(FRAME);
        assertTrue(relativeSpeed() < 0.3, "matched: " + relativeSpeed());
    }

    @Tag("slow")
    @Test
    void theHelmsmanFliesAPlannedBurnWithTheMainEngine() {
        handOverTheHelm();
        world.burns.addNode();
        world.burns.adjust(20, 0);
        ManeuverNodeComponent.Node n = world.burns.selected();
        n.time = world.universe.now() + 200;
        KeplerOrbit k = conic();
        double expected = k.withBurn(n.time, 20, 0).apoapsis();
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.FLY_BURNS).startsWith("Helm: fly the planned burns"));
        world.timeWarp.setWarp(10);
        boolean flew = false;
        for (int i = 0; i < 60 * 60 && world.nav.helm.mode() != HelmOrderComponent.Mode.NONE; i++) {
            world.update(FRAME);
            flew |= game.player.getComponent(HelmOrderComponent.class).burning;
        }
        assertTrue(flew, "the helmsman flew it");
        assertEquals(0, world.burns.nodes().nodes.size, "no impulse left to fire");
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode(), "done with every burn");
        assertEquals(expected, conic().apoapsis(), 3_000, "where the impulse would have put it");
    }

    @Test
    void beingShotAtDropsAnApproachButNotAHold() {
        world.nav.target.select(game.highport);
        world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION);
        assertEquals(HelmOrderComponent.Mode.HOLD_POSITION, world.nav.helm.mode());
        ShotFiredEvent shot = new ShotFiredEvent();
        shot.shooter = game.raider;
        shot.target = game.player;
        shot.weaponDefId = world.combatDefs.weapons.keys().next();
        world.combatEvents.shots.dispatch(shot);
        // Round 8: keeping station is what a ship under fire wants
        assertEquals(HelmOrderComponent.Mode.HOLD_POSITION, world.nav.helm.mode(), "a hold carries on under fire");

        String said = world.nav.helm.give(HelmOrderComponent.Mode.APPROACH);
        assertEquals(HelmOrderComponent.Mode.APPROACH, world.nav.helm.mode(), said);
        world.combatEvents.shots.dispatch(shot);
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());
        world.update(FRAME);
        assertEquals("Helm order dropped: shots fired at us", world.notices.latest().text);
        assertEquals(NoticeLog.Kind.WARNING, world.notices.latest().kind);
    }

    @Test
    void theTargetAndTheOrderAreSaved() {
        world.nav.target.select(game.depot);
        world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION);
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        assertEquals(game.depot, world.nav.target.uuid());
        assertEquals(HelmOrderComponent.Mode.HOLD_POSITION, world.nav.helm.mode());
    }

    // ------------------------------------------------------------------ pirates and warp

    @Test
    void piratesInSensorRangeShowOnTheTrafficMapAndFarOnesDoNot() {
        world.nav.sightings.refresh();
        Map<String, HostileComponent> all = world.macroSim.hostiles().all();
        int near = 0;
        StateVector q = new StateVector();
        double[] trueState = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, trueState);
        for (Map.Entry<String, HostileComponent> e : all.entrySet()) {
            Entity pirate = world.engine.getEntity(e.getKey());
            if (!e.getValue().isPirate() || pirate == null || world.universe.orbit(pirate) == null) continue;
            world.universe.absoluteState(pirate, world.universe.now(), q);
            double d = Math.hypot(q.x - trueState[0], q.y - trueState[1]);
            if (d < PirateSightings.SENSOR_RANGE) {
                near++;
                assertTrue(world.nav.sightings.known().contains(e.getKey()), "seen at " + d);
                assertTrue(world.nav.sightings.sighting(e.getKey()).live);
            }
        }
        assertTrue(near > 0, "a pirate lurks by Highport");
        TrafficMapProvider.TrafficFrame frame = new TrafficMapProvider.TrafficFrame();
        new RailsTrafficMap(world.context, world.physics, world.universe, world.macroSim, world.nav, world.contracts, world.derelicts, world.pirateBase, world.tension).collect(frame);
        assertTrue(frame.count(TrafficMapProvider.ContactKind.HOSTILE) >= near);
        for (TrafficMapProvider.Contact c : frame.contacts) {
            if (c.kind == TrafficMapProvider.ContactKind.STATION) assertFalse(c.id.isEmpty(), "stations can be picked");
        }
    }

    @Test
    void warpToBurnStopsAMinuteBeforeTheHelmTurnsOntoIt() {
        world.burns.addNode();
        world.burns.adjust(5, 0);
        ManeuverNodeComponent.Node n = world.burns.selected();
        n.time = world.universe.now() + 3_600;
        String said = world.nav.warpToNextBurn();
        assertTrue(said.startsWith("Warping to the next burn"), said);
        assertTrue(world.timeWarp.isRailsWarp());
        for (int i = 0; i < 60 * 30 && world.timeWarp.isRailsWarp(); i++) world.update(FRAME);
        assertFalse(world.timeWarp.isRailsWarp(), "dropped out");
        double before = n.time - world.universe.now();
        assertEquals(world.nav.leadFor(n), before, 5, "a minute before the helm turns onto the burn");
        assertEquals(1, world.burns.nodes().nodes.size, "which has not fired");
    }

    @Test
    void theFuelEstimateCountsThePlanAgainstTheTanks() {
        world.update(FRAME);
        double left = world.nav.deltaVLeft();
        assertTrue(left > 100 && Double.isFinite(left), "a corvette's tanks: " + left);
        world.nav.target.select(game.depot);
        world.nav.transfer.plan();
        assertEquals(world.nav.transfer.proposal().totalDeltaV, world.nav.deltaVNeeded(), 1e-9);
        double back = world.nav.returnDeltaV();
        assertTrue(back >= 0 && back < 50, "Highport is 8 km ahead on the same orbit: " + back);
        assertTrue(String.join("\n", world.nav.readout()).contains("Delta-v needed"));
    }

    @Test
    void dockingGuidanceStartsFiveKilometersOut() {
        assertEquals(5_000f, PlayerDocking.READOUT_RANGE);
        assertEquals(PlayerDocking.MIN_SAFE_SPEED, PlayerDocking.safeSpeed(0f));
        assertEquals(Math.sqrt(2 * 0.1 * 1000), PlayerDocking.safeSpeed(1_000f), 1e-3);
        assertEquals(PlayerDocking.MAX_SAFE_SPEED, PlayerDocking.safeSpeed(1e6f));
    }

    // ------------------------------------------------------------------

    private KeplerOrbit conic() {
        double[] s = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, s);
        return KeplerOrbit.fromState(s[0], s[1], s[2], s[3], MU, world.universe.now());
    }

    private double range() {
        StateVector q = new StateVector();
        double[] s = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, s);
        world.nav.target.stateAt(world.universe.entity(GameScenario.EARTH), world.universe.now(), q);
        return Math.hypot(s[0] - q.x, s[1] - q.y);
    }

    private double relativeSpeed() {
        StateVector q = new StateVector();
        double[] s = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, s);
        world.nav.target.stateAt(world.universe.entity(GameScenario.EARTH), world.universe.now(), q);
        return Math.hypot(s[2] - q.vx, s[3] - q.vy);
    }

    private void handOverTheHelm() {
        world.bridge.stand();
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "Bea takes the helm");
    }

    private void knockOutTheCrew() {
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e == game.avatar || e.getComponent(GridPositionComponent.class).ship != game.player) continue;
            CrewMemberComponent c = world.crew.crew(e);
            world.crew.applyDamage(e, c.health * 0.95f, CrewDamageSink.DamageCause.BLAST);
        }
    }
}
