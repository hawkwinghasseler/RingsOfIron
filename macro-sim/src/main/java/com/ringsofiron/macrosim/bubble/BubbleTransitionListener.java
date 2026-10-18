package com.ringsofiron.macrosim.bubble;

import com.ringsofiron.macrosim.rails.RailsModel.Burn;

import java.util.List;

/** Hooks for modules that care when bodies cross the bubble boundary (AI, combat, audio, HUD). */
public interface BubbleTransitionListener {
    /** The body now has a Box2D body. {@code cancelledBurns} are its rails plan, to be flown as AI goals. */
    default void onEnterBubble(String uuid, List<Burn> cancelledBurns, double t) {
    }

    /** The body is back on rails. */
    default void onExitBubble(String uuid, double t) {
    }

    default void onFastPassStart(String uuid, double t) {
    }

    default void onFastPassEnd(String uuid, double t) {
    }
}
