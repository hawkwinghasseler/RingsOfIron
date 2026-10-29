package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Abandoning ship in the escape pods, and waking at a station in a requisitioned ship. */
class AbandonShipTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;
    final List<AbandonShip.Escape> launches = new ArrayList<>();
    final List<Rescue.Result> rescues = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.abandon.launches.add((signal, e) -> launches.add(e));
        world.rescue.rescues.add((signal, r) -> rescues.add(r));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** Runs until the rescue has happened, or {@code seconds} of game time. */
    private void runUntilRescued(float seconds) {
        for (int i = 0; i < seconds / FRAME && rescues.isEmpty(); i++) world.update(FRAME);
    }

    private List<Entity> crewAboard(GameWorld w, Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) out.add(e);
        }
        return out;
    }

    @Test
    void theCorvetteCarriesOneFourSeatPodAndTheFreighterTwo() {
        assertEquals(1, AbandonShip.usablePods(game.player).size());
        assertEquals(4, AbandonShip.seats(game.player));
        Entity freighter = game.content.factory.spawnShip(GameScenario.FREIGHTER_SHIP, 3_000f, 3_000f, 0f);
        assertEquals(2, AbandonShip.usablePods(freighter).size());
        assertEquals(8, AbandonShip.seats(freighter));
    }

    @Test
    void noAbandoningShipWhileDocked() {
        assertTrue(FirstPlay.dockAtHighport(world, game));
        run(60);
        assertNotNull(world.abandon.order(game.player));
        assertFalse(AbandonShip.abandoning(game.player));
    }

    @Test
    void theCrewMakeForThePodAndThePlayerWakesAtAStationInANewShip() {
        Entity oldShip = game.player;
        Entity avatar = game.avatar;
        game.player.getComponent(TraderComponent.class).credits = 1_234;
        int crew = crewAboard(world, oldShip).size();
        assertEquals(4, crew, "the player and three crew");
        double before = world.engine.getSimSeconds();

        assertNull(world.abandon.order(game.player));
        assertTrue(AbandonShip.abandoning(game.player));
        PlayerToPod.board(world, game.player);
        runUntilRescued(60f);

        assertEquals(1, launches.size(), "one pod");
        AbandonShip.Escape escape = launches.get(0);
        assertTrue(escape.player);
        assertEquals(3, escape.crew.size(), "everyone made it in");
        assertEquals(1, rescues.size());
        Rescue.Result r = rescues.get(0);

        // The old ship is a derelict
        assertNotSame(oldShip, game.player);
        assertNull(oldShip.getComponent(PlayerControllerComponent.class));
        assertEquals(DisabledComponent.Reason.ABANDONED, ShipDisablement.reason(oldShip));
        assertFalse(AbandonShip.usablePods(oldShip).iterator().hasNext(), "its pod is spent");

        // Days later, aboard a new ship with the same avatar and crew, and the credits
        assertSame(r.newShip, game.player);
        assertSame(game.player, world.getPlayer());
        assertEquals(Rescue.TIME_SKIP, r.skipped);
        assertTrue(world.engine.getSimSeconds() - before >= Rescue.TIME_SKIP);
        assertSame(avatar, game.avatar);
        assertSame(game.player, avatar.getComponent(GridPositionComponent.class).ship);
        assertEquals(4, crewAboard(world, game.player).size());
        assertEquals(0, r.lost);
        assertEquals(0, r.price, "the starter class is free");
        assertEquals(1_234, game.player.getComponent(TraderComponent.class).credits, 1e-9);
        assertNotNull(r.station.getComponent(StationComponent.class));

        // ... and docked at the station once it is in the bubble
        run(120);
        assertSame(r.station, world.docking.dockedTo(game.player), "docked at " + r.stationName);
        assertFalse(ShipDisablement.isDisabled(game.player));
    }

    @Test
    void thePlayerIsNeverWalkedToThePod() {
        // The figure is the player's alone: the crew go, and the player stays where they were left
        GridPositionComponent p = game.avatar.getComponent(GridPositionComponent.class);
        int x = p.tileX(), y = p.tileY();
        assertNull(world.abandon.order(game.player));
        for (int i = 0; i < AbandonShip.LAUNCH_AFTER / FRAME + 60 && launches.isEmpty(); i++) world.update(FRAME);
        assertEquals(1, launches.size(), "the pod went without the player");
        assertFalse(launches.get(0).player);
        assertSame(game.player, p.ship);
        assertEquals(x, p.tileX());
        assertEquals(y, p.tileY());
    }

    @Test
    void crewWhoAreDownAreLeftBehind() {
        Entity oldShip = game.player;
        Entity down = null;
        for (Entity e : crewAboard(world, oldShip)) {
            if (e.getComponent(PlayerAvatarComponent.class) != null) continue;
            down = e;
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            world.crew.applyDamage(e, c.health * 0.9f, CrewDamageSink.DamageCause.SPALL);
            break;
        }
        assertNull(world.abandon.order(game.player));
        PlayerToPod.board(world, game.player);
        runUntilRescued(60f);
        assertEquals(1, rescues.size());
        Rescue.Result r = rescues.get(0);
        assertEquals(2, r.survivors.size());
        assertEquals(1, r.lost);
        assertSame(oldShip, down.getComponent(GridPositionComponent.class).ship, "still aboard the derelict");
        assertEquals(3, crewAboard(world, game.player).size());
    }

    @Test
    void thePodWaitsForTheCrewUnlessToldToGo() {
        assertNull(world.abandon.order(game.player));
        run(1);
        world.abandon.launchNow(game.player);
        run(2);
        assertTrue(launches.isEmpty(), "nobody in the pod yet");
        assertNotNull(world.abandon.order(game.player), "ordered twice");
        world.abandon.callOff(game.player);
        assertFalse(AbandonShip.abandoning(game.player));
        run(600);
        assertTrue(launches.isEmpty());
        assertSame(game.player, world.getPlayer());
    }

    @Test
    void anOrderInHandIsSaved(@TempDir Path dir) {
        assertNull(world.abandon.order(game.player));
        PlayerToPod.board(world, game.player);
        run(30);
        FileHandle file = new FileHandle(dir.resolve("abandon.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertTrue(AbandonShip.abandoning(loaded.player));
            List<Rescue.Result> done = new ArrayList<>();
            fresh.rescue.rescues.add((signal, r) -> done.add(r));
            for (int i = 0; i < 60 / FRAME && done.isEmpty(); i++) fresh.update(FRAME);
            assertEquals(1, done.size(), "the crew carry on to the pod after the load");
            assertSame(done.get(0).newShip, loaded.player);
        } finally {
            fresh.dispose();
        }
    }
}
