package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.UiContextProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The HUD variants' common data layer on a real career world, headless (no GL, so no UiHud): the
 * providers and the alerts that {@code GameHud} wires into {@link HudData}.
 */
class HudDataTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HudData data;
    GameAlerts alerts;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
        data = new HudData();
        // The same wiring GameHud does, without the stage
        data.context = new PlayerUiContext(world.context, null, world.timeWarp, world.universe, world.nav, world.bridge,
                world.avatar, world.docking, world.spacewalks, world.gunCrews, world.pirateBase);
        data.ship = new com.hawkwing.RingsOfIron.wiring.PlayerShipStatus(world::getPlayer);
        data.captain = new PlayerCaptainStatus(world.avatar);
        PlayerWeaponStatus weapons = new PlayerWeaponStatus(world.context, world.gunCrews, world.crew, world.bridge,
                world.docking, world.sensors);
        data.weapons = weapons;
        data.shields = new PlayerShieldStatus(world.context);
        data.power = new PlayerPowerStatus(world.context);
        data.target = new PlayerTargetStatus(world.context, world.docking, world.sensors, world.pirateBase);
        data.threats = new CombatThreats(world.context);
        alerts = new GameAlerts(world.context, world.warnings, world.spacewalks, world.crew, data);
        data.afterRefresh = alerts::update;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    @Test
    void theContextSaysWhereTheCaptainIsAndThatAllIsWell() {
        data.refresh();
        assertEquals(game.player, data.ctx.ship);
        assertTrue(data.ctx.presence != UiContextProvider.Presence.NONE);
        assertEquals(UiContextProvider.Condition.GREEN, data.ctx.condition);
        assertEquals(0, data.ctx.missilesInbound);
        assertFalse(data.ctx.emergency);
        assertTrue(data.ctx.docked, "the career starts docked");
        assertFalse(data.ctx.stationName.isEmpty());
        assertEquals(0, data.alerts.size(), () -> "a sound ship raises nothing: " + first());
    }

    @Test
    void theCaptainsRoomIsNamed() {
        data.refresh();
        assertTrue(data.captainStatus.present);
        assertFalse(data.captainStatus.room.isEmpty());
        assertTrue(data.captainStatus.roomPressure > 0.9f, "" + data.captainStatus.roomPressure);
        // The corvette's rooms: the one with the helm is the bridge
        assertNotNull(data.roomNames());
        assertEquals("BRIDGE", data.roomAt(helmTile()[0], helmTile()[1]));
    }

    @Test
    void everyMountHasAPlaceNameAndAState() {
        data.refresh();
        assertFalse(data.weaponFrame.mounts.isEmpty(), "the starter corvette is armed");
        for (WeaponStatusProvider.Mount m : data.weaponFrame.mounts) {
            assertFalse(m.name.isEmpty());
            assertFalse(m.weapon.isEmpty());
            assertNotNull(m.state);
            assertNotNull(m.controller);
        }
    }

    @Test
    void aFireRaisesAWarningInItsRoomAndClearsWithIt() {
        data.refresh();
        assertEquals(0, data.alerts.size());
        Tile t = burn();
        data.refresh();
        AlertRegistry.Alert fire = null;
        for (AlertRegistry.Alert a : data.alerts.all()) if ("FIRE".equals(a.code)) fire = a;
        assertNotNull(fire, () -> "no fire alert: " + first());
        assertEquals(AlertRegistry.Level.WARNING, fire.level);
        assertEquals(AlertRegistry.Category.LIFE_SUPPORT, fire.category);
        assertFalse(fire.room.isEmpty());
        assertEquals(AlertRegistry.Level.WARNING, data.alerts.master());
        data.alerts.acknowledgeAll();
        data.refresh();
        assertEquals(null, data.alerts.master(), "acknowledged, so the master lamp is out");
        // Put it out: the alert goes with it
        t.fireIntensity = 0f;
        data.refresh();
        for (AlertRegistry.Alert a : data.alerts.all()) assertFalse("FIRE".equals(a.code));
    }

    @Test
    void aHullBreachRaisesAWarningInTheRoomItOpensOnto() {
        data.refresh();
        assertEquals(0, data.alerts.size());
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        int[] hole = hullBesideARoom(grid);
        grid.getTile(hole[0], hole[1]).setIntegrity(0f);
        // Let the room vent: the breach is still its breach once the air is gone
        run(120);
        data.refresh();
        AlertRegistry.Alert breach = null;
        for (AlertRegistry.Alert a : data.alerts.all()) if ("BREACH".equals(a.code)) breach = a;
        assertNotNull(breach, () -> "no breach alert: " + first());
        assertEquals(AlertRegistry.Level.WARNING, breach.level);
        assertEquals(AlertRegistry.Category.LIFE_SUPPORT, breach.category);
        assertFalse(breach.room.isEmpty());
        assertEquals(hole[0], breach.tileX);
        assertEquals(hole[1], breach.tileY);
        assertEquals(1, breach.count);
    }

    @Test
    void aSwitchedOffSystemShowsAsAFaultAndAnAlert() {
        SubsystemComponent life = system(SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        life.switchedOn = false;
        world.update(FRAME);
        data.refresh();
        PowerStatusProvider.SystemRow row = null;
        for (PowerStatusProvider.SystemRow r : data.powerFrame.faults) if ("LIFE SUP".equals(r.system)) row = r;
        assertNotNull(row, () -> "no life support fault: " + data.powerFrame.faults.size + " faults");
        assertEquals(PowerStatusProvider.Fault.OFF, row.fault);
        assertFalse(row.room.isEmpty());
        assertNotNull(data.alerts.get("sys:LIFE SUP:" + row.tileX + "," + row.tileY));
        assertTrue(data.powerFrame.circuits.size > 0, "the reactor's circuit");
        assertTrue(data.powerFrame.ratedWatts > 0);
    }

    @Test
    void aWreckedReactorIsAFaultAndAWarning() {
        SubsystemComponent reactor = system(SubsystemComponent.SubsystemType.REACTOR);
        reactor.health = 0f;
        world.update(FRAME);
        data.refresh();
        PowerStatusProvider.SystemRow row = null;
        for (PowerStatusProvider.SystemRow r : data.powerFrame.faults) if ("REACTOR".equals(r.system)) row = r;
        assertNotNull(row, () -> "no reactor fault: " + data.powerFrame.faults.size + " faults");
        assertEquals(PowerStatusProvider.Fault.DESTROYED, row.fault);
        AlertRegistry.Alert a = data.alerts.get("sys:REACTOR:" + row.tileX + "," + row.tileY);
        assertNotNull(a);
        assertEquals(AlertRegistry.Level.WARNING, a.level);
    }

    @Test
    void theMessageFeedCarriesTheGamesNotices() {
        world.notices.post("Test notice", NoticeLog.Kind.WARNING);
        data.refresh();
        assertFalse(data.messages.all().isEmpty());
        assertEquals("Test notice", data.messages.newest().text);
        assertEquals(AlertRegistry.Level.WARNING, data.messages.newest().level);
        assertTrue(data.messages.newest().secondsLeft > 0f);
    }

    /** The first alert's code and text, for a failure message. */
    private String first() {
        if (data.alerts.size() == 0) return "none";
        AlertRegistry.Alert a = data.alerts.all().first();
        return a.code + " / " + a.text;
    }

    private int[] helmTile() {
        SubsystemComponent helm = system(SubsystemComponent.SubsystemType.HELM);
        return new int[]{helm.footprint.originX, helm.footprint.originY};
    }

    private SubsystemComponent system(SubsystemComponent.SubsystemType type) {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == type) return s;
        }
        throw new IllegalStateException("no " + type + " aboard");
    }

    /** Sets one floor tile of the ship alight, as a hit does. */
    /** A hull tile with a room's air tile beside it and no edge wall between. */
    private static int[] hullBesideARoom(ShipGridComponent grid) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.wallType != Tile.WallType.HULL) continue;
                for (com.ringsofiron.interiorgrid.Side s : com.ringsofiron.interiorgrid.Side.values()) {
                    if (grid.compartmentAt(x + s.dx, y + s.dy) != null && !grid.isEdgeBlocked(x, y, x + s.dx, y + s.dy)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        throw new IllegalStateException("no hull beside a room");
    }

    private Tile burn() {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor() && t.compartment != null) {
                    t.fireIntensity = 1f;
                    return t;
                }
            }
        }
        throw new IllegalStateException("nowhere to light a fire");
    }
}
