package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.career.Contract;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.SalvagedComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A taken ship towed home on the dock's weld, and sold or turned in at a station. */
class PrizeTowingTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    Entity highport;
    final List<Entity> raiderCrew = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        highport = world.engine.getEntity(game.highport);
        // Two pirates crew the raider
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            raiderCrew.add(CrewFactory.spawn(world.crew, game.raider, "Pirate " + i, CrewRole.CREWMAN, pirates, t[0], t[1]));
        }
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

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

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private int ownCrewAboard(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship && world.crew.homeOf(e) == game.player) n++;
        }
        return n;
    }

    /** The raider surrenders, is latched to, boarded and taken, and the boarding party comes home. */
    private void takeTheRaider() {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(120);
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        assertTrue(runUntil(240, () -> world.boarding.takenBy(game.raider, game.player)), "taken");
        assertTrue(runUntil(120, () -> ownCrewAboard(game.raider) == 0), "the party came home");
        assertTrue(world.docking.isRigid(game.player), "still latched, welded");
    }

    /** Moves the latched pair (as one) to {@code meters} off Highport, at rest beside it. */
    private void towTo(float meters) {
        Body station = world.docking.bodyOf(highport);
        Vector2 at = PhysicsManager.getPosition(station, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(station, new Vector2());
        Vector2 own = PhysicsManager.getPosition(body(game.player), new Vector2());
        Vector2 prize = PhysicsManager.getPosition(body(game.raider), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x + meters, at.y, body(game.player).getAngle());
        PhysicsManager.setPosition(body(game.raider), at.x + meters + prize.x - own.x, at.y + prize.y - own.y,
                body(game.raider).getAngle());
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
        PhysicsManager.setVelocity(body(game.raider), v.x, v.y);
        run(2);
    }

    /** Full thrust for ten seconds; returns the centre-of-mass speed gained by {@code ships}. */
    private float thrustTenSeconds(Entity... ships) {
        float mass = 0f;
        Vector2 before = new Vector2(), after = new Vector2();
        for (Entity e : ships) {
            float m = PhysicsManager.getMass(body(e));
            mass += m;
            before.mulAdd(PhysicsManager.getVelocity(body(e), new Vector2()), m);
        }
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 600; i++) {
            controls.forward = 1f;
            world.update(FRAME);
        }
        controls.forward = 0f;
        for (Entity e : ships) after.mulAdd(PhysicsManager.getVelocity(body(e), new Vector2()), PhysicsManager.getMass(body(e)));
        return after.sub(before).len() / mass;
    }

    @Test
    void aTakenShipLatchedOnIsThePrizeAndTowsAsOneBody() {
        assertNull(world.shipyard.prize(), "nothing taken yet");
        // What the engines give the ship alone
        float alone = thrustTenSeconds(game.player);
        assertTrue(alone > 0.5f, "it moves: " + alone + " m/s");
        Vector2 rv = PhysicsManager.getVelocity(body(game.raider), new Vector2());
        PhysicsManager.setVelocity(body(game.player), rv.x, rv.y);

        takeTheRaider();
        assertSame(game.raider, world.shipyard.prize());
        assertEquals("Bring it within 2 km of a station", world.shipyard.prizeRefusal(false));

        // Full thrust again: the weld holds, and the pair gains what the combined mass allows
        float m1 = PhysicsManager.getMass(body(game.player)), m2 = PhysicsManager.getMass(body(game.raider));
        Vector2 p0 = PhysicsManager.getVelocity(body(game.raider), new Vector2()).cpy();
        float towed = thrustTenSeconds(game.player, game.raider);
        assertSame(game.raider, world.docking.dockedTo(game.player), "the weld held: strain " + world.docking.strain(game.player));
        float expected = alone * m1 / (m1 + m2);
        assertEquals(expected, towed, 0.25f * expected, "alone " + alone + " m/s; " + m1 + " + " + m2 + " kg");
        assertTrue(PhysicsManager.getVelocity(body(game.raider), new Vector2()).dst(p0) > 0.5f * expected, "the prize comes along");

        // Into time warp and out: the prize is still in tow
        assertTrue(world.timeWarp.setWarp(1_000), String.valueOf(world.engine.getRailsWarpBlockedReason()));
        run(60);
        assertTrue(world.timeWarp.setWarp(1));
        run(120);
        assertSame(game.raider, world.docking.dockedTo(game.player), "still latched after warp");
        assertNotNull(game.raider.getComponent(PhysicsBodyComponent.class));
        assertSame(game.raider, world.shipyard.prize());
    }

    @Test
    void aPrizeBroughtToAStationIsSoldForItsPrizeShareWithItsPrisoners() {
        takeTheRaider();
        towTo(800f);
        assertSame(highport, world.shipyard.prizeStation());
        assertNull(world.shipyard.prizeRefusal(false));
        TraderComponent account = game.player.getComponent(TraderComponent.class);
        double before = account.credits;
        double value = Difficulty.shipValue(game.raider);
        String uuid = world.universe.uuidOf(game.raider);
        Entity raider = game.raider;

        String said = world.shipyard.sellPrize();
        assertTrue(said.startsWith("Highport's tugs took the"), said);
        assertTrue(said.endsWith("2 prisoners handed over"), said);
        // Pirate prisoners handed over at a sponsor station pay as they would brought aboard
        assertEquals(before + Math.round(Shipyard.PRIZE_SHARE * value) + 2 * Prisoners.HANDOVER_PAY, account.credits, 1e-6);
        assertNull(world.engine.getEntity(uuid), "the station took it");
        for (Entity e : raiderCrew) assertFalse(world.engine.getEcsEngine().getEntities().contains(e, true), "prisoners handed over");
        assertNull(world.docking.dockedTo(game.player));
        assertFalse(game.contacts.contains(raider, true));
        assertNull(world.shipyard.prize());
        run(120);
    }

    @Test
    void aStrippedPrizeSellsForLess() {
        takeTheRaider();
        SalvagedComponent stripped = new SalvagedComponent();
        stripped.add(1, 1);
        game.raider.add(stripped);
        assertEquals(Math.round(Shipyard.STRIPPED_PRIZE_SHARE * Difficulty.shipValue(game.raider)), Shipyard.prizeValue(game.raider), 1e-6);
    }

    @Test
    void turnedInAtTheSponsorsStationAPrizeGivesItsBountysStanding() {
        // Take the bounty on the raider at Highport first
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) bounty = c;
        }
        assertNotNull(bounty);
        assertNull(world.contracts.accept(bounty.id));
        takeTheRaider();
        towTo(800f);
        float before = world.standing.standing(world.standing.sponsor());
        float expected = world.shipyard.prizeStanding(game.raider);
        assertEquals(bounty.standing, expected);
        double credits = game.player.getComponent(TraderComponent.class).credits;
        int held = world.prisoners.aboard(game.raider).size();

        String said = world.shipyard.turnInPrize();
        assertTrue(said.startsWith("Turned the"), said);
        assertEquals(before + expected + held * Prisoners.HANDOVER_STANDING, world.standing.standing(world.standing.sponsor()), 1e-3);
        assertEquals(credits + held * Prisoners.HANDOVER_PAY, game.player.getComponent(TraderComponent.class).credits, 1e-6,
                "standing for the ship; only its prisoners pay");
        assertNull(world.shipyard.prize());
    }

    @Test
    void crewStillAboardThePrizeHoldTheHandOver() {
        takeTheRaider();
        towTo(800f);
        Entity hand = CrewFactory.spawn(world.crew, game.player, "Dee", CrewRole.CREWMAN, 0, 8, 2);
        world.crew.position(hand).setShip(game.raider);
        int[] t = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class)).get(0);
        world.crew.position(hand).setTile(t[0], t[1]);
        assertEquals("1 of your crew are still aboard: recall them first", world.shipyard.prizeRefusal(false));
        assertEquals("1 of your crew are still aboard: recall them first", world.shipyard.sellPrize());
        assertSame(game.raider, world.shipyard.prize(), "nothing handed over");
    }
}
