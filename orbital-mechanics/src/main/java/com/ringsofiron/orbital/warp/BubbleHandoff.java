package com.ringsofiron.orbital.warp;

/**
 * Collapses and rebuilds the Box2D bubble around rails warp. Implemented by the bubble side
 * (physics-exterior handoff helpers, driven by macro-sim's BubbleTransitionSystem).
 */
public interface BubbleHandoff {
    /**
     * Runs the exit handoff for every bubble body, the player included: each gets an
     * {@code OrbitComponent} from its true state and loses its Box2D body. Box2D then sits idle.
     */
    void collapseToRails();

    /** Re-anchors on the player's current rails orbit and runs the entry handoff for everything in range. */
    void rebuildFromRails();
}
