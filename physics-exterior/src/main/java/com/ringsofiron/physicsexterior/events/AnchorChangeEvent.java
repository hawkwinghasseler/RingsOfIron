package com.ringsofiron.physicsexterior.events;

import com.ringsofiron.physicsexterior.anchor.AnchorModel;

/**
 * The bubble's anchor changed: a new motion model or parent body ({@link Kind#SET}), a rebase
 * that restarted it from the player's state ({@link Kind#REBASED}), or the bubble collapsed onto
 * rails so there is no live anchor until it is rebuilt ({@link Kind#CLEARED}). Lets rails code
 * (orbital-mechanics' event system) keep its copy of the anchor orbit in step.
 *
 * The instance is reused; copy anything you keep.
 */
public class AnchorChangeEvent {
    public enum Kind { SET, REBASED, CLEARED }

    public Kind kind;
    /** The anchor's motion model after the change. */
    public AnchorModel model;
    /** The body the bubble falls around, or null if none was named. */
    public String parentUuid;
    /** Sim seconds at which the change happened. */
    public double time;
}
