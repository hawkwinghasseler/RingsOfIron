package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import org.junit.jupiter.api.Test;

/** NPC freighters buying propellant and air wherever they dock. */
class FreighterRefuelingTest {

    private final Engine engine = new Engine();
    private final FreighterRefueling refueling = new FreighterRefueling(ship -> null);
    private FreighterComponent freighter;
    private Entity ship;

    private void build() {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "#######",
                "#P.O.N#",
                "#P.O.N#",
                "#######"));
        ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        freighter = new FreighterComponent();
        ship.add(r.grid);
        ship.add(registry);
        ship.add(freighter);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        engine.addSystem(refueling);
        for (SubsystemComponent t : tanks()) t.contents = 0;
    }

    private com.badlogic.gdx.utils.Array<SubsystemComponent> tanks() {
        return TankSupply.tanks(ship.getComponent(ShipSubsystemRegistryComponent.class), null);
    }

    @Test
    void aFreighterIdleAtAStationFillsItsTanksAndPaysForThem() {
        build();
        freighter.state = FreighterComponent.State.IDLE;
        freighter.dockedAtUuid = "station";
        freighter.credits = 1e6;
        double cost = 0;
        for (SubsystemComponent t : tanks()) cost += t.capacity * StationYard.pricePerKg(t.fluid);
        engine.update(FreighterRefueling.INTERVAL);
        for (SubsystemComponent t : tanks()) assertEquals(t.capacity, t.contents, 1e-6);
        assertEquals(1e6 - cost, freighter.credits, 1e-6);
    }

    @Test
    void shortOfCreditsItBuysWhatItCan() {
        build();
        freighter.state = FreighterComponent.State.IDLE;
        freighter.dockedAtUuid = "station";
        double cost = 0;
        for (SubsystemComponent t : tanks()) cost += t.capacity * StationYard.pricePerKg(t.fluid);
        freighter.credits = cost / 4;
        assertEquals(cost / 4, refueling.refuel(ship), 1e-6);
        for (SubsystemComponent t : tanks()) assertEquals(t.capacity / 4, t.contents, 1e-6);
        assertEquals(0, freighter.credits, 1e-6);
    }

    @Test
    void aFreighterUnderwayBuysNothing() {
        build();
        freighter.state = FreighterComponent.State.TO_DELIVERY;
        freighter.credits = 1e6;
        engine.update(FreighterRefueling.INTERVAL);
        for (SubsystemComponent t : tanks()) assertEquals(0, t.contents, 1e-9);
        assertEquals(1e6, freighter.credits, 1e-9);
    }
}
