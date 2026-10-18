package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/** All the weapon mounts on a ship. Ashley allows one component per type, so they live here. */
public class WeaponsComponent implements Component {
    public final Array<WeaponMountComponent> mounts = new Array<>();
    /** Index of the weapon the player has selected, for the HUD reticle. */
    public int selected;

    public WeaponMountComponent add(WeaponMountComponent mount) {
        mounts.add(mount);
        return mount;
    }

    public WeaponMountComponent selectedMount() {
        return mounts.size == 0 ? null : mounts.get(Math.min(selected, mounts.size - 1));
    }
}
