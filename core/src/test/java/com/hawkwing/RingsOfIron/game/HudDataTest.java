package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.shields.EmitterSpec;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
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
    void eachShieldEmitterSaysItsHeatAndWhenATrippedOneStartsAgain() {
        // Two emitters on the player's hull, as the shield model keeps them: one warm, one tripped
        ShieldCloudComponent cloud = new ShieldCloudComponent();
        Entity warm = new Entity(), tripped = new Entity();
        ShieldCloudComponent.Emitter a = new ShieldCloudComponent.Emitter(), b = new ShieldCloudComponent.Emitter();
        a.spec = b.spec = EmitterSpec.S;
        a.heat = EmitterSpec.S.heatTrip * 0.5f;
        a.working = true;
        b.heat = EmitterSpec.S.heatTrip * 0.8f;
        b.tripped = true;
        cloud.emitters.put(warm, a);
        cloud.emitters.put(tripped, b);
        game.player.add(cloud);
        data.refresh();
        ShieldStatusProvider.ShieldStatus st = data.shieldStatus;
        assertTrue(st.present);
        assertEquals(2, st.each.size);
        assertEquals(1, st.emittersTripped);
        for (ShieldStatusProvider.Emitter e : st.each) {
            if (e.system == warm) {
                assertEquals(0.5f, e.heat, 1e-4f);
                assertTrue(e.working);
                assertTrue(Float.isNaN(e.restartIn), "only a tripped one waits to start");
            } else {
                assertTrue(e.system == tripped);
                assertTrue(e.tripped);
                // It sheds heat at its radiators' rate until it is down to the restart share
                float wait = (b.heat - ShieldModel.RESTART_AT * EmitterSpec.S.heatTrip) / EmitterSpec.S.cooling;
                assertEquals(wait, e.restartIn, 1e-3f);
                assertTrue(e.restartIn > 0f);
            }
        }
        assertEquals(0.8f, st.hottest, 1e-4f);
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
    void aFireSaysHowManyCrewArePuttingItOut() {
        int[] at = new int[2];
        burn(at);
        data.refresh();
        assertEquals(0, alert("FIRE").handling, "nobody on it yet");
        ShipTaskBoardComponent board = game.player.getComponent(ShipTaskBoardComponent.class);
        ExtinguishTask task = new ExtinguishTask(at[0], at[1]);
        assertTrue(board.post(task));
        Entity hand = hand();
        assertTrue(board.claim(task, hand));
        data.refresh();
        AlertRegistry.Alert fire = alert("FIRE");
        assertEquals(1, fire.handling, "one hand on the extinguisher");
        // Named, on the way there, and a fire has no set time
        assertEquals(hand.getComponent(CrewMemberComponent.class).name, fire.who);
        assertFalse(fire.atWork);
        assertTrue(Float.isNaN(fire.workLeft));
    }

    @Test
    void aResetSaysItsWorkTimeOnTheWayAndWhatIsLeftOnceAtIt() {
        CrewMemberComponent ada = new CrewMemberComponent("Ada", com.ringsofiron.aicrew.CrewRole.ENGINEER, 0);
        ResetShortTask reset = new ResetShortTask(4, 2);
        // Going to it: the work's own time at their pace, the walk not known
        ada.activity = "going to " + CrewTasks.describe(reset);
        assertFalse(GameAlerts.atWork(ada, reset));
        assertEquals(ada.def.resetTime, GameAlerts.workLeft(ada, reset, false), 1e-4f);
        // At it, 40% done: 60% of it left
        ada.task = reset;
        ada.activity = CrewTasks.describe(reset);
        ada.progress = 0.4f;
        assertTrue(GameAlerts.atWork(ada, reset));
        assertEquals(0.6f * ada.def.resetTime, GameAlerts.workLeft(ada, reset, true), 1e-4f);
        // A fire has no set time
        assertTrue(Float.isNaN(GameAlerts.workLeft(ada, new ExtinguishTask(1, 1), false)));
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

    @Test
    void theLockAndItsContactReadOneTrack() {
        data.contacts = new PlayerContacts(world.context, world.docking, world.nav.target, world.pirateBase, world.spacewalks,
                world.sensors);
        assertNotNull(game.raider, "the patrol start has a raider in the bubble");
        game.player.getComponent(SensorComponent.class).lockedTarget = game.raider;
        run(60);
        data.refresh();
        assertTrue(data.targetStatus.present);
        assertFalse(Float.isNaN(data.targetStatus.range), "the lock is tracked");
        ContactProvider.Contact locked = null;
        for (ContactProvider.Contact c : data.contactFrame.contacts) if (c.locked) locked = c;
        assertNotNull(locked, "the lock is among the contacts");
        // The sensors' noise puts the track metres off the truth: both read the track, so they agree exactly
        assertEquals(data.targetStatus.range, locked.range, 1e-3f);
        assertEquals(data.targetStatus.closing, locked.closing, 1e-4f);
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
    private Tile burn() {
        return burn(new int[2]);
    }

    /** Sets one floor tile alight, putting where in {@code at}. */
    private Tile burn(int[] at) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isFloor() && t.compartment != null) {
                    t.fireIntensity = 1f;
                    at[0] = x;
                    at[1] = y;
                    return t;
                }
            }
        }
        throw new IllegalStateException("nowhere to light a fire");
    }

    private AlertRegistry.Alert alert(String code) {
        for (AlertRegistry.Alert a : data.alerts.all()) if (code.equals(a.code)) return a;
        throw new AssertionError("no " + code + " alert: " + first());
    }

    /** One of the crew, not the captain. */
    private Entity hand() {
        for (Entity e : world.crew.aboard(game.player)) {
            if (e.getComponent(PlayerAvatarComponent.class) == null) return e;
        }
        throw new IllegalStateException("no crew aboard");
    }
}
