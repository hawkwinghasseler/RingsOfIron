package com.ringsofiron.interiorgrid.editor;

import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.EscapePodLayout;
import com.ringsofiron.interiorgrid.HullMounts;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Escape pods sit through the outer hull: the hatch end overlaps the hull and the rest lies inside,
 * with {@link HullMounts#CLEARANCE} tiles of open space beyond the hatch to launch into.
 */
class EditorEscapePodTest {
    static final Path ASSETS = Paths.get("../assets");

    private static ChassisBlueprintLoader.LoadResult load(ChassisModel m, ShipPartCatalog catalog) {
        return ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(m.toDef(catalog), catalog));
    }

    @Test
    void everyShipsPodsSitThroughTheHullWithAClearLaunchPath() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        List<String> problems = new ArrayList<>();
        int pods = 0;
        for (EditorProject.Item<ChassisModel> item : project.chassis.values()) {
            EditorProject.Check check = project.check(item.value);
            if (check.error != null) {
                problems.add(item.id + ": " + check.error);
                continue;
            }
            ShipGridComponent g = check.result.grid;
            for (ChassisBlueprintLoader.SubsystemPlacement p : check.result.subsystems) {
                if (p.type != SubsystemComponent.SubsystemType.ESCAPE_POD) continue;
                pods++;
                MountFootprint f = p.footprint;
                Side out = HullMounts.facing(f);
                int inHull = 0;
                for (int y = f.originY; y < f.originY + f.height(); y++)
                    for (int x = f.originX; x < f.originX + f.width(); x++)
                        if (g.getTile(x, y).wallType == Tile.WallType.HULL) inHull++;
                int hatch = out.dx == 0 ? f.width() : f.height();
                if (inHull != hatch) problems.add(item.id + ": the pod at " + f.originX + "," + f.originY + " has " + inHull + " hull tiles, not " + hatch);
                String clear = EscapePodLayout.clearance(g, f);
                if (clear != null) problems.add(item.id + ": " + clear);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(pods >= 40, "checked the fleet's pods, found " + pods);
    }

    /** Why the layout won't load, or null. */
    private static String error(ChassisModel m, ShipPartCatalog catalog) {
        try {
            load(m, catalog);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    /** A 12 x 10 layout with a hull border; the bottom hull row is y = 0. */
    private static ChassisModel blank(ShipPartCatalog catalog) {
        return ChassisModel.blank("test", 12, 10, catalog);
    }

    @Test
    void aPodOverlapsTheHullWithItsHatchEnd() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = blank(catalog);
        // Small pod facing -y: hatch end on the bottom hull at (5, 0), its bay at (5, 1)
        m.place("escape_pod", MountFootprint.MountSize.S, 5, 0, 2, catalog);
        assertEquals('#', m.tiles[0][5], "the hull under the hatch end stays");
        assertEquals('.', m.tiles[1][5]);
        ChassisDef def = m.toDef(catalog);
        assertEquals(1, def.mounts.size(), "a pod is always listed, so the hull under it is kept");

        ChassisBlueprintLoader.LoadResult r = load(m, catalog);
        ShipGridComponent g = r.grid;
        assertEquals(Tile.WallType.HULL, g.getTile(5, 0).wallType);
        assertNotNull(g.getTile(5, 0).mount, "the hull tile belongs to the pod");
        assertTrue(g.getTile(5, 1).isFloor());
        MountFootprint f = r.subsystems.first().footprint;
        assertArrayEquals(new int[]{5, 1}, EscapePodLayout.bayTile(f), "crew make for the tile inside");
    }

    @Test
    void aPodInsideTheHullOrOutsideItIsRefused() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel inside = blank(catalog);
        inside.place("escape_pod", MountFootprint.MountSize.S, 5, 1, 2, catalog);
        assertNotNull(error(inside, catalog), "a pod wholly inside the hull");

        ChassisModel backwards = blank(catalog);
        backwards.place("escape_pod", MountFootprint.MountSize.S, 5, 0, 0, catalog);
        String error = error(backwards, catalog);
        assertNotNull(error, "a pod with its hatch end facing into the ship");

        ChassisModel sideways = blank(catalog);
        sideways.place("escape_pod", MountFootprint.MountSize.S, 0, 4, 1, catalog);
        assertNull(error(sideways, catalog), "out through the left hull");
    }

    @Test
    void aPodNeedsFiveTilesOfOpenSpaceToLaunchInto() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        String[] rows = {
                "#####",
                "#...#",
                "#...#",
                "#####",
                "     ",
                "     ",
                "  #  ",
        };
        ChassisBlueprint bp = new ChassisBlueprint(rows).catalog(catalog)
                .mount(new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.S, 2, 3, 2), SubsystemComponent.SubsystemType.ESCAPE_POD);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ChassisBlueprintLoader.load(bp));
        assertTrue(e.getMessage().contains("2,0"), e.getMessage());

        // Off the edge of the layout is open space
        ChassisBlueprint clear = new ChassisBlueprint(new String[]{"#####", "#...#", "#...#", "#####"}).catalog(catalog)
                .mount(new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.S, 2, 0, 2), SubsystemComponent.SubsystemType.ESCAPE_POD);
        assertEquals(1, ChassisBlueprintLoader.load(clear).subsystems.size);
    }

    @Test
    void anRcsBlockOnTheHatchEndIsInTheWay() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = blank(catalog);
        assertNull(m.toggleRcs(5, 0, Side.BOTTOM, "LINEAR", catalog));
        m.place("escape_pod", MountFootprint.MountSize.S, 5, 0, 2, catalog);
        String error = error(m, catalog);
        assertNotNull(error);
        assertTrue(error.contains("5,0"), error);
    }

    @Test
    void aPodInTheHullFacesSpaceAndKeepsTheHullUnderItsHatch() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(new ChassisBlueprint(new String[]{
                "##333##",
                "#.....#",
                "#.....#",
                "#######",
        }).catalog(catalog).mount("escape_pod", MountFootprint.MountSize.S, 3, 2));
        MountFootprint f = r.subsystems.first().footprint;
        assertEquals(Side.TOP, HullMounts.facing(f));
        Tile hatch = r.grid.getTile(3, 3);
        assertEquals(Tile.WallType.HULL, hatch.wallType);
        assertEquals(r.grid.getTile(2, 3).weightClass, hatch.weightClass, "the hull the rows lay under it");
        assertTrue(r.grid.getTile(3, 2).isFloor());
    }

    @Test
    void aPodCannotBeTurnedInPlace() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = blank(catalog);
        ChassisModel.Mount pod = m.place("escape_pod", MountFootprint.MountSize.S, 5, 0, 2, catalog);
        assertNotNull(m.rotate(pod, catalog));
        assertEquals(2, pod.rotation);
    }
}
