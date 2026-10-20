package com.ringsofiron.uihud.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * A console on a ship's interior grid that the player's avatar can use. When the avatar is
 * within {@code InteractPromptSystem.INTERACT_RADIUS} tiles, the HUD shows
 * "Press [E] for the {title} panel", and pressing E opens the console window.
 *
 * Usually added to a subsystem entity (helm, weapons, life support); the window then shows
 * that subsystem's status.
 */
public class InteractableConsoleComponent implements Component {
    /** Stable id the console window layout is chosen by, e.g. "helm". Saved with the entity. */
    public String consoleId = "";
    /** Name shown in the prompt and window title. */
    public String title = "";
    /** Tile the avatar must stand near. */
    public int tileX, tileY;
    /** UUID of the ship entity whose grid the console sits on (for save files). */
    public String shipUuid = "";
    /** Live reference to that ship, resolved from {@link #shipUuid} after load. */
    public transient Entity ship;

    public InteractableConsoleComponent() {}

    public InteractableConsoleComponent(String consoleId, String title, Entity ship, int tileX, int tileY) {
        this.consoleId = consoleId;
        this.title = title;
        this.ship = ship;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    /**
     * True when this console is aboard {@code ship}. The live reference is not saved, so after a
     * load the console adopts the ship whose UUID matches {@link #shipUuid}.
     */
    public boolean isOn(Entity ship) {
        if (this.ship == null && ship != null && shipUuid != null && !shipUuid.isEmpty()) {
            UuidComponent id = ship.getComponent(UuidComponent.class);
            if (id != null && shipUuid.equals(id.id)) this.ship = ship;
        }
        return ship != null && this.ship == ship;
    }
}
