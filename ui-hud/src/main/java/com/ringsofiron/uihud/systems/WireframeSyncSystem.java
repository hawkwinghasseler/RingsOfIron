package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.WireframeMinimap;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.widgets.AlertList;

/**
 * Feeds the player ship's interior grid to the {@link WireframeMinimap} and refreshes the
 * alert list. The ship shown is the one set with {@link #setShip}, or else the first entity
 * with both a {@link PlayerControllerComponent} and a {@link ShipGridComponent}.
 */
public class WireframeSyncSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 900;
    /** Alerts are recounted at most this often (seconds); the minimap itself redraws every frame. */
    public static final float SCAN_INTERVAL = 0.25f;

    private static final Family PLAYER_SHIPS = Family.all(PlayerControllerComponent.class, ShipGridComponent.class).get();
    private static final ComponentMapper<ShipGridComponent> GRIDS = ComponentMapper.getFor(ShipGridComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRIES = ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    private final WireframeMinimap minimap;
    private final AlertList alertList;
    private final ShipAlerts alerts = new ShipAlerts();
    private final Vector2 avatar = new Vector2();
    private ImmutableArray<Entity> playerShips;
    private Entity explicitShip;
    private Entity currentShip;
    private InteractorProvider interactor;
    private float scanTimer;

    public WireframeSyncSystem(WireframeMinimap minimap, AlertList alertList) {
        super(DEFAULT_PRIORITY);
        this.minimap = minimap;
        this.alertList = alertList;
    }

    /** Shows this ship instead of the player-controlled one (null to go back to automatic). */
    public void setShip(Entity ship) {
        explicitShip = ship;
    }

    public void setInteractor(InteractorProvider interactor) {
        this.interactor = interactor;
    }

    public Entity getShownShip() {
        return currentShip;
    }

    public ShipAlerts getAlerts() {
        return alerts;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerShips = engine.getEntitiesFor(PLAYER_SHIPS);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        playerShips = null;
    }

    @Override
    public void update(float deltaTime) {
        Entity ship = explicitShip != null ? explicitShip
                : (playerShips != null && playerShips.size() > 0 ? playerShips.first() : null);
        ShipGridComponent grid = ship != null ? GRIDS.get(ship) : null;

        if (ship != currentShip) {
            currentShip = ship;
            minimap.clearShip();
            scanTimer = 0f;
        }
        minimap.setGrid(grid);
        minimap.setVisible(grid != null);

        minimap.getSubsystems().clear();
        ShipSubsystemRegistryComponent registry = ship != null ? REGISTRIES.get(ship) : null;
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = SUBSYSTEMS.get(e);
                if (s != null) minimap.getSubsystems().add(s);
            }
        }

        boolean avatarAboard = interactor != null && ship != null && interactor.getAvatarShip() == ship
                && interactor.getAvatarTile(avatar);
        minimap.setAvatar(avatarAboard, avatar.x, avatar.y);

        scanTimer -= deltaTime;
        if (scanTimer <= 0f) {
            scanTimer = SCAN_INTERVAL;
            alerts.scan(grid);
            if (alertList != null) alertList.show(grid != null ? alerts : null);
        }
    }
}
