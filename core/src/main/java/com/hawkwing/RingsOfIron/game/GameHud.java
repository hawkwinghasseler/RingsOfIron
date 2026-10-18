package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.modding.ModTrustDialog;

/**
 * Attaches ui-hud to a {@link GameWorld}. Needs a GL context, so screens build it in {@code show()}.
 *
 * <p>Bridges: physics readout, orbit info from orbital-mechanics, the avatar from ai-crew, hull hits
 * on the player's ship from combat-sys flashed on the minimap, and the view mode driving
 * audio-spatial's listener. Targeting stays on ui-hud's stand-in until combat-sys has a target lock.
 */
public class GameHud implements Disposable {
    public final UiHud hud;
    private final GameWorld world;
    private final Listener<HullHitEvent> hullHits;

    public GameHud(GameWorld world) {
        this(world, new UiHud());
    }

    public GameHud(GameWorld world, UiHud hud) {
        this.world = world;
        this.hud = hud;
        hud.addTo(world.engine.getEcsEngine());
        hud.setPhysics(world.physics);
        hud.setOrbitInfo(new PlayerOrbitInfo(world.universe, world::getPlayer));
        hud.setInteractor(world.avatar);
        hud.getViewMode().addListener((previous, current) -> world.audioListener.setMode(toListenerMode(current)));
        world.audioListener.setMode(toListenerMode(hud.getViewMode().getMode()));
        hullHits = (signal, hit) -> {
            if (hit.ship != null && hit.ship == world.getPlayer()) hud.flashHullHit(hit.tileX, hit.tileY);
        };
        world.combatEvents.hullHits.add(hullHits);
    }

    /** The mod-script trust prompt as a dialog; pass to {@link GameWorld.Modules#modTrust} before loading content. */
    public ModTrustDialog createModTrustDialog() {
        return new ModTrustDialog(hud.getStage(), hud.getSkin(), null);
    }

    public static AudioListenerComponent.Mode toListenerMode(ViewMode mode) {
        return mode == ViewMode.INTERIOR ? AudioListenerComponent.Mode.INTERIOR : AudioListenerComponent.Mode.EXTERIOR;
    }

    public void resize(int width, int height) {
        hud.resize(width, height);
    }

    /** Draws the HUD; call after the world is drawn and {@link GameWorld#update} has run. */
    public void draw(float delta) {
        hud.draw(delta);
    }

    @Override
    public void dispose() {
        world.combatEvents.hullHits.remove(hullHits);
        hud.removeFrom(world.engine.getEcsEngine());
        hud.dispose();
    }
}
