package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.providers.CaptainStatusProvider;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.ContactFrame;
import com.ringsofiron.uihud.providers.DockingApproachProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHintFrame;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrdersProvider;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider.StationBoard;
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
    public FlightStatusProvider flight;
    public NavigationProvider navigation;
    public DockingApproachProvider dockingApproach;
    public ContactProvider contacts;
    public ObjectiveProvider objectives;
    public StationBoardProvider stationBoard;
    public KeyHintProvider hints;
    public OrdersProvider orders;
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
    /** The last hit on the player's hull, for a schematic to flash ({@link #hullHit(int, int)}). */
    public final HullHit lastHullHit = new HullHit();
    /** The game's magnified insets this frame (picture-in-picture), set as it lays them, before the HUD updates. */
    public final Insets insets = new Insets();

    /** A hit on the player's hull: the deck tile it struck, and seconds since, NaN before any. */
    public static final class HullHit {
        public int tileX = -1, tileY = -1;
        public float age = Float.NaN;
    }

    // --- This frame's snapshots -----------------------------------------------------------
    public final UiContext ctx = new UiContext();
    public final ShipStatus shipStatus = new ShipStatus();
    public final CaptainStatus captainStatus = new CaptainStatus();
    public final WeaponFrame weaponFrame = new WeaponFrame();
    public final ShieldStatus shieldStatus = new ShieldStatus();
    public final PowerFrame powerFrame = new PowerFrame();
    public final TargetStatus targetStatus = new TargetStatus();
    public final ThreatFrame threatFrame = new ThreatFrame();
    /** Speed against the frame that matters, engines, delta-v. */
    public final FlightStatus flightStatus = new FlightStatus();
    /** The nav target, the next burn, the station docked at. */
    public final NavReadout navReadout = new NavReadout();
    /** The selected docking port and how it lines up. */
    public final DockingApproach docking = new DockingApproach();
    /** Every contact in the bubble, as values. */
    public final ContactFrame contactFrame = new ContactFrame();
    /** The contracts held. */
    public final ObjectiveFrame objectiveFrame = new ObjectiveFrame();
    /** Who is at each post. */
    public final StationBoard stations = new StationBoard();
    /** The keys worth knowing where the player is. */
    public final KeyHintFrame keyHints = new KeyHintFrame();
    /** The orders that stand, and why any can't be given now. */
    public final Orders orderState = new Orders();
    /** Whether {@link #shipStatus} holds a ship this frame. */
    public boolean hasShipStatus;
    /**
     * What the interact key does here, as the classic prompt words it after "Press [E] " ("to put on a
     * pressure suit", "for the Helm panel"), or "" with nothing in reach; and that key's name.
     */
    public String promptAction = "", promptKey = "E";
    /** The docked banner's text ("Docked at Highport   [T] Trade   [Y] Yard"), or "" when not docked. */
    public String dockHint = "";
    /**
     * The tutorial running now, for a variant's guide card: its heading ("FIRST PLAY · 2 OF 9",
     * "GETTING STARTED") and the objective with the player's keys, both "" with no guide running; and
     * whether that is a finished guide's last word. Set each frame by the game.
     */
    public String guideTitle = "", guideText = "";
    public boolean guideDone;

    private RoomNames roomNames = new RoomNames();
    private Entity roomShip;

    /** The player's hull was hit at deck tile ({@code tileX}, {@code tileY}), as {@code UiHud#flashHullHit} hears it. */
    public void hullHit(int tileX, int tileY) {
        lastHullHit.tileX = tileX;
        lastHullHit.tileY = tileY;
        lastHullHit.age = 0f;
    }

    /** {@code delta} seconds pass for what the game keeps here. Called by {@code UiHud} once a frame, variant or none. */
    public void age(float delta) {
        if (!Float.isNaN(lastHullHit.age)) lastHullHit.age += delta;
    }

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
        flightStatus.clear();
        if (flight != null) flight.read(flightStatus);
        navReadout.clear();
        if (navigation != null) navigation.read(navReadout);
        docking.clear();
        if (dockingApproach != null) dockingApproach.read(docking);
        contactFrame.clear();
        if (contacts != null) contacts.collect(contactFrame);
        objectiveFrame.clear();
        if (objectives != null) objectives.collect(objectiveFrame);
        stations.clear();
        if (stationBoard != null) stationBoard.collect(stations);
        keyHints.clear();
        if (hints != null) hints.collect(keyHints);
        orderState.clear();
        if (orders != null) orders.read(orderState);
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
