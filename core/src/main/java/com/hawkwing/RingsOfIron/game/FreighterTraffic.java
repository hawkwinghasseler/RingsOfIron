package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.fleet.FreighterController;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.List;
import java.util.Set;

/**
 * macro-sim's freighters in the bubble, docking and leaving under their own power. One flying to a
 * station that is in the bubble too is taken off its rails plan ({@link NpcPilotComponent}) and
 * berthed there ({@link BerthingSystem}); once docked it loads or unloads as it would on the rails
 * ({@link FreighterController#berthedInBubble}). A docked one with somewhere else to go waits
 * {@link FreighterController#UNDOCK_DELAY}, then undocks and flies off until it leaves the bubble,
 * where macro-sim plans the rest of its leg.
 *
 * <p>The goods it buys and sells there cross the dock by hand ({@link CargoLoading}): the station's
 * loaders and its own crew carry what macro-sim has it take on or hand over between its bays and
 * the station's, and it does not undock until they are done, or {@link #MOST_LOADING} has gone by.
 * Anywhere else its bays are kept holding what it carries ({@link FreighterShips#stow}).
 */
public class FreighterTraffic extends EntitySystem implements BubbleTransitionListener {
    /** Seconds between looks at the bubble's freighters. */
    static final float CHECK_SECONDS = 1f;
    /** Seconds past {@link FreighterController#UNDOCK_DELAY} a freighter waits for its goods to be carried before it leaves anyway. */
    static final double MOST_LOADING = 1200;
    /** Units of difference between its bays and its cargo too small to carry. */
    static final double TOO_FEW = 0.5;

    private final GameWorld world;
    private float since = CHECK_SECONDS;

    public FreighterTraffic(GameWorld world) {
        this.world = world;
        world.berthing.listener = new BerthingSystem.BerthingListener() {
            @Override
            public void berthed(Entity craft, Entity station) {
                String id = uuid(craft), at = uuid(station);
                if (id != null && at != null) world.macroSim.freighters().berthedInBubble(id, at);
            }

            @Override
            public void departed(Entity craft, Entity station) {
            }
        };
        // Coming in to berth when warp collapses the bubble, it arrives in the warp
        world.timeWarp.changes.add((signal, change) -> {
            if (!change.rails) return;
            for (String id : world.macroSim.bubble().inBubble()) {
                world.macroSim.freighters().arriveFromBubble(id, world.engine.getSimSeconds());
            }
        });
    }

    @Override
    public void update(float deltaTime) {
        since += deltaTime;
        if (since < CHECK_SECONDS) return;
        since = 0f;
        for (String id : world.macroSim.bubble().inBubble()) {
            Entity e = world.engine.getEntity(id);
            FreighterComponent f = e == null ? null : e.getComponent(FreighterComponent.class);
            if (f == null || f.state != FreighterComponent.State.IN_BUBBLE) continue;
            BerthingComponent b = e.getComponent(BerthingComponent.class);
            Entity docked = world.docking.dockedTo(e);
            Entity to = stationInBubble(f.destinationUuid);
            carry(e, f, docked);
            if (b == null) {
                if (docked == null && to != null) berth(e, to);
            } else if (b.phase == BerthingComponent.Phase.BERTHED) {
                if (docked != null && f.destinationUuid != null && !f.destinationUuid.equals(uuid(docked))
                        && b.time > FreighterController.UNDOCK_DELAY
                        && (!loading(e) || b.time > FreighterController.UNDOCK_DELAY + MOST_LOADING)) {
                    world.cargo.callOff(e);
                    world.berthing.depart(e);
                    world.macroSim.freighters().undockedInBubble(id);
                }
            } else if (b.phase == BerthingComponent.Phase.DEPART && to != null && !to.equals(b.station.get(world.engine.getUuidRegistry()))
                    && "Leaving".equals(b.status)) {
                // Clear of the last station, and the next one is in the bubble too
                berth(e, to);
            }
        }
    }

    /**
     * Has the goods {@code freighter}'s bays are short of, or hold too many of, carried across to or
     * from {@code docked}, the station it is latched to (null for none); with no one to carry them,
     * or not docked, the bays are made to hold its cargo at once, once nothing is in hand.
     */
    private void carry(Entity freighter, FreighterComponent f, Entity docked) {
        CargoHoldComponent hold = freighter.getComponent(CargoHoldComponent.class);
        if (hold == null) return;
        if (docked == null || !world.cargo.canCarry(freighter, docked)) {
            if (!loading(freighter)) FreighterShips.stow(world, freighter);
            return;
        }
        CargoOrdersComponent orders = freighter.getComponent(CargoOrdersComponent.class);
        EntityRef at = EntityRef.of(docked);
        Set<String> goods = FreighterShips.commodities(hold);
        if (f.cargoCommodity != null) goods.add(f.cargoCommodity);
        for (String k : goods) {
            double want = k.equals(f.cargoCommodity) ? f.cargoQuantity : 0;
            double have = hold.amount(k) + (orders == null ? 0 : orders.pending(at, k));
            double diff = want - have;
            if (Math.abs(diff) > TOO_FEW) world.cargo.order(freighter, docked, k, Math.abs(diff), diff > 0);
        }
    }

    /** True while any of {@code freighter}'s goods are still to be carried, or are in someone's hands. */
    private static boolean loading(Entity freighter) {
        CargoOrdersComponent orders = freighter.getComponent(CargoOrdersComponent.class);
        if (orders == null) return false;
        for (CargoOrdersComponent.CargoOrder o : orders.orders) {
            if (!o.closed || o.inHand > 0) return true;
        }
        return false;
    }

    private void berth(Entity freighter, Entity station) {
        freighter.add(new BerthingComponent(EntityRef.of(station), 0, BerthingComponent.Phase.APPROACH));
        freighter.remove(NpcPilotComponent.class);
    }

    /** The station with this uuid, if it is in the bubble. */
    private Entity stationInBubble(String uuid) {
        Entity s = uuid == null ? null : world.engine.getEntity(uuid);
        return s != null && s.getComponent(StationComponent.class) != null && world.docking.bodyOf(s) != null ? s : null;
    }

    @Override
    public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
        // StationTraffic berths one that comes in at its station
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        Entity e = world.engine.getEntity(uuid);
        if (e == null) return;
        e.remove(BerthingComponent.class);
        // On the rails it holds what it carries; whatever was still to be carried is done with
        world.cargo.callOff(e);
        FreighterShips.stow(world, e);
    }

    private static String uuid(Entity e) {
        UuidComponent id = e == null ? null : e.getComponent(UuidComponent.class);
        return id == null ? null : id.id;
    }
}
