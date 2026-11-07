package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

/**
 * Every craft's airlock failsafes ({@link Airlock#failsafe}): when a docking port's outer door or
 * hatch is wrecked, what is left across it locks itself shut, and unlocks again once the airlock is
 * whole. The player hears when one of their airlocks locks itself, and how to get past it: unlock
 * the door at it ([E]), which lets the crew go out through it in suits.
 */
public final class AirlockLocks {
    private static final Family SHIPS = Family.all(ShipGridComponent.class).get();

    private final GameContext ctx;
    private final ImmutableArray<Entity> ships;

    public AirlockLocks(GameContext ctx) {
        this.ctx = ctx;
        ships = ctx.engine.getEcsEngine().getEntitiesFor(SHIPS);
    }

    /** Once a tick: each docking port's failsafe. */
    public void update() {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
            for (int m = 0; m < grid.mounts.size; m++) {
                MountFootprint port = grid.mounts.get(m);
                if (port.kind != MountFootprint.MountKind.PORT) continue;
                if (Airlock.failsafe(grid, port) && ship == ctx.player()) {
                    int index = index(ship, port);
                    ctx.notices.post((index < 0 ? "An airlock" : "Port " + (index + 1) + "'s airlock")
                            + " is broken: its other door has locked itself to hold the air. Unlock it at the door ([E]) to let suited crew out through it",
                            NoticeLog.Kind.WARNING);
                }
            }
        }
    }

    /** The index of {@code port}'s docking port on {@code ship}, or -1. */
    static int index(Entity ship, MountFootprint port) {
        DockingPortsComponent ports = ship.getComponent(DockingPortsComponent.class);
        if (ports == null) return -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.gridX >= 0 && port.covers(p.gridX, p.gridY)) return i;
        }
        return -1;
    }
}
