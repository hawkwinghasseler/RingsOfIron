package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.uihud.providers.OrdersProvider;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * ui-hud's {@link OrdersProvider}: the helm's standing order, and why each of the others can't be
 * given now as {@link HelmOrders#give} would answer it; the {@link com.hawkwing.RingsOfIron.game.Autopilot
 * autopilot}; the crew's standing
 * orders on their task board; abandoning ship ({@link AbandonShip}) and the tow
 * ({@link DistressBeacon}), with the second press pending on their keys. Only reads: nothing here
 * gives an order, and the words are the game's own.
 */
public class PlayerOrders implements OrdersProvider {
    /**
     * The tow's fee and refusal, the station it goes to and the ship a rescue gives, are worked out
     * once every this many reads: the fee walks the deck plan.
     */
    static final int TOW_REFRESH = 30;

    /** Each helm order's mode, by {@link HelmOrder} ordinal: the two share their names. */
    private static final HelmOrderComponent.Mode[] MODE = new HelmOrderComponent.Mode[HelmOrder.values().length];

    static {
        for (HelmOrder h : HelmOrder.values()) MODE[h.ordinal()] = HelmOrderComponent.Mode.valueOf(h.name());
    }

    private final GameContext ctx;
    private final Navigation nav;
    private final AbandonShip abandon;
    private final DistressBeacon beacon;
    private final BooleanSupplier seated, paused;
    private final DoubleSupplier abandonArmed, towArmed;
    private final AbandonShip.Boarding boarding = new AbandonShip.Boarding();
    private int towReads;
    private String towRefusal = "", towStation = "", abandonShip = "";
    private double towFee;

    /**
     * @param seated       the player sits at a console, where the helm orders and the autopilot are taken
     * @param paused       the game is paused
     * @param abandonArmed seconds left to press abandon ship again and give the order, 0 when not pending
     * @param towArmed     the same for the tow
     */
    public PlayerOrders(GameContext ctx, Navigation nav, AbandonShip abandon, DistressBeacon beacon, BooleanSupplier seated,
                        BooleanSupplier paused, DoubleSupplier abandonArmed, DoubleSupplier towArmed) {
        this.ctx = ctx;
        this.nav = nav;
        this.abandon = abandon;
        this.beacon = beacon;
        this.seated = seated;
        this.paused = paused;
        this.abandonArmed = abandonArmed;
        this.towArmed = towArmed;
    }

    @Override
    public void read(Orders out) {
        out.paused = paused.getAsBoolean();
        out.abandonArmed = (float) Math.max(0, abandonArmed.getAsDouble());
        out.towArmed = (float) Math.max(0, towArmed.getAsDouble());
        Entity ship = ctx.player();
        if (ship == null) return;
        out.seated = seated.getAsBoolean();
        helm(out, ship);
        autopilot(out);
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        if (board != null) {
            out.battleStations = board.battleStations;
            out.suitsAtBattleStations = board.suitsAtBattleStations;
            out.evaHeld = board.evaHeld;
        }
        abandon(out, ship);
        tow(out);
    }

    private void helm(Orders out, Entity ship) {
        // Read off the ship: HelmOrders.order() would add the component to one that has none
        HelmOrderComponent o = ship.getComponent(HelmOrderComponent.class);
        if (o != null && o.mode != HelmOrderComponent.Mode.NONE) {
            out.helm = HelmOrder.valueOf(o.mode.name());
            out.helmTarget = o.targetUuid == null ? "" : nav.target.name(o.targetUuid);
            if (o.mode == HelmOrderComponent.Mode.APPROACH) out.approachDistance = (float) o.distance;
            String line = nav.helm.describe();
            out.helmLine = line == null ? "" : line;
        }
        String fromASeat = out.seated ? null : fromASeat();
        for (int i = 0; i < MODE.length; i++) {
            String refused = fromASeat != null ? fromASeat : nav.helm.refusal(MODE[i]);
            out.helmRefusal[i] = refused == null ? "" : refused;
        }
    }

    private void autopilot(Orders out) {
        AutopilotComponent a = nav.autopilot.state();
        if (a != null) {
            out.autopilot = a.held ? Autopilot.HELD : Autopilot.ON;
            out.autopilotTo = nav.target.name(a.targetUuid);
            out.autopilotHeld = a.held && a.holdReason != null ? a.holdReason : "";
        }
        String refused = !out.seated ? fromASeat() : a != null ? null : nav.autopilot.refusal();
        out.autopilotRefusal = refused == null ? "" : refused;
    }

    private void abandon(Orders out, Entity ship) {
        out.abandoning = AbandonShip.abandoning(ship);
        out.podsLaunchIn = (float) abandon.launchIn(ship);
        abandon.boarding(ship, boarding);
        out.crewAboard = boarding.aboard;
        out.inPods = boarding.inPods;
        out.pods = boarding.pods;
        out.freeSeats = boarding.freeSeats;
        out.captainInPod = boarding.playerInPod;
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (boarding.podX >= 0 && grid != null) {
            out.podTileX = boarding.podX;
            out.podTileY = boarding.podY;
            out.podDistance = boarding.playerInPod ? 0f
                    : (float) Math.hypot(boarding.podX - boarding.playerX, boarding.podY - boarding.playerY) * grid.tileSize;
        }
        // Under orders, the key launches the pods now
        String refused = out.abandoning ? null : abandon.refusal(ship);
        out.abandonRefusal = refused == null ? "" : refused;
    }

    private void tow(Orders out) {
        if (towReads++ % TOW_REFRESH == 0) {
            String refused = beacon.refusal();
            towRefusal = refused == null ? "" : refused;
            towFee = beacon.fee();
            towStation = beacon.destination();
            abandonShip = beacon.requisition();
        }
        out.towRefusal = towRefusal;
        out.towFee = towFee;
        out.towStanding = DistressBeacon.STANDING_COST;
        out.towDays = (float) DistressBeacon.DAYS;
        out.towStation = towStation;
        out.abandonStanding = LoopWiring.RESCUE_COST;
        out.abandonShip = abandonShip;
    }

    /** Said of the orders taken at a seat (the helm's and the autopilot) while the player is on deck. */
    private String fromASeat() {
        return "Take a seat to give orders: " + nav.key(Action.VIEW) + " at the helm or weapons console";
    }
}
