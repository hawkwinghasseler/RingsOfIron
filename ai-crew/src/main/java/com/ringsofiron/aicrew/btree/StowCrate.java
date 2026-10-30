package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.interiorgrid.CargoHoldComponent;

/**
 * Carries the crate in hand to a bay of the craft's hold with room for it and sets it down there
 * ({@link CrewDef#loadTime}). With no way to the hold, it goes in all the same; with no room in
 * it, it is cast off. Its work hears which ({@link OutsideTask.Job#stowed}).
 */
public class StowCrate extends CrewLeaf {
    private float working;
    private boolean anyBay;

    @Override
    public void start() {
        working = 0f;
        anyBay = false;
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent c = a.crew;
        CrewMemberComponent.Crate crate = c.crate;
        if (crate == null) return Status.SUCCEEDED;
        Entity ship = a.service.homeOf(a.entity);
        CargoHoldComponent hold = ship == null ? null : ship.getComponent(CargoHoldComponent.class);
        CrewAgent.Travel go = CrewAgent.Travel.UNREACHABLE;
        if (hold != null && a.pos.ship == ship) {
            go = a.travel(ship, CrewTasks.beside(a, ship, anyBay ? null : crate.commodity, false));
            if (go == CrewAgent.Travel.UNREACHABLE && !anyBay) {
                anyBay = true;
                go = a.travel(ship, CrewTasks.beside(a, ship, null, false));
            }
        }
        if (go == CrewAgent.Travel.GOING) {
            c.activity = "carrying " + crate.name + " to the hold";
            return Status.RUNNING;
        }
        if (go == CrewAgent.Travel.ARRIVED) {
            c.activity = "stowing " + crate.name + " in the hold";
            working += a.dt;
            if (working < c.def.loadTime) return Status.RUNNING;
        }
        double in = hold == null ? 0 : hold.load(crate.commodity, 1, crate.volume, crate.mass);
        c.crate = null;
        if (crate.job != null) crate.job.stowed(a.entity, crate, in >= 1 - 1e-6);
        return Status.SUCCEEDED;
    }
}
