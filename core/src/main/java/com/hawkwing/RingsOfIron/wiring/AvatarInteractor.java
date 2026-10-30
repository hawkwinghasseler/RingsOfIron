package com.hawkwing.RingsOfIron.wiring;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.uihud.providers.InteractorProvider;

/** ui-hud's view of the player's avatar, read from ai-crew's avatar and grid position. */
public class AvatarInteractor implements InteractorProvider {
    private static final Family AVATAR = Family.all(PlayerAvatarComponent.class, GridPositionComponent.class).get();
    private final ComponentMapper<GridPositionComponent> positions = ComponentMapper.getFor(GridPositionComponent.class);
    private final ImmutableArray<Entity> avatars;

    public AvatarInteractor(Engine engine) {
        avatars = engine.getEntitiesFor(AVATAR);
    }

    /** The player's avatar entity, or null. */
    public Entity getAvatar() {
        return avatars.size() == 0 ? null : avatars.first();
    }

    @Override
    public Entity getAvatarShip() {
        Entity avatar = getAvatar();
        return avatar == null ? null : positions.get(avatar).ship;
    }

    @Override
    public boolean getAvatarTile(Vector2 out) {
        Entity avatar = getAvatar();
        if (avatar == null) return false;
        GridPositionComponent pos = positions.get(avatar);
        if (pos.ship == null) return false;
        out.set(pos.x, pos.y);
        return true;
    }
}
