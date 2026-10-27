package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.combatsys.shields.ShieldSystem;

/**
 * What a ship's shield does to a hit (documentation/combat-sys/Design.md, 5.11): its emitters
 * hold a cloud of charged particles round the hull and gather it where they expect hits
 * ({@link ShieldSystem}). A round loses energy to the mass it plows through on its way in, by
 * how dense the cloud is where it crosses and how hard the round is to stop; that mass is
 * scattered, and what is thrown faster than the emitter can hold is lost. Rounds, fragments,
 * missiles, beams and ion bolts are all softened; a ram is not (it is a hull meeting a hull).
 */
public final class Shields {
    private static final Vector2 COM = new Vector2();

    private Shields() {
    }

    /**
     * Lets {@code ship}'s cloud take its share of a round of {@code def} and {@code mass} carrying
     * {@code energy} joules into the hull at body-local ({@code lx}, {@code ly}), travelling along
     * body-local ({@code dirX}, {@code dirY}) relative to the ship. Returns the joules the cloud
     * took (0 with no working emitter).
     */
    public static float absorb(Entity ship, Body body, ProjectileDef def, float mass, float energy,
                               float lx, float ly, float dirX, float dirY) {
        ShieldCloudComponent cloud = ShieldModel.ensure(ship);
        if (cloud == null || body == null) return 0f;
        ShieldSystem.centerOfMass(body, COM);
        return ShieldModel.absorbRound(cloud, def, mass, energy, lx, ly, dirX, dirY, COM.x, COM.y);
    }

    /** As {@link #absorb}, for one tick ({@code dt} seconds) of a beam carrying {@code energy} joules. */
    public static float absorbBeam(Entity ship, Body body, float energy, float lx, float ly, float dirX, float dirY, float dt) {
        ShieldCloudComponent cloud = ShieldModel.ensure(ship);
        if (cloud == null || body == null) return 0f;
        ShieldSystem.centerOfMass(body, COM);
        return ShieldModel.absorbBeam(cloud, energy, lx, ly, dirX, dirY, COM.x, COM.y, dt);
    }

    /** True if {@code ship} has a working emitter and particles in its cloud. */
    public static boolean up(Entity ship) {
        ShieldCloudComponent cloud = ship == null ? null : ship.getComponent(ShieldCloudComponent.class);
        if (cloud == null) cloud = ShieldModel.ensure(ship);
        return cloud != null && cloud.cloud() > 0f;
    }
}
