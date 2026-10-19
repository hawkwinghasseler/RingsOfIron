package com.ringsofiron.macrosim.hostile;

import com.ringsofiron.macrosim.MacroEventQueue;
import com.ringsofiron.macrosim.bubble.BubbleGateway;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.fleet.FreighterController;
import com.ringsofiron.macrosim.fleet.TransferPlanner;
import com.ringsofiron.macrosim.hostile.HostileComponent.Role;
import com.ringsofiron.macrosim.hostile.HostileComponent.State;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Armed NPC traffic on the rails side, beside {@link FreighterController}: pirates and faction
 * patrols, each a {@link HostileComponent}. Main thread only.
 *
 * <ul>
 *   <li><b>Pirates</b> lurk on their station's orbit {@link #LURK_OFFSET} behind it, by the lanes
 *       the freighters fly. Every {@link #LOOK_INTERVAL} one looks for prey: the player, if it is
 *       bold enough ({@link #PLAYER_NERVE}), else a loaded freighter it can beat to its destination.
 *       It flies to wait {@link #AMBUSH_OFFSET} behind that station and takes the cargo as the
 *       freighter comes in, unless a patrol that outguns it holds there. Then it flies back to its
 *       lurk. An intercept on the player is a rendezvous with the bubble anchor; the bubble takes
 *       it from there.</li>
 *   <li><b>Patrols</b> fly between the stations of their route, holding {@link #HOLD_OFFSET} behind
 *       each for {@link #PATROL_HOLD}. One with a single station guards it.</li>
 *   <li><b>Wingmen</b> ({@link HostileComponent#wingOf}) lurk {@link #WING_SPACING} behind their
 *       leader and never hunt on their own: when the leader commits to prey, each one plans its own
 *       way to the same prey, to arrive {@link #WING_SPACING} behind it. A pair's firepower counts
 *       together for {@link #PLAYER_NERVE}. A wingman whose leader is gone flies on its own.</li>
 *   <li>An ace with a grudge ({@link HostileComponent#grudge}) goes after the player whatever the
 *       odds.</li>
 * </ul>
 * Hostiles only travel around one body: their stations, and their prey's destinations, share it.
 * In the bubble the game flies and fights them; leaving it, a pirate goes home to rest and a
 * patrol flies on to its stop.
 */
public class HostileTrafficController {
    /** Sim seconds between a lurking pirate's looks for prey. */
    public static final double LOOK_INTERVAL = 900;
    /** How far behind its station a pirate lurks, along the station's orbit, meters. */
    public static final double LURK_OFFSET = 60_000;
    /** How far behind its prey's destination a pirate waits, meters. */
    public static final double AMBUSH_OFFSET = 5_000;
    /** How far behind each station a patrol holds, meters: clear of the station and its traffic. */
    public static final double HOLD_OFFSET = 3_000;
    /** A pirate strikes this long before its prey reaches the station, seconds. */
    public static final double AMBUSH_LEAD = 60;
    /** A pirate only goes after prey it can be in place for at least this long before, seconds. */
    public static final double AMBUSH_MARGIN = 300;
    /** Prey further off than this, by arrival, is ignored, seconds. */
    public static final double MAX_HUNT = 6 * 3600.0;
    /** How long a patrol holds at each stop, seconds. */
    public static final double PATROL_HOLD = 2 * 3600.0;
    /** How long a pirate rests after a hunt, a fight or a plunder before it hunts again, seconds. */
    public static final double REST = 6 * 3600.0;
    /** Seconds between deciding to go and the first burn. */
    public static final double DEPART_DELAY = 60;
    /** A pirate goes after the player when its firepower is at least this share of the player's. */
    public static final double PLAYER_NERVE = 0.8;
    /** How far behind its leader a wingman lurks and arrives, meters. */
    public static final double WING_SPACING = 2_000;
    /** The player's orbit must be this close to circular for a pirate to plan a rendezvous with it. */
    static final double MAX_PLAYER_ECCENTRICITY = 0.02;
    /** Lowest altitude any planned orbit may reach, above the body's surface, meters. */
    private static final double SAFE_ALTITUDE = 200e3;

    /** What the hostiles get up to, for the game's notices. */
    public interface Listener {
        /** A pirate committed to an intercept: on a freighter, or {@link HostileComponent#PLAYER}. */
        default void intercepting(String hostile, String target, double t) {
        }

        /** A pirate took a freighter's cargo. */
        default void plundered(String hostile, String freighter, String commodity, double quantity, double t) {
        }

        /** A pirate found a patrol that outguns it guarding its ambush and turned back. */
        default void drivenOff(String hostile, String patrol, double t) {
        }
    }

    private final RailsModel rails;
    private final MacroEventQueue events;
    private final FreighterController freighters;
    private final BubbleGateway gateway;
    private final Map<String, HostileComponent> hostiles = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    /** The player's firepower, for {@link #PLAYER_NERVE}; +inf (never hunted) until the game sets it. */
    public DoubleSupplier playerFirepower = () -> Double.POSITIVE_INFINITY;
    /** No pirate goes after the player before this sim second. */
    public double playerGraceUntil;

    /** @param gateway the bubble, or null: then the player is never hunted */
    public HostileTrafficController(RailsModel rails, MacroEventQueue events, FreighterController freighters,
                                    BubbleGateway gateway) {
        this.rails = rails;
        this.events = events;
        this.freighters = freighters;
        this.gateway = gateway;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public HostileComponent get(String uuid) {
        return hostiles.get(uuid);
    }

    public Map<String, HostileComponent> all() {
        return hostiles;
    }

    /** Registers a pirate lurking by {@code stationUuid}. Puts it on the rails there. */
    public void addPirate(String uuid, HostileComponent h, String stationUuid, double now) {
        h.role = Role.PIRATE;
        h.stationUuid = stationUuid;
        hostiles.put(uuid, h);
        holdAt(uuid, h, stationUuid, lurkOffset(h), now);
        schedule(uuid, h, now + LOOK_INTERVAL);
    }

    /** How far behind its lurk station a pirate holds: a wingman sits {@link #WING_SPACING} behind its leader. */
    static double lurkOffset(HostileComponent h) {
        return h.wingOf == null ? LURK_OFFSET : LURK_OFFSET + WING_SPACING;
    }

    /** The pirates flying as {@code leader}'s wingmen, by UUID. */
    public List<String> wingmenOf(String leader) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            if (leader.equals(e.getValue().wingOf)) out.add(e.getKey());
        }
        return out;
    }

    /** Registers a patrol flying {@code route}, starting held at its first station. */
    public void addPatrol(String uuid, HostileComponent h, String[] route, double now) {
        if (route.length == 0) throw new IllegalArgumentException("A patrol needs a station");
        h.role = Role.PATROL;
        h.route = route.clone();
        h.routeIndex = 0;
        h.stationUuid = route[0];
        hostiles.put(uuid, h);
        holdAt(uuid, h, route[0], HOLD_OFFSET, now);
        if (route.length > 1) schedule(uuid, h, now + PATROL_HOLD);
    }

    /** Forgets every hostile (before restoring a save). */
    public void clear() {
        hostiles.clear();
    }

    /** Re-registers a saved hostile: its orbit and burns are already back on the rails. */
    public void restore(String uuid, HostileComponent h) {
        hostiles.put(uuid, h);
        h.eventId = 0;
        if (h.state != State.IN_BUBBLE && h.eventTime >= 0) schedule(uuid, h, h.eventTime);
    }

    /** Forgets a destroyed hostile; the caller removes the entity. */
    public void lose(String uuid) {
        HostileComponent h = hostiles.remove(uuid);
        if (h != null) events.cancel(h.eventId);
        // Its wingmen fly on their own now
        for (HostileComponent w : hostiles.values()) if (uuid.equals(w.wingOf)) w.wingOf = null;
    }

    /**
     * A station is gone: pirates lurking there move to the nearest of {@code remaining}, patrols
     * drop it from their route, and an ambush laid there is called off.
     */
    public void stationLost(String stationUuid, Collection<String> remaining, double now) {
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            String uuid = e.getKey();
            HostileComponent h = e.getValue();
            if (h.role == Role.PATROL) {
                List<String> route = new ArrayList<>(List.of(h.route));
                if (route.remove(stationUuid)) {
                    h.route = route.toArray(new String[0]);
                    h.routeIndex = route.isEmpty() ? 0 : h.routeIndex % route.size();
                    if (stationUuid.equals(h.stationUuid)) h.stationUuid = route.isEmpty() ? null : route.get(h.routeIndex);
                }
                continue;
            }
            boolean home = stationUuid.equals(h.stationUuid);
            if (home) h.stationUuid = nearest(uuid, remaining, now);
            if (stationUuid.equals(h.ambushStationUuid)) {
                h.targetUuid = null;
                h.ambushStationUuid = null;
                if (h.state != State.IN_BUBBLE) goHome(uuid, h, now);
            } else if (home && (h.state == State.RETURNING || h.state == State.HOLDING)) {
                goHome(uuid, h, now);
            }
        }
    }

    /** Called when the hostile is handed to the bubble: its rails plan is suspended. */
    public void onEnterBubble(String uuid) {
        HostileComponent h = hostiles.get(uuid);
        if (h == null || h.state == State.IN_BUBBLE) return;
        h.resumeState = h.state;
        h.state = State.IN_BUBBLE;
        events.cancel(h.eventId);
        h.eventId = 0;
        h.eventTime = -1;
    }

    /**
     * Called when the hostile is back on the rails after the bubble. It circularizes where it is;
     * a pirate then flies home to rest, a patrol on to its stop.
     */
    public void onExitBubble(String uuid, double now) {
        HostileComponent h = hostiles.get(uuid);
        if (h == null || h.state != State.IN_BUBBLE) return;
        h.state = h.resumeState;
        h.brokeOff = false;
        double tBurn = now + 1;
        String parent = rails.parentOf(uuid);
        double mu = rails.muOf(parent);
        rails.stateAt(uuid, tBurn, a);
        Burn circ = TransferPlanner.circularize(mu, a, tBurn);
        StateVector circular = circularState(mu, a);
        if (h.role == Role.PIRATE) {
            h.targetUuid = null;
            h.ambushStationUuid = null;
            h.restUntil = Math.max(h.restUntil, now + REST);
            fly(uuid, h, circ, circular, tBurn, h.stationUuid, lurkOffset(h), State.RETURNING);
        } else {
            fly(uuid, h, circ, circular, tBurn, h.stationUuid, HOLD_OFFSET, State.PATROLLING);
        }
    }

    /**
     * A pirate in the bubble took its prey's cargo there. The freighter flies on empty; the pirate
     * should now leave. Returns the units taken.
     */
    public double plunderInBubble(String uuid, double now) {
        HostileComponent h = hostiles.get(uuid);
        if (h == null || h.targetUuid == null || h.huntingPlayer()) return 0;
        double taken = plunder(uuid, h, h.targetUuid, now);
        h.brokeOff = true;
        return taken;
    }

    /** Sends a pirate home: it is done with its prey (the game calls this when it breaks off a fight). */
    public void breakOff(String uuid, double now) {
        HostileComponent h = hostiles.get(uuid);
        if (h == null) return;
        h.brokeOff = true;
        h.restUntil = Math.max(h.restUntil, now + REST);
    }

    /**
     * Sends a patrol on from the bubble (done there, or beaten): once back on the rails it flies to
     * the next station of its route.
     */
    public void moveOn(String uuid) {
        HostileComponent h = hostiles.get(uuid);
        if (h == null) return;
        h.brokeOff = true;
        if (h.route.length > 1) {
            h.routeIndex = (h.routeIndex + 1) % h.route.length;
            h.stationUuid = h.route[h.routeIndex];
        }
    }

    // ---------------------------------------------------------------------------------------------

    private void schedule(String uuid, HostileComponent h, double t) {
        events.cancel(h.eventId);
        h.eventTime = t;
        h.eventId = events.schedule(t, () -> due(uuid, h, t));
    }

    private void due(String uuid, HostileComponent h, double now) {
        if (hostiles.get(uuid) != h || !rails.isOnRails(uuid)) return;
        h.eventId = 0;
        h.eventTime = -1;
        switch (h.state) {
            case HOLDING:
                if (h.role == Role.PIRATE) look(uuid, h, now);
                else nextLeg(uuid, h, now);
                break;
            case INTERCEPTING:
                if (h.huntingPlayer() || h.targetUuid == null) {
                    // Arrived where the player was, and they are not here: give up
                    h.targetUuid = null;
                    goHome(uuid, h, now);
                } else {
                    lieInWait(uuid, h, now);
                }
                break;
            case AMBUSHING:
                ambush(uuid, h, now);
                break;
            case RETURNING:
                holdAt(uuid, h, h.stationUuid, lurkOffset(h), now);
                h.state = State.HOLDING;
                schedule(uuid, h, Math.max(now + LOOK_INTERVAL, h.restUntil));
                break;
            case PATROLLING:
                holdAt(uuid, h, h.stationUuid, HOLD_OFFSET, now);
                h.state = State.HOLDING;
                if (h.route.length > 1) schedule(uuid, h, now + PATROL_HOLD);
                break;
            default:
                break;
        }
    }

    /** A lurking pirate looks for prey: the player first, else the best freighter it can catch. */
    private void look(String uuid, HostileComponent h, double now) {
        if (now < h.restUntil) {
            schedule(uuid, h, h.restUntil);
            return;
        }
        if (h.wingOf != null && hostiles.get(h.wingOf) == null) h.wingOf = null;
        if (h.wingOf != null) {
            // A wingman hunts only with its leader
            schedule(uuid, h, now + LOOK_INTERVAL);
            return;
        }
        String parent = rails.parentOf(uuid);
        TransferPlanner.Plan plan = playerIntercept(uuid, h, parent, now);
        if (plan != null) {
            h.targetUuid = HostileComponent.PLAYER;
            h.ambushStationUuid = null;
            follow(uuid, h, new ArrayList<>(), plan, State.INTERCEPTING);
            for (Listener l : listeners) l.intercepting(uuid, HostileComponent.PLAYER, now);
            bringWingmen(uuid, h, parent, now);
            return;
        }
        String best = null;
        TransferPlanner.Plan bestPlan = null;
        double bestCargo = 0;
        for (Map.Entry<String, FreighterComponent> e : freighters.all().entrySet()) {
            String id = e.getKey();
            FreighterComponent f = e.getValue();
            if (f.state != FreighterComponent.State.TO_DELIVERY || f.cargoQuantity <= bestCargo
                    || f.destinationUuid == null || f.transitBodyUuid != null || Double.isNaN(f.arrivalTime)) continue;
            if (f.arrivalTime - now > MAX_HUNT || !rails.isOnRails(id) || hunted(id)) continue;
            String to = f.destinationUuid;
            if (!rails.isOnRails(to) || !parent.equals(rails.parentOf(to)) || guard(to, h) != null) continue;
            TransferPlanner.Plan p = planTo(uuid, to, AMBUSH_OFFSET, now, now + DEPART_DELAY);
            if (p == null || p.arrivalTime + AMBUSH_MARGIN > f.arrivalTime) continue;
            best = id;
            bestPlan = p;
            bestCargo = f.cargoQuantity;
        }
        if (best == null) {
            schedule(uuid, h, now + LOOK_INTERVAL);
            return;
        }
        h.targetUuid = best;
        h.ambushStationUuid = freighters.get(best).destinationUuid;
        follow(uuid, h, new ArrayList<>(), bestPlan, State.INTERCEPTING);
        for (Listener l : listeners) l.intercepting(uuid, best, now);
        bringWingmen(uuid, h, parent, now);
    }

    /**
     * {@code leader} committed to its prey: each of its wingmen lurking ready plans its own way
     * there, to arrive {@link #WING_SPACING} behind it, and goes after the same prey.
     */
    private void bringWingmen(String leader, HostileComponent h, String parent, double now) {
        for (String w : wingmenOf(leader)) {
            HostileComponent wing = hostiles.get(w);
            if (wing.state != State.HOLDING || now < wing.restUntil || !rails.isOnRails(w)
                    || !parent.equals(rails.parentOf(w))) continue;
            TransferPlanner.Plan plan;
            if (h.huntingPlayer()) {
                double mu = rails.muOf(parent);
                StateVector player = behind(gateway.anchorStateAt(now, new StateVector()), WING_SPACING);
                plan = TransferPlanner.plan(mu, rails.stateAt(w, now, a), player, now, now + DEPART_DELAY, floor(parent));
            } else {
                plan = planTo(w, h.ambushStationUuid, AMBUSH_OFFSET + WING_SPACING, now, now + DEPART_DELAY);
            }
            if (plan == null) continue;
            wing.targetUuid = h.targetUuid;
            wing.ambushStationUuid = h.ambushStationUuid;
            follow(w, wing, new ArrayList<>(), plan, State.INTERCEPTING);
        }
    }

    /** A rendezvous with the player, if this pirate would go after them now; else null. */
    private TransferPlanner.Plan playerIntercept(String uuid, HostileComponent h, String parent, double now) {
        if (gateway == null || now < playerGraceUntil || !parent.equals(gateway.anchorParentUuid())) return null;
        if (hunted(HostileComponent.PLAYER)) return null;
        if (!h.grudge && pairFirepower(uuid, h) < PLAYER_NERVE * playerFirepower.getAsDouble()) return null;
        double mu = rails.muOf(parent);
        StateVector player = gateway.anchorStateAt(now, new StateVector());
        if (eccentricity(mu, player) > MAX_PLAYER_ECCENTRICITY || player.distance() < floor(parent)) return null;
        StateVector ship = rails.stateAt(uuid, now, a);
        TransferPlanner.Plan p = TransferPlanner.plan(mu, ship, player, now, now + DEPART_DELAY, floor(parent));
        return p.arrivalTime - now > MAX_HUNT ? null : p;
    }

    /** {@code h}'s firepower with that of its wingmen lurking ready to come along. */
    double pairFirepower(String uuid, HostileComponent h) {
        double sum = h.firepower;
        for (String w : wingmenOf(uuid)) {
            HostileComponent wing = hostiles.get(w);
            if (wing.state == State.HOLDING) sum += wing.firepower;
        }
        return sum;
    }

    /** Arrived by the prey's destination: waits there for it, if it is still coming. */
    private void lieInWait(String uuid, HostileComponent h, double now) {
        FreighterComponent f = freighters.get(h.targetUuid);
        holdAt(uuid, h, h.ambushStationUuid, h.wingOf == null ? AMBUSH_OFFSET : AMBUSH_OFFSET + WING_SPACING, now);
        if (f == null || !h.ambushStationUuid.equals(f.destinationUuid) || Double.isNaN(f.arrivalTime)
                || f.arrivalTime - AMBUSH_LEAD < now) {
            h.targetUuid = null;
            goHome(uuid, h, now);
            return;
        }
        h.state = State.AMBUSHING;
        schedule(uuid, h, f.arrivalTime - AMBUSH_LEAD);
    }

    /** The prey is coming in: take its cargo, unless a patrol guards the station; then go home. */
    private void ambush(String uuid, HostileComponent h, double now) {
        String prey = h.targetUuid;
        FreighterComponent f = prey == null ? null : freighters.get(prey);
        if (f != null && rails.isOnRails(prey) && h.ambushStationUuid.equals(f.destinationUuid)
                && f.cargoQuantity > 0) {
            String patrol = guard(h.ambushStationUuid, h);
            if (patrol != null) {
                for (Listener l : listeners) l.drivenOff(uuid, patrol, now);
            } else {
                plunder(uuid, h, prey, now);
            }
        }
        h.targetUuid = null;
        h.ambushStationUuid = null;
        h.restUntil = Math.max(h.restUntil, now + REST);
        goHome(uuid, h, now);
    }

    private double plunder(String uuid, HostileComponent h, String prey, double now) {
        FreighterComponent f = freighters.get(prey);
        if (f == null || f.cargoCommodity == null) return 0;
        String commodity = f.cargoCommodity;
        double taken = freighters.plunder(prey);
        if (taken <= 0) return 0;
        if (commodity.equals(h.lootCommodity)) {
            h.lootQuantity += taken;
        } else {
            h.lootCommodity = commodity;
            h.lootQuantity = taken;
        }
        h.plunders++;
        h.restUntil = Math.max(h.restUntil, now + REST);
        for (Listener l : listeners) l.plundered(uuid, prey, commodity, taken, now);
        return taken;
    }

    /** A patrol moves on to the next station of its route. */
    private void nextLeg(String uuid, HostileComponent h, double now) {
        if (h.route.length < 2) return;
        h.routeIndex = (h.routeIndex + 1) % h.route.length;
        h.stationUuid = h.route[h.routeIndex];
        TransferPlanner.Plan plan = planTo(uuid, h.stationUuid, HOLD_OFFSET, now, now + DEPART_DELAY);
        if (plan == null) {
            holdAt(uuid, h, h.stationUuid, HOLD_OFFSET, now);
            schedule(uuid, h, now + PATROL_HOLD);
            return;
        }
        follow(uuid, h, new ArrayList<>(), plan, State.PATROLLING);
    }

    /** Flies a pirate back to its lurk (with no lurk left, it holds where it is). */
    private void goHome(String uuid, HostileComponent h, double now) {
        TransferPlanner.Plan plan = h.stationUuid == null ? null
                : planTo(uuid, h.stationUuid, lurkOffset(h), now, now + DEPART_DELAY);
        if (plan == null) {
            h.state = State.HOLDING;
            schedule(uuid, h, Math.max(now + LOOK_INTERVAL, h.restUntil));
            return;
        }
        follow(uuid, h, new ArrayList<>(), plan, State.RETURNING);
    }

    /** After the bubble: circularizes, then flies to {@code offset} behind {@code station}. */
    private void fly(String uuid, HostileComponent h, Burn circ, StateVector circular, double tBurn, String station,
                     double offset, State state) {
        List<Burn> lead = new ArrayList<>();
        lead.add(circ);
        TransferPlanner.Plan plan = station == null ? null
                : planFrom(rails.parentOf(uuid), circular, station, offset, tBurn, tBurn + DEPART_DELAY);
        if (plan == null) {
            rails.cancelBurns(uuid);
            rails.scheduleBurn(uuid, circ);
            h.state = State.HOLDING;
            schedule(uuid, h, Math.max(tBurn + LOOK_INTERVAL, h.restUntil));
            return;
        }
        follow(uuid, h, lead, plan, state);
    }

    private void follow(String uuid, HostileComponent h, List<Burn> lead, TransferPlanner.Plan plan, State state) {
        rails.cancelBurns(uuid);
        for (Burn burn : lead) rails.scheduleBurn(uuid, burn);
        for (Burn burn : plan.burns) rails.scheduleBurn(uuid, burn);
        h.state = state;
        schedule(uuid, h, plan.arrivalTime);
    }

    /** Puts the ship on the station's orbit, {@code offset} meters behind it. */
    private void holdAt(String uuid, HostileComponent h, String station, double offset, double now) {
        if (station == null || !rails.isOnRails(station)) return;
        rails.cancelBurns(uuid);
        StateVector s = behind(rails.stateAt(station, now, b), offset);
        rails.attach(uuid, rails.parentOf(station), s, now);
    }

    private TransferPlanner.Plan planTo(String uuid, String station, double offset, double now, double minStart) {
        if (!rails.isOnRails(uuid)) return null;
        return planFrom(rails.parentOf(uuid), rails.stateAt(uuid, now, a), station, offset, now, minStart);
    }

    /** A transfer from a near-circular orbit to {@code offset} behind a station around the same body. */
    private TransferPlanner.Plan planFrom(String parent, StateVector ship, String station, double offset, double now,
                                          double minStart) {
        if (station == null || !rails.isOnRails(station) || !parent.equals(rails.parentOf(station))) return null;
        StateVector target = behind(rails.stateAt(station, now, b), offset);
        return TransferPlanner.plan(rails.muOf(parent), ship, target, now, minStart, floor(parent));
    }

    /** A patrol of another side that outguns {@code pirate}, holding at {@code station}; or null. */
    private String guard(String station, HostileComponent pirate) {
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            HostileComponent p = e.getValue();
            if (p.role != Role.PATROL || p.state != State.HOLDING || !station.equals(p.stationUuid)) continue;
            if (p.faction != null && p.faction.equals(pirate.faction)) continue;
            if (p.firepower >= pirate.firepower) return e.getKey();
        }
        return null;
    }

    /** True if some pirate is already after {@code target}. */
    private boolean hunted(String target) {
        for (HostileComponent h : hostiles.values()) if (target.equals(h.targetUuid)) return true;
        return false;
    }

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

    private double floor(String parent) {
        return rails.radiusOf(parent) + SAFE_ALTITUDE;
    }

    /**
     * {@code s} turned back along its orbit by {@code meters} of arc: the same circular orbit,
     * that far behind. Changes {@code s} and returns it.
     */
    static StateVector behind(StateVector s, double meters) {
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double angle = -dir * meters / s.distance();
        double c = Math.cos(angle), sn = Math.sin(angle);
        return s.set(c * s.x - sn * s.y, sn * s.x + c * s.y, c * s.vx - sn * s.vy, sn * s.vx + c * s.vy);
    }

    /** Same position, circular speed, same sense of motion. */
    static StateVector circularState(double mu, StateVector s) {
        int dir = (s.x * s.vy - s.y * s.vx) < 0 ? -1 : 1;
        double r = s.distance();
        double vc = Math.sqrt(mu / r);
        return new StateVector(s.x, s.y, -dir * s.y / r * vc, dir * s.x / r * vc);
    }

    /** Eccentricity of the orbit through {@code s}. */
    static double eccentricity(double mu, StateVector s) {
        double r = s.distance();
        double v2 = s.vx * s.vx + s.vy * s.vy;
        double rv = s.x * s.vx + s.y * s.vy;
        double ex = ((v2 - mu / r) * s.x - rv * s.vx) / mu;
        double ey = ((v2 - mu / r) * s.y - rv * s.vy) / mu;
        return Math.hypot(ex, ey);
    }
}
