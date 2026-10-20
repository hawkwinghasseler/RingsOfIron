package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
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
import org.junit.jupiter.api.io.TempDir;

/** Round 8: pirates refuel on the rails, and top up from the freighters they rob. */
class PirateUpkeepTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;
    GameWorld loaded;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
        if (loaded != null) loaded.dispose();
    }

    @Test
    void aDryPirateOnTheRailsIsFullInTwoDays() {
        String id = pirate(HostileTraffic.RAIDER);
        Entity ship = world.engine.getEntity(id);
        assertNull(ship.getComponent(PhysicsBodyComponent.class), "out on the rails");
        empty(ship);
        PirateUpkeep upkeep = new PirateUpkeep(world);
        double t0 = 1_000;
        upkeep.update(hostiles.all(), t0);
        assertEquals(0, PirateUpkeep.fuelShare(ship), 1e-9, "the first call only starts the clock");
        upkeep.update(hostiles.all(), t0 + PirateUpkeep.DAY);
        assertEquals(1 / PirateUpkeep.RAILS_REFUEL_DAYS, PirateUpkeep.fuelShare(ship), 1e-6, "half full after a day");
        upkeep.update(hostiles.all(), t0 + 3 * PirateUpkeep.DAY);
        assertEquals(1, PirateUpkeep.fuelShare(ship), 1e-9, "full, and no more");
    }

    @Test
    void aPirateInTheBubbleTakesOnNothing() {
        String id = pirate(HostileTraffic.RAIDER);
        Entity ship = bringIn(id, 3_000, 0);
        empty(ship);
        PirateUpkeep upkeep = new PirateUpkeep(world);
        upkeep.update(hostiles.all(), 0);
        upkeep.update(hostiles.all(), PirateUpkeep.DAY);
        assertEquals(0, PirateUpkeep.fuelShare(ship), 1e-9);
    }

    @Test
    void aRobberyInTheBubbleTopsItUpFromThePreysReactionMass() {
        Entity pirate = world.engine.getEntity(pirate(HostileTraffic.RAIDER));
        Entity prey = world.engine.getEntity(pirate(HostileTraffic.PIRATE_GUNSHIP));
        empty(pirate);
        for (SubsystemComponent t : tanks(prey)) t.contents = t.capacity / 8;
        double need = capacity(pirate), had = held(prey);
        double moved = PirateUpkeep.robbed(pirate, prey);
        assertEquals(Math.min(need, had), moved, 1e-6);
        assertEquals(moved, held(pirate), 1e-6, "into the pirate's tanks");
        assertEquals(had - moved, held(prey), 1e-6, "out of its prey's");
    }

    @Test
    void aRobberyOnTheRailsFillsItsTanks() {
        Entity pirate = world.engine.getEntity(pirate(HostileTraffic.RAIDER));
        Entity freighter = world.engine.getEntity(world.macroSim.freighters().all().keySet().iterator().next());
        assertNull(freighter.getComponent(ShipSubsystemRegistryComponent.class), "a freighter on the rails has no tanks");
        empty(pirate);
        PirateUpkeep.robbed(pirate, freighter);
        assertEquals(PirateUpkeep.RAILS_ROBBERY_SHARE, PirateUpkeep.fuelShare(pirate), 1e-9);
    }

    @Test
    void theTanksAndTheClockComeThroughASave(@TempDir Path dir) {
        String id = pirate(HostileTraffic.RAIDER);
        Entity ship = world.engine.getEntity(id);
        empty(ship);
        run(world, 2);
        double share = PirateUpkeep.fuelShare(ship);
        assertTrue(share > 0 && share < 1, "refuelling: " + share);

        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        loaded = new GameWorld();
        GameScenario again = GameScenario.start(loaded, assets, assets.resolve("no-mods-here"));
        again.quickLoad(file);
        Entity back = loaded.engine.getEntity(id);
        assertEquals(share, PirateUpkeep.fuelShare(back), 1e-9, "the tanks as they were");
        // Played on side by side, the two refuel alike: the clock came through as well
        assertEquals(world.hostiles.upkeepClock(), loaded.hostiles.upkeepClock(), 1e-9, "the refuelling clock");
        // Played on, the two refuel alike, give or take where the checks fall
        run(world, 5);
        run(loaded, 5);
        assertEquals(PirateUpkeep.fuelShare(ship), PirateUpkeep.fuelShare(back), 2 * HostileTraffic.CHECK_SECONDS / (PirateUpkeep.RAILS_REFUEL_DAYS * PirateUpkeep.DAY));
    }

    // ---------------------------------------------------------------------------------------------

    private static Array<SubsystemComponent> tanks(Entity ship) {
        return TankSupply.tanks(ship.getComponent(ShipSubsystemRegistryComponent.class), Fluid.PROPELLANT);
    }

    private static void empty(Entity ship) {
        for (SubsystemComponent t : tanks(ship)) t.contents = 0;
    }

    private static double held(Entity ship) {
        double sum = 0;
        for (SubsystemComponent t : tanks(ship)) sum += t.contents;
        return sum;
    }

    private static double capacity(Entity ship) {
        double sum = 0;
        for (SubsystemComponent t : tanks(ship)) sum += t.capacity;
        return sum;
    }

    private String pirate(String shipId) {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && !e.getValue().moored() && shipId.equals(e.getValue().shipId)) return e.getKey();
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

    private static void run(GameWorld w, double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) w.update(FRAME);
    }
}
