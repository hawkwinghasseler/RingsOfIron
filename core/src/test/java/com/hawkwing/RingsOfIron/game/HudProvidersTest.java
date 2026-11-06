package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.shipops.ShipNames;
import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider;
import com.ringsofiron.uihud.providers.FlightStatusProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.OrdersProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.shipops.AbandonShip;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The shared providers every HUD variant reads for flight, navigation, docking, contacts,
 * objectives, the posts aboard, the key strip and the orders, on a real career world, headless: the
 * career starts docked at a station, which most of them have something to say about.
 */
class HudProvidersTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HudData data;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        for (int i = 0; i < 120; i++) world.update(FRAME);
        data = new HudData();
        GameHud.wireFlightAndPosts(data, world);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Entity dock() {
        Entity dock = world.docking.dockedTo(game.player);
        assertNotNull(dock, "the career starts docked");
        return dock;
    }

    @Test
    void docked_theFlightFrameIsTheStationAndTheShipIsStill() {
        data.refresh();
        FlightStatusProvider.FlightStatus f = data.flightStatus;
        assertTrue(f.present);
        assertEquals(FlightStatusProvider.Frame.STATION, f.frame);
        assertEquals(ShipNames.name(dock()), f.frameName);
        assertTrue(f.speed < 1f, "docked, so still against the station: " + f.speed);
        assertTrue(f.frameDistance < FlightStatusProvider.FlightStatus.STATION_FRAME_RANGE);
        assertTrue(f.mass > 0f && f.acceleration > 0f, f.mass + " kg, " + f.acceleration + " m/s2");
        assertTrue(f.deltaVLeft > 0, "propellant aboard: " + f.deltaVLeft);
        assertEquals(0, f.deltaVHome, 1e-6, "docked at a station that takes the ship");
        assertTrue(f.heading >= 0f && f.heading < 360f, "" + f.heading);
        assertFalse(f.mainEngine, "hands off the stick");
    }

    @Test
    void docked_theStationSaysWhoRunsItAndWhatItOffers() {
        data.refresh();
        NavigationProvider.StationInfo s = data.navReadout.docked;
        assertTrue(s.present);
        assertEquals(ShipNames.name(dock()), s.name);
        assertFalse(s.owner.isEmpty());
        assertFalse(s.standingName.isEmpty());
        assertEquals("", s.refusal, "the career starts with clearance");
        assertTrue(s.market && s.yard && s.contracts && s.hiring && s.infirmary, "a trading station of a friendly faction");
    }

    @Test
    void aStationNavTargetReadsItsDistanceAndWhatItOffers() {
        String home = world.universe.uuidOf(dock());
        String other = null;
        for (EconomyNode s : world.macroSim.stations()) {
            if (!s.stationUuid.equals(home) && !world.pirateBase.hidden(s.stationUuid)) other = s.stationUuid;
        }
        assertNotNull(other, "a second station");
        world.nav.target.select(other);
        data.refresh();
        NavigationProvider.NavReadout n = data.navReadout;
        assertTrue(n.present);
        assertEquals(NavigationProvider.Kind.STATION, n.kind);
        assertEquals(world.nav.target.name(), n.name);
        assertTrue(n.station.present);
        assertTrue(n.distance > 10_000, "another station is far off: " + n.distance);
        assertFalse(Float.isNaN(n.closing));
        assertFalse(n.inBubble);
    }

    @Test
    void plannedBurnsCountDownAndPointTheWay() {
        ManeuverNodeComponent plan = game.player.getComponent(ManeuverNodeComponent.class);
        if (plan == null) game.player.add(plan = new ManeuverNodeComponent());
        double now = world.universe.now();
        plan.add(now + 3_600, 40, 0).add(now + 7_200, 0, 25);
        data.refresh();
        NavigationProvider.NavReadout n = data.navReadout;
        assertEquals(2, n.burnsLeft);
        assertEquals(1, n.burnNumber);
        assertEquals(2, n.burnsPlanned);
        assertEquals(3_600, n.burnIn, 1);
        assertEquals(40, n.burnDeltaV, 1e-3);
        assertTrue(n.burnStartIn < n.burnIn && n.turnIn < n.burnStartIn, n.turnIn + " < " + n.burnStartIn + " < " + n.burnIn);
        assertTrue(n.burnTime > 0f);
        assertFalse(n.burning);
        assertTrue(Float.isNaN(n.burnToGo));
        assertEquals(65, n.planDeltaV, 1e-3);
        assertTrue(n.burnHeadingDeg >= 0f && n.burnHeadingDeg < 360f, "" + n.burnHeadingDeg);
        assertTrue(n.pointingDeg > -180f && n.pointingDeg <= 180f, "" + n.pointingDeg);
    }

    @Test
    void aBurnLeavingThePlanWhenDueCountsAsFlown() {
        BurnCount count = new BurnCount();
        ManeuverNodeComponent plan = new ManeuverNodeComponent().add(100, 10, 0).add(5_000, 10, 0).add(9_000, 10, 0);
        count.update(plan, false, 0, n -> 120);
        assertEquals(0, count.flown());
        // The first is due within its lead: the helm takes it off the list as it lights the engine
        plan.nodes.removeIndex(0);
        count.update(plan, true, 50, n -> 120);
        assertEquals(1, count.flown());
        // The last is deleted long before its time: not flown
        plan.nodes.removeIndex(1);
        count.update(plan, false, 60, n -> 120);
        assertEquals(1, count.flown());
        // The plan done, the count starts again
        plan.nodes.clear();
        count.update(plan, false, 5_000, n -> 120);
        assertEquals(0, count.flown());
        // Watching from part-way through a burn: it was in the plan
        count.update(plan, true, 5_001, n -> 120);
        assertEquals(1, count.flown());
    }

    @Test
    void docked_thePortReadsDockedAtTheStation() {
        data.refresh();
        DockingApproachProvider.DockingApproach d = data.docking;
        assertTrue(d.present);
        assertTrue(d.port >= 1 && d.port <= d.ports);
        assertFalse(d.portSize.isEmpty());
        assertEquals(DockingApproachProvider.Capture.DOCKED, d.capture);
        assertEquals(ShipNames.name(dock()), d.other);
        assertTrue(d.otherPort >= 1);
        assertTrue(d.strain >= 0f, "" + d.strain);
    }

    @Test
    void contactsAreNearestFirstWithTheStationAmongThem() {
        data.refresh();
        ContactProvider.ContactFrame c = data.contactFrame;
        assertFalse(c.contacts.isEmpty());
        ContactProvider.Contact station = null;
        float last = -1f;
        for (ContactProvider.Contact x : c.contacts) {
            assertTrue(x.range >= last, "nearest first");
            last = x.range;
            assertTrue(x.entity != game.player, "not the ship itself");
            if (x.entity == dock()) station = x;
        }
        assertNotNull(station, "the station docked at");
        assertEquals(ContactProvider.Kind.STATION, station.kind);
        assertFalse(Float.isNaN(station.bearingDeg));
    }

    @Test
    void aContractTakenIsAnObjectiveWithItsDeadline() {
        List<Contract> offers = world.contracts.offers(world.universe.uuidOf(dock()));
        assertFalse(offers.isEmpty(), "the station has work");
        String said = world.contracts.accept(offers.get(0).id);
        data.refresh();
        assertEquals(1, data.objectiveFrame.objectives.size, said);
        com.ringsofiron.uihud.providers.ObjectiveProvider.Objective o = data.objectiveFrame.first();
        assertFalse(o.title.isEmpty());
        assertFalse(o.kind.isEmpty());
        assertTrue(o.timeLeft > 0 && !o.late, "" + o.timeLeft);
        assertTrue(o.reward > 0);
    }

    @Test
    void thePostsAboardAreNamedAndTheHelmIsOne() {
        data.refresh();
        StationBoardProvider.StationBoard b = data.stations;
        StationBoardProvider.Seat helm = b.at(StationBoardProvider.Post.HELM);
        assertNotNull(helm);
        assertEquals("HELM", helm.name);
        assertTrue(helm.working);
        Set<String> names = new HashSet<>();
        for (StationBoardProvider.Seat s : b.seats) {
            assertTrue(names.add(s.name), "each post its own name: " + s.name);
            if (s.post == StationBoardProvider.Post.GUN) assertTrue(s.name.contains("GUN"), s.name);
        }
        assertTrue(b.crewFree <= b.crewAboard);
    }

    @Test
    void theKeyStripListsEachActionOnceThenHelp() {
        boolean[] placing = {false};
        data.hints = new PlayerKeyHints(world.hints, () -> EnumSet.of(HintRegistry.Context.ANY, HintRegistry.Context.DECK),
                Action::defaultKeyName, () -> placing[0] ? "Click to place" : null);
        data.refresh();
        KeyHintProvider.KeyHintFrame k = data.keyHints;
        assertTrue(k.hints.size > HintRegistry.LINE_HINTS, "not cut to the bottom line's few: " + k.hints.size);
        Set<String> actions = new HashSet<>();
        for (int i = 0; i < k.hints.size - 1; i++) {
            KeyHintProvider.KeyHint h = k.hints.get(i);
            assertFalse(h.label.isEmpty());
            assertFalse(h.key.isEmpty());
            if (!h.action.isEmpty()) assertTrue(actions.add(h.action), "once: " + h.action);
        }
        assertEquals("controls", k.hints.peek().label);
        assertEquals(Action.HELP.name(), k.hints.peek().action);
        assertEquals("walk", k.hints.first().label, "most useful first");
        placing[0] = true;
        data.refresh();
        assertTrue(k.placing);
        assertEquals("Click to place", k.placingText);
        assertEquals(0, k.hints.size);
    }

    @Test
    void theControlsPanelListsEveryHintByWhereItApplies() {
        data.hints = new PlayerKeyHints(world.hints, () -> EnumSet.of(HintRegistry.Context.ANY), Action::defaultKeyName, () -> null);
        KeyHintProvider.HelpFrame help = new KeyHintProvider.HelpFrame();
        data.hints.collectAll(help);
        // As the classic F1 panel has them: a section for each place, in the registry's order
        java.util.Map<HintRegistry.Context, List<HintRegistry.Hint>> byContext = world.hints.byContext();
        assertEquals(byContext.size(), help.sections.size);
        int i = 0;
        for (java.util.Map.Entry<HintRegistry.Context, List<HintRegistry.Hint>> e : byContext.entrySet()) {
            KeyHintProvider.HelpSection s = help.sections.get(i++);
            assertEquals(e.getKey().title, s.title);
            assertEquals(e.getValue().size(), s.hints.size, s.title);
            for (KeyHintProvider.KeyHint h : s.hints) assertFalse(h.label.isEmpty(), s.title);
        }
        assertTrue(help.sections.size > 1, "more than the one place");
        // Asked again, it is the same list, not twice as long
        help.clear();
        data.hints.collectAll(help);
        assertEquals(byContext.size(), help.sections.size);
    }

    // --- Orders -------------------------------------------------------------------------

    final boolean[] seated = {true}, paused = {false};

    private OrdersProvider.Orders orders() {
        if (!(data.orders instanceof PlayerOrders)) {
            data.orders = new PlayerOrders(world.context, world.nav, world.abandon, world.beacon, () -> seated[0], () -> paused[0],
                    () -> 2.5, () -> 0);
        }
        data.refresh();
        return data.orderState;
    }

    /** Another station than the one docked at, as the nav target. */
    private String targetAnotherStation() {
        String home = world.universe.uuidOf(dock());
        for (EconomyNode s : world.macroSim.stations()) {
            if (!s.stationUuid.equals(home) && !world.pirateBase.hidden(s.stationUuid)) {
                world.nav.target.select(s.stationUuid);
                return s.stationUuid;
            }
        }
        throw new AssertionError("a second station");
    }

    @Test
    void eachHelmOrderIsRefusedExactlyWhenTheHelmWouldRefuseIt() {
        // With no target, then with a far station targeted: the reasons the menu shows are the helm's own
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) targetAnotherStation();
            for (OrdersProvider.HelmOrder h : OrdersProvider.HelmOrder.values()) {
                if (h == OrdersProvider.HelmOrder.NONE) continue;
                String shown = orders().helmRefusal[h.ordinal()];
                HelmOrderComponent.Mode mode = HelmOrderComponent.Mode.valueOf(h.name());
                String said = world.nav.helm.give(mode);
                boolean given = world.nav.helm.mode() == mode;
                if (given) assertEquals("", shown, h + ": the helm took it (" + said + ")");
                else assertEquals(said, shown, h + ", pass " + pass);
                world.nav.helm.give(HelmOrderComponent.Mode.NONE);
            }
        }
        assertEquals("", orders().helmRefusal[OrdersProvider.HelmOrder.NONE.ordinal()], "cancel always can");
    }

    @Test
    void aHelmOrderGivenStandsWithItsTargetAndLine() {
        targetAnotherStation();
        world.nav.helm.give(HelmOrderComponent.Mode.MATCH_VELOCITY);
        OrdersProvider.Orders o = orders();
        assertEquals(OrdersProvider.HelmOrder.MATCH_VELOCITY, o.helm);
        assertEquals(world.nav.target.name(), o.helmTarget);
        assertFalse(o.helmLine.isEmpty());
        assertTrue(Float.isNaN(o.approachDistance), "not an approach");
    }

    @Test
    void onDeckTheSeatedOrdersSaySoAndTheRestStillRead() {
        seated[0] = false;
        paused[0] = true;
        OrdersProvider.Orders o = orders();
        assertFalse(o.seated);
        for (String r : o.helmRefusal) assertTrue(r.startsWith("Take a seat"), r);
        assertTrue(o.autopilotRefusal.startsWith("Take a seat"), o.autopilotRefusal);
        assertTrue(o.paused);
        assertEquals(2.5f, o.abandonArmed, 1e-6);
        assertEquals(0f, o.towArmed);
    }

    @Test
    void docked_theAutopilotTheTowAndThePodsSayWhyNot() {
        targetAnotherStation();
        OrdersProvider.Orders o = orders();
        assertEquals(OrdersProvider.Autopilot.OFF, o.autopilot);
        assertTrue(o.autopilotRefusal.startsWith("Undock first"), o.autopilotRefusal);
        assertEquals(world.beacon.refusal(), o.towRefusal);
        assertTrue(o.towFee > 0 && o.towStanding == DistressBeacon.STANDING_COST, o.towFee + " cr");
        // Each way out's full cost: the tow's days and where it docks the ship; abandoning's standing
        // and the ship given free there
        assertEquals(3f, o.towDays, 1e-6);
        assertEquals("Highport", o.towStation);
        assertEquals(LoopWiring.RESCUE_COST, o.abandonStanding, 1e-6);
        assertEquals("Patrol Corvette", o.abandonShip);
        assertEquals(world.abandon.refusal(game.player), o.abandonRefusal);
        assertFalse(o.abandoning);
        assertTrue(Float.isNaN(o.podsLaunchIn));
        assertTrue(o.crewAboard > 0 && o.inPods <= o.crewAboard, o.inPods + " of " + o.crewAboard);
        assertTrue(o.pods > 0 && o.freeSeats > 0, o.pods + " pods, " + o.freeSeats + " seats");
        assertTrue(o.podDistance >= 0f && o.podTileX >= 0 && o.podTileY >= 0, "the captain is aboard: " + o.podDistance);
        assertFalse(o.captainInPod);
    }

    @Test
    void underwayTheCrewToThePodsCountsDown() {
        assertTrue(world.docking.undock(game.player));
        for (int i = 0; i < 300 && world.docking.dockedTo(game.player) != null; i++) world.update(FRAME);
        assertEquals(null, world.docking.dockedTo(game.player), "cast off");
        assertEquals("", orders().abandonRefusal, "underway, with pods and crew");
        assertEquals(null, world.abandon.order(game.player));
        OrdersProvider.Orders o = orders();
        assertTrue(o.abandoning);
        assertEquals(AbandonShip.LAUNCH_AFTER, o.podsLaunchIn, 0.5);
        assertEquals("", o.abandonRefusal, "the key now launches the pods");
    }

    @Test
    void theCrewsStandingOrdersAndAHeldAutopilotRead() {
        ShipTaskBoardComponent board = game.player.getComponent(ShipTaskBoardComponent.class);
        board.evaHeld = true;
        board.suitsAtBattleStations = false;
        String to = targetAnotherStation();
        AutopilotComponent a = new AutopilotComponent();
        a.targetUuid = to;
        a.held = true;
        a.holdReason = "a raider closing";
        game.player.add(a);
        OrdersProvider.Orders o = orders();
        assertTrue(o.evaHeld);
        assertFalse(o.suitsAtBattleStations);
        assertEquals(board.battleStations, o.battleStations);
        assertEquals(OrdersProvider.Autopilot.HELD, o.autopilot);
        assertEquals(world.nav.target.name(to), o.autopilotTo);
        assertEquals("a raider closing", o.autopilotHeld);
        assertEquals("", o.autopilotRefusal, "on: the key goes on or stops it");
    }

    @Test
    void aHullHitAgesFromWhenItStruck() {
        assertTrue(Float.isNaN(data.lastHullHit.age), "none yet");
        data.age(1f);
        assertTrue(Float.isNaN(data.lastHullHit.age));
        data.hullHit(3, 4);
        data.age(0.25f);
        data.age(0.25f);
        assertEquals(3, data.lastHullHit.tileX);
        assertEquals(4, data.lastHullHit.tileY);
        assertEquals(0.5f, data.lastHullHit.age, 1e-6);
    }
}
