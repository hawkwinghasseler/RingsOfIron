package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a deck's lights go, and when they are on. */
class DeckLightRigTest {
    static final EmissiveLights ART = EmissiveLightsTest.art();

    /** A room with a 2x2 reactor and a 2x3 helm turned on its side. */
    static ShipGridComponent room() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.RR....#",
                "#.RR....#",
                "#.......#",
                "#.......#",
                "#########");
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
        new DeckLightRig(ART).collect(g, spots);
        for (int i = 0; i < spots.size(); i++) {
            LightSpot s = spots.get(i);
            assertEquals(!inside(s, g, reactor), s.on);
        }
        assertFalse(DeckLightRig.hasPower(g), "no reactor running: the rooms go dark");
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
}
