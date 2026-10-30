package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.DockingPortLayout;

/** A shipmate in tow for {@link EvaSystem}: taking hold of them adrift, keeping them alongside, and in with them. */
final class EvaTow {
    private final EvaSystem sys;

    EvaTow(EvaSystem sys) {
        this.sys = sys;
    }

    /** {@code e} takes hold of {@code other}, adrift, to bring them in. */
    void takeInTow(Entity e, EvaComponent eva, Entity other, EvaComponent o) {
        eva.chase = null;
        eva.towing = other;
        o.heldBy = e;
        o.stranded = false;
        // Their two speeds made one, momentum kept
        float m = EvaSystem.MASS + eva.cargo, mo = EvaSystem.MASS + o.cargo;
        eva.vx = (eva.vx * m + o.vx * mo) / (m + mo);
        eva.vy = (eva.vy * m + o.vy * mo) / (m + mo);
        GridPositionComponent op = sys.service.position(other);
        sys.event.by = e;
        sys.emit(EvaEvent.Kind.TAKEN_IN_TOW, other, op == null ? null : op.ship, o.adrift);
        sys.event.by = null;
        o.adrift = 0f;
    }

    /** Keeps the shipmate {@code eva} has in tow beside them, or lets go of them if they can't hold on. */
    void carry(Entity e, EvaComponent eva, boolean conscious) {
        Entity other = eva.towing;
        EvaComponent o = EvaSystem.of(other);
        GridPositionComponent op = sys.service.position(other);
        if (!conscious || o == null || op == null || o.heldBy != e || !o.outside()) {
            letGoOf(e, eva);
            return;
        }
        // Beside them, on the side away from the ship's middle
        float dx = eva.wx - sys.frame.cx, dy = eva.wy - sys.frame.cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = d > 1e-3f ? dx / d : 1f, uy = d > 1e-3f ? dy / d : 0f;
        o.phase = EvaComponent.Phase.FREE;
        o.wx = eva.wx + ux * EvaSystem.TOW_GAP;
        o.wy = eva.wy + uy * EvaSystem.TOW_GAP;
        o.vx = eva.vx;
        o.vy = eva.vy;
        o.worldValid = true;
        o.frameBody = sys.frame.body;
        o.facing = eva.facing;
        sys.toShipFrame(o, op);
    }

    /** Lets go of whoever {@code eva} has in tow: adrift again where they are. */
    void letGoOf(Entity e, EvaComponent eva) {
        EvaComponent o = EvaSystem.of(eva.towing);
        if (o != null && o.heldBy == e) o.heldBy = null;
        eva.towing = null;
    }

    /** Takes the shipmate {@code eva} has in tow into the chamber with them, stepping in. */
    void inWith(EvaComponent eva) {
        Entity other = eva.towing;
        EvaComponent o = EvaSystem.of(other);
        GridPositionComponent op = sys.service.position(other);
        if (o == null || op == null) {
            eva.towing = null;
            return;
        }
        o.port = eva.port;
        o.portMount = eva.portMount;
        o.phase = EvaComponent.Phase.RETURNING;
        o.rope = null;
        o.worldValid = false;
        o.path.clear();
        op.stop();
        DockingPortLayout.chamberTile(eva.portMount, sys.tile);
        op.setTile(sys.tile[0], sys.tile[1]);
    }
}
