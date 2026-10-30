package com.ringsofiron.aicrew.btree;

import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** The way out to a job: the port, and the ring tile outside the hull the job is worked from. */
final class EvaRoute {
    final CrewTask task;
    final MountFootprint port;
    final int workX, workY;
    final float cost;

    EvaRoute(CrewTask task, MountFootprint port, int workX, int workY, float cost) {
        this.task = task;
        this.port = port;
        this.workX = workX;
        this.workY = workY;
        this.cost = cost;
    }
}
