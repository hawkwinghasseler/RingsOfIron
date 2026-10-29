package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Fills the suits hanging in a craft's suit lockers ({@link SuitLockers}): a locker is plumbed to
 * the ship's oxygen, and tops a suit's air up over {@link #AIR_FILL} seconds from empty, drawing the
 * oxygen it takes from a tank or working life support aboard ({@link CrewTasks#oxygenSupplies});
 * and an EVA suit's jetpack over {@link #JET_FILL} seconds, drawing {@link Suit#JET_PROPELLANT_KG}
 * a full pack from the ship's propellant ({@link ThrusterComponent#fuel}, which the propellant feed
 * takes out of the tanks): the packs burn the same monopropellant as the RCS. With no oxygen aboard
 * to give, or no propellant, the suits wait.
 */
public class SuitLockerSystem extends FixedIteratingSystem {
    /** Seconds a locker takes to fill a suit's air from empty. */
    public static final float AIR_FILL = 120f;
    /** Seconds a locker takes to fill a jetpack from empty. */
    public static final float JET_FILL = 300f;
    /** Seconds between fills: suits fill slowly, so once a second is plenty. */
    static final float TICK = 1f;

    private static final Family SHIPS = Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class).get();

    private final Array<SubsystemComponent> supplies = new Array<>();

    public SuitLockerSystem() {
        super(SHIPS, 16, TICK);
    }

    @Override
    protected void processEntity(Entity ship, float dt) {
        Array<Furnishing> lockers = SuitLockers.all(ship.getComponent(ShipGridComponent.class));
        if (lockers.size == 0) return;
        supplies.clear();
        boolean oxygen = CrewTasks.oxygenSupplies(ship, supplies);
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        // A craft whose thrust is free (no engine in its layout, or none at all) fills jetpacks free too
        ThrusterComponent fuel = thruster != null && (thruster.fuelPerNewtonSecond > 0f || thruster.fuelPerTorqueSecond > 0f)
                ? thruster : null;
        for (Furnishing f : lockers) {
            if (!SuitLockers.works(f)) continue;
            for (int i = 0; i < f.suits.size; i++) {
                Suit s = f.suits.get(i);
                if (oxygen && s.air < s.capacity()) {
                    float add = Math.min(s.capacity() - s.air, s.capacity() / AIR_FILL * dt);
                    s.air += add;
                    CrewTasks.drawOxygen(supplies.first(), CrewTasks.suitOxygen(s.kind) * add / s.capacity());
                }
                if (s.kind == Suit.Kind.EVA && s.jet < Suit.JET_DV) {
                    float add = Math.min(Suit.JET_DV - s.jet, Suit.JET_DV / JET_FILL * dt);
                    double kg = Suit.JET_PROPELLANT_KG * add / Suit.JET_DV;
                    if (fuel != null && fuel.fuel < kg) continue;
                    s.jet += add;
                    if (fuel != null) fuel.fuel -= (float) kg;
                }
            }
        }
    }
}
