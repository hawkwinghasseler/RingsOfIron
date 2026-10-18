package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Docked craft joined at their ports: the doors each side agreed to, air through them, and walking across. */
class DockLinkWalkTest {
    static final float FRAME = 1f / 60f;

    DockingDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new DockingDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds * 60; i++) demo.update(FRAME);
    }

    private boolean runUntilDocked(float seconds) {
        for (int i = 0; i < seconds * 60 && !demo.world.docking.isRigid(demo.player); i++) demo.update(FRAME);
        return demo.world.docking.isRigid(demo.player);
    }

    private static ShipGridComponent grid(Entity e) {
        return e.getComponent(ShipGridComponent.class);
    }

    /** The port of {@code e} that is docked, or the selected one when none is. */
    private static DockingPort dockedPort(Entity e) {
        DockingPortsComponent ports = e.getComponent(DockingPortsComponent.class);
        for (DockingPort p : ports.ports) {
            if (p.state == DockingPort.State.DOCKED) return p;
        }
        return ports.selectedPort();
    }

    private static MountFootprint mount(Entity e, DockingPort p) {
        return grid(e).getTile(p.gridX, p.gridY).mount;
    }

    /** True if every door of {@code e}'s docked port is open. */
    private static boolean doorsOpen(Entity e) {
        DockingPort p = dockedPort(e);
        MountFootprint m = mount(e, p);
        boolean any = false;
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                if (!DockingPortLayout.isDoorTile(m, x, y)) continue;
                EdgeWall door = DockingPortLayout.door(grid(e), m, x, y);
                if (door.blocks()) return false;
                any = true;
            }
        }
        return any;
    }

    /** Walks the avatar out through the docked port of the craft it is aboard, until it is aboard something else. */
    private boolean walkOut(float seconds) {
        Entity from = demo.aboard();
        Side out = DockingPortLayout.facing(mount(from, dockedPort(from)));
        PlayerAvatarComponent walker = demo.avatar.getComponent(PlayerAvatarComponent.class);
        GridPositionComponent pos = demo.avatar.getComponent(GridPositionComponent.class);
        DockingPort port = dockedPort(from);
        for (int i = 0; i < seconds * 60 && demo.aboard() == from; i++) {
            // Toward the opening's middle across, out along the facing
            MountFootprint m = mount(from, port);
            float cx = m.originX + m.width() / 2f, cy = m.originY + m.height() / 2f;
            walker.moveX = out.dx != 0 ? out.dx : Math.signum(cx - pos.x) * Math.min(1f, Math.abs(cx - pos.x) * 4f);
            walker.moveY = out.dy != 0 ? out.dy : Math.signum(cy - pos.y) * Math.min(1f, Math.abs(cy - pos.y) * 4f);
            demo.update(FRAME);
        }
        walker.moveX = 0f;
        walker.moveY = 0f;
        return demo.aboard() != from;
    }

    @Test
    void oneDirectionOnScreenWalksTheSameWayAboardEitherCraft() {
        demo.load(DockingDemoScene.Scene.WALK);
        PlayerAvatarComponent walker = demo.avatar.getComponent(PlayerAvatarComponent.class);
        // Straight out of the corvette's port, as the player sees it on screen
        DockingPort port = DockingDemoScene.port(demo.player, 0);
        com.badlogic.gdx.math.Vector2 dir = demo.world.docking.bodyOf(demo.player)
                .getWorldVector(new com.badlogic.gdx.math.Vector2(port.normalX, port.normalY)).cpy().nor();
        com.badlogic.gdx.math.Vector2 at = new com.badlogic.gdx.math.Vector2();
        float start = demo.avatarWorld(at).dot(dir);
        for (int i = 0; i < 60 * 20 && demo.aboard() == demo.player; i++) {
            DeckView.walk(walker, demo.world.docking.bodyOf(demo.aboard()), dir.x, dir.y, 1f);
            demo.update(FRAME);
        }
        assertSame(demo.target, demo.aboard(), "walked through the port");
        float crossed = demo.avatarWorld(at).dot(dir);
        assertTrue(crossed > start, "went the way the key points");
        for (int i = 0; i < 60; i++) {
            DeckView.walk(walker, demo.world.docking.bodyOf(demo.aboard()), dir.x, dir.y, 1f);
            demo.update(FRAME);
        }
        assertSame(demo.target, demo.aboard());
        assertTrue(demo.avatarWorld(at).dot(dir) > crossed + 0.5f, "and keeps going that way aboard the station");
    }

    @Test
    void dockingWithClearanceOpensBothDoorsAndJoinsTheOpenings() {
        demo.load(DockingDemoScene.Scene.CLEARANCE);
        assertFalse(doorsOpen(demo.player), "the doors start shut");
        demo.docking.toggleArm();
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        run(1f);
        assertTrue(doorsOpen(demo.player), "the corvette opens its door");
        assertTrue(doorsOpen(demo.target), "the outpost opens its door for a craft it gave clearance");
        int opening = DockingPortLayout.openingWidth(mount(demo.player, dockedPort(demo.player)));
        assertEquals(opening, grid(demo.player).links.size);
        assertEquals(opening, grid(demo.target).links.size);
        assertTrue(grid(demo.player).links.first().canCross());
        assertFalse(grid(demo.player).isVenting(), "open to the station, not to space");
        assertFalse(grid(demo.target).isVenting());
    }

    @Test
    void aStationThatRefusedKeepsItsDoorShut() {
        demo.load(DockingDemoScene.Scene.REFUSED);
        demo.docking.toggleArm();
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        run(1f);
        assertTrue(doorsOpen(demo.player), "the corvette armed its own port, so its door opens");
        assertFalse(doorsOpen(demo.target), "the outpost never agreed: its door stays shut");
        assertFalse(grid(demo.player).links.first().canCross());
        assertFalse(grid(demo.player).isVenting(), "the corvette's open door faces the outpost's shut one");
    }

    @Test
    void airMovesBetweenTheCraftThroughTheOpenPorts() {
        demo.load(DockingDemoScene.Scene.WALK);
        Compartment corvette = grid(demo.player).compartmentAt(
                DockingDemoScene.insidePort(demo.player, 0)[0], DockingDemoScene.insidePort(demo.player, 0)[1]);
        for (Compartment c : grid(demo.player).compartments) {
            c.oxygen *= 0.5;
            c.nitrogen *= 0.5;
            c.carbonDioxide *= 0.5;
        }
        double before = corvette.pressure();
        run(20f);
        assertTrue(corvette.pressure() > before + 5, "the station's air comes aboard: " + before + " -> " + corvette.pressure());
        assertFalse(grid(demo.player).isVenting());
    }

    @Test
    void theAvatarWalksOntoTheStationAndBackThenUndocks() {
        demo.load(DockingDemoScene.Scene.WALK);
        assertTrue(demo.world.docking.isRigid(demo.player));
        assertTrue(doorsOpen(demo.player) && doorsOpen(demo.target));
        assertSame(demo.player, demo.aboard());

        assertTrue(walkOut(20f), "walked through the port");
        assertSame(demo.target, demo.aboard(), "aboard the outpost");
        GridPositionComponent pos = demo.avatar.getComponent(GridPositionComponent.class);
        assertTrue(grid(demo.target).getTile(pos.tileX(), pos.tileY()).isOpen(), "standing on the outpost's floor");
        assertTrue(demo.docking.undock().startsWith("You're aboard Outpost Kepler"), "no undocking from the station's deck");
        assertSame(demo.target, demo.world.docking.dockedTo(demo.player));

        assertTrue(walkOut(20f), "walked back");
        assertSame(demo.player, demo.aboard());
        assertEquals("Undocked from Outpost Kepler", demo.docking.undock());
        run(1f);
        assertEquals(0, grid(demo.player).links.size, "the walkway is gone");
        assertEquals(0, grid(demo.target).links.size);
        DockingPort mine = DockingDemoScene.port(demo.player, 0);
        MountFootprint m = mount(demo.player, mine);
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                if (DockingPortLayout.isDoorTile(m, x, y)) {
                    assertTrue(DockingPortLayout.door(grid(demo.player), m, x, y).blocks(), "undocking shuts the door");
                }
            }
        }
        assertFalse(grid(demo.player).isVenting());
    }
}
