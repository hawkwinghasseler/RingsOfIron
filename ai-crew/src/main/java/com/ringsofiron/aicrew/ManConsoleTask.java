package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Man a ship's console (a helm or weapons system): stand beside it and keep at it until something
 * else comes up. (x, y) is its footprint origin. Crew only take it when no other task suits them
 * ({@link ShipTaskBoardComponent#best}), and any other task pulls them off it, when the console goes
 * back on the board. Its priority only ranks consoles against each other.
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
