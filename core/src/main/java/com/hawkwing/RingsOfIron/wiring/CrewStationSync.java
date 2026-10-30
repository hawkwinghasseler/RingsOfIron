package com.hawkwing.RingsOfIron.wiring;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.CrewQueries.Station;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;

/**
 * Sets each crewed ship's gunnery and tracking skill from its crew: the best conscious gunner
 * aims every mount and the best conscious sensor operator runs the tracks, so a knocked-out
 * crew shoots and tracks like a green one. On a ship with a weapons console that means whoever
 * mans it (and the player); see {@link CrewQueries#bestSkill}. Ships with no crew aboard keep
 * their own values. A ship {@link #gunsByGun} has only its point defense set here; {@link GunCrews}
 * sets its other guns one by one.
 */
public class CrewStationSync extends FixedLogicSystem {
    /** Seconds between updates; crew skill only changes when someone goes down or wakes. */
    public static final float INTERVAL = 0.5f;

    private static final Family ARMED = Family.one(WeaponsComponent.class, WeaponMountComponent.class,
            SensorComponent.class).get();

    /** Ships whose guns other than point defense {@link GunCrews} crews gun by gun; null for none. */
    public java.util.function.Predicate<Entity> gunsByGun;

    private Engine engine;
    private ImmutableArray<Entity> ships;

    public CrewStationSync() {
        super(INTERVAL);
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        ships = engine.getEntitiesFor(ARMED);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        this.engine = null;
        ships = null;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            sync(engine, ship, gunsByGun != null && gunsByGun.test(ship));
        }
    }

    /** Copies the crew's skill onto one ship's weapons and sensors. */
    public static void sync(Engine engine, Entity ship) {
        sync(engine, ship, false);
    }

    /** As {@link #sync(Engine, Entity)}; with {@code pointDefenseOnly}, of its guns only point defense. */
    static void sync(Engine engine, Entity ship, boolean pointDefenseOnly) {
        float gunnery = CrewQueries.bestSkill(engine, ship, Station.GUNNERY);
        if (gunnery != CrewQueries.NO_CREW) {
            WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
            if (weapons != null) for (WeaponMountComponent m : weapons.mounts) {
                if (!pointDefenseOnly || m.def != null && m.def.pointDefense) m.gunnerySkill = gunnery;
            }
            WeaponMountComponent single = ship.getComponent(WeaponMountComponent.class);
            if (single != null) single.gunnerySkill = gunnery;
        }
        float tracking = CrewQueries.bestSkill(engine, ship, Station.TRACKING);
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        if (tracking != CrewQueries.NO_CREW && sensor != null) sensor.trackingSkill = tracking;
    }
}
