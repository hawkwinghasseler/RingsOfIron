package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.uihud.providers.ShipStatusProvider;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

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

    private final GameContext ctx;
    private final BooleanSupplier crashed;
    private final Supplier<Entity> playerStation;
    private final Rescue rescue;
    private final FactionStanding standing;
    private final FlightWarnings warnings;
    private final PlayerShipStatus status;

    DistressBeacon(GameContext ctx, BooleanSupplier crashed, Supplier<Entity> playerStation, Rescue rescue, FactionStanding standing, FlightWarnings warnings) {
        this.ctx = ctx;
        this.crashed = crashed;
        this.playerStation = playerStation;
        this.rescue = rescue;
        this.standing = standing;
        this.warnings = warnings;
        this.status = new PlayerShipStatus(ctx::player);
    }

    /** True when the player's ship can't get home by itself: disabled, or propellant below the low mark. */
    public boolean stranded() {
        Entity ship = ctx.player();
        if (ship == null) return false;
        if (ShipDisablement.reason(ship) != null) return true;
        ShipStatusProvider.ShipStatus s = status.getShipStatus();
        return s != null && !Float.isNaN(s.propellant) && s.propellant < FlightWarnings.LOW_PROPELLANT;
    }

    /** Why the beacon can't be called now, or null when it can. */
    public String refusal() {
        Entity ship = ctx.player();
        if (ship == null || crashed.getAsBoolean()) return "there is no ship to tow";
        if (playerStation.get() != null) return "you are docked at a station";
        if (rescue.pending()) return "the pods are away";
        if (!stranded()) return "your ship can still fly home";
        if (warnings.inbound() > 0) return "missiles are inbound; no tug will come into that";
        return null;
    }

    /** What the tow will cost, credits. */
    public double fee() {
        Entity ship = ctx.player();
        return ship == null ? 0 : Math.round(Difficulty.shipValue(ship) * FEE_SHARE);
    }

    /** Calls the tug. Returns what to tell the player. */
    public String call() {
        String refused = refusal();
        if (refused != null) return "Can't call a tow: " + refused;
        Entity ship = ctx.player();
        double fee = fee();
        String failed = rescue.tow(ship);
        if (failed != null) return "Can't call a tow: " + failed;
        TraderComponent account = ship.getComponent(TraderComponent.class);
        if (account != null) account.credits -= fee;
        standing.change(standing.sponsor(), -STANDING_COST);
        return String.format(Locale.ROOT, "Towed in: the tow cost %,.0f cr and %.0f standing", fee, STANDING_COST);
    }
}
