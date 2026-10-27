package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.TargetingProvider;
import com.ringsofiron.uihud.widgets.RadarScope;

/**
 * Fills the {@link RadarScope} with every {@link HudMarkerComponent} entity in the bubble, placed
 * relative to the player's ship and tinted like its on-screen marker; the locked target is ringed.
 */
public class RadarSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = HudRenderSystem.DEFAULT_PRIORITY + 1;
    private static final Family MARKED = Family.all(HudMarkerComponent.class, TransformComponent.class).get();
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class, TransformComponent.class).get();
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);

    private final RadarScope scope;
    private ImmutableArray<Entity> marked;
    private ImmutableArray<Entity> players;
    private TargetingProvider targeting;

    public RadarSystem(RadarScope scope) {
        super(DEFAULT_PRIORITY);
        this.scope = scope;
    }

    public void setTargeting(TargetingProvider targeting) {
        this.targeting = targeting;
    }

    @Override
    public void addedToEngine(Engine engine) {
        marked = engine.getEntitiesFor(MARKED);
        players = engine.getEntitiesFor(PLAYER);
    }

    @Override
    public void update(float deltaTime) {
        Entity player = players.size() > 0 ? players.first() : null;
        TransformComponent self = player == null ? null : TRANSFORMS.get(player);
        scope.begin(self != null, self == null ? 90f : self.rotation);
        if (self == null) return;
        Entity lock = targeting == null ? null : targeting.getLockedTarget();
        for (Entity e : marked) {
            if (e == player) continue;
            TransformComponent t = TRANSFORMS.get(e);
            scope.addContact(e, t.x - self.x, t.y - self.y, MARKERS.get(e).color, e == lock);
        }
    }
}
