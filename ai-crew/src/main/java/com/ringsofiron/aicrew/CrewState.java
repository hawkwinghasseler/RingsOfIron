package com.ringsofiron.aicrew;

/** Consciousness state of a crew member. See ai-crew/Design.md section 5. */
public enum CrewState {
    /** Health above the unconscious threshold. Acts normally. */
    CONSCIOUS,
    /** Health at or below the threshold: cannot act or move, still occupies its tile. */
    UNCONSCIOUS,
    /** Health reached 0. Removed from duty; the body stays where it fell. */
    DEAD
}
