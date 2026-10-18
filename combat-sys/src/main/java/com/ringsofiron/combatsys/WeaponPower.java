package com.ringsofiron.combatsys;

import com.ringsofiron.combatsys.components.WeaponMountComponent;

/**
 * Where weapons take their energy from: the ship's power grid, through the mount's weapon
 * system. {@code WeaponControlSystem} asks before each shot for {@code power_draw * cooldown}
 * joules, all or nothing, and each tick a beam burns for {@code power_draw * dt}, taking what
 * it can get and burning that much weaker. With no source set, power is unlimited.
 */
public interface WeaponPower {
    /** Takes {@code joules} for the mount if it can have them all. Returns whether it did. */
    boolean tryDraw(WeaponMountComponent mount, double joules);

    /** Takes up to {@code joules} for the mount. Returns how much it got, 0 to {@code joules}. */
    double draw(WeaponMountComponent mount, double joules);
}
