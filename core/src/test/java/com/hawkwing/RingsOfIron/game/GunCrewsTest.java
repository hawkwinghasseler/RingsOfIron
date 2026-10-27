package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ManConsoleTask;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Crewed guns firing on hostiles by themselves, and the guns the player takes in hand. */
class GunCrewsTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    WeaponsComponent weapons;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        weapons = game.player.getComponent(WeaponsComponent.class);
        // Weapon control has placed the muzzles
        world.update(FRAME);
        for (Entity e : crew()) world.crew.crew(e).task = null;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void nobodyAtTheGunsMeansTheyWaitForTheTrigger() {
        game.provokeRaider();
        man();
        for (WeaponMountComponent m : mainGuns()) assertFalse(m.weaponsFree, m.def.id);
    }

    @Test
    void aGunnerAtTheWeaponsConsoleWorksEveryGunButNoneOfThemWell() {
        game.provokeRaider();
        Entity cole = crewman(CrewRole.MARINE);
        mans(cole, consoleEntity(SubsystemType.WEAPONS));
        man();
        float skill = world.crew.crew(cole).def.gunnerySkill;
        for (WeaponMountComponent m : mainGuns()) {
            if (m.def.mount == WeaponDef.MountType.LAUNCHER) {
                assertFalse(m.weaponsFree, "missiles wait for the player's word");
                continue;
            }
            assertTrue(m.weaponsFree, m.def.id + " is worked from the console");
            assertSame(game.raider, m.target, "on the raider fighting the player");
            assertEquals(skill * GunCrews.REMOTE_SKILL, m.gunnerySkill, 1e-6f);
        }
    }

    @Test
    void aGunnerAtTheGunItselfShootsAtTheirOwnSkill() {
        game.provokeRaider();
        WeaponMountComponent laser = mount("laser_mk1");
        Entity cole = crewman(CrewRole.MARINE);
        Entity bea = crewman(CrewRole.CREWMAN);
        world.crew.crew(cole).def.gunnerySkill = 0.8f;
        world.crew.crew(bea).def.gunnerySkill = 0.8f;
        mans(cole, laser.subsystem);
        mans(bea, consoleEntity(SubsystemType.WEAPONS));
        man();
        assertTrue(laser.weaponsFree);
        assertEquals(0.8f, laser.gunnerySkill, 1e-6f, "at the gun");
        WeaponMountComponent railgun = mount("railgun_mk1");
        assertTrue(railgun.weaponsFree);
        assertEquals(0.8f * GunCrews.REMOTE_SKILL, railgun.gunnerySkill, 1e-6f, "from the console");
    }

    @Test
    void theCrewDontStartAFight() {
        mans(crewman(CrewRole.MARINE), consoleEntity(SubsystemType.WEAPONS));
        man();
        assertFalse(GunCrews.hostile(world, game.raider), "the raider hasn't come for the player yet");
        for (WeaponMountComponent m : mainGuns()) assertFalse(m.weaponsFree, m.def.id);
    }

    @Test
    void atTheWeaponsConsoleThePlayerHasEveryGunInHand() {
        game.provokeRaider();
        mans(crewman(CrewRole.MARINE), mount("laser_mk1").subsystem);
        world.bridge.stand();
        SubsystemComponent console = Bridge.console(game.player, SubsystemType.WEAPONS);
        game.avatar.getComponent(GridPositionComponent.class).setTile(console.footprint.originX - 1, console.footprint.originY);
        assertNull(world.bridge.sit(SubsystemType.WEAPONS));
        man();
        for (WeaponMountComponent m : mainGuns()) {
            assertTrue(PlayerGunnery.inHand(world, m), m.def.id);
            assertFalse(m.weaponsFree, "only the trigger fires a gun in hand");
        }
    }

    @Test
    void thePlayerCanSitAtAGunAndHaveJustThatOne() {
        WeaponMountComponent laser = mount("laser_mk1");
        SubsystemComponent mount = laser.subsystem.getComponent(SubsystemComponent.class);
        world.bridge.stand();
        game.avatar.getComponent(GridPositionComponent.class).setTile(mount.footprint.originX - 1, mount.footprint.originY);
        assertNull(world.bridge.sit(SubsystemType.TURRET_MOUNT));
        assertTrue(world.bridge.playerAtGun());
        assertSame(laser.subsystem, world.bridge.seatedGun());
        for (WeaponMountComponent m : mainGuns()) assertEquals(m == laser, PlayerGunnery.inHand(world, m), m.def.id);
    }

    @Test
    void crewPostToManTheGuns() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        int guns = 0;
        for (var t : world.crew.board(game.player).tasks) {
            if (t instanceof ManConsoleTask && ((ManConsoleTask) t).type() == SubsystemType.TURRET_MOUNT) guns++;
        }
        assertTrue(guns > 0, "the turret mounts are on the board to man");
    }

    private void man() {
        GunCrews.man(world, game.player, weapons, false);
    }

    private Iterable<Entity> crew() {
        java.util.List<Entity> aboard = new java.util.ArrayList<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player && e != game.avatar) aboard.add(e);
        }
        return aboard;
    }

    private Entity crewman(CrewRole role) {
        for (Entity e : crew()) if (world.crew.crew(e).role == role) return e;
        throw new AssertionError("no " + role + " aboard");
    }

    /** Puts {@code e} at {@code console}, holding the task to man it. */
    private void mans(Entity e, Entity console) {
        SubsystemComponent s = console.getComponent(SubsystemComponent.class);
        ManConsoleTask t = new ManConsoleTask(console, s.footprint.originX, s.footprint.originY);
        t.claimedBy = e;
        t.status = com.ringsofiron.interiorgrid.crew.CrewTask.Status.CLAIMED;
        CrewMemberComponent c = world.crew.crew(e);
        c.task = t;
        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
        p.setTile(s.footprint.originX - 1, s.footprint.originY);
        p.path.clear();
    }

    private Entity consoleEntity(SubsystemType type) {
        for (Entity e : game.player.getComponent(com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == type) return e;
        }
        throw new AssertionError("no " + type);
    }

    private java.util.List<WeaponMountComponent> mainGuns() {
        java.util.List<WeaponMountComponent> out = new java.util.ArrayList<>();
        for (WeaponMountComponent m : weapons.mounts) if (!m.def.pointDefense) out.add(m);
        return out;
    }

    private WeaponMountComponent mount(String weapon) {
        for (WeaponMountComponent m : weapons.mounts) if (m.def.id.equals(weapon)) return m;
        throw new AssertionError("no " + weapon);
    }
}
