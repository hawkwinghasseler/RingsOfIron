package com.ringsofiron.moddingdata.defs;

/**
 * A step on the sponsoring faction's rank ladder. {@code rank.<id>}.
 * <pre>
 * [rank.lieutenant]
 * name = "Lieutenant"
 * order = 1                        # place on the ladder, lowest first
 * standing = 20.0                  # standing with the sponsor that earns it
 * budget = 5000.0                  # credits paid each in-game week while holding it
 * max_chassis = "corvette"         # largest chassis it may requisition
 * </pre>
 */
public class RankDef {
    public String id;
    public String name;
    public int order;
    public float standing;
    public double budget;
    public String maxChassis;
}
