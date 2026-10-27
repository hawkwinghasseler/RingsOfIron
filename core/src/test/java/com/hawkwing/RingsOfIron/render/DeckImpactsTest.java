package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Signal;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hits show on their own ship's deck by what struck, stay inside the walls, and fade. */
class DeckImpactsTest {
    private static final float DT = 1f / 60f;
    private ShipGridComponent grid;
    private Entity ship;
    private DeckImpacts impacts;

    @BeforeEach
    void setUp() {
        // One room, 5 by 3 tiles of deck inside a hull a tile thick
        grid = new ShipGridComponent(7, 5, 1f);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                Tile t = grid.getTile(x, y);
                boolean edge = x == 0 || y == 0 || x == 6 || y == 4;
                t.wallType = edge ? Tile.WallType.HULL : Tile.WallType.NONE;
                t.floorType = Tile.FloorType.FLOOR;
            }
        }
        ship = new Entity().add(grid);
        impacts = new DeckImpacts();
    }

    /** A hit on the port hull at (0, 2), travelling to starboard. */
    private HullHitEvent hit(HullHitEvent.Outcome outcome, DamageType type, float severity) {
        HullHitEvent h = new HullHitEvent();
        h.ship = ship;
        h.tileX = 0;
        h.tileY = 2;
        h.entry.set(0f, 2.5f);
        h.exit.set(h.entry);
        h.direction.set(1f, 0f);
        h.normal.set(-1f, 0f);
        h.outcome = outcome;
        h.damageType = type;
        h.severity = severity;
        h.energyAbsorbed = severity * 1e7f;
        return h;
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) impacts.update(DT);
    }

    @Test
    void aRoundThatGetsInThrowsWreckageThatStaysInsideTheRoom() {
        impacts.add(hit(HullHitEvent.Outcome.PENETRATED, DamageType.KINETIC, 1f));
        assertEquals(1, impacts.hitsShowing());
        assertTrue(impacts.debris.count > 0 && impacts.sparks.count > 0);
        for (int step = 0; step < 60; step++) {
            impacts.update(DT);
            for (DeckImpacts.Particles p : new DeckImpacts.Particles[]{impacts.sparks, impacts.debris}) {
                for (int i = 0; i < p.count; i++) {
                    if (!p.inside[i]) continue;
                    assertTrue(p.x[i] < 6f && p.y[i] > 1f && p.y[i] < 4f,
                            "bounced off the hull: " + p.x[i] + ", " + p.y[i]);
                }
            }
        }
    }

    @Test
    void everythingFadesAndAStoppedClockHoldsIt() {
        impacts.add(hit(HullHitEvent.Outcome.STOPPED, DamageType.KINETIC, 0.5f));
        int before = impacts.particles();
        impacts.update(0f);
        assertEquals(before, impacts.particles(), "paused, nothing moves on");
        run(5f);
        assertEquals(0, impacts.hitsShowing());
        assertEquals(0, impacts.particles());
    }

    @Test
    void aHitTheShieldsTookShowsNothingInside() {
        HullHitEvent h = hit(HullHitEvent.Outcome.STOPPED, DamageType.KINETIC, 0f);
        h.energyAbsorbed = 0f;
        h.shielded = 1e6f;
        impacts.add(h);
        assertEquals(0, impacts.hitsShowing());
        assertEquals(0, impacts.particles());
    }

    @Test
    void lasersMeltAndIonBoltsArc() {
        HullHitEvent beam = hit(HullHitEvent.Outcome.STOPPED, DamageType.ENERGY, 0.2f);
        beam.beam = true;
        impacts.add(beam);
        int embers = impacts.embers.count;
        assertTrue(embers > 0, "molten droplets, not sparks");
        assertEquals(0, impacts.sparks.count);
        run(0.3f);
        assertTrue(impacts.embers.count > 0, "and more while the plate is white hot");
        impacts.clear();
        HullHitEvent ion = hit(HullHitEvent.Outcome.STOPPED, DamageType.ION, 0f);
        ion.ion = true;
        impacts.add(ion);
        assertEquals(1, impacts.hitsShowing(), "the bolt broke nothing but still shows");
    }

    @Test
    void itShowsOnlyTheWorldItListensTo() {
        Signal<HullHitEvent> first = new Signal<>(), second = new Signal<>();
        impacts.listen(first);
        first.dispatch(hit(HullHitEvent.Outcome.PENETRATED, DamageType.KINETIC, 1f));
        assertEquals(1, impacts.hitsShowing());
        impacts.listen(second);
        assertEquals(0, impacts.hitsShowing(), "a new world starts clean");
        first.dispatch(hit(HullHitEvent.Outcome.PENETRATED, DamageType.KINETIC, 1f));
        assertEquals(0, impacts.hitsShowing());
        second.dispatch(hit(HullHitEvent.Outcome.PENETRATED, DamageType.KINETIC, 1f));
        assertEquals(1, impacts.hitsShowing());
    }
}
