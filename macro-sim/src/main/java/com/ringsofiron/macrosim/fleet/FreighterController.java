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
    /**
     * Within this of its destination when rails warp collapses the bubble, m, a freighter there
     * arrives ({@link #arriveFromBubble}): the bubble's exit radius, so any that came in to berth.
     */
    public static final double ARRIVE_ON_COLLAPSE = 25_000;
    /** Seconds after the predicted SOI crossing to check it happened, and between later checks. */
    static final double CROSSING_CHECK = 1;
    static final double CROSSING_RETRY = 60;
    /** How long past the predicted crossing a freighter keeps waiting for it. */
    static final double CROSSING_PATIENCE = 6 * 3600;

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
            f.arrivalEventId = f.transitBodyUuid != null
                    ? events.schedule(arrival, () -> cross(uuid, f, target, arrival))
                    : events.schedule(arrival, () -> arrive(uuid, f, target, arrival));
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
     * Pirates took a loaded freighter's cargo. Its order is cancelled and what it held at the buyer
     * released, as if the cargo were lost with the ship; the freighter flies on, empty, to where it
     * was going, and takes new work there. Returns the units taken, 0 if it carried nothing.
     */
    public double plunder(String uuid) {
        FreighterComponent f = freighters.get(uuid);
        if (f == null || f.cargoCommodity == null || f.cargoQuantity <= 0) return 0;
        double taken = f.cargoQuantity;
        TradeOrder o = activeOrders.remove(f.orderId);
        if (o != null) release(o, f);
        f.orderId = 0;
        f.cargoCommodity = null;
        f.cargoQuantity = 0;
        if (f.state == FreighterComponent.State.TO_DELIVERY) f.state = FreighterComponent.State.REPOSITIONING;
        if (f.state == FreighterComponent.State.IN_BUBBLE && f.resumeState == FreighterComponent.State.TO_DELIVERY) {
            f.resumeState = FreighterComponent.State.REPOSITIONING;
        }
        return taken;
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

    /**
     * Gives every idle freighter the best order it can reach, if any: those on the rails, and those
     * docked in the bubble, which fly off under their own power ({@link #berthedInBubble}).
     */
    public void assignIdle(double now) {
        for (Map.Entry<String, FreighterComponent> e : freighters.entrySet()) {
            FreighterComponent f = e.getValue();
            if (f.state != FreighterComponent.State.IDLE && !idleInBubble(f)) continue;
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

    /** Docked in the bubble with nothing to do. */
    private static boolean idleInBubble(FreighterComponent f) {
        return f.state == FreighterComponent.State.IN_BUBBLE && f.resumeState == FreighterComponent.State.IDLE
                && f.dockedAtUuid != null && f.destinationUuid == null;
    }

    /** Profit per hour of an order for this freighter, or 0 if not worth it (or out of reach). */
    private double score(String uuid, FreighterComponent f, TradeOrder o, double now) {
        double qty = Math.min(o.quantity, f.capacityUnits(economy.catalog().commodity(o.commodity)));
        double start = now + UNDOCK_DELAY;
        double leg1Arrival = start;
        double dv = 0;
        if (!o.fromUuid.equals(f.dockedAtUuid)) {
            // In the bubble the ship is off the rails: it starts from the station it is docked at
            String from = idleInBubble(f) ? f.dockedAtUuid : uuid;
            // Idle but off the rails and not docked (drifting in the bubble after a fight): no order
            if (!rails.isOnRails(from)) return 0;
            RoutePlanner.Route r1 = RoutePlanner.estimate(rails, rails.parentOf(from), rails.stateAt(from, now, a),
                    o.fromUuid, now, start);
            if (r1 == null) return 0;
            leg1Arrival = r1.arrivalTime;
            dv += r1.totalDeltaV;
        }
        double t2 = leg1Arrival + UNDOCK_DELAY;
        RoutePlanner.Route r2 = RoutePlanner.estimate(rails, rails.parentOf(o.fromUuid),
                rails.stateAt(o.fromUuid, leg1Arrival, a), o.toUuid, leg1Arrival, t2);
        if (r2 == null) return 0;
        dv += r2.totalDeltaV;
        double duration = r2.arrivalTime - now;
        if (duration > MAX_TRIP_SECONDS) return 0;
        double profit = qty * o.unitMargin() - dv * FUEL_COST_PER_DV * f.holdCapacity();
        return profit > 0 ? profit / (duration / 3600.0) : 0;
    }

    private void start(String uuid, FreighterComponent f, TradeOrder o, double now) {
        o.quantity = Math.min(o.quantity, f.capacityUnits(economy.catalog().commodity(o.commodity)));
        economy.claim(o, uuid, stations.apply(o.fromUuid), stations.apply(o.toUuid));
        activeOrders.put(o.id, o);
        f.orderId = o.id;
        if (idleInBubble(f)) {
            // The bubble flies it off; the leg is planned once it is back on the rails
            if (o.fromUuid.equals(f.dockedAtUuid)) {
                takeOn(f, o);
                f.resumeState = FreighterComponent.State.TO_DELIVERY;
                f.destinationUuid = o.toUuid;
            } else {
                f.resumeState = FreighterComponent.State.TO_PICKUP;
                f.destinationUuid = o.fromUuid;
            }
            return;
        }
        if (o.fromUuid.equals(f.dockedAtUuid)) {
            load(uuid, f, o, now);
        } else {
            f.state = FreighterComponent.State.TO_PICKUP;
            fly(uuid, f, o.fromUuid, now, now + UNDOCK_DELAY);
        }
    }

    private void load(String uuid, FreighterComponent f, TradeOrder o, double now) {
        takeOn(f, o);
        f.state = FreighterComponent.State.TO_DELIVERY;
        fly(uuid, f, o.toUuid, now, now + UNDOCK_DELAY);
    }

    /** Takes the order's cargo aboard at its pickup station. */
    private void takeOn(FreighterComponent f, TradeOrder o) {
        EconomyNode from = stations.apply(o.fromUuid);
        from.reserveOut(o.commodity, -o.quantity);
        double got = from.remove(o.commodity, o.quantity);
        f.cargoCommodity = o.commodity;
        f.cargoQuantity = got;
        f.credits -= got * o.buyPrice;
        if (got < o.quantity) stations.apply(o.toUuid).expectIncoming(o.commodity, got - o.quantity);
    }

    private void fly(String uuid, FreighterComponent f, String targetUuid, double now, double earliest) {
        RoutePlanner.Route route = RoutePlanner.plan(rails, rails.parentOf(uuid), rails.stateAt(uuid, now, a),
                targetUuid, now, earliest);
        follow(uuid, f, targetUuid, new ArrayList<>(), 0, route, now);
    }

    /**
     * Flies {@code lead} burns and then {@code route} towards {@code targetUuid}. A route that ends at an
     * SOI crossing is continued by {@link #cross}; a null route (no way there) drops the job.
     */
    private void follow(String uuid, FreighterComponent f, String targetUuid, List<Burn> lead, double leadDeltaV,
                        RoutePlanner.Route route, double now) {
        if (route == null) {
            abandon(uuid, f, now);
            return;
        }
        f.dockedAtUuid = null;
        f.destinationUuid = targetUuid;
        f.deltaVUsed += leadDeltaV;
        rails.cancelBurns(uuid);
        for (Burn burn : lead) rails.scheduleBurn(uuid, burn);
        for (Burn burn : route.burns) rails.scheduleBurn(uuid, burn);
        for (Burn burn : route.burns) f.deltaVUsed += Math.hypot(burn.prograde, burn.radial);
        events.cancel(f.arrivalEventId);
        if (route.nextParent != null) {
            double check = route.crossingTime + CROSSING_CHECK;
            f.transitBodyUuid = route.nextParent;
            f.transitDeadline = route.crossingTime + CROSSING_PATIENCE;
            f.arrivalTime = check;
            f.arrivalEventId = events.schedule(check, () -> cross(uuid, f, targetUuid, check));
        } else {
            double arrival = route.arrivalTime;
            f.transitBodyUuid = null;
            f.transitDeadline = Double.NaN;
            f.arrivalTime = arrival;
            f.arrivalEventId = events.schedule(arrival, () -> arrive(uuid, f, targetUuid, arrival));
        }
    }

    /** The predicted SOI crossing is due: plan the rest of the leg from the real orbit. */
    private void cross(String uuid, FreighterComponent f, String targetUuid, double now) {
        if (!rails.isOnRails(uuid)) return; // in the bubble; re-planned on exit
        if (!f.transitBodyUuid.equals(rails.parentOf(uuid))) {
            if (now > f.transitDeadline) {
                abandon(uuid, f, now);
                return;
            }
            double retry = now + CROSSING_RETRY;
            f.arrivalTime = retry;
            f.arrivalEventId = events.schedule(retry, () -> cross(uuid, f, targetUuid, retry));
            return;
        }
        String parent = f.transitBodyUuid;
        f.transitBodyUuid = null;
        follow(uuid, f, targetUuid, new ArrayList<>(), 0,
                RoutePlanner.afterCrossing(rails, parent, rails.stateAt(uuid, now, a), targetUuid, now, now + 1), now);
    }

    /**
     * No way to the target: the order is dropped and the freighter waits where it is. A freighter
     * left drifting in transit circularizes first.
     */
    private void abandon(String uuid, FreighterComponent f, double now) {
        TradeOrder o = activeOrders.remove(f.orderId);
        if (o != null) release(o, f);
        f.orderId = 0;
        events.cancel(f.arrivalEventId);
        f.arrivalEventId = 0;
        f.transitBodyUuid = null;
        f.transitDeadline = Double.NaN;
        f.destinationUuid = null;
        f.arrivalTime = Double.NaN;
        f.state = FreighterComponent.State.IDLE;
        if (f.dockedAtUuid == null && rails.isOnRails(uuid)) {
            rails.cancelBurns(uuid);
            double mu = rails.muOf(rails.parentOf(uuid));
            rails.scheduleBurn(uuid, TransferPlanner.circularize(mu, rails.stateAt(uuid, now + 1, a), now + 1));
        }
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
            deliver(f, o, stationUuid);
            f.state = FreighterComponent.State.IDLE;
        } else {
            if (f.cargoQuantity > 0) sellLeftover(f, stations.apply(stationUuid));
            f.state = FreighterComponent.State.IDLE;
        }
    }

    /** Hands the order's cargo over at its delivery station and closes the order. */
    private void deliver(FreighterComponent f, TradeOrder o, String stationUuid) {
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
    }

    /**
     * A freighter in the bubble has docked at {@code stationUuid} under its own power: if it was
     * flying there, it loads or unloads as it would on arriving on the rails, and what it does next
     * goes in {@link FreighterComponent#resumeState} (and its next stop in
     * {@link FreighterComponent#destinationUuid}) for the bubble to fly, or the rails once it leaves.
     */
    public void berthedInBubble(String uuid, String stationUuid) {
        FreighterComponent f = freighters.get(uuid);
        if (f == null || f.state != FreighterComponent.State.IN_BUBBLE) return;
        f.dockedAtUuid = stationUuid;
        if (!stationUuid.equals(f.destinationUuid)) return;
        f.destinationUuid = null;
        f.arrivalTime = Double.NaN;
        f.lastArrivalMiss = 0;
        TradeOrder o = activeOrders.get(f.orderId);
        if (f.resumeState == FreighterComponent.State.TO_PICKUP && o != null) {
            takeOn(f, o);
            f.resumeState = FreighterComponent.State.TO_DELIVERY;
            f.destinationUuid = o.toUuid;
        } else if (f.resumeState == FreighterComponent.State.TO_DELIVERY && o != null) {
            deliver(f, o, stationUuid);
            f.resumeState = FreighterComponent.State.IDLE;
        } else {
            if (f.cargoQuantity > 0) sellLeftover(f, stations.apply(stationUuid));
            f.resumeState = FreighterComponent.State.IDLE;
        }
    }

    /**
     * Rails warp has collapsed the bubble: a freighter in it within {@link #ARRIVE_ON_COLLAPSE} of
     * its destination arrives there, as on the rails, since the rest of its approach passes in the
     * warp. Otherwise it coasts on through the warp, leaves the bubble when it is rebuilt and is
     * re-planned from wherever it got to. Round 10: seed 17's escorted Ore Barge dropped warp coming
     * in to Selene Forge, warp went back up a frame later, and it was re-planned 28 days out.
     */
    public void arriveFromBubble(String uuid, double now) {
        FreighterComponent f = freighters.get(uuid);
        if (f == null || f.state != FreighterComponent.State.IN_BUBBLE || f.destinationUuid == null) return;
        if (!rails.isOnRails(uuid)) return;
        rails.stateAt(uuid, now, a);
        rails.stateAt(f.destinationUuid, now, b);
        if (Math.hypot(a.x - b.x, a.y - b.y) > ARRIVE_ON_COLLAPSE) return;
        f.state = f.resumeState;
        arrive(uuid, f, f.destinationUuid, now);
    }

    /** A freighter in the bubble has undocked from its station to fly on. */
    public void undockedInBubble(String uuid) {
        FreighterComponent f = freighters.get(uuid);
        if (f != null && f.state == FreighterComponent.State.IN_BUBBLE) f.dockedAtUuid = null;
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
        f.transitBodyUuid = null;
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
        RoutePlanner.Route route = RoutePlanner.plan(rails, parent, RoutePlanner.circularState(mu, a), target, tBurn,
                tBurn + UNDOCK_DELAY);
        List<Burn> lead = new ArrayList<>();
        lead.add(circ);
        // An idle ship that was pulled away from its dock flies back before taking new work.
        if (f.state == FreighterComponent.State.IDLE) f.state = FreighterComponent.State.REPOSITIONING;
        follow(uuid, f, target, lead, Math.hypot(circ.prograde, circ.radial), route, now);
    }
}
