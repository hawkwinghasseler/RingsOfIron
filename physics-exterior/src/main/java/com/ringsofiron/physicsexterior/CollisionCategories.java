package com.ringsofiron.physicsexterior;

/**
 * Box2D collision filter bits for exterior bodies.
 *
 * Use a category as a fixture's {@code categoryBits} and a mask as its {@code maskBits}.
 */
public final class CollisionCategories {
    public static final short CATEGORY_SHIP = 0x0001;
    public static final short CATEGORY_ASTEROID = 0x0002;
    public static final short CATEGORY_PROJECTILE = 0x0004;
    public static final short CATEGORY_STATION = 0x0008;
    /**
     * Added to a docking port's latch collar, on top of its owner's category (ship or station), so
     * the collar can be told from the hull. Collars collide like the hull they belong to.
     */
    public static final short CATEGORY_COLLAR = 0x0010;

    public static final short MASK_ALL = (short) 0xFFFF;
    /** Projectiles hit everything except other projectiles. */
    public static final short MASK_PROJECTILE = (short) (MASK_ALL & ~CATEGORY_PROJECTILE);

    private CollisionCategories() {
    }
}
