package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/** Crossing for {@link EvaSystem}: pushing off from one craft for another, to go in by one of its ports. */
final class EvaCrossing {
    private final EvaSystem sys;

    EvaCrossing(EvaSystem sys) {
        this.sys = sys;
    }

    /** As {@link EvaSystem#cross}. */
    boolean cross(Entity e, Entity target, MountFootprint port, boolean breaking) {
        EvaComponent eva = EvaSystem.of(e);
        GridPositionComponent p = sys.service.position(e);
        ShipGridComponent from = p == null ? null : sys.service.grid(p.ship), to = sys.service.grid(target);
        if (eva == null || !eva.outside() || from == null || to == null || port == null) return false;
        int index = to.mounts.indexOf(port, true);
        if (index < 0) return false;
        sys.frame.read(p.ship, from);
        if (!eva.worldValid) sys.initWorld(eva);
        if (eva.portMount != null && eva.portMount.airlock != null) eva.portMount.airlock.release(e);
        if (eva.phase == EvaComponent.Phase.ON_HULL) {
            // Off with the hull point's motion and a push away from it
            sys.frame.toWorld(eva.rx, eva.ry, sys.a);
            sys.frame.velocityAt(sys.a[0], sys.a[1], sys.b);
            eva.wx = sys.a[0];
            eva.wy = sys.a[1];
            eva.vx = sys.b[0];
            eva.vy = sys.b[1];
        }
        eva.phase = EvaComponent.Phase.FREE;
        eva.tethered = false;
        eva.rope = null;
        eva.reelLocked = false;
        eva.tension = 0f;
        eva.hop = false;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.strain = 0f;
        eva.slideX = eva.slideY = 0f;
        eva.hullVValid = false;
        eva.fell = false;
        eva.regrab = EvaSystem.REGRAB_SECONDS;
        eva.port = index;
        eva.portMount = port;
        eva.crossing = true;
        eva.breaking = breaking;
        eva.forcedIn = false;
        eva.returning = true;
        eva.outOfReach = false;
        eva.crossTo = null;
        eva.crossPort = null;
        Entity left = p.ship;
        // They still work for the craft they leave, which counts as home by where they stand
        CrewMemberComponent c = sys.service.crew(e);
        if (c.home == null) c.setHome(left);
        p.setShip(target);
        sys.frame.read(target, to);
        sys.frame.toTiles(eva.wx, eva.wy, sys.a);
        eva.rx = sys.a[0];
        eva.ry = sys.a[1];
        sys.frame.velocityAt(eva.wx, eva.wy, sys.b);
        sys.frame.vecToTiles(eva.vx - sys.b[0], eva.vy - sys.b[1], sys.c4);
        eva.rvx = sys.c4[0];
        eva.rvy = sys.c4[1];
        eva.frameBody = sys.frame.body;
        eva.shipVValid = false;
        p.x = eva.rx;
        p.y = eva.ry;
        sys.emit(EvaEvent.Kind.CROSSING, e, left, 0f);
        return true;
    }
}
