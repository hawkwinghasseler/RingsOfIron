package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.PlayerShipStatus;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.ShipStatusProvider;

import java.util.Locale;

/**
 * The distress beacon: a ship that can no longer get itself home, disabled or out of propellant,
 * calls the sponsor's tug. After the same days as a rescue the ship is towed into the sponsor's
 * nearest station and docked there, keeping its crew, cargo and damage, for a fee of
 * {@link #FEE_SHARE} of the ship's value (the account can go negative) and
 * {@link #STANDING_COST} standing with the sponsor. Called with B at the helm while it is
 * allowed, twice to confirm, or from the pause menu. See {@link Rescue#tow}.
 */
public final class DistressBeacon {
    /** The tow costs this share of the ship's value. */
    public static final double FEE_SHARE = 0.10;
    /** ...and this much standing with the sponsor. */
    public static final float STANDING_COST = 3f;

    private final GameWorld world;
    private final PlayerShipStatus status;

    DistressBeacon(GameWorld world) {
        this.world = world;
        this.status = new PlayerShipStatus(world::getPlayer);
    }

    /** True when the player's ship can't get home by itself: disabled, or propellant below the low mark. */
    public boolean stranded() {
        Entity ship = world.getPlayer();
        if (ship == null) return false;
        if (ShipDisablement.reason(ship) != null) return true;
        ShipStatusProvider.ShipStatus s = status.getShipStatus();
        return s != null && !Float.isNaN(s.propellant) && s.propellant < FlightWarnings.LOW_PROPELLANT;
    }

    /** Why the beacon can't be called now, or null when it can. */
    public String refusal() {
        Entity ship = world.getPlayer();
        if (ship == null || world.getCrash() != null) return "there is no ship to tow";
        if (world.playerStation() != null) return "you are docked at a station";
        if (world.rescue.pending()) return "the pods are away";
        if (!stranded()) return "your ship can still fly home";
        if (world.warnings.inbound() > 0) return "missiles are inbound; no tug will come into that";
        return null;
    }

    /** What the tow will cost, credits. */
    public double fee() {
        Entity ship = world.getPlayer();
        return ship == null ? 0 : Math.round(Difficulty.shipValue(ship) * FEE_SHARE);
    }

    /** Calls the tug. Returns what to tell the player. */
    public String call() {
        String refused = refusal();
        if (refused != null) return "Can't call a tow: " + refused;
        Entity ship = world.getPlayer();
        double fee = fee();
        String failed = world.rescue.tow(ship);
        if (failed != null) return "Can't call a tow: " + failed;
        TraderComponent account = ship.getComponent(TraderComponent.class);
        if (account != null) account.credits -= fee;
        world.standing.change(world.standing.sponsor(), -STANDING_COST);
        return String.format(Locale.ROOT, "Towed in: the tow cost %,.0f cr and %.0f standing", fee, STANDING_COST);
    }
}
