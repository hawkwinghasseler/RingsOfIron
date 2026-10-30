package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;

/**
 * Who is aboard each ship: the crew ({@link CrewService#CREW}) by {@link GridPositionComponent#ship},
 * each ship's in the engine's crew order, the order a scan of every crew member meets them. Use it
 * instead of scanning all the crew and skipping those on other ships.
 *
 * It is rebuilt on the first lookup after crew join or leave the engine or anyone changes ship
 * ({@link GridPositionComponent#setShip}), so a lookup always gives what such a scan would at that
 * moment. A rebuild makes new arrays: one a caller is still walking stays as it was, even if the
 * walk moves crew between ships.
 */
public final class CrewIndex {

    private static final ImmutableArray<Entity> NONE = new ImmutableArray<>(new Array<>(Entity.class));

    private final ComponentMapper<GridPositionComponent> posM = ComponentMapper.getFor(GridPositionComponent.class);
    private final ImmutableArray<Entity> crew;
    private final IdentityMap<Entity, ImmutableArray<Entity>> byShip = new IdentityMap<>();
    /** Crew aboard no ship. */
    private ImmutableArray<Entity> nowhere = NONE;
    private boolean joinedOrLeft = true;
    private long shipChanges = -1;

    CrewIndex(Engine engine) {
        crew = engine.getEntitiesFor(CrewService.CREW);
        engine.addEntityListener(CrewService.CREW, new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                joinedOrLeft = true;
            }

            @Override
            public void entityRemoved(Entity entity) {
                joinedOrLeft = true;
            }
        });
    }

    /** Everyone aboard {@code ship} (or aboard nothing, for null), alive or not, in crew order. */
    public ImmutableArray<Entity> aboard(Entity ship) {
        refresh();
        if (ship == null) return nowhere;
        ImmutableArray<Entity> on = byShip.get(ship);
        return on == null ? NONE : on;
    }

    private void refresh() {
        long changes = GridPositionComponent.shipChanges();
        if (!joinedOrLeft && changes == shipChanges) return;
        joinedOrLeft = false;
        shipChanges = changes;
        IdentityMap<Entity, Array<Entity>> lists = new IdentityMap<>();
        Array<Entity> none = null;
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            Entity ship = posM.get(e).ship;
            if (ship == null) {
                if (none == null) none = new Array<>(Entity.class);
                none.add(e);
                continue;
            }
            Array<Entity> list = lists.get(ship);
            if (list == null) lists.put(ship, list = new Array<>(Entity.class));
            list.add(e);
        }
        byShip.clear();
        for (IdentityMap.Entry<Entity, Array<Entity>> entry : lists) byShip.put(entry.key, new ImmutableArray<>(entry.value));
        nowhere = none == null ? NONE : new ImmutableArray<>(none);
    }
}
