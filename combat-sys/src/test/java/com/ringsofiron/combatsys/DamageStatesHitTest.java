package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.combatsys.damage.PenetrationResolver;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.SpallDef;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rounds short out the systems and burst the tanks they hit. */
class DamageStatesHitTest {
    private static final float TILE = 2f;
    private final PenetrationResolver resolver = new PenetrationResolver(MaterialTable.createDefault());
    private final PenetrationResolver.Result result = new PenetrationResolver.Result();
    private final DamageReport report = new DamageReport();
    private final Entity ship = new Entity();

    private void shootFromLeft(ShipGridComponent grid, int row, float budget) {
        report.clear();
        Vector2 point = new Vector2(grid.gridOriginLocal.x, grid.gridOriginLocal.y + (row + 0.5f) * TILE);
        resolver.resolveHit(grid, ship, point, new Vector2(-1, 0), new Vector2(1000, 0), budget,
                DamageType.KINETIC, Fuse.contact(), new SpallDef(), 70f, 0.5f, new RandomXS128(1), report, result);
    }

    @Test
    void aRoundThroughAPoweredReactorCanShortIt() {
        ShipGridComponent grid = TestShips.box(10, 5, TILE, WeightClass.LIGHT);
        SubsystemComponent reactor = TestShips.install(grid, SubsystemComponent.SubsystemType.REACTOR,
                MountFootprint.MountSize.M, 4, 2).getComponent(SubsystemComponent.class);
        reactor.powered = true;
        reactor.shortChance = 1f;
        shootFromLeft(grid, 2, 6e7f);
        assertTrue(reactor.health < 1f);
        assertTrue(reactor.shorted, "shorted");
        assertFalse(reactor.isOperational);
        assertEquals(1, report.shorts.size);
        assertEquals(1, grid.incidents.size);
        assertFalse(report.isEmpty());
    }

    @Test
    void anUnpoweredSystemDoesNotShort() {
        ShipGridComponent grid = TestShips.box(10, 5, TILE, WeightClass.LIGHT);
        SubsystemComponent reactor = TestShips.install(grid, SubsystemComponent.SubsystemType.REACTOR,
                MountFootprint.MountSize.M, 4, 2).getComponent(SubsystemComponent.class);
        reactor.shortChance = 1f;
        shootFromLeft(grid, 2, 6e7f);
        assertFalse(reactor.shorted);
        assertEquals(0, report.shorts.size);
    }

    @Test
    void aRoundThroughAWreckedPropellantTankBurstsItWithABlast() {
        ShipGridComponent grid = TestShips.box(10, 5, TILE, WeightClass.LIGHT);
        SubsystemComponent tank = TestShips.install(grid, SubsystemComponent.SubsystemType.TANK,
                MountFootprint.MountSize.S, 4, 2).getComponent(SubsystemComponent.class);
        tank.fluid = Fluid.PROPELLANT;
        tank.capacity = tank.contents = 2000;
        tank.health = 0.26f; // the hit takes it under the 0.25 threshold
        tank.burstChance = 1f;
        final Entity sailor = new Entity();
        final float[] blast = {0f};
        resolver.setCrew(new CrewDamageBridge() {
            @Override
            public void occupantsAt(Entity s, int x, int y, Array<Entity> out) {
                if (x == 4 && y == 3) out.add(sailor); // beside the tank, out of the round's path
            }

            @Override
            public void applyDamage(Entity crew, float amount, HitCause cause) {
                if (cause == HitCause.BLAST) blast[0] += amount;
            }
        });
        shootFromLeft(grid, 2, 6e7f);
        assertEquals(0, tank.contents, 1e-9, "it let everything go");
        assertTrue(tank.isDestroyed());
        assertEquals(1, report.bursts.size);
        assertTrue(blast[0] > 0f, "the blast reached the crew member beside it");
        assertTrue(blast[0] < 100f, "a small blast, not a lethal one");
        float fuel = 0f;
        for (int x = 3; x <= 6; x++) fuel += grid.getTile(x, 3).fuel;
        assertTrue(fuel > 4 * com.ringsofiron.interiorgrid.FloorCovering.CARPET.fuelPerArea * TILE * TILE,
                "propellant pooled on top of the carpet");
    }

    @Test
    void aSoundTankDoesNotBurst() {
        ShipGridComponent grid = TestShips.box(10, 5, TILE, WeightClass.LIGHT);
        SubsystemComponent tank = TestShips.install(grid, SubsystemComponent.SubsystemType.TANK,
                MountFootprint.MountSize.S, 4, 2).getComponent(SubsystemComponent.class);
        tank.fluid = Fluid.PROPELLANT;
        tank.capacity = tank.contents = 2000;
        tank.burstChance = 1f;
        shootFromLeft(grid, 2, 1e7f);
        assertTrue(tank.health > 0.25f, "one hit leaves it above the threshold: " + tank.health);
        assertEquals(2000, tank.contents, 1e-9);
        assertEquals(0, report.bursts.size);
    }
}
