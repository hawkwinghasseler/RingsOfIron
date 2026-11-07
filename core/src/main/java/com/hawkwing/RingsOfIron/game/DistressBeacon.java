package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.FactionStanding;
import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.PlayerShipStatus;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.uihud.providers.ShipStatusProvider;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/**
 * The distress beacon: a ship that can no longer get itself home, disabled or out of propellant,
 * calls the sponsor's tug. So does one that can't dock anywhere ({@link #cannotDock}): every
 * collar jammed or torn off, and no airlock aboard that can let the crew out to free a jammed one. After the same days as a rescue the ship is towed into the sponsor's
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

    /**
     * True when the player's ship can't get home by itself: disabled, propellant below the low mark,
     * or no way to dock ({@link #cannotDock}).
     */
    public boolean stranded() {
        Entity ship = ctx.player();
        if (ship == null) return false;
        if (ShipDisablement.reason(ship) != null || cannotDock(ship)) return true;
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

    /**
     * True if {@code ship} has docking ports but can latch at none of them, nor be made to by its
     * crew: every collar jammed or torn off, and no airlock aboard that can cycle (the hit that
     * jammed a lone port wrecked its door too), so nobody can go out on EVA to free a jammed one
     * now. The crew can still brace a wrecked door or cut a port open ({@link JammedCollars}), but
     * the tow is there as the other way out: the tug clamps the ship in at the station, and the yard
     * there refits the collar and the doors. A torn collar only a yard mends.
     */
    public static boolean cannotDock(Entity ship) {
        DockingPortsComponent ports = ship.getComponent(DockingPortsComponent.class);
        if (ports == null || ports.ports.length == 0) return false;
        boolean jammed = false;
        for (DockingPort p : ports.ports) {
            if (p.usable()) return false;
            jammed |= p.intact() && p.jammed;
        }
        if (!jammed) return true;
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid == null) return true;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint m = grid.mounts.get(i);
            if (m.kind == MountFootprint.MountKind.PORT && Airlock.works(grid, m)) return false;
        }
        return true;
    }
}
