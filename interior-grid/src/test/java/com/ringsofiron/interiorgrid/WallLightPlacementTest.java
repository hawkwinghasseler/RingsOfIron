package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Wall light auto-placement on real layouts: clear of systems, every room lit, and redone when the layout changes. */
class WallLightPlacementTest {

    @Test
    void lightsDoNotHangOnTilesWhereSystemsStand() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########");
        // A reactor along the whole left end of the room
        bp.mount(new MountFootprint(MountFootprint.MountKind.PRIMARY, MountFootprint.MountSize.M, 1, 1, 0),
                SubsystemComponent.SubsystemType.REACTOR);
        ShipGridComponent g = ChassisBlueprintLoader.load(bp).grid;
        Array<WallLight> lights = WallLightLayout.of(g);
        assertFalse(lights.isEmpty());
        for (WallLight l : lights) assertNull(g.getTile(l.x, l.y).mount, "not behind machinery: " + l);
    }

    @Test
    void placementIsTheSameEveryTime() {
        ShipGridComponent g = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "#..........#",
                "############")).grid;
        assertEquals(WallLightLayout.of(g), WallLightLayout.of(g));
    }

    /** Every room of every chassis in the game, auto-placed, has a light in it. */
    @Test
    void everyRoomOfEveryChassisGetsALight() {
        EditorProject project = EditorProject.load(Paths.get("../assets"), null);
        ShipPartCatalog catalog = project.catalog();
        for (EditorProject.Item<ChassisModel> item : project.chassis.values()) {
            ChassisModel m = item.value.copy();
            m.lights = null;
            ShipGridComponent g = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(m.toDef(catalog), catalog)).grid;
            assertNull(g.wallLights, "left to the game");
            int[] room = WallLightLayout.rooms(g);
            Set<Integer> lit = new HashSet<>();
            for (WallLight l : WallLightLayout.lightsOf(g)) lit.add(room[l.y * g.width + l.x]);
            Set<Integer> needs = new HashSet<>();
            for (int y = 0; y < g.height; y++) {
                for (int x = 0; x < g.width; x++) {
                    int r = room[y * g.width + x];
                    if (r >= 0 && g.getTile(x, y).mount == null && hasWall(g, x, y)) needs.add(r);
                }
            }
            needs.removeAll(lit);
            assertTrue(needs.isEmpty(), item.id + ": rooms left dark " + needs);
        }
    }

    @Test
    void theGameWorksTheLightsOutAgainWhenTheLayoutChanges() {
        ShipGridComponent g = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "#####",
                "#...#",
                "#...#",
                "#####")).grid;
        Array<WallLight> first = WallLightLayout.lightsOf(g);
        assertSame(first, WallLightLayout.lightsOf(g), "kept while the layout holds");
        g.layoutRevision++;
        assertNotSame(first, WallLightLayout.lightsOf(g), "worked out again after a change");
        // A layout that says where its lights go is taken at its word
        Array<WallLight> placed = new Array<>();
        placed.add(new WallLight(1, 1, Side.LEFT));
        g.wallLights = placed;
        assertSame(placed, WallLightLayout.lightsOf(g));
    }

    private static boolean hasWall(ShipGridComponent g, int x, int y) {
        for (Side s : Side.values()) {
            EdgeWall e = g.getEdge(x, y, s);
            if (e != null && !e.hasBulkhead) return true;
            Tile beyond = g.getTile(x + s.dx, y + s.dy);
            if (e == null && beyond != null && beyond.isIntactWall()) return true;
        }
        return false;
    }
}
