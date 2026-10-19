package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A pirate running, end to end: a raider hunting the player, hit and outgunned, takes the RUN plan,
 * flies off out of the bubble with the range opening, and is back on the rails as an ace with a grudge.
 */
class RunStanceTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aHitRaiderRunsFromThePlayerAndGetsAway() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        String was = h.displayName;
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(3);
        NpcCombatComponent combat = ship.getComponent(NpcCombatComponent.class);
        assertNotNull(combat, "it came to fight");
        assertSame(game.player, combat.target);
        assertFalse(h.brokeOff);

        wallsAt(ship, 0.75f);
        assertEquals(Tactics.Plan.RUN, Tactics.choose(Tactics.arms(ship),
                HostileTraffic.firepower(ship) * 0.75 / HostileTraffic.firepower(game.player), 0.75, true, false),
                "hit, and outgunned by the corvette");
        world.hostiles.onShotAt(ship, game.player);
        run(2);
        assertTrue(h.hull < Tactics.RUN_HULL && h.hull > HostileTraffic.BREAK_OFF_HULL, "running, not breaking off: " + h.hull);
        assertTrue(h.brokeOff);
        assertNull(ship.getComponent(NpcCombatComponent.class), "done fighting");
        assertNotNull(ship.getComponent(NpcPilotComponent.class), "flying off");
        assertSame(game.player, ship.getComponent(SensorComponent.class).lockedTarget, "its guns still on the player as it goes");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.equals(was + " is running")));

        world.timeWarp.setWarp(10);
        double start = range(ship), last = start;
        int steps = 0;
        while (world.macroSim.bubble().inBubble().contains(id) && steps < 300) {
            run(1);
            if (ship.getComponent(PhysicsBodyComponent.class) == null) break;
            double now = range(ship);
            assertTrue(now > last, "the range opens: " + last + " to " + now);
            last = now;
            steps++;
        }
        assertTrue(steps > 10, "seen running for a while");
        assertTrue(last > start + 10_000, "well away: " + start + " to " + last);
        assertFalse(world.macroSim.bubble().inBubble().contains(id), "out of the bubble");

        assertTrue(world.macroSim.rails().isOnRails(id), "back on the rails");
        assertNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNotEquals(HostileComponent.State.IN_BUBBLE, h.state);
        assertFalse(h.huntingPlayer(), "done with the player for now");
        assertTrue(h.restUntil > world.macroSim.now(), "and resting");
        assertEquals(1, h.escapes);
        assertTrue(h.ace && h.grudge, "it got away: an ace with a grudge");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.startsWith(was + " got away from you")));
    }

    // ------------------------------------------------------------------

    private double range(Entity ship) {
        Vector2 a = PhysicsManager.getPosition(ship.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        Vector2 b = PhysicsManager.getPosition(game.player.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        return a.dst(b);
    }

    private static void wallsAt(Entity ship, float integrity) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(integrity);
            }
        }
    }

    private String pirate(String shipId) {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && shipId.equals(e.getValue().shipId)) return e.getKey();
        }
        fail("no " + shipId);
        return null;
    }

    /** Puts a hostile on the rails {@code dx, dy} meters from the bubble origin and lets it come in. */
    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, GameScenario.EARTH, s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
