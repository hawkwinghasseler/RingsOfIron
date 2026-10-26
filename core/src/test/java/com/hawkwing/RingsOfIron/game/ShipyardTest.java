package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.LoadReport;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipSaleDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Buying ships at a faction's yard, and what the rank ladder lets the player fly. */
class ShipyardTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    /** Anvil Yards, the Patrol's shipyard, where each test starts docked. */
    Entity yard;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        yard = world.engine.getEntity(game.shipyard);
        game.beginCareer();
        run(120);
        // From Highport over to the yards, docked there: the flying, done by hand
        assertTrue(world.docking.undock(game.player));
        run(60);
        assertTrue(game.dockAt(game.shipyard));
        run(120);
        assertSame(yard, world.docking.dockedTo(game.player), "docked at Anvil Yards");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private TraderComponent account() {
        return game.player.getComponent(TraderComponent.class);
    }

    /** Raises the player to rank {@code rank} with the sponsor. */
    private void promote(int rank) {
        float need = world.standing.ranks().get(rank).standing - world.standing.standing(world.standing.sponsor());
        FactionStandingTest.skipTimeInGrade(world.standing);
        if (need > 0) world.standing.reward(world.standing.sponsor(), need + 0.5f, "test");
        assertEquals(rank, world.standing.record().rank);
    }

    private String shipIdOf(Entity ship) {
        return ship.getComponent(DefRefComponent.class).defId;
    }

    private List<Entity> crewOf(Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) out.add(e);
        }
        return out;
    }

    /** Sells {@code shipId} at the Patrol's yards too, for a test: free and open to an ensign. */
    private void sellAtTheYard(String shipId) {
        world.getContent().defs.mergeFile(Map.of("ship_sale", Map.of("test_" + shipId,
                Map.of("ship", shipId, "seller", "patrol", "price", 1.0))), "test", new LoadReport());
    }

    @Test
    void anvilYardsSellsThePatrolsShipsAndHighportAndTheDepotNone() {
        assertSame(yard, world.shipyard.dockedStation());
        List<String> sold = new ArrayList<>();
        for (Shipyard.Listing l : world.shipyard.listings(yard)) sold.add(l.shipId);
        assertTrue(world.shipyard.listings(world.engine.getEntity(game.highport)).isEmpty(), "Highport only repairs and refits");
        assertEquals(List.of("corvette_patrol", "gunship_heavy", "frigate_patrol", "picket_patrol", "interceptor_patrol"), sold);
        assertTrue(world.shipyard.listings(world.engine.getEntity(game.depot)).isEmpty(), "the depot is no yard");
        // The Selene Combine sells its raider hull and its working hulls at its own station
        List<Shipyard.Listing> selene = world.shipyard.listings(world.engine.getEntity(game.lunarMine));
        List<String> seleneSold = new ArrayList<>();
        for (Shipyard.Listing l : selene) {
            seleneSold.add(l.shipId);
            assertEquals(10f, l.standing, l.shipId);
        }
        assertEquals(List.of("raider_light", "tug_salvage", "courier_selene", "surveyor_selene", "barge_ore"), seleneSold);
    }

    @Test
    void theShipyardWindowListsThemWithTheTradeInAndWhatsLocked() {
        com.ringsofiron.uihud.providers.ShipyardProvider.Yard yard = new com.ringsofiron.uihud.providers.ShipyardProvider.Yard();
        new ShipyardWork(world).collect(yard);
        assertEquals("Anvil Yards", yard.stationName);
        assertTrue(yard.current.startsWith("Your Patrol Corvette: trade-in "), yard.current);
        assertEquals(5, yard.ships.size);
        assertEquals("Gunship", yard.ships.get(1).name);
        assertEquals("Terran Patrol Authority", yard.ships.get(1).seller);
        assertTrue(yard.ships.get(1).terms.startsWith("320,000 cr, "), yard.ships.get(1).terms);
        assertEquals("Needs rank Lieutenant Commander", yard.ships.get(1).locked);
        assertEquals("", yard.prize, "no prize in tow");
        // Undocked, there is nothing to show
        world.docking.undock(game.player);
        run(60);
        yard.clear();
        new ShipyardWork(world).collect(yard);
        assertFalse(yard.isOpen());
    }

    @Test
    void theLadderTheSalesAndTheRequisitionAgree() {
        RankLadder ranks = world.standing.ranks();
        // The gunship comes with Lieutenant Commander, the frigate with Commander; freighters never
        assertEquals(ranks.indexOf("lieutenant_commander"), ranks.lowestToFly("gunship_heavy"));
        assertEquals(ranks.indexOf("commander"), ranks.lowestToFly("frigate_patrol"));
        assertEquals(0, ranks.lowestToFly("raider_light"), "the light raider is smaller than the corvette");
        assertEquals(-1, ranks.lowestToFly(GameScenario.FREIGHTER_SHIP), "no rank flies a freighter");
        // Every sponsor sale's rank is the first rank the ladder lets fly it
        for (ShipSaleDef sale : world.getContent().defs.all("ship_sale", ShipSaleDef.class)) {
            if (!"patrol".equals(sale.seller)) continue;
            int needs = sale.rank == null ? 0 : ranks.indexOf(sale.rank);
            assertEquals(ranks.lowestToFly(sale.ship), needs, sale.id);
        }
        // ... and a rescue's requisition keeps to the same limit
        assertEquals(GameScenario.PLAYER_SHIP, world.loop.bestRequisition());
        promote(2);
        assertEquals("gunship_heavy", world.loop.bestRequisition());
    }

    @Test
    void anEnsignIsRefusedTheGunshipAndCantAffordANewCorvette() {
        Shipyard.Listing gunship = world.shipyard.listing("patrol_gunship");
        assertEquals("Needs rank Lieutenant Commander", world.shipyard.refusal(gunship));
        Shipyard.Listing corvette = world.shipyard.listing("patrol_corvette");
        String no = world.shipyard.refusal(corvette);
        assertTrue(no.startsWith("Needs ") && no.endsWith(" cr after the trade-in"), no);
        assertEquals(no, world.shipyard.buy("patrol_corvette"), "refused, nothing changed");
        assertEquals(GameScenario.PLAYER_SHIP, shipIdOf(game.player));
    }

    @Test
    void theTradeInIsItsShareOfTheShipsValueTimesItsCondition() {
        double value = Difficulty.shipValue(game.player);
        assertEquals(1f, Shipyard.condition(game.player), 1e-3, "a new corvette is sound");
        assertEquals(Shipyard.TRADE_IN_SHARE * value, Shipyard.tradeIn(game.player), 1);
        // A holed ship is worth less
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        for (int x = 0; x < grid.width; x++) {
            var t = grid.getTile(x, 0);
            if (t != null && !t.isVoid()) t.integrity = 0.5f;
        }
        assertTrue(Shipyard.tradeIn(game.player) < Shipyard.TRADE_IN_SHARE * value);
    }

    @Test
    void aLieutenantCommanderBuysTheGunshipAndTakesCrewCargoAndCreditsAcross(@TempDir Path dir) {
        promote(2);
        Entity old = game.player;
        account().credits = 400_000;
        CargoHoldComponent oldHold = old.getComponent(CargoHoldComponent.class);
        oldHold.load("water", 5, 1, 1000);
        double before = Difficulty.of(world);
        Shipyard.Listing gunship = world.shipyard.listing("patrol_gunship");
        assertNull(world.shipyard.refusal(gunship));
        double cost = world.shipyard.cost(gunship, old);
        assertEquals(gunship.price - Shipyard.TRADE_IN_SHARE * Difficulty.shipValue(old), cost, 1);
        List<Shipyard.Deal> deals = new ArrayList<>();
        world.shipyard.deals.add((s, d) -> deals.add(d));
        int people = crewOf(old).size();
        List<Entity> oldSystems = new ArrayList<>();
        for (Entity sys : old.getComponent(ShipSubsystemRegistryComponent.class).subsystems) oldSystems.add(sys);

        String said = world.shipyard.buy("patrol_gunship");
        assertTrue(said.startsWith("Bought the Gunship at Anvil Yards"), said);
        assertEquals(1, deals.size());
        assertNotSame(old, game.player, "the player flies the new ship");
        assertSame(game.player, world.getPlayer());
        assertEquals("gunship_heavy", shipIdOf(game.player));
        assertNull(world.engine.getEntity(world.universe.uuidOf(old)), "the old ship went to the yard");
        for (Entity s : oldSystems) assertFalse(world.engine.getEcsEngine().getEntities().contains(s, true));

        // Credits, cargo and crew came across; the avatar sits at the new helm
        assertEquals(400_000 - cost, account().credits, 1e-6);
        assertEquals(5, game.player.getComponent(CargoHoldComponent.class).amount("water"), 1e-9);
        List<Entity> aboard = crewOf(game.player);
        assertEquals(people, aboard.size());
        for (Entity e : aboard) assertSame(game.player, e.getComponent(CrewMemberComponent.class).home);
        assertTrue(aboard.stream().anyMatch(e -> e.getComponent(PlayerAvatarComponent.class) != null));
        assertTrue(world.bridge.playerAtHelm(), "seated at the gunship's helm");
        assertEquals(0, game.player.getComponent(ShipTaskBoardComponent.class).faction, "the player's crew");

        // Latched at the yards in the old ship's place
        run(120);
        assertSame(yard, world.docking.dockedTo(game.player));
        assertTrue(world.docking.dockedWithClearance(game.player));
        // The bigger ship counts in net worth: with the credits back as they were, the difficulty is higher
        account().credits = 400_000;
        assertTrue(Difficulty.of(world) > before, Difficulty.of(world) + " against " + before);
        account().credits = 400_000 - cost;

        // It saves and loads as the player's ship
        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld loaded = new GameWorld();
        try {
            Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
            GameScenario again = GameScenario.start(loaded, assets, assets.resolve("no-mods-here"));
            again.quickLoad(file);
            for (int i = 0; i < 120; i++) loaded.update(FRAME);
            assertEquals("gunship_heavy", again.player.getComponent(DefRefComponent.class).defId);
            assertEquals(400_000 - cost, again.player.getComponent(TraderComponent.class).credits, 1e-6);
            assertEquals(yard.getComponent(com.ringsofiron.stationsdocking.StationComponent.class).name,
                    loaded.docking.dockedTo(again.player).getComponent(com.ringsofiron.stationsdocking.StationComponent.class).name);
        } finally {
            loaded.dispose();
        }
    }

    @Test
    void aYardTechnicianAboardAtTheSaleStaysWithTheYard() {
        promote(2);
        account().credits = 400_000;
        Entity old = game.player;
        Entity tech = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (c.role == com.ringsofiron.aicrew.CrewRole.TECHNICIAN && c.home == yard) tech = e;
        }
        assertNotNull(tech, "Anvil Yards has a technician");
        // Walking back from the yard's work, still aboard when the deal is struck
        int[] tile = GameScenario.floorTiles(old.getComponent(ShipGridComponent.class)).get(0);
        GridPositionComponent pos = tech.getComponent(GridPositionComponent.class);
        pos.setShip(old);
        pos.setTile(tile[0], tile[1]);
        int people = crewOf(old).size() - 1;

        String said = world.shipyard.buy("patrol_gunship");
        assertTrue(said.startsWith("Bought the Gunship"), said);
        assertSame(yard, tech.getComponent(CrewMemberComponent.class).home, "still the yard's");
        assertFalse(crewOf(game.player).contains(tech), "not carried off in the new ship");
        assertEquals(people, crewOf(game.player).size());
    }

    @Test
    void cargoThatWontFitAndMorePeopleThanSeatsAreRefused() {
        sellAtTheYard("raider_light");
        Shipyard.Listing raider = world.shipyard.listing("test_raider_light");
        account().credits = 1e6;
        assertNull(world.shipyard.refusal(raider));
        // The raider's two-tile hold takes 5 m3; the corvette's 40
        game.player.getComponent(CargoHoldComponent.class).load("water", 20, 1, 1000);
        String no = world.shipyard.refusal(raider);
        assertTrue(no.startsWith("Unload 15 m3 of cargo first"), no);
        game.player.getComponent(CargoHoldComponent.class).unload("water", 20);
        // A fifth hand aboard: the raider's one pod seats four
        Entity e = com.ringsofiron.aicrew.CrewFactory.spawn(world.crew, game.player, "Dee", com.ringsofiron.aicrew.CrewRole.CREWMAN, 0, 8, 2);
        assertNotNull(e);
        assertEquals("The Light Raider's pods seat 4; you have 5 aboard", world.shipyard.refusal(raider));
    }

    /** The light raider, the gunship and the frigate as the player's ship, checked as the corvette is. */
    @ParameterizedTest
    @ValueSource(strings = {"raider_light", "gunship_heavy", "frigate_patrol"})
    void flyableAsThePlayersShip(String shipId) {
        sellAtTheYard(shipId);
        promote(world.standing.ranks().size() - 1);
        String said = world.shipyard.buy("test_" + shipId);
        assertTrue(said.startsWith("Bought the"), said);
        Entity ship = game.player;
        run(120);
        // A helm the avatar sits at, with a helmsman to take over, and the weapons console
        assertNotNull(ship.getComponent(HelmComponent.class));
        assertTrue(world.bridge.playerAtHelm());
        assertNotNull(Bridge.console(ship, com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.WEAPONS));
        assertNull(world.bridge.helmFault(ship), world.bridge.helmFault(ship));
        // Escape pods for everyone aboard, and the crew are the player's
        int people = crewOf(ship).size();
        assertTrue(AbandonShip.seats(ship) >= people, AbandonShip.seats(ship) + " seats for " + people);
        assertEquals(0, ship.getComponent(ShipTaskBoardComponent.class).faction);
        // It flies: undocked, the engines push it
        assertTrue(world.docking.undock(ship));
        run(60);
        assertFalse(ship.getComponent(ThrusterComponent.class).isOutOfFuel());
        assertTrue(ship.getComponent(ThrusterComponent.class).forwardThrust > 0);
        // ... and the crew can abandon it
        assertNull(world.abandon.order(ship));
    }

    @Test
    void theFrigateIsTheBiggestShipARankFliesWithTwoMainGunsAndMissiles() {
        Entity frigate = game.content.factory.spawnShip("frigate_patrol", 3_000f, 3_000f, 0f);
        Entity gunship = game.content.factory.spawnShip("gunship_heavy", 3_000f, -3_000f, 0f);
        run(3);
        float fm = PhysicsManager.getMass(frigate.getComponent(PhysicsBodyComponent.class).body);
        float gm = PhysicsManager.getMass(gunship.getComponent(PhysicsBodyComponent.class).body);
        assertTrue(fm > gm, fm + " kg against the gunship's " + gm);
        assertTrue(Difficulty.shipValue(frigate) > Difficulty.shipValue(gunship));
        RankLadder ranks = world.standing.ranks();
        assertTrue(ranks.chassisSize("frigate") > ranks.chassisSize("gunship"));
        assertTrue(ranks.chassisSize("frigate") < ranks.chassisSize("freighter"));
    }
}
