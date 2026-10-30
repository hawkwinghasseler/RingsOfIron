package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.SalvageOrdersComponent;
import com.ringsofiron.shipops.SalvagedComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boarding, salvage and disabled ships across a save: a taken ship stays taken with its crew held,
 * a part half stripped is finished after the load, and a wreck stays a wreck.
 */
class BoardingSaveTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

    Path assets;
    GameWorld world;
    GameScenario game;
    String raiderId;
    final List<Entity> raiderCrew = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        raiderId = game.raider.getComponent(UuidComponent.class).id;
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = FloorTiles.of(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            raiderCrew.add(CrewFactory.spawn(world.crew, game.raider, "Raider " + i, CrewRole.CREWMAN, RAIDERS, t[0], t[1]));
        }
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private static boolean runUntil(GameWorld w, int seconds, BooleanSupplier done) {
        for (int i = 0; i < 60 * seconds; i++) {
            w.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }

    private void beatAndLatch() {
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        runUntil(world, 2, () -> false);
        assertTrue(ShipDisablement.isDisabled(game.raider));
    }

    private FileHandle save(Path dir, String name) {
        FileHandle file = new FileHandle(dir.resolve(name).toFile());
        assertNull(game.quickSave(file));
        return file;
    }

    private GameScenario load(GameWorld into, FileHandle file) {
        GameScenario loaded = GameScenario.start(into, assets, assets.resolve("no-mods-here"));
        loaded.quickLoad(file);
        return loaded;
    }

    private static List<CrewMemberComponent> crewAboard(GameWorld w, Entity ship, int faction) {
        List<CrewMemberComponent> out = new ArrayList<>();
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = w.crew.crew(e);
            if (c.faction == faction && w.crew.position(e).ship == ship) out.add(c);
        }
        return out;
    }

    @Test
    void aTakenShipStaysTakenWithItsCrewHeld(@TempDir Path dir) {
        beatAndLatch();
        world.disablement.set(game.raider, DisabledComponent.Reason.SURRENDERED);
        world.boarding.order(game.player);
        assertTrue(runUntil(world, 240, () -> world.boarding.takenBy(game.raider, game.player)));
        float standing = world.standing.standing(RAIDERS);
        FileHandle file = save(dir, "taken.json");

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = load(fresh, file);
            Entity raider = fresh.engine.getEntity(raiderId);
            assertNotNull(raider);
            runUntil(fresh, 1, () -> false);
            assertTrue(fresh.boarding.takenBy(raider, loaded.player), "still the player's prize");
            assertEquals(2, Boarding.of(raider).prisoners);
            assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(raider));
            List<CrewMemberComponent> prisoners = crewAboard(fresh, raider, RAIDERS);
            assertEquals(2, prisoners.size());
            for (CrewMemberComponent c : prisoners) {
                assertTrue(c.held, "still held");
                assertTrue(c.state != CrewState.DEAD);
            }
            for (CrewMemberComponent ours : crewAboard(fresh, loaded.player, 0)) {
                assertFalse(fresh.crew.hostile(ours, prisoners.get(0)), "prisoners aren't fought after a load");
            }
            assertEquals(standing, fresh.standing.standing(RAIDERS), 1e-3, "and the boarding isn't charged twice");
            assertNotNull(fresh.boarding.order(loaded.player), "a taken ship can't be boarded again");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aPartHalfStrippedIsFinishedAfterTheLoad(@TempDir Path dir) {
        beatAndLatch();
        for (Entity e : raiderCrew) world.crew.crew(e).state = CrewState.DEAD;
        WeaponMountComponent gun = null;
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) {
            if (m.def != null && !m.def.pointDefense && m.subsystem != null) gun = m;
        }
        assertNotNull(gun);
        String part = Salvage.PART + gun.def.id;
        MountFootprint f = gun.subsystem.getComponent(SubsystemComponent.class).footprint;
        world.boarding.order(game.player);
        String said = world.salvage.mark(game.player, f.originX, f.originY);
        assertTrue(said.startsWith("Engineers ordered to strip"), said);
        // Saved with the order given but the part not yet home
        runUntil(world, 3, () -> false);
        assertEquals(0, game.player.getComponent(CargoHoldComponent.class).amount(part), 1e-9, "not home yet");
        assertNotNull(game.player.getComponent(SalvageOrdersComponent.class));
        FileHandle file = save(dir, "strip.json");

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = load(fresh, file);
            Entity raider = fresh.engine.getEntity(raiderId);
            assertNotNull(loaded.player.getComponent(SalvageOrdersComponent.class), "the order is saved");
            CargoHoldComponent hold = loaded.player.getComponent(CargoHoldComponent.class);
            assertTrue(runUntil(fresh, 900, () -> hold.amount(part) >= 1), "the part came home after the load: "
                    + fresh.salvage.notices);
            assertEquals(1, hold.amount(part), 1e-9, "exactly one crate, not two");
            assertTrue(raider.getComponent(SalvagedComponent.class).has(f.originX, f.originY));
            assertTrue(fresh.salvage.parts(raider).stream().noneMatch(p -> p.system.footprint.originX == f.originX
                    && p.system.footprint.originY == f.originY), "and it can't be stripped again");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aWreckStaysAWreckAndARecoveredShipGetsItsNameBack(@TempDir Path dir) {
        HudMarkerComponent marker = game.raider.getComponent(HudMarkerComponent.class);
        String label = marker.label;
        world.disablement.set(game.raider, DisabledComponent.Reason.NO_POWER);
        assertNotEquals(label, marker.label, "marked as disabled");
        FileHandle file = save(dir, "noPower.json");

        GameWorld fresh = new GameWorld();
        try {
            load(fresh, file);
            Entity raider = fresh.engine.getEntity(raiderId);
            assertEquals(DisabledComponent.Reason.NO_POWER, ShipDisablement.reason(raider));
            // Its reactors run, so the next look recovers it, with the name it had before
            assertTrue(runUntil(fresh, 2, () -> !ShipDisablement.isDisabled(raider)));
            assertEquals(label, raider.getComponent(HudMarkerComponent.class).label);
        } finally {
            fresh.dispose();
        }

        world.disablement.set(game.raider, DisabledComponent.Reason.WRECKED);
        file = save(dir, "wreck.json");
        fresh = new GameWorld();
        try {
            load(fresh, file);
            Entity raider = fresh.engine.getEntity(raiderId);
            runUntil(fresh, 2, () -> false);
            assertEquals(DisabledComponent.Reason.WRECKED, ShipDisablement.reason(raider), "a wreck for good");
            assertFalse(ShipDisablement.reactorRunning(raider.getComponent(ShipSubsystemRegistryComponent.class)), "its reactors stay off");
        } finally {
            fresh.dispose();
        }
    }
}
