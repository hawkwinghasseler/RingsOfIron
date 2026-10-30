package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.hostile.HostileComponent;

/**
 * Round 10: which ships have struck, for the player's guns firing at will and for Tab. A ship has
 * struck when it has {@link DisabledComponent.Reason#SURRENDERED surrendered}, or when it is one of
 * a fallen base's gun platforms ({@link PirateBase#silenced}): those never surrender, they fall
 * silent with the base. A ship disabled for other reasons (a short, a broken hull) has not struck.
 */
final class Struck {
    private Struck() {
    }

    /** True if {@code ship} has struck its colours: surrendered, or a fallen base's silenced battery. */
    static boolean struck(GameWorld world, Entity ship) {
        return struck(world.pirateBase, ship);
    }

    /** {@link #struck(GameWorld, Entity)}, with the pirates' base, or null before there is one. */
    static boolean struck(PirateBase base, Entity ship) {
        if (ship == null) return false;
        return ShipDisablement.reason(ship) == DisabledComponent.Reason.SURRENDERED || silencedBattery(base, ship);
    }

    /** True if {@code ship} is one of a fallen base's gun platforms. */
    static boolean silencedBattery(GameWorld world, Entity ship) {
        return silencedBattery(world.pirateBase, ship);
    }

    /** {@link #silencedBattery(GameWorld, Entity)}, with the pirates' base, or null before there is one. */
    static boolean silencedBattery(PirateBase base, Entity ship) {
        HostileComponent h = ship == null ? null : ship.getComponent(HostileComponent.class);
        return h != null && base != null && base.silenced(h.displayName);
    }
}
