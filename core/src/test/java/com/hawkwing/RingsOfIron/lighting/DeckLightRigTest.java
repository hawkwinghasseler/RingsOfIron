package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.interiorgrid.WallLightLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a deck's lights go, and when they are on. */
class DeckLightRigTest {
    static final EmissiveLights ART = EmissiveLightsTest.art();

    /** A room with a 2x2 reactor and a 2x3 helm turned on its side. */
    static ShipGridComponent room() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########")
                .mount("reactor", MountFootprint.MountSize.M, 2, 3);
        bp.mounts.add(new ChassisBlueprint.MountDef(
                new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.M, 5, 1, 1),
                SubsystemComponent.SubsystemType.HELM));
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity sys = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            sub.isOperational = true;
            sys.add(sub);
            p.footprint.installed = sys;
        }
        return r.grid;
    }

    static MountFootprint mount(ShipGridComponent grid, SubsystemComponent.SubsystemType type) {
        for (MountFootprint m : grid.mounts) {
            if (m.installed.getComponent(SubsystemComponent.class).type == type) return m;
        }
        throw new AssertionError("no " + type);
    }

    static boolean inside(LightSpot s, ShipGridComponent g, MountFootprint m) {
        float x0 = g.gridOriginLocal.x + m.originX * g.tileSize, y0 = g.gridOriginLocal.y + m.originY * g.tileSize;
        return s.x >= x0 && s.x <= x0 + m.width() * g.tileSize && s.y >= y0 && s.y <= y0 + m.height() * g.tileSize;
    }

    @Test
    void aRunningReactorLightsTheRoomFromItsCore() {
        ShipGridComponent g = room();
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        MountFootprint reactor = mount(g, SubsystemComponent.SubsystemType.REACTOR);
        LightSpot core = null;
        int casting = 0;
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            if (!inside(s, g, reactor)) continue;
            assertTrue(s.on);
            if (s.casts()) casting++;
            if (core == null || s.reach > core.reach) core = s;
        }
        assertNotNull(core);
        float cx = g.gridOriginLocal.x + (reactor.originX + 1f) * g.tileSize;
        float cy = g.gridOriginLocal.y + (reactor.originY + 1f) * g.tileSize;
        assertEquals(cx, core.x, 1e-3f);
        assertEquals(cy, core.y, 1e-3f);
        assertTrue(core.reach > 2f * g.tileSize, "the core lights the room around it: " + core.reach);
        assertTrue(casting >= 1 && casting <= DeckLightRig.CASTS_PER_SYSTEM, "only the biggest parts cast: " + casting);
        assertTrue(DeckLightRig.hasPower(g));
    }

    @Test
    void aSystemLightsTheRoomFromAFewPartsTheMarkedOnesFirst() {
        // A helm whose art marks every screen as a light, besides a bigger unmarked part
        StringBuilder lights = new StringBuilder("{\"x\": 64, \"y\": 96, \"w\": 60, \"h\": 60, \"color\": \"#ffffff\", \"alpha\": 1}");
        for (int i = 0; i < 6; i++) {
            lights.append(", {\"x\": ").append(20 + 16 * i).append(", \"y\": 40, \"w\": 10, \"h\": 10, \"color\": \"#66ccff\", ")
                    .append("\"alpha\": 1, \"light\": \"point\", \"reach\": 2.5, \"intensity\": 0.5}");
        }
        EmissiveLights art = EmissiveLights.parse("{\"components/helm_m\": {\"canvas\": [0, 0, 128, 192], \"lights\": [" + lights + "]}}");
        ShipGridComponent g = room();
        LightSpots spots = new LightSpots();
        new DeckLightRig(art).collect(g, spots);
        int casting = 0;
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            if (!s.casts()) continue;
            casting++;
            assertEquals(2.5f * g.tileSize, s.reach, 1e-4f, "a marked screen, not the bigger unmarked part");
        }
        assertEquals(DeckLightRig.CASTS_PER_SYSTEM, casting);
        assertEquals(7, spots.size(), "the rest still glow");
    }

    @Test
    void aTurnedSystemsLightsTurnWithIt() {
        ShipGridComponent g = room();
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        MountFootprint helm = mount(g, SubsystemComponent.SubsystemType.HELM);
        assertEquals(3, helm.width(), "turned on its side");
        int n = 0;
        for (int i = 0; i < spots.size(); i++) if (inside(spots.get(i), g, helm)) n++;
        assertEquals(ART.get("components/helm_m").lights.length, n, "every helm light lands on its footprint");
    }

    @Test
    void rotationTurnsArtCounterClockwiseWithinItsFootprint() {
        EmissiveLights.Art a = ART.get("components/helm_m"); // 2 x 3 tiles
        float x = a.left + 0.5f * 64f, y = a.bottom + 2.5f * 64f; // near the top-left corner
        float[] p0 = DeckLightRig.rotated(a, x, y, 0), p1 = DeckLightRig.rotated(a, x, y, 1);
        float[] p2 = DeckLightRig.rotated(a, x, y, 2), p3 = DeckLightRig.rotated(a, x, y, 3);
        assertArrayEquals(new float[]{0.5f, 2.5f}, p0, 1e-5f);
        assertArrayEquals(new float[]{0.5f, 0.5f}, p1, 1e-5f, "top-left turns to bottom-left");
        assertArrayEquals(new float[]{1.5f, 0.5f}, p2, 1e-5f, "then bottom-right");
        assertArrayEquals(new float[]{2.5f, 1.5f}, p3, 1e-5f, "then top-right");
    }

    @Test
    void aSystemWithoutPowerGoesDark() {
        ShipGridComponent g = room();
        MountFootprint reactor = mount(g, SubsystemComponent.SubsystemType.REACTOR);
        reactor.installed.getComponent(SubsystemComponent.class).isOperational = false;
        LightSpots spots = new LightSpots();
        DeckLightRig rig = new DeckLightRig(ART);
        rig.collect(g, spots);
        MountFootprint helm = mount(g, SubsystemComponent.SubsystemType.HELM);
        int walls = WallLightLayout.lightsOf(g).size;
        assertTrue(walls > 0);
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            if (i < walls) assertFalse(s.on, "no reactor running: the wall lights go out");
            else if (inside(s, g, reactor)) assertFalse(s.on);
            else if (inside(s, g, helm)) assertTrue(s.on, "the helm still has its own lights on");
        }
        assertFalse(DeckLightRig.hasPower(g));
    }

    @Test
    void wallLightsShineIntoTheRoomFromItsWalls() {
        ShipGridComponent g = room();
        LightSpots spots = new LightSpots();
        DeckLightRig rig = new DeckLightRig(ART);
        rig.collect(g, spots);
        int walls = WallLightLayout.lightsOf(g).size;
        for (int i = 0; i < walls; i++) {
            LightSpot s = spots.get(i);
            WallLight p = WallLightLayout.lightsOf(g).get(i);
            assertTrue(s.on && s.isCone && s.casts());
            assertEquals(6f * g.tileSize, s.reach, 1e-4f, "the art's reach, not a guess from its size");
            // Just off the wall, on its tile, shining away from it
            float tx = (s.x - g.gridOriginLocal.x) / g.tileSize, ty = (s.y - g.gridOriginLocal.y) / g.tileSize;
            assertEquals(p.x, (int) Math.floor(tx));
            assertEquals(p.y, (int) Math.floor(ty));
            float wallDist = Math.abs((tx - p.edgeX()) * p.side.dx + (ty - p.edgeY()) * p.side.dy);
            assertTrue(wallDist > 0f && wallDist < 0.3f, "hangs just off its wall: " + wallDist);
            assertEquals(-p.side.dx, Math.round(Math.cos(s.direction)), "shines away from the wall");
            assertEquals(-p.side.dy, Math.round(Math.sin(s.direction)));
        }
    }

    @Test
    void firesLightTheirTilesAndFlicker() {
        ShipGridComponent g = room();
        Tile t = g.getTile(3, 2);
        t.fireIntensity = 0.8f;
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        boolean found = false;
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            if (s.flicker < 0f) continue;
            found = true;
            assertTrue(s.casts());
            assertEquals(3, (int) Math.floor((s.x - g.gridOriginLocal.x) / g.tileSize));
            assertEquals(2, (int) Math.floor((s.y - g.gridOriginLocal.y) / g.tileSize));
        }
        assertTrue(found, "a large fire gives off light");
    }

    /** Two rooms with a reactor, split at x = 5 by a thin wall with a bulkhead door at y = 2. */
    static ShipGridComponent twoRooms(boolean sealed) {
        ChassisBlueprint bp = new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########")
                .mount("reactor", MountFootprint.MountSize.M, 1, 2);
        for (int y = 1; y <= 3; y++) bp.edgeWall(5, y, com.ringsofiron.interiorgrid.Side.LEFT,
                com.ringsofiron.interiorgrid.WeightClass.LIGHT, y == 2);
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity sys = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            sub.isOperational = true;
            sys.add(sub);
            p.footprint.installed = sys;
        }
        r.grid.getEdge(5, 2, com.ringsofiron.interiorgrid.Side.LEFT).bulkheadSealed = sealed;
        return r.grid;
    }

    /** The spots near the door at x = 5, y = 2.5 tiles that light the floor. */
    static java.util.List<LightSpot> doorLights(ShipGridComponent g, LightSpots spots) {
        java.util.List<LightSpot> out = new java.util.ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            float tx = (s.x - g.gridOriginLocal.x) / g.tileSize, ty = (s.y - g.gridOriginLocal.y) / g.tileSize;
            if (Math.abs(tx - 5f) < 0.5f && Math.abs(ty - 2.5f) < 0.1f && s.casts()) out.add(s);
        }
        return out;
    }

    @Test
    void aDoorsLampsLightTheFloorEachSideInTheirColor() {
        ShipGridComponent g = twoRooms(true);
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        java.util.List<LightSpot> lamps = doorLights(g, spots);
        assertEquals(2, lamps.size(), "one each side");
        assertTrue(lamps.get(0).x < g.gridOriginLocal.x + 5f * g.tileSize);
        assertTrue(lamps.get(1).x > g.gridOriginLocal.x + 5f * g.tileSize);
        for (LightSpot s : lamps) {
            assertTrue(s.on && s.reach <= 2f * g.tileSize);
            assertTrue(s.color.r > s.color.g, "red while shut");
        }
        assertSame(lamps.get(1), spots.get(spots.size() - 1), "doors come last, the first left out");

        g.getEdge(5, 2, com.ringsofiron.interiorgrid.Side.LEFT).bulkheadSealed = false;
        spots.clear();
        new DeckLightRig(ART).collect(g, spots);
        for (LightSpot s : doorLights(g, spots)) assertTrue(s.color.g > s.color.r, "green while open");
    }

    @Test
    void doorLampsGoOutWithThePower() {
        ShipGridComponent g = twoRooms(true);
        for (MountFootprint m : g.mounts) m.installed.getComponent(SubsystemComponent.class).isOperational = false;
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        assertTrue(doorLights(g, spots).isEmpty());
    }

    /** The steady spot over tile (x, y), the heat's (fires flicker), or null. */
    static LightSpot heatAt(ShipGridComponent g, LightSpots spots, int x, int y) {
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            float tx = (s.x - g.gridOriginLocal.x) / g.tileSize, ty = (s.y - g.gridOriginLocal.y) / g.tileSize;
            if (s.flicker < 0f && Math.abs(tx - (x + 0.5f)) < 1e-3f && Math.abs(ty - (y + 0.5f)) < 1e-3f) return s;
        }
        return null;
    }

    @Test
    void hotTilesGlowAsBlackBodiesWithOrWithoutPower() {
        ShipGridComponent g = room();
        for (MountFootprint m : g.mounts) m.installed.getComponent(SubsystemComponent.class).isOperational = false;
        g.getTile(6, 3).temperature = 700f;
        g.getTile(6, 4).temperature = 900f;
        g.getTile(7, 4).temperature = 1500f;
        LightSpots spots = new LightSpots();
        new DeckLightRig(ART).collect(g, spots);
        assertNull(heatAt(g, spots, 6, 3), "under the Draper point nothing glows");
        LightSpot dull = heatAt(g, spots, 6, 4), bright = heatAt(g, spots, 7, 4);
        assertNotNull(dull);
        assertNotNull(bright);
        assertTrue(dull.on && bright.on, "heat glows without power");
        assertFalse(dull.casts(), "barely glowing steel lights nothing around it");
        assertTrue(bright.casts());
        assertTrue(bright.color.a > dull.color.a && bright.glow > dull.glow);
        assertTrue(dull.color.r > dull.color.g && dull.color.g > dull.color.b, "dull heat is red");
        assertTrue(bright.color.g / bright.color.r > dull.color.g / dull.color.r, "hotter is yellower");
    }

    @Test
    void blackBodyBrightnessRisesFromTheDraperPoint() {
        assertEquals(0f, BlackBody.brightness(BlackBody.DRAPER_POINT));
        assertEquals(1f, BlackBody.brightness(BlackBody.BRIGHT), 1e-5f);
        assertEquals(1f, BlackBody.brightness(3000f));
        assertTrue(BlackBody.brightness(1000f) < BlackBody.brightness(1200f));
        com.badlogic.gdx.graphics.Color c = BlackBody.color(6600f, new com.badlogic.gdx.graphics.Color());
        assertTrue(c.r > 0.95f && c.g > 0.95f && c.b > 0.95f, "6600 K is about white");
    }
}
