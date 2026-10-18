package com.ringsofiron.macrosim.fleet;

import com.ringsofiron.macrosim.MacroEventQueue;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * NPC freighter brain on the rails side: picks trade orders, plans each leg as maneuver nodes,
 * loads and unloads on arrival. Main thread only.
 */
public class FreighterController {
    /** Seconds between deciding to go and the first possible burn (undocking). */
    public static final double UNDOCK_DELAY = 120;
    /** Credits charged per m/s of delta-v per unit of hold capacity, a stand-in for fuel cost. */
    public static final double FUEL_COST_PER_DV = 0.0005;
    /** Orders whose two legs take longer than this are ignored. */
    public static final double MAX_TRIP_SECONDS = 30 * 86400.0;

    private final RailsModel rails;
    private final EconomySimulation economy;
    private final MacroEventQueue events;
    private final Function<String, EconomyNode> stations;
    private final Map<String, FreighterComponent> freighters = new LinkedHashMap<>();
    private final Map<Long, TradeOrder> activeOrders = new LinkedHashMap<>();
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    public FreighterController(RailsModel rails, EconomySimulation economy, MacroEventQueue events,
                               Function<String, EconomyNode> stations) {
        this.rails = rails;
        this.economy = economy;
        this.events = events;
        this.stations = stations;
    }

    /** Registers a freighter docked at {@code stationUuid}. Puts it on the station's orbit. */
    public void addDocked(String uuid, FreighterComponent f, String stationUuid, double now) {
        freighters.put(uuid, f);
        dock(uuid, f, stationUuid, now);
    }

    public FreighterComponent get(String uuid) {
        return freighters.get(uuid);
    }

    public Map<String, FreighterComponent> all() {
        return freighters;
    }

    /** Gives every idle freighter the best order it can reach, if any. */
    public void assignIdle(double now) {
        for (Map.Entry<String, FreighterComponent> e : freighters.entrySet()) {
            FreighterComponent f = e.getValue();
            if (f.state != FreighterComponent.State.IDLE) continue;
            TradeOrder best = null;
            double bestScore = 0;
            for (TradeOrder o : economy.orders()) {
                if (o.claimedBy != null) continue;
                double score = score(e.getKey(), f, o, now);
                if (score > bestScore) {
                    bestScore = score;
                    best = o;
                }
            }
            if (best != null) start(e.getKey(), f, best, now);
        }
    }

    /** Profit per hour of an order for this freighter, or 0 if not worth it. */
    private double score(String uuid, FreighterComponent f, TradeOrder o, double now) {
        double qty = Math.min(o.quantity, f.capacity);
        String parent = rails.parentOf(uuid);
        if (!parent.equals(rails.parentOf(o.fromUuid)) || !parent.equals(rails.parentOf(o.toUuid))) return 0;
        double mu = rails.muOf(parent);
        double start = now + UNDOCK_DELAY;
        double leg1Arrival = start;
        double dv = 0;
        if (!o.fromUuid.equals(f.dockedAtUuid)) {
            TransferPlanner.Plan p1 = TransferPlanner.plan(mu, rails.stateAt(uuid, now, a),
                    rails.stateAt(o.fromUuid, now, b), now, start);
            leg1Arrival = p1.arrivalTime;
            dv += p1.totalDeltaV;
        }
        double t2 = leg1Arrival + UNDOCK_DELAY;
        TransferPlanner.Plan p2 = TransferPlanner.plan(mu, rails.stateAt(o.fromUuid, leg1Arrival, a),
                rails.stateAt(o.toUuid, leg1Arrival, b), leg1Arrival, t2);
        dv += p2.totalDeltaV;
        double duration = p2.arrivalTime - now;
        if (duration > MAX_TRIP_SECONDS) return 0;
        double profit = qty * o.unitMargin() - dv * FUEL_COST_PER_DV * f.capacity;
        return profit > 0 ? profit / (duration / 3600.0) : 0;
    }

    private void start(String uuid, FreighterComponent f, TradeOrder o, double now) {
        o.quantity = Math.min(o.quantity, f.capacity);
        economy.claim(o, uuid, stations.apply(o.fromUuid), stations.apply(o.toUuid));
        activeOrders.put(o.id, o);
        f.orderId = o.id;
        if (o.fromUuid.equals(f.dockedAtUuid)) {
            load(uuid, f, o, now);
        } else {
            f.state = FreighterComponent.State.TO_PICKUP;
            fly(uuid, f, o.fromUuid, now, now + UNDOCK_DELAY);
        }
    }

    private void load(String uuid, FreighterComponent f, TradeOrder o, double now) {
        EconomyNode from = stations.apply(o.fromUuid);
        from.reserveOut(o.commodity, -o.quantity);
        double got = from.remove(o.commodity, o.quantity);
        f.cargoCommodity = o.commodity;
        f.cargoQuantity = got;
        f.credits -= got * o.buyPrice;
        if (got < o.quantity) stations.apply(o.toUuid).expectIncoming(o.commodity, got - o.quantity);
        f.state = FreighterComponent.State.TO_DELIVERY;
        fly(uuid, f, o.toUuid, now, now + UNDOCK_DELAY);
    }

    private void fly(String uuid, FreighterComponent f, String targetUuid, double now, double earliest) {
        String parent = rails.parentOf(uuid);
        TransferPlanner.Plan plan = TransferPlanner.plan(rails.muOf(parent), rails.stateAt(uuid, now, a),
                rails.stateAt(targetUuid, now, b), now, earliest);
        follow(uuid, f, targetUuid, plan.burns, plan.arrivalTime, plan.totalDeltaV);
    }

    private void follow(String uuid, FreighterComponent f, String targetUuid, List<Burn> burns,
                        double arrival, double deltaV) {
        f.dockedAtUuid = null;
        f.destinationUuid = targetUuid;
        f.arrivalTime = arrival;
        f.deltaVUsed += deltaV;
        rails.cancelBurns(uuid);
        for (Burn burn : burns) rails.scheduleBurn(uuid, burn);
        events.cancel(f.arrivalEventId);
        f.arrivalEventId = events.schedule(arrival, () -> arrive(uuid, f, targetUuid, arrival));
    }

    private void arrive(String uuid, FreighterComponent f, String stationUuid, double now) {
        if (!rails.isOnRails(uuid)) return; // in the bubble; resumed on exit
        rails.stateAt(uuid, now, a);
        rails.stateAt(stationUuid, now, b);
        f.lastArrivalMiss = Math.hypot(a.x - b.x, a.y - b.y);
        dock(uuid, f, stationUuid, now);

        TradeOrder o = activeOrders.get(f.orderId);
        if (f.state == FreighterComponent.State.TO_PICKUP && o != null) {
            load(uuid, f, o, now);
        } else if (f.state == FreighterComponent.State.TO_DELIVERY && o != null) {
            EconomyNode to = stations.apply(stationUuid);
            to.expectIncoming(o.commodity, -f.cargoQuantity);
            to.add(f.cargoCommodity, f.cargoQuantity);
            f.credits += f.cargoQuantity * o.sellPrice;
            f.cargoQuantity = 0;
            f.cargoCommodity = null;
            f.tripsCompleted++;
            economy.close(o);
            activeOrders.remove(o.id);
            f.orderId = 0;
            f.state = FreighterComponent.State.IDLE;
        } else {
            f.state = FreighterComponent.State.IDLE;
        }
    }

    private void dock(String uuid, FreighterComponent f, String stationUuid, double now) {
        rails.cancelBurns(uuid);
        rails.attach(uuid, rails.parentOf(stationUuid), rails.stateAt(stationUuid, now, b), now);
        f.dockedAtUuid = stationUuid;
        f.destinationUuid = null;
        f.arrivalTime = Double.NaN;
    }

    /** Called when the freighter is handed to the bubble. Its plan is suspended. */
    public void onEnterBubble(String uuid) {
        FreighterComponent f = freighters.get(uuid);
        if (f == null) return;
        f.resumeState = f.state;
        f.state = FreighterComponent.State.IN_BUBBLE;
        events.cancel(f.arrivalEventId);
        f.arrivalEventId = 0;
    }

    /**
     * Called when the freighter is back on rails after the bubble. It circularizes where it is and
     * re-plans the leg it was on.
     */
    public void onExitBubble(String uuid, double now) {
        FreighterComponent f = freighters.get(uuid);
        if (f == null || f.state != FreighterComponent.State.IN_BUBBLE) return;
        f.state = f.resumeState;
        String target = f.destinationUuid != null ? f.destinationUuid : f.dockedAtUuid;
        if (target == null) {
            f.state = FreighterComponent.State.IDLE;
            return;
        }
        String parent = rails.parentOf(uuid);
        double mu = rails.muOf(parent);
        double tBurn = now + 1;
        rails.stateAt(uuid, tBurn, a);
        Burn circ = TransferPlanner.circularize(mu, a, tBurn);
        // State right after the circularisation burn: same position, circular speed, same sense.
        int dir = (a.x * a.vy - a.y * a.vx) < 0 ? -1 : 1;
        double r = a.distance();
        double vc = Math.sqrt(mu / r);
        a.set(a.x, a.y, -dir * a.y / r * vc, dir * a.x / r * vc);
        TransferPlanner.Plan plan = TransferPlanner.plan(mu, a, rails.stateAt(target, tBurn, b), tBurn,
                tBurn + UNDOCK_DELAY);
        List<Burn> burns = new ArrayList<>();
        burns.add(circ);
        burns.addAll(plan.burns);
        // An idle ship that was pulled away from its dock flies back before taking new work.
        if (f.state == FreighterComponent.State.IDLE) f.state = FreighterComponent.State.REPOSITIONING;
        follow(uuid, f, target, burns, plan.arrivalTime, plan.totalDeltaV + Math.hypot(circ.prograde, circ.radial));
    }
}
