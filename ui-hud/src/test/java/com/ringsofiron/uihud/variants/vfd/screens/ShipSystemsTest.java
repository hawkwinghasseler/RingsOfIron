package com.ringsofiron.uihud.variants.vfd.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A gun outside the rack is its weapon and its mount, the mount less its kind (functional-context, version 19). */
class ShipSystemsTest {
    @Test
    void aGunsMountIsWhereItSitsTheWeaponSaysWhatItIs() {
        assertEquals("FWD PORT", ShipSystems.where("FWD PORT PD"));
        assertEquals("NOSE", ShipSystems.where("NOSE GUN"));
        assertEquals("PORT 2", ShipSystems.where("PORT PD 2"), "two alike keep their numbers");
        // A mount the game could not place keeps its name, so the row still tells two apart
        assertEquals("PD", ShipSystems.where("PD"));
        assertEquals("GUN 2", ShipSystems.where("gun 2"));
    }
}
