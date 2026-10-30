package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewMemberComponent;

/** Carries the load to a bay of the task's destination craft and sets it down there. */
public class DeliverLoad extends CrewLeaf {
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
        CarryTask t = (CarryTask) a.crew.task;
        CrewMemberComponent.Load load = a.crew.load;
        if (load == null) return Status.FAILED;
        if (!t.job.isOpen()) {
            a.service.dropLoad(a.crew);
            return Status.FAILED;
        }
        a.crew.activity = "carrying " + t.job.commodity();
        CrewAgent.Travel go = t.toX >= 0 ? a.travel(t.to, CrewTasks.near(a, t.to, t.toX, t.toY, 1, 1))
                : a.travel(t.to, CrewTasks.beside(a, t.to, anyBay ? null : t.job.commodity(), false));
        if (go == CrewAgent.Travel.UNREACHABLE && !anyBay && t.toX < 0) {
            anyBay = true;
            go = a.travel(t.to, CrewTasks.beside(a, t.to, null, false));
        }
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        a.crew.activity = "unloading " + t.job.commodity();
        working += a.dt;
        if (working < a.crew.def.loadTime) return Status.RUNNING;
        a.crew.load = null;
        t.job.deliver(load.units);
        return Status.SUCCEEDED;
    }
}
