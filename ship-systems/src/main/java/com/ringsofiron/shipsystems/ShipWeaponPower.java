package com.ringsofiron.shipsystems;

import com.badlogic.ashley.core.ComponentMapper;
import com.ringsofiron.combatsys.WeaponPower;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * Weapons take their energy from the ship's power grid: a mount draws through the turret
 * mount or weapons system it is wired to, from that system's power circuit. A circuit's store
 * refills from its reactors, so a damaged reactor or a cut conduit slows or silences the guns
 * on it. A mount wired to no system (a test rig, a mod ship without one) has unlimited power.
 */
public class ShipWeaponPower implements WeaponPower {
    private final ComponentMapper<SubsystemComponent> systems = ComponentMapper.getFor(SubsystemComponent.class);

    @Override
    public boolean tryDraw(WeaponMountComponent mount, double joules) {
        SubsystemComponent s = systemOf(mount);
        return s == null || s.tryDrawPower(joules);
    }

    @Override
    public double draw(WeaponMountComponent mount, double joules) {
        SubsystemComponent s = systemOf(mount);
        if (s == null) return joules;
        return s.isOperational && s.circuit != null ? s.circuit.draw(joules) : 0;
    }

    private SubsystemComponent systemOf(WeaponMountComponent mount) {
        return mount.subsystem == null ? null : systems.get(mount.subsystem);
    }
}
