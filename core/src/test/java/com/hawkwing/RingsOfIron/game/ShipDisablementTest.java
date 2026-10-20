package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
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

/** Ships disabled for want of power, crew or hull, NPCs surrendering, and wrecks. */
class ShipDisablementTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private static List<SubsystemComponent> systems(Entity ship, SubsystemComponent.SubsystemType type) {
        List<SubsystemComponent> out = new ArrayList<>();
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == type) out.add(s);
        }
        return out;
    }

    private static void setHull(Entity ship, float integrity) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL) t.setIntegrity(integrity);
            }
        }
    }

    private List<Entity> crewAboard(Entity ship) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) out.add(e);
        }
        return out;
    }

    @Test
    void aShipFitToFightIsNotDisabled() {
        run(60);
        assertFalse(ShipDisablement.isDisabled(game.player));
        assertFalse(ShipDisablement.isDisabled(game.raider));
        assertEquals(1f, ShipDisablement.hullIntegrity(game.raider.getComponent(ShipGridComponent.class)), 1e-6f);
    }

    @Test
    void aShipWithNoReactorRunningIsDisabledUntilItComesBack() {
        game.provokeRaider();
        run(10);
        for (SubsystemComponent r : systems(game.raider, SubsystemComponent.SubsystemType.REACTOR)) r.switchedOn = false;
        run(60);
        assertEquals(DisabledComponent.Reason.NO_POWER, ShipDisablement.reason(game.raider));
        HudMarkerComponent marker = game.raider.getComponent(HudMarkerComponent.class);
        assertEquals("Raider (reactor down)", marker.label);
        assertEquals(ShipDisablement.DISABLED_COLOR, marker.color);
        // It stops: no combat pilot, guns held, no lock
        assertNull(game.raider.getComponent(NpcCombatComponent.class));
        assertNull(game.raider.getComponent(SensorComponent.class).lockedTarget);
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) assertFalse(m.weaponsFree);
        // and being shot at again doesn't wake it
        game.provokeRaider();
        assertNull(game.raider.getComponent(NpcCombatComponent.class));
        assertFalse(game.raiderHostile());

        for (SubsystemComponent r : systems(game.raider, SubsystemComponent.SubsystemType.REACTOR)) r.switchedOn = true;
        run(60);
        assertFalse(ShipDisablement.isDisabled(game.raider), "power back, fit again");
        assertEquals("Raider", marker.label);
        assertEquals(HudMarkerComponent.Kind.HOSTILE.defaultColor, marker.color);
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) assertTrue(m.weaponsFree);
    }

    @Test
    void aShipWhoseCrewAreAllDownIsDisabled() {
        List<Entity> crew = crewAboard(game.player);
        assertFalse(crew.isEmpty());
        for (Entity e : crew) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            world.crew.applyDamage(e, c.health * 0.9f, CrewDamageSink.DamageCause.SPALL);
            assertFalse(c.isConscious());
        }
        world.disablement.judge(game.player);
        assertEquals(DisabledComponent.Reason.NO_CREW, ShipDisablement.reason(game.player));
        // The raider's crew are not simulated: it is judged on hull and power alone
        assertNull(world.disablement.judge(game.raider));
    }

    @Test
    void aBrokenHullDisablesAndAWreckStaysAWreck() {
        setHull(game.raider, 0.4f);
        run(40);
        assertEquals(DisabledComponent.Reason.HULL, ShipDisablement.reason(game.raider));
        setHull(game.raider, 0.2f);
        run(40);
        assertEquals(DisabledComponent.Reason.WRECKED, ShipDisablement.reason(game.raider));
        assertTrue(game.raider.getComponent(DisabledComponent.class).isWreck());
        for (SubsystemComponent r : systems(game.raider, SubsystemComponent.SubsystemType.REACTOR)) {
            assertFalse(r.switchedOn, "a wreck's reactors go cold");
        }
        assertEquals("Raider (wreck)", game.raider.getComponent(HudMarkerComponent.class).label);
        setHull(game.raider, 1f);
        run(40);
        assertEquals(DisabledComponent.Reason.WRECKED, ShipDisablement.reason(game.raider), "patching it doesn't make it fly");
        assertTrue(world.engine.getEcsEngine().getEntities().contains(game.raider, true), "it stays to be salvaged");
    }

    @Test
    void aLosingNpcSurrendersAndStaysSurrendered() {
        List<DisabledComponent.Reason> seen = new ArrayList<>();
        world.disablement.changes.add((signal, c) -> {
            if (c.ship == game.raider) seen.add(c.now);
        });
        game.provokeRaider();
        run(10);
        setHull(game.raider, 0.5f);
        run(40);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
        assertNull(game.raider.getComponent(NpcCombatComponent.class));
        assertEquals(List.of(DisabledComponent.Reason.SURRENDERED), seen);
        setHull(game.raider, 1f);
        run(40);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
        // Shot to pieces after surrendering, it still breaks up
        setHull(game.raider, 0.1f);
        run(40);
        assertEquals(DisabledComponent.Reason.WRECKED, ShipDisablement.reason(game.raider));
    }

    @Test
    void onlyAShipInAFightSurrenders() {
        setHull(game.raider, 0.5f);
        run(40);
        assertNull(ShipDisablement.reason(game.raider), "not fighting, nothing to give up");
        setHull(game.player, 0.5f);
        game.provokeRaider();
        run(40);
        assertNull(ShipDisablement.reason(game.player), "the player's ship never surrenders on its own");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
    }

    @Test
    void aCrewThatFightsToTheEndDoesNotSurrender() {
        game.raider.add(new MoraleComponent(false));
        game.provokeRaider();
        setHull(game.raider, 0.5f);
        run(40);
        assertNull(ShipDisablement.reason(game.raider));
        assertNotNull(game.raider.getComponent(NpcCombatComponent.class));
        setHull(game.raider, 0.4f);
        run(40);
        assertEquals(DisabledComponent.Reason.HULL, ShipDisablement.reason(game.raider), "disabled under them instead");
    }

    @Test
    void anNpcWithEveryGunSilencedSurrenders() {
        game.provokeRaider();
        run(10);
        for (WeaponMountComponent m : game.raider.getComponent(WeaponsComponent.class).mounts) {
            if (m.def.pointDefense || m.subsystem == null) continue;
            m.subsystem.getComponent(SubsystemComponent.class).health = 0.1f;
        }
        run(40);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
    }

    @Test
    void thePlayersMainGunsWaitForTheTriggerWhenPowerComesBack() {
        Entity target = game.raider;
        game.player.getComponent(SensorComponent.class).lockedTarget = target;
        run(10);
        for (SubsystemComponent r : systems(game.player, SubsystemComponent.SubsystemType.REACTOR)) r.switchedOn = false;
        run(60);
        assertEquals(DisabledComponent.Reason.NO_POWER, ShipDisablement.reason(game.player));
        for (SubsystemComponent r : systems(game.player, SubsystemComponent.SubsystemType.REACTOR)) r.switchedOn = true;
        run(60);
        assertFalse(ShipDisablement.isDisabled(game.player), "power back, fit again");
        // Round 8: they used to come back weapons free and fire on their own at the locked target
        for (WeaponMountComponent m : game.player.getComponent(WeaponsComponent.class).mounts) {
            assertEquals(m.def != null && m.def.pointDefense, m.weaponsFree, "only point defense fires by itself");
        }
    }

    @Test
    void theDisabledStateIsSaved(@TempDir Path dir) {
        game.provokeRaider();
        run(10);
        setHull(game.raider, 0.5f);
        run(40);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(game.raider));
        FileHandle file = new FileHandle(dir.resolve("disabled.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertNotNull(loaded.raider, "still found as the raider");
            assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(loaded.raider));
            assertEquals("Raider (surrendered)", loaded.raider.getComponent(HudMarkerComponent.class).label);
            for (int i = 0; i < 60; i++) fresh.update(FRAME);
            assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(loaded.raider));
            loaded.provokeRaider();
            assertFalse(loaded.raiderHostile());
        } finally {
            fresh.dispose();
        }
    }
}
