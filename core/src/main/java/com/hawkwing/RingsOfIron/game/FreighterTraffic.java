package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.fleet.FreighterController;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.List;

/**
 * macro-sim's freighters in the bubble, docking and leaving under their own power. One flying to a
 * station that is in the bubble too is taken off its rails plan ({@link NpcPilotComponent}) and
 * berthed there ({@link BerthingSystem}); once docked it loads or unloads as it would on the rails
 * ({@link FreighterController#berthedInBubble}). A docked one with somewhere else to go waits
 * {@link FreighterController#UNDOCK_DELAY}, then undocks and flies off until it leaves the bubble,
 * where macro-sim plans the rest of its leg.
 */
public class FreighterTraffic extends EntitySystem implements BubbleTransitionListener {
    /** Seconds between looks at the bubble's freighters. */
    static final float CHECK_SECONDS = 1f;

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
            if (b == null) {
                if (docked == null && to != null) berth(e, to);
            } else if (b.phase == BerthingComponent.Phase.BERTHED) {
                if (docked != null && f.destinationUuid != null && !f.destinationUuid.equals(uuid(docked))
                        && b.time > FreighterController.UNDOCK_DELAY) {
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
        if (e != null) e.remove(BerthingComponent.class);
    }

    private static String uuid(Entity e) {
        UuidComponent id = e == null ? null : e.getComponent(UuidComponent.class);
        return id == null ? null : id.id;
    }
}
