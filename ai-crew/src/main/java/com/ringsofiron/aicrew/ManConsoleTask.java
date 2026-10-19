package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Man a ship's console (a helm or weapons system): stand beside it and keep at it until something
 * more pressing comes up. (x, y) is its footprint origin. A low priority, so any hazard or casualty
 * pulls the crew member off it, and the console goes back on the board.
 */
public class ManConsoleTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 10f;

    /** The console's subsystem entity. */
    public final Entity console;

    public ManConsoleTask(Entity console, int x, int y) {
        super(Kind.MAN_CONSOLE, x, y, DEFAULT_PRIORITY);
        this.console = console;
    }

    /** The console's type (HELM or WEAPONS), or null if it has no subsystem. */
    public SubsystemComponent.SubsystemType type() {
        SubsystemComponent s = console == null ? null : console.getComponent(SubsystemComponent.class);
        return s == null ? null : s.type;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof ManConsoleTask && ((ManConsoleTask) other).console == console;
    }
}
