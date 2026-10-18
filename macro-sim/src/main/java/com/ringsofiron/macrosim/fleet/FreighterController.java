package com.ringsofiron.macrosim.fleet;

import com.ringsofiron.macrosim.MacroEventQueue;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.ArrayList;
import java.util.Collection;
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
    /** Credits charged per m/s of delta-v per m³ of hold capacity, a stand-in for fuel cost. */
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

    /** Forgets every freighter and active order (before restoring a save). */
    public void clear() {
        freighters.clear();
        activeOrders.clear();
    }

    /**
     * Re-registers a saved freighter as it was: its orbit and pending burns are already back on the
     * rails, so only the arrival event is rescheduled. {@code order} is its claimed order, or null.
     */
    public void restore(String uuid, FreighterComponent f, TradeOrder order) {
        freighters.put(uuid, f);
        if (order != null) activeOrders.put(order.id, order);
        f.arrivalEventId = 0;
        if (f.destinationUuid != null && !Double.isNaN(f.arrivalTime) && f.state != FreighterComponent.State.IN_BUBBLE) {
            String target = f.destinationUuid;
            double arrival = f.arrivalTime;
            f.arrivalEventId = events.schedule(arrival, () -> arrive(uuid, f, target, arrival));
        }
    }

    /**
     * Forgets a destroyed freighter. Its order is cancelled and what the order held at both stations
     * is released; the cargo aboard is lost with the ship. The caller removes the entity.
     */
    public void lose(String uuid) {
        FreighterComponent f = freighters.remove(uuid);
        if (f == null) return;
        events.cancel(f.arrivalEventId);
        f.arrivalEventId = 0;
        TradeOrder o = activeOrders.remove(f.orderId);
        if (o != null) release(o, f);
    }

    /**
     * A station is gone. Orders to or from it are cancelled, and every freighter that was going
     * there or docked there heads for the nearest of {@code remaining} instead. Cargo it was taking
     * there is sold where it lands, or dumped if that station does not trade it. Call after the
     * station is unregistered.
     */
    public void stationLost(String stationUuid, Collection<String> remaining, double now) {
        for (Map.Entry<String, FreighterComponent> e : freighters.entrySet()) {
            String uuid = e.getKey();
            FreighterComponent f = e.getValue();
            TradeOrder o = activeOrders.get(f.orderId);
            // A loaded freighter no longer needs its seller, only its buyer.
            boolean orderHit = o != null && (o.toUuid.equals(stationUuid)
                    || (f.cargoCommodity == null && o.fromUuid.equals(stationUuid)));
            if (orderHit) {
                activeOrders.remove(o.id);
                release(o, f);
                f.orderId = 0;
            }
            boolean bound = stationUuid.equals(f.destinationUuid) || stationUuid.equals(f.dockedAtUuid);
            if (!orderHit && !bound) continue;
            boolean inBubble = f.state == FreighterComponent.State.IN_BUBBLE;
            boolean docked = f.dockedAtUuid != null && !f.dockedAtUuid.equals(stationUuid);
            if (docked && !stationUuid.equals(f.destinationUuid) && !inBubble) {
                // Sitting at another station: it already is somewhere safe, so it just waits for work.
                f.state = FreighterComponent.State.IDLE;
                continue;
            }
            if (inBubble) f.resumeState = FreighterComponent.State.REPOSITIONING;
            else f.state = FreighterComponent.State.REPOSITIONING;
            if (stationUuid.equals(f.dockedAtUuid)) f.dockedAtUuid = null;
            String target = inBubble ? null : nearest(uuid, remaining, now);
            if (target != null) {
                fly(uuid, f, target, now, now + UNDOCK_DELAY);
            } else if (inBubble) {
                // Re-planned when it leaves the bubble, towards whatever station is nearest then.
                f.destinationUuid = null;
                f.dockedAtUuid = null;
            } else {
                events.cancel(f.arrivalEventId);
                f.arrivalEventId = 0;
                rails.cancelBurns(uuid);
                f.destinationUuid = null;
                f.arrivalTime = Double.NaN;
                f.state = FreighterComponent.State.IDLE;
            }
        }
    }

    /** Frees what an unfinished order held: the seller's reservation if not loaded, the buyer's expected delivery. */
    private void release(TradeOrder o, FreighterComponent f) {
        boolean loaded = f.cargoCommodity != null;
        EconomyNode from = stations.apply(o.fromUuid);
        EconomyNode to = stations.apply(o.toUuid);
        if (!loaded && from != null) from.reserveOut(o.commodity, -o.quantity);
        if (to != null) to.expectIncoming(o.commodity, -(loaded ? f.cargoQuantity : o.quantity));
        economy.close(o);
    }

    /** Closest station to the freighter around the same body, or null. */
    private String nearest(String uuid, Collection<String> candidates, double now) {
        if (!rails.isOnRails(uuid)) return null;
        String parent = rails.parentOf(uuid);
        rails.stateAt(uuid, now, a);
        String best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (String s : candidates) {
            if (!rails.isOnRails(s) || !parent.equals(rails.parentOf(s))) continue;
            rails.stateAt(s, now, b);
            double d = Math.hypot(a.x - b.x, a.y - b.y);
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
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
        double qty = Math.min(o.quantity, f.capacityUnits(economy.catalog().commodity(o.commodity)));
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
        double profit = qty * o.unitMargin() - dv * FUEL_COST_PER_DV * f.holdCapacity();
        return profit > 0 ? profit / (duration / 3600.0) : 0;
    }

    private void start(String uuid, FreighterComponent f, TradeOrder o, double now) {
        o.quantity = Math.min(o.quantity, f.capacityUnits(economy.catalog().commodity(o.commodity)));
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
            if (f.cargoQuantity > 0) sellLeftover(f, stations.apply(stationUuid));
            f.state = FreighterComponent.State.IDLE;
        }
    }

    /** Cargo with no order left (its buyer was lost) goes at the local price, or is dumped. */
    private void sellLeftover(FreighterComponent f, EconomyNode station) {
        if (station != null && station.price(f.cargoCommodity) > 0) {
            station.add(f.cargoCommodity, f.cargoQuantity);
            f.credits += f.cargoQuantity * station.price(f.cargoCommodity);
        }
        f.cargoQuantity = 0;
        f.cargoCommodity = null;
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
