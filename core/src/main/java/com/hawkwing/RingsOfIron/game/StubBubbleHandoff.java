package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.orbital.warp.BubbleHandoff;

/**
 * Stand-in until macro-sim's bubble transitions land: does no handoff, so rails warp only runs when
 * the player is already on rails (has an {@code OrbitComponent}). Replace via
 * {@link GameWorld.Modules#bubble}.
 */
public class StubBubbleHandoff implements BubbleHandoff {
    @Override
    public void collapseToRails() {
    }

    @Override
    public void rebuildFromRails() {
    }
}
