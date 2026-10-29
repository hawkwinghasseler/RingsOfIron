package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.EntitySystem;

/**
 * Moves the world camera onto the ship (or avatar) once the frame's ticks have run, before any HUD
 * system projects a world point to the screen. Without it the HUD placed this frame's positions with
 * last frame's camera, so anything drawn round a moving ship (the prograde markers above all) sat
 * off it by however far the ship moved that frame: zero, one or two ticks' worth as the frame
 * caught up, which read as a flicker zoomed in, until a floating origin rebase stopped the ship in
 * the bubble again.
 *
 * <p>The game still follows after the frame too, for paused frames and eased zooms; this only
 * makes sure the HUD sees the same camera the world is drawn with.
 */
public class CameraFollowSystem extends EntitySystem {
    /** After physics and game logic, before the first HUD system ({@link WireframeSyncSystem}). */
    public static final int DEFAULT_PRIORITY = WireframeSyncSystem.DEFAULT_PRIORITY - 20;

    private Runnable follow;

    public CameraFollowSystem() {
        super(DEFAULT_PRIORITY);
    }

    /** What centers the camera, or null to leave it where it is. */
    public void setFollow(Runnable follow) {
        this.follow = follow;
    }

    @Override
    public void update(float deltaTime) {
        if (follow != null) follow.run();
    }
}
