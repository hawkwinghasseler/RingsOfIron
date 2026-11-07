package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.CaptainStatusProvider;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.ContactFrame;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.GameOverProvider;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHintFrame;
import com.ringsofiron.uihud.providers.ManeuverPlannerProvider;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.OrdersProvider;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider.StationBoard;
import com.ringsofiron.uihud.providers.TargetStatusProvider;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.providers.UiContextProvider;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.providers.YardProvider;

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
    /** Ship lost or career over; null leaves {@link #gameOver} down. */
    public GameOverProvider ending;
    /** Also set by {@code UiHud} when the game sets them there. */
    public TargetingProvider targeting;
    public OrbitInfoProvider orbitInfo;
    public InteractorProvider interactor;
    public PhysicsManager physics;

    // --- The classic windows' providers, for a variant's own screens ({@code ClassicWindow}) ----
    // Set by UiHud's setters as the game sets them there. A screen reads them when it draws (each
    // fills a board, as the classic window's refresh does) and acts through them (buy, take, board).
    /** The station market ({@code ClassicWindow.TRADE}). */
    public TradeProvider trade;
    /** The yard, or the crew's field refit away from one ({@code ClassicWindow.YARD}). */
    public YardProvider yard;
    /** Ships for sale, the trade-in, a prize to hand over ({@code ClassicWindow.SHIPYARD}). */
    public ShipyardProvider shipyard;
    /** The mission board: the career line, offers and the contracts held ({@code ClassicWindow.MISSIONS}). */
    public MissionProvider missions;
    /** Boarding and salvage while latched ({@code ClassicWindow.BOARDING}). */
    public BoardingProvider boarding;
    /** The crew roster ({@code ClassicWindow.CREW}). */
    public CrewProvider crew;
    /** The orbit, the plan's paths and the bodies, as the map draws them ({@code ClassicWindow.MAP}). */
    public OrbitPathProvider orbitPath;
    /** What the map's panel does: the burns, transfers, warp, the helm's and the autopilot's orders. */
    public ManeuverPlannerProvider planner;
    /** The traffic round the body, for the map. */
    public TrafficMapProvider trafficMap;
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
    /**
     * The record of the conditions the game raised, as they were raised and as they cleared, for a
     * log among the notices: read from {@link #alerts} at the end of each {@link #refresh}, whatever
     * the HUD shows, so a change of HUD keeps it, and saved with the game.
     */
    public final ConditionLog record = new ConditionLog();
    /** The last hit on the player's hull, for a schematic to flash ({@link #hullHit(int, int)}). */
    public final HullHit lastHullHit = new HullHit();
    /** The game's magnified insets this frame (picture-in-picture), set as it lays them, before the HUD updates. */
    public final Insets insets = new Insets();
    /** The console E opened, while a variant shows its screen for it ({@code ClassicWindow.CONSOLE}); kept by {@code UiHud}. */
    public final ConsoleView console = new ConsoleView();

    /**
     * The console the player is using, for a variant's own console screen: the entity (its
     * {@code SubsystemComponent} is the system, health and switch the classic window shows), what E
     * found there, and what else the game offers at it ("Take fire control"). Empty while none.
     */
    public static final class ConsoleView {
        public Entity console;
        public InteractableConsoleComponent data;
        /** The game's own actions at this console, in order ({@code UiHud#registerConsoleAction}). */
        public final Array<ConsoleAction> actions = new Array<>();
        /**
         * Tiles from the player's avatar to the console's tile, NaN while the avatar is not aboard its
         * ship: for a panel that closes as the captain walks off. Kept by {@code UiHud} each frame.
         */
        public float reach = Float.NaN;
        /** Where the console stands, world (bubble) meters, for a panel drawn beside it; NaN when its ship has no pose. */
        public float worldX = Float.NaN, worldY = Float.NaN;
        /**
         * The corners of its ship's deck, world meters, in turn round it from the grid's first tile: for a
         * panel that keeps off the ship. NaN when its ship has no pose.
         */
        public final float[] hullX = new float[4], hullY = new float[4];

        {
            unplaced();
        }

        public boolean isOpen() {
            return console != null;
        }

        public void set(Entity console, InteractableConsoleComponent data, Array<ConsoleAction> actions) {
            this.console = console;
            this.data = data;
            this.actions.clear();
            if (actions != null) this.actions.addAll(actions);
            reach = worldX = worldY = Float.NaN;
            unplaced();
        }

        public void clear() {
            console = null;
            data = null;
            actions.clear();
            reach = worldX = worldY = Float.NaN;
            unplaced();
        }

        private void unplaced() {
            java.util.Arrays.fill(hullX, Float.NaN);
            java.util.Arrays.fill(hullY, Float.NaN);
        }

        /**
         * Takes {@code reach}, and works out where the console stands from its ship's pose: the middle
         * of its tile, turned and moved as the ship is; and the corners of the ship's deck likewise.
         */
        public void track(float reach) {
            this.reach = reach;
            Entity ship = data == null ? null : data.ship;
            TransformComponent t = ship == null ? null : ship.getComponent(TransformComponent.class);
            ShipGridComponent g = ship == null ? null : ship.getComponent(ShipGridComponent.class);
            if (t == null || g == null) {
                worldX = worldY = Float.NaN;
                unplaced();
                return;
            }
            double a = Math.toRadians(t.rotation);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            Vector2 local = g.tileToLocal(data.tileX, data.tileY);
            worldX = t.x + cos * local.x - sin * local.y;
            worldY = t.y + sin * local.x + cos * local.y;
            Vector2 first = g.tileToLocal(0, 0), last = g.tileToLocal(g.width - 1, g.height - 1);
            float half = g.tileSize / 2f, x0 = first.x - half, y0 = first.y - half, x1 = last.x + half, y1 = last.y + half;
            for (int i = 0; i < 4; i++) {
                float lx = i == 1 || i == 2 ? x1 : x0, ly = i >= 2 ? y1 : y0;
                hullX[i] = t.x + cos * lx - sin * ly;
                hullY[i] = t.y + sin * lx + cos * ly;
            }
        }
    }

    /** An action the game offers at a console, as the classic window's button: what it says, its key, and what it does. */
    public static final class ConsoleAction {
        /** "Take fire control: every gun". */
        public final String label;
        /** The {@code Action} name of its key ("VIEW"), for {@link HudData#key}; "" for none. */
        public final String action;
        public final Runnable run;

        public ConsoleAction(String label, String action, Runnable run) {
            this.label = label;
            this.action = action == null ? "" : action;
            this.run = run;
        }
    }

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
    /** Ship lost or career over, and the ways to go on ({@code ClassicWindow.GAME_OVER}). */
    public final GameOver gameOver = new GameOver();
    /** Whether {@link #shipStatus} holds a ship this frame. */
    public boolean hasShipStatus;
    /**
     * What the interact key does here, as the classic prompt words it after "Press [E] " ("to put on a
     * pressure suit", "for the Helm panel"), or "" with nothing in reach; and that key's name.
     */
    public String promptAction = "", promptKey = "E";
    /** The id of the console that prompt is for ("helm", "reactor"), or "" when it is for none. */
    public String promptConsole = "";
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

    /**
     * Reads every provider into its snapshot, then the {@link #record} the alerts the game raised from
     * them. Called by {@code UiHud} once a frame while a variant is up.
     */
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
        gameOver.clear();
        if (ending != null) ending.read(gameOver);
        if (afterRefresh != null) afterRefresh.run();
        record.update(alerts, weaponFrame.mounts, ctx.simSeconds);
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
