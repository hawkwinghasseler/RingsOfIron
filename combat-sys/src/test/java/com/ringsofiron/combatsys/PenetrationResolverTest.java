package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.combatsys.damage.PenetrationResolver;
import com.ringsofiron.combatsys.damage.PenetrationResolver.BurnResult;
import com.ringsofiron.combatsys.damage.PenetrationResolver.CrushResult;
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
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.moddingdata.defs.MaterialDef;
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
    void aRoundAlongTheHullsSkinGrazesOffRatherThanBoringThroughTheRow() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        // Head-on into the corner of the bottom hull row, then on along it
        Result r = shootFromLeft(grid, 0, 1e8f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.OVER_PENETRATED, r.outcome);
        assertTrue(grid.getTile(0, 0).getIntegrity() < 1f, "the corner it hit");
        for (int x = 1; x < 8; x++) {
            assertEquals(1f, grid.getTile(x, 0).getIntegrity(), 1e-6f, "the row beyond it, at " + x);
        }
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

    /** What spall takes out of a round fired through a wide medium box: its exit budget against a round with none. */
    private float spallLoss(float budget, float spallScale) {
        ShipGridComponent plain = TestShips.box(10, 7, TILE, WeightClass.MEDIUM);
        float without = shootFromLeft(plain, 3, budget, DamageType.KINETIC, Fuse.contact(), new SpallDef()).exitBudget;
        ShipGridComponent grid = TestShips.box(10, 7, TILE, WeightClass.MEDIUM);
        report.clear();
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + 3.5f * TILE);
        float with = resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), budget,
                DamageType.KINETIC, Fuse.contact(), new SpallDef(6, 30f, 0.3f), spallScale, 70f, 0.5f,
                new RandomXS128(1), report, result).exitBudget;
        return without - with;
    }

    @Test
    void spallTakesAShareOfWhatTheRoundStillCarries() {
        float small = spallLoss(2e7f, 1f), big = spallLoss(1e8f, 1f);
        assertTrue(small > 0f, "spall is thrown");
        // The same hull tile absorbs the same from both, so spall that came from it would match.
        assertTrue(big > 4f * small, big + " from a round five times as strong, against " + small);
    }

    @Test
    void aLightRoundThrowsLessSpall() {
        float heavy = spallLoss(1e8f, 1f);
        float light = spallLoss(1e8f, SpallDef.massScale(0.5f));
        assertEquals(0.5f / 5.5f, SpallDef.massScale(0.5f), 1e-6f);
        assertTrue(light < 0.15f * heavy, light + " against " + heavy);
        assertEquals(0f, spallLoss(1e8f, 0f), 1f, "a massless round throws none");
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
        resolver.setCrewHitChance(1f); // every round in their path strikes them
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                if (x == 1 && y == 2) out.add(sailor);
            }

            @Override
            public void applyDamage(Entity crew, float amount, HitCause c) {
                hp[0] += amount;
                cause[0] = c;
            }
        });
        shootFromLeft(grid, 2, 1e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(100f, hp[0], 1e-3f, "a crew member in the first room takes the full CREW_RESISTANCE bite");
        assertEquals(HitCause.PENETRATOR, cause[0]);
        assertTrue(report.injuredCrew.contains(sailor, true));
    }

    @Test
    void aRoundHurtsCrewLessPastTheFirstTile() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
        final Entity[] sailors = {new Entity(), new Entity(), new Entity()};
        final float[] hp = new float[3];
        resolver.setCrewHitChance(1f); // every round in their path strikes them
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                if (y == 2 && x >= 1 && x <= 3) out.add(sailors[x - 1]);
            }

            @Override
            public void applyDamage(Entity crew, float amount, HitCause c) {
                for (int i = 0; i < sailors.length; i++) if (sailors[i] == crew) hp[i] += amount;
            }
        });
        // A railgun slug: through all three and out the far side
        Result r = shootFromLeft(grid, 2, 1e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertEquals(Result.Outcome.OVER_PENETRATED, r.outcome);
        assertEquals(100f, hp[0], 1e-3f, "the first takes the full bite");
        assertEquals(100f * PenetrationResolver.CREW_FALLOFF_PER_TILE, hp[1], 1e-3f, "the second half");
        assertEquals(100f * PenetrationResolver.CREW_FALLOFF_PER_TILE * PenetrationResolver.CREW_FALLOFF_PER_TILE, hp[2], 1e-3f,
                "the third a quarter");
    }

    @Test
    void mostRoundsThroughATileMissThePeopleOnIt() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
        final Entity[] sailors = {new Entity(), new Entity()};
        final int[] struck = new int[2];
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                // Two gathered on one tile, round a casualty say
                if (y == 2 && x == 1) out.addAll(sailors);
            }

            @Override
            public void applyDamage(Entity crew, float amount, HitCause c) {
                for (int i = 0; i < sailors.length; i++) if (sailors[i] == crew) struck[i]++;
            }
        });
        int shots = 400;
        RandomXS128 rng = new RandomXS128(7);
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + 2.5f * TILE);
        for (int i = 0; i < shots; i++) {
            report.clear();
            resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), 1e7f, DamageType.KINETIC,
                    Fuse.contact(), new SpallDef(), 70f, 0.5f, rng, report, result);
        }
        for (int n : struck) {
            assertEquals(PenetrationResolver.CREW_HIT_CHANCE, n / (float) shots, 0.08f, "each struck on their own odds");
        }
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
        for (int x = 1; x < 7; x++) grid.setTileConduit(x, 2, new TileConduit());
        grid.gridDirty = false;
        resolver.setConduitHitChance(1f); // every round across it strikes it
        // Each round that strikes it takes FLOOR_RESISTANCE off it: two cut it
        shootFromLeft(grid, 2, 2e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertFalse(grid.getTileConduit(1, 2).isCut(), "one round leaves it carrying");
        assertEquals(1f - PenetrationResolver.FLOOR_RESISTANCE / PenetrationResolver.CONDUIT_HIT_POINTS,
                grid.getTileConduit(1, 2).integrity, 1e-4f);
        shootFromLeft(grid, 2, 2e7f, DamageType.KINETIC, Fuse.contact(), new SpallDef());
        assertTrue(grid.getTileConduit(1, 2).isCut(), "the second cuts it");
        assertTrue(grid.gridDirty);
        assertTrue(grid.getTile(1, 2).getIntegrity() > 0f, "the deck under it still stands");
    }

    @Test
    void mostRoundsThroughATileMissTheConduitOnIt() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
        int shots = 2000, struck = 0;
        RandomXS128 rng = new RandomXS128(7);
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + 2.5f * TILE);
        for (int i = 0; i < shots; i++) {
            grid.setTileConduit(1, 2, new TileConduit());
            report.clear();
            resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), 1e7f, DamageType.KINETIC,
                    Fuse.contact(), new SpallDef(), 70f, 0.5f, rng, report, result);
            if (grid.getTileConduit(1, 2).integrity < 1f) struck++;
        }
        assertEquals(PenetrationResolver.CONDUIT_HIT_CHANCE, struck / (float) shots, 0.04f, "a cable fills little of its tile");
    }

    // ------------------------------------------------------------------
    // Collisions
    // ------------------------------------------------------------------

    /** A collision pressing along +x into the left hull of a box at row y. */
    private CrushResult crushFromLeft(ShipGridComponent grid, int row, float energy, float spread) {
        report.clear();
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + (row + 0.5f) * TILE);
        return resolver.crush(grid, ship, point, new Vector2(1, 0), energy, spread, new RandomXS128(1), report, new CrushResult());
    }

    private float hullHitPoints(WeightClass weightClass) {
        return materials.get(MaterialTable.DEFAULT_MATERIAL, weightClass).hitPoints;
    }

    @Test
    void aLightKnockDamagesAPatchOfHullMostWhereTheyMet() {
        ShipGridComponent grid = TestShips.box(8, 7, TILE, WeightClass.MEDIUM);
        float energy = 0.5f * hullHitPoints(WeightClass.MEDIUM);
        CrushResult c = crushFromLeft(grid, 3, energy, 1f);
        assertEquals(0, c.entryX);
        assertEquals(3, c.entryY);
        assertEquals(energy, c.absorbed, energy * 1e-4f, "the hull took all of it");
        assertTrue(c.tilesDamaged > 1, "a patch, not one tile: " + c.tilesDamaged);
        float met = 1f - grid.getTile(0, 3).getIntegrity();
        assertEquals(met, c.worstLoss, 1e-5f, "the worst where they met");
        assertTrue(1f - grid.getTile(0, 2).getIntegrity() < met, "less beside it");
        assertTrue(1f - grid.getTile(0, 1).getIntegrity() < 1f - grid.getTile(0, 2).getIntegrity(), "and less further off");
        assertFalse(grid.getTile(0, 3).isHullBreach());
        assertEquals(0, report.breaches.size);
    }

    @Test
    void aHardHitCrushesAColumnInwardAndBreachesTheHull() {
        ShipGridComponent grid = TestShips.box(8, 7, TILE, WeightClass.MEDIUM);
        float energy = 3f * hullHitPoints(WeightClass.MEDIUM);
        CrushResult c = crushFromLeft(grid, 3, energy, 0.3f);
        assertEquals(0f, grid.getTile(0, 3).getIntegrity(), 1e-6f, "the hull where they met crushed");
        assertTrue(grid.getTile(0, 3).isHullBreach());
        assertTrue(report.breaches.size >= 1);
        assertTrue(report.structureChanged);
        assertTrue(grid.getTile(1, 3).getIntegrity() < 1f, "and pushed on into the deck behind it");
        assertTrue(c.absorbed <= energy * (1f + 1e-4f), "never more than it brought: " + c.absorbed);
        assertEquals(1f, c.worstLoss, 1e-5f);
    }

    @Test
    void aCollisionThatMeetsNothingSolidDoesNothing() {
        ShipGridComponent grid = TestShips.box(8, 7, TILE, WeightClass.MEDIUM);
        report.clear();
        Vector2 far = new Vector2(grid.gridOriginLocal.x - 40f, grid.gridOriginLocal.y + 7f);
        CrushResult c = resolver.crush(grid, ship, far, new Vector2(1, 0), 1e9f, 1f, new RandomXS128(1), report, new CrushResult());
        assertEquals(-1, c.entryX);
        assertEquals(0f, c.absorbed);
        assertEquals(0, c.tilesDamaged);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) assertEquals(1f, grid.getTile(x, y).getIntegrity(), 1e-6f);
        }
    }

    @Test
    void crewWhereTheHullsMeetAreHurt() {
        ShipGridComponent grid = TestShips.box(8, 7, TILE, WeightClass.MEDIUM);
        Entity person = new Entity();
        float[] hurt = new float[1];
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                if (x == 1 && y == 3) out.add(person);
            }

            @Override
            public void applyDamage(Entity crewMember, float healthPoints, HitCause cause) {
                hurt[0] += healthPoints;
            }
        });
        crushFromLeft(grid, 3, 3f * hullHitPoints(WeightClass.MEDIUM), 0.3f);
        assertTrue(hurt[0] > 0f, "crushed with the deck they stood on");
    }

    // ------------------------------------------------------------------
    // Beams and material properties
    // ------------------------------------------------------------------

    /** A beam along +x onto the left hull of a box at row y, for one tick's worth of energy. */
    private BurnResult burnFromLeft(PenetrationResolver r, ShipGridComponent grid, int row, float energy) {
        report.clear();
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + (row + 0.5f) * TILE);
        return r.burn(grid, ship, point, new Vector2(1, 0), energy, new RandomXS128(1), report, new BurnResult());
    }

    private float beamHardness(WeightClass weightClass) {
        MaterialTable.ClassStats steel = materials.get(MaterialTable.DEFAULT_MATERIAL, weightClass);
        return steel.hitPoints + steel.armor;
    }

    @Test
    void aBeamBurnsThroughTheHullThenOnToTheFarSide() {
        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        float quarter = 0.25f * beamHardness(WeightClass.MEDIUM);
        BurnResult b = burnFromLeft(resolver, grid, 2, quarter);
        assertTrue(b.touched);
        assertEquals(0, b.tileX);
        assertEquals(2, b.tileY);
        assertEquals(0.75f, grid.getTile(0, 2).getIntegrity(), 1e-4f, "armor does not strip a beam, it only slows it");
        assertFalse(b.burnedThrough);
        for (int i = 0; i < 2; i++) burnFromLeft(resolver, grid, 2, quarter);
        b = burnFromLeft(resolver, grid, 2, quarter);
        assertTrue(b.burnedThrough);
        assertTrue(grid.getTile(0, 2).isHullBreach());
        assertEquals(1, report.breaches.size);

        // Through the hole and across the open deck, onto the far hull from inside.
        b = burnFromLeft(resolver, grid, 2, quarter);
        assertEquals(7, b.tileX);
        assertEquals(0.75f, grid.getTile(7, 2).getIntegrity(), 1e-4f);
        assertEquals(quarter, b.absorbed, quarter * 1e-4f);
    }

    @Test
    void ablativeArmorSlowsABeam() {
        MaterialTable table = MaterialTable.createDefault();
        MaterialTable.ClassStats s = table.get(MaterialTable.DEFAULT_MATERIAL, WeightClass.MEDIUM);
        table.define("composite_armor", s, s, s);
        CombatDefs defs = CombatDefs.createDefault();
        MaterialDef composite = new MaterialDef();
        composite.id = "composite_armor";
        composite.ablationFactor = 0.6f;
        defs.add(composite);
        PenetrationResolver r = new PenetrationResolver(table);
        r.setDefs(defs);

        ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.MEDIUM);
        grid.getTile(0, 2).material = "composite_armor";
        float quarter = 0.25f * beamHardness(WeightClass.MEDIUM);
        burnFromLeft(r, grid, 2, quarter);
        burnFromLeft(r, grid, 1, quarter);
        assertEquals(1f - 0.25f * 0.6f, grid.getTile(0, 2).getIntegrity(), 1e-4f, "composite ablates at 0.6");
        assertEquals(0.75f, grid.getTile(0, 1).getIntegrity(), 1e-4f, "steel at 1");
    }

    @Test
    void aFlammableMaterialCatchesFireFromHits() {
        CombatDefs defs = CombatDefs.createDefault();
        MaterialDef deck = new MaterialDef();
        deck.id = "resin_deck";
        deck.fireChance = 1f;
        defs.add(deck);
        int steelFires = 0, resinFires = 0;
        for (int seed = 1; seed <= 20; seed++) {
            for (boolean resin : new boolean[]{false, true}) {
                PenetrationResolver r = new PenetrationResolver(materials);
                r.setDefs(defs);
                ShipGridComponent grid = TestShips.box(8, 5, TILE, WeightClass.LIGHT);
                if (resin) {
                    for (int x = 1; x < 7; x++) grid.getTile(x, 2).material = deck.id;
                }
                report.clear();
                Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + 2.5f * TILE);
                r.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), 2e7f, DamageType.KINETIC,
                        Fuse.contact(), new SpallDef(), 70f, 0.5f, new RandomXS128(seed), report, new Result());
                if (resin) resinFires += report.fires.size > 0 ? 1 : 0;
                else steelFires += report.fires.size > 0 ? 1 : 0;
            }
        }
        assertEquals(20, resinFires, "a fire_chance 1 deck lights on every hit");
        assertTrue(steelFires < 20, "plain steel only sometimes: " + steelFires);
    }
}
