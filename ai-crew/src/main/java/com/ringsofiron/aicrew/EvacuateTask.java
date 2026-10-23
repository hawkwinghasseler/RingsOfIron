package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A seat in an escape pod, posted when the ship is abandoned: the crew member who takes it walks
 * into the pod's bay and waits there until the pod launches (whoever posted the order decides
 * when, and takes them off the ship). One task per seat. Above every other job: abandoning ship
 * comes first. (x, y) is a tile of the pod's bay inside the hull; any tile of the bay will do.
 */
public class EvacuateTask extends CrewTask {

    public static final float PRIORITY = 1000f;

    /** The pod's subsystem entity. */
    public final Entity pod;
    /** Cleared when the order is called off or the pod has gone. */
    public boolean open = true;

    public EvacuateTask(Entity pod, int x, int y) {
        super(Kind.EVACUATE, x, y, PRIORITY);
        this.pod = pod;
    }

    /** False once the pod has gone, been wrecked or the order called off. */
    public boolean isOpen() {
        SubsystemComponent s = pod == null ? null : pod.getComponent(SubsystemComponent.class);
        return open && s != null && !s.isDisabled();
    }

    /** True if tile (x, y) is in the pod's bay. */
    public boolean inPod(int x, int y) {
        SubsystemComponent s = pod == null ? null : pod.getComponent(SubsystemComponent.class);
        return s != null && s.covers(x, y);
    }

    /** Every seat is its own job. */
    @Override
    public boolean sameJob(CrewTask other) {
        return other == this;
    }
}
