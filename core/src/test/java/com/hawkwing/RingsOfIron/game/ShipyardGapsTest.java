package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shipyard's refusals the other tests leave out, and a prize in tow through a save. */
class ShipyardGapsTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;
    Entity highport;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        highport = world.engine.getEntity(game.highport);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private static void run(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(FRAME);
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

    /** The first Small port of {@code station}. */
    private static int smallPort(Entity station) {
        DockingPortsComponent ports = station.getComponent(DockingPortsComponent.class);
        for (int i = 0; i < ports.ports.length; i++) if (ports.ports[i].size == MountFootprint.MountSize.S) return i;
        return -1;
    }

    private int ownCrewAboard(Entity ship) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship && world.crew.homeOf(e) == game.player) n++;
        }
        return n;
    }

    /** As in {@code PrizeTowingTest}: the raider, crewed by two pirates, surrenders and is taken while latched. */
    private void takeTheRaider() {
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        java.util.List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            CrewFactory.spawn(world.crew, game.raider, "Pirate " + i, CrewRole.CREWMAN, pirates, t[0], t[1]);
        }
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

    /** Moves the latched pair (as one) to {@code meters} off {@code station}, at rest beside it. */
    private void towTo(Entity station, float meters) {
        Body at = world.docking.bodyOf(station);
        assertNotNull(at, "the station is in the bubble");
        Vector2 p = PhysicsManager.getPosition(at, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(at, new Vector2());
        Vector2 own = PhysicsManager.getPosition(body(game.player), new Vector2());
        Vector2 prize = PhysicsManager.getPosition(body(game.raider), new Vector2());
        PhysicsManager.setPosition(body(game.player), p.x + meters, p.y, body(game.player).getAngle());
        PhysicsManager.setPosition(body(game.raider), p.x + meters + prize.x - own.x, p.y + prize.y - own.y,
                body(game.raider).getAngle());
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
        PhysicsManager.setVelocity(body(game.raider), v.x, v.y);
        run(2);
    }

    @Test
    void theLightRaiderIsRefusedBelowTenStandingWithSelene() {
        game.beginCareer();
        run(120);
        world.docking.undock(game.player);
        run(60);
        Entity mine = world.engine.getEntity(game.lunarMine);
        world.docking.dockInPlace(game.player, 0, mine, smallPort(mine));
        run(5);
        assertSame(mine, world.shipyard.dockedStation(), "docked at Shackleton Ice with clearance");
        game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class).credits = 1e6;
        Shipyard.Listing raider = world.shipyard.listing("selene_raider");
        assertNotNull(raider);
        assertEquals("Needs standing 10 with the Selene Mining Combine", world.shipyard.refusal(raider));
        assertEquals("Needs standing 10 with the Selene Mining Combine", world.shipyard.buy("selene_raider"), "refused");
        assertEquals(GameScenario.PLAYER_SHIP, game.player.getComponent(
                com.ringsofiron.moddingdata.components.DefRefComponent.class).defId, "nothing changed");
        // At ten, the sale goes through the standing check
        world.standing.reward("selene", 10f, "test");
        assertNotEquals("Needs standing 10 with the Selene Mining Combine", world.shipyard.refusal(raider));
    }

    @Test
    void aPrizeIsTurnedInOnlyAtTheSponsorsStations() {
        takeTheRaider();
        towTo(highport, 800f);
        // Serving Selene, the Patrol's Highport is no station of the sponsor's (Selene's mine is out of the bubble)
        world.standing.record().sponsor = "selene";
        assertSame(highport, world.shipyard.prizeStation());
        String no = "Only the Selene Mining Combine's stations take prizes in for standing";
        assertEquals(no, world.shipyard.prizeRefusal(true));
        float before = world.standing.standing("selene");
        assertEquals(no, world.shipyard.turnInPrize());
        assertSame(game.raider, world.shipyard.prize(), "nothing handed over");
        assertEquals(before, world.standing.standing("selene"), 1e-6);
        // It can still be sold there
        assertNull(world.shipyard.prizeRefusal(false));
    }

    @Test
    void aPrizeInTowSurvivesASaveAndLoad(@TempDir Path dir) {
        takeTheRaider();
        run(5);
        String uuid = world.universe.uuidOf(game.raider);
        assertSame(body(game.raider), game.player.getComponent(FlightControlComponent.class).towing);

        FileHandle file = new FileHandle(dir.resolve("save.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld loaded = new GameWorld();
        try {
            GameScenario again = GameScenario.start(loaded, assets, assets.resolve("no-mods-here"));
            again.quickLoad(file);
            run(loaded, 120);
            Entity prize = loaded.engine.getEntity(uuid);
            assertNotNull(prize, "the prize is in the loaded world");
            assertSame(prize, loaded.docking.dockedTo(again.player), "still latched");
            assertTrue(loaded.docking.isRigid(again.player), "welded");
            assertTrue(loaded.boarding.takenBy(prize, again.player), "still the player's");
            assertSame(prize, loaded.shipyard.prize());
            assertSame(body(prize), again.player.getComponent(FlightControlComponent.class).towing, "flies as one body");
        } finally {
            loaded.dispose();
        }
    }
}
