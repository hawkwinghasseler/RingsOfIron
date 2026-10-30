package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.GridLink;

/** A latched pair and the joint holding them. Runtime only; rebuilt from the ports. */
final class LatchLink {
    final Entity a, b;
    final DockingPort pa, pb;
    final int portA, portB;
    Joint joint;
    boolean rigid;
    float seatingTime;
    boolean hadJoint;
    float waiting;
    float strainForce, strainTorque;
    /** Put {@link #a} straight onto {@link #b} at the latched pose before the first weld. */
    boolean snap;
    /** The walkway between the two grids while welded; see {@link DockLinks}. */
    final Array<GridLink> gridLinks = new Array<>();
    /** Seconds the pressure doors stay open for someone crossing; see {@link PortSeals}. */
    float sealHold;

    LatchLink(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        this.a = a;
        this.portA = portA;
        this.pa = pa;
        this.b = b;
        this.portB = portB;
        this.pb = pb;
    }
}
