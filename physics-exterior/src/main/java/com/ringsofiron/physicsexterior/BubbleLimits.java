package com.ringsofiron.physicsexterior;

/**
 * Size and speed limits of the physics bubble, in meters and m/s relative to the anchor.
 * See {@code documentation/orbital-mechanics/Design.md} sections 4 and 5.
 */
public final class BubbleLimits {
    /** A rails body closer than this to the anchor is brought into the bubble. */
    public static final float BUBBLE_ENTER_RADIUS = 20_000f;
    /** A bubble body further than this from the anchor goes back onto rails. */
    public static final float BUBBLE_EXIT_RADIUS = 25_000f;
    /**
     * Fastest relative speed at which a body may become a Box2D body. Anything faster is a
     * kinematic transient. Leaves margin under {@link PhysicsManager#MAX_SPEED}.
     */
    public static final float B2D_ENTRY_SPEED = 1_000f;
    /** Fastest a projectile may ever fly and still be a Box2D bullet; faster ones are raycast. */
    public static final float B2D_PROJECTILE_MAX_SPEED = 800f;

    private BubbleLimits() {
    }
}
