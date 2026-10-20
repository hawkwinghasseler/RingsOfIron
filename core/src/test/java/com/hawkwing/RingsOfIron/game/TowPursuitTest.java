package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Round 7: a tow takes the ship away from whatever was after it, near or on its way. */
class TowPursuitTest {
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
        world.docking.undock(game.player);
        run(1);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aPirateInTheBubbleAfterThePlayerIsLeftBehind() {
        String id = pirate();
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 12_000, 0);
        run(2);
        towed();
        assertNotEquals(HostileComponent.PLAYER, h.targetUuid, "it has lost the player");
        assertNull(ship.getComponent(PhysicsBodyComponent.class), "and isn't at the station with them");
        assertNotEquals(HostileComponent.State.IN_BUBBLE, h.state);
        quiet(30);
    }

    @Test
    void aPirateOnItsWayToThePlayerIsLeftBehind() {
        String id = pirate();
        HostileComponent h = hostiles.get(id);
        // Committed to a rendezvous with the player, an hour out on the rails
        h.targetUuid = HostileComponent.PLAYER;
        h.state = HostileComponent.State.INTERCEPTING;
        towed();
        assertNotEquals(HostileComponent.PLAYER, h.targetUuid, "it has lost the player");
        assertNotEquals(HostileComponent.State.INTERCEPTING, h.state, "and gone back to its own business");
        quiet(30);
    }

    @Test
    void noPirateTakesUpTheHuntWhileTheTugBringsTheShipIn() {
        // Every pirate bold enough, and the new game's grace over
        hostiles.playerFirepower = () -> 0;
        hostiles.playerGraceUntil = 0;
        java.util.List<String> hunts = new java.util.ArrayList<>();
        hostiles.addListener(new HostileTrafficController.Listener() {
            @Override
            public void intercepting(String hostile, String target, double t) {
                if (HostileComponent.PLAYER.equals(target)) hunts.add(hostiles.get(hostile).displayName + " at " + t);
            }
        });
        double t0 = world.engine.getSimSeconds();
        towed();
        assertEquals(java.util.List.of(), hunts.stream().filter(s -> Double.parseDouble(s.substring(s.lastIndexOf(' ') + 1)) < world.engine.getSimSeconds() - 1).toList(),
                "nobody set out after the player during the tow (started " + t0 + ")");
    }

    @Test
    void aSaveAfterTheTowKeepsThePursuersDropped(@org.junit.jupiter.api.io.TempDir Path dir) {
        String id = pirate();
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        h.state = HostileComponent.State.INTERCEPTING;
        towed();
        double grace = hostiles.playerGraceUntil;
        assertTrue(grace >= world.engine.getSimSeconds() - 1, "no hunt while the tug brought the ship in");
        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(dir.resolve("towed.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        hostiles = world.macroSim.hostiles();
        assertNotEquals(HostileComponent.PLAYER, hostiles.get(id).targetUuid, "still dropped");
        assertEquals(grace, hostiles.playerGraceUntil, 1e-6);
    }

    @Test
    void aSurrenderedPirateStaysAPrizeThroughATow() {
        String id = pirate();
        HostileComponent h = hostiles.get(id);
        h.surrendered = true;
        h.targetUuid = HostileComponent.PLAYER;
        bringIn(id, 12_000, 0);
        towed();
        assertTrue(h.surrendered);
    }

    /** Strands the ship and calls the tug, then lets it dock. */
    private void towed() {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent t = e.getComponent(SubsystemComponent.class);
            if (t != null && t.type == SubsystemComponent.SubsystemType.TANK
                    && t.fluid == com.ringsofiron.interiorgrid.Fluid.PROPELLANT) t.contents = 0;
        }
        run(1);
        assertTrue(world.beacon.call().startsWith("Towed in"), world.beacon.refusal());
        for (int i = 0; i < 120 && world.rescue.bringingIn(); i++) world.update(FRAME);
        assertNotNull(world.docking.dockedTo(game.player), "docked at the station");
    }

    /** No hostile at the station has the player as its target for {@code seconds}, and nothing hits the ship. */
    private void quiet(double seconds) {
        int[] hits = {0};
        world.combatEvents.impacts.add((sig, e) -> {
            if (e.target == game.player) hits[0]++;
        });
        float hull = ShipDisablement.hullIntegrity(game.player.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class));
        run(seconds);
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            Entity s = world.engine.getEntity(e.getKey());
            if (s != null && s.getComponent(PhysicsBodyComponent.class) != null && !e.getValue().surrendered) {
                assertNotEquals(HostileComponent.PLAYER, e.getValue().targetUuid, e.getValue().displayName + " came after the player");
            }
        }
        assertEquals(0, hits[0], "nothing hit the ship at the station");
        assertEquals(hull, ShipDisablement.hullIntegrity(game.player.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class)), 1e-6f);
    }

    private String pirate() {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && !e.getValue().moored() && e.getValue().wingOf == null) return e.getKey();
        }
        fail("no pirate");
        return null;
    }

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
