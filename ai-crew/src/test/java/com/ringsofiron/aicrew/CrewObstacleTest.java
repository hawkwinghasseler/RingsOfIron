package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;
import com.ringsofiron.interiorgrid.ChassisBlueprint;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import org.junit.jupiter.api.Test;

/** Big systems and stacked cargo are solid; consoles and empty bays are floor. */
class CrewObstacleTest {

    static MountFootprint mount(TestShips s, SubsystemComponent.SubsystemType type) {
        for (MountFootprint m : s.grid.mounts) {
            if (m.installed.getComponent(SubsystemComponent.class).type == type) return m;
        }
        throw new AssertionError("no " + type);
    }

    static boolean on(MountFootprint m, int x, int y) {
        return x >= m.originX && x < m.originX + m.width() && y >= m.originY && y < m.originY + m.height();
    }

    @Test
    void crewWalkRoundAReactorAndOverAConsole() {
        TestShips s = new TestShips(
                new ChassisBlueprint("###########",
                "#.........#",
                "#.........#",
                "#.........#",
                "#.........#",
                "#.........#",
                "###########")
                .mount("helm", MountFootprint.MountSize.S, 7, 2)
                .mount("reactor", MountFootprint.MountSize.M, 4, 3)
                .conduitRun(5, 3, 7, 3));
        MountFootprint reactor = mount(s, SubsystemComponent.SubsystemType.REACTOR);
        MountFootprint helm = mount(s, SubsystemComponent.SubsystemType.HELM);
        GridNavGraph nav = s.crew.pathfinder(s.grid).graph;
        assertFalse(nav.isWalkable(reactor.originX, reactor.originY), "a reactor fills its tiles");
        assertTrue(nav.isWalkable(helm.originX, helm.originY), "crew stand at a console");
        int y = reactor.originY;
        Array<GridNode> path = new Array<>();
        assertTrue(s.crew.pathfinder(s.grid).findPath(1, y, 8, y, path));
        for (GridNode n : path) assertFalse(on(reactor, n.x, n.y), "went round it, not over " + n.x + "," + n.y);
    }

    @Test
    void theAvatarBumpsIntoAReactor() {
        TestShips s = new TestShips(
                new ChassisBlueprint("#########",
                "#.......#",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########")
                .mount("reactor", MountFootprint.MountSize.M, 4, 2));
        MountFootprint reactor = mount(s, SubsystemComponent.SubsystemType.REACTOR);
        Entity me = CrewFactory.spawnPlayer(s.crew, s.ship, "Me", 1, reactor.originY);
        me.getComponent(PlayerAvatarComponent.class).moveX = 1f;
        s.run(3f);
        assertEquals(reactor.originX - CrewBody.RADIUS, s.p(me).x, 0.02f, "stopped against it");
    }

    @Test
    void stackedCargoBlocksAndAnEmptyBayIsFloor() {
        TestShips s = new TestShips(
                new ChassisBlueprint("########",
                "#......#",
                "#......#",
                "#......#",
                "#......#",
                "#......#",
                "########")
                .mount("cargo", MountFootprint.MountSize.M, 3, 2));
        MountFootprint bay = mount(s, SubsystemComponent.SubsystemType.CARGO);
        int[] tiles = new int[bay.width() * bay.height() * 2];
        int k = 0;
        for (int y = bay.originY; y < bay.originY + bay.height(); y++) {
            for (int x = bay.originX; x < bay.originX + bay.width(); x++) {
                tiles[k++] = x;
                tiles[k++] = y;
            }
        }
        CargoHoldComponent hold = new CargoHoldComponent(s.grid.tileSize, tiles);
        s.ship.add(hold);
        assertTrue(s.crew.pathfinder(s.grid).graph.isWalkable(bay.originX, bay.originY), "empty");
        hold.load("water", 1, 1.0, 1000.0);
        int sx = hold.bayX[0], sy = hold.bayY[0];
        assertNotNull(hold.stackAt(sx, sy));
        GridNavGraph nav = s.crew.pathfinder(s.grid).graph;
        assertFalse(nav.isWalkable(sx, sy), "goods stacked there");
        int free = 0;
        for (int i = 0; i < hold.bayTileCount(); i++) if (nav.isWalkable(hold.bayX[i], hold.bayY[i])) free++;
        assertEquals(hold.bayTileCount() - 1, free, "the rest of the bay is still floor");
    }
}
