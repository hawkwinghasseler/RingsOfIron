package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.providers.CaptainStatusProvider;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.TargetStatusProvider;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame;
import com.ringsofiron.uihud.providers.UiContextProvider;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;

import java.util.function.Function;

/**
 * The common data layer every HUD variant reads: one snapshot per frame of everything the three
 * functional specs show, so a variant never reaches into the game itself. The game wiring layer sets
 * the providers once ({@code GameHud}); {@link #refresh} reads them all into the public snapshots at
 * the start of each HUD frame, before the variant's {@code update}.
 *
 * <p>Each snapshot is valid for the frame it was read in; a variant may hold the objects but must
 * not keep their contents across frames. A provider left null leaves its snapshot cleared, so a
 * variant can always read every field.
 */
public final class HudData {
    // --- What the game sets ---------------------------------------------------------------
    public UiContextProvider context;
    public ShipStatusProvider ship;
    public CaptainStatusProvider captain;
    public WeaponStatusProvider weapons;
    public ShieldStatusProvider shields;
    public PowerStatusProvider power;
    public TargetStatusProvider target;
    public ThreatProvider threats;
    /** Also set by {@code UiHud} when the game sets them there. */
    public TargetingProvider targeting;
    public OrbitInfoProvider orbitInfo;
    public InteractorProvider interactor;
    public PhysicsManager physics;
    /** Key names for actions, by {@code Action} name ("FIRE" → "Space"), or null to show none. */
    public Function<String, String> keyNames;
    /**
     * Run at the end of each {@link #refresh}, with the snapshots read: the game raises its
     * {@link #alerts} and fills {@link #messages} here, so they are current before the variant draws.
     */
    public Runnable afterRefresh;

    // --- Kept by the game, read by the variants -------------------------------------------
    /** Everything wrong right now. The game raises alerts each frame. */
    public final AlertRegistry alerts = new AlertRegistry();
    /** What the game has told the player lately. */
    public final MessageFeed messages = new MessageFeed();

    // --- This frame's snapshots -----------------------------------------------------------
    public final UiContext ctx = new UiContext();
    public final ShipStatus shipStatus = new ShipStatus();
    public final CaptainStatus captainStatus = new CaptainStatus();
    public final WeaponFrame weaponFrame = new WeaponFrame();
    public final ShieldStatus shieldStatus = new ShieldStatus();
    public final PowerFrame powerFrame = new PowerFrame();
    public final TargetStatus targetStatus = new TargetStatus();
    public final ThreatFrame threatFrame = new ThreatFrame();
    /** Whether {@link #shipStatus} holds a ship this frame. */
    public boolean hasShipStatus;
    /**
     * What the interact key does here, as the classic prompt words it after "Press [E] " ("to put on a
     * pressure suit", "for the Helm panel"), or "" with nothing in reach; and that key's name.
     */
    public String promptAction = "", promptKey = "E";
    /** The docked banner's text ("Docked at Highport   [T] Trade   [Y] Yard"), or "" when not docked. */
    public String dockHint = "";

    private RoomNames roomNames = new RoomNames();
    private Entity roomShip;

    /** Reads every provider into its snapshot. Called by {@code UiHud} once a frame while a variant is up. */
    public void refresh() {
        ctx.clear();
        if (context != null) context.read(ctx);
        shipStatus.clear();
        ShipStatus s = ship == null ? null : ship.getShipStatus();
        hasShipStatus = s != null;
        if (s != null) copy(s, shipStatus);
        captainStatus.clear();
        if (captain != null) captain.read(captainStatus);
        weaponFrame.clear();
        if (weapons != null) weapons.collect(weaponFrame);
        shieldStatus.clear();
        if (shields != null) shields.read(shieldStatus);
        powerFrame.clear();
        if (power != null) power.collect(powerFrame);
        targetStatus.clear();
        if (target != null) target.read(targetStatus);
        threatFrame.clear();
        if (threats != null) threats.collect(threatFrame);
        if (afterRefresh != null) afterRefresh.run();
    }

    /** The name of the room at tile ({@code x}, {@code y}) of the player's ship, or "" ({@link RoomNames}). */
    public String roomAt(int x, int y) {
        ShipGridComponent grid = grid();
        return grid == null ? "" : roomNames.at(grid, x, y);
    }

    /** The player's ship's room names, kept with its layout; null with no ship. */
    public RoomNames roomNames() {
        return grid() == null ? null : roomNames;
    }

    /** The key for {@code action} (an {@code Action} name), or "" when unknown. */
    public String key(String action) {
        String k = keyNames == null ? null : keyNames.apply(action);
        return k == null ? "" : k;
    }

    private ShipGridComponent grid() {
        Entity ship = ctx.ship;
        if (ship != roomShip) {
            // Another ship: its rooms are named afresh
            roomShip = ship;
            roomNames = new RoomNames();
        }
        return ship == null ? null : ship.getComponent(ShipGridComponent.class);
    }

    private static void copy(ShipStatus from, ShipStatus to) {
        to.propellant = from.propellant;
        to.propellantKg = from.propellantKg;
        to.structure = from.structure;
        to.power = from.power;
        to.powerOutputWatts = from.powerOutputWatts;
        to.oxygen = from.oxygen;
        to.emergencyOxygenKg = from.emergencyOxygenKg;
        to.emergencyOxygen = from.emergencyOxygen;
        to.cabinPressure = from.cabinPressure;
    }
}
