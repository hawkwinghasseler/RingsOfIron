package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rescue at its edges: a requisition that costs credits, one the player can't afford, waking only
 * at a sponsor's station, and a rescued game saved and loaded.
 */
class RescueEdgesTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;
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
        world.rescue.rescues.add((signal, r) -> rescues.add(r));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    /** A requisition of the freighter at {@code price}. */
    private static Rescue.Requisition freighterAt(double price) {
        return new Rescue.Requisition() {
            @Override
            public String ship(Entity station, double credits) {
                return GameScenario.FREIGHTER_SHIP;
            }

            @Override
            public double price(String shipId) {
                return GameScenario.FREIGHTER_SHIP.equals(shipId) ? price : 0;
            }
        };
    }

    private Rescue.Result abandonAndWait() {
        assertNull(world.abandon.order(game.player));
        PlayerToPod.board(world, game.player);
        for (int i = 0; i < 60 / FRAME && rescues.isEmpty(); i++) world.update(FRAME);
        assertEquals(1, rescues.size(), "rescued");
        return rescues.get(0);
    }

    private static String shipIdOf(Entity ship) {
        DefRefComponent ref = ship.getComponent(DefRefComponent.class);
        return ref == null ? null : ref.defId;
    }

    @Test
    void aRequisitionThePlayerCanAffordIsPaidFor() {
        game.player.getComponent(TraderComponent.class).credits = 50_000;
        world.rescue.requisition = freighterAt(20_000);
        Rescue.Result r = abandonAndWait();
        assertEquals(20_000, r.price, 1e-9);
        assertEquals(GameScenario.FREIGHTER_SHIP, shipIdOf(r.newShip));
        assertEquals(30_000, r.newShip.getComponent(TraderComponent.class).credits, 1e-9, "paid from the credits");
        assertEquals(0, r.oldShip.getComponent(TraderComponent.class).credits, 1e-9, "nothing left on the derelict");
    }

    @Test
    void oneThePlayerCantAffordFallsBackToTheFreeStarter() {
        game.player.getComponent(TraderComponent.class).credits = 5_000;
        world.rescue.requisition = freighterAt(20_000);
        Rescue.Result r = abandonAndWait();
        assertEquals(0, r.price, 1e-9);
        assertEquals(GameScenario.PLAYER_SHIP, shipIdOf(r.newShip));
        assertEquals(5_000, r.newShip.getComponent(TraderComponent.class).credits, 1e-9);
    }

    @Test
    void thePlayerWakesOnlyAtAStationTheSponsorOwns() {
        Entity highport = world.engine.getEntity(game.highport);
        world.rescue.sponsorStation = station -> station == highport;
        Rescue.Result r = abandonAndWait();
        assertSame(highport, r.station);
        assertEquals("Highport", r.stationName);
    }

    @Test
    void aRescuedGameSavesWithTheNewShipAsThePlayers(@TempDir Path dir) {
        game.player.getComponent(TraderComponent.class).credits = 777;
        Rescue.Result r = abandonAndWait();
        for (int i = 0; i < 120; i++) world.update(FRAME);
        String oldId = r.oldShip.getComponent(UuidComponent.class).id;
        String newId = r.newShip.getComponent(UuidComponent.class).id;
        FileHandle file = new FileHandle(dir.resolve("rescued.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(newId, loaded.player.getComponent(UuidComponent.class).id, "the new ship is the player's");
            assertSame(loaded.player, fresh.getPlayer());
            assertEquals(777, loaded.player.getComponent(TraderComponent.class).credits, 1e-9);
            Entity derelict = fresh.engine.getEntity(oldId);
            assertNotNull(derelict, "the old ship is still out there");
            assertNull(derelict.getComponent(PlayerControllerComponent.class));
            assertEquals(DisabledComponent.Reason.ABANDONED, ShipDisablement.reason(derelict));
            assertNotNull(derelict.getComponent(HudMarkerComponent.class));
            assertFalse(AbandonShip.abandoning(loaded.player));
            for (int i = 0; i < 120; i++) fresh.update(FRAME);
            assertEquals(DisabledComponent.Reason.ABANDONED, ShipDisablement.reason(derelict), "and stays abandoned");
            assertNotNull(fresh.docking.dockedTo(loaded.player).getComponent(StationComponent.class), "still docked");
        } finally {
            fresh.dispose();
        }
    }
}
