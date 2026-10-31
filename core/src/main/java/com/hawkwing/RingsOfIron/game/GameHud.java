package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.gdx.utils.Disposable;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.hawkwing.RingsOfIron.wiring.PlayerShipStatus;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.events.InterceptEvent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.modding.ModTrustDialog;

/**
 * Attaches ui-hud to a {@link GameWorld}. Needs a GL context, so screens build it in {@code show()}.
 *
 * <p>Bridges: physics readout, orbit info and the orbit map from orbital-mechanics, the avatar from ai-crew, hull hits
 * on the player's ship from combat-sys flashed on the minimap, and the view mode driving
 * audio-spatial's listener. Targeting comes from combat-sys through {@link CombatTargetingProvider},
 * which the screen sets once it has the player's {@link PlayerGunnery}.
 */
public class GameHud implements Disposable {
    public final UiHud hud;
    private final GameWorld world;
    private final Listener<HullHitEvent> hullHits;
    private final Listener<InterceptEvent> intercepts;

    public GameHud(GameWorld world) {
        this(world, new UiHud());
    }

    /** The docked station's yard, as the yard window sees it. */
    public final YardWork yard;

    public GameHud(GameWorld world, UiHud hud) {
        this.world = world;
        this.hud = hud;
        hud.addTo(world.engine.getEcsEngine());
        hud.setPhysics(world.physics);
        hud.setOrbitInfo(new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer));
        PlayerOrbitPath orbitPath = new PlayerOrbitPath(world.context, world.physics, world.universe, world.macroSim, world.pirateBase, world.nav,
                world.burns);
        hud.setOrbitPath(orbitPath);
        // The full-screen orbit map's mouse planning and its buttons: the burn planner and navigation
        hud.setManeuverPlanner(new MapManeuvers(world.context, world.physics, world.universe, world.burns, world.nav, orbitPath));
        // interior-grid's tanks, hull and power circuits on the ship gauges.
        hud.setShipStatus(new PlayerShipStatus(world::getPlayer));
        hud.setInteractor(world.avatar);
        hud.getViewMode().addListener((previous, current) -> world.audioListener.setMode(toListenerMode(current)));
        world.audioListener.setMode(toListenerMode(hud.getViewMode().getMode()));
        // macro-sim's traffic on rails for the [M] map.
        hud.setTrafficMap(new RailsTrafficMap(world.context, world.physics, world.universe, world.macroSim, world.nav, world.contracts, world.derelicts, world.pirateBase, world.tension));
        // macro-sim's station markets for the [T] trade window.
        hud.setTrade(new MarketTrade(world.context, world.docking, world.macroSim, world.cargo, world.cargoSettlement, world.salvage));
        // The docked station's yard for the [Y] yard window: repairs and refits.
        hud.setYard(yard = new YardWork(world.context, world.docking, world.combatDefs, world.yard, world.fieldRefits, world.furnishings));
        // The station's mission board and the contracts held, for the [J] mission window.
        hud.setMissions(new ContractMissions(world.context, world.docking, world.macroSim, world.standing, world.tension, world.contracts));
        // Ships for sale at the docked station and the prize in tow, for the shipyard window (the [Y] yard's Ships for sale).
        hud.setShipyard(new ShipyardWork(world.context, world.standing, world.shipyard));
        // The ship latched to, for the [K] boarding window: boarding it, then stripping it.
        hud.setBoarding(new BoardingWork(world.context, world.docking, world.boarding, world.salvage));
        // The player's crew, the station's hires and the prisoners, for the [I] crew window.
        hud.setCrew(new CrewRoster(world.context, world.crew, world.macroSim, world.standing, world.crewCareer, world.crewHiring, world.medical, world.prisoners, world.spacewalks));
        hullHits = (signal, hit) -> {
            if (hit.ship != null && hit.ship == world.getPlayer()) hud.flashHullHit(hit.tileX, hit.tileY);
        };
        world.combatEvents.hullHits.add(hullHits);
        // combat-sys's missiles and point-defense hits on the threat overlay.
        hud.setThreats(new CombatThreats(world.context));
        intercepts = (signal, hit) -> hud.flashIntercept(hit.point.x, hit.point.y, hit.destroyed);
        world.combatEvents.intercepts.add(intercepts);
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
        world.combatEvents.intercepts.remove(intercepts);
        hud.removeFrom(world.engine.getEcsEngine());
        hud.dispose();
    }
}
