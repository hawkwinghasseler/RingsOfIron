package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.AvatarInteractor;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewModeController;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.UiContextProvider;

/**
 * ui-hud's {@link UiContextProvider} from the game: where the captain is (the bridge's seat, the
 * avatar's ship, a spacewalk), the ship's condition, docking, warp, the helm and its order.
 */
public class PlayerUiContext implements UiContextProvider {
    private static final ComponentMapper<GridPositionComponent> POSITIONS = ComponentMapper.getFor(GridPositionComponent.class);
    private static final ComponentMapper<EvaComponent> EVA = ComponentMapper.getFor(EvaComponent.class);

    private final GameContext ctx;
    private final ViewModeController view;
    private final TimeWarpController warp;
    private final RailsUniverse universe;
    private final Navigation nav;
    private final Bridge bridge;
    private final AvatarInteractor avatar;
    private final DockingSystem docking;
    private final Spacewalks spacewalks;
    private final GunCrews gunCrews;
    private final PirateBase pirateBase;
    private final CombatThreats threats;
    private final ThreatProvider.ThreatFrame frame = new ThreatProvider.ThreatFrame();
    private final ShipAlerts damage = new ShipAlerts();

    public PlayerUiContext(GameContext ctx, ViewModeController view, TimeWarpController warp, RailsUniverse universe,
                           Navigation nav, Bridge bridge, AvatarInteractor avatar, DockingSystem docking, Spacewalks spacewalks,
                           GunCrews gunCrews, PirateBase pirateBase) {
        this.ctx = ctx;
        this.view = view;
        this.warp = warp;
        this.universe = universe;
        this.nav = nav;
        this.bridge = bridge;
        this.avatar = avatar;
        this.docking = docking;
        this.spacewalks = spacewalks;
        this.gunCrews = gunCrews;
        this.pirateBase = pirateBase;
        this.threats = new CombatThreats(ctx);
    }

    @Override
    public void read(UiContext out) {
        Entity ship = ctx.player();
        out.ship = ship;
        out.exteriorView = view == null || view.getMode() == ViewMode.EXTERIOR;
        out.simSeconds = ctx.engine.getSimSeconds();
        out.warp = warp.getWarp();
        out.railsWarp = warp.isRailsWarp();
        String hold = warp.getRailsWarpBlockedReason();
        out.warpHold = hold == null ? "" : hold;
        double next = nav.nextDropOutTime();
        out.warpNextStop = Double.isNaN(next) ? Float.NaN : (float) Math.max(0, next - universe.now());
        out.presence = presence(ship);
        out.seatedGun = bridge.seatedGun();
        if (ship == null) return;

        Entity at = docking.dockedTo(ship);
        if (at != null) {
            StationComponent station = at.getComponent(StationComponent.class);
            if (station != null) {
                out.docked = true;
                HudMarkerComponent marker = at.getComponent(HudMarkerComponent.class);
                out.stationName = marker != null && !marker.label.isEmpty() ? marker.label : station.name;
            } else {
                out.latched = true;
            }
        }

        frame.clear();
        threats.collect(frame);
        out.missilesInbound = frame.incoming();
        boolean hostiles = hostileNear(ship);
        out.combat = out.missilesInbound > 0 || hostiles;
        DisabledComponent.Reason disabled = ShipDisablement.reason(ship);
        out.disabledReason = disabled == null ? "" : disabled.label;
        out.abandoning = AbandonShip.abandoning(ship);
        damage.scan(ship.getComponent(ShipGridComponent.class));
        out.emergency = disabled != null || out.abandoning || damage.burningTiles > 0 || damage.breaches > 0
                || damage.lowPressureCompartments > 0;
        out.condition = out.missilesInbound > 0 || out.emergency ? Condition.RED
                : hostiles || damage.any() ? Condition.YELLOW : Condition.GREEN;
        out.crewOutside = spacewalks.statusLine() != null;
        out.holdFire = gunCrews.holdFire;

        if (bridge.playerAtHelm()) {
            out.helmsman = "you";
        } else {
            Entity helmsman = bridge.helmsman(ship);
            CrewMemberComponent c = helmsman == null ? null : helmsman.getComponent(CrewMemberComponent.class);
            out.helmsman = c == null ? "" : c.name;
        }
        String fault = bridge.helmFault(ship);
        out.helmFault = fault == null ? "" : fault;
        String order = nav.helm.describe();
        out.helmOrder = order == null ? "" : order;
        HelmOrderComponent o = nav.helm.order();
        out.burnFlown = o != null && o.burning;
    }

    private Presence presence(Entity ship) {
        if (bridge.playerAtHelm()) return Presence.HELM;
        if (bridge.playerAtWeapons()) return Presence.WEAPONS;
        if (bridge.seatedGun() != null) return Presence.GUN;
        Entity avatar = this.avatar.getAvatar();
        if (avatar == null) return ship == null ? Presence.NONE : Presence.DECK;
        EvaComponent eva = EVA.get(avatar);
        if (eva != null && eva.outside()) return Presence.EVA;
        GridPositionComponent pos = POSITIONS.get(avatar);
        if (pos == null || pos.ship == null) return ship == null ? Presence.NONE : Presence.DECK;
        return pos.ship == ship ? Presence.DECK : Presence.ABOARD_OTHER;
    }

    /** A ship fighting the player that the ship tracks ({@link GunCrews#hostile}). */
    private boolean hostileNear(Entity ship) {
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        if (sensor == null) return false;
        for (int i = 0; i < sensor.tracks.size; i++) {
            Track t = sensor.tracks.get(i);
            if (t.target == null || t.target == ship || !GunCrews.hostile(ship, pirateBase, t.target)) continue;
            return true;
        }
        return false;
    }
}
