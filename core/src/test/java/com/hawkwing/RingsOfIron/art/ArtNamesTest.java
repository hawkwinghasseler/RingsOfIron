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
        for (String material : new String[]{"hull_steel", "composite_armor"}) {
            for (WeightClass w : WeightClass.values()) {
                for (int[] size : new int[][]{{1, 1}, {1, 2}, {2, 1}}) {
                    // Every size has art of its own, so no wedge is drawn mirrored
                    String[] names = ArtNames.wedge(new HullPiece(HullPiece.Shape.WEDGE, 0, 0, size[0], size[1], 0, w, material));
                    assertArt(names[0]);
                    assertTrue(!ArtNames.wedgeMirrored(names[0], size[0], size[1]));
                }
            }
        }
    }

    @Test
    void eachHullMaterialHasItsOwnWallArt() {
        Tile t = new Tile();
        t.wallType = Tile.WallType.HULL;
        for (WeightClass w : WeightClass.values()) {
            t.weightClass = w;
            t.material = "composite_armor";
            assertEquals("composite_armor_" + ArtNames.weight(w), ArtNames.wallId(t));
            for (int mask = 0; mask < 16; mask++) assertArt(ArtNames.wall(t, mask)[0]);
            for (boolean h : new boolean[]{true, false}) assertArt(ArtNames.edgeWall("composite_armor", w, h)[0]);
            for (int mask = 1; mask < 16; mask++) assertArt(ArtNames.edgeJoint("composite_armor", w, mask)[0]);
            t.material = "hull_steel";
            assertEquals("hull_" + ArtNames.weight(w), ArtNames.wallId(t));
            assertEquals(1, ArtNames.wall(t, 0).length);
        }
        // A material without art of its own falls back to hull steel's
        t.material = "unobtainium";
        t.weightClass = WeightClass.HEAVY;
        assertArrayEquals(new String[]{"walls/unobtainium_heavy_ns", "walls/hull_heavy_ns"},
                ArtNames.wall(t, ArtNames.N | ArtNames.S));
        assertTrue(ArtNames.wedgeMirrored("walls/hull_heavy_wedge_1x2", 2, 1));
    }

    @Test
    void everySystemHasDamagedAndDestroyedArt() {
        assertEquals(ArtNames.Condition.INTACT, ArtNames.condition(0.6f));
        assertEquals(ArtNames.Condition.DAMAGED, ArtNames.condition(0.3f));
        assertEquals(ArtNames.Condition.DESTROYED, ArtNames.condition(0f));
        for (SubsystemComponent.SubsystemType type : SubsystemComponent.SubsystemType.values()) {
            for (MountFootprint.MountSize size : MountFootprint.MountSize.values()) {
                if (!size.exists(type.mountKind)) continue;
                for (ArtNames.Condition c : new ArtNames.Condition[]{ArtNames.Condition.DAMAGED, ArtNames.Condition.DESTROYED}) {
                    for (boolean powered : new boolean[]{true, false}) {
                        // Passive cargo has no unpowered look, so its candidates go on to the plain damaged art
                        String state = "_" + c.name().toLowerCase(java.util.Locale.ROOT);
                        boolean found = false;
                        for (String n : ArtNames.component(null, type, size, null, powered, c)) {
                            if (n.contains(state) && exists(n)) found = true;
                        }
                        assertTrue(found, "no " + state + " art for " + type + " " + size);
                    }
                }
            }
        }
        assertArrayEquals(new String[]{"components/reactor_m_destroyed", "components/reactor_m_unpowered", "components/reactor_m"},
                ArtNames.component(null, SubsystemComponent.SubsystemType.REACTOR, MountFootprint.MountSize.M, null, true,
                        ArtNames.Condition.DESTROYED));
    }

    @Test
    void everyEngineSlotSizeHasEngineAndExhaustArt() {
        for (MountFootprint.MountSize slot : MountFootprint.MountSize.values()) {
            String size = ArtNames.engineSize(slot);
            for (boolean burning : new boolean[]{true, false}) assertArt(ArtNames.engine(size, burning)[0]);
            assertArt(ArtNames.exhaust(size));
            assertArt(ArtNames.component(null, SubsystemComponent.SubsystemType.ENGINE, slot, null, false)[0]);
        }
        assertArt(ArtNames.exhaust(ArtNames.ENGINE_SIZE));
    }

    @Test
    void everyTileTypesArtKeyNamesArt() throws IOException {
        String text = new String(Files.readAllBytes(ASSETS.resolve("data/tiles.toml")), java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^art\\s*=\\s*\"([^\"]+)\"").matcher(text);
        int n = 0;
        while (m.find()) {
            String art = m.group(1);
            // a floor's region, or a wall family that adds join sides
            assertTrue(exists(art) || exists(art + "_0"), "tiles.toml art " + art + " has no SVG");
            n++;
        }
        assertTrue(n > 0);
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
            assertArt(ArtNames.conduit(powered));
            assertArt(ArtNames.conduitArm(powered));
        }
        assertNull(ArtNames.covering(FloorCovering.NONE));
        for (FloorCovering c : FloorCovering.values()) if (c != FloorCovering.NONE) assertArt(ArtNames.covering(c));
    }

    /**
     * What is drawn over a tile's floor (a conduit's junction, every system) has no steel deck plate
     * of its own, so carpet or any other covering under it stays in sight.
     */
    @Test
    void artOverTheFloorLeavesTheFloorInSight() throws IOException {
        // The plate in the middle of tiles/floor.svg: one per deck tile drawn
        String plate = "rx=\"2\" fill=\"#444e5b\"";
        assertTrue(new String(Files.readAllBytes(ART.resolve("tiles/floor.svg"))).contains(plate));
        try (java.util.stream.Stream<Path> files = Files.list(ART.resolve("components"))) {
            for (Path f : (Iterable<Path>) files::iterator) {
                assertTrue(!new String(Files.readAllBytes(f)).contains(plate), f.getFileName() + " draws its own deck");
            }
        }
        for (boolean powered : new boolean[]{true, false}) {
            Path f = ART.resolve(ArtNames.conduit(powered) + ".svg");
            assertTrue(!new String(Files.readAllBytes(f)).contains(plate), f.getFileName() + " draws its own deck");
        }
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
        for (String e : new String[]{"breach", "bulkhead_seal", "hull_patch", "short_circuit", "propellant_spill"}) assertArt("effects/" + e);
        assertArt(ArtNames.wallLight(true));
        assertArt(ArtNames.wallLight(false));
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

    @Test
    void crewProjectilesAndCombatEffectsHaveArt() throws IOException {
        for (ArtNames.Crew who : ArtNames.Crew.values()) {
            assertArt(ArtNames.crew(who, false));
            assertArt(ArtNames.crew(who, true));
            for (ArtNames.CrewPose pose : ArtNames.CrewPose.values()) {
                assertArt(ArtNames.crew(who, pose, false));
                assertArt(ArtNames.crew(who, pose, true));
            }
        }
        // Each wound, over a standing figure and one down
        com.ringsofiron.aicrew.CrewMemberComponent c = new com.ringsofiron.aicrew.CrewMemberComponent();
        for (com.ringsofiron.aicrew.Wound wound : com.ringsofiron.aicrew.Wound.values()) {
            for (boolean stabilised : new boolean[]{false, true}) {
                c.wound = wound;
                c.stabilised = stabilised;
                for (ArtNames.CrewPose pose : ArtNames.CrewPose.values()) {
                    String name = ArtNames.crewWound(c, pose);
                    if (wound == com.ringsofiron.aicrew.Wound.NONE || pose == ArtNames.CrewPose.DEAD) assertNull(name);
                    else assertArt(name);
                }
            }
        }
        // Each round and missile in data finds its art, through its art key or its id
        String weapons = new String(Files.readAllBytes(ASSETS.resolve("data/weapons.toml")), java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("^\\[(projectile|missile)\\.([a-z0-9_]+)]\\s*\\n(?:art = \"([a-z0-9_]+)\")?", Pattern.MULTILINE)
                .matcher(weapons);
        int found = 0;
        while (m.find()) {
            assertAnyArt(ArtNames.projectile(m.group(3), m.group(2), m.group(1).equals("missile")));
            found++;
        }
        assertTrue(found >= 3, "rounds and missiles in weapons.toml");
        assertArt(ArtNames.DECOY);
        assertArt(ArtNames.BEAM);
        assertArt(ArtNames.WALKWAY);
        assertEquals("effects/explosion_1", ArtNames.explosion(0f));
        assertEquals("effects/explosion_4", ArtNames.explosion(1f));
        for (float t = 0f; t <= 1f; t += 0.1f) assertArt(ArtNames.explosion(t));
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
