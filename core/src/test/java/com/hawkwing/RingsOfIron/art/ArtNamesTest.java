package com.hawkwing.RingsOfIron.art;

import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every name the game asks the atlas for has an SVG in assets/art. */
class ArtNamesTest {
    private static final Path ASSETS = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    private static final Path ART = ASSETS.resolve("art");

    private static boolean exists(String region) {
        return Files.isRegularFile(ART.resolve(region + ".svg"));
    }

    private static void assertArt(String region) {
        assertTrue(exists(region), "no art for " + region);
    }

    private static void assertAnyArt(String... candidates) {
        for (String c : candidates) if (exists(c)) return;
        assertTrue(false, "no art for any of " + String.join(", ", candidates));
    }

    @Test
    void sidesFollowTheFileNames() {
        assertEquals("0", ArtNames.sides(0));
        assertEquals("nesw", ArtNames.sides(ArtNames.N | ArtNames.E | ArtNames.S | ArtNames.W));
        assertEquals("sw", ArtNames.sides(ArtNames.W | ArtNames.S));
    }

    @Test
    void everyWallJoinHasArt() {
        Tile t = new Tile();
        for (Tile.WallType type : new Tile.WallType[]{Tile.WallType.HULL, Tile.WallType.INTERNAL}) {
            for (WeightClass w : WeightClass.values()) {
                t.wallType = type;
                t.weightClass = w;
                for (int mask = 0; mask < 16; mask++) assertArt(ArtNames.wall(ArtNames.wallId(t), mask));
            }
        }
        for (WeightClass w : WeightClass.values()) {
            for (int[] size : new int[][]{{1, 1}, {1, 2}}) {
                assertArt(ArtNames.wedge(new HullPiece(HullPiece.Shape.WEDGE, 0, 0, size[0], size[1], 0, w, "hull_steel")));
            }
        }
    }

    @Test
    void everyEdgeWallJointAndDoorHasArt() {
        for (WeightClass w : WeightClass.values()) {
            assertArt(ArtNames.edgeWall(w, true));
            assertArt(ArtNames.edgeWall(w, false));
            assertNull(ArtNames.edgeJoint(w, 0));
            for (int mask = 1; mask < 16; mask++) assertArt(ArtNames.edgeJoint(w, mask));
        }
        for (boolean sealed : new boolean[]{true, false}) {
            for (boolean powered : new boolean[]{true, false}) {
                assertArt(ArtNames.door(sealed, powered, true));
                assertArt(ArtNames.door(sealed, powered, false));
            }
        }
    }

    @Test
    void floorsConduitsAndCoveringsHaveArt() {
        Tile t = new Tile();
        t.floorType = Tile.FloorType.FLOOR;
        assertArt(ArtNames.floor(t));
        t.floorType = Tile.FloorType.POWER_CONDUIT;
        for (boolean powered : new boolean[]{true, false}) {
            t.isPowered = powered;
            assertArt(ArtNames.floor(t));
            assertArt(ArtNames.conduitArm(powered));
        }
        assertNull(ArtNames.covering(FloorCovering.NONE));
        for (FloorCovering c : FloorCovering.values()) if (c != FloorCovering.NONE) assertArt(ArtNames.covering(c));
    }

    @Test
    void everySystemInEveryMountSizeHasArt() {
        for (SubsystemComponent.SubsystemType type : SubsystemComponent.SubsystemType.values()) {
            for (MountFootprint.MountSize size : MountFootprint.MountSize.values()) {
                if (!size.exists(type.mountKind)) continue;
                for (boolean powered : new boolean[]{true, false}) {
                    assertAnyArt(ArtNames.component(null, type, size, null, powered));
                    if (type == SubsystemComponent.SubsystemType.DOCKING_PORT) {
                        assertAnyArt(ArtNames.component(null, type, size, "open", powered));
                        assertArt(ArtNames.collar(size, powered));
                    }
                }
            }
        }
        // Powered systems light up and go dark; passive cargo has one look
        assertArt(ArtNames.component(null, SubsystemComponent.SubsystemType.REACTOR, MountFootprint.MountSize.XL, null, false)[0]);
        assertEquals(1, ArtNames.component(null, SubsystemComponent.SubsystemType.CARGO, MountFootprint.MountSize.S, null, true).length);
    }

    @Test
    void aComponentMadeInDataFallsBackToItsBehavior() {
        String[] names = ArtNames.component("fusion_core", SubsystemComponent.SubsystemType.REACTOR,
                MountFootprint.MountSize.L, null, false);
        assertArrayEquals(new String[]{"components/fusion_core_l_unpowered", "components/fusion_core_l",
                "components/reactor_l_unpowered", "components/reactor_l"}, names);
    }

    @Test
    void effectsAndCargoHaveArt() {
        for (Tile.FireSize f : Tile.FireSize.values()) if (f != Tile.FireSize.NONE) assertArt(ArtNames.fire(f));
        assertNull(ArtNames.damage(1f));
        assertArt(ArtNames.damage(0.4f));
        assertArt(ArtNames.damage(0.1f));
        assertNull(ArtNames.cargoStack(0f));
        assertEquals("interactive/cargo_stack_1", ArtNames.cargoStack(0.2f));
        assertEquals("interactive/cargo_stack_3", ArtNames.cargoStack(1f));
        for (float fill : new float[]{0.2f, 0.5f, 1f}) assertArt(ArtNames.cargoStack(fill));
        for (String e : new String[]{"breach", "bulkhead_seal"}) assertArt("effects/" + e);
    }

    @Test
    void everyChassisAndWeaponInDataHasExteriorArt() throws IOException {
        for (String chassis : ids(ASSETS.resolve("data/chassis.toml"), "chassis")) assertArt(ArtNames.hull(chassis));
        for (String weapon : ids(ASSETS.resolve("data/weapons.toml"), "weapon")) {
            assertAnyArt(ArtNames.turret(weapon, true));
            assertAnyArt(ArtNames.turret(weapon, false));
        }
        assertArt("hulls/thruster_plume");
    }

    private static Iterable<String> ids(Path toml, String table) throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("^\\[" + table + "\\.([a-z0-9_]+)]", Pattern.MULTILINE)
                .matcher(new String(Files.readAllBytes(toml), java.nio.charset.StandardCharsets.UTF_8));
        while (m.find()) out.add(m.group(1));
        assertTrue(!out.isEmpty(), "no " + table + " in " + toml);
        return out;
    }
}
