package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.shipops.CollarWear;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.uihud.providers.CrewProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The patrol career's corvette, cast off from Highport, its docking port's collar jammed by a hit:
 * it won't arm, an engineer goes out and frees it, and it arms again. Held in from the crew window,
 * nobody goes out for it until one is sent.
 */
class JammedCollarsTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    DockingPort port;
    int index;
    MountFootprint mount;

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
        assertTrue(world.docking.undock(game.player), "cast off: a docked port is no way out");
        run(60 * 5);
        DockingPortsComponent ports = game.player.getComponent(DockingPortsComponent.class);
        index = 0;
        port = ports.ports[index];
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        MountFootprint m = mount = grid.getTile(port.gridX, port.gridY).mount;
        assertNotNull(m, "the port on the grid");
        // A heavy hit on the port itself
        CollarWear.wear(game.player, m.originX, m.originY, 0.3f);
        assertTrue(port.jammed, "jammed");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private boolean said(String text) {
        return world.notices.history().stream().anyMatch(n -> n.text.equals(text));
    }

    private boolean saidStarting(String text) {
        return world.notices.history().stream().anyMatch(n -> n.text.startsWith(text));
    }

    @Test
    void aJammedCollarWontArmTillAnEngineerFreesItFromOutside() {
        assertEquals("the collar is jammed", world.docking.arm(game.player, index));
        run(2);
        assertTrue(said("Port " + (index + 1) + "'s docking collar is jammed: it won't arm till the crew free it on EVA"));
        boolean out = false;
        for (int i = 0; i < 60 * 10 && port.jammed; i++) {
            run(60);
            out |= !world.spacewalks.onEva().isEmpty();
        }
        assertTrue(out, "someone went out");
        assertFalse(port.jammed, "freed");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.endsWith(" has freed port " + (index + 1) + "'s jammed docking collar")));
        assertNull(world.docking.arm(game.player, index), "it arms again");
        world.docking.disarm(game.player, index);
        for (int i = 0; i < 60 * 5 && !world.spacewalks.onEva().isEmpty(); i++) run(60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "and back in");
    }

    @Test
    void heldInFromTheCrewWindowNobodyGoesOutTillOneIsSent() {
        CrewRoster roster = new CrewRoster(world.context, world.crew, world.macroSim, world.standing, world.crewCareer, world.crewHiring, world.medical, world.prisoners, world.spacewalks);
        CrewProvider.CrewSheet sheet = new CrewProvider.CrewSheet();
        assertEquals("Nobody goes out on EVA unless sent, and anyone out is called back in", roster.toggleEva());
        roster.collect(sheet);
        assertEquals("EVA: held in, only crew sent out go out", sheet.eva);
        run(60 * 60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "held in");
        assertTrue(port.jammed);
        sheet.clear();
        roster.collect(sheet);
        CrewProvider.Member sent = null;
        for (CrewProvider.Member m : sheet.crew) if (sent == null && "Send out".equals(m.evaAction)) sent = m;
        assertNotNull(sent, "someone to send out for it");
        assertEquals(sent.name + " is going out for the work outside", roster.evaOrder(sent.id));
        sheet.clear();
        roster.collect(sheet);
        for (CrewProvider.Member m : sheet.crew) if (m.id == sent.id) assertEquals("Call in", m.evaAction, "sent: they can be called in");
        Entity who = null;
        for (int i = 0; i < 60 * 10 && port.jammed; i++) {
            run(60);
            for (Entity e : world.spacewalks.onEva()) who = e;
        }
        assertFalse(port.jammed, "freed");
        assertNotNull(who);
        assertEquals(sent.name, world.crew.crew(who).name, "by the one sent");
        for (int i = 0; i < 60 * 5 && !world.spacewalks.onEva().isEmpty(); i++) run(60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "back in");
        assertEquals(CrewMemberComponent.EvaOrder.NONE, world.crew.crew(who).evaOrder, "the order carried out");
        assertNull(EvaSystem.of(who));
        assertTrue(saidStarting(sent.name + " goes out on EVA"));
    }

    @Test
    void aLonePortJammedWithItsDoorWreckedCallsATowNotADeadEnd() {
        assertEquals("your ship can still fly home", world.beacon.refusal(), "the crew can still go out and free it: no tow yet");
        // The same hit wrecked the corvette's only port's outer door: its airlock can't cycle
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        assertEquals(1, game.player.getComponent(DockingPortsComponent.class).ports.length, "a lone port");
        for (int y = mount.originY; y < mount.originY + mount.height(); y++)
            for (int x = mount.originX; x < mount.originX + mount.width(); x++)
                if (DockingPortLayout.isDoorTile(mount, x, y)) DockingPortLayout.door(grid, mount, x, y).integrity = 0f;
        assertFalse(Airlock.works(grid, mount));
        assertTrue(DistressBeacon.cannotDock(game.player));
        run(60 * 60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "nobody can get out");
        assertTrue(port.jammed);
        assertTrue(saidStarting("No airlock aboard can cycle anyone out to free port " + (index + 1) + "'s collar"));
        assertNull(world.beacon.refusal(), "stranded: the beacon can be called");
        world.beacon.call();
        for (int i = 0; i < 60 * 30 && world.docking.dockedTo(game.player) == null; i++) run(1);
        assertNotNull(world.docking.dockedTo(game.player), "towed in and clamped at the station, jammed collar and all");
        assertTrue(port.collarIntegrity < 1f, "worn by the hit, so the yard there lists the collar's refit, which clears the jam");
    }
}
