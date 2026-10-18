package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.combatsys.damage.PenetrationResolver;
import com.ringsofiron.combatsys.damage.PenetrationResolver.Result;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.SpallDef;
import com.ringsofiron.combatsys.defs.WarheadDef;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PenetrationResolverTest {
    private static final float TILE = 2f;
    private final MaterialTable materials = MaterialTable.createDefault();
    private final PenetrationResolver resolver = new PenetrationResolver(materials);
    private final Result result = new Result();
    private final DamageReport report = new DamageReport();
    private final Entity ship = new Entity();

    /** Fires along +x into the left hull of a box at row y, head-on. */
    private Result shootFromLeft(ShipGridComponent grid, int row, float budget, DamageType type, Fuse fuse, SpallDef spall) {
        report.clear();
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + (row + 0.5f) * TILE);
        return resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), budget,
                type, fuse, spall, 70f, 0.5f, new RandomXS128(1), report, result);
    }

    private static float gridEnergyLeft(ShipGridComponent grid, MaterialTable materials) {
        float hp = 0f;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.isWall()) hp += t.getIntegrity() * materials.get(t.material, t.weightClass).hitPoints;
            }
        }
        return hp;
    }

    @Test
    void weakRoundIsStoppedByHull() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        Result r = shootFromLeft(grid, 2, 1e6f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.STOPPED, r.outcome);
        assertEquals(0, r.entryX);
        assertEquals(2, r.entryY);
        assertTrue(grid.getTile(0, 2).getIntegrity() > 0.9f, "armor took it with only wear");
        assertEquals(0, report.breaches.size);
    }

    @Test
    void strongRoundBreachesAndOverPenetrates() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        Result r = shootFromLeft(grid, 2, 1e8f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.OVER_PENETRATED, r.outcome);
        assertTrue(grid.getTile(0, 2).isHullBreach(), "entry hole");
        assertTrue(grid.getTile(7, 2).isHullBreach(), "exit hole");
        assertEquals(2, report.breaches.size);
        assertEquals(8f, r.exitPoint.x, 1e-3f);
        assertTrue(r.exitBudget > 0f && r.exitBudget < 1e8f);
    }

    @Test
    void energyIsNeverCreated() {
        float[] budgets = {1e5f, 3e6f, 9e6f, 2e7f, 1e8f};
        for (float budget : budgets) {
            ShipGridComponent grid = TestShips.box(10, 7, TILE, WeightClass.MEDIUM);
            Result r = shootFromLeft(grid, 3, budget, DamageType.KINETIC, Fuse.contact(), new SpallDef(6, 30f, 0.3f));
            assertTrue(r.absorbed + r.exitBudget <= budget * 1.0001f,
                    "absorbed " + r.absorbed + " + exit " + r.exitBudget + " <= " + budget);
            assertTrue(r.absorbed >= 0f);
        }
    }

    @Test
    void grazingHitRicochets() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.HEAVY);
        report.clear();
        // 80 degrees off the normal, moderate round.
        float a = (float) Math.toRadians(80);
        Vector2 vel = new Vector2((float) Math.cos(a), (float) Math.sin(a)).scl(1000f);
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + 2.5f * TILE);
        resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(vel.y, -vel.x).rotate90(1).scl(1),
                5e6f, DamageType.KINETIC, Fuse.contact(), new SpallDef(), 70f, 0.5f, new RandomXS128(1), report, result);
        // Velocity above is (cos80, sin80) -> mostly along the hull.
        assertEquals(Result.Outcome.RICOCHET, result.outcome);
        assertTrue(result.ricochetDir.x < 0f, "bounces back out");
        assertTrue(result.ricochetDir.y > 0f, "keeps its along-hull motion");
        assertFalse(grid.getTile(0, 2).isHullBreach());
    }

    @Test
    void wedgeSlopeDeflectsAHeadOnShot() {
        // A 1x1 wedge at rotation 1 has its sloped face toward (-x, +y): a head-on shot from the
        // left meets it at 45 degrees and faces 1/cos(45) more armor.
        ShipGridComponent flat = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        ShipGridComponent sloped = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        Tile t = sloped.getTile(0, 2);
        t.wallType = Tile.WallType.NONE;
        sloped.placeHullPiece(new HullPiece(HullPiece.Shape.WEDGE, 0, 2, 1, 1, 1, WeightClass.MEDIUM, MaterialTable.DEFAULT_MATERIAL));
        CompartmentBuilder.rebuild(sloped);

        float budget = 9e6f; // just over a flat medium tile (3e6 armor + 5e6 hp)
        Result r1 = shootFromLeft(flat, 2, budget, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.PENETRATED, r1.outcome, "flat plate is holed");
        Result r2 = shootFromLeft(sloped, 2, budget, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.STOPPED, r2.outcome, "wedge holds");
        assertFalse(sloped.getTile(0, 2).isHullBreach());
    }

    @Test
    void breachVentsTheRoomOnTheNextHazardTick() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        Compartment room = grid.compartmentAt(3, 2);
        assertEquals(1f, room.pressureRatio(), 0.01f);
        shootFromLeft(grid, 2, 2e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertTrue(grid.getTile(0, 2).isHullBreach());

        GridHazardSystem hazards = new GridHazardSystem();
        for (int i = 0; i < 40; i++) hazards.step(grid, GridHazardSystem.HAZARD_TICK);
        assertTrue(grid.pressureRatioAt(3, 2) < 0.9f, "room is venting: " + grid.pressureRatioAt(3, 2));
    }

    @Test
    void roundDamagesAndDisablesASystemInItsPath() {
        ShipGridComponent grid = TestShips.box(10, 5, TILE, WeightClass.LIGHT);
        Entity reactor = TestShips.install(grid, SubsystemComponent.SubsystemType.REACTOR, MountFootprint.MountSize.M, 4, 2);
        SubsystemComponent sub = reactor.getComponent(SubsystemComponent.class);
        shootFromLeft(grid, 2, 6e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertTrue(sub.health < 1f, "reactor hit: " + sub.health);
        assertTrue(report.damagedSubsystems.contains(reactor, true));
        // Keep shooting the same line until it drops below the disable threshold.
        for (int i = 0; i < 10 && sub.health >= PenetrationResolver.DISABLE_THRESHOLD; i++) {
            shootFromLeft(grid, 2, 6e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        }
        assertFalse(sub.isOperational);
    }

    @Test
    void crewInThePathAreHurt() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
        final Entity sailor = new Entity();
        final float[] hp = {0f};
        final HitCause[] cause = {null};
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                if (x == 3 && y == 2) out.add(sailor);
            }

            @Override
            public void applyDamage(Entity crew, float amount, HitCause c) {
                hp[0] += amount;
                cause[0] = c;
            }
        });
        shootFromLeft(grid, 2, 1e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(100f, hp[0], 1e-3f, "a crew member takes the full CREW_RESISTANCE bite");
        assertEquals(HitCause.PENETRATOR, cause[0]);
        assertTrue(report.injuredCrew.contains(sailor, true));
    }

    @Test
    void delayedFuseDetonatesInsideAndBlastDependsOnPressure() {
        WarheadDef he = CombatDefs.createDefault().warhead("he_shell");
        he.fragmentCount = 0; // isolate overpressure

        ShipGridComponent pressurized = TestShips.box(12, 9, TILE, WeightClass.LIGHT);
        ShipGridComponent vented = TestShips.box(12, 9, TILE, WeightClass.LIGHT);
        for (Compartment c : vented.compartments) {
            c.oxygen = 0;
            c.nitrogen = 0;
            c.carbonDioxide = 0;
        }

        SubsystemComponent a = TestShips.install(pressurized, SubsystemComponent.SubsystemType.HELM,
                MountFootprint.MountSize.S, 7, 4).getComponent(SubsystemComponent.class);
        SubsystemComponent b = TestShips.install(vented, SubsystemComponent.SubsystemType.HELM,
                MountFootprint.MountSize.S, 7, 4).getComponent(SubsystemComponent.class);
        float pressurizedDamage = resolver.detonateInside(pressurized, ship, 6, 4, he, new RandomXS128(2), new DamageReport());
        float ventedDamage = resolver.detonateInside(vented, ship, 6, 4, he, new RandomXS128(2), new DamageReport());
        assertTrue(pressurizedDamage > 0f);
        assertTrue(a.health < 1f, "the blast wrecks the helm next door");
        assertEquals(0f, ventedDamage, 1e-3f, "no air, no overpressure");
        assertEquals(1f, b.health, 1e-6f);

        // And the fuse itself fires one tile inside the hull.
        ShipGridComponent grid = TestShips.box(12, 9, TILE, WeightClass.LIGHT);
        Result r = shootFromLeft(grid, 4, 5e6f, DamageType.EXPLOSIVE, Fuse.delayed(1), new SpallDef());
        assertEquals(1, r.detonationX, "first tile inside the hull");
        assertEquals(4, r.detonationY);
    }

    @Test
    void blastIsStoppedByIntactWalls() {
        WarheadDef he = CombatDefs.createDefault().warhead("he_shell");
        he.fragmentCount = 0;
        ShipGridComponent grid = TestShips.box(12, 5, TILE, WeightClass.HEAVY);
        // Internal heavy wall at x = 6 dividing two rooms.
        for (int y = 1; y < 4; y++) {
            Tile t = grid.getTile(6, y);
            t.floorType = Tile.FloorType.NONE;
            t.wallType = Tile.WallType.INTERNAL;
            t.weightClass = WeightClass.HEAVY;
        }
        CompartmentBuilder.rebuild(grid);
        Entity far = TestShips.install(grid, SubsystemComponent.SubsystemType.HELM, MountFootprint.MountSize.S, 7, 2);
        resolver.detonateInside(grid, ship, 5, 2, he, new RandomXS128(3), new DamageReport());
        assertEquals(1f, far.getComponent(SubsystemComponent.class).health, 1e-6f, "shielded by the wall");
        assertTrue(grid.getTile(6, 2).getIntegrity() < 1f, "the wall took the blast");
    }

    @Test
    void conduitCanBeSevered() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
        for (int x = 1; x < 7; x++) grid.getTile(x, 2).floorType = Tile.FloorType.POWER_CONDUIT;
        grid.gridDirty = false;
        for (int i = 0; i < 4; i++) shootFromLeft(grid, 2, 2e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(0f, grid.getTile(1, 2).getIntegrity(), 1e-6f);
        assertTrue(grid.gridDirty);
    }
}
