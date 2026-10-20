package com.ringsofiron.moddingdata.defs;

/**
 * A step on the sponsoring faction's rank ladder. {@code rank.<id>}.
 * <pre>
 * [rank.lieutenant]
 * name = "Lieutenant"
 * order = 1                        # place on the ladder, lowest first
 * standing = 20.0                  # standing with the sponsor that earns it
 * budget = 5000.0                  # credits paid each in-game week while holding it
 * max_chassis = "corvette"         # largest chassis it may fly, requisitioned or bought
 * min_days = 5.0                   # days at the rank before it, at least, before this one is given
 * </pre>
 */
public class RankDef {
    public String id;
    public String name;
    public int order;
    public float standing;
    public double budget;
    public String maxChassis;
    /** In-game days the player must hold the rank below before this one is given, whatever their standing; 0 for none. */
    public double minDays;
    /**
     * In-game days the player must hold this rank before a yard sells them a chassis no lower rank may
     * fly (the first frigate, say); 0 for none.
     */
    public double shipDays;
}
