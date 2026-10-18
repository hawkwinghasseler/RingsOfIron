package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.util.function.Supplier;

/**
 * Keeps audio-spatial's listener on the player's ship and, in interior view, at the avatar's
 * spot on the deck (ship-local meters). Runs every frame just before the audio systems.
 */
public class AudioListenerSync extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 890;

    private final ComponentMapper<AudioListenerComponent> listeners = ComponentMapper.getFor(AudioListenerComponent.class);
    private final ComponentMapper<ShipGridComponent> grids = ComponentMapper.getFor(ShipGridComponent.class);
    private final Supplier<Entity> player;
    private final AvatarInteractor avatar;
    private final Vector2 tile = new Vector2();
    private AudioListenerComponent.Mode mode = AudioListenerComponent.Mode.EXTERIOR;

    public AudioListenerSync(Supplier<Entity> player, AvatarInteractor avatar) {
        super(Math.min(DEFAULT_PRIORITY, AudioUpdateSystem.DEFAULT_PRIORITY - 1));
        this.player = player;
        this.avatar = avatar;
    }

    /** Set from the HUD's view mode: exterior hears from the ship, interior from the avatar. */
    public void setMode(AudioListenerComponent.Mode mode) {
        this.mode = mode;
    }

    public AudioListenerComponent.Mode getMode() {
        return mode;
    }

    @Override
    public void update(float deltaTime) {
        Entity ship = player.get();
        if (ship == null) return;
        AudioListenerComponent listener = listeners.get(ship);
        if (listener == null) {
            listener = new AudioListenerComponent();
            ship.add(listener);
        }
        listener.ship = ship;
        listener.mode = mode;

        // Inside, the listener is wherever the avatar walks, aboard a docked craft too
        Entity aboard = avatar.getAvatarShip();
        ShipGridComponent grid = aboard == null ? null : grids.get(aboard);
        if (mode == AudioListenerComponent.Mode.INTERIOR && grid != null && avatar.getAvatarTile(tile)) {
            listener.ship = aboard;
            listener.localX = grid.gridOriginLocal.x + tile.x * grid.tileSize;
            listener.localY = grid.gridOriginLocal.y + tile.y * grid.tileSize;
        }
    }
}
